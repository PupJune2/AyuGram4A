package com.radolyn.ayugram.ui.aurora;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Aurora Flow's chat light: the whole chat screen is one stage lit by three
 * big blurred lights in the person's avatar colours, two behind the header
 * and one rising behind the composer, under a shade that keeps the messages
 * readable. The header and the chat background both draw this same stage
 * (each its own slice), so it reads as one surface with no seam.
 * <p>
 * Sizes follow the design's 390 x 844 frame, scaled to the screen width; the
 * bottom light and the lower half of the shade stay anchored to the bottom.
 */
public final class AuroraChatStage {

    /** Decides, on each frame, whether the stage replaces the header colour and the wallpaper. */
    public interface Condition {
        boolean isEnabled();
    }

    private static final long FRAME_DELAY_MS = AuroraLights.FRAME_DELAY_MS;
    private static final long PALETTE_MS = 1300;
    private static final float DESIGN_WIDTH = 390f;
    private static final float DESIGN_HEIGHT = 844f;
    private static final int BASE = 0xff0b0b10;

    /**
     * The board's per-chat palettes, by Telegram's avatar colour (red, orange,
     * violet, green, cyan, blue, pink): {header light, second light, bottom light}.
     */
    private static final int[][] PALETTES = {
            {0xffdc2626, 0xffc2410c, 0xfffdba74},
            {0xffea580c, 0xffbe123c, 0xfffbbf24},
            {0xff7c3aed, 0xffc026d3, 0xfff472b6},
            {0xff059669, 0xff0369a1, 0xffa3e635},
            {0xff0d9488, 0xff0284c7, 0xff5eead4},
            {0xff0284c7, 0xff4f46e5, 0xff22d3ee},
            {0xffdb2777, 0xff7c3aed, 0xfffb923c},
    };
    private static final int[] SAVED = {0xff2563eb, 0xff1e40af, 0xff60a5fa};

    // {left, top, size} in the design's frame; the third is measured from the bottom.
    private static final float[][] LAYOUT = {{-110, -150, 320}, {170, -130, 360}, {150, 640, 300}};
    private static final float[] OPACITY = {0.9f, 0.82f, 0.64f};
    private static final long[] PERIOD = {9_000, 11_000, 13_000};
    private static final float BLUR = 64f;

    private final Paint basePaint = new Paint();
    private final Paint shadePaint = new Paint();
    private final Paint[] lightPaints = new Paint[3];
    private final RadialGradient[] shaders = new RadialGradient[3];
    private final int[] shaderColors = new int[3];
    private final Matrix matrix = new Matrix();
    private final ArrayList<View> views = new ArrayList<>();
    private final Runnable tick = this::onTick;
    private final long startTime = SystemClock.elapsedRealtime();

    private Condition condition;
    private int[] fromPalette;
    private int[] palette;
    private long paletteStart;

    private ImageReceiver avatar;
    private Bitmap avatarBitmap;

    private int shadeHeight = -1;
    private int shadeWidth = -1;

    private boolean ticking;
    private long firstDrawTime;
    private long frameTime;
    // The stage's height, eased so the bottom light rises and sinks with the keyboard instead of jumping.
    private float targetHeight;
    private float shownHeight;
    private float energy;
    private float pointerX;
    private float pointerY;

    public AuroraChatStage() {
        for (int i = 0; i < lightPaints.length; i++) {
            lightPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        palette = PALETTES[5];
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /** True when the stage should draw now: Aurora is on and the chat has no theme or wallpaper of its own. */
    public boolean isEnabled() {
        return AuroraTheme.isActive() && (condition == null || condition.isEnabled());
    }

    /**
     * The three light colours for a chat: from its avatar photo when it has a
     * colourful one, otherwise the board's palette for its avatar colour.
     */
    public static int[] paletteFor(int colorIndex, boolean saved, Bitmap photo) {
        if (saved) {
            return SAVED.clone();
        }
        int[] fromPhoto = photo != null ? paletteFromPhoto(photo) : null;
        return fromPhoto != null ? fromPhoto : PALETTES[Math.abs(colorIndex) % PALETTES.length].clone();
    }

    /** The palette for a chat or person without a photo: their avatar colour index, or Saved Messages. */
    public void setColorIndex(int index, boolean saved) {
        int[] colors = saved ? SAVED : PALETTES[Math.abs(index) % PALETTES.length];
        setPalette(colors, false);
    }

    /** Once {@code receiver} has a photo, the lights take its colours. */
    public void setAvatar(ImageReceiver receiver) {
        avatar = receiver;
        avatarBitmap = null;
        checkAvatar();
    }

    private void checkAvatar() {
        if (avatar == null) {
            return;
        }
        Bitmap bitmap = avatar.getBitmap();
        if (bitmap == null || bitmap == avatarBitmap) {
            return;
        }
        avatarBitmap = bitmap;
        int[] colors = paletteFromPhoto(bitmap);
        if (colors != null) {
            // A photo that is ready while the chat slides in just sets the colours; a later one fades them over.
            boolean shown = firstDrawTime != 0 && SystemClock.elapsedRealtime() - firstDrawTime > 250;
            setPalette(colors, shown);
        }
    }

    private void setPalette(int[] colors, boolean animated) {
        if (palette != null && Arrays.equals(palette, colors)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (animated && AuroraTheme.canAnimate() && palette != null) {
            fromPalette = currentPalette(now);
            paletteStart = now;
        } else {
            fromPalette = null;
        }
        palette = colors.clone();
        invalidateViews();
    }

    private int[] currentPalette(long now) {
        if (fromPalette == null) {
            return palette.clone();
        }
        float t = Math.min(1f, (now - paletteStart) / (float) PALETTE_MS);
        if (t >= 1f) {
            fromPalette = null;
            return palette.clone();
        }
        float e = CubicBezierInterpolator.DEFAULT.getInterpolation(t);
        int[] colors = new int[palette.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = mix(fromPalette[i], palette[i], e);
        }
        return colors;
    }

    /**
     * Draws the part of the stage a view covers.
     *
     * @param view        the view drawing; it is redrawn with every frame of the stage
     * @param offsetY     where the view's top is on the stage
     * @param viewHeight  the view's height
     * @param stageWidth  the stage (and view) width
     * @param stageHeight the whole stage's height (the chat screen)
     */
    public void draw(Canvas canvas, View view, float offsetY, float viewHeight, int stageWidth, int stageHeight) {
        if (stageWidth <= 0 || stageHeight <= 0) {
            return;
        }
        if (!views.contains(view)) {
            views.add(view);
        }
        targetHeight = stageHeight;
        if (frameTime == 0 || shownHeight <= 0) {
            shownHeight = stageHeight;
            snapshot();
        }
        if (firstDrawTime == 0) {
            firstDrawTime = frameTime;
        }
        final int height = Math.round(shownHeight);
        final float unit = AndroidUtilities.dp(1) * (stageWidth / (float) AndroidUtilities.dp(DESIGN_WIDTH));
        final int[] colors = currentPalette(frameTime);
        final float top = offsetY;
        final float bottom = offsetY + viewHeight;

        canvas.save();
        canvas.translate(0, -offsetY);
        // background-color: mix(palette[0], #0B0B10, .86)
        basePaint.setColor(mix(colors[0], BASE, 0.86f));
        canvas.drawRect(0, top, stageWidth, bottom, basePaint);

        boolean animate = AuroraTheme.canAnimate();
        long time = animate ? frameTime - startTime : 0;
        for (int i = 0; i < 3; i++) {
            drawLight(canvas, i, colors[i], unit, height, time);
        }
        drawShade(canvas, unit, stageWidth, height, top, bottom);
        canvas.restore();

        if (animate && !ticking) {
            ticking = true;
            AndroidUtilities.runOnUIThread(tick, FRAME_DELAY_MS);
        }
    }

    private void drawLight(Canvas canvas, int i, int color, float unit, int stageHeight, long time) {
        float[] box = LAYOUT[i];
        float radius = box[2] / 2f;
        float extent = radius + BLUR * 2.5f;
        if (shaders[i] == null || shaderColors[i] != color) {
            // A disc blurred by a Gaussian of 64: solid inside, half strength at its edge, gone ~2.5 sigma out.
            float r = radius / extent;
            float s = BLUR / extent;
            shaders[i] = new RadialGradient(0, 0, 1f,
                    new int[]{withAlpha(color, 1f), withAlpha(color, 0.84f), withAlpha(color, 0.5f), withAlpha(color, 0.16f), withAlpha(color, 0f)},
                    new float[]{0f, Math.max(0f, r - s), r, Math.min(1f, r + s), 1f},
                    Shader.TileMode.CLAMP);
            shaderColors[i] = color;
            lightPaints[i].setShader(shaders[i]);
        }

        // afDrift: translate(0,0) scale(1) <-> translate(26px,20px) scale(1.1), ease-in-out; the second runs reversed.
        float drift = 0f;
        if (time > 0) {
            float phase = (time % (PERIOD[i] * 2)) / (float) PERIOD[i];
            float linear = phase <= 1f ? phase : 2f - phase;
            drift = CubicBezierInterpolator.EASE_BOTH.getInterpolation(i == 1 ? 1f - linear : linear);
        } else if (i == 1) {
            drift = 1f;
        }
        // Living light: lean towards the finger, swell and brighten with energy.
        float reach = AuroraEnergy.lean(i) * (1f + energy);
        float cx = (box[0] + radius + 26 * drift + pointerX * reach) * unit;
        float cy;
        if (i == 2) {
            cy = stageHeight - (DESIGN_HEIGHT - box[1] - radius - 20 * drift - pointerY * reach) * unit;
        } else {
            cy = (box[1] + radius + 20 * drift + pointerY * reach) * unit;
        }
        float r = extent * (1f + 0.1f * drift) * (1f + 0.24f * energy) * unit;
        matrix.setScale(r, r);
        matrix.postTranslate(cx, cy);
        shaders[i].setLocalMatrix(matrix);
        Paint paint = lightPaints[i];
        paint.setAlpha(Math.round(255 * Math.min(1f, OPACITY[i] + 0.12f * energy)));
        canvas.drawCircle(cx, cy, r, paint);
    }

    // linear-gradient(to bottom, rgba(11,11,16,.08) 0, .5 130px, .86 300px, .9 620px, .62 844px)
    private void drawShade(Canvas canvas, float unit, int width, int height, float top, float bottom) {
        if (shadeHeight != height || shadeWidth != width) {
            shadeHeight = height;
            shadeWidth = width;
            float[] at = {0, 130 * unit, 300 * unit, height - 224 * unit, height};
            float[] stops = new float[at.length];
            for (int i = 0; i < at.length; i++) {
                float p = Math.max(0f, Math.min(1f, at[i] / height));
                stops[i] = i == 0 ? p : Math.max(stops[i - 1], p);
            }
            shadePaint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{withAlpha(BASE, 0.08f), withAlpha(BASE, 0.5f), withAlpha(BASE, 0.86f), withAlpha(BASE, 0.9f), withAlpha(BASE, 0.62f)},
                    stops, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, top, width, bottom, shadePaint);
    }

    private void snapshot() {
        long now = SystemClock.elapsedRealtime();
        if (frameTime != 0 && AuroraTheme.canAnimate()) {
            float k = 1f - (float) Math.exp(-Math.min(500, now - frameTime) / 120f);
            shownHeight += (targetHeight - shownHeight) * k;
            if (Math.abs(targetHeight - shownHeight) < 1f) {
                shownHeight = targetHeight;
            }
        } else {
            shownHeight = targetHeight;
        }
        frameTime = now;
        energy = AuroraEnergy.energy();
        pointerX = AuroraEnergy.pointerX();
        pointerY = AuroraEnergy.pointerY();
    }

    // One clock for every view that shows the stage, so the header and the background never drift apart.
    private void onTick() {
        ticking = false;
        snapshot();
        checkAvatar();
        invalidateViews();
    }

    private void invalidateViews() {
        for (int i = 0; i < views.size(); i++) {
            views.get(i).invalidate();
        }
    }

    /** Forgets every view and stops the clock (the chat closed). */
    public void release() {
        views.clear();
        avatar = null;
        avatarBitmap = null;
        AndroidUtilities.cancelRunOnUIThread(tick);
        ticking = false;
    }

    /** Stops redrawing {@code view}; the stage stops ticking once no view shows it. */
    public void detach(View view) {
        views.remove(view);
        if (views.isEmpty()) {
            AndroidUtilities.cancelRunOnUIThread(tick);
            ticking = false;
        }
    }

    /**
     * Three glowing colours from an avatar photo: the average of its top left,
     * top right and lower half, made vivid enough to glow on the dark base.
     * Returns null for a grey or unreadable photo, so the avatar colour stays.
     */
    static int[] paletteFromPhoto(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        final int size = 6;
        int[] pixels = new int[size * size];
        try {
            Bitmap small = Bitmap.createScaledBitmap(bitmap, size, size, true);
            small.getPixels(pixels, 0, size, 0, 0, size, size);
            if (small != bitmap) {
                small.recycle();
            }
        } catch (Throwable e) {
            return null;
        }
        long[][] sums = new long[3][4];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int c = pixels[y * size + x];
                if (Color.alpha(c) < 128) {
                    continue;
                }
                int region = y < size / 2 ? (x < size / 2 ? 0 : 1) : 2;
                sums[region][0] += Color.red(c);
                sums[region][1] += Color.green(c);
                sums[region][2] += Color.blue(c);
                sums[region][3]++;
            }
        }
        int[] colors = new int[3];
        float saturation = 0f;
        float[] hsv = new float[3];
        for (int i = 0; i < 3; i++) {
            if (sums[i][3] == 0) {
                return null;
            }
            int c = Color.rgb((int) (sums[i][0] / sums[i][3]), (int) (sums[i][1] / sums[i][3]), (int) (sums[i][2] / sums[i][3]));
            Color.colorToHSV(c, hsv);
            saturation += hsv[1] / 3f;
            hsv[1] = Math.max(0.55f, Math.min(0.9f, hsv[1] * 1.4f));
            hsv[2] = Math.max(0.6f, Math.min(0.88f, hsv[2]));
            colors[i] = Color.HSVToColor(hsv);
        }
        return saturation < 0.12f ? null : colors;
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (color & 0x00ffffff);
    }
}

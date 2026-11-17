package com.radolyn.ayugram.ui.aurora;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.Arrays;

/**
 * The drifting header aurora shared by the chat list and chat headers: three
 * soft violet, blue and teal lights over #17132A that drift slowly (10, 12,
 * 14 s, back and forth). Positions are fractions of the header box.
 * Aurora Flow's living light ({@link AuroraEnergy}) leans them towards the
 * finger and swells them when something happens.
 */
public final class AuroraLights {

    /** How often to redraw while the lights drift (~30 fps is plenty for slow, blurry motion). */
    public static final long FRAME_DELAY_MS = 32;

    private final Paint basePaint = new Paint();
    private final Light[] lights = {
            new Light(0, AuroraTheme.VIOLET, 0.9f, 0.00f, 0.00f, 1.10f, 0.85f, 10_000, false),
            new Light(1, AuroraTheme.BLUE, 0.85f, 1.00f, 0.12f, 0.90f, 0.80f, 12_000, true),
            new Light(2, AuroraTheme.TEAL, 0.7f, 0.55f, 1.10f, 0.95f, 0.70f, 14_000, false),
    };
    private final long startTime = SystemClock.elapsedRealtime();

    // Aurora Flow: the lights can take a chat's colours (1.3s ease), e.g. the one you just tapped.
    private static final long COLORS_MS = 1300;
    private static final int[] DEFAULT_COLORS = {AuroraTheme.VIOLET, AuroraTheme.BLUE, AuroraTheme.TEAL};
    private int[] fromColors;
    private int[] toColors = DEFAULT_COLORS;
    private int fromBase;
    private int toBase = AuroraTheme.HEADER_BASE;
    private long colorsStart;

    /**
     * Gives the lights {@code colors} (three: violet, blue and teal slots), or
     * the theme's own with null; the base takes the first colour, darkened.
     */
    public void setColors(int[] colors, boolean animated) {
        int[] target = colors != null && colors.length >= 3 ? colors.clone() : DEFAULT_COLORS;
        int targetBase = colors != null && colors.length >= 3 ? mix(colors[0], 0xff0b0b10, 0.86f) : AuroraTheme.HEADER_BASE;
        if (Arrays.equals(target, toColors) && targetBase == toBase) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (animated && AuroraTheme.canAnimate()) {
            int[] current = new int[3];
            fromBase = currentColors(now, current);
            fromColors = current;
            colorsStart = now;
        } else {
            fromColors = null;
        }
        toColors = target;
        toBase = targetBase;
    }

    /** Fills {@code out} with the lights' colours right now and returns the base colour. */
    private int currentColors(long now, int[] out) {
        float t = fromColors == null ? 1f : Math.min(1f, (now - colorsStart) / (float) COLORS_MS);
        if (t >= 1f) {
            fromColors = null;
            System.arraycopy(toColors, 0, out, 0, 3);
            return toBase;
        }
        float e = CubicBezierInterpolator.DEFAULT.getInterpolation(t);
        for (int i = 0; i < 3; i++) {
            out[i] = mix(fromColors[i], toColors[i], e);
        }
        return mix(fromBase, toBase, e);
    }

    private final int[] frameColors = new int[3];

    static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xff) + (((to >> 16) & 0xff) - ((from >> 16) & 0xff)) * t);
        int g = Math.round(((from >> 8) & 0xff) + (((to >> 8) & 0xff) - ((from >> 8) & 0xff)) * t);
        int b = Math.round((from & 0xff) + ((to & 0xff) - (from & 0xff)) * t);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Fills the box with the header base and the lights.
     *
     * @return true when the lights are drifting and the caller should redraw again in {@link #FRAME_DELAY_MS}
     */
    public boolean draw(Canvas canvas, float left, float top, float width, float height, int alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0) {
            return false;
        }
        boolean animate = AuroraTheme.canAnimate();
        long now = SystemClock.elapsedRealtime();
        long time = animate ? now - startTime : 0;
        int base = currentColors(now, frameColors);
        basePaint.setColor(withAlpha(base, alpha));
        canvas.drawRect(left, top, left + width, top + height, basePaint);
        float energy = AuroraEnergy.energy();
        float pointerX = AuroraEnergy.pointerX();
        float pointerY = AuroraEnergy.pointerY();
        for (int i = 0; i < lights.length; i++) {
            lights[i].setColor(frameColors[i]);
            lights[i].draw(canvas, left, top, width, height, time, alpha, energy, pointerX, pointerY);
        }
        return animate;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static final class Light {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final float lean;
        private final float opacity;
        private int color;
        private final float centerX;
        private final float centerY;
        private final float radiusX;
        private final float radiusY;
        private final long period;
        private final boolean reverse;

        Light(int index, int color, float opacity, float centerX, float centerY, float radiusX, float radiusY, long period, boolean reverse) {
            this.lean = AuroraEnergy.lean(index);
            this.opacity = opacity;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.period = period;
            this.reverse = reverse;
            setColor(color);
        }

        void setColor(int value) {
            if (paint.getShader() != null && color == value) {
                return;
            }
            color = value;
            // Full strength here; the light's opacity goes on the paint so energy can raise it.
            int core = withAlpha(value, 255);
            int mid = withAlpha(value, (int) (255 * 0.45f));
            int clear = withAlpha(value, 0);
            // A unit circle; the matrix stretches it into the ellipse each frame.
            paint.setShader(new RadialGradient(0, 0, 1, new int[]{core, mid, clear}, new float[]{0f, 0.32f, 0.62f}, Shader.TileMode.CLAMP));
        }

        void draw(Canvas canvas, float left, float top, float width, float height, long time, int alpha, float energy, float pointerX, float pointerY) {
            float drift = ease(pingPong(time, period));
            if (reverse) {
                drift = 1f - drift;
            }
            // Living light: scale(1 + energy * .24), opacity + energy * .12, and a lean of
            // translate(px * f, py * f) * (1 + energy) towards the finger.
            final float scale = (1f + 0.12f * drift) * (1f + 0.24f * energy);
            final float rx = Math.max(1f, width * radiusX * scale);
            final float ry = Math.max(1f, height * radiusY * scale);
            final float reach = AndroidUtilities.dp(lean) * (1f + energy);
            matrix.setScale(rx, ry);
            // The design drifts 30 x 24 px on a much taller header; keep the vertical drift proportional.
            matrix.postTranslate(left + width * centerX + AndroidUtilities.dp(30) * drift + pointerX * reach,
                    top + height * (centerY + 0.08f * drift) + pointerY * reach);
            paint.getShader().setLocalMatrix(matrix);
            paint.setAlpha((int) (alpha * Math.min(1f, opacity + 0.12f * energy)));
            canvas.drawRect(left, top, left + width, top + height, paint);
        }

        private static float pingPong(long time, long period) {
            float t = (time % (2 * period)) / (float) period;
            return t <= 1f ? t : 2f - t;
        }

        private static float ease(float t) {
            return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
        }
    }
}

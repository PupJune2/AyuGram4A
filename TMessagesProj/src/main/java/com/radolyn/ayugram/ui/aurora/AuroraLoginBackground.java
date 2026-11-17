package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * The Aurora Motion sign-in backdrop: three big blurred violet, blue and teal
 * lights over #0B0B10 that drift slowly and glide to a new spot at each step
 * (phone, code, password, done), a fade to solid dark towards the bottom, and
 * the design's top row: the app's logo and name on the left, the step dots on
 * the right. Sizes follow the design's 390-wide frame, scaled to the screen
 * width.
 */
public class AuroraLoginBackground extends View {

    public static final int STEP_PHONE = 0;
    public static final int STEP_CODE = 1;
    public static final int STEP_PASSWORD = 2;
    public static final int STEP_DONE = 3;
    private static final int DOT_COUNT = 3;

    /** The top row's vertical centre, below the status bar; the back button sits on the same line. */
    public static final int ROW_CENTER_DP = 32;

    private static final long FRAME_DELAY_MS = 32;
    private static final long STEP_MS = 1400;
    private static final long DOTS_MS = 600;
    private static final long SHIFT_MS = 300;
    private static final float DESIGN_WIDTH_DP = 390f;
    private static final int BASE = 0xff0b0b10;
    private static final int DOT_ON = 0xffc4b5fd;
    private static final int DOT_OFF = 0x40ffffff;

    // transition: transform 1.4s cubic-bezier(.4, 0, .2, 1)
    private static final CubicBezierInterpolator STEP_EASE = new CubicBezierInterpolator(0.4, 0.0, 0.2, 1.0);
    // transition: width .6s cubic-bezier(.3, .7, .3, 1.2)
    private static final CubicBezierInterpolator DOT_EASE = new CubicBezierInterpolator(0.3, 0.7, 0.3, 1.2);

    private final Paint basePaint = new Paint();
    private final Paint shadePaint = new Paint();
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dotRect = new RectF();
    private final Light[] lights;
    private final long startTime = SystemClock.elapsedRealtime();

    private final Paint logoFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubble = new Path();
    private final Path sparkle = new Path();
    private final RectF logoRect = new RectF();
    private String appName;

    private View backButton;
    private View[] cornerViews;
    // How far the logo is pushed right for the back button (0..1), eased towards its target.
    private float backShift = -1f;
    private long lastShiftTime;

    private int step;
    private int fromStep;
    private long stepStart;
    private int shadeHeight;

    public AuroraLoginBackground(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        basePaint.setColor(BASE);
        // Positions, sizes and per-step moves are the design's, in dp of a 390dp-wide screen:
        // {left, top, size, blur, opacity, drift period, reversed}, then {dx, dy, scale} per step.
        lights = new Light[]{
                new Light(AuroraTheme.VIOLET, -90, -70, 340, 64, 0.85f, 9_000, false,
                        new float[][]{{0, 0, 1f}, {170, 30, 1f}, {60, 170, 1.1f}, {110, 70, 1.2f}}),
                new Light(AuroraTheme.BLUE, 200, 110, 300, 64, 0.8f, 11_000, true,
                        new float[][]{{0, 0, 1f}, {-170, -30, 1f}, {-50, 120, 1f}, {-90, 10, 1f}}),
                new Light(AuroraTheme.TEAL, 10, 300, 280, 70, 0.6f, 13_000, false,
                        new float[][]{{0, 0, 1f}, {90, -80, 1f}, {-30, -170, 1f}, {50, -80, 1.15f}}),
        };

        // A frosted 40dp square: white at 16% with a 28% rim.
        logoFill.setColor(0x29ffffff);
        logoStroke.setColor(0x47ffffff);
        logoStroke.setStyle(Paint.Style.STROKE);
        logoStroke.setStrokeWidth(Math.max(1, AndroidUtilities.dp(1)));
        iconStroke.setColor(0xffffffff);
        iconStroke.setStyle(Paint.Style.STROKE);
        iconStroke.setStrokeJoin(Paint.Join.ROUND);
        iconStroke.setStrokeCap(Paint.Cap.ROUND);
        iconFill.setColor(0xffffffff);
        namePaint.setColor(0xfff5f5f7);
        namePaint.setTextSize(AndroidUtilities.dp(18));
        namePaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        namePaint.setLetterSpacing(-0.01f);

        // The chat bubble with a sparkle, in a 24-unit box:
        // M5 4h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7l-5 4v-4H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z
        RectF arc = new RectF();
        bubble.moveTo(5, 4);
        bubble.lineTo(19, 4);
        arc.set(17, 4, 21, 8);
        bubble.arcTo(arc, -90, 90);
        bubble.lineTo(21, 15);
        arc.set(17, 13, 21, 17);
        bubble.arcTo(arc, 0, 90);
        bubble.lineTo(12, 17);
        bubble.lineTo(7, 21);
        bubble.lineTo(7, 17);
        bubble.lineTo(5, 17);
        arc.set(3, 13, 7, 17);
        bubble.arcTo(arc, 90, 90);
        bubble.lineTo(3, 6);
        arc.set(3, 4, 7, 8);
        bubble.arcTo(arc, 180, 90);
        bubble.close();
        // M12 7.5l1 2.2 2.2 1-2.2 1-1 2.2-1-2.2-2.2-1 2.2-1z
        sparkle.moveTo(12, 7.5f);
        sparkle.lineTo(13, 9.7f);
        sparkle.lineTo(15.2f, 10.7f);
        sparkle.lineTo(13, 11.7f);
        sparkle.lineTo(12, 13.9f);
        sparkle.lineTo(11, 11.7f);
        sparkle.lineTo(8.8f, 10.7f);
        sparkle.lineTo(11, 9.7f);
        sparkle.close();
    }

    /**
     * Shows the logo and {@code name} in the top row. The logo moves aside
     * while {@code back} is shown, and the dots move aside for any of
     * {@code corner} (the proxy and progress icons in the top right).
     */
    public void setHeader(String name, View back, View... corner) {
        appName = name;
        backButton = back;
        cornerViews = corner;
        invalidate();
    }

    /**
     * Moves the lights and the step dots to {@code newStep} (one of the STEP_ constants).
     */
    public void setStep(int newStep, boolean animated) {
        newStep = Math.max(STEP_PHONE, Math.min(STEP_DONE, newStep));
        if (newStep == step) {
            return;
        }
        if (animated && AuroraTheme.canAnimate() && isAttachedToWindow()) {
            // Start from where things are now, even mid-way through another move.
            long now = SystemClock.elapsedRealtime();
            for (Light light : lights) {
                light.captureFrom(stepProgress(now), fromStep, step);
            }
            fromStep = step;
            stepStart = now;
        } else {
            for (Light light : lights) {
                light.clearFrom();
            }
            fromStep = newStep;
            stepStart = 0;
        }
        step = newStep;
        invalidate();
    }

    private float stepProgress(long now) {
        if (stepStart == 0) {
            return 1f;
        }
        return Math.min(1f, (now - stepStart) / (float) STEP_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.drawRect(0, 0, width, height, basePaint);

        boolean animate = AuroraTheme.canAnimate();
        long now = SystemClock.elapsedRealtime();
        float scale = width / (float) AndroidUtilities.dp(DESIGN_WIDTH_DP);
        float rawStep = animate ? stepProgress(now) : 1f;
        float move = STEP_EASE.getInterpolation(rawStep);
        for (Light light : lights) {
            light.draw(canvas, scale, step, move, animate ? now - startTime : 0);
        }

        // linear-gradient(to bottom, transparent 30%, rgba(11,11,16,.86) 58%, #0B0B10 72%)
        if (shadeHeight != height) {
            shadeHeight = height;
            shadePaint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{BASE & 0x00ffffff, 0xdb0b0b10, BASE, BASE},
                    new float[]{0.30f, 0.58f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, height * 0.30f, width, height, shadePaint);

        float rowCenter = (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight) + AndroidUtilities.dp(ROW_CENTER_DP);
        boolean shifting = drawLogoRow(canvas, rowCenter, animate ? now : 0);
        float dotsProgress = drawDots(canvas, width, rowCenter, animate ? now : 0);

        if (animate) {
            if (rawStep < 1f || dotsProgress < 1f || shifting) {
                postInvalidateOnAnimation();
            } else {
                postInvalidateDelayed(FRAME_DELAY_MS);
            }
        }
    }

    /**
     * The 40dp frosted logo square with the bubble icon, then the app name.
     *
     * @return true while the row is still sliding to make room for the back button
     */
    private boolean drawLogoRow(Canvas canvas, float centerY, long now) {
        if (appName == null) {
            return false;
        }
        float target = backButton != null && backButton.getVisibility() == VISIBLE ? backButton.getAlpha() : 0f;
        if (backShift < 0 || now == 0) {
            backShift = target;
        } else if (backShift != target) {
            float dt = Math.min(SHIFT_MS, Math.max(0, now - lastShiftTime));
            float stepSize = dt / SHIFT_MS;
            backShift = target > backShift ? Math.min(target, backShift + stepSize) : Math.max(target, backShift - stepSize);
        }
        lastShiftTime = now;
        float eased = CubicBezierInterpolator.EASE_OUT.getInterpolation(backShift);

        if (backButton != null && eased > 0) {
            // A glass circle behind the back arrow, like the chat header's.
            float cx = backButton.getX() + backButton.getWidth() / 2f;
            float cy = backButton.getY() + backButton.getHeight() / 2f;
            AuroraTheme.drawGlassCircle(canvas, cx, cy, AndroidUtilities.dp(20), eased);
        }

        float size = AndroidUtilities.dp(40);
        float left = AndroidUtilities.dp(24) + AndroidUtilities.dp(36) * eased;
        logoRect.set(left, centerY - size / 2f, left + size, centerY + size / 2f);
        float radius = AndroidUtilities.dp(14);
        canvas.drawRoundRect(logoRect, radius, radius, logoFill);
        float inset = logoStroke.getStrokeWidth() / 2f;
        logoRect.inset(inset, inset);
        canvas.drawRoundRect(logoRect, radius - inset, radius - inset, logoStroke);

        float icon = AndroidUtilities.dp(22);
        float unit = icon / 24f;
        canvas.save();
        canvas.translate(left + (size - icon) / 2f, centerY - icon / 2f);
        canvas.scale(unit, unit);
        iconStroke.setStrokeWidth(1.7f);
        canvas.drawPath(bubble, iconStroke);
        canvas.drawPath(sparkle, iconFill);
        canvas.restore();

        float baseline = centerY - (namePaint.descent() + namePaint.ascent()) / 2f;
        canvas.drawText(appName, left + size + AndroidUtilities.dp(10), baseline, namePaint);
        return backShift != target;
    }

    /**
     * Pills of 5dp height and 5dp gap at the row's right end: the current
     * step is 26dp wide, the others 8dp; done and current steps are lavender.
     */
    private float drawDots(Canvas canvas, int width, float centerY, long now) {
        float raw = now == 0 || stepStart == 0 ? 1f : Math.min(1f, (now - stepStart) / (float) DOTS_MS);
        float t = DOT_EASE.getInterpolation(raw);
        float gap = AndroidUtilities.dp(5);
        float dotHeight = AndroidUtilities.dp(5);
        float[] widths = new float[DOT_COUNT];
        float total = gap * (DOT_COUNT - 1);
        for (int i = 0; i < DOT_COUNT; i++) {
            float from = AndroidUtilities.dp(i == fromStep ? 26 : 8);
            float to = AndroidUtilities.dp(i == step ? 26 : 8);
            widths[i] = Math.max(dotHeight, from + (to - from) * t);
            total += widths[i];
        }
        // Room for the proxy or progress icon in the top right corner when one shows.
        float corner = 0f;
        if (cornerViews != null) {
            for (View view : cornerViews) {
                if (view != null && view.getVisibility() == VISIBLE) {
                    corner = Math.max(corner, view.getAlpha());
                }
            }
        }
        float right = width - AndroidUtilities.dp(24) - AndroidUtilities.dp(36) * corner;
        float top = centerY - dotHeight / 2f;
        float x = right - total;
        float colorT = Math.max(0f, Math.min(1f, raw));
        for (int i = 0; i < DOT_COUNT; i++) {
            int from = i <= fromStep ? DOT_ON : DOT_OFF;
            int to = i <= step ? DOT_ON : DOT_OFF;
            dotPaint.setColor(blend(from, to, colorT));
            dotRect.set(x, top, x + widths[i], top + dotHeight);
            canvas.drawRoundRect(dotRect, dotHeight / 2f, dotHeight / 2f, dotPaint);
            x += widths[i] + gap;
        }
        return raw;
    }

    private static int blend(int from, int to, float t) {
        if (t >= 1f) {
            return to;
        }
        int a = (int) (((from >>> 24) & 0xff) + (((to >>> 24) & 0xff) - ((from >>> 24) & 0xff)) * t);
        int r = (int) (((from >> 16) & 0xff) + (((to >> 16) & 0xff) - ((from >> 16) & 0xff)) * t);
        int g = (int) (((from >> 8) & 0xff) + (((to >> 8) & 0xff) - ((from >> 8) & 0xff)) * t);
        int b = (int) ((from & 0xff) + ((to & 0xff) - (from & 0xff)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class Light {
        // ease-in-out for the drift, like the design's amDrift keyframes.
        private static final CubicBezierInterpolator DRIFT_EASE = CubicBezierInterpolator.EASE_BOTH;

        final float left;
        final float top;
        final float radius;
        final float extent;
        final long period;
        final boolean reverse;
        final float[][] steps;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix matrix = new Matrix();
        final RadialGradient shader;

        // Where the light started its current step move, or null to start from the previous step's spot.
        float[] from;

        Light(int color, float left, float top, float size, float blur, float opacity, long period, boolean reverse, float[][] steps) {
            this.left = left;
            this.top = top;
            this.radius = size / 2f;
            this.period = period;
            this.reverse = reverse;
            this.steps = steps;
            // A disc blurred by a Gaussian of this sigma: solid inside, half strength at its
            // edge, gone about 2.5 sigma out.
            extent = radius + blur * 2.5f;
            float r = radius / extent;
            float s = blur / extent;
            shader = new RadialGradient(0, 0, 1f,
                    new int[]{
                            withAlpha(color, opacity),
                            withAlpha(color, opacity * 0.84f),
                            withAlpha(color, opacity * 0.5f),
                            withAlpha(color, opacity * 0.16f),
                            withAlpha(color, 0f)
                    },
                    new float[]{0f, Math.max(0f, r - s), r, Math.min(1f, r + s), 1f},
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }

        void captureFrom(float rawProgress, int fromStep, int toStep) {
            float t = STEP_EASE.getInterpolation(rawProgress);
            float[] a = from != null ? from : steps[fromStep];
            float[] b = steps[toStep];
            from = new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        }

        void clearFrom() {
            from = null;
        }

        void draw(Canvas canvas, float scale, int step, float move, long time) {
            float[] a = from != null ? from : steps[step];
            float[] b = steps[step];
            float dx = a[0] + (b[0] - a[0]) * move;
            float dy = a[1] + (b[1] - a[1]) * move;
            float stepScale = a[2] + (b[2] - a[2]) * move;

            // translate(0,0) scale(1) <-> translate(24px,18px) scale(1.08), alternating.
            float drift = 0f;
            if (time > 0) {
                float phase = (time % (period * 2)) / (float) period;
                float linear = phase <= 1f ? phase : 2f - phase;
                drift = DRIFT_EASE.getInterpolation(reverse ? 1f - linear : linear);
            } else if (reverse) {
                drift = 1f;
            }

            float unit = AndroidUtilities.dp(1) * scale;
            float cx = (left + radius + dx + 24 * drift) * unit;
            float cy = (top + radius + dy + 18 * drift) * unit;
            float r = extent * stepScale * (1f + 0.08f * drift) * unit;
            matrix.setScale(r, r);
            matrix.postTranslate(cx, cy);
            shader.setLocalMatrix(matrix);
            canvas.drawCircle(cx, cy, r, paint);
        }

        private static int withAlpha(int color, float alpha) {
            return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (color & 0x00ffffff);
        }
    }
}

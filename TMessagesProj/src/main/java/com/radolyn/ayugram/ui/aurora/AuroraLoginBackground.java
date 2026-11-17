package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * The Aurora Motion sign-in backdrop: three big blurred violet, blue and teal
 * lights over #0B0B10 that drift slowly and glide to a new spot at each step
 * (phone, code, password), a fade to solid dark towards the bottom, and three
 * step dots at the top. Sizes follow the design's 390-wide frame, scaled to the
 * screen width.
 */
public class AuroraLoginBackground extends View {

    public static final int STEP_PHONE = 0;
    public static final int STEP_CODE = 1;
    public static final int STEP_PASSWORD = 2;
    private static final int STEP_COUNT = 3;

    private static final long FRAME_DELAY_MS = 32;
    private static final long STEP_MS = 1400;
    private static final long DOTS_MS = 600;
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
                        new float[][]{{0, 0, 1f}, {170, 30, 1f}, {60, 170, 1.1f}}),
                new Light(AuroraTheme.BLUE, 200, 110, 300, 64, 0.8f, 11_000, true,
                        new float[][]{{0, 0, 1f}, {-170, -30, 1f}, {-50, 120, 1f}}),
                new Light(AuroraTheme.TEAL, 10, 300, 280, 70, 0.6f, 13_000, false,
                        new float[][]{{0, 0, 1f}, {90, -80, 1f}, {-30, -170, 1f}}),
        };
    }

    /**
     * Moves the lights and the step dots to {@code newStep} (one of the STEP_ constants).
     */
    public void setStep(int newStep, boolean animated) {
        newStep = Math.max(0, Math.min(STEP_COUNT - 1, newStep));
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

        float dotsProgress = drawDots(canvas, width, animate ? now : 0);

        if (animate) {
            if (rawStep < 1f || dotsProgress < 1f) {
                postInvalidateOnAnimation();
            } else {
                postInvalidateDelayed(FRAME_DELAY_MS);
            }
        }
    }

    /**
     * Pills of 5dp height and 5dp gap: the current step is 26dp wide, the
     * others 8dp; done and current steps are lavender.
     */
    private float drawDots(Canvas canvas, int width, long now) {
        float raw = now == 0 || stepStart == 0 ? 1f : Math.min(1f, (now - stepStart) / (float) DOTS_MS);
        float t = DOT_EASE.getInterpolation(raw);
        float gap = AndroidUtilities.dp(5);
        float dotHeight = AndroidUtilities.dp(5);
        float[] widths = new float[STEP_COUNT];
        float total = gap * (STEP_COUNT - 1);
        for (int i = 0; i < STEP_COUNT; i++) {
            float from = AndroidUtilities.dp(i == fromStep ? 26 : 8);
            float to = AndroidUtilities.dp(i == step ? 26 : 8);
            widths[i] = Math.max(dotHeight, from + (to - from) * t);
            total += widths[i];
        }
        // Centred on the back button's row.
        float top = (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight) + AndroidUtilities.dp(16 + 16) - dotHeight / 2f;
        float x = (width - total) / 2f;
        float colorT = Math.max(0f, Math.min(1f, raw));
        for (int i = 0; i < STEP_COUNT; i++) {
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

package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Draws the Aurora Motion header behind the chat list action bar and folder
 * tabs: three soft violet, blue and teal lights over a deep violet base that
 * slowly drift, with rounded bottom corners.
 * <p>
 * The host decides where the header is via {@link #setHeader(float, float, float)};
 * the view itself is full-size, transparent everywhere else and never takes touches.
 */
public class AuroraHeaderView extends View {

    private static final long FRAME_DELAY_MS = 32;

    private final Paint basePaint = new Paint();
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cornerPath = new Path();
    private final Light[] lights;
    private final long startTime = SystemClock.elapsedRealtime();

    private float headerTop;
    private float headerHeight;
    private float headerAlpha;
    private int cornersWidth = -1;
    private float cornersBottom = -1;
    private float cornersRadius = -1;

    public AuroraHeaderView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Positions are fractions of the header; the drift is in dp, matching the design.
        lights = new Light[]{
                new Light(AuroraTheme.VIOLET, 0.9f, 0.00f, 0.00f, 1.10f, 0.85f, 10_000, false),
                new Light(AuroraTheme.BLUE, 0.85f, 1.00f, 0.12f, 0.90f, 0.80f, 12_000, true),
                new Light(AuroraTheme.TEAL, 0.7f, 0.55f, 1.10f, 0.95f, 0.70f, 14_000, false),
        };
    }

    /**
     * @param top    header top in the parent's coordinates
     * @param height header height (action bar plus folder tabs)
     * @param alpha  0 hides the aurora (search, selection mode), 1 shows it fully
     */
    public void setHeader(float top, float height, float alpha) {
        if (headerTop != top || headerHeight != height || headerAlpha != alpha) {
            headerTop = top;
            headerHeight = height;
            headerAlpha = alpha;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Restart the drift when the chat list comes back into view.
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            invalidate();
        }
    }

    public boolean isAuroraVisible() {
        return headerAlpha > 0f && headerHeight > 0f && AuroraTheme.isActive();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isAuroraVisible()) {
            return;
        }
        final int width = getMeasuredWidth();
        if (width <= 0) {
            return;
        }
        final float top = headerTop - getTop();
        final float bottom = top + headerHeight;
        final int alpha = (int) (255 * Math.min(1f, headerAlpha));
        final boolean animate = AuroraTheme.canAnimate();
        final long time = animate ? SystemClock.elapsedRealtime() - startTime : 0;

        canvas.save();
        canvas.clipRect(0, top, width, bottom);

        basePaint.setColor(withAlpha(AuroraTheme.HEADER_BASE, alpha));
        canvas.drawRect(0, top, width, bottom, basePaint);
        for (Light light : lights) {
            light.draw(canvas, width, top, headerHeight, time, alpha);
        }

        drawCorners(canvas, width, bottom, alpha);
        canvas.restore();

        if (animate) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }

    // Masks the bottom corners with the list background so the header reads as a rounded card.
    private void drawCorners(Canvas canvas, int width, float bottom, int alpha) {
        final float r = Math.min(AndroidUtilities.dp(24), headerHeight / 2f);
        if (cornersWidth != width || cornersBottom != bottom || cornersRadius != r) {
            cornersWidth = width;
            cornersBottom = bottom;
            cornersRadius = r;
            cornerPath.reset();
            cornerPath.moveTo(0, bottom);
            cornerPath.lineTo(0, bottom - r);
            cornerPath.arcTo(0, bottom - 2 * r, 2 * r, bottom, 180, -90, false);
            cornerPath.close();
            cornerPath.moveTo(width, bottom);
            cornerPath.lineTo(width, bottom - r);
            cornerPath.arcTo(width - 2 * r, bottom - 2 * r, width, bottom, 0, 90, false);
            cornerPath.close();
        }
        cornerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cornerPaint.setAlpha(alpha);
        canvas.drawPath(cornerPath, cornerPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static final class Light {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final float centerX;
        private final float centerY;
        private final float radiusX;
        private final float radiusY;
        private final long period;
        private final boolean reverse;

        Light(int color, float opacity, float centerX, float centerY, float radiusX, float radiusY, long period, boolean reverse) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.period = period;
            this.reverse = reverse;
            int core = withAlpha(color, (int) (255 * opacity));
            int mid = withAlpha(color, (int) (255 * opacity * 0.45f));
            int clear = withAlpha(color, 0);
            // A unit circle; the matrix stretches it into the ellipse each frame.
            paint.setShader(new RadialGradient(0, 0, 1, new int[]{core, mid, clear}, new float[]{0f, 0.32f, 0.62f}, Shader.TileMode.CLAMP));
        }

        void draw(Canvas canvas, int width, float top, float height, long time, int alpha) {
            float drift = ease(pingPong(time, period));
            if (reverse) {
                drift = 1f - drift;
            }
            final float scale = 1f + 0.12f * drift;
            final float rx = Math.max(1f, width * radiusX * scale);
            final float ry = Math.max(1f, height * radiusY * scale);
            matrix.setScale(rx, ry);
            // The design drifts 30 x 24 px on a much taller header; keep the vertical drift proportional.
            matrix.postTranslate(width * centerX + AndroidUtilities.dp(30) * drift, top + height * (centerY + 0.08f * drift));
            paint.getShader().setLocalMatrix(matrix);
            paint.setAlpha(alpha);
            canvas.drawRect(0, top, width, top + height, paint);
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

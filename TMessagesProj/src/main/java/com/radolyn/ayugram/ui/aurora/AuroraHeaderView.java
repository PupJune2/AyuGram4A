package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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

    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cornerPath = new Path();
    private final AuroraLights lights = new AuroraLights();

    private float headerTop;
    private float headerHeight;
    private float headerAlpha;
    private int cornersWidth = -1;
    private float cornersBottom = -1;
    private float cornersRadius = -1;
    private int cornerRadius = AndroidUtilities.dp(24);

    public AuroraHeaderView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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

    /** Radius of the rounded bottom corners (Aurora Motion 24dp, Aurora Flow 32dp). */
    public void setCornerRadius(int radius) {
        if (cornerRadius != radius) {
            cornerRadius = radius;
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

        canvas.save();
        canvas.clipRect(0, top, width, bottom);
        boolean animate = lights.draw(canvas, 0, top, width, headerHeight, alpha);
        drawCorners(canvas, width, bottom, alpha);
        canvas.restore();

        if (animate) {
            postInvalidateDelayed(AuroraLights.FRAME_DELAY_MS);
        }
    }

    // Masks the bottom corners with the list background so the header reads as a rounded card.
    private void drawCorners(Canvas canvas, int width, float bottom, int alpha) {
        final float r = Math.min(cornerRadius, headerHeight / 2f);
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

}

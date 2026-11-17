package com.radolyn.ayugram.ui.aurora;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Turns the sign-in screen's round next button into the design's light
 * full-width pill ("Continue →") that narrows into a circle with a spinner
 * while a request runs, and widens back when it ends.
 * <p>
 * The host keeps its own button container, click handling and spinner; this
 * only supplies the background, the label and the width change.
 */
public class AuroraLoginButton {

    public static final int HEIGHT_DP = 58;
    public static final int FILL = 0xfff5f5f7;
    public static final int INK = 0xff0b0b10;

    // transition: width .45s cubic-bezier(.4, 0, .2, 1)
    private static final CubicBezierInterpolator WIDTH_EASE = new CubicBezierInterpolator(0.4, 0.0, 0.2, 1.0);
    private static final long WIDTH_MS = 450;

    private final View container;
    private final PillDrawable background = new PillDrawable();
    private final Label label;
    private ValueAnimator animator;
    private float narrow;
    private boolean loading;

    public AuroraLoginButton(Context context, ViewGroup container, CharSequence text) {
        this.container = container;
        label = new Label(context, text);
        container.addView(label, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.setStateListAnimator(AuroraTheme.createPressAnimator(container, 0.96f));
    }

    public Drawable getBackground() {
        return background;
    }

    /**
     * @param show true narrows the pill to a circle for the spinner, false widens it back
     */
    public void setLoading(boolean show, boolean animated) {
        if (loading == show && animator == null) {
            return;
        }
        loading = show;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        float target = show ? 1f : 0f;
        if (!animated || !AuroraTheme.canAnimate()) {
            setNarrow(target);
            return;
        }
        animator = ValueAnimator.ofFloat(narrow, target);
        animator.addUpdateListener(a -> setNarrow((float) a.getAnimatedValue()));
        animator.setDuration(WIDTH_MS);
        animator.setInterpolator(WIDTH_EASE);
        animator.start();
    }

    private void setNarrow(float value) {
        narrow = value;
        // The label goes quickly as the pill starts narrowing, and comes back late as it widens.
        label.setAlpha(Math.max(0f, 1f - value * 3f));
        background.invalidateSelf();
        container.invalidate();
    }

    private class PillDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PillDrawable() {
            paint.setColor(FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float height = bounds.height();
            float width = height + (bounds.width() - height) * (1f - narrow);
            float left = bounds.centerX() - width / 2f;
            AndroidUtilities.rectTmp.set(left, bounds.top, left + width, bounds.bottom);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, height / 2f, height / 2f, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * "Continue" in 16dp medium with a right arrow 10dp after it (mirrored for RTL).
     */
    private static class Label extends View {
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final String text;

        Label(Context context, CharSequence text) {
            super(context);
            this.text = text.toString();
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            textPaint.setColor(INK);
            textPaint.setTextSize(AndroidUtilities.dp(16));
            textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            arrowPaint.setColor(INK);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(AndroidUtilities.dp(2) * 18f / 24f);
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float textWidth = textPaint.measureText(text);
            float arrowSize = AndroidUtilities.dp(18);
            float gap = AndroidUtilities.dp(10);
            float total = textWidth + gap + arrowSize;
            float x = (getWidth() - total) / 2f;
            float baseline = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            boolean rtl = LocaleController.isRTL;
            float arrowLeft = rtl ? x : x + textWidth + gap;
            float textLeft = rtl ? x + arrowSize + gap : x;
            canvas.drawText(text, textLeft, baseline, textPaint);

            // M5 12h14 M13 6l6 6-6 6 in a 24-unit box.
            float u = arrowSize / 24f;
            float top = getHeight() / 2f - arrowSize / 2f;
            arrow.reset();
            arrow.moveTo(5 * u, 12 * u);
            arrow.lineTo(19 * u, 12 * u);
            arrow.moveTo(13 * u, 6 * u);
            arrow.lineTo(19 * u, 12 * u);
            arrow.lineTo(13 * u, 18 * u);
            canvas.save();
            canvas.translate(arrowLeft, top);
            if (rtl) {
                canvas.scale(-1f, 1f, arrowSize / 2f, 0);
            }
            canvas.drawPath(arrow, arrowPaint);
            canvas.restore();
        }
    }
}

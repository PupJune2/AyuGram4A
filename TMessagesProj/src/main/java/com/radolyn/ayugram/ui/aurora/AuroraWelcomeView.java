package com.radolyn.ayugram.ui.aurora;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

/**
 * The design's last sign-in step, "You're in, Max": the headline rises in
 * word by word, a check circle pops up and its tick draws itself, then an
 * "Open my chats" pill fades up. Sits over the sign-in pages, on the same
 * backdrop.
 */
public class AuroraWelcomeView extends FrameLayout {

    // cubic-bezier(.3, .7, .3, 1.3): the design's pop, with a little overshoot.
    private static final CubicBezierInterpolator POP_EASE = new CubicBezierInterpolator(0.3, 0.7, 0.3, 1.3);

    private final LinearLayout column;
    private final AuroraHeadlineTextView headline;
    private final TextView subtitle;
    private final CheckView check;
    private final FrameLayout button;
    private boolean opened;

    public AuroraWelcomeView(Context context, CharSequence title, CharSequence subtitleText, CharSequence buttonText, Runnable onOpen) {
        super(context);
        setClickable(true);
        setFocusable(false);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int top = (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight) + AndroidUtilities.dp(30 + AuroraLoginLayout.PAGE_TOP_DP);
        column.setPadding(AndroidUtilities.dp(24), top, AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        headline = new AuroraHeadlineTextView(context);
        headline.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headline.setText(title);
        AuroraLoginLayout.styleHeadline(headline);

        subtitle = new TextView(context);
        subtitle.setText(subtitleText);
        AuroraLoginLayout.styleSubtitle(subtitle);
        subtitle.setVisibility(subtitleText == null || subtitleText.length() == 0 ? GONE : VISIBLE);

        column.addView(new Space(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(headline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, AuroraLoginLayout.startGravity(), 0, 6, 0, 0));
        column.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, AuroraLoginLayout.startGravity(), 0, 12, 36, 0));
        column.addView(new Space(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));

        check = new CheckView(context);
        column.addView(check, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL));

        button = new FrameLayout(context);
        AuroraLoginButton pill = new AuroraLoginButton(context, button, buttonText);
        button.setBackground(pill.getBackground());
        button.setContentDescription(buttonText);
        button.setOnClickListener(v -> {
            if (opened) {
                return;
            }
            opened = true;
            onOpen.run();
        });
        column.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AuroraLoginButton.HEIGHT_DP, 0, 18, 0, 0));
    }

    /** Room to keep free at the bottom, for the footnote under the pill. */
    public void setBottomInset(int bottom) {
        if (column.getPaddingBottom() != bottom) {
            column.setPadding(column.getPaddingLeft(), column.getPaddingTop(), column.getPaddingRight(), bottom);
        }
    }

    /** Taps the pill, as the back button does on this step. */
    public void open() {
        button.performClick();
    }

    /**
     * Plays the entrance: the words rise (0.7s each, 90ms apart) with the
     * subtitle fading up 0.35s in, the check pops (0.5s) and draws (0.6s
     * from 0.3s), and the pill fades up at 0.5s.
     */
    public void show() {
        if (!AuroraTheme.canAnimate()) {
            check.setProgress(1f, 1f);
            return;
        }
        headline.reveal(subtitle.getVisibility() == VISIBLE ? subtitle : null);

        check.setProgress(0f, 0f);
        ValueAnimator pop = ValueAnimator.ofFloat(0f, 1f);
        pop.addUpdateListener(a -> check.setPop((float) a.getAnimatedValue()));
        pop.setDuration(500);
        pop.start();
        ValueAnimator draw = ValueAnimator.ofFloat(0f, 1f);
        draw.addUpdateListener(a -> check.setDraw((float) a.getAnimatedValue()));
        draw.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        draw.setStartDelay(300);
        draw.setDuration(600);
        draw.start();

        button.setAlpha(0f);
        button.setTranslationY(AndroidUtilities.dp(8));
        button.animate().alpha(1f).translationY(0).setStartDelay(500).setDuration(500).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    /**
     * A 72dp circle (white at 10%, lavender rim) with a tick that draws
     * itself: M5 12.5 10 17 19 7 in a 34dp box.
     */
    private static class CheckView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tickPath = new Path();
        private final Path tickPart = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final float tickLength;
        private float pop = 1f;
        private float draw = 1f;

        CheckView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            fill.setColor(0x1affffff);
            rim.setColor(0x99c4b5fd);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1, AndroidUtilities.dp(1)));
            tick.setColor(AuroraHeadlineTextView.ACCENT);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeCap(Paint.Cap.ROUND);
            tick.setStrokeJoin(Paint.Join.ROUND);
            tick.setStrokeWidth(2.4f);
            tickPath.moveTo(5, 12.5f);
            tickPath.lineTo(10, 17);
            tickPath.lineTo(19, 7);
            measure.setPath(tickPath, false);
            tickLength = measure.getLength();
        }

        void setProgress(float pop, float draw) {
            this.pop = pop;
            this.draw = draw;
            invalidate();
        }

        void setPop(float value) {
            pop = value;
            invalidate();
        }

        void setDraw(float value) {
            draw = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // amPop: 0% scale .5, hidden; 60% scale 1.12, shown; 100% scale 1.
            float scale;
            float alpha;
            if (pop < 0.6f) {
                float t = POP_EASE.getInterpolation(pop / 0.6f);
                scale = 0.5f + (1.12f - 0.5f) * t;
                alpha = Math.min(1f, t);
            } else {
                float t = POP_EASE.getInterpolation((pop - 0.6f) / 0.4f);
                scale = 1.12f + (1f - 1.12f) * t;
                alpha = 1f;
            }
            if (alpha <= 0) {
                return;
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy);
            int layerAlpha = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            fill.setAlpha((int) (0x1a * alpha));
            rim.setAlpha((int) (0x99 * alpha));
            canvas.drawCircle(cx, cy, radius, fill);
            canvas.drawCircle(cx, cy, radius - rim.getStrokeWidth() / 2f, rim);

            // stroke-dasharray 48, offset 48 -> 0: the visible part grows 48 units over the run.
            float visible = Math.min(tickLength, 48f * draw);
            if (visible > 0) {
                tickPart.reset();
                measure.getSegment(0, visible, tickPart, true);
                float box = AndroidUtilities.dp(34);
                float unit = box / 24f;
                canvas.translate(cx - box / 2f, cy - box / 2f);
                canvas.scale(unit, unit);
                tick.setAlpha(layerAlpha);
                canvas.drawPath(tickPart, tick);
            }
            canvas.restore();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Same as the pages: hide the headline rather than cut it on a short screen.
        headline.setVisibility(headline.getTop() < column.getPaddingTop() ? INVISIBLE : VISIBLE);
    }
}

package com.radolyn.ayugram.ui.aurora;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * The design's show/hide password button: a 46dp frosted circle with an eye
 * that gets a slash drawn through it (.35s) while the password is shown.
 */
public class AuroraPasswordToggle extends View {

    public static final int SIZE_DP = 46;
    private static final int ICON = 0xffd4d4dc;
    private static final float SLASH_LENGTH = (float) Math.hypot(16, 16);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path eye = new Path();
    private final EditText field;
    private final CharSequence showLabel;
    private final CharSequence hideLabel;
    private boolean shown;
    private float slash;
    private ValueAnimator animator;

    public AuroraPasswordToggle(Context context, EditText field, CharSequence showLabel, CharSequence hideLabel) {
        super(context);
        this.field = field;
        this.showLabel = showLabel;
        this.hideLabel = hideLabel;
        fill.setColor(0x14ffffff);
        stroke.setColor(ICON);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        // M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z
        eye.moveTo(2, 12);
        eye.cubicTo(2, 12, 5.5f, 5, 12, 5);
        eye.cubicTo(18.5f, 5, 22, 12, 22, 12);
        eye.cubicTo(22, 12, 18.5f, 19, 12, 19);
        eye.cubicTo(5.5f, 19, 2, 12, 2, 12);
        eye.close();
        eye.addCircle(12, 12, 3, Path.Direction.CW);
        setContentDescription(showLabel);
        setOnClickListener(v -> toggle());
        setStateListAnimator(AuroraTheme.createPressAnimator(this, 0.9f));
    }

    private void toggle() {
        shown = !shown;
        int selection = field.getSelectionEnd();
        field.setTransformationMethod(shown ? null : PasswordTransformationMethod.getInstance());
        if (selection >= 0 && selection <= field.length()) {
            field.setSelection(selection);
        }
        setContentDescription(shown ? hideLabel : showLabel);
        if (animator != null) {
            animator.cancel();
        }
        float target = shown ? 1f : 0f;
        if (!AuroraTheme.canAnimate()) {
            slash = target;
            invalidate();
            return;
        }
        // stroke-dashoffset .35s ease
        animator = ValueAnimator.ofFloat(slash, target);
        animator.addUpdateListener(a -> {
            slash = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = AndroidUtilities.dp(SIZE_DP);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, Math.min(cx, cy), fill);

        float icon = AndroidUtilities.dp(21);
        float unit = icon / 24f;
        canvas.save();
        canvas.translate(cx - icon / 2f, cy - icon / 2f);
        canvas.scale(unit, unit);
        stroke.setStrokeWidth(1.8f);
        canvas.drawPath(eye, stroke);
        // The slash is a 24-unit dash over a 22.6-unit line, drawn from its top-left end.
        float visible = Math.min(SLASH_LENGTH, 24f * slash);
        if (visible > 0) {
            float k = visible / SLASH_LENGTH;
            canvas.drawLine(4, 4, 4 + 16 * k, 4 + 16 * k, stroke);
        }
        canvas.restore();
    }
}

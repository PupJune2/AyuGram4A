package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

/**
 * A screen headline. With another theme it is a plain TextView. Under Aurora
 * Motion its last word is lavender, it shrinks rather than break a word that
 * is too long for a line, and {@link #reveal(View)} slides the words up into
 * place one after another, as in the design's sign-in screens (0.7s each,
 * 90ms apart).
 */
public class AuroraHeadlineTextView extends TextView {

    public static final int ACCENT = 0xffdcd1ff;
    private static final float MIN_FIT_SCALE = 0.7f;
    private static final long FIRST_DELAY_MS = 50;
    private static final long STAGGER_MS = 90;
    private static final long WORD_MS = 700;
    private static final CubicBezierInterpolator SUBTITLE_EASE = CubicBezierInterpolator.DEFAULT;

    private final Path wordPath = new Path();
    private final RectF wordBounds = new RectF();
    private final ArrayList<int[]> words = new ArrayList<>();
    private CharSequence wordsFor;
    // -1: not revealing; 0: waiting for the first frame; otherwise when the words started.
    private long revealStart = -1;
    private View pendingSubtitle;

    // The size asked for, and what it was fitted to, so a long word can shrink it.
    private float wantedSizePx;
    private int fittedWidth = -1;
    private CharSequence fittedText;
    private TextPaint fitPaint;

    public AuroraHeadlineTextView(Context context) {
        super(context);
        if (AuroraTheme.isActive()) {
            setLetterSpacing(-0.03f);
        }
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        wantedSizePx = getTextSize();
        fittedWidth = -1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (AuroraTheme.isActive() && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            fitLongestWord(MeasureSpec.getSize(widthMeasureSpec) - getCompoundPaddingLeft() - getCompoundPaddingRight());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Big headlines in long languages ("Bestätigungscode") would otherwise
     * break inside a word. Scale down just enough that the longest word fits.
     */
    private void fitLongestWord(int available) {
        CharSequence text = getText();
        if (available <= 0 || wantedSizePx <= 0 || (available == fittedWidth && text == fittedText)) {
            return;
        }
        fittedWidth = available;
        fittedText = text;
        if (fitPaint == null) {
            fitPaint = new TextPaint(getPaint());
        }
        fitPaint.set(getPaint());
        fitPaint.setTextSize(wantedSizePx);
        float longest = 0;
        int length = text == null ? 0 : text.length();
        int i = 0;
        while (i < length) {
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (start < i) {
                longest = Math.max(longest, fitPaint.measureText(text, start, i));
            }
        }
        float size = wantedSizePx;
        if (longest > available) {
            size = Math.max(wantedSizePx * MIN_FIT_SCALE, wantedSizePx * available / longest);
        }
        if (Math.abs(size - getTextSize()) > 0.5f) {
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (AuroraTheme.isActive() && !TextUtils.isEmpty(text) && !(text instanceof Spanned)) {
            int end = text.length();
            while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            int start = end;
            while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
                start--;
            }
            if (start < end) {
                SpannableString highlighted = new SpannableString(text);
                highlighted.setSpan(new ForegroundColorSpan(ACCENT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text = highlighted;
            }
        }
        super.setText(text, type);
    }

    /**
     * Plays the word-by-word entrance, if Aurora is on and motion is allowed.
     * It starts on the next frame this view draws, so a screen that is still
     * sliding in doesn't miss it.
     *
     * @param subtitle a view to fade up 0.35s after the words start, or null
     */
    public void reveal(View subtitle) {
        if (!AuroraTheme.isActive() || !AuroraTheme.canAnimate()) {
            return;
        }
        revealStart = 0;
        pendingSubtitle = subtitle;
        invalidate();
    }

    /**
     * Reveals the first headline found in {@code page} and fades up the view
     * right after it (the subtitle), 0.35s later, like the design.
     */
    public static void revealIn(View page) {
        if (!AuroraTheme.isActive() || !AuroraTheme.canAnimate()) {
            return;
        }
        AuroraHeadlineTextView headline = find(page);
        if (headline == null || headline.getVisibility() != VISIBLE) {
            return;
        }
        View subtitle = null;
        if (headline.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) headline.getParent();
            int index = parent.indexOfChild(headline);
            View next = index + 1 < parent.getChildCount() ? parent.getChildAt(index + 1) : null;
            if (next instanceof TextView) {
                subtitle = next;
            }
        }
        headline.reveal(subtitle);
    }

    private static void fadeUp(View subtitle) {
        // Skipped if something else is animating it or it is hidden.
        if (subtitle.getVisibility() != VISIBLE || subtitle.getAlpha() != 1f || subtitle.getTranslationY() != 0) {
            return;
        }
        subtitle.setAlpha(0f);
        subtitle.setTranslationY(AndroidUtilities.dp(8));
        subtitle.animate().alpha(1f).translationY(0).setStartDelay(350).setDuration(600).setInterpolator(SUBTITLE_EASE).start();
    }

    private static AuroraHeadlineTextView find(View view) {
        if (view instanceof AuroraHeadlineTextView) {
            return (AuroraHeadlineTextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                AuroraHeadlineTextView found = find(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (revealStart < 0 || layout == null) {
            super.onDraw(canvas);
            return;
        }
        CharSequence text = layout.getText();
        if (wordsFor != text) {
            collectWords(text);
            wordsFor = text;
        }
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (revealStart == 0) {
            revealStart = now;
            if (pendingSubtitle != null) {
                fadeUp(pendingSubtitle);
                pendingSubtitle = null;
            }
        }
        long elapsed = now - revealStart;
        if (words.isEmpty() || elapsed >= FIRST_DELAY_MS + STAGGER_MS * (words.size() - 1) + WORD_MS) {
            revealStart = -1;
            super.onDraw(canvas);
            return;
        }

        getPaint().setColor(getCurrentTextColor());
        getPaint().drawableState = getDrawableState();

        canvas.save();
        canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop() + verticalOffset(layout));
        float pad = AndroidUtilities.dp(4);
        float sidePad = AndroidUtilities.dp(2);
        int lastLine = layout.getLineCount() - 1;
        for (int i = 0; i < words.size(); i++) {
            float t = (elapsed - FIRST_DELAY_MS - STAGGER_MS * i) / (float) WORD_MS;
            if (t <= 0f) {
                continue;
            }
            float p = AuroraTheme.RISE.getInterpolation(Math.min(1f, t));
            int[] word = words.get(i);
            int line = layout.getLineForOffset(word[0]);
            float lineTop = layout.getLineTop(line);
            float lineBottom = layout.getLineBottom(line);
            // The design gives the window 4px of room below the line; the last line can take it as is.
            float windowBottom = lineBottom + pad;
            float sourceBottom = line == lastLine ? windowBottom : lineBottom;
            layout.getSelectionPath(word[0], word[1], wordPath);
            wordPath.computeBounds(wordBounds, true);
            float left = wordBounds.left - sidePad;
            float right = wordBounds.right + sidePad;

            int count = canvas.save();
            // The word rises through a window the size of its own line (overflow: hidden)...
            canvas.clipRect(left, lineTop, right, windowBottom);
            canvas.translate(0, (1f - p) * (windowBottom - lineTop) * 1.05f);
            // ...and only its own line is drawn, so the line above never shows through.
            canvas.clipRect(left, lineTop, right, sourceBottom);
            if (p < 1f) {
                canvas.saveLayerAlpha(left, lineTop, right, sourceBottom, (int) (255 * p));
            }
            layout.draw(canvas);
            canvas.restoreToCount(count);
        }
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void collectWords(CharSequence text) {
        words.clear();
        int i = 0;
        int length = text.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (start < i) {
                words.add(new int[]{start, i});
            }
        }
    }

    private int verticalOffset(Layout layout) {
        int gravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        int boxHeight = getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int textHeight = layout.getHeight();
        if (gravity == Gravity.TOP || textHeight >= boxHeight) {
            return 0;
        }
        return gravity == Gravity.BOTTOM ? boxHeight - textHeight : (boxHeight - textHeight) / 2;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        revealStart = -1;
        pendingSubtitle = null;
    }
}

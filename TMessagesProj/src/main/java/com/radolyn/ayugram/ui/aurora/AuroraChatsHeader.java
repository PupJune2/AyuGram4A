package com.radolyn.ayugram.ui.aurora;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Aurora Flow's chat list header, drawn by the chat list's action bar:
 * <ul>
 *     <li>a frosted search pill between the menu button and the avatar
 *     (it shows "Connecting..." and the like while the app is offline);</li>
 *     <li>a big two-line greeting under the bar ("Hello Max / Stay in the
 *     loop") whose words slide up one after another the first time it shows;</li>
 *     <li>as the chat list scrolls, the greeting folds into one small line,
 *     and a grab handle under it folds or unfolds it on a tap or a swipe.</li>
 * </ul>
 * The bar reserves {@link #openExtra()} under its buttons for the greeting and
 * tells this class how far it is folded; positions are in the bar's coordinates.
 */
public final class AuroraChatsHeader {

    public interface Delegate {
        void onSearchPillClick();

        /**
         * @param direction 1 to fold (swipe up), -1 to unfold (swipe down), 0 to toggle (tap)
         */
        void onGreetingGesture(int direction);
    }

    private static final int LINE_DP = 44;
    private static final int TEXT_DP = 40;
    private static final int WORD_MS = 700;
    private static final int TEXT_COLOR = 0xfff5f5f7;

    /** Space the open greeting takes under the bar's buttons. */
    public static int openExtra() {
        return AndroidUtilities.dp(8 + 2 * LINE_DP + 26);
    }

    /** Space the folded greeting keeps: one line at 60% and the handle. */
    public static int foldedExtra() {
        return AndroidUtilities.dp(4 + 27 + 21);
    }

    private final View host;
    private final Delegate delegate;
    private final TextPaint heroPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF pillHit = new RectF();
    private final RectF heroHit = new RectF();
    private final AnimatedFloat pillRight;
    private final AnimatedFloat searchProgress;
    private final AnimatedFloat pillPressed;
    private final AnimatedFloat handlePressed;
    private final int touchSlop;

    private String[] words = new String[0];
    private int[] wordColors = new int[0];
    private float[] wordWidths = new float[0];
    private String secondLine = "";
    private float secondLineWidth;
    private float spaceWidth;
    private float baseline;
    private String searchHint = "";

    private float fold;
    private float pin;
    private float heroAlpha = 1f;
    private boolean revealPending;
    private long revealStart;

    private boolean pillEnabled;
    private int pressTarget;
    private float downX;
    private float downY;

    private String pillText;
    private float pillTextWidth = -1;
    private CharSequence pillTextShown;

    public AuroraChatsHeader(View host, Delegate delegate) {
        this.host = host;
        this.delegate = delegate;
        touchSlop = ViewConfiguration.get(host.getContext()).getScaledTouchSlop();

        heroPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        heroPaint.setTextSize(AndroidUtilities.dp(TEXT_DP));
        if (Build.VERSION.SDK_INT >= 21) {
            heroPaint.setLetterSpacing(-0.025f);
        }
        pillTextPaint.setTextSize(AndroidUtilities.dp(15));
        pillTextPaint.setColor(0xffffffff);
        pillFill.setColor(0xffffffff);
        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(Math.max(1, AndroidUtilities.dp(1)));
        pillStroke.setColor(0xffffffff);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        iconPaint.setColor(0xffffffff);
        handlePaint.setColor(0xffffffff);

        pillRight = new AnimatedFloat(host, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        searchProgress = new AnimatedFloat(host, 0, 300, CubicBezierInterpolator.DEFAULT);
        pillPressed = new AnimatedFloat(host, 0, 150, CubicBezierInterpolator.DEFAULT);
        // The design's handle grows with a little spring: .35s cubic-bezier(.3,.7,.3,1.3).
        handlePressed = new AnimatedFloat(host, 0, 350, new CubicBezierInterpolator(0.3, 0.7, 0.3, 1.3));
    }

    /**
     * The greeting: {@code before name after} on the first line with the name
     * in lavender (name may be null), and {@code secondLine} under it.
     */
    public void setText(String before, String name, String after, String secondLine) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
        addWords(list, colors, before, TEXT_COLOR);
        addWords(list, colors, name, AuroraTheme.TYPING);
        addWords(list, colors, after, TEXT_COLOR);
        words = list.toArray(new String[0]);
        wordColors = new int[colors.size()];
        wordWidths = new float[words.length];
        for (int i = 0; i < words.length; i++) {
            wordColors[i] = colors.get(i);
            wordWidths[i] = heroPaint.measureText(words[i]);
        }
        this.secondLine = secondLine != null ? secondLine : "";
        secondLineWidth = heroPaint.measureText(this.secondLine);
        spaceWidth = heroPaint.measureText(" ");
        Paint.FontMetrics metrics = heroPaint.getFontMetrics();
        baseline = (AndroidUtilities.dp(LINE_DP) - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
        host.invalidate();
    }

    private static void addWords(java.util.ArrayList<String> list, java.util.ArrayList<Integer> colors, String text, int color) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        for (String word : text.trim().split("\\s+")) {
            if (word.length() > 0) {
                list.add(word);
                colors.add(color);
            }
        }
    }

    public void setSearchHint(String hint) {
        searchHint = hint != null ? hint : "";
        host.invalidate();
    }

    /** Slides the greeting's words in on the next frame (once, when the chat list first shows). */
    public void startReveal() {
        revealPending = true;
        host.invalidate();
    }

    /** 0 open, 1 folded into one small line. */
    public void setFold(float fold) {
        fold = Math.max(0f, Math.min(1f, fold));
        if ((this.fold < 0.5f) != (fold < 0.5f)) {
            // Folding or reopening the greeting gives the light a small push.
            AuroraEnergy.pulse(AuroraEnergy.COLLAPSE);
        }
        if (this.fold != fold) {
            this.fold = fold;
            host.invalidate();
        }
    }

    /** How far the bar's buttons are drawn below their laid-out place (they stay put while the bar folds). */
    public void setPin(float pin) {
        this.pin = pin;
    }

    /** Fades the greeting while search or selection takes over the header. */
    public void setHeroAlpha(float alpha) {
        if (heroAlpha != alpha) {
            heroAlpha = alpha;
            host.invalidate();
        }
    }

    /**
     * Draws the frosted search pill, in the bar's button row (the canvas is
     * already shifted by the pin).
     *
     * @param status shown instead of "Search" while connecting, or null
     */
    public void drawPill(Canvas canvas, float left, float right, float centerY, boolean searchOpen, String status, float alpha) {
        if (alpha <= 0f) {
            pillHit.setEmpty();
            pillEnabled = false;
            return;
        }
        final float search = searchProgress.set(searchOpen ? 1f : 0f);
        right = pillRight.set(right);
        final float height = AndroidUtilities.dp(40);
        if (right - left < AndroidUtilities.dp(48)) {
            pillHit.setEmpty();
            pillEnabled = false;
            return;
        }
        rect.set(left, centerY - height / 2f, right, centerY + height / 2f);
        pillHit.set(rect);
        pillHit.offset(0, pin);
        pillEnabled = !searchOpen && search < 0.5f;

        final float press = pillPressed.set(pressTarget == 1 ? 1f : 0f);
        pillFill.setAlpha((int) ((0x24 + 0x12 * press) * alpha));
        canvas.drawRoundRect(rect, height / 2f, height / 2f, pillFill);
        // The rim brightens while you search, as in the design.
        pillStroke.setAlpha((int) (AndroidUtilities.lerp(0x38, 0x99, search) * alpha));
        final float inset = pillStroke.getStrokeWidth() / 2f;
        rect.inset(inset, inset);
        canvas.drawRoundRect(rect, height / 2f - inset, height / 2f - inset, pillStroke);

        final float contentAlpha = alpha * (1f - search);
        if (contentAlpha <= 0f) {
            return;
        }
        // An outline magnifier (24-unit icon drawn at 18dp).
        final float size = AndroidUtilities.dp(18);
        final float k = size / 24f;
        final float ix = left + AndroidUtilities.dp(14);
        final float iy = centerY - size / 2f;
        iconPaint.setAlpha((int) (255 * contentAlpha));
        canvas.drawCircle(ix + 11 * k, iy + 11 * k, 8 * k, iconPaint);
        canvas.drawLine(ix + 16.7f * k, iy + 16.7f * k, ix + 21 * k, iy + 21 * k, iconPaint);

        final String text = !TextUtils.isEmpty(status) ? status : searchHint;
        final float textLeft = ix + size + AndroidUtilities.dp(8);
        final float available = right - AndroidUtilities.dp(14) - textLeft;
        if (available <= 0 || TextUtils.isEmpty(text)) {
            return;
        }
        if (!text.equals(pillText) || pillTextWidth != available) {
            pillText = text;
            pillTextWidth = available;
            pillTextShown = TextUtils.ellipsize(text, pillTextPaint, available, TextUtils.TruncateAt.END);
        }
        pillTextPaint.setAlpha((int) (0xc7 * contentAlpha));
        final float textBaseline = centerY - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2f;
        canvas.drawText(pillTextShown, 0, pillTextShown.length(), textLeft, textBaseline, pillTextPaint);
    }

    /**
     * Draws the greeting and the handle between {@code top} (just under the
     * button row) and {@code bottom} (the bar's bottom edge).
     */
    public void drawHero(Canvas canvas, float width, float top, float bottom, float alpha) {
        heroHit.set(0, top, width, bottom);
        final float a = alpha * heroAlpha;
        if (bottom - top <= 0 || a <= 0f) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (revealPending) {
            revealPending = false;
            revealStart = AuroraTheme.canAnimate() ? now + 80 : 0;
        }
        boolean revealing = false;

        canvas.save();
        canvas.clipRect(0, top, width, bottom);

        final float left = AndroidUtilities.dp(20);
        final float lineHeight = AndroidUtilities.dp(LINE_DP);
        float lineWidth = 0;
        for (int i = 0; i < words.length; i++) {
            lineWidth += wordWidths[i] + (i > 0 ? spaceWidth : 0);
        }
        final float available = width - left * 2;
        final float fit = available > 0 ? Math.min(1f, available / Math.max(1f, Math.max(lineWidth, secondLineWidth))) : 1f;
        final float scale = AndroidUtilities.lerp(1f, 0.6f, fold) * fit;

        canvas.save();
        canvas.translate(left, top + AndroidUtilities.dp(AndroidUtilities.lerp(8, 4, fold)));
        canvas.scale(scale, scale);
        float x = 0;
        for (int i = 0; i < words.length; i++) {
            float p = reveal(now, 50 + 90L * i);
            revealing |= p < 1f;
            drawWord(canvas, words[i], wordColors[i], x, 0, lineHeight, p, a);
            x += wordWidths[i] + spaceWidth;
        }
        // The second line fades and lifts away as the greeting folds.
        final float secondAlpha = a * (1f - Math.min(1f, fold * 2f));
        if (secondAlpha > 0f && secondLine.length() > 0) {
            float p = reveal(now, 50 + 90L * words.length + 30);
            revealing |= p < 1f;
            drawWord(canvas, secondLine, TEXT_COLOR, 0, lineHeight - AndroidUtilities.dp(16) * fold, lineHeight, p, secondAlpha);
        }
        canvas.restore();

        // The grab handle: grows and brightens under the finger.
        final float press = handlePressed.set(pressTarget == 2 ? 1f : 0f);
        final float handleWidth = AndroidUtilities.dp(36) + AndroidUtilities.dp(16) * press;
        final float handleY = bottom - AndroidUtilities.dp(13);
        final float r = AndroidUtilities.dp(2.5f);
        handlePaint.setAlpha((int) (255 * AndroidUtilities.lerp(0.55f, 0.9f, Math.min(1f, press)) * a));
        rect.set(width / 2f - handleWidth / 2f, handleY - r, width / 2f + handleWidth / 2f, handleY + r);
        canvas.drawRoundRect(rect, r, r, handlePaint);

        canvas.restore();

        if (revealing) {
            host.invalidate();
        } else {
            revealStart = 0;
        }
    }

    private void drawWord(Canvas canvas, String word, int color, float x, float lineTop, float lineHeight, float progress, float alpha) {
        if (progress <= 0f || alpha <= 0f) {
            return;
        }
        canvas.save();
        // Each word rises out of its own line box, like the design's afWord.
        canvas.clipRect(x - AndroidUtilities.dp(4), lineTop, x + heroPaint.measureText(word) + AndroidUtilities.dp(6), lineTop + lineHeight + AndroidUtilities.dp(4));
        heroPaint.setColor(color);
        heroPaint.setAlpha((int) (255 * alpha * progress));
        canvas.drawText(word, x, lineTop + baseline + (1f - progress) * lineHeight * 1.05f, heroPaint);
        canvas.restore();
    }

    private float reveal(long now, long delay) {
        if (revealStart == 0) {
            return 1f;
        }
        float t = (now - revealStart - delay) / (float) WORD_MS;
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return AuroraTheme.RISE.getInterpolation(t);
    }

    /**
     * Taps on the pill open search; taps and vertical swipes on the greeting
     * fold or unfold it. Coordinates are the bar's.
     *
     * @return true while this gesture belongs to the header
     */
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        final float x = ev.getX();
        final float y = ev.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            pressTarget = 0;
            if (pillEnabled && pillHit.contains(x, y)) {
                pressTarget = 1;
            } else if (heroAlpha > 0.5f && heroHit.height() > AndroidUtilities.dp(24) && heroHit.contains(x, y)) {
                pressTarget = 2;
            }
            if (pressTarget == 0) {
                return false;
            }
            downX = x;
            downY = y;
            host.invalidate();
            return true;
        }
        if (pressTarget == 0) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (pressTarget == 1 && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                // Stays consumed, but no longer counts as a tap.
                pressTarget = -1;
                host.invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            final int target = pressTarget;
            pressTarget = 0;
            host.invalidate();
            if (target == 1) {
                host.playSoundEffect(SoundEffectConstants.CLICK);
                delegate.onSearchPillClick();
            } else if (target == 2) {
                float dy = y - downY;
                if (Math.abs(dy) > touchSlop * 2 && Math.abs(dy) > Math.abs(x - downX)) {
                    delegate.onGreetingGesture(dy < 0 ? 1 : -1);
                } else if (Math.abs(dy) <= touchSlop && Math.abs(x - downX) <= touchSlop) {
                    delegate.onGreetingGesture(0);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressTarget = 0;
            host.invalidate();
        }
        return true;
    }
}

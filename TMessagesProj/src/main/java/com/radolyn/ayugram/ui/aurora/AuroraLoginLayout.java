package com.radolyn.ayugram.ui.aurora;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/**
 * Lays the sign-in pages out like the design: a big start-aligned headline
 * near the top with its subtitle under it, and the form at the bottom, above
 * the Continue pill. Telegram's pages are reused as they are; this only moves
 * and restyles their views.
 */
public final class AuroraLoginLayout {

    public static final int TEXT = 0xfff5f5f7;
    public static final int SUBTITLE = 0xffd4d4dc;
    public static final int MUTED = 0xffa1a1aa;
    public static final int LINK = 0xffc4b5fd;

    /** Added to each page's own 18dp side margin, so content sits 24dp from the edges like the design. */
    public static final int SIDE_DP = 6;
    /** Page top padding: keeps the page clear of the logo row, which ends 22dp below the page's top. */
    public static final int PAGE_TOP_DP = 30;
    public static final int HEADLINE_SP = 40;
    public static final int SUBTITLE_SP = 16;

    private AuroraLoginLayout() {
    }

    public static int startGravity() {
        return LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
    }

    public static int endGravity() {
        return LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT;
    }

    /**
     * Puts {@code headline} and {@code subtitle} at the top of {@code page}
     * and everything from {@code formStart} on at its bottom. The free space
     * is split 1:3 above the headline and above the form, so the headline
     * stays high on tall screens. When the keyboard leaves too little room,
     * both gaps close first, then the headline is hidden rather than cut.
     */
    public static void arrange(LinearLayout page, TextView headline, TextView subtitle, View formStart) {
        page.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        restyle(page, headline, subtitle);
        page.addView(new Space(page.getContext()), page.indexOfChild(headline), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (formStart != null && formStart.getParent() == page) {
            page.addView(new Space(page.getContext()), page.indexOfChild(formStart), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));
        }
        hideWhenCut(page, headline, subtitle);
    }

    /**
     * Only restyles the headline and subtitle where they are, for the rarer
     * pages whose own layout is kept (waiting for a call, account reset).
     */
    public static void restyle(LinearLayout page, TextView headline, TextView subtitle) {
        page.setPadding(0, AndroidUtilities.dp(PAGE_TOP_DP), 0, page.getPaddingBottom());
        styleHeadline(headline);
        placeText(headline, 6, 0);
        if (subtitle != null) {
            styleSubtitle(subtitle);
            placeText(subtitle, 12, 36);
        }
    }

    /**
     * The design's headline: 40sp, tight, start-aligned. The view itself
     * shrinks it if a single word would not fit on a line.
     */
    public static void styleHeadline(TextView headline) {
        headline.setGravity(startGravity() | Gravity.TOP);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_DIP, HEADLINE_SP);
        headline.setLineSpacing(0, 0.95f);
        headline.setTextColor(TEXT);
    }

    public static void styleSubtitle(TextView subtitle) {
        subtitle.setGravity(startGravity() | Gravity.TOP);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SUBTITLE_SP);
        subtitle.setLineSpacing(AndroidUtilities.dp(4), 1f);
        subtitle.setTextColor(SUBTITLE);
    }

    /** A small lavender text button, like the design's "Wrong number?". */
    public static void styleLink(TextView link) {
        link.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        link.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        link.setTextColor(LINK);
        link.setGravity(startGravity() | Gravity.CENTER_VERTICAL);
        link.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        link.setBackground(Theme.createRadSelectorDrawable(0x1fffffff, 8, 8));
        link.setLineSpacing(0, 1f);
    }

    public static TextView createLink(Context context, CharSequence text) {
        TextView link = new TextView(context);
        link.setText(text);
        link.setSingleLine(true);
        styleLink(link);
        return link;
    }

    /** Small grey text, like the design's "Resend in 0:42". */
    public static void styleNote(TextView note) {
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        note.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        note.setLineSpacing(0, 1f);
    }

    /** Width MATCH_PARENT, start-aligned, design side margins. {@code extraEndDp} narrows the line, like a max-width. */
    private static void placeText(View view, int topDp, int extraEndDp) {
        LinearLayout.LayoutParams lp = linearParams(view);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.weight = 0;
        lp.gravity = startGravity();
        int start = AndroidUtilities.dp(SIDE_DP);
        int end = AndroidUtilities.dp(SIDE_DP + extraEndDp);
        lp.leftMargin = LocaleController.isRTL ? end : start;
        lp.rightMargin = LocaleController.isRTL ? start : end;
        lp.topMargin = AndroidUtilities.dp(topDp);
        lp.bottomMargin = 0;
        view.setLayoutParams(lp);
    }

    /** Full width with the design's side margins, {@code heightDp} tall (or wrap when negative), {@code topDp} below the view before. */
    public static void placeRow(View view, int heightDp, int topDp) {
        LinearLayout.LayoutParams lp = linearParams(view);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = heightDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : AndroidUtilities.dp(heightDp);
        lp.weight = 0;
        lp.gravity = Gravity.NO_GRAVITY;
        lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(SIDE_DP);
        lp.topMargin = AndroidUtilities.dp(topDp);
        lp.bottomMargin = 0;
        view.setLayoutParams(lp);
    }

    /**
     * Shrinks a container Telegram stretched to fill the page bottom (with
     * its content pinned 32dp above it) down to its content, so it can sit
     * right under the form.
     */
    public static void collapse(View container, int topDp) {
        placeRow(container, -1, topDp);
        if (container instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) container;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                    lp.topMargin = 0;
                    lp.bottomMargin = 0;
                    lp.gravity = startGravity() | Gravity.TOP;
                    child.setLayoutParams(lp);
                }
            }
        }
    }

    public static void hide(View... views) {
        for (View view : views) {
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        }
    }

    private static LinearLayout.LayoutParams linearParams(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams) {
            return (LinearLayout.LayoutParams) params;
        }
        if (params instanceof ViewGroup.MarginLayoutParams) {
            return new LinearLayout.LayoutParams((ViewGroup.MarginLayoutParams) params);
        }
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * With the keyboard up on a short screen the page's top is cut off.
     * Instead of half a headline, hide whatever would be cut.
     */
    private static void hideWhenCut(LinearLayout page, View headline, View subtitle) {
        page.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int limit = page.getPaddingTop();
            showUnlessCut(headline, limit);
            if (subtitle != null) {
                showUnlessCut(subtitle, limit);
            }
        });
    }

    private static void showUnlessCut(View view, int limit) {
        if (view.getVisibility() == View.GONE) {
            return;
        }
        view.setVisibility(view.getTop() < limit ? View.INVISIBLE : View.VISIBLE);
    }
}

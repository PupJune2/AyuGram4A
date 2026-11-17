package com.radolyn.ayugram.ui.aurora;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Aurora Motion: a built-in dark theme (assets/aurora.attheme) plus a few
 * custom-drawn pieces that the theme file alone can't express, like the
 * drifting aurora light behind the chat list header and the gradient FAB.
 */
public final class AuroraTheme {

    public static final String THEME_NAME = "Aurora Motion";
    public static final String ASSET_NAME = "aurora.attheme";

    public static final int HEADER_BASE = 0xff17132a;
    public static final int VIOLET = 0xff7c5cff;
    public static final int BLUE = 0xff2f6bff;
    public static final int TEAL = 0xff14a89c;
    public static final int VIOLET_PRESSED = 0xff6646e8;

    // cubic-bezier(.2, .8, .2, 1), the design's "rise" easing.
    public static final CubicBezierInterpolator RISE = new CubicBezierInterpolator(0.2, 0.8, 0.2, 1.0);

    private AuroraTheme() {
    }

    public static boolean isActive() {
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        return themeInfo != null && themeInfo.info == null && THEME_NAME.equals(themeInfo.name);
    }

    /**
     * Ambient motion (the drifting header light) follows the power-saving
     * "animated backgrounds" switch and the system animator scale.
     */
    public static boolean canAnimate() {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * The first time {@code list} lays out rows, they fade in and rise 16dp,
     * one after another (60ms apart), like the design's chat list. Uses a
     * layout animation, so it never leaves rows half-transparent if a row is
     * recycled or animated by the item animator meanwhile.
     */
    public static void riseInOnFirstLayout(ViewGroup list) {
        list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (list.getChildCount() == 0) {
                    return;
                }
                list.removeOnLayoutChangeListener(this);
                if (!isActive() || !canAnimate()) {
                    return;
                }
                AnimationSet rise = new AnimationSet(true);
                rise.addAnimation(new AlphaAnimation(0f, 1f));
                rise.addAnimation(new TranslateAnimation(0, 0, AndroidUtilities.dp(16), 0));
                rise.setDuration(500);
                rise.setInterpolator(RISE);
                list.setLayoutAnimation(new LayoutAnimationController(rise, 0.12f));
                list.setLayoutAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        list.post(() -> {
                            list.setLayoutAnimation(null);
                            list.setLayoutAnimationListener(null);
                        });
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                list.scheduleLayoutAnimation();
            }
        });
    }

    /**
     * Violet to blue diagonal gradient with a soft light rim, as in the design's
     * new-message button. Keeps the user's FAB shape setting via {@code radius}.
     */
    public static Drawable createFabBackground(int radius) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{VIOLET, BLUE});
        fill.setCornerRadius(radius);
        fill.setStroke(Math.max(1, AndroidUtilities.dp(1)), 0x40ffffff);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xffffffff);
        mask.setCornerRadius(radius);

        ColorStateList rippleColor = new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{0x33ffffff});
        return new RippleDrawable(rippleColor, fill, mask);
    }
}

package com.radolyn.ayugram.ui.aurora;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

/**
 * The Aurora Motion "New" menu: tapping the chat list button dims the list and
 * pops up New channel, New group and New message pills above the button.
 */
public class AuroraNewMenu extends FrameLayout {

    public interface Delegate {
        void onNewMessage();

        void onNewGroup();

        void onNewChannel();

        void onDismissRequested();
    }

    private final View backdrop;
    private final LinearLayout column;
    private final View[] items;
    private boolean menuShown;
    private AnimatorSet currentAnimation;

    public AuroraNewMenu(Context context, Delegate delegate) {
        super(context);
        // Invisible rather than gone, so the pills are measured before their first pop-in.
        setVisibility(INVISIBLE);

        backdrop = new View(context);
        backdrop.setBackgroundColor(0x990b0b10);
        backdrop.setContentDescription(LocaleController.getString("Close", R.string.Close));
        backdrop.setOnClickListener(v -> delegate.onDismissRequested());
        addView(backdrop, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        // Sits 16dp above the 56dp button, which has a 14dp margin.
        addView(column, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 14, 0, 14, 14 + 56 + 16));

        // Top to bottom; the last one sits closest to the button and appears first.
        items = new View[]{
                createItem(context, LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.msg_channel, 0xff0f766e, delegate::onNewChannel),
                createItem(context, LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.msg_groups, 0xff2563eb, delegate::onNewGroup),
                createItem(context, LocaleController.getString("NewMessageTitle", R.string.NewMessageTitle), R.drawable.msg_message, 0xff7c3aed, delegate::onNewMessage),
        };
        for (int i = 0; i < items.length; i++) {
            column.addView(items[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 52, 0, i == 0 ? 0 : 10, 0, 0));
        }
    }

    private View createItem(Context context, String label, int iconRes, int iconColor, Runnable action) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(20), 0);

        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0xeb221e38);
        fill.setCornerRadius(AndroidUtilities.dp(26));
        fill.setStroke(Math.max(1, AndroidUtilities.dp(1)), 0x24ffffff);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xffffffff);
        mask.setCornerRadius(AndroidUtilities.dp(26));
        pill.setBackground(new RippleDrawable(new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{0x1fffffff}), fill, mask));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(iconColor);
        icon.setBackground(circle);
        pill.addView(icon, LayoutHelper.createLinear(38, 38));

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(0xfff5f5f7);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        text.setSingleLine(true);
        pill.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 12, 0, 0, 0));

        pill.setOnClickListener(v -> action.run());
        pill.setStateListAnimator(createPressAnimator(pill));
        return pill;
    }

    static StateListAnimator createPressAnimator(View view) {
        StateListAnimator animator = new StateListAnimator();
        AnimatorSet press = new AnimatorSet();
        press.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, 0.94f), ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.94f));
        press.setDuration(150);
        press.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        AnimatorSet release = new AnimatorSet();
        release.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, 1f), ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f));
        release.setDuration(350);
        release.setInterpolator(new OvershootInterpolator(2f));
        animator.addState(new int[]{android.R.attr.state_pressed}, press);
        animator.addState(new int[]{}, release);
        return animator;
    }

    public boolean isMenuShown() {
        return menuShown;
    }

    /**
     * Shows the pills 16dp above {@code anchor}, the new-message button, which
     * must share this view's parent. The button can sit higher than its usual
     * place, for example above a player bar, so its position is read each time.
     */
    public void show(View anchor) {
        if (menuShown) {
            return;
        }
        menuShown = true;
        cancelAnimation();
        if (anchor != null && column.getHeight() > 0) {
            float anchorTop = anchor.getTop() + anchor.getTranslationY() - getTop();
            column.setTranslationY(anchorTop - AndroidUtilities.dp(16) - column.getBottom());
        }
        setVisibility(VISIBLE);
        if (!AuroraTheme.canAnimate()) {
            backdrop.setAlpha(1f);
            for (View item : items) {
                item.setAlpha(1f);
                item.setTranslationY(0);
                item.setScaleX(1f);
                item.setScaleY(1f);
            }
            return;
        }
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator dim = ObjectAnimator.ofFloat(backdrop, View.ALPHA, 0f, 1f);
        dim.setDuration(300);
        set.play(dim);
        for (int i = items.length - 1, order = 0; i >= 0; i--, order++) {
            View item = items[i];
            item.setPivotX(LocaleController.isRTL ? 0 : item.getMeasuredWidth());
            item.setPivotY(item.getMeasuredHeight() / 2f);
            AnimatorSet pop = new AnimatorSet();
            pop.playTogether(
                    ObjectAnimator.ofFloat(item, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(item, View.TRANSLATION_Y, AndroidUtilities.dp(24), 0),
                    ObjectAnimator.ofFloat(item, View.SCALE_X, 0.9f, 1f),
                    ObjectAnimator.ofFloat(item, View.SCALE_Y, 0.9f, 1f)
            );
            pop.setDuration(400);
            pop.setStartDelay(50L * order);
            pop.setInterpolator(new OvershootInterpolator(1.6f));
            item.setAlpha(0f);
            set.play(pop);
        }
        currentAnimation = set;
        set.start();
    }

    public void dismiss(boolean animated) {
        if (!menuShown) {
            return;
        }
        menuShown = false;
        cancelAnimation();
        if (!animated || !AuroraTheme.canAnimate()) {
            setVisibility(INVISIBLE);
            return;
        }
        AnimatorSet set = new AnimatorSet();
        set.play(ObjectAnimator.ofFloat(backdrop, View.ALPHA, 0f));
        for (View item : items) {
            set.play(ObjectAnimator.ofFloat(item, View.ALPHA, 0f));
            set.play(ObjectAnimator.ofFloat(item, View.TRANSLATION_Y, AndroidUtilities.dp(12)));
        }
        set.setDuration(180);
        set.setInterpolator(CubicBezierInterpolator.EASE_IN);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!menuShown) {
                    setVisibility(INVISIBLE);
                }
            }
        });
        currentAnimation = set;
        set.start();
    }

    private void cancelAnimation() {
        if (currentAnimation != null) {
            currentAnimation.removeAllListeners();
            currentAnimation.cancel();
            currentAnimation = null;
        }
    }
}

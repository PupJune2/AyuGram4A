package com.radolyn.ayugram.ui.aurora;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.Collections;

/**
 * The design's message motion in a chat:
 * <ul>
 *     <li>your new message pops out toward the send button (amSend: from 24px
 *     right, scale .7 and transparent, through scale 1.02, .5s);</li>
 *     <li>a new reply fades and grows in as it slides up (amRise);</li>
 *     <li>when a chat opens, the visible messages rise in one after another,
 *     top first (amRise: fade, 14px, scale .96; .5s, 70ms apart).</li>
 * </ul>
 * For new messages Telegram's own slide up from the composer stays; this adds
 * the scale, fade and sideways part on top of it. Everything is plain view
 * properties, so what the chat draws around a cell (side buttons, album
 * captions) follows it, and {@link #cancel} hands a cell back at rest the
 * moment Telegram's item animator needs it.
 */
public final class AuroraMessageMotion {

    private static final int TAG_KEY = 0x7a0a0001;
    private static final long DURATION_MS = 500;

    private static final int KIND_SEND = 0;
    private static final int KIND_REPLY = 1;
    private static final int KIND_OPEN = 2;

    private AuroraMessageMotion() {
    }

    /**
     * Plays the send pop (outgoing) or the rise (incoming) on a cell that was
     * just added at the bottom of the chat.
     *
     * @param animateAlpha false when the cell's alpha belongs to another transition
     */
    public static void playEnter(View view, boolean animateAlpha) {
        cancel(view);
        if (!AuroraTheme.isActive() || !AuroraTheme.canAnimate()) {
            return;
        }
        boolean out = view instanceof ChatMessageCell && ((ChatMessageCell) view).getMessageObject() != null && ((ChatMessageCell) view).getMessageObject().isOutOwner();
        play(view, out ? KIND_SEND : KIND_REPLY, 0, animateAlpha);
    }

    /** Ends a running pop or rise at its final state (scale 1, in place, opaque). */
    public static void cancel(View view) {
        Object press = view.getTag(PRESS_TAG_KEY);
        if (press instanceof Animator) {
            view.setTag(PRESS_TAG_KEY, null);
            ((Animator) press).cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
        Object tag = view.getTag(TAG_KEY);
        if (tag instanceof Animator) {
            view.setTag(TAG_KEY, null);
            ((Animator) tag).end();
        }
    }

    private static void play(View view, int kind, long delay, boolean animateAlpha) {
        setPivot(view, kind);
        float shift = AndroidUtilities.dp(24);
        float back = -AndroidUtilities.dp(2);
        float rise = AndroidUtilities.dp(14);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setStartDelay(delay);
        Frame frame = t -> {
            float scale;
            float alpha;
            float x = 0;
            float y = 0;
            if (kind == KIND_SEND) {
                // Each keyframe segment gets the easing, as in CSS.
                if (t < 0.7f) {
                    float p = AuroraTheme.RISE.getInterpolation(t / 0.7f);
                    scale = lerp(0.7f, 1.02f, p);
                    alpha = p;
                    x = lerp(shift, back, p);
                } else {
                    float p = AuroraTheme.RISE.getInterpolation((t - 0.7f) / 0.3f);
                    scale = lerp(1.02f, 1f, p);
                    alpha = 1f;
                    x = lerp(back, 0, p);
                }
            } else {
                float p = AuroraTheme.RISE.getInterpolation(t);
                scale = lerp(0.96f, 1f, p);
                alpha = p;
                y = lerp(rise, 0, p);
            }
            view.setScaleX(scale);
            view.setScaleY(scale);
            if (animateAlpha) {
                view.setAlpha(Math.max(0f, Math.min(1f, alpha)));
            }
            if (kind == KIND_SEND) {
                setOffsetX(view, x);
            } else if (kind == KIND_OPEN) {
                view.setTranslationY(y);
            }
        };
        animator.addUpdateListener(a -> frame.apply((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getTag(TAG_KEY) == animation) {
                    view.setTag(TAG_KEY, null);
                }
                finish(view, kind, animateAlpha);
            }
        });
        view.setTag(TAG_KEY, animator);
        // Show the first frame right away, also while waiting out the delay.
        frame.apply(0f);
        animator.start();
    }

    private interface Frame {
        void apply(float t);
    }

    private static void setPivot(View view, int kind) {
        if (view instanceof ChatMessageCell && ((ChatMessageCell) view).getMessageObject() != null) {
            ChatMessageCell cell = (ChatMessageCell) view;
            int left = cell.getBackgroundDrawableLeft();
            int right = cell.getBackgroundDrawableRight();
            if (kind == KIND_SEND) {
                // Grow out of the bubble's corner nearest the send button.
                view.setPivotX(right);
                view.setPivotY(cell.getBackgroundDrawableBottom());
            } else {
                view.setPivotX(left + (right - left) / 2f);
                view.setPivotY(view.getHeight() / 2f);
            }
        }
    }

    private static void finish(View view, int kind, boolean animateAlpha) {
        view.setScaleX(1f);
        view.setScaleY(1f);
        if (animateAlpha) {
            view.setAlpha(1f);
        }
        if (kind == KIND_SEND) {
            setOffsetX(view, 0);
        } else if (kind == KIND_OPEN) {
            view.setTranslationY(0);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            view.resetPivot();
        } else {
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(view.getHeight() / 2f);
        }
    }

    private static void setOffsetX(View view, float x) {
        if (view instanceof ChatMessageCell) {
            ((ChatMessageCell) view).setAnimationOffsetX(x);
        } else {
            view.setTranslationX(x);
        }
    }

    private static final int PRESS_TAG_KEY = 0x7a0a0002;
    // The design's bubble press: .2s cubic-bezier(.3,.7,.4,1.4), both ways.
    private static final CubicBezierInterpolator PRESS_EASE = new CubicBezierInterpolator(0.3, 0.7, 0.4, 1.4);

    /**
     * A pressed bubble sinks to .97 and springs back on release, like the
     * design's {@code .bub:active}. Skipped while the cell pops or rises in.
     */
    public static void press(View view, boolean pressed) {
        Object running = view.getTag(PRESS_TAG_KEY);
        if (running instanceof Animator) {
            view.setTag(PRESS_TAG_KEY, null);
            ((Animator) running).cancel();
        }
        if (!AuroraTheme.isActive() || !AuroraTheme.canAnimate() || view.getTag(TAG_KEY) != null) {
            return;
        }
        float from = view.getScaleX();
        float to = pressed ? 0.97f : 1f;
        if (from == to) {
            return;
        }
        if (pressed) {
            setPivot(view, KIND_REPLY);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(200);
        animator.setInterpolator(PRESS_EASE);
        animator.addUpdateListener(a -> {
            float scale = (float) a.getAnimatedValue();
            view.setScaleX(scale);
            view.setScaleY(scale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getTag(PRESS_TAG_KEY) == animation) {
                    view.setTag(PRESS_TAG_KEY, null);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // Whoever cancels takes over the scale; a release never stays shrunk.
                if (!pressed) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                }
            }
        });
        view.setTag(PRESS_TAG_KEY, animator);
        animator.start();
    }

    private static float lerp(float from, float to, float p) {
        return from + (to - from) * p;
    }

    /**
     * The first time {@code list} lays out messages, they rise in one after
     * another, top first. Scrolling ends the cascade at once, so a recycled
     * cell never carries it to another message.
     */
    public static void riseInOnFirstLayout(RecyclerView list) {
        list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (list.getChildCount() == 0) {
                    return;
                }
                list.removeOnLayoutChangeListener(this);
                if (!AuroraTheme.isActive() || !AuroraTheme.canAnimate()) {
                    return;
                }
                ArrayList<View> order = new ArrayList<>();
                for (int i = 0; i < list.getChildCount(); i++) {
                    View child = list.getChildAt(i);
                    // Leave alone anything already placed or faded by the chat itself.
                    if (child.getVisibility() == View.VISIBLE && child.getTranslationY() == 0 && child.getAlpha() == 1f
                            && child.getBottom() > 0 && child.getTop() < list.getHeight()) {
                        order.add(child);
                    }
                }
                if (order.isEmpty()) {
                    return;
                }
                Collections.sort(order, (a, b) -> Integer.compare(a.getTop(), b.getTop()));
                // Keep the whole cascade under ~0.8s on tall screens.
                long step = order.size() > 10 ? 700 / order.size() : 70;
                for (int i = 0; i < order.size(); i++) {
                    play(order.get(i), KIND_OPEN, 50 + i * step, true);
                }
                list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        if (dx == 0 && dy == 0) {
                            return;
                        }
                        recyclerView.removeOnScrollListener(this);
                        for (View view : order) {
                            cancel(view);
                        }
                    }
                });
            }
        });
    }
}

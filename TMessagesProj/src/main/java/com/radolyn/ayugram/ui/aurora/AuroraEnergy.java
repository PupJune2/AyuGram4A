package com.radolyn.ayugram.ui.aurora;

import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Aurora Flow's living light: one energy level and one finger position shared
 * by every aurora on screen.
 * <ul>
 *     <li>Taps, typing, sending and opening things add energy ({@link #pulse});
 *     it fades by 16% every 90ms, as in the design.</li>
 *     <li>The lights lean towards the finger ({@link #onTouch}) and drift back
 *     to the middle once it lifts.</li>
 * </ul>
 * The lights read {@link #energy()} and {@link #pointerX()}/{@link #pointerY()}
 * on every frame; values are eased like the design's .7s transform transition,
 * so a pulse swells the lights instead of making them jump.
 * Everything here runs on the main thread.
 */
public final class AuroraEnergy {

    public static final float TAP = 0.16f;
    public static final float SEND = 0.75f;
    public static final float REPLY = 0.55f;
    public static final float OPEN_CHAT = 0.45f;
    public static final float REACT = 0.4f;
    public static final float CHATS = 0.35f;
    public static final float MENU = 0.3f;
    public static final float TAB = 0.25f;
    public static final float SHEET = 0.2f;
    public static final float SEARCH = 0.2f;
    public static final float COLLAPSE = 0.2f;
    public static final float CODE_DIGIT = 0.18f;
    public static final float FIELD = 0.1f;
    public static final float SEARCH_TYPE = 0.08f;
    public static final float TYPE = 0.07f;
    public static final float LOGIN_STEP = 0.6f;
    public static final float LOGIN_DONE = 1f;

    /** How far each of the three lights leans towards the finger, in dp (the design's px). */
    private static final float[] LEAN_DP = {30, -24, 20};

    private static final float DECAY = 0.84f;
    private static final float DECAY_STEP_MS = 90f;
    // The design eases light changes with transform .7s cubic-bezier(.2,.8,.2,1): mostly done in ~0.3s.
    private static final float FOLLOW_MS = 160f;
    private static final float RELEASE_MS = 650f;

    private static float level;
    private static long levelTime;
    private static float shown;
    private static float targetX;
    private static float targetY;
    private static float x;
    private static float y;
    private static boolean touching;
    private static long frameTime;

    private AuroraEnergy() {
    }

    private static boolean enabled() {
        return AuroraTheme.isActive() && AuroraTheme.canAnimate();
    }

    /** Adds {@code amount} of energy (capped at 1), as the design's {@code pulse(a)}. */
    public static void pulse(float amount) {
        if (!enabled()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        level = Math.min(1f, levelAt(now) + amount);
        levelTime = now;
    }

    /** Pulses by {@code amount} whenever the focused {@code field}'s text changes, like the design's inputs. */
    public static void pulseWhileTyping(TextView field, float amount) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if ((before > 0 || count > 0) && field.isFocused()) {
                    pulse(amount);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Follows a finger on a window of {@code width} x {@code height}: a new
     * finger nudges the light, moves lean it, lifting lets it settle back.
     */
    public static void onTouch(MotionEvent event, int width, int height) {
        if (width <= 0 || height <= 0 || !enabled()) {
            return;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                setTarget(event.getX(index), event.getY(index), width, height);
                touching = true;
                pulse(TAP);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (touching) {
                    setTarget(event.getX(0), event.getY(0), width, height);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                targetX = 0;
                targetY = 0;
                break;
        }
    }

    private static void setTarget(float px, float py, int width, int height) {
        targetX = clamp(px / width * 2f - 1f);
        targetY = clamp(py / height * 2f - 1f);
    }

    /** The eased energy, 0..1. */
    public static float energy() {
        update();
        return shown;
    }

    /** The eased finger position across the window, -1 (left) .. 1 (right). */
    public static float pointerX() {
        update();
        return x;
    }

    /** The eased finger position down the window, -1 (top) .. 1 (bottom). */
    public static float pointerY() {
        update();
        return y;
    }

    /** How far light {@code index} (0 violet, 1 blue, 2 teal) leans with the finger, in dp, before energy. */
    public static float lean(int index) {
        return LEAN_DP[Math.max(0, Math.min(LEAN_DP.length - 1, index))];
    }

    /** True while the light still moves on its own: energy fading or the lean easing out. */
    public static boolean isSettling() {
        update();
        return shown > 0.002f || Math.abs(x) > 0.002f || Math.abs(y) > 0.002f || touching;
    }

    private static void update() {
        long now = SystemClock.elapsedRealtime();
        if (!enabled()) {
            level = shown = x = y = targetX = targetY = 0;
            touching = false;
            frameTime = now;
            return;
        }
        long dt = now - frameTime;
        frameTime = now;
        if (dt <= 0) {
            return;
        }
        dt = Math.min(dt, 500);
        float energyTarget = levelAt(now);
        shown += (energyTarget - shown) * follow(dt, FOLLOW_MS);
        if (shown < 0.002f && energyTarget == 0) {
            shown = 0;
        }
        float k = follow(dt, touching ? FOLLOW_MS : RELEASE_MS);
        x += (targetX - x) * k;
        y += (targetY - y) * k;
    }

    private static float levelAt(long now) {
        if (level <= 0) {
            return 0;
        }
        float steps = (now - levelTime) / DECAY_STEP_MS;
        float value = level * (float) Math.pow(DECAY, steps);
        return value > 0.01f ? value : 0;
    }

    private static float follow(long dt, float timeConstant) {
        return 1f - (float) Math.exp(-dt / timeConstant);
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}

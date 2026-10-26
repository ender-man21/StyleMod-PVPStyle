package ru.stylemod.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import ru.stylemod.config.StyleConfig;

/** Shows a short informational window after combat damage; it never awards style. */
public final class JumpResetTracker {
    private static final long TICK_MS = 50L;
    private static final long WINDOW_MS = 3 * TICK_MS;

    private static long hitTimeMs;
    private static boolean waiting;
    private static boolean jumpKeyWasDown;
    private static boolean inputInitialized;

    private JumpResetTracker() {}

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Opens the informational timer for a grounded player hit by another entity. */
    public static void onCombatHit(float damage) {
        if (!StyleConfig.get().jumpResetEnabled || damage < 0.1f) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.onGround()) return;

        hitTimeMs = nowMs();
        waiting = true;
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            waiting = false;
            inputInitialized = false;
            jumpKeyWasDown = false;
            return;
        }

        boolean jumpDown = isJumpDown(client, player);
        if (!inputInitialized) {
            jumpKeyWasDown = jumpDown;
            inputInitialized = true;
        } else {
            boolean jumpPressed = jumpDown && !jumpKeyWasDown;
            jumpKeyWasDown = jumpDown;
            if (waiting && jumpPressed) waiting = false;
        }

        if (!StyleConfig.get().jumpResetEnabled || nowMs() - hitTimeMs >= WINDOW_MS) {
            waiting = false;
        }
    }

    private static boolean isJumpDown(Minecraft client, LocalPlayer player) {
        try {
            if (client.options.keyJump.isDown()) return true;
        } catch (Throwable ignored) {
        }
        try {
            return player.input != null && player.input.keyPresses.jump();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isWaiting() {
        return waiting;
    }

    /** Boundaries for the three 50 ms segments in the informational timer. */
    public static float[] getZoneFractions() {
        return new float[] {1f / 3f, 2f / 3f, 1f};
    }

    public static float getWaitProgress() {
        if (!waiting) return 0f;
        return Math.max(0f, Math.min(1f, (nowMs() - hitTimeMs) / (float) WINDOW_MS));
    }
}

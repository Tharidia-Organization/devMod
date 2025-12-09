package com.frenkvs.devmod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-side handler for configuration confirmation feedback.
 * Shows visual/audio feedback when server confirms config changes.
 */
public class ClientConfigFeedback {

    private static long lastConfirmTime = 0;
    private static String lastConfirmMessage = "";
    private static boolean lastConfirmSuccess = false;
    private static float fadeProgress = 0f;

    /**
     * Handle mob config confirmation from server.
     */
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        lastConfirmTime = System.currentTimeMillis();
        lastConfirmMessage = payload.message();
        lastConfirmSuccess = payload.success();
        fadeProgress = 1.0f;

        // Show action bar message
        if (payload.success()) {
            String prefix = payload.isGlobal() ? "§6[GLOBAL] " : "§a[OK] ";
            mc.player.displayClientMessage(
                Component.literal(prefix + "§f" + payload.message()),
                true
            );

            // Play success sound
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        } else {
            mc.player.displayClientMessage(
                Component.literal("§c[ERROR] §f" + payload.message()),
                true
            );

            // Play error sound
            mc.player.playSound(SoundEvents.VILLAGER_NO, 0.5f, 1.0f);
        }
    }

    /**
     * Get current confirmation state for HUD rendering.
     */
    public static ConfirmState getConfirmState() {
        long elapsed = System.currentTimeMillis() - lastConfirmTime;
        if (elapsed > 3000) {
            return null; // Expired
        }

        fadeProgress = Math.max(0, 1.0f - (elapsed / 3000.0f));
        return new ConfirmState(lastConfirmMessage, lastConfirmSuccess, fadeProgress);
    }

    public record ConfirmState(String message, boolean success, float fade) {}
}

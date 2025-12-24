package com.devmod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Objects;

/**
 * Client-side handler for configuration confirmation feedback.
 * Shows visual/audio feedback when server confirms config changes.
 */
public class ClientConfigFeedbackPayload {

    private static long lastConfirmTime = 0;
    private static String lastConfirmMessage = "";
    private static boolean lastConfirmSuccess = false;
    private static float fadeProgress = 0f;

    /**
     * Handle mob config confirmation from server.
     */
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        lastConfirmTime = System.currentTimeMillis();
        lastConfirmMessage = payload.message();
        lastConfirmSuccess = payload.success();
        fadeProgress = 1.0f;

        // Show action bar message
        if (payload.success()) {
            String prefix = payload.isGlobal() ? "§6[GLOBAL] " : "§a[OK] ";
            player.displayClientMessage(
                Objects.requireNonNull(Component.literal(prefix + "§f" + payload.message()), "message"),
                true
            );

            // Play success sound
            player.playSound(Objects.requireNonNull(SoundEvents.EXPERIENCE_ORB_PICKUP, "sound"), 0.5f, 1.2f);
        } else {
            player.displayClientMessage(
                Objects.requireNonNull(Component.literal("§c[ERROR] §f" + payload.message()), "message"),
                true
            );

            // Play error sound
            player.playSound(Objects.requireNonNull(SoundEvents.VILLAGER_NO, "sound"), 0.5f, 1.0f);
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

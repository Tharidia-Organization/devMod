package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.resonance.ResonanceNotificationPayload;

@OnlyIn(Dist.CLIENT)
public class ResonanceHudOverlay implements LayeredDraw.Layer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResonanceHudOverlay.class);

    public static final ResonanceHudOverlay INSTANCE = new ResonanceHudOverlay();

    // Active notifications
    private final List<ResonanceNotification> activeNotifications = new ArrayList<>();

    // Screen effect state
    private float screenFlashAlpha = 0f;
    private int screenFlashColor = 0xFFFFFF;
    private float shakeIntensity = 0f;
    private float shakeOffsetX = 0f;
    private float shakeOffsetY = 0f;

    // Animation constants
    private static final long NOTIFICATION_DURATION_MS = 2000;
    private static final long FLASH_DURATION_MS = 300;
    private static final float FLASH_MAX_ALPHA = 0.4f;

    private ResonanceHudOverlay() {}

    /**
     * Notification data structure.
     */
    private static class ResonanceNotification {
        final String text;
        final int color;
        final int styleBonus;
        final long startTime;
        final boolean isApocalypse;
        final boolean isTrinity;

        ResonanceNotification(ResonanceNotificationPayload payload) {
            this.text = payload.announcement();
            this.color = payload.color();
            this.styleBonus = payload.styleBonus();
            this.startTime = System.currentTimeMillis();
            this.isApocalypse = payload.isApocalypse();
            this.isTrinity = payload.isTrinity();
        }

        float getProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, (float) elapsed / NOTIFICATION_DURATION_MS);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > NOTIFICATION_DURATION_MS;
        }
    }

    /**
     * Called when a resonance notification is received from the server.
     */
    public void onResonanceTriggered(ResonanceNotificationPayload payload) {
        LOGGER.debug("[ResonanceHUD] Received: {} (+{} style)", payload.tierName(), payload.styleBonus());

        // Add notification
        activeNotifications.add(new ResonanceNotification(payload));

        // Trigger screen flash
        screenFlashColor = payload.color();
        screenFlashAlpha = FLASH_MAX_ALPHA;

        // Trigger screen shake for TRINITY and APOCALYPSE
        if (payload.isTrinity()) {
            shakeIntensity = 3.0f;
        } else if (payload.isApocalypse()) {
            shakeIntensity = 8.0f;
        }

        // Play additional client-side sound
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (payload.isApocalypse()) {
                mc.player.playSound(SoundEvents.WITHER_SPAWN, 0.5f, 1.5f);
            } else if (payload.isTrinity()) {
                mc.player.playSound(SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 2.0f);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Update shake effect
        updateShake();

        // Apply shake offset
        if (shakeIntensity > 0.1f) {
            graphics.pose().pushPose();
            graphics.pose().translate(shakeOffsetX, shakeOffsetY, 0);
        }

        // Render screen flash
        if (screenFlashAlpha > 0.01f) {
            int alpha = (int) (screenFlashAlpha * 255);
            int flashColorWithAlpha = (alpha << 24) | (screenFlashColor & 0xFFFFFF);
            graphics.fill(0, 0, screenWidth, screenHeight, flashColorWithAlpha);
            screenFlashAlpha *= 0.85f; // Decay
        }

        // Render notifications
        Iterator<ResonanceNotification> iter = activeNotifications.iterator();
        int offsetY = 0;
        while (iter.hasNext()) {
            ResonanceNotification notif = iter.next();
            if (notif.isExpired()) {
                iter.remove();
                continue;
            }

            renderNotification(graphics, notif, screenWidth, screenHeight, offsetY);
            offsetY += 40;
        }

        // Pop shake transform
        if (shakeIntensity > 0.1f) {
            graphics.pose().popPose();
        }
    }

    /**
     * Render a single notification.
     */
    private void renderNotification(GuiGraphics graphics, ResonanceNotification notif,
                                    int screenWidth, int screenHeight, int offsetY) {
        float progress = notif.getProgress();
        Minecraft mc = Minecraft.getInstance();

        // Calculate animation values
        float scale;
        float alpha;

        if (progress < 0.1f) {
            // Pop in
            float t = progress / 0.1f;
            scale = 0.5f + t * 1.5f; // 0.5 -> 2.0
            alpha = t;
        } else if (progress < 0.3f) {
            // Settle
            float t = (progress - 0.1f) / 0.2f;
            scale = 2.0f - t * 0.5f; // 2.0 -> 1.5
            alpha = 1.0f;
        } else if (progress < 0.7f) {
            // Hold
            scale = 1.5f;
            alpha = 1.0f;
        } else {
            // Fade out
            float t = (progress - 0.7f) / 0.3f;
            scale = 1.5f - t * 0.5f; // 1.5 -> 1.0
            alpha = 1.0f - t;
        }

        // Calculate position (center of screen, offset up from center)
        int textWidth = mc.font.width(notif.text);
        float x = screenWidth / 2f;
        float y = screenHeight / 3f + offsetY;

        // Apply transformations
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        // Draw glow effect for higher tiers
        if (notif.isTrinity || notif.isApocalypse) {
            int glowAlpha = (int) (alpha * 100);
            int glowColor = (glowAlpha << 24) | (notif.color & 0xFFFFFF);
            for (int i = 3; i >= 1; i--) {
                int offset = i * 2;
                graphics.drawString(mc.font, notif.text, -textWidth / 2 - offset, -offset, glowColor, false);
                graphics.drawString(mc.font, notif.text, -textWidth / 2 + offset, -offset, glowColor, false);
                graphics.drawString(mc.font, notif.text, -textWidth / 2 - offset, offset, glowColor, false);
                graphics.drawString(mc.font, notif.text, -textWidth / 2 + offset, offset, glowColor, false);
            }
        }

        // Draw main text
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | (notif.color & 0xFFFFFF);
        graphics.drawString(mc.font, notif.text, -textWidth / 2, 0, textColor, true);

        // Draw style bonus below
        String bonusText = "+" + notif.styleBonus + " STYLE";
        int bonusWidth = mc.font.width(bonusText);
        int bonusAlpha = (int) (alpha * 200);
        int bonusColor = (bonusAlpha << 24) | 0xFFFFFF;
        graphics.drawString(mc.font, bonusText, -bonusWidth / 2, 15, bonusColor, false);

        graphics.pose().popPose();
    }

    /**
     * Update screen shake effect.
     */
    private void updateShake() {
        if (shakeIntensity > 0.1f) {
            // Random shake offset
            shakeOffsetX = (float) (Math.random() * 2 - 1) * shakeIntensity;
            shakeOffsetY = (float) (Math.random() * 2 - 1) * shakeIntensity;

            // Decay shake
            shakeIntensity *= 0.9f;
        } else {
            shakeOffsetX = 0;
            shakeOffsetY = 0;
            shakeIntensity = 0;
        }
    }

    /**
     * Clear all notifications (call on quest end).
     */
    public void clear() {
        activeNotifications.clear();
        screenFlashAlpha = 0;
        shakeIntensity = 0;
    }

    /**
     * Check if any notifications are active.
     */
    public boolean hasActiveNotifications() {
        return !activeNotifications.isEmpty() || screenFlashAlpha > 0.01f;
    }
}

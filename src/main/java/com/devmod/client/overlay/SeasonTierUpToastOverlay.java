package com.devmod.client.overlay;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.endurance.season.SeasonTierUpPayload;

/**
 * Toast overlay that displays when the player reaches a new season pass tier.
 * Shows tier number, available rewards, and slides in from the top.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class SeasonTierUpToastOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonTierUpToastOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "season_tier_up_toast");

    // Timing
    private static final long DISPLAY_DURATION_MS = 5000;
    private static final long SLIDE_IN_MS = 350;
    private static final long FADE_OUT_MS = 500;

    // Layout
    private static final int BOX_WIDTH = 280;
    private static final int BOX_HEIGHT = 50;
    private static final int BOX_Y = 20;
    private static final int START_Y = -BOX_HEIGHT - 20;

    // Colors
    private static final int BG_COLOR = 0xEE1A1A2E;
    private static final int BORDER_COLOR_NORMAL = 0xFFFFD700; // Gold
    private static final int BORDER_COLOR_PREMIUM = 0xFFAA00FF; // Purple for premium
    private static final int TITLE_COLOR = 0xFFFFD700; // Gold
    private static final int REWARD_COLOR = 0xFF90EE90; // Light green
    private static final int MUTED_COLOR = 0xFFAAAAAA;

    // State
    private static boolean active = false;
    private static long startTime = 0;
    private static int displayTier = 0;
    private static String rewardText = "";
    private static boolean isPremiumTier = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.TITLE),
            Objects.requireNonNull(LAYER_ID),
            SeasonTierUpToastOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // Don't show when in a screen

        Font font = mc.font;
        if (font == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DISPLAY_DURATION_MS) {
            dismiss();
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;

        // Calculate animation progress
        float slideProgress = calculateSlideProgress(elapsed);
        float alpha = calculateAlpha(elapsed);

        // Current Y position with slide
        int currentY = (int) Mth.lerp(slideProgress, START_Y, BOX_Y);
        int x = centerX - BOX_WIDTH / 2;

        // Skip if not visible
        if (alpha < 0.01f) return;

        int boxAlpha = (int) (alpha * 240);
        int borderAlpha = (int) (alpha * 255);
        int textAlpha = (int) (alpha * 255);

        // Draw border (gold for normal, purple for premium)
        int borderColor = isPremiumTier ? BORDER_COLOR_PREMIUM : BORDER_COLOR_NORMAL;
        int borderColorWithAlpha = (borderAlpha << 24) | (borderColor & 0x00FFFFFF);
        graphics.fill(x - 2, currentY - 2, x + BOX_WIDTH + 2, currentY + BOX_HEIGHT + 2, borderColorWithAlpha);

        // Draw background
        int bgColor = (boxAlpha << 24) | (BG_COLOR & 0x00FFFFFF);
        graphics.fill(x, currentY, x + BOX_WIDTH, currentY + BOX_HEIGHT, bgColor);

        // Content
        int contentY = currentY + 10;

        // Title - "TIER X UNLOCKED!"
        String title = "TIER " + displayTier + " UNLOCKED!";
        int titleColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centerX - titleWidth / 2, contentY, titleColor, true);
        contentY += 16;

        // Reward info
        String reward = rewardText;
        if (reward != null && !reward.isEmpty()) {
            int rewardColor = (textAlpha << 24) | (REWARD_COLOR & 0x00FFFFFF);
            int rewardWidth = font.width(reward);
            graphics.drawString(font, reward, centerX - rewardWidth / 2, contentY, rewardColor, false);
        } else {
            String noReward = "Keep going!";
            int mutedColor = (textAlpha << 24) | (MUTED_COLOR & 0x00FFFFFF);
            int noRewardWidth = font.width(noReward);
            graphics.drawString(font, noReward, centerX - noRewardWidth / 2, contentY, mutedColor, false);
        }
    }

    private static float calculateSlideProgress(long elapsed) {
        if (elapsed >= SLIDE_IN_MS) return 1f;
        float t = elapsed / (float) SLIDE_IN_MS;
        // Ease out cubic
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float calculateAlpha(long elapsed) {
        if (elapsed < SLIDE_IN_MS / 2) {
            // Fade in during first half of slide
            return elapsed / (float) (SLIDE_IN_MS / 2);
        } else if (elapsed > DISPLAY_DURATION_MS - FADE_OUT_MS) {
            // Fade out
            float t = (DISPLAY_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
            return t * t;
        }
        return 1f;
    }

    // === Public API ===

    /**
     * Show the tier-up toast notification.
     */
    public static void show(SeasonTierUpPayload payload) {
        if (active) {
            // If already showing, just update the data
            displayTier = payload.newTier();
            rewardText = payload.getRewardDisplayText();
            isPremiumTier = payload.isPremium() && payload.hasPremiumReward();
            startTime = System.currentTimeMillis();
            return;
        }

        active = true;
        startTime = System.currentTimeMillis();
        displayTier = payload.newTier();
        rewardText = payload.getRewardDisplayText();
        isPremiumTier = payload.isPremium() && payload.hasPremiumReward();

        // Play tier-up sound
        Minecraft mc = Minecraft.getInstance();
        var soundManager = mc.getSoundManager();
        if (soundManager != null) {
            soundManager.play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), 1.0f, 0.8f)));
        }

        LOGGER.debug("[SeasonPass] Showing tier-up toast for tier {}", displayTier);
    }

    /**
     * Dismiss the toast early.
     */
    public static void dismiss() {
        if (!active) return;
        active = false;
        startTime = 0;
        displayTier = 0;
        rewardText = "";
        isPremiumTier = false;
    }

    /**
     * Check if the toast is currently active.
     */
    public static boolean isActive() {
        return active;
    }
}

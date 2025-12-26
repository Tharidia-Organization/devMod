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
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class WelcomeToastOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeToastOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "welcome_toast");

    // Timing
    private static final long DISPLAY_DURATION_MS = 8000;
    private static final long SLIDE_IN_MS = 400;
    private static final long FADE_OUT_MS = 600;

    // Layout
    private static final int BOX_WIDTH = 340;
    private static final int BOX_HEIGHT = 60;
    private static final int BOX_Y = 15;
    private static final int START_Y = -BOX_HEIGHT - 20;

    // Colors
    private static final int BG_COLOR = 0xEE1A1A2E;
    private static final int BORDER_COLOR = 0xFF4CAF50;
    private static final int TITLE_COLOR = 0xFF81C784;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFFFD54F;
    private static final int MUTED_COLOR = 0xFF888888;

    // State
    private static boolean active = false;
    private static long startTime = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.TITLE),
            Objects.requireNonNull(LAYER_ID),
            WelcomeToastOverlay::render
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

        // Draw border
        int borderColor = (borderAlpha << 24) | (BORDER_COLOR & 0x00FFFFFF);
        graphics.fill(x - 2, currentY - 2, x + BOX_WIDTH + 2, currentY + BOX_HEIGHT + 2, borderColor);

        // Draw background
        int bgColor = (boxAlpha << 24) | (BG_COLOR & 0x00FFFFFF);
        graphics.fill(x, currentY, x + BOX_WIDTH, currentY + BOX_HEIGHT, bgColor);

        // Content
        int contentX = x + 12;
        int contentY = currentY + 10;

        // Title
        String title = I18n.translate("devmod.onboarding.toast_title").getString();
        int titleColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        graphics.drawString(font, title, contentX, contentY, titleColor, false);
        contentY += 14;

        // Key binding info
        String keyName = KeyInputHandler.OPEN_RADIAL_MENU_KEY.getTranslatedKeyMessage().getString();
        String tip = I18n.translate("devmod.onboarding.toast_tip", keyName).getString();
        int textColor = (textAlpha << 24) | (TEXT_COLOR & 0x00FFFFFF);
        graphics.drawString(font, tip, contentX, contentY, textColor, false);
        contentY += 12;

        // Dismiss hint
        String dismissHint = I18n.translate("devmod.onboarding.toast_dismiss").getString();
        int hintColor = (textAlpha << 24) | (MUTED_COLOR & 0x00FFFFFF);
        graphics.drawString(font, dismissHint, contentX, contentY, hintColor, false);
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
            float t = elapsed / (float) (SLIDE_IN_MS / 2);
            return t;
        } else if (elapsed > DISPLAY_DURATION_MS - FADE_OUT_MS) {
            // Fade out
            float t = (DISPLAY_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
            return t * t;
        }
        return 1f;
    }

    // === Public API ===

    /**
     * Show the welcome toast notification.
     * Called when welcome screen couldn't be displayed.
     */
    public static void show() {
        if (active) return; // Don't show if already active

        active = true;
        startTime = System.currentTimeMillis();

        // Play a subtle sound
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.5f)
            ));
        }

        // Mark as seen
        SettingsManager.INSTANCE.getSettings().onboarding.hasSeenWelcome = true;
        SettingsManager.INSTANCE.markDirty();

        LOGGER.info("[WelcomeToast] Showing welcome toast notification");
    }

    /**
     * Dismiss the toast early.
     */
    public static void dismiss() {
        if (!active) return;
        active = false;
        startTime = 0;
        LOGGER.debug("[WelcomeToast] Toast dismissed");
    }

    /**
     * Check if the toast is currently active.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Handle ESC key to dismiss the toast.
     * Returns true if handled (should cancel default ESC behavior).
     */
    public static boolean handleEscape() {
        if (active) {
            dismiss();
            return true;
        }
        return false;
    }
}

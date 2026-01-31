package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.abilities.ClientStaminaCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class StaminaHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "stamina_hud");

    // Bar dimensions
    private static final int BAR_WIDTH = 91;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_BORDER = 1;

    // Colors (delegating to OverlayTheme.Stamina)
    private static final int COLOR_BACKGROUND = OverlayTheme.Stamina.BG;
    private static final int COLOR_BORDER = OverlayTheme.Stamina.BORDER;
    private static final int COLOR_STAMINA_FULL = OverlayTheme.Stamina.FULL;
    private static final int COLOR_STAMINA_MEDIUM = OverlayTheme.Stamina.MEDIUM;
    private static final int COLOR_STAMINA_LOW = OverlayTheme.Stamina.LOW;
    private static final int COLOR_STAMINA_EXHAUSTED = OverlayTheme.Stamina.EXHAUSTED;
    private static final int COLOR_STAMINA_REGEN = OverlayTheme.Stamina.REGEN;

    // Visibility control (disabled by default - enable via radial menu)
    private static boolean enabled = false;
    private static long lastNotFullTime = 0;
    private static final long HIDE_DELAY_MS = 3000; // Hide bar 3 seconds after full

    // Animation
    private static float displayedStamina = 100.0f;
    private static final float SMOOTH_SPEED = 0.15f;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            StaminaHudOverlay::render
        );
    }

    /**
     * Toggle stamina HUD visibility.
     */
    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * Check if stamina HUD is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Set stamina HUD enabled state.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // Skip in creative/spectator
        if (player.isCreative() || player.isSpectator()) return;

        float currentStamina = ClientStaminaCache.getCurrentStamina();
        float maxStamina = ClientStaminaCache.getMaxStamina();

        // Skip if data is stale
        if (ClientStaminaCache.isStale()) return;

        float percent = maxStamina > 0 ? currentStamina / maxStamina : 0;

        // Track when stamina is not full for auto-hide
        if (percent < 0.99f) {
            lastNotFullTime = System.currentTimeMillis();
        }

        // Auto-hide when full for some time
        long timeSinceNotFull = System.currentTimeMillis() - lastNotFullTime;
        if (percent >= 0.99f && timeSinceNotFull > HIDE_DELAY_MS) {
            return;
        }

        // Smooth animation
        displayedStamina += (currentStamina - displayedStamina) * SMOOTH_SPEED;
        float displayPercent = maxStamina > 0 ? displayedStamina / maxStamina : 0;

        // Calculate position (centered above hotbar)
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int sBarWidth = UIScaleManager.scale(BAR_WIDTH);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);
        int sBarBorder = UIScaleManager.scale(BAR_BORDER);
        int sIndicatorSize = UIScaleManager.scale(4);
        int sIndicatorGap = UIScaleManager.scale(2);
        int sOffsetY = UIScaleManager.scale(52); // Above hotbar

        int x = (screenWidth - sBarWidth) / 2;
        int y = screenHeight - sOffsetY;
        int totalHeight = sBarHeight + sIndicatorGap + sIndicatorSize + sBarBorder * 2;
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        y = Math.max(safeTop, Math.min(y, safeBottom - totalHeight));

        // Fade effect when near full
        float alpha = 1.0f;
        if (percent >= 0.99f) {
            alpha = Math.max(0, 1.0f - (timeSinceNotFull / (float) HIDE_DELAY_MS));
        }

        if (alpha <= 0) return;

        // Draw background
        int bgColor = applyAlpha(COLOR_BACKGROUND, alpha);
        int borderColor = applyAlpha(COLOR_BORDER, alpha);

        graphics.fill(
            x - sBarBorder,
            y - sBarBorder,
            x + sBarWidth + sBarBorder,
            y + sBarHeight + sBarBorder,
            borderColor
        );

        graphics.fill(x, y, x + sBarWidth, y + sBarHeight, bgColor);

        // Choose color based on stamina level
        int staminaColor;
        if (percent <= 0) {
            staminaColor = COLOR_STAMINA_EXHAUSTED;
        } else if (percent < 0.25f) {
            staminaColor = COLOR_STAMINA_LOW;
        } else if (percent < 0.5f) {
            staminaColor = COLOR_STAMINA_MEDIUM;
        } else {
            staminaColor = COLOR_STAMINA_FULL;
        }

        // Pulse effect when regenerating
        if (displayedStamina < currentStamina) {
            long pulse = System.currentTimeMillis() % 500;
            if (pulse < 250) {
                staminaColor = lerpColor(staminaColor, COLOR_STAMINA_REGEN, pulse / 250.0f);
            } else {
                staminaColor = lerpColor(COLOR_STAMINA_REGEN, staminaColor, (pulse - 250) / 250.0f);
            }
        }

        staminaColor = applyAlpha(staminaColor, alpha);

        // Draw stamina bar
        int filledWidth = (int) (sBarWidth * Math.max(0, Math.min(1, displayPercent)));
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + sBarHeight, staminaColor);
        }

        // Draw ability cooldown indicators
        renderCooldownIndicators(graphics, x, y, sBarWidth, sBarHeight, sIndicatorSize, sIndicatorGap, alpha);
    }

    /**
     * Render small indicators for dash/dodge cooldowns.
     */
    private static void renderCooldownIndicators(GuiGraphics graphics, int barX, int barY,
                                                 int barWidth, int barHeight,
                                                 int indicatorSize, int indicatorGap,
                                                 float alpha) {
        // Small indicators below the stamina bar
        int indicatorY = barY + barHeight + indicatorGap;

        // These would need network sync for actual cooldown data
        // For now, just show placeholder slots
        int dashX = barX + barWidth / 3 - indicatorSize / 2;
        int dodgeX = barX + 2 * barWidth / 3 - indicatorSize / 2;

        int readyColor = applyAlpha(COLOR_STAMINA_FULL, alpha);
        int borderCol = applyAlpha(COLOR_BACKGROUND, alpha);

        // Dash indicator (left)
        graphics.fill(dashX - 1, indicatorY - 1, dashX + indicatorSize + 1, indicatorY + indicatorSize + 1, borderCol);
        graphics.fill(dashX, indicatorY, dashX + indicatorSize, indicatorY + indicatorSize, readyColor);

        // Dodge indicator (right)
        graphics.fill(dodgeX - 1, indicatorY - 1, dodgeX + indicatorSize + 1, indicatorY + indicatorSize + 1, borderCol);
        graphics.fill(dodgeX, indicatorY, dodgeX + indicatorSize, indicatorY + indicatorSize, readyColor);
    }

    /**
     * Apply alpha to a color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | OverlayTheme.stripAlpha(color);
    }

    /**
     * Lerp between two colors.
     */
    private static int lerpColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

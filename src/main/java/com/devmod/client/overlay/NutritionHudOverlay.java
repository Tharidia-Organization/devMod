package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.endurance.ClientNutritionCache;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.compat.mods.easydiet.EasyDietCompat;
import com.devmod.endurance.nutrition.NutritionCategory;

/**
 * HUD overlay displaying nutrition status during Endurance quests.
 *
 * <p>Shows 6 compact bars representing each nutrition category when
 * Easy-Diet is installed and player is in an active quest.
 *
 * @author DevMod Team
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class NutritionHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "nutrition_hud");

    // Layout
    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_GAP = 2;
    private static final int BAR_BORDER = 1;
    private static final int PADDING = 4;
    private static final int LABEL_WIDTH = 8; // Space for category letter
    private static final int MODIFIER_LINE_HEIGHT = 9; // Height for modifier text lines

    // Colors
    private static final int COLOR_BACKGROUND = DesignTokens.Nutrition.HUD_BG;
    private static final int COLOR_BORDER = DesignTokens.Nutrition.HUD_BORDER;
    private static final int COLOR_WELL_FED = DesignTokens.Nutrition.HUD_WELL_FED;
    private static final int COLOR_CRITICAL = DesignTokens.Nutrition.HUD_CRITICAL;

    // Visibility control
    private static boolean enabled = true;

    // FIX AUDIT #5: Configurable position offsets (from bottom-left)
    // Can be adjusted by config or other mods to avoid HUD collisions
    private static int offsetX = 10;      // Default: 10px from left
    private static int offsetY = 50;      // Default: 50px above hotbar

    // FIX AUDIT #3: Reasonable bounds for offsets to prevent off-screen rendering
    private static final int MIN_OFFSET = 0;
    private static final int MAX_OFFSET_X = 500;  // Max distance from left
    private static final int MAX_OFFSET_Y = 400;  // Max distance from bottom

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            NutritionHudOverlay::render
        );
    }

    /**
     * Toggle nutrition HUD visibility.
     */
    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * Check if nutrition HUD is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Set nutrition HUD enabled state.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * FIX AUDIT #5: Get current X offset from left edge.
     */
    public static int getOffsetX() {
        return offsetX;
    }

    /**
     * FIX AUDIT #5: Set X offset from left edge.
     * FIX AUDIT #3: Clamped to valid range to prevent off-screen rendering.
     * @param x pixels from left (default: 10, range: 0-500)
     */
    public static void setOffsetX(int x) {
        offsetX = Math.max(MIN_OFFSET, Math.min(MAX_OFFSET_X, x));
    }

    /**
     * FIX AUDIT #5: Get current Y offset from bottom (above hotbar).
     */
    public static int getOffsetY() {
        return offsetY;
    }

    /**
     * FIX AUDIT #5: Set Y offset from bottom.
     * FIX AUDIT #3: Clamped to valid range to prevent off-screen rendering.
     * @param y pixels above hotbar (default: 50, range: 0-400)
     */
    public static void setOffsetY(int y) {
        offsetY = Math.max(MIN_OFFSET, Math.min(MAX_OFFSET_Y, y));
    }

    /**
     * FIX AUDIT #5: Set both position offsets at once.
     * FIX AUDIT #3: Values clamped to valid ranges.
     * @param x pixels from left (range: 0-500)
     * @param y pixels above hotbar (range: 0-400)
     */
    public static void setPosition(int x, int y) {
        offsetX = Math.max(MIN_OFFSET, Math.min(MAX_OFFSET_X, x));
        offsetY = Math.max(MIN_OFFSET, Math.min(MAX_OFFSET_Y, y));
    }

    /**
     * FIX AUDIT2 #7: Get minimum allowed offset value.
     * @return minimum offset (0)
     */
    public static int getMinOffset() {
        return MIN_OFFSET;
    }

    /**
     * FIX AUDIT2 #7: Get maximum allowed X offset.
     * @return maximum X offset from left edge (500)
     */
    public static int getMaxOffsetX() {
        return MAX_OFFSET_X;
    }

    /**
     * FIX AUDIT2 #7: Get maximum allowed Y offset.
     * @return maximum Y offset from bottom (400)
     */
    public static int getMaxOffsetY() {
        return MAX_OFFSET_Y;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        // Only show when Easy-Diet is available and we have data
        if (!EasyDietCompat.isAvailable()) return;
        if (!ClientNutritionCache.isAvailable()) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // Skip in creative/spectator
        if (player.isCreative() || player.isSpectator()) return;

        // Cache font reference with null safety
        Font font = Objects.requireNonNull(mc.font);

        // Calculate panel dimensions (include label width)
        int panelWidth = LABEL_WIDTH + BAR_WIDTH + PADDING * 2;
        int barsHeight = (BAR_HEIGHT + BAR_GAP) * NutritionCategory.COUNT - BAR_GAP;

        // FIX UX #1: Add space for modifier text when active
        int modifierLines = 0;
        if (ClientNutritionCache.isWellFed() || ClientNutritionCache.isMalnourished()) {
            modifierLines = 2; // DMG and SPD lines
        }
        int panelHeight = barsHeight + PADDING * 2 + (modifierLines * MODIFIER_LINE_HEIGHT);

        // FIX AUDIT #5: Use configurable position offsets
        int screenHeight = graphics.guiHeight();
        int x = offsetX;
        int y = screenHeight - panelHeight - offsetY;

        // Draw panel background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_BACKGROUND);

        // Draw border based on nutrition state
        int borderColor = COLOR_BORDER;
        if (ClientNutritionCache.isCritical()) {
            // Pulsing red border when critical
            long pulse = System.currentTimeMillis() % 1000;
            float intensity = (float) Math.sin(pulse / 1000.0 * Math.PI * 2.0) * 0.5f + 0.5f;
            borderColor = lerpColor(COLOR_BORDER, COLOR_CRITICAL, intensity);
        } else if (ClientNutritionCache.isWellFed()) {
            // Subtle green tint when well-fed
            borderColor = lerpColor(COLOR_BORDER, COLOR_WELL_FED, 0.3f);
        }

        // Draw border
        graphics.fill(x - BAR_BORDER, y - BAR_BORDER, x + panelWidth + BAR_BORDER, y, borderColor);
        graphics.fill(x - BAR_BORDER, y + panelHeight, x + panelWidth + BAR_BORDER, y + panelHeight + BAR_BORDER, borderColor);
        graphics.fill(x - BAR_BORDER, y, x, y + panelHeight, borderColor);
        graphics.fill(x + panelWidth, y, x + panelWidth + BAR_BORDER, y + panelHeight, borderColor);

        // Draw each nutrition bar with category label
        int labelX = x + PADDING;
        int barX = x + PADDING + LABEL_WIDTH;
        int barY = y + PADDING;

        for (NutritionCategory cat : NutritionCategory.VALUES) {
            float value = ClientNutritionCache.getValue(cat);
            // FIX UX #9: Draw category label (first letter)
            String label = cat.displayName.substring(0, 1);
            int labelColor = getCategoryColor(cat, value);
            graphics.drawString(font, label, labelX, barY - 1, labelColor, false);
            // Draw the bar
            drawNutritionBar(graphics, barX, barY, cat, value);
            barY += BAR_HEIGHT + BAR_GAP;
        }

        // Draw status icon/text if critical or well-fed
        if (ClientNutritionCache.isCritical()) {
            // Warning indicator
            graphics.drawString(font, "!", x + panelWidth - 8, y + 2, DesignTokens.Nutrition.HUD_CRITICAL, false);
        } else if (ClientNutritionCache.isWellFed()) {
            // Checkmark indicator
            graphics.drawString(font, "+", x + panelWidth - 8, y + 2, DesignTokens.Nutrition.HUD_WELL_FED, false);
        }

        // FIX UX #1: Draw modifier text when well-fed or malnourished
        if (modifierLines > 0) {
            int textY = y + PADDING + barsHeight + 2;
            float dmgMult = ClientNutritionCache.getDamageMultiplier();
            float spdMult = ClientNutritionCache.getSpeedMultiplier();

            // Format as percentage change from 1.0
            int dmgPercent = Math.round((dmgMult - 1.0f) * 100);
            int spdPercent = Math.round((spdMult - 1.0f) * 100);

            // Damage modifier line
            String dmgText = "DMG: " + (dmgPercent >= 0 ? "+" : "") + dmgPercent + "%";
            int dmgColor = dmgPercent > 0 ? DesignTokens.Nutrition.MOD_POSITIVE
                : (dmgPercent < 0 ? DesignTokens.Nutrition.MOD_NEGATIVE : DesignTokens.Nutrition.MOD_NEUTRAL);
            graphics.drawString(font, dmgText, x + PADDING, textY, dmgColor, false);

            // Speed modifier line
            String spdText = "SPD: " + (spdPercent >= 0 ? "+" : "") + spdPercent + "%";
            int spdColor = spdPercent > 0 ? DesignTokens.Nutrition.MOD_POSITIVE
                : (spdPercent < 0 ? DesignTokens.Nutrition.MOD_NEGATIVE : DesignTokens.Nutrition.MOD_NEUTRAL);
            graphics.drawString(font, spdText, x + PADDING, textY + MODIFIER_LINE_HEIGHT, spdColor, false);
        }
    }

    private static void drawNutritionBar(GuiGraphics graphics, int x, int y,
                                         NutritionCategory category, float value) {
        // Background
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, DesignTokens.Nutrition.BAR_BG);

        // Fill bar with category color
        int fillWidth = (int) (BAR_WIDTH * Math.max(0, Math.min(1, value)));
        if (fillWidth > 0) {
            int color = getCategoryColor(category, value);
            graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, color);
        }

        // Warning pulse for low values
        if (value < 0.2f) {
            long pulse = System.currentTimeMillis() % 500;
            float intensity = (float) Math.sin(pulse / 500.0 * Math.PI * 2.0) * 0.3f;
            if (intensity > 0) {
                int warnColor = applyAlpha(DesignTokens.Nutrition.HUD_CRITICAL, intensity);
                graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, warnColor);
            }
        }
    }

    private static int getCategoryColor(NutritionCategory category, float value) {
        int baseColor = category.color | DesignTokens.Mask.ALPHA;

        // Desaturate when low
        if (value < 0.2f) {
            return lerpColor(DesignTokens.Nutrition.BAR_LOW, baseColor, value / 0.2f);
        }

        return baseColor;
    }

    /**
     * Apply alpha to a color.
     */
    private static int applyAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
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

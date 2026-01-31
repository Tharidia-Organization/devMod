package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.overlay.OverlayTheme;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class QuickHelpOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "quick_help_hud");

    // Colors (delegating to OverlayTheme)
    private static final int PANEL_BG = OverlayTheme.Panel.BG_STANDARD;
    private static final int PANEL_BORDER = OverlayTheme.Border.SUCCESS;
    private static final int TEXT_TITLE = OverlayTheme.Help.TITLE;
    private static final int TEXT_KEY = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_DESC = OverlayTheme.Text.MUTED;
    private static final int TEXT_CATEGORY = OverlayTheme.Help.CATEGORY;

    // Layout
    private static final int PANEL_PADDING = OverlayTheme.Dimension.PADDING_COMFORTABLE;
    private static final int LINE_HEIGHT = 12; // Slightly larger than standard for readability
    private static final int KEY_WIDTH = 50;

    // State
    private static boolean enabled = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            QuickHelpOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate panel size
        int panelWidth = 280;
        int panelHeight = calculatePanelHeight();

        // Position: center-right of screen
        int panelX = screenWidth - panelWidth - 20;
        int panelY = (screenHeight - panelHeight) / 2;

        // Draw panel background
        graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);

        int y = panelY + PANEL_PADDING;
        int x = panelX + PANEL_PADDING;
        // Title
        var safeFont = Objects.requireNonNull(font);
        graphics.drawCenteredString(safeFont, "DevMod Quick Help (F1)", panelX + panelWidth / 2, y, TEXT_TITLE);
        y += LINE_HEIGHT + 6;

        // Separator
        graphics.fill(panelX + 10, y, panelX + panelWidth - 10, y + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
        y += 6;

        // Core Controls
        y = renderCategory(graphics, font, x, y, "Core Controls");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.OPEN_SETTINGS_KEY, "Settings Panel");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.OPEN_WEAPON_EDITOR_KEY, "Weapon Editor");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.OPEN_DASHBOARD_KEY, "Telemetry Dashboard");
        y += 4;

        // Debug Overlays
        y = renderCategory(graphics, font, x, y, "Debug Overlays");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, "Debug Renderer");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY, "Light Levels");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_HEATMAP_KEY, "Heatmaps");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, "Room Bounds");
        y += 4;

        // Spatial Analysis
        y = renderCategory(graphics, font, x, y, "Spatial & AI");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_PATHFINDING_KEY, "Pathfinding");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_LOS_KEY, "Line of Sight");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_SAFE_SPOT_KEY, "Safe Spots");
        y += 4;

        // Quest System
        y = renderCategory(graphics, font, x, y, "Quest System");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_QUEST_HUD_KEY, "Quest HUD");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY, "Endurance Quest");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.QUEST_CONTINUE_KEY, "Continue (in quest)");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.QUEST_EXIT_KEY, "Exit Quest");
        y += 4;

        // Performance
        y = renderCategory(graphics, font, x, y, "Performance");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_FPS_TRACKER_KEY, "FPS Tracker");
        y = renderKeybind(graphics, font, x, y, KeyInputHandler.TOGGLE_PROFILER_KEY, "Profiler");

        // Footer hint
        y += 8;
        graphics.drawCenteredString(safeFont, "Press K for full settings", panelX + panelWidth / 2, y, OverlayTheme.Help.HINT);
    }

    private static int renderCategory(GuiGraphics graphics, Font font, int x, int y, String name) {
        graphics.drawString(Objects.requireNonNull(font), name, x, y, TEXT_CATEGORY, false);
        return y + LINE_HEIGHT + 2;
    }

    private static int renderKeybind(GuiGraphics graphics, Font font, int x, int y,
                                      KeyMapping key, String description) {
        // Skip unbound keys - they show as "Not bound" or similar
        if (key.isUnbound()) {
            return y; // Don't advance Y, effectively hiding this entry
        }

        var safeFont = Objects.requireNonNull(font);
        // Key badge
        String keyName = Objects.requireNonNull(key.getTranslatedKeyMessage().getString());
        int keyBgWidth = Math.max(KEY_WIDTH, safeFont.width(keyName) + 8);

        graphics.fill(x, y - 1, x + keyBgWidth, y + LINE_HEIGHT - 1, OverlayTheme.Help.KEY_BG);

        int keyTextX = x + (keyBgWidth - safeFont.width(Objects.requireNonNull(keyName))) / 2;
        graphics.drawString(safeFont, keyName, keyTextX, y, TEXT_KEY, false);

        // Description
        graphics.drawString(safeFont, description, x + keyBgWidth + 8, y, TEXT_DESC, false);

        return y + LINE_HEIGHT + 1;
    }

    private static int calculatePanelHeight() {
        // Title + separator + 4 categories with entries + footer
        return PANEL_PADDING * 2
            + LINE_HEIGHT + 6     // Title
            + 6                    // Separator
            + (LINE_HEIGHT + 2) * 4  // 4 category headers
            + (LINE_HEIGHT + 1) * 16 // 16 keybinds
            + 4 * 4                // 4 category spacings
            + LINE_HEIGHT + 8;     // Footer
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            // Notify onboarding tutorial that help was opened
            OnboardingOverlay.onHelpOpened();
        }
    }
}

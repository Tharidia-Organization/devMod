package com.devmod.client.ui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.rendering.HeatmapVisualizer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.LineOfSightVisualizer;
import com.devmod.client.rendering.PathfindingDebugger;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.client.rendering.SafeSpotVisualizer;
import com.devmod.client.rendering.VerticalLevelsVisualizer;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.rendering.HeatmapType;
import com.devmod.telemetry.TelemetryService;
import com.devmod.testing.stats.EnvironmentalDamageStats;
import com.devmod.testing.stats.HazardTypeRegistry.HazardType;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class TelemetryDashboardScreen extends Screen {

    private static final int CONTENT_WIDTH = 320;
    private static final int TAB_WIDTH = 80;
    private static final int TAB_HEIGHT = 22;
    private static final int ROW_HEIGHT = 26;
    private static final int STATS_PAGE_SIZE = 10;

    @Nullable
    private final Screen parent;
    private DashboardTab currentTab = DashboardTab.OVERLAYS;

    private List<String> cachedStats = new ArrayList<>();
    private int scrollOffset = 0;
    private int mouseX, mouseY;
    private int scaledRowHeight;

    // Blur control
    private int originalBlurValue = 0;

    // Auto-refresh for Stats tab
    private boolean autoRefresh = false;
    private long lastRefreshTime = 0;
    private static final long AUTO_REFRESH_INTERVAL_MS = 2000; // 2 seconds

    // Confirmation state for destructive actions
    private boolean showClearConfirmation = false;
    private final EditorButton backButton = new EditorButton("tele-back", "Back");
    private final EditorButton refreshButton = new EditorButton("tele-refresh", "Refresh").style(EditorButton.Style.PRIMARY);

    private enum DashboardTab {
        OVERLAYS("Overlays"),
        OPS("Ops"),
        DATA("Data"),
        SCANS("Scans"),
        EXPORT("Export"),
        STATS("Statistics"),
        VISUALIZERS("Visualizers");

        private final String name;

        DashboardTab(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public TelemetryDashboardScreen(@Nullable Screen parent) {
        super(I18n.screenTitle("telemetry_dashboard"));
        this.parent = parent;

        // Disable menu blur when opening this screen
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    @Override
    protected void init() {
        // Custom rendering - no vanilla widgets
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Font safeFont = getSafeFont();
        scaledRowHeight = UIScaleManager.scaleMin(ROW_HEIGHT, 22);

        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        // Title
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, UIScaleManager.scale(8), "Telemetry Dashboard");

        // Tab bar with scaling
        int scaledTabWidth = UIScaleManager.scale(TAB_WIDTH);
        int scaledTabHeight = UIScaleManager.scale(TAB_HEIGHT);
        int tabStartX = (this.width - (DashboardTab.values().length * scaledTabWidth)) / 2;
        int tabY = UIScaleManager.scale(26);

        for (int i = 0; i < DashboardTab.values().length; i++) {
            DashboardTab tab = DashboardTab.values()[i];
            int tabX = tabStartX + (i * scaledTabWidth);
            boolean selected = tab == currentTab;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tabX, tabY, scaledTabWidth - 2, scaledTabHeight);

            String tabName = Objects.requireNonNull(tab.getName(), "tabName");
            AxiomRenderer.drawTab(graphics, safeFont, tabX, tabY, scaledTabWidth - 2, scaledTabHeight, tabName, selected, hovered);
        }

        // Content area with scaling
        int scaledContentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int contentX = (this.width - scaledContentWidth) / 2;
        int contentY = tabY + scaledTabHeight + UIScaleManager.scale(16);

        // Tab-specific content
        switch (currentTab) {
            case OVERLAYS -> renderOverlaysTab(graphics, contentX, contentY);
            case OPS -> renderOpsTab(graphics, contentX, contentY);
            case DATA -> renderDataTab(graphics, contentX, contentY);
            case SCANS -> renderScansTab(graphics, contentX, contentY);
            case EXPORT -> renderExportTab(graphics, contentX, contentY);
            case STATS -> renderStatsTab(graphics, contentX, contentY);
            case VISUALIZERS -> renderVisualizersTab(graphics, contentX, contentY);
        }

        // Back button
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 35;
        backButton
            .style(EditorButton.Style.NORMAL)
            .onClick(this::onClose);
        backButton.render(graphics, buttonX, buttonY, buttonWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderOverlaysTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Toggle Overlays");
        y += 20;

        // Use dynamic keybind names from KeyInputHandler
        y = drawOverlayToggle(graphics, x, y, "Debug Overlay", KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, DebugRenderer.INSTANCE.isEnabled(), DesignTokens.Accent.ORANGE());
        y = drawOverlayToggle(graphics, x, y, "Light Level", KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY, LightLevelOverlay.INSTANCE.isEnabled(), DesignTokens.Accent.YELLOW());
        y = drawOverlayToggle(graphics, x, y, "Heatmap", KeyInputHandler.TOGGLE_HEATMAP_KEY, HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(), DesignTokens.Accent.PURPLE());
        y = drawOverlayToggle(graphics, x, y, "Room Bounds", KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, RoomBoundsVisualizer.INSTANCE.isEnabled(), DesignTokens.Accent.ORANGE());
        y = drawOverlayToggle(graphics, x, y, "Pathfinding", KeyInputHandler.TOGGLE_PATHFINDING_KEY, PathfindingDebugger.INSTANCE.isEnabled(), DesignTokens.Accent.CYAN());
        y = drawOverlayToggle(graphics, x, y, "Line of Sight", KeyInputHandler.TOGGLE_LOS_KEY, LineOfSightVisualizer.INSTANCE.isEnabled(), DesignTokens.Accent.GREEN());
        y = drawOverlayToggle(graphics, x, y, "Vertical Levels", KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY, VerticalLevelsVisualizer.INSTANCE.isEnabled(), DesignTokens.Accent.YELLOW());
        drawOverlayToggle(graphics, x, y, "Safe Spots", KeyInputHandler.TOGGLE_SAFE_SPOT_KEY, SafeSpotVisualizer.INSTANCE.isEnabled(), DesignTokens.Accent.RED());
    }

    private void renderOpsTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Server Ops");
        y += 20;

        y = drawActionButton(graphics, x, y, "Open Dashboard Server", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Start Dashboard Server", DesignTokens.Accent.GREEN());
        y = drawActionButton(graphics, x, y, "Stop Dashboard Server", DesignTokens.Accent.RED());
        y = drawActionButton(graphics, x, y, "Server Status", DesignTokens.Accent.YELLOW());
        y = drawActionButton(graphics, x, y, "Reload Telemetry Config", DesignTokens.Accent.ORANGE());

        y += 8;
        y = drawActionButton(graphics, x, y, "Dungeon Help", DesignTokens.Accent.PURPLE());
        y = drawActionButton(graphics, x, y, "Dungeon Start", DesignTokens.Accent.GREEN());
        y = drawActionButton(graphics, x, y, "Dungeon End", DesignTokens.Accent.RED());
        drawActionButton(graphics, x, y, "Dungeon Status", DesignTokens.Accent.YELLOW());
    }

    private void renderDataTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Data Dumps & Exports");
        y += 20;

        y = drawActionButton(graphics, x, y, "Dump Weapons", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Dump Rooms", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Dump Fights", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Dump Minions", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Dump Dungeons", DesignTokens.Accent.CYAN());
        y += 8;
        y = drawActionButton(graphics, x, y, "Export Heatmaps (All)", DesignTokens.Accent.YELLOW());
        y = drawActionButton(graphics, x, y, "Export PNG", DesignTokens.Accent.YELLOW());
        y = drawActionButton(graphics, x, y, "Export CSV", DesignTokens.Accent.YELLOW());
        y = drawActionButton(graphics, x, y, "Export JSON", DesignTokens.Accent.YELLOW());
        drawActionButton(graphics, x, y, "Export All", DesignTokens.Accent.ORANGE());
    }

    private void renderScansTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Scans & Analysis");
        y += 20;

        y = drawActionButton(graphics, x, y, "Scan Light (All)", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Scan Light (Room)", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Spawnability Scan", DesignTokens.Accent.CYAN());
        y = drawActionButton(graphics, x, y, "Desire Lines Dump", DesignTokens.Accent.PURPLE());
        y = drawActionButton(graphics, x, y, "Desire Lines Analyze", DesignTokens.Accent.PURPLE());
        y = drawActionButton(graphics, x, y, "Backtracking Dump", DesignTokens.Accent.ORANGE());
        y = drawActionButton(graphics, x, y, "Backtracking Confusing", DesignTokens.Accent.ORANGE());
        drawActionButton(graphics, x, y, "Dungeon Stats", DesignTokens.Accent.YELLOW());
    }

    private int drawOverlayToggle(GuiGraphics graphics, int x, int y, String name, KeyMapping keyMapping, boolean enabled, int accentColor) {
        Font safeFont = getSafeFont();
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        String safeName = Objects.requireNonNull(name, "name");
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, contentWidth, scaledRowHeight - 2);

        if (hovered) {
            graphics.fill(x - 2, y, x + contentWidth + 2, y + scaledRowHeight - 2, DesignTokens.Background.HOVER());
        }

        // Accent bar
        graphics.fill(x, y + 4, x + 3, y + scaledRowHeight - 6, accentColor);

        // Name + dynamic hotkey from KeyMapping
        UIScaleManager.drawScaledString(graphics, safeFont, safeName, x + 10, y + 6, DesignTokens.Text.PRIMARY(), false);
        String hotkeyText = keyMapping.isUnbound()
            ? "unbound"
            : Objects.requireNonNull(keyMapping.getTranslatedKeyMessage().getString(), "hotkeyText");
        int hotkeyColor = keyMapping.isUnbound()
            ? DesignTokens.withAlpha(DesignTokens.Text.MUTED(), DesignTokens.Alpha.A53)
            : DesignTokens.Text.MUTED();
        String hotkeyLabel = Objects.requireNonNull("(" + hotkeyText + ")", "hotkeyLabel");
        int nameWidth = UIScaleManager.getScaledStringWidth(safeFont, safeName);
        UIScaleManager.drawScaledString(graphics, safeFont, hotkeyLabel, x + 10 + nameWidth + 6, y + 6, hotkeyColor, false);

        // Toggle
        int toggleW = UIScaleManager.scale(DesignTokens.Size.TOGGLE_WIDTH);
        int toggleH = UIScaleManager.scale(DesignTokens.Size.TOGGLE_HEIGHT);
        int toggleX = x + contentWidth - toggleW;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, y + 3, toggleW, toggleH, enabled, hovered);

        return y + scaledRowHeight;
    }

    private void renderExportTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Export Heatmap Data");
        y += 20;

        y = drawExportButton(graphics, x, y, "Death Heatmap", DesignTokens.Accent.RED());
        y = drawExportButton(graphics, x, y, "Movement Heatmap", DesignTokens.Accent.CYAN());
        y = drawExportButton(graphics, x, y, "Camping Heatmap", DesignTokens.Accent.YELLOW());
        y = drawExportButton(graphics, x, y, "Stuck Heatmap", DesignTokens.Accent.ORANGE());
        y = drawExportButton(graphics, x, y, "Aggro Drop Heatmap", DesignTokens.Accent.PURPLE());
        y = drawExportButton(graphics, x, y, "Kiting Heatmap", DesignTokens.Accent.GREEN());
        y = drawExportButton(graphics, x, y, "Choke Points", DesignTokens.Accent.RED());
        y = drawExportButton(graphics, x, y, "Parkour Falls", DesignTokens.Accent.ORANGE());
        y += 8; // Gap before damage stats
        drawExportButton(graphics, x, y, "Damage Statistics", DesignTokens.Accent.GREEN());

        // Hint
        int hintY = this.height - 60;
        AxiomRenderer.drawHint(graphics, safeFont, x, hintY, "Exports are saved to run/telemetry/");
    }

    private int drawExportButton(GuiGraphics graphics, int x, int y, String name, int accentColor) {
        return drawActionButton(graphics, x, y, "Export " + name, accentColor);
    }

    private int drawActionButton(GuiGraphics graphics, int x, int y, String label, int accentColor) {
        Font safeFont = getSafeFont();
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        String safeLabel = Objects.requireNonNull(label, "label");
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, contentWidth, scaledRowHeight - 4);

        int bgColor = hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL();
        graphics.fill(x, y, x + contentWidth, y + scaledRowHeight - 4, bgColor);

        // Accent bar
        graphics.fill(x, y, x + 3, y + scaledRowHeight - 4, accentColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, contentWidth, scaledRowHeight - 4, hovered ? DesignTokens.Border.ACCENT() : DesignTokens.Border.DEFAULT());

        // Text
        UIScaleManager.drawScaledString(graphics, safeFont, safeLabel, x + 10, y + 5, DesignTokens.Text.PRIMARY(), false);

        // Arrow
        UIScaleManager.drawScaledString(graphics, safeFont, ">", x + contentWidth - 12, y + 5, hovered ? DesignTokens.Text.ACCENT() : DesignTokens.Text.MUTED(), false);

        return y + scaledRowHeight;
    }

    private void renderStatsTab(GuiGraphics graphics, int x, int y) {
        // Auto-refresh logic
        long now = System.currentTimeMillis();
        if (autoRefresh && now - lastRefreshTime >= AUTO_REFRESH_INTERVAL_MS) {
            refreshStats();
            lastRefreshTime = now;
        } else if (cachedStats.isEmpty()) {
            refreshStats();
            lastRefreshTime = now;
        }
        clampStatsScroll();
        Font safeFont = getSafeFont();

        // Control row: Refresh button + Auto-refresh toggle
        int btnWidth = 80;
        int toggleWidth = 100;
        int totalWidth = btnWidth + 10 + toggleWidth;
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int startX = x + (contentWidth - totalWidth) / 2;

        // Manual Refresh button
        refreshButton
            .style(EditorButton.Style.PRIMARY)
            .onClick(() -> {
                refreshStats();
                lastRefreshTime = System.currentTimeMillis();
                showMessage("Stats refreshed!");
            });
        refreshButton.render(graphics, startX, y, btnWidth, DesignTokens.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Auto-refresh toggle
        int toggleX = startX + btnWidth + 10;
        boolean autoHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, toggleX, y, toggleWidth, DesignTokens.Size.BUTTON_HEIGHT);
        int toggleBg = autoHovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL();
        graphics.fill(toggleX, y, toggleX + toggleWidth, y + DesignTokens.Size.BUTTON_HEIGHT, toggleBg);
        AxiomRenderer.drawBorder(graphics, toggleX, y, toggleWidth, DesignTokens.Size.BUTTON_HEIGHT,
            autoRefresh ? DesignTokens.Accent.GREEN() : DesignTokens.Border.DEFAULT());

        String autoLabel = autoRefresh ? "Auto: ON" : "Auto: OFF";
        int autoColor = autoRefresh ? DesignTokens.Accent.GREEN() : DesignTokens.Text.SECONDARY();
        int labelWidth = UIScaleManager.getScaledStringWidth(safeFont, autoLabel);
        UIScaleManager.drawScaledString(graphics, safeFont, autoLabel, toggleX + (toggleWidth - labelWidth) / 2, y + 6, autoColor, false);

        y += 30;

        // Stats panel
        int panelHeight = Math.min(STATS_PAGE_SIZE, cachedStats.size()) * 12 + 16;
        AxiomRenderer.drawSimplePanel(graphics, x, y, contentWidth, panelHeight);

        int textY = y + 8;
        int maxDisplay = Math.min(STATS_PAGE_SIZE, cachedStats.size() - scrollOffset);

        graphics.enableScissor(x, y, x + contentWidth, y + panelHeight);
        try {
            for (int i = 0; i < maxDisplay; i++) {
                int index = i + scrollOffset;
                if (index < cachedStats.size()) {
                    String line = cachedStats.get(index);
                    UIScaleManager.drawScaledString(graphics, safeFont, line, x + 8, textY, DesignTokens.Text.PRIMARY(), false);
                    textY += 12;
                }
            }
        } finally {
            graphics.disableScissor();
        }

        // Scroll indicator - always visible when scrollable
        if (cachedStats.size() > STATS_PAGE_SIZE) {
            String scrollInfo = String.format("\u00A7e[%d-%d / %d]\u00A7r",
                scrollOffset + 1,
                Math.min(scrollOffset + STATS_PAGE_SIZE, cachedStats.size()),
                cachedStats.size());

            // Draw scroll hint bar at bottom
            int hintY = y + panelHeight + 4;
            graphics.fill(x, hintY, x + contentWidth, hintY + 16, DesignTokens.Background.PANEL());
            AxiomRenderer.drawBorder(graphics, x, hintY, contentWidth, 16, DesignTokens.Border.DEFAULT());

            String hintText = Objects.requireNonNull("\u00A77Scroll/↑↓/PgUp/PgDn\u00A7r | " + scrollInfo, "hintText");
            String hintTextPlain = Objects.requireNonNull(hintText.replaceAll("\u00A7.", ""), "hintTextPlain");
            int textWidth = UIScaleManager.getScaledStringWidth(safeFont, hintTextPlain); // Strip color codes for width
            UIScaleManager.drawScaledString(graphics, safeFont, hintText, x + (contentWidth - textWidth) / 2, hintY + 4, DesignTokens.Accent.YELLOW(), false);

            // Show scroll arrows if not at edges
            if (scrollOffset > 0) {
                UIScaleManager.drawScaledString(graphics, safeFont, "▲", x + 4, hintY + 4, DesignTokens.Accent.GREEN(), false);
            }
            if (scrollOffset < cachedStats.size() - STATS_PAGE_SIZE) {
                UIScaleManager.drawScaledString(graphics, safeFont, "▼", x + contentWidth - 12, hintY + 4, DesignTokens.Accent.GREEN(), false);
            }
        }
    }

    private void refreshStats() {
        cachedStats.clear();

        int roomCount = TelemetryService.INSTANCE.getRoomDefinitions().size();
        cachedStats.add("Rooms Configured: " + roomCount);
        cachedStats.add("");

        cachedStats.add("=== Minion Wave Stats ===");
        List<String> minionStats = TelemetryService.INSTANCE.getAllMinionWaveStats();
        if (minionStats.isEmpty()) {
            cachedStats.add("No minion data yet");
        } else {
            cachedStats.addAll(minionStats);
        }
        cachedStats.add("");

        cachedStats.add("=== Entity Density ===");
        List<String> densityReport = TelemetryService.INSTANCE.getEntityDensityReport();
        if (densityReport.isEmpty()) {
            cachedStats.add("No density data yet");
        } else {
            cachedStats.addAll(densityReport);
        }
        cachedStats.add("");

        cachedStats.add("=== Active Overlays ===");
        if (DebugRenderer.INSTANCE.isEnabled()) cachedStats.add("* Debug Overlay");
        if (LightLevelOverlay.INSTANCE.isEnabled()) cachedStats.add("* Light Level");
        if (HeatmapVisualizer.INSTANCE.hasActiveHeatmaps()) cachedStats.add("* Heatmap");
        if (RoomBoundsVisualizer.INSTANCE.isEnabled()) cachedStats.add("* Room Bounds");
        if (PathfindingDebugger.INSTANCE.isEnabled()) cachedStats.add("* Pathfinding");
        if (LineOfSightVisualizer.INSTANCE.isEnabled()) cachedStats.add("* Line of Sight");
        if (VerticalLevelsVisualizer.INSTANCE.isEnabled()) cachedStats.add("* Vertical Levels");
        if (SafeSpotVisualizer.INSTANCE.isEnabled()) cachedStats.add("* Safe Spots");

        if (RoomBoundsVisualizer.INSTANCE.isEnabled()) {
            cachedStats.add("");
            cachedStats.add("Room Bounds: " + RoomBoundsVisualizer.INSTANCE.getRoomCount() + " rooms");
        }

        if (SafeSpotVisualizer.INSTANCE.isEnabled()) {
            cachedStats.add("Safe Spots: " + SafeSpotVisualizer.INSTANCE.getSafeSpotCount() + " spots");
        }

        // Environmental Damage Statistics
        cachedStats.add("");
        cachedStats.add("=== Environmental Damage ===");
        EnvironmentalDamageStats envStats = EnvironmentalDamageStats.INSTANCE;
        double totalEnv = envStats.getTotalEnvironmentalDamage();

        if (totalEnv > 0) {
            cachedStats.add(String.format("Total: %.1f damage", totalEnv));

            // Show non-zero hazard types
            for (HazardType type : HazardType.values()) {
                double dmg = envStats.getDamage(type);
                if (dmg > 0.1) {
                    String icon = getHazardIcon(type);
                    cachedStats.add(String.format("  %s %s: %.1f", icon, formatHazardName(type), dmg));
                }
            }

            cachedStats.add(String.format("Environmental Deaths: %d", envStats.getEnvironmentalDeaths()));
            if (envStats.hasSurvivedExplosion()) {
                cachedStats.add("  ★ Survived Explosion!");
            }
        } else {
            cachedStats.add("No environmental damage recorded");
        }
        clampStatsScroll();
    }

    /**
     * Get a color-coded icon for hazard types.
     */
    private String getHazardIcon(HazardType type) {
        return switch (type) {
            case FALL -> "\u00A7e⬇";      // Yellow down arrow
            case FIRE -> "\u00A76🔥";     // Orange fire
            case LAVA -> "\u00A7c🌋";     // Red lava
            case DROWNING -> "\u00A7b💧"; // Aqua water
            case EXPLOSION -> "\u00A7c💥"; // Red explosion
            case POISON -> "\u00A7a☠";    // Green poison
            case WITHER -> "\u00A75💀";   // Purple skull
            case FREEZING -> "\u00A7f❄";  // White snowflake
            case LIGHTNING -> "\u00A7e⚡"; // Yellow lightning
            case CACTUS -> "\u00A72🌵";   // Green cactus
            case VOID -> "\u00A70⬛";      // Black void
            case MAGIC -> "\u00A7d✨";    // Magenta sparkle
            default -> "\u00A77•";        // Gray bullet
        };
    }

    /**
     * Format hazard type enum name to readable string.
     */
    private String formatHazardName(HazardType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private Font getSafeFont() {
        return Objects.requireNonNull(font, "font");
    }

    private void renderVisualizersTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Load Heatmap Visualizers");
        y += 20;

        y = drawVisualizerButton(graphics, x, y, "Death Heatmap", HeatmapType.DEATH, DesignTokens.Accent.RED());
        y = drawVisualizerButton(graphics, x, y, "Movement Heatmap", HeatmapType.MOVEMENT, DesignTokens.Accent.CYAN());
        y = drawVisualizerButton(graphics, x, y, "Camping Heatmap", HeatmapType.CAMPING, DesignTokens.Accent.YELLOW());
        y = drawVisualizerButton(graphics, x, y, "Stuck Heatmap", HeatmapType.STUCK, DesignTokens.Accent.ORANGE());
        y = drawVisualizerButton(graphics, x, y, "Aggro Drop Heatmap", HeatmapType.AGGRO_DROP, DesignTokens.Accent.PURPLE());
        y = drawVisualizerButton(graphics, x, y, "Kiting Heatmap", HeatmapType.KITING, DesignTokens.Accent.GREEN());
        y += 12;

        // Clear all button (with confirmation)
        if (showClearConfirmation) {
            // Show confirmation buttons
            int btnWidth = (contentWidth - 10) / 2;

            // "Confirm" button
            boolean confirmHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, btnWidth, scaledRowHeight - 4);
            int confirmBg = confirmHovered
                ? DesignTokens.TelemetryDashboard.CONFIRM_HOVER_BG
                : DesignTokens.Background.PANEL();
            graphics.fill(x, y, x + btnWidth, y + scaledRowHeight - 4, confirmBg);
            graphics.fill(x, y, x + 3, y + scaledRowHeight - 4, DesignTokens.Accent.RED());
            AxiomRenderer.drawBorder(graphics, x, y, btnWidth, scaledRowHeight - 4, DesignTokens.Accent.RED());
            String confirmText = "Yes, Clear All";
            int confirmWidth = UIScaleManager.getScaledStringWidth(safeFont, confirmText);
            UIScaleManager.drawScaledString(graphics, safeFont, confirmText, x + (btnWidth - confirmWidth) / 2, y + 5, DesignTokens.Accent.RED(), false);

            // "Cancel" button
            int cancelX = x + btnWidth + 10;
            boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, y, btnWidth, scaledRowHeight - 4);
            int cancelBg = cancelHovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL();
            graphics.fill(cancelX, y, cancelX + btnWidth, y + scaledRowHeight - 4, cancelBg);
            AxiomRenderer.drawBorder(graphics, cancelX, y, btnWidth, scaledRowHeight - 4, DesignTokens.Border.DEFAULT());
            String cancelText = "Cancel";
            int cancelWidth = UIScaleManager.getScaledStringWidth(safeFont, cancelText);
            UIScaleManager.drawScaledString(graphics, safeFont, cancelText, cancelX + (btnWidth - cancelWidth) / 2, y + 5, DesignTokens.Text.PRIMARY(), false);
        } else {
            // Normal clear button
            boolean clearHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, contentWidth, scaledRowHeight);
            int clearBg = clearHovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL();
            graphics.fill(x, y, x + contentWidth, y + scaledRowHeight - 4, clearBg);
            graphics.fill(x, y, x + 3, y + scaledRowHeight - 4, DesignTokens.Accent.RED());
            AxiomRenderer.drawBorder(graphics, x, y, contentWidth, scaledRowHeight - 4, clearHovered ? DesignTokens.Accent.RED() : DesignTokens.Border.DEFAULT());
            String clearText = "Clear All Heatmaps";
            int textWidth = UIScaleManager.getScaledStringWidth(safeFont, clearText);
            UIScaleManager.drawScaledString(graphics, safeFont, clearText, x + (contentWidth - textWidth) / 2, y + 5, DesignTokens.Accent.RED(), false);
        }
    }

    private int drawVisualizerButton(GuiGraphics graphics, int x, int y, String name, HeatmapType type, int accentColor) {
        Font safeFont = getSafeFont();
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        String safeName = Objects.requireNonNull(name, "name");
        boolean enabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, contentWidth, scaledRowHeight - 4);

        int bgColor = hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.PANEL();
        graphics.fill(x, y, x + contentWidth, y + scaledRowHeight - 4, bgColor);

        // Accent bar
        graphics.fill(x, y, x + 3, y + scaledRowHeight - 4, accentColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, contentWidth, scaledRowHeight - 4, hovered ? DesignTokens.Border.ACCENT() : DesignTokens.Border.DEFAULT());

        // Text
        UIScaleManager.drawScaledString(graphics, safeFont, "Load " + safeName, x + 10, y + 5, DesignTokens.Text.PRIMARY(), false);

        // Status indicator
        String status = enabled ? "[ACTIVE]" : "";
        if (!status.isEmpty()) {
            int statusWidth = UIScaleManager.getScaledStringWidth(safeFont, status);
            UIScaleManager.drawScaledString(graphics, safeFont, status, x + contentWidth - statusWidth - 8, y + 5, DesignTokens.Accent.GREEN(), false);
        }

        return y + scaledRowHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        UIScaleManager.update();
        scaledRowHeight = UIScaleManager.scaleMin(ROW_HEIGHT, 22);
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int scaledTabWidth = UIScaleManager.scale(TAB_WIDTH);
        int scaledTabHeight = UIScaleManager.scale(TAB_HEIGHT);
        int scaledContentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int tabY = UIScaleManager.scale(26);

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Tab clicks
        int tabStartX = (this.width - (DashboardTab.values().length * scaledTabWidth)) / 2;

        for (int i = 0; i < DashboardTab.values().length; i++) {
            int tabX = tabStartX + (i * scaledTabWidth);
            if (AxiomRenderer.isMouseOver(mx, my, tabX, tabY, scaledTabWidth - 2, scaledTabHeight)) {
                currentTab = DashboardTab.values()[i];
                scrollOffset = 0;
                showClearConfirmation = false;
                return true;
            }
        }

        // Content clicks
        int contentX = (this.width - scaledContentWidth) / 2;
        int contentY = tabY + scaledTabHeight + UIScaleManager.scale(16);

        switch (currentTab) {
            case OVERLAYS -> { if (handleOverlaysClick(mx, my, contentX, contentY)) return true; }
            case OPS -> { if (handleOpsClick(mx, my, contentX, contentY)) return true; }
            case DATA -> { if (handleDataClick(mx, my, contentX, contentY)) return true; }
            case SCANS -> { if (handleScansClick(mx, my, contentX, contentY)) return true; }
            case EXPORT -> { if (handleExportClick(mx, my, contentX, contentY)) return true; }
            case STATS -> { if (handleStatsClick(mx, my, contentX, contentY)) return true; }
            case VISUALIZERS -> { if (handleVisualizersClick(mx, my, contentX, contentY)) return true; }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleOverlaysClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        String[] actionIds = {
            ActionIds.DEBUG_OVERLAY_TOGGLE,
            ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE,
            ActionIds.DEBUG_HEATMAP_TOGGLE,
            ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE,
            ActionIds.DEBUG_PATHFINDING_TOGGLE,
            ActionIds.DEBUG_LOS_TOGGLE,
            ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE,
            ActionIds.DEBUG_SAFE_SPOTS_TOGGLE
        };

        for (String actionId : actionIds) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 2)) {
                invokeAction(actionId);
                return true;
            }
            y += scaledRowHeight;
        }
        return false;
    }

    private boolean handleExportClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        String[] exportActions = {
            ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS,
            ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS
        };

        String[] exportMessages = {
            "devmod.telemetry.exported_heatmap.death",
            "devmod.telemetry.exported_heatmap.movement",
            "devmod.telemetry.exported_heatmap.camping",
            "devmod.telemetry.exported_heatmap.stuck",
            "devmod.telemetry.exported_heatmap.aggro_drop",
            "devmod.telemetry.exported_heatmap.kiting",
            "devmod.telemetry.exported_heatmap.choke_points",
            "devmod.telemetry.exported_heatmap.parkour_falls"
        };

        for (int i = 0; i < exportActions.length; i++) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                invokeAction(exportActions[i]);
                showMessage(I18n.translate(exportMessages[i]).getString());
                return true;
            }
            y += scaledRowHeight;
        }

        // Gap + Damage Statistics button
        y += 8;
        if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
            invokeAction(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS);
            showMessage(I18n.translate("devmod.telemetry.exported_damage_stats").getString());
            return true;
        }

        return false;
    }

    private boolean handleOpsClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        String[] opsActions = {
            ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN,
            ActionIds.TELEMETRY_DASHBOARD_SERVER_START,
            ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP,
            ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS,
            ActionIds.TELEMETRY_RELOAD
        };

        for (String actionId : opsActions) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                invokeAction(actionId);
                return true;
            }
            y += scaledRowHeight;
        }

        y += 8;

        String[] dungeonActions = {
            ActionIds.DUNGEON_HELP,
            ActionIds.DUNGEON_START,
            ActionIds.DUNGEON_END,
            ActionIds.DUNGEON_STATUS
        };

        for (String actionId : dungeonActions) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                invokeAction(actionId);
                return true;
            }
            y += scaledRowHeight;
        }

        return false;
    }

    private boolean handleDataClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        String[] dataActions = {
            ActionIds.TELEMETRY_DUMP_WEAPONS,
            ActionIds.TELEMETRY_DUMP_ROOMS,
            ActionIds.TELEMETRY_DUMP_FIGHTS,
            ActionIds.TELEMETRY_DUMP_MINIONS,
            ActionIds.TELEMETRY_DUNGEONS_DUMP,
            ActionIds.TELEMETRY_EXPORT_HEATMAPS,
            ActionIds.TELEMETRY_EXPORT_PNG,
            ActionIds.TELEMETRY_EXPORT_CSV,
            ActionIds.TELEMETRY_EXPORT_JSON,
            ActionIds.TELEMETRY_EXPORT_ALL
        };

        for (String actionId : dataActions) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                invokeAction(actionId);
                return true;
            }
            y += scaledRowHeight;
        }
        return false;
    }

    private boolean handleScansClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        String[] scanActions = {
            ActionIds.TELEMETRY_SCAN_LIGHT_ALL,
            ActionIds.TELEMETRY_SCAN_LIGHT_ROOM,
            ActionIds.TELEMETRY_SPAWNABILITY,
            ActionIds.TELEMETRY_DESIRELINES_DUMP,
            ActionIds.TELEMETRY_DESIRELINES_ANALYZE,
            ActionIds.TELEMETRY_BACKTRACKING_DUMP,
            ActionIds.TELEMETRY_BACKTRACKING_CONFUSING,
            ActionIds.TELEMETRY_DUNGEONS_STATS
        };

        for (String actionId : scanActions) {
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                invokeAction(actionId);
                return true;
            }
            y += scaledRowHeight;
        }

        return false;
    }

    private boolean handleStatsClick(int mx, int my, int x, int y) {
        // Control row layout matching renderStatsTab
        int btnWidth = 80;
        int toggleWidth = 100;
        int totalWidth = btnWidth + 10 + toggleWidth;
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        int startX = x + (contentWidth - totalWidth) / 2;

        // Manual Refresh button
        if (refreshButton.mouseClicked(mx, my, 0)) {
            return true;
        }

        // Auto-refresh toggle
        int toggleX = startX + btnWidth + 10;
        if (mx >= toggleX && mx < toggleX + toggleWidth && my >= y && my < y + DesignTokens.Size.BUTTON_HEIGHT) {
            autoRefresh = !autoRefresh;
            if (autoRefresh) {
                lastRefreshTime = System.currentTimeMillis();
                showMessage("Auto-refresh enabled (2s interval)");
            } else {
                showMessage("Auto-refresh disabled");
            }
            return true;
        }

        return false;
    }

    private int getMaxStatsScroll() {
        return Math.max(0, cachedStats.size() - STATS_PAGE_SIZE);
    }

    private void clampStatsScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxStatsScroll()));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        handled |= backButton.mouseReleased(mouseX, mouseY, button);
        handled |= refreshButton.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleVisualizersClick(int mx, int my, int x, int y) {
        int contentWidth = UIScaleManager.scale(CONTENT_WIDTH);
        y += 20; // section header

        HeatmapType[] types = {
            HeatmapType.DEATH,
            HeatmapType.MOVEMENT,
            HeatmapType.CAMPING,
            HeatmapType.STUCK,
            HeatmapType.AGGRO_DROP,
            HeatmapType.KITING
        };

        String[] actionIds = {
            ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE,
            ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE,
            ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE,
            ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE,
            ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE,
            ActionIds.DEBUG_HEATMAP_KITING_TOGGLE
        };

        for (int i = 0; i < types.length; i++) {
            HeatmapType type = types[i];
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                boolean currentlyEnabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
                invokeAction(actionIds[i]);
                String status = currentlyEnabled ? "\u00A7cOFF" : "\u00A7aON";
                showMessage(I18n.translate("devmod.render.heatmap_type_toggle_status", type.name(), status).getString());
                return true;
            }
            y += scaledRowHeight;
        }

        y += 12; // gap before clear button

        // Clear all button (with confirmation)
        if (showClearConfirmation) {
            int btnWidth = (contentWidth - 10) / 2;

            // "Yes, Clear All" button
            if (AxiomRenderer.isMouseOver(mx, my, x, y, btnWidth, scaledRowHeight - 4)) {
                invokeConfirmedAction(ActionIds.DEBUG_HEATMAP_CLEAR_ALL);
                showMessage(I18n.translate("devmod.render.heatmaps_cleared").getString());
                showClearConfirmation = false;
                return true;
            }

            // "Cancel" button
            int cancelX = x + btnWidth + 10;
            if (AxiomRenderer.isMouseOver(mx, my, cancelX, y, btnWidth, scaledRowHeight - 4)) {
                showClearConfirmation = false;
                return true;
            }
        } else {
            // Show confirmation first
            if (AxiomRenderer.isMouseOver(mx, my, x, y, contentWidth, scaledRowHeight - 4)) {
                showClearConfirmation = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == DashboardTab.STATS && cachedStats.size() > STATS_PAGE_SIZE) {
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(getMaxStatsScroll(), scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && showClearConfirmation) { // Escape
            showClearConfirmation = false;
            return true;
        }
        // Keyboard navigation for stats tab
        if (currentTab == DashboardTab.STATS && cachedStats.size() > STATS_PAGE_SIZE) {
            // Arrow Up / Page Up
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
                int amount = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP ? 5 : 1;
                scrollOffset = Math.max(0, scrollOffset - amount);
                return true;
            }
            // Arrow Down / Page Down
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                int amount = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 5 : 1;
                scrollOffset = Math.min(getMaxStatsScroll(), scrollOffset + amount);
                return true;
            }
            // Home - go to start
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
                scrollOffset = 0;
                return true;
            }
            // End - go to end
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
                scrollOffset = getMaxStatsScroll();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void showMessage(String message) {
        Minecraft mc = this.minecraft;
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(I18n.literal(message), true);
        }
    }

    private boolean invokeAction(String actionId) {
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI);
        return ActionRegistry.invoke(actionId, context);
    }

    private boolean invokeConfirmedAction(String actionId) {
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI).withConfirmed(true);
        return ActionRegistry.invoke(actionId, context);
    }

    @Override
    public void onClose() {
        // Restore original blur setting
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Disable blur - just solid dimmed background
        graphics.fill(0, 0, this.width, this.height, DesignTokens.TelemetryDashboard.SCRIM);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.TelemetryDashboard.SCRIM);
    }
}

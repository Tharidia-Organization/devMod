package com.devmod.ui.screens;

import com.devmod.client.input.KeyInputHandler;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.rendering.DebugRenderer;
import com.devmod.rendering.HeatmapVisualizer;
import com.devmod.rendering.LightLevelOverlay;
import com.devmod.rendering.LineOfSightVisualizer;
import com.devmod.rendering.PathfindingDebugger;
import com.devmod.rendering.RoomBoundsVisualizer;
import com.devmod.rendering.SafeSpotVisualizer;
import com.devmod.rendering.VerticalLevelsVisualizer;
import com.devmod.telemetry.TelemetryService;
import com.devmod.testing.stats.EnvironmentalDamageStats;
import com.devmod.testing.stats.HazardTypeRegistry.HazardType;
import com.devmod.ui.AxiomRenderer;
import com.devmod.ui.editor.core.UIConstants;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.util.I18n;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dashboard e Analytics Screen per la telemetria.
 * Fornisce controlli per esportare heatmap, visualizzare statistiche e toggle overlay.
 * Refactored con Axiom-style UI.
 */
@OnlyIn(Dist.CLIENT)
public class TelemetryDashboardScreen extends Screen {

    private static final int CONTENT_WIDTH = 320;
    private static final int TAB_WIDTH = 80;
    private static final int TAB_HEIGHT = 22;
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private DashboardTab currentTab = DashboardTab.OVERLAYS;

    private List<String> cachedStats = new ArrayList<>();
    private int scrollOffset = 0;
    private int mouseX, mouseY;

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

    public TelemetryDashboardScreen(Screen parent) {
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
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Font safeFont = getSafeFont();

        // Dark background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        // Title
        AxiomRenderer.drawCenteredTitle(graphics, safeFont, this.width, UIConstants.Position.TITLE_Y, "Telemetry Dashboard");

        // Tab bar
        int tabStartX = (this.width - (DashboardTab.values().length * TAB_WIDTH)) / 2;
        int tabY = 26;

        for (int i = 0; i < DashboardTab.values().length; i++) {
            DashboardTab tab = DashboardTab.values()[i];
            int tabX = tabStartX + (i * TAB_WIDTH);
            boolean selected = tab == currentTab;
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT);

            String tabName = Objects.requireNonNull(tab.getName(), "tabName");
            AxiomRenderer.drawTab(graphics, safeFont, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT, tabName, selected, hovered);
        }

        // Content area
        int contentX = (this.width - CONTENT_WIDTH) / 2;
        int contentY = tabY + TAB_HEIGHT + 16;

        // Tab-specific content
        switch (currentTab) {
            case OVERLAYS -> renderOverlaysTab(graphics, contentX, contentY);
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
        backButton.render(graphics, buttonX, buttonY, buttonWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderOverlaysTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Toggle Overlays");
        y += 20;

        // Use dynamic keybind names from KeyInputHandler
        y = drawOverlayToggle(graphics, x, y, "Debug Overlay", KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY, DebugRenderer.INSTANCE.isEnabled(), UIConstants.Accent.ORANGE());
        y = drawOverlayToggle(graphics, x, y, "Light Level", KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY, LightLevelOverlay.INSTANCE.isEnabled(), UIConstants.Accent.YELLOW());
        y = drawOverlayToggle(graphics, x, y, "Heatmap", KeyInputHandler.TOGGLE_HEATMAP_KEY, HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(), UIConstants.Accent.PURPLE());
        y = drawOverlayToggle(graphics, x, y, "Room Bounds", KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY, RoomBoundsVisualizer.INSTANCE.isEnabled(), UIConstants.Accent.ORANGE());
        y = drawOverlayToggle(graphics, x, y, "Pathfinding", KeyInputHandler.TOGGLE_PATHFINDING_KEY, PathfindingDebugger.INSTANCE.isEnabled(), UIConstants.Accent.CYAN());
        y = drawOverlayToggle(graphics, x, y, "Line of Sight", KeyInputHandler.TOGGLE_LOS_KEY, LineOfSightVisualizer.INSTANCE.isEnabled(), UIConstants.Accent.GREEN());
        y = drawOverlayToggle(graphics, x, y, "Vertical Levels", KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY, VerticalLevelsVisualizer.INSTANCE.isEnabled(), UIConstants.Accent.YELLOW());
        drawOverlayToggle(graphics, x, y, "Safe Spots", KeyInputHandler.TOGGLE_SAFE_SPOT_KEY, SafeSpotVisualizer.INSTANCE.isEnabled(), UIConstants.Accent.RED());
    }

    private int drawOverlayToggle(GuiGraphics graphics, int x, int y, String name, KeyMapping keyMapping, boolean enabled, int accentColor) {
        Font safeFont = getSafeFont();
        String safeName = Objects.requireNonNull(name, "name");
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW_HEIGHT - 2);

        if (hovered) {
            graphics.fill(x - 2, y, x + CONTENT_WIDTH + 2, y + ROW_HEIGHT - 2, UIConstants.Background.HOVER());
        }

        // Accent bar
        graphics.fill(x, y + 4, x + 3, y + ROW_HEIGHT - 6, accentColor);

        // Name + dynamic hotkey from KeyMapping
        graphics.drawString(safeFont, safeName, x + 10, y + 6, UIConstants.Text.PRIMARY(), false);
        String hotkeyText = keyMapping.isUnbound()
            ? "unbound"
            : Objects.requireNonNull(keyMapping.getTranslatedKeyMessage().getString(), "hotkeyText");
        int hotkeyColor = keyMapping.isUnbound() ? UIConstants.Text.MUTED() & 0x88FFFFFF : UIConstants.Text.MUTED();
        String hotkeyLabel = Objects.requireNonNull("(" + hotkeyText + ")", "hotkeyLabel");
        graphics.drawString(safeFont, hotkeyLabel, x + 10 + safeFont.width(safeName) + 6, y + 6, hotkeyColor, false);

        // Toggle
        int toggleX = x + CONTENT_WIDTH - UIConstants.Size.TOGGLE_WIDTH;
        AxiomRenderer.drawToggle(graphics, safeFont, toggleX, y + 3, UIConstants.Size.TOGGLE_WIDTH, UIConstants.Size.TOGGLE_HEIGHT, enabled, hovered);

        return y + ROW_HEIGHT;
    }

    private void renderExportTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Export Heatmap Data");
        y += 20;

        y = drawExportButton(graphics, x, y, "Death Heatmap", UIConstants.Accent.RED());
        y = drawExportButton(graphics, x, y, "Movement Heatmap", UIConstants.Accent.CYAN());
        y = drawExportButton(graphics, x, y, "Camping Heatmap", UIConstants.Accent.YELLOW());
        y = drawExportButton(graphics, x, y, "Stuck Heatmap", UIConstants.Accent.ORANGE());
        y = drawExportButton(graphics, x, y, "Aggro Drop Heatmap", UIConstants.Accent.PURPLE());
        y = drawExportButton(graphics, x, y, "Kiting Heatmap", UIConstants.Accent.GREEN());
        y = drawExportButton(graphics, x, y, "Choke Points", UIConstants.Accent.RED());
        y = drawExportButton(graphics, x, y, "Parkour Falls", UIConstants.Accent.ORANGE());
        y += 8; // Gap before damage stats
        drawExportButton(graphics, x, y, "Damage Statistics", UIConstants.Accent.GREEN());

        // Hint
        int hintY = this.height - 60;
        AxiomRenderer.drawHint(graphics, safeFont, x, hintY, "Exports are saved to run/telemetry/");
    }

    private int drawExportButton(GuiGraphics graphics, int x, int y, String name, int accentColor) {
        Font safeFont = getSafeFont();
        String safeName = Objects.requireNonNull(name, "name");
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4);

        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
        graphics.fill(x, y, x + CONTENT_WIDTH, y + ROW_HEIGHT - 4, bgColor);

        // Accent bar
        graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 4, accentColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4, hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT());

        // Text
        graphics.drawString(safeFont, "Export " + safeName, x + 10, y + 5, UIConstants.Text.PRIMARY(), false);

        // Arrow
        graphics.drawString(safeFont, ">", x + CONTENT_WIDTH - 12, y + 5, hovered ? UIConstants.Text.ACCENT() : UIConstants.Text.MUTED(), false);

        return y + ROW_HEIGHT;
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
        Font safeFont = getSafeFont();

        // Control row: Refresh button + Auto-refresh toggle
        int btnWidth = 80;
        int toggleWidth = 100;
        int totalWidth = btnWidth + 10 + toggleWidth;
        int startX = x + (CONTENT_WIDTH - totalWidth) / 2;

        // Manual Refresh button
        refreshButton
            .style(EditorButton.Style.PRIMARY)
            .onClick(() -> {
                refreshStats();
                lastRefreshTime = System.currentTimeMillis();
                showMessage("Stats refreshed!");
            });
        refreshButton.render(graphics, startX, y, btnWidth, UIConstants.Size.BUTTON_HEIGHT, mouseX, mouseY);

        // Auto-refresh toggle
        int toggleX = startX + btnWidth + 10;
        boolean autoHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, toggleX, y, toggleWidth, UIConstants.Size.BUTTON_HEIGHT);
        int toggleBg = autoHovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
        graphics.fill(toggleX, y, toggleX + toggleWidth, y + UIConstants.Size.BUTTON_HEIGHT, toggleBg);
        AxiomRenderer.drawBorder(graphics, toggleX, y, toggleWidth, UIConstants.Size.BUTTON_HEIGHT,
            autoRefresh ? UIConstants.Accent.GREEN() : UIConstants.Border.DEFAULT());

        String autoLabel = autoRefresh ? "Auto: ON" : "Auto: OFF";
        int autoColor = autoRefresh ? UIConstants.Accent.GREEN() : UIConstants.Text.SECONDARY();
        int labelWidth = safeFont.width(autoLabel);
        graphics.drawString(safeFont, autoLabel, toggleX + (toggleWidth - labelWidth) / 2, y + 6, autoColor, false);

        y += 30;

        // Stats panel
        int panelHeight = Math.min(10, cachedStats.size()) * 12 + 16;
        AxiomRenderer.drawSimplePanel(graphics, x, y, CONTENT_WIDTH, panelHeight);

        int textY = y + 8;
        int maxDisplay = Math.min(10, cachedStats.size() - scrollOffset);

        for (int i = 0; i < maxDisplay; i++) {
            int index = i + scrollOffset;
            if (index < cachedStats.size()) {
                String line = cachedStats.get(index);
                graphics.drawString(safeFont, line, x + 8, textY, UIConstants.Text.PRIMARY(), false);
                textY += 12;
            }
        }

        // Scroll indicator - always visible when scrollable
        if (cachedStats.size() > 10) {
            String scrollInfo = String.format("§e[%d-%d / %d]§r",
                scrollOffset + 1,
                Math.min(scrollOffset + 10, cachedStats.size()),
                cachedStats.size());

            // Draw scroll hint bar at bottom
            int hintY = y + panelHeight + 4;
            graphics.fill(x, hintY, x + CONTENT_WIDTH, hintY + 16, UIConstants.Background.PANEL());
            AxiomRenderer.drawBorder(graphics, x, hintY, CONTENT_WIDTH, 16, UIConstants.Border.DEFAULT());

            String hintText = Objects.requireNonNull("§7Scroll/↑↓/PgUp/PgDn§r | " + scrollInfo, "hintText");
            String hintTextPlain = Objects.requireNonNull(hintText.replaceAll("§.", ""), "hintTextPlain");
            int textWidth = safeFont.width(hintTextPlain); // Strip color codes for width
            graphics.drawString(safeFont, hintText, x + (CONTENT_WIDTH - textWidth) / 2, hintY + 4, UIConstants.Accent.YELLOW(), false);

            // Show scroll arrows if not at edges
            if (scrollOffset > 0) {
                graphics.drawString(safeFont, "▲", x + 4, hintY + 4, UIConstants.Accent.GREEN(), false);
            }
            if (scrollOffset < cachedStats.size() - 10) {
                graphics.drawString(safeFont, "▼", x + CONTENT_WIDTH - 12, hintY + 4, UIConstants.Accent.GREEN(), false);
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
    }

    /**
     * Get a color-coded icon for hazard types.
     */
    private String getHazardIcon(HazardType type) {
        return switch (type) {
            case FALL -> "§e⬇";      // Yellow down arrow
            case FIRE -> "§6🔥";     // Orange fire
            case LAVA -> "§c🌋";     // Red lava
            case DROWNING -> "§b💧"; // Aqua water
            case EXPLOSION -> "§c💥"; // Red explosion
            case POISON -> "§a☠";    // Green poison
            case WITHER -> "§5💀";   // Purple skull
            case FREEZING -> "§f❄";  // White snowflake
            case LIGHTNING -> "§e⚡"; // Yellow lightning
            case CACTUS -> "§2🌵";   // Green cactus
            case VOID -> "§0⬛";      // Black void
            case MAGIC -> "§d✨";    // Magenta sparkle
            default -> "§7•";        // Gray bullet
        };
    }

    /**
     * Format hazard type enum name to readable string.
     */
    private String formatHazardName(HazardType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    @Nonnull
    private Font getSafeFont() {
        return Objects.requireNonNull(font, "font");
    }

    private void renderVisualizersTab(GuiGraphics graphics, int x, int y) {
        Font safeFont = getSafeFont();
        AxiomRenderer.drawSectionHeader(graphics, safeFont, x, y, "Load Heatmap Visualizers");
        y += 20;

        y = drawVisualizerButton(graphics, x, y, "Death Heatmap", HeatmapVisualizer.HeatmapType.DEATH, UIConstants.Accent.RED());
        y = drawVisualizerButton(graphics, x, y, "Movement Heatmap", HeatmapVisualizer.HeatmapType.MOVEMENT, UIConstants.Accent.CYAN());
        y = drawVisualizerButton(graphics, x, y, "Camping Heatmap", HeatmapVisualizer.HeatmapType.CAMPING, UIConstants.Accent.YELLOW());
        y = drawVisualizerButton(graphics, x, y, "Stuck Heatmap", HeatmapVisualizer.HeatmapType.STUCK, UIConstants.Accent.ORANGE());
        y = drawVisualizerButton(graphics, x, y, "Aggro Drop Heatmap", HeatmapVisualizer.HeatmapType.AGGRO_DROP, UIConstants.Accent.PURPLE());
        y = drawVisualizerButton(graphics, x, y, "Kiting Heatmap", HeatmapVisualizer.HeatmapType.KITING, UIConstants.Accent.GREEN());
        y += 12;

        // Clear all button (with confirmation)
        if (showClearConfirmation) {
            // Show confirmation buttons
            int btnWidth = (CONTENT_WIDTH - 10) / 2;

            // "Confirm" button
            boolean confirmHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, btnWidth, ROW_HEIGHT - 4);
            int confirmBg = confirmHovered ? 0x80FF0000 : UIConstants.Background.PANEL();
            graphics.fill(x, y, x + btnWidth, y + ROW_HEIGHT - 4, confirmBg);
            graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 4, UIConstants.Accent.RED());
            AxiomRenderer.drawBorder(graphics, x, y, btnWidth, ROW_HEIGHT - 4, UIConstants.Accent.RED());
            String confirmText = "Yes, Clear All";
            graphics.drawString(safeFont, confirmText, x + (btnWidth - safeFont.width(confirmText)) / 2, y + 5, UIConstants.Accent.RED(), false);

            // "Cancel" button
            int cancelX = x + btnWidth + 10;
            boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, y, btnWidth, ROW_HEIGHT - 4);
            int cancelBg = cancelHovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
            graphics.fill(cancelX, y, cancelX + btnWidth, y + ROW_HEIGHT - 4, cancelBg);
            AxiomRenderer.drawBorder(graphics, cancelX, y, btnWidth, ROW_HEIGHT - 4, UIConstants.Border.DEFAULT());
            String cancelText = "Cancel";
            graphics.drawString(safeFont, cancelText, cancelX + (btnWidth - safeFont.width(cancelText)) / 2, y + 5, UIConstants.Text.PRIMARY(), false);
        } else {
            // Normal clear button
            boolean clearHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW_HEIGHT);
            int clearBg = clearHovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
            graphics.fill(x, y, x + CONTENT_WIDTH, y + ROW_HEIGHT - 4, clearBg);
            graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 4, UIConstants.Accent.RED());
            AxiomRenderer.drawBorder(graphics, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4, clearHovered ? UIConstants.Accent.RED() : UIConstants.Border.DEFAULT());
            int textWidth = safeFont.width("Clear All Heatmaps");
            graphics.drawString(safeFont, "Clear All Heatmaps", x + (CONTENT_WIDTH - textWidth) / 2, y + 5, UIConstants.Accent.RED(), false);
        }
    }

    private int drawVisualizerButton(GuiGraphics graphics, int x, int y, String name, HeatmapVisualizer.HeatmapType type, int accentColor) {
        Font safeFont = getSafeFont();
        String safeName = Objects.requireNonNull(name, "name");
        boolean enabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
        boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4);

        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.PANEL();
        graphics.fill(x, y, x + CONTENT_WIDTH, y + ROW_HEIGHT - 4, bgColor);

        // Accent bar
        graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 4, accentColor);

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4, hovered ? UIConstants.Border.ACCENT() : UIConstants.Border.DEFAULT());

        // Text
        graphics.drawString(safeFont, "Load " + safeName, x + 10, y + 5, UIConstants.Text.PRIMARY(), false);

        // Status indicator
        String status = enabled ? "[ACTIVE]" : "";
        if (!status.isEmpty()) {
            graphics.drawString(safeFont, status, x + CONTENT_WIDTH - safeFont.width(status) - 8, y + 5, UIConstants.Accent.GREEN(), false);
        }

        return y + ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Tab clicks
        int tabStartX = (this.width - (DashboardTab.values().length * TAB_WIDTH)) / 2;
        int tabY = 26;

        for (int i = 0; i < DashboardTab.values().length; i++) {
            int tabX = tabStartX + (i * TAB_WIDTH);
            if (AxiomRenderer.isMouseOver(mx, my, tabX, tabY, TAB_WIDTH - 2, TAB_HEIGHT)) {
                currentTab = DashboardTab.values()[i];
                scrollOffset = 0;
                return true;
            }
        }

        // Content clicks
        int contentX = (this.width - CONTENT_WIDTH) / 2;
        int contentY = tabY + TAB_HEIGHT + 16;

        switch (currentTab) {
            case OVERLAYS -> { if (handleOverlaysClick(mx, my, contentX, contentY)) return true; }
            case EXPORT -> { if (handleExportClick(mx, my, contentX, contentY)) return true; }
            case STATS -> { if (handleStatsClick(mx, my, contentX, contentY)) return true; }
            case VISUALIZERS -> { if (handleVisualizersClick(mx, my, contentX, contentY)) return true; }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleOverlaysClick(int mx, int my, int x, int y) {
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
            if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT - 2)) {
                invokeAction(actionId);
                return true;
            }
            y += ROW_HEIGHT;
        }
        return false;
    }

    private boolean handleExportClick(int mx, int my, int x, int y) {
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
            if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4)) {
                invokeAction(exportActions[i]);
                showMessage(I18n.translate(exportMessages[i]).getString());
                return true;
            }
            y += ROW_HEIGHT;
        }

        // Gap + Damage Statistics button
        y += 8;
        if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4)) {
            invokeAction(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS);
            showMessage(I18n.translate("devmod.telemetry.exported_damage_stats").getString());
            return true;
        }

        return false;
    }

    private boolean handleStatsClick(int mx, int my, int x, int y) {
        // Control row layout matching renderStatsTab
        int btnWidth = 80;
        int toggleWidth = 100;
        int totalWidth = btnWidth + 10 + toggleWidth;
        int startX = x + (CONTENT_WIDTH - totalWidth) / 2;

        // Manual Refresh button
        if (refreshButton.mouseClicked(mx, my, 0)) {
            return true;
        }

        // Auto-refresh toggle
        int toggleX = startX + btnWidth + 10;
        if (mx >= toggleX && mx < toggleX + toggleWidth && my >= y && my < y + UIConstants.Size.BUTTON_HEIGHT) {
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        handled |= backButton.mouseReleased(mouseX, mouseY, button);
        handled |= refreshButton.mouseReleased(mouseX, mouseY, button);
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleVisualizersClick(int mx, int my, int x, int y) {
        y += 20; // section header

        HeatmapVisualizer.HeatmapType[] types = {
            HeatmapVisualizer.HeatmapType.DEATH,
            HeatmapVisualizer.HeatmapType.MOVEMENT,
            HeatmapVisualizer.HeatmapType.CAMPING,
            HeatmapVisualizer.HeatmapType.STUCK,
            HeatmapVisualizer.HeatmapType.AGGRO_DROP,
            HeatmapVisualizer.HeatmapType.KITING
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
            HeatmapVisualizer.HeatmapType type = types[i];
            if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4)) {
                boolean currentlyEnabled = HeatmapVisualizer.INSTANCE.isEnabled(type);
                invokeAction(actionIds[i]);
                String status = currentlyEnabled ? "§cOFF" : "§aON";
                showMessage(I18n.translate("devmod.render.heatmap_type_toggle_status", type.name(), status).getString());
                return true;
            }
            y += ROW_HEIGHT;
        }

        y += 12; // gap before clear button

        // Clear all button (with confirmation)
        if (showClearConfirmation) {
            int btnWidth = (CONTENT_WIDTH - 10) / 2;

            // "Yes, Clear All" button
            if (AxiomRenderer.isMouseOver(mx, my, x, y, btnWidth, ROW_HEIGHT - 4)) {
                invokeConfirmedAction(ActionIds.DEBUG_HEATMAP_CLEAR_ALL);
                showMessage(I18n.translate("devmod.render.heatmaps_cleared").getString());
                showClearConfirmation = false;
                return true;
            }

            // "Cancel" button
            int cancelX = x + btnWidth + 10;
            if (AxiomRenderer.isMouseOver(mx, my, cancelX, y, btnWidth, ROW_HEIGHT - 4)) {
                showClearConfirmation = false;
                return true;
            }
        } else {
            // Show confirmation first
            if (AxiomRenderer.isMouseOver(mx, my, x, y, CONTENT_WIDTH, ROW_HEIGHT - 4)) {
                showClearConfirmation = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == DashboardTab.STATS && cachedStats.size() > 10) {
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(cachedStats.size() - 10, scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Keyboard navigation for stats tab
        if (currentTab == DashboardTab.STATS && cachedStats.size() > 10) {
            // Arrow Up / Page Up
            if (keyCode == 265 || keyCode == 266) { // GLFW_KEY_UP = 265, PAGE_UP = 266
                int amount = keyCode == 266 ? 5 : 1; // Page Up scrolls 5 lines
                scrollOffset = Math.max(0, scrollOffset - amount);
                return true;
            }
            // Arrow Down / Page Down
            if (keyCode == 264 || keyCode == 267) { // GLFW_KEY_DOWN = 264, PAGE_DOWN = 267
                int amount = keyCode == 267 ? 5 : 1; // Page Down scrolls 5 lines
                scrollOffset = Math.min(cachedStats.size() - 10, scrollOffset + amount);
                return true;
            }
            // Home - go to start
            if (keyCode == 268) { // GLFW_KEY_HOME
                scrollOffset = 0;
                return true;
            }
            // End - go to end
            if (keyCode == 269) { // GLFW_KEY_END
                scrollOffset = Math.max(0, cachedStats.size() - 10);
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
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Do nothing - disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}

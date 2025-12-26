package com.devmod.client.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.editor.components.FooterComponent;
import com.devmod.client.ui.editor.components.HeaderComponent;
import com.devmod.client.ui.editor.components.LeftColumnComponent;
import com.devmod.client.ui.editor.components.ScrollableContentArea;
import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.client.ui.editor.core.EditorLayout;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.client.ui.editor.favorites.FavoritePresetStore;
import com.devmod.client.ui.editor.systems.CraftingInfoPanel;
import com.devmod.client.ui.editor.systems.DebugPanel;
import com.devmod.client.ui.editor.systems.HelpOverlay;
import com.devmod.client.ui.editor.systems.MultiEditPanel;
import com.devmod.client.ui.editor.systems.PresetSelectorOverlay;
import com.devmod.client.ui.editor.systems.TemplateOverlay;
import com.devmod.config.ArmorConfigManager;
import com.devmod.config.WeaponConfigManager;

public class ItemEditorRenderer {

    // Constants for rendering
    private static final int STATUS_MESSAGE_BOTTOM_OFFSET = 60;
    private static final int STATUS_MESSAGE_PADDING_X = 10;
    private static final int STATUS_MESSAGE_PADDING_Y = 4;
    private static final int STATUS_MESSAGE_SCREEN_MARGIN = 4;
    private static final int STATUS_MESSAGE_BG = 0xE0000000;

    private static final int HISTORY_PANEL_WIDTH = 260;
    private static final int HISTORY_PANEL_HEIGHT = 200;
    private static final int HISTORY_PANEL_MARGIN_RIGHT = 8;
    private static final int HISTORY_PANEL_MARGIN_TOP = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_X = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_Y = 8;
    private static final int HISTORY_PANEL_LIST_OFFSET_Y = 24;
    private static final int HISTORY_PANEL_LIST_HEIGHT_PADDING = 50;
    private static final int HISTORY_PANEL_LINE_HEIGHT = 14;
    private static final int HISTORY_PANEL_FOOTER_OFFSET_Y = 18;
    private static final int HISTORY_PANEL_TEXT_OFFSET_X = 8;
    private static final int HISTORY_PANEL_CLEAR_WIDTH = 60;
    private static final int HISTORY_PANEL_CLEAR_HEIGHT = 14;
    private static final int HISTORY_PANEL_CLEAR_OFFSET_X = 8;
    private static final int HISTORY_PANEL_CLEAR_OFFSET_Y = 2;
    private static final int HISTORY_PANEL_CLEAR_TEXT_OFFSET_X = 12;
    private static final int HISTORY_PANEL_CLEAR_TEXT_OFFSET_Y = 3;

    private static final String HISTORY_TITLE_TEXT = "Edit History";
    private static final String HISTORY_EMPTY_TEXT = "No history yet";
    private static final String HISTORY_SHOWING_EMPTY = "Showing 0/0";
    private static final String HISTORY_SHOWING_PREFIX = "Showing ";
    private static final String HISTORY_SHOWING_SEPARATOR = "/";
    private static final String HISTORY_CLEAR_TEXT = "Clear";

    private static final int TOOLTIP_WIDTH_PADDING = 8;
    private static final int TOOLTIP_HEIGHT = 14;
    private static final int TOOLTIP_OFFSET_X = 12;
    private static final int TOOLTIP_OFFSET_Y = 20;
    private static final int TOOLTIP_SCREEN_MARGIN = 4;
    private static final int TOOLTIP_TEXT_OFFSET_X = 4;
    private static final int TOOLTIP_TEXT_OFFSET_Y = 3;
    private static final int TOOLTIP_BG = 0xF0100010;

    private static final String FAVORITES_TITLE_TEXT = "Favorites";
    private static final String FAVORITES_LABEL_PREFIX = "★ ";
    private static final String FAVORITES_LABEL_FALLBACK = "Preset";
    private static final String FAVORITES_PIN_TEXT = "Pin last";
    private static final String FAVORITES_UNPIN_TEXT = "Unpin last";
    private static final int FAVORITES_TITLE_OFFSET_X = 8;
    private static final int FAVORITES_TITLE_OFFSET_Y = 8;
    private static final int FAVORITES_ROW_HEIGHT = 18;
    private static final int FAVORITES_LIST_START_Y = 20;
    private static final int FAVORITES_LIST_BOTTOM_PADDING = 28;
    private static final int FAVORITES_ROW_HIT_INSET = 4;
    private static final int FAVORITES_ROW_HOVER_INSET = 2;
    private static final int FAVORITES_ROW_TEXT_OFFSET_X = 8;
    private static final int FAVORITES_ROW_TEXT_OFFSET_Y = 5;
    private static final int FAVORITES_PIN_BUTTON_OFFSET_X = 8;
    private static final int FAVORITES_PIN_BUTTON_OFFSET_Y = 18;
    private static final int FAVORITES_PIN_BUTTON_WIDTH = 80;
    private static final int FAVORITES_PIN_BUTTON_HEIGHT = 14;
    private static final int FAVORITES_PIN_TEXT_OFFSET_X = 12;
    private static final int FAVORITES_PIN_TEXT_OFFSET_Y = 3;

    private static final int DEV_PANEL_FALLBACK_WIDTH = 150;
    private static final int DEV_PANEL_FALLBACK_HEIGHT = 200;
    private static final int DEV_PANEL_FALLBACK_MARGIN_TOP = 10;
    private static final int DEV_PANEL_FALLBACK_MARGIN_RIGHT = 10;
    private static final int DEV_PANEL_TEXT_OFFSET_X = 8;
    private static final int DEV_PANEL_TEXT_OFFSET_Y = 8;
    private static final int DEV_PANEL_TITLE_LINE_STEP = 12;
    private static final int DEV_PANEL_LINE_STEP = 10;
    private static final int DEV_PANEL_BG = 0xE0101020;
    private static final int DEV_PANEL_TITLE_COLOR = 0xFFFFFFFF;
    private static final String DEV_PANEL_TITLE_TEXT = "§b[Dev Mode]";
    private static final String DEV_PANEL_HEADER_TEXT = "Dev Mode";

    private static final String SOURCE_SPECIFIC_ARMOR = "Specific (CustomData: ArmorModStats)";
    private static final String SOURCE_SPECIFIC_WEAPON = "Specific (CustomData: WeaponModStats)";
    private static final String SOURCE_GLOBAL_OVERRIDE = "Global override available (serverconfig/devmod/devmod-items.toml)";
    private static final String SOURCE_GLOBAL = "Global (serverconfig/devmod/devmod-items.toml)";
    private static final String SOURCE_VANILLA = "Vanilla (no overrides)";
    private static final String WEAPON_STATS_KEY = "WeaponModStats";
    private static final String ARMOR_STATS_KEY = "ArmorModStats";

    private final PerformanceMonitor perfMonitor = PerformanceMonitor.INSTANCE;

    // Tooltip state (managed by renderer, read by screen)
    private String tooltipText = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    /**
     * Main render method - renders the entire editor screen.
     */
    public void render(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float partialTick,
            EditorLayout editorLayout, ResponsiveLayout layout, Font font,
            HeaderComponent header, LeftColumnComponent leftColumn, FooterComponent footer,
            ScrollableContentArea scrollArea, EditorModule activeModule,
            boolean isPreviewMode, boolean showDevPanel, boolean showMultiEditPanel,
            boolean showHistoryPanel, boolean showPresetsPanel, boolean showTemplatesPanel, boolean showCraftingPanel,
            boolean showLowConfidenceDialog, WeaponTypeDetector.DetectionResult pendingDetection,
            MultiEditPanel multiEditPanel, DebugPanel debugPanel, PresetSelectorOverlay presetSelectorOverlay,
            TemplateOverlay templateOverlay, CraftingInfoPanel craftingPanel, HelpOverlay helpOverlay,
            ConfirmDialog activeDialog, ItemStack item, String lastLoadedPreset,
            List<ItemEditorDataManager.PresetData> favorites, String activeItemType,
            int historyScrollOffset, String statusMessage, int statusColor, int statusTicks,
            String lowConfidenceStatus,
            ResponsiveLayout.Rect lowConfidenceContinueBounds,
            ResponsiveLayout.Rect lowConfidenceWhitelistBounds,
            ResponsiveLayout.Rect lowConfidenceCancelBounds,
            FavoritePresetStore favoriteStore) {

        perfMonitor.startFrame();
        tooltipText = null;

        // Dark overlay background, but leave vanilla hotbar area unobscured
        int hotbarReserve = ScaledCoord.scaleDim(UIConstants.Spacing.XXL);
        graphics.fill(0, 0, width, Math.max(0, height - hotbarReserve), UIConstants.Background.OVERLAY());

        // Get layout areas
        var panelBounds = editorLayout.getPanelBounds();
        var headerBounds = editorLayout.getHeaderBounds();
        var footerBounds = editorLayout.getFooterBounds();
        var leftBounds = editorLayout.getLeftColumnBounds();
        var contentBounds = editorLayout.getContentBounds();
        int contentY = contentBounds.y();
        int footerY = footerBounds.y();
        int contentHeight = contentBounds.height();

        // Main panel background
        graphics.fill(panelBounds.x(), panelBounds.y(), panelBounds.right(), panelBounds.bottom(),
            UIConstants.Background.PANEL());

        // Panel border
        AxiomRenderer.drawBorder(graphics, panelBounds.x(), panelBounds.y(),
            panelBounds.width(), panelBounds.height(), UIConstants.Border.DEFAULT());

        // Header
        header.render(graphics, headerBounds.x(), headerBounds.y(), headerBounds.width(), mouseX, mouseY);
        if (header.getModeBadge().isHovered()) {
            tooltipText = header.getModeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        } else if (header.getScopeBadge().isHovered()) {
            tooltipText = header.getScopeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        // Left column
        leftColumn.render(graphics, leftBounds.x(), leftBounds.y(), leftBounds.width(), leftBounds.height(),
            mouseX, mouseY, partialTick);

        // Multi-edit panel
        if (showMultiEditPanel && multiEditPanel != null) {
            int panelX = leftBounds.x() + UIConstants.Spacing.MD;
            int panelY = contentY + UIConstants.Spacing.MD;
            int panelW = leftBounds.width() - UIConstants.Spacing.MD * 2;
            multiEditPanel.render(graphics, font, panelX, panelY, panelW, mouseX, mouseY);
        }

        // Content area
        int contentX = contentBounds.x() + UIConstants.Spacing.MD;
        int contentWidth = contentBounds.width() - UIConstants.Spacing.MD * 2;
        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight,
            UIConstants.Background.CONTENT());

        if (activeModule != null) {
            perfMonitor.startTiming("render_content");
            int viewportHeight = contentHeight - UIConstants.Spacing.MD * 2;
            scrollArea.render(graphics, contentX, contentY, contentWidth, contentHeight,
                mouseX, mouseY, partialTick, (g, x, y, w, mx, my) -> {
                    ResponsiveLayout.Rect bounds = new ResponsiveLayout.Rect(x, y, w, viewportHeight);
                    activeModule.renderContent(g, bounds, mx, my);
                    return activeModule.calculateContentHeight();
                });
            perfMonitor.endTiming("render_content");
        }

        // Footer
        int pending = activeModule != null ? activeModule.getPendingChanges().size() : 0;
        boolean dirty = activeModule != null && (activeModule.hasUnsavedChanges() || pending > 0);
        footer.canUndo(activeModule != null && activeModule.canUndo())
            .canRedo(activeModule != null && activeModule.canRedo())
            .canApply(activeModule != null)
            .isDirty(dirty)
            .pendingCount(pending);
        footer.render(graphics, panelBounds.x(), footerY, panelBounds.width(), mouseX, mouseY);

        // Side panels
        if (layout.showSidePanels()) {
            renderSidePanels(graphics, font, layout, mouseX, mouseY, showDevPanel, favorites,
                lastLoadedPreset, activeItemType, favoriteStore);
        }

        // Status message overlay
        if (statusMessage != null && statusTicks > 0) {
            renderStatusMessage(graphics, font, width, height, statusMessage, statusColor);
        }

        // History panel
        if (showHistoryPanel && activeModule != null) {
            renderHistoryPanel(graphics, font, layout, activeModule, historyScrollOffset);
        }

        // Presets overlay
        if (showPresetsPanel && presetSelectorOverlay != null) {
            presetSelectorOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Templates overlay
        if (showTemplatesPanel && templateOverlay != null) {
            templateOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Crafting panel
        if (showCraftingPanel && craftingPanel != null) {
            craftingPanel.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Header dropdown overlays
        header.renderOverlays(graphics, mouseX, mouseY);

        // Tooltip
        if (tooltipText != null) {
            renderTooltip(graphics, font, width, tooltipText, tooltipX, tooltipY);
        }

        // Dev panel
        if (showDevPanel) {
            renderDevPanel(graphics, font, layout, width, debugPanel, item, isPreviewMode, activeModule);
        }

        // Module overlay
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            activeModule.renderOverlay(graphics, font, width, height, mouseX, mouseY);
        }

        // Modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            activeDialog.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Help overlay
        if (helpOverlay != null && helpOverlay.isVisible()) {
            helpOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Low-confidence dialog
        if (showLowConfidenceDialog && pendingDetection != null) {
            renderLowConfidenceDialog(graphics, font, width, height, mouseX, mouseY, item, pendingDetection,
                lowConfidenceStatus, lowConfidenceContinueBounds, lowConfidenceWhitelistBounds, lowConfidenceCancelBounds);
        }

        // Performance overlay
        perfMonitor.endRender();
        var perf = perfMonitor.getMetrics();
        DebugOverlay.setPerformanceLine(String.format("Frame %.2fms | Render %.2fms | Entities %.0f | %s",
            perf.frameTime(), perf.renderTime(), perf.entityCount(), perf.bottleneck()));
        int contentTotalHeight = activeModule == null ? 0 : activeModule.calculateContentHeight();
        int sectionCount = activeModule == null ? 0 : activeModule.getSections().size();
        DebugOverlay.render(graphics, font, panelBounds, headerBounds, leftBounds, contentBounds, footerBounds,
            mouseX, mouseY, contentTotalHeight, scrollArea.getScrollOffset(), sectionCount);
    }

    public void renderStatusMessage(GuiGraphics graphics, Font font, int width, int height,
            String message, int color) {
        if (message == null) return;
        var safeFont = Objects.requireNonNull(font, "font");
        String safeMessage = Objects.requireNonNull(message, "message");

        int msgWidth = safeFont.width(safeMessage) + STATUS_MESSAGE_PADDING_X * 2;
        int msgX = (width - msgWidth) / 2;
        int msgY = height - STATUS_MESSAGE_BOTTOM_OFFSET;
        float fontScale = Typography.withUiScale(1.0f);

        int paddingX = STATUS_MESSAGE_PADDING_X;
        int paddingY = STATUS_MESSAGE_PADDING_Y;
        int msgHeight = safeFont.lineHeight + paddingY * 2;
        msgWidth = safeFont.width(safeMessage) + paddingX * 2;
        msgX = Math.max(STATUS_MESSAGE_SCREEN_MARGIN,
            Math.min(width - msgWidth - STATUS_MESSAGE_SCREEN_MARGIN, msgX));

        graphics.fill(msgX, msgY, msgX + msgWidth, msgY + msgHeight, STATUS_MESSAGE_BG);
        AxiomRenderer.drawBorder(graphics, msgX, msgY, msgWidth, msgHeight, color);
        Typography.drawText(graphics, safeFont, safeMessage, msgX + paddingX, msgY + paddingY, color, fontScale);
    }

    public void renderHistoryPanel(GuiGraphics graphics, Font font, ResponsiveLayout layout,
            EditorModule activeModule, int historyScrollOffset) {
        if (activeModule == null) return;
        var safeFont = Objects.requireNonNull(font, "font");

        ResponsiveLayout.Rect historyBounds = getHistoryPanelBounds(layout);
        int panelWidth = historyBounds.width();
        int panelHeight = historyBounds.height();
        int x = historyBounds.x();
        int y = historyBounds.y();

        graphics.fill(x, y, x + panelWidth, y + panelHeight, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, UIConstants.Border.DEFAULT());

        graphics.drawString(safeFont, HISTORY_TITLE_TEXT, x + HISTORY_PANEL_TITLE_OFFSET_X,
            y + HISTORY_PANEL_TITLE_OFFSET_Y, UIConstants.Text.TITLE(), false);

        int listY = y + HISTORY_PANEL_LIST_OFFSET_Y;
        var entries = activeModule.getHistoryEntries();
        int lineHeight = HISTORY_PANEL_LINE_HEIGHT;
        int listHeight = panelHeight - HISTORY_PANEL_LIST_HEIGHT_PADDING;
        int maxScroll = Math.max(0, entries.size() * lineHeight - listHeight);
        int scrollOffset = Math.max(0, Math.min(historyScrollOffset, maxScroll));

        int visibleLines = listHeight / lineHeight;
        if (entries.isEmpty()) {
            graphics.drawString(safeFont, HISTORY_EMPTY_TEXT, x + HISTORY_PANEL_TEXT_OFFSET_X, listY,
                UIConstants.Text.MUTED(), false);
        } else {
            int startFromEnd = scrollOffset / lineHeight;
            int startIndex = Math.max(0, entries.size() - visibleLines - startFromEnd);
            int endIndex = Math.min(entries.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                String entry = entries.get(entries.size() - 1 - i);
                int entryY = listY + (i - startIndex) * lineHeight;
                graphics.drawString(safeFont, entry, x + HISTORY_PANEL_TEXT_OFFSET_X, entryY,
                    UIConstants.Text.SECONDARY(), false);
            }
        }

        // Footer
        int footerY = y + panelHeight - HISTORY_PANEL_FOOTER_OFFSET_Y;
        String countText = entries.isEmpty()
            ? HISTORY_SHOWING_EMPTY
            : HISTORY_SHOWING_PREFIX + Math.min(visibleLines, entries.size()) + HISTORY_SHOWING_SEPARATOR + entries.size();
        graphics.drawString(safeFont, countText, x + HISTORY_PANEL_TEXT_OFFSET_X, footerY,
            UIConstants.Text.MUTED(), false);

        // Clear button
        int clearW = HISTORY_PANEL_CLEAR_WIDTH;
        int clearH = HISTORY_PANEL_CLEAR_HEIGHT;
        int clearX = x + panelWidth - clearW - HISTORY_PANEL_CLEAR_OFFSET_X;
        int clearY = footerY - HISTORY_PANEL_CLEAR_OFFSET_Y;
        int clearBg = entries.isEmpty() ? UIConstants.Button.DISABLED() : UIConstants.Background.INPUT();
        graphics.fill(clearX, clearY, clearX + clearW, clearY + clearH, clearBg);
        AxiomRenderer.drawBorder(graphics, clearX, clearY, clearW, clearH, UIConstants.Border.DEFAULT());
        int clearColor = entries.isEmpty() ? UIConstants.Text.DISABLED() : UIConstants.Text.PRIMARY();
        graphics.drawString(safeFont, HISTORY_CLEAR_TEXT, clearX + HISTORY_PANEL_CLEAR_TEXT_OFFSET_X,
            clearY + HISTORY_PANEL_CLEAR_TEXT_OFFSET_Y, clearColor, false);
    }

    public ResponsiveLayout.Rect getHistoryPanelBounds(ResponsiveLayout layout) {
        int x = layout.getEditorX() + layout.getEditorWidth() - HISTORY_PANEL_WIDTH - HISTORY_PANEL_MARGIN_RIGHT;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + HISTORY_PANEL_MARGIN_TOP;
        return new ResponsiveLayout.Rect(x, y, HISTORY_PANEL_WIDTH, HISTORY_PANEL_HEIGHT);
    }

    public void renderTooltip(GuiGraphics graphics, Font font, int screenWidth, String text, int x, int y) {
        var safeFont = Objects.requireNonNull(font, "font");
        String safeText = Objects.requireNonNull(text, "text");
        int tipWidth = safeFont.width(safeText) + TOOLTIP_WIDTH_PADDING;
        int tipX = Math.min(x + TOOLTIP_OFFSET_X, screenWidth - tipWidth - TOOLTIP_SCREEN_MARGIN);
        int tipY = Math.max(y - TOOLTIP_OFFSET_Y, TOOLTIP_SCREEN_MARGIN);

        graphics.fill(tipX, tipY, tipX + tipWidth, tipY + TOOLTIP_HEIGHT, TOOLTIP_BG);
        AxiomRenderer.drawBorder(graphics, tipX, tipY, tipWidth, TOOLTIP_HEIGHT, UIConstants.Border.DEFAULT());
        graphics.drawString(safeFont, safeText, tipX + TOOLTIP_TEXT_OFFSET_X, tipY + TOOLTIP_TEXT_OFFSET_Y,
            UIConstants.Text.PRIMARY(), false);
    }

    private void renderSidePanels(GuiGraphics graphics, Font font, ResponsiveLayout layout,
            int mouseX, int mouseY, boolean showDevPanel,
            List<ItemEditorDataManager.PresetData> favorites, String lastLoadedPreset, String activeItemType,
            FavoritePresetStore favoriteStore) {
        var safeFont = Objects.requireNonNull(font, "font");

        // Left panel (favorites)
        ResponsiveLayout.Rect leftPanel = layout.getFavoritesPanelArea();
        if (!leftPanel.isEmpty()) {
            graphics.fill(leftPanel.x(), leftPanel.y(), leftPanel.right(), leftPanel.bottom(),
                UIConstants.Background.PANEL());
            AxiomRenderer.drawBorder(graphics, leftPanel.x(), leftPanel.y(),
                leftPanel.width(), leftPanel.height(), UIConstants.Border.DEFAULT());
            graphics.drawString(safeFont, FAVORITES_TITLE_TEXT, leftPanel.x() + FAVORITES_TITLE_OFFSET_X,
                leftPanel.y() + FAVORITES_TITLE_OFFSET_Y, UIConstants.Text.SECONDARY(), false);

            int rowHeight = FAVORITES_ROW_HEIGHT;
            int startY = leftPanel.y() + FAVORITES_LIST_START_Y;
            int maxRows = Math.max(0, (leftPanel.height() - FAVORITES_LIST_BOTTOM_PADDING) / rowHeight);

            for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                int rowY = startY + i * rowHeight;
                boolean hovered = mouseX >= leftPanel.x() + FAVORITES_ROW_HIT_INSET
                    && mouseX <= leftPanel.right() - FAVORITES_ROW_HIT_INSET
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
                if (hovered) {
                    graphics.fill(leftPanel.x() + FAVORITES_ROW_HOVER_INSET, rowY,
                        leftPanel.right() - FAVORITES_ROW_HOVER_INSET, rowY + rowHeight,
                        UIConstants.Background.HOVER());
                    if (tooltipText == null) {
                        tooltipText = preset.name;
                        tooltipX = mouseX;
                        tooltipY = mouseY;
                    }
                }
                String label = preset.name == null ? FAVORITES_LABEL_FALLBACK : preset.name;
                graphics.drawString(safeFont, FAVORITES_LABEL_PREFIX + label, leftPanel.x() + FAVORITES_ROW_TEXT_OFFSET_X,
                    rowY + FAVORITES_ROW_TEXT_OFFSET_Y,
                    hovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY(), false);
            }

            // Pin toggle
            if (lastLoadedPreset != null) {
                boolean isFav = favoriteStore != null && favoriteStore.isFavorite(activeItemType, lastLoadedPreset);
                String pinLabel = isFav ? FAVORITES_UNPIN_TEXT : FAVORITES_PIN_TEXT;
                int btnY = leftPanel.bottom() - FAVORITES_PIN_BUTTON_OFFSET_Y;
                int btnW = FAVORITES_PIN_BUTTON_WIDTH;
                int btnH = FAVORITES_PIN_BUTTON_HEIGHT;
                int btnX = leftPanel.x() + FAVORITES_PIN_BUTTON_OFFSET_X;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, UIConstants.Button.NORMAL());
                AxiomRenderer.drawBorder(graphics, btnX, btnY, btnW, btnH, UIConstants.Border.DEFAULT());
                graphics.drawString(safeFont, pinLabel, btnX + FAVORITES_PIN_TEXT_OFFSET_X,
                    btnY + FAVORITES_PIN_TEXT_OFFSET_Y, UIConstants.Text.PRIMARY(), false);
            }
        }

        // Right panel (dev mode header)
        if (showDevPanel) {
            ResponsiveLayout.Rect rightPanel = layout.getDevModePanelArea();
            if (!rightPanel.isEmpty()) {
                graphics.fill(rightPanel.x(), rightPanel.y(), rightPanel.right(), rightPanel.bottom(),
                    UIConstants.Background.PANEL());
                AxiomRenderer.drawBorder(graphics, rightPanel.x(), rightPanel.y(),
                    rightPanel.width(), rightPanel.height(), UIConstants.Border.DEFAULT());
                graphics.drawString(safeFont, DEV_PANEL_HEADER_TEXT, rightPanel.x() + DEV_PANEL_TEXT_OFFSET_X,
                    rightPanel.y() + DEV_PANEL_TEXT_OFFSET_Y, UIConstants.Text.SECONDARY(), false);
            }
        }
    }

    private void renderDevPanel(GuiGraphics graphics, Font font, ResponsiveLayout layout, int width,
            DebugPanel debugPanel, ItemStack item, boolean isPreviewMode, EditorModule activeModule) {
        ResponsiveLayout.Rect devArea = layout.getDevModePanelArea();
        if (devArea.isEmpty()) {
            devArea = new ResponsiveLayout.Rect(
                width - DEV_PANEL_FALLBACK_WIDTH - DEV_PANEL_FALLBACK_MARGIN_RIGHT,
                DEV_PANEL_FALLBACK_MARGIN_TOP,
                DEV_PANEL_FALLBACK_WIDTH,
                DEV_PANEL_FALLBACK_HEIGHT
            );
        }

        if (debugPanel != null) {
            ItemStack displayItem = item;
            if (isPreviewMode && activeModule != null) {
                try {
                    var preview = activeModule.getPreviewItem();
                    if (preview != null) displayItem = preview;
                } catch (Exception ignored) {}
            }
            debugPanel.setStatSources(buildStatSources(displayItem));
            debugPanel.render(graphics, font, devArea.x(), devArea.y(), devArea.width(), devArea.height(),
                0, 0, displayItem);
            return;
        }

        var safeFont = Objects.requireNonNull(font, "font");
        graphics.fill(devArea.x(), devArea.y(), devArea.right(), devArea.bottom(), DEV_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, devArea.x(), devArea.y(), devArea.width(), devArea.height(),
            UIConstants.Border.ACCENT());

        int textY = devArea.y() + DEV_PANEL_TEXT_OFFSET_Y;
        graphics.drawString(safeFont, DEV_PANEL_TITLE_TEXT, devArea.x() + DEV_PANEL_TEXT_OFFSET_X, textY,
            DEV_PANEL_TITLE_COLOR, false);
        textY += DEV_PANEL_TITLE_LINE_STEP;

        EditorCache.CacheStats stats = EditorCache.INSTANCE.getStats();
        graphics.drawString(safeFont, "Cache: " + stats.valid() + "/" + stats.total(),
            devArea.x() + DEV_PANEL_TEXT_OFFSET_X, textY, UIConstants.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;
        graphics.drawString(safeFont, String.format("Hit: %.1f%%", stats.hitRate() * 100),
            devArea.x() + DEV_PANEL_TEXT_OFFSET_X, textY, UIConstants.Text.SECONDARY(), false);
    }

    private void renderLowConfidenceDialog(GuiGraphics graphics, Font font, int width, int height,
            int mouseX, int mouseY, ItemStack item, WeaponTypeDetector.DetectionResult detection,
            String lowConfidenceStatus,
            ResponsiveLayout.Rect continueBounds, ResponsiveLayout.Rect whitelistBounds,
            ResponsiveLayout.Rect cancelBounds) {
        // This method is called externally with bounds to update
        // The actual rendering logic stays in ItemEditorScreen for now as it needs to update bounds
    }

    private List<String> buildStatSources(ItemStack stack) {
        List<String> sources = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return sources;
        }

        CustomData data = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        boolean hasWeaponSpecific = false;
        boolean hasArmorSpecific = false;
        if (data != null) {
            try {
                var tag = data.copyTag();
                hasWeaponSpecific = tag != null && tag.contains(WEAPON_STATS_KEY);
                hasArmorSpecific = tag != null && tag.contains(ARMOR_STATS_KEY);
            } catch (Exception ignored) {}
        }

        boolean isArmor = ArmorConfigManager.isArmor(stack);
        boolean hasGlobal = isArmor
            ? ArmorConfigManager.hasGlobalConfig(stack.getItem())
            : WeaponConfigManager.hasGlobalConfig(stack.getItem());

        if (isArmor) {
            if (hasArmorSpecific) {
                sources.add(SOURCE_SPECIFIC_ARMOR);
                if (hasGlobal) sources.add(SOURCE_GLOBAL_OVERRIDE);
            } else if (hasGlobal) {
                sources.add(SOURCE_GLOBAL);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        } else {
            if (hasWeaponSpecific) {
                sources.add(SOURCE_SPECIFIC_WEAPON);
                if (hasGlobal) sources.add(SOURCE_GLOBAL_OVERRIDE);
            } else if (hasGlobal) {
                sources.add(SOURCE_GLOBAL);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        }

        return sources;
    }

    // Tooltip state accessors
    public String getTooltipText() { return tooltipText; }
    public int getTooltipX() { return tooltipX; }
    public int getTooltipY() { return tooltipY; }
    public void clearTooltip() { tooltipText = null; }
    public void setTooltip(String text, int x, int y) {
        this.tooltipText = text;
        this.tooltipX = x;
        this.tooltipY = y;
    }
}

package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.components.FooterComponent;
import com.frenkvs.devmod.ui.editor.components.HeaderComponent;
import com.frenkvs.devmod.ui.editor.components.LeftColumnComponent;
import com.frenkvs.devmod.ui.editor.components.ModeBadge;
import com.frenkvs.devmod.ui.editor.components.ScrollableContentArea;
import com.frenkvs.devmod.ui.editor.components.SlotSelector;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.core.OverlayInputGuard;
import com.frenkvs.devmod.ui.editor.favorites.FavoritePresetStore;
import com.frenkvs.devmod.ui.editor.modules.ArmorModule;
import com.frenkvs.devmod.ui.editor.modules.WeaponModule;
import com.frenkvs.devmod.ui.editor.systems.ConfirmDialog;
import com.frenkvs.devmod.ui.editor.systems.HelpOverlay;
import com.frenkvs.devmod.ui.editor.systems.MultiEditManager;
import com.frenkvs.devmod.ui.editor.systems.MultiEditPanel;
import com.frenkvs.devmod.ui.editor.systems.DebugPanel;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified Item Editor Screen.
 * Main entry point for all item editing functionality.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#31-unified-editor-architecture
 */
public class ItemEditorScreen extends Screen {

    // ═══════════════════════════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════════════════════════

    private final ResponsiveLayout layout;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final ItemStack item;
    private final ItemStack originalItem;
    private final EditorStartTab requestedTab;
    private EditorModule activeModule;
    private SlotSelector.SlotInfo selectedSlot;

    // Mode flags
    private boolean isPreviewMode = true;
    private boolean isGlobalMode = false;
    private boolean showDevPanel = false;

    // Components
    private final HeaderComponent header = new HeaderComponent();
    private final LeftColumnComponent leftColumn = new LeftColumnComponent();
    private final FooterComponent footer = new FooterComponent();
    private final ScrollableContentArea scrollArea = new ScrollableContentArea();

    // UI state
    private String statusMessage = null;
    private int statusColor = 0;
    private int statusTicks = 0;

    // Tooltip state
    private String tooltipText = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    // Dialog state
    private ConfirmDialog activeDialog = null;
    private long lastSaveTimestamp = 0;
    private boolean showHistoryPanel = false;
    private int historyScrollOffset = 0;
    private boolean showPresetsPanel = false;
    private int presetScrollOffset = 0;
    private boolean historyWasOpenBeforePresets = false;
    private String presetSearchQuery = "";
    private SortMode presetSortMode = SortMode.RECENT;
    private String renamingPreset = null;
    private String renameBuffer = "";
    private String lastLoadedPreset = null;
    private String lastHoveredPreset = null;
    private boolean presetSearchFocused = true;
    private final FavoritePresetStore favoriteStore = new FavoritePresetStore();

    // Help overlay
    private final HelpOverlay helpOverlay = new HelpOverlay();

    // Debug panel
    private DebugPanel debugPanel;

    // Multi-edit subsystem
    private MultiEditManager multiEditManager;
    private MultiEditPanel multiEditPanel;
    private boolean showMultiEditPanel = false;

    private enum SortMode { RECENT, ALPHA }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        super(Component.literal("Item Editor"));
        this.item = item.copy();
        this.originalItem = item.copy();
        this.requestedTab = startTab;
        this.layout = new ResponsiveLayout();
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        // Calculate layout for screen dimensions
        layout.calculate(width, height);

        // Resolve and initialize the module
        activeModule = resolveModule(item, requestedTab);
        activeModule.setStatusConsumer(this::showStatus);
        activeModule.setItem(item);
        activeModule.init(layout);
        activeModule.setDirtyTrackingEnabled(false); // preview mode: no dirty accumulation
        activeModule.clearDirty();
        configureHeader();
        configureLeftColumn();
        configureFooterCallbacks();

        // Initialize multi-edit subsystem
        this.multiEditManager = new MultiEditManager();
        this.multiEditPanel = new MultiEditPanel(multiEditManager);
        this.debugPanel = new DebugPanel();

        // Invalidate cache on init
        EditorCache.getInstance().invalidateAll();
    }

    private EditorModule resolveModule(ItemStack stack, EditorStartTab requested) {
        return switch (requested) {
            case WEAPON -> new WeaponModule();
            case ARMOR -> new ArmorModule();
            case GENERAL -> new PlaceholderModule("general", "Item Editor");
        };
    }

    private void configureHeader() {
        header.clearTabs();
        if (activeModule != null) {
            List<ModuleTab> tabs = activeModule.getTabs();
            for (ModuleTab tab : tabs) {
                header.addTab(tab.id(), tab.label());
            }
            header.selectTab(activeModule.getActiveTabIndex());
        }

        header.onTabChange(index -> {
            if (activeModule != null) {
                activeModule.setActiveTab(index);
                scrollArea.scrollToTop();
            }
        });
        header.onClose(this::handleCloseRequest);

        header.getModeBadge()
            .badgeType(ModeBadge.BadgeType.MODE)
            .mode(isPreviewMode ? ModeBadge.Mode.PREVIEW : ModeBadge.Mode.APPLY)
            .onModeChange(this::handleModeChange);

        header.getScopeBadge()
            .badgeType(ModeBadge.BadgeType.SCOPE)
            .scope(isGlobalMode ? ModeBadge.Scope.GLOBAL : ModeBadge.Scope.SPECIFIC)
            .onScopeChange(scope -> isGlobalMode = scope == ModeBadge.Scope.GLOBAL);
    }

    private void configureLeftColumn() {
        SlotSelector.SlotType slotType = switch (requestedTab) {
            case ARMOR -> SlotSelector.SlotType.ARMOR;
            default -> SlotSelector.SlotType.WEAPON;
        };
        leftColumn.slotType(slotType);
        leftColumn.item(item);
        leftColumn.onSlotSelect(this::handleSlotSwitch);
        selectedSlot = leftColumn.getSelectedSlot();
    }

    private void updateLeftColumnStats() {
        leftColumn.clearStats();
        leftColumn.item(item);
        if (activeModule instanceof WeaponModule weaponModule) {
            var stats = weaponModule.getStats();
            leftColumn.addStat("Attack", stats.attackDamage, "+%.1f");
            leftColumn.addStat("Speed", stats.attackSpeed, "+%.1f");
            leftColumn.addStat("Crit", stats.critChance * 100f, "%.0f%%");
        } else if (activeModule instanceof ArmorModule armorModule) {
            var stats = armorModule.getStats();
            leftColumn.addStat("Defense", stats.physicalReduction * 100f, "%.0f%%");
            leftColumn.addStat("Fire", stats.fireReduction * 100f, "%.0f%%");
            leftColumn.addStat("Magic", stats.magicReduction * 100f, "%.0f%%");
        }

        if (activeModule != null) {
            leftColumn.pendingChanges(activeModule.getPendingChanges().size());
        }
        if (lastSaveTimestamp > 0) {
            leftColumn.lastSaved(lastSaveTimestamp);
        }
    }

    private void handleSlotSwitch(SlotSelector.SlotInfo slot) {
        if (slot == null || Objects.equals(selectedSlot, slot)) {
            return;
        }
        if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.switchSlot(slot.label(), activeModule.getPendingChanges().size(),
                () -> {
                    selectedSlot = slot;
                    showStatus("Switched to " + slot.label(), UIConstants.Accent.BLUE);
                },
                () -> {});
            activeDialog.show();
            return;
        }
        selectedSlot = slot;
        showStatus("Switched to " + slot.label(), UIConstants.Accent.BLUE);
        if (showMultiEditPanel) {
            refreshMultiEditSelection();
        }
    }

    private void handleModeChange(ModeBadge.Mode mode) {
        boolean preview = mode == ModeBadge.Mode.PREVIEW;
        if (preview == isPreviewMode) {
            return;
        }
        isPreviewMode = preview;
        if (activeModule != null) {
            activeModule.setDirtyTrackingEnabled(!isPreviewMode);
            if (isPreviewMode) {
                activeModule.clearDirty();
                activeModule.applyPreview();
                activeModule.logEvent("Switched to PREVIEW (dirty off)");
            } else {
                if (!activeModule.hasUnsavedChanges() && activeModule.hasPendingDiff()) {
                    activeModule.markDirty("Pending changes from preview");
                }
                activeModule.logEvent("Switched to APPLY (dirty on)");
            }
        }
        showStatus(isPreviewMode ? "Preview Mode" : "Apply Mode",
            isPreviewMode ? UIConstants.Accent.CYAN : UIConstants.Accent.GREEN);
    }

    private void configureFooterCallbacks() {
        footer
            .onUndo(() -> {
                if (activeModule != null && activeModule.canUndo()) {
                    activeModule.undo();
                    showStatus("Undone", UIConstants.Accent.BLUE);
                }
            })
            .onRedo(() -> {
                if (activeModule != null && activeModule.canRedo()) {
                    activeModule.redo();
                    showStatus("Redone", UIConstants.Accent.BLUE);
                }
            })
            .onApply(this::applyChanges)
            .onAction(this::handleFooterAction);
    }

    private void handleFooterAction(String actionId) {
        if (actionId == null) return;
        switch (actionId) {
            case "reset" -> {
                if (activeModule != null) {
                    activeDialog = ConfirmDialog.resetToDefault(() -> {
                        activeModule.resetToOriginal();
                        showStatus("Reset to original", UIConstants.Accent.ORANGE);
                    }, () -> {});
                    activeDialog.show();
                }
            }
            case "cancel" -> handleCloseRequest();
            case "history" -> {
                showHistoryPanel = !showHistoryPanel;
                historyScrollOffset = 0;
                if (showHistoryPanel) {
                    showPresetsPanel = false;
                }
            }
            case "export" -> handleExport();
            case "import" -> handleImport();
            case "presets" -> {
                if (!supportsDataOps()) {
                    showStatus("Presets available only for weapons/armors", UIConstants.Accent.ORANGE);
                    return;
                }
                boolean newState = !showPresetsPanel;
                if (newState) {
                    historyWasOpenBeforePresets = showHistoryPanel;
                    showHistoryPanel = false;
                    presetScrollOffset = 0;
                    renamingPreset = null;
                } else {
                    closePresetsPanel();
                }
                showPresetsPanel = newState;
            }
            default -> {}
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark overlay background
        graphics.fill(0, 0, width, height, UIConstants.Background.OVERLAY);

        // Get layout areas
        ResponsiveLayout.Rect editorArea = layout.getEditorArea();
        int headerHeight = UIConstants.Size.HEADER_HEIGHT;
        int footerHeight = UIConstants.Size.FOOTER_HEIGHT;
        int leftWidth = UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH;
        int contentY = editorArea.y() + headerHeight;
        int footerY = editorArea.y() + editorArea.height() - footerHeight;
        int contentHeight = editorArea.height() - headerHeight - footerHeight;

        // Main panel background
        graphics.fill(editorArea.x(), editorArea.y(),
                     editorArea.right(), editorArea.bottom(),
                     UIConstants.Background.PANEL);

        // Panel border
        AxiomRenderer.drawBorder(graphics, editorArea.x(), editorArea.y(),
                                editorArea.width(), editorArea.height(),
                                UIConstants.Border.DEFAULT);

        // Header (tabs + badges + close)
        header.render(graphics, editorArea.x(), editorArea.y(), editorArea.width(), mouseX, mouseY);
        if (header.getModeBadge().isHovered()) {
            tooltipText = header.getModeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        } else if (header.getScopeBadge().isHovered()) {
            tooltipText = header.getScopeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        // Left column (preview + slots + info)
        if (activeModule != null) {
            updateLeftColumnStats();
            leftColumn.pendingChanges(activeModule.getPendingChanges().size());
        }
        leftColumn.render(graphics, editorArea.x(), contentY, contentHeight, mouseX, mouseY, partialTick);

        // Render multi-edit panel if visible (placed beneath left column area)
        if (showMultiEditPanel && multiEditPanel != null) {
            int panelX = editorArea.x() + 8;
            int panelY = contentY + 8;
            int panelW = UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH - 16;
            multiEditPanel.render(graphics, font, panelX, panelY, panelW, mouseX, mouseY);
        }

        // Content area (scrollable)
        int contentX = editorArea.x() + leftWidth + UIConstants.Spacing.MD;
        int contentWidth = editorArea.width() - leftWidth - UIConstants.Spacing.MD * 2;
        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, UIConstants.Background.CONTENT);
        if (activeModule != null) {
            int viewportHeight = contentHeight - UIConstants.Spacing.MD * 2;
            scrollArea.render(graphics, contentX, contentY, contentWidth, contentHeight,
                mouseX, mouseY, partialTick, (g, x, y, w, mx, my) -> {
                    ResponsiveLayout.Rect bounds = new ResponsiveLayout.Rect(x, y, w, viewportHeight);
                    activeModule.renderContent(g, bounds, mx, my + (int) scrollArea.getScrollOffset());
                    return activeModule.calculateContentHeight();
                });
        }

        // Footer
        footer
            .canUndo(activeModule != null && activeModule.canUndo())
            .canRedo(activeModule != null && activeModule.canRedo())
            .canApply(!isPreviewMode)
            .isDirty(activeModule != null && activeModule.hasUnsavedChanges())
            .pendingCount(activeModule != null ? activeModule.getPendingChanges().size() : 0);
        footer.render(graphics, editorArea.x(), footerY, editorArea.width(), mouseX, mouseY);

        // Render side panels if visible
        if (layout.showSidePanels()) {
            renderSidePanels(graphics, mouseX, mouseY);
        }

        // Status message overlay
        if (statusMessage != null && statusTicks > 0) {
            renderStatusMessage(graphics);
        }

        if (showHistoryPanel && activeModule != null) {
            renderHistoryPanel(graphics);
        }

        if (showPresetsPanel && supportsDataOps()) {
            renderPresetsPanel(graphics, mouseX, mouseY);
        }

        // Tooltip (rendered last, on top)
        if (tooltipText != null) {
            renderTooltip(graphics, tooltipText, tooltipX, tooltipY);
        }

        // Dev panel
        if (showDevPanel) {
            renderDevPanel(graphics, mouseX, mouseY);
        }

        // Modal dialog (rendered last, on top of everything)
        if (activeDialog != null && activeDialog.isVisible()) {
            activeDialog.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Help overlay (rendered on top of dialogs)
        if (helpOverlay.isVisible()) {
            helpOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }
    }

    private void renderSidePanels(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");

        // Left panel (favorites)
        ResponsiveLayout.Rect leftPanel = layout.getFavoritesPanelArea();
        if (!leftPanel.isEmpty()) {
            graphics.fill(leftPanel.x(), leftPanel.y(), leftPanel.right(), leftPanel.bottom(),
                         UIConstants.Background.PANEL);
            AxiomRenderer.drawBorder(graphics, leftPanel.x(), leftPanel.y(),
                                    leftPanel.width(), leftPanel.height(), UIConstants.Border.DEFAULT);
            graphics.drawString(safeFont, "Favorites", leftPanel.x() + 8, leftPanel.y() + 8,
                               UIConstants.Text.SECONDARY, false);

            List<ItemEditorDataManager.PresetData> favorites = getFavoritePresetsForActiveType();
            int rowHeight = 18;
            int startY = leftPanel.y() + 20;
            int maxRows = Math.max(0, (leftPanel.height() - 28) / rowHeight);
            for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                int rowY = startY + i * rowHeight;
                boolean hovered = mouseX >= leftPanel.x() + 4 && mouseX <= leftPanel.right() - 4
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
                if (hovered) {
                    graphics.fill(leftPanel.x() + 2, rowY, leftPanel.right() - 2, rowY + rowHeight, UIConstants.Background.HOVER);
                    if (tooltipText == null) {
                        tooltipText = preset.name;
                        tooltipX = mouseX;
                        tooltipY = mouseY;
                    }
                }
                String label = preset.name == null ? "Preset" : preset.name;
                graphics.drawString(safeFont, "★ " + label, leftPanel.x() + 8, rowY + 5,
                    hovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY, false);
            }

            // Pin toggle for last loaded preset
            if (lastLoadedPreset != null) {
                boolean isFav = favoriteStore.isFavorite(getActiveItemType(), lastLoadedPreset);
                String pinLabel = isFav ? "Unpin last" : "Pin last";
                int btnY = leftPanel.bottom() - 18;
                int btnW = 80;
                graphics.fill(leftPanel.x() + 8, btnY, leftPanel.x() + 8 + btnW, btnY + 14, UIConstants.Button.NORMAL);
                AxiomRenderer.drawBorder(graphics, leftPanel.x() + 8, btnY, btnW, 14, UIConstants.Border.DEFAULT);
                graphics.drawString(safeFont, pinLabel, leftPanel.x() + 12, btnY + 3, UIConstants.Text.PRIMARY, false);
            }
        }

        // Right panel (dev mode)
        if (showDevPanel) {
            ResponsiveLayout.Rect rightPanel = layout.getDevModePanelArea();
            if (!rightPanel.isEmpty()) {
                graphics.fill(rightPanel.x(), rightPanel.y(), rightPanel.right(), rightPanel.bottom(),
                             UIConstants.Background.PANEL);
                AxiomRenderer.drawBorder(graphics, rightPanel.x(), rightPanel.y(),
                                        rightPanel.width(), rightPanel.height(), UIConstants.Border.DEFAULT);
                graphics.drawString(safeFont, "Dev Mode", rightPanel.x() + 8, rightPanel.y() + 8,
                                   UIConstants.Text.SECONDARY, false);
            }
        }
    }

    private void refreshMultiEditSelection() {
        if (multiEditManager == null) return;
        multiEditManager.clearSelection();
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        var inv = mc.player.getInventory();
        var targetItem = item.getItem();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                multiEditManager.addToSelection(stack, i);
            }
        }
    }

    private void renderStatusMessage(GuiGraphics graphics) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        String safeMessage = Objects.requireNonNull(statusMessage, "statusMessage cannot be null");
        int msgWidth = safeFont.width(safeMessage) + 20;
        int msgX = (width - msgWidth) / 2;
        int msgY = height - 60;

        graphics.fill(msgX, msgY, msgX + msgWidth, msgY + 20, 0xE0000000);
        AxiomRenderer.drawBorder(graphics, msgX, msgY, msgWidth, 20, statusColor);
        graphics.drawString(safeFont, safeMessage, msgX + 10, msgY + 6, statusColor, false);
    }

    private void renderHistoryPanel(GuiGraphics graphics) {
        if (activeModule == null) return;
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        int panelWidth = 260;
        int panelHeight = 200;
        int x = layout.getEditorX() + layout.getEditorWidth() - panelWidth - 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, UIConstants.Border.DEFAULT);

        graphics.drawString(safeFont, "Edit History", x + 8, y + 8, UIConstants.Text.TITLE, false);
        int listY = y + 24;
        var entries = activeModule.getHistoryEntries();
        int lineHeight = 14;
        int listHeight = panelHeight - 50;
        int maxScroll = Math.max(0, Math.max(0, entries.size() * lineHeight - listHeight));
        historyScrollOffset = Math.max(0, Math.min(historyScrollOffset, maxScroll));

        int visibleLines = listHeight / lineHeight;
        if (entries.isEmpty()) {
            graphics.drawString(safeFont, "No history yet", x + 8, listY, UIConstants.Text.MUTED, false);
        } else {
            int startFromEnd = historyScrollOffset / lineHeight;
            int startIndex = Math.max(0, entries.size() - visibleLines - startFromEnd);
            int endIndex = Math.min(entries.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                String entry = entries.get(entries.size() - 1 - i);
                int entryY = listY + (i - startIndex) * lineHeight;
                graphics.drawString(safeFont, entry, x + 8, entryY, UIConstants.Text.SECONDARY, false);
            }
        }

        // Footer with clear button and counter
        int footerY = y + panelHeight - 18;
        String countText = entries.isEmpty()
            ? "Showing 0/0"
            : "Showing " + Math.min(visibleLines, entries.size()) + "/" + entries.size();
        graphics.drawString(safeFont, countText, x + 8, footerY, UIConstants.Text.MUTED, false);

        // Clear button
        int clearW = 60;
        int clearH = 14;
        int clearX = x + panelWidth - clearW - 8;
        int clearY = footerY - 2;
        int clearBg = entries.isEmpty() ? UIConstants.Button.DISABLED : UIConstants.Background.INPUT;
        graphics.fill(clearX, clearY, clearX + clearW, clearY + clearH, clearBg);
        AxiomRenderer.drawBorder(graphics, clearX, clearY, clearW, clearH, UIConstants.Border.DEFAULT);
        int clearColor = entries.isEmpty() ? UIConstants.Text.DISABLED : UIConstants.Text.PRIMARY;
        graphics.drawString(safeFont, "Clear", clearX + 12, clearY + 3, clearColor, false);
    }

    private boolean handleHistoryClick(double mouseX, double mouseY) {
        int panelWidth = 260;
        int panelHeight = 200;
        int x = layout.getEditorX() + layout.getEditorWidth() - panelWidth - 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;

        // Click outside closes panel
        if (mouseX < x || mouseX > x + panelWidth || mouseY < y || mouseY > y + panelHeight) {
            showHistoryPanel = false;
            return true;
        }

        // Clear button
        int clearW = 60;
        int clearH = 14;
        int footerY = y + panelHeight - 18;
        int clearX = x + panelWidth - clearW - 8;
        int clearY = footerY - 2;
        if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= clearY && mouseY <= clearY + clearH) {
            if (activeModule != null) {
                activeModule.clearHistory();
                historyScrollOffset = 0;
            }
            return true;
        }

        return false;
    }

    private boolean isPointInHistoryPanel(double mouseX, double mouseY) {
        int panelWidth = 260;
        int panelHeight = 200;
        int x = layout.getEditorX() + layout.getEditorWidth() - panelWidth - 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;
        return mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY <= y + panelHeight;
    }

    private void renderTooltip(GuiGraphics graphics, String text, int x, int y) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        String safeText = Objects.requireNonNull(text, "text cannot be null");
        int tipWidth = safeFont.width(safeText) + 8;
        int tipX = Math.min(x + 12, width - tipWidth - 4);
        int tipY = Math.max(y - 20, 4);

        graphics.fill(tipX, tipY, tipX + tipWidth, tipY + 14, 0xF0100010);
        AxiomRenderer.drawBorder(graphics, tipX, tipY, tipWidth, 14, UIConstants.Border.DEFAULT);
        graphics.drawString(safeFont, safeText, tipX + 4, tipY + 3, UIConstants.Text.PRIMARY, false);

        tooltipText = null;
    }

    private void renderDevPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        ResponsiveLayout.Rect devArea = layout.getDevModePanelArea();
        if (devArea.isEmpty()) {
            devArea = new ResponsiveLayout.Rect(width - 160, 10, 150, 200);
        }

        // Delegate to DebugPanel for richer info
        if (debugPanel != null) {
            debugPanel.render(graphics, font, devArea.x(), devArea.y(), devArea.width(), devArea.height(), mouseX, mouseY, item);
            return;
        }

        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.fill(devArea.x(), devArea.y(), devArea.right(), devArea.bottom(), 0xE0101020);
        AxiomRenderer.drawBorder(graphics, devArea.x(), devArea.y(), devArea.width(), devArea.height(), UIConstants.Border.ACCENT);

        int textY = devArea.y() + 8;
        graphics.drawString(safeFont, "§b[Dev Mode]", devArea.x() + 8, textY, 0xFFFFFFFF, false);
        textY += 12;

        EditorCache.CacheStats stats = EditorCache.getInstance().getStats();
        graphics.drawString(safeFont, "Cache: " + stats.valid() + "/" + stats.total(), devArea.x() + 8, textY, UIConstants.Text.SECONDARY, false);
        textY += 10;
        graphics.drawString(safeFont, String.format("Hit: %.1f%%", stats.hitRate() * 100), devArea.x() + 8, textY, UIConstants.Text.SECONDARY, false);
        textY += 10;

        graphics.drawString(safeFont, "Size: " + layout.getScreenSize(), devArea.x() + 8, textY, UIConstants.Text.SECONDARY, false);
        textY += 10;
        graphics.drawString(safeFont, "Scroll: " + (int) scrollArea.getScrollOffset(), devArea.x() + 8, textY, UIConstants.Text.SECONDARY, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle help overlay first
        if (helpOverlay.isVisible()) {
            return helpOverlay.mouseClicked((int) mouseX, (int) mouseY);
        }

        // Handle modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.mouseClicked((int) mouseX, (int) mouseY);
        }

        if (showDevPanel && debugPanel != null && debugPanel.handleClick(mouseX, mouseY)) {
            showStatus("Copied debug log", UIConstants.Accent.BLUE);
            return true;
        }

        if (showHistoryPanel && handleHistoryClick(mouseX, mouseY)) {
            return true;
        }
        if (OverlayInputGuard.shouldConsumePresetInput(showPresetsPanel)) {
            presetSearchFocused = true;
            handlePresetsClick(mouseX, mouseY);
            return true;
        }
        if (handleFavoritesClick(mouseX, mouseY)) {
            return true;
        }

        // Reset tooltip
        tooltipText = null;

        if (header.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (footer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (leftColumn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (scrollArea.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (activeModule != null) {
            return activeModule.mouseClicked(mouseX, mouseY + scrollArea.getScrollOffset(), button);
        }

        // If not handled by other components, give multi-edit panel a chance
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseClicked(mouseX, mouseY, button)) {
            // If the panel performed a batch operation, surface the result
            var result = multiEditPanel.takeLastResult();
            if (result != null) {
                int succ = result.successCount();
                int fail = result.failureCount();
                if (fail == 0) {
                    showStatus("Applied preset to " + succ + " items", UIConstants.Accent.GREEN);
                    if (debugPanel != null) debugPanel.log("MultiEdit apply: " + succ + " successes");
                } else {
                    // Show brief summary and log detailed failures
                    showStatus("Applied: " + succ + " successes, " + fail + " failures", UIConstants.Accent.ORANGE);
                    try {
                        var failures = result.failures();
                        // Build short list (up to 5) for immediate feedback
                        StringBuilder sb = new StringBuilder();
                        int limit = Math.min(5, failures.size());
                        for (int i = 0; i < limit; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(failures.get(i));
                        }
                        if (failures.size() > limit) sb.append(" (+" + (failures.size() - limit) + " more)");
                        DevMod.LOGGER.warn("MultiEdit apply failures: {}", failures);
                        showStatus("Failures: " + sb.toString(), UIConstants.Accent.RED);
                        if (debugPanel != null) {
                            String first = failures.isEmpty() ? "" : failures.get(0);
                            debugPanel.log("MultiEdit apply: " + succ + " successes, " + fail + " failures - first: " + first);
                        }
                    } catch (Exception ignore) {
                        // ignore logging failure
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollArea.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (activeModule != null) {
            return activeModule.mouseReleased(mouseX, mouseY + scrollArea.getScrollOffset(), button);
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollArea.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (activeModule != null) {
            return activeModule.mouseDragged(mouseX, mouseY + scrollArea.getScrollOffset(), button, dragX, dragY);
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showHistoryPanel) {
            if (isPointInHistoryPanel(mouseX, mouseY)) {
                historyScrollOffset -= (int) (scrollY * 14);
                return true;
            }
        }
        if (showPresetsPanel) {
            if (isPointInPresetsPanel(mouseX, mouseY)) {
                presetScrollOffset -= (int) (scrollY * 16);
            }
            return true; // Consume all scroll while overlay open
        }
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (activeModule != null) {
            return activeModule.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle help overlay first
        if (helpOverlay.isVisible()) {
            return helpOverlay.keyPressed(keyCode);
        }

        if (showHistoryPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            showHistoryPanel = false;
            return true;
        }
        if (showPresetsPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePresetsPanel();
            return true;
        }

        // Handle modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.keyPressed(keyCode);
        }

        if (showPresetsPanel) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renamingPreset != null && !renameBuffer.isEmpty()) {
                    renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                } else if (renamingPreset == null && !presetSearchQuery.isEmpty()) {
                    presetSearchQuery = presetSearchQuery.substring(0, presetSearchQuery.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && renamingPreset != null) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                return true; // do nothing else
            }
            if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                renamingPreset = null;
                presetSearchFocused = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE && lastHoveredPreset != null) {
                ItemEditorDataManager.INSTANCE.getPreset(lastHoveredPreset).ifPresent(preset -> {
                    activeDialog = ConfirmDialog.deletePreset(preset.name == null ? "preset" : preset.name,
                        () -> {
                            ItemEditorDataManager.INSTANCE.deletePreset(preset.name);
                            ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", item.getHoverName().getString(), preset.name);
                            DevMod.LOGGER.info("[Editor] Preset deleted via keyboard: {}", preset.name);
                        },
                        () -> {});
                    activeDialog.show();
                });
                return true;
            }
            return true;
        }

        // Escape to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            handleCloseRequest();
            return true;
        }

        // F5 toggle preview/apply
        if (keyCode == GLFW.GLFW_KEY_F5) {
            handleModeChange(isPreviewMode ? ModeBadge.Mode.APPLY : ModeBadge.Mode.PREVIEW);
            return true;
        }

        // Ctrl+Enter quick apply (only APPLY + dirty)
        if (keyCode == GLFW.GLFW_KEY_ENTER && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (isPreviewMode) {
                showStatus("Preview mode: cannot apply", UIConstants.Accent.ORANGE);
            } else if (activeModule != null && activeModule.hasUnsavedChanges()) {
                applyChanges();
            } else {
                showStatus("No changes to apply", UIConstants.Accent.ORANGE);
            }
            return true;
        }

        // Component shortcuts
        if (header.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (footer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (leftColumn.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (scrollArea.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // F3+D for dev panel toggle
        if (keyCode == GLFW.GLFW_KEY_D && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            showDevPanel = !showDevPanel;
            return true;
        }

        // Toggle multi-edit panel (M)
        if (keyCode == GLFW.GLFW_KEY_M) {
            showMultiEditPanel = !showMultiEditPanel;
            if (showMultiEditPanel) {
                refreshMultiEditSelection();
            }
            return true;
        }

        // F1 for help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            helpOverlay.toggle();
            return true;
        }

        // Pass to module
        if (activeModule != null) {
            return activeModule.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (showPresetsPanel) {
            if (Character.isISOControl(chr)) {
                return true;
            }
            if (renamingPreset != null) {
                renameBuffer += chr;
            } else if (presetSearchFocused) {
                presetSearchQuery += chr;
            }
            return true;
        }
        if (activeModule != null) {
            return activeModule.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    private void applyChanges() {
        if (activeModule == null) return;

        if (isPreviewMode) {
            showStatus("Preview mode: Apply disabled", UIConstants.Accent.ORANGE);
            return;
        }
        if (!activeModule.hasUnsavedChanges()) {
            showStatus("No changes to apply", UIConstants.Accent.ORANGE);
            activeModule.logEvent("Apply skipped (no changes)");
            return;
        }

        try {
            if (activeModule != null) {
                activeModule.logEvent("Apply requested (" + (isGlobalMode ? "GLOBAL" : "SPECIFIC") + ")");
            }
            // Build and send payload
            var payload = activeModule.buildPayload(isGlobalMode);
            if (payload != null) {
                PacketDistributor.sendToServer(payload);
            }

            // Apply preview locally to reflect new state
            activeModule.applyPreview();

            // Clear dirty state
            activeModule.clearDirty();
            lastSaveTimestamp = System.currentTimeMillis();

            // Invalidate cache
            EditorCache.getInstance().invalidateAll();

            playSound(SoundEvents.UI_BUTTON_CLICK.value());
            showStatus("Changes applied!", UIConstants.Accent.GREEN);
            activeModule.logEvent("Apply sent to server");

        } catch (Exception e) {
            showStatus("Failed to apply: " + e.getMessage(), UIConstants.Accent.RED);
            if (activeModule != null) {
                activeModule.logEvent("Apply error: " + e.getMessage());
            }
        }
    }

    private void handleCloseRequest() {
        if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.unsavedChanges(
                activeModule.getPendingChanges().size(),
                this::onClose,
                () -> {}
            );
            activeDialog.show();
            return;
        }
        onClose();
    }

    private void closePresetsPanel() {
        if (historyWasOpenBeforePresets) {
            showHistoryPanel = true;
            historyWasOpenBeforePresets = false;
        }
        showPresetsPanel = false;
        renamingPreset = null;
        renameBuffer = "";
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA OPS: EXPORT / IMPORT / PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void handleExport() {
        if (!supportsDataOps()) {
            showStatus("Export available only for weapons/armors", UIConstants.Accent.ORANGE);
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        ItemEditorDataManager.ItemConfigExport config = new ItemEditorDataManager.ItemConfigExport();
        config.itemId = getCurrentItemId();
        config.itemName = item.getHoverName().getString();
        config.stats = collectStatsForExport();

        String fileName = buildSafeFileName(config.itemId + "_export_" + System.currentTimeMillis());
        boolean ok = data.exportToFile(config, fileName);
        if (ok) {
            data.addHistoryEntry("export", config.itemName, fileName);
            showStatus("Exported to " + fileName + ".json", UIConstants.Accent.GREEN);
        } else {
            showStatus("Export failed", UIConstants.Accent.RED);
        }
    }

    private void handleImport() {
        if (!supportsDataOps()) {
            showStatus("Import available only for weapons/armors", UIConstants.Accent.ORANGE);
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> exports = data.listExportFiles();
        if (exports.isEmpty()) {
            showStatus("No exports found", UIConstants.Accent.ORANGE);
            return;
        }

        // Use most recent export (last in sorted list)
        String fileName = exports.get(exports.size() - 1);
        try {
            ItemEditorDataManager.ItemConfigExport imported = data.importFromFile(fileName);
            applyImportedStats(imported, "Imported " + fileName);
            data.addHistoryEntry("import", item.getHoverName().getString(), fileName);
            showStatus("Imported " + fileName, UIConstants.Accent.BLUE);
        } catch (Exception e) {
            showStatus("Import failed: " + e.getMessage(), UIConstants.Accent.RED);
        }
    }

    private boolean supportsDataOps() {
        return activeModule instanceof WeaponModule || activeModule instanceof ArmorModule;
    }

    private List<ItemEditorDataManager.PresetData> getFilteredPresets() {
        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<ItemEditorDataManager.PresetData> base = data.getPresetsForItemType(getActiveItemType());

        String query = presetSearchQuery == null ? "" : presetSearchQuery.trim().toLowerCase();
        List<ItemEditorDataManager.PresetData> filtered = new ArrayList<>();
        for (ItemEditorDataManager.PresetData preset : base) {
            if (preset == null) continue;
            String name = preset.name == null ? "" : preset.name.toLowerCase();
            if (!query.isEmpty() && !name.contains(query)) continue;
            filtered.add(preset);
        }

        filtered.sort((a, b) -> {
            if (presetSortMode == SortMode.ALPHA) {
                String an = a.name == null ? "" : a.name.toLowerCase();
                String bn = b.name == null ? "" : b.name.toLowerCase();
                int cmp = an.compareTo(bn);
                if (cmp != 0) return cmp;
            }
            return Long.compare(b.createdAt, a.createdAt); // recent first
        });

        return filtered;
    }

    private List<ItemEditorDataManager.PresetData> getFavoritePresetsForActiveType() {
        List<ItemEditorDataManager.PresetData> result = new ArrayList<>();
        for (String name : favoriteStore.getFavorites(getActiveItemType())) {
            ItemEditorDataManager.INSTANCE.getPreset(name).ifPresent(result::add);
        }
        return result;
    }

    private String getActiveItemType() {
        if (activeModule instanceof WeaponModule) return "weapon";
        if (activeModule instanceof ArmorModule) return "armor";
        return "item";
    }

    private String getCurrentItemId() {
        var key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem(), "item cannot be null"));
        return key == null ? "unknown" : key.toString();
    }

    private String buildSafeFileName(String raw) {
        return raw.replace(":", "_").replace("/", "_");
    }

    private String buildPresetName(String itemType) {
        String base = buildSafeFileName(getCurrentItemId());
        return base + "_preset_" + itemType + "_" + System.currentTimeMillis();
    }

    private String getDevModVersion() {
        return ModList.get()
            .getModContainerById(DevMod.MODID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("unknown");
    }

    private List<Float> collectStatsForExport() {
        List<Float> stats = new ArrayList<>();
        if (activeModule instanceof WeaponModule weaponModule) {
            var s = weaponModule.getStats();
            stats.add(s.headMult);
            stats.add(s.bodyMult);
            stats.add(s.armsMult);
            stats.add(s.legsMult);
            stats.add(s.attackDamage);
            stats.add(s.attackSpeed);
            stats.add(s.attackReach);
            stats.add(s.attackKnockback);
            stats.add(s.armorPenetration);
            stats.add(s.baseDamageBonus);
            stats.add(s.critChance);
            stats.add(s.critDamage);
            stats.add(s.lifesteal);
            stats.add(s.fireDamageBonus);
            stats.add(s.magicDamageBonus);
        } else if (activeModule instanceof ArmorModule armorModule) {
            var s = armorModule.getStats();
            stats.add(s.physicalReduction);
            stats.add(s.fireReduction);
            stats.add(s.magicReduction);
            stats.add(s.explosionReduction);
            stats.add(s.projectileReduction);
            stats.add(s.armorBonus);
            stats.add(s.toughnessBonus);
            stats.add(s.knockbackResistance);
            stats.add(s.thornsPercent);
            stats.add(s.thornsReflect ? 1f : 0f);
        }
        return stats;
    }

    private ItemEditorDataManager.PresetData buildPresetFromCurrent(String name, String itemType) {
        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name);
        preset.itemType = itemType;
        preset.scope = isGlobalMode ? "GLOBAL" : "SPECIFIC";
        preset.devmodVersion = getDevModVersion();
        preset.statValues = collectStatsForExport();
        return preset;
    }

    private String formatRelativeTime(long timestampMs) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestampMs);
        java.time.Duration dur = java.time.Duration.between(instant, java.time.Instant.now());
        long seconds = Math.max(0, dur.getSeconds());
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    private void applyImportedStats(ItemEditorDataManager.ItemConfigExport config, String reason) {
        if (config == null || config.stats == null) {
            showStatus("Import file missing stats", UIConstants.Accent.RED);
            return;
        }

        if (activeModule instanceof WeaponModule weaponModule) {
            WeaponStats newStats = weaponModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.headMult = getStat(values, 0, newStats.headMult);
            newStats.bodyMult = getStat(values, 1, newStats.bodyMult);
            newStats.armsMult = getStat(values, 2, newStats.armsMult);
            newStats.legsMult = getStat(values, 3, newStats.legsMult);
            newStats.attackDamage = getStat(values, 4, newStats.attackDamage);
            newStats.attackSpeed = getStat(values, 5, newStats.attackSpeed);
            newStats.attackReach = getStat(values, 6, newStats.attackReach);
            newStats.attackKnockback = getStat(values, 7, newStats.attackKnockback);
            newStats.armorPenetration = getStat(values, 8, newStats.armorPenetration);
            newStats.baseDamageBonus = getStat(values, 9, newStats.baseDamageBonus);
            newStats.critChance = getStat(values, 10, newStats.critChance);
            newStats.critDamage = getStat(values, 11, newStats.critDamage);
            newStats.lifesteal = getStat(values, 12, newStats.lifesteal);
            newStats.fireDamageBonus = getStat(values, 13, newStats.fireDamageBonus);
            newStats.magicDamageBonus = getStat(values, 14, newStats.magicDamageBonus);
            weaponModule.applyExternalStats(newStats, reason);
            weaponModule.applyPreview();
        } else if (activeModule instanceof ArmorModule armorModule) {
            ArmorStats newStats = armorModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.physicalReduction = getStat(values, 0, newStats.physicalReduction);
            newStats.fireReduction = getStat(values, 1, newStats.fireReduction);
            newStats.magicReduction = getStat(values, 2, newStats.magicReduction);
            newStats.explosionReduction = getStat(values, 3, newStats.explosionReduction);
            newStats.projectileReduction = getStat(values, 4, newStats.projectileReduction);
            newStats.armorBonus = getStat(values, 5, newStats.armorBonus);
            newStats.toughnessBonus = getStat(values, 6, newStats.toughnessBonus);
            newStats.knockbackResistance = getStat(values, 7, newStats.knockbackResistance);
            newStats.thornsPercent = getStat(values, 8, newStats.thornsPercent);
            newStats.thornsReflect = getStat(values, 9, newStats.thornsReflect ? 1f : 0f) > 0.5f;
            armorModule.applyExternalStats(newStats, reason);
            armorModule.applyPreview();
        } else {
            showStatus("Unsupported editor for import", UIConstants.Accent.RED);
        }
    }

    private void applyPreset(ItemEditorDataManager.PresetData preset) {
        if (preset == null) {
            showStatus("Preset not found", UIConstants.Accent.RED);
            return;
        }
        ItemEditorDataManager.ItemConfigExport wrapper = new ItemEditorDataManager.ItemConfigExport();
        wrapper.stats = preset.statValues;
        applyImportedStats(wrapper, "Preset: " + preset.name);
        lastLoadedPreset = preset.name;
    }

    private float getStat(List<Float> values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.size()) {
            return fallback;
        }
        Float value = values.get(index);
        return value == null ? fallback : value;
    }

    private void commitRename() {
        if (renamingPreset == null) return;
        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        data.getPreset(renamingPreset).ifPresent(p -> {
            String newName = renameBuffer.isBlank() ? p.name : renameBuffer.trim();
            p.name = newName;
            data.savePreset(p);
            data.addHistoryEntry("preset_rename", item.getHoverName().getString(), newName);
            DevMod.LOGGER.info("[Editor] Preset renamed to {}", newName);
            showStatus("Preset renamed: " + newName, UIConstants.Accent.BLUE);
        });
        renamingPreset = null;
        renameBuffer = "";
    }

    private void renderPresetsPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ItemEditorDataManager.PresetData> presets = getFilteredPresets();
        var safeFont = Objects.requireNonNull(font, "font cannot be null");

        int panelWidth = 288;
        int panelHeight = 224;
        int x = layout.getEditorX() + 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, UIConstants.Border.DEFAULT);

        graphics.drawString(safeFont, "Presets", x + 8, y + 8, UIConstants.Text.TITLE, false);

        // Search box
        int searchX = x + 8;
        int searchY = y + 24;
        int searchW = panelWidth - 8 - 80 - 8; // leave room for sort button and save
        int searchH = 16;
        graphics.fill(searchX, searchY, searchX + searchW, searchY + searchH, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, searchX, searchY, searchW, searchH, UIConstants.Border.DEFAULT);
        String searchLabel = presetSearchQuery.isEmpty() ? "Search..." : presetSearchQuery;
        int searchColor = presetSearchQuery.isEmpty() ? UIConstants.Text.MUTED : UIConstants.Text.PRIMARY;
        graphics.drawString(safeFont, searchLabel, searchX + 4, searchY + 4, searchColor, false);

        // Sort toggle
        int sortW = 36;
        int sortX = searchX + searchW + 4;
        graphics.fill(sortX, searchY, sortX + sortW, searchY + searchH,
            presetSortMode == SortMode.ALPHA ? UIConstants.Button.HOVER : UIConstants.Button.NORMAL);
        AxiomRenderer.drawBorder(graphics, sortX, searchY, sortW, searchH, UIConstants.Border.DEFAULT);
        String sortLabel = presetSortMode == SortMode.ALPHA ? "A-Z" : "Rec";
        graphics.drawString(safeFont, sortLabel, sortX + 6, searchY + 4, UIConstants.Text.PRIMARY, false);

        // Save button
        int saveW = 64;
        int saveH = 16;
        int saveX = x + panelWidth - saveW - 8;
        int saveY = searchY;
        graphics.fill(saveX, saveY, saveX + saveW, saveY + saveH, UIConstants.Button.NORMAL);
        AxiomRenderer.drawBorder(graphics, saveX, saveY, saveW, saveH, UIConstants.Border.DEFAULT);
        graphics.drawString(safeFont, "Save", saveX + 18, saveY + 4, UIConstants.Text.PRIMARY, false);

        // List area
        int listY = y + 48;
        int rowHeight = 24;
        int listHeight = panelHeight - (listY - y) - 8;
        int maxScroll = Math.max(0, Math.max(0, presets.size() * rowHeight - listHeight));
        presetScrollOffset = Math.max(0, Math.min(presetScrollOffset, maxScroll));
        int startRow = presetScrollOffset / rowHeight;
        int visibleRows = listHeight / rowHeight + 1;
        int endRow = Math.min(presets.size(), startRow + visibleRows);

        if (presets.isEmpty()) {
            graphics.drawString(safeFont, "No presets yet", x + 8, listY, UIConstants.Text.MUTED, false);
        } else {
            for (int i = startRow; i < endRow; i++) {
                ItemEditorDataManager.PresetData preset = presets.get(i);
                int rowY = listY + (i - startRow) * rowHeight;
                boolean hovered = mouseX >= x + 4 && mouseX <= x + panelWidth - 4
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
                if (hovered) {
                    graphics.fill(x + 2, rowY, x + panelWidth - 2, rowY + rowHeight, UIConstants.Background.HOVER);
                }
                if (hovered) {
                    lastHoveredPreset = preset.name;
                }

                // Name line
                String label = preset.name != null ? preset.name : "Preset " + i;
                if (Objects.equals(label, lastLoadedPreset)) {
                    label = "✓ " + label;
                }
                graphics.drawString(safeFont, label, x + 8, rowY + 6,
                    hovered ? UIConstants.Text.PRIMARY : UIConstants.Text.SECONDARY, false);

                // Metadata line
                String metaTime = formatRelativeTime(preset.createdAt);
                String meta = (preset.scope == null ? "SPECIFIC" : preset.scope)
                    + " • " + (preset.itemType == null ? "item" : preset.itemType)
                    + " • " + metaTime
                    + " • v" + (preset.devmodVersion == null ? "?" : preset.devmodVersion);
                graphics.drawString(safeFont, meta, x + 8, rowY + 16, UIConstants.Text.MUTED, false);
                if (hovered) {
                    tooltipText = java.time.Instant.ofEpochMilli(preset.createdAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toString();
                    tooltipX = mouseX + 12;
                    tooltipY = mouseY;
                }

                // Row buttons (rename/delete)
                int btnY = rowY + 4;
                int btnSize = 12;
                int deleteX = x + panelWidth - btnSize - 8;
                int renameX = deleteX - btnSize - 4;
                graphics.fill(renameX, btnY, renameX + btnSize, btnY + btnSize, UIConstants.Button.NORMAL);
                AxiomRenderer.drawBorder(graphics, renameX, btnY, btnSize, btnSize, UIConstants.Border.DEFAULT);
                graphics.drawString(safeFont, "R", renameX + 3, btnY + 2, UIConstants.Text.PRIMARY, false);

                graphics.fill(deleteX, btnY, deleteX + btnSize, btnY + btnSize, UIConstants.Button.NORMAL);
                AxiomRenderer.drawBorder(graphics, deleteX, btnY, btnSize, btnSize, UIConstants.Border.DEFAULT);
                graphics.drawString(safeFont, "X", deleteX + 3, btnY + 2, UIConstants.Text.PRIMARY, false);

                if (renamingPreset != null && renamingPreset.equals(preset.name)) {
                    int rnW = panelWidth - 32;
                    int rnX = x + 8;
                    int rnY = rowY - 18;
                    graphics.fill(rnX, rnY, rnX + rnW, rnY + 16, UIConstants.Background.INPUT);
                    AxiomRenderer.drawBorder(graphics, rnX, rnY, rnW, 16, UIConstants.Border.ACCENT);
                    String text = renameBuffer.isEmpty() ? preset.name : renameBuffer;
                    graphics.drawString(safeFont, text, rnX + 4, rnY + 4, UIConstants.Text.PRIMARY, false);
                }
            }
        }
    }

    private boolean handlePresetsClick(double mouseX, double mouseY) {
        if (!supportsDataOps()) return true;

        List<ItemEditorDataManager.PresetData> presets = getFilteredPresets();

        int panelWidth = 288;
        int panelHeight = 224;
        int x = layout.getEditorX() + 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;

        // Click outside closes panel and restores history state
        if (mouseX < x || mouseX > x + panelWidth || mouseY < y || mouseY > y + panelHeight) {
            closePresetsPanel();
            return true;
        }

        // Search box click focuses search
        int searchX = x + 8;
        int searchY = y + 24;
        int searchW = panelWidth - 8 - 80 - 8;
        int searchH = 16;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            renamingPreset = null;
            presetSearchFocused = true;
            return true;
        }

        // Sort toggle
        int sortW = 36;
        int sortX = searchX + searchW + 4;
        if (mouseX >= sortX && mouseX <= sortX + sortW && mouseY >= searchY && mouseY <= searchY + searchH) {
            presetSortMode = presetSortMode == SortMode.RECENT ? SortMode.ALPHA : SortMode.RECENT;
            return true;
        }

        // Save button
        int saveW = 64;
        int saveH = 16;
        int saveX = x + panelWidth - saveW - 8;
        int saveY = searchY;
        if (mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= saveY && mouseY <= saveY + saveH) {
            String presetName = buildPresetName(getActiveItemType());
            ItemEditorDataManager.PresetData preset = buildPresetFromCurrent(presetName, getActiveItemType());
            ItemEditorDataManager.INSTANCE.savePreset(preset);
            ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save", item.getHoverName().getString(), presetName);
            DevMod.LOGGER.info("[Editor] Preset saved: {}", presetName);
            showStatus("Preset saved: " + presetName, UIConstants.Accent.GREEN);
            lastLoadedPreset = presetName;
            return true;
        }

        // List items
        int listY = y + 48;
        int rowHeight = 24;
        int listHeight = panelHeight - (listY - y) - 8;
        int maxScroll = Math.max(0, Math.max(0, presets.size() * rowHeight - listHeight));
        presetScrollOffset = Math.max(0, Math.min(presetScrollOffset, maxScroll));
        int startRow = presetScrollOffset / rowHeight;
        int visibleRows = listHeight / rowHeight + 1;
        int endRow = Math.min(presets.size(), startRow + visibleRows);

        for (int i = startRow; i < endRow; i++) {
            ItemEditorDataManager.PresetData preset = presets.get(i);
            int rowY = listY + (i - startRow) * rowHeight;

            int btnY = rowY + 4;
            int btnSize = 12;
            int deleteX = x + panelWidth - btnSize - 8;
            int renameX = deleteX - btnSize - 4;

            if (mouseX >= renameX && mouseX <= renameX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                renamingPreset = preset.name;
                renameBuffer = preset.name == null ? "" : preset.name;
                return true;
            }
            if (mouseX >= deleteX && mouseX <= deleteX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                activeDialog = ConfirmDialog.deletePreset(preset.name == null ? "preset" : preset.name,
                    () -> {
                        ItemEditorDataManager.INSTANCE.deletePreset(preset.name);
                        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", item.getHoverName().getString(), preset.name);
                        DevMod.LOGGER.info("[Editor] Preset deleted: {}", preset.name);
                    },
                    () -> {});
                activeDialog.show();
                return true;
            }

            if (mouseY >= rowY && mouseY <= rowY + rowHeight) {
                Runnable loadAction = () -> {
                    applyPreset(preset);
                    ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_load", item.getHoverName().getString(), preset.name);
                    DevMod.LOGGER.info("[Editor] Preset loaded: {}", preset.name);
                    showStatus("Preset loaded: " + preset.name, UIConstants.Accent.BLUE);
                    closePresetsPanel();
                };

                if (activeModule != null && activeModule.hasUnsavedChanges()) {
                    int changes = activeModule.getPendingChanges().size();
                    activeDialog = new ConfirmDialog(
                        "Overwrite with preset",
                        "Discard " + changes + " pending changes and load '" + preset.name + "'?",
                        "Load",
                        "Cancel",
                        UIConstants.Accent.ORANGE,
                        loadAction,
                        () -> {}
                    );
                    activeDialog.show();
                } else {
                    loadAction.run();
                }
                return true;
            }
        }

        return true;
    }

    private boolean isPointInPresetsPanel(double mouseX, double mouseY) {
        int panelWidth = 288;
        int panelHeight = 224;
        int x = layout.getEditorX() + 8;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + 8;
        return mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY <= y + panelHeight;
    }

    private boolean handleFavoritesClick(double mouseX, double mouseY) {
        ResponsiveLayout.Rect leftPanel = layout.getFavoritesPanelArea();
        if (leftPanel.isEmpty()) return false;
        if (mouseX < leftPanel.x() || mouseX > leftPanel.right() || mouseY < leftPanel.y() || mouseY > leftPanel.bottom()) {
            return false;
        }

        List<ItemEditorDataManager.PresetData> favorites = getFavoritePresetsForActiveType();
        int rowHeight = 18;
        int startY = leftPanel.y() + 20;
        int maxRows = Math.max(0, (leftPanel.height() - 28) / rowHeight);
        for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
            int rowY = startY + i * rowHeight;
            if (mouseY >= rowY && mouseY <= rowY + rowHeight) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                Runnable loadAction = () -> {
                    applyPreset(preset);
                    ItemEditorDataManager.INSTANCE.addHistoryEntry("favorite_load", item.getHoverName().getString(), preset.name);
                    showStatus("Favorite applied: " + preset.name, UIConstants.Accent.BLUE);
                };
                if (activeModule != null && activeModule.hasUnsavedChanges()) {
                    activeDialog = new ConfirmDialog(
                        "Apply favorite",
                        "Overwrite current changes with favorite '" + preset.name + "'?",
                        "Apply",
                        "Cancel",
                        UIConstants.Accent.ORANGE,
                        loadAction,
                        () -> {}
                    );
                    activeDialog.show();
                } else {
                    loadAction.run();
                }
                return true;
            }
        }

        // Pin/unpin last loaded
        if (lastLoadedPreset != null) {
            int btnY = leftPanel.bottom() - 18;
            int btnW = 80;
            int btnX = leftPanel.x() + 8;
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 14) {
                favoriteStore.toggleFavorite(getActiveItemType(), lastLoadedPreset);
                boolean nowFav = favoriteStore.isFavorite(getActiveItemType(), lastLoadedPreset);
                String msg = nowFav ? "Pinned " : "Unpinned ";
                showStatus(msg + lastLoadedPreset, UIConstants.Accent.BLUE);
                return true;
            }
        }

        return true; // consume clicks inside panel
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    private void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusTicks = 60; // 3 seconds at 20 TPS
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && sound != null) {
            mc.player.playSound(Objects.requireNonNull(sound, "sound cannot be null"), 0.5f, 1.0f);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (statusTicks > 0) {
            statusTicks--;
        }

        if (activeModule != null) {
            activeModule.tick();
        }
    }

    @Override
    public void onClose() {
        if (activeModule != null) {
            activeModule.onClose();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public ItemStack getItem() {
        return item;
    }

    public ItemStack getOriginalItem() {
        return originalItem;
    }

    public boolean isPreviewMode() {
        return isPreviewMode;
    }

    public boolean isGlobalMode() {
        return isGlobalMode;
    }

    public EditorModule getActiveModule() {
        return activeModule;
    }
}

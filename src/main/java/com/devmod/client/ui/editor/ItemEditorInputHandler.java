package com.devmod.client.ui.editor;

import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.editor.components.FooterComponent;
import com.devmod.client.ui.editor.components.HeaderComponent;
import com.devmod.client.ui.editor.components.LeftColumnComponent;
import com.devmod.client.ui.editor.components.ScrollableContentArea;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.client.ui.editor.systems.BatchEditResult;
import com.devmod.client.ui.editor.systems.CraftingInfoPanel;
import com.devmod.client.ui.editor.systems.DebugPanel;
import com.devmod.client.ui.editor.systems.HelpOverlay;
import com.devmod.client.ui.editor.systems.MultiEditManager;
import com.devmod.client.ui.editor.systems.MultiEditPanel;
import com.devmod.client.ui.editor.systems.PresetSelectorOverlay;
import com.devmod.client.ui.editor.systems.TemplateOverlay;
public class ItemEditorInputHandler {

    // Hotbar constants
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int HOTBAR_BOTTOM_OFFSET = 22;
    private static final int HISTORY_PANEL_LINE_HEIGHT = 14;

    // State
    private boolean f3Held = false;

    /**
     * Handle mouse click events.
     * @return true if the click was handled
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight,
            HelpOverlay helpOverlay, boolean showLowConfidenceDialog, WeaponTypeDetector.DetectionResult pendingDetection,
            ConfirmDialog activeDialog, EditorModule activeModule, boolean showCraftingPanel,
            CraftingInfoPanel craftingPanel, boolean showTemplatesPanel, TemplateOverlay templateOverlay,
            boolean showDevPanel, DebugPanel debugPanel, boolean showHistoryPanel,
            boolean showPresetsPanel, PresetSelectorOverlay presetSelectorOverlay,
            HeaderComponent header, FooterComponent footer, LeftColumnComponent leftColumn,
            ScrollableContentArea scrollArea, boolean showMultiEditPanel, MultiEditPanel multiEditPanel,
            InputCallbacks callbacks) {

        // Help overlay first
        if (helpOverlay != null && helpOverlay.isVisible()) {
            return helpOverlay.mouseClicked(mouseX, mouseY, screenWidth, screenHeight);
        }

        // Low confidence dialog
        if (showLowConfidenceDialog && pendingDetection != null) {
            return callbacks.handleLowConfidenceClick(mouseX, mouseY, button);
        }

        // Modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.mouseClicked(mouseX, mouseY, screenWidth, screenHeight);
        }

        // Module overlay
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayMouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        }

        // Crafting panel
        if (showCraftingPanel && craftingPanel != null && craftingPanel.isVisible()) {
            if (craftingPanel.mouseClicked(mouseX, mouseY, screenWidth, screenHeight)) {
                if (!craftingPanel.isVisible()) {
                    callbacks.closeOverlay();
                }
                return true;
            }
        }

        // Templates overlay
        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.mouseClicked(mouseX, mouseY, screenWidth, screenHeight)) {
                return true;
            }
        }

        // Debug panel
        if (showDevPanel && debugPanel != null && debugPanel.handleClick(mouseX, mouseY)) {
            callbacks.showStatus("Copied debug log", UIConstants.Accent.BLUE());
            return true;
        }

        // History panel
        if (showHistoryPanel && callbacks.handleHistoryClick(mouseX, mouseY)) {
            return true;
        }

        // Presets overlay
        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.mouseClicked(mouseX, mouseY, screenWidth, screenHeight)) {
                return true;
            }
        }

        // Favorites
        if (callbacks.handleFavoritesClick(mouseX, mouseY)) {
            return true;
        }

        // Header
        if (header.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Hotbar
        if (handleHotbarClick(mouseX, mouseY, button, false, screenWidth, screenHeight)) {
            return true;
        }

        // Footer
        if (footer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Left column
        if (leftColumn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Scroll area
        if (scrollArea.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Module
        if (activeModule != null) {
            return activeModule.mouseClicked(mouseX, mouseY, button);
        }

        // Multi-edit panel
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseClicked(mouseX, mouseY, button)) {
            callbacks.handleMultiEditResult(multiEditPanel.takeLastResult());
            return true;
        }

        return false;
    }

    /**
     * Handle mouse release events.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button, int screenWidth, int screenHeight,
            boolean showLowConfidenceDialog, ScrollableContentArea scrollArea, EditorModule activeModule) {
        if (showLowConfidenceDialog) return false;

        if (handleHotbarClick(mouseX, mouseY, button, true, screenWidth, screenHeight)) {
            return true;
        }

        if (scrollArea.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        if (activeModule != null) {
            return activeModule.mouseReleased(mouseX, mouseY, button);
        }

        return false;
    }

    /**
     * Handle mouse drag events.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            boolean showLowConfidenceDialog, ScrollableContentArea scrollArea, EditorModule activeModule) {
        if (showLowConfidenceDialog) return false;

        if (scrollArea.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }

        if (activeModule != null) {
            return activeModule.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        return false;
    }

    /**
     * Handle mouse scroll events.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
            int screenWidth, int screenHeight,
            boolean showLowConfidenceDialog, WeaponTypeDetector.DetectionResult pendingDetection,
            EditorModule activeModule, boolean showHistoryPanel, ResponsiveLayout layout,
            boolean showTemplatesPanel, TemplateOverlay templateOverlay,
            boolean showCraftingPanel, CraftingInfoPanel craftingPanel,
            boolean showPresetsPanel, PresetSelectorOverlay presetSelectorOverlay,
            HeaderComponent header, FooterComponent footer,
            boolean showMultiEditPanel, MultiEditPanel multiEditPanel,
            ScrollableContentArea scrollArea, InputCallbacks callbacks) {

        if (showLowConfidenceDialog && pendingDetection != null) {
            return true;
        }

        // Module overlay scroll
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayMouseScrolled(mouseX, mouseY, scrollY, screenWidth, screenHeight);
        }

        // History panel scroll
        if (showHistoryPanel) {
            if (callbacks.isPointInHistoryPanel(mouseX, mouseY)) {
                callbacks.adjustHistoryScroll((int) (-scrollY * HISTORY_PANEL_LINE_HEIGHT));
                return true;
            }
        }

        // Templates overlay
        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.mouseScrolled(mouseX, mouseY, scrollY, screenWidth, screenHeight)) {
                return true;
            }
        }

        // Crafting panel
        if (showCraftingPanel && craftingPanel != null && craftingPanel.isVisible()) {
            if (craftingPanel.mouseScrolled(mouseX, mouseY, scrollY, screenWidth, screenHeight)) {
                return true;
            }
        }

        // Presets overlay
        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.mouseScrolled(mouseX, mouseY, scrollY, screenWidth, screenHeight)) {
                return true;
            }
        }

        // Header
        if (header.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        // Footer
        if (footer.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        // Multi-edit panel
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        // Scroll area
        if (scrollArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        // Module
        if (activeModule != null) {
            return activeModule.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return false;
    }

    /**
     * Handle key press events.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers,
            HelpOverlay helpOverlay, EditorModule activeModule,
            boolean showTemplatesPanel, TemplateOverlay templateOverlay,
            boolean showHistoryPanel, boolean showPresetsPanel, PresetSelectorOverlay presetSelectorOverlay,
            ConfirmDialog activeDialog, boolean showLowConfidenceDialog,
            WeaponTypeDetector.DetectionResult pendingDetection,
            HeaderComponent header, FooterComponent footer, LeftColumnComponent leftColumn,
            ScrollableContentArea scrollArea, boolean showDevPanel, DebugPanel debugPanel,
            boolean showMultiEditPanel, MultiEditPanel multiEditPanel, MultiEditManager multiEditManager,
            boolean showCraftingPanel, CraftingInfoPanel craftingPanel,
            boolean isPreviewMode, InputCallbacks callbacks) {

        // Debug overlay shortcuts
        if (DebugOverlay.handleKeyPressed(keyCode, modifiers)) {
            return true;
        }

        // Help overlay
        if (helpOverlay != null && helpOverlay.isVisible()) {
            return helpOverlay.keyPressed(keyCode);
        }

        // Module overlay
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayKeyPressed(keyCode);
        }

        // Templates overlay
        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }

        // M key - MultiEdit refresh
        if (keyCode == GLFW.GLFW_KEY_M) {
            callbacks.refreshMultiEditSelection();
            if (multiEditPanel != null) {
                multiEditPanel.setExpanded(true);
            }
            callbacks.showStatus("MultiEdit refreshed", UIConstants.Accent.BLUE());
            return true;
        }

        // Escape for overlay close
        if (showHistoryPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            callbacks.closeOverlay();
            return true;
        }
        if (showPresetsPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            callbacks.closeOverlay();
            return true;
        }
        if (showTemplatesPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            callbacks.closeOverlay();
            return true;
        }

        // Modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.keyPressed(keyCode);
        }

        // Low-confidence dialog
        if (showLowConfidenceDialog && pendingDetection != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                callbacks.dismissLowConfidenceDialog();
                return true;
            }
            return true; // swallow other keys
        }

        // Presets overlay keys
        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.keyPressed(keyCode)) {
                return true;
            }
        }

        // Escape to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            callbacks.handleCloseRequest();
            return true;
        }

        // F5 toggle preview/apply
        if (keyCode == GLFW.GLFW_KEY_F5) {
            callbacks.togglePreviewMode();
            return true;
        }

        // Ctrl+Enter quick apply
        if (keyCode == GLFW.GLFW_KEY_ENTER && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (isPreviewMode) {
                callbacks.showStatus("Preview mode: cannot apply", UIConstants.Accent.ORANGE());
            } else if (activeModule != null && activeModule.hasUnsavedChanges()) {
                callbacks.applyChanges();
            } else {
                callbacks.showStatus("No changes to apply", UIConstants.Accent.ORANGE());
            }
            return true;
        }

        // Ctrl+Z batch undo
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (multiEditManager != null && multiEditManager.hasSnapshot()) {
                var result = multiEditManager.restoreSnapshot();
                if (result.failureCount() == 0) {
                    callbacks.showStatus("Batch undo: " + result.successCount() + " items restored", UIConstants.Accent.GREEN());
                } else {
                    callbacks.showStatus("Batch undo: " + result.successCount() + " ok, " + result.failureCount() + " failed", UIConstants.Accent.ORANGE());
                }
                return true;
            }
        }

        // Component shortcuts
        if (header.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (footer.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (leftColumn.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (scrollArea.keyPressed(keyCode, scanCode, modifiers)) return true;

        // F3 tracking for dev toggle
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = true;
        }

        // F3+D for dev panel toggle
        if (f3Held && keyCode == GLFW.GLFW_KEY_D) {
            callbacks.toggleDevPanel();
            return true;
        }

        // Debug panel shortcuts
        if (showDevPanel && debugPanel != null) {
            if (keyCode == GLFW.GLFW_KEY_E && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                callbacks.exportDebugRecent(debugPanel);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                debugPanel.clear();
                callbacks.showStatus("Debug log cleared", UIConstants.Accent.BLUE());
                return true;
            }
        }

        // F1 for help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            if (helpOverlay != null) helpOverlay.toggle();
            return true;
        }

        // Crafting panel keys
        if (showCraftingPanel && craftingPanel != null && craftingPanel.isVisible() && craftingPanel.keyPressed(keyCode)) {
            callbacks.closeOverlay();
            return true;
        }

        // Pass to module
        if (activeModule != null) {
            return activeModule.keyPressed(keyCode, scanCode, modifiers);
        }

        return false;
    }

    /**
     * Handle character typed events.
     */
    public boolean charTyped(char chr, int modifiers,
            boolean showLowConfidenceDialog, WeaponTypeDetector.DetectionResult pendingDetection,
            EditorModule activeModule, boolean showTemplatesPanel, TemplateOverlay templateOverlay,
            boolean showPresetsPanel, PresetSelectorOverlay presetSelectorOverlay) {

        if (showLowConfidenceDialog && pendingDetection != null) {
            return true;
        }

        // Module overlay
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayCharTyped(chr, modifiers);
        }

        // Templates overlay
        if (showTemplatesPanel && templateOverlay != null) {
            return templateOverlay.charTyped(chr, modifiers);
        }

        // Presets overlay
        if (showPresetsPanel && presetSelectorOverlay != null) {
            return presetSelectorOverlay.charTyped(chr, modifiers);
        }

        // Module
        if (activeModule != null) {
            return activeModule.charTyped(chr, modifiers);
        }

        return false;
    }

    /**
     * Handle key release events.
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = false;
        }
        return false;
    }

    /**
     * Handle hotbar click interaction.
     */
    private boolean handleHotbarClick(double mouseX, double mouseY, int button, boolean placeOnly,
            int screenWidth, int screenHeight) {
        if (button != 0) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;

        final int hotbarWidth = HOTBAR_WIDTH;
        final int slotSize = HOTBAR_SLOT_SIZE;
        final int startX = (screenWidth - hotbarWidth) / 2;
        final int startY = screenHeight - HOTBAR_BOTTOM_OFFSET;

        for (int i = 0; i < HOTBAR_SLOT_COUNT; i++) {
            int slotX = startX + i * slotSize;
            if (mouseX >= slotX && mouseX < slotX + slotSize && mouseY >= startY && mouseY < startY + slotSize) {
                var inv = player.getInventory();
                ItemStack slotStack = Objects.requireNonNull(inv.getItem(i), "hotbar slot");
                ItemStack carried = Objects.requireNonNull(player.containerMenu.getCarried(), "carried stack");

                if (placeOnly && carried.isEmpty()) {
                    return false;
                }

                if (carried.isEmpty()) {
                    player.containerMenu.setCarried(Objects.requireNonNull(slotStack.copy(), "copy"));
                    inv.setItem(i, Objects.requireNonNull(ItemStack.EMPTY, "empty"));
                } else {
                    ItemStack carriedCopy = Objects.requireNonNull(carried.copy(), "copy");
                    ItemStack slotCopy = Objects.requireNonNull(slotStack.copy(), "copy");
                    inv.setItem(i, carriedCopy);
                    player.containerMenu.setCarried(slotCopy);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Callbacks interface for input handler to communicate with screen.
     */
    public interface InputCallbacks {
        boolean handleLowConfidenceClick(double mouseX, double mouseY, int button);
        void closeOverlay();
        void showStatus(String message, int color);
        boolean handleHistoryClick(double mouseX, double mouseY);
        boolean handleFavoritesClick(double mouseX, double mouseY);
        void handleMultiEditResult(BatchEditResult result);
        boolean isPointInHistoryPanel(double mouseX, double mouseY);
        void adjustHistoryScroll(int delta);
        void refreshMultiEditSelection();
        void dismissLowConfidenceDialog();
        void handleCloseRequest();
        void togglePreviewMode();
        void applyChanges();
        void toggleDevPanel();
        void exportDebugRecent(DebugPanel debugPanel);
    }
}

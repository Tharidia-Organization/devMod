package com.devmod.client.ui.editor.controller;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.editor.EditorModule;
import com.devmod.client.ui.editor.components.FooterComponent;
import com.devmod.client.ui.editor.components.HeaderComponent;
import com.devmod.client.ui.editor.components.LeftColumnComponent;
import com.devmod.client.ui.editor.components.ModeBadge;
import com.devmod.client.ui.editor.components.ScrollableContentArea;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.client.ui.editor.systems.CraftingInfoPanel;
import com.devmod.client.ui.editor.systems.DebugPanel;
import com.devmod.client.ui.editor.systems.HelpOverlay;
import com.devmod.client.ui.editor.systems.LowConfidenceDetector;
import com.devmod.client.ui.editor.systems.MultiEditManager;
import com.devmod.client.ui.editor.systems.MultiEditPanel;
import com.devmod.client.ui.editor.systems.PresetSelectorOverlay;
import com.devmod.client.ui.editor.systems.TemplateOverlay;

@OnlyIn(Dist.CLIENT)
public final class InputRouter {

    // ═══════════════════════════════════════════════════════════════
    // CONTEXT (provided by screen)
    // ═══════════════════════════════════════════════════════════════

    private final InputContext context;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private boolean f3Held = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public InputRouter(InputContext context) {
        this.context = context;
    }

    // ═══════════════════════════════════════════════════════════════
    // MOUSE INPUT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Route a mouse click to the appropriate handler.
     *
     * @return true if the click was consumed
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Priority 1: Modal overlays
        if (context.helpOverlay().isVisible()) {
            return context.helpOverlay().mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight());
        }
        if (context.lowConfidenceDetector().isVisible()) {
            return context.lowConfidenceDetector().mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight());
        }
        ConfirmDialog dialog = context.activeDialog();
        if (dialog != null && dialog.isVisible()) {
            return dialog.mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight());
        }

        // Priority 2: Module overlay
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayMouseClicked(mouseX, mouseY, button, context.screenWidth(), context.screenHeight());
        }

        // Priority 3: Panel overlays
        if (context.overlayController().isCraftingVisible() && context.craftingPanel().isVisible()) {
            if (context.craftingPanel().mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight())) {
                if (!context.craftingPanel().isVisible()) {
                    context.overlayController().closeAll();
                }
                return true;
            }
        }
        TemplateOverlay templateOvl = context.templateOverlay();
        if (context.overlayController().isTemplatesVisible() && templateOvl != null) {
            if (templateOvl.mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        DebugPanel dbgPanel = context.debugPanel();
        if (context.showDevPanel() && dbgPanel != null && dbgPanel.handleClick(mouseX, mouseY)) {
            context.showStatus("Copied debug log", DesignTokens.Semantic.INFO);
            return true;
        }
        if (context.overlayController().isHistoryVisible() && context.handleHistoryClick(mouseX, mouseY)) {
            return true;
        }
        PresetSelectorOverlay presetOvl = context.presetSelectorOverlay();
        if (context.overlayController().isPresetsVisible() && presetOvl != null) {
            if (presetOvl.mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        if (context.handleFavoritesClick(mouseX, mouseY)) {
            return true;
        }

        // Reset tooltip
        context.clearTooltip();

        // Priority 4: Components
        if (context.header().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (context.handleHotbarClick(mouseX, mouseY, button)) {
            return true;
        }
        if (context.footer().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Only process left column clicks if it's visible
        if (context.isLeftColumnVisible() && context.leftColumn().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (context.scrollArea().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Priority 5: Module content
        if (module != null) {
            return module.mouseClicked(mouseX, mouseY, button);
        }

        // Priority 6: Multi-edit panel
        MultiEditPanel mePanel = context.multiEditPanel();
        if (context.showMultiEditPanel() && mePanel != null) {
            if (mePanel.mouseClicked(mouseX, mouseY, button)) {
                handleMultiEditResult();
                return true;
            }
        }

        return false;
    }

    /**
     * Route mouse release.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Block when low-confidence dialog is visible
        if (context.lowConfidenceDetector().isVisible()) {
            return false;
        }

        // Handle hotbar drag-release (place only mode)
        if (context.handleHotbarRelease(mouseX, mouseY, button)) {
            return true;
        }

        // ScrollArea release
        if (context.scrollArea().mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        // Module release
        EditorModule module = context.activeModule();
        if (module != null) {
            return module.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    /**
     * Route mouse drag.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Block when low-confidence dialog is visible
        if (context.lowConfidenceDetector().isVisible()) {
            return false;
        }

        // ScrollArea drag
        if (context.scrollArea().mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }

        // Module drag
        EditorModule module = context.activeModule();
        if (module != null) {
            return module.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    /**
     * Route mouse scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Block scroll when low-confidence dialog is visible
        if (context.lowConfidenceDetector().isVisible()) {
            return true;
        }

        // Module overlay scroll
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayMouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight());
        }

        // Panel overlay scroll
        if (context.overlayController().isHistoryVisible()) {
            if (context.isPointInHistoryPanel(mouseX, mouseY)) {
                context.scrollHistory((int) -scrollY);
                return true;
            }
        }
        TemplateOverlay templateOvlScroll = context.templateOverlay();
        if (context.overlayController().isTemplatesVisible() && templateOvlScroll != null) {
            if (templateOvlScroll.mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        if (context.overlayController().isCraftingVisible() && context.craftingPanel().isVisible()) {
            if (context.craftingPanel().mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        PresetSelectorOverlay presetOvlScroll = context.presetSelectorOverlay();
        if (context.overlayController().isPresetsVisible() && presetOvlScroll != null) {
            if (presetOvlScroll.mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }

        // Component scrolling
        if (context.header().mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (context.footer().mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        // Multi-edit panel scroll
        MultiEditPanel mePanelScroll = context.multiEditPanel();
        if (context.showMultiEditPanel() && mePanelScroll != null && mePanelScroll.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        // Scroll area
        if (context.scrollArea().mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        // Module scroll
        if (module != null) {
            return module.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // KEYBOARD INPUT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Route a key press to the appropriate handler.
     *
     * @return true if the key was consumed
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // PRIORITY 0: Module text input fields (HIGHEST PRIORITY)
        // When a text field is focused, route ALL input to it (bypassing editor keybinds)
        // This prevents keybinds like M, F5, number keys from interfering with text input
        EditorModule moduleForInput = context.activeModule();
        if (moduleForInput != null && moduleForInput.hasFocusedInput()) {
            // All keys go directly to the module's focused text field
            return moduleForInput.keyPressed(keyCode, scanCode, modifiers);
        }

        // Debug overlay shortcuts (global)
        if (DebugOverlay.handleKeyPressed(keyCode, modifiers)) {
            return true;
        }

        // Shift+Escape to force close all overlays
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            if (context.forceCloseAllOverlays()) {
                return true;
            }
        }

        // Priority 1: Modal overlays
        if (context.helpOverlay().isVisible()) {
            return context.helpOverlay().keyPressed(keyCode);
        }

        // Module overlay
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayKeyPressed(keyCode);
        }

        // Template overlay (has text input)
        TemplateOverlay templateOvlKey = context.templateOverlay();
        if (context.overlayController().isTemplatesVisible() && templateOvlKey != null) {
            if (templateOvlKey.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }

        // M key for multi-edit toggle/refresh
        if (keyCode == GLFW.GLFW_KEY_M) {
            MultiEditPanel mePanelKey = context.multiEditPanel();
            if (context.showMultiEditPanel()) {
                context.refreshMultiEditSelection();
                if (mePanelKey != null) {
                    mePanelKey.setExpanded(true);
                }
                context.showStatus("MultiEdit refreshed", DesignTokens.Semantic.INFO);
            } else {
                context.toggleMultiEditPanel();
                if (context.showMultiEditPanel()) {
                    context.refreshMultiEditSelection();
                    // Re-fetch after toggle since panel may have been created
                    MultiEditPanel mePanelAfterToggle = context.multiEditPanel();
                    if (mePanelAfterToggle != null) {
                        mePanelAfterToggle.setExpanded(true);
                    }
                }
            }
            return true;
        }

        // ESC for overlay close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (context.overlayController().hasActiveOverlay()) {
                context.closeOverlay();
                return true;
            }
        }

        // Modal dialog
        ConfirmDialog dialog = context.activeDialog();
        if (dialog != null && dialog.isVisible()) {
            return dialog.keyPressed(keyCode);
        }

        // Low-confidence dialog
        if (context.lowConfidenceDetector().isVisible()) {
            return context.lowConfidenceDetector().keyPressed(keyCode);
        }

        // Preset overlay keyboard
        PresetSelectorOverlay presetOvlKey = context.presetSelectorOverlay();
        if (context.overlayController().isPresetsVisible() && presetOvlKey != null) {
            if (presetOvlKey.keyPressed(keyCode)) {
                return true;
            }
        }

        // ESC to close screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            context.handleCloseRequest();
            return true;
        }

        // F5 toggle mode
        if (keyCode == GLFW.GLFW_KEY_F5) {
            context.handleModeChange(context.isPreviewMode() ? ModeBadge.Mode.APPLY : ModeBadge.Mode.PREVIEW);
            return true;
        }

        // Ctrl+Enter quick apply
        if (keyCode == GLFW.GLFW_KEY_ENTER && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (context.isPreviewMode()) {
                context.showStatus("Preview mode: cannot apply", DesignTokens.Semantic.WARNING);
            } else if (module != null && module.hasUnsavedChanges()) {
                context.applyChanges();
            } else {
                context.showStatus("No changes to apply", DesignTokens.Semantic.WARNING);
            }
            return true;
        }

        // Ctrl+Z batch undo
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            MultiEditManager meManager = context.multiEditManager();
            if (meManager != null && meManager.hasSnapshot()) {
                var result = meManager.restoreSnapshot();
                if (result.failureCount() == 0) {
                    context.showStatus("Batch undo: " + result.successCount() + " items restored", DesignTokens.Semantic.SUCCESS);
                } else {
                    context.showStatus("Batch undo: " + result.successCount() + " ok, " + result.failureCount() + " failed", DesignTokens.Semantic.WARNING);
                }
                return true;
            }
        }

        // Component shortcuts
        if (context.header().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (context.footer().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (context.leftColumn().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (context.scrollArea().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // F3 tracking for dev panel combo
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = true;
        }

        // F3+D dev panel toggle
        if (f3Held && keyCode == GLFW.GLFW_KEY_D) {
            context.toggleDevPanel();
            return true;
        }

        // Dev panel shortcuts
        DebugPanel dbgPanelKey = context.debugPanel();
        if (context.showDevPanel() && dbgPanelKey != null) {
            if (keyCode == GLFW.GLFW_KEY_E && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                try {
                    var path = dbgPanelKey.exportRecentToTempFile(10);
                    dbgPanelKey.log("Exported recent to: " + path.toString());
                    context.showStatus("Exported debug recent", DesignTokens.Semantic.INFO);
                } catch (Exception e) {
                    dbgPanelKey.log("Export failed: " + e.getMessage());
                    context.showStatus("Export failed", DesignTokens.Semantic.WARNING);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                dbgPanelKey.clear();
                context.showStatus("Debug log cleared", DesignTokens.Semantic.INFO);
                return true;
            }
        }

        // F1 for help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            context.helpOverlay().toggle();
            return true;
        }

        // Crafting panel key handling
        if (context.overlayController().isCraftingVisible() && context.craftingPanel().isVisible()) {
            if (context.craftingPanel().keyPressed(keyCode)) {
                context.overlayController().closeAll();
                return true;
            }
        }

        // Module key handling
        if (module != null) {
            return module.keyPressed(keyCode, scanCode, modifiers);
        }

        return false;
    }

    /**
     * Route key release.
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = false;
        }
        return false;
    }

    /**
     * Route character typed.
     */
    public boolean charTyped(char chr, int modifiers) {
        // PRIORITY 0: Module text input fields (HIGHEST PRIORITY)
        // When a text field is focused, all character input goes directly to it
        EditorModule moduleForInput = context.activeModule();
        if (moduleForInput != null && moduleForInput.hasFocusedInput()) {
            return moduleForInput.charTyped(chr, modifiers);
        }

        // Block input when low-confidence dialog is visible
        if (context.lowConfidenceDetector().isVisible()) {
            return context.lowConfidenceDetector().charTyped(chr, modifiers);
        }

        // Module overlay char input
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayCharTyped(chr, modifiers);
        }

        // Template overlay char input
        TemplateOverlay templateOvlChar = context.templateOverlay();
        if (context.overlayController().isTemplatesVisible() && templateOvlChar != null) {
            return templateOvlChar.charTyped(chr, modifiers);
        }

        // Preset overlay char input
        PresetSelectorOverlay presetOvlChar = context.presetSelectorOverlay();
        if (context.overlayController().isPresetsVisible() && presetOvlChar != null) {
            return presetOvlChar.charTyped(chr, modifiers);
        }

        // Module char input
        if (module != null) {
            return module.charTyped(chr, modifiers);
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void handleMultiEditResult() {
        MultiEditPanel panel = context.multiEditPanel();
        if (panel == null) return;

        var result = panel.takeLastResult();
        if (result == null) return;

        int succ = result.successCount();
        int fail = result.failureCount();
        DebugPanel dbgPanelResult = context.debugPanel();

        if (fail == 0) {
            context.showStatus("Applied preset to " + succ + " items", DesignTokens.Semantic.SUCCESS);
            if (dbgPanelResult != null) {
                dbgPanelResult.log("MultiEdit apply: " + succ + " successes");
            }
        } else {
            context.showStatus("Applied: " + succ + " successes, " + fail + " failures", DesignTokens.Semantic.WARNING);
            try {
                var failures = result.failures();
                StringBuilder sb = new StringBuilder();
                int limit = Math.min(5, failures.size());
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(failures.get(i));
                }
                if (failures.size() > limit) {
                    sb.append(" (+").append(failures.size() - limit).append(" more)");
                }
                DevMod.LOGGER.warn("MultiEdit apply failures: {}", failures);
                context.showStatus("Failures: " + sb, DesignTokens.Semantic.ERROR);
                if (dbgPanelResult != null) {
                    String first = failures.isEmpty() ? "" : failures.get(0);
                    dbgPanelResult.log("MultiEdit apply: " + succ + " successes, " + fail + " failures - first: " + first);
                }
            } catch (Exception ignore) {
                // ignore logging failure
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTEXT INTERFACE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Context interface providing access to screen state and components.
     * Implemented by ItemEditorScreen to provide dependencies to InputRouter.
     */
    public interface InputContext {
        // Screen dimensions
        int screenWidth();
        int screenHeight();

        // Components
        HeaderComponent header();
        FooterComponent footer();
        LeftColumnComponent leftColumn();
        ScrollableContentArea scrollArea();

        // Overlays
        HelpOverlay helpOverlay();
        LowConfidenceDetector lowConfidenceDetector();
        @Nullable ConfirmDialog activeDialog();
        @Nullable TemplateOverlay templateOverlay();
        @Nullable PresetSelectorOverlay presetSelectorOverlay();
        CraftingInfoPanel craftingPanel();
        @Nullable DebugPanel debugPanel();

        // Controllers
        OverlayController overlayController();

        // Module
        @Nullable EditorModule activeModule();

        // Multi-edit
        @Nullable MultiEditManager multiEditManager();
        @Nullable MultiEditPanel multiEditPanel();
        boolean showMultiEditPanel();
        void toggleMultiEditPanel();
        void refreshMultiEditSelection();

        // State queries
        boolean isPreviewMode();
        boolean showDevPanel();
        void toggleDevPanel();
        /** Returns true if the left column is visible in the current layout. */
        boolean isLeftColumnVisible();

        // Actions
        void showStatus(String message, int color);
        void clearTooltip();
        void closeOverlay();
        boolean forceCloseAllOverlays();
        void handleCloseRequest();
        void handleModeChange(ModeBadge.Mode mode);
        void applyChanges();
        void scrollHistory(int delta);

        // Special click handlers
        boolean handleHistoryClick(double mouseX, double mouseY);
        boolean handleFavoritesClick(double mouseX, double mouseY);
        boolean handleHotbarClick(double mouseX, double mouseY, int button);
        boolean handleHotbarRelease(double mouseX, double mouseY, int button);
        boolean isPointInHistoryPanel(double mouseX, double mouseY);
    }
}

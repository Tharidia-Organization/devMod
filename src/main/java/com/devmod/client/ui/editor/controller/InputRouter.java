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
import com.devmod.client.ui.editor.core.UIConstants;
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
        if (context.overlayController().isTemplatesVisible() && context.templateOverlay() != null) {
            if (context.templateOverlay().mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        if (context.showDevPanel() && context.debugPanel() != null && context.debugPanel().handleClick(mouseX, mouseY)) {
            context.showStatus("Copied debug log", UIConstants.Accent.BLUE());
            return true;
        }
        if (context.overlayController().isHistoryVisible() && context.handleHistoryClick(mouseX, mouseY)) {
            return true;
        }
        if (context.overlayController().isPresetsVisible() && context.presetSelectorOverlay() != null) {
            if (context.presetSelectorOverlay().mouseClicked(mouseX, mouseY, context.screenWidth(), context.screenHeight())) {
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
        if (context.leftColumn().mouseClicked(mouseX, mouseY, button)) {
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
        if (context.showMultiEditPanel() && context.multiEditPanel() != null) {
            if (context.multiEditPanel().mouseClicked(mouseX, mouseY, button)) {
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
        // Module overlay scroll
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayMouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight());
        }

        // Panel overlay scroll
        if (context.overlayController().isHistoryVisible()) {
            context.scrollHistory((int) -scrollY);
            return true;
        }
        if (context.overlayController().isTemplatesVisible() && context.templateOverlay() != null) {
            if (context.templateOverlay().mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        if (context.overlayController().isCraftingVisible() && context.craftingPanel().isVisible()) {
            if (context.craftingPanel().mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
        }
        if (context.overlayController().isPresetsVisible() && context.presetSelectorOverlay() != null) {
            if (context.presetSelectorOverlay().mouseScrolled(mouseX, mouseY, scrollY, context.screenWidth(), context.screenHeight())) {
                return true;
            }
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
        // Debug overlay shortcuts (global)
        if (DebugOverlay.handleKeyPressed(keyCode, modifiers)) {
            return true;
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
        if (context.overlayController().isTemplatesVisible() && context.templateOverlay() != null) {
            if (context.templateOverlay().keyPressed(keyCode, modifiers)) {
                return true;
            }
        }

        // M key for multi-edit refresh
        if (keyCode == GLFW.GLFW_KEY_M) {
            context.refreshMultiEditSelection();
            if (context.multiEditPanel() != null) {
                context.multiEditPanel().setExpanded(true);
            }
            context.showStatus("MultiEdit refreshed", UIConstants.Accent.BLUE());
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
        if (context.overlayController().isPresetsVisible() && context.presetSelectorOverlay() != null) {
            if (context.presetSelectorOverlay().keyPressed(keyCode)) {
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
                context.showStatus("Preview mode: cannot apply", UIConstants.Accent.ORANGE());
            } else if (module != null && module.hasUnsavedChanges()) {
                context.applyChanges();
            } else {
                context.showStatus("No changes to apply", UIConstants.Accent.ORANGE());
            }
            return true;
        }

        // Ctrl+Z batch undo
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (context.multiEditManager() != null && context.multiEditManager().hasSnapshot()) {
                var result = context.multiEditManager().restoreSnapshot();
                if (result.failureCount() == 0) {
                    context.showStatus("Batch undo: " + result.successCount() + " items restored", UIConstants.Accent.GREEN());
                } else {
                    context.showStatus("Batch undo: " + result.successCount() + " ok, " + result.failureCount() + " failed", UIConstants.Accent.ORANGE());
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
        if (context.showDevPanel() && context.debugPanel() != null) {
            if (keyCode == GLFW.GLFW_KEY_E && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                try {
                    var path = context.debugPanel().exportRecentToTempFile(10);
                    context.debugPanel().log("Exported recent to: " + path.toString());
                    context.showStatus("Exported debug recent", UIConstants.Accent.BLUE());
                } catch (Exception e) {
                    context.debugPanel().log("Export failed: " + e.getMessage());
                    context.showStatus("Export failed", UIConstants.Accent.ORANGE());
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                context.debugPanel().clear();
                context.showStatus("Debug log cleared", UIConstants.Accent.BLUE());
                return true;
            }
        }

        // M toggle multi-edit panel
        if (keyCode == GLFW.GLFW_KEY_M) {
            context.toggleMultiEditPanel();
            if (context.showMultiEditPanel()) {
                context.refreshMultiEditSelection();
                if (context.multiEditPanel() != null) {
                    context.multiEditPanel().setExpanded(true);
                }
            }
            return true;
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
        // Module overlay char input
        EditorModule module = context.activeModule();
        if (module != null && module.hasActiveOverlay()) {
            return module.overlayCharTyped(chr, modifiers);
        }

        // Template overlay char input
        if (context.overlayController().isTemplatesVisible() && context.templateOverlay() != null) {
            return context.templateOverlay().charTyped(chr, modifiers);
        }

        // Preset overlay char input
        if (context.overlayController().isPresetsVisible() && context.presetSelectorOverlay() != null) {
            return context.presetSelectorOverlay().charTyped(chr, modifiers);
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

        if (fail == 0) {
            context.showStatus("Applied preset to " + succ + " items", UIConstants.Accent.GREEN());
            if (context.debugPanel() != null) {
                context.debugPanel().log("MultiEdit apply: " + succ + " successes");
            }
        } else {
            context.showStatus("Applied: " + succ + " successes, " + fail + " failures", UIConstants.Accent.ORANGE());
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
                context.showStatus("Failures: " + sb, UIConstants.Accent.RED());
                if (context.debugPanel() != null) {
                    String first = failures.isEmpty() ? "" : failures.get(0);
                    context.debugPanel().log("MultiEdit apply: " + succ + " successes, " + fail + " failures - first: " + first);
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

        // Actions
        void showStatus(String message, int color);
        void clearTooltip();
        void closeOverlay();
        void handleCloseRequest();
        void handleModeChange(ModeBadge.Mode mode);
        void applyChanges();
        void scrollHistory(int delta);

        // Special click handlers
        boolean handleHistoryClick(double mouseX, double mouseY);
        boolean handleFavoritesClick(double mouseX, double mouseY);
        boolean handleHotbarClick(double mouseX, double mouseY, int button);
    }
}

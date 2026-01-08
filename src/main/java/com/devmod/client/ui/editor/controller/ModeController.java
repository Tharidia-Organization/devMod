package com.devmod.client.ui.editor.controller;

import java.util.function.BiConsumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.EditorModule;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.state.ItemEditorState;
import com.devmod.config.EditorClientConfig;

@OnlyIn(Dist.CLIENT)
public final class ModeController {

    private final ItemEditorState state;

    /** Callback for status messages: (message, color) */
    private BiConsumer<String, Integer> statusCallback = (msg, color) -> {};

    /** Callback when mode changes - for UI sync */
    private Runnable onModeChanged = () -> {};

    /**
     * Create a new ModeController with the given state.
     *
     * @param state The centralized editor state
     */
    public ModeController(ItemEditorState state) {
        this.state = state;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the status message callback.
     */
    public ModeController setStatusCallback(BiConsumer<String, Integer> callback) {
        this.statusCallback = callback != null ? callback : (msg, color) -> {};
        return this;
    }

    /**
     * Set the callback invoked when mode changes.
     */
    public ModeController setOnModeChanged(Runnable callback) {
        this.onModeChanged = callback != null ? callback : () -> {};
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE SWITCHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Switch to preview mode.
     *
     * @param discardChanges If true, discards unsaved changes
     */
    public void switchToPreviewMode(boolean discardChanges) {
        state.setPreviewMode(true);

        EditorModule module = state.getActiveModule();
        if (module != null) {
            if (discardChanges) {
                module.resetToOriginal();
                module.clearDirty();
            }
            module.applyPreview();
            module.logEvent(discardChanges
                ? "Switched to PREVIEW (discarded changes)"
                : "Switched to PREVIEW");
        }

        showStatus("Preview Mode", DesignTokens.Accent.PRIMARY);
        onModeChanged.run();
    }

    /**
     * Switch to apply mode.
     */
    public void switchToApplyMode() {
        state.setPreviewMode(false);

        EditorModule module = state.getActiveModule();
        if (module != null) {
            module.clearPreview();
            if (!module.hasUnsavedChanges() && module.hasPendingDiff()) {
                module.markDirty("Pending changes from preview");
            }
            module.logEvent("Switched to APPLY (dirty on)");
        }

        showStatus("Apply Mode", DesignTokens.Semantic.SUCCESS);
        onModeChanged.run();
    }

    /**
     * Toggle between preview and apply mode.
     */
    public void toggleMode() {
        if (state.isPreviewMode()) {
            switchToApplyMode();
        } else {
            switchToPreviewMode(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCOPE (GLOBAL/SPECIFIC)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set global mode.
     *
     * @param global true for global (all items of type), false for specific
     */
    public void setGlobalMode(boolean global) {
        state.setGlobalMode(false);
        onModeChanged.run();
    }

    /**
     * Toggle between global and specific mode.
     */
    public void toggleGlobalMode() {
        state.toggleGlobalMode();
        onModeChanged.run();
    }

    // ═══════════════════════════════════════════════════════════════
    // PREFERENCE PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Save current mode preference to config.
     */
    public void savePreference() {
        try {
            EditorClientConfig.EDITOR_DEFAULT_MODE.set(
                state.isPreviewMode()
                    ? EditorClientConfig.EditorDefaultMode.PREVIEW
                    : EditorClientConfig.EditorDefaultMode.APPLY
            );
            showStatus("Default mode saved", DesignTokens.Semantic.INFO);
        } catch (Exception ignored) {
            // Best-effort: config may be read-only in some contexts
        }
    }

    /**
     * Load mode preference from config and apply to state.
     */
    public void loadPreference() {
        try {
            EditorClientConfig.EditorDefaultMode pref = EditorClientConfig.EDITOR_DEFAULT_MODE.get();
            state.setPreviewMode(pref != EditorClientConfig.EditorDefaultMode.APPLY);
        } catch (Exception ignored) {
            state.setPreviewMode(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if currently in preview mode.
     */
    public boolean isPreviewMode() {
        return state.isPreviewMode();
    }

    /**
     * Check if currently in global mode.
     */
    public boolean isGlobalMode() {
        return state.isGlobalMode();
    }

    /**
     * Check if there are unsaved changes that would be lost on mode switch.
     */
    public boolean hasUnsavedChanges() {
        EditorModule module = state.getActiveModule();
        return module != null && module.hasUnsavedChanges();
    }

    /**
     * Get a descriptive string of the current mode.
     */
    public String getModeDescription() {
        return state.getModeDescription();
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void showStatus(String message, int color) {
        statusCallback.accept(message, color);
    }
}

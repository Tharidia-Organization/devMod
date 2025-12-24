package com.devmod.ui.editor.controller;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/**
 * Controller for managing overlay visibility state in ItemEditorScreen.
 * Client-only.
 * Implements a simple state machine for overlay switching with support for
 * returning to the previous overlay when one is closed.
 *
 * <p>This controller is decoupled from the actual overlay implementations -
 * it only manages the state and notifies listeners when state changes.
 */
@OnlyIn(Dist.CLIENT)
public final class OverlayController {

    /**
     * Enumeration of overlay types.
     */
    public enum OverlayType {
        NONE,
        HISTORY,
        PRESETS,
        TEMPLATES,
        CRAFTING
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private OverlayType activeOverlay = OverlayType.NONE;
    private OverlayType previousOverlay = OverlayType.NONE;

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    /** Called when overlay state changes - receives the new active overlay */
    private Consumer<OverlayType> onOverlayChanged = type -> {};

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the callback invoked when the active overlay changes.
     *
     * @param callback The callback (receives new overlay type)
     * @return this for chaining
     */
    public OverlayController setOnOverlayChanged(Consumer<OverlayType> callback) {
        this.onOverlayChanged = callback != null ? callback : type -> {};
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // OVERLAY SWITCHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Toggle the specified overlay.
     * If the overlay is already active, it will be closed (returning to previous).
     * If another overlay is active, switch to the new one (saving current as previous).
     *
     * @param type The overlay type to toggle
     */
    public void toggle(OverlayType type) {
        if (type == null) {
            type = OverlayType.NONE;
        }

        if (activeOverlay == type) {
            // Close current overlay, return to previous
            close();
            return;
        }

        // Save current as previous (unless switching to NONE)
        previousOverlay = (type == OverlayType.NONE) ? OverlayType.NONE : activeOverlay;
        activeOverlay = type;
        notifyChange();
    }

    /**
     * Close the current overlay, returning to the previous overlay.
     */
    public void close() {
        activeOverlay = previousOverlay;
        previousOverlay = OverlayType.NONE;
        notifyChange();
    }

    /**
     * Close all overlays (reset to NONE).
     */
    public void closeAll() {
        activeOverlay = OverlayType.NONE;
        previousOverlay = OverlayType.NONE;
        notifyChange();
    }

    /**
     * Open a specific overlay (without toggle behavior).
     *
     * @param type The overlay to open
     */
    public void open(OverlayType type) {
        if (type == null || type == OverlayType.NONE) {
            closeAll();
            return;
        }
        if (activeOverlay != type) {
            previousOverlay = activeOverlay;
            activeOverlay = type;
            notifyChange();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the currently active overlay.
     */
    public OverlayType getActive() {
        return activeOverlay;
    }

    /**
     * Get the previous overlay (for returning to after close).
     */
    public OverlayType getPrevious() {
        return previousOverlay;
    }

    /**
     * Check if a specific overlay is active.
     *
     * @param type The overlay type to check
     * @return true if the specified overlay is active
     */
    public boolean isActive(OverlayType type) {
        return activeOverlay == type;
    }

    /**
     * Check if any overlay is currently shown.
     */
    public boolean hasActiveOverlay() {
        return activeOverlay != OverlayType.NONE;
    }

    /**
     * Check if history panel should be shown.
     */
    public boolean isHistoryVisible() {
        return activeOverlay == OverlayType.HISTORY;
    }

    /**
     * Check if presets panel should be shown.
     */
    public boolean isPresetsVisible() {
        return activeOverlay == OverlayType.PRESETS;
    }

    /**
     * Check if templates panel should be shown.
     */
    public boolean isTemplatesVisible() {
        return activeOverlay == OverlayType.TEMPLATES;
    }

    /**
     * Check if crafting panel should be shown.
     */
    public boolean isCraftingVisible() {
        return activeOverlay == OverlayType.CRAFTING;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void notifyChange() {
        onOverlayChanged.accept(activeOverlay);
    }
}

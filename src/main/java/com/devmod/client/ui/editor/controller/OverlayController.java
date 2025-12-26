package com.devmod.client.ui.editor.controller;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Controller for managing overlay visibility state in ItemEditorScreen.
 * Client-only.
 * Implements a simple overlay stack for switching with support for returning
 * to previously opened overlays when the top is closed.
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

    private final Deque<OverlayType> overlayStack = new ArrayDeque<>();

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
        OverlayType normalized = type == null ? OverlayType.NONE : type;
        OverlayType active = getActive();

        if (normalized == OverlayType.NONE) {
            closeAll();
            return;
        }

        if (active == normalized) {
            close();
            return;
        }

        removeAll(normalized);
        overlayStack.addLast(normalized);
        notifyChange();
    }

    /**
     * Close the current overlay, returning to the previous overlay.
     */
    public void close() {
        if (!overlayStack.isEmpty()) {
            overlayStack.removeLast();
        }
        notifyChange();
    }

    /**
     * Close all overlays (reset to NONE).
     */
    public void closeAll() {
        overlayStack.clear();
        notifyChange();
    }

    /**
     * Open a specific overlay (without toggle behavior).
     *
     * @param type The overlay to open
     */
    public void open(OverlayType type) {
        OverlayType normalized = type == null ? OverlayType.NONE : type;
        if (normalized == OverlayType.NONE) {
            closeAll();
            return;
        }
        if (getActive() == normalized) {
            return;
        }
        removeAll(normalized);
        overlayStack.addLast(normalized);
        notifyChange();
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the currently active overlay.
     */
    public OverlayType getActive() {
        OverlayType active = overlayStack.peekLast();
        return active == null ? OverlayType.NONE : active;
    }

    /**
     * Get the previous overlay (for returning to after close).
     */
    public OverlayType getPrevious() {
        if (overlayStack.size() < 2) {
            return OverlayType.NONE;
        }
        Iterator<OverlayType> it = overlayStack.descendingIterator();
        it.next();
        return it.hasNext() ? it.next() : OverlayType.NONE;
    }

    /**
     * Check if a specific overlay is active.
     *
     * @param type The overlay type to check
     * @return true if the specified overlay is active
     */
    public boolean isActive(OverlayType type) {
        return getActive() == type;
    }

    /**
     * Check if any overlay is currently shown.
     */
    public boolean hasActiveOverlay() {
        return !overlayStack.isEmpty();
    }

    /**
     * Check if history panel should be shown.
     */
    public boolean isHistoryVisible() {
        return getActive() == OverlayType.HISTORY;
    }

    /**
     * Check if presets panel should be shown.
     */
    public boolean isPresetsVisible() {
        return getActive() == OverlayType.PRESETS;
    }

    /**
     * Check if templates panel should be shown.
     */
    public boolean isTemplatesVisible() {
        return getActive() == OverlayType.TEMPLATES;
    }

    /**
     * Check if crafting panel should be shown.
     */
    public boolean isCraftingVisible() {
        return getActive() == OverlayType.CRAFTING;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void notifyChange() {
        onOverlayChanged.accept(getActive());
    }

    private void removeAll(OverlayType type) {
        overlayStack.removeIf(entry -> entry == type);
    }
}

package com.frenkvs.devmod.ui.editor.debug;

import com.frenkvs.devmod.ui.editor.core.Bounds;

import java.util.List;

/**
 * Interface for components that report debug info.
 * Implement this interface to have your component's bounds and warnings
 * shown in the debug overlay.
 *
 * @see DebugOverlay
 * @see DebugWarning
 * @see docs/editor-design-system/14-debug-overlay.md
 */
public interface DebugReporter {

    /**
     * Get the component name for debug display.
     *
     * @return A short, descriptive name for this component
     */
    String getDebugName();

    /**
     * Get the bounding box for debug overlay.
     *
     * @return The bounds of this component, or {@link Bounds#EMPTY} if not applicable
     */
    Bounds getDebugBounds();

    /**
     * Report any warnings (overflow, truncation, misalignment, etc).
     * Default implementation returns an empty list.
     *
     * @return List of debug warnings, or empty list if none
     */
    default List<DebugWarning> getDebugWarnings() {
        return List.of();
    }

    /**
     * Check if this component should be highlighted when hovered in debug mode.
     * Default implementation returns true.
     *
     * @return true if this component should show hover highlighting
     */
    default boolean isDebugHighlightable() {
        return true;
    }

    /**
     * Get additional debug info to display when this component is hovered.
     * Default implementation returns null (no additional info).
     *
     * @return Additional debug text, or null for none
     */
    default String getDebugDetails() {
        return null;
    }
}

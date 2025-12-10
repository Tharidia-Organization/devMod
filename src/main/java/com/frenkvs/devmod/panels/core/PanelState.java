package com.frenkvs.devmod.panels.core;

/**
 * Lifecycle states of a FloatingPanel.
 *
 * State Machine:
 * SPAWNING -> VISIBLE -> MINIMIZED (optional) -> DESPAWNING -> REMOVED
 *                ^____________|
 */
public enum PanelState {
    /** Panel spawning, fade-in and scale-up phase */
    SPAWNING(200, true),

    /** Panel fully visible and interactive */
    VISIBLE(0, true),

    /** Panel minimized (only title visible) */
    MINIMIZED(0, true),

    /** Panel removal phase, fade-out */
    DESPAWNING(300, false),

    /** Panel removed, ready for garbage collection */
    REMOVED(0, false);

    private final long transitionDurationMs;
    private final boolean interactive;

    PanelState(long transitionDurationMs, boolean interactive) {
        this.transitionDurationMs = transitionDurationMs;
        this.interactive = interactive;
    }

    /**
     * Transition duration in this state (ms).
     */
    public long getTransitionDuration() {
        return transitionDurationMs;
    }

    /**
     * Whether the panel accepts interactions in this state.
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Whether the panel should be rendered.
     */
    public boolean shouldRender() {
        return this != REMOVED;
    }

    /**
     * Whether the panel is in an animated transition phase.
     */
    public boolean isTransitioning() {
        return this == SPAWNING || this == DESPAWNING;
    }

    /**
     * Gets the next state in the transition.
     */
    public PanelState getNextState() {
        return switch (this) {
            case SPAWNING -> VISIBLE;
            case DESPAWNING -> REMOVED;
            default -> this;
        };
    }
}

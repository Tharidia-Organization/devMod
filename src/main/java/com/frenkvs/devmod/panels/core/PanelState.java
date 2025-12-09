package com.frenkvs.devmod.panels.core;

/**
 * Stati del lifecycle di un FloatingPanel.
 *
 * State Machine:
 * SPAWNING -> VISIBLE -> MINIMIZED (opzionale) -> DESPAWNING -> REMOVED
 *                ^____________|
 */
public enum PanelState {
    /** Pannello in fase di spawn, fade-in e scale-up */
    SPAWNING(200, true),

    /** Pannello completamente visibile e interattivo */
    VISIBLE(0, true),

    /** Pannello minimizzato (solo titolo visibile) */
    MINIMIZED(0, true),

    /** Pannello in fase di rimozione, fade-out */
    DESPAWNING(300, false),

    /** Pannello rimosso, pronto per garbage collection */
    REMOVED(0, false);

    private final long transitionDurationMs;
    private final boolean interactive;

    PanelState(long transitionDurationMs, boolean interactive) {
        this.transitionDurationMs = transitionDurationMs;
        this.interactive = interactive;
    }

    /**
     * Durata della transizione in questo stato (ms).
     */
    public long getTransitionDuration() {
        return transitionDurationMs;
    }

    /**
     * Se il pannello accetta interazioni in questo stato.
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Se il pannello dovrebbe essere renderizzato.
     */
    public boolean shouldRender() {
        return this != REMOVED;
    }

    /**
     * Se il pannello e' in una fase di transizione animata.
     */
    public boolean isTransitioning() {
        return this == SPAWNING || this == DESPAWNING;
    }

    /**
     * Ottiene lo stato successivo nella transizione.
     */
    public PanelState getNextState() {
        return switch (this) {
            case SPAWNING -> VISIBLE;
            case DESPAWNING -> REMOVED;
            default -> this;
        };
    }
}

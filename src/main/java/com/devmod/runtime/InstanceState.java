package com.devmod.runtime;

import java.util.Set;

/**
 * State of an instance dimension lifecycle.
 *
 * Valid transitions:
 * - CREATING -> READY (success) or DESTROYING (failure)
 * - READY -> ACTIVE (player entered) or DESTROYING (cancelled)
 * - ACTIVE -> COMPLETING (quest ended)
 * - COMPLETING -> DESTROYING (cleanup done)
 * - DESTROYING -> DESTROYED (final cleanup)
 * - DESTROYED is terminal (no transitions out)
 */
public enum InstanceState {
    /**
     * Dimension is being created and arena is being generated.
     */
    CREATING,

    /**
     * Arena is ready, waiting for players to teleport in.
     */
    READY,

    /**
     * Quest is active, players are fighting.
     */
    ACTIVE,

    /**
     * Quest has ended, cleanup in progress.
     */
    COMPLETING,

    /**
     * Instance is being destroyed (unloading chunks, deleting files).
     */
    DESTROYING,

    /**
     * Instance has been fully cleaned up and removed.
     * This is a terminal state.
     */
    DESTROYED;

    /**
     * Check if transitioning to the given state is valid.
     *
     * @param to The target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(InstanceState to) {
        return switch (this) {
            case CREATING -> to == READY || to == DESTROYING;
            case READY -> to == ACTIVE || to == DESTROYING;
            case ACTIVE -> to == COMPLETING;
            case COMPLETING -> to == DESTROYING;
            case DESTROYING -> to == DESTROYED;
            case DESTROYED -> false; // Terminal state
        };
    }

    /**
     * Get the set of valid next states from this state.
     */
    public Set<InstanceState> getValidNextStates() {
        return switch (this) {
            case CREATING -> Set.of(READY, DESTROYING);
            case READY -> Set.of(ACTIVE, DESTROYING);
            case ACTIVE -> Set.of(COMPLETING);
            case COMPLETING -> Set.of(DESTROYING);
            case DESTROYING -> Set.of(DESTROYED);
            case DESTROYED -> Set.of();
        };
    }

    /**
     * Check if this is a terminal state.
     */
    public boolean isTerminal() {
        return this == DESTROYED;
    }

    /**
     * Check if the instance is still "alive" (not destroyed or destroying).
     */
    public boolean isAlive() {
        return this != DESTROYING && this != DESTROYED;
    }
}

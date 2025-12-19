package com.frenkvs.devmod.instance;

import java.util.Set;

/**
 * State of a player relative to the instance system.
 * Used for tracking and recovery.
 *
 * Valid transitions (forward flow):
 * - NORMAL -> PREPARING (quest accepted, snapshot saved)
 * - PREPARING -> IN_TRANSIT (teleport started)
 * - IN_TRANSIT -> IN_INSTANCE (teleport complete)
 * - IN_INSTANCE -> RETURNING (quest ended)
 * - RETURNING -> NORMAL (recovery complete)
 *
 * Recovery transitions (always valid to NORMAL):
 * - Any state can transition to NORMAL during recovery
 */
public enum PlayerInstanceState {
    /**
     * Player is in the normal world, not involved with any instance.
     */
    NORMAL,

    /**
     * Player snapshot has been saved, preparing for teleport.
     * Recovery: Restore to original position immediately.
     */
    PREPARING,

    /**
     * Player is being teleported to the instance dimension.
     * Recovery: Restore to original position (teleport may have failed).
     */
    IN_TRANSIT,

    /**
     * Player is inside the instance, quest is active.
     * Recovery: Quest failed, restore to original position.
     */
    IN_INSTANCE,

    /**
     * Player is being teleported back to original position.
     * Recovery: Force teleport to original position.
     */
    RETURNING;

    /**
     * Check if transitioning to the given state is valid.
     * Note: NORMAL is always a valid target (recovery path).
     *
     * @param to The target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(PlayerInstanceState to) {
        // Recovery: any state can transition to NORMAL
        if (to == NORMAL) {
            return true;
        }

        return switch (this) {
            case NORMAL -> to == PREPARING;
            case PREPARING -> to == IN_TRANSIT;
            case IN_TRANSIT -> to == IN_INSTANCE;
            case IN_INSTANCE -> to == RETURNING;
            case RETURNING -> false; // Can only go to NORMAL from here
        };
    }

    /**
     * Get the set of valid next states from this state.
     */
    public Set<PlayerInstanceState> getValidNextStates() {
        return switch (this) {
            case NORMAL -> Set.of(PREPARING);
            case PREPARING -> Set.of(IN_TRANSIT, NORMAL);
            case IN_TRANSIT -> Set.of(IN_INSTANCE, NORMAL);
            case IN_INSTANCE -> Set.of(RETURNING, NORMAL);
            case RETURNING -> Set.of(NORMAL);
        };
    }

    /**
     * Check if this state requires a snapshot for recovery.
     */
    public boolean requiresSnapshot() {
        return this != NORMAL;
    }

    /**
     * Check if player is actively in an instance flow.
     */
    public boolean isInInstanceFlow() {
        return this == IN_TRANSIT || this == IN_INSTANCE || this == RETURNING;
    }
}

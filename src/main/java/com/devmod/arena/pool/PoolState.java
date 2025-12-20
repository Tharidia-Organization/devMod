package com.devmod.arena.pool;

/**
 * State machine for pooled arena lifecycle (DD64).
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>BUILDING → READY (build completed)</li>
 *   <li>READY → RESERVED (arena claimed for use)</li>
 *   <li>RESERVED → IN_USE (player joined)</li>
 *   <li>IN_USE → CLEANUP (match ended)</li>
 *   <li>CLEANUP → READY (cleanup completed, returned to pool)</li>
 *   <li>READY → CLEANUP (unused timeout, eviction)</li>
 *   <li>BUILDING → CLEANUP (build failed)</li>
 * </ul>
 */
public enum PoolState {
    /**
     * Arena is being constructed.
     */
    BUILDING,

    /**
     * Arena is ready for use and available in the pool.
     */
    READY,

    /**
     * Arena has been reserved for a pending match.
     */
    RESERVED,

    /**
     * Arena is actively in use by players.
     */
    IN_USE,

    /**
     * Arena is being cleaned up before returning to pool or disposal.
     */
    CLEANUP;

    /**
     * Checks if this state can transition to the target state.
     */
    public boolean canTransitionTo(PoolState target) {
        return switch (this) {
            case BUILDING -> target == READY || target == CLEANUP;
            case READY -> target == RESERVED || target == CLEANUP;
            case RESERVED -> target == IN_USE || target == CLEANUP;
            case IN_USE -> target == CLEANUP;
            case CLEANUP -> target == READY;
        };
    }

    /**
     * Returns true if this state allows eviction from pool.
     */
    public boolean canBeEvicted() {
        return this == READY || this == BUILDING;
    }

    /**
     * Returns true if this state is considered "active" (not available).
     */
    public boolean isActive() {
        return this == RESERVED || this == IN_USE;
    }

    /**
     * Returns true if arena is available for use.
     */
    public boolean isAvailable() {
        return this == READY;
    }
}

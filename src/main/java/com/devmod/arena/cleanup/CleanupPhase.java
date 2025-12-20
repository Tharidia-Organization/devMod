package com.devmod.arena.cleanup;

/**
 * Enum representing the 4 phases of arena cleanup.
 * DD37: Cleanup order is critical - must follow this sequence.
 */
public enum CleanupPhase {
    /**
     * Phase 1: Remove all entities (mobs, items, projectiles, etc.)
     * Must be done first as entities may reference block entities.
     */
    ENTITIES(1, "Entities"),

    /**
     * Phase 2: Remove block entities (chests, furnaces, signs, etc.)
     * Clear container contents before removal to prevent item drops.
     */
    BLOCK_ENTITIES(2, "Block Entities"),

    /**
     * Phase 3: Cancel scheduled ticks (redstone, liquid flow, etc.)
     * Prevents delayed block updates after cleanup.
     */
    SCHEDULED_TICKS(3, "Scheduled Ticks"),

    /**
     * Phase 4: Remove blocks (set to air)
     * Done last after all dependencies are cleared.
     */
    BLOCKS(4, "Blocks");

    private final int order;
    private final String displayName;

    CleanupPhase(int order, String displayName) {
        this.order = order;
        this.displayName = displayName;
    }

    public int getOrder() {
        return order;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the next phase in sequence, or null if this is the last phase.
     */
    public CleanupPhase next() {
        CleanupPhase[] phases = values();
        int nextOrdinal = this.ordinal() + 1;
        return nextOrdinal < phases.length ? phases[nextOrdinal] : null;
    }

    /**
     * Returns true if this phase should be executed before the given phase.
     */
    public boolean isBefore(CleanupPhase other) {
        return this.order < other.order;
    }
}

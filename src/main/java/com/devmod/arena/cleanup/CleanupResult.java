package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of an arena cleanup operation.
 * Contains counters for each phase and any warnings encountered.
 *
 * DD37: Cleanup Robusto - Track results from 4 phases:
 * 1. Entities removed
 * 2. Block entities removed
 * 3. Scheduled ticks cancelled
 * 4. Blocks removed
 */
public record CleanupResult(
    int entitiesRemoved,
    int blockEntitiesRemoved,
    int scheduledTicksCancelled,
    int blocksRemoved,
    long durationMs,
    List<String> warnings
) {

    public CleanupResult {
        // Defensive copy for immutability
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Creates a builder for constructing CleanupResult incrementally.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns true if cleanup completed without warnings.
     */
    public boolean isComplete() {
        return warnings.isEmpty();
    }

    /**
     * Returns true if any cleanup operations were performed.
     */
    public boolean hasChanges() {
        return entitiesRemoved > 0 || blockEntitiesRemoved > 0
            || scheduledTicksCancelled > 0 || blocksRemoved > 0;
    }

    /**
     * Returns the total number of items cleaned up across all phases.
     */
    public int totalCleaned() {
        return entitiesRemoved + blockEntitiesRemoved + scheduledTicksCancelled + blocksRemoved;
    }

    /**
     * Creates an empty result indicating no cleanup was needed.
     */
    public static CleanupResult empty() {
        return new CleanupResult(0, 0, 0, 0, 0L, List.of());
    }

    /**
     * Creates a failed result with a single error message.
     */
    public static CleanupResult failed(String error) {
        return new CleanupResult(0, 0, 0, 0, 0L, List.of(error));
    }

    /**
     * Builder for constructing CleanupResult incrementally during cleanup phases.
     */
    public static class Builder {
        private int entitiesRemoved = 0;
        private int blockEntitiesRemoved = 0;
        private int scheduledTicksCancelled = 0;
        private int blocksRemoved = 0;
        private long startTime = System.currentTimeMillis();
        private final List<String> warnings = new ArrayList<>();

        public Builder entitiesRemoved(int count) {
            this.entitiesRemoved = count;
            return this;
        }

        public Builder addEntitiesRemoved(int count) {
            this.entitiesRemoved += count;
            return this;
        }

        public Builder blockEntitiesRemoved(int count) {
            this.blockEntitiesRemoved = count;
            return this;
        }

        public Builder addBlockEntitiesRemoved(int count) {
            this.blockEntitiesRemoved += count;
            return this;
        }

        public Builder scheduledTicksCancelled(int count) {
            this.scheduledTicksCancelled = count;
            return this;
        }

        public Builder addScheduledTicksCancelled(int count) {
            this.scheduledTicksCancelled += count;
            return this;
        }

        public Builder blocksRemoved(int count) {
            this.blocksRemoved = count;
            return this;
        }

        public Builder addBlocksRemoved(int count) {
            this.blocksRemoved += count;
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder addWarnings(List<String> warnings) {
            this.warnings.addAll(warnings);
            return this;
        }

        public CleanupResult build() {
            long duration = System.currentTimeMillis() - startTime;
            return new CleanupResult(
                entitiesRemoved,
                blockEntitiesRemoved,
                scheduledTicksCancelled,
                blocksRemoved,
                duration,
                warnings
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CleanupResult[entities=%d, blockEntities=%d, ticks=%d, blocks=%d, duration=%dms, warnings=%d]",
            entitiesRemoved, blockEntitiesRemoved, scheduledTicksCancelled, blocksRemoved, durationMs, warnings.size()
        );
    }
}

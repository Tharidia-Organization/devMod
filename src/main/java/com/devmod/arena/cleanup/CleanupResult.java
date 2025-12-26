package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;
    public record CleanupResult(
        int entitiesRemoved,
        int blockEntitiesRemoved,
        int scheduledTicksCancelled,
        int blocksRemoved,
        int chunksUnloaded,
        long durationMs,
        int entitiesResidual,
        int blocksResidual,
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
            || scheduledTicksCancelled > 0 || blocksRemoved > 0 || chunksUnloaded > 0;
    }

    /**
     * Returns the total number of items cleaned up across all phases.
     */
    public int totalCleaned() {
        return entitiesRemoved + blockEntitiesRemoved + scheduledTicksCancelled + blocksRemoved + chunksUnloaded;
    }

    /**
     * Returns true if there are residual entities or blocks after cleanup.
     * Used to trigger alerts based on AlertThresholds.
     */
    public boolean hasResiduals() {
        return entitiesResidual > 0 || blocksResidual > 0;
    }

    /**
     * Creates an empty result indicating no cleanup was needed.
     */
    public static CleanupResult empty() {
        return new CleanupResult(0, 0, 0, 0, 0, 0L, 0, 0, List.of());
    }

    /**
     * Creates a failed result with a single error message.
     */
    public static CleanupResult failed(String error) {
        return new CleanupResult(0, 0, 0, 0, 0, 0L, 0, 0, List.of(error));
    }

    /**
     * Builder for constructing CleanupResult incrementally during cleanup phases.
     */
    public static class Builder {
        private int entitiesRemoved = 0;
        private int blockEntitiesRemoved = 0;
        private int scheduledTicksCancelled = 0;
        private int blocksRemoved = 0;
        private int chunksUnloaded = 0;
        private long startTime = System.currentTimeMillis();
        private long durationMs = 0;
        private int entitiesResidual = 0;
        private int blocksResidual = 0;
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

        public Builder chunksUnloaded(int count) {
            this.chunksUnloaded = count;
            return this;
        }

        public Builder addChunksUnloaded(int count) {
            this.chunksUnloaded += count;
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

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        /**
         * Sets the residual entity count (entities remaining after cleanup).
         */
        public Builder entitiesResidual(int count) {
            this.entitiesResidual = count;
            return this;
        }

        /**
         * Sets the residual block count (non-air blocks remaining after cleanup).
         */
        public Builder blocksResidual(int count) {
            this.blocksResidual = count;
            return this;
        }

        public CleanupResult build() {
            return new CleanupResult(
                entitiesRemoved,
                blockEntitiesRemoved,
                scheduledTicksCancelled,
                blocksRemoved,
                chunksUnloaded,
                durationMs > 0 ? durationMs : (System.currentTimeMillis() - startTime),
                entitiesResidual,
                blocksResidual,
                warnings
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "CleanupResult[entities=%d, blockEntities=%d, ticks=%d, blocks=%d, chunks=%d, duration=%dms, " +
            "entitiesResidual=%d, blocksResidual=%d, warnings=%d]",
            entitiesRemoved, blockEntitiesRemoved, scheduledTicksCancelled, blocksRemoved, chunksUnloaded,
            durationMs, entitiesResidual, blocksResidual, warnings.size()
        );
    }
}

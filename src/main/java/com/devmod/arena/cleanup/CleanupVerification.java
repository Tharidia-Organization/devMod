package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;
public record CleanupVerification(
    boolean entitiesClean,
    boolean blockEntitiesClean,
    boolean scheduledTicksClean,
    boolean blocksClean,
    List<String> violations
) {

    public CleanupVerification {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * Returns true if all verification checks passed.
     */
    public boolean isFullyClean() {
        return entitiesClean && blockEntitiesClean && scheduledTicksClean && blocksClean;
    }

    /**
     * Returns true if there are any violations.
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /**
     * Creates a successful verification with all checks passing.
     */
    public static CleanupVerification success() {
        return new CleanupVerification(true, true, true, true, List.of());
    }

    /**
     * Creates a builder for constructing verification results.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for incrementally constructing verification results.
     */
    public static class Builder {
        private boolean entitiesClean = true;
        private boolean blockEntitiesClean = true;
        private boolean scheduledTicksClean = true;
        private boolean blocksClean = true;
        private final List<String> violations = new ArrayList<>();

        public Builder entitiesClean(boolean clean) {
            this.entitiesClean = clean;
            return this;
        }

        public Builder blockEntitiesClean(boolean clean) {
            this.blockEntitiesClean = clean;
            return this;
        }

        public Builder scheduledTicksClean(boolean clean) {
            this.scheduledTicksClean = clean;
            return this;
        }

        public Builder blocksClean(boolean clean) {
            this.blocksClean = clean;
            return this;
        }

        public Builder addViolation(String violation) {
            this.violations.add(violation);
            return this;
        }

        public Builder addViolation(CleanupPhase phase, String detail) {
            this.violations.add(String.format("[%s] %s", phase.getDisplayName(), detail));
            switch (phase) {
                case ENTITIES -> this.entitiesClean = false;
                case BLOCK_ENTITIES -> this.blockEntitiesClean = false;
                case SCHEDULED_TICKS -> this.scheduledTicksClean = false;
                case BLOCKS -> this.blocksClean = false;
            }
            return this;
        }

        public CleanupVerification build() {
            return new CleanupVerification(
                entitiesClean,
                blockEntitiesClean,
                scheduledTicksClean,
                blocksClean,
                violations
            );
        }
    }

    @Override
    public String toString() {
        if (isFullyClean()) {
            return "CleanupVerification[CLEAN]";
        }
        return String.format(
            "CleanupVerification[entities=%s, blockEntities=%s, ticks=%s, blocks=%s, violations=%d]",
            entitiesClean ? "OK" : "DIRTY",
            blockEntitiesClean ? "OK" : "DIRTY",
            scheduledTicksClean ? "OK" : "DIRTY",
            blocksClean ? "OK" : "DIRTY",
            violations.size()
        );
    }
}

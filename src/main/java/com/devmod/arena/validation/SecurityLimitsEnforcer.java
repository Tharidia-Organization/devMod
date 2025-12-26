package com.devmod.arena.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityLimitsEnforcer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityLimitsEnforcer.class);

    // === HARD LIMITS (non-configurable) ===

    /** Maximum arena dimension per axis (blocks) */
    public static final int HARD_LIMIT_ARENA_SIZE = 256;

    /** Maximum hazard definitions */
    public static final int HARD_LIMIT_HAZARDS = 50;

    /** Maximum spawn slot definitions */
    public static final int HARD_LIMIT_SPAWNS = 100;

    /** Maximum entities during build */
    public static final int HARD_LIMIT_ENTITIES = 200;

    /** Maximum blocks to place */
    public static final int HARD_LIMIT_BLOCKS = 100_000;

    /** Maximum concurrent arenas per world */
    public static final int HARD_LIMIT_CONCURRENT_ARENAS = 10;

    /** Maximum inheritance depth */
    public static final int HARD_LIMIT_INHERITANCE_DEPTH = 5;

    /** Maximum template file size (bytes) */
    public static final long HARD_LIMIT_TEMPLATE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    /** Maximum structure NBT size (bytes) */
    public static final long HARD_LIMIT_STRUCTURE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    // === SOFT LIMITS (configurable, but cannot exceed hard limits) ===

    private int softLimitArenaSize;
    private int softLimitHazards;
    private int softLimitSpawns;
    private int softLimitEntities;
    private int softLimitBlocks;
    private int softLimitConcurrentArenas;

    /**
     * Creates enforcer with default soft limits (same as hard limits).
     */
    public SecurityLimitsEnforcer() {
        this.softLimitArenaSize = HARD_LIMIT_ARENA_SIZE;
        this.softLimitHazards = HARD_LIMIT_HAZARDS;
        this.softLimitSpawns = HARD_LIMIT_SPAWNS;
        this.softLimitEntities = HARD_LIMIT_ENTITIES;
        this.softLimitBlocks = HARD_LIMIT_BLOCKS;
        this.softLimitConcurrentArenas = HARD_LIMIT_CONCURRENT_ARENAS;
    }

    /**
     * Creates enforcer with custom soft limits.
     */
    public SecurityLimitsEnforcer(LimitsConfig config) {
        Objects.requireNonNull(config, "config");

        // Apply soft limits, capped to hard limits
        this.softLimitArenaSize = Math.min(config.arenaSize(), HARD_LIMIT_ARENA_SIZE);
        this.softLimitHazards = Math.min(config.hazards(), HARD_LIMIT_HAZARDS);
        this.softLimitSpawns = Math.min(config.spawns(), HARD_LIMIT_SPAWNS);
        this.softLimitEntities = Math.min(config.entities(), HARD_LIMIT_ENTITIES);
        this.softLimitBlocks = Math.min(config.blocks(), HARD_LIMIT_BLOCKS);
        this.softLimitConcurrentArenas = Math.min(config.concurrentArenas(), HARD_LIMIT_CONCURRENT_ARENAS);
    }

    /**
     * Enforces all limits on the given template context.
     *
     * @return EnforcementResult with pass/fail status and violations
     */
    public EnforcementResult enforce(LimitsContext context) {
        List<LimitViolation> violations = new ArrayList<>();

        // Arena size check
        if (context.sizeX() > softLimitArenaSize) {
            violations.add(new LimitViolation(
                LimitType.ARENA_SIZE_X,
                context.sizeX(),
                softLimitArenaSize,
                HARD_LIMIT_ARENA_SIZE,
                context.sizeX() > HARD_LIMIT_ARENA_SIZE
            ));
        }
        if (context.sizeY() > softLimitArenaSize) {
            violations.add(new LimitViolation(
                LimitType.ARENA_SIZE_Y,
                context.sizeY(),
                softLimitArenaSize,
                HARD_LIMIT_ARENA_SIZE,
                context.sizeY() > HARD_LIMIT_ARENA_SIZE
            ));
        }
        if (context.sizeZ() > softLimitArenaSize) {
            violations.add(new LimitViolation(
                LimitType.ARENA_SIZE_Z,
                context.sizeZ(),
                softLimitArenaSize,
                HARD_LIMIT_ARENA_SIZE,
                context.sizeZ() > HARD_LIMIT_ARENA_SIZE
            ));
        }

        // Hazard count check
        if (context.hazardCount() > softLimitHazards) {
            violations.add(new LimitViolation(
                LimitType.HAZARDS,
                context.hazardCount(),
                softLimitHazards,
                HARD_LIMIT_HAZARDS,
                context.hazardCount() > HARD_LIMIT_HAZARDS
            ));
        }

        // Spawn count check
        if (context.spawnCount() > softLimitSpawns) {
            violations.add(new LimitViolation(
                LimitType.SPAWNS,
                context.spawnCount(),
                softLimitSpawns,
                HARD_LIMIT_SPAWNS,
                context.spawnCount() > HARD_LIMIT_SPAWNS
            ));
        }

        // Entity count check
        if (context.entityCount() > softLimitEntities) {
            violations.add(new LimitViolation(
                LimitType.ENTITIES,
                context.entityCount(),
                softLimitEntities,
                HARD_LIMIT_ENTITIES,
                context.entityCount() > HARD_LIMIT_ENTITIES
            ));
        }

        // Block count check
        if (context.blockCount() > softLimitBlocks) {
            violations.add(new LimitViolation(
                LimitType.BLOCKS,
                context.blockCount(),
                softLimitBlocks,
                HARD_LIMIT_BLOCKS,
                context.blockCount() > HARD_LIMIT_BLOCKS
            ));
        }

        // Concurrent arenas check
        if (context.concurrentArenas() >= softLimitConcurrentArenas) {
            violations.add(new LimitViolation(
                LimitType.CONCURRENT_ARENAS,
                context.concurrentArenas(),
                softLimitConcurrentArenas,
                HARD_LIMIT_CONCURRENT_ARENAS,
                context.concurrentArenas() >= HARD_LIMIT_CONCURRENT_ARENAS
            ));
        }

        // Inheritance depth check
        if (context.inheritanceDepth() > HARD_LIMIT_INHERITANCE_DEPTH) {
            violations.add(new LimitViolation(
                LimitType.INHERITANCE_DEPTH,
                context.inheritanceDepth(),
                HARD_LIMIT_INHERITANCE_DEPTH,
                HARD_LIMIT_INHERITANCE_DEPTH,
                true // Always hard violation for inheritance
            ));
        }

        // Template file size check
        if (context.templateSizeBytes() > HARD_LIMIT_TEMPLATE_SIZE_BYTES) {
            violations.add(new LimitViolation(
                LimitType.TEMPLATE_SIZE,
                context.templateSizeBytes(),
                HARD_LIMIT_TEMPLATE_SIZE_BYTES,
                HARD_LIMIT_TEMPLATE_SIZE_BYTES,
                true
            ));
        }

        // Structure NBT size check
        if (context.structureSizeBytes() > HARD_LIMIT_STRUCTURE_SIZE_BYTES) {
            violations.add(new LimitViolation(
                LimitType.STRUCTURE_SIZE,
                context.structureSizeBytes(),
                HARD_LIMIT_STRUCTURE_SIZE_BYTES,
                HARD_LIMIT_STRUCTURE_SIZE_BYTES,
                true
            ));
        }

        boolean hasHardViolations = violations.stream().anyMatch(LimitViolation::isHardViolation);
        boolean passed = violations.isEmpty();

        if (!passed) {
            LOGGER.warn("Security limits enforcement: {} violation(s), hard={}",
                violations.size(), hasHardViolations);
            for (LimitViolation v : violations) {
                if (v.isHardViolation()) {
                    LOGGER.error("HARD LIMIT EXCEEDED: {} = {} (max={})",
                        v.type(), v.actual(), v.hardLimit());
                } else {
                    LOGGER.warn("Soft limit exceeded: {} = {} (soft={}, hard={})",
                        v.type(), v.actual(), v.softLimit(), v.hardLimit());
                }
            }
        }

        return new EnforcementResult(
            passed,
            hasHardViolations,
            violations
        );
    }

    /**
     * Quick check if a single value exceeds its hard limit.
     */
    public boolean exceedsHardLimit(LimitType type, long value) {
        return switch (type) {
            case ARENA_SIZE_X, ARENA_SIZE_Y, ARENA_SIZE_Z -> value > HARD_LIMIT_ARENA_SIZE;
            case HAZARDS -> value > HARD_LIMIT_HAZARDS;
            case SPAWNS -> value > HARD_LIMIT_SPAWNS;
            case ENTITIES -> value > HARD_LIMIT_ENTITIES;
            case BLOCKS -> value > HARD_LIMIT_BLOCKS;
            case CONCURRENT_ARENAS -> value >= HARD_LIMIT_CONCURRENT_ARENAS;
            case INHERITANCE_DEPTH -> value > HARD_LIMIT_INHERITANCE_DEPTH;
            case TEMPLATE_SIZE -> value > HARD_LIMIT_TEMPLATE_SIZE_BYTES;
            case STRUCTURE_SIZE -> value > HARD_LIMIT_STRUCTURE_SIZE_BYTES;
        };
    }

    /**
     * Returns current soft limits.
     */
    public LimitsConfig getCurrentLimits() {
        return new LimitsConfig(
            softLimitArenaSize,
            softLimitHazards,
            softLimitSpawns,
            softLimitEntities,
            softLimitBlocks,
            softLimitConcurrentArenas
        );
    }

    // ========== Supporting Types ==========

    /**
     * Types of limits that can be enforced.
     */
    public enum LimitType {
        ARENA_SIZE_X("Arena size X"),
        ARENA_SIZE_Y("Arena size Y"),
        ARENA_SIZE_Z("Arena size Z"),
        HAZARDS("Hazard count"),
        SPAWNS("Spawn slot count"),
        ENTITIES("Entity count"),
        BLOCKS("Block count"),
        CONCURRENT_ARENAS("Concurrent arenas"),
        INHERITANCE_DEPTH("Inheritance depth"),
        TEMPLATE_SIZE("Template file size"),
        STRUCTURE_SIZE("Structure NBT size");

        private final String displayName;

        LimitType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * A single limit violation.
     */
    public record LimitViolation(
        LimitType type,
        long actual,
        long softLimit,
        long hardLimit,
        boolean isHardViolation
    ) {
        public String format() {
            if (isHardViolation) {
                return String.format("HARD LIMIT: %s = %d (max %d)",
                    type.getDisplayName(), actual, hardLimit);
            }
            return String.format("Soft limit: %s = %d (soft %d, hard %d)",
                type.getDisplayName(), actual, softLimit, hardLimit);
        }
    }

    /**
     * Result of enforcement check.
     */
    public record EnforcementResult(
        boolean passed,
        boolean hasHardViolations,
        List<LimitViolation> violations
    ) {
        public EnforcementResult {
            violations = violations != null
                ? Collections.unmodifiableList(new ArrayList<>(violations))
                : Collections.emptyList();
        }

        /**
         * Returns true if build should be blocked.
         */
        public boolean shouldBlock() {
            return hasHardViolations;
        }

        /**
         * Returns true if build can proceed with warnings.
         */
        public boolean canProceedWithWarnings() {
            return !hasHardViolations && !violations.isEmpty();
        }

        /**
         * Returns formatted summary.
         */
        public String formatSummary() {
            if (passed) {
                return "All security limits passed";
            }
            if (hasHardViolations) {
                long hardCount = violations.stream().filter(LimitViolation::isHardViolation).count();
                return String.format("BLOCKED: %d hard limit violation(s)", hardCount);
            }
            return String.format("WARNINGS: %d soft limit violation(s)", violations.size());
        }
    }

    /**
     * Configuration for soft limits.
     */
    public record LimitsConfig(
        int arenaSize,
        int hazards,
        int spawns,
        int entities,
        int blocks,
        int concurrentArenas
    ) {
        /**
         * Creates default config (same as hard limits).
         */
        public static LimitsConfig defaults() {
            return new LimitsConfig(
                HARD_LIMIT_ARENA_SIZE,
                HARD_LIMIT_HAZARDS,
                HARD_LIMIT_SPAWNS,
                HARD_LIMIT_ENTITIES,
                HARD_LIMIT_BLOCKS,
                HARD_LIMIT_CONCURRENT_ARENAS
            );
        }

        /**
         * Creates a restrictive config for testing.
         */
        public static LimitsConfig restrictive() {
            return new LimitsConfig(64, 10, 20, 50, 10_000, 3);
        }
    }

    /**
     * Context for limit checking.
     */
    public interface LimitsContext {
        int sizeX();
        int sizeY();
        int sizeZ();
        int hazardCount();
        int spawnCount();
        int entityCount();
        int blockCount();
        int concurrentArenas();
        int inheritanceDepth();
        long templateSizeBytes();
        long structureSizeBytes();
    }

    /**
     * Simple implementation of LimitsContext.
     */
    public record SimpleLimitsContext(
        int sizeX,
        int sizeY,
        int sizeZ,
        int hazardCount,
        int spawnCount,
        int entityCount,
        int blockCount,
        int concurrentArenas,
        int inheritanceDepth,
        long templateSizeBytes,
        long structureSizeBytes
    ) implements LimitsContext {

        /**
         * Builder for SimpleLimitsContext.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int sizeX = 64;
            private int sizeY = 64;
            private int sizeZ = 64;
            private int hazardCount = 0;
            private int spawnCount = 1;
            private int entityCount = 0;
            private int blockCount = 0;
            private int concurrentArenas = 0;
            private int inheritanceDepth = 0;
            private long templateSizeBytes = 0;
            private long structureSizeBytes = 0;

            public Builder sizeX(int sizeX) { this.sizeX = sizeX; return this; }
            public Builder sizeY(int sizeY) { this.sizeY = sizeY; return this; }
            public Builder sizeZ(int sizeZ) { this.sizeZ = sizeZ; return this; }
            public Builder size(int x, int y, int z) {
                this.sizeX = x;
                this.sizeY = y;
                this.sizeZ = z;
                return this;
            }
            public Builder hazardCount(int hazardCount) { this.hazardCount = hazardCount; return this; }
            public Builder spawnCount(int spawnCount) { this.spawnCount = spawnCount; return this; }
            public Builder entityCount(int entityCount) { this.entityCount = entityCount; return this; }
            public Builder blockCount(int blockCount) { this.blockCount = blockCount; return this; }
            public Builder concurrentArenas(int concurrentArenas) { this.concurrentArenas = concurrentArenas; return this; }
            public Builder inheritanceDepth(int inheritanceDepth) { this.inheritanceDepth = inheritanceDepth; return this; }
            public Builder templateSizeBytes(long templateSizeBytes) { this.templateSizeBytes = templateSizeBytes; return this; }
            public Builder structureSizeBytes(long structureSizeBytes) { this.structureSizeBytes = structureSizeBytes; return this; }

            public SimpleLimitsContext build() {
                return new SimpleLimitsContext(
                    sizeX, sizeY, sizeZ, hazardCount, spawnCount,
                    entityCount, blockCount, concurrentArenas,
                    inheritanceDepth, templateSizeBytes, structureSizeBytes
                );
            }
        }
    }
}

package com.devmod.arena.validation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class RuntimePreflightCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimePreflightCheck.class);

    // DD40: Preflight timeouts and thresholds
    public static final Duration DEFAULT_CHUNK_LOAD_TIMEOUT = Duration.ofSeconds(10);
    public static final double MAX_MSPT_THRESHOLD = 45.0; // Allow build if MSPT < 45ms
    public static final double MIN_TPS_THRESHOLD = 18.0;  // Allow build if TPS > 18

    private final Duration chunkLoadTimeout;
    private final double msptThreshold;
    private final double tpsThreshold;
    private final boolean requireInstanceWorld;

    /**
     * Creates preflight check with default settings.
     */
    public RuntimePreflightCheck() {
        this(DEFAULT_CHUNK_LOAD_TIMEOUT, MAX_MSPT_THRESHOLD, MIN_TPS_THRESHOLD, true);
    }

    /**
     * Creates preflight check with custom settings.
     */
    public RuntimePreflightCheck(Duration chunkLoadTimeout, double msptThreshold,
                                  double tpsThreshold, boolean requireInstanceWorld) {
        this.chunkLoadTimeout = Objects.requireNonNull(chunkLoadTimeout);
        this.msptThreshold = msptThreshold;
        this.tpsThreshold = tpsThreshold;
        this.requireInstanceWorld = requireInstanceWorld;
    }

    /**
     * Performs all preflight checks.
     *
     * @param context Runtime context to check
     * @return PreflightResult with pass/fail status and details
     */
    public PreflightResult check(PreflightContext context) {
        List<PreflightIssue> issues = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // === Check 1: Instance-only gate ===
        if (requireInstanceWorld && !context.isInstanceWorld()) {
            issues.add(new PreflightIssue(
                PreflightCheckType.INSTANCE_ONLY_GATE,
                PreflightSeverity.BLOCKING,
                "Arena build rejected: only allowed in instance worlds",
                String.format("World type: %s, dimension: %s",
                    context.worldType(), context.dimensionKey())
            ));
            LOGGER.warn("Instance-only gate blocked arena build in world type: {}",
                context.worldType());
        }

        // === Check 2: Dimension blacklist ===
        if (isDimensionBlacklisted(context.dimensionKey())) {
            issues.add(new PreflightIssue(
                PreflightCheckType.DIMENSION_BLACKLIST,
                PreflightSeverity.BLOCKING,
                "Arena build rejected: dimension is blacklisted",
                String.format("Dimension: %s", context.dimensionKey())
            ));
        }

        // === Check 3: MSPT threshold ===
        double currentMspt = context.currentMspt();
        if (currentMspt > msptThreshold) {
            issues.add(new PreflightIssue(
                PreflightCheckType.MSPT_THRESHOLD,
                PreflightSeverity.BLOCKING,
                String.format("Server MSPT too high: %.1fms (max %.1fms)", currentMspt, msptThreshold),
                "Server is under heavy load, arena build deferred"
            ));
        }

        // === Check 4: TPS threshold ===
        double currentTps = context.currentTps();
        if (currentTps < tpsThreshold) {
            issues.add(new PreflightIssue(
                PreflightCheckType.TPS_THRESHOLD,
                PreflightSeverity.BLOCKING,
                String.format("Server TPS too low: %.1f (min %.1f)", currentTps, tpsThreshold),
                "Server is under heavy load, arena build deferred"
            ));
        }

        // === Check 5: Concurrent arena limit ===
        int activeArenas = context.activeArenasInWorld();
        int maxArenas = SecurityLimitsEnforcer.HARD_LIMIT_CONCURRENT_ARENAS;
        if (activeArenas >= maxArenas) {
            issues.add(new PreflightIssue(
                PreflightCheckType.CONCURRENT_ARENA_LIMIT,
                PreflightSeverity.BLOCKING,
                String.format("Concurrent arena limit reached: %d/%d", activeArenas, maxArenas),
                "Wait for existing arenas to complete"
            ));
        }

        // === Check 6: Chunk loading verification ===
        ChunkLoadResult chunkResult = verifyChunksLoaded(context);
        if (!chunkResult.allLoaded()) {
            issues.add(new PreflightIssue(
                PreflightCheckType.CHUNKS_LOADED,
                PreflightSeverity.BLOCKING,
                String.format("Required chunks not loaded: %d/%d",
                    chunkResult.loadedCount(), chunkResult.requiredCount()),
                chunkResult.details()
            ));
        }

        // === Check 7: Memory available ===
        if (context.availableMemoryMb() < 256) {
            issues.add(new PreflightIssue(
                PreflightCheckType.MEMORY_AVAILABLE,
                PreflightSeverity.WARNING,
                String.format("Low memory: %d MB available", context.availableMemoryMb()),
                "Arena build may cause memory pressure"
            ));
        }

        long duration = System.currentTimeMillis() - startTime;
        boolean passed = issues.stream().noneMatch(i -> i.severity() == PreflightSeverity.BLOCKING);

        PreflightResult result = new PreflightResult(
            passed,
            issues,
            Duration.ofMillis(duration)
        );

        if (passed) {
            LOGGER.info("Preflight check PASSED in {}ms", duration);
        } else {
            long blockingCount = issues.stream()
                .filter(i -> i.severity() == PreflightSeverity.BLOCKING)
                .count();
            LOGGER.warn("Preflight check FAILED: {} blocking issue(s) in {}ms",
                blockingCount, duration);
        }

        return result;
    }

    /**
     * Performs async preflight check with timeout.
     */
    public CompletableFuture<PreflightResult> checkAsync(PreflightContext context, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> check(context))
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                if (ex.getCause() instanceof TimeoutException) {
                    return PreflightResult.timeout(timeout);
                }
                return PreflightResult.error(ex.getMessage());
            });
    }

    /**
     * Verifies that all required chunks are loaded.
     */
    private ChunkLoadResult verifyChunksLoaded(PreflightContext context) {
        List<ChunkPos> required = context.requiredChunks();
        if (required == null || required.isEmpty()) {
            return new ChunkLoadResult(true, 0, 0, "No chunks required");
        }

        int loaded = 0;
        List<ChunkPos> notLoaded = new ArrayList<>();

        for (ChunkPos chunk : required) {
            if (context.isChunkLoaded(chunk.x(), chunk.z())) {
                loaded++;
            } else {
                notLoaded.add(chunk);
                if (notLoaded.size() >= 10) {
                    break; // Don't enumerate all missing chunks
                }
            }
        }

        if (notLoaded.isEmpty()) {
            return new ChunkLoadResult(true, loaded, required.size(), "All chunks loaded");
        }

        String details = String.format("Missing chunks (first %d): %s",
            Math.min(notLoaded.size(), 10),
            notLoaded.stream()
                .limit(10)
                .map(c -> String.format("(%d,%d)", c.x(), c.z()))
                .reduce((a, b) -> a + ", " + b)
                .orElse(""));

        return new ChunkLoadResult(false, loaded, required.size(), details);
    }

    /**
     * Checks if a dimension is blacklisted for arena builds.
     */
    private boolean isDimensionBlacklisted(String dimensionKey) {
        if (dimensionKey == null) return false;

        // Blacklist vanilla dimensions when instance-only mode is enabled
        if (requireInstanceWorld) {
            return dimensionKey.equals("minecraft:overworld")
                || dimensionKey.equals("minecraft:the_nether")
                || dimensionKey.equals("minecraft:the_end");
        }

        return false;
    }

    /**
     * Waits for chunks to load with timeout.
     */
    public CompletableFuture<ChunkLoadResult> waitForChunks(
            PreflightContext context,
            Supplier<Boolean> loadChunks) {

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            long timeoutMs = chunkLoadTimeout.toMillis();

            // Trigger chunk loading
            loadChunks.get();

            // Poll for chunks loaded
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                ChunkLoadResult result = verifyChunksLoaded(context);
                if (result.allLoaded()) {
                    return result;
                }

                try {
                    Thread.sleep(100); // Poll every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Timeout reached
            return verifyChunksLoaded(context);
        }).orTimeout(chunkLoadTimeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
    }

    // ========== Supporting Types ==========

    /**
     * Types of preflight checks.
     */
    public enum PreflightCheckType {
        INSTANCE_ONLY_GATE("Instance-only gate"),
        DIMENSION_BLACKLIST("Dimension blacklist"),
        MSPT_THRESHOLD("MSPT threshold"),
        TPS_THRESHOLD("TPS threshold"),
        CONCURRENT_ARENA_LIMIT("Concurrent arena limit"),
        CHUNKS_LOADED("Chunks loaded"),
        MEMORY_AVAILABLE("Memory available");

        private final String displayName;

        PreflightCheckType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Severity of preflight issues.
     */
    public enum PreflightSeverity {
        /** Build is blocked */
        BLOCKING,
        /** Build proceeds with warning */
        WARNING
    }

    /**
     * A single preflight issue.
     */
    public record PreflightIssue(
        PreflightCheckType type,
        PreflightSeverity severity,
        String message,
        String details
    ) {}

    /**
     * Result of preflight check.
     */
    public record PreflightResult(
        boolean passed,
        List<PreflightIssue> issues,
        Duration checkDuration
    ) {
        public PreflightResult {
            issues = issues != null
                ? Collections.unmodifiableList(new ArrayList<>(issues))
                : Collections.emptyList();
        }

        public boolean hasBlockingIssues() {
            return issues.stream().anyMatch(i -> i.severity() == PreflightSeverity.BLOCKING);
        }

        public boolean hasWarnings() {
            return issues.stream().anyMatch(i -> i.severity() == PreflightSeverity.WARNING);
        }

        public List<PreflightIssue> getBlockingIssues() {
            return issues.stream()
                .filter(i -> i.severity() == PreflightSeverity.BLOCKING)
                .toList();
        }

        public String formatSummary() {
            if (passed && issues.isEmpty()) {
                return String.format("Preflight PASSED (%dms)", checkDuration.toMillis());
            }
            if (passed) {
                return String.format("Preflight PASSED with %d warning(s) (%dms)",
                    issues.size(), checkDuration.toMillis());
            }
            long blocking = issues.stream()
                .filter(i -> i.severity() == PreflightSeverity.BLOCKING)
                .count();
            return String.format("Preflight FAILED: %d blocking issue(s) (%dms)",
                blocking, checkDuration.toMillis());
        }

        public static PreflightResult timeout(Duration timeout) {
            return new PreflightResult(
                false,
                List.of(new PreflightIssue(
                    PreflightCheckType.CHUNKS_LOADED,
                    PreflightSeverity.BLOCKING,
                    "Preflight check timed out after " + timeout.toMillis() + "ms",
                    "Server may be under heavy load"
                )),
                timeout
            );
        }

        public static PreflightResult error(String message) {
            return new PreflightResult(
                false,
                List.of(new PreflightIssue(
                    PreflightCheckType.CHUNKS_LOADED,
                    PreflightSeverity.BLOCKING,
                    "Preflight check error: " + message,
                    null
                )),
                Duration.ZERO
            );
        }
    }

    /**
     * Result of chunk loading verification.
     */
    public record ChunkLoadResult(
        boolean allLoaded,
        int loadedCount,
        int requiredCount,
        String details
    ) {}

    /**
     * Simple chunk position.
     */
    public record ChunkPos(int x, int z) {}

    /**
     * Context for preflight checks.
     */
    public interface PreflightContext {
        // World type checks
        boolean isInstanceWorld();
        String worldType();
        String dimensionKey();

        // Performance metrics
        double currentMspt();
        double currentTps();

        // Arena state
        int activeArenasInWorld();

        // Chunk state
        boolean isChunkLoaded(int chunkX, int chunkZ);
        List<ChunkPos> requiredChunks();

        // System resources
        long availableMemoryMb();
    }

    /**
     * Simple implementation of PreflightContext.
     */
    public record SimplePreflightContext(
        boolean isInstanceWorld,
        String worldType,
        String dimensionKey,
        double currentMspt,
        double currentTps,
        int activeArenasInWorld,
        ChunkChecker chunkChecker,
        List<ChunkPos> requiredChunks,
        long availableMemoryMb
    ) implements PreflightContext {

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return chunkChecker != null && chunkChecker.isLoaded(chunkX, chunkZ);
        }

        @FunctionalInterface
        public interface ChunkChecker {
            boolean isLoaded(int x, int z);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean isInstanceWorld = true;
            private String worldType = "instance";
            private String dimensionKey = "devmod:arena";
            private double currentMspt = 20.0;
            private double currentTps = 20.0;
            private int activeArenasInWorld = 0;
            private ChunkChecker chunkChecker = (x, z) -> true;
            private List<ChunkPos> requiredChunks = List.of();
            private long availableMemoryMb = 1024;

            public Builder isInstanceWorld(boolean isInstanceWorld) {
                this.isInstanceWorld = isInstanceWorld;
                return this;
            }

            public Builder worldType(String worldType) {
                this.worldType = worldType;
                return this;
            }

            public Builder dimensionKey(String dimensionKey) {
                this.dimensionKey = dimensionKey;
                return this;
            }

            public Builder currentMspt(double currentMspt) {
                this.currentMspt = currentMspt;
                return this;
            }

            public Builder currentTps(double currentTps) {
                this.currentTps = currentTps;
                return this;
            }

            public Builder activeArenasInWorld(int activeArenasInWorld) {
                this.activeArenasInWorld = activeArenasInWorld;
                return this;
            }

            public Builder chunkChecker(ChunkChecker chunkChecker) {
                this.chunkChecker = chunkChecker;
                return this;
            }

            public Builder requiredChunks(List<ChunkPos> requiredChunks) {
                this.requiredChunks = requiredChunks;
                return this;
            }

            public Builder availableMemoryMb(long availableMemoryMb) {
                this.availableMemoryMb = availableMemoryMb;
                return this;
            }

            public SimplePreflightContext build() {
                return new SimplePreflightContext(
                    isInstanceWorld, worldType, dimensionKey,
                    currentMspt, currentTps, activeArenasInWorld,
                    chunkChecker, requiredChunks, availableMemoryMb
                );
            }
        }
    }
}

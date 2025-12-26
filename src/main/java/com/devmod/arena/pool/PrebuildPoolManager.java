package com.devmod.arena.pool;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrebuildPoolManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrebuildPoolManager.class);

    // DD63: Feature flag - default OFF until data justifies
    private volatile boolean poolEnabled = false;

    // DD64: Unused threshold
    private static final Duration UNUSED_THRESHOLD = Duration.ofMinutes(10);

    // DD65: Miss rate thresholds
    private static final double MISS_RATE_WARN = 0.20;
    private static final double MISS_RATE_CRITICAL = 0.30;
    private static final double MISS_RATE_AUTO_DISABLE = 0.50;
    private static final int CONSECUTIVE_CHECKS_FOR_DISABLE = 3;

    /**
     * Pool state machine (DD64).
     */
    public enum PoolState {
        /** Arena is being built */
        BUILDING,
        /** Arena is ready, not assigned */
        READY,
        /** Arena is reserved for a party but not yet used */
        RESERVED,
        /** Arena is actively in use */
        IN_USE,
        /** Arena is being cleaned up */
        CLEANUP
    }

    /**
     * Pooled arena record (DD64).
     */
    public record PooledArena(
        UUID arenaId,
        String templateId,
        PoolState state,
        Instant createdAt,
        Instant lastStateChange,
        @Nullable UUID reservedFor
    ) {
        /**
         * Checks if arena is unused.
         * DD64: Only READY can be considered unused.
         */
        public boolean isUnused(Instant now) {
            if (state != PoolState.READY) return false;
            return Duration.between(lastStateChange, now).compareTo(UNUSED_THRESHOLD) > 0;
        }

        /**
         * Checks if arena can be evicted.
         * DD64: NEVER evict if RESERVED or IN_USE.
         */
        public boolean canBeEvicted() {
            return state == PoolState.READY || state == PoolState.BUILDING;
        }

        /**
         * Creates a new instance with updated state.
         */
        public PooledArena withState(PoolState newState) {
            return new PooledArena(
                arenaId, templateId, newState, createdAt, Instant.now(), reservedFor
            );
        }

        /**
         * Creates a new instance reserved for a party.
         */
        public PooledArena withReservation(UUID partyId) {
            return new PooledArena(
                arenaId, templateId, PoolState.RESERVED, createdAt, Instant.now(), partyId
            );
        }
    }

    /**
     * Pool metrics (DD65).
     */
    public record PoolMetrics(
        long hits,
        long misses,
        double missRate,
        int poolSize,
        int readyCount,
        int reservedCount,
        int inUseCount
    ) {
        public static PoolMetrics empty() {
            return new PoolMetrics(0, 0, 0.0, 0, 0, 0, 0);
        }
    }

    /**
     * Pool configuration (DD63).
     */
    public record PoolConfig(
        Map<String, Integer> templatePoolSizes,
        int maxTotalPoolSize,
        Duration refreshInterval
    ) {
        public static PoolConfig defaultConfig() {
            return new PoolConfig(
                Map.of(
                    "default_flat_64", 2,
                    "boss_ring_80", 1
                ),
                5,
                Duration.ofMinutes(5)
            );
        }
    }

    /**
     * Decision criteria for enabling pool (DD63).
     */
    public record PoolJustification(
        long buildTimeP95Ms,
        double questRatePerMinPerPlayer,
        int peakPlayerCount,
        boolean justified
    ) {
        /**
         * Evaluates if pool is justified based on DD63 criteria.
         */
        public static PoolJustification evaluate(
                long buildTimeP95Ms,
                double questRatePerMinPerPlayer,
                int peakPlayerCount) {
            // DD63: Pool justified IF build_time_p95 > 5000ms AND quest_rate > 0.5/min/player
            boolean justified = buildTimeP95Ms > 5000 && questRatePerMinPerPlayer > 0.5;
            return new PoolJustification(
                buildTimeP95Ms, questRatePerMinPerPlayer, peakPlayerCount, justified
            );
        }
    }

    private final ConcurrentHashMap<UUID, PooledArena> pool = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong consecutiveHighMissChecks = new AtomicLong(0);
    private final PoolConfig config;
    private final ScheduledExecutorService scheduler;

    public PrebuildPoolManager() {
        this(PoolConfig.defaultConfig());
    }

    @SuppressWarnings("this-escape") // scheduler captures this; OK because class is final-ish in usage and tasks are delayed
    public PrebuildPoolManager(PoolConfig config) {
        this.config = config;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "arena-pool-manager");
            t.setDaemon(true);
            return t;
        });

        // Schedule metrics evaluation every 5 minutes (DD65)
        scheduler.scheduleAtFixedRate(
            this::evaluatePoolEffectiveness,
            5, 5, TimeUnit.MINUTES
        );

        // Schedule cleanup every minute (DD64)
        scheduler.scheduleAtFixedRate(
            this::cleanupUnusedArenas,
            1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * Enables the pool.
     * DD63: Only enable when data justifies.
     */
    public void enable() {
        this.poolEnabled = true;
        LOGGER.info("Prebuild pool enabled");
        emitTelemetry("arena.pool.enabled", Map.of());
    }

    /**
     * Disables the pool.
     */
    public void disable() {
        this.poolEnabled = false;
        LOGGER.info("Prebuild pool disabled");
        emitTelemetry("arena.pool.disabled", Map.of());
    }

    /**
     * Checks if pool is enabled.
     */
    public boolean isEnabled() {
        return poolEnabled;
    }

    /**
     * Tries to acquire a pooled arena for a template.
     * DD65: Records hit/miss.
     *
     * @param templateId the template ID
     * @param partyId    the party requesting the arena
     * @return Optional containing pooled arena if available
     */
    public Optional<PooledArena> acquire(String templateId, UUID partyId) {
        if (!poolEnabled) {
            return Optional.empty();
        }

        Optional<PooledArena> match = pool.values().stream()
            .filter(a -> a.templateId().equals(templateId))
            .filter(a -> a.state() == PoolState.READY)
            .findFirst();

        if (match.isPresent()) {
            PooledArena arena = match.get();
            PooledArena reserved = arena.withReservation(partyId);
            pool.put(arena.arenaId(), reserved);
            hits.incrementAndGet();

            LOGGER.debug("Pool hit for template {}, arena {}", templateId, arena.arenaId());
            emitTelemetry("arena.pool.hit", Map.of(
                "templateId", templateId,
                "arenaId", arena.arenaId().toString()
            ));

            return Optional.of(reserved);
        } else {
            misses.incrementAndGet();

            LOGGER.debug("Pool miss for template {}", templateId);
            emitTelemetry("arena.pool.miss", Map.of("templateId", templateId));

            return Optional.empty();
        }
    }

    /**
     * Adds a newly built arena to the pool.
     *
     * @param arenaId    the arena ID
     * @param templateId the template ID
     */
    public void add(UUID arenaId, String templateId) {
        if (!poolEnabled) {
            return;
        }

        PooledArena arena = new PooledArena(
            arenaId, templateId, PoolState.READY,
            Instant.now(), Instant.now(), null
        );
        pool.put(arenaId, arena);

        LOGGER.debug("Added arena {} to pool for template {}", arenaId, templateId);
        emitTelemetry("arena.pool.added", Map.of(
            "arenaId", arenaId.toString(),
            "templateId", templateId
        ));
    }

    /**
     * Marks an arena as in use.
     *
     * @param arenaId the arena ID
     */
    public void markInUse(UUID arenaId) {
        PooledArena arena = pool.get(arenaId);
        if (arena != null) {
            pool.put(arenaId, arena.withState(PoolState.IN_USE));
        }
    }

    /**
     * Releases an arena back to the pool or for cleanup.
     *
     * @param arenaId the arena ID
     */
    public void release(UUID arenaId) {
        PooledArena arena = pool.get(arenaId);
        if (arena != null) {
            pool.put(arenaId, arena.withState(PoolState.CLEANUP));
            // In real implementation, trigger actual cleanup
        }
    }

    /**
     * Removes an arena from the pool.
     *
     * @param arenaId the arena ID
     */
    public void remove(UUID arenaId) {
        pool.remove(arenaId);
    }

    /**
     * Gets current pool metrics.
     * DD65: Hit/miss tracking.
     */
    public PoolMetrics getMetrics() {
        long h = hits.get();
        long m = misses.get();
        double total = h + m;
        double missRate = total > 0 ? m / total : 0.0;

        int ready = 0, reserved = 0, inUse = 0;
        for (PooledArena arena : pool.values()) {
            switch (arena.state()) {
                case READY -> ready++;
                case RESERVED -> reserved++;
                case IN_USE -> inUse++;
                default -> {}
            }
        }

        return new PoolMetrics(h, m, missRate, pool.size(), ready, reserved, inUse);
    }

    /**
     * Evaluates pool effectiveness.
     * DD65: Alert on high miss rate, auto-disable if > 50%.
     */
    private void evaluatePoolEffectiveness() {
        if (!poolEnabled) {
            consecutiveHighMissChecks.set(0);
            return;
        }

        PoolMetrics metrics = getMetrics();
        double missRate = metrics.missRate();

        if (missRate > MISS_RATE_AUTO_DISABLE) {
            long checks = consecutiveHighMissChecks.incrementAndGet();
            if (checks >= CONSECUTIVE_CHECKS_FOR_DISABLE) {
                // DD65: Auto-disable if > 50% for 3 consecutive checks
                poolEnabled = false;
                LOGGER.warn("Pool auto-disabled due to high miss rate: {}%", missRate * 100);
                emitTelemetry("arena.pool.auto_disabled", Map.of(
                    "missRate", missRate,
                    "consecutiveChecks", checks
                ));
            }
        } else if (missRate > MISS_RATE_CRITICAL) {
            // DD65: Alert on miss_rate > 30%
            LOGGER.warn("Pool critical: miss rate {}% (threshold 30%)", missRate * 100);
            emitTelemetry("arena.pool.critical_miss_rate", Map.of(
                "missRate", missRate,
                "recommendation", "Consider disabling pool"
            ));
            consecutiveHighMissChecks.set(0);
        } else if (missRate > MISS_RATE_WARN) {
            // DD65: Warn on miss_rate > 20%
            LOGGER.info("Pool warning: miss rate {}% (threshold 20%)", missRate * 100);
            consecutiveHighMissChecks.set(0);
        } else {
            consecutiveHighMissChecks.set(0);
        }

        emitTelemetry("arena.pool.metrics", Map.of(
            "hits", metrics.hits(),
            "misses", metrics.misses(),
            "missRate", missRate,
            "poolSize", metrics.poolSize(),
            "readyCount", metrics.readyCount()
        ));
    }

    /**
     * Cleans up unused arenas.
     * DD64: Unused = no assignment for 10 min AND state READY.
     */
    private void cleanupUnusedArenas() {
        Instant now = Instant.now();
        int cleaned = 0;

        for (Map.Entry<UUID, PooledArena> entry : pool.entrySet()) {
            PooledArena arena = entry.getValue();

            // DD64: Double-check before eviction
            if (arena.isUnused(now) && arena.canBeEvicted()) {
                pool.remove(entry.getKey());
                cleaned++;

                LOGGER.debug("Cleaned up unused arena: {}", entry.getKey());
                emitTelemetry("arena.pool.evicted", Map.of(
                    "arenaId", entry.getKey().toString(),
                    "templateId", arena.templateId(),
                    "ageMinutes", Duration.between(arena.createdAt(), now).toMinutes()
                ));
            }
        }

        if (cleaned > 0) {
            LOGGER.info("Cleaned up {} unused arenas from pool", cleaned);
        }
    }

    /**
     * Evaluates if pool is justified based on telemetry data.
     * DD63: Decision criteria for enabling pool.
     *
     * @param buildTimeP95Ms           P95 build time in milliseconds
     * @param questRatePerMinPerPlayer quest rate per minute per player
     * @param peakPlayerCount          peak player count
     * @return justification result
     */
    public PoolJustification evaluateJustification(
            long buildTimeP95Ms,
            double questRatePerMinPerPlayer,
            int peakPlayerCount) {
        PoolJustification justification = PoolJustification.evaluate(
            buildTimeP95Ms, questRatePerMinPerPlayer, peakPlayerCount
        );

        LOGGER.info("Pool justification evaluated: justified={}, buildP95={}ms, questRate={}/min/player",
            justification.justified(), buildTimeP95Ms, questRatePerMinPerPlayer);

        emitTelemetry("arena.pool.justification_evaluated", Map.of(
            "justified", justification.justified(),
            "buildTimeP95Ms", buildTimeP95Ms,
            "questRatePerMinPerPlayer", questRatePerMinPerPlayer,
            "peakPlayerCount", peakPlayerCount
        ));

        return justification;
    }

    /**
     * Generates DuckDB query for pool justification data.
     * DD63: Required data before implementation.
     */
    public String generateJustificationQuery(long windowStartEpoch, long windowEndEpoch) {
        return """
            SELECT
                percentile_cont(0.95) WITHIN GROUP (ORDER BY build_ms) as build_time_p95,
                COUNT(*) / ((%d - %d) / 60.0) / NULLIF(MAX(concurrent_players), 1) as quest_rate_per_min_per_player,
                MAX(concurrent_players) as peak_player_count
            FROM arena_builds
            WHERE created_at >= %d
              AND created_at < %d
              AND result = 'success'
            """.formatted(windowEndEpoch, windowStartEpoch, windowStartEpoch, windowEndEpoch);
    }

    /**
     * Shuts down the pool manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pool.clear();
    }

    /**
     * Emits telemetry event.
     * Override in subclass for actual telemetry implementation.
     */
    protected void emitTelemetry(String eventName, Map<String, Object> data) {
        // Default implementation - override for actual telemetry
        LOGGER.trace("Telemetry: {} - {}", eventName, data);
    }

    /**
     * Gets the current pool size.
     */
    public int getPoolSize() {
        return pool.size();
    }

    /**
     * Alias for compatibility with health checks.
     */
    public int getCurrentPoolSize() {
        return getPoolSize();
    }

    /**
     * Gets the pool configuration.
     */
    public PoolConfig getConfig() {
        return config;
    }
}

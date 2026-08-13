package com.devmod.telemetry.duckdb.aggregation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregator.AggregatedEvent;
import com.devmod.telemetry.duckdb.lvc.TelemetryLVC;

/**
 * Registry and lifecycle manager for per-player aggregators.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create aggregators on player join</li>
 *   <li>Destroy aggregators on player leave (with flush)</li>
 *   <li>Periodic flush of all aggregators</li>
 *   <li>Route aggregated events to DuckDB</li>
 * </ul>
 *
 * <p>Integration points:
 * <ul>
 *   <li>CommonModEvents.onPlayerLoggedIn → onPlayerJoin()</li>
 *   <li>EnduranceEventHandler.onPlayerLogout → onPlayerLeave()</li>
 *   <li>EnduranceQuestManager → onQuestStart(), onQuestEnd()</li>
 * </ul>
 */
public class TelemetryAggregatorRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryAggregatorRegistry.class);

    /** Singleton instance */
    public static final TelemetryAggregatorRegistry INSTANCE = new TelemetryAggregatorRegistry();

    // ============================================
    // STATE
    // ============================================

    /** Per-player aggregators */
    private final ConcurrentHashMap<UUID, TelemetryAggregator> aggregators = new ConcurrentHashMap<>();

    /** Reference to LVC */
    private final TelemetryLVC lvc = TelemetryLVC.INSTANCE;

    /** Scheduler for periodic flush */
    private final ScheduledExecutorService scheduler;

    /** Periodic flush task */
    @Nullable
    private ScheduledFuture<?> flushTask;

    /** Running flag */
    private volatile boolean running = false;

    // ============================================
    // STATISTICS
    // ============================================

    private final AtomicLong totalEventsAggregated = new AtomicLong(0);
    private final AtomicLong totalEventsFlushed = new AtomicLong(0);
    private final AtomicLong totalFlushCycles = new AtomicLong(0);

    private TelemetryAggregatorRegistry() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Telemetry-Aggregator-Flush");
            t.setDaemon(true);
            return t;
        });
    }

    // ============================================
    // LIFECYCLE
    // ============================================

    /**
     * Start the registry (called on server start).
     */
    public void start() {
        if (running) return;

        if (!AggregationConfig.AGGREGATION_ENABLED) {
            LOGGER.info("[AggregatorRegistry] Aggregation is disabled");
            return;
        }

        running = true;

        // Schedule periodic flush
        flushTask = scheduler.scheduleAtFixedRate(
            this::periodicFlush,
            AggregationConfig.PERIODIC_FLUSH_INTERVAL_MS,
            AggregationConfig.PERIODIC_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("[AggregatorRegistry] Started with {}ms flush interval",
            AggregationConfig.PERIODIC_FLUSH_INTERVAL_MS);
    }

    /**
     * Stop the registry (called on server shutdown).
     */
    public void shutdown() {
        if (!running) return;

        running = false;

        // Cancel periodic task
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }

        // Force flush all aggregators
        LOGGER.info("[AggregatorRegistry] Shutting down, flushing {} aggregators...",
            aggregators.size());

        List<AggregatedEvent> allEvents = new ArrayList<>();
        for (TelemetryAggregator agg : aggregators.values()) {
            allEvents.addAll(agg.forceFlush());
            agg.shutdown();
        }

        // Write all pending events
        writeAggregatedEvents(allEvents);

        aggregators.clear();
        lvc.clear();

        LOGGER.info("[AggregatorRegistry] Shutdown complete, flushed {} events",
            allEvents.size());
    }

    // ============================================
    // PLAYER LIFECYCLE HOOKS
    // ============================================

    /**
     * Called when a player joins the server.
     * Creates aggregator and LVC entry.
     *
     * @param playerId the player's UUID
     */
    public void onPlayerJoin(UUID playerId) {
        if (!AggregationConfig.AGGREGATION_ENABLED) return;

        // Create LVC entry
        lvc.createEntry(playerId);

        // Create aggregator
        aggregators.computeIfAbsent(playerId, TelemetryAggregator::new);

        LOGGER.debug("[AggregatorRegistry] Player {} joined, created aggregator", playerId);
    }

    /**
     * Called when a player leaves the server.
     * Flushes aggregator and cleans up LVC entry.
     *
     * @param playerId the player's UUID
     */
    public void onPlayerLeave(UUID playerId) {
        if (!AggregationConfig.AGGREGATION_ENABLED) return;

        // Remove and flush aggregator
        TelemetryAggregator agg = aggregators.remove(playerId);
        if (agg != null) {
            List<AggregatedEvent> events = agg.forceFlush();
            agg.shutdown();
            writeAggregatedEvents(events);

            LOGGER.debug("[AggregatorRegistry] Player {} left, flushed {} events",
                playerId, events.size());
        }

        // Remove LVC entry
        lvc.removeEntry(playerId);
    }

    /**
     * Called when a player starts a quest.
     *
     * @param playerId player UUID
     * @param questId quest UUID
     * @param templateId arena template ID
     * @param templateVersion template version
     */
    public void onQuestStart(UUID playerId, UUID questId,
                             String templateId, Integer templateVersion) {
        if (!AggregationConfig.AGGREGATION_ENABLED) return;

        TelemetryAggregator agg = getAggregator(playerId);
        if (agg != null) {
            agg.setQuestContext(questId, templateId, templateVersion);
        }
    }

    /**
     * Called when a player ends a quest.
     *
     * @param playerId player UUID
     * @param questId quest UUID
     */
    public void onQuestEnd(UUID playerId, UUID questId) {
        if (!AggregationConfig.AGGREGATION_ENABLED) return;

        TelemetryAggregator agg = getAggregator(playerId);
        if (agg != null) {
            writeAggregatedEvents(agg.clearQuestContext());
        }
    }

    // ============================================
    // AGGREGATOR ACCESS
    // ============================================

    /**
     * Get aggregator for a player (may return null).
     */
    @Nullable
    public TelemetryAggregator getAggregator(UUID playerId) {
        return aggregators.get(playerId);
    }

    /**
     * Get or create aggregator for a player.
     */
    public TelemetryAggregator getOrCreateAggregator(UUID playerId) {
        return aggregators.computeIfAbsent(playerId, id -> {
            lvc.createEntry(id);
            return new TelemetryAggregator(id);
        });
    }

    /**
     * Check if aggregator exists for a player.
     */
    public boolean hasAggregator(UUID playerId) {
        return aggregators.containsKey(playerId);
    }

    // ============================================
    // PERIODIC FLUSH
    // ============================================

    /**
     * Periodic flush of all aggregators.
     * Called by scheduler every PERIODIC_FLUSH_INTERVAL_MS.
     */
    private void periodicFlush() {
        if (!running) return;

        // Skip if DuckDB is circuit-broken
        DuckDBTelemetryService telemetry = DuckDBTelemetryService.INSTANCE;
        if (telemetry != null && telemetry.isCircuitBroken()) {
            LOGGER.debug("[AggregatorRegistry] Skipping flush - circuit breaker active");
            return;
        }

        List<AggregatedEvent> allEvents = new ArrayList<>();

        for (TelemetryAggregator agg : aggregators.values()) {
            if (agg.isActive()) {
                allEvents.addAll(agg.flush());
            }
        }

        if (!allEvents.isEmpty()) {
            writeAggregatedEvents(allEvents);
            totalEventsFlushed.addAndGet(allEvents.size());

            if (AggregationConfig.LOG_AGGREGATION_STATS) {
                LOGGER.info("[AggregatorRegistry] Periodic flush: {} events from {} players",
                    allEvents.size(), aggregators.size());
            }
        }

        // Prune stale LVC entries
        lvc.pruneStaleEntries();

        totalFlushCycles.incrementAndGet();
    }

    /**
     * Write aggregated events to DuckDB.
     */
    private void writeAggregatedEvents(List<AggregatedEvent> events) {
        if (events.isEmpty()) return;

        DuckDBTelemetryService telemetry = DuckDBTelemetryService.INSTANCE;
        if (telemetry == null || !telemetry.isEnabled()) {
            LOGGER.debug("[AggregatorRegistry] Telemetry not available, dropping {} events",
                events.size());
            return;
        }

        for (AggregatedEvent event : events) {
            try {
                switch (event.type()) {
                    case COMBAT -> telemetry.writeAggregatedCombat(event.playerId(), event.asCombat());
                    case ABILITY -> telemetry.writeAggregatedAbility(event.playerId(), event.asAbility());
                    case HEATMAP -> telemetry.writeAggregatedHeatmap(event.playerId(), event.asHeatmap());
                }
            } catch (Exception e) {
                LOGGER.warn("[AggregatorRegistry] Failed to write aggregated event: {}",
                    e.getMessage());
            }
        }
    }

    // ============================================
    // STATISTICS
    // ============================================

    /**
     * Get number of active aggregators.
     */
    public int getActiveAggregatorCount() {
        return aggregators.size();
    }

    /**
     * Get total events aggregated.
     */
    public long getTotalEventsAggregated() {
        return totalEventsAggregated.get();
    }

    /**
     * Get total events flushed.
     */
    public long getTotalEventsFlushed() {
        return totalEventsFlushed.get();
    }

    /**
     * Get total flush cycles.
     */
    public long getTotalFlushCycles() {
        return totalFlushCycles.get();
    }

    /**
     * Get estimated memory usage.
     */
    public long getEstimatedMemoryUsage() {
        // ~5KB per aggregator + LVC overhead
        return aggregators.size() * 5L * 1024L + lvc.getEstimatedMemoryUsage();
    }

    /**
     * Check if registry is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get registry statistics.
     */
    public RegistryStats getStats() {
        return new RegistryStats(
            aggregators.size(),
            totalEventsAggregated.get(),
            totalEventsFlushed.get(),
            totalFlushCycles.get(),
            getEstimatedMemoryUsage(),
            running
        );
    }

    /**
     * Registry statistics record.
     */
    public record RegistryStats(
        int activeAggregators,
        long totalEventsAggregated,
        long totalEventsFlushed,
        long totalFlushCycles,
        long estimatedMemoryBytes,
        boolean running
    ) {}
}

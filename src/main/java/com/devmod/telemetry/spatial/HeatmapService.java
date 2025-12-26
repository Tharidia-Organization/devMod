package com.devmod.telemetry.spatial;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;

import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
public class HeatmapService {
    public static final HeatmapService INSTANCE = new HeatmapService();
    private static final Logger LOGGER = LogUtils.getLogger();

    // Flush configuration
    private static final long FLUSH_INTERVAL_MS = 60_000; // 60 seconds
    private static final int MOVEMENT_THROTTLE_MS = 2000; // Max 1 movement per player per 2s

    // HARDENING: Memory cap per heatmap type (prevents OOM from unbounded growth)
    private static final int MAX_BUCKETS_PER_TYPE = 50_000;
    private static final long CAP_LOG_INTERVAL_MS = 60_000; // Rate-limit cap warnings
    private final AtomicLong lastCapWarningMs = new AtomicLong(0);

    // Last flush timestamp
    private final AtomicLong lastFlushMs = new AtomicLong(0);

    // Movement throttle per player (playerUUID -> lastRecordMs)
    private final Map<String, Long> movementThrottle = new ConcurrentHashMap<>();

    // Heatmap type whitelist for validation
    private static final Set<String> VALID_HEATMAP_TYPES = Set.of(
        "stuck", "aggro_drop", "kiting", "death", "movement",
        "camping", "choke_point", "invisible_collision", "parkour_fall"
    );

    // Heatmap by type
    private final Map<String, Map<BlockPos, Integer>> stuckHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> aggroDropHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> kitingHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> deathHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> movementHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> campingHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> chokePointHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> invisibleCollisionHeatmap = new ConcurrentHashMap<>();
    private final Map<String, Map<BlockPos, Integer>> parkourFallHeatmap = new ConcurrentHashMap<>();

    private HeatmapService() {}

    // ============================================
    // RECORD METHODS
    // ============================================

    public void recordStuck(String room, BlockPos pos) {
        increment(stuckHeatmap, room, pos);
    }

    public void recordAggroDrop(String room, BlockPos pos) {
        increment(aggroDropHeatmap, room, pos);
    }

    public void recordKiting(String room, BlockPos pos) {
        increment(kitingHeatmap, room, pos);
    }

    public void recordDeath(String room, BlockPos pos) {
        increment(deathHeatmap, room, pos);
    }

    /**
     * Record movement with throttle to prevent spam.
     * Max 1 movement per player per 2 seconds.
     *
     * @param room Room ID
     * @param pos Block position
     * @param playerId Player UUID string (for throttle key)
     */
    public void recordMovement(String room, BlockPos pos, String playerId) {
        long now = System.currentTimeMillis();
        Long lastRecord = movementThrottle.get(playerId);
        if (lastRecord != null && now - lastRecord < MOVEMENT_THROTTLE_MS) {
            return; // Throttled
        }
        movementThrottle.put(playerId, now);
        increment(movementHeatmap, room, pos);
    }

    /**
     * Legacy method without throttle (for backward compatibility).
     * @deprecated Use recordMovement(room, pos, playerId) instead
     */
    @Deprecated
    public void recordMovement(String room, BlockPos pos) {
        increment(movementHeatmap, room, pos);
    }

    public void recordCamping(String room, BlockPos pos) {
        increment(campingHeatmap, room, pos);
    }

    public void recordChokePoint(String room, BlockPos pos) {
        increment(chokePointHeatmap, room, pos);
    }

    public void recordInvisibleCollision(String room, BlockPos pos) {
        increment(invisibleCollisionHeatmap, room, pos);
    }

    public void recordParkourFall(String room, BlockPos pos) {
        increment(parkourFallHeatmap, room, pos);
    }

    // ============================================
    // GETTER METHODS (read-only views)
    // ============================================

    public Map<String, Map<BlockPos, Integer>> getStuckHeatmap() {
        return Collections.unmodifiableMap(stuckHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getAggroDropHeatmap() {
        return Collections.unmodifiableMap(aggroDropHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getKitingHeatmap() {
        return Collections.unmodifiableMap(kitingHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getDeathHeatmap() {
        return Collections.unmodifiableMap(deathHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getMovementHeatmap() {
        return Collections.unmodifiableMap(movementHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getCampingHeatmap() {
        return Collections.unmodifiableMap(campingHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getChokePointHeatmap() {
        return Collections.unmodifiableMap(chokePointHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getInvisibleCollisionHeatmap() {
        return Collections.unmodifiableMap(invisibleCollisionHeatmap);
    }

    public Map<String, Map<BlockPos, Integer>> getParkourFallHeatmap() {
        return Collections.unmodifiableMap(parkourFallHeatmap);
    }

    // ============================================
    // EXPORT METHODS
    // ============================================

    /**
     * Esporta una heatmap usando il consumer fornito.
     * @param heatmap La heatmap da esportare
     * @param lineConsumer Consumer che riceve (room, line) per ogni entry
     */
    public void exportHeatmap(Map<String, Map<BlockPos, Integer>> heatmap,
                              BiConsumer<String, String> lineConsumer) {
        heatmap.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = String.format(
                    "{\"room\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"count\":%d}",
                    escapeJson(room), pos.getX(), pos.getY(), pos.getZ(), count
                );
                lineConsumer.accept(room, line);
            });
        });
    }

    public void exportStuckHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(stuckHeatmap, lineConsumer);
    }

    public void exportAggroDropHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(aggroDropHeatmap, lineConsumer);
    }

    public void exportKitingHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(kitingHeatmap, lineConsumer);
    }

    public void exportDeathHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(deathHeatmap, lineConsumer);
    }

    public void exportMovementHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(movementHeatmap, lineConsumer);
    }

    public void exportCampingHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(campingHeatmap, lineConsumer);
    }

    public void exportChokePointHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(chokePointHeatmap, lineConsumer);
    }

    public void exportInvisibleCollisionHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(invisibleCollisionHeatmap, lineConsumer);
    }

    public void exportParkourFallHeatmap(BiConsumer<String, String> lineConsumer) {
        exportHeatmap(parkourFallHeatmap, lineConsumer);
    }

    // ============================================
    // CLEAR METHODS
    // ============================================

    public void clearAll() {
        stuckHeatmap.clear();
        aggroDropHeatmap.clear();
        kitingHeatmap.clear();
        deathHeatmap.clear();
        movementHeatmap.clear();
        campingHeatmap.clear();
        chokePointHeatmap.clear();
        invisibleCollisionHeatmap.clear();
        parkourFallHeatmap.clear();
    }

    public void clearRoom(String room) {
        stuckHeatmap.remove(room);
        aggroDropHeatmap.remove(room);
        kitingHeatmap.remove(room);
        deathHeatmap.remove(room);
        movementHeatmap.remove(room);
        campingHeatmap.remove(room);
        chokePointHeatmap.remove(room);
        invisibleCollisionHeatmap.remove(room);
        parkourFallHeatmap.remove(room);
    }

    // ============================================
    // DUCKDB FLUSH METHODS
    // ============================================

    /**
     * Tick method - called periodically to check if flush is needed.
     * Should be called from ServerTick at configurable interval.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFlushMs.get() >= FLUSH_INTERVAL_MS) {
            flushToDuckDB();
            lastFlushMs.set(now);
        }
    }

    /**
     * Flush all aggregated heatmap data to DuckDB.
     * Called periodically (every 60s) and on shutdown.
     *
     * Strategy: Aggregated flush - writes 1 row per (type, room, pos, count)
     * This prevents raw event spam while preserving spatial data.
     */
    public void flushToDuckDB() {
        if (!DuckDBTelemetryService.INSTANCE.isEnabled()) {
            LOGGER.debug("[HeatmapService] DuckDB disabled, skipping flush");
            return;
        }

        AtomicInteger totalFlushed = new AtomicInteger(0);

        // Flush each heatmap type
        totalFlushed.addAndGet(flushHeatmapType("stuck", stuckHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("aggro_drop", aggroDropHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("kiting", kitingHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("death", deathHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("movement", movementHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("camping", campingHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("choke_point", chokePointHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("invisible_collision", invisibleCollisionHeatmap));
        totalFlushed.addAndGet(flushHeatmapType("parkour_fall", parkourFallHeatmap));

        if (totalFlushed.get() > 0) {
            LOGGER.debug("[HeatmapService] Flushed {} heatmap buckets to DuckDB", totalFlushed.get());
        }
    }

    /**
     * Flush a single heatmap type to DuckDB.
     * Clears the in-memory data after flush.
     *
     * @param type Heatmap type (must be in VALID_HEATMAP_TYPES)
     * @param heatmap The heatmap data to flush
     * @return Number of buckets flushed
     */
    private int flushHeatmapType(String type, Map<String, Map<BlockPos, Integer>> heatmap) {
        if (!VALID_HEATMAP_TYPES.contains(type)) {
            LOGGER.warn("[HeatmapService] Invalid heatmap type: {}", type);
            return 0;
        }

        AtomicInteger flushed = new AtomicInteger(0);

        // Snapshot and clear to avoid concurrent modification
        heatmap.forEach((room, posMap) -> {
            // Create a snapshot of positions
            Map<BlockPos, Integer> snapshot = new ConcurrentHashMap<>(posMap);
            posMap.clear(); // Clear after snapshot

            snapshot.forEach((pos, count) -> {
                // Sanity check coordinates
                if (pos.getY() < -64 || pos.getY() > 320) {
                    LOGGER.debug("[HeatmapService] Skipping invalid Y coordinate: {}", pos.getY());
                    return;
                }

                // HARDENING: Wrap DuckDB write in try-catch to prevent tick crash
                try {
                    DuckDBTelemetryService.INSTANCE.logHeatmap(
                        type,
                        room,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        count
                    );
                    flushed.incrementAndGet();
                } catch (Exception e) {
                    // Log but don't crash - data is already cleared from memory
                    LOGGER.debug("[HeatmapService] Failed to write heatmap bucket: {}", e.getMessage());
                }
            });
        });

        return flushed.get();
    }

    /**
     * Force flush on shutdown - called from TelemetryEvents.onServerStopped()
     */
    public void shutdown() {
        LOGGER.info("[HeatmapService] Shutdown flush starting...");
        flushToDuckDB();
        LOGGER.info("[HeatmapService] Shutdown flush complete");
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    /**
     * Increment bucket count with memory cap protection.
     * HARDENING: Drops LOW priority events when approaching memory cap.
     * BlockPos uses integer coordinates (floor of double) ensuring deterministic bucket keys.
     */
    private void increment(Map<String, Map<BlockPos, Integer>> heatmap, String room, BlockPos pos) {
        // HARDENING: Memory cap check - count total buckets across all rooms
        int totalBuckets = heatmap.values().stream().mapToInt(Map::size).sum();
        if (totalBuckets >= MAX_BUCKETS_PER_TYPE) {
            // Rate-limited warning
            long now = System.currentTimeMillis();
            if (now - lastCapWarningMs.get() > CAP_LOG_INTERVAL_MS) {
                lastCapWarningMs.set(now);
                LOGGER.warn("[HeatmapService] Memory cap reached ({} buckets), dropping new entries until flush", totalBuckets);
            }
            return; // Drop this event
        }

        heatmap.computeIfAbsent(room, k -> new ConcurrentHashMap<>())
               .merge(pos, 1, (a, b) -> a + b);
    }

    private String escapeJson(String s) {
        return com.devmod.telemetry.TelemetryJson.escape(s);
    }

    /**
     * Ottiene statistiche aggregate per una room.
     */
    public HeatmapStats getStatsForRoom(String room) {
        int stuckCount = countEntries(stuckHeatmap.get(room));
        int aggroDropCount = countEntries(aggroDropHeatmap.get(room));
        int kitingCount = countEntries(kitingHeatmap.get(room));
        int deathCount = countEntries(deathHeatmap.get(room));
        int movementCount = countEntries(movementHeatmap.get(room));
        int campingCount = countEntries(campingHeatmap.get(room));
        int chokePointCount = countEntries(chokePointHeatmap.get(room));
        int invisibleCollisionCount = countEntries(invisibleCollisionHeatmap.get(room));
        int parkourFallCount = countEntries(parkourFallHeatmap.get(room));

        return new HeatmapStats(room, stuckCount, aggroDropCount, kitingCount,
                               deathCount, movementCount, campingCount,
                               chokePointCount, invisibleCollisionCount, parkourFallCount);
    }

    private int countEntries(Map<BlockPos, Integer> posMap) {
        if (posMap == null) return 0;
        return posMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Record per le statistiche aggregate di una room.
     */
    public record HeatmapStats(
        String room,
        int stuckEvents,
        int aggroDropEvents,
        int kitingEvents,
        int deathEvents,
        int movementSamples,
        int campingEvents,
        int chokePointEvents,
        int invisibleCollisionEvents,
        int parkourFallEvents
    ) {
        @Override
        public String toString() {
            return String.format(
                "Room: %s | Stuck: %d | AggroDrop: %d | Kiting: %d | Deaths: %d | Movement: %d | Camping: %d | ChokePoints: %d | InvisCollisions: %d | ParkourFalls: %d",
                room, stuckEvents, aggroDropEvents, kitingEvents, deathEvents, movementSamples, campingEvents,
                chokePointEvents, invisibleCollisionEvents, parkourFallEvents
            );
        }
    }
}

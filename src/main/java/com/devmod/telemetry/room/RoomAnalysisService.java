package com.devmod.telemetry.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
public class RoomAnalysisService {
    public static final RoomAnalysisService INSTANCE = new RoomAnalysisService();

    // Choke point detection (quit positions)
    private final Map<String, Map<BlockPos, Integer>> chokePointQuits = new ConcurrentHashMap<>();

    // Invisible collision detection
    private final Map<String, Map<BlockPos, Integer>> invisibleCollisions = new ConcurrentHashMap<>();

    // Parkour fall detection
    private final Map<String, Map<BlockPos, Integer>> parkourFalls = new ConcurrentHashMap<>();

    // Entity density per room
    private final Map<String, EntityDensityTracker> entityDensity = new ConcurrentHashMap<>();

    private RoomAnalysisService() {}

    // ===== Choke Point Tracking =====

    /**
     * Record a player quit position for choke point analysis.
     *
     * @param roomId Room identifier
     * @param pos Block position
     */
    public void recordPlayerQuit(String roomId, BlockPos pos) {
        chokePointQuits.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
            .merge(pos, 1, (a, b) -> a + b);
    }

    /**
     * Get quit positions for a room.
     *
     * @param roomId Room identifier
     * @return Map of positions to quit counts
     */
    public Map<BlockPos, Integer> getQuitPositions(String roomId) {
        return chokePointQuits.getOrDefault(roomId, Map.of());
    }

    /**
     * Export choke point heatmap data.
     *
     * @param lineConsumer Consumer for each line (room, jsonLine)
     */
    public void exportChokePointHeatmap(BiConsumer<String, String> lineConsumer) {
        chokePointQuits.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + escape(room) + "\","
                    + "\"x\":" + pos.getX() + ","
                    + "\"y\":" + pos.getY() + ","
                    + "\"z\":" + pos.getZ() + ","
                    + "\"count\":" + count + "}";
                lineConsumer.accept(room, line);
            });
        });
    }

    // ===== Invisible Collision Detection =====

    /**
     * Record an invisible collision (player hits invisible barrier).
     *
     * @param roomId Room identifier
     * @param pos Block position
     */
    public void recordInvisibleCollision(String roomId, BlockPos pos) {
        invisibleCollisions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
            .merge(pos, 1, (a, b) -> a + b);
    }

    /**
     * Get collision positions for a room.
     *
     * @param roomId Room identifier
     * @return Map of positions to collision counts
     */
    public Map<BlockPos, Integer> getCollisionPositions(String roomId) {
        return invisibleCollisions.getOrDefault(roomId, Map.of());
    }

    /**
     * Export invisible collision heatmap data.
     *
     * @param lineConsumer Consumer for each line (room, jsonLine)
     */
    public void exportInvisibleCollisionHeatmap(BiConsumer<String, String> lineConsumer) {
        invisibleCollisions.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + escape(room) + "\","
                    + "\"x\":" + pos.getX() + ","
                    + "\"y\":" + pos.getY() + ","
                    + "\"z\":" + pos.getZ() + ","
                    + "\"count\":" + count + "}";
                lineConsumer.accept(room, line);
            });
        });
    }

    // ===== Parkour Fall Tracking =====

    /**
     * Record a parkour fall position.
     *
     * @param roomId Room identifier
     * @param pos Fall position
     */
    public void recordParkourFall(String roomId, BlockPos pos) {
        parkourFalls.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
            .merge(pos, 1, (a, b) -> a + b);
    }

    /**
     * Get fall positions for a room.
     *
     * @param roomId Room identifier
     * @return Map of positions to fall counts
     */
    public Map<BlockPos, Integer> getFallPositions(String roomId) {
        return parkourFalls.getOrDefault(roomId, Map.of());
    }

    /**
     * Export parkour fall heatmap data.
     *
     * @param lineConsumer Consumer for each line (room, jsonLine)
     */
    public void exportParkourFallHeatmap(BiConsumer<String, String> lineConsumer) {
        parkourFalls.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + escape(room) + "\","
                    + "\"x\":" + pos.getX() + ","
                    + "\"y\":" + pos.getY() + ","
                    + "\"z\":" + pos.getZ() + ","
                    + "\"count\":" + count + "}";
                lineConsumer.accept(room, line);
            });
        });
    }

    // ===== Entity Density Tracking =====

    /**
     * Update entity density for a room.
     *
     * @param roomId Room identifier
     * @param count Current entity count
     * @param typeBreakdown Map of entity types to counts
     */
    public void updateEntityDensity(String roomId, int count, Map<String, Integer> typeBreakdown) {
        EntityDensityTracker tracker = entityDensity.computeIfAbsent(
            roomId, k -> new EntityDensityTracker()
        );
        tracker.update(count, typeBreakdown, System.currentTimeMillis());
    }

    /**
     * Get entity density report for all rooms.
     *
     * @return List of density report strings
     */
    public List<String> getEntityDensityReport() {
        List<String> report = new ArrayList<>();
        entityDensity.forEach((room, tracker) -> {
            StringBuilder types = new StringBuilder();
            tracker.entityTypeCount.forEach((type, count) ->
                types.append(types.length() > 0 ? ", " : "").append(type).append("=").append(count)
            );
            report.add(String.format("%s: current=%d peak=%d lastUpdate=%dms [%s]",
                room, tracker.currentEntityCount, tracker.peakEntityCount,
                System.currentTimeMillis() - tracker.getLastUpdateMs(), types));
        });
        return report;
    }

    /**
     * Get peak entity count for a room.
     *
     * @param roomId Room identifier
     * @return Peak entity count
     */
    public int getPeakEntityCount(String roomId) {
        EntityDensityTracker tracker = entityDensity.get(roomId);
        return tracker != null ? tracker.peakEntityCount : 0;
    }

    /**
     * Get current entity count for a room.
     *
     * @param roomId Room identifier
     * @return Current entity count
     */
    public int getCurrentEntityCount(String roomId) {
        EntityDensityTracker tracker = entityDensity.get(roomId);
        return tracker != null ? tracker.currentEntityCount : 0;
    }

    // ===== Cleanup =====

    /**
     * Clear all data for a room.
     *
     * @param roomId Room identifier
     */
    public void clearRoom(String roomId) {
        chokePointQuits.remove(roomId);
        invisibleCollisions.remove(roomId);
        parkourFalls.remove(roomId);
        entityDensity.remove(roomId);
    }

    /**
     * Clear all trackers (for reload).
     */
    public void clear() {
        chokePointQuits.clear();
        invisibleCollisions.clear();
        parkourFalls.clear();
        entityDensity.clear();
    }

    // ===== Utilities =====

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ===== Internal Classes =====

    /**
     * Internal entity density tracker.
     */
    private static final class EntityDensityTracker {
        int peakEntityCount = 0;
        int currentEntityCount = 0;
        long lastUpdateMs = 0;
        final Map<String, Integer> entityTypeCount = new ConcurrentHashMap<>();

        /**
         * Gets the last update timestamp for monitoring staleness.
         */
        public long getLastUpdateMs() {
            return lastUpdateMs;
        }

        void update(int count, Map<String, Integer> typeBreakdown, long nowMs) {
            currentEntityCount = count;
            if (count > peakEntityCount) {
                peakEntityCount = count;
            }
            entityTypeCount.clear();
            entityTypeCount.putAll(typeBreakdown);
            lastUpdateMs = nowMs;
        }
    }
}

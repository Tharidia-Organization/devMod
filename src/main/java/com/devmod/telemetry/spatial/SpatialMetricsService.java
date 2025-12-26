package com.devmod.telemetry.spatial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.telemetry.RoomDefinition;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.room.RoomService;
public class SpatialMetricsService {
    public static final SpatialMetricsService INSTANCE = new SpatialMetricsService();

    // M42: Choke point detection (quit positions)
    private final Map<String, Map<BlockPos, Integer>> chokePointQuits = new ConcurrentHashMap<>();

    // M52: Entity density per room
    private final Map<String, EntityDensityTracker> entityDensity = new ConcurrentHashMap<>();

    // M13: Invisible collision detection
    private final Map<String, Map<BlockPos, Integer>> invisibleCollisions = new ConcurrentHashMap<>();

    // M14: Parkour fall points
    private final Map<String, Map<BlockPos, Integer>> parkourFalls = new ConcurrentHashMap<>();

    private SpatialMetricsService() {}

    // ===== M42: Choke Point Detection =====

    /**
     * Record a player quit position for choke point analysis.
     *
     * @param roomId Room identifier
     * @param pos Player position when quitting
     * @return QuitRecord for telemetry logging
     */
    public QuitRecord recordQuit(String roomId, BlockPos pos) {
        chokePointQuits.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(pos, 1, (a, b) -> a + b);
        return new QuitRecord(roomId, pos);
    }

    /**
     * Get quit count at a specific position.
     *
     * @param roomId Room identifier
     * @param pos Position
     * @return Number of quits at position
     */
    public int getQuitCount(String roomId, BlockPos pos) {
        Map<BlockPos, Integer> roomQuits = chokePointQuits.get(roomId);
        return roomQuits != null ? roomQuits.getOrDefault(pos, 0) : 0;
    }

    /**
     * Export choke point heatmap.
     *
     * @param consumer Receives (room, jsonLine) pairs
     */
    public void exportChokePointHeatmap(BiConsumer<String, String> consumer) {
        chokePointQuits.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + TelemetryJson.escape(room) + "\","
                        + "\"x\":" + pos.getX() + ","
                        + "\"y\":" + pos.getY() + ","
                        + "\"z\":" + pos.getZ() + ","
                        + "\"count\":" + count + "}";
                consumer.accept(room, line);
            });
        });
    }

    // ===== M52: Entity Density Tracking =====

    /**
     * Update entity density for a room.
     *
     * @param level Server level
     * @param roomId Room identifier
     */
    public void updateEntityDensity(ServerLevel level, String roomId) {
        RoomDefinition room = RoomService.INSTANCE.findRoomById(roomId).orElse(null);
        if (room == null) return;

        Map<String, Integer> typeBreakdown = new HashMap<>();
        int totalCount = 0;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living) {
                if (room.contains(level, living.blockPosition())) {
                    totalCount++;
                    String type = entity.getType().toShortString();
                    typeBreakdown.merge(type, 1, (a, b) -> a + b);
                }
            }
        }

        EntityDensityTracker tracker = entityDensity.computeIfAbsent(roomId, k -> new EntityDensityTracker());
        tracker.update(totalCount, typeBreakdown, System.currentTimeMillis());
    }

    /**
     * Get entity density info for a room.
     *
     * @param roomId Room identifier
     * @return Optional density info
     */
    public EntityDensityInfo getDensityInfo(String roomId) {
        EntityDensityTracker tracker = entityDensity.get(roomId);
        if (tracker == null) return null;
        return new EntityDensityInfo(
            roomId, tracker.currentEntityCount, tracker.peakEntityCount,
            new HashMap<>(tracker.entityTypeCount), tracker.isStale()
        );
    }

    /**
     * Get entity density report for all rooms.
     *
     * @return List of formatted density reports
     */
    public List<String> getEntityDensityReport() {
        List<String> report = new ArrayList<>();
        entityDensity.forEach((room, tracker) -> {
            StringBuilder types = new StringBuilder();
            tracker.entityTypeCount.forEach((type, count) ->
                    types.append(types.length() > 0 ? ", " : "").append(type).append("=").append(count));
            report.add(String.format("%s: current=%d peak=%d [%s]",
                    room, tracker.currentEntityCount, tracker.peakEntityCount, types));
        });
        return report;
    }

    // ===== M13: Invisible Collision Detection =====

    /**
     * Record an invisible collision event.
     *
     * @param roomId Room identifier
     * @param pos Collision position
     * @return CollisionRecord for telemetry logging
     */
    public CollisionRecord recordInvisibleCollision(String roomId, BlockPos pos) {
        invisibleCollisions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(pos, 1, (a, b) -> a + b);
        return new CollisionRecord(roomId, pos, getInvisibleCollisionCount(roomId, pos));
    }

    /**
     * Get collision count at a specific position.
     *
     * @param roomId Room identifier
     * @param pos Position
     * @return Number of collisions at position
     */
    public int getInvisibleCollisionCount(String roomId, BlockPos pos) {
        Map<BlockPos, Integer> roomCollisions = invisibleCollisions.get(roomId);
        return roomCollisions != null ? roomCollisions.getOrDefault(pos, 0) : 0;
    }

    /**
     * Export invisible collision heatmap.
     *
     * @param consumer Receives (room, jsonLine) pairs
     */
    public void exportInvisibleCollisionHeatmap(BiConsumer<String, String> consumer) {
        invisibleCollisions.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + TelemetryJson.escape(room) + "\","
                        + "\"x\":" + pos.getX() + ","
                        + "\"y\":" + pos.getY() + ","
                        + "\"z\":" + pos.getZ() + ","
                        + "\"count\":" + count + "}";
                consumer.accept(room, line);
            });
        });
    }

    // ===== M14: Parkour Fall Detection =====

    /**
     * Record a parkour fall event.
     *
     * @param roomId Room identifier
     * @param pos Fall position
     * @param fallDistance Distance fallen
     * @return FallRecord for telemetry logging
     */
    public FallRecord recordParkourFall(String roomId, BlockPos pos, float fallDistance) {
        parkourFalls.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .merge(pos, 1, (a, b) -> a + b);
        return new FallRecord(roomId, pos, fallDistance, getParkourFallCount(roomId, pos));
    }

    /**
     * Get fall count at a specific position.
     *
     * @param roomId Room identifier
     * @param pos Position
     * @return Number of falls at position
     */
    public int getParkourFallCount(String roomId, BlockPos pos) {
        Map<BlockPos, Integer> roomFalls = parkourFalls.get(roomId);
        return roomFalls != null ? roomFalls.getOrDefault(pos, 0) : 0;
    }

    /**
     * Export parkour fall heatmap.
     *
     * @param consumer Receives (room, jsonLine) pairs
     */
    public void exportParkourFallHeatmap(BiConsumer<String, String> consumer) {
        parkourFalls.forEach((room, posMap) -> {
            posMap.forEach((pos, count) -> {
                String line = "{\"room\":\"" + TelemetryJson.escape(room) + "\","
                        + "\"x\":" + pos.getX() + ","
                        + "\"y\":" + pos.getY() + ","
                        + "\"z\":" + pos.getZ() + ","
                        + "\"count\":" + count + "}";
                consumer.accept(room, line);
            });
        });
    }

    // ===== Cleanup =====

    /**
     * Clear all tracked data (for reload).
     */
    public void clear() {
        chokePointQuits.clear();
        entityDensity.clear();
        invisibleCollisions.clear();
        parkourFalls.clear();
    }

    // ===== Data Classes =====

    /**
     * Record of a player quit event.
     */
    public record QuitRecord(String roomId, BlockPos pos) {
        public String toJson(String playerName) {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }

    /**
     * Record of an invisible collision event.
     */
    public record CollisionRecord(String roomId, BlockPos pos, int totalCount) {
        public String toJson(String playerName) {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"invisible_collision\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }

    /**
     * Record of a parkour fall event.
     */
    public record FallRecord(String roomId, BlockPos pos, float fallDistance, int totalCount) {
        public String toJson(String playerName) {
            return "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"parkour_fall\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"fallDistance\":" + fallDistance + ","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]}";
        }
    }

    /**
     * Entity density information for a room.
     */
    public record EntityDensityInfo(
        String roomId,
        int currentCount,
        int peakCount,
        Map<String, Integer> typeBreakdown,
        boolean stale
    ) {}

    // ===== Internal Classes =====

    /**
     * Internal entity density tracker.
     */
    private static final class EntityDensityTracker {
        private static final long STALE_THRESHOLD_MS = 10_000; // 10 seconds

        int peakEntityCount = 0;
        int currentEntityCount = 0;
        long lastUpdateMs = 0;
        final Map<String, Integer> entityTypeCount = new ConcurrentHashMap<>();

        void update(int count, Map<String, Integer> typeBreakdown, long nowMs) {
            currentEntityCount = count;
            if (count > peakEntityCount) {
                peakEntityCount = count;
            }
            entityTypeCount.clear();
            entityTypeCount.putAll(typeBreakdown);
            lastUpdateMs = nowMs;
        }

        boolean isStale() {
            return lastUpdateMs > 0 && System.currentTimeMillis() - lastUpdateMs > STALE_THRESHOLD_MS;
        }
    }
}

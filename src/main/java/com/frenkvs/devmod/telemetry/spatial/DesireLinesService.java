package com.frenkvs.devmod.telemetry.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VOXEL-LAB M10: Movement Desire Lines
 *
 * Tracks movement patterns to identify "desire lines" - the paths players
 * naturally take through a space, which may differ from intended paths.
 *
 * Features:
 * - Tracks movement segments (from A to B)
 * - Aggregates into paths by frequency
 * - Identifies most common routes per room
 * - Detects shortcuts and unexpected paths
 *
 * This data helps level designers understand actual player flow vs designed flow.
 */
public class DesireLinesService {
    public static final DesireLinesService INSTANCE = new DesireLinesService();

    // Movement segments: from -> to -> count
    // Key format: "roomId:fromX,fromY,fromZ"
    private final Map<String, Map<BlockPos, Integer>> movementSegments = new ConcurrentHashMap<>();

    // Player last positions (for tracking segments)
    private final Map<UUID, PositionHistory> playerHistories = new ConcurrentHashMap<>();

    // Aggregated path data per room
    private final Map<String, PathAnalysis> roomPathAnalysis = new ConcurrentHashMap<>();

    // Configuration
    private static final int HISTORY_SIZE = 10; // Positions to remember per player
    private static final double SEGMENT_MIN_DISTANCE = 2.0; // Minimum distance for a segment
    private static final int GRID_SIZE = 4; // Quantize positions to 4-block grid

    private DesireLinesService() {}

    /**
     * Records a player movement for desire line analysis.
     *
     * @param playerId Player UUID
     * @param roomId Current room ID
     * @param position Current position
     */
    public void recordMovement(UUID playerId, String roomId, Vec3 position) {
        PositionHistory history = playerHistories.computeIfAbsent(playerId,
            k -> new PositionHistory(HISTORY_SIZE));

        // Quantize position to grid
        BlockPos gridPos = quantizeToGrid(position);

        // Get previous position
        BlockPos lastPos = history.getLastPosition();
        String lastRoom = history.getLastRoom();

        // Add to history
        history.addPosition(gridPos, roomId);

        // Only record segment if in same room and moved significant distance
        if (lastPos != null && roomId.equals(lastRoom)) {
            double distance = Math.sqrt(Objects.requireNonNull(lastPos).distSqr(Objects.requireNonNull(gridPos)));
            if (distance >= SEGMENT_MIN_DISTANCE) {
                recordSegment(roomId, lastPos, gridPos);
            }
        }
    }

    /**
     * Records a movement segment from one position to another.
     */
    private void recordSegment(String roomId, BlockPos from, BlockPos to) {
        String key = roomId + ":" + from.getX() + "," + from.getY() + "," + from.getZ();

        movementSegments.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
            .merge(to, 1, (a, b) -> a + b);
    }

    /**
     * Quantizes a position to the grid for aggregation.
     */
    private BlockPos quantizeToGrid(Vec3 position) {
        int x = (int) Math.floor(position.x / GRID_SIZE) * GRID_SIZE;
        int y = (int) Math.floor(position.y / GRID_SIZE) * GRID_SIZE;
        int z = (int) Math.floor(position.z / GRID_SIZE) * GRID_SIZE;
        return new BlockPos(x, y, z);
    }

    /**
     * Analyzes movement data to identify desire lines for a room.
     * Call periodically (e.g., every minute) to update analysis.
     */
    public void analyzeRoom(String roomId) {
        Map<BlockPos, Map<BlockPos, Integer>> roomSegments = new HashMap<>();

        // Collect all segments for this room
        String prefix = roomId + ":";
        for (Map.Entry<String, Map<BlockPos, Integer>> entry : movementSegments.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String[] parts = entry.getKey().substring(prefix.length()).split(",");
                if (parts.length == 3) {
                    try {
                        BlockPos from = new BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                        );
                        roomSegments.put(from, new HashMap<>(entry.getValue()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (roomSegments.isEmpty()) {
            return;
        }

        // Find hotspots (positions with most traffic)
        Map<BlockPos, Integer> positionTraffic = new HashMap<>();
        int totalSegments = 0;

        for (Map.Entry<BlockPos, Map<BlockPos, Integer>> fromEntry : roomSegments.entrySet()) {
            int fromCount = fromEntry.getValue().values().stream().mapToInt(i -> i).sum();
            positionTraffic.merge(fromEntry.getKey(), fromCount, (a, b) -> a + b);
            totalSegments += fromCount;

            for (Map.Entry<BlockPos, Integer> toEntry : fromEntry.getValue().entrySet()) {
                positionTraffic.merge(toEntry.getKey(), toEntry.getValue(), (a, b) -> a + b);
            }
        }

        // Find top paths (highest frequency segments)
        List<PathSegment> topPaths = new ArrayList<>();
        for (Map.Entry<BlockPos, Map<BlockPos, Integer>> fromEntry : roomSegments.entrySet()) {
            for (Map.Entry<BlockPos, Integer> toEntry : fromEntry.getValue().entrySet()) {
                topPaths.add(new PathSegment(fromEntry.getKey(), toEntry.getKey(), toEntry.getValue()));
            }
        }
        topPaths.sort(Comparator.comparingInt(PathSegment::count).reversed());

        // Find hotspots (top 10 busiest positions)
        List<Map.Entry<BlockPos, Integer>> hotspots = new ArrayList<>(positionTraffic.entrySet());
        hotspots.sort(Comparator.comparingInt(Map.Entry<BlockPos, Integer>::getValue).reversed());

        PathAnalysis analysis = new PathAnalysis(
            roomId,
            totalSegments,
            topPaths.size() > 20 ? topPaths.subList(0, 20) : topPaths,
            hotspots.size() > 10 ? hotspots.subList(0, 10).stream()
                .map(e -> new Hotspot(e.getKey(), e.getValue())).toList()
                : hotspots.stream().map(e -> new Hotspot(e.getKey(), e.getValue())).toList()
        );

        roomPathAnalysis.put(roomId, analysis);
    }

    /**
     * Gets the path analysis for a room.
     */
    public Optional<PathAnalysis> getPathAnalysis(String roomId) {
        return Optional.ofNullable(roomPathAnalysis.get(roomId));
    }

    /**
     * Gets the top desire lines (most traveled paths) for a room.
     */
    public List<PathSegment> getTopDesireLines(String roomId, int limit) {
        PathAnalysis analysis = roomPathAnalysis.get(roomId);
        if (analysis == null) {
            return List.of();
        }
        List<PathSegment> paths = analysis.topPaths();
        return paths.size() > limit ? paths.subList(0, limit) : paths;
    }

    /**
     * Gets all movement data for export.
     */
    public Map<String, Map<BlockPos, Integer>> getAllSegments() {
        return Map.copyOf(movementSegments);
    }

    /**
     * Clears all data.
     */
    public void clear() {
        movementSegments.clear();
        playerHistories.clear();
        roomPathAnalysis.clear();
    }

    /**
     * Clears data for a specific room.
     */
    public void clearRoom(String roomId) {
        String prefix = roomId + ":";
        movementSegments.keySet().removeIf(key -> key.startsWith(prefix));
        roomPathAnalysis.remove(roomId);
    }

    /**
     * Gets summary for all rooms.
     */
    public List<String> getSummaries() {
        List<String> summaries = new ArrayList<>();
        for (PathAnalysis analysis : roomPathAnalysis.values()) {
            summaries.add(String.format(
                "Room '%s': %d segments, %d hotspots, top path: %s",
                analysis.roomId(),
                analysis.totalSegments(),
                analysis.hotspots().size(),
                analysis.topPaths().isEmpty() ? "none" : formatPath(analysis.topPaths().get(0))
            ));
        }
        return summaries;
    }

    private String formatPath(PathSegment segment) {
        return String.format("[%d,%d,%d]->[%d,%d,%d] (%dx)",
            segment.from().getX(), segment.from().getY(), segment.from().getZ(),
            segment.to().getX(), segment.to().getY(), segment.to().getZ(),
            segment.count());
    }

    // ==================== Inner Classes ====================

    /**
     * Tracks position history for a player.
     */
    private static class PositionHistory {
        private final int maxSize;
        private final LinkedList<BlockPos> positions = new LinkedList<>();
        private final LinkedList<String> rooms = new LinkedList<>();

        PositionHistory(int maxSize) {
            this.maxSize = maxSize;
        }

        void addPosition(BlockPos pos, String room) {
            positions.addLast(pos);
            rooms.addLast(room);
            if (positions.size() > maxSize) {
                positions.removeFirst();
                rooms.removeFirst();
            }
        }

        BlockPos getLastPosition() {
            return positions.isEmpty() ? null : positions.getLast();
        }

        String getLastRoom() {
            return rooms.isEmpty() ? null : rooms.getLast();
        }
    }

    /**
     * A movement segment from one position to another.
     */
    public record PathSegment(BlockPos from, BlockPos to, int count) {}

    /**
     * A hotspot - a position with high traffic.
     */
    public record Hotspot(BlockPos position, int traffic) {}

    /**
     * Analysis results for a room's movement patterns.
     */
    public record PathAnalysis(
        String roomId,
        int totalSegments,
        List<PathSegment> topPaths,
        List<Hotspot> hotspots
    ) {}
}

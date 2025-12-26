package com.devmod.telemetry.spatial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.devmod.telemetry.TelemetryService;

public class BacktrackingService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BacktrackingService INSTANCE = new BacktrackingService();

    // Player movement history for backtrack detection
    private final Map<UUID, PlayerMovementHistory> playerHistories = new ConcurrentHashMap<>();

    // Aggregated backtrack stats per room
    private final Map<String, RoomBacktrackStats> roomStats = new ConcurrentHashMap<>();

    // Configuration
    private static final int HISTORY_SIZE = 50; // Positions to remember
    private static final int GRID_SIZE = 4; // Quantize to 4-block grid
    private static final double BACKTRACK_THRESHOLD = 2.0; // Distance to consider "same position"
    private static final int MIN_STEPS_BETWEEN = 5; // Minimum steps before counting as backtrack

    private BacktrackingService() {}

    /**
     * Records a player's position and checks for backtracking.
     *
     * @param playerId Player UUID
     * @param roomId Current room ID
     * @param position Current position
     */
    public void recordPosition(UUID playerId, String roomId, Vec3 position) {
        PlayerMovementHistory history = playerHistories.computeIfAbsent(playerId,
            k -> new PlayerMovementHistory(HISTORY_SIZE));

        BlockPos gridPos = quantizeToGrid(position);

        // Check if this is a backtrack
        BacktrackResult result = history.checkBacktrack(gridPos, roomId);

        if (result.isBacktrack) {
            // Record backtrack event
            RoomBacktrackStats stats = roomStats.computeIfAbsent(roomId, k -> new RoomBacktrackStats());
            stats.recordBacktrack(gridPos, result.stepsBack);

            // Log to telemetry
            logBacktrack(playerId, roomId, gridPos, result.stepsBack);

            LOGGER.debug("[Backtrack] Player {} backtracked {} steps at {} in room {}",
                playerId, result.stepsBack, gridPos, roomId);
        }

        // Add to history
        history.addPosition(gridPos, roomId);
    }

    /**
     * Quantizes a position to the grid.
     */
    private BlockPos quantizeToGrid(Vec3 position) {
        int x = (int) Math.floor(position.x / GRID_SIZE) * GRID_SIZE;
        int y = (int) Math.floor(position.y / GRID_SIZE) * GRID_SIZE;
        int z = (int) Math.floor(position.z / GRID_SIZE) * GRID_SIZE;
        return new BlockPos(x, y, z);
    }

    /**
     * Logs a backtrack event to telemetry.
     */
    private void logBacktrack(UUID playerId, String roomId, BlockPos pos, int stepsBack) {
        String json = String.format(
            "{\"ts\":\"%s\",\"player\":\"%s\",\"room\":\"%s\",\"pos\":[%d,%d,%d],\"steps_back\":%d}",
            Instant.now(), playerId, roomId, pos.getX(), pos.getY(), pos.getZ(), stepsBack);
        TelemetryService.INSTANCE.appendBacktrack(json);
    }

    /**
     * Gets the confusion index for a room.
     * Higher values indicate more backtracking/confusion.
     *
     * @return Value between 0 (no backtracking) and 1+ (heavy backtracking)
     */
    public double getConfusionIndex(String roomId) {
        RoomBacktrackStats stats = roomStats.get(roomId);
        if (stats == null || stats.uniqueVisitors == 0) {
            return 0.0;
        }
        return (double) stats.backtrackCount / stats.uniqueVisitors;
    }

    /**
     * Gets backtrack stats for a room.
     */
    public RoomBacktrackStats getStats(String roomId) {
        return roomStats.getOrDefault(roomId, new RoomBacktrackStats());
    }

    /**
     * Gets all room stats.
     */
    public Map<String, RoomBacktrackStats> getAllStats() {
        return Map.copyOf(roomStats);
    }

    /**
     * Gets rooms sorted by confusion index (most confusing first).
     */
    public List<String> getMostConfusingRooms(int limit) {
        List<Map.Entry<String, RoomBacktrackStats>> entries = new ArrayList<>(roomStats.entrySet());
        entries.sort((a, b) -> {
            double indexA = getConfusionIndex(a.getKey());
            double indexB = getConfusionIndex(b.getKey());
            return Double.compare(indexB, indexA);
        });

        List<String> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, RoomBacktrackStats> entry : entries) {
            if (count++ >= limit) break;
            result.add(String.format("%s: %.2f confusion (%.0f%% backtrack rate)",
                entry.getKey(),
                getConfusionIndex(entry.getKey()),
                entry.getValue().getBacktrackRate() * 100));
        }
        return result;
    }

    /**
     * Gets backtrack hotspots for a room.
     */
    public List<BlockPos> getHotspots(String roomId, int limit) {
        RoomBacktrackStats stats = roomStats.get(roomId);
        if (stats == null) {
            return List.of();
        }

        List<Map.Entry<BlockPos, Integer>> entries = new ArrayList<>(stats.backtrackPositions.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry<BlockPos, Integer>::getValue).reversed());

        List<BlockPos> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<BlockPos, Integer> entry : entries) {
            if (count++ >= limit) break;
            result.add(entry.getKey());
        }
        return result;
    }

    /**
     * Gets summary for display.
     */
    public List<String> getSummaries() {
        List<String> summaries = new ArrayList<>();
        for (Map.Entry<String, RoomBacktrackStats> entry : roomStats.entrySet()) {
            RoomBacktrackStats stats = entry.getValue();
            summaries.add(String.format("Room '%s': %d backtracks, %.2f confusion index, %.0f%% rate",
                entry.getKey(),
                stats.backtrackCount,
                getConfusionIndex(entry.getKey()),
                stats.getBacktrackRate() * 100));
        }
        return summaries;
    }

    /**
     * Clears all data.
     */
    public void clear() {
        playerHistories.clear();
        roomStats.clear();
    }

    /**
     * Cleans up data for a player who left.
     */
    public void cleanupPlayer(UUID playerId) {
        playerHistories.remove(playerId);
    }

    // ==================== Inner Classes ====================

    /**
     * Tracks a player's movement history for backtrack detection.
     */
    private static class PlayerMovementHistory {
        private final int maxSize;
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<String> rooms = new ArrayList<>();
        private final Set<BlockPos> visitedPositions = new HashSet<>();

        PlayerMovementHistory(int maxSize) {
            this.maxSize = maxSize;
        }

        void addPosition(BlockPos pos, String room) {
            positions.add(pos);
            rooms.add(room);
            visitedPositions.add(pos);

            if (positions.size() > maxSize) {
                BlockPos removed = positions.remove(0);
                com.mojang.logging.LogUtils.getLogger().debug("[BacktrackingService] Removed old position from history: {}", removed);
                rooms.remove(0);
                // Don't remove from visitedPositions - we want to track all visited
            }
        }

        /**
         * Checks if the new position is a backtrack.
         * A backtrack occurs when we return to a position we visited earlier
         * (but not recently).
         */
        BacktrackResult checkBacktrack(BlockPos newPos, String currentRoom) {
            if (positions.size() < MIN_STEPS_BETWEEN) {
                return new BacktrackResult(false, 0);
            }

            // Check if we've been here before (but not in last MIN_STEPS_BETWEEN positions)
            int skipRecent = Math.min(MIN_STEPS_BETWEEN, positions.size());

            for (int i = positions.size() - skipRecent - 1; i >= 0; i--) {
                BlockPos oldPos = positions.get(i);
                String oldRoom = rooms.get(i);

                // Must be in same room
                if (!currentRoom.equals(oldRoom)) continue;

                // Check if close enough to count as same position
                double dist = Math.sqrt(Objects.requireNonNull(oldPos).distSqr(Objects.requireNonNull(newPos)));
                if (dist <= BACKTRACK_THRESHOLD) {
                    int stepsBack = positions.size() - i;
                    return new BacktrackResult(true, stepsBack);
                }
            }

            return new BacktrackResult(false, 0);
        }
    }

    /**
     * Result of a backtrack check.
     */
    private record BacktrackResult(boolean isBacktrack, int stepsBack) {}

    /**
     * Aggregated backtrack statistics for a room.
     */
    public static class RoomBacktrackStats {
        public int backtrackCount = 0;
        public int totalMovements = 0;
        public int uniqueVisitors = 0;
        public int totalStepsBack = 0;
        public final Map<BlockPos, Integer> backtrackPositions = new ConcurrentHashMap<>();
        private final Set<UUID> visitors = ConcurrentHashMap.newKeySet();

        void recordBacktrack(BlockPos pos, int stepsBack) {
            backtrackCount++;
            totalStepsBack += stepsBack;
            backtrackPositions.merge(pos, 1, (a, b) -> a + b);
        }

        void recordVisitor(UUID playerId) {
            if (visitors.add(playerId)) {
                uniqueVisitors++;
            }
        }

        void recordMovement() {
            totalMovements++;
        }

        /**
         * Gets the backtrack rate (backtracks / total movements).
         */
        public double getBacktrackRate() {
            return totalMovements > 0 ? (double) backtrackCount / totalMovements : 0.0;
        }

        /**
         * Gets average steps back per backtrack event.
         */
        public double getAverageStepsBack() {
            return backtrackCount > 0 ? (double) totalStepsBack / backtrackCount : 0.0;
        }
    }
}

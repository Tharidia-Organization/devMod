package com.frenkvs.devmod.telemetry.player;

import com.frenkvs.devmod.telemetry.RoomDefinition;
import com.frenkvs.devmod.telemetry.room.RoomService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking player positions, room transitions, and movement patterns.
 * Extracted from TelemetryService for better separation of concerns.
 *
 * Tracks:
 * - Player room transitions (room_time.ndjson)
 * - Movement sampling for heatmaps (every 2 seconds)
 * - Out-of-bounds detection (vertical delta)
 * - Backtracking detection in dungeons
 *
 * Thread-safe singleton for concurrent access.
 */
public class PlayerTrackingService {
    public static final PlayerTrackingService INSTANCE = new PlayerTrackingService();

    private final Map<UUID, PlayerRoomTracker> playerRooms = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMovementSample = new ConcurrentHashMap<>();
    private final Map<UUID, OutOfBoundsTracker> oobTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, BacktrackTracker> backtrackTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, IdleTracker> idleTrackers = new ConcurrentHashMap<>();

    // Configuration (loaded from TelemetrySettings)
    private int movementSampleIntervalMs = 2000;
    private double outOfBoundsDeltaY = 10.0;
    private long outOfBoundsMs = 5000;

    private PlayerTrackingService() {}

    /**
     * Configure settings from TelemetrySettings.
     *
     * @param movementSampleIntervalMs Interval for movement sampling in ms
     * @param outOfBoundsDeltaY Vertical delta threshold for OOB detection
     * @param outOfBoundsMs Time threshold before OOB alert
     */
    public void configure(int movementSampleIntervalMs, double outOfBoundsDeltaY, long outOfBoundsMs) {
        this.movementSampleIntervalMs = movementSampleIntervalMs;
        this.outOfBoundsDeltaY = outOfBoundsDeltaY;
        this.outOfBoundsMs = outOfBoundsMs;
    }

    /**
     * Track player room transitions and movement sampling.
     * Returns a result indicating whether room changed and if movement should be sampled.
     *
     * @param player The server player to track
     * @param roomId The resolved room ID for the player's position
     * @return RoomTrackResult with room change info and movement sampling flag
     */
    public RoomTrackResult trackPlayerRoom(ServerPlayer player, String roomId) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        PlayerRoomTracker tracker = playerRooms.computeIfAbsent(playerId,
                id -> new PlayerRoomTracker(roomId, Instant.now()));

        boolean roomChanged = false;
        String oldRoom = tracker.currentRoom();

        if (!tracker.currentRoom().equals(roomId)) {
            tracker = tracker.withRoom(roomId, Instant.now());
            playerRooms.put(playerId, tracker);
            roomChanged = true;
        }

        // Check if movement should be sampled (every movementSampleIntervalMs)
        boolean shouldSampleMovement = false;
        Long lastSample = lastMovementSample.get(playerId);
        if (lastSample == null || now - lastSample >= movementSampleIntervalMs) {
            lastMovementSample.put(playerId, now);
            shouldSampleMovement = true;
        }

        return new RoomTrackResult(
                roomChanged,
                oldRoom,
                roomId,
                shouldSampleMovement,
                player.blockPosition()
        );
    }

    /**
     * Check if player is out of bounds vertically within a room.
     * Returns result indicating OOB status and whether an alert should be triggered.
     *
     * @param player The server player to check
     * @return OutOfBoundsResult with OOB status and alert flag
     */
    public OutOfBoundsResult checkOutOfBounds(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return OutOfBoundsResult.NOT_APPLICABLE;
        }

        ServerLevel level = player.serverLevel();
        RoomDefinition def = RoomService.INSTANCE.findRoom(level, player.blockPosition());

        if (def == null) {
            oobTrackers.remove(player.getUUID());
            return OutOfBoundsResult.NOT_IN_ROOM;
        }

        double baselineY = def.min().getY();
        double delta = player.getY() - baselineY;
        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();

        OutOfBoundsTracker tracker = oobTrackers.get(playerId);

        if (delta > outOfBoundsDeltaY) {
            if (tracker == null) {
                tracker = new OutOfBoundsTracker(now, delta);
                oobTrackers.put(playerId, tracker);
                return new OutOfBoundsResult(true, false, def.id(),
                        new Vec3(player.getX(), player.getY(), player.getZ()), delta);
            } else if (now - tracker.startMs > outOfBoundsMs) {
                // Reset timer to avoid spamming alerts
                tracker.startMs = now;
                return new OutOfBoundsResult(true, true, def.id(),
                        new Vec3(player.getX(), player.getY(), player.getZ()), delta);
            }
            return new OutOfBoundsResult(true, false, def.id(),
                    new Vec3(player.getX(), player.getY(), player.getZ()), delta);
        } else {
            oobTrackers.remove(playerId);
            return OutOfBoundsResult.IN_BOUNDS;
        }
    }

    /**
     * Track player room visit for backtracking detection.
     * Returns result indicating if this visit is a backtrack.
     *
     * @param playerId Player UUID
     * @param roomId Room being visited
     * @return BacktrackResult with backtrack status and count
     */
    public BacktrackResult trackRoomVisit(UUID playerId, String roomId) {
        BacktrackTracker tracker = backtrackTrackers.computeIfAbsent(playerId,
                k -> new BacktrackTracker());
        long now = System.currentTimeMillis();
        boolean isBacktrack = tracker.recordRoomVisit(roomId, now);

        return new BacktrackResult(isBacktrack, tracker.backtrackCount, tracker.visitedRooms.size());
    }

    /**
     * Get the current room for a player.
     *
     * @param playerId Player UUID
     * @return Optional containing current room ID if tracked
     */
    public Optional<String> getCurrentRoom(UUID playerId) {
        PlayerRoomTracker tracker = playerRooms.get(playerId);
        return tracker != null ? Optional.of(tracker.currentRoom()) : Optional.empty();
    }

    /**
     * Get the room entry time for a player.
     *
     * @param playerId Player UUID
     * @return Optional containing entry time if tracked
     */
    public Optional<Instant> getRoomEntryTime(UUID playerId) {
        PlayerRoomTracker tracker = playerRooms.get(playerId);
        return tracker != null ? Optional.of(tracker.enteredAt()) : Optional.empty();
    }

    /**
     * Get backtrack statistics for a player.
     *
     * @param playerId Player UUID
     * @return Optional containing backtrack tracker if exists
     */
    public Optional<BacktrackTracker> getBacktrackTracker(UUID playerId) {
        return Optional.ofNullable(backtrackTrackers.get(playerId));
    }

    /**
     * Get all tracked player IDs.
     *
     * @return Set of tracked player UUIDs
     */
    public Set<UUID> getTrackedPlayers() {
        return new HashSet<>(playerRooms.keySet());
    }

    /**
     * Get player count per room.
     *
     * @return Map of room ID to player count
     */
    public Map<String, Integer> getPlayerCountPerRoom() {
        Map<String, Integer> counts = new HashMap<>();
        playerRooms.values().forEach(tracker ->
                counts.merge(tracker.currentRoom(), 1, (a, b) -> a + b));
        return counts;
    }

    /**
     * Update idle/confusion tracking for a player.
     * M17: Idle/Confusion Time metric.
     *
     * @param player The server player to track
     * @return IdleResult with current idle state
     */
    public IdleResult updateIdleTracking(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        IdleTracker tracker = idleTrackers.computeIfAbsent(playerId, k -> new IdleTracker());
        tracker.update(player.position(), player.getYRot(), now);

        return new IdleResult(
                tracker.isCurrentlyIdle(),
                tracker.getCurrentIdleMs(now),
                tracker.getLookAroundCount(),
                tracker.getConfusionScore(now - (player.tickCount * 50L)) // Approximate session time
        );
    }

    /**
     * Get idle tracker for a player.
     *
     * @param playerId Player UUID
     * @return Optional containing idle tracker if exists
     */
    public Optional<IdleTracker> getIdleTracker(UUID playerId) {
        return Optional.ofNullable(idleTrackers.get(playerId));
    }

    /**
     * Cleanup tracking data for a player (on disconnect/death).
     *
     * @param playerId Player UUID to cleanup
     */
    public void cleanupPlayer(UUID playerId) {
        playerRooms.remove(playerId);
        lastMovementSample.remove(playerId);
        oobTrackers.remove(playerId);
        backtrackTrackers.remove(playerId);
        idleTrackers.remove(playerId);
    }

    /**
     * Clear all tracking data.
     */
    public void clear() {
        playerRooms.clear();
        lastMovementSample.clear();
        oobTrackers.clear();
        backtrackTrackers.clear();
        idleTrackers.clear();
    }

    // ===== Result Records =====

    /**
     * Result of idle/confusion tracking.
     * M17: Idle/Confusion Time metric.
     */
    public record IdleResult(
            boolean isIdle,
            long totalIdleMs,
            int lookAroundCount,
            float confusionScore
    ) {
        public static final IdleResult NONE = new IdleResult(false, 0, 0, 0);
    }

    /**
     * Result of tracking player room position.
     */
    public record RoomTrackResult(
            boolean roomChanged,
            String oldRoom,
            String newRoom,
            boolean shouldSampleMovement,
            BlockPos position
    ) {
        public static final RoomTrackResult NO_CHANGE = new RoomTrackResult(false, "", "", false, BlockPos.ZERO);
    }

    /**
     * Result of out-of-bounds check.
     */
    public record OutOfBoundsResult(
            boolean isOutOfBounds,
            boolean shouldAlert,
            String roomId,
            Vec3 position,
            double deltaY
    ) {
        public static final OutOfBoundsResult NOT_APPLICABLE = new OutOfBoundsResult(false, false, "", Vec3.ZERO, 0);
        public static final OutOfBoundsResult NOT_IN_ROOM = new OutOfBoundsResult(false, false, "", Vec3.ZERO, 0);
        public static final OutOfBoundsResult IN_BOUNDS = new OutOfBoundsResult(false, false, "", Vec3.ZERO, 0);
    }

    /**
     * Result of backtrack detection.
     */
    public record BacktrackResult(
            boolean isBacktrack,
            int backtrackCount,
            int totalRoomsVisited
    ) {
        public static final BacktrackResult NO_BACKTRACK = new BacktrackResult(false, 0, 0);
    }

    // ===== Internal Tracker Classes =====

    /**
     * Tracks which room a player is in and when they entered.
     * Immutable record with factory method for room changes.
     */
    public record PlayerRoomTracker(String currentRoom, Instant enteredAt) {
        public PlayerRoomTracker withRoom(String newRoom, Instant now) {
            return new PlayerRoomTracker(newRoom, now);
        }
    }

    /**
     * Tracks out-of-bounds duration for vertical delta detection.
     */
    public static final class OutOfBoundsTracker {
        long startMs;
        double peakDelta;

        public OutOfBoundsTracker(long startMs, double peakDelta) {
            this.startMs = startMs;
            this.peakDelta = peakDelta;
        }

        public long getStartMs() {
            return startMs;
        }

        public double getPeakDelta() {
            return peakDelta;
        }
    }

    /**
     * Tracks room visit history for backtracking detection.
     * Backtracking occurs when a player returns to a previously visited room.
     */
    public static final class BacktrackTracker {
        final List<String> visitedRooms = new ArrayList<>();
        int backtrackCount = 0;
        long lastBacktrackMs = 0;

        boolean recordRoomVisit(String roomId, long nowMs) {
            int prevIndex = visitedRooms.indexOf(roomId);
            if (prevIndex >= 0 && prevIndex < visitedRooms.size() - 1) {
                // Backtrack detected - returning to a previously visited room
                backtrackCount++;
                lastBacktrackMs = nowMs;
                return true;
            }
            if (visitedRooms.isEmpty() || !visitedRooms.get(visitedRooms.size() - 1).equals(roomId)) {
                visitedRooms.add(roomId);
            }
            return false;
        }

        public List<String> getVisitedRooms() {
            return Collections.unmodifiableList(visitedRooms);
        }

        public int getBacktrackCount() {
            return backtrackCount;
        }

        public long getLastBacktrackMs() {
            return lastBacktrackMs;
        }
    }

    /**
     * Tracks player idle time and confusion (rapid look-around behavior).
     * M17: Idle/Confusion Time metric.
     */
    public static final class IdleTracker {
        private Vec3 lastPosition = Vec3.ZERO;
        private float lastYaw = 0;
        private long idleStartMs = 0;
        private long totalIdleMs = 0;
        private int lookAroundCount = 0;
        private long lastUpdateMs = 0;

        /**
         * Update tracker with current player state.
         *
         * @param pos Current position
         * @param yaw Current yaw rotation
         * @param nowMs Current time in milliseconds
         */
        public void update(Vec3 pos, float yaw, long nowMs) {
            if (lastUpdateMs == 0) {
                // First update, just store state
                lastPosition = Objects.requireNonNull(pos);
                lastYaw = yaw;
                lastUpdateMs = nowMs;
                return;
            }

            double dist = lastPosition.distanceTo(Objects.requireNonNull(pos));
            float yawDelta = Math.abs(yaw - lastYaw);

            // Normalize yaw delta (handle wrap-around)
            if (yawDelta > 180) {
                yawDelta = 360 - yawDelta;
            }

            if (dist < 0.1) {
                // Player is stationary
                if (idleStartMs == 0) {
                    idleStartMs = nowMs;
                }
            } else {
                // Player moved
                if (idleStartMs > 0) {
                    totalIdleMs += nowMs - idleStartMs;
                    idleStartMs = 0;
                }
            }

            // Rapid rotation indicates confusion/looking around
            if (yawDelta > 90) {
                lookAroundCount++;
            }

            lastPosition = pos;
            lastYaw = yaw;
            lastUpdateMs = nowMs;
        }

        /**
         * Get current idle duration (including ongoing idle).
         */
        public long getCurrentIdleMs(long nowMs) {
            if (idleStartMs > 0) {
                return totalIdleMs + (nowMs - idleStartMs);
            }
            return totalIdleMs;
        }

        public long getTotalIdleMs() {
            return totalIdleMs;
        }

        public int getLookAroundCount() {
            return lookAroundCount;
        }

        public boolean isCurrentlyIdle() {
            return idleStartMs > 0;
        }

        /**
         * Calculate confusion score based on look-around behavior.
         * Higher score = more confused.
         */
        public float getConfusionScore(long sessionDurationMs) {
            if (sessionDurationMs <= 0) return 0;
            // Normalize: expect ~1 look-around per 10 seconds normally
            float expectedLooks = sessionDurationMs / 10000f;
            if (expectedLooks <= 0) return 0;
            return Math.min(1.0f, lookAroundCount / (expectedLooks * 3));
        }

        public void reset() {
            lastPosition = Vec3.ZERO;
            lastYaw = 0;
            idleStartMs = 0;
            totalIdleMs = 0;
            lookAroundCount = 0;
            lastUpdateMs = 0;
        }
    }
}

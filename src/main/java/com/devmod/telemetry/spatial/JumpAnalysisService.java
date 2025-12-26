package com.devmod.telemetry.spatial;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.room.RoomService;
import com.devmod.telemetry.util.BitPackedFlags;

/**
 * Service for tracking and analyzing jump patterns (M18).
 *
 * Features:
 * - Jump frequency tracking per room
 * - Jump height analysis
 * - Jump direction patterns
 * - Failed jump detection (short jumps, wall collisions)
 * - Parkour segment difficulty estimation
 *
 * Thread-safe for concurrent access from multiple server threads.
 */
public class JumpAnalysisService {
    public static final JumpAnalysisService INSTANCE = new JumpAnalysisService();

    // === Jump Tracking ===
    // Per-player jump state
    private final Map<UUID, JumpState> playerJumpStates = new ConcurrentHashMap<>();

    // Jump heatmap per room (position -> jump count)
    private final Map<String, Map<BlockPos, JumpStats>> jumpHeatmap = new ConcurrentHashMap<>();

    // Jump pattern analysis per room
    private final Map<String, RoomJumpAnalysis> roomAnalysis = new ConcurrentHashMap<>();

    // Configuration
    private static final double MIN_JUMP_HEIGHT = 0.4;     // Minimum Y velocity to count as jump
    private static final double FAILED_JUMP_THRESHOLD = 0.8; // Less than this is a "short" jump
    private static final long JUMP_COOLDOWN_MS = 300;      // Minimum time between jumps

    private JumpAnalysisService() {}

    // ===== Jump Detection =====

    /**
     * Called every tick to track player jumping state.
     * Should be called from ServerTick for each player.
     *
     * @param player Server player to track
     * @param level Server level
     */
    public void tick(ServerPlayer player, ServerLevel level) {
        UUID playerId = player.getUUID();
        JumpState state = playerJumpStates.computeIfAbsent(playerId, k -> new JumpState());

        Vec3 currentPos = player.position();
        Vec3 currentVel = player.getDeltaMovement();
        boolean onGround = player.onGround();
        long now = System.currentTimeMillis();

        // Detect jump start (was on ground, now has upward velocity)
        if (state.wasOnGround && !onGround && currentVel.y > MIN_JUMP_HEIGHT) {
            // Check cooldown to avoid double-counting
            if (now - state.lastJumpTime > JUMP_COOLDOWN_MS) {
                state.jumpStartPos = currentPos;
                state.jumpStartTime = now;
                state.isJumping = true;
                state.maxJumpHeight = 0;
                state.jumpDirection = calculateDirection(currentVel);
            }
        }

        // Track maximum height during jump
        if (state.isJumping && currentPos.y > state.jumpStartPos.y) {
            double height = currentPos.y - state.jumpStartPos.y;
            if (height > state.maxJumpHeight) {
                state.maxJumpHeight = height;
            }
        }

        // Detect jump end (landed or hit wall/ceiling)
        if (state.isJumping && (onGround || player.horizontalCollision || player.verticalCollision)) {
            // Record the jump
            JumpRecord record = createJumpRecord(player, level, state, currentPos, onGround);
            processJumpRecord(record);

            state.isJumping = false;
            state.lastJumpTime = now;
        }

        // Update state for next tick
        state.wasOnGround = onGround;
        // Check if player teleported (moved more than 10 blocks in one tick)
        Vec3 lastPos = state.getLastPos();
        if (lastPos != null && currentPos.distanceTo(Objects.requireNonNull(lastPos)) > 10.0) {
            state.isJumping = false; // Cancel any active jump tracking on teleport
        }
        state.lastPos = currentPos;
    }

    /**
     * Create a jump record from current state.
     */
    private JumpRecord createJumpRecord(ServerPlayer player, ServerLevel level, JumpState state, Vec3 endPos, boolean landed) {
        BlockPos startBlock = BlockPos.containing(Objects.requireNonNull(state.jumpStartPos));
        BlockPos endBlock = BlockPos.containing(Objects.requireNonNull(endPos));
        String roomId = RoomService.INSTANCE.resolveRoom(level, startBlock);

        double horizontalDistance = Math.sqrt(
                Math.pow(endPos.x - state.jumpStartPos.x, 2) +
                Math.pow(endPos.z - state.jumpStartPos.z, 2)
        );

        boolean isFailedJump = !landed || state.maxJumpHeight < FAILED_JUMP_THRESHOLD;
        boolean isWallCollision = player.horizontalCollision;
        boolean isCeilingCollision = player.verticalCollision && state.maxJumpHeight < 1.0;

        return new JumpRecord(
                player.getGameProfile().getName(),
                roomId,
                startBlock,
                endBlock,
                state.maxJumpHeight,
                horizontalDistance,
                state.jumpDirection,
                System.currentTimeMillis() - state.jumpStartTime,
                landed,
                isFailedJump,
                isWallCollision,
                isCeilingCollision
        );
    }

    /**
     * Process and store a jump record.
     */
    private void processJumpRecord(JumpRecord record) {
        // Update jump heatmap
        Map<BlockPos, JumpStats> roomMap = jumpHeatmap.computeIfAbsent(record.roomId, k -> new ConcurrentHashMap<>());
        JumpStats stats = roomMap.computeIfAbsent(record.startPos, k -> new JumpStats());
        stats.totalJumps++;
        stats.totalHeight += record.maxHeight;
        if (record.failedJump) stats.failedJumps++;
        if (record.wallCollision) stats.wallCollisions++;
        if (record.ceilingCollision) stats.ceilingCollisions++;

        // Update room analysis
        RoomJumpAnalysis analysis = roomAnalysis.computeIfAbsent(record.roomId, k -> new RoomJumpAnalysis());
        analysis.totalJumps++;
        analysis.totalJumpHeight += record.maxHeight;
        analysis.totalJumpDistance += record.horizontalDistance;
        if (record.failedJump) analysis.failedJumps++;

        // Track direction distribution
        analysis.directionCounts.merge(record.direction, 1, (a, b) -> a + b);

        // Track difficulty hotspots
        if (record.failedJump) {
            analysis.failureHotspots.merge(record.startPos, 1, (a, b) -> a + b);
        }
    }

    /**
     * Calculate jump direction from velocity.
     */
    private String calculateDirection(Vec3 velocity) {
        if (Math.abs(velocity.x) < 0.1 && Math.abs(velocity.z) < 0.1) {
            return "VERTICAL"; // Straight up jump
        }

        double angle = Math.atan2(velocity.z, velocity.x) * 180 / Math.PI;

        if (angle >= -22.5 && angle < 22.5) return "EAST";
        if (angle >= 22.5 && angle < 67.5) return "SOUTHEAST";
        if (angle >= 67.5 && angle < 112.5) return "SOUTH";
        if (angle >= 112.5 && angle < 157.5) return "SOUTHWEST";
        if (angle >= 157.5 || angle < -157.5) return "WEST";
        if (angle >= -157.5 && angle < -112.5) return "NORTHWEST";
        if (angle >= -112.5 && angle < -67.5) return "NORTH";
        if (angle >= -67.5 && angle < -22.5) return "NORTHEAST";

        return "UNKNOWN";
    }

    // ===== Statistics & Reports =====

    /**
     * Get jump statistics for a room.
     *
     * @param roomId Room identifier
     * @return RoomJumpStats summary
     */
    public RoomJumpStats getRoomStats(String roomId) {
        RoomJumpAnalysis analysis = roomAnalysis.get(roomId);
        if (analysis == null) {
            return new RoomJumpStats(roomId, 0, 0, 0, 0, 0, Collections.emptyList());
        }

        double avgHeight = analysis.totalJumps > 0 ? analysis.totalJumpHeight / analysis.totalJumps : 0;
        double avgDistance = analysis.totalJumps > 0 ? analysis.totalJumpDistance / analysis.totalJumps : 0;
        double failureRate = analysis.totalJumps > 0 ? (double) analysis.failedJumps / analysis.totalJumps : 0;

        // Get top 5 failure hotspots
        List<BlockPos> topHotspots = analysis.failureHotspots.entrySet().stream()
                .sorted(Map.Entry.<BlockPos, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        return new RoomJumpStats(roomId, analysis.totalJumps, analysis.failedJumps, avgHeight, avgDistance, failureRate, topHotspots);
    }

    /**
     * Get jumps per meter for a room (platforming intensity).
     *
     * @param roomId Room identifier
     * @return Jumps per meter traveled (higher = more platforming)
     */
    public double getJumpsPerMeter(String roomId) {
        RoomJumpAnalysis analysis = roomAnalysis.get(roomId);
        if (analysis == null || analysis.totalJumpDistance == 0) return 0;
        return analysis.totalJumps / analysis.totalJumpDistance;
    }

    /**
     * Export jump heatmap data.
     *
     * @param consumer Receives (roomId, jsonLine) pairs
     */
    public void exportJumpHeatmap(BiConsumer<String, String> consumer) {
        jumpHeatmap.forEach((roomId, posMap) -> {
            posMap.forEach((pos, stats) -> {
                String line = "{\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                        + "\"x\":" + pos.getX() + ","
                        + "\"y\":" + pos.getY() + ","
                        + "\"z\":" + pos.getZ() + ","
                        + "\"jumps\":" + stats.totalJumps + ","
                        + "\"failed\":" + stats.failedJumps + ","
                        + "\"avgHeight\":" + String.format("%.2f", stats.totalJumps > 0 ? stats.totalHeight / stats.totalJumps : 0) + ","
                        + "\"wallHits\":" + stats.wallCollisions + ","
                        + "\"ceilingHits\":" + stats.ceilingCollisions + "}";
                consumer.accept(roomId, line);
            });
        });
    }

    /**
     * Export room jump analysis summary.
     *
     * @param consumer Receives (roomId, jsonLine) pairs
     */
    public void exportRoomAnalysis(BiConsumer<String, String> consumer) {
        roomAnalysis.forEach((roomId, analysis) -> {
            StringBuilder directions = new StringBuilder("[");
            analysis.directionCounts.forEach((dir, count) -> {
                if (directions.length() > 1) directions.append(",");
                directions.append("{\"dir\":\"").append(dir).append("\",\"count\":").append(count).append("}");
            });
            directions.append("]");

            String line = "{\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"totalJumps\":" + analysis.totalJumps + ","
                    + "\"failedJumps\":" + analysis.failedJumps + ","
                    + "\"avgHeight\":" + String.format("%.2f", analysis.totalJumps > 0 ? analysis.totalJumpHeight / analysis.totalJumps : 0) + ","
                    + "\"avgDistance\":" + String.format("%.2f", analysis.totalJumps > 0 ? analysis.totalJumpDistance / analysis.totalJumps : 0) + ","
                    + "\"jumpsPerMeter\":" + String.format("%.3f", getJumpsPerMeter(roomId)) + ","
                    + "\"directions\":" + directions + "}";
            consumer.accept(roomId, line);
        });
    }

    // ===== Cleanup =====

    /**
     * Clear all tracked data (for reload).
     */
    public void clear() {
        playerJumpStates.clear();
        jumpHeatmap.clear();
        roomAnalysis.clear();
    }

    /**
     * Cleanup data for a specific player.
     *
     * @param playerId Player UUID
     */
    public void cleanupPlayer(UUID playerId) {
        playerJumpStates.remove(playerId);
    }

    // ===== Data Classes =====

    /**
     * Internal state for tracking player jumps.
     */
    private static class JumpState {
        boolean wasOnGround = true;
        boolean isJumping = false;
        Vec3 jumpStartPos = Vec3.ZERO;
        long jumpStartTime = 0;
        long lastJumpTime = 0;
        double maxJumpHeight = 0;
        String jumpDirection = "UNKNOWN";
        Vec3 lastPos = Vec3.ZERO;

        /**
         * Gets the last recorded position for tracking jump patterns.
         */
        public Vec3 getLastPos() {
            return lastPos;
        }
    }

    /**
     * Statistics for a specific jump location.
     */
    private static class JumpStats {
        int totalJumps = 0;
        double totalHeight = 0;
        int failedJumps = 0;
        int wallCollisions = 0;
        int ceilingCollisions = 0;
    }

    /**
     * Analysis data for a room's jump patterns.
     */
    private static class RoomJumpAnalysis {
        int totalJumps = 0;
        int failedJumps = 0;
        double totalJumpHeight = 0;
        double totalJumpDistance = 0;
        Map<String, Integer> directionCounts = new ConcurrentHashMap<>();
        Map<BlockPos, Integer> failureHotspots = new ConcurrentHashMap<>();
    }

    // ===== Records =====

    /**
     * Record of a single jump event.
     */
    public record JumpRecord(
            String player,
            String roomId,
            BlockPos startPos,
            BlockPos endPos,
            double maxHeight,
            double horizontalDistance,
            String direction,
            long durationMs,
            boolean landed,
            boolean failedJump,
            boolean wallCollision,
            boolean ceilingCollision
    ) {
        /**
         * Convert to JSON string.
         * PERFORMANCE: Uses bit-packed flags for 4 booleans and StringBuilder.
         * flags bits: [0]=landed, [1]=failedJump, [2]=wallCollision, [3]=ceilingCollision
         */
        public String toJson() {
            // PERFORMANCE: Pack 4 booleans into single int
            int flags = BitPackedFlags.pack(landed, failedJump, wallCollision, ceilingCollision);

            StringBuilder json = new StringBuilder(256);
            json.append("{\"ts\":\"").append(Instant.now()).append("\",");
            json.append("\"player\":\"").append(TelemetryJson.escape(player)).append("\",");
            json.append("\"room\":\"").append(TelemetryJson.escape(roomId)).append("\",");
            json.append("\"start\":[").append(startPos.getX()).append(",")
                .append(startPos.getY()).append(",").append(startPos.getZ()).append("],");
            json.append("\"end\":[").append(endPos.getX()).append(",")
                .append(endPos.getY()).append(",").append(endPos.getZ()).append("],");
            json.append("\"height\":"); appendDouble2(json, maxHeight); json.append(",");
            json.append("\"distance\":"); appendDouble2(json, horizontalDistance); json.append(",");
            json.append("\"dir\":\"").append(direction).append("\",");
            json.append("\"duration\":").append(durationMs).append(",");
            json.append("\"flags\":").append(flags);
            json.append("}");
            return json.toString();
        }

        /** Append double with 2 decimal places without String.format() */
        private static void appendDouble2(StringBuilder sb, double value) {
            long scaled = Math.round(value * 100.0);
            long intPart = scaled / 100;
            long decPart = Math.abs(scaled % 100);
            sb.append(intPart).append('.');
            if (decPart < 10) sb.append('0');
            sb.append(decPart);
        }
    }

    /**
     * Summary statistics for a room's jump patterns.
     */
    public record RoomJumpStats(
            String roomId,
            int totalJumps,
            int failedJumps,
            double avgHeight,
            double avgDistance,
            double failureRate,
            List<BlockPos> failureHotspots
    ) {}
}

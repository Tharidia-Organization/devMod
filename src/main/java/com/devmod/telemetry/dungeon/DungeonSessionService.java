package com.devmod.telemetry.dungeon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking dungeon run sessions and player progression.
 * Extracted from TelemetryService for better separation of concerns.
 *
 * Features:
 * - Dungeon session tracking (start/end with outcome)
 * - Room sequence tracking
 * - Backtracking detection
 * - Session statistics (deaths, kills, damage)
 *
 * Thread-safe for concurrent access from multiple server threads.
 */
public class DungeonSessionService {
    public static final DungeonSessionService INSTANCE = new DungeonSessionService();

    private final Map<UUID, DungeonSession> dungeonSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BacktrackTracker> backtrackTrackers = new ConcurrentHashMap<>();

    private DungeonSessionService() {}

    // ===== Dungeon Session Management =====

    /**
     * Start a new dungeon session for a player.
     *
     * @param playerId Player UUID
     * @param dungeonId Dungeon identifier
     */
    public void startSession(UUID playerId, String dungeonId) {
        dungeonSessions.put(playerId, new DungeonSession(dungeonId, System.currentTimeMillis()));
    }

    /**
     * End a dungeon session and get result for logging.
     *
     * @param playerId Player UUID
     * @param playerName Player display name
     * @param outcome Session outcome ("completed", "abandoned", "wipe")
     * @return Optional session result
     */
    public Optional<SessionResult> endSession(UUID playerId, String playerName, String outcome) {
        DungeonSession session = dungeonSessions.remove(playerId);
        if (session == null) {
            return Optional.empty();
        }

        session.outcome = outcome;
        session.endMs = System.currentTimeMillis();

        return Optional.of(new SessionResult(
            playerName, session.dungeonId, session.outcome,
            session.startMs, session.endMs, session.endMs - session.startMs,
            session.deaths, session.kills,
            session.totalDamageDealt, session.totalDamageTaken,
            new ArrayList<>(session.roomSequence)
        ));
    }

    /**
     * Check if player has active session.
     *
     * @param playerId Player UUID
     * @return true if player has active dungeon session
     */
    public boolean hasActiveSession(UUID playerId) {
        return dungeonSessions.containsKey(playerId);
    }

    /**
     * Record a room entry for the session.
     *
     * @param playerId Player UUID
     * @param roomId Room identifier
     */
    public void enterRoom(UUID playerId, String roomId) {
        DungeonSession session = dungeonSessions.get(playerId);
        if (session != null) {
            session.enterRoom(roomId);
        }
    }

    /**
     * Record a death in the session.
     *
     * @param playerId Player UUID
     */
    public void recordDeath(UUID playerId) {
        DungeonSession session = dungeonSessions.get(playerId);
        if (session != null) {
            session.deaths++;
        }
    }

    /**
     * Record a kill in the session.
     *
     * @param playerId Player UUID
     */
    public void recordKill(UUID playerId) {
        DungeonSession session = dungeonSessions.get(playerId);
        if (session != null) {
            session.kills++;
        }
    }

    /**
     * Record damage dealt in the session.
     *
     * @param playerId Player UUID
     * @param damage Damage amount
     */
    public void recordDamageDealt(UUID playerId, double damage) {
        DungeonSession session = dungeonSessions.get(playerId);
        if (session != null) {
            session.totalDamageDealt += damage;
        }
    }

    /**
     * Record damage taken in the session.
     *
     * @param playerId Player UUID
     * @param damage Damage amount
     */
    public void recordDamageTaken(UUID playerId, double damage) {
        DungeonSession session = dungeonSessions.get(playerId);
        if (session != null) {
            session.totalDamageTaken += damage;
        }
    }

    // ===== Backtracking Detection =====

    /**
     * Record room visit and check for backtracking.
     *
     * @param playerId Player UUID
     * @param roomId Room identifier
     * @return Optional backtrack info if backtracking detected
     */
    public Optional<BacktrackInfo> recordRoomVisit(UUID playerId, String roomId) {
        BacktrackTracker tracker = backtrackTrackers.computeIfAbsent(
            playerId, k -> new BacktrackTracker()
        );

        long now = System.currentTimeMillis();
        boolean isBacktrack = tracker.recordRoomVisit(roomId, now);

        // Also update dungeon session if active
        enterRoom(playerId, roomId);

        if (isBacktrack) {
            return Optional.of(new BacktrackInfo(roomId, tracker.backtrackCount, tracker.lastBacktrackMs));
        }
        return Optional.empty();
    }

    /**
     * Get backtrack count for player.
     *
     * @param playerId Player UUID
     * @return Backtrack count
     */
    public int getBacktrackCount(UUID playerId) {
        BacktrackTracker tracker = backtrackTrackers.get(playerId);
        return tracker != null ? tracker.backtrackCount : 0;
    }

    // ===== Cleanup =====

    /**
     * Cleanup all trackers for entity.
     *
     * @param entityId Entity UUID
     */
    public void cleanupEntity(UUID entityId) {
        dungeonSessions.remove(entityId);
        backtrackTrackers.remove(entityId);
    }

    /**
     * Clear all trackers (for reload).
     */
    public void clear() {
        dungeonSessions.clear();
        backtrackTrackers.clear();
    }

    // ===== Data Classes =====

    /**
     * Info about a backtrack event.
     */
    public record BacktrackInfo(String roomId, int backtrackCount, long timestampMs) {}

    /**
     * Result of a completed dungeon session.
     */
    public record SessionResult(
        String playerName,
        String dungeonId,
        String outcome,
        long startMs,
        long endMs,
        long durationMs,
        int deaths,
        int kills,
        double damageDealt,
        double damageTaken,
        List<String> roomSequence
    ) {
        public String toJson() {
            StringBuilder rooms = new StringBuilder("[");
            for (int i = 0; i < roomSequence.size(); i++) {
                if (i > 0) rooms.append(",");
                rooms.append("\"").append(escape(roomSequence.get(i))).append("\"");
            }
            rooms.append("]");

            return "{\"ts\":\"" + Instant.now() + "\","
                + "\"player\":\"" + escape(playerName) + "\","
                + "\"dungeon\":\"" + escape(dungeonId) + "\","
                + "\"outcome\":\"" + outcome + "\","
                + "\"durationMs\":" + durationMs + ","
                + "\"deaths\":" + deaths + ","
                + "\"kills\":" + kills + ","
                + "\"damageDealt\":" + damageDealt + ","
                + "\"damageTaken\":" + damageTaken + ","
                + "\"roomSequence\":" + rooms + "}";
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    // ===== Internal Classes =====

    /**
     * Internal dungeon session tracker.
     */
    private static final class DungeonSession {
        final String dungeonId;
        final long startMs;
        final List<String> roomSequence = new ArrayList<>();
        int deaths = 0;
        int kills = 0;
        double totalDamageDealt = 0;
        double totalDamageTaken = 0;
        String outcome = "in_progress";
        long endMs = 0;

        DungeonSession(String dungeonId, long startMs) {
            this.dungeonId = dungeonId;
            this.startMs = startMs;
        }

        void enterRoom(String roomId) {
            if (roomSequence.isEmpty() || !roomSequence.get(roomSequence.size() - 1).equals(roomId)) {
                roomSequence.add(roomId);
            }
        }
    }

    /**
     * Internal backtracking tracker.
     */
    private static final class BacktrackTracker {
        final List<String> visitedRooms = new ArrayList<>();
        int backtrackCount = 0;
        long lastBacktrackMs = 0;

        boolean recordRoomVisit(String roomId, long nowMs) {
            int prevIndex = visitedRooms.indexOf(roomId);
            if (prevIndex >= 0 && prevIndex < visitedRooms.size() - 1) {
                backtrackCount++;
                lastBacktrackMs = nowMs;
                return true;
            }
            if (visitedRooms.isEmpty() || !visitedRooms.get(visitedRooms.size() - 1).equals(roomId)) {
                visitedRooms.add(roomId);
            }
            return false;
        }
    }
}

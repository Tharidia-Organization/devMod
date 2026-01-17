package com.devmod.transport.executor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.transport.network.TransportNetworkHandler;
import com.devmod.transport.network.TransportPartyStatusPayload;

/**
 * Manages party arrival tracking for coordinated group teleportation.
 *
 * <p>Features:
 * <ul>
 *   <li>Phase-based arrival sequence (similar to QuestStartSequence)</li>
 *   <li>Position verification for arrival confirmation</li>
 *   <li>Timeout handling with partial success support</li>
 *   <li>Callbacks for completion/failure</li>
 * </ul>
 *
 * <p>Pattern based on QuestStartSequence's arrival confirmation system.
 */
public final class ArrivalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrivalManager.class);

    public static final ArrivalManager INSTANCE = new ArrivalManager();

    /** Default arrival timeout (30 seconds). */
    private static final int DEFAULT_ARRIVAL_TIMEOUT_TICKS = 600;

    /** Minimum required arrivals for success (1 = at least one player). */
    private static final int DEFAULT_MIN_ARRIVALS = 1;

    /** Interval for party status sync (ticks). */
    private static final int STATUS_SYNC_INTERVAL_TICKS = 10;

    /** Active arrival sessions by session ID. */
    private final Map<UUID, ArrivalSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStatusSync = new ConcurrentHashMap<>();

    private ArrivalManager() {}

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Starts tracking arrivals for a group teleport.
     *
     * @param sessionId Unique session identifier
     * @param expectedPlayers UUIDs of players expected to arrive
     * @param destinationLevel The destination level
     * @param destinationPos The destination position (center)
     * @param arrivalRadius Radius to check for arrival (blocks)
     * @param timeoutTicks Timeout in ticks
     * @param minArrivals Minimum arrivals required for success
     * @param onComplete Callback when all arrive or timeout
     * @return true if session started
     */
    public boolean startArrivalTracking(
            @Nonnull UUID sessionId,
            @Nonnull List<UUID> expectedPlayers,
            @Nonnull ServerLevel destinationLevel,
            @Nonnull BlockPos destinationPos,
            double arrivalRadius,
            int timeoutTicks,
            int minArrivals,
            @Nullable Consumer<ArrivalResult> onComplete) {

        if (activeSessions.containsKey(sessionId)) {
            LOGGER.warn("[ArrivalManager] Session {} already exists", sessionId);
            return false;
        }

        if (expectedPlayers.isEmpty()) {
            LOGGER.warn("[ArrivalManager] No players expected for session {}", sessionId);
            return false;
        }

        // Create arrival bounds
        Vec3 center = Vec3.atCenterOf(destinationPos);
        AABB arrivalBounds = new AABB(
            center.x - arrivalRadius,
            center.y - 2,
            center.z - arrivalRadius,
            center.x + arrivalRadius,
            center.y + 4,
            center.z + arrivalRadius
        );

        ArrivalSession session = new ArrivalSession(
            sessionId,
            new ArrayList<>(expectedPlayers),
            ConcurrentHashMap.newKeySet(),
            destinationLevel,
            arrivalBounds,
            timeoutTicks,
            minArrivals,
            Phase.WAITING,
            destinationLevel.getGameTime(),
            onComplete
        );

        activeSessions.put(sessionId, session);

        MinecraftServer server = destinationLevel.getServer();
        if (server != null) {
            sendStatusUpdate(session, server, TransportPartyStatusPayload.PHASE_WAITING, true);
        }

        LOGGER.info("[ArrivalManager] Started arrival tracking for session {} with {} expected players",
            sessionId, expectedPlayers.size());

        return true;
    }

    /**
     * Starts tracking with default timeout and minimum arrivals.
     */
    public boolean startArrivalTracking(
            @Nonnull UUID sessionId,
            @Nonnull List<UUID> expectedPlayers,
            @Nonnull ServerLevel destinationLevel,
            @Nonnull BlockPos destinationPos,
            double arrivalRadius,
            @Nullable Consumer<ArrivalResult> onComplete) {
        return startArrivalTracking(
            sessionId,
            expectedPlayers,
            destinationLevel,
            destinationPos,
            arrivalRadius,
            DEFAULT_ARRIVAL_TIMEOUT_TICKS,
            DEFAULT_MIN_ARRIVALS,
            onComplete
        );
    }

    /**
     * Confirms arrival for a player (called when player enters arrival zone).
     * Verifies the player is actually within the arrival bounds.
     *
     * @param sessionId The session ID
     * @param playerId The player's UUID
     * @param server The server (to get player position)
     * @return true if arrival was confirmed
     */
    public boolean confirmArrival(
            @Nonnull UUID sessionId,
            @Nonnull UUID playerId,
            @Nonnull MinecraftServer server) {

        ArrivalSession session = activeSessions.get(sessionId);
        if (session == null) {
            LOGGER.debug("[ArrivalManager] Arrival confirm for unknown session: {}", sessionId);
            return false;
        }

        if (session.phase != Phase.WAITING) {
            LOGGER.debug("[ArrivalManager] Arrival confirm in wrong phase: {}", session.phase);
            return false;
        }

        // Check if this player was expected
        if (!session.expectedPlayers.contains(playerId)) {
            LOGGER.debug("[ArrivalManager] Unexpected player {} in session {}", playerId, sessionId);
            return false;
        }

        // Get player and verify position
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            LOGGER.warn("[ArrivalManager] Player {} is offline", playerId);
            return false;
        }

        // Verify player is in the correct dimension
        if (!player.level().dimension().equals(session.destinationLevel.dimension())) {
            LOGGER.debug("[ArrivalManager] Player {} is in wrong dimension", playerId);
            return false;
        }

        // Verify player is within arrival bounds
        if (!session.arrivalBounds.intersects(Objects.requireNonNull(player.getBoundingBox()))) {
            LOGGER.warn("[ArrivalManager] Player {} not in arrival bounds for session {}",
                player.getName().getString(), sessionId);
            return false;
        }

        // Confirm arrival
        if (session.arrivedPlayers.add(playerId)) {
            LOGGER.info("[ArrivalManager] Player {} confirmed arrival ({}/{})",
                player.getName().getString(),
                session.arrivedPlayers.size(),
                session.expectedPlayers.size());

            // Check if all expected players have arrived
            if (session.arrivedPlayers.size() >= session.expectedPlayers.size()) {
                completeSession(session, true, "All players arrived", server);
            }

            sendStatusUpdate(session, server, TransportPartyStatusPayload.PHASE_WAITING, true);
            return true;
        }

        return false;
    }

    /**
     * Cancels an arrival session.
     */
    public boolean cancelSession(@Nonnull UUID sessionId, @Nullable String reason) {
        ArrivalSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return false;
        }

        session.phase = Phase.CANCELLED;

        MinecraftServer server = session.destinationLevel.getServer();
        if (server != null) {
            sendStatusUpdate(session, server, TransportPartyStatusPayload.PHASE_CANCELLED, true);
        }
        lastStatusSync.remove(session.sessionId);

        // Invoke callback with failure
        if (session.onComplete != null) {
            try {
                session.onComplete.accept(new ArrivalResult(
                    sessionId,
                    false,
                    new ArrayList<>(session.arrivedPlayers),
                    session.expectedPlayers.size(),
                    reason != null ? reason : "Cancelled"
                ));
            } catch (Exception e) {
                LOGGER.error("[ArrivalManager] Error in cancel callback for {}", sessionId, e);
            }
        }

        LOGGER.info("[ArrivalManager] Cancelled session {}: {}", sessionId, reason);
        return true;
    }

    /**
     * Checks if a session is active.
     */
    public boolean hasActiveSession(@Nonnull UUID sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    /**
     * Gets arrival progress for a session.
     *
     * @return [arrived, expected] or null if session not found
     */
    @Nullable
    public int[] getArrivalProgress(@Nonnull UUID sessionId) {
        ArrivalSession session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }
        return new int[] { session.arrivedPlayers.size(), session.expectedPlayers.size() };
    }

    /**
     * Gets the list of players who have arrived.
     */
    @Nonnull
    public List<UUID> getArrivedPlayers(@Nonnull UUID sessionId) {
        ArrivalSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Objects.requireNonNull(List.of());
        }
        return new ArrayList<>(session.arrivedPlayers);
    }

    // =========================================================================
    // Tick Processing
    // =========================================================================

    /**
     * Processes arrival timeouts. Called once per server tick.
     */
    public void tick(@Nonnull MinecraftServer server) {
        if (activeSessions.isEmpty()) {
            return;
        }

        long currentTick = server.overworld().getGameTime();

        Iterator<Map.Entry<UUID, ArrivalSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArrivalSession> entry = iterator.next();
            ArrivalSession session = entry.getValue();

            // Check timeout
            long elapsed = currentTick - session.startTick;
            if (elapsed >= session.timeoutTicks) {
                iterator.remove();

                // Check if we have minimum arrivals
                boolean success = session.arrivedPlayers.size() >= session.minArrivals;
                String message = success
                    ? "Timeout with minimum arrivals reached"
                    : "Timeout without minimum arrivals";

                completeSession(session, success, message, server);
                lastStatusSync.remove(session.sessionId);
                continue;
            }

            sendStatusUpdate(session, server, TransportPartyStatusPayload.PHASE_WAITING, false);
        }
    }

    /**
     * Completes a session (success or timeout).
     */
    private void completeSession(ArrivalSession session, boolean success, String message, @Nonnull MinecraftServer server) {
        activeSessions.remove(session.sessionId);
        session.phase = success ? Phase.COMPLETED : Phase.TIMEOUT;

        int phase = success ? TransportPartyStatusPayload.PHASE_COMPLETE : TransportPartyStatusPayload.PHASE_CANCELLED;
        sendStatusUpdate(session, server, phase, true);
        lastStatusSync.remove(session.sessionId);

        if (session.onComplete != null) {
            try {
                session.onComplete.accept(new ArrivalResult(
                    Objects.requireNonNull(session.sessionId),
                    success,
                    new ArrayList<>(session.arrivedPlayers),
                    session.expectedPlayers.size(),
                    Objects.requireNonNull(message)
                ));
            } catch (Exception e) {
                LOGGER.error("[ArrivalManager] Error in completion callback for {}", session.sessionId, e);
            }
        }

        LOGGER.info("[ArrivalManager] Session {} completed: {} ({}/{} arrived)",
            session.sessionId,
            message,
            session.arrivedPlayers.size(),
            session.expectedPlayers.size());
    }

    /**
     * Handles player disconnect during arrival tracking.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        for (ArrivalSession session : activeSessions.values()) {
            if (session.expectedPlayers.contains(playerId)) {
                // Remove from expected but don't fail the session yet
                // (timeout will handle if not enough arrive)
                session.expectedPlayers.remove(playerId);
                session.arrivedPlayers.remove(playerId);

                LOGGER.info("[ArrivalManager] Player {} disconnected from session {}",
                    playerId, session.sessionId);

                // If no expected players left, cancel
                if (session.expectedPlayers.isEmpty()) {
                    cancelSession(Objects.requireNonNull(session.sessionId), "All expected players disconnected");
                } else {
                    MinecraftServer server = session.destinationLevel.getServer();
                    if (server != null) {
                        sendStatusUpdate(session, server, TransportPartyStatusPayload.PHASE_WAITING, true);
                    }
                }
            }
        }
    }

    /**
     * Clears all sessions (for server shutdown).
     */
    public void clear() {
        activeSessions.clear();
        lastStatusSync.clear();
        LOGGER.info("[ArrivalManager] Cleared all arrival sessions");
    }

    /**
     * Gets the number of active sessions.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Phase of an arrival session.
     */
    public enum Phase {
        WAITING,
        COMPLETED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Result of an arrival session.
     */
    public record ArrivalResult(
        @Nonnull UUID sessionId,
        boolean success,
        @Nonnull List<UUID> arrivedPlayers,
        int expectedCount,
        @Nonnull String message
    ) {
        /**
         * Returns the number of players who arrived.
         */
        public int arrivedCount() {
            return arrivedPlayers.size();
        }

        /**
         * Returns true if all expected players arrived.
         */
        public boolean allArrived() {
            return arrivedPlayers.size() >= expectedCount;
        }
    }

    /**
     * Internal session state.
     */
    private static class ArrivalSession {
        final UUID sessionId;
        final List<UUID> expectedPlayers;
        final Set<UUID> arrivedPlayers;
        final ServerLevel destinationLevel;
        final AABB arrivalBounds;
        final int timeoutTicks;
        final int minArrivals;
        final long startTick;
        @Nullable final Consumer<ArrivalResult> onComplete;
        Phase phase;

        ArrivalSession(
                UUID sessionId,
                List<UUID> expectedPlayers,
                Set<UUID> arrivedPlayers,
                ServerLevel destinationLevel,
                AABB arrivalBounds,
                int timeoutTicks,
                int minArrivals,
                Phase phase,
                long startTick,
                @Nullable Consumer<ArrivalResult> onComplete) {
            this.sessionId = sessionId;
            this.expectedPlayers = expectedPlayers;
            this.arrivedPlayers = arrivedPlayers;
            this.destinationLevel = destinationLevel;
            this.arrivalBounds = arrivalBounds;
            this.timeoutTicks = timeoutTicks;
            this.minArrivals = minArrivals;
            this.phase = phase;
            this.startTick = startTick;
            this.onComplete = onComplete;
        }
    }

    private void sendStatusUpdate(
        @Nonnull ArrivalSession session,
        @Nonnull MinecraftServer server,
        int phase,
        boolean force
    ) {
        long now = server.overworld().getGameTime();
        if (!force) {
            long last = lastStatusSync.getOrDefault(session.sessionId, now - STATUS_SYNC_INTERVAL_TICKS);
            if (now - last < STATUS_SYNC_INTERVAL_TICKS) {
                return;
            }
        }

        TransportPartyStatusPayload payload = new TransportPartyStatusPayload(
            session.sessionId,
            phase,
            session.arrivedPlayers.size(),
            session.expectedPlayers.size(),
            new ArrayList<>(session.arrivedPlayers)
        );

        for (UUID playerId : session.expectedPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
            if (player != null) {
                TransportNetworkHandler.sendPartyStatus(player, payload);
            }
        }

        lastStatusSync.put(session.sessionId, now);
    }
}

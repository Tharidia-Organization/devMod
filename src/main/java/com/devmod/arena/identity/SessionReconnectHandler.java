package com.devmod.arena.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class SessionReconnectHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionReconnectHandler.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);

    private final Map<UUID, ReconnectState> reconnectStates = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToArena = new ConcurrentHashMap<>();

    /**
     * Tracks reconnect state for a player/arena combination.
     */
    public record ReconnectState(
        UUID playerId,
        UUID arenaId,
        UUID currentSessionId,
        int reconnectCount,
        Instant lastConnected,
        Instant lastDisconnected
    ) {
        /**
         * Creates a new state for first connection.
         */
        public static ReconnectState firstConnection(UUID playerId, UUID arenaId) {
            return new ReconnectState(
                playerId,
                arenaId,
                UUID.randomUUID(),
                0,
                Instant.now(),
                null
            );
        }

        /**
         * Creates a new state for reconnection.
         */
        public ReconnectState reconnect() {
            return new ReconnectState(
                playerId,
                arenaId,
                UUID.randomUUID(),
                reconnectCount + 1,
                Instant.now(),
                lastDisconnected
            );
        }

        /**
         * Creates a new state for disconnection.
         */
        public ReconnectState disconnect() {
            return new ReconnectState(
                playerId,
                arenaId,
                currentSessionId,
                reconnectCount,
                lastConnected,
                Instant.now()
            );
        }

        /**
         * Checks if this session is expired.
         */
        public boolean isExpired() {
            if (lastDisconnected == null) {
                return false;
            }
            return Duration.between(lastDisconnected, Instant.now()).compareTo(SESSION_TIMEOUT) > 0;
        }
    }

    /**
     * Called when a player connects to an arena.
     *
     * @return The session ID for this connection
     */
    public UUID onConnect(UUID playerId, UUID arenaId) {
        ReconnectState existingState = reconnectStates.get(playerId);

        ReconnectState newState;
        if (existingState == null || !existingState.arenaId().equals(arenaId)) {
            // First connection to this arena
            newState = ReconnectState.firstConnection(playerId, arenaId);
            LOGGER.info("Player {} connected to arena {} (first connection)", playerId, arenaId);
        } else if (existingState.isExpired()) {
            // Session expired, treat as first connection
            newState = ReconnectState.firstConnection(playerId, arenaId);
            LOGGER.info("Player {} connected to arena {} (session expired)", playerId, arenaId);
        } else {
            // Reconnection
            newState = existingState.reconnect();
            LOGGER.info("Player {} reconnected to arena {} (reconnect #{})",
                playerId, arenaId, newState.reconnectCount());
        }

        reconnectStates.put(playerId, newState);
        playerToArena.put(playerId, arenaId);

        return newState.currentSessionId();
    }

    /**
     * Called when a player disconnects.
     */
    public void onDisconnect(UUID playerId) {
        ReconnectState state = reconnectStates.get(playerId);
        if (state != null) {
            reconnectStates.put(playerId, state.disconnect());
            LOGGER.debug("Player {} disconnected from arena {}", playerId, state.arenaId());
        }
    }

    /**
     * Returns the current reconnect state for a player.
     */
    public Optional<ReconnectState> getState(UUID playerId) {
        return Optional.ofNullable(reconnectStates.get(playerId));
    }

    /**
     * Returns the current session ID for a player.
     */
    public Optional<UUID> getCurrentSessionId(UUID playerId) {
        return getState(playerId).map(ReconnectState::currentSessionId);
    }

    /**
     * Returns the reconnect count for a player.
     */
    public int getReconnectCount(UUID playerId) {
        return getState(playerId).map(ReconnectState::reconnectCount).orElse(0);
    }

    /**
     * Returns the arena a player is connected to.
     */
    public Optional<UUID> getPlayerArena(UUID playerId) {
        return Optional.ofNullable(playerToArena.get(playerId));
    }

    /**
     * Cleans up expired sessions.
     */
    public int cleanupExpiredSessions() {
        int cleaned = 0;
        var iterator = reconnectStates.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                playerToArena.remove(entry.getKey());
                cleaned++;
            }
        }
        if (cleaned > 0) {
            LOGGER.debug("Cleaned up {} expired sessions", cleaned);
        }
        return cleaned;
    }

    /**
     * Clears all state for an arena (when arena is destroyed).
     */
    public void clearArena(UUID arenaId) {
        var iterator = reconnectStates.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().arenaId().equals(arenaId)) {
                iterator.remove();
                playerToArena.remove(entry.getKey());
            }
        }
    }
}

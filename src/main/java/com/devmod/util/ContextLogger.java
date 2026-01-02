package com.devmod.util;

import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * Structured logging with MDC (Mapped Diagnostic Context).
 *
 * Automatically adds contextual information to all log messages:
 * <ul>
 *   <li>playerUuid - Current player's UUID</li>
 *   <li>playerName - Current player's name</li>
 *   <li>questId - Active quest ID (if in quest)</li>
 *   <li>arenaId - Arena instance ID (if in arena)</li>
 *   <li>partyId - Party ID (if in party)</li>
 *   <li>requestId - Correlation ID for request tracing</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Set context at entry point (e.g., packet handler)
 * try (var ctx = ContextLogger.forPlayer(player)) {
 *     // All logs in this scope include player context
 *     LOGGER.info("Processing action"); // Includes [playerUuid=xxx]
 * }
 *
 * // With quest context
 * try (var ctx = ContextLogger.forQuest(player, questId, arenaId)) {
 *     LOGGER.info("Wave completed"); // Includes player + quest + arena
 * }
 * </pre>
 */
public final class ContextLogger {

    // MDC keys
    public static final String KEY_PLAYER_UUID = "playerUuid";
    public static final String KEY_PLAYER_NAME = "playerName";
    public static final String KEY_QUEST_ID = "questId";
    public static final String KEY_ARENA_ID = "arenaId";
    public static final String KEY_PARTY_ID = "partyId";
    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_ACTION = "action";
    public static final String KEY_SOURCE = "source";

    private ContextLogger() {}

    // ============================================================================
    // CONTEXT SCOPES
    // ============================================================================

    /**
     * Create a logging context for a player.
     * Use in try-with-resources for automatic cleanup.
     */
    public static LoggingContext forPlayer(UUID playerUuid, String playerName) {
        LoggingContext ctx = new LoggingContext();
        ctx.put(KEY_PLAYER_UUID, playerUuid.toString());
        ctx.put(KEY_PLAYER_NAME, playerName);
        return ctx;
    }

    /**
     * Create a logging context for a quest/arena session.
     */
    public static LoggingContext forQuest(UUID playerUuid, String playerName,
                                          @Nullable String questId, @Nullable UUID arenaId) {
        LoggingContext ctx = forPlayer(playerUuid, playerName);
        if (questId != null) {
            ctx.put(KEY_QUEST_ID, questId);
        }
        if (arenaId != null) {
            ctx.put(KEY_ARENA_ID, arenaId.toString());
        }
        return ctx;
    }

    /**
     * Create a logging context for a party action.
     */
    public static LoggingContext forParty(UUID playerUuid, String playerName, UUID partyId) {
        LoggingContext ctx = forPlayer(playerUuid, playerName);
        ctx.put(KEY_PARTY_ID, partyId.toString());
        return ctx;
    }

    /**
     * Create a logging context for a network request.
     */
    public static LoggingContext forRequest(String requestId, String action) {
        LoggingContext ctx = new LoggingContext();
        ctx.put(KEY_REQUEST_ID, requestId);
        ctx.put(KEY_ACTION, action);
        return ctx;
    }

    /**
     * Create a logging context from packet source.
     */
    public static LoggingContext forPacket(UUID playerUuid, String playerName, String packetType) {
        LoggingContext ctx = forPlayer(playerUuid, playerName);
        ctx.put(KEY_ACTION, packetType);
        ctx.put(KEY_SOURCE, "network");
        return ctx;
    }

    // ============================================================================
    // DIRECT CONTEXT MANIPULATION
    // ============================================================================

    /**
     * Set a single context value (manual management).
     */
    public static void put(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * Set player context without scope (manual cleanup required).
     */
    public static void setPlayer(UUID playerUuid, String playerName) {
        MDC.put(KEY_PLAYER_UUID, playerUuid.toString());
        MDC.put(KEY_PLAYER_NAME, playerName);
    }

    /**
     * Set quest context without scope (manual cleanup required).
     */
    public static void setQuest(String questId, UUID arenaId) {
        if (questId != null) MDC.put(KEY_QUEST_ID, questId);
        if (arenaId != null) MDC.put(KEY_ARENA_ID, arenaId.toString());
    }

    /**
     * Clear all context.
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Clear specific key.
     */
    public static void remove(String key) {
        MDC.remove(key);
    }

    /**
     * Get current context value.
     */
    @Nullable
    public static String get(String key) {
        return MDC.get(key);
    }

    // ============================================================================
    // CONVENIENCE LOGGING WITH CONTEXT
    // ============================================================================

    /**
     * Log with temporary context (one-shot).
     */
    public static void infoWithContext(Logger logger, String key, String value, String message) {
        try (LoggingContext ctx = new LoggingContext()) {
            ctx.put(key, value);
            logger.info(message);
        }
    }

    /**
     * Log with temporary context (one-shot, with supplier).
     */
    public static void infoWithContext(Logger logger, String key, String value, Supplier<String> messageSupplier) {
        if (logger.isInfoEnabled()) {
            try (LoggingContext ctx = new LoggingContext()) {
                ctx.put(key, value);
                logger.info(messageSupplier.get());
            }
        }
    }

    /**
     * Log debug with temporary context.
     */
    public static void debugWithContext(Logger logger, String key, String value, Supplier<String> messageSupplier) {
        if (logger.isDebugEnabled()) {
            try (LoggingContext ctx = new LoggingContext()) {
                ctx.put(key, value);
                logger.debug(messageSupplier.get());
            }
        }
    }

    /**
     * Log warn with temporary context.
     */
    public static void warnWithContext(Logger logger, String key, String value, String message) {
        try (LoggingContext ctx = new LoggingContext()) {
            ctx.put(key, value);
            logger.warn(message);
        }
    }

    /**
     * Log error with temporary context and exception.
     */
    public static void errorWithContext(Logger logger, String key, String value, String message, Throwable t) {
        try (LoggingContext ctx = new LoggingContext()) {
            ctx.put(key, value);
            logger.error(message, t);
        }
    }

    // ============================================================================
    // CONTEXT SCOPE CLASS
    // ============================================================================

    /**
     * Auto-closeable logging context that cleans up MDC on close.
     */
    public static class LoggingContext implements AutoCloseable {
        private int keysAdded = 0;
        private final String[] addedKeys = new String[10]; // Max 10 keys per context

        /**
         * Add a key-value pair to the MDC context.
         */
        public LoggingContext put(String key, @Nullable String value) {
            if (value != null && keysAdded < addedKeys.length) {
                MDC.put(key, value);
                addedKeys[keysAdded++] = key;
            }
            return this;
        }

        /**
         * Add player UUID.
         */
        public LoggingContext withPlayer(UUID playerUuid, String playerName) {
            put(KEY_PLAYER_UUID, playerUuid.toString());
            put(KEY_PLAYER_NAME, playerName);
            return this;
        }

        /**
         * Add quest context.
         */
        public LoggingContext withQuest(String questId) {
            put(KEY_QUEST_ID, questId);
            return this;
        }

        /**
         * Add arena context.
         */
        public LoggingContext withArena(UUID arenaId) {
            put(KEY_ARENA_ID, arenaId.toString());
            return this;
        }

        /**
         * Add party context.
         */
        public LoggingContext withParty(UUID partyId) {
            put(KEY_PARTY_ID, partyId.toString());
            return this;
        }

        /**
         * Add request ID for correlation.
         */
        public LoggingContext withRequestId(String requestId) {
            put(KEY_REQUEST_ID, requestId);
            return this;
        }

        /**
         * Add action identifier.
         */
        public LoggingContext withAction(String action) {
            put(KEY_ACTION, action);
            return this;
        }

        @Override
        public void close() {
            // Remove only keys we added (preserve parent context)
            for (int i = 0; i < keysAdded; i++) {
                MDC.remove(addedKeys[i]);
            }
        }
    }

    // ============================================================================
    // UTILITIES
    // ============================================================================

    /**
     * Generate a unique request ID for correlation.
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get current context as formatted string (for manual inclusion in messages).
     */
    public static String getContextString() {
        StringBuilder sb = new StringBuilder();
        String playerUuid = MDC.get(KEY_PLAYER_UUID);
        String questId = MDC.get(KEY_QUEST_ID);
        String arenaId = MDC.get(KEY_ARENA_ID);

        if (playerUuid != null) {
            sb.append("player=").append(playerUuid.substring(0, 8)).append("...");
        }
        if (questId != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("quest=").append(questId);
        }
        if (arenaId != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("arena=").append(arenaId.substring(0, 8)).append("...");
        }

        return sb.length() > 0 ? "[" + sb + "] " : "";
    }

    /**
     * Check if currently in a quest context.
     */
    public static boolean isInQuest() {
        return MDC.get(KEY_QUEST_ID) != null;
    }

    /**
     * Check if currently in an arena context.
     */
    public static boolean isInArena() {
        return MDC.get(KEY_ARENA_ID) != null;
    }

    /**
     * Check if player context is set.
     */
    public static boolean hasPlayerContext() {
        return MDC.get(KEY_PLAYER_UUID) != null;
    }
}

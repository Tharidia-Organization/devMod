package com.devmod.arena.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * DD31: Command Permissions - modello granulare + audit log
 *
 * Audit logging for arena commands, especially mutating operations.
 * Uses a separate logger (arena.audit) for easy filtering and analysis.
 */
public class ArenaCommandAudit {

    private static final ArenaCommandAudit INSTANCE = new ArenaCommandAudit();

    /** Separate logger for audit trail - configured in log4j2.xml as arena.audit */
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("arena.audit");

    /** Standard logger for internal errors */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCommandAudit.class);

    /** ISO 8601 timestamp format */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .withZone(ZoneId.systemDefault());

    /** Audit enabled flag */
    private volatile boolean enabled = true;

    /** Log level for non-mutating commands (false = don't log) */
    private volatile boolean logNonMutating = false;

    private ArenaCommandAudit() {}

    public static ArenaCommandAudit getInstance() {
        return INSTANCE;
    }

    /**
     * Logs a command execution (for mutating commands)
     *
     * @param playerId The player who executed the command
     * @param playerName The player's name
     * @param category The command category
     * @param command The full command string
     * @param arenaId The affected arena ID (if any)
     * @param success Whether the command succeeded
     */
    public void log(UUID playerId, String playerName, ArenaCommandPermissions.CommandCategory category,
                    String command, String arenaId, boolean success) {
        if (!enabled) {
            return;
        }

        // Skip non-mutating commands unless explicitly logging them
        if (!category.isMutating() && !logNonMutating) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String status = success ? "SUCCESS" : "FAILED";

        // Structured log format: [timestamp] [status] [category] player=uuid:name arena=id command="..."
        String logEntry = String.format(
                "[%s] [%s] [%s] player=%s:%s arena=%s command=\"%s\"",
                timestamp,
                status,
                category.name(),
                playerId,
                playerName,
                arenaId != null ? arenaId : "N/A",
                sanitizeCommand(command)
        );

        AUDIT_LOGGER.info(logEntry);
    }

    /**
     * Logs a command execution (simplified - no arena context)
     *
     * @param playerId The player who executed the command
     * @param playerName The player's name
     * @param category The command category
     * @param command The full command string
     * @param success Whether the command succeeded
     */
    public void log(UUID playerId, String playerName, ArenaCommandPermissions.CommandCategory category,
                    String command, boolean success) {
        log(playerId, playerName, category, command, null, success);
    }

    /**
     * Logs a permission denied event
     *
     * @param playerId The player who was denied
     * @param playerName The player's name
     * @param category The command category that was denied
     * @param requiredLevel The required permission level
     * @param actualLevel The player's actual permission level
     */
    public void logPermissionDenied(UUID playerId, String playerName,
                                    ArenaCommandPermissions.CommandCategory category,
                                    ArenaCommandPermissions.PermissionLevel requiredLevel,
                                    ArenaCommandPermissions.PermissionLevel actualLevel) {
        if (!enabled) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());

        String logEntry = String.format(
                "[%s] [DENIED] [%s] player=%s:%s required=%s actual=%s",
                timestamp,
                category.name(),
                playerId,
                playerName,
                requiredLevel.name(),
                actualLevel.name()
        );

        AUDIT_LOGGER.warn(logEntry);
    }

    /**
     * Logs a permission level change
     *
     * @param playerId The player whose permission changed
     * @param newLevel The new permission level
     */
    public void logPermissionChange(UUID playerId, ArenaCommandPermissions.PermissionLevel newLevel) {
        if (!enabled) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());

        String logEntry = String.format(
                "[%s] [PERM_CHANGE] player=%s new_level=%s",
                timestamp,
                playerId,
                newLevel.name()
        );

        AUDIT_LOGGER.info(logEntry);
    }

    /**
     * Logs an arena lifecycle event
     *
     * @param event The event type (CREATED, STARTED, ENDED, DELETED, etc.)
     * @param arenaId The arena ID
     * @param details Additional details
     */
    public void logArenaEvent(String event, String arenaId, String details) {
        if (!enabled) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());

        String logEntry = String.format(
                "[%s] [ARENA_%s] arena=%s details=\"%s\"",
                timestamp,
                event,
                arenaId,
                details != null ? sanitizeCommand(details) : ""
        );

        AUDIT_LOGGER.info(logEntry);
    }

    /**
     * Logs a security event (suspicious activity, rate limiting, etc.)
     *
     * @param event The event type
     * @param playerId The involved player
     * @param details Details of the event
     */
    public void logSecurityEvent(String event, UUID playerId, String details) {
        if (!enabled) {
            return;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());

        String logEntry = String.format(
                "[%s] [SECURITY_%s] player=%s details=\"%s\"",
                timestamp,
                event,
                playerId,
                sanitizeCommand(details)
        );

        AUDIT_LOGGER.warn(logEntry);
    }

    /**
     * Sanitizes a command string for safe logging
     * Removes newlines and truncates long commands
     */
    private String sanitizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String sanitized = command.replace("\n", " ").replace("\r", " ");
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 497) + "...";
        }
        return sanitized;
    }

    /**
     * Enables or disables audit logging
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether audit logging is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether to log non-mutating commands
     */
    public void setLogNonMutating(boolean logNonMutating) {
        this.logNonMutating = logNonMutating;
    }

    /**
     * Gets whether non-mutating commands are logged
     */
    public boolean isLogNonMutating() {
        return logNonMutating;
    }
}

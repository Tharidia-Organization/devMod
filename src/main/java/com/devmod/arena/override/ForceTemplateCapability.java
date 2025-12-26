package com.devmod.arena.override;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForceTemplateCapability {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForceTemplateCapability.class);

    /**
     * DD29: Permission required to force templates.
     */
    public static final String PERMISSION_FORCE_TEMPLATE = "devmod.arena.force_template";

    /**
     * DD29: Default session duration (30 minutes).
     */
    public static final Duration DEFAULT_SESSION_DURATION = Duration.ofMinutes(30);

    /**
     * DD29: Maximum session duration (4 hours).
     */
    public static final Duration MAX_SESSION_DURATION = Duration.ofHours(4);

    /**
     * Represents a force template session.
     */
    public record ForceSession(
        UUID sessionId,
        UUID playerId,
        String templateId,
        Instant createdAt,
        Instant expiresAt,
        String reason,
        Set<String> grantedBy
    ) {
        /**
         * Checks if session is expired.
         */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        /**
         * Gets remaining duration.
         */
        public Duration remaining() {
            if (isExpired()) {
                return Duration.ZERO;
            }
            return Duration.between(Instant.now(), expiresAt);
        }
    }

    /**
     * Result of a capability check.
     */
    public record CapabilityResult(
        boolean allowed,
        String templateId,
        String reason,
        Optional<ForceSession> session
    ) {
        public static CapabilityResult allowed(String templateId, ForceSession session) {
            return new CapabilityResult(true, templateId, "Session active", Optional.of(session));
        }

        public static CapabilityResult denied(String reason) {
            return new CapabilityResult(false, null, reason, Optional.empty());
        }
    }

    /**
     * Interface for permission checking.
     */
    public interface PermissionChecker {
        boolean hasPermission(UUID playerId, String permission);
        Set<String> getPlayerRoles(UUID playerId);
    }

    /**
     * Interface for capability telemetry.
     */
    public interface CapabilityTelemetry {
        void onSessionCreated(ForceSession session);
        void onSessionExpired(ForceSession session);
        void onSessionRevoked(ForceSession session, String revokedBy);
        void onCapabilityDenied(UUID playerId, String templateId, String reason);
    }

    private final PermissionChecker permissionChecker;
    private final CapabilityTelemetry telemetry;
    private final Map<UUID, ForceSession> activeSessions = new ConcurrentHashMap<>();

    public ForceTemplateCapability(PermissionChecker permissionChecker, CapabilityTelemetry telemetry) {
        this.permissionChecker = permissionChecker;
        this.telemetry = telemetry;
    }

    /**
     * Creates a force template session for a player.
     * DD29: Session-scoped with capability check.
     *
     * @param playerId the player ID
     * @param templateId the template to force
     * @param duration session duration
     * @param reason reason for forcing
     * @return the created session, or empty if not permitted
     */
    public Optional<ForceSession> createSession(
            UUID playerId,
            String templateId,
            Duration duration,
            String reason) {

        // Check permission
        if (!permissionChecker.hasPermission(playerId, PERMISSION_FORCE_TEMPLATE)) {
            LOGGER.warn("Player {} denied force template capability - no permission", playerId);
            if (telemetry != null) {
                telemetry.onCapabilityDenied(playerId, templateId, "Missing permission");
            }
            return Optional.empty();
        }

        // Validate duration
        Duration effectiveDuration = duration;
        if (effectiveDuration == null || effectiveDuration.isNegative()) {
            effectiveDuration = DEFAULT_SESSION_DURATION;
        } else if (effectiveDuration.compareTo(MAX_SESSION_DURATION) > 0) {
            effectiveDuration = MAX_SESSION_DURATION;
            LOGGER.info("Session duration capped to max for player {}", playerId);
        }

        // Create session
        ForceSession session = new ForceSession(
            UUID.randomUUID(),
            playerId,
            templateId,
            Instant.now(),
            Instant.now().plus(effectiveDuration),
            reason,
            permissionChecker.getPlayerRoles(playerId)
        );

        // Store session (replace any existing)
        ForceSession previous = activeSessions.put(playerId, session);
        if (previous != null) {
            LOGGER.info("Replaced existing force session for player {}", playerId);
        }

        LOGGER.info("Created force template session: player={}, template={}, duration={}",
            playerId, templateId, effectiveDuration);

        if (telemetry != null) {
            telemetry.onSessionCreated(session);
        }

        return Optional.of(session);
    }

    /**
     * Creates a session with default duration.
     */
    public Optional<ForceSession> createSession(UUID playerId, String templateId, String reason) {
        return createSession(playerId, templateId, DEFAULT_SESSION_DURATION, reason);
    }

    /**
     * Checks if a player has an active force template capability.
     * DD29: Returns capability result with session info.
     *
     * @param playerId the player ID
     * @return capability result
     */
    public CapabilityResult checkCapability(UUID playerId) {
        ForceSession session = activeSessions.get(playerId);

        if (session == null) {
            return CapabilityResult.denied("No active session");
        }

        if (session.isExpired()) {
            activeSessions.remove(playerId);
            if (telemetry != null) {
                telemetry.onSessionExpired(session);
            }
            return CapabilityResult.denied("Session expired");
        }

        return CapabilityResult.allowed(session.templateId(), session);
    }

    /**
     * Gets the forced template ID for a player, if any.
     *
     * @param playerId the player ID
     * @return forced template ID or empty
     */
    public Optional<String> getForcedTemplate(UUID playerId) {
        CapabilityResult result = checkCapability(playerId);
        return result.allowed() ? Optional.of(result.templateId()) : Optional.empty();
    }

    /**
     * Gets the active session for a player.
     *
     * @param playerId the player ID
     * @return active session or empty
     */
    public Optional<ForceSession> getSession(UUID playerId) {
        CapabilityResult result = checkCapability(playerId);
        return result.session();
    }

    /**
     * Revokes a player's force template session.
     *
     * @param playerId the player whose session to revoke
     * @param revokedBy who revoked the session
     * @return true if a session was revoked
     */
    public boolean revokeSession(UUID playerId, String revokedBy) {
        ForceSession session = activeSessions.remove(playerId);

        if (session != null) {
            LOGGER.info("Revoked force session for player {} by {}", playerId, revokedBy);
            if (telemetry != null) {
                telemetry.onSessionRevoked(session, revokedBy);
            }
            return true;
        }

        return false;
    }

    /**
     * Cleans up expired sessions.
     *
     * @return number of expired sessions removed
     */
    public int cleanupExpiredSessions() {
        int removed = 0;

        for (Map.Entry<UUID, ForceSession> entry : activeSessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                ForceSession session = activeSessions.remove(entry.getKey());
                if (session != null) {
                    removed++;
                    if (telemetry != null) {
                        telemetry.onSessionExpired(session);
                    }
                }
            }
        }

        if (removed > 0) {
            LOGGER.debug("Cleaned up {} expired force sessions", removed);
        }

        return removed;
    }

    /**
     * Gets the number of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return (int) activeSessions.values().stream()
            .filter(s -> !s.isExpired())
            .count();
    }

    /**
     * Gets all active sessions.
     *
     * @return map of player ID to session
     */
    public Map<UUID, ForceSession> getActiveSessions() {
        Map<UUID, ForceSession> active = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, ForceSession> entry : activeSessions.entrySet()) {
            if (!entry.getValue().isExpired()) {
                active.put(entry.getKey(), entry.getValue());
            }
        }
        return active;
    }
}

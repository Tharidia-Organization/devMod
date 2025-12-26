package com.devmod.arena.obsolescence;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for template obsolescence with session-safe removal (DD71).
 *
 * <p>Ensures templates are not removed while sessions are active.
 * Provides successor mapping for deprecated templates.
 */
public class TemplateObsolescenceHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateObsolescenceHandler.class);

    private final Map<String, SuccessorMapping> successorMappings = new ConcurrentHashMap<>();
    private final Map<String, TemplateUsageTracker> usageTrackers = new ConcurrentHashMap<>();
    private final Map<String, ObsolescenceState> obsolescenceStates = new ConcurrentHashMap<>();

    /**
     * Registers a successor mapping for a deprecated template.
     */
    public void registerSuccessor(String deprecatedTemplateId, String successorTemplateId, String migrationNotes) {
        successorMappings.put(deprecatedTemplateId, new SuccessorMapping(
            deprecatedTemplateId,
            successorTemplateId,
            migrationNotes,
            Instant.now()
        ));
        LOGGER.info("Registered successor for template {}: {}", deprecatedTemplateId, successorTemplateId);
    }

    /**
     * Looks up the successor for a deprecated template.
     */
    public Optional<String> findSuccessor(String templateId) {
        SuccessorMapping mapping = successorMappings.get(templateId);
        if (mapping != null) {
            return Optional.of(mapping.successorTemplateId());
        }
        return Optional.empty();
    }

    /**
     * Gets the full successor mapping for a template.
     */
    public Optional<SuccessorMapping> getSuccessorMapping(String templateId) {
        return Optional.ofNullable(successorMappings.get(templateId));
    }

    /**
     * Resolves a template ID, following successor chain if deprecated.
     */
    public String resolveTemplate(String templateId) {
        String current = templateId;
        Set<String> visited = ConcurrentHashMap.newKeySet();

        while (successorMappings.containsKey(current)) {
            if (!visited.add(current)) {
                LOGGER.warn("Circular successor reference detected for template: {}", current);
                return current;
            }
            current = successorMappings.get(current).successorTemplateId();
        }

        return current;
    }

    /**
     * Marks a template as obsolete, starting the deprecation process.
     */
    public void markObsolete(String templateId, String reason) {
        obsolescenceStates.put(templateId, new ObsolescenceState(
            templateId,
            ObsolescencePhase.DEPRECATED,
            Instant.now(),
            reason
        ));
        LOGGER.warn("Template {} marked as obsolete: {}", templateId, reason);
    }

    /**
     * Records a session start for a template.
     */
    public void onSessionStart(String templateId, String sessionId) {
        usageTrackers.computeIfAbsent(templateId, k -> new TemplateUsageTracker())
            .addSession(sessionId);
    }

    /**
     * Records a session end for a template.
     */
    public void onSessionEnd(String templateId, String sessionId) {
        TemplateUsageTracker tracker = usageTrackers.get(templateId);
        if (tracker != null) {
            tracker.removeSession(sessionId);
        }
    }

    /**
     * Checks if a template can be safely removed.
     */
    public RemovalCheck canRemove(String templateId) {
        TemplateUsageTracker tracker = usageTrackers.get(templateId);

        if (tracker == null || tracker.getActiveSessionCount() == 0) {
            return new RemovalCheck(true, 0, "No active sessions");
        }

        int activeSessions = tracker.getActiveSessionCount();
        return new RemovalCheck(false, activeSessions,
            String.format("Cannot remove: %d active sessions", activeSessions));
    }

    /**
     * Attempts to remove a template, waiting for sessions to end.
     *
     * @param templateId The template to remove
     * @param maxWait Maximum time to wait for sessions
     * @return true if removed, false if sessions still active
     */
    public boolean tryRemove(String templateId, Duration maxWait) {
        Instant deadline = Instant.now().plus(maxWait);

        while (Instant.now().isBefore(deadline)) {
            RemovalCheck check = canRemove(templateId);
            if (check.canRemove()) {
                performRemoval(templateId);
                return true;
            }

            LOGGER.info("Waiting for {} sessions to end on template {}",
                check.activeSessionCount(), templateId);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        LOGGER.warn("Timeout waiting for sessions on template {}", templateId);
        return false;
    }

    /**
     * Gets the current obsolescence state for a template.
     */
    public Optional<ObsolescenceState> getObsolescenceState(String templateId) {
        return Optional.ofNullable(obsolescenceStates.get(templateId));
    }

    /**
     * Advances the obsolescence phase for a template.
     */
    public void advancePhase(String templateId) {
        ObsolescenceState current = obsolescenceStates.get(templateId);
        if (current == null) {
            return;
        }

        ObsolescencePhase nextPhase = current.phase().next();
        if (nextPhase != null) {
            obsolescenceStates.put(templateId, new ObsolescenceState(
                templateId,
                nextPhase,
                Instant.now(),
                current.reason()
            ));
            LOGGER.info("Template {} advanced to phase {}", templateId, nextPhase);
        }
    }

    private void performRemoval(String templateId) {
        obsolescenceStates.put(templateId, new ObsolescenceState(
            templateId,
            ObsolescencePhase.REMOVED,
            Instant.now(),
            "Session-safe removal completed"
        ));
        usageTrackers.remove(templateId);
        LOGGER.info("Template {} removed", templateId);
    }

    // ========== Data Types ==========

    public record SuccessorMapping(
        String deprecatedTemplateId,
        String successorTemplateId,
        String migrationNotes,
        Instant registeredAt
    ) {}

    public record RemovalCheck(
        boolean canRemove,
        int activeSessionCount,
        String message
    ) {}

    public record ObsolescenceState(
        String templateId,
        ObsolescencePhase phase,
        Instant phaseStartedAt,
        String reason
    ) {}

    public enum ObsolescencePhase {
        DEPRECATED,
        WARNING,
        BLOCKED,
        REMOVED;

        public ObsolescencePhase next() {
            return switch (this) {
                case DEPRECATED -> WARNING;
                case WARNING -> BLOCKED;
                case BLOCKED -> REMOVED;
                case REMOVED -> null;
            };
        }
    }

    /**
     * Tracks active sessions for a template.
     */
    private static class TemplateUsageTracker {
        private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
        private final AtomicInteger totalSessions = new AtomicInteger(0);

        void addSession(String sessionId) {
            activeSessions.add(sessionId);
            totalSessions.incrementAndGet();
        }

        void removeSession(String sessionId) {
            activeSessions.remove(sessionId);
        }

        int getActiveSessionCount() {
            return activeSessions.size();
        }
    }
}

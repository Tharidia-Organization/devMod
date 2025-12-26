package com.devmod.arena.snapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Detects version and configuration drift at end of arena template sessions (DD16).
 *
 * This component compares the initial snapshot taken at session start
 * with the current state at session end to detect any changes.
 */
public class VersionDriftDetector {

    private static final Logger LOGGER = Logger.getLogger(VersionDriftDetector.class.getName());

    /**
     * Stores active session snapshots for comparison at session end.
     */
    private final Map<UUID, ArenaTemplateSnapshot> activeSnapshots;

    /**
     * Callback interface for drift detection events.
     */
    @FunctionalInterface
    public interface DriftCallback {
        void onDriftDetected(DriftResult result);
    }

    private volatile DriftCallback driftCallback;

    /**
     * Creates a new VersionDriftDetector.
     */
    public VersionDriftDetector() {
        this.activeSnapshots = new ConcurrentHashMap<>();
    }

    /**
     * Registers a callback for drift detection events.
     */
    public void onDriftDetected(DriftCallback callback) {
        this.driftCallback = callback;
    }

    /**
     * Captures the initial snapshot at session start.
     *
     * @param snapshot The initial session snapshot
     */
    public void captureInitialSnapshot(ArenaTemplateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        activeSnapshots.put(snapshot.sessionId(), snapshot);
        LOGGER.fine("Captured initial snapshot for session: " + snapshot.sessionId());
    }

    /**
     * Detects drift at session end by comparing with initial snapshot.
     *
     * @param sessionId The session ID
     * @param currentVersion The current template version
     * @param currentConfiguration The current configuration
     * @return DriftResult containing drift analysis
     */
    public DriftResult detectDrift(
            UUID sessionId,
            String currentVersion,
            Map<String, Object> currentConfiguration) {

        ArenaTemplateSnapshot initialSnapshot = activeSnapshots.remove(sessionId);

        if (initialSnapshot == null) {
            LOGGER.warning("No initial snapshot found for session: " + sessionId);
            return DriftResult.noSnapshot(sessionId);
        }

        // Create current snapshot for comparison
        ArenaTemplateSnapshot currentSnapshot = ArenaTemplateSnapshot.builder()
            .sessionId(sessionId)
            .templateId(initialSnapshot.templateId())
            .templateVersion(currentVersion)
            .createdAt(initialSnapshot.createdAt())
            .snapshotAt(Instant.now())
            .configuration(currentConfiguration)
            .metrics(initialSnapshot.metrics())
            .build();

        // Check version drift
        boolean versionDrift = initialSnapshot.hasVersionDrift(currentVersion);

        // Check configuration drift
        boolean configDrift = initialSnapshot.hasConfigurationDrift(currentSnapshot);

        DriftResult result = new DriftResult(
            sessionId,
            initialSnapshot.templateId(),
            initialSnapshot.templateVersion(),
            currentVersion,
            initialSnapshot.checksum(),
            currentSnapshot.checksum(),
            versionDrift,
            configDrift,
            initialSnapshot.createdAt(),
            Instant.now()
        );

        // Notify callback if drift detected
        if (result.hasDrift() && driftCallback != null) {
            try {
                driftCallback.onDriftDetected(result);
            } catch (Exception e) {
                LOGGER.warning("Drift callback failed: " + e.getMessage());
            }
        }

        if (result.hasDrift()) {
            LOGGER.info("Drift detected for session " + sessionId +
                        ": version=" + versionDrift + ", config=" + configDrift);
        }

        return result;
    }

    /**
     * Gets the initial snapshot for a session if it exists.
     */
    public Optional<ArenaTemplateSnapshot> getInitialSnapshot(UUID sessionId) {
        return Optional.ofNullable(activeSnapshots.get(sessionId));
    }

    /**
     * Removes a session without drift detection (e.g., for cancelled sessions).
     */
    public void removeSession(UUID sessionId) {
        activeSnapshots.remove(sessionId);
    }

    /**
     * Gets the count of active sessions being tracked.
     */
    public int getActiveSessionCount() {
        return activeSnapshots.size();
    }

    /**
     * Clears all tracked sessions.
     */
    public void clear() {
        activeSnapshots.clear();
    }

    /**
     * Result of drift detection analysis.
     */
    public record DriftResult(
        UUID sessionId,
        String templateId,
        String initialVersion,
        String finalVersion,
        String initialChecksum,
        String finalChecksum,
        boolean versionDriftDetected,
        boolean configurationDriftDetected,
        Instant sessionStartedAt,
        Instant sessionEndedAt
    ) {
        /**
         * Returns true if any type of drift was detected.
         */
        public boolean hasDrift() {
            return versionDriftDetected || configurationDriftDetected;
        }

        /**
         * Returns the session duration in milliseconds.
         */
        public long durationMs() {
            return sessionEndedAt.toEpochMilli() - sessionStartedAt.toEpochMilli();
        }

        /**
         * Creates a result for when no initial snapshot was found.
         */
        public static DriftResult noSnapshot(UUID sessionId) {
            Instant now = Instant.now();
            return new DriftResult(
                sessionId,
                "unknown",
                "unknown",
                "unknown",
                null,
                null,
                false,
                false,
                now,
                now
            );
        }

        /**
         * Converts to a map for logging/serialization.
         */
        public Map<String, Object> toMap() {
            return Map.of(
                "sessionId", sessionId.toString(),
                "templateId", templateId,
                "initialVersion", initialVersion,
                "finalVersion", finalVersion,
                "versionDriftDetected", versionDriftDetected,
                "configurationDriftDetected", configurationDriftDetected,
                "durationMs", durationMs()
            );
        }
    }
}

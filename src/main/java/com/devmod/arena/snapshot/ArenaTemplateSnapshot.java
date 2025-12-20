package com.devmod.arena.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of arena template session state.
 * Used for hot-reload session management (DD16).
 *
 * This record captures the complete state at a point in time,
 * enabling version drift detection at end of session.
 */
public record ArenaTemplateSnapshot(
    UUID sessionId,
    String templateId,
    String templateVersion,
    Instant createdAt,
    Instant snapshotAt,
    Map<String, Object> configuration,
    Map<String, Object> metrics,
    String checksum
) {

    /**
     * Compact constructor with validation and defensive copying.
     */
    public ArenaTemplateSnapshot {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(templateVersion, "templateVersion must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");

        // Defensive copy for immutability
        configuration = configuration != null
            ? Collections.unmodifiableMap(Map.copyOf(configuration))
            : Collections.emptyMap();
        metrics = metrics != null
            ? Collections.unmodifiableMap(Map.copyOf(metrics))
            : Collections.emptyMap();

        // Generate checksum if not provided
        if (checksum == null || checksum.isBlank()) {
            checksum = generateChecksum(templateId, templateVersion, configuration);
        }
    }

    /**
     * Creates a new snapshot for the current instant.
     */
    public static ArenaTemplateSnapshot create(
            UUID sessionId,
            String templateId,
            String templateVersion,
            Instant createdAt,
            Map<String, Object> configuration,
            Map<String, Object> metrics) {
        return new ArenaTemplateSnapshot(
            sessionId,
            templateId,
            templateVersion,
            createdAt,
            Instant.now(),
            configuration,
            metrics,
            null
        );
    }

    /**
     * Creates a builder for more flexible snapshot creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Detects version drift by comparing this snapshot with the current template version.
     * Returns true if versions differ (drift detected).
     *
     * @param currentVersion The current template version to compare against
     * @return true if version drift is detected
     */
    public boolean hasVersionDrift(String currentVersion) {
        return !templateVersion.equals(currentVersion);
    }

    /**
     * Detects configuration drift by comparing checksums.
     *
     * @param other Another snapshot to compare against
     * @return true if configuration has drifted
     */
    public boolean hasConfigurationDrift(ArenaTemplateSnapshot other) {
        if (other == null) {
            return true;
        }
        return !this.checksum.equals(other.checksum);
    }

    /**
     * Creates a new snapshot with updated metrics while preserving immutability.
     */
    public ArenaTemplateSnapshot withUpdatedMetrics(Map<String, Object> newMetrics) {
        return new ArenaTemplateSnapshot(
            this.sessionId,
            this.templateId,
            this.templateVersion,
            this.createdAt,
            Instant.now(),
            this.configuration,
            newMetrics,
            this.checksum
        );
    }

    /**
     * Calculates session duration in milliseconds.
     */
    public long sessionDurationMs() {
        return snapshotAt.toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Generates a checksum based on template configuration.
     */
    private static String generateChecksum(
            String templateId,
            String version,
            Map<String, Object> config) {
        int hash = Objects.hash(templateId, version, config);
        return String.format("%08x", hash);
    }

    /**
     * Builder for ArenaTemplateSnapshot.
     */
    public static class Builder {
        private UUID sessionId;
        private String templateId;
        private String templateVersion;
        private Instant createdAt;
        private Instant snapshotAt;
        private Map<String, Object> configuration;
        private Map<String, Object> metrics;
        private String checksum;

        public Builder sessionId(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder templateVersion(String templateVersion) {
            this.templateVersion = templateVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder snapshotAt(Instant snapshotAt) {
            this.snapshotAt = snapshotAt;
            return this;
        }

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public ArenaTemplateSnapshot build() {
            if (sessionId == null) {
                sessionId = UUID.randomUUID();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            if (snapshotAt == null) {
                snapshotAt = Instant.now();
            }
            return new ArenaTemplateSnapshot(
                sessionId,
                templateId,
                templateVersion,
                createdAt,
                snapshotAt,
                configuration,
                metrics,
                checksum
            );
        }
    }
}

package com.devmod.telemetry.duckdb;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.annotation.Nullable;

public final class ArenaRecords {

    private ArenaRecords() {}

    /**
     * Record for build data.
     */
    public record BuildRecord(
        UUID buildId,
        String templateId,
        @Nullable String templateVersion,
        Instant startedAt,
        Instant completedAt,
        @Nullable Long durationMs,
        String status,
        @Nullable String errorMessage
    ) {
        public static BuildRecord success(String templateId, String version, Instant startedAt, long durationMs) {
            return new BuildRecord(
                UUID.randomUUID(),
                templateId,
                version,
                startedAt,
                Instant.now(),
                durationMs,
                "success",
                null
            );
        }

        public static BuildRecord failed(String templateId, String version, Instant startedAt, String error) {
            return new BuildRecord(
                UUID.randomUUID(),
                templateId,
                version,
                startedAt,
                Instant.now(),
                Instant.now().toEpochMilli() - startedAt.toEpochMilli(),
                "failed",
                error
            );
        }
    }

    /**
     * Record for enriched build telemetry ingestion.
     */
    public record BuildEventRecord(
        UUID arenaId,
        String templateId,
        @Nullable Integer templateVersion,
        @Nullable String policyId,
        @Nullable Integer policyVersion,
        Instant startedAt,
        Instant completedAt,
        @Nullable Long estimatedBlocks,
        @Nullable Long actualBlocks,
        @Nullable Long estimatedMs,
        @Nullable Long actualMs,
        @Nullable Boolean success,
        @Nullable String errorMessage,
        @Nullable Long rollbackMs,
        @Nullable Integer blocksReverted,
        @Nullable Integer originX,
        @Nullable Integer originY,
        @Nullable Integer originZ,
        @Nullable String dimension,
        @Nullable Double baselineMspt,
        @Nullable Double avgMspt,
        @Nullable Double peakMspt,
        @Nullable Double maxBuildImpactMs,
        @Nullable Integer pauseCount,
        @Nullable Integer throttleCount,
        @Nullable Boolean perfAborted
    ) {
        public static BuildEventRecord fromBuildRecord(BuildRecord build) {
            Integer version = null;
            try {
                version = build.templateVersion() != null ? Integer.parseInt(build.templateVersion()) : null;
            } catch (NumberFormatException ignored) {
                // Keep version as null
            }
            return new BuildEventRecord(
                build.buildId(),
                build.templateId(),
                version,
                null,
                null,
                build.startedAt(),
                build.completedAt(),
                null,
                null,
                null,
                build.durationMs(),
                "success".equalsIgnoreCase(build.status()),
                build.errorMessage(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }

    /**
     * Record for build performance updates.
     */
    public record BuildPerformanceRecord(
        UUID arenaId,
        @Nullable Double baselineMspt,
        @Nullable Double avgMspt,
        @Nullable Double peakMspt,
        @Nullable Double maxBuildImpactMs,
        @Nullable Integer pauseCount,
        @Nullable Integer throttleCount,
        @Nullable Boolean perfAborted
    ) {}

    /**
     * Record for performance samples.
     */
    public record BuildPerformanceSample(
        Instant timestamp,
        @Nullable Double baselineMspt,
        @Nullable Double avgMspt,
        @Nullable Double peakMspt
    ) {}

    /**
     * Record for wave aggregates.
     */
    public record WaveAggregate(
        int waveNumber,
        int attempts,
        int completions,
        double avgDurationMs
    ) {}

    /**
     * Record for temporal gaps between builds.
     */
    public record TemporalGap(
        Instant previous,
        Instant current,
        Duration gap
    ) {}

    /**
     * Record for usage data.
     */
    public record UsageRecord(
        UUID usageId,
        UUID sessionId,
        String templateId,
        String templateVersion,
        Instant sessionStartedAt,
        Instant sessionEndedAt,
        Long durationMs,
        String status,
        boolean versionDriftDetected,
        boolean configurationDriftDetected
    ) {}

    /**
     * Record for spatial events.
     */
    public record SpatialEventRecord(
        UUID eventId,
        String templateId,
        @Nullable Integer templateVersion,
        UUID sessionId,
        String eventType,
        int gridX,
        int gridZ,
        double worldX,
        double worldY,
        double worldZ,
        UUID playerUuid,
        Instant occurredAt
    ) {
        /**
         * Creates a new spawn event.
         */
        public static SpatialEventRecord spawn(String templateId, UUID sessionId,
                int gridX, int gridZ, double worldX, double worldY, double worldZ, UUID playerUuid) {
            return new SpatialEventRecord(UUID.randomUUID(), templateId, null, sessionId,
                "spawn", gridX, gridZ, worldX, worldY, worldZ, playerUuid, Instant.now());
        }

        /**
         * Creates a new death event.
         */
        public static SpatialEventRecord death(String templateId, UUID sessionId,
                int gridX, int gridZ, double worldX, double worldY, double worldZ, UUID playerUuid) {
            return new SpatialEventRecord(UUID.randomUUID(), templateId, null, sessionId,
                "death", gridX, gridZ, worldX, worldY, worldZ, playerUuid, Instant.now());
        }
    }
}

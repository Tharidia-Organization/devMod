package com.frenkvs.devmod.telemetry.duckdb;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Record types for arena telemetry data.
 * Used by DuckDBBatchWriter and DuckDBQueryAPI for unified arena analytics.
 */
public final class ArenaRecords {

    private ArenaRecords() {}

    /**
     * Record for build data.
     */
    public record BuildRecord(
        UUID buildId,
        String templateId,
        String templateVersion,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String status,
        String errorMessage
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
        Integer templateVersion,
        String policyId,
        Integer policyVersion,
        Instant startedAt,
        Instant completedAt,
        Long estimatedBlocks,
        Long actualBlocks,
        Long estimatedMs,
        Long actualMs,
        Boolean success,
        String errorMessage,
        Long rollbackMs,
        Integer blocksReverted,
        Integer originX,
        Integer originY,
        Integer originZ,
        String dimension,
        Double baselineMspt,
        Double avgMspt,
        Double peakMspt,
        Double maxBuildImpactMs,
        Integer pauseCount,
        Integer throttleCount,
        Boolean perfAborted
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
        Double baselineMspt,
        Double avgMspt,
        Double peakMspt,
        Double maxBuildImpactMs,
        Integer pauseCount,
        Integer throttleCount,
        Boolean perfAborted
    ) {}

    /**
     * Record for performance samples.
     */
    public record BuildPerformanceSample(
        Instant timestamp,
        Double baselineMspt,
        Double avgMspt,
        Double peakMspt
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
        Integer templateVersion,
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

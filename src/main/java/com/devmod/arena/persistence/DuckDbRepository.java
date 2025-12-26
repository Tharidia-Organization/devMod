package com.devmod.arena.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.duckdb.ArenaRecords;
import com.devmod.telemetry.duckdb.DuckDBBatchWriter;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

public class DuckDbRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDbRepository.class);

    private final @Nullable DuckDBBatchWriter batchWriterOverride;
    private final @Nullable DuckDBQueryAPI queryApiOverride;

    public DuckDbRepository() {
        this(null, null);
    }

    DuckDbRepository(@Nullable DuckDBBatchWriter batchWriter, @Nullable DuckDBQueryAPI queryApi) {
        this.batchWriterOverride = batchWriter;
        this.queryApiOverride = queryApi;
    }

    private @Nullable DuckDBBatchWriter getBatchWriter() {
        if (batchWriterOverride != null) {
            return batchWriterOverride;
        }
        return DuckDBTelemetryService.INSTANCE.getBatchWriter();
    }

    private @Nullable DuckDBQueryAPI getQueryApi() {
        if (queryApiOverride != null) {
            return queryApiOverride;
        }
        return DuckDBTelemetryService.INSTANCE.getQueryAPI();
    }

    /**
     * Records an arena build event into DuckDB.
     */
    public void recordBuild(ArenaRecords.BuildEventRecord record) {
        if (record == null) {
            return;
        }
        if (!hasRequiredBuildFields(record)) {
            LOGGER.warn("Skipping build record with missing required fields: templateId={}, templateVersion={}, arenaId={}",
                record.templateId(), record.templateVersion(), record.arenaId());
            return;
        }

        DuckDBBatchWriter batchWriter = getBatchWriter();
        if (batchWriter == null) {
            LOGGER.debug("DuckDB batch writer not available; build record skipped");
            return;
        }

        batchWriter.queueArenaTemplateBuild(record);
    }

    /**
     * Records a build summary by converting it to a build event record.
     */
    public void recordBuild(ArenaRecords.BuildRecord record) {
        if (record == null) {
            return;
        }
        ArenaRecords.BuildEventRecord eventRecord = ArenaRecords.BuildEventRecord.fromBuildRecord(record);
        if (eventRecord.templateVersion() == null) {
            LOGGER.warn("Skipping build record with non-numeric templateVersion: {}", record.templateVersion());
            return;
        }
        recordBuild(eventRecord);
    }

    /**
     * Records a usage event (start/end) for a template session.
     */
    public void recordUsageEvent(UsageEvent event) {
        if (event == null) {
            return;
        }
        if (!hasRequiredUsageFields(event.templateId(), event.templateVersion(), event.eventType())) {
            LOGGER.warn("Skipping usage event with missing required fields: templateId={}, templateVersion={}, eventType={}",
                event.templateId(), event.templateVersion(), event.eventType());
            return;
        }

        DuckDBBatchWriter batchWriter = getBatchWriter();
        if (batchWriter == null) {
            LOGGER.debug("DuckDB batch writer not available; usage event skipped");
            return;
        }

        batchWriter.queueArenaTemplateUsage(
            event.timestamp(),
            event.templateId(),
            event.templateVersion(),
            event.policyId(),
            event.policyVersion(),
            event.playerId(),
            event.playerName(),
            event.questType(),
            event.mobId(),
            event.difficulty(),
            event.playerCount(),
            event.sessionId(),
            event.eventType(),
            event.durationMs(),
            event.wavesCompleted(),
            event.outcome()
        );
    }

    /**
     * Records a usage session as start/end events when timestamps are present.
     */
    public void recordUsageSession(ArenaRecords.UsageRecord record) {
        if (record == null) {
            return;
        }

        Integer templateVersion = parseTemplateVersion(record.templateVersion());
        if (templateVersion == null) {
            LOGGER.warn("Skipping usage record with non-numeric templateVersion: {}", record.templateVersion());
            return;
        }
        if (record.templateId() == null) {
            LOGGER.warn("Skipping usage record with missing templateId");
            return;
        }

        Instant startedAt = record.sessionStartedAt();
        if (startedAt != null) {
            recordUsageEvent(new UsageEvent(
                startedAt,
                record.templateId(),
                templateVersion,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                record.sessionId(),
                "start",
                null,
                null,
                null
            ));
        }

        Instant endedAt = record.sessionEndedAt();
        if (endedAt != null) {
            Long durationMs = record.durationMs();
            if (durationMs == null && startedAt != null) {
                durationMs = Duration.between(startedAt, endedAt).toMillis();
            }
            recordUsageEvent(new UsageEvent(
                endedAt,
                record.templateId(),
                templateVersion,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                record.sessionId(),
                "end",
                durationMs,
                null,
                record.status()
            ));
        }
    }

    /**
     * Convenience wrapper for usage start events.
     */
    public void recordUsageStart(String templateId, Integer templateVersion,
                                 @Nullable String policyId, @Nullable Integer policyVersion,
                                 @Nullable UUID playerId, @Nullable String playerName,
                                 @Nullable String questType, @Nullable String mobId,
                                 @Nullable String difficulty, @Nullable Integer playerCount,
                                 @Nullable UUID sessionId) {
        if (!hasRequiredUsageFields(templateId, templateVersion, "start")) {
            LOGGER.warn("Skipping usage start with missing required fields: templateId={}, templateVersion={}",
                templateId, templateVersion);
            return;
        }

        DuckDBBatchWriter batchWriter = getBatchWriter();
        if (batchWriter == null) {
            LOGGER.debug("DuckDB batch writer not available; usage start skipped");
            return;
        }

        batchWriter.queueArenaUsageStart(
            templateId,
            templateVersion,
            policyId,
            policyVersion,
            playerId,
            playerName,
            questType,
            mobId,
            difficulty,
            playerCount,
            sessionId
        );
    }

    /**
     * Convenience wrapper for usage end events.
     */
    public void recordUsageEnd(String templateId, Integer templateVersion,
                               @Nullable String policyId, @Nullable Integer policyVersion,
                               @Nullable UUID playerId, @Nullable String playerName,
                               @Nullable String questType, @Nullable String mobId,
                               @Nullable String difficulty, @Nullable Integer playerCount,
                               @Nullable UUID sessionId, @Nullable Long durationMs,
                               @Nullable Integer wavesCompleted, @Nullable String outcome) {
        if (!hasRequiredUsageFields(templateId, templateVersion, "end")) {
            LOGGER.warn("Skipping usage end with missing required fields: templateId={}, templateVersion={}",
                templateId, templateVersion);
            return;
        }

        DuckDBBatchWriter batchWriter = getBatchWriter();
        if (batchWriter == null) {
            LOGGER.debug("DuckDB batch writer not available; usage end skipped");
            return;
        }

        batchWriter.queueArenaUsageEnd(
            templateId,
            templateVersion,
            policyId,
            policyVersion,
            playerId,
            playerName,
            questType,
            mobId,
            difficulty,
            playerCount,
            sessionId,
            durationMs,
            wavesCompleted,
            outcome
        );
    }

    public List<String> getTemplateIds() {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.getArenaTemplateIds();
    }

    public List<ArenaRecords.BuildRecord> getRecentBuilds(String templateId, int limit) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.getArenaRecentBuilds(templateId, limit);
    }

    public List<ArenaRecords.BuildRecord> getRecentBuilds(String templateId, @Nullable Integer templateVersion, int limit) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.getArenaRecentBuilds(templateId, templateVersion, limit);
    }

    public List<ArenaRecords.BuildPerformanceSample> getBuildPerformance(String templateId,
                                                                         @Nullable Integer templateVersion,
                                                                         int limit) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.getArenaBuildPerformance(templateId, templateVersion, limit);
    }

    public int[][] getHeatmap(String templateId, String eventType, int gridSize) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return new int[gridSize][gridSize];
        }
        return queryApi.getArenaHeatmap(templateId, eventType, gridSize);
    }

    public int[][] getHeatmap(String templateId, @Nullable Integer templateVersion, String eventType, int gridSize) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return new int[gridSize][gridSize];
        }
        return queryApi.getArenaHeatmap(templateId, templateVersion, eventType, gridSize);
    }

    public int getSpatialEventCount(String templateId, String eventType) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return 0;
        }
        return queryApi.getArenaSpatialEventCount(templateId, eventType);
    }

    public int getSpatialEventCount(String templateId, @Nullable Integer templateVersion, String eventType) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return 0;
        }
        return queryApi.getArenaSpatialEventCount(templateId, templateVersion, eventType);
    }

    public List<ArenaRecords.TemporalGap> findBuildGaps(Instant since, Duration minGap, int limit) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.findArenaBuildGaps(since, minGap, limit);
    }

    public long countBuildsAfter(Instant timestamp) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return 0L;
        }
        return queryApi.countArenaBuildsAfter(timestamp);
    }

    public List<ArenaRecords.WaveAggregate> getWaveAggregates(String templateId,
                                                               @Nullable Integer templateVersion,
                                                               Instant from,
                                                               Instant to) {
        DuckDBQueryAPI queryApi = getQueryApi();
        if (queryApi == null) {
            return List.of();
        }
        return queryApi.getArenaWaveAggregates(templateId, templateVersion, from, to);
    }

    private boolean hasRequiredBuildFields(ArenaRecords.BuildEventRecord record) {
        return record.arenaId() != null
            && record.templateId() != null
            && record.templateVersion() != null;
    }

    private boolean hasRequiredUsageFields(@Nullable String templateId,
                                           @Nullable Integer templateVersion,
                                           @Nullable String eventType) {
        return templateId != null
            && templateVersion != null
            && eventType != null
            && !eventType.isBlank();
    }

    private static @Nullable Integer parseTemplateVersion(@Nullable String templateVersion) {
        if (templateVersion == null || templateVersion.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(templateVersion);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record UsageEvent(
        Instant timestamp,
        String templateId,
        Integer templateVersion,
        @Nullable String policyId,
        @Nullable Integer policyVersion,
        @Nullable UUID playerId,
        @Nullable String playerName,
        @Nullable String questType,
        @Nullable String mobId,
        @Nullable String difficulty,
        @Nullable Integer playerCount,
        @Nullable UUID sessionId,
        String eventType,
        @Nullable Long durationMs,
        @Nullable Integer wavesCompleted,
        @Nullable String outcome
    ) {
        public UsageEvent {
            Objects.requireNonNull(timestamp, "timestamp must not be null");
            Objects.requireNonNull(templateId, "templateId must not be null");
            Objects.requireNonNull(templateVersion, "templateVersion must not be null");
            Objects.requireNonNull(eventType, "eventType must not be null");
        }
    }
}

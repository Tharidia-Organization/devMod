package com.devmod.arena.logging;

import com.devmod.telemetry.duckdb.ArenaRecords;
import com.devmod.telemetry.duckdb.DuckDBBatchWriter;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.arena.logging.LogAggregationPipeline.LogDestination;
import com.devmod.arena.logging.LogAggregationPipeline.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DuckDB destination for arena.build.* telemetry events.
 * Uses DuckDBBatchWriter for async, non-blocking writes.
 */
public class DuckDbDestination implements LogDestination {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDbDestination.class);

    private final ConcurrentHashMap<UUID, BuildStartInfo> buildStarts = new ConcurrentHashMap<>();

    public DuckDbDestination() {
    }

    /**
     * Legacy constructor for backwards compatibility.
     * @deprecated Use no-arg constructor instead
     */
    @Deprecated
    public DuckDbDestination(Object repositorySupplier) {
        // Ignore repository supplier - we use DuckDBTelemetryService.INSTANCE
    }

    @Override
    public String name() {
        return "duckdb";
    }

    @Override
    public void write(List<LogEvent> events) {
        DuckDBBatchWriter batchWriter = DuckDBTelemetryService.INSTANCE.getBatchWriter();
        if (batchWriter == null) {
            return;
        }

        for (LogEvent event : events) {
            switch (event.eventName()) {
                case "arena.build.start" -> handleBuildStart(event);
                case "arena.build.end" -> handleBuildEnd(event, true);
                case "arena.build.fail" -> handleBuildEnd(event, false);
                case "arena.build.performance_summary" -> handlePerformanceSummary(event);
                default -> {
                    // ignore
                }
            }
        }
    }

    @Override
    public void close() {
        // Lifecycle managed by DuckDBTelemetryService
    }

    private void handleBuildStart(LogEvent event) {
        Map<String, Object> data = event.data();
        UUID arenaId = asUuid(data.get("arenaId"));
        if (arenaId == null) {
            return;
        }
        Origin origin = resolveOrigin(data);
        BuildStartInfo info = new BuildStartInfo(
            event.timestamp(),
            asLong(data.get("estimatedMs")),
            origin.x(),
            origin.y(),
            origin.z(),
            asString(data.get("dimension")),
            asString(data.get("templateId")),
            asInteger(data.get("templateVersion")),
            asString(data.get("policyId")),
            asInteger(data.get("policyVersion"))
        );
        buildStarts.put(arenaId, info);
    }

    private void handleBuildEnd(LogEvent event, boolean success) {
        DuckDBBatchWriter batchWriter = DuckDBTelemetryService.INSTANCE.getBatchWriter();
        if (batchWriter == null) {
            return;
        }
        Map<String, Object> data = event.data();
        UUID arenaId = asUuid(data.get("arenaId"));
        if (arenaId == null) {
            return;
        }

        BuildStartInfo start = buildStarts.remove(arenaId);
        Origin origin = resolveOrigin(data);
        if (start != null && origin.isEmpty()) {
            origin = new Origin(start.originX(), start.originY(), start.originZ());
        }

        String templateId = asString(data.get("templateId"));
        if ((templateId == null || templateId.isBlank()) && start != null) {
            templateId = start.templateId();
        }
        Integer templateVersion = asInteger(data.get("templateVersion"));
        if (templateVersion == null && start != null) {
            templateVersion = start.templateVersion();
        }
        String policyId = asString(data.get("policyId"));
        if (policyId == null && start != null) {
            policyId = start.policyId();
        }
        Integer policyVersion = asInteger(data.get("policyVersion"));
        if (policyVersion == null && start != null) {
            policyVersion = start.policyVersion();
        }
        String dimension = asString(data.get("dimension"));
        if (dimension == null && start != null) {
            dimension = start.dimension();
        }

        Long estimatedMs = start != null ? start.estimatedMs() : asLong(data.get("estimatedMs"));
        Long estimatedBlocks = asLong(data.get("expected_blocks"));
        Long actualMs = asLong(data.get("actualMs"));
        if (actualMs == null) {
            actualMs = asLong(data.get("build_ms"));
        }
        Long actualBlocks = asLong(data.get("actualBlocks"));
        if (actualBlocks == null) {
            actualBlocks = asLong(data.get("blocksPlaced"));
        }

        String errorMessage = success ? null : asString(data.get("message"));
        Long rollbackMs = asLong(data.get("rollbackMs"));
        Integer blocksReverted = asInteger(data.get("blocksReverted"));

        Instant startedAt = start != null ? start.startedAt() : event.timestamp();

        try {
            batchWriter.queueArenaTemplateBuild(
                startedAt,
                arenaId,
                templateId,
                templateVersion,
                policyId,
                policyVersion,
                origin.x(),
                origin.y(),
                origin.z(),
                dimension,
                estimatedBlocks != null ? estimatedBlocks.intValue() : null,
                actualBlocks != null ? actualBlocks.intValue() : null,
                estimatedMs,
                actualMs,
                success,
                errorMessage,
                rollbackMs,
                blocksReverted,
                null, null, null, null, null, null, null  // performance metrics set separately
            );
        } catch (Exception e) {
            LOGGER.debug("Failed to queue build event: {}", e.getMessage());
        }
    }

    private void handlePerformanceSummary(LogEvent event) {
        DuckDBBatchWriter batchWriter = DuckDBTelemetryService.INSTANCE.getBatchWriter();
        if (batchWriter == null) {
            return;
        }
        Map<String, Object> data = event.data();
        UUID arenaId = asUuid(data.get("arenaId"));
        if (arenaId == null) {
            return;
        }

        // Performance updates need the full build event record
        // Queue a new build event with performance data
        ArenaRecords.BuildEventRecord record = new ArenaRecords.BuildEventRecord(
            arenaId,
            null, null, null, null,
            Instant.now(), Instant.now(),
            null, null, null, null,
            true, null, null, null,
            null, null, null, null,
            asDouble(data.get("baselineMspt")),
            asDouble(data.get("averageMspt")),
            asDouble(data.get("peakMspt")),
            asDouble(data.get("maxBuildImpactMs")),
            asInteger(data.get("pauseCount")),
            asInteger(data.get("throttleCount")),
            asBoolean(data.get("aborted"))
        );

        try {
            // Note: This queues a new row. Performance updates ideally would update existing rows,
            // but BatchWriter doesn't support updates. The dashboard can aggregate by arenaId.
            batchWriter.queueArenaTemplateBuild(record);
        } catch (Exception e) {
            LOGGER.debug("Failed to queue build performance: {}", e.getMessage());
        }
    }

    private Origin resolveOrigin(Map<String, Object> data) {
        Integer x = asInteger(data.get("originX"));
        Integer y = asInteger(data.get("originY"));
        Integer z = asInteger(data.get("originZ"));
        if (x != null && y != null && z != null) {
            return new Origin(x, y, z);
        }
        String origin = asString(data.get("origin"));
        if (origin != null && origin.contains(",")) {
            String[] parts = origin.split(",");
            if (parts.length == 3) {
                Integer ox = parseInt(parts[0]);
                Integer oy = parseInt(parts[1]);
                Integer oz = parseInt(parts[2]);
                if (ox != null && oy != null && oz != null) {
                    return new Origin(ox, oy, oz);
                }
            }
        }
        return Origin.empty();
    }

    @Nullable
    private UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private String asString(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        return String.valueOf(value);
    }

    @Nullable
    private Integer asInteger(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            return parseInt(s);
        }
        return null;
    }

    @Nullable
    private Long asLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private Double asDouble(Object value) {
        if (value instanceof Double d) return d;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    @Nullable
    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record BuildStartInfo(
        Instant startedAt,
        Long estimatedMs,
        Integer originX,
        Integer originY,
        Integer originZ,
        String dimension,
        String templateId,
        Integer templateVersion,
        String policyId,
        Integer policyVersion
    ) {}

    private record Origin(Integer x, Integer y, Integer z) {
        static Origin empty() {
            return new Origin(null, null, null);
        }

        boolean isEmpty() {
            return x == null || y == null || z == null;
        }
    }
}

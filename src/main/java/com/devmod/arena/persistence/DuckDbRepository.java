package com.devmod.arena.persistence;

import com.devmod.arena.alert.ErrorContext;
import com.devmod.arena.snapshot.VersionDriftDetector.DriftResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DuckDB repository for arena template observability data (DD21).
 *
 * Provides CRUD operations for:
 * - arena_template_builds
 * - arena_template_usage
 * - arena_template_errors
 * - arena_template_alerts
 *
 * Optimized with indices for dashboard queries (<200ms target).
 */
public class DuckDbRepository implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DuckDbRepository.class.getName());

    private final String databasePath;
    private volatile Connection connection;

    /**
     * Creates a new DuckDB repository.
     *
     * @param databasePath Path to the DuckDB database file (use ":memory:" for in-memory)
     */
    public DuckDbRepository(String databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath);
    }

    /**
     * Initializes the database connection and schema.
     */
    public void initialize() throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB driver not found", e);
        }

        connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath);
        initializeSchema();
        // Ensure baseline schema exists even if resource file is missing/unsupported
        initializeInlineSchema();
        LOGGER.info("DuckDB repository initialized: " + databasePath);
    }

    /**
     * Initializes the database schema from the SQL file.
     */
    private void initializeSchema() throws SQLException {
        try (InputStream is = getClass().getResourceAsStream("/db/duckdb_schema.sql")) {
            if (is == null) {
                LOGGER.warning("Schema file not found, using inline schema");
                initializeInlineSchema();
                return;
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Execute each statement separately
            for (String statement : schema.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    try (var stmt = connection.createStatement()) {
                        stmt.execute(trimmed);
                    } catch (SQLException e) {
                        // Skip errors for DROP statements or if table already exists
                        if (!e.getMessage().contains("already exists")) {
                            LOGGER.log(Level.WARNING, "Schema statement failed: " + trimmed, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read schema file", e);
        }
    }

    /**
     * Fallback inline schema initialization.
     */
    private void initializeInlineSchema() throws SQLException {
        String[] statements = {
            """
            CREATE TABLE IF NOT EXISTS arena_template_builds (
                build_id UUID PRIMARY KEY,
                template_id VARCHAR NOT NULL,
                template_version VARCHAR NOT NULL,
                started_at TIMESTAMP NOT NULL,
                completed_at TIMESTAMP,
                duration_ms BIGINT,
                status VARCHAR NOT NULL,
                error_message VARCHAR,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arena_template_usage (
                usage_id UUID PRIMARY KEY,
                session_id UUID NOT NULL,
                template_id VARCHAR NOT NULL,
                template_version VARCHAR NOT NULL,
                session_started_at TIMESTAMP NOT NULL,
                session_ended_at TIMESTAMP,
                duration_ms BIGINT,
                status VARCHAR NOT NULL,
                version_drift_detected BOOLEAN DEFAULT FALSE,
                configuration_drift_detected BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arena_template_errors (
                error_id UUID PRIMARY KEY,
                error_type VARCHAR NOT NULL,
                error_message VARCHAR,
                severity VARCHAR NOT NULL,
                component VARCHAR NOT NULL,
                stack_frames JSON,
                session_id UUID,
                occurred_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            // Gap 4: Spatial events table for heatmap data
            """
            CREATE TABLE IF NOT EXISTS arena_spatial_events (
                event_id UUID PRIMARY KEY,
                template_id VARCHAR NOT NULL,
                session_id UUID NOT NULL,
                event_type VARCHAR NOT NULL,
                grid_x INTEGER NOT NULL,
                grid_z INTEGER NOT NULL,
                world_x DOUBLE,
                world_y DOUBLE,
                world_z DOUBLE,
                player_uuid UUID,
                occurred_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            // Gap 4: Index for heatmap queries
            "CREATE INDEX IF NOT EXISTS idx_spatial_template_type ON arena_spatial_events (template_id, event_type)",
            // DD21: 5 indices for dashboard queries (<200ms target)
            // idx_builds_template_day: for filtering builds by template and date
            "CREATE INDEX IF NOT EXISTS idx_builds_template_day ON arena_template_builds (template_id, CAST(started_at AS DATE))",
            // idx_builds_result_day: for filtering by result status and date
            "CREATE INDEX IF NOT EXISTS idx_builds_result_day ON arena_template_builds (status, CAST(started_at AS DATE))",
            // idx_builds_created: for ordering by creation time
            "CREATE INDEX IF NOT EXISTS idx_builds_created ON arena_template_builds (started_at DESC)",
            // idx_usage_template_day: for usage by template and date
            "CREATE INDEX IF NOT EXISTS idx_usage_template_day ON arena_template_usage (template_id, CAST(session_started_at AS DATE))",
            // DD21: idx_usage_player for player-specific queries (using session_id as proxy)
            "CREATE INDEX IF NOT EXISTS idx_usage_player ON arena_template_usage (session_id, session_started_at DESC)"
        };

        for (String sql : statements) {
            try (var stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) {
                    throw e;
                }
            }
        }
    }

    /**
     * Records a template build event.
     */
    public void recordBuild(BuildRecord build) throws SQLException {
        String sql = """
            INSERT INTO arena_template_builds
            (build_id, template_id, template_version, started_at, completed_at, duration_ms, status, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, build.buildId().toString());
            stmt.setString(2, build.templateId());
            stmt.setString(3, build.templateVersion());
            stmt.setTimestamp(4, Timestamp.from(build.startedAt()));
            stmt.setTimestamp(5, build.completedAt() != null ? Timestamp.from(build.completedAt()) : null);
            stmt.setLong(6, build.durationMs() != null ? build.durationMs() : 0L);
            stmt.setString(7, build.status());
            stmt.setString(8, build.errorMessage());
            stmt.executeUpdate();
        }
    }

    /**
     * Records a template usage session.
     */
    public void recordUsage(UsageRecord usage) throws SQLException {
        String sql = """
            INSERT INTO arena_template_usage
            (usage_id, session_id, template_id, template_version, session_started_at, session_ended_at,
             duration_ms, status, version_drift_detected, configuration_drift_detected)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, usage.usageId().toString());
            stmt.setString(2, usage.sessionId().toString());
            stmt.setString(3, usage.templateId());
            stmt.setString(4, usage.templateVersion());
            stmt.setTimestamp(5, Timestamp.from(usage.sessionStartedAt()));
            stmt.setTimestamp(6, usage.sessionEndedAt() != null ? Timestamp.from(usage.sessionEndedAt()) : null);
            stmt.setLong(7, usage.durationMs() != null ? usage.durationMs() : 0L);
            stmt.setString(8, usage.status());
            stmt.setBoolean(9, usage.versionDriftDetected());
            stmt.setBoolean(10, usage.configurationDriftDetected());
            stmt.executeUpdate();
        }
    }

    /**
     * Records an error with context.
     */
    public void recordError(ErrorContext error) throws SQLException {
        String sql = """
            INSERT INTO arena_template_errors
            (error_id, error_type, error_message, severity, component, stack_frames, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, error.errorId().toString());
            stmt.setString(2, error.errorType());
            stmt.setString(3, error.message());
            stmt.setString(4, error.severity().name());
            stmt.setString(5, error.component());
            stmt.setString(6, stackFramesToJson(error));
            stmt.setTimestamp(7, Timestamp.from(error.timestamp()));
            stmt.executeUpdate();
        }
    }

    /**
     * Records usage from a drift result.
     */
    public void recordDriftResult(DriftResult drift, String status) throws SQLException {
        UsageRecord usage = new UsageRecord(
            UUID.randomUUID(),
            drift.sessionId(),
            drift.templateId(),
            drift.initialVersion(),
            drift.sessionStartedAt(),
            drift.sessionEndedAt(),
            drift.durationMs(),
            status,
            drift.versionDriftDetected(),
            drift.configurationDriftDetected()
        );
        recordUsage(usage);
    }

    /**
     * Gap 3: Returns all distinct template IDs from builds.
     */
    public List<String> getAllTemplateIds() throws SQLException {
        String sql = """
            SELECT DISTINCT template_id
            FROM arena_template_builds
            ORDER BY template_id
            """;

        List<String> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(rs.getString("template_id"));
            }
        }
        return results;
    }

    /**
     * Queries recent builds for a template.
     */
    public List<BuildRecord> getRecentBuilds(String templateId, int limit) throws SQLException {
        String sql = """
            SELECT build_id, template_id, template_version, started_at, completed_at,
                   duration_ms, status, error_message
            FROM arena_template_builds
            WHERE template_id = ?
            ORDER BY started_at DESC
            LIMIT ?
            """;

        List<BuildRecord> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, templateId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new BuildRecord(
                        UUID.fromString(rs.getString("build_id")),
                        rs.getString("template_id"),
                        rs.getString("template_version"),
                        rs.getTimestamp("started_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("completed_at"))
                            .map(Timestamp::toInstant).orElse(null),
                        rs.getLong("duration_ms"),
                        rs.getString("status"),
                        rs.getString("error_message")
                    ));
                }
            }
        }
        return results;
    }

    // ========== Gap 4: Spatial Events for Heatmap ==========

    /**
     * Gap 4: Records a spatial event (spawn, death, etc.) for heatmap visualization.
     */
    public void recordSpatialEvent(SpatialEventRecord event) throws SQLException {
        String sql = """
            INSERT INTO arena_spatial_events
                (event_id, template_id, session_id, event_type, grid_x, grid_z,
                 world_x, world_y, world_z, player_uuid, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.eventId().toString());
            stmt.setString(2, event.templateId());
            stmt.setString(3, event.sessionId().toString());
            stmt.setString(4, event.eventType());
            stmt.setInt(5, event.gridX());
            stmt.setInt(6, event.gridZ());
            stmt.setDouble(7, event.worldX());
            stmt.setDouble(8, event.worldY());
            stmt.setDouble(9, event.worldZ());
            stmt.setString(10, event.playerUuid() != null ? event.playerUuid().toString() : null);
            stmt.setTimestamp(11, Timestamp.from(event.occurredAt()));
            stmt.executeUpdate();
        }
    }

    /**
     * Gap 4: Record for spatial events.
     */
    public record SpatialEventRecord(
        UUID eventId,
        String templateId,
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
            return new SpatialEventRecord(UUID.randomUUID(), templateId, sessionId,
                "spawn", gridX, gridZ, worldX, worldY, worldZ, playerUuid, Instant.now());
        }

        /**
         * Creates a new death event.
         */
        public static SpatialEventRecord death(String templateId, UUID sessionId,
                int gridX, int gridZ, double worldX, double worldY, double worldZ, UUID playerUuid) {
            return new SpatialEventRecord(UUID.randomUUID(), templateId, sessionId,
                "death", gridX, gridZ, worldX, worldY, worldZ, playerUuid, Instant.now());
        }
    }

    /**
     * Gap 4: Queries aggregated spatial events for heatmap grid.
     * Returns count per grid cell for a specific template and event type.
     *
     * @param templateId The template ID
     * @param eventType The event type ("spawn" or "death")
     * @param gridSize The heatmap grid size (default 16)
     * @return 2D array of event counts [x][z]
     */
    public int[][] getSpatialEventHeatmap(String templateId, String eventType, int gridSize) throws SQLException {
        String sql = """
            SELECT grid_x, grid_z, COUNT(*) as count
            FROM arena_spatial_events
            WHERE template_id = ? AND event_type = ?
            GROUP BY grid_x, grid_z
            """;

        int[][] grid = new int[gridSize][gridSize];

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, templateId);
            stmt.setString(2, eventType);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt("grid_x");
                    int z = rs.getInt("grid_z");
                    int count = rs.getInt("count");

                    // Ensure within bounds
                    if (x >= 0 && x < gridSize && z >= 0 && z < gridSize) {
                        grid[x][z] = count;
                    }
                }
            }
        }
        return grid;
    }

    /**
     * Gap 4: Gets total count of spatial events for a template and type.
     */
    public int getSpatialEventCount(String templateId, String eventType) throws SQLException {
        String sql = """
            SELECT COUNT(*) as total
            FROM arena_spatial_events
            WHERE template_id = ? AND event_type = ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, templateId);
            stmt.setString(2, eventType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }

    /**
     * Queries sessions with version drift.
     */
    public List<UsageRecord> getVersionDriftSessions(int limit) throws SQLException {
        String sql = """
            SELECT usage_id, session_id, template_id, template_version, session_started_at,
                   session_ended_at, duration_ms, status, version_drift_detected, configuration_drift_detected
            FROM arena_template_usage
            WHERE version_drift_detected = TRUE
            ORDER BY session_started_at DESC
            LIMIT ?
            """;

        List<UsageRecord> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new UsageRecord(
                        UUID.fromString(rs.getString("usage_id")),
                        UUID.fromString(rs.getString("session_id")),
                        rs.getString("template_id"),
                        rs.getString("template_version"),
                        rs.getTimestamp("session_started_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("session_ended_at"))
                            .map(Timestamp::toInstant).orElse(null),
                        rs.getLong("duration_ms"),
                        rs.getString("status"),
                        rs.getBoolean("version_drift_detected"),
                        rs.getBoolean("configuration_drift_detected")
                    ));
                }
            }
        }
        return results;
    }

    /**
     * Gets error count by severity for the last N hours.
     */
    public Map<String, Long> getErrorCountBySeverity(int hours) throws SQLException {
        String sql = """
            SELECT severity, COUNT(*) as count
            FROM arena_template_errors
            WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '%d hours'
            GROUP BY severity
            """.formatted(hours);

        Map<String, Long> counts = new java.util.HashMap<>();
        try (var stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                counts.put(rs.getString("severity"), rs.getLong("count"));
            }
        }
        return counts;
    }

    /**
     * Rebuilds DuckDB tables from NDJSON files (DD24).
     * DD24: NDJSON is source of truth, DuckDB is materialized view (rebuildable).
     *
     * @param ndjsonDir directory containing NDJSON files
     * @throws SQLException if rebuild fails
     * @throws IOException if reading NDJSON files fails
     */
    public void rebuildFromNdjson(java.nio.file.Path ndjsonDir) throws SQLException, IOException {
        LOGGER.info("DD24: Starting DuckDB rebuild from NDJSON: " + ndjsonDir);

        // Step 1: Truncate all tables
        String[] tables = {"arena_template_builds", "arena_template_usage", "arena_template_errors"};
        for (String table : tables) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM " + table);
                LOGGER.info("DD24: Truncated table " + table);
            }
        }

        // Step 2: Re-ingest from NDJSON files
        int totalIngested = 0;

        if (java.nio.file.Files.isDirectory(ndjsonDir)) {
            try (var stream = java.nio.file.Files.list(ndjsonDir)) {
                var files = stream
                    .filter(p -> p.toString().endsWith(".ndjson"))
                    .toList();

                for (java.nio.file.Path file : files) {
                    int count = ingestNdjsonFile(file);
                    totalIngested += count;
                    LOGGER.info("DD24: Ingested " + count + " records from " + file.getFileName());
                }
            }
        }

        LOGGER.info("DD24: Rebuild complete. Total records ingested: " + totalIngested);
    }

    /**
     * Ingests a single NDJSON file into the appropriate table.
     * DD24: Parses NDJSON and routes to correct table based on event type.
     */
    private int ingestNdjsonFile(java.nio.file.Path file) throws IOException, SQLException {
        int count = 0;
        String filename = file.getFileName().toString();

        try (var reader = java.nio.file.Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    ingestNdjsonLine(line, filename);
                    count++;
                } catch (Exception e) {
                    LOGGER.warning("DD24: Failed to ingest line: " + e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * Ingests a single NDJSON line based on event type.
     */
    private void ingestNdjsonLine(String json, String filename) throws SQLException {
        // Parse event type from JSON (simplified parsing)
        if (json.contains("\"event\":\"arena.build.")) {
            ingestBuildEvent(json);
        } else if (json.contains("\"event\":\"arena.usage.") || json.contains("\"event\":\"arena.session.")) {
            ingestUsageEvent(json);
        } else if (json.contains("\"event\":\"arena.error.")) {
            ingestErrorEvent(json);
        }
        // Skip unknown event types
    }

    private void ingestBuildEvent(String json) throws SQLException {
        // Extract fields from JSON (simplified - in production use proper JSON parser)
        String buildId = extractJsonString(json, "buildId");
        String templateId = extractJsonString(json, "templateId");
        String templateVersion = extractJsonString(json, "templateVersion");
        String timestamp = extractJsonString(json, "timestamp");
        String status = extractJsonString(json, "status");

        if (buildId != null && templateId != null) {
            String sql = """
                INSERT INTO arena_template_builds
                (build_id, template_id, template_version, started_at, status)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (build_id) DO NOTHING
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, buildId);
                stmt.setString(2, templateId);
                stmt.setString(3, templateVersion != null ? templateVersion : "unknown");
                stmt.setTimestamp(4, timestamp != null ? Timestamp.valueOf(timestamp.replace("T", " ").replace("Z", "")) : Timestamp.from(Instant.now()));
                stmt.setString(5, status != null ? status : "unknown");
                stmt.executeUpdate();
            }
        }
    }

    private void ingestUsageEvent(String json) throws SQLException {
        String sessionId = extractJsonString(json, "sessionId");
        String templateId = extractJsonString(json, "templateId");
        if (sessionId != null && templateId != null) {
            String sql = """
                INSERT INTO arena_template_usage
                (usage_id, session_id, template_id, template_version, session_started_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (usage_id) DO NOTHING
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, UUID.randomUUID().toString());
                stmt.setString(2, sessionId);
                stmt.setString(3, templateId);
                stmt.setString(4, extractJsonString(json, "templateVersion") != null ? extractJsonString(json, "templateVersion") : "unknown");
                stmt.setTimestamp(5, Timestamp.from(Instant.now()));
                stmt.setString(6, "ingested");
                stmt.executeUpdate();
            }
        }
    }

    private void ingestErrorEvent(String json) throws SQLException {
        String errorType = extractJsonString(json, "errorType");
        String message = extractJsonString(json, "message");
        if (errorType != null) {
            String sql = """
                INSERT INTO arena_template_errors
                (error_id, error_type, error_message, severity, component, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, UUID.randomUUID().toString());
                stmt.setString(2, errorType);
                stmt.setString(3, message);
                stmt.setString(4, extractJsonString(json, "severity") != null ? extractJsonString(json, "severity") : "UNKNOWN");
                stmt.setString(5, extractJsonString(json, "component") != null ? extractJsonString(json, "component") : "unknown");
                stmt.setTimestamp(6, Timestamp.from(Instant.now()));
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Simple JSON string extraction (for NDJSON re-ingestion).
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /**
     * Cleans up old data (DD17: 14 days retention).
     */
    public int cleanupOldData(int retentionDays) throws SQLException {
        int totalDeleted = 0;

        String[] tables = {"arena_template_builds", "arena_template_usage", "arena_template_errors"};

        for (String table : tables) {
            String sql = "DELETE FROM " + table + " WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '" + retentionDays + " days'";
            try (var stmt = connection.createStatement()) {
                totalDeleted += stmt.executeUpdate(sql);
            }
        }

        LOGGER.info("Cleaned up " + totalDeleted + " old records");
        return totalDeleted;
    }

    private String stackFramesToJson(ErrorContext error) {
        List<Map<String, Object>> frames = error.stackFramesToJson();
        // Simple JSON array serialization
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map<String, Object> frame : frames) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            boolean firstEntry = true;
            for (Map.Entry<String, Object> entry : frame.entrySet()) {
                if (!firstEntry) sb.append(",");
                firstEntry = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    sb.append("\"").append(entry.getValue()).append("\"");
                } else {
                    sb.append(entry.getValue());
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            LOGGER.info("DuckDB repository closed");
        }
    }

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
}

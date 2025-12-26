package com.devmod.arena.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.devmod.telemetry.duckdb.ArenaRecords;
import com.devmod.telemetry.duckdb.DuckDBBatchWriter;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;
import com.devmod.telemetry.duckdb.DuckDBQueryAPI;
import com.devmod.telemetry.duckdb.DuckDBSchemaManager;

class DuckDbRepositoryTest {

    @TempDir
    @Nullable static Path tempDir;

    @Nullable private static DuckDBConnectionManager connectionManager;
    @Nullable private static DuckDBBatchWriter batchWriter;
    @Nullable private static DuckDBQueryAPI queryApi;
    @Nullable private static DuckDbRepository repository;
    @Nullable private static Connection testConnection;

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Path dbPath = Objects.requireNonNull(tempDir).resolve("test_arena_repo.duckdb");
        connectionManager = new DuckDBConnectionManager(dbPath);
        testConnection = requireConnectionManager().getConnection();

        DuckDBSchemaManager.ensureSchema(requireConnection());

        batchWriter = new DuckDBBatchWriter(requireConnectionManager());
        requireBatchWriter().start();

        queryApi = new DuckDBQueryAPI(requireConnectionManager());
        repository = new DuckDbRepository(requireBatchWriter(), requireQueryApi());
    }

    @AfterAll
    static void cleanup() {
        if (batchWriter != null) {
            requireBatchWriter().shutdown();
        }
        if (connectionManager != null) {
            requireConnectionManager().shutdown();
        }
    }

    @BeforeEach
    void ensureCleanState() throws Exception {
        waitForPendingWrites();
        requireBatchWriter().resetForTest();
        testConnection = requireConnectionManager().getConnection();
        clearArenaTables();
    }

    @Test
    @DisplayName("Records arena build events via repository")
    void recordsBuildEvent() throws Exception {
        UUID arenaId = UUID.randomUUID();
        String templateId = "devmod:test_arena";

        ArenaRecords.BuildEventRecord record = new ArenaRecords.BuildEventRecord(
            arenaId,
            templateId,
            1,
            "devmod:test_policy",
            2,
            Instant.now(),
            Instant.now().plusMillis(750),
            120L,
            140L,
            600L,
            750L,
            true,
            null,
            null,
            null,
            0,
            64,
            0,
            "minecraft:overworld",
            18.0,
            21.5,
            30.0,
            5.0,
            0,
            0,
            false
        );

        requireRepository().recordBuild(record);
        forceFlush();

        List<ArenaRecords.BuildRecord> builds = requireRepository().getRecentBuilds(templateId, 1, 5);
        assertFalse(builds.isEmpty(), "Expected recent builds to be present");

        try (PreparedStatement stmt = requireConnection().prepareStatement(
                "SELECT COUNT(*) FROM arena_template_builds WHERE arena_id = ? AND template_id = ?")) {
            stmt.setObject(1, arenaId);
            stmt.setString(2, templateId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Records usage sessions as start/end events")
    void recordsUsageSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String templateId = "devmod:test_usage";
        Instant startedAt = Instant.now().minusSeconds(30);
        Instant endedAt = Instant.now();

        ArenaRecords.UsageRecord usage = new ArenaRecords.UsageRecord(
            UUID.randomUUID(),
            sessionId,
            templateId,
            "2",
            startedAt,
            endedAt,
            30_000L,
            "completed",
            false,
            false
        );

        requireRepository().recordUsageSession(usage);
        forceFlush();

        try (PreparedStatement stmt = requireConnection().prepareStatement(
                "SELECT COUNT(*) FROM arena_template_usage WHERE session_id = ?")) {
            stmt.setObject(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }

        try (PreparedStatement stmt = requireConnection().prepareStatement(
                "SELECT COUNT(*) FROM arena_template_usage WHERE session_id = ? AND event_type = 'end'")) {
            stmt.setObject(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    private void clearArenaTables() throws SQLException {
        try (Statement stmt = requireConnection().createStatement()) {
            stmt.executeUpdate("DELETE FROM arena_template_builds");
            stmt.executeUpdate("DELETE FROM arena_template_usage");
        }
    }

    private void waitForPendingWrites() throws InterruptedException {
        requireBatchWriter().forceFlush();
        int maxWait = 10;
        while (requireBatchWriter().getPendingInserts() > 0 && maxWait-- > 0) {
            Thread.sleep(100);
            requireBatchWriter().forceFlush();
        }
        Thread.sleep(50);
    }

    private void forceFlush() throws InterruptedException {
        requireBatchWriter().forceFlush();
        Thread.sleep(100);
    }

    private static DuckDBConnectionManager requireConnectionManager() {
        return Objects.requireNonNull(connectionManager, "Connection manager not initialized");
    }

    private static DuckDBBatchWriter requireBatchWriter() {
        return Objects.requireNonNull(batchWriter, "Batch writer not initialized");
    }

    private static DuckDBQueryAPI requireQueryApi() {
        return Objects.requireNonNull(queryApi, "Query API not initialized");
    }

    private static DuckDbRepository requireRepository() {
        return Objects.requireNonNull(repository, "Repository not initialized");
    }

    private static Connection requireConnection() {
        return Objects.requireNonNull(testConnection, "Test connection not initialized");
    }
}

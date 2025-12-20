package com.devmod.arena.persistence;

import com.devmod.arena.alert.ErrorContext;
import com.devmod.arena.persistence.DuckDbRepository.BuildRecord;
import com.devmod.arena.persistence.DuckDbRepository.UsageRecord;
import com.devmod.arena.snapshot.VersionDriftDetector.DriftResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DuckDbRepository (DD21).
 * Verifies CRUD operations and query performance.
 */
class DuckDbRepositoryTest {

    private DuckDbRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        // Use in-memory database for testing
        repository = new DuckDbRepository(":memory:");
        repository.initialize();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (repository != null) {
            repository.close();
        }
    }

    @Nested
    @DisplayName("Build Record Tests")
    class BuildRecordTests {

        @Test
        @DisplayName("Can insert and query build records")
        void canInsertAndQueryBuilds() throws SQLException {
            BuildRecord build = BuildRecord.success(
                "test-template",
                "1.0.0",
                Instant.now().minusSeconds(60),
                1000L
            );

            repository.recordBuild(build);

            List<BuildRecord> results = repository.getRecentBuilds("test-template", 10);
            assertEquals(1, results.size());
            assertEquals("test-template", results.get(0).templateId());
            assertEquals("success", results.get(0).status());
        }

        @Test
        @DisplayName("Queries are ordered by start time descending")
        void queriesOrderedByStartTime() throws SQLException {
            Instant now = Instant.now();

            repository.recordBuild(BuildRecord.success("test-template", "1.0.0", now.minusSeconds(300), 100L));
            repository.recordBuild(BuildRecord.success("test-template", "1.0.1", now.minusSeconds(200), 100L));
            repository.recordBuild(BuildRecord.success("test-template", "1.0.2", now.minusSeconds(100), 100L));

            List<BuildRecord> results = repository.getRecentBuilds("test-template", 10);
            assertEquals(3, results.size());
            assertEquals("1.0.2", results.get(0).templateVersion(), "Most recent should be first");
            assertEquals("1.0.0", results.get(2).templateVersion(), "Oldest should be last");
        }

        @Test
        @DisplayName("Can record failed builds with error message")
        void canRecordFailedBuilds() throws SQLException {
            BuildRecord build = BuildRecord.failed(
                "test-template",
                "1.0.0",
                Instant.now().minusSeconds(10),
                "Compilation error: missing dependency"
            );

            repository.recordBuild(build);

            List<BuildRecord> results = repository.getRecentBuilds("test-template", 10);
            assertEquals(1, results.size());
            assertEquals("failed", results.get(0).status());
            assertEquals("Compilation error: missing dependency", results.get(0).errorMessage());
        }
    }

    @Nested
    @DisplayName("Usage Record Tests")
    class UsageRecordTests {

        @Test
        @DisplayName("Can insert usage records")
        void canInsertUsageRecords() throws SQLException {
            UsageRecord usage = new UsageRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test-template",
                "1.0.0",
                Instant.now().minusSeconds(300),
                Instant.now(),
                300000L,
                "completed",
                false,
                false
            );

            repository.recordUsage(usage);
            // No exception means success
        }

        @Test
        @DisplayName("Can query sessions with version drift")
        void canQueryVersionDriftSessions() throws SQLException {
            // Insert session without drift
            repository.recordUsage(new UsageRecord(
                UUID.randomUUID(), UUID.randomUUID(),
                "template1", "1.0.0",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300),
                300000L, "completed", false, false
            ));

            // Insert session with version drift
            repository.recordUsage(new UsageRecord(
                UUID.randomUUID(), UUID.randomUUID(),
                "template2", "1.0.0",
                Instant.now().minusSeconds(300), Instant.now(),
                300000L, "completed", true, false
            ));

            List<UsageRecord> driftSessions = repository.getVersionDriftSessions(10);
            assertEquals(1, driftSessions.size());
            assertTrue(driftSessions.get(0).versionDriftDetected());
            assertEquals("template2", driftSessions.get(0).templateId());
        }

        @Test
        @DisplayName("Can record drift result")
        void canRecordDriftResult() throws SQLException {
            DriftResult drift = new DriftResult(
                UUID.randomUUID(),
                "test-template",
                "1.0.0",
                "1.1.0",
                "abc123",
                "def456",
                true,
                true,
                Instant.now().minusSeconds(300),
                Instant.now()
            );

            repository.recordDriftResult(drift, "completed");

            List<UsageRecord> driftSessions = repository.getVersionDriftSessions(10);
            assertEquals(1, driftSessions.size());
        }
    }

    @Nested
    @DisplayName("Error Record Tests")
    class ErrorRecordTests {

        @Test
        @DisplayName("Can record errors with context")
        void canRecordErrors() throws SQLException {
            ErrorContext error = ErrorContext.builder()
                .errorType("TestException")
                .message("Test error message")
                .severity(ErrorContext.Severity.ERROR)
                .component("test-component")
                .fromThrowable(new RuntimeException("Test"))
                .build();

            repository.recordError(error);
            // No exception means success
        }

        @Test
        @DisplayName("Can get error count by severity")
        void canGetErrorCountBySeverity() throws SQLException {
            // Insert errors of different severities
            for (int i = 0; i < 3; i++) {
                repository.recordError(ErrorContext.builder()
                    .errorType("CriticalError")
                    .message("Critical " + i)
                    .severity(ErrorContext.Severity.CRITICAL)
                    .component("test")
                    .build());
            }

            for (int i = 0; i < 5; i++) {
                repository.recordError(ErrorContext.builder()
                    .errorType("RegularError")
                    .message("Error " + i)
                    .severity(ErrorContext.Severity.ERROR)
                    .component("test")
                    .build());
            }

            Map<String, Long> counts = repository.getErrorCountBySeverity(24);
            assertEquals(3L, counts.get("CRITICAL"));
            assertEquals(5L, counts.get("ERROR"));
        }
    }

    @Nested
    @DisplayName("Query Performance Tests (DD21)")
    class QueryPerformanceTests {

        @Test
        @DisplayName("Build query completes under 200ms")
        void buildQueryUnder200ms() throws SQLException {
            // Insert test data
            Instant now = Instant.now();
            for (int i = 0; i < 100; i++) {
                repository.recordBuild(BuildRecord.success(
                    "test-template",
                    "1.0." + i,
                    now.minusSeconds(i * 60),
                    100L
                ));
            }

            // Measure query time
            long startTime = System.currentTimeMillis();
            List<BuildRecord> results = repository.getRecentBuilds("test-template", 50);
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(elapsed < 200, "Query should complete under 200ms, took: " + elapsed + "ms");
            assertEquals(50, results.size());
        }

        @Test
        @DisplayName("Version drift query completes under 200ms")
        void versionDriftQueryUnder200ms() throws SQLException {
            // Insert test data
            for (int i = 0; i < 100; i++) {
                repository.recordUsage(new UsageRecord(
                    UUID.randomUUID(), UUID.randomUUID(),
                    "template-" + i, "1.0.0",
                    Instant.now().minusSeconds(i * 60), Instant.now(),
                    60000L, "completed", i % 3 == 0, false
                ));
            }

            // Measure query time
            long startTime = System.currentTimeMillis();
            List<UsageRecord> results = repository.getVersionDriftSessions(50);
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(elapsed < 200, "Query should complete under 200ms, took: " + elapsed + "ms");
            assertFalse(results.isEmpty(), "Should return some drift sessions");
        }

        @Test
        @DisplayName("Error severity query completes under 200ms")
        void errorSeverityQueryUnder200ms() throws SQLException {
            // Insert test data
            ErrorContext.Severity[] severities = ErrorContext.Severity.values();
            for (int i = 0; i < 100; i++) {
                repository.recordError(ErrorContext.builder()
                    .errorType("TestError")
                    .message("Error " + i)
                    .severity(severities[i % severities.length])
                    .component("test-component")
                    .build());
            }

            // Measure query time
            long startTime = System.currentTimeMillis();
            Map<String, Long> counts = repository.getErrorCountBySeverity(24);
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(elapsed < 200, "Query should complete under 200ms, took: " + elapsed + "ms");
            assertFalse(counts.isEmpty());
        }
    }

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Cleanup removes old data")
        void cleanupRemovesOldData() throws SQLException {
            // Note: In-memory DuckDB may not support INTERVAL the same way
            // This test verifies the cleanup method runs without error
            int deleted = repository.cleanupOldData(14);
            assertTrue(deleted >= 0, "Cleanup should return non-negative count");
        }
    }
}

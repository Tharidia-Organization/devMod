package com.devmod.arena.retention;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled retention job for arena data (DD23).
 *
 * <p>Runs daily at 04:00 to:
 * <ul>
 *   <li>Archive old NDJSON files</li>
 *   <li>Prune old DuckDB records</li>
 *   <li>Clean up orphaned files</li>
 * </ul>
 *
 * <p>Uses a dedicated logger 'arena.retention' for audit.
 */
public class RetentionJob implements AutoCloseable {

    // DD23: Separate logger for retention audit
    private static final Logger LOGGER = LoggerFactory.getLogger("arena.retention");
    private static final Logger MAIN_LOGGER = LoggerFactory.getLogger(RetentionJob.class);

    // DD23: Run at 04:00 daily
    private static final LocalTime SCHEDULED_TIME = LocalTime.of(4, 0);

    // Default retention periods
    private static final int DEFAULT_NDJSON_RETENTION_DAYS = 14;
    private static final int DEFAULT_DUCKDB_RETENTION_DAYS = 30;

    private final ScheduledExecutorService scheduler;
    private final NdjsonArchiver ndjsonArchiver;
    private final DuckDBCleaner duckdbCleaner;
    private final RetentionConfig config;

    private volatile boolean running = false;

    public RetentionJob(
            NdjsonArchiver ndjsonArchiver,
            DuckDBCleaner duckdbCleaner,
            RetentionConfig config) {
        this.ndjsonArchiver = ndjsonArchiver;
        this.duckdbCleaner = duckdbCleaner;
        this.config = config != null ? config : RetentionConfig.defaults();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArenaRetentionJob");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the scheduled retention job.
     */
    public void start() {
        long initialDelay = computeInitialDelayMs();

        scheduler.scheduleAtFixedRate(
            this::runRetention,
            initialDelay,
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.MILLISECONDS
        );

        MAIN_LOGGER.info("RetentionJob scheduled to run at {} daily (first run in {}ms)",
            SCHEDULED_TIME, initialDelay);
        running = true;
    }

    /**
     * Runs retention immediately (for testing or manual trigger).
     */
    public RetentionResult runNow() {
        return runRetention();
    }

    /**
     * Main retention logic.
     */
    private RetentionResult runRetention() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("=== Retention job started ===");

        int ndjsonArchived = 0;
        int ndjsonErrors = 0;
        int duckdbDeleted = 0;
        int duckdbErrors = 0;

        try {
            // 1. Archive old NDJSON files
            LocalDate ndjsonCutoff = LocalDate.now().minusDays(config.ndjsonRetentionDays());
            LOGGER.info("Archiving NDJSON files older than {}", ndjsonCutoff);

            try {
                ndjsonArchived = ndjsonArchiver.archiveOlderThan(ndjsonCutoff);
                LOGGER.info("Archived {} NDJSON files", ndjsonArchived);
            } catch (Exception e) {
                ndjsonErrors++;
                LOGGER.error("NDJSON archiving failed: {}", e.getMessage(), e);
            }

            // 2. Prune old DuckDB records
            LocalDate duckdbCutoff = LocalDate.now().minusDays(config.duckdbRetentionDays());
            LOGGER.info("Deleting DuckDB records older than {}", duckdbCutoff);

            try {
                duckdbDeleted = duckdbCleaner.deleteOlderThan(duckdbCutoff);
                LOGGER.info("Deleted {} DuckDB records", duckdbDeleted);
            } catch (Exception e) {
                duckdbErrors++;
                LOGGER.error("DuckDB cleanup failed: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            LOGGER.error("Retention job failed unexpectedly: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        RetentionResult result = new RetentionResult(
            ndjsonArchived,
            ndjsonErrors,
            duckdbDeleted,
            duckdbErrors,
            duration
        );

        LOGGER.info("=== Retention job completed in {}ms: {} NDJSON archived, {} DuckDB deleted, {} errors ===",
            duration, ndjsonArchived, duckdbDeleted, ndjsonErrors + duckdbErrors);

        return result;
    }

    /**
     * Computes delay until next 04:00.
     */
    private long computeInitialDelayMs() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime nextRun = now.with(SCHEDULED_TIME);

        if (now.toLocalTime().isAfter(SCHEDULED_TIME)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }

    /**
     * Returns whether the job is running.
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        MAIN_LOGGER.info("RetentionJob stopped");
    }

    /**
     * Retention configuration.
     */
    public record RetentionConfig(
        int ndjsonRetentionDays,
        int duckdbRetentionDays
    ) {
        public static RetentionConfig defaults() {
            return new RetentionConfig(DEFAULT_NDJSON_RETENTION_DAYS, DEFAULT_DUCKDB_RETENTION_DAYS);
        }
    }

    /**
     * Result of a retention run.
     */
    public record RetentionResult(
        int ndjsonArchived,
        int ndjsonErrors,
        int duckdbDeleted,
        int duckdbErrors,
        long durationMs
    ) {
        public boolean hasErrors() {
            return ndjsonErrors > 0 || duckdbErrors > 0;
        }

        public int totalProcessed() {
            return ndjsonArchived + duckdbDeleted;
        }
    }

    /**
     * Interface for NDJSON archiving.
     */
    public interface NdjsonArchiver {
        int archiveOlderThan(LocalDate cutoff);
    }

    /**
     * Interface for DuckDB cleanup.
     */
    public interface DuckDBCleaner {
        int deleteOlderThan(LocalDate cutoff);
    }
}

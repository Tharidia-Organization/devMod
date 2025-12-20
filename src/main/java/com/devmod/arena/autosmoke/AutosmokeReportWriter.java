package com.devmod.arena.autosmoke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Gap 5: Autosmoke Report Writer - writes reports to dedicated log file.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Writes autosmoke reports to logs/autosmoke-reports.log</li>
 *   <li>Rotation: daily files (autosmoke-reports-YYYY-MM-DD.log)</li>
 *   <li>Format: timestamped entries with CSV export appended</li>
 *   <li>Configurable log directory</li>
 * </ul>
 */
public class AutosmokeReportWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeReportWriter.class);

    private static final String DEFAULT_LOG_DIR = "logs";
    private static final String LOG_FILE_PREFIX = "autosmoke-reports";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path logDirectory;
    private final ZoneId zoneId;

    /**
     * Creates a new report writer with default log directory.
     */
    public AutosmokeReportWriter() {
        this(Path.of(DEFAULT_LOG_DIR), ZoneId.systemDefault());
    }

    /**
     * Creates a new report writer with custom log directory.
     *
     * @param logDirectory The directory for log files
     * @param zoneId The timezone for timestamps
     */
    public AutosmokeReportWriter(Path logDirectory, ZoneId zoneId) {
        this.logDirectory = Objects.requireNonNull(logDirectory, "logDirectory");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        ensureLogDirectory();
    }

    /**
     * Writes an autosmoke report to the dedicated log file.
     *
     * @param report The autosmoke report to write
     */
    public void writeReport(AutosmokeRunner.AutosmokeReport report) {
        if (report == null) {
            LOGGER.warn("Cannot write null report");
            return;
        }

        try {
            Path logFile = getLogFilePath();
            String entry = formatReportEntry(report);

            Files.writeString(logFile, entry,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

            LOGGER.info("Autosmoke report written to {}", logFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write autosmoke report to log file", e);
        }
    }

    /**
     * Writes a scheduled run summary to the log file.
     *
     * @param status The run status
     */
    public void writeRunStatus(AutosmokeScheduler.RunStatus status) {
        if (status == null) {
            return;
        }

        try {
            Path logFile = getLogFilePath();
            String entry = formatRunStatusEntry(status);

            Files.writeString(logFile, entry,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to write run status to log file", e);
        }
    }

    /**
     * Gets the current log file path (with date rotation).
     */
    private Path getLogFilePath() {
        String date = LocalDateTime.now(zoneId).format(DATE_FORMATTER);
        String filename = LOG_FILE_PREFIX + "-" + date + LOG_FILE_EXTENSION;
        return logDirectory.resolve(filename);
    }

    /**
     * Formats a report as a log entry.
     */
    private String formatReportEntry(AutosmokeRunner.AutosmokeReport report) {
        StringBuilder sb = new StringBuilder();

        String timestamp = LocalDateTime.now(zoneId).format(TIMESTAMP_FORMATTER);

        sb.append("\n");
        sb.append("================================================================================\n");
        sb.append("[").append(timestamp).append("] AUTOSMOKE REPORT\n");
        sb.append("================================================================================\n");

        // Header info
        if (report.header() != null) {
            sb.append("Timestamp: ").append(report.header().timestamp()).append("\n");
            sb.append("Mod Version: ").append(report.header().modVersion()).append("\n");
            sb.append("Git Commit: ").append(report.header().gitCommit()).append("\n");
            sb.append("Git Branch: ").append(report.header().gitBranch()).append("\n");
            sb.append("Java Version: ").append(report.header().javaVersion()).append("\n");
        }

        // Summary
        sb.append("\n--- SUMMARY ---\n");
        sb.append("Passed: ").append(report.passedCount()).append("\n");
        sb.append("Failed: ").append(report.failedCount()).append("\n");
        sb.append("Total Duration: ").append(report.totalDuration().toMillis()).append("ms\n");

        // Guard result
        if (report.guardResult() != null) {
            sb.append("\n--- GUARD ---\n");
            sb.append("Allowed: ").append(report.guardResult().allowed()).append("\n");
            sb.append("Env Check: ").append(report.guardResult().envCheckPassed()).append("\n");
            sb.append("Flag Check: ").append(report.guardResult().flagCheckPassed()).append("\n");
            sb.append("Marker Check: ").append(report.guardResult().markerCheckPassed()).append("\n");
            if (!report.guardResult().allowed()) {
                sb.append("Block Reasons: ").append(report.guardResult().getBlockReasons()).append("\n");
            }
        }

        // Individual results
        sb.append("\n--- RESULTS ---\n");
        sb.append(report.toCsv());

        sb.append("================================================================================\n");

        return sb.toString();
    }

    /**
     * Formats a run status as a log entry.
     */
    private String formatRunStatusEntry(AutosmokeScheduler.RunStatus status) {
        StringBuilder sb = new StringBuilder();

        String timestamp = LocalDateTime.now(zoneId).format(TIMESTAMP_FORMATTER);

        sb.append("[").append(timestamp).append("] ");
        sb.append("RUN STATUS: ").append(status.formatSummary()).append("\n");
        sb.append("  Started: ").append(status.startTime()).append("\n");
        sb.append("  Ended: ").append(status.endTime()).append("\n");
        sb.append("  Duration: ").append(status.duration().toMillis()).append("ms\n");

        return sb.toString();
    }

    /**
     * Ensures the log directory exists.
     */
    private void ensureLogDirectory() {
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
                LOGGER.info("Created autosmoke log directory: {}", logDirectory);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create log directory: {}", logDirectory, e);
        }
    }

    /**
     * Gets the path to today's log file.
     */
    public Path getTodayLogFile() {
        return getLogFilePath();
    }

    /**
     * Checks if today's log file exists.
     */
    public boolean hasTodayLog() {
        return Files.exists(getLogFilePath());
    }

    /**
     * Gets the configured log directory.
     */
    public Path getLogDirectory() {
        return logDirectory;
    }
}

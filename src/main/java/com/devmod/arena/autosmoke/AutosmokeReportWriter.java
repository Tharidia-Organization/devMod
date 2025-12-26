package com.devmod.arena.autosmoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class AutosmokeReportWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeReportWriter.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path DEFAULT_REPORT_DIR = Path.of("run", "autosmoke-reports");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final Path reportDirectory;
    private final ZoneId zoneId;
    private final int retentionDays;

    /**
     * Creates a writer with default report directory and retention.
     */
    public AutosmokeReportWriter() {
        this(DEFAULT_REPORT_DIR, ZoneId.systemDefault(), DEFAULT_RETENTION_DAYS);
    }

    /**
     * Creates a writer with custom directory and retention.
     */
    public AutosmokeReportWriter(Path reportDirectory, ZoneId zoneId, int retentionDays) {
        this.reportDirectory = Objects.requireNonNull(reportDirectory, "reportDirectory");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.retentionDays = retentionDays;
        ensureDirectory();
    }

    /**
     * Writes an autosmoke report to JSON and CSV daily files.
     */
    public void writeReport(AutosmokeRunner.AutosmokeReport report) {
        if (report == null) {
            LOGGER.warn("Cannot write null report");
            return;
        }

        String date = LocalDateTime.now(zoneId).format(DATE_FORMATTER);
        Path jsonFile = reportDirectory.resolve(date + ".json");
        Path csvFile = reportDirectory.resolve(date + ".csv");

        try {
            writeJsonReport(jsonFile, report);
            writeCsvReport(csvFile, report);
            cleanupOldReports();
            LOGGER.info("Autosmoke report written to {}", jsonFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write autosmoke report", e);
        }
    }

    /**
     * Writes a run status entry for audits.
     */
    public void writeRunStatus(AutosmokeScheduler.RunStatus status) {
        if (status == null) {
            return;
        }

        String date = LocalDateTime.now(zoneId).format(DATE_FORMATTER);
        Path statusFile = reportDirectory.resolve(date + ".status.log");
        String timestamp = LocalDateTime.now(zoneId).format(TS_FORMATTER);
        String entry = "[" + timestamp + "] " + status.formatSummary() + "\n";

        try {
            Files.writeString(statusFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to write autosmoke run status", e);
        }
    }

    private void writeJsonReport(Path jsonFile, AutosmokeRunner.AutosmokeReport report) throws IOException {
        JsonArray reports = new JsonArray();
        if (Files.exists(jsonFile)) {
            try {
                String existing = Files.readString(jsonFile);
                JsonElement parsed = JsonParser.parseString(existing);
                if (parsed.isJsonArray()) {
                    reports = parsed.getAsJsonArray();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse existing JSON report, rewriting file");
            }
        }

        JsonElement reportJson = JsonParser.parseString(report.toJson());
        reports.add(reportJson);

        Files.writeString(
            jsonFile,
            GSON.toJson(reports),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private void writeCsvReport(Path csvFile, AutosmokeRunner.AutosmokeReport report) throws IOException {
        String csv = report.toCsv();
        if (Files.exists(csvFile)) {
            int newline = csv.indexOf('\n');
            if (newline >= 0) {
                csv = csv.substring(newline + 1);
            }
        }
        Files.writeString(csvFile, csv, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void ensureDirectory() {
        try {
            if (!Files.exists(reportDirectory)) {
                Files.createDirectories(reportDirectory);
                LOGGER.info("Created autosmoke report directory: {}", reportDirectory);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create autosmoke report directory: {}", reportDirectory, e);
        }
    }

    private void cleanupOldReports() {
        if (retentionDays <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        try {
            Files.list(reportDirectory)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete old autosmoke report: {}", path);
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("Failed to cleanup autosmoke reports", e);
        }
    }

    /**
     * Returns the report directory.
     */
    public Path getReportDirectory() {
        return reportDirectory;
    }
}

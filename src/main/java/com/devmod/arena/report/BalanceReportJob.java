package com.devmod.arena.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class BalanceReportJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceReportJob.class);
    private static final LocalTime RUN_TIME = LocalTime.of(6, 0);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);

    private final BalanceDataSource dataSource;
    private final SlackNotifier slackNotifier;
    private final Path reportDirectory;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running = false;

    public BalanceReportJob(
            BalanceDataSource dataSource,
            SlackNotifier slackNotifier,
            Path reportDirectory) {
        this.dataSource = dataSource;
        this.slackNotifier = slackNotifier;
        this.reportDirectory = reportDirectory;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BalanceReportJob");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the scheduled job.
     */
    public void start() {
        if (running) return;
        running = true;

        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toMillis(7);

        scheduler.scheduleAtFixedRate(
            this::runReport,
            initialDelay,
            period,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("BalanceReportJob scheduled to run every Sunday at {}", RUN_TIME);
    }

    /**
     * Stops the scheduled job.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs the report immediately (for testing).
     */
    public BalanceReport runReport() {
        LOGGER.info("Starting balance report generation");
        long startTime = System.currentTimeMillis();

        try {
            // Query with timeout
            BalanceReport report = queryWithTimeout();

            // Persist JSON
            persistReport(report);

            // Send Slack notification
            if (report.hasOutliers()) {
                slackNotifier.sendBalanceAlert(report);
            }

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Balance report completed in {}ms", duration);

            return report;

        } catch (TimeoutException e) {
            LOGGER.error("Balance report query timed out after {}s", QUERY_TIMEOUT.getSeconds());
            return BalanceReport.failed("Query timeout");
        } catch (Exception e) {
            LOGGER.error("Balance report failed", e);
            return BalanceReport.failed(e.getMessage());
        }
    }

    private BalanceReport queryWithTimeout() throws TimeoutException {
        long deadline = System.currentTimeMillis() + QUERY_TIMEOUT.toMillis();

        // Query template stats
        List<TemplateStats> templateStats = dataSource.queryTemplateStats();
        checkTimeout(deadline);

        // Query perk stats
        List<PerkStats> perkStats = dataSource.queryPerkStats();
        checkTimeout(deadline);

        // Detect outliers (win rate <30% or >70%)
        List<Outlier> outliers = detectOutliers(templateStats, perkStats);

        return new BalanceReport(
            LocalDateTime.now(),
            templateStats,
            perkStats,
            outliers,
            true
        );
    }

    private void checkTimeout(long deadline) throws TimeoutException {
        if (System.currentTimeMillis() > deadline) {
            throw new TimeoutException("Query exceeded 30s timeout");
        }
    }

    private List<Outlier> detectOutliers(
            List<TemplateStats> templateStats,
            List<PerkStats> perkStats) {

        List<Outlier> outliers = new ArrayList<>();

        // Check templates
        for (TemplateStats stats : templateStats) {
            if (stats.winRate() < 0.30) {
                outliers.add(new Outlier(
                    OutlierType.TEMPLATE,
                    stats.templateId(),
                    stats.winRate(),
                    "Win rate below 30%"
                ));
            } else if (stats.winRate() > 0.70) {
                outliers.add(new Outlier(
                    OutlierType.TEMPLATE,
                    stats.templateId(),
                    stats.winRate(),
                    "Win rate above 70%"
                ));
            }
        }

        // Check perks
        for (PerkStats stats : perkStats) {
            if (stats.winRate() < 0.30) {
                outliers.add(new Outlier(
                    OutlierType.PERK,
                    stats.perkId(),
                    stats.winRate(),
                    "Win rate below 30%"
                ));
            } else if (stats.winRate() > 0.70) {
                outliers.add(new Outlier(
                    OutlierType.PERK,
                    stats.perkId(),
                    stats.winRate(),
                    "Win rate above 70%"
                ));
            }
        }

        return outliers;
    }

    private void persistReport(BalanceReport report) {
        try {
            Files.createDirectories(reportDirectory);

            String filename = "balance_report_" +
                report.generatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) +
                ".json";

            Path reportPath = reportDirectory.resolve(filename);
            String json = toJson(report);
            Files.writeString(reportPath, json);

            LOGGER.info("Balance report saved to {}", reportPath);
        } catch (IOException e) {
            LOGGER.error("Failed to persist balance report", e);
        }
    }

    private String toJson(BalanceReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAt\": \"").append(report.generatedAt()).append("\",\n");
        sb.append("  \"success\": ").append(report.success()).append(",\n");

        sb.append("  \"templateStats\": [\n");
        for (int i = 0; i < report.templateStats().size(); i++) {
            TemplateStats ts = report.templateStats().get(i);
            sb.append("    {\"templateId\": \"").append(ts.templateId())
              .append("\", \"matchCount\": ").append(ts.matchCount())
              .append(", \"winRate\": ").append(ts.winRate())
              .append(", \"avgDuration\": ").append(ts.avgDurationMs())
              .append("}");
            if (i < report.templateStats().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"perkStats\": [\n");
        for (int i = 0; i < report.perkStats().size(); i++) {
            PerkStats ps = report.perkStats().get(i);
            sb.append("    {\"perkId\": \"").append(ps.perkId())
              .append("\", \"usageCount\": ").append(ps.usageCount())
              .append(", \"winRate\": ").append(ps.winRate())
              .append("}");
            if (i < report.perkStats().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"outliers\": [\n");
        for (int i = 0; i < report.outliers().size(); i++) {
            Outlier o = report.outliers().get(i);
            sb.append("    {\"type\": \"").append(o.type())
              .append("\", \"id\": \"").append(o.id())
              .append("\", \"value\": ").append(o.value())
              .append(", \"reason\": \"").append(o.reason())
              .append("\"}");
            if (i < report.outliers().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}");
        return sb.toString();
    }

    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atTime(RUN_TIME);

        // Find next Sunday
        while (nextRun.getDayOfWeek() != DayOfWeek.SUNDAY || !nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }

    // ========== Data Types ==========

    public record BalanceReport(
        LocalDateTime generatedAt,
        List<TemplateStats> templateStats,
        List<PerkStats> perkStats,
        List<Outlier> outliers,
        boolean success
    ) {
        public boolean hasOutliers() {
            return !outliers.isEmpty();
        }

        public static BalanceReport failed(String reason) {
            return new BalanceReport(
                LocalDateTime.now(),
                List.of(),
                List.of(),
                List.of(),
                false
            );
        }
    }

    public record TemplateStats(
        String templateId,
        int matchCount,
        double winRate,
        long avgDurationMs
    ) {}

    public record PerkStats(
        String perkId,
        int usageCount,
        double winRate
    ) {}

    public record Outlier(
        OutlierType type,
        String id,
        double value,
        String reason
    ) {}

    public enum OutlierType {
        TEMPLATE, PERK
    }

    // ========== Dependencies ==========

    public interface BalanceDataSource {
        List<TemplateStats> queryTemplateStats();
        List<PerkStats> queryPerkStats();
    }

    public interface SlackNotifier {
        void sendBalanceAlert(BalanceReport report);
    }
}

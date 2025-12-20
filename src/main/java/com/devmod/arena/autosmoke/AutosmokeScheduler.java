package com.devmod.arena.autosmoke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DD34: Autosmoke Scheduler with cron-style scheduling.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Cron-style schedule configuration (default: 0 3 * * * = daily at 3 AM)</li>
 *   <li>Manual trigger support</li>
 *   <li>Run status tracking</li>
 *   <li>Report callbacks</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 */
public class AutosmokeScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeScheduler.class);

    /** Default schedule: daily at 3 AM */
    private static final LocalTime DEFAULT_RUN_TIME = LocalTime.of(3, 0);

    private final AutosmokeRunner runner;
    private final ScheduledExecutorService scheduler;
    private final ZoneId zoneId;

    private volatile ScheduleConfig config;
    private volatile ScheduledFuture<?> scheduledTask;
    private volatile RunStatus lastRunStatus;
    private volatile boolean running = false;

    private Consumer<AutosmokeRunner.AutosmokeReport> reportCallback;

    /**
     * Creates a new scheduler with default configuration.
     */
    public AutosmokeScheduler(AutosmokeRunner runner) {
        this(runner, ScheduleConfig.defaultConfig(), ZoneId.systemDefault());
    }

    /**
     * Creates a new scheduler with custom configuration.
     */
    public AutosmokeScheduler(AutosmokeRunner runner, ScheduleConfig config, ZoneId zoneId) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.config = Objects.requireNonNull(config, "config");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autosmoke-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the scheduler.
     */
    public void start() {
        if (!config.enabled()) {
            LOGGER.info("Autosmoke scheduler is disabled");
            return;
        }

        scheduleNextRun();
        LOGGER.info("Autosmoke scheduler started, next run at {}", getNextRunTime());
    }

    /**
     * Stops the scheduler.
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        LOGGER.info("Autosmoke scheduler stopped");
    }

    /**
     * Shuts down the scheduler completely.
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Triggers an immediate run.
     *
     * @return CompletableFuture with the report
     */
    public CompletableFuture<AutosmokeRunner.AutosmokeReport> triggerNow() {
        return CompletableFuture.supplyAsync(this::executeRun, scheduler);
    }

    /**
     * Updates the schedule configuration.
     */
    public void updateConfig(ScheduleConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");

        // Reschedule if currently scheduled
        if (scheduledTask != null) {
            stop();
            if (config.enabled()) {
                start();
            }
        }
    }

    /**
     * Sets the report callback.
     */
    public void setReportCallback(Consumer<AutosmokeRunner.AutosmokeReport> callback) {
        this.reportCallback = callback;
    }

    /**
     * Gets the last run status.
     */
    public RunStatus getLastRunStatus() {
        return lastRunStatus;
    }

    /**
     * Gets the next scheduled run time.
     */
    public LocalDateTime getNextRunTime() {
        if (scheduledTask == null || scheduledTask.isCancelled()) {
            return null;
        }

        long delayMs = scheduledTask.getDelay(TimeUnit.MILLISECONDS);
        return LocalDateTime.now(zoneId).plus(Duration.ofMillis(delayMs));
    }

    /**
     * Returns true if a run is in progress.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns true if the scheduler is enabled.
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    private void scheduleNextRun() {
        Duration delay = calculateDelayToNextRun();
        scheduledTask = scheduler.schedule(this::scheduledRun, delay.toMillis(), TimeUnit.MILLISECONDS);
        LOGGER.debug("Scheduled next autosmoke run in {}", formatDuration(delay));
    }

    private void scheduledRun() {
        try {
            executeRun();
        } finally {
            // Schedule next run
            if (config.enabled()) {
                scheduleNextRun();
            }
        }
    }

    private AutosmokeRunner.AutosmokeReport executeRun() {
        if (running) {
            LOGGER.warn("Autosmoke run already in progress, skipping");
            return null;
        }

        running = true;
        LocalDateTime startTime = LocalDateTime.now(zoneId);
        LOGGER.info("Starting scheduled autosmoke run at {}", startTime);

        try {
            AutosmokeRunner.AutosmokeReport report = runner.runAll();

            if (report != null) {
                lastRunStatus = new RunStatus(
                    startTime,
                    LocalDateTime.now(zoneId),
                    true,
                    report.passedCount(),
                    report.failedCount(),
                    null
                );

                LOGGER.info("Autosmoke run completed: {} passed, {} failed",
                    report.passedCount(), report.failedCount());

                // Notify callback
                if (reportCallback != null) {
                    try {
                        reportCallback.accept(report);
                    } catch (Exception e) {
                        LOGGER.error("Report callback failed", e);
                    }
                }
            } else {
                lastRunStatus = new RunStatus(
                    startTime,
                    LocalDateTime.now(zoneId),
                    false,
                    0,
                    0,
                    "Run blocked by guard"
                );
                LOGGER.warn("Autosmoke run was blocked by guard");
            }

            return report;

        } catch (Exception e) {
            LOGGER.error("Autosmoke run failed", e);
            lastRunStatus = new RunStatus(
                startTime,
                LocalDateTime.now(zoneId),
                false,
                0,
                0,
                e.getMessage()
            );
            return null;

        } finally {
            running = false;
        }
    }

    private Duration calculateDelayToNextRun() {
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime nextRun = now.with(config.runTime());

        // If time has passed today, schedule for tomorrow
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return String.format("%dh %dm", hours, minutes);
    }

    // ========== Configuration ==========

    /**
     * Schedule configuration.
     */
    public record ScheduleConfig(
        boolean enabled,
        LocalTime runTime,
        boolean runOnStartup
    ) {
        public static ScheduleConfig defaultConfig() {
            return new ScheduleConfig(true, DEFAULT_RUN_TIME, false);
        }

        public static ScheduleConfig disabled() {
            return new ScheduleConfig(false, DEFAULT_RUN_TIME, false);
        }

        public static ScheduleConfig withTime(int hour, int minute) {
            return new ScheduleConfig(true, LocalTime.of(hour, minute), false);
        }

        public static ScheduleConfig withTimeAndStartup(int hour, int minute) {
            return new ScheduleConfig(true, LocalTime.of(hour, minute), true);
        }

        /**
         * Parses a cron-style schedule string.
         * Supports: "HH:MM" or "0 H * * *" format.
         *
         * @param cronExpression the schedule expression
         * @return ScheduleConfig
         */
        public static ScheduleConfig fromCron(String cronExpression) {
            if (cronExpression == null || cronExpression.isBlank()) {
                return defaultConfig();
            }

            String trimmed = cronExpression.trim();

            // Try HH:MM format first
            if (trimmed.contains(":") && !trimmed.contains(" ")) {
                try {
                    LocalTime time = LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("H:mm"));
                    return new ScheduleConfig(true, time, false);
                } catch (Exception e) {
                    LOGGER.warn("Invalid time format '{}', using default", trimmed);
                    return defaultConfig();
                }
            }

            // Try cron format: "0 H * * *" (minute hour day month weekday)
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                try {
                    int minute = parseIntOrDefault(parts[0], 0);
                    int hour = parseIntOrDefault(parts[1], 3);
                    return new ScheduleConfig(true, LocalTime.of(hour, minute), false);
                } catch (Exception e) {
                    LOGGER.warn("Invalid cron expression '{}', using default", trimmed);
                    return defaultConfig();
                }
            }

            return defaultConfig();
        }

        private static int parseIntOrDefault(String s, int defaultVal) {
            if (s == null || s.equals("*")) {
                return defaultVal;
            }
            return Integer.parseInt(s);
        }
    }

    /**
     * Status of the last run.
     */
    public record RunStatus(
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean success,
        long passedCount,
        long failedCount,
        String errorMessage
    ) {
        public Duration duration() {
            return Duration.between(startTime, endTime);
        }

        public String formatSummary() {
            if (success) {
                return String.format("SUCCESS: %d passed, %d failed in %dms",
                    passedCount, failedCount, duration().toMillis());
            } else {
                return String.format("FAILED: %s", errorMessage != null ? errorMessage : "Unknown error");
            }
        }
    }
}

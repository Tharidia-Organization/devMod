package com.devmod.arena.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for telemetry audit (DD57).
 *
 * <p>Runs daily at 05:00 to detect:
 * <ul>
 *   <li>Orphan events (missing required context fields)</li>
 *   <li>Sub-service coverage gaps (12 expected services)</li>
 *   <li>Events without proper context</li>
 * </ul>
 */
public class TelemetryAuditJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryAuditJob.class);
    private static final LocalTime RUN_TIME = LocalTime.of(5, 0);

    /**
     * Required context fields for all arena events.
     */
    private static final Set<String> REQUIRED_FIELDS = Set.of(
        "arenaId",
        "templateId",
        "matchType",
        "region"
    );

    /**
     * Expected sub-services that should emit telemetry.
     */
    private static final Set<String> EXPECTED_SERVICES = Set.of(
        "arena.template",
        "arena.policy",
        "arena.build",
        "arena.spawn",
        "arena.cleanup",
        "arena.rollback",
        "arena.budget",
        "arena.metrics",
        "arena.pool",
        "arena.session",
        "arena.retention",
        "arena.recovery"
    );

    private final TelemetryEventStore eventStore;
    private final AlertNotifier alertNotifier;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running = false;

    public TelemetryAuditJob(TelemetryEventStore eventStore, AlertNotifier alertNotifier) {
        this.eventStore = eventStore;
        this.alertNotifier = alertNotifier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TelemetryAuditJob");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the scheduled audit job.
     */
    public void start() {
        if (running) return;
        running = true;

        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toMillis(1);

        scheduler.scheduleAtFixedRate(
            this::runAudit,
            initialDelay,
            period,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("TelemetryAuditJob scheduled to run daily at {}", RUN_TIME);
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
     * Runs the audit immediately (for testing).
     */
    public AuditResult runAudit() {
        LOGGER.info("Starting telemetry audit");
        long startTime = System.currentTimeMillis();

        try {
            List<OrphanEvent> orphans = findOrphanEvents();
            Set<String> missingServices = findMissingServices();
            List<EventWithoutContext> eventsWithoutContext = findEventsWithoutContext();

            AuditResult result = new AuditResult(
                orphans,
                missingServices,
                eventsWithoutContext,
                System.currentTimeMillis() - startTime
            );

            if (result.hasIssues()) {
                alertNotifier.sendAuditAlert(result);
            }

            LOGGER.info("Telemetry audit completed in {}ms: {} orphans, {} missing services",
                result.durationMs(), orphans.size(), missingServices.size());

            return result;

        } catch (Exception e) {
            LOGGER.error("Telemetry audit failed", e);
            return AuditResult.failed(e.getMessage());
        }
    }

    /**
     * Finds events with missing required fields.
     */
    private List<OrphanEvent> findOrphanEvents() {
        List<OrphanEvent> orphans = new ArrayList<>();

        eventStore.queryLastDay().forEach(event -> {
            Set<String> missingFields = new HashSet<>();
            Map<String, Object> data = event.data();

            for (String field : REQUIRED_FIELDS) {
                if (!data.containsKey(field) || data.get(field) == null) {
                    missingFields.add(field);
                }
            }

            if (!missingFields.isEmpty()) {
                orphans.add(new OrphanEvent(
                    event.name(),
                    event.timestamp().toString(),
                    missingFields
                ));
            }
        });

        return orphans;
    }

    /**
     * Finds sub-services that haven't emitted any events.
     */
    private Set<String> findMissingServices() {
        Set<String> foundServices = new HashSet<>();

        eventStore.queryLastDay().forEach(event -> {
            String eventName = event.name();
            // Extract service prefix (e.g., "arena.build" from "arena.build.start")
            int lastDot = eventName.lastIndexOf('.');
            if (lastDot > 0) {
                String servicePrefix = eventName.substring(0, lastDot);
                foundServices.add(servicePrefix);
            }
        });

        Set<String> missing = new HashSet<>(EXPECTED_SERVICES);
        missing.removeAll(foundServices);
        return missing;
    }

    /**
     * Finds emit() calls without proper context wrapper.
     */
    private List<EventWithoutContext> findEventsWithoutContext() {
        List<EventWithoutContext> issues = new ArrayList<>();

        eventStore.queryLastDay().forEach(event -> {
            Map<String, Object> data = event.data();

            // Check if this looks like a raw emit without context wrapper
            if (!data.containsKey("arenaId") &&
                !data.containsKey("templateId") &&
                event.name().startsWith("arena.")) {

                issues.add(new EventWithoutContext(
                    event.name(),
                    event.timestamp().toString()
                ));
            }
        });

        return issues;
    }

    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atTime(RUN_TIME);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }

    // ========== Result Types ==========

    /**
     * Audit result containing all detected issues.
     */
    public record AuditResult(
        List<OrphanEvent> orphanEvents,
        Set<String> missingServices,
        List<EventWithoutContext> eventsWithoutContext,
        long durationMs
    ) {
        public boolean hasIssues() {
            return !orphanEvents.isEmpty() ||
                   !missingServices.isEmpty() ||
                   !eventsWithoutContext.isEmpty();
        }

        public static AuditResult failed(String reason) {
            return new AuditResult(List.of(), Set.of(), List.of(), -1);
        }
    }

    /**
     * An event with missing required fields.
     */
    public record OrphanEvent(
        String eventName,
        String timestamp,
        Set<String> missingFields
    ) {}

    /**
     * An event emitted without context wrapper.
     */
    public record EventWithoutContext(
        String eventName,
        String timestamp
    ) {}

    // ========== Dependencies ==========

    /**
     * Interface for querying telemetry events.
     */
    public interface TelemetryEventStore {
        List<ArenaTelemetry.TelemetryEvent> queryLastDay();
    }

    /**
     * Interface for sending audit alerts.
     */
    public interface AlertNotifier {
        void sendAuditAlert(AuditResult result);
    }
}

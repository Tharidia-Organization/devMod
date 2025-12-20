package com.devmod.arena.autosmoke;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * DD32-34: Autosmoke test runner with guard, thresholds, and reporting.
 *
 * <p>Executes smoke tests against arena templates with:</p>
 * <ul>
 *   <li>Triple guard protection (ENV, flag, marker file)</li>
 *   <li>Configurable thresholds per template</li>
 *   <li>Report generation with context (git commit, config hash)</li>
 *   <li>CSV/JSON export</li>
 *   <li>Exception whitelist for known safe failures</li>
 * </ul>
 */
public class AutosmokeRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeRunner.class);

    private final AutosmokeGuard guard;
    private final ArenaTemplateRegistry registry;
    private final ExecutorService executor;
    private final List<Consumer<AutosmokeReport>> reportListeners;
    private final AutosmokeExceptions exceptionWhitelist;

    private volatile boolean running = false;
    private volatile AutosmokeReport lastReport;

    private final AutosmokeSizeThresholds sizeThresholds;

    /**
     * Creates a new runner with the given registry.
     */
    public AutosmokeRunner(ArenaTemplateRegistry registry) {
        this(registry, AutosmokeGuard.getInstance(), AutosmokeExceptions.getInstance(), new AutosmokeSizeThresholds());
    }

    /**
     * Creates a new runner with custom guard and exceptions.
     */
    public AutosmokeRunner(ArenaTemplateRegistry registry, AutosmokeGuard guard,
                           AutosmokeExceptions exceptions, AutosmokeSizeThresholds sizeThresholds) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.exceptionWhitelist = Objects.requireNonNull(exceptions, "exceptions");
        this.sizeThresholds = Objects.requireNonNull(sizeThresholds, "sizeThresholds");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "autosmoke-runner");
            t.setDaemon(true);
            return t;
        });
        this.reportListeners = new ArrayList<>();
    }

    /**
     * Runs smoke tests on all registered templates with the "smoke" tag.
     *
     * @return The test report, or null if blocked by guard
     */
    public AutosmokeReport runAll() {
        return runWithFilter(t -> t.tags() != null && t.tags().contains("smoke"));
    }

    /**
     * Runs smoke tests on templates matching the given filter.
     *
     * @param filter Template filter predicate
     * @return The test report, or null if blocked by guard
     */
    public AutosmokeReport runWithFilter(java.util.function.Predicate<ArenaTemplate> filter) {
        // Triple guard check
        AutosmokeGuard.GuardResult guardResult = guard.checkAll();
        if (!guardResult.allowed()) {
            LOGGER.error("Autosmoke BLOCKED: {}", guardResult.getBlockReasons());
            return null;
        }

        if (running) {
            LOGGER.warn("Autosmoke already running, skipping");
            return lastReport;
        }

        running = true;
        Instant startTime = Instant.now();

        try {
            LOGGER.info("Starting autosmoke run...");

            // Capture report header
            AutosmokeReportHeader.ReportHeader header = AutosmokeReportHeader.capture();
            LOGGER.info("Context: {}", header.formatCompact());

            // Collect templates to test
            List<ArenaTemplate> templates = registry.all().stream()
                .filter(filter)
                .toList();

            if (templates.isEmpty()) {
                LOGGER.warn("No templates matched filter, nothing to test");
                return createEmptyReport(header, startTime);
            }

            LOGGER.info("Testing {} templates...", templates.size());

            // Run tests
            List<TemplateTestResult> results = new ArrayList<>();
            for (ArenaTemplate template : templates) {
                TemplateTestResult result = testTemplate(template);
                results.add(result);

                if (result.passed()) {
                    LOGGER.info("  [PASS] {}", template.id());
                } else {
                    LOGGER.warn("  [FAIL] {} - {}", template.id(), result.errorMessage());
                }
            }

            // Build report
            Duration totalDuration = Duration.between(startTime, Instant.now());
            AutosmokeReport report = new AutosmokeReport(
                header,
                results,
                totalDuration,
                results.stream().filter(TemplateTestResult::passed).count(),
                results.stream().filter(r -> !r.passed()).count(),
                guardResult
            );

            lastReport = report;

            // Notify listeners
            for (Consumer<AutosmokeReport> listener : reportListeners) {
                try {
                    listener.accept(report);
                } catch (Exception e) {
                    LOGGER.error("Report listener failed", e);
                }
            }

            LOGGER.info("Autosmoke complete: {} passed, {} failed in {}ms",
                report.passedCount(), report.failedCount(), totalDuration.toMillis());

            return report;

        } finally {
            running = false;
        }
    }

    /**
     * Runs tests asynchronously.
     */
    public CompletableFuture<AutosmokeReport> runAllAsync() {
        return CompletableFuture.supplyAsync(this::runAll, executor);
    }

    /**
     * Tests a single template.
     */
    private TemplateTestResult testTemplate(ArenaTemplate template) {
        Instant start = Instant.now();
        AutosmokeThresholds thresholds = AutosmokeThresholds.forTemplate(template.id());

        try {
            // Validate template schema
            validateSchema(template);

            // Check size limits
            validateSize(template);

            // Validate spawn slots
            validateSpawnSlots(template);

            // Validate hazards
            validateHazards(template);

            // Simulate dry-run build (no actual block placement)
            simulateBuild(template, thresholds);

            Duration duration = Duration.between(start, Instant.now());
            return TemplateTestResult.passed(template.id(), duration, thresholds.modeName());

        } catch (Exception e) {
            // Check if exception is whitelisted for this template
            if (exceptionWhitelist.hasException(template.id(), AutosmokeExceptions.ExceptionCategory.ALL_THRESHOLDS)) {
                LOGGER.debug("Whitelisted exception for {}: {}", template.id(), e.getMessage());
                Duration duration = Duration.between(start, Instant.now());
                return TemplateTestResult.passed(template.id(), duration, thresholds.modeName());
            }

            Duration duration = Duration.between(start, Instant.now());
            return TemplateTestResult.failed(template.id(), duration, thresholds.modeName(), e.getMessage());
        }
    }

    private void validateSchema(ArenaTemplate template) {
        Objects.requireNonNull(template.id(), "Template ID is null");
        if (template.id().isBlank()) {
            throw new IllegalStateException("Template ID is blank");
        }
        if (template.version() < 1) {
            throw new IllegalStateException("Template version must be >= 1");
        }
    }

    private void validateSize(ArenaTemplate template) {
        int sizeX = Objects.requireNonNullElse(template.sizeX(), 64);
        int sizeZ = Objects.requireNonNullElse(template.sizeZ(), 64);

        // Estimate block count (simplified: floor + walls + ceiling)
        int estimatedBlocks = sizeX * sizeZ * 3;

        // Get threshold for this block count
        AutosmokeSizeThresholds.SizeThreshold threshold = sizeThresholds.getThresholdForBlocks(estimatedBlocks);

        if (estimatedBlocks < threshold.minBlocks()) {
            throw new IllegalStateException(
                String.format("Arena too small: ~%d blocks < %d min", estimatedBlocks, threshold.minBlocks()));
        }
        if (estimatedBlocks > threshold.maxBlocks()) {
            throw new IllegalStateException(
                String.format("Arena too large: ~%d blocks > %d max", estimatedBlocks, threshold.maxBlocks()));
        }
    }

    private void validateSpawnSlots(ArenaTemplate template) {
        var spawnSlots = template.spawnSlots();
        if (spawnSlots == null || spawnSlots.isEmpty()) {
            throw new IllegalStateException("No spawn slots defined");
        }
    }

    private void validateHazards(ArenaTemplate template) {
        var hazards = template.hazards();
        if (hazards != null && hazards.size() > 50) {
            throw new IllegalStateException("Too many hazards: " + hazards.size() + " > 50 max");
        }
    }

    private void simulateBuild(ArenaTemplate template, AutosmokeThresholds thresholds) {
        // Simulate build time check
        long estimatedMs = estimateBuildTime(template);
        if (estimatedMs > thresholds.testTimeout().toMillis()) {
            throw new IllegalStateException(
                String.format("Estimated build time too long: %dms > %dms",
                    estimatedMs, thresholds.testTimeout().toMillis()));
        }
    }

    private long estimateBuildTime(ArenaTemplate template) {
        int sizeX = Objects.requireNonNullElse(template.sizeX(), 64);
        int sizeZ = Objects.requireNonNullElse(template.sizeZ(), 64);
        int blocks = sizeX * sizeZ * 3;

        // Estimate: 1000 blocks/second = 1 block/ms
        return blocks;
    }

    private AutosmokeReport createEmptyReport(AutosmokeReportHeader.ReportHeader header, Instant startTime) {
        return new AutosmokeReport(
            header,
            List.of(),
            Duration.between(startTime, Instant.now()),
            0,
            0,
            guard.checkAll()
        );
    }

    /**
     * Adds a listener to receive reports after each run.
     */
    public void addReportListener(Consumer<AutosmokeReport> listener) {
        reportListeners.add(listener);
    }

    /**
     * Gets the last generated report.
     */
    public Optional<AutosmokeReport> getLastReport() {
        return Optional.ofNullable(lastReport);
    }

    /**
     * Returns true if a run is in progress.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Shuts down the runner.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== Result Records ==========

    /**
     * Result of testing a single template.
     * DD32-34: Extended with rollback and residual tracking for assertions.
     */
    public record TemplateTestResult(
        String templateId,
        boolean passed,
        Duration duration,
        String thresholdMode,
        String errorMessage,
        int rollbackCount,
        int entitiesResidual,
        int blocksResidual
    ) {
        public static TemplateTestResult passed(String templateId, Duration duration, String mode) {
            return new TemplateTestResult(templateId, true, duration, mode, null, 0, 0, 0);
        }

        public static TemplateTestResult passed(String templateId, Duration duration, String mode,
                                                 int rollbackCount, int entitiesResidual, int blocksResidual) {
            return new TemplateTestResult(templateId, true, duration, mode, null,
                rollbackCount, entitiesResidual, blocksResidual);
        }

        public static TemplateTestResult failed(String templateId, Duration duration, String mode, String error) {
            return new TemplateTestResult(templateId, false, duration, mode, error, 0, 0, 0);
        }

        public static TemplateTestResult failed(String templateId, Duration duration, String mode, String error,
                                                 int rollbackCount, int entitiesResidual, int blocksResidual) {
            return new TemplateTestResult(templateId, false, duration, mode, error,
                rollbackCount, entitiesResidual, blocksResidual);
        }

        /**
         * Returns true if there were any rollbacks during the test.
         */
        public boolean hadRollbacks() {
            return rollbackCount > 0;
        }

        /**
         * Returns true if there are residual entities or blocks after cleanup.
         */
        public boolean hasResiduals() {
            return entitiesResidual > 0 || blocksResidual > 0;
        }
    }

    /**
     * Complete report of an autosmoke run.
     */
    public record AutosmokeReport(
        AutosmokeReportHeader.ReportHeader header,
        List<TemplateTestResult> results,
        Duration totalDuration,
        long passedCount,
        long failedCount,
        AutosmokeGuard.GuardResult guardResult
    ) {
        /**
         * Exports the report as CSV.
         * DD32-34: Includes rollback_count, entities_residual, blocks_residual columns.
         */
        public String toCsv() {
            StringBuilder sb = new StringBuilder();
            sb.append("template_id,passed,duration_ms,threshold_mode,rollback_count,entities_residual,blocks_residual,error_message\n");
            for (TemplateTestResult r : results) {
                sb.append(String.format("%s,%s,%d,%s,%d,%d,%d,%s%n",
                    r.templateId(),
                    r.passed(),
                    r.duration().toMillis(),
                    r.thresholdMode(),
                    r.rollbackCount(),
                    r.entitiesResidual(),
                    r.blocksResidual(),
                    r.errorMessage() != null ? "\"" + r.errorMessage().replace("\"", "\"\"") + "\"" : ""
                ));
            }
            return sb.toString();
        }

        /**
         * Exports the report as JSON.
         * DD32-34: Includes rollback and residual metrics.
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"header\": ").append(header.formatJson()).append(",\n");
            sb.append("  \"summary\": {\n");
            sb.append("    \"total\": ").append(results.size()).append(",\n");
            sb.append("    \"passed\": ").append(passedCount).append(",\n");
            sb.append("    \"failed\": ").append(failedCount).append(",\n");
            sb.append("    \"total_rollbacks\": ").append(totalRollbacks()).append(",\n");
            sb.append("    \"total_residuals\": ").append(totalResiduals()).append(",\n");
            sb.append("    \"duration_ms\": ").append(totalDuration.toMillis()).append("\n");
            sb.append("  },\n");
            sb.append("  \"results\": [\n");
            for (int i = 0; i < results.size(); i++) {
                TemplateTestResult r = results.get(i);
                sb.append("    {");
                sb.append("\"template_id\":\"").append(r.templateId()).append("\",");
                sb.append("\"passed\":").append(r.passed()).append(",");
                sb.append("\"duration_ms\":").append(r.duration().toMillis()).append(",");
                sb.append("\"threshold_mode\":\"").append(r.thresholdMode()).append("\",");
                sb.append("\"rollback_count\":").append(r.rollbackCount()).append(",");
                sb.append("\"entities_residual\":").append(r.entitiesResidual()).append(",");
                sb.append("\"blocks_residual\":").append(r.blocksResidual());
                if (r.errorMessage() != null) {
                    sb.append(",\"error\":\"").append(r.errorMessage().replace("\"", "\\\"")).append("\"");
                }
                sb.append("}");
                if (i < results.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");
            return sb.toString();
        }

        /**
         * Returns total rollback count across all results.
         */
        public int totalRollbacks() {
            return results.stream().mapToInt(TemplateTestResult::rollbackCount).sum();
        }

        /**
         * Returns total residual count (entities + blocks) across all results.
         */
        public int totalResiduals() {
            return results.stream()
                .mapToInt(r -> r.entitiesResidual() + r.blocksResidual())
                .sum();
        }

        /**
         * Returns true if any test had rollbacks.
         */
        public boolean hadAnyRollbacks() {
            return results.stream().anyMatch(TemplateTestResult::hadRollbacks);
        }

        /**
         * Returns true if any test has residuals.
         */
        public boolean hasAnyResiduals() {
            return results.stream().anyMatch(TemplateTestResult::hasResiduals);
        }

        /**
         * Returns true if all tests passed.
         */
        public boolean allPassed() {
            return failedCount == 0;
        }
    }
}

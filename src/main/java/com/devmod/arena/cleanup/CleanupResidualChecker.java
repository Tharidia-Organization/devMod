package com.devmod.arena.cleanup;

import com.devmod.arena.config.ArenaTemplateConfig.AlertThresholds;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Checks cleanup residuals against AlertThresholds and emits telemetry.
 *
 * <p>After arena cleanup, this checker:
 * <ul>
 *   <li>Compares residual counts against configured thresholds</li>
 *   <li>Emits telemetry events for monitoring and alerting</li>
 *   <li>Logs warnings/errors based on severity</li>
 * </ul>
 *
 * <p>Threshold levels (from AlertThresholds):
 * <ul>
 *   <li>entitiesResidual: warn=0, error=5 (default)</li>
 *   <li>blocksResidual: warn=0, error=10 (default)</li>
 * </ul>
 *
 * @see AlertThresholds
 * @see CleanupResult
 */
public class CleanupResidualChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupResidualChecker.class);

    private final AlertThresholds thresholds;
    private final ArenaTelemetry telemetry;

    public CleanupResidualChecker(AlertThresholds thresholds, ArenaTelemetry telemetry) {
        this.thresholds = thresholds != null ? thresholds : AlertThresholds.defaults();
        this.telemetry = telemetry;
    }

    /**
     * Checks cleanup result residuals and emits appropriate telemetry.
     *
     * @param arenaId The arena UUID
     * @param templateId The template ID
     * @param result The cleanup result to check
     * @return CheckResult with severity level and details
     */
    public CheckResult check(UUID arenaId, String templateId, CleanupResult result) {
        int entitiesResidual = result.entitiesResidual();
        int blocksResidual = result.blocksResidual();

        Severity entitiesSeverity = checkThreshold(
            entitiesResidual,
            thresholds.warnEntitiesResidual(),
            thresholds.errorEntitiesResidual()
        );

        Severity blocksSeverity = checkThreshold(
            blocksResidual,
            thresholds.warnBlocksResidual(),
            thresholds.errorBlocksResidual()
        );

        Severity overallSeverity = entitiesSeverity.ordinal() > blocksSeverity.ordinal()
            ? entitiesSeverity : blocksSeverity;

        // Emit telemetry
        emitTelemetry(arenaId, templateId, result, overallSeverity);

        // Log based on severity
        logResult(arenaId, templateId, entitiesResidual, blocksResidual, overallSeverity);

        return new CheckResult(
            overallSeverity,
            entitiesResidual,
            blocksResidual,
            entitiesSeverity,
            blocksSeverity
        );
    }

    private Severity checkThreshold(int value, int warnThreshold, int errorThreshold) {
        if (value > errorThreshold) {
            return Severity.ERROR;
        } else if (value > warnThreshold) {
            return Severity.WARN;
        }
        return Severity.OK;
    }

    private void emitTelemetry(UUID arenaId, String templateId, CleanupResult result, Severity severity) {
        if (telemetry == null) return;

        // Always emit cleanup metrics for baseline tracking
        telemetry.emit("arena.cleanup.completed", Map.of(
            "arenaId", arenaId.toString(),
            "templateId", templateId != null ? templateId : "",
            "durationMs", result.durationMs(),
            "entitiesRemoved", result.entitiesRemoved(),
            "blocksRemoved", result.blocksRemoved(),
            "entities_residual", result.entitiesResidual(),
            "blocks_residual", result.blocksResidual(),
            "severity", severity.name()
        ));

        // Emit alert if above thresholds
        if (severity != Severity.OK) {
            telemetry.emit("arena.cleanup.residual_alert", Map.of(
                "arenaId", arenaId.toString(),
                "templateId", templateId != null ? templateId : "",
                "entities_residual", result.entitiesResidual(),
                "blocks_residual", result.blocksResidual(),
                "severity", severity.name(),
                "warnEntitiesThreshold", thresholds.warnEntitiesResidual(),
                "errorEntitiesThreshold", thresholds.errorEntitiesResidual(),
                "warnBlocksThreshold", thresholds.warnBlocksResidual(),
                "errorBlocksThreshold", thresholds.errorBlocksResidual()
            ));
        }
    }

    private void logResult(UUID arenaId, String templateId, int entitiesResidual, int blocksResidual, Severity severity) {
        switch (severity) {
            case ERROR -> LOGGER.error(
                "[CLEANUP_RESIDUAL] ERROR arena={} template={} entities_residual={} blocks_residual={} " +
                "(thresholds: entities>{}, blocks>{})",
                arenaId, templateId, entitiesResidual, blocksResidual,
                thresholds.errorEntitiesResidual(), thresholds.errorBlocksResidual()
            );
            case WARN -> LOGGER.warn(
                "[CLEANUP_RESIDUAL] WARN arena={} template={} entities_residual={} blocks_residual={} " +
                "(thresholds: entities>{}, blocks>{})",
                arenaId, templateId, entitiesResidual, blocksResidual,
                thresholds.warnEntitiesResidual(), thresholds.warnBlocksResidual()
            );
            case OK -> {
                if (entitiesResidual > 0 || blocksResidual > 0) {
                    LOGGER.debug(
                        "[CLEANUP_RESIDUAL] OK (within thresholds) arena={} template={} entities_residual={} blocks_residual={}",
                        arenaId, templateId, entitiesResidual, blocksResidual
                    );
                }
            }
        }
    }

    /**
     * Severity levels for residual checks.
     */
    public enum Severity {
        OK,
        WARN,
        ERROR
    }

    /**
     * Result of residual check.
     */
    public record CheckResult(
        Severity overallSeverity,
        int entitiesResidual,
        int blocksResidual,
        Severity entitiesSeverity,
        Severity blocksSeverity
    ) {
        public boolean isOk() {
            return overallSeverity == Severity.OK;
        }

        public boolean hasError() {
            return overallSeverity == Severity.ERROR;
        }
    }
}

package com.devmod.actions.legacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.engine.EngineFeatureFlags;
import com.devmod.actions.legacy.MigrationCompletionValidator.MigrationReadinessReport;
import com.devmod.arena.config.FeatureFlagRegistry;

/**
 * Manages the cutover sequence from V1 to V2 actions engine.
 *
 * <p>State machine: IDLE -> CHECKING -> CUTTING_OVER -> ACTIVE -> ROLLED_BACK
 *
 * <p>The orchestrator coordinates the actual cutover steps:
 * <ol>
 *   <li>Pre-cutover check (runs MigrationCompletionValidator)</li>
 *   <li>Enable V2 engine</li>
 *   <li>Disable shadow mode</li>
 *   <li>Verify V2 is active</li>
 *   <li>Emergency rollback if needed</li>
 * </ol>
 */
public final class CutoverOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CutoverOrchestrator.class);

    /**
     * State machine states for the cutover process.
     */
    public enum CutoverState {
        /** Initial state, no cutover attempted */
        IDLE,
        /** Running pre-cutover validation */
        CHECKING,
        /** Actively performing cutover steps */
        CUTTING_OVER,
        /** V2 is active, cutover complete */
        ACTIVE,
        /** Rollback performed, V1 restored */
        ROLLED_BACK
    }

    private final MigrationCompletionValidator validator;
    private final FeatureFlagRegistry flagRegistry;

    private volatile CutoverState state = CutoverState.IDLE;
    private final List<CutoverEvent> eventLog = new ArrayList<>();
    private MigrationReadinessReport lastReadinessReport;

    /**
     * Creates a cutover orchestrator.
     *
     * @param validator    the migration completion validator
     * @param flagRegistry the feature flag registry for toggling V2 flags
     */
    public CutoverOrchestrator(MigrationCompletionValidator validator,
                                FeatureFlagRegistry flagRegistry) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
    }

    /**
     * Returns the current cutover state.
     */
    public CutoverState getState() {
        return state;
    }

    /**
     * Runs the pre-cutover validation check.
     *
     * @return the readiness report
     * @throws IllegalStateException if cutover is already active
     */
    public MigrationReadinessReport preCutoverCheck() {
        if (state == CutoverState.ACTIVE) {
            throw new IllegalStateException("Cannot run pre-check: cutover already active");
        }

        logEvent("PRE_CHECK_START", "Running pre-cutover validation");
        state = CutoverState.CHECKING;

        MigrationReadinessReport report = validator.validate();
        lastReadinessReport = report;

        if (report.ready()) {
            logEvent("PRE_CHECK_PASSED", "Migration readiness validated: " +
                String.format("%.1f%% coverage", report.coveragePercent()));
        } else {
            logEvent("PRE_CHECK_FAILED", "Migration not ready: " +
                report.missingActions().size() + " missing actions, " +
                report.missingHandlers().size() + " missing handlers, " +
                report.orphanBindings().size() + " orphan bindings, " +
                report.validationErrors().size() + " validation errors");
            state = CutoverState.IDLE;
        }

        return report;
    }

    /**
     * Enables the V2 engine (sets ACTIONS_ENGINE_V2_ENABLED=true).
     *
     * @throws IllegalStateException if pre-check has not passed
     */
    public void enableV2() {
        if (state != CutoverState.CHECKING) {
            throw new IllegalStateException(
                "Cannot enable V2: must run preCutoverCheck() first. Current state: " + state);
        }
        if (lastReadinessReport == null || !lastReadinessReport.ready()) {
            throw new IllegalStateException("Cannot enable V2: pre-check did not pass");
        }

        logEvent("ENABLE_V2", "Enabling V2 engine");
        state = CutoverState.CUTTING_OVER;

        flagRegistry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, true);
        // Set to V2_ACTIVE mode (V2 primary, V1 fallback) as intermediate step
        flagRegistry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, true);

        logEvent("V2_ENABLED", "V2 engine enabled with V2_ACTIVE mode");
    }

    /**
     * Disables shadow mode (stops V1/V2 comparison runs).
     *
     * @throws IllegalStateException if not in CUTTING_OVER state
     */
    public void disableShadowMode() {
        if (state != CutoverState.CUTTING_OVER) {
            throw new IllegalStateException(
                "Cannot disable shadow mode: not in CUTTING_OVER state. Current state: " + state);
        }

        logEvent("DISABLE_SHADOW", "Disabling shadow mode");
        flagRegistry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, false);
        logEvent("SHADOW_DISABLED", "Shadow mode disabled");
    }

    /**
     * Verifies that V2 is active and handling all requests.
     * Transitions to ACTIVE state on success.
     *
     * @return true if V2 is confirmed active
     * @throws IllegalStateException if not in CUTTING_OVER state
     */
    public boolean verifyV2Active() {
        if (state != CutoverState.CUTTING_OVER) {
            throw new IllegalStateException(
                "Cannot verify V2: not in CUTTING_OVER state. Current state: " + state);
        }

        logEvent("VERIFY_V2", "Verifying V2 is active");

        boolean engineEnabled = flagRegistry.isEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED);
        boolean activeMode = flagRegistry.isEnabled(EngineFeatureFlags.V2_ACTIVE_MODE);
        boolean shadowOff = !flagRegistry.isEnabled(EngineFeatureFlags.V2_SHADOW_MODE);

        if (engineEnabled && activeMode && shadowOff) {
            state = CutoverState.ACTIVE;
            logEvent("V2_ACTIVE", "V2 confirmed active. Cutover complete.");
            return true;
        }

        logEvent("V2_VERIFY_FAILED", String.format(
            "V2 not fully active: engine=%s, active=%s, shadowOff=%s",
            engineEnabled, activeMode, shadowOff));
        return false;
    }

    /**
     * Emergency rollback: disables V2 and restores V1 as the sole path.
     */
    public void rollback() {
        logEvent("ROLLBACK_START", "Emergency rollback initiated. Previous state: " + state);

        flagRegistry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, false);
        flagRegistry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, false);
        flagRegistry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, false);

        state = CutoverState.ROLLED_BACK;
        logEvent("ROLLBACK_COMPLETE", "V2 disabled, V1 restored as sole path");
        LOGGER.warn("[CutoverOrchestrator] Emergency rollback complete - V1 restored");
    }

    /**
     * Generates a cutover report summarizing the process.
     */
    public CutoverReport generateCutoverReport() {
        return new CutoverReport(
            state,
            lastReadinessReport,
            List.copyOf(eventLog),
            Instant.now()
        );
    }

    // =========================================================================
    // Event logging
    // =========================================================================

    private void logEvent(String type, String detail) {
        CutoverEvent event = new CutoverEvent(type, detail, Instant.now());
        eventLog.add(event);
        LOGGER.info("[CutoverOrchestrator] {}: {}", type, detail);
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * A single event in the cutover process log.
     */
    public record CutoverEvent(String type, String detail, Instant timestamp) {}

    /**
     * Report summarizing the cutover process.
     */
    public record CutoverReport(
        CutoverState finalState,
        MigrationReadinessReport readinessReport,
        List<CutoverEvent> events,
        Instant generatedAt
    ) {
        public CutoverReport {
            events = List.copyOf(events);
        }

        /**
         * Returns a human-readable summary of the cutover.
         */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Cutover Report ===\n");
            sb.append("Final State: ").append(finalState).append("\n");
            sb.append("Generated: ").append(generatedAt).append("\n");
            sb.append("Events:\n");
            for (CutoverEvent event : events) {
                sb.append("  [").append(event.type()).append("] ")
                  .append(event.detail()).append("\n");
            }
            if (readinessReport != null) {
                sb.append("\n").append(readinessReport.toSummary());
            }
            return sb.toString();
        }
    }
}

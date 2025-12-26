package com.devmod.arena.rollout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RolloutEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolloutEvaluator.class);

    private final MetricsProvider metricsProvider;
    private final RolloutSuccessCriteria criteria;

    public RolloutEvaluator(MetricsProvider metricsProvider, RolloutSuccessCriteria criteria) {
        this.metricsProvider = metricsProvider;
        this.criteria = criteria;
    }

    public RolloutEvaluator(MetricsProvider metricsProvider) {
        this(metricsProvider, RolloutSuccessCriteria.DEFAULT);
    }

    /**
     * Evaluates if the rollout can advance to the next phase.
     */
    public EvaluationResult canAdvance(RolloutPhase currentPhase) {
        LOGGER.info("Evaluating rollout advancement from phase {}", currentPhase);

        List<GateCheck> checks = new ArrayList<>();

        // Check observation period
        checks.add(checkObservationPeriod());

        // Check sample size
        checks.add(checkSampleSize());

        // Check build P95
        checks.add(checkBuildP95());

        // Check rollback rate
        checks.add(checkRollbackRate());

        // Check completion rate
        checks.add(checkCompletionRate());

        // Check error rate
        checks.add(checkErrorRate());

        boolean canAdvance = checks.stream().allMatch(GateCheck::passed);
        RolloutPhase nextPhase = canAdvance ? currentPhase.next() : null;

        EvaluationResult result = new EvaluationResult(
            currentPhase,
            nextPhase,
            canAdvance,
            checks,
            Instant.now()
        );

        if (canAdvance) {
            LOGGER.info("Rollout can advance to phase {}", nextPhase);
        } else {
            List<String> failedGates = checks.stream()
                .filter(c -> !c.passed())
                .map(GateCheck::name)
                .toList();
            LOGGER.warn("Rollout cannot advance: failed gates {}", failedGates);
        }

        return result;
    }

    private GateCheck checkObservationPeriod() {
        Duration elapsed = metricsProvider.getObservationPeriod();
        boolean passed = elapsed.compareTo(criteria.minObservationPeriod()) >= 0;

        return new GateCheck(
            "observation_period",
            passed,
            String.format("Elapsed: %dh, Required: %dh",
                elapsed.toHours(), criteria.minObservationPeriod().toHours()),
            elapsed.toHours(),
            criteria.minObservationPeriod().toHours()
        );
    }

    private GateCheck checkSampleSize() {
        int samples = metricsProvider.getSampleCount();
        boolean passed = samples >= criteria.minSampleSize();

        return new GateCheck(
            "sample_size",
            passed,
            String.format("Samples: %d, Required: %d", samples, criteria.minSampleSize()),
            samples,
            criteria.minSampleSize()
        );
    }

    private GateCheck checkBuildP95() {
        Duration buildP95 = metricsProvider.getBuildP95();
        boolean passed = buildP95.compareTo(criteria.maxBuildP95()) <= 0;

        return new GateCheck(
            "build_p95",
            passed,
            String.format("P95: %dms, Max: %dms",
                buildP95.toMillis(), criteria.maxBuildP95().toMillis()),
            buildP95.toMillis(),
            criteria.maxBuildP95().toMillis()
        );
    }

    private GateCheck checkRollbackRate() {
        double rate = metricsProvider.getRollbackRate();
        boolean passed = rate <= criteria.maxRollbackRate();

        return new GateCheck(
            "rollback_rate",
            passed,
            String.format("Rate: %.2f%%, Max: %.2f%%", rate * 100, criteria.maxRollbackRate() * 100),
            rate,
            criteria.maxRollbackRate()
        );
    }

    private GateCheck checkCompletionRate() {
        double rate = metricsProvider.getCompletionRate();
        boolean passed = rate >= criteria.minCompletionRate();

        return new GateCheck(
            "completion_rate",
            passed,
            String.format("Rate: %.2f%%, Min: %.2f%%", rate * 100, criteria.minCompletionRate() * 100),
            rate,
            criteria.minCompletionRate()
        );
    }

    private GateCheck checkErrorRate() {
        double rate = metricsProvider.getErrorRate();
        boolean passed = rate <= criteria.maxErrorRate();

        return new GateCheck(
            "error_rate",
            passed,
            String.format("Rate: %.2f%%, Max: %.2f%%", rate * 100, criteria.maxErrorRate() * 100),
            rate,
            criteria.maxErrorRate()
        );
    }

    // ========== Data Types ==========

    /**
     * Result of a rollout evaluation.
     */
    public record EvaluationResult(
        RolloutPhase currentPhase,
        RolloutPhase nextPhase,
        boolean canAdvance,
        List<GateCheck> checks,
        Instant evaluatedAt
    ) {
        public List<GateCheck> getFailedChecks() {
            return checks.stream()
                .filter(c -> !c.passed())
                .toList();
        }

        public List<GateCheck> getPassedChecks() {
            return checks.stream()
                .filter(GateCheck::passed)
                .toList();
        }
    }

    /**
     * A single gate check result.
     */
    public record GateCheck(
        String name,
        boolean passed,
        String message,
        double actualValue,
        double thresholdValue
    ) {}

    /**
     * Rollout phases.
     */
    public enum RolloutPhase {
        CANARY_1_PERCENT,
        CANARY_5_PERCENT,
        CANARY_10_PERCENT,
        GRADUAL_25_PERCENT,
        GRADUAL_50_PERCENT,
        GRADUAL_75_PERCENT,
        FULL_ROLLOUT;

        public RolloutPhase next() {
            return switch (this) {
                case CANARY_1_PERCENT -> CANARY_5_PERCENT;
                case CANARY_5_PERCENT -> CANARY_10_PERCENT;
                case CANARY_10_PERCENT -> GRADUAL_25_PERCENT;
                case GRADUAL_25_PERCENT -> GRADUAL_50_PERCENT;
                case GRADUAL_50_PERCENT -> GRADUAL_75_PERCENT;
                case GRADUAL_75_PERCENT -> FULL_ROLLOUT;
                case FULL_ROLLOUT -> null;
            };
        }

        public int getPercentage() {
            return switch (this) {
                case CANARY_1_PERCENT -> 1;
                case CANARY_5_PERCENT -> 5;
                case CANARY_10_PERCENT -> 10;
                case GRADUAL_25_PERCENT -> 25;
                case GRADUAL_50_PERCENT -> 50;
                case GRADUAL_75_PERCENT -> 75;
                case FULL_ROLLOUT -> 100;
            };
        }
    }

    /**
     * Interface for providing metrics to the evaluator.
     */
    public interface MetricsProvider {
        Duration getObservationPeriod();
        int getSampleCount();
        Duration getBuildP95();
        double getRollbackRate();
        double getCompletionRate();
        double getErrorRate();
    }
}

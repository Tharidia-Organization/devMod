package com.devmod.arena.fallback;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DD44: Rollback Staging Test Scenarios")
class RollbackTestScenario {

    private CircuitBreaker circuitBreaker;
    private FallbackMetrics metrics;

    @BeforeEach
    void setUp() {
        // Use shorter times for testing
        circuitBreaker = new CircuitBreaker(3, 1000, 100);
        metrics = new FallbackMetrics();
    }

    /**
     * Scenario 1: Primary strategy succeeds.
     * The fallback should not be used and metrics should reflect success.
     */
    @Nested
    @DisplayName("Scenario 1: Primary Success")
    class PrimarySuccessScenario {

        @Test
        @DisplayName("Primary strategy succeeds without fallback")
        void primarySucceeds_noFallbackUsed() {
            AtomicBoolean fallbackCalled = new AtomicBoolean(false);

            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "primary-success-test",
                () -> "primary result",
                () -> {
                    fallbackCalled.set(true);
                    return "fallback result";
                },
                circuitBreaker,
                metrics
            );

            String result = assertDoesNotThrow(strategy::execute);

            assertEquals("primary result", result);
            assertFalse(fallbackCalled.get(), "Fallback should not have been called");
            assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
            assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
            assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));
        }

        @Test
        @DisplayName("Multiple primary successes accumulate correctly")
        void multiplePrimarySuccesses() {
            FallbackBuildStrategy<Integer> strategy = new FallbackBuildStrategy<>(
                "multi-success",
                () -> 42,
                () -> -1,
                circuitBreaker,
                metrics
            );

            for (int i = 0; i < 10; i++) {
                assertDoesNotThrow(strategy::execute);
            }

            assertEquals(10, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
            assertEquals(100.0, metrics.getPrimarySuccessRate(), 0.01);
        }
    }

    /**
     * Scenario 2: Primary fails, fallback succeeds.
     * The system should gracefully fall back and record the fallback usage.
     */
    @Nested
    @DisplayName("Scenario 2: Fallback Recovery")
    class FallbackRecoveryScenario {

        @Test
        @DisplayName("Fallback is used when primary fails")
        void primaryFails_fallbackSucceeds() {
            AtomicBoolean primaryCalled = new AtomicBoolean(false);
            AtomicBoolean fallbackCalled = new AtomicBoolean(false);

            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "fallback-recovery-test",
                () -> {
                    primaryCalled.set(true);
                    throw new RuntimeException("Primary failed");
                },
                () -> {
                    fallbackCalled.set(true);
                    return "fallback result";
                },
                circuitBreaker,
                metrics
            );

            String result = assertDoesNotThrow(strategy::execute);

            assertEquals("fallback result", result);
            assertTrue(primaryCalled.get(), "Primary should have been called");
            assertTrue(fallbackCalled.get(), "Fallback should have been called");
            assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
            assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
            assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));
        }

        @Test
        @DisplayName("Circuit breaker resets after successful fallback")
        void circuitBreakerResetsAfterFallbackSuccess() {
            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "circuit-reset-test",
                () -> { throw new RuntimeException("Primary failed"); },
                () -> "fallback result",
                circuitBreaker,
                metrics
            );

            // Execute once - primary fails, fallback succeeds
            assertDoesNotThrow(strategy::execute);

            // Circuit should still be closed because fallback succeeded
            assertTrue(circuitBreaker.isClosed());
            assertEquals(0, circuitBreaker.getFailureCount());
        }

        @Test
        @DisplayName("DD45: Max 1 retry - only one fallback attempt")
        void maxOneRetry() {
            AtomicInteger primaryAttempts = new AtomicInteger(0);
            AtomicInteger fallbackAttempts = new AtomicInteger(0);

            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "max-retry-test",
                () -> {
                    primaryAttempts.incrementAndGet();
                    throw new RuntimeException("Primary failed");
                },
                () -> {
                    fallbackAttempts.incrementAndGet();
                    return "fallback result";
                },
                circuitBreaker,
                metrics
            );

            assertDoesNotThrow(strategy::execute);

            assertEquals(1, primaryAttempts.get(), "Primary should be called exactly once");
            assertEquals(1, fallbackAttempts.get(), "Fallback should be called exactly once (max 1 retry)");
        }
    }

    /**
     * Scenario 3: Both primary and fallback fail.
     * The circuit breaker should eventually open to prevent cascading failures.
     */
    @Nested
    @DisplayName("Scenario 3: Complete Failure and Circuit Breaker")
    class CompleteFailureScenario {

        @Test
        @DisplayName("Both strategies fail throws exception")
        void bothStrategiesFail_throwsException() {
            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "both-fail-test",
                () -> { throw new RuntimeException("Primary failed"); },
                () -> { throw new RuntimeException("Fallback failed"); },
                circuitBreaker,
                metrics
            );

            FallbackBuildStrategy.BuildStrategyException exception =
                assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);

            assertEquals(FallbackBuildStrategy.BuildStrategyException.FailureType.ALL_STRATEGIES_FAILED,
                exception.getFailureType());
            assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));
        }

        @Test
        @DisplayName("DD45: Circuit breaker opens after 3 failures")
        void circuitBreakerOpensAfterThreshold() {
            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "circuit-open-test",
                () -> { throw new RuntimeException("Primary failed"); },
                () -> { throw new RuntimeException("Fallback failed"); },
                circuitBreaker,
                metrics
            );

            // Execute 3 times to hit threshold
            for (int i = 0; i < 3; i++) {
                assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);
            }

            // Circuit should now be open
            assertTrue(circuitBreaker.isOpen(), "Circuit should be open after 3 failures");

            // Next attempt should fail fast with CIRCUIT_OPEN
            FallbackBuildStrategy.BuildStrategyException exception =
                assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);
            assertEquals(FallbackBuildStrategy.BuildStrategyException.FailureType.CIRCUIT_OPEN,
                exception.getFailureType());
        }

        @Test
        @DisplayName("DD45: Circuit breaker cooldown allows retry after 30s")
        void circuitBreakerCooldown() throws InterruptedException {
            // Use very short cooldown for test
            CircuitBreaker fastCircuitBreaker = new CircuitBreaker(3, 1000, 50); // 50ms cooldown

            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "cooldown-test",
                () -> { throw new RuntimeException("Primary failed"); },
                () -> { throw new RuntimeException("Fallback failed"); },
                fastCircuitBreaker,
                new FallbackMetrics()
            );

            // Trip the circuit
            for (int i = 0; i < 3; i++) {
                assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);
            }

            assertTrue(fastCircuitBreaker.isOpen());

            // Wait for cooldown
            Thread.sleep(100);

            // Should transition to HALF_OPEN and allow request
            assertTrue(fastCircuitBreaker.allowRequest());
            assertEquals(CircuitBreaker.State.HALF_OPEN, fastCircuitBreaker.getState());
        }

        @Test
        @DisplayName("Complete failure includes both exceptions")
        void completeFailureIncludesBothExceptions() {
            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "exception-chain-test",
                () -> { throw new RuntimeException("Primary error message"); },
                () -> { throw new RuntimeException("Fallback error message"); },
                circuitBreaker,
                metrics
            );

            FallbackBuildStrategy.BuildStrategyException exception =
                assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);

            // Main exception should be from fallback
            assertTrue(exception.getCause().getMessage().contains("Fallback error message"));

            // Primary exception should be suppressed
            assertEquals(1, exception.getSuppressed().length);
            assertTrue(exception.getSuppressed()[0].getMessage().contains("Primary error message"));
        }
    }

    /**
     * Additional staging checklist tests.
     */
    @Nested
    @DisplayName("Staging Checklist Pre-Deploy")
    class StagingChecklist {

        @Test
        @DisplayName("Metrics are correctly recorded for all scenarios")
        void metricsAccuracy() {
            FallbackMetrics testMetrics = new FallbackMetrics();

            // Simulate various outcomes
            testMetrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
            testMetrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
            testMetrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
            testMetrics.record(FallbackMetrics.MetricType.ALL_FAILED);

            assertEquals(4, testMetrics.getTotalAttempts());
            assertEquals(2, testMetrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
            assertEquals(1, testMetrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
            assertEquals(1, testMetrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));

            assertEquals(50.0, testMetrics.getPrimarySuccessRate(), 0.01);
            assertEquals(75.0, testMetrics.getOverallSuccessRate(), 0.01);
            assertEquals(25.0, testMetrics.getOverallFailureRate(), 0.01);
        }

        @Test
        @DisplayName("Strategy can be reset")
        void strategyReset() {
            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "reset-test",
                () -> "result",
                () -> "fallback",
                circuitBreaker,
                metrics
            );

            // Execute a few times
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(strategy::execute);
            }

            assertEquals(5, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));

            // Reset
            strategy.reset(true);

            assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
            assertTrue(circuitBreaker.isClosed());
        }

        @Test
        @DisplayName("Optional execution returns empty on failure")
        void optionalExecutionOnFailure() {
            // Trip the circuit first
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure();
            }

            FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
                "optional-test",
                () -> "result",
                () -> "fallback",
                circuitBreaker,
                metrics
            );

            assertTrue(strategy.executeOptional().isEmpty());
        }
    }
}

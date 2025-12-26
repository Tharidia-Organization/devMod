package com.devmod.arena.fallback;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.arena.failure.ArenaFailureHandler;
import com.devmod.arena.failure.FailureType;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fallback Chain Integration Tests")
class FallbackIntegrationTest {

    private CircuitBreaker circuitBreaker;
    private FallbackMetrics metrics;
    private ArenaFailureHandler failureHandler;

    @BeforeAll
    static void disableFallbackLogging() {
        FallbackBuildStrategy.setLoggingEnabled(false);
    }

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker(3, 1000, 50);
        metrics = new FallbackMetrics();
        failureHandler = new ArenaFailureHandler();
        failureHandler.setLoggingEnabled(false);
    }

    @Test
    @DisplayName("End-to-end: Primary succeeds")
    void endToEndPrimarySucceeds() {
        AtomicInteger buildAttempts = new AtomicInteger(0);

        FallbackBuildStrategy<String> strategy = FallbackBuildStrategy.<String>builder()
            .name("arena-build")
            .primary(() -> {
                buildAttempts.incrementAndGet();
                return "Arena built successfully";
            })
            .fallback(() -> "Fallback arena")
            .circuitBreaker(circuitBreaker)
            .metrics(metrics)
            .build();

        String result = assertDoesNotThrow(strategy::execute);

        assertEquals("Arena built successfully", result);
        assertEquals(1, buildAttempts.get());
        assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
        assertTrue(circuitBreaker.isClosed());
    }

    @Test
    @DisplayName("End-to-end: Primary fails, fallback succeeds")
    void endToEndFallbackRecovery() {
        AtomicBoolean primaryAttempted = new AtomicBoolean(false);
        AtomicBoolean fallbackAttempted = new AtomicBoolean(false);

        FallbackBuildStrategy<String> strategy = FallbackBuildStrategy.<String>builder()
            .name("arena-build")
            .primary(() -> {
                primaryAttempted.set(true);
                throw new RuntimeException("Primary build failed - template corrupted");
            })
            .fallback(() -> {
                fallbackAttempted.set(true);
                return "Fallback arena loaded";
            })
            .circuitBreaker(circuitBreaker)
            .metrics(metrics)
            .build();

        String result = assertDoesNotThrow(strategy::execute);

        assertEquals("Fallback arena loaded", result);
        assertTrue(primaryAttempted.get());
        assertTrue(fallbackAttempted.get());
        assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
    }

    @Test
    @DisplayName("End-to-end: Both fail, failure handler invoked")
    void endToEndCompleteFailure() {
        AtomicBoolean alertTriggered = new AtomicBoolean(false);
        failureHandler.registerAlertHandler(event -> alertTriggered.set(true));

        FallbackBuildStrategy<String> strategy = FallbackBuildStrategy.<String>builder()
            .name("arena-build")
            .primary(() -> { throw new RuntimeException("Primary failed"); })
            .fallback(() -> { throw new RuntimeException("Fallback failed"); })
            .circuitBreaker(circuitBreaker)
            .metrics(metrics)
            .build();

        FallbackBuildStrategy.BuildStrategyException exception =
            assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);

        // Handle the failure
        String playerMessage = failureHandler.handleFailure(
            FailureType.BUILD_FAILED,
            exception,
            "Arena build failed"
        );

        // Verify failure handling
        assertNotNull(playerMessage);
        assertFalse(playerMessage.contains("RuntimeException")); // No tech details
        assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));
    }

    @Test
    @DisplayName("End-to-end: Circuit breaker opens after repeated failures")
    void endToEndCircuitBreakerOpens() {
        FallbackBuildStrategy<String> strategy = FallbackBuildStrategy.<String>builder()
            .name("arena-build")
            .primary(() -> { throw new RuntimeException("Primary failed"); })
            .fallback(() -> { throw new RuntimeException("Fallback failed"); })
            .circuitBreaker(circuitBreaker)
            .metrics(metrics)
            .build();

        // Fail 3 times to trip circuit
        for (int i = 0; i < 3; i++) {
            assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);
        }

        // Circuit should be open
        assertTrue(circuitBreaker.isOpen());

        // Next attempt should fail fast
        FallbackBuildStrategy.BuildStrategyException fastFail =
            assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);

        assertEquals(FallbackBuildStrategy.BuildStrategyException.FailureType.CIRCUIT_OPEN,
            fastFail.getFailureType());

        // Metrics should show 4 ALL_FAILED (3 actual + 1 circuit open)
        assertEquals(4, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));
    }

    @Test
    @DisplayName("End-to-end: Circuit breaker recovers after cooldown")
    void endToEndCircuitBreakerRecovery() throws InterruptedException {
        AtomicInteger primaryCalls = new AtomicInteger(0);

        FallbackBuildStrategy<String> strategy = FallbackBuildStrategy.<String>builder()
            .name("arena-build")
            .primary(() -> {
                int call = primaryCalls.incrementAndGet();
                if (call <= 3) {
                    throw new RuntimeException("Temporary failure");
                }
                return "Recovered!";
            })
            .fallback(() -> { throw new RuntimeException("Fallback failed"); })
            .circuitBreaker(circuitBreaker)
            .metrics(metrics)
            .build();

        // Fail 3 times to trip circuit
        for (int i = 0; i < 3; i++) {
            assertThrows(FallbackBuildStrategy.BuildStrategyException.class, strategy::execute);
        }

        assertTrue(circuitBreaker.isOpen());

        // Wait for cooldown
        Thread.sleep(100);

        // Now should succeed
        String result = assertDoesNotThrow(strategy::execute);

        assertEquals("Recovered!", result);
        assertTrue(circuitBreaker.isClosed());
    }

    @Test
    @DisplayName("End-to-end: Metrics summary is accurate")
    void endToEndMetricsSummary() {
        FallbackBuildStrategy<String> successStrategy = new FallbackBuildStrategy<>(
            "success",
            () -> "ok",
            () -> "fallback",
            new CircuitBreaker(3, 1000, 50),
            metrics
        );

        FallbackBuildStrategy<String> fallbackStrategy = new FallbackBuildStrategy<>(
            "fallback-used",
            () -> { throw new RuntimeException("fail"); },
            () -> "recovered",
            new CircuitBreaker(3, 1000, 50),
            metrics
        );

        // 5 primary successes
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(successStrategy::execute);
        }

        // 3 fallback recoveries
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(fallbackStrategy::execute);
        }

        // Check metrics
        assertEquals(8, metrics.getTotalAttempts());
        assertEquals(5, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));
        assertEquals(3, metrics.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
        assertEquals(0, metrics.getCount(FallbackMetrics.MetricType.ALL_FAILED));

        assertEquals(62.5, metrics.getPrimarySuccessRate(), 0.01); // 5/8
        assertEquals(37.5, metrics.getFallbackRate(), 0.01);       // 3/8
        assertEquals(100.0, metrics.getOverallSuccessRate(), 0.01); // (5+3)/8
    }

    @Test
    @DisplayName("Staging checklist: Full scenario validation")
    void stagingChecklistFullScenario() {
        // This test simulates a real deployment scenario

        // Setup
        CircuitBreaker arenaCircuit = new CircuitBreaker();
        FallbackMetrics arenaMetrics = new FallbackMetrics();
        ArenaFailureHandler handler = new ArenaFailureHandler();
        handler.setLoggingEnabled(false);

        AtomicBoolean criticalAlertSent = new AtomicBoolean(false);
        handler.registerAlertHandler(event -> criticalAlertSent.set(true));

        // Simulate arena build strategy
        AtomicInteger buildCount = new AtomicInteger(0);
        FallbackBuildStrategy<String> arenaBuild = FallbackBuildStrategy.<String>builder()
            .name("production-arena-build")
            .primary(() -> {
                int count = buildCount.incrementAndGet();
                if (count % 10 == 0) {
                    throw new RuntimeException("Occasional primary failure");
                }
                return "Arena-" + count;
            })
            .fallback(() -> "FallbackArena")
            .circuitBreaker(arenaCircuit)
            .metrics(arenaMetrics)
            .build();

        // Run 100 arena builds
        int successes = 0;
        for (int i = 0; i < 100; i++) {
            try {
                arenaBuild.execute();
                successes++;
            } catch (Exception e) {
                handler.handleFailure(FailureType.BUILD_FAILED, e, "Build " + i);
            }
        }

        // Verify staging criteria
        assertTrue(successes >= 90, "At least 90% success rate required");
        assertTrue(arenaMetrics.getOverallSuccessRate() >= 90.0,
            "Overall success rate should be >= 90%");
        assertTrue(arenaCircuit.isClosed(),
            "Circuit breaker should remain closed with high success rate");

        System.out.println("Staging Checklist Results:");
        System.out.println(arenaMetrics.getSummary());
        System.out.println("Circuit State: " + arenaCircuit.getState());
        System.out.println("Critical Alerts: " + criticalAlertSent.get());
    }
}

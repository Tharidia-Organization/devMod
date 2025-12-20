package com.devmod.arena.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FallbackBuildStrategy.
 * DD45: Max 1 retry.
 */
@DisplayName("FallbackBuildStrategy Tests")
class FallbackBuildStrategyTest {

    private CircuitBreaker circuitBreaker;
    private FallbackMetrics metrics;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker(3, 1000, 50);
        metrics = new FallbackMetrics();
    }

    @Test
    @DisplayName("DD45: Max 1 retry - only one fallback attempt")
    void maxOneRetry() {
        AtomicInteger primaryCalls = new AtomicInteger(0);
        AtomicInteger fallbackCalls = new AtomicInteger(0);

        FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
            "test",
            () -> {
                primaryCalls.incrementAndGet();
                throw new RuntimeException("fail");
            },
            () -> {
                fallbackCalls.incrementAndGet();
                return "ok";
            },
            circuitBreaker,
            metrics
        );

        assertDoesNotThrow(strategy::execute);

        assertEquals(1, primaryCalls.get(), "Primary should be called exactly once");
        assertEquals(1, fallbackCalls.get(), "Fallback should be called exactly once (max 1 retry)");
    }

    @Test
    @DisplayName("Builder pattern works correctly")
    void builderPatternWorks() {
        FallbackBuildStrategy<Integer> strategy = FallbackBuildStrategy.<Integer>builder()
            .name("builder-test")
            .primary(() -> 42)
            .fallback(() -> -1)
            .build();

        assertEquals("builder-test", strategy.getStrategyName());
        assertEquals(42, assertDoesNotThrow(strategy::execute));
    }

    @Test
    @DisplayName("Null primary strategy throws exception")
    void nullPrimaryThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new FallbackBuildStrategy<>(null, () -> "fallback"));
    }

    @Test
    @DisplayName("Null fallback strategy throws exception")
    void nullFallbackThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new FallbackBuildStrategy<>(() -> "primary", null));
    }

    @Test
    @DisplayName("Metrics are recorded correctly")
    void metricsRecordedCorrectly() {
        FallbackBuildStrategy<String> successStrategy = new FallbackBuildStrategy<>(
            "success",
            () -> "ok",
            () -> "fallback",
            circuitBreaker,
            metrics
        );

        assertDoesNotThrow(successStrategy::execute);
        assertEquals(1, metrics.getCount(FallbackMetrics.MetricType.PRIMARY_SUCCESS));

        // Reset for next test
        CircuitBreaker cb2 = new CircuitBreaker(3, 1000, 50);
        FallbackMetrics m2 = new FallbackMetrics();

        FallbackBuildStrategy<String> fallbackStrategy = new FallbackBuildStrategy<>(
            "fallback-used",
            () -> { throw new RuntimeException("fail"); },
            () -> "fallback",
            cb2,
            m2
        );

        assertDoesNotThrow(fallbackStrategy::execute);
        assertEquals(1, m2.getCount(FallbackMetrics.MetricType.FALLBACK_USED));
    }

    @Test
    @DisplayName("Optional execution returns value on success")
    void optionalExecutionReturnsValue() {
        FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
            "optional-test",
            () -> "success",
            () -> "fallback",
            circuitBreaker,
            metrics
        );

        assertTrue(strategy.executeOptional().isPresent());
        assertEquals("success", strategy.executeOptional().get());
    }

    @Test
    @DisplayName("Optional execution returns empty on complete failure")
    void optionalExecutionReturnsEmptyOnFailure() {
        // Trip circuit first
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
            "optional-fail-test",
            () -> "success",
            () -> "fallback",
            circuitBreaker,
            metrics
        );

        assertTrue(strategy.executeOptional().isEmpty());
    }

    @Test
    @DisplayName("Strategy name defaults to 'default'")
    void defaultStrategyName() {
        FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
            () -> "primary",
            () -> "fallback"
        );

        assertEquals("default", strategy.getStrategyName());
    }

    @Test
    @DisplayName("Getters return correct instances")
    void gettersReturnCorrectInstances() {
        FallbackBuildStrategy<String> strategy = new FallbackBuildStrategy<>(
            "getter-test",
            () -> "primary",
            () -> "fallback",
            circuitBreaker,
            metrics
        );

        assertSame(circuitBreaker, strategy.getCircuitBreaker());
        assertSame(metrics, strategy.getMetrics());
    }
}

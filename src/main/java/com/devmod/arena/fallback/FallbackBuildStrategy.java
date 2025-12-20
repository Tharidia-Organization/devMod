package com.devmod.arena.fallback;

import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback build strategy with circuit breaker pattern.
 * DD45: Fallback Chain Limits - max 1 retry, circuit breaker 3/5min.
 *
 * <p>This strategy executes a primary build operation and falls back to
 * an alternative if the primary fails. The circuit breaker prevents
 * cascading failures by failing fast when too many failures occur.
 *
 * @param <T> The type of result produced by the build strategies
 */
public class FallbackBuildStrategy<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackBuildStrategy.class);

    private final CircuitBreaker circuitBreaker;
    private final FallbackMetrics metrics;
    private final Supplier<T> primaryStrategy;
    private final Supplier<T> fallbackStrategy;
    private final String strategyName;

    /**
     * Creates a new FallbackBuildStrategy.
     *
     * @param primaryStrategy The primary build strategy
     * @param fallbackStrategy The fallback strategy to use if primary fails
     */
    public FallbackBuildStrategy(Supplier<T> primaryStrategy, Supplier<T> fallbackStrategy) {
        this("default", primaryStrategy, fallbackStrategy, new CircuitBreaker(), new FallbackMetrics());
    }

    /**
     * Creates a new FallbackBuildStrategy with custom circuit breaker.
     *
     * @param strategyName Name for logging purposes
     * @param primaryStrategy The primary build strategy
     * @param fallbackStrategy The fallback strategy to use if primary fails
     * @param circuitBreaker Custom circuit breaker instance
     * @param metrics Metrics instance for recording
     */
    public FallbackBuildStrategy(
            String strategyName,
            Supplier<T> primaryStrategy,
            Supplier<T> fallbackStrategy,
            CircuitBreaker circuitBreaker,
            FallbackMetrics metrics) {

        if (primaryStrategy == null) {
            throw new IllegalArgumentException("Primary strategy cannot be null");
        }
        if (fallbackStrategy == null) {
            throw new IllegalArgumentException("Fallback strategy cannot be null");
        }
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("Circuit breaker cannot be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("Metrics cannot be null");
        }

        this.strategyName = strategyName != null ? strategyName : "default";
        this.primaryStrategy = primaryStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
    }

    /**
     * Execute the build strategy with fallback support.
     * DD45: Max 1 retry (fallback), circuit breaker protection.
     *
     * @return The result of the successful build strategy
     * @throws BuildStrategyException if all strategies fail
     */
    public T execute() throws BuildStrategyException {
        // Check circuit breaker first
        if (!circuitBreaker.allowRequest()) {
            LOGGER.warn("[{}] Circuit breaker is open, failing fast", strategyName);
            metrics.record(FallbackMetrics.MetricType.ALL_FAILED);
            throw new BuildStrategyException(
                "Circuit breaker is open - too many recent failures",
                BuildStrategyException.FailureType.CIRCUIT_OPEN
            );
        }

        // Try primary strategy
        long primaryStart = System.nanoTime();
        try {
            T result = primaryStrategy.get();
            long elapsed = System.nanoTime() - primaryStart;
            metrics.recordPrimaryTime(elapsed);
            circuitBreaker.recordSuccess();
            metrics.record(FallbackMetrics.MetricType.PRIMARY_SUCCESS);
            LOGGER.debug("[{}] Primary strategy succeeded in {}ms", strategyName, elapsed / 1_000_000.0);
            return result;
        } catch (Exception primaryException) {
            long elapsed = System.nanoTime() - primaryStart;
            metrics.recordPrimaryTime(elapsed);
            LOGGER.warn("[{}] Primary strategy failed after {}ms: {}",
                strategyName, elapsed / 1_000_000.0, primaryException.getMessage());

            // DD45: Max 1 retry - try fallback once
            return tryFallback(primaryException);
        }
    }

    /**
     * Execute build strategy, returning Optional instead of throwing.
     *
     * @return Optional containing the result, or empty if all strategies fail
     */
    public Optional<T> executeOptional() {
        try {
            return Optional.of(execute());
        } catch (BuildStrategyException e) {
            LOGGER.debug("[{}] Build strategy failed: {}", strategyName, e.getMessage());
            return Optional.empty();
        }
    }

    private T tryFallback(Exception primaryException) throws BuildStrategyException {
        long fallbackStart = System.nanoTime();
        try {
            T result = fallbackStrategy.get();
            long elapsed = System.nanoTime() - fallbackStart;
            metrics.recordFallbackTime(elapsed);
            circuitBreaker.recordSuccess();
            metrics.record(FallbackMetrics.MetricType.FALLBACK_USED);
            LOGGER.info("[{}] Fallback strategy succeeded in {}ms", strategyName, elapsed / 1_000_000.0);
            return result;
        } catch (Exception fallbackException) {
            long elapsed = System.nanoTime() - fallbackStart;
            metrics.recordFallbackTime(elapsed);
            circuitBreaker.recordFailure();
            metrics.record(FallbackMetrics.MetricType.ALL_FAILED);

            LOGGER.error("[{}] All strategies failed. Primary: {}, Fallback: {}",
                strategyName, primaryException.getMessage(), fallbackException.getMessage());

            // Combine both exceptions for diagnosis
            BuildStrategyException exception = new BuildStrategyException(
                "All build strategies failed",
                BuildStrategyException.FailureType.ALL_STRATEGIES_FAILED,
                fallbackException
            );
            exception.addSuppressed(primaryException);
            throw exception;
        }
    }

    /**
     * Get the circuit breaker for this strategy.
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Get the metrics for this strategy.
     */
    public FallbackMetrics getMetrics() {
        return metrics;
    }

    /**
     * Get the strategy name.
     */
    public String getStrategyName() {
        return strategyName;
    }

    /**
     * Reset the circuit breaker and optionally the metrics.
     */
    public void reset(boolean resetMetrics) {
        circuitBreaker.reset();
        if (resetMetrics) {
            metrics.reset();
        }
    }

    /**
     * Exception thrown when build strategies fail.
     */
    public static class BuildStrategyException extends Exception {
        private static final long serialVersionUID = 1L;

        public enum FailureType {
            PRIMARY_FAILED,
            FALLBACK_FAILED,
            ALL_STRATEGIES_FAILED,
            CIRCUIT_OPEN
        }

        private final FailureType failureType;

        public BuildStrategyException(String message, FailureType failureType) {
            super(message);
            this.failureType = failureType;
        }

        public BuildStrategyException(String message, FailureType failureType, Throwable cause) {
            super(message, cause);
            this.failureType = failureType;
        }

        public FailureType getFailureType() {
            return failureType;
        }
    }

    /**
     * Builder for creating FallbackBuildStrategy instances.
     */
    public static class Builder<T> {
        private String name = "default";
        private Supplier<T> primaryStrategy;
        private Supplier<T> fallbackStrategy;
        private CircuitBreaker circuitBreaker;
        private FallbackMetrics metrics;

        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> primary(Supplier<T> strategy) {
            this.primaryStrategy = strategy;
            return this;
        }

        public Builder<T> fallback(Supplier<T> strategy) {
            this.fallbackStrategy = strategy;
            return this;
        }

        public Builder<T> circuitBreaker(CircuitBreaker breaker) {
            this.circuitBreaker = breaker;
            return this;
        }

        public Builder<T> metrics(FallbackMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public FallbackBuildStrategy<T> build() {
            if (circuitBreaker == null) {
                circuitBreaker = new CircuitBreaker();
            }
            if (metrics == null) {
                metrics = new FallbackMetrics();
            }
            return new FallbackBuildStrategy<>(name, primaryStrategy, fallbackStrategy, circuitBreaker, metrics);
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}

package com.devmod.arena.fallback;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DD65: Unified graceful degradation manager for arena subsystem.
 *
 * <p>Coordinates degradation across multiple services:
 * <ul>
 *   <li>Pool degradation: Disables prebuild pool on high miss rate</li>
 *   <li>Build degradation: Falls back to sync builds on async failures</li>
 *   <li>External service degradation: Uses circuit breakers</li>
 *   <li>Feature degradation: Disables non-essential features under load</li>
 * </ul>
 *
 * <p>Degradation levels:
 * <ul>
 *   <li>NORMAL: All features enabled</li>
 *   <li>DEGRADED: Non-essential features disabled</li>
 *   <li>MINIMAL: Only critical functionality</li>
 *   <li>EMERGENCY: Reject new requests, drain existing</li>
 * </ul>
 */
public class GracefulDegradationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulDegradationManager.class);

    private final Map<ServiceType, CircuitBreaker> circuitBreakers;
    private final Map<ServiceType, Supplier<Boolean>> healthChecks;

    private final AtomicReference<DegradationLevel> currentLevel = new AtomicReference<>(DegradationLevel.NORMAL);
    private final ConcurrentHashMap<Feature, Boolean> featureStates = new ConcurrentHashMap<>();

    private volatile Instant lastEvaluation = Instant.now();
    private volatile String degradationReason = null;

    public GracefulDegradationManager() {
        this.circuitBreakers = new EnumMap<>(ServiceType.class);
        this.healthChecks = new EnumMap<>(ServiceType.class);

        // Initialize all features as enabled
        for (Feature feature : Feature.values()) {
            featureStates.put(feature, true);
        }
    }

    /**
     * Registers a circuit breaker for a service type.
     */
    public void registerCircuitBreaker(ServiceType serviceType, CircuitBreaker breaker) {
        circuitBreakers.put(serviceType, breaker);
    }

    /**
     * Registers a health check for a service type.
     */
    public void registerHealthCheck(ServiceType serviceType, Supplier<Boolean> healthCheck) {
        healthChecks.put(serviceType, healthCheck);
    }

    /**
     * DD65: Evaluates current system state and adjusts degradation level.
     */
    public DegradationLevel evaluate() {
        lastEvaluation = Instant.now();

        int unhealthyCount = 0;
        int openCircuits = 0;

        // Check health of registered services
        for (var entry : healthChecks.entrySet()) {
            try {
                if (!entry.getValue().get()) {
                    unhealthyCount++;
                    LOGGER.warn("Service {} is unhealthy", entry.getKey());
                }
            } catch (Exception e) {
                unhealthyCount++;
                LOGGER.warn("Health check failed for {}: {}", entry.getKey(), e.getMessage());
            }
        }

        // Check circuit breakers
        for (var entry : circuitBreakers.entrySet()) {
            if (entry.getValue().getState() == CircuitBreaker.State.OPEN) {
                openCircuits++;
                LOGGER.warn("Circuit breaker OPEN for {}", entry.getKey());
            }
        }

        // Determine degradation level based on conditions
        DegradationLevel newLevel = calculateLevel(unhealthyCount, openCircuits);

        DegradationLevel oldLevel = currentLevel.getAndSet(newLevel);
        if (oldLevel != newLevel) {
            LOGGER.warn("Degradation level changed: {} -> {} (unhealthy={}, openCircuits={})",
                oldLevel, newLevel, unhealthyCount, openCircuits);
            applyDegradation(newLevel);
        }

        return newLevel;
    }

    private DegradationLevel calculateLevel(int unhealthyCount, int openCircuits) {
        int totalServices = healthChecks.size();

        // EMERGENCY: More than 50% of services are down
        if (totalServices > 0 && (double) unhealthyCount / totalServices > 0.5) {
            degradationReason = "More than 50% of services unhealthy";
            return DegradationLevel.EMERGENCY;
        }

        // MINIMAL: Multiple critical services down or circuits open
        if (unhealthyCount >= 2 || openCircuits >= 2) {
            degradationReason = "Multiple services degraded";
            return DegradationLevel.MINIMAL;
        }

        // DEGRADED: Single service issue
        if (unhealthyCount >= 1 || openCircuits >= 1) {
            degradationReason = "Service degraded";
            return DegradationLevel.DEGRADED;
        }

        // NORMAL: All services healthy
        degradationReason = null;
        return DegradationLevel.NORMAL;
    }

    private void applyDegradation(DegradationLevel level) {
        switch (level) {
            case EMERGENCY -> {
                // Disable all non-essential features
                disableFeature(Feature.PREBUILD_POOL);
                disableFeature(Feature.ASYNC_BUILDS);
                disableFeature(Feature.LEADERBOARDS);
                disableFeature(Feature.TELEMETRY_DETAILED);
                disableFeature(Feature.PROGRESS_OVERLAY);
            }
            case MINIMAL -> {
                // Disable expensive features
                disableFeature(Feature.PREBUILD_POOL);
                disableFeature(Feature.ASYNC_BUILDS);
                disableFeature(Feature.LEADERBOARDS);
                enableFeature(Feature.TELEMETRY_DETAILED);
                enableFeature(Feature.PROGRESS_OVERLAY);
            }
            case DEGRADED -> {
                // Disable nice-to-have features
                disableFeature(Feature.PREBUILD_POOL);
                enableFeature(Feature.ASYNC_BUILDS);
                enableFeature(Feature.LEADERBOARDS);
                enableFeature(Feature.TELEMETRY_DETAILED);
                enableFeature(Feature.PROGRESS_OVERLAY);
            }
            case NORMAL -> {
                // Enable all features
                for (Feature feature : Feature.values()) {
                    enableFeature(feature);
                }
            }
        }
    }

    /**
     * DD65: Checks if a feature is currently enabled.
     */
    public boolean isFeatureEnabled(Feature feature) {
        return featureStates.getOrDefault(feature, false);
    }

    /**
     * DD65: Manually enables a feature (for recovery or testing).
     */
    public void enableFeature(Feature feature) {
        Boolean previous = featureStates.put(feature, true);
        if (previous != null && !previous) {
            LOGGER.info("Feature {} enabled", feature);
        }
    }

    /**
     * DD65: Manually disables a feature.
     */
    public void disableFeature(Feature feature) {
        Boolean previous = featureStates.put(feature, false);
        if (previous != null && previous) {
            LOGGER.info("Feature {} disabled", feature);
        }
    }

    /**
     * DD65: Gets current degradation status.
     */
    public DegradationStatus getStatus() {
        return new DegradationStatus(
            currentLevel.get(),
            degradationReason,
            lastEvaluation,
            Map.copyOf(featureStates)
        );
    }

    /**
     * DD65: Forces a specific degradation level (for testing or manual override).
     */
    public void forceLevel(DegradationLevel level) {
        LOGGER.warn("Forcing degradation level to {}", level);
        currentLevel.set(level);
        applyDegradation(level);
    }

    /**
     * DD65: Resets to normal operation.
     */
    public void reset() {
        LOGGER.info("Resetting degradation manager to NORMAL");
        currentLevel.set(DegradationLevel.NORMAL);
        degradationReason = null;
        for (Feature feature : Feature.values()) {
            featureStates.put(feature, true);
        }
    }

    // === Types ===

    public enum DegradationLevel {
        NORMAL,     // All features enabled
        DEGRADED,   // Non-essential features disabled
        MINIMAL,    // Only critical functionality
        EMERGENCY   // Reject new requests, drain existing
    }

    public enum ServiceType {
        DATABASE,
        POOL,
        EXTERNAL_API,
        REDIS,
        TELEMETRY
    }

    public enum Feature {
        PREBUILD_POOL,
        ASYNC_BUILDS,
        LEADERBOARDS,
        TELEMETRY_DETAILED,
        PROGRESS_OVERLAY
    }

    public record DegradationStatus(
        DegradationLevel level,
        String reason,
        Instant lastEvaluation,
        Map<Feature, Boolean> featureStates
    ) {
        public boolean isHealthy() {
            return level == DegradationLevel.NORMAL;
        }

        public boolean isDegraded() {
            return level != DegradationLevel.NORMAL;
        }

        public boolean isEmergency() {
            return level == DegradationLevel.EMERGENCY;
        }
    }
}

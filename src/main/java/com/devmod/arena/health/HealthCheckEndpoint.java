package com.devmod.arena.health;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.errorprone.annotations.InlineMe;

import com.devmod.arena.fallback.CircuitBreaker;
import com.devmod.arena.pool.PrebuildPoolManager;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

public class HealthCheckEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckEndpoint.class);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final Supplier<PrebuildPoolManager> poolManagerSupplier;
    private final Supplier<CircuitBreaker> circuitBreakerSupplier;

    private volatile boolean startupComplete = false;
    private volatile Instant startupTime;

    public HealthCheckEndpoint(
            Supplier<PrebuildPoolManager> poolManagerSupplier,
            Supplier<CircuitBreaker> circuitBreakerSupplier) {
        this.poolManagerSupplier = poolManagerSupplier;
        this.circuitBreakerSupplier = circuitBreakerSupplier;
    }

    /**
     * Legacy constructor for backwards compatibility.
     * @deprecated Use constructor without repository supplier
     */
    @Deprecated
    @InlineMe(
        replacement = "this(poolManagerSupplier, circuitBreakerSupplier)"
    )
    public HealthCheckEndpoint(
            Supplier<PrebuildPoolManager> poolManagerSupplier,
            Object repositorySupplier,
            Supplier<CircuitBreaker> circuitBreakerSupplier) {
        this(poolManagerSupplier, circuitBreakerSupplier);
    }

    /**
     * Marks startup as complete. Call after all initialization is done.
     */
    public void markStartupComplete() {
        this.startupComplete = true;
        this.startupTime = Instant.now();
        LOGGER.info("Arena subsystem startup complete");
    }

    /**
     * DD64: Liveness probe - is the service alive?
     *
     * <p>Returns UP if JVM is running and basic operations work.
     * Used by Kubernetes to determine if pod should be restarted.
     */
    public HealthStatus liveness() {
        return HealthStatus.up("arena-liveness")
            .withDetail("timestamp", Instant.now().toString())
            .build();
    }

    /**
     * DD64: Readiness probe - can the service accept requests?
     *
     * <p>Checks all critical dependencies before accepting traffic.
     * Used by Kubernetes to determine if pod should receive traffic.
     */
    public HealthStatus readiness() {
        Map<String, ComponentHealth> components = new LinkedHashMap<>();

        // Check pool manager
        components.put("pool", checkPool());

        // Check database
        components.put("database", checkDatabase());

        // Check circuit breaker
        components.put("circuitBreaker", checkCircuitBreaker());

        boolean allHealthy = components.values().stream()
            .allMatch(c -> c.status() == Status.UP);

        return new HealthStatus(
            allHealthy ? Status.UP : Status.DOWN,
            "arena-readiness",
            components,
            Instant.now()
        );
    }

    /**
     * DD64: Startup probe - has the service finished initializing?
     *
     * <p>Used by Kubernetes to know when to start liveness/readiness probes.
     */
    public HealthStatus startup() {
        if (!startupComplete) {
            return HealthStatus.down("arena-startup")
                .withDetail("reason", "Initialization in progress")
                .build();
        }

        return HealthStatus.up("arena-startup")
            .withDetail("startupTime", startupTime.toString())
            .withDetail("uptime", Duration.between(startupTime, Instant.now()).toString())
            .build();
    }

    /**
     * DD64: Full health check with all component details.
     */
    public HealthStatus health() {
        HealthStatus readiness = readiness();
        HealthStatus startup = startup();

        Map<String, ComponentHealth> allComponents = new LinkedHashMap<>(readiness.components());
        allComponents.put("startup", new ComponentHealth(
            startup.status(),
            startup.details()
        ));

        boolean overallHealthy = readiness.status() == Status.UP && startup.status() == Status.UP;

        return new HealthStatus(
            overallHealthy ? Status.UP : Status.DOWN,
            "arena-health",
            allComponents,
            Instant.now()
        );
    }

    private ComponentHealth checkPool() {
        try {
            PrebuildPoolManager pool = poolManagerSupplier.get();
            if (pool == null) {
                return ComponentHealth.down("Pool manager not available");
            }

            int poolSize = pool.getPoolSize();
            boolean enabled = pool.isEnabled();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("enabled", enabled);
            details.put("poolSize", poolSize);

            return new ComponentHealth(Status.UP, details);
        } catch (Exception e) {
            LOGGER.warn("Pool health check failed", e);
            return ComponentHealth.down("Pool check failed: " + e.getMessage());
        }
    }

    private ComponentHealth checkDatabase() {
        try {
            var connection = DuckDBTelemetryService.INSTANCE.getConnection();
            if (connection == null) {
                return ComponentHealth.down("Database connection not available");
            }

            // Quick connectivity check with timeout
            CompletableFuture<Boolean> check = CompletableFuture.supplyAsync(() -> {
                try {
                    // Simple query to verify connectivity
                    DuckDBTelemetryService.INSTANCE.getQueryAPI().countArenaBuildsAfter(Instant.EPOCH);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });

            boolean connected = check.get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            if (!connected) {
                return ComponentHealth.down("Database query failed");
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("connected", true);

            return new ComponentHealth(Status.UP, details);
        } catch (Exception e) {
            LOGGER.warn("Database health check failed", e);
            return ComponentHealth.down("Database check failed: " + e.getMessage());
        }
    }

    private ComponentHealth checkCircuitBreaker() {
        try {
            CircuitBreaker breaker = circuitBreakerSupplier.get();
            if (breaker == null) {
                return ComponentHealth.down("Circuit breaker not available");
            }

            CircuitBreaker.State state = breaker.getState();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("state", state.name());

            // Circuit breaker OPEN means degraded but not necessarily unhealthy
            if (state == CircuitBreaker.State.OPEN) {
                details.put("warning", "Circuit breaker is open");
                return new ComponentHealth(Status.DEGRADED, details);
            }

            return new ComponentHealth(Status.UP, details);
        } catch (Exception e) {
            LOGGER.warn("Circuit breaker health check failed", e);
            return ComponentHealth.down("Circuit breaker check failed: " + e.getMessage());
        }
    }

    // === Health Status Types ===

    public enum Status {
        UP,
        DOWN,
        DEGRADED
    }

    public record HealthStatus(
        Status status,
        String name,
        Map<String, ComponentHealth> components,
        Instant timestamp
    ) {
        public Map<String, Object> details() {
            Map<String, Object> result = new LinkedHashMap<>();
            components.forEach((k, v) -> result.put(k, v.details()));
            return result;
        }

        public static Builder up(String name) {
            return new Builder(Status.UP, name);
        }

        public static Builder down(String name) {
            return new Builder(Status.DOWN, name);
        }

        public static class Builder {
            private final Status status;
            private final String name;
            private final Map<String, Object> details = new LinkedHashMap<>();

            Builder(Status status, String name) {
                this.status = status;
                this.name = name;
            }

            public Builder withDetail(String key, Object value) {
                details.put(key, value);
                return this;
            }

            public HealthStatus build() {
                Map<String, ComponentHealth> components = new LinkedHashMap<>();
                if (!details.isEmpty()) {
                    components.put("details", new ComponentHealth(status, details));
                }
                return new HealthStatus(status, name, components, Instant.now());
            }
        }
    }

    public record ComponentHealth(Status status, Map<String, Object> details) {
        public static ComponentHealth up() {
            return new ComponentHealth(Status.UP, Map.of());
        }

        public static ComponentHealth down(String reason) {
            return new ComponentHealth(Status.DOWN, Map.of("error", reason));
        }
    }
}

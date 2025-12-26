package com.devmod.arena.admin;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.security.ArenaCommandAudit;
import com.devmod.arena.telemetry.ArenaTelemetry;
public class HotReloadEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotReloadEndpoint.class);

    /** Minimum time between reloads (rate limiting) */
    private static final Duration RELOAD_COOLDOWN = Duration.ofSeconds(10);

    private final ArenaTemplateRegistry registry;
    private final Path templatesDirectory;
    private final ArenaTelemetry telemetry;
    private final ArenaCommandAudit audit;
    private final com.devmod.arena.registry.TemplateRegistryBootstrap bootstrap;

    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastReloadTime = new AtomicReference<>(Instant.EPOCH);
    private final Map<String, ReloadResult> reloadHistory = new ConcurrentHashMap<>();

    public HotReloadEndpoint(
            ArenaTemplateRegistry registry,
            Path templatesDirectory,
            ArenaTelemetry telemetry) {
        this(registry, templatesDirectory, telemetry, null);
    }

    public HotReloadEndpoint(
            ArenaTemplateRegistry registry,
            Path templatesDirectory,
            ArenaTelemetry telemetry,
            com.devmod.arena.registry.TemplateRegistryBootstrap bootstrap) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.templatesDirectory = Objects.requireNonNull(templatesDirectory, "templatesDirectory");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.audit = ArenaCommandAudit.getInstance();
        this.bootstrap = bootstrap;
    }

    /**
     * Triggers a hot-reload of all templates.
     *
     * @param requesterId The UUID of the requesting player (null for console/system)
     * @param force If true, bypass rate limiting
     * @return Result of the reload operation
     */
    public ReloadResult reload(UUID requesterId, boolean force) {
        String reloadId = UUID.randomUUID().toString().substring(0, 8);
        Instant startTime = Instant.now();

        LOGGER.info("[HotReload-{}] Reload requested by {}", reloadId, requesterId != null ? requesterId : "SYSTEM");

        // Rate limiting check
        if (!force) {
            Instant lastReload = lastReloadTime.get();
            Duration sinceLastReload = Duration.between(lastReload, startTime);
            if (sinceLastReload.compareTo(RELOAD_COOLDOWN) < 0) {
                Duration remaining = RELOAD_COOLDOWN.minus(sinceLastReload);
                String reason = String.format("Rate limited. Try again in %d seconds", remaining.toSeconds());
                LOGGER.warn("[HotReload-{}] {}", reloadId, reason);

                ReloadResult result = new ReloadResult(
                    reloadId,
                    ReloadStatus.RATE_LIMITED,
                    0,
                    null,
                    reason,
                    Duration.ZERO,
                    requesterId
                );
                logResult(result);
                return result;
            }
        }

        // Check if reload already in progress
        if (!reloadInProgress.compareAndSet(false, true)) {
            String reason = "Another reload is already in progress";
            LOGGER.warn("[HotReload-{}] {}", reloadId, reason);

            ReloadResult result = new ReloadResult(
                reloadId,
                ReloadStatus.BUSY,
                0,
                null,
                reason,
                Duration.ZERO,
                requesterId
            );
            logResult(result);
            return result;
        }

        try {
            // Capture pre-reload state for rollback
            int previousGeneration = registry.getGeneration();

            LOGGER.info("[HotReload-{}] Starting reload from {}", reloadId, templatesDirectory);

            // Perform atomic reload
            ArenaTemplateRegistry.ReloadResult registryResult =
                bootstrap != null
                    ? bootstrap.reloadWithConfig()
                    : reloadWithConfigFallback();

            Duration duration = Duration.between(startTime, Instant.now());
            lastReloadTime.set(Instant.now());

            ReloadResult result;
            if (registryResult.success()) {
                LOGGER.info("[HotReload-{}] Success: {} templates loaded in {}ms",
                    reloadId, registryResult.loadedCount(), duration.toMillis());

                result = new ReloadResult(
                    reloadId,
                    ReloadStatus.SUCCESS,
                    registryResult.loadedCount(),
                    registry.getGeneration(),
                    null,
                    duration,
                    requesterId
                );

                telemetry.emit("arena.hotreload.success", Map.of(
                    "reloadId", reloadId,
                    "templateCount", registryResult.loadedCount(),
                    "durationMs", duration.toMillis(),
                    "previousGeneration", previousGeneration,
                    "newGeneration", registry.getGeneration()
                ));
            } else {
                LOGGER.error("[HotReload-{}] Failed with {} errors", reloadId, registryResult.errors().size());

                String errorSummary = String.join("; ", registryResult.errors());
                if (errorSummary.length() > 500) {
                    errorSummary = errorSummary.substring(0, 497) + "...";
                }

                result = new ReloadResult(
                    reloadId,
                    ReloadStatus.FAILED,
                    0,
                    previousGeneration,
                    errorSummary,
                    duration,
                    requesterId
                );

                telemetry.emit("arena.hotreload.failed", Map.of(
                    "reloadId", reloadId,
                    "errorCount", registryResult.errors().size(),
                    "durationMs", duration.toMillis(),
                    "errors", registryResult.errors()
                ));
            }

            logResult(result);
            reloadHistory.put(reloadId, result);
            return result;

        } catch (Exception e) {
            LOGGER.error("[HotReload-{}] Exception during reload", reloadId, e);

            Duration duration = Duration.between(startTime, Instant.now());
            ReloadResult result = new ReloadResult(
                reloadId,
                ReloadStatus.ERROR,
                0,
                null,
                e.getMessage(),
                duration,
                requesterId
            );

            telemetry.emit("arena.hotreload.error", Map.of(
                "reloadId", reloadId,
                "exception", e.getClass().getSimpleName(),
                "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));

            logResult(result);
            return result;

        } finally {
            reloadInProgress.set(false);
        }
    }

    private ArenaTemplateRegistry.ReloadResult reloadWithConfigFallback() {
        try {
            ArenaTemplateConfig config = ArenaTemplateConfig.load();
            if (bootstrap != null) {
                bootstrap.applyConfig(config);
                return bootstrap.reload();
            }
            registry.applyConfigSnapshot(config.snapshot());
        } catch (Exception e) {
            LOGGER.warn("[HotReload] Failed to apply config snapshot before reload: {}", e.getMessage());
        }
        return registry.reloadFromDirectoryAtomic(templatesDirectory);
    }

    /**
     * Triggers an async hot-reload.
     */
    public CompletableFuture<ReloadResult> reloadAsync(UUID requesterId, boolean force) {
        return CompletableFuture.supplyAsync(() -> reload(requesterId, force));
    }

    /**
     * Gets the current reload status.
     */
    public ReloadStatus getStatus() {
        if (reloadInProgress.get()) {
            return ReloadStatus.IN_PROGRESS;
        }
        return ReloadStatus.IDLE;
    }

    /**
     * Gets the last reload result.
     */
    public ReloadResult getLastReloadResult() {
        if (reloadHistory.isEmpty()) {
            return null;
        }
        return reloadHistory.values().stream()
            .max((a, b) -> a.duration().compareTo(b.duration()))
            .orElse(null);
    }

    /**
     * Gets the time since last reload.
     */
    public Duration getTimeSinceLastReload() {
        Instant last = lastReloadTime.get();
        if (last.equals(Instant.EPOCH)) {
            return Duration.ZERO;
        }
        return Duration.between(last, Instant.now());
    }

    /**
     * Checks if a reload can be performed (not rate limited).
     */
    public boolean canReload() {
        if (reloadInProgress.get()) {
            return false;
        }
        Duration sinceLastReload = Duration.between(lastReloadTime.get(), Instant.now());
        return sinceLastReload.compareTo(RELOAD_COOLDOWN) >= 0;
    }

    /**
     * Gets the cooldown remaining until next reload is allowed.
     */
    public Duration getCooldownRemaining() {
        Duration sinceLastReload = Duration.between(lastReloadTime.get(), Instant.now());
        if (sinceLastReload.compareTo(RELOAD_COOLDOWN) >= 0) {
            return Duration.ZERO;
        }
        return RELOAD_COOLDOWN.minus(sinceLastReload);
    }

    private void logResult(ReloadResult result) {
        audit.logSecurityEvent(
            "HOTRELOAD_" + result.status().name(),
            result.requesterId(),
            String.format("id=%s templates=%d duration=%dms error=%s",
                result.reloadId(),
                result.templatesLoaded(),
                result.duration().toMillis(),
                result.errorMessage() != null ? result.errorMessage() : "none")
        );
    }

    // ========== Result Types ==========

    /**
     * Status of a reload operation.
     */
    public enum ReloadStatus {
        /** Reload completed successfully */
        SUCCESS,
        /** Reload failed with validation errors */
        FAILED,
        /** Reload threw an exception */
        ERROR,
        /** Rate limited - too soon since last reload */
        RATE_LIMITED,
        /** Another reload is in progress */
        BUSY,
        /** Reload is currently in progress */
        IN_PROGRESS,
        /** No reload in progress */
        IDLE
    }

    /**
     * Result of a reload operation.
     */
    public record ReloadResult(
        String reloadId,
        ReloadStatus status,
        int templatesLoaded,
        Integer newGeneration,
        String errorMessage,
        Duration duration,
        UUID requesterId
    ) {
        public boolean isSuccess() {
            return status == ReloadStatus.SUCCESS;
        }

        public String formatSummary() {
            if (isSuccess()) {
                return String.format("SUCCESS: %d templates loaded in %dms (gen %d)",
                    templatesLoaded, duration.toMillis(), newGeneration);
            } else {
                return String.format("%s: %s", status.name(),
                    errorMessage != null ? errorMessage : "Unknown error");
            }
        }
    }
}

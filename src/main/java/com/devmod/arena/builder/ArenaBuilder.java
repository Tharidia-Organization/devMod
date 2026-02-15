package com.devmod.arena.builder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.phys.AABB;

import com.devmod.arena.cleanup.BlockIntegrityVerifier;
import com.devmod.arena.cleanup.PostBuildEntityAudit;
import com.devmod.arena.concurrency.ArenaBuildRateLimiter;
import com.devmod.arena.concurrency.BuildPermit;
import com.devmod.arena.concurrency.TemplateLockManager;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.integration.BatchBlockPlacer;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.metrics.MetricsCompatibilityLayer;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.monitoring.BuildOutcomeMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.GoldenReference;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.util.HotPathLogger;

public class ArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaBuilder.class);

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final TemplateLockManager TEMPLATE_LOCK_MANAGER = new TemplateLockManager();
    private static final ArenaBuildRateLimiter BUILD_RATE_LIMITER = new ArenaBuildRateLimiter();
    private static final AtomicBoolean LOCK_MANAGER_STARTED = new AtomicBoolean(false);

    private final ArenaTelemetry telemetry;
    private final BlockPlacer blockPlacer;
    private final EntitySpawner entitySpawner;
    private final ChunkLoadingManager chunkManager;
    @Nullable
    private final BuildHistoryStore historyStore;
    private final TemplateValidator templateValidator;
    private final InstanceSettingsValidator.InstanceLimits instanceLimits;
    @Nullable
    private final InstanceOnlyGate instanceGate;
    @Nullable
    private final ArenaTemplateConfig.ConfigSnapshot configSnapshot;
    @Nullable
    private MsptMonitor msptMonitor;
    @Nullable
    private PerformanceBudgetEnforcer performanceEnforcer;
    @Nullable
    private Supplier<Double> msptSupplier;
    private final AtomicLong nextPerformanceCheckAt = new AtomicLong(0);

    // Delegates
    private final ArenaStructurePlacer structurePlacer;
    private final ArenaHazardPlacer hazardPlacer;
    private final ArenaLightingPlacer lightingPlacer;
    private final ArenaBuildEstimator estimator;

    private static void ensureLockManagerStarted() {
        if (LOCK_MANAGER_STARTED.compareAndSet(false, true)) {
            TEMPLATE_LOCK_MANAGER.start();
        }
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore) {
        this(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore,
             InstanceLimitConfig.load().toLimits(), null, null);
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits) {
        this(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore,
             instanceLimits, null, null);
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits,
            @Nullable CustomHazardHandler customHazardHandler) {
        this(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore,
             instanceLimits, customHazardHandler, null);
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits,
            @Nullable CustomHazardHandler customHazardHandler,
            @Nullable ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.entitySpawner = entitySpawner;
        this.chunkManager = chunkManager;
        this.historyStore = historyStore;
        this.instanceLimits = instanceLimits;
        this.templateValidator = new TemplateValidator().withInstanceLimits(instanceLimits);
        this.configSnapshot = configSnapshot;
        this.instanceGate = configSnapshot != null ? new InstanceOnlyGate(configSnapshot, telemetry) : null;

        // Initialize delegates
        this.structurePlacer = new ArenaStructurePlacer(blockPlacer, telemetry);
        this.hazardPlacer = new ArenaHazardPlacer(structurePlacer, telemetry, customHazardHandler);
        this.lightingPlacer = new ArenaLightingPlacer(structurePlacer, telemetry);
        this.estimator = new ArenaBuildEstimator(historyStore, configSnapshot);

        ensureLockManagerStarted();
    }

    // === Public API ===

    public BuildResult build(ArenaTemplate template, @Nullable String policyId, int policyVersion,
                            int originX, int originY, int originZ) {
        return doBuild(template, policyId, policyVersion, originX, originY, originZ);
    }

    public BuildResult build(ArenaTemplate template, int originX, int originY, int originZ) {
        return doBuild(template, null, 0, originX, originY, originZ);
    }

    public long estimateBuildTimeMs(ArenaTemplate template) {
        return estimator.estimateBuildTimeMs(template);
    }

    public AccuracyResult calculateAccuracy(long estimated, long actual) {
        return estimator.calculateAccuracy(estimated, actual);
    }

    public BuildDryRun dryRun(ArenaTemplate template) {
        return BuildDryRunCalculator.calculate(template);
    }

    public BuildValidation validateBuild(ArenaTemplate template) {
        return estimator.validateBuild(template);
    }

    // === Build orchestration ===

    private BuildResult doBuild(ArenaTemplate template, @Nullable String policyId, int policyVersion,
                               int originX, int originY, int originZ) {
        UUID arenaId = UUID.randomUUID();
        long startTime = System.currentTimeMillis();
        String dimension = resolveDimension();

        // Instance-only gate check (fail fast before validation)
        var gate = instanceGate;
        if (gate != null) {
            net.minecraft.server.level.ServerLevel gateLevel = null;
            if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
                gateLevel = batchPlacer.getLevel();
            } else if (blockPlacer instanceof MinecraftBlockPlacer mcb) {
                gateLevel = mcb.getLevel();
            }
            if (gateLevel != null) {
                try {
                    gate.requireAllowedOrThrow(gateLevel, "ArenaBuilder.build", template.id(), template.version());
                } catch (InstanceOnlyGate.GateBlockedException e) {
                    LOGGER.error(e.getMessage());
                    return BuildResult.failure(e.getMessage(), new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
                }
            }
        }

        // Pre-build validation
        ValidationResult validation = templateValidator.validate(template);
        if (!validation.valid()) {
            String msg = "Template validation failed: " + validation.errorsAsString();
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.build.validation_failed", Map.of(
                "templateId", template.id(), "templateVersion", template.version(),
                "arenaId", arenaId.toString(), "errors", validation.errors()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }
        if (validation.hasWarnings()) {
            LOGGER.warn("Template '{}' validation warnings: {}", template.id(), validation.warningsAsString());
            telemetry.emit("arena.build.validation_warning", Map.of(
                "templateId", template.id(), "templateVersion", template.version(),
                "warnings", validation.warnings()
            ));
        }

        // Instance settings validation
        InstanceSettingsValidator isValidator = new InstanceSettingsValidator();
        InstanceSettingsValidator.Result isResult = isValidator.validate(template, instanceLimits);
        if (!isResult.errors().isEmpty()) {
            String msg = "Instance settings invalid: " + String.join("; ", isResult.errors());
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.instance.coverage_insufficient", Map.of(
                "templateId", template.id(), "errors", isResult.errors()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }
        if (!isResult.warnings().isEmpty()) {
            telemetry.emit("arena.instance.clamped", Map.of(
                "templateId", template.id(), "warnings", isResult.warnings(),
                "effectiveChunkRadius", isResult.effectiveChunkRadius(),
                "effectiveTickDistance", isResult.effectiveTickDistance()
            ));
        }

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

        ArenaTemplate.BuildSettings.Order buildOrder = estimator.resolveBuildOrder(template);
        ArenaTemplate.BuildSettings.Priority buildPriority = estimator.resolveBuildPriority(template);
        if (buildPriority == ArenaTemplate.BuildSettings.Priority.ASYNC) {
            String msg = "Template '%s' requires ASYNC build; sync builder is not allowed"
                .formatted(template.id());
            LOGGER.warn("{}", msg);
            telemetry.emit("arena.build.priority_blocked", Map.of(
                "templateId", template.id(), "templateVersion", template.version(),
                "requestedPriority", buildPriority.name()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }

        int maxBlocks = estimator.determineMaxBlocks(template);

        // DD8: Soft budget warning at 80%
        int warningThreshold = (int) (maxBlocks * 0.80);
        if (dryRun.totalBlocks() > warningThreshold && dryRun.totalBlocks() <= maxBlocks) {
            LOGGER.warn("Template '{}' at {}% of block budget ({}/{} blocks)",
                template.id(), (dryRun.totalBlocks() * 100) / maxBlocks,
                dryRun.totalBlocks(), maxBlocks);
            telemetry.emit("arena.build.budget_warning", Map.of(
                "templateId", template.id(), "templateVersion", template.version(),
                "estimatedBlocks", dryRun.totalBlocks(), "maxBlocks", maxBlocks,
                "percentUsed", (dryRun.totalBlocks() * 100) / maxBlocks
            ));
        }

        if (dryRun.totalBlocks() > maxBlocks) {
            String msg = "Estimated blocks %d exceed limit %d".formatted(dryRun.totalBlocks(), maxBlocks);
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.build.block_budget_exceeded", Map.of(
                "templateId", template.id(), "estimatedBlocks", dryRun.totalBlocks(),
                "maxBlocks", maxBlocks, "floorBlocks", dryRun.floorBlocks(),
                "wallBlocks", dryRun.wallBlocks(), "ceilingBlocks", dryRun.ceilingBlocks(),
                "underfloorBlocks", dryRun.underfloorBlocks()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }

        long maxDurationMs = estimator.determineMaxBuildTimeMs(template);
        String lockOwner = "arena:" + arenaId;
        boolean lockAcquired = false;
        long lockAcquiredAtMs = 0L;
        BuildPermit.Granted buildPermit = null;
        BuildTransaction transaction = null;

        try {
            long lockWaitStartMs = System.currentTimeMillis();
            lockAcquired = TEMPLATE_LOCK_MANAGER.tryAcquire(template.id(), lockOwner, LOCK_TIMEOUT);
            long lockWaitMs = System.currentTimeMillis() - lockWaitStartMs;
            if (!lockAcquired) {
                telemetry.emit("arena.build.lock_timeout", Map.of(
                    "lockedTemplateId", template.id(), "owner", lockOwner, "waitTimeMs", lockWaitMs
                ));
                String msg = "Template lock timeout after %dms".formatted(lockWaitMs);
                return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }

            lockAcquiredAtMs = System.currentTimeMillis();
            telemetry.emit("arena.build.lock_acquired", Map.of(
                "lockedTemplateId", template.id(), "owner", lockOwner, "waitTimeMs", lockWaitMs
            ));

            UUID rateLimitPlayerId = UUID.nameUUIDFromBytes(lockOwner.getBytes(StandardCharsets.UTF_8));
            BuildPermit permit = BUILD_RATE_LIMITER.requestPermit(rateLimitPlayerId, lockOwner);
            if (!permit.isGranted()) {
                BuildPermit.Rejected rejected = (BuildPermit.Rejected) permit;
                telemetry.emit("arena.build.permit_rejected", Map.of(
                    "templateId", template.id(), "reason", rejected.reason().name(),
                    "retryAfterMs", rejected.retryAfter().toMillis(), "queuePosition", rejected.queuePosition()
                ));
                String msg = "Build rate limited: " + rejected.reason().name();
                return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }

            buildPermit = (BuildPermit.Granted) permit;
            telemetry.emit("arena.build.permit_granted", Map.of(
                "permitId", buildPermit.permitId(), "templateId", template.id(),
                "waitTimeMs", buildPermit.waitTimeMs()
            ));

            transaction = new BuildTransaction(template.id(), maxBlocks, maxDurationMs);

            HotPathLogger.rateLimitedInfo(LOGGER, "arena.build.start", 5000,
                () -> String.format("Starting build for template '%s' at (%d,%d,%d) with max %d blocks",
                    template.id(), originX, originY, originZ, maxBlocks));

            Map<String, Object> startEventData = new java.util.HashMap<>();
            startEventData.put("templateId", template.id());
            startEventData.put("templateVersion", template.version());
            startEventData.put("arenaId", arenaId.toString());
            startEventData.put("origin", "%d,%d,%d".formatted(originX, originY, originZ));
            startEventData.put("originX", originX);
            startEventData.put("originY", originY);
            startEventData.put("originZ", originZ);
            if (dimension != null) {
                startEventData.put("dimension", dimension);
            }
            startEventData.put("estimatedMs", estimator.estimateBuildTimeMs(template));
            startEventData.put("maxBlocks", maxBlocks);
            if (policyId != null) {
                startEventData.put("policyId", policyId);
                startEventData.put("policyVersion", policyVersion);
            }
            telemetry.emit("arena.build.start", startEventData);

            initPerformanceMonitoring();

            // 1. Ensure chunks are loaded
            ChunkLoadingManager.ChunkLoadResult chunkResult = loadRequiredChunks(template, originX, originZ);
            if (!chunkResult.success()) {
                throw new BuildException("Chunk loading failed: " + chunkResult.errorMessage());
            }
            for (long chunkPos : chunkResult.loadedChunks()) {
                transaction.trackChunk(chunkPos);
            }

            // 2. Build order handling — delegate to structure placer
            if (buildOrder == ArenaTemplate.BuildSettings.Order.STRUCTURE_FIRST && template.structureNbt() != null) {
                structurePlacer.placeStructure(template, originX, originY, originZ, transaction);
            }

            if (buildOrder == ArenaTemplate.BuildSettings.Order.WALLS_FIRST) {
                if (template.walls() != null && template.walls().enabled()) {
                    structurePlacer.buildWalls(template, originX, originZ, transaction);
                }
                if (template.floor() != null) {
                    structurePlacer.buildFloor(template, originX, originZ, transaction);
                }
            } else {
                if (template.floor() != null) {
                    structurePlacer.buildFloor(template, originX, originZ, transaction);
                }
                if (template.walls() != null && template.walls().enabled()) {
                    structurePlacer.buildWalls(template, originX, originZ, transaction);
                }
            }

            var terrainSettings = template.terrainSettings();
            boolean isDynamicTerrain = terrainSettings != null &&
                terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC;

            if (template.ceiling() != null && template.ceiling().enabled()) {
                if (isDynamicTerrain) {
                    LOGGER.debug("Skipping ceiling for '{}' - dynamic terrain mode", template.id());
                } else {
                    structurePlacer.buildCeiling(template, originX, originZ, transaction);
                }
            }

            if (template.underfloor() != null) {
                if (isDynamicTerrain) {
                    LOGGER.debug("Skipping underfloor for '{}' - dynamic terrain mode", template.id());
                } else {
                    structurePlacer.buildUnderfloor(template, originX, originZ, transaction);
                }
            }

            if (template.hazards() != null && !template.hazards().isEmpty()) {
                hazardPlacer.placeHazards(template, originX, originZ, transaction);
            }

            if (template.lighting() != null) {
                lightingPlacer.placeLighting(template, originX, originZ, transaction);
            }

            if (buildOrder != ArenaTemplate.BuildSettings.Order.STRUCTURE_FIRST && template.structureNbt() != null) {
                structurePlacer.placeStructure(template, originX, originY, originZ, transaction);
            }

            // Flush BatchBlockPlacer before commit
            if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
                batchPlacer.flush();
            }

            // Commit transaction
            transaction.commit();

            long duration = System.currentTimeMillis() - startTime;
            maybeCheckGoldenReference(template, transaction);
            maybeWarnOnBuildTime(template, duration);

            if (historyStore != null) {
                historyStore.recordBuild(template.id(), duration, transaction.getBlockCount(), true);
            }

            Map<String, Object> endEventData = new java.util.HashMap<>();
            endEventData.put("templateId", template.id());
            endEventData.put("templateVersion", template.version());
            endEventData.put("arenaId", arenaId.toString());
            endEventData.put("success", true);
            endEventData.put("actualMs", duration);
            endEventData.put("build_ms", duration);
            endEventData.put("actualBlocks", transaction.getBlockCount());
            endEventData.put("totalPlacements", transaction.getTotalBlockPlacements());
            endEventData.put("originX", originX);
            endEventData.put("originY", originY);
            endEventData.put("originZ", originZ);
            if (dimension != null) {
                endEventData.put("dimension", dimension);
            }
            int expectedBlocks = BuildDryRunCalculator.calculate(template).totalBlocks();
            ResidualMetrics residualMetrics = null;
            String residualSource = "unknown";
            if (blockPlacer instanceof ResidualProvider residualProvider) {
                residualMetrics = residualProvider.measureResiduals(template, originX, originY, originZ, expectedBlocks);
                residualSource = "provider";
            } else if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
                residualMetrics = measureResiduals(template, originX, originY, originZ, batchPlacer.getLevel());
                residualSource = "batch";
            } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
                residualMetrics = measureResiduals(template, originX, originY, originZ, mcPlacer);
                residualSource = "minecraft";
            } else {
                residualMetrics = measureResidualsFromTransaction(expectedBlocks, transaction);
                residualSource = "transaction";
            }
            if (residualMetrics != null) {
                endEventData.put("entities_residual", residualMetrics.entitiesResidual());
                endEventData.put("blocks_residual", residualMetrics.blocksResidual());
                endEventData.put("expected_blocks", expectedBlocks);
                endEventData.put("residuals_source", residualSource);
                endEventData.put("residuals_unknown", false);
            } else {
                endEventData.put("entities_residual", -1);
                endEventData.put("blocks_residual", -1);
                endEventData.put("expected_blocks", expectedBlocks);
                endEventData.put("residuals_source", "unknown");
                endEventData.put("residuals_unknown", true);
            }
            if (policyId != null) {
                endEventData.put("policyId", policyId);
                endEventData.put("policyVersion", policyVersion);
            }
        telemetry.emit("arena.build.end", endEventData);

        final int blockCount = transaction.getBlockCount();
        HotPathLogger.rateLimitedInfo(LOGGER, "arena.build.complete", 5000,
            () -> String.format("Build completed for '%s' in %dms: %d blocks",
                template.id(), duration, blockCount));

        // Post-build audits
        performPostBuildAudits(template, arenaId, transaction, originX, originY, originZ);

        recordBuildOutcome(true, false, template.id());

        // Clear placer cache
        if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
            if (LOGGER.isDebugEnabled()) {
                BatchBlockPlacer.PlacementStats stats = batchPlacer.getStats();
                int trackedStates = batchPlacer.getTrackedStateCount();
                LOGGER.debug("[ArenaBuilder] BatchBlockPlacer stats: placed={}, flushes={}, cacheHitRate={:.2f}%, trackedStates={}",
                    stats.totalPlaced(), stats.batchFlushes(), stats.cacheHitRate() * 100, trackedStates);
            }
            batchPlacer.clearCache();
        } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[ArenaBuilder] MinecraftBlockPlacer trackedStates={}", mcPlacer.getTrackedStateCount());
            }
            mcPlacer.clearCache();
        }

        return BuildResult.success(arenaId, template.id(), transaction.getBlockCount(), duration);

        } catch (BuildLimitExceededException e) {
            if (transaction == null) {
                return BuildResult.failure(e.getMessage(), new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }
            return handleBuildFailure(template, policyId, policyVersion, arenaId, transaction, e, startTime,
                originX, originY, originZ, dimension);

        } catch (BuildException e) {
            if (transaction == null) {
                return BuildResult.failure(e.getMessage(), new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }
            return handleBuildFailure(template, policyId, policyVersion, arenaId, transaction, e, startTime,
                originX, originY, originZ, dimension);

        } catch (Exception e) {
            if (transaction == null) {
                return BuildResult.failure(e.getMessage(), new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }
            return handleBuildFailure(template, policyId, policyVersion, arenaId, transaction,
                new BuildException("Unexpected error: " + e.getMessage(), e), startTime,
                originX, originY, originZ, dimension);
        } finally {
            finalizePerformanceMonitoring(template, arenaId);
            if (buildPermit != null) {
                BUILD_RATE_LIMITER.releasePermit(buildPermit.permitId());
            }
            if (lockAcquired) {
                boolean released = TEMPLATE_LOCK_MANAGER.release(template.id(), lockOwner);
                if (released) {
                    long heldForMs = System.currentTimeMillis() - lockAcquiredAtMs;
                    telemetry.emit("arena.build.lock_released", Map.of(
                        "lockedTemplateId", template.id(), "owner", lockOwner, "heldForMs", heldForMs
                    ));
                }
            }
        }
    }

    private BuildResult handleBuildFailure(
            ArenaTemplate template, @Nullable String policyId, int policyVersion,
            UUID arenaId, BuildTransaction transaction, Exception error, long startTime,
            int originX, int originY, int originZ, @Nullable String dimension) {

        LOGGER.error("Build failed for '{}': {}", template.id(), error.getMessage());
        transaction.markFailed();

        BuildTransaction.RollbackResult rollbackResult = transaction.rollback(
            blockPlacer::revertBlock, entitySpawner::removeEntity, packedChunkPos -> {}
        );

        chunkManager.releaseAllTickets();

        long duration = System.currentTimeMillis() - startTime;

        if (historyStore != null) {
            historyStore.recordBuild(template.id(), duration, transaction.getBlockCount(), false);
        }

        Map<String, Object> failEventData = new java.util.HashMap<>();
        failEventData.put("templateId", template.id());
        failEventData.put("templateVersion", template.version());
        failEventData.put("arenaId", arenaId.toString());
        failEventData.put("reason", error.getClass().getSimpleName());
        failEventData.put("message", error.getMessage());
        failEventData.put("blocksPlaced", transaction.getBlockCount());
        failEventData.put("rollbackMs", rollbackResult.durationMs());
        failEventData.put("blocksReverted", rollbackResult.blocksReverted());
        failEventData.put("actualMs", duration);
        failEventData.put("originX", originX);
        failEventData.put("originY", originY);
        failEventData.put("originZ", originZ);
        if (dimension != null) {
            failEventData.put("dimension", dimension);
        }
        if (policyId != null) {
            failEventData.put("policyId", policyId);
            failEventData.put("policyVersion", policyVersion);
        }
        telemetry.emit("arena.build.fail", failEventData);

        recordBuildOutcome(false, rollbackResult.blocksReverted() > 0, template.id());

        return BuildResult.failure(error.getMessage(), rollbackResult);
    }

    private void recordBuildOutcome(boolean success, boolean rolledBack, String templateId) {
        ArenaTemplateConfig.AlertThresholds thresholds = configSnapshot != null
            ? configSnapshot.alertThresholds()
            : ArenaTemplateConfig.AlertThresholds.defaults();
        BuildOutcomeMonitor.recordBuild(thresholds, success, rolledBack, templateId);
    }

    // === Post-build audits ===

    private void performPostBuildAudits(ArenaTemplate template, UUID arenaId,
                                        BuildTransaction transaction,
                                        int originX, int originY, int originZ) {
        net.minecraft.server.level.ServerLevel auditLevel = null;
        if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
            auditLevel = batchPlacer.getLevel();
        } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            auditLevel = mcPlacer.getLevel();
        }
        if (auditLevel == null) return;

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int wallThickness = 0;
        if (template.walls() != null && template.walls().enabled()) {
            wallThickness = Math.max(1, template.walls().thickness());
        }

        int minX, maxX, minZ, maxZ;
        if (shape == ArenaTemplate.ArenaShape.CIRCULAR || shape == ArenaTemplate.ArenaShape.RING) {
            int radius = Math.max(halfX, halfZ);
            int extent = radius + wallThickness;
            minX = originX - extent;
            maxX = originX + extent;
            minZ = originZ - extent;
            maxZ = originZ + extent;
        } else {
            int wallPad = Math.max(0, wallThickness - 1);
            minX = originX + ArenaShapeHelper.minOffsetX(template) - wallPad;
            maxX = originX + ArenaShapeHelper.maxOffsetX(template) + wallPad;
            minZ = originZ + ArenaShapeHelper.minOffsetZ(template) - wallPad;
            maxZ = originZ + ArenaShapeHelper.maxOffsetZ(template) + wallPad;
        }

        int minY = originY;
        if (template.floor() != null) {
            minY = template.floor().y();
            if (template.underfloor() != null) {
                minY -= template.underfloor().depth();
            }
        }
        int maxY = minY;
        if (template.floor() != null) {
            maxY = Math.max(maxY, template.floor().y() + template.floor().thickness() - 1);
        }
        if (template.ceiling() != null && template.ceiling().enabled()) {
            maxY = Math.max(maxY, template.ceiling().y() + template.ceiling().thickness() - 1);
        } else if (template.walls() != null && template.walls().enabled()) {
            maxY = Math.max(maxY, template.walls().startY() + template.walls().height() - 1);
        } else {
            maxY = minY + 20;
        }

        PostBuildEntityAudit.AuditResult auditResult = new PostBuildEntityAudit()
            .audit(auditLevel, minX, minY, minZ, maxX, maxY, maxZ);
        if (auditResult.hasResiduals()) {
            telemetry.emit("arena.build.residual_entities", Map.of(
                "templateId", template.id(), "arenaId", arenaId.toString(),
                "residualCount", auditResult.residualEntities(), "itemsFound", auditResult.itemsFound(),
                "mobsFound", auditResult.mobsFound(), "auditDurationMs", auditResult.auditDurationMs()
            ));
        }

        BlockIntegrityVerifier.VerificationResult integrityResult = new BlockIntegrityVerifier()
            .verify(auditLevel, minX, minY, minZ, maxX, maxY, maxZ,
                    transaction.getBlockCount(), template, originX, originZ);
        if (integrityResult.hasIntegrityIssues()) {
            telemetry.emit("arena.build.integrity_issue", Map.of(
                "templateId", template.id(), "arenaId", arenaId.toString(),
                "expectedBlocks", integrityResult.expectedBlocks(),
                "actualBlocks", integrityResult.actualNonAirBlocks(),
                "integrityPercent", integrityResult.integrityPercent(),
                "sampleFailures", integrityResult.sampleFailures(),
                "verificationDurationMs", integrityResult.verificationDurationMs()
            ));
        }
    }

    // === Infrastructure helpers ===

    @Nullable
    private String resolveDimension() {
        net.minecraft.server.level.ServerLevel level = null;
        if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
            level = batchPlacer.getLevel();
        } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            level = mcPlacer.getLevel();
        }
        if (level != null) {
            return level.dimension().location().toString();
        }
        return null;
    }

    private ChunkLoadingManager.ChunkLoadResult loadRequiredChunks(ArenaTemplate template, int originX, int originZ) {
        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);

        int minChunkX = (originX - sizeX / 2) >> 4;
        int maxChunkX = (originX + sizeX / 2) >> 4;
        int minChunkZ = (originZ - sizeZ / 2) >> 4;
        int maxChunkZ = (originZ + sizeZ / 2) >> 4;

        return chunkManager.ensureChunksLoadedWithRetry(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    // === Performance monitoring ===

    private void initPerformanceMonitoring() {
        net.minecraft.server.level.ServerLevel level = null;
        if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
            level = batchPlacer.getLevel();
        } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            level = mcPlacer.getLevel();
        }
        if (level == null || level.getServer() == null) {
            return;
        }
        final net.minecraft.server.level.ServerLevel finalLevel = level;
        Supplier<Double> supplier = () -> finalLevel.getServer().getAverageTickTimeNanos() / 1_000_000.0;
        MsptMonitor monitor = new MsptMonitor();
        msptSupplier = supplier;
        msptMonitor = monitor;
        double seedMspt = supplier.get();
        for (int i = 0; i < 5; i++) {
            monitor.recordSample(seedMspt);
        }
        PerformanceBudgetEnforcer enforcer = new PerformanceBudgetEnforcer(monitor);
        performanceEnforcer = enforcer;
        enforcer.captureBaseline();
        enforcer.beginBuild();
        nextPerformanceCheckAt.set(0);
    }

    private void finalizePerformanceMonitoring(ArenaTemplate template, UUID arenaId) {
        @Nullable PerformanceBudgetEnforcer enforcer = performanceEnforcer;
        if (enforcer == null) {
            return;
        }
        PerformanceBudgetEnforcer.BuildPerformanceReport report = enforcer.endBuild();
        telemetry.emit("arena.build.performance_summary", Map.ofEntries(
            Map.entry("templateId", template.id()),
            Map.entry("templateVersion", template.version()),
            Map.entry("arenaId", arenaId.toString()),
            Map.entry("baselineMspt", report.baseline()),
            Map.entry("averageMspt", report.averageMspt()),
            Map.entry("peakMspt", report.peakMspt()),
            Map.entry("maxBuildImpactMs", report.maxBuildImpact()),
            Map.entry("pauseCount", report.pauseCount()),
            Map.entry("throttleCount", report.throttleCount()),
            Map.entry("aborted", report.wasAborted()),
            Map.entry("durationMs", report.buildDuration().toMillis())
        ));
        resetPerformanceMonitoring();
    }

    private void resetPerformanceMonitoring() {
        msptMonitor = null;
        performanceEnforcer = null;
        msptSupplier = null;
        nextPerformanceCheckAt.set(0);
    }

    // === Golden reference & build time warnings ===

    private void maybeCheckGoldenReference(ArenaTemplate template, BuildTransaction tx) {
        GoldenReference golden;
        if ("default_flat_64".equals(template.id())) {
            golden = GoldenReference.defaultFlat64();
        } else if ("boss_ring_80".equals(template.id())) {
            golden = GoldenReference.bossRing80();
        } else {
            return;
        }
        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        List<String> diffs = new ArrayList<>();
        if (dryRun.floorBlocks() != golden.floorBlocks()) {
            diffs.add("floor " + dryRun.floorBlocks() + "!=" + golden.floorBlocks());
        }
        if (dryRun.wallBlocks() != golden.wallBlocks()) {
            diffs.add("walls " + dryRun.wallBlocks() + "!=" + golden.wallBlocks());
        }
        if (dryRun.ceilingBlocks() != golden.ceilingBlocks()) {
            diffs.add("ceiling " + dryRun.ceilingBlocks() + "!=" + golden.ceilingBlocks());
        }
        if (dryRun.underfloorBlocks() != golden.underfloorBlocks()) {
            diffs.add("underfloor " + dryRun.underfloorBlocks() + "!=" + golden.underfloorBlocks());
        }
        if (tx.getBlockCount() != golden.totalBlocks()) {
            diffs.add("total " + tx.getBlockCount() + "!=" + golden.totalBlocks());
        }

        if (!diffs.isEmpty()) {
            LOGGER.error("Golden reference mismatch for {}: {}", template.id(), String.join("; ", diffs));
            telemetry.emit("arena.golden_reference.mismatch", Map.ofEntries(
                Map.entry("templateId", template.id()),
                Map.entry("expectedFloor", golden.floorBlocks()),
                Map.entry("actualFloor", dryRun.floorBlocks()),
                Map.entry("expectedWalls", golden.wallBlocks()),
                Map.entry("actualWalls", dryRun.wallBlocks()),
                Map.entry("expectedCeiling", golden.ceilingBlocks()),
                Map.entry("actualCeiling", dryRun.ceilingBlocks()),
                Map.entry("expectedUnderfloor", golden.underfloorBlocks()),
                Map.entry("actualUnderfloor", dryRun.underfloorBlocks()),
                Map.entry("expectedTotal", golden.totalBlocks()),
                Map.entry("actualTotal", tx.getBlockCount())
            ));
        } else {
            telemetry.emit("arena.golden_reference.passed", Map.of(
                "templateId", template.id(), "blockCount", tx.getBlockCount()
            ));
        }
    }

    private void maybeWarnOnBuildTime(ArenaTemplate template, long durationMs) {
        long maxBuildTimeMs = estimator.determineMaxBuildTimeMs(template);
        if (durationMs > maxBuildTimeMs) {
            LOGGER.warn("Build time {}ms exceeded limit {}ms for template {}",
                durationMs, maxBuildTimeMs, template.id());
            telemetry.emit("arena.build.time_exceeded", Map.of(
                "templateId", template.id(), "limitMs", maxBuildTimeMs, "actualMs", durationMs
            ));
        }
    }

    // === Residual metrics ===

    private ResidualMetrics measureResiduals(ArenaTemplate template, int originX, int originY, int originZ, MinecraftBlockPlacer mcPlacer) {
        return measureResiduals(template, originX, originY, originZ, mcPlacer.level());
    }

    private ResidualMetrics measureResiduals(ArenaTemplate template, int originX, int originY, int originZ, net.minecraft.server.level.ServerLevel level) {
        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        int expectedBlocks = dryRun.totalBlocks();

        int minX = originX + ArenaShapeHelper.minOffsetX(template);
        int maxX = originX + ArenaShapeHelper.maxOffsetX(template);
        int minZ = originZ + ArenaShapeHelper.minOffsetZ(template);
        int maxZ = originZ + ArenaShapeHelper.maxOffsetZ(template);

        int minY = originY;
        if (template.floor() != null) {
            minY = template.floor().y();
        }
        if (template.underfloor() != null) {
            minY = minY - template.underfloor().depth();
        }
        int maxY = minY;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            maxY = Math.max(maxY, template.ceiling().y());
        } else if (template.walls() != null && template.walls().enabled()) {
            maxY = Math.max(maxY, template.walls().startY() + template.walls().height());
        }

        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        var residuals = MetricsCompatibilityLayer.measureResiduals(level, bounds, expectedBlocks);
        return new ResidualMetrics(residuals.entitiesResidual(), residuals.blocksResidual());
    }

    private ResidualMetrics measureResidualsFromTransaction(int expectedBlocks, BuildTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        int placedBlocks = transaction.getBlockCount();
        int blocksResidual = placedBlocks - expectedBlocks;
        return new ResidualMetrics(0, blocksResidual);
    }

    // === Interfaces ===

    @FunctionalInterface
    public interface BlockPlacer {
        int placeBlock(int x, int y, int z, String material);

        default boolean revertBlock(long packedPos, int stateId) {
            return true;
        }
    }

    public interface ResidualProvider {
        @Nullable ResidualMetrics measureResiduals(ArenaTemplate template,
                                                   int originX, int originY, int originZ,
                                                   int expectedBlocks);
    }

    @FunctionalInterface
    public interface EntitySpawner {
        @Nullable UUID spawnEntity(int x, int y, int z, String entityType);

        default boolean removeEntity(UUID entityId) {
            return true;
        }
    }

    public interface BuildHistoryStore {
        void recordBuild(String templateId, long durationMs, int blockCount, boolean success);
        @Nullable Long getP75BuildTime(String templateId, int minSamples);
    }

    @FunctionalInterface
    public interface CustomHazardHandler {
        void placeCustom(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx);
    }

    // === Result Records ===

    public record BuildResult(
        boolean success,
        @Nullable UUID arenaId,
        @Nullable String templateId,
        int blockCount,
        long durationMs,
        @Nullable String errorMessage,
        @Nullable BuildTransaction.RollbackResult rollbackResult
    ) {
        public static BuildResult success(UUID arenaId, String templateId, int blockCount, long durationMs) {
            return new BuildResult(true, arenaId, templateId, blockCount, durationMs, null, null);
        }

        public static BuildResult failure(String error, BuildTransaction.RollbackResult rollback) {
            return new BuildResult(false, null, null, 0, 0, error, rollback);
        }
    }

    public static class BuildException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public BuildException(String message) {
            super(message);
        }

        public BuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record ResidualMetrics(int entitiesResidual, int blocksResidual) {}

    public record BuildValidation(
        boolean valid,
        int blocksRequired,
        int chunksRequired,
        long estimatedMs,
        List<String> warnings,
        List<String> errors
    ) {}

    public enum AccuracyBand {
        EXCELLENT, GOOD, ACCEPTABLE, POOR
    }

    public record AccuracyResult(AccuracyBand band, double deviation) {
        public boolean needsCalibration() {
            return band == AccuracyBand.POOR;
        }
    }

    // === Static Cleanup Methods ===

    public static void cleanupPlayerRateLimiter(UUID playerId) {
        BUILD_RATE_LIMITER.cleanupPlayer(playerId);
    }
}

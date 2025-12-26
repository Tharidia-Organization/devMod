package com.devmod.arena.builder;

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

import com.devmod.arena.concurrency.ArenaBuildRateLimiter;
import com.devmod.arena.concurrency.BuildPermit;
import com.devmod.arena.concurrency.TemplateLockManager;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.metrics.MetricsCompatibilityLayer;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.monitoring.BuildOutcomeMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;
import com.devmod.arena.performance.PerformanceBudgetEnforcer.PerformanceAction;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.GoldenReference;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;
import com.devmod.arena.telemetry.ArenaTelemetry;

public class ArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaBuilder.class);

    // DD8: Block limits by category
    private static final int DEFAULT_MAX_BLOCKS = 50_000;
    private static final int BOSS_MAX_BLOCKS = 100_000;
    private static final int HARD_CAP_BLOCKS = 150_000;
    private static final int DEFAULT_MAX_BUILD_TIME_MS = 5_000;
    private static final int BOSS_MAX_BUILD_TIME_MS = 15_000;

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final TemplateLockManager TEMPLATE_LOCK_MANAGER = new TemplateLockManager();
    private static final ArenaBuildRateLimiter BUILD_RATE_LIMITER = new ArenaBuildRateLimiter();
    private static final AtomicBoolean LOCK_MANAGER_STARTED = new AtomicBoolean(false);

    // DD10: Estimation constants
    private static final double MS_PER_BLOCK_BASELINE = 0.05;
    private static final double NBT_MULTIPLIER = 1.5;
    private static final double HAZARD_MULTIPLIER = 1.2;
    private static final int MIN_HISTORY_SAMPLES = 5;

    // DD10: Accuracy bands for estimation feedback
    private static final double ACCURACY_EXCELLENT = 0.20;  // ±20%
    private static final double ACCURACY_GOOD = 0.35;       // ±35%
    private static final double ACCURACY_ACCEPTABLE = 0.50; // ±50%
    private static final int PERFORMANCE_CHECK_INTERVAL_BLOCKS = 256;

    private final ArenaTelemetry telemetry;
    private final BlockPlacer blockPlacer;
    private final EntitySpawner entitySpawner;
    private final ChunkLoadingManager chunkManager;
    private final BuildHistoryStore historyStore;
    @Nullable
    private final CustomHazardHandler customHazardHandler;
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
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.entitySpawner = entitySpawner;
        this.chunkManager = chunkManager;
        this.historyStore = historyStore;
        this.customHazardHandler = null;
        this.instanceLimits = InstanceLimitConfig.load().toLimits();
        this.templateValidator = new TemplateValidator().withInstanceLimits(instanceLimits);
        this.instanceGate = null;
        this.configSnapshot = null;
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.entitySpawner = entitySpawner;
        this.chunkManager = chunkManager;
        this.historyStore = historyStore;
        this.customHazardHandler = null;
        this.instanceLimits = instanceLimits;
        this.templateValidator = new TemplateValidator().withInstanceLimits(instanceLimits);
        this.instanceGate = null;
        this.configSnapshot = null;
    }

    public ArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits,
            @Nullable CustomHazardHandler customHazardHandler) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.entitySpawner = entitySpawner;
        this.chunkManager = chunkManager;
        this.historyStore = historyStore;
        this.customHazardHandler = customHazardHandler;
        this.instanceLimits = instanceLimits;
        this.templateValidator = new TemplateValidator().withInstanceLimits(instanceLimits);
        this.instanceGate = null;
        this.configSnapshot = null;
        ensureLockManagerStarted();
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
        this.customHazardHandler = customHazardHandler;
        this.instanceLimits = instanceLimits;
        this.templateValidator = new TemplateValidator().withInstanceLimits(instanceLimits);
        this.configSnapshot = configSnapshot;
        this.instanceGate = configSnapshot != null ? new InstanceOnlyGate(configSnapshot, telemetry) : null;
        ensureLockManagerStarted();
    }

    /**
     * Builds an arena from a template with policy context for telemetry.
     *
     * @param template The arena template
     * @param policyId The policy ID (for telemetry correlation)
     * @param policyVersion The policy version (for telemetry correlation)
     * @param originX World X coordinate for origin
     * @param originY World Y coordinate for origin
     * @param originZ World Z coordinate for origin
     * @return Build result with arena handle or error
     */
    public BuildResult build(ArenaTemplate template, @Nullable String policyId, int policyVersion,
                            int originX, int originY, int originZ) {
        return doBuild(template, policyId, policyVersion, originX, originY, originZ);
    }

    /**
     * Builds an arena from a template.
     *
     * @param template The arena template
     * @param originX World X coordinate for origin
     * @param originY World Y coordinate for origin
     * @param originZ World Z coordinate for origin
     * @return Build result with arena handle or error
     */
    public BuildResult build(ArenaTemplate template, int originX, int originY, int originZ) {
        return doBuild(template, null, 0, originX, originY, originZ);
    }

    private BuildResult doBuild(ArenaTemplate template, @Nullable String policyId, int policyVersion,
                               int originX, int originY, int originZ) {
        UUID arenaId = UUID.randomUUID();
        long startTime = System.currentTimeMillis();
        String dimension = resolveDimension();

        // Instance-only gate check (fail fast before validation)
        var gate = instanceGate;
        if (gate != null && blockPlacer instanceof MinecraftBlockPlacer mcb) {
            try {
                gate.requireAllowedOrThrow(mcb.getLevel(), "ArenaBuilder.build", template.id(), template.version());
            } catch (InstanceOnlyGate.GateBlockedException e) {
                LOGGER.error(e.getMessage());
                return BuildResult.failure(e.getMessage(), new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }
        }

        // Pre-build validation (defensive even if registry validated)
        ValidationResult validation = templateValidator.validate(template);
        if (!validation.valid()) {
            String msg = "Template validation failed: " + validation.errorsAsString();
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.build.validation_failed", Map.of(
                "templateId", template.id(),
                "templateVersion", template.version(),
                "arenaId", arenaId.toString(),
                "errors", validation.errors()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }
        if (validation.hasWarnings()) {
            LOGGER.warn("Template '{}' validation warnings: {}", template.id(), validation.warningsAsString());
            telemetry.emit("arena.build.validation_warning", Map.of(
                "templateId", template.id(),
                "templateVersion", template.version(),
                "warnings", validation.warnings()
            ));
        }

        // Instance settings clamp/coverage check (server limits)
        InstanceSettingsValidator isValidator = new InstanceSettingsValidator();
        InstanceSettingsValidator.Result isResult = isValidator.validate(template, instanceLimits);
        if (!isResult.errors().isEmpty()) {
            String msg = "Instance settings invalid: " + String.join("; ", isResult.errors());
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.instance.coverage_insufficient", Map.of(
                "templateId", template.id(),
                "errors", isResult.errors()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }
        if (!isResult.warnings().isEmpty()) {
            telemetry.emit("arena.instance.clamped", Map.of(
                "templateId", template.id(),
                "warnings", isResult.warnings(),
                "effectiveChunkRadius", isResult.effectiveChunkRadius(),
                "effectiveTickDistance", isResult.effectiveTickDistance()
            ));
        }

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

        ArenaTemplate.BuildSettings.Order buildOrder = resolveBuildOrder(template);
        ArenaTemplate.BuildSettings.Priority buildPriority = resolveBuildPriority(template);
        if (buildPriority == ArenaTemplate.BuildSettings.Priority.ASYNC) {
            String msg = "Template '%s' requires ASYNC build; sync builder is not allowed"
                .formatted(template.id());
            LOGGER.warn("{}", msg);
            telemetry.emit("arena.build.priority_blocked", Map.of(
                "templateId", template.id(),
                "templateVersion", template.version(),
                "requestedPriority", buildPriority.name()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }

        // Determine max blocks based on template category
        int maxBlocks = determineMaxBlocks(template);

        // DD8: Soft budget warning at 80%
        int warningThreshold = (int) (maxBlocks * 0.80);
        if (dryRun.totalBlocks() > warningThreshold && dryRun.totalBlocks() <= maxBlocks) {
            LOGGER.warn("Template '{}' at {}% of block budget ({}/{} blocks)",
                template.id(),
                (dryRun.totalBlocks() * 100) / maxBlocks,
                dryRun.totalBlocks(),
                maxBlocks);
            telemetry.emit("arena.build.budget_warning", Map.of(
                "templateId", template.id(),
                "templateVersion", template.version(),
                "estimatedBlocks", dryRun.totalBlocks(),
                "maxBlocks", maxBlocks,
                "percentUsed", (dryRun.totalBlocks() * 100) / maxBlocks
            ));
        }

        if (dryRun.totalBlocks() > maxBlocks) {
            String msg = "Estimated blocks %d exceed limit %d".formatted(dryRun.totalBlocks(), maxBlocks);
            LOGGER.error("Cannot build template '{}': {}", template.id(), msg);
            telemetry.emit("arena.build.block_budget_exceeded", Map.of(
                "templateId", template.id(),
                "estimatedBlocks", dryRun.totalBlocks(),
                "maxBlocks", maxBlocks,
                "floorBlocks", dryRun.floorBlocks(),
                "wallBlocks", dryRun.wallBlocks(),
                "ceilingBlocks", dryRun.ceilingBlocks(),
                "underfloorBlocks", dryRun.underfloorBlocks()
            ));
            return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
        }

        long maxDurationMs = determineMaxBuildTimeMs(template);
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
                    "lockedTemplateId", template.id(),
                    "owner", lockOwner,
                    "waitTimeMs", lockWaitMs
                ));
                String msg = "Template lock timeout after %dms".formatted(lockWaitMs);
                return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }

            lockAcquiredAtMs = System.currentTimeMillis();
            telemetry.emit("arena.build.lock_acquired", Map.of(
                "lockedTemplateId", template.id(),
                "owner", lockOwner,
                "waitTimeMs", lockWaitMs
            ));

            UUID rateLimitPlayerId = UUID.nameUUIDFromBytes(lockOwner.getBytes());
            BuildPermit permit = BUILD_RATE_LIMITER.requestPermit(rateLimitPlayerId, lockOwner);
            if (!permit.isGranted()) {
                BuildPermit.Rejected rejected = (BuildPermit.Rejected) permit;
                telemetry.emit("arena.build.permit_rejected", Map.of(
                    "templateId", template.id(),
                    "reason", rejected.reason().name(),
                    "retryAfterMs", rejected.retryAfter().toMillis(),
                    "queuePosition", rejected.queuePosition()
                ));
                String msg = "Build rate limited: " + rejected.reason().name();
                return BuildResult.failure(msg, new BuildTransaction.RollbackResult(true, 0, 0, 0, 0));
            }

            buildPermit = (BuildPermit.Granted) permit;
            telemetry.emit("arena.build.permit_granted", Map.of(
                "permitId", buildPermit.permitId(),
                "templateId", template.id(),
                "waitTimeMs", buildPermit.waitTimeMs()
            ));

            transaction = new BuildTransaction(template.id(), maxBlocks, maxDurationMs);

            LOGGER.info("Starting build for template '{}' at ({},{},{}) with max {} blocks",
                template.id(), originX, originY, originZ, maxBlocks);

            // Build telemetry with policy context
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
            startEventData.put("estimatedMs", estimateBuildTimeMs(template));
            startEventData.put("maxBlocks", maxBlocks);
            if (policyId != null) {
                startEventData.put("policyId", policyId);
                startEventData.put("policyVersion", policyVersion);
            }
            telemetry.emit("arena.build.start", startEventData);

            initPerformanceMonitoring();

            // 1. Ensure chunks are loaded (DD9)
            ChunkLoadingManager.ChunkLoadResult chunkResult = loadRequiredChunks(template, originX, originZ);
            if (!chunkResult.success()) {
                throw new BuildException("Chunk loading failed: " + chunkResult.errorMessage());
            }

            // Track chunks in transaction
            for (long chunkPos : chunkResult.loadedChunks()) {
                transaction.trackChunk(chunkPos);
            }

            // 2. Build order handling
            if (buildOrder == ArenaTemplate.BuildSettings.Order.STRUCTURE_FIRST && template.structureNbt() != null) {
                placeStructure(template, originX, originY, originZ, transaction);
            }

            if (buildOrder == ArenaTemplate.BuildSettings.Order.WALLS_FIRST) {
                if (template.walls() != null && template.walls().enabled()) {
                    buildWalls(template, originX, originY, originZ, transaction);
                }
                if (template.floor() != null) {
                    buildFloor(template, originX, originY, originZ, transaction);
                }
            } else {
                if (template.floor() != null) {
                    buildFloor(template, originX, originY, originZ, transaction);
                }
                if (template.walls() != null && template.walls().enabled()) {
                    buildWalls(template, originX, originY, originZ, transaction);
                }
            }

            if (template.ceiling() != null && template.ceiling().enabled()) {
                buildCeiling(template, originX, originY, originZ, transaction);
            }

            if (template.underfloor() != null) {
                buildUnderfloor(template, originX, originY, originZ, transaction);
            }

            if (template.hazards() != null && !template.hazards().isEmpty()) {
                placeHazards(template, originX, originY, originZ, transaction);
            }

            if (buildOrder != ArenaTemplate.BuildSettings.Order.STRUCTURE_FIRST && template.structureNbt() != null) {
                placeStructure(template, originX, originY, originZ, transaction);
            }

            // 8. Commit transaction
            transaction.commit();

            long duration = System.currentTimeMillis() - startTime;
            maybeCheckGoldenReference(template, transaction);
            maybeWarnOnBuildTime(template, duration);

            // Record for future estimations
            if (historyStore != null) {
                historyStore.recordBuild(template.id(), duration, transaction.getBlockCount(), true);
            }

            Map<String, Object> endEventData = new java.util.HashMap<>();
            endEventData.put("templateId", template.id());
            endEventData.put("templateVersion", template.version());
            endEventData.put("arenaId", arenaId.toString());
            endEventData.put("success", true);
            endEventData.put("actualMs", duration);
            endEventData.put("build_ms", duration); // baseline compatibility
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

        LOGGER.info("Build completed for '{}' in {}ms: {} blocks",
            template.id(), duration, transaction.getBlockCount());

        recordBuildOutcome(true, false, template.id());

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
                        "lockedTemplateId", template.id(),
                        "owner", lockOwner,
                        "heldForMs", heldForMs
                    ));
                }
            }
        }
    }

    private BuildResult handleBuildFailure(
            ArenaTemplate template,
            @Nullable String policyId,
            int policyVersion,
            UUID arenaId,
            BuildTransaction transaction,
            Exception error,
            long startTime,
            int originX,
            int originY,
            int originZ,
            @Nullable String dimension) {

        LOGGER.error("Build failed for '{}': {}", template.id(), error.getMessage());
        transaction.markFailed();

        // DD7: Rollback all changes
        BuildTransaction.RollbackResult rollbackResult = transaction.rollback(
            blockPlacer::revertBlock,
            entitySpawner::removeEntity,
            packedChunkPos -> {} // Chunks released separately
        );

        // Release chunk tickets
        chunkManager.releaseAllTickets();

        long duration = System.currentTimeMillis() - startTime;

        // Record failed build
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

    // === Build Steps ===

    @Nullable
    private String resolveDimension() {
        if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            var level = mcPlacer.getLevel();
            if (level != null) {
                return level.dimension().location().toString();
            }
        }
        return null;
    }

    private int getSizeX(ArenaTemplate template) {
        Integer sx = template.sizeX();
        return sx != null ? sx.intValue() : template.size();
    }

    private int getSizeZ(ArenaTemplate template) {
        Integer sz = template.sizeZ();
        return sz != null ? sz.intValue() : template.size();
    }

    private void buildFloor(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        if (template.floor() == null) return;

        var floor = template.floor();
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);

        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = startX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = startZ + dz;

                    String material = floor.material();
                    // Border check
                    if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                        boolean inBorderX = dx < floor.borderWidth() || dx >= sizeX - floor.borderWidth();
                        boolean inBorderZ = dz < floor.borderWidth() || dz >= sizeZ - floor.borderWidth();
                        if (inBorderX || inBorderZ) {
                            material = floor.borderMaterial();
                        }
                    }

                    placeBlock(worldX, worldY, worldZ, material, tx);
                }
            }
        }
    }

    private void buildWalls(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        var walls = template.walls();
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);
        int endX = startX + sizeX - 1;
        int endZ = startZ + sizeZ - 1;

        for (int dy = 0; dy < walls.height(); dy++) {
            int worldY = walls.startY() + dy;

            // North and South walls
            for (int t = 0; t < walls.thickness(); t++) {
                for (int x = startX; x <= endX; x++) {
                    placeBlock(x, worldY, startZ - t, walls.material(), tx);
                    placeBlock(x, worldY, endZ + t, walls.material(), tx);
                }
            }

            // East and West walls
            for (int t = 0; t < walls.thickness(); t++) {
                for (int z = startZ + 1; z <= endZ - 1; z++) { // avoid double-counting corners
                    placeBlock(startX - t, worldY, z, walls.material(), tx);
                    placeBlock(endX + t, worldY, z, walls.material(), tx);
                }
            }
        }
    }

    private void buildCeiling(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        var ceiling = template.ceiling();
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);

        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dy = 0; dy < ceiling.thickness(); dy++) {
                    placeBlock(startX + dx, ceiling.y() + dy, startZ + dz, ceiling.material(), tx);
                }
            }
        }
    }

    private void buildUnderfloor(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        var underfloor = template.underfloor();
        var floor = template.floor();
        if (floor == null) return;

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);

        String material = underfloor.sameAsFloor() ? floor.material() : underfloor.material();

        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dy = 1; dy <= underfloor.depth(); dy++) {
                    placeBlock(startX + dx, floor.y() - dy, startZ + dz, material, tx);
                }
            }
        }
    }

    private void placeHazards(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        LOGGER.debug("Placing {} hazards for template '{}'", template.hazards().size(), template.id());
        int placedHazards = 0;
        for (ArenaTemplate.Hazard hazard : template.hazards()) {
            switch (hazard.type()) {
                case "lava_ring" -> placeLavaRing(hazard, template, originX, originZ, tx);
                case "lava_pool" -> placeLavaPool(hazard, template, originX, originZ, tx);
                case "void_pit" -> placeVoidPit(hazard, template, originX, originZ, tx);
                case "spike_trap" -> placeSpikeTrap(hazard, template, originX, originZ, tx);
                case "fire_zone" -> placeFireZone(hazard, template, originX, originZ, tx);
                case "magma_floor" -> placeMagmaFloor(hazard, template, originX, originZ, tx);
                case "falling_blocks" -> placeFallingBlocks(hazard, template, originX, originZ, tx);
                case "custom" -> placeCustomHazard(hazard, template, originX, originZ, tx);
                default -> LOGGER.debug("Hazard type '{}' has no placement implementation", hazard.type());
            }
            placedHazards++;
        }
        if (placedHazards > 0) {
            telemetry.emit("arena.hazard.placed", Map.of(
                "templateId", template.id(),
                "count", placedHazards
            ));
        }
    }

    private void placeStructure(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        ArenaTemplate.StructureNbt structureNbt = template.structureNbt();
        if (structureNbt == null) {
            return;
        }

        LOGGER.debug("Placing structure NBT '{}' for template '{}'", structureNbt.path(), template.id());

        // Calculate placement offset
        int offsetX = originX;
        int offsetY = originY;
        int offsetZ = originZ;

        if (structureNbt.offset() != null) {
            offsetX += structureNbt.offset().x();
            offsetY += structureNbt.offset().y();
            offsetZ += structureNbt.offset().z();
        }

        // Structure placement is delegated to LevelAccess interface
        // This method prepares the parameters; actual NBT loading and block placement
        // happens through the Minecraft integration layer
        telemetry.emit("arena.structure.placement_requested", Map.of(
            "templateId", template.id(),
            "path", structureNbt.path(),
            "offsetX", offsetX,
            "offsetY", offsetY,
            "offsetZ", offsetZ,
            "rotation", structureNbt.rotation() != null ? structureNbt.rotation() : "NONE",
            "mirror", structureNbt.mirror() != null ? structureNbt.mirror() : "NONE",
            "ignoreAir", structureNbt.ignoreAir()
        ));

        // Track structure placement in transaction for potential rollback
        tx.trackStructurePlacement(structureNbt.path(), offsetX, offsetY, offsetZ);
    }

    private int resolveY(ArenaTemplate.Hazard hazard, ArenaTemplate template) {
        Integer hazardY = hazard.y();
        int baseY = hazardY != null ? hazardY : template.floor().y();
        if (hazard.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            baseY += template.floor().y();
        }
        return baseY;
    }

    private int[] resolveCenter(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ) {
        Object centerObj = hazard.params() != null ? hazard.params().get("center") : null;
        if (centerObj instanceof java.util.List<?> list && list.size() == 3) {
            int[] c = new int[3];
            for (int i = 0; i < 3; i++) {
                Object v = list.get(i);
                c[i] = v instanceof Number n ? n.intValue() : 0;
            }
            return c;
        }
        return new int[]{originX, resolveY(hazard, template), originZ};
    }

    private void placeLavaRing(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int inner = ((Number) hazard.params().getOrDefault("innerRadius", 1)).intValue();
        int outer = ((Number) hazard.params().getOrDefault("outerRadius", inner + 1)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 >= inner * inner && r2 <= outer * outer) {
                    placeBlock(center[0] + dx, y, center[2] + dz, material, tx);
                }
            }
        }
    }

    private void placeLavaPool(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    placeBlock(center[0] + dx, y, center[2] + dz, material, tx);
                }
            }
        }
    }

    private void placeVoidPit(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
        int depth = ((Number) hazard.params().getOrDefault("depth", 10)).intValue();
        int yTop = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    for (int dy = 0; dy < depth; dy++) {
                        placeBlock(center[0] + dx, yTop - dy, center[2] + dz, "minecraft:void_air", tx);
                    }
                }
            }
        }
    }

    private void placeSpikeTrap(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object positionsObj = hazard.params() != null ? hazard.params().get("positions") : null;
        if (!(positionsObj instanceof java.util.List<?> positions)) return;
        String material = (String) hazard.params().getOrDefault("material", "minecraft:iron_bars");
        for (Object posObj : positions) {
            if (posObj instanceof java.util.List<?> p && p.size() == 3) {
                int x = ((Number) p.get(0)).intValue() + originX;
                int y = resolveY(hazard, template);
                int z = ((Number) p.get(2)).intValue() + originZ;
                placeBlock(x, y, z, material, tx);
            }
        }
    }

    private void placeFireZone(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object minObj = hazard.params() != null ? hazard.params().get("min") : null;
        Object maxObj = hazard.params() != null ? hazard.params().get("max") : null;
        if (!(minObj instanceof java.util.List<?> min) || !(maxObj instanceof java.util.List<?> max) || min.size() != 3 || max.size() != 3) {
            return;
        }
        int minX = ((Number) min.get(0)).intValue() + originX;
        int minY = resolveY(hazard, template);
        int minZ = ((Number) min.get(2)).intValue() + originZ;
        int maxX = ((Number) max.get(0)).intValue() + originX;
        int maxZ = ((Number) max.get(2)).intValue() + originZ;
        String block = (String) hazard.params().getOrDefault("block", "minecraft:fire");
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                placeBlock(x, minY, z, block, tx);
            }
        }
    }

    private void placeMagmaFloor(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        double coverage = ((Number) hazard.params().getOrDefault("coverage", 0.1d)).doubleValue();
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);
        int y = resolveY(hazard, template);
        int total = (int) Math.round(sizeX * sizeZ * Math.min(coverage, 0.5));
        String block = (String) hazard.params().getOrDefault("block", "minecraft:magma_block");
        int placed = 0;
        // Deterministic grid placement every 2 blocks until coverage met
        outer:
        for (int dx = 0; dx < sizeX; dx += 2) {
            for (int dz = 0; dz < sizeZ; dz += 2) {
                if (placed >= total) break outer;
                placeBlock(startX + dx, y, startZ + dz, block, tx);
                placed++;
            }
        }
    }

    private void placeFallingBlocks(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object areaObj = hazard.params() != null ? hazard.params().get("area") : null;
        String blockType = (String) hazard.params().getOrDefault("blockType", "minecraft:sand");
        int count = ((Number) hazard.params().getOrDefault("count", 5)).intValue();
        int interval = ((Number) hazard.params().getOrDefault("interval", 20)).intValue();

        // interval determines vertical spacing between blocks (in blocks, derived from tick interval)
        // Higher interval = more spaced out falling blocks
        int verticalSpacing = Math.max(1, interval / 10);

        if (areaObj instanceof java.util.List<?> area && area.size() == 3) {
            int centerX = ((Number) area.get(0)).intValue() + originX;
            int centerZ = ((Number) area.get(2)).intValue() + originZ;
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                placeBlock(centerX, y + (i * verticalSpacing), centerZ, blockType, tx);
            }
        } else {
            // Fallback: drop a short column at origin
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                placeBlock(originX, y + (i * verticalSpacing), originZ, blockType, tx);
            }
        }
    }

    private void placeCustomHazard(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        if (customHazardHandler != null) {
            try {
                customHazardHandler.placeCustom(hazard, template, originX, originZ, tx);
            } catch (Exception e) {
                LOGGER.error("Custom hazard placement failed for {}: {}", hazard.params(), e.getMessage());
                telemetry.emit("arena.hazard.custom_failed", Map.of(
                    "templateId", template.id(),
                    "hazardType", hazard.type(),
                    "error", e.getMessage()
                ));
            }
        } else {
            LOGGER.warn("Custom hazard encountered but no handler provided; skipping. Hazard params: {}", hazard.params());
            telemetry.emit("arena.hazard.custom_skipped", Map.of(
                "templateId", template.id(),
                "hazardType", hazard.type()
            ));
        }
    }

    private void placeBlock(int x, int y, int z, String material, BuildTransaction tx) {
        maybeCheckPerformance(tx);
        long packedPos = CompactBlockTracker.pack(x, y, z);
        int previousStateId = blockPlacer.placeBlock(x, y, z, material);
        tx.trackBlock(packedPos, previousStateId);
    }

    private void initPerformanceMonitoring() {
        if (!(blockPlacer instanceof MinecraftBlockPlacer mcPlacer)) {
            return;
        }
        var level = mcPlacer.getLevel();
        if (level == null || level.getServer() == null) {
            return;
        }
        Supplier<Double> supplier = () -> level.getServer().getAverageTickTimeNanos() / 1_000_000.0;
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

    private void maybeCheckPerformance(BuildTransaction tx) {
        @Nullable Supplier<Double> supplier = msptSupplier;
        @Nullable MsptMonitor monitor = msptMonitor;
        @Nullable PerformanceBudgetEnforcer enforcer = performanceEnforcer;
        if (enforcer == null || monitor == null || supplier == null) {
            return;
        }
        long placements = tx.getTotalBlockPlacements();
        // Atomic check-then-update to prevent race conditions
        long currentThreshold = nextPerformanceCheckAt.get();
        if (placements < currentThreshold) {
            return;
        }
        // Only update if we're the first thread to pass the threshold
        if (!nextPerformanceCheckAt.compareAndSet(currentThreshold, placements + PERFORMANCE_CHECK_INTERVAL_BLOCKS)) {
            return; // Another thread already updated, skip this check
        }

        double mspt = supplier.get();
        monitor.recordSample(mspt);
        PerformanceAction action = enforcer.checkPerformance();
        if (action == PerformanceAction.CONTINUE) {
            return;
        }

        double tps = mspt > 0 ? Math.min(1000.0 / mspt, 20.0) : 20.0;
        if (action == PerformanceAction.ABORT) {
            telemetry.emit("arena.build.performance_abort", Map.of(
                "templateId", tx.getTemplateId(),
                "mspt", mspt,
                "tps", tps,
                "baselineMspt", enforcer.getBaseline(),
                "buildImpactMs", enforcer.getBuildImpact()
            ));
            throw new BuildException("Performance budget exceeded; build aborted");
        }

        telemetry.emit("arena.build.performance_backpressure", Map.of(
            "templateId", tx.getTemplateId(),
            "action", action.name(),
            "mspt", mspt,
            "tps", tps,
            "baselineMspt", enforcer.getBaseline(),
            "buildImpactMs", enforcer.getBuildImpact()
        ));
        enforcer.applyBackpressure(action);
    }

    // === Chunk Loading ===

    private ChunkLoadingManager.ChunkLoadResult loadRequiredChunks(ArenaTemplate template, int originX, int originZ) {
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);

        int minChunkX = (originX - sizeX / 2) >> 4;
        int maxChunkX = (originX + sizeX / 2) >> 4;
        int minChunkZ = (originZ - sizeZ / 2) >> 4;
        int maxChunkZ = (originZ + sizeZ / 2) >> 4;

        // Use retry-enabled chunk loading for resilience
        return chunkManager.ensureChunksLoadedWithRetry(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    // === Estimation (DD10) ===

    /**
     * Estimates build time using heuristic + historical data.
     */
    public long estimateBuildTimeMs(ArenaTemplate template) {
        // Try historical data first
        if (historyStore != null) {
            Long historical = historyStore.getP75BuildTime(template.id(), MIN_HISTORY_SAMPLES);
            if (historical != null) {
                return historical;
            }
        }

        // Fall back to heuristic
        return estimateHeuristic(template);
    }

    /**
     * DD10: Calculates estimation accuracy and returns the accuracy band.
     *
     * @param estimated The estimated build time in ms
     * @param actual The actual build time in ms
     * @return AccuracyResult with band and deviation
     */
    public AccuracyResult calculateAccuracy(long estimated, long actual) {
        if (estimated == 0) {
            return new AccuracyResult(AccuracyBand.POOR, 1.0);
        }

        double deviation = Math.abs((double)(actual - estimated) / estimated);

        AccuracyBand band;
        if (deviation <= ACCURACY_EXCELLENT) {
            band = AccuracyBand.EXCELLENT;  // ±20%
        } else if (deviation <= ACCURACY_GOOD) {
            band = AccuracyBand.GOOD;       // ±35%
        } else if (deviation <= ACCURACY_ACCEPTABLE) {
            band = AccuracyBand.ACCEPTABLE; // ±50%
        } else {
            band = AccuracyBand.POOR;       // >±50%
        }

        return new AccuracyResult(band, deviation);
    }

    /**
     * DD10: Accuracy bands for estimation feedback.
     */
    public enum AccuracyBand {
        EXCELLENT,  // ±20%
        GOOD,       // ±35%
        ACCEPTABLE, // ±50%
        POOR        // >±50%
    }

    /**
     * DD10: Result of accuracy calculation.
     */
    public record AccuracyResult(AccuracyBand band, double deviation) {
        public boolean needsCalibration() {
            return band == AccuracyBand.POOR;
        }
    }

    private long estimateHeuristic(ArenaTemplate template) {
        int estimatedBlocks = estimateBlockCount(template);
        double baseEstimate = estimatedBlocks * MS_PER_BLOCK_BASELINE;

        // Apply multipliers
        if (template.structureNbt() != null) {
            baseEstimate *= NBT_MULTIPLIER;
        }
        if (template.hazards() != null && template.hazards().size() > 10) {
            baseEstimate *= HAZARD_MULTIPLIER;
        }

        return (long) baseEstimate;
    }

    /**
     * Compute block counts without placing blocks.
     */
    public BuildDryRun dryRun(ArenaTemplate template) {
        return BuildDryRunCalculator.calculate(template);
    }

    /**
     * Dry-run validation summary: block counts, chunk requirements, and time estimates.
     */
    public BuildValidation validateBuild(ArenaTemplate template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        int blocksRequired = dryRun.totalBlocks();
        int maxBlocks = determineMaxBlocks(template);
        int warnBlocks = (int) (maxBlocks * 0.80);

        if (blocksRequired > maxBlocks) {
            errors.add("Estimated blocks %d exceed limit %d".formatted(blocksRequired, maxBlocks));
        } else if (blocksRequired > warnBlocks) {
            warnings.add("Estimated blocks %d near limit %d".formatted(blocksRequired, maxBlocks));
        }

        long estimatedMs = estimateBuildTimeMs(template);
        long maxBuildTimeMs = determineMaxBuildTimeMs(template);
        long warnBuildTimeMs = (long) (maxBuildTimeMs * 0.80);
        if (estimatedMs > maxBuildTimeMs) {
            errors.add("Estimated build time %dms exceeds limit %dms".formatted(estimatedMs, maxBuildTimeMs));
        } else if (estimatedMs > warnBuildTimeMs) {
            warnings.add("Estimated build time %dms near limit %dms".formatted(estimatedMs, maxBuildTimeMs));
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int chunksX = (int) Math.ceil(sizeX / 16.0);
        int chunksZ = (int) Math.ceil(sizeZ / 16.0);
        int chunksRequired = Math.max(1, chunksX * chunksZ);

        return new BuildValidation(errors.isEmpty(), blocksRequired, chunksRequired, estimatedMs, warnings, errors);
    }

    private int estimateBlockCount(ArenaTemplate template) {
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);

        int floorBlocks = sizeX * sizeZ;
        if (template.floor() != null) {
            floorBlocks *= template.floor().thickness();
        }

        int wallBlocks = 0;
        if (template.walls() != null && template.walls().enabled()) {
            int perimeter = 2 * (sizeX + sizeZ - 2); // avoid double-counting corners
            wallBlocks = perimeter * template.walls().height() * template.walls().thickness();
        }

        int ceilingBlocks = 0;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            ceilingBlocks = sizeX * sizeZ * template.ceiling().thickness();
        }

        return floorBlocks + wallBlocks + ceilingBlocks;
    }

    private int determineMaxBlocks(ArenaTemplate template) {
        if (template.limits() != null && template.limits().maxBlocks() > 0) {
            return Math.min(template.limits().maxBlocks(), HARD_CAP_BLOCKS);
        }

        int defaultMax = DEFAULT_MAX_BLOCKS;
        int bossMax = BOSS_MAX_BLOCKS;
        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshot;
        if (snapshot != null) {
            defaultMax = snapshot.defaultMaxBlocks();
            bossMax = snapshot.bossMaxBlocks();
        }
        return Math.min(isBossTemplate(template) ? bossMax : defaultMax, HARD_CAP_BLOCKS);
    }

    private long determineMaxBuildTimeMs(ArenaTemplate template) {
        if (template.limits() != null && template.limits().maxBuildTimeMs() > 0) {
            return template.limits().maxBuildTimeMs();
        }
        int defaultMax = DEFAULT_MAX_BUILD_TIME_MS;
        int bossMax = BOSS_MAX_BUILD_TIME_MS;
        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshot;
        if (snapshot != null) {
            defaultMax = snapshot.defaultMaxBuildTimeMs();
            bossMax = snapshot.bossMaxBuildTimeMs();
        }
        return isBossTemplate(template) ? bossMax : defaultMax;
    }

    private ArenaTemplate.BuildSettings.Order resolveBuildOrder(ArenaTemplate template) {
        if (template.buildSettings() == null || template.buildSettings().buildOrder() == null) {
            return ArenaTemplate.BuildSettings.Order.FLOOR_FIRST;
        }
        return template.buildSettings().buildOrder();
    }

    private ArenaTemplate.BuildSettings.Priority resolveBuildPriority(ArenaTemplate template) {
        if (template.buildSettings() == null || template.buildSettings().buildPriority() == null) {
            return ArenaTemplate.BuildSettings.Priority.SYNC;
        }
        return template.buildSettings().buildPriority();
    }

    private boolean isBossTemplate(ArenaTemplate template) {
        return template.tags() != null
            && (template.tags().contains("boss") || template.tags().contains("large"));
    }

    private void maybeCheckGoldenReference(ArenaTemplate template, BuildTransaction tx) {
        if (!"default_flat_64".equals(template.id())) {
            return;
        }
        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        GoldenReference golden = GoldenReference.defaultFlat64();
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
                "templateId", template.id(),
                "blockCount", tx.getBlockCount()
            ));
        }
    }

    private void maybeWarnOnBuildTime(ArenaTemplate template, long durationMs) {
        long maxBuildTimeMs = determineMaxBuildTimeMs(template);
        if (durationMs > maxBuildTimeMs) {
            LOGGER.warn("Build time {}ms exceeded limit {}ms for template {}",
                durationMs, maxBuildTimeMs, template.id());
            telemetry.emit("arena.build.time_exceeded", Map.of(
                "templateId", template.id(),
                "limitMs", maxBuildTimeMs,
                "actualMs", durationMs
            ));
        }
    }

    // === Interfaces ===

    @FunctionalInterface
    public interface BlockPlacer {
        /**
         * Places a block and returns the previous state ID.
         */
        int placeBlock(int x, int y, int z, String material);

        /**
         * Reverts a block to a previous state.
         */
        default boolean revertBlock(long packedPos, int stateId) {
            return true; // Override in implementation
        }
    }

    /**
     * Optional hook for residual metrics when non-Minecraft placers can evaluate cleanup.
     */
    public interface ResidualProvider {
        @Nullable ResidualMetrics measureResiduals(ArenaTemplate template,
                                                   int originX,
                                                   int originY,
                                                   int originZ,
                                                   int expectedBlocks);
    }

    @FunctionalInterface
    public interface EntitySpawner {
        UUID spawnEntity(int x, int y, int z, String entityType);

        default boolean removeEntity(UUID entityId) {
            return true; // Override in implementation
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

    /**
     * Computes residual metrics using MetricsCompatibilityLayer when level is available.
     */
    private ResidualMetrics measureResiduals(ArenaTemplate template, int originX, int originY, int originZ, MinecraftBlockPlacer mcPlacer) {
        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);
        int expectedBlocks = dryRun.totalBlocks();

        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal : template.size();
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int minX = originX - halfX;
        int maxX = originX + halfX - 1;
        int minZ = originZ - halfZ;
        int maxZ = originZ + halfZ - 1;

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
        var residuals = MetricsCompatibilityLayer.measureResiduals(mcPlacer.level(), bounds, expectedBlocks);
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

    public record ResidualMetrics(int entitiesResidual, int blocksResidual) {}

    public record BuildValidation(
        boolean valid,
        int blocksRequired,
        int chunksRequired,
        long estimatedMs,
        List<String> warnings,
        List<String> errors
    ) {}
}

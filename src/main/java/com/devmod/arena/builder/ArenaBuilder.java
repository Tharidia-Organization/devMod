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
import com.devmod.arena.performance.PerformanceBudgetEnforcer.PerformanceAction;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.GoldenReference;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;
import com.devmod.util.HotPathLogger;

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

            UUID rateLimitPlayerId = UUID.nameUUIDFromBytes(lockOwner.getBytes(StandardCharsets.UTF_8));
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

            // P0: Gate hot-path log behind rate limiter to reduce log spam during bulk builds
            HotPathLogger.rateLimitedInfo(LOGGER, "arena.build.start", 5000,
                () -> String.format("Starting build for template '%s' at (%d,%d,%d) with max %d blocks",
                    template.id(), originX, originY, originZ, maxBlocks));

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
                    buildWalls(template, originX, originZ, transaction);
                }
                if (template.floor() != null) {
                    buildFloor(template, originX, originZ, transaction);
                }
            } else {
                if (template.floor() != null) {
                    buildFloor(template, originX, originZ, transaction);
                }
                if (template.walls() != null && template.walls().enabled()) {
                    buildWalls(template, originX, originZ, transaction);
                }
            }

            if (template.ceiling() != null && template.ceiling().enabled()) {
                buildCeiling(template, originX, originZ, transaction);
            }

            if (template.underfloor() != null) {
                buildUnderfloor(template, originX, originZ, transaction);
            }

            if (template.hazards() != null && !template.hazards().isEmpty()) {
                placeHazards(template, originX, originZ, transaction);
            }

            // Place lighting sources based on template configuration
            if (template.lighting() != null) {
                placeLighting(template, originX, originZ, transaction);
            }

            if (buildOrder != ArenaTemplate.BuildSettings.Order.STRUCTURE_FIRST && template.structureNbt() != null) {
                placeStructure(template, originX, originY, originZ, transaction);
            }

            // P2: Flush BatchBlockPlacer before commit to ensure all blocks are placed
            if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
                batchPlacer.flush();
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
            } else if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
                // BatchBlockPlacer uses level-based metrics via MetricsCompatibilityLayer
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

        // P0: Gate hot-path log behind rate limiter to reduce log spam during bulk builds
        final int blockCount = transaction.getBlockCount();
        HotPathLogger.rateLimitedInfo(LOGGER, "arena.build.complete", 5000,
            () -> String.format("Build completed for '%s' in %dms: %d blocks",
                template.id(), duration, blockCount));

        // P1: Post-build entity audit to detect residual entities
        net.minecraft.server.level.ServerLevel auditLevel = null;
        if (blockPlacer instanceof BatchBlockPlacer batchPlacer) {
            auditLevel = batchPlacer.getLevel();
        } else if (blockPlacer instanceof MinecraftBlockPlacer mcPlacer) {
            auditLevel = mcPlacer.getLevel();
        }
        if (auditLevel != null) {
            int size = template.size();
            PostBuildEntityAudit.AuditResult auditResult = new PostBuildEntityAudit()
                .audit(auditLevel, originX, originY, originZ,
                       originX + size, originY + size, originZ + size);
            if (auditResult.hasResiduals()) {
                telemetry.emit("arena.build.residual_entities", Map.of(
                    "templateId", template.id(),
                    "arenaId", arenaId.toString(),
                    "residualCount", auditResult.residualEntities(),
                    "itemsFound", auditResult.itemsFound(),
                    "mobsFound", auditResult.mobsFound(),
                    "auditDurationMs", auditResult.auditDurationMs()
                ));
            }

            // P1: Block integrity verification
            BlockIntegrityVerifier.VerificationResult integrityResult = new BlockIntegrityVerifier()
                .verify(auditLevel, originX, originY, originZ,
                        originX + size, originY + size, originZ + size,
                        transaction.getBlockCount());
            if (integrityResult.hasIntegrityIssues()) {
                telemetry.emit("arena.build.integrity_issue", Map.of(
                    "templateId", template.id(),
                    "arenaId", arenaId.toString(),
                    "expectedBlocks", integrityResult.expectedBlocks(),
                    "actualBlocks", integrityResult.actualNonAirBlocks(),
                    "integrityPercent", integrityResult.integrityPercent(),
                    "sampleFailures", integrityResult.sampleFailures(),
                    "verificationDurationMs", integrityResult.verificationDurationMs()
                ));
            }
        }

        recordBuildOutcome(true, false, template.id());

        // P1: Clear placer cache after successful commit to prevent memory leak
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

    private int getSizeX(ArenaTemplate template) {
        Integer sx = template.sizeX();
        return sx != null ? sx.intValue() : template.size();
    }

    private int getSizeZ(ArenaTemplate template) {
        Integer sz = template.sizeZ();
        return sz != null ? sz.intValue() : template.size();
    }

    // === Shape-aware helpers ===

    /**
     * Checks if relative coordinates (dx, dz from center) are inside the arena shape.
     * For RECTANGULAR: checks bounds
     * For CIRCULAR: checks if within radius
     * For RING: checks if within outer radius and outside inner radius
     */
    private boolean isInArenaShape(int dx, int dz, ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;

        return switch (shape) {
            case RECTANGULAR -> dx >= -halfX && dx < halfX && dz >= -halfZ && dz < halfZ;
            case CIRCULAR -> {
                int radius = Math.max(halfX, halfZ);
                yield (dx * dx + dz * dz) <= (radius * radius);
            }
            case RING -> {
                int outerRadius = Math.max(halfX, halfZ);
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                yield distSq <= (outerRadius * outerRadius) && distSq >= (innerRadius * innerRadius);
            }
        };
    }

    /**
     * Checks if relative coordinates are on the circular/ring border for wall placement.
     * @param dx relative X from center
     * @param dz relative Z from center
     * @param template arena template
     * @param thickness wall thickness (outward from edge)
     * @return true if position should have a wall block
     */
    private boolean isOnCircularBorder(int dx, int dz, ArenaTemplate template, int thickness) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null || shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            return false; // Rectangular walls handled separately
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;
        int outerRadius = Math.max(halfX, halfZ);
        int distSq = dx * dx + dz * dz;

        // Outer wall: between outerRadius and outerRadius + thickness
        int outerWallInnerSq = outerRadius * outerRadius;
        int outerWallOuterSq = (outerRadius + thickness) * (outerRadius + thickness);
        boolean onOuterWall = distSq >= outerWallInnerSq && distSq < outerWallOuterSq;

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            return onOuterWall;
        }

        // RING shape: also has inner wall
        Integer innerRadiusVal = template.ringInnerRadius();
        int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
        int innerWallOuterSq = innerRadius * innerRadius;
        int innerWallInnerSq = Math.max(0, (innerRadius - thickness) * (innerRadius - thickness));
        boolean onInnerWall = distSq <= innerWallOuterSq && distSq >= innerWallInnerSq;

        return onOuterWall || onInnerWall;
    }

    private void buildFloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        if (template.floor() == null) return;

        // Check if zone-aware floor building is needed
        var zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            // Check if any zone has a custom floor material
            boolean hasZoneFloorMaterials = zoneSettings.zones().stream()
                .anyMatch(z -> {
                    String mat = z.floorMaterial();
                    return mat != null && !mat.isEmpty();
                });
            if (hasZoneFloorMaterials) {
                buildZoneAwareFloor(template, originX, originZ, zoneSettings, tx);
                return;
            }
        }

        // Fall back to template-level floor
        buildTemplateFloor(template, originX, originZ, tx);
    }

    /**
     * Builds floor with zone-specific materials.
     * Each zone can have a different floor material defined in its environment.
     */
    private void buildZoneAwareFloor(ArenaTemplate template, int originX, int originZ,
                                     ArenaTemplate.ZoneSettings zoneSettings, BuildTransaction tx) {
        var floor = template.floor();
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        // Build zone layout with environments for floor material lookup
        ZoneLayout layout = buildZoneLayoutWithEnvironments(zoneSettings, halfX, halfZ);

        // Track zone floor placements for telemetry
        java.util.Map<String, Integer> zoneBlockCounts = new java.util.HashMap<>();

        int radius = Math.max(halfX, halfZ);
        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        for (int dx = -iterHalfX; dx < iterHalfX; dx++) {
            for (int dz = -iterHalfZ; dz < iterHalfZ; dz++) {
                if (!isInArenaShape(dx, dz, template)) {
                    continue;
                }

                // Determine material based on zone
                String material = floor.material(); // Default
                String zoneName = "default";

                // Find which zone this position belongs to
                java.util.Optional<ArenaZone> zone = layout.getZoneAt(dx, dz);
                if (zone.isPresent()) {
                    ArenaZone z = zone.get();
                    zoneName = z.name();
                    // Check if zone has custom floor material
                    java.util.Optional<net.minecraft.resources.ResourceLocation> zoneMaterial =
                        z.environment().floorMaterial();
                    if (zoneMaterial.isPresent()) {
                        material = zoneMaterial.get().toString();
                    }
                }

                // Border check - shape-aware (border uses template material, not zone)
                if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                    boolean inBorder = isOnFloorBorder(dx, dz, template, floor.borderWidth());
                    if (inBorder) {
                        material = floor.borderMaterial();
                        zoneName = "border";
                    }
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;
                    placeBlock(worldX, worldY, worldZ, material, tx);
                }

                // Track per-zone counts
                zoneBlockCounts.merge(zoneName, floor.thickness(), (a, b) -> a.intValue() + b.intValue());
            }
        }

        // Emit telemetry for zone-aware floor placement
        telemetry.emit("arena.floor.zone_aware", Map.of(
            "templateId", template.id(),
            "zoneCount", zoneSettings.zones().size(),
            "zoneCounts", zoneBlockCounts
        ));

        LOGGER.debug("Built zone-aware floor for '{}': {} zones, counts: {}",
            template.id(), zoneSettings.zones().size(), zoneBlockCounts);
    }

    /**
     * Builds zone layout with proper ZoneEnvironment including floor materials.
     */
    private ZoneLayout buildZoneLayoutWithEnvironments(ArenaTemplate.ZoneSettings settings, int halfX, int halfZ) {
        var zones = settings.zones();
        int zoneCount = zones.size();

        ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

        if (zoneCount == 1) {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            var def = zones.get(0);
            ArenaZone zone = ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ)
                .withEnvironment(createZoneEnvironment(def));
            builder.addZone(zone);
        } else if (zoneCount == 2) {
            builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
            var def0 = zones.get(0);
            var def1 = zones.get(1);
            builder.addZone(ArenaZone.rectangular(def0.name(), -halfX, -halfZ, 0, halfZ)
                .withEnvironment(createZoneEnvironment(def0)));
            builder.addZone(ArenaZone.rectangular(def1.name(), 0, -halfZ, halfX, halfZ)
                .withEnvironment(createZoneEnvironment(def1)));
        } else if (zoneCount <= 4) {
            builder.strategy(ZoneLayout.LayoutStrategy.QUADRANT);
            if (zones.size() >= 1) {
                builder.addZone(ArenaZone.rectangular(zones.get(0).name(), 0, -halfZ, halfX, 0)
                    .withEnvironment(createZoneEnvironment(zones.get(0))));
            }
            if (zones.size() >= 2) {
                builder.addZone(ArenaZone.rectangular(zones.get(1).name(), -halfX, -halfZ, 0, 0)
                    .withEnvironment(createZoneEnvironment(zones.get(1))));
            }
            if (zones.size() >= 3) {
                builder.addZone(ArenaZone.rectangular(zones.get(2).name(), -halfX, 0, 0, halfZ)
                    .withEnvironment(createZoneEnvironment(zones.get(2))));
            }
            if (zones.size() >= 4) {
                builder.addZone(ArenaZone.rectangular(zones.get(3).name(), 0, 0, halfX, halfZ)
                    .withEnvironment(createZoneEnvironment(zones.get(3))));
            }
        } else {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            builder.addZone(ArenaZone.rectangular("main", -halfX, -halfZ, halfX, halfZ));
        }

        return builder.build();
    }

    /**
     * Builds template-level floor (non-zone-aware).
     */
    private void buildTemplateFloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var floor = template.floor();
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        // For circular/ring shapes, we need to iterate in a square bounding box
        // and check each position against the shape
        int radius = Math.max(halfX, halfZ);
        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        for (int dx = -iterHalfX; dx < iterHalfX; dx++) {
            for (int dz = -iterHalfZ; dz < iterHalfZ; dz++) {
                // Check if position is within arena shape
                if (!isInArenaShape(dx, dz, template)) {
                    continue;
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;

                    String material = floor.material();

                    // Border check - shape-aware
                    if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                        boolean inBorder = isOnFloorBorder(dx, dz, template, floor.borderWidth());
                        if (inBorder) {
                            material = floor.borderMaterial();
                        }
                    }

                    placeBlock(worldX, worldY, worldZ, material, tx);
                }
            }
        }
    }

    /**
     * Checks if a position is on the floor border for the given shape.
     */
    private boolean isOnFloorBorder(int dx, int dz, ArenaTemplate template, int borderWidth) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;

        return switch (shape) {
            case RECTANGULAR -> {
                // Convert to 0-based coords for border check
                int localX = dx + halfX;
                int localZ = dz + halfZ;
                boolean inBorderX = localX < borderWidth || localX >= (halfX * 2) - borderWidth;
                boolean inBorderZ = localZ < borderWidth || localZ >= (halfZ * 2) - borderWidth;
                yield inBorderX || inBorderZ;
            }
            case CIRCULAR -> {
                int radius = Math.max(halfX, halfZ);
                int distSq = dx * dx + dz * dz;
                int innerBorderSq = (radius - borderWidth) * (radius - borderWidth);
                yield distSq >= innerBorderSq;
            }
            case RING -> {
                int outerRadius = Math.max(halfX, halfZ);
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                // Border at outer edge and inner edge
                int outerBorderSq = (outerRadius - borderWidth) * (outerRadius - borderWidth);
                int innerBorderSq = (innerRadius + borderWidth) * (innerRadius + borderWidth);
                yield distSq >= outerBorderSq || distSq <= innerBorderSq;
            }
        };
    }

    private void buildWalls(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var walls = template.walls();
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        if (shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            // Original rectangular wall logic
            int startX = originX - halfX;
            int startZ = originZ - halfZ;
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
                    for (int z = startZ + 1; z <= endZ - 1; z++) {
                        placeBlock(startX - t, worldY, z, walls.material(), tx);
                        placeBlock(endX + t, worldY, z, walls.material(), tx);
                    }
                }
            }
        } else {
            // Circular or ring wall - iterate bounding box and check border
            int radius = Math.max(halfX, halfZ);
            int outerExtent = radius + walls.thickness();

            for (int dy = 0; dy < walls.height(); dy++) {
                int worldY = walls.startY() + dy;

                for (int dx = -outerExtent; dx <= outerExtent; dx++) {
                    for (int dz = -outerExtent; dz <= outerExtent; dz++) {
                        if (isOnCircularBorder(dx, dz, template, walls.thickness())) {
                            placeBlock(originX + dx, worldY, originZ + dz, walls.material(), tx);
                        }
                    }
                }
            }
        }
    }

    private void buildCeiling(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var ceiling = template.ceiling();
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        // For circular/ring shapes, use radius as extent
        int radius = Math.max(halfX, halfZ);
        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        for (int dx = -iterHalfX; dx < iterHalfX; dx++) {
            for (int dz = -iterHalfZ; dz < iterHalfZ; dz++) {
                // Check if position is within arena shape
                if (!isInArenaShape(dx, dz, template)) {
                    continue;
                }

                for (int dy = 0; dy < ceiling.thickness(); dy++) {
                    placeBlock(originX + dx, ceiling.y() + dy, originZ + dz, ceiling.material(), tx);
                }
            }
        }
    }

    private void buildUnderfloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var underfloor = template.underfloor();
        var floor = template.floor();
        if (floor == null) return;

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        String material = underfloor.sameAsFloor() ? floor.material() : underfloor.material();

        // For circular/ring shapes, use radius as extent
        int radius = Math.max(halfX, halfZ);
        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        for (int dx = -iterHalfX; dx < iterHalfX; dx++) {
            for (int dz = -iterHalfZ; dz < iterHalfZ; dz++) {
                // Check if position is within arena shape
                if (!isInArenaShape(dx, dz, template)) {
                    continue;
                }

                for (int dy = 1; dy <= underfloor.depth(); dy++) {
                    placeBlock(originX + dx, floor.y() - dy, originZ + dz, material, tx);
                }
            }
        }
    }

    private void placeHazards(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
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
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int y = resolveY(hazard, template);

        // Estimate arena area based on shape
        int arenaArea;
        int radius = Math.max(halfX, halfZ);
        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            arenaArea = (int) (Math.PI * radius * radius);
        } else if (shape == ArenaTemplate.ArenaShape.RING) {
            Integer innerRadiusVal = template.ringInnerRadius();
            int innerRadius = innerRadiusVal != null ? innerRadiusVal : radius / 2;
            arenaArea = (int) (Math.PI * (radius * radius - innerRadius * innerRadius));
        } else {
            arenaArea = sizeX * sizeZ;
        }

        int total = (int) Math.round(arenaArea * Math.min(coverage, 0.5));
        String block = (String) hazard.params().getOrDefault("block", "minecraft:magma_block");
        int placed = 0;

        // Iterate in shape-aware manner
        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        // Deterministic grid placement every 2 blocks until coverage met
        outer:
        for (int dx = -iterHalfX; dx < iterHalfX; dx += 2) {
            for (int dz = -iterHalfZ; dz < iterHalfZ; dz += 2) {
                if (placed >= total) break outer;
                if (!isInArenaShape(dx, dz, template)) {
                    continue;
                }
                placeBlock(originX + dx, y, originZ + dz, block, tx);
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

    /**
     * Places lighting based on template configuration.
     * Supports both template-level lighting and zone-aware lighting.
     *
     * <p>If zones are enabled in the template, lighting is placed per-zone based on
     * each zone's environment lighting configuration. Otherwise, uses template-level lighting.</p>
     */
    private void placeLighting(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        // Check if zone-aware lighting is needed
        var zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            placeZoneAwareLighting(template, originX, originZ, zoneSettings, tx);
            return;
        }

        // Fall back to template-level lighting
        placeTemplateLighting(template, originX, originZ, tx);
    }

    /**
     * Places zone-aware lighting based on zone definitions.
     * Each zone can have different lighting requirements.
     */
    private void placeZoneAwareLighting(ArenaTemplate template, int originX, int originZ,
                                        ArenaTemplate.ZoneSettings zoneSettings, BuildTransaction tx) {
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int floorY = template.floor() != null ? template.floor().y() : 64;

        int totalLights = 0;

        // Build ZoneLayout from zone definitions for bounds checking
        ZoneLayout layout = buildZoneLayoutFromSettings(zoneSettings, halfX, halfZ);

        for (var zoneDef : zoneSettings.zones()) {
            // Convert ZoneDefinition to ZoneEnvironment for unified handling
            ZoneEnvironment environment = createZoneEnvironment(zoneDef);

            // Skip if no lighting requirements
            if (!environment.lighting().placeLightSources() && environment.lighting().blockLight() <= 0) {
                continue;
            }

            // Find the corresponding ArenaZone for bounds
            ArenaZone zone = layout.zones().stream()
                .filter(z -> z.name().equals(zoneDef.name()))
                .findFirst()
                .orElse(null);

            if (zone == null) {
                LOGGER.warn("Zone '{}' not found in layout, skipping lighting", zoneDef.name());
                continue;
            }

            // Place lights within this zone's bounds using environment config
            int zoneLights = placeZoneLighting(zone, originX, originZ, floorY,
                environment.lighting().blockLight(), template, tx);
            totalLights += zoneLights;

            LOGGER.debug("Placed {} lights in zone '{}' (target level: {}, env: {})",
                zoneLights, zoneDef.name(), environment.lighting().blockLight(),
                environment.hasRequirements() ? "custom" : "default");
        }

        if (totalLights > 0) {
            telemetry.emit("arena.lighting.zone_aware", Map.of(
                "templateId", template.id(),
                "totalLights", totalLights,
                "zoneCount", zoneSettings.zones().size()
            ));
        }
    }

    /**
     * Creates a ZoneEnvironment from an ArenaTemplate.ZoneDefinition.
     * Converts raw definition data into the structured domain model.
     */
    private ZoneEnvironment createZoneEnvironment(ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef) {
        // Parse biome if present
        java.util.Optional<net.minecraft.resources.ResourceLocation> biome = java.util.Optional.empty();
        String biomeStr = zoneDef.biome();
        if (biomeStr != null && !biomeStr.isEmpty()) {
            biome = java.util.Optional.of(net.minecraft.resources.ResourceLocation.parse(biomeStr));
        }

        // Parse floor material if present
        java.util.Optional<net.minecraft.resources.ResourceLocation> floorMaterial = java.util.Optional.empty();
        String floorStr = zoneDef.floorMaterial();
        if (floorStr != null && !floorStr.isEmpty()) {
            floorMaterial = java.util.Optional.of(net.minecraft.resources.ResourceLocation.parse(floorStr));
        }

        // Build lighting config
        Integer skyLightVal = zoneDef.skyLight();
        Integer blockLightVal = zoneDef.blockLight();
        int skyLight = skyLightVal != null ? skyLightVal.intValue() : 15;
        int blockLight = blockLightVal != null ? blockLightVal.intValue() : 0;
        boolean placeSources = blockLight > 0;
        ZoneEnvironment.LightingConfig lighting = new ZoneEnvironment.LightingConfig(skyLight, blockLight, placeSources);

        // Parse time config
        ZoneEnvironment.TimeConfig time = ZoneEnvironment.TimeConfig.ANY;
        String timeStr = zoneDef.time();
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                time = ZoneEnvironment.TimeConfig.valueOf(timeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown time config '{}' for zone '{}', using ANY", timeStr, zoneDef.name());
            }
        }

        return new ZoneEnvironment(biome, floorMaterial, lighting, time);
    }

    /**
     * Builds a ZoneLayout from template zone settings for bounds checking.
     */
    private ZoneLayout buildZoneLayoutFromSettings(ArenaTemplate.ZoneSettings settings, int halfX, int halfZ) {
        var zones = settings.zones();
        int zoneCount = zones.size();

        // Simple strategy: divide arena based on zone count
        ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

        if (zoneCount == 1) {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            var def = zones.get(0);
            builder.addZone(ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ));
        } else if (zoneCount == 2) {
            // Vertical split
            builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
            var def0 = zones.get(0);
            var def1 = zones.get(1);
            builder.addZone(ArenaZone.rectangular(def0.name(), -halfX, -halfZ, 0, halfZ));
            builder.addZone(ArenaZone.rectangular(def1.name(), 0, -halfZ, halfX, halfZ));
        } else if (zoneCount <= 4) {
            // Quadrant layout
            builder.strategy(ZoneLayout.LayoutStrategy.QUADRANT);
            if (zones.size() >= 1) builder.addZone(ArenaZone.rectangular(zones.get(0).name(), 0, -halfZ, halfX, 0));
            if (zones.size() >= 2) builder.addZone(ArenaZone.rectangular(zones.get(1).name(), -halfX, -halfZ, 0, 0));
            if (zones.size() >= 3) builder.addZone(ArenaZone.rectangular(zones.get(2).name(), -halfX, 0, 0, halfZ));
            if (zones.size() >= 4) builder.addZone(ArenaZone.rectangular(zones.get(3).name(), 0, 0, halfX, halfZ));
        } else {
            // Default: single zone for remaining
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            builder.addZone(ArenaZone.rectangular("main", -halfX, -halfZ, halfX, halfZ));
        }

        return builder.build();
    }

    /**
     * Places lighting within a specific zone's bounds.
     */
    private int placeZoneLighting(ArenaZone zone, int originX, int originZ, int floorY,
                                  int targetLight, ArenaTemplate template, BuildTransaction tx) {
        var bounds = zone.bounds();

        // Calculate light source spacing based on target light level
        // Max spacing increased to 20 for low light targets with weaker sources
        int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

        // Determine Y level for light placement
        int lightY = floorY + 1;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            // Account for ceiling thickness to place light below the ceiling blocks
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.walls() != null && template.walls().enabled()) {
            lightY = template.walls().startY() + template.walls().height() - 2;
        }

        String lightBlock = selectLightBlock(targetLight);
        int placedCount = 0;

        // Place lights in grid within zone bounds
        int startX = originX + bounds.x1();
        int endX = originX + bounds.x2();
        int startZ = originZ + bounds.z1();
        int endZ = originZ + bounds.z2();

        for (int x = startX + spacing / 2; x < endX; x += spacing) {
            for (int z = startZ + spacing / 2; z < endZ; z += spacing) {
                // Verify position is within zone (important for non-rectangular zones)
                if (zone.contains(x - originX, z - originZ)) {
                    placeBlock(x, lightY, z, lightBlock, tx);
                    placedCount++;
                }
            }
        }

        return placedCount;
    }

    /**
     * Places template-level lighting (non-zone-aware).
     * Includes explicit light sources and ambient grid lighting.
     */
    private void placeTemplateLighting(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var lighting = template.lighting();
        if (lighting == null) return;

        int placedLights = 0;
        int skippedLights = 0;

        // Calculate arena bounds for validation
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        // 1. Place explicit light sources from template
        // Note: Y coordinate in LightSource is relative to floor if floor exists
        if (lighting.lightSources() != null && !lighting.lightSources().isEmpty()) {
            for (var lightSource : lighting.lightSources()) {
                int[] pos = lightSource.pos();
                if (pos != null && pos.length == 3) {
                    // Validate bounds before placement (pos is relative to origin)
                    int relX = pos[0];
                    int relZ = pos[2];
                    if (relX < -halfX || relX >= halfX || relZ < -halfZ || relZ >= halfZ) {
                        LOGGER.warn("Light source at [{}, {}, {}] is outside arena bounds, skipping",
                            pos[0], pos[1], pos[2]);
                        skippedLights++;
                        continue;
                    }

                    int x = originX + pos[0];
                    int y = pos[1];
                    int z = originZ + pos[2];

                    // Y is floor-relative when floor exists (pos[1] is offset from floor)
                    if (template.floor() != null) {
                        y = template.floor().y() + pos[1];
                    }

                    placeBlock(x, y, z, lightSource.block(), tx);
                    placedLights++;
                }
            }
        }

        if (skippedLights > 0) {
            LOGGER.warn("Skipped {} light sources outside arena bounds for template '{}'",
                skippedLights, template.id());
        }

        // 2. Place ambient grid lighting if enabled
        if (lighting.ambientLight() && lighting.blockLight() > 0) {
            placedLights += placeAmbientLighting(template, originX, originZ, lighting.blockLight(), tx);
        }

        if (placedLights > 0) {
            LOGGER.debug("Placed {} light sources for template '{}'", placedLights, template.id());
            telemetry.emit("arena.lighting.placed", Map.of(
                "templateId", template.id(),
                "count", placedLights,
                "ambientEnabled", lighting.ambientLight(),
                "targetBlockLight", lighting.blockLight()
            ));
        }
    }

    /**
     * Places ambient lighting in a grid pattern to achieve target light level.
     * Uses sea lanterns (light level 15) spaced to maintain target brightness.
     */
    private int placeAmbientLighting(ArenaTemplate template, int originX, int originZ, int targetLight, BuildTransaction tx) {
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);

        // Determine light Y level with fallback chain: floor -> ceiling -> walls -> default
        int lightY;
        if (template.floor() != null) {
            lightY = template.floor().y() + 1; // 1 block above floor for ground-level lighting
        } else if (template.ceiling() != null && template.ceiling().enabled()) {
            // No floor - use ceiling as anchor (account for thickness)
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.walls() != null && template.walls().enabled()) {
            // No floor or ceiling - use wall height
            lightY = template.walls().startY() + Math.max(1, template.walls().height() / 2);
        } else {
            // No structural reference - use reasonable default (Y=65)
            lightY = 65;
        }

        // Override with ceiling mount if ceiling exists (preferred position)
        if (template.ceiling() != null && template.ceiling().enabled()) {
            // Account for ceiling thickness to place light below the ceiling blocks
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.floor() != null && template.walls() != null && template.walls().enabled()) {
            // Place at wall height minus 2 if no ceiling but floor and walls exist
            lightY = template.walls().startY() + template.walls().height() - 2;
        }

        // Calculate light source spacing based on target light level
        // Light decreases by 1 per block, so for level 15 source to maintain level N,
        // spacing should be approximately (15 - N) * 2 to ensure overlap
        // Max spacing increased to 20 to support low light targets with weaker sources
        int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

        // Determine light block based on target level
        String lightBlock = selectLightBlock(targetLight);

        int placedCount = 0;
        for (int dx = spacing / 2; dx < sizeX; dx += spacing) {
            for (int dz = spacing / 2; dz < sizeZ; dz += spacing) {
                int x = startX + dx;
                int z = startZ + dz;

                placeBlock(x, lightY, z, lightBlock, tx);
                placedCount++;
            }
        }

        return placedCount;
    }

    /**
     * Selects appropriate light-emitting block based on target light level.
     * Uses weaker sources for lower target levels to avoid over-lighting.
     */
    private String selectLightBlock(int targetLight) {
        // Use appropriate light sources to match target levels
        // Light level of source affects achievable minimum at spacing midpoints
        if (targetLight >= 14) {
            return "minecraft:sea_lantern"; // Light level 15
        } else if (targetLight >= 11) {
            return "minecraft:glowstone"; // Light level 15
        } else if (targetLight >= 8) {
            return "minecraft:lantern"; // Light level 15
        } else if (targetLight >= 5) {
            return "minecraft:soul_lantern"; // Light level 10 - better for mid-low levels
        } else if (targetLight >= 1) {
            return "minecraft:redstone_torch"; // Light level 7 - for very low levels
        } else {
            // Target is 0 or negative - use weakest available
            return "minecraft:redstone_torch"; // Light level 7
        }
    }

    private void placeBlock(int x, int y, int z, String material, BuildTransaction tx) {
        maybeCheckPerformance(tx);
        long packedPos = CompactBlockTracker.pack(x, y, z);
        int previousStateId = blockPlacer.placeBlock(x, y, z, material);
        tx.trackBlock(packedPos, previousStateId);
    }

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
        return measureResiduals(template, originX, originY, originZ, mcPlacer.level());
    }

    /**
     * Computes residual metrics using MetricsCompatibilityLayer with a ServerLevel.
     */
    private ResidualMetrics measureResiduals(ArenaTemplate template, int originX, int originY, int originZ, net.minecraft.server.level.ServerLevel level) {
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

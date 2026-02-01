package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.debug.DiagnosticLogger;
import com.devmod.arena.budget.BackpressureManager;
import com.devmod.arena.budget.BuildBudget;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.event.TemplateEventDispatcher;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;
import com.devmod.arena.performance.PerformanceBudgetEnforcer.PerformanceAction;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

public class AsyncArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncArenaBuilder.class);

    // P2: Queue depth limits for backpressure
    private static final int DEFAULT_MAX_QUEUE_DEPTH = 5;
    private static final int DEFAULT_CRITICAL_QUEUE_DEPTH = 10;
    private static final double SEVERE_PRESSURE_MSPT = 45.0;

    private final ArenaTelemetry telemetry;
    private final ArenaBuilder.BlockPlacer blockPlacer;
    private final BackpressureManager backpressure;
    private final Supplier<Double> msptSupplier;
    private final TemplateEventDispatcher eventDispatcher;
    private final InstanceOnlyGate instanceGate;
    private final MsptMonitor msptMonitor;
    private final PerformanceBudgetEnforcer performanceEnforcer;
    private boolean performanceTrackingActive = false;

    // P2: Queue depth configuration
    private volatile int maxQueueDepth = DEFAULT_MAX_QUEUE_DEPTH;
    private volatile int criticalQueueDepth = DEFAULT_CRITICAL_QUEUE_DEPTH;

    // Active builds
    private final Queue<AsyncBuild> activeBuildQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, AsyncBuild> buildsByArenaId = new ConcurrentHashMap<>();

    // Statistics
    private long totalBlocksPlaced = 0;
    private long totalBuildsCompleted = 0;
    private long totalBuildsFailed = 0;
    private long totalBuildsRejectedQueueFull = 0;
    private long totalBuildsCancelledPressure = 0;

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            ArenaBuilder.BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier) {
        this(telemetry, blockPlacer, msptSupplier, BackpressureManager.createOptimized(), null);
    }

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            ArenaBuilder.BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier,
            BackpressureManager backpressure) {
        this(telemetry, blockPlacer, msptSupplier, backpressure, null);
    }

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            ArenaBuilder.BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier,
            ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this(telemetry, blockPlacer, msptSupplier, BackpressureManager.createOptimized(), configSnapshot);
    }

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            ArenaBuilder.BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier,
            BackpressureManager backpressure,
            ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.msptSupplier = msptSupplier;
        this.backpressure = backpressure;
        this.eventDispatcher = TemplateEventDispatcher.getInstance();
        this.instanceGate = configSnapshot != null ? new InstanceOnlyGate(configSnapshot, telemetry) : null;
        this.msptMonitor = new MsptMonitor();
        this.performanceEnforcer = new PerformanceBudgetEnforcer(msptMonitor);
    }

    /**
     * DD44: Submits a build and returns a CompletableFuture for the result.
     *
     * @param arenaId Arena ID
     * @param template Template to build
     * @param originX World X
     * @param originY World Y
     * @param originZ World Z
     * @return CompletableFuture that completes when build finishes
     */
    public CompletableFuture<AsyncBuildResult> submitBuildAsync(
            UUID arenaId,
            ArenaTemplate template,
            int originX,
            int originY,
            int originZ) {

        CompletableFuture<AsyncBuildResult> future = new CompletableFuture<>();

        boolean submitted = submitBuild(arenaId, template, originX, originY, originZ, result -> {
            if (result.success()) {
                future.complete(result);
            } else {
                future.completeExceptionally(new BuildException(result.errorMessage(), result));
            }
        });

        if (!submitted) {
            future.completeExceptionally(new IllegalStateException(
                "Build already in progress for arena " + arenaId));
        }

        return future;
    }

    /**
     * Submits a build to be executed asynchronously across ticks.
     *
     * @param arenaId Arena ID
     * @param template Template to build
     * @param originX World X
     * @param originY World Y
     * @param originZ World Z
     * @param callback Called when build completes or fails
     * @return true if submitted, false if already building this arena
     */
    public boolean submitBuild(
            UUID arenaId,
            ArenaTemplate template,
            int originX,
            int originY,
            int originZ,
            Consumer<AsyncBuildResult> callback) {
        DiagnosticLogger.arena("submitBuild: arenaId=%s, template=%s, origin=(%d,%d,%d)",
            arenaId, template.id(), originX, originY, originZ);

        // Instance-only gate: block overworld builds when configured and using block placer with level access
        if (instanceGate != null) {
            net.minecraft.server.level.ServerLevel gateLevel = null;
            if (blockPlacer instanceof com.devmod.arena.integration.BatchBlockPlacer batchPlacer) {
                gateLevel = batchPlacer.level();
            } else if (blockPlacer instanceof com.devmod.arena.integration.MinecraftBlockPlacer mcPlacer) {
                gateLevel = mcPlacer.level();
            }
            if (gateLevel != null) {
                InstanceOnlyGate.Result gateResult = instanceGate.check(gateLevel, "AsyncArenaBuilder.submitBuild");
                if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                    LOGGER.error("Instance-only gate blocked async build for template {} in {}", template.id(), gateLevel.dimension().location());
                    callback.accept(AsyncBuildResult.failure(arenaId, template.id(), "instance_only_blocked", 0, 0));
                    return false;
                } else if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                    LOGGER.warn("Instance-only gate: debug-only build for template {} in {}", template.id(), gateLevel.dimension().location());
                }
            }
        }

        if (buildsByArenaId.containsKey(arenaId)) {
            LOGGER.warn("Build already in progress for arena {}", arenaId);
            return false;
        }

        String unsupportedReason = validateTemplateForAsync(template);
        if (unsupportedReason != null) {
            LOGGER.warn("Async build rejected for template {}: {}", template.id(), unsupportedReason);
            telemetry.emit("arena.async_build.unsupported_template", Map.of(
                "arenaId", arenaId.toString(),
                "templateId", template.id(),
                "reason", unsupportedReason
            ));
            callback.accept(AsyncBuildResult.failure(arenaId, template.id(),
                "unsupported_template: " + unsupportedReason, 0, 0));
            return false;
        }

        // P2: Queue depth check - reject if queue is at max capacity
        int currentQueueSize = activeBuildQueue.size();
        if (currentQueueSize >= maxQueueDepth) {
            totalBuildsRejectedQueueFull++;
            LOGGER.warn("Build queue full ({}/{}), rejecting build for template {}",
                currentQueueSize, maxQueueDepth, template.id());
            telemetry.emit("arena.async_build.rejected_queue_full", Map.of(
                "arenaId", arenaId.toString(),
                "templateId", template.id(),
                "queueSize", currentQueueSize,
                "maxQueueDepth", maxQueueDepth
            ));
            callback.accept(AsyncBuildResult.failure(arenaId, template.id(), "queue_full", 0, 0));
            return false;
        }

        AsyncBuild build = AsyncBuild.create(arenaId, template, originX, originY, originZ, callback);

        activeBuildQueue.add(build);
        buildsByArenaId.put(arenaId, build);

        LOGGER.info("Submitted async build for arena {} (template: {})", arenaId, template.id());
        telemetry.emit("arena.async_build.submitted", Map.of(
            "arenaId", arenaId.toString(),
            "templateId", template.id(),
            "queueSize", activeBuildQueue.size()
        ));

        // DD13: Emit BuildStarted event
        eventDispatcher.emitBuildStarted(
            template.id(), arenaId, null, build.placements.size());

        return true;
    }

    /**
     * Called each server tick to process builds.
     * Register this with the server tick event.
     */
    public void onTick() {
        if (activeBuildQueue.isEmpty()) {
            if (performanceTrackingActive) {
                finishPerformanceTracking();
            }
            return;
        }

        double currentMspt = msptSupplier.get();
        startPerformanceTrackingIfNeeded(currentMspt);
        msptMonitor.recordSample(currentMspt);
        performanceEnforcer.captureBaselineIfNeeded();

        PerformanceAction action = performanceEnforcer.checkPerformance();
        double tps = currentMspt > 0 ? Math.min(1000.0 / currentMspt, 20.0) : 20.0;
        double buildImpact = performanceEnforcer.getBuildImpact();

        if (buildImpact > PerformanceBudgetEnforcer.DEFAULT_BUILD_IMPACT_THRESHOLD
            || tps < PerformanceBudgetEnforcer.DEFAULT_TPS_THRESHOLD) {
            telemetry.emit("arena.build.perf.degraded", Map.of(
                "mspt", currentMspt,
                "tps", tps,
                "baselineMspt", performanceEnforcer.getBaseline(),
                "buildImpactMs", buildImpact,
                "confidence", msptMonitor.getCurrentSample().confidenceScore(),
                "action", action.name(),
                "queueSize", activeBuildQueue.size(),
                "activeBuilds", buildsByArenaId.size()
            ));
        }

        int blocksThisTick = backpressure.update(currentMspt);

        // P2: Queue depth shedding under severe pressure
        int queueSize = activeBuildQueue.size();
        if (currentMspt > SEVERE_PRESSURE_MSPT && queueSize > criticalQueueDepth) {
            // Cancel oldest builds to reduce queue to critical depth
            int buildsToCancel = queueSize - criticalQueueDepth;
            LOGGER.warn("Severe pressure (MSPT={}, queue={}): shedding {} oldest builds",
                String.format("%.1f", currentMspt), queueSize, buildsToCancel);

            for (int i = 0; i < buildsToCancel && !activeBuildQueue.isEmpty(); i++) {
                AsyncBuild oldest = activeBuildQueue.peek();
                if (oldest != null) {
                    cancelBuild(oldest.arenaId, null, "queue_pressure_shedding");
                    totalBuildsCancelledPressure++;
                }
            }

            telemetry.emit("arena.async_build.queue_shedding", Map.of(
                "mspt", currentMspt,
                "queueSizeBefore", queueSize,
                "queueSizeAfter", activeBuildQueue.size(),
                "buildsCancelled", buildsToCancel
            ));
        }

        if (action == PerformanceAction.ABORT) {
            telemetry.emit("arena.build.aborted.performance", Map.of(
                "mspt", currentMspt,
                "tps", tps,
                "baselineMspt", performanceEnforcer.getBaseline(),
                "buildImpactMs", buildImpact,
                "queueSize", activeBuildQueue.size(),
                "activeBuilds", buildsByArenaId.size()
            ));
            abortAllBuilds("Performance budget exceeded");
            finishPerformanceTracking();
            return;
        }

        if (action == PerformanceAction.PAUSE) {
            performanceEnforcer.recordPauseApplied();
            telemetry.emit("arena.build.backpressure", Map.of(
                "mspt", currentMspt,
                "tps", tps,
                "buildImpactMs", buildImpact,
                "action", action.name(),
                "previousBlocksPerTick", blocksThisTick,
                "blocksPerTick", 0,
                "queueSize", activeBuildQueue.size(),
                "activeBuilds", buildsByArenaId.size()
            ));
            return;
        }

        if (action == PerformanceAction.THROTTLE) {
            int previousBlocks = blocksThisTick;
            backpressure.setBlocksPerTick(previousBlocks / 2);
            blocksThisTick = backpressure.getCurrentBlocksPerTick();
            telemetry.emit("arena.build.backpressure", Map.of(
                "mspt", currentMspt,
                "tps", tps,
                "buildImpactMs", buildImpact,
                "action", action.name(),
                "previousBlocksPerTick", previousBlocks,
                "blocksPerTick", blocksThisTick,
                "queueSize", activeBuildQueue.size(),
                "activeBuilds", buildsByArenaId.size()
            ));
        }

        // Process builds round-robin
        int blocksRemaining = blocksThisTick;
        int buildsProcessed = 0;

        while (blocksRemaining > 0 && !activeBuildQueue.isEmpty()) {
            AsyncBuild build = activeBuildQueue.poll();
            if (build == null) break;

            try {
                int blocksPlaced = processBuild(build, blocksRemaining);
                blocksRemaining -= blocksPlaced;
                totalBlocksPlaced += blocksPlaced;

                if (!build.isComplete()) {
                    // Re-queue for next tick
                    activeBuildQueue.add(build);
                } else {
                    // Build complete
                    completeBuild(build, null);
                }

            } catch (Exception e) {
                // Build failed
                completeBuild(build, e);
            }

            buildsProcessed++;

            // Prevent infinite loop if one build keeps re-queuing
            if (buildsProcessed >= buildsByArenaId.size()) {
                break;
            }
        }

        if (activeBuildQueue.isEmpty() && performanceTrackingActive) {
            finishPerformanceTracking();
        }
    }

    private void startPerformanceTrackingIfNeeded(double seedMspt) {
        if (performanceTrackingActive) {
            return;
        }
        msptMonitor.reset();
        for (int i = 0; i < 5; i++) {
            msptMonitor.recordSample(seedMspt);
        }
        performanceEnforcer.captureBaseline();
        performanceEnforcer.beginBuild();
        performanceTrackingActive = true;
    }

    private void finishPerformanceTracking() {
        PerformanceBudgetEnforcer.BuildPerformanceReport report = performanceEnforcer.endBuild();
        telemetry.emit("arena.build.performance_summary", Map.of(
            "buildType", "async",
            "baselineMspt", report.baseline(),
            "averageMspt", report.averageMspt(),
            "peakMspt", report.peakMspt(),
            "maxBuildImpactMs", report.maxBuildImpact(),
            "pauseCount", report.pauseCount(),
            "throttleCount", report.throttleCount(),
            "aborted", report.wasAborted(),
            "durationMs", report.buildDuration().toMillis()
        ));
        performanceTrackingActive = false;
    }

    private void abortAllBuilds(String reason) {
        List<AsyncBuild> builds = new ArrayList<>(buildsByArenaId.values());
        activeBuildQueue.clear();
        for (AsyncBuild build : builds) {
            completeBuild(build, new RuntimeException(reason));
        }
    }

    /**
     * Processes a single build, placing up to maxBlocks.
     */
    private int processBuild(AsyncBuild build, int maxBlocks) {
        int blocksPlaced = 0;

        while (blocksPlaced < maxBlocks && build.hasMoreWork()) {
            BlockPlacement placement = build.nextPlacement();
            if (placement != null) {
                int previousStateId = blockPlacer.placeBlock(
                    placement.x(), placement.y(), placement.z(), placement.material());
                build.transaction.trackBlock(
                    CompactBlockTracker.pack(placement.x(), placement.y(), placement.z()),
                    previousStateId
                );
                blocksPlaced++;
                build.budget.trackBlock();
            }
        }

        // Check time budget
        build.budget.checkTime();

        return blocksPlaced;
    }

    /**
     * Completes a build (success or failure).
     */
    private void completeBuild(AsyncBuild build, Exception error) {
        buildsByArenaId.remove(build.arenaId);
        DiagnosticLogger.arena("completeBuild: arenaId=%s, template=%s, success=%s, blocks=%d, durationMs=%d",
            build.arenaId, build.template.id(), error == null,
            build.budget.getCurrentBlocks(), build.budget.getElapsedMs());

        if (error != null) {
            totalBuildsFailed++;
            LOGGER.error("Async build failed for arena {}: {}", build.arenaId, error.getMessage());

            // Rollback - delegates to BlockPlacer for blocks, entity/chunk ops require Minecraft integration
            build.transaction.rollback(
                blockPlacer::revertBlock,
                uuid -> { LOGGER.debug("Entity removal requested: {}", uuid); return true; },
                pos -> LOGGER.debug("Chunk release requested: {}", pos)
            );

            build.callback.accept(AsyncBuildResult.failure(
                build.arenaId, build.template.id(), error.getMessage(),
                build.budget.getCurrentBlocks(), build.budget.getElapsedMs()
            ));

            telemetry.emit("arena.async_build.failed", Map.of(
                "arenaId", build.arenaId.toString(),
                "templateId", build.template.id(),
                "error", error.getMessage(),
                "blocksPlaced", build.budget.getCurrentBlocks(),
                "durationMs", build.budget.getElapsedMs()
            ));

            // DD13: Emit BuildFailed event
            eventDispatcher.emitBuildFailed(
                build.template.id(), build.arenaId, error.getMessage(), error,
                build.budget.getElapsedMs(), true);

        } else {
            totalBuildsCompleted++;

            // P2: Flush BatchBlockPlacer before commit to ensure all blocks are placed
            if (blockPlacer instanceof com.devmod.arena.integration.BatchBlockPlacer batchPlacer) {
                batchPlacer.flush();
            }

            build.transaction.commit();

            LOGGER.info("Async build completed for arena {}: {} blocks in {}ms",
                build.arenaId, build.budget.getCurrentBlocks(), build.budget.getElapsedMs());

            build.callback.accept(AsyncBuildResult.success(
                build.arenaId, build.template.id(),
                build.budget.getCurrentBlocks(), build.budget.getElapsedMs()
            ));

            telemetry.emit("arena.async_build.completed", Map.of(
                "arenaId", build.arenaId.toString(),
                "templateId", build.template.id(),
                "blocksPlaced", build.budget.getCurrentBlocks(),
                "durationMs", build.budget.getElapsedMs()
            ));

            // DD13: Emit BuildCompleted event
            eventDispatcher.emitBuildCompleted(
                build.template.id(), build.arenaId,
                build.budget.getCurrentBlocks(), 0, build.budget.getElapsedMs());

            // P1: Clear placer cache after successful commit to prevent memory leak
            if (blockPlacer instanceof com.devmod.arena.integration.BatchBlockPlacer bp) {
                if (LOGGER.isDebugEnabled()) {
                    com.devmod.arena.integration.BatchBlockPlacer.PlacementStats stats = bp.getStats();
                    int trackedStates = bp.getTrackedStateCount();
                    LOGGER.debug("[AsyncArenaBuilder] BatchBlockPlacer stats: placed={}, flushes={}, cacheHitRate={:.2f}%, trackedStates={}",
                        stats.totalPlaced(), stats.batchFlushes(), stats.cacheHitRate() * 100, trackedStates);
                }
                bp.clearCache();
            } else if (blockPlacer instanceof com.devmod.arena.integration.MinecraftBlockPlacer mcPlacer) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[AsyncArenaBuilder] MinecraftBlockPlacer trackedStates={}", mcPlacer.getTrackedStateCount());
                }
                mcPlacer.clearCache();
            }
        }
    }

    /**
     * Cancels a build in progress.
     */
    public boolean cancelBuild(UUID arenaId) {
        return cancelBuild(arenaId, null, "user-requested");
    }

    /**
     * Cancels a build in progress with tracking.
     *
     * @param arenaId Arena ID to cancel
     * @param cancelledBy UUID of player who cancelled (null for system)
     * @param reason Reason for cancellation
     * @return true if cancelled, false if no build found
     */
    public boolean cancelBuild(UUID arenaId, UUID cancelledBy, String reason) {
        AsyncBuild build = buildsByArenaId.remove(arenaId);
        if (build != null) {
            activeBuildQueue.remove(build);

            int blocksPlaced = build.budget.getCurrentBlocks();
            long durationMs = build.budget.getElapsedMs();

            // Rollback with proper logging
            build.transaction.rollback(
                blockPlacer::revertBlock,
                uuid -> { LOGGER.debug("Entity removal requested during cancel: {}", uuid); return true; },
                pos -> LOGGER.debug("Chunk release requested during cancel: {}", pos)
            );

            LOGGER.info("Cancelled build for arena {}", arenaId);

            // DD13: Emit BuildCancelled event
            eventDispatcher.emitBuildCancelled(
                build.template.id(), arenaId, cancelledBy, reason, blocksPlaced, durationMs);

            return true;
        }
        return false;
    }

    // === Getters ===

    public int getQueueSize() {
        return activeBuildQueue.size();
    }

    public int getActiveBuilds() {
        return buildsByArenaId.size();
    }

    private static String validateTemplateForAsync(ArenaTemplate template) {
        if (template.structureNbt() != null) {
            return "structureNbt not supported in async builder";
        }
        if (template.hazards() != null) {
            for (ArenaTemplate.Hazard hazard : template.hazards()) {
                if ("custom".equals(hazard.type())) {
                    return "custom hazards not supported in async builder";
                }
            }
        }
        return null;
    }

    private static int getSizeX(ArenaTemplate template) {
        Integer sizeXVal = template.sizeX();
        return sizeXVal != null ? sizeXVal : template.size();
    }

    private static int getSizeZ(ArenaTemplate template) {
        Integer sizeZVal = template.sizeZ();
        return sizeZVal != null ? sizeZVal : template.size();
    }

    private static int minOffset(int size) {
        return -size / 2;
    }

    private static int maxOffset(int size) {
        return size - (size / 2) - 1;
    }

    private static boolean isInArenaShape(int dx, int dz, ArenaTemplate template, int sizeX, int sizeZ) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int minX = minOffset(sizeX);
        int maxX = maxOffset(sizeX);
        int minZ = minOffset(sizeZ);
        int maxZ = maxOffset(sizeZ);

        return switch (shape) {
            case RECTANGULAR -> dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ;
            case CIRCULAR -> {
                int radius = Math.max(sizeX, sizeZ) / 2;
                yield dx * dx + dz * dz <= radius * radius;
            }
            case RING -> {
                int outerRadius = Math.max(sizeX, sizeZ) / 2;
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                yield distSq <= outerRadius * outerRadius && distSq >= innerRadius * innerRadius;
            }
        };
    }

    public BackpressureManager.BackpressureStatus getBackpressureStatus() {
        return backpressure.getStatus();
    }

    public long getTotalBlocksPlaced() {
        return totalBlocksPlaced;
    }

    public long getTotalBuildsCompleted() {
        return totalBuildsCompleted;
    }

    public long getTotalBuildsFailed() {
        return totalBuildsFailed;
    }

    // P2: Queue depth backpressure stats and configuration

    public long getTotalBuildsRejectedQueueFull() {
        return totalBuildsRejectedQueueFull;
    }

    public long getTotalBuildsCancelledPressure() {
        return totalBuildsCancelledPressure;
    }

    public int getMaxQueueDepth() {
        return maxQueueDepth;
    }

    public void setMaxQueueDepth(int maxQueueDepth) {
        this.maxQueueDepth = Math.max(1, maxQueueDepth);
    }

    public int getCriticalQueueDepth() {
        return criticalQueueDepth;
    }

    public void setCriticalQueueDepth(int criticalQueueDepth) {
        this.criticalQueueDepth = Math.max(1, criticalQueueDepth);
    }

    /**
     * Returns comprehensive queue status for monitoring.
     */
    public QueueStatus getQueueStatus() {
        return new QueueStatus(
            activeBuildQueue.size(),
            maxQueueDepth,
            criticalQueueDepth,
            totalBuildsRejectedQueueFull,
            totalBuildsCancelledPressure,
            backpressure.isUnderPressure()
        );
    }

    /**
     * Queue status snapshot for monitoring.
     */
    public record QueueStatus(
        int currentSize,
        int maxDepth,
        int criticalDepth,
        long rejectedQueueFull,
        long cancelledPressure,
        boolean underPressure
    ) {
        public boolean isAtCapacity() {
            return currentSize >= maxDepth;
        }

        public boolean isOverCritical() {
            return currentSize > criticalDepth;
        }

        public double utilizationRatio() {
            return maxDepth > 0 ? (double) currentSize / maxDepth : 0.0;
        }
    }

    // === Supporting Types ===

    /**
     * Represents a build in progress.
     */
    private static class AsyncBuild {
        final UUID arenaId;
        final ArenaTemplate template;
        final Consumer<AsyncBuildResult> callback;
        final BuildTransaction transaction;
        final BuildBudget budget;
        final List<BlockPlacement> placements;
        int placementIndex = 0;

        static AsyncBuild create(UUID arenaId, ArenaTemplate template, int originX, int originY, int originZ,
                                 Consumer<AsyncBuildResult> callback) {
            List<BlockPlacement> placements = computePlacements(template, originX, originY, originZ);
            return new AsyncBuild(arenaId, template, callback, placements);
        }

        private AsyncBuild(UUID arenaId, ArenaTemplate template, Consumer<AsyncBuildResult> callback,
                           List<BlockPlacement> placements) {
            this.arenaId = arenaId;
            this.template = template;
            this.callback = callback;
            this.transaction = new BuildTransaction(template.id());
            this.budget = BuildBudget.defaults();
            this.placements = placements;

            budget.start();
        }

        boolean hasMoreWork() {
            return placementIndex < placements.size();
        }

        boolean isComplete() {
            return placementIndex >= placements.size();
        }

        BlockPlacement nextPlacement() {
            if (placementIndex < placements.size()) {
                return placements.get(placementIndex++);
            }
            return null;
        }

        @SuppressWarnings("UnusedVariable")
        private static List<BlockPlacement> computePlacements(
                ArenaTemplate template, int originX, int originY, int originZ) {
            List<BlockPlacement> placements = new ArrayList<>();

            ArenaTemplate.BuildSettings.Order buildOrder = resolveBuildOrder(template);
            if (buildOrder == ArenaTemplate.BuildSettings.Order.WALLS_FIRST) {
                if (template.walls() != null && template.walls().enabled()) {
                    buildWalls(template, originX, originZ, placements);
                }
                if (template.floor() != null) {
                    buildFloor(template, originX, originZ, placements);
                }
            } else {
                if (template.floor() != null) {
                    buildFloor(template, originX, originZ, placements);
                }
                if (template.walls() != null && template.walls().enabled()) {
                    buildWalls(template, originX, originZ, placements);
                }
            }

            ArenaTemplate.TerrainSettings terrainSettings = template.terrainSettings();
            boolean isDynamicTerrain = terrainSettings != null
                && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC;

            if (template.ceiling() != null && template.ceiling().enabled() && !isDynamicTerrain) {
                buildCeiling(template, originX, originZ, placements);
            }

            if (template.underfloor() != null && !isDynamicTerrain) {
                buildUnderfloor(template, originX, originZ, placements);
            }

            if (template.hazards() != null && !template.hazards().isEmpty()) {
                placeHazards(template, originX, originZ, placements);
            }

            if (template.lighting() != null) {
                placeLighting(template, originX, originZ, placements);
            }

            return placements;
        }

        private static ArenaTemplate.BuildSettings.Order resolveBuildOrder(ArenaTemplate template) {
            if (template.buildSettings() == null || template.buildSettings().buildOrder() == null) {
                return ArenaTemplate.BuildSettings.Order.FLOOR_FIRST;
            }
            return template.buildSettings().buildOrder();
        }

        private static void buildFloor(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            if (template.floor() == null) {
                return;
            }

            ArenaTemplate.TerrainSettings terrainSettings = template.terrainSettings();
            if (terrainSettings != null
                && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC) {
                buildDynamicTerrainFloor(template, originX, originZ, terrainSettings, placements);
                return;
            }

            ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
            if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
                boolean hasZoneFloorMaterials = zoneSettings.zones().stream()
                    .anyMatch(z -> {
                        String mat = z.floorMaterial();
                        return mat != null && !mat.isEmpty();
                    });
                if (hasZoneFloorMaterials) {
                    buildZoneAwareFloor(template, originX, originZ, zoneSettings, placements);
                    return;
                }
            }

            buildTemplateFloor(template, originX, originZ, placements);
        }

        private static void buildWalls(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            ArenaTemplate.Walls walls = template.walls();
            if (walls == null || !walls.enabled()) {
                return;
            }

            ArenaTemplate.ArenaShape shape = template.arenaShape();
            if (shape == null) {
                shape = ArenaTemplate.ArenaShape.RECTANGULAR;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int halfX = sizeX / 2;
            int halfZ = sizeZ / 2;

            if (shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
                int startX = originX - halfX;
                int startZ = originZ - halfZ;
                int endX = startX + sizeX - 1;
                int endZ = startZ + sizeZ - 1;

                for (int dy = 0; dy < walls.height(); dy++) {
                    int worldY = walls.startY() + dy;

                    for (int t = 0; t < walls.thickness(); t++) {
                        for (int x = startX; x <= endX; x++) {
                            addBlock(x, worldY, startZ - t, walls.material(), placements);
                            addBlock(x, worldY, endZ + t, walls.material(), placements);
                        }
                    }

                    for (int t = 0; t < walls.thickness(); t++) {
                        for (int z = startZ + 1; z <= endZ - 1; z++) {
                            addBlock(startX - t, worldY, z, walls.material(), placements);
                            addBlock(endX + t, worldY, z, walls.material(), placements);
                        }
                    }
                }
            } else {
                int radius = Math.max(halfX, halfZ);
                int outerExtent = radius + walls.thickness();

                for (int dy = 0; dy < walls.height(); dy++) {
                    int worldY = walls.startY() + dy;

                    for (int dx = -outerExtent; dx <= outerExtent; dx++) {
                        for (int dz = -outerExtent; dz <= outerExtent; dz++) {
                            if (isOnCircularBorder(dx, dz, template, walls.thickness())) {
                                addBlock(originX + dx, worldY, originZ + dz, walls.material(), placements);
                            }
                        }
                    }
                }
            }
        }

        private static void buildCeiling(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            ArenaTemplate.Ceiling ceiling = template.ceiling();
            if (ceiling == null || !ceiling.enabled()) {
                return;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int minX = minOffsetX(template);
            int maxX = maxOffsetX(template);
            int minZ = minOffsetZ(template);
            int maxZ = maxOffsetZ(template);

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (!isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                        continue;
                    }
                    for (int dy = 0; dy < ceiling.thickness(); dy++) {
                        addBlock(originX + dx, ceiling.y() + dy, originZ + dz, ceiling.material(), placements);
                    }
                }
            }
        }

        private static void buildUnderfloor(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            ArenaTemplate.Underfloor underfloor = template.underfloor();
            ArenaTemplate.Floor floor = template.floor();
            if (underfloor == null || floor == null) {
                return;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int minX = minOffsetX(template);
            int maxX = maxOffsetX(template);
            int minZ = minOffsetZ(template);
            int maxZ = maxOffsetZ(template);

            String material = underfloor.sameAsFloor() ? floor.material() : underfloor.material();

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (!isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                        continue;
                    }
                    for (int dy = 1; dy <= underfloor.depth(); dy++) {
                        addBlock(originX + dx, floor.y() - dy, originZ + dz, material, placements);
                    }
                }
            }
        }

        private static void placeHazards(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            List<ArenaTemplate.Hazard> hazards = template.hazards();
            if (hazards == null || hazards.isEmpty()) {
                return;
            }
            for (ArenaTemplate.Hazard hazard : hazards) {
                switch (hazard.type()) {
                    case "lava_ring" -> placeLavaRing(hazard, template, originX, originZ, placements);
                    case "lava_pool" -> placeLavaPool(hazard, template, originX, originZ, placements);
                    case "void_pit" -> placeVoidPit(hazard, template, originX, originZ, placements);
                    case "spike_trap" -> placeSpikeTrap(hazard, template, originX, originZ, placements);
                    case "fire_zone" -> placeFireZone(hazard, template, originX, originZ, placements);
                    case "magma_floor" -> placeMagmaFloor(hazard, template, originX, originZ, placements);
                    case "falling_blocks" -> placeFallingBlocks(hazard, template, originX, originZ, placements);
                    case "custom" -> LOGGER.warn("Custom hazard '{}' skipped in async builder", hazard.type());
                    default -> LOGGER.debug("Hazard type '{}' has no placement implementation", hazard.type());
                }
            }
        }

        private static void placeLighting(ArenaTemplate template, int originX, int originZ, List<BlockPlacement> placements) {
            ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
            if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
                placeZoneAwareLighting(template, originX, originZ, zoneSettings, placements);
                return;
            }

            placeTemplateLighting(template, originX, originZ, placements);
        }

        private static void buildDynamicTerrainFloor(ArenaTemplate template, int originX, int originZ,
                                                     ArenaTemplate.TerrainSettings terrainSettings,
                                                     List<BlockPlacement> placements) {
            ArenaTemplate.Floor floor = template.floor();
            if (floor == null) {
                return;
            }

            ArenaTemplate.TerrainSettings.DynamicSettings dynamicSettings = terrainSettings.dynamic();
            if (dynamicSettings == null) {
                dynamicSettings = ArenaTemplate.TerrainSettings.DynamicSettings.proxyOverworld();
            }

            int templateRadius = Math.max(getSizeX(template), getSizeZ(template)) / 2;
            int combatRadius = Math.min(dynamicSettings.combatRingRadius(), templateRadius);
            int blendRadius = dynamicSettings.blendRadius();

            if (combatRadius < dynamicSettings.combatRingRadius()) {
                LOGGER.warn("Template '{}': combatRingRadius {} exceeds template size {}, clamped to {}",
                    template.id(), dynamicSettings.combatRingRadius(), templateRadius * 2, combatRadius);
            }

            int maxRadius = combatRadius + blendRadius;
            int minX = Math.max(minOffsetX(template), -maxRadius);
            int maxX = Math.min(maxOffsetX(template), maxRadius);
            int minZ = Math.max(minOffsetZ(template), -maxRadius);
            int maxZ = Math.min(maxOffsetZ(template), maxRadius);

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > maxRadius) {
                        continue;
                    }

                    double blendFactor = 1.0;
                    if (dist > combatRadius) {
                        blendFactor = 1.0 - ((dist - combatRadius) / blendRadius);
                        blendFactor = Math.max(0.0, Math.min(1.0, blendFactor));
                    }

                    if (blendFactor < 1.0) {
                        long posHash = ((long) dx * 31 + dz) ^ template.id().hashCode();
                        double threshold = (posHash & 0xFFFF) / 65536.0;
                        if (threshold > blendFactor) {
                            continue;
                        }
                    }

                    for (int dy = 0; dy < floor.thickness(); dy++) {
                        int worldX = originX + dx;
                        int worldY = floor.y() + dy;
                        int worldZ = originZ + dz;

                        String material = floor.material();
                        if (dist <= combatRadius && floor.borderMaterial() != null && floor.borderWidth() > 0) {
                            if (dist >= combatRadius - floor.borderWidth()) {
                                material = floor.borderMaterial();
                            }
                        }

                        addBlock(worldX, worldY, worldZ, material, placements);
                    }
                }
            }
        }

        private static void buildZoneAwareFloor(ArenaTemplate template, int originX, int originZ,
                                                ArenaTemplate.ZoneSettings zoneSettings,
                                                List<BlockPlacement> placements) {
            ArenaTemplate.Floor floor = template.floor();
            if (floor == null) {
                return;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int halfX = sizeX / 2;
            int halfZ = sizeZ / 2;

            ZoneLayout layout = buildZoneLayoutWithEnvironments(zoneSettings, halfX, halfZ);

            int minX = minOffsetX(template);
            int maxX = maxOffsetX(template);
            int minZ = minOffsetZ(template);
            int maxZ = maxOffsetZ(template);

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (!isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                        continue;
                    }

                    String material = floor.material();
                    Optional<ArenaZone> zone = layout.getZoneAt(dx, dz);
                    if (zone.isPresent()) {
                        Optional<ResourceLocation> zoneMaterial = zone.get().environment().floorMaterial();
                        if (zoneMaterial.isPresent()) {
                            material = zoneMaterial.get().toString();
                        }
                    }

                    if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                        boolean inBorder = isOnFloorBorder(dx, dz, template, floor.borderWidth());
                        if (inBorder) {
                            material = floor.borderMaterial();
                        }
                    }

                    for (int dy = 0; dy < floor.thickness(); dy++) {
                        int worldX = originX + dx;
                        int worldY = floor.y() + dy;
                        int worldZ = originZ + dz;
                        addBlock(worldX, worldY, worldZ, material, placements);
                    }
                }
            }
        }

        private static ZoneLayout buildZoneLayoutWithEnvironments(ArenaTemplate.ZoneSettings settings,
                                                                  int halfX,
                                                                  int halfZ) {
            List<ArenaTemplate.ZoneSettings.ZoneDefinition> zones = settings.zones();
            int zoneCount = zones.size();
            ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

            if (zoneCount == 1) {
                builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
                ArenaTemplate.ZoneSettings.ZoneDefinition def = zones.get(0);
                ArenaZone zone = ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ)
                    .withEnvironment(createZoneEnvironment(def));
                builder.addZone(zone);
            } else if (zoneCount == 2) {
                builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
                ArenaTemplate.ZoneSettings.ZoneDefinition def0 = zones.get(0);
                ArenaTemplate.ZoneSettings.ZoneDefinition def1 = zones.get(1);
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

        private static ZoneEnvironment createZoneEnvironment(ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef) {
            Optional<ResourceLocation> biome = Optional.empty();
            String biomeStr = zoneDef.biome();
            if (biomeStr != null && !biomeStr.isEmpty()) {
                biome = Optional.of(ResourceLocation.parse(biomeStr));
            }

            Optional<ResourceLocation> floorMaterial = Optional.empty();
            String floorStr = zoneDef.floorMaterial();
            if (floorStr != null && !floorStr.isEmpty()) {
                floorMaterial = Optional.of(ResourceLocation.parse(floorStr));
            }

            Integer skyLightVal = zoneDef.skyLight();
            Integer blockLightVal = zoneDef.blockLight();
            int skyLight = skyLightVal != null ? skyLightVal.intValue() : 15;
            int blockLight = blockLightVal != null ? blockLightVal.intValue() : 0;
            boolean placeSources = blockLight > 0;
            ZoneEnvironment.LightingConfig lighting = new ZoneEnvironment.LightingConfig(skyLight, blockLight, placeSources);

            ZoneEnvironment.TimeConfig time = ZoneEnvironment.TimeConfig.ANY;
            String timeStr = zoneDef.time();
            if (timeStr != null && !timeStr.isEmpty()) {
                try {
                    time = ZoneEnvironment.TimeConfig.valueOf(timeStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown time config '{}' for zone '{}', using ANY", timeStr, zoneDef.name());
                }
            }

            return new ZoneEnvironment(biome, floorMaterial, lighting, time);
        }

        private static void buildTemplateFloor(ArenaTemplate template, int originX, int originZ,
                                               List<BlockPlacement> placements) {
            ArenaTemplate.Floor floor = template.floor();
            if (floor == null) {
                return;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int minX = minOffsetX(template);
            int maxX = maxOffsetX(template);
            int minZ = minOffsetZ(template);
            int maxZ = maxOffsetZ(template);

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (!isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                        continue;
                    }

                    for (int dy = 0; dy < floor.thickness(); dy++) {
                        int worldX = originX + dx;
                        int worldY = floor.y() + dy;
                        int worldZ = originZ + dz;

                        String material = floor.material();
                        if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                            boolean inBorder = isOnFloorBorder(dx, dz, template, floor.borderWidth());
                            if (inBorder) {
                                material = floor.borderMaterial();
                            }
                        }

                        addBlock(worldX, worldY, worldZ, material, placements);
                    }
                }
            }
        }

        private static boolean isOnFloorBorder(int dx, int dz, ArenaTemplate template, int borderWidth) {
            ArenaTemplate.ArenaShape shape = template.arenaShape();
            if (shape == null) {
                shape = ArenaTemplate.ArenaShape.RECTANGULAR;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int minX = minOffsetX(template);
            int minZ = minOffsetZ(template);

            return switch (shape) {
                case RECTANGULAR -> {
                    int localX = dx - minX;
                    int localZ = dz - minZ;
                    boolean inBorderX = localX < borderWidth || localX >= sizeX - borderWidth;
                    boolean inBorderZ = localZ < borderWidth || localZ >= sizeZ - borderWidth;
                    yield inBorderX || inBorderZ;
                }
                case CIRCULAR -> {
                    int radius = Math.max(sizeX, sizeZ) / 2;
                    int distSq = dx * dx + dz * dz;
                    int innerBorderSq = (radius - borderWidth) * (radius - borderWidth);
                    yield distSq >= innerBorderSq;
                }
                case RING -> {
                    int outerRadius = Math.max(sizeX, sizeZ) / 2;
                    Integer innerRadiusVal = template.ringInnerRadius();
                    int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                    int distSq = dx * dx + dz * dz;
                    int outerBorderSq = (outerRadius - borderWidth) * (outerRadius - borderWidth);
                    int innerBorderSq = (innerRadius + borderWidth) * (innerRadius + borderWidth);
                    yield distSq >= outerBorderSq || distSq <= innerBorderSq;
                }
            };
        }

        private static boolean isOnCircularBorder(int dx, int dz, ArenaTemplate template, int thickness) {
            ArenaTemplate.ArenaShape shape = template.arenaShape();
            if (shape == null || shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
                return false;
            }

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int halfX = sizeX / 2;
            int halfZ = sizeZ / 2;
            int outerRadius = Math.max(halfX, halfZ);
            int distSq = dx * dx + dz * dz;

            int outerWallInnerSq = outerRadius * outerRadius;
            int outerWallOuterSq = (outerRadius + thickness) * (outerRadius + thickness);
            boolean onOuterWall = distSq >= outerWallInnerSq && distSq < outerWallOuterSq;

            if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
                return onOuterWall;
            }

            Integer innerRadiusVal = template.ringInnerRadius();
            int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
            int innerWallOuterSq = innerRadius * innerRadius;
            int innerWallInnerSq = Math.max(0, (innerRadius - thickness) * (innerRadius - thickness));
            boolean onInnerWall = distSq <= innerWallOuterSq && distSq >= innerWallInnerSq;

            return onOuterWall || onInnerWall;
        }

        private static int resolveY(ArenaTemplate.Hazard hazard, ArenaTemplate template) {
            Integer hazardY = hazard.y();
            int baseY = hazardY != null ? hazardY : template.floor().y();
            ArenaTemplate.SpawnSlot.YMode mode = hazard.yMode();
            if (mode == null || mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
                int offset = hazardY != null ? hazardY : 0;
                return template.floor().y() + offset;
            }
            return baseY;
        }

        private static int[] resolveCenter(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ) {
            Object centerObj = hazard.params() != null ? hazard.params().get("center") : null;
            if (centerObj instanceof List<?> list && list.size() == 3) {
                int[] c = new int[3];
                for (int i = 0; i < 3; i++) {
                    Object v = list.get(i);
                    c[i] = v instanceof Number n ? n.intValue() : 0;
                }
                return c;
            }
            return new int[]{originX, resolveY(hazard, template), originZ};
        }

        private static void placeLavaRing(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                          int originX, int originZ, List<BlockPlacement> placements) {
            int inner = ((Number) hazard.params().getOrDefault("innerRadius", 1)).intValue();
            int outer = ((Number) hazard.params().getOrDefault("outerRadius", inner + 1)).intValue();
            int y = resolveY(hazard, template);
            int[] center = resolveCenter(hazard, template, originX, originZ);
            String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");

            for (int dx = -outer; dx <= outer; dx++) {
                for (int dz = -outer; dz <= outer; dz++) {
                    int r2 = dx * dx + dz * dz;
                    if (r2 >= inner * inner && r2 <= outer * outer) {
                        addBlock(center[0] + dx, y, center[2] + dz, material, placements);
                    }
                }
            }
        }

        private static void placeLavaPool(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                          int originX, int originZ, List<BlockPlacement> placements) {
            int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
            int y = resolveY(hazard, template);
            int[] center = resolveCenter(hazard, template, originX, originZ);
            String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        addBlock(center[0] + dx, y, center[2] + dz, material, placements);
                    }
                }
            }
        }

        private static void placeVoidPit(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                         int originX, int originZ, List<BlockPlacement> placements) {
            int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
            int depth = ((Number) hazard.params().getOrDefault("depth", 10)).intValue();
            int yTop = resolveY(hazard, template);
            int[] center = resolveCenter(hazard, template, originX, originZ);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        for (int dy = 0; dy < depth; dy++) {
                            addBlock(center[0] + dx, yTop - dy, center[2] + dz,
                                "minecraft:void_air", placements);
                        }
                    }
                }
            }
        }

        private static void placeSpikeTrap(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                           int originX, int originZ, List<BlockPlacement> placements) {
            Object positionsObj = hazard.params() != null ? hazard.params().get("positions") : null;
            if (!(positionsObj instanceof List<?> positions)) {
                return;
            }
            String material = (String) hazard.params().getOrDefault("material", "minecraft:iron_bars");
            for (Object posObj : positions) {
                if (posObj instanceof List<?> p && p.size() == 3) {
                    int x = ((Number) p.get(0)).intValue() + originX;
                    int y = resolveY(hazard, template);
                    int z = ((Number) p.get(2)).intValue() + originZ;
                    addBlock(x, y, z, material, placements);
                }
            }
        }

        private static void placeFireZone(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                          int originX, int originZ, List<BlockPlacement> placements) {
            Object minObj = hazard.params() != null ? hazard.params().get("min") : null;
            Object maxObj = hazard.params() != null ? hazard.params().get("max") : null;
            if (!(minObj instanceof List<?> min) || !(maxObj instanceof List<?> max)
                || min.size() != 3 || max.size() != 3) {
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
                    addBlock(x, minY, z, block, placements);
                }
            }
        }

        private static void placeMagmaFloor(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                            int originX, int originZ, List<BlockPlacement> placements) {
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

            int minX = minOffsetX(template);
            int maxX = maxOffsetX(template);
            int minZ = minOffsetZ(template);
            int maxZ = maxOffsetZ(template);

            outer:
            for (int dx = minX; dx <= maxX; dx += 2) {
                for (int dz = minZ; dz <= maxZ; dz += 2) {
                    if (placed >= total) {
                        break outer;
                    }
                    if (!isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                        continue;
                    }
                    addBlock(originX + dx, y, originZ + dz, block, placements);
                    placed++;
                }
            }
        }

        private static void placeFallingBlocks(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                               int originX, int originZ, List<BlockPlacement> placements) {
            Object areaObj = hazard.params() != null ? hazard.params().get("area") : null;
            String blockType = (String) hazard.params().getOrDefault("blockType", "minecraft:sand");
            int count = ((Number) hazard.params().getOrDefault("count", 5)).intValue();
            int interval = ((Number) hazard.params().getOrDefault("interval", 20)).intValue();

            int verticalSpacing = Math.max(1, interval / 10);

            if (areaObj instanceof List<?> area && area.size() == 3) {
                int centerX = ((Number) area.get(0)).intValue() + originX;
                int centerZ = ((Number) area.get(2)).intValue() + originZ;
                int y = resolveY(hazard, template);
                for (int i = 0; i < count; i++) {
                    addBlock(centerX, y + (i * verticalSpacing), centerZ, blockType, placements);
                }
            } else {
                int y = resolveY(hazard, template);
                for (int i = 0; i < count; i++) {
                    addBlock(originX, y + (i * verticalSpacing), originZ, blockType, placements);
                }
            }
        }

        private static void placeZoneAwareLighting(ArenaTemplate template, int originX, int originZ,
                                                   ArenaTemplate.ZoneSettings zoneSettings,
                                                   List<BlockPlacement> placements) {
            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int halfX = sizeX / 2;
            int halfZ = sizeZ / 2;
            int floorY = template.floor() != null ? template.floor().y() : 64;

            ZoneLayout layout = buildZoneLayoutFromSettings(zoneSettings, halfX, halfZ);

            for (ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef : zoneSettings.zones()) {
                ZoneEnvironment environment = createZoneEnvironment(zoneDef);
                if (!environment.lighting().placeLightSources() && environment.lighting().blockLight() <= 0) {
                    continue;
                }

                ArenaZone zone = layout.zones().stream()
                    .filter(z -> z.name().equals(zoneDef.name()))
                    .findFirst()
                    .orElse(null);

                if (zone == null) {
                    LOGGER.warn("Zone '{}' not found in layout, skipping lighting", zoneDef.name());
                    continue;
                }

                placeZoneLighting(zone, originX, originZ, floorY,
                    environment.lighting().blockLight(), template, placements);
            }
        }

        private static int placeZoneLighting(ArenaZone zone, int originX, int originZ, int floorY,
                                             int targetLight, ArenaTemplate template,
                                             List<BlockPlacement> placements) {
            ArenaZone.ZoneBounds bounds = zone.bounds();

            int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

            int lightY = floorY + 1;
            if (template.ceiling() != null && template.ceiling().enabled()) {
                int ceilingThickness = Math.max(1, template.ceiling().thickness());
                lightY = template.ceiling().y() - ceilingThickness;
            } else if (template.walls() != null && template.walls().enabled()) {
                lightY = template.walls().startY() + template.walls().height() - 2;
            }

            String lightBlock = selectLightBlock(targetLight);
            int placedCount = 0;

            int startX = originX + bounds.x1();
            int endX = originX + bounds.x2();
            int startZ = originZ + bounds.z1();
            int endZ = originZ + bounds.z2();

            for (int x = startX + spacing / 2; x < endX; x += spacing) {
                for (int z = startZ + spacing / 2; z < endZ; z += spacing) {
                    if (zone.contains(x - originX, z - originZ)) {
                        addBlock(x, lightY, z, lightBlock, placements);
                        placedCount++;
                    }
                }
            }

            return placedCount;
        }

        private static ZoneLayout buildZoneLayoutFromSettings(ArenaTemplate.ZoneSettings settings,
                                                              int halfX,
                                                              int halfZ) {
            List<ArenaTemplate.ZoneSettings.ZoneDefinition> zones = settings.zones();
            int zoneCount = zones.size();

            ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

            if (zoneCount == 1) {
                builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
                ArenaTemplate.ZoneSettings.ZoneDefinition def = zones.get(0);
                builder.addZone(ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ));
            } else if (zoneCount == 2) {
                builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
                ArenaTemplate.ZoneSettings.ZoneDefinition def0 = zones.get(0);
                ArenaTemplate.ZoneSettings.ZoneDefinition def1 = zones.get(1);
                builder.addZone(ArenaZone.rectangular(def0.name(), -halfX, -halfZ, 0, halfZ));
                builder.addZone(ArenaZone.rectangular(def1.name(), 0, -halfZ, halfX, halfZ));
            } else if (zoneCount <= 4) {
                builder.strategy(ZoneLayout.LayoutStrategy.QUADRANT);
                if (zones.size() >= 1) {
                    builder.addZone(ArenaZone.rectangular(zones.get(0).name(), 0, -halfZ, halfX, 0));
                }
                if (zones.size() >= 2) {
                    builder.addZone(ArenaZone.rectangular(zones.get(1).name(), -halfX, -halfZ, 0, 0));
                }
                if (zones.size() >= 3) {
                    builder.addZone(ArenaZone.rectangular(zones.get(2).name(), -halfX, 0, 0, halfZ));
                }
                if (zones.size() >= 4) {
                    builder.addZone(ArenaZone.rectangular(zones.get(3).name(), 0, 0, halfX, halfZ));
                }
            } else {
                builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
                builder.addZone(ArenaZone.rectangular("main", -halfX, -halfZ, halfX, halfZ));
            }

            return builder.build();
        }

        private static void placeTemplateLighting(ArenaTemplate template, int originX, int originZ,
                                                  List<BlockPlacement> placements) {
            ArenaTemplate.Lighting lighting = template.lighting();
            if (lighting == null) {
                return;
            }

            int placedLights = 0;
            int skippedLights = 0;

            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int halfX = sizeX / 2;
            int halfZ = sizeZ / 2;
            int minX = -halfX;
            int maxX = sizeX - halfX - 1;
            int minZ = -halfZ;
            int maxZ = sizeZ - halfZ - 1;

            if (lighting.lightSources() != null && !lighting.lightSources().isEmpty()) {
                for (ArenaTemplate.Lighting.LightSource lightSource : lighting.lightSources()) {
                    int[] pos = lightSource.pos();
                    if (pos != null && pos.length == 3) {
                        int relX = pos[0];
                        int relZ = pos[2];
                        if (relX < minX || relX > maxX || relZ < minZ || relZ > maxZ) {
                            skippedLights++;
                            continue;
                        }

                        int x = originX + pos[0];
                        int y = pos[1];
                        int z = originZ + pos[2];

                        if (template.floor() != null) {
                            y = template.floor().y() + pos[1];
                        }

                        addBlock(x, y, z, lightSource.block(), placements);
                        placedLights++;
                    }
                }
            }

            if (lighting.ambientLight() && lighting.blockLight() > 0) {
                placedLights += placeAmbientLighting(template, originX, originZ, lighting.blockLight(), placements);
            }

            if (placedLights > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Placed {} light sources for template '{}'", placedLights, template.id());
            }

            if (skippedLights > 0) {
                LOGGER.warn("Skipped {} light sources outside arena bounds for template '{}'",
                    skippedLights, template.id());
            }
        }

        private static int placeAmbientLighting(ArenaTemplate template, int originX, int originZ,
                                                int targetLight, List<BlockPlacement> placements) {
            int sizeX = getSizeX(template);
            int sizeZ = getSizeZ(template);
            int startX = originX - (sizeX / 2);
            int startZ = originZ - (sizeZ / 2);

            int lightY;
            if (template.floor() != null) {
                lightY = template.floor().y() + 1;
            } else if (template.ceiling() != null && template.ceiling().enabled()) {
                int ceilingThickness = Math.max(1, template.ceiling().thickness());
                lightY = template.ceiling().y() - ceilingThickness;
            } else if (template.walls() != null && template.walls().enabled()) {
                lightY = template.walls().startY() + Math.max(1, template.walls().height() / 2);
            } else {
                lightY = 65;
            }

            if (template.ceiling() != null && template.ceiling().enabled()) {
                int ceilingThickness = Math.max(1, template.ceiling().thickness());
                lightY = template.ceiling().y() - ceilingThickness;
            } else if (template.floor() != null && template.walls() != null && template.walls().enabled()) {
                lightY = template.walls().startY() + template.walls().height() - 2;
            }

            int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));
            String lightBlock = selectLightBlock(targetLight);

            int placedCount = 0;
            for (int dx = spacing / 2; dx < sizeX; dx += spacing) {
                for (int dz = spacing / 2; dz < sizeZ; dz += spacing) {
                    int x = startX + dx;
                    int z = startZ + dz;
                    addBlock(x, lightY, z, lightBlock, placements);
                    placedCount++;
                }
            }

            return placedCount;
        }

        private static String selectLightBlock(int targetLight) {
            if (targetLight >= 14) {
                return "minecraft:sea_lantern";
            } else if (targetLight >= 11) {
                return "minecraft:glowstone";
            } else if (targetLight >= 8) {
                return "minecraft:lantern";
            } else if (targetLight >= 5) {
                return "minecraft:soul_lantern";
            } else {
                return "minecraft:redstone_torch";
            }
        }

        private static int minOffsetX(ArenaTemplate template) {
            return minOffset(getSizeX(template));
        }

        private static int maxOffsetX(ArenaTemplate template) {
            return maxOffset(getSizeX(template));
        }

        private static int minOffsetZ(ArenaTemplate template) {
            return minOffset(getSizeZ(template));
        }

        private static int maxOffsetZ(ArenaTemplate template) {
            return maxOffset(getSizeZ(template));
        }

        private static void addBlock(int x, int y, int z, String material, List<BlockPlacement> placements) {
            placements.add(new BlockPlacement(x, y, z, material));
        }
    }

    public record BlockPlacement(int x, int y, int z, String material) {}

    public record AsyncBuildResult(
        boolean success,
        UUID arenaId,
        String templateId,
        int blocksPlaced,
        long durationMs,
        String errorMessage
    ) {
        public static AsyncBuildResult success(UUID arenaId, String templateId, int blocks, long duration) {
            return new AsyncBuildResult(true, arenaId, templateId, blocks, duration, null);
        }

        public static AsyncBuildResult failure(UUID arenaId, String templateId, String error, int blocks, long duration) {
            return new AsyncBuildResult(false, arenaId, templateId, blocks, duration, error);
        }
    }

    /**
     * DD44: Exception for failed builds, includes result details.
     */
    public static class BuildException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient AsyncBuildResult result;

        public BuildException(String message, AsyncBuildResult result) {
            super(message);
            this.result = result;
        }

        public AsyncBuildResult getResult() {
            return result;
        }
    }
}

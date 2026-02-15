package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class AsyncArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncArenaBuilder.class);

    private static final int DEFAULT_MAX_QUEUE_DEPTH = 5;
    private static final int DEFAULT_CRITICAL_QUEUE_DEPTH = 10;
    private static final double SEVERE_PRESSURE_MSPT = 45.0;

    private final ArenaTelemetry telemetry;
    private final ArenaBuilder.BlockPlacer blockPlacer;
    private final BackpressureManager backpressure;
    private final Supplier<Double> msptSupplier;
    private final TemplateEventDispatcher eventDispatcher;
    private final InstanceOnlyGate instanceGate;
    private final AsyncBuildPerformanceTracker perfTracker;

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
        MsptMonitor msptMonitor = new MsptMonitor();
        PerformanceBudgetEnforcer performanceEnforcer = new PerformanceBudgetEnforcer(msptMonitor);
        this.perfTracker = new AsyncBuildPerformanceTracker(msptMonitor, performanceEnforcer, telemetry);
    }

    // === Public API ===

    /**
     * DD44: Submits a build and returns a CompletableFuture for the result.
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

        if (!checkInstanceGate(arenaId, template, callback)) {
            return false;
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

        eventDispatcher.emitBuildStarted(
            template.id(), arenaId, null, build.placements.size());

        return true;
    }

    /**
     * Called each server tick to process builds.
     */
    public void onTick() {
        if (activeBuildQueue.isEmpty()) {
            if (perfTracker.isActive()) {
                perfTracker.finish();
            }
            return;
        }

        double currentMspt = msptSupplier.get();
        perfTracker.startIfNeeded(currentMspt);
        perfTracker.recordSample(currentMspt);

        PerformanceAction action = perfTracker.checkPerformance();
        double tps = currentMspt > 0 ? Math.min(1000.0 / currentMspt, 20.0) : 20.0;
        double buildImpact = perfTracker.getBuildImpact();

        if (buildImpact > PerformanceBudgetEnforcer.DEFAULT_BUILD_IMPACT_THRESHOLD
            || tps < PerformanceBudgetEnforcer.DEFAULT_TPS_THRESHOLD) {
            perfTracker.emitDegradedTelemetry(currentMspt, tps, buildImpact, action,
                activeBuildQueue.size(), buildsByArenaId.size());
        }

        int blocksThisTick = backpressure.update(currentMspt);

        // Queue depth shedding under severe pressure
        int queueSize = activeBuildQueue.size();
        if (currentMspt > SEVERE_PRESSURE_MSPT && queueSize > criticalQueueDepth) {
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
            perfTracker.emitAbortTelemetry(currentMspt, tps, buildImpact,
                activeBuildQueue.size(), buildsByArenaId.size());
            abortAllBuilds("Performance budget exceeded");
            perfTracker.finish();
            return;
        }

        if (action == PerformanceAction.PAUSE) {
            perfTracker.recordPauseApplied();
            perfTracker.emitBackpressureTelemetry(currentMspt, tps, buildImpact, action,
                blocksThisTick, 0, activeBuildQueue.size(), buildsByArenaId.size());
            return;
        }

        if (action == PerformanceAction.THROTTLE) {
            int previousBlocks = blocksThisTick;
            backpressure.setBlocksPerTick(previousBlocks / 2);
            blocksThisTick = backpressure.getCurrentBlocksPerTick();
            perfTracker.emitBackpressureTelemetry(currentMspt, tps, buildImpact, action,
                previousBlocks, blocksThisTick, activeBuildQueue.size(), buildsByArenaId.size());
        }

        processBuildsRoundRobin(blocksThisTick);

        if (activeBuildQueue.isEmpty() && perfTracker.isActive()) {
            perfTracker.finish();
        }
    }

    public boolean cancelBuild(UUID arenaId) {
        return cancelBuild(arenaId, null, "user-requested");
    }

    public boolean cancelBuild(UUID arenaId, UUID cancelledBy, String reason) {
        AsyncBuild build = buildsByArenaId.remove(arenaId);
        if (build != null) {
            activeBuildQueue.remove(build);

            int blocksPlaced = build.budget.getCurrentBlocks();
            long durationMs = build.budget.getElapsedMs();

            build.transaction.rollback(
                blockPlacer::revertBlock,
                uuid -> { LOGGER.debug("Entity removal requested during cancel: {}", uuid); return true; },
                pos -> LOGGER.debug("Chunk release requested during cancel: {}", pos)
            );

            LOGGER.info("Cancelled build for arena {}", arenaId);

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

    // === Internal methods ===

    private boolean checkInstanceGate(UUID arenaId, ArenaTemplate template, Consumer<AsyncBuildResult> callback) {
        if (instanceGate == null) {
            return true;
        }
        net.minecraft.server.level.ServerLevel gateLevel = null;
        if (blockPlacer instanceof com.devmod.arena.integration.BatchBlockPlacer batchPlacer) {
            gateLevel = batchPlacer.level();
        } else if (blockPlacer instanceof com.devmod.arena.integration.MinecraftBlockPlacer mcPlacer) {
            gateLevel = mcPlacer.level();
        }
        if (gateLevel != null) {
            InstanceOnlyGate.Result gateResult = instanceGate.check(gateLevel, "AsyncArenaBuilder.submitBuild");
            if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                LOGGER.error("Instance-only gate blocked async build for template {} in {}",
                    template.id(), gateLevel.dimension().location());
                callback.accept(AsyncBuildResult.failure(arenaId, template.id(), "instance_only_blocked", 0, 0));
                return false;
            } else if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                LOGGER.warn("Instance-only gate: debug-only build for template {} in {}",
                    template.id(), gateLevel.dimension().location());
            }
        }
        return true;
    }

    private void processBuildsRoundRobin(int blocksThisTick) {
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
                    activeBuildQueue.add(build);
                } else {
                    completeBuild(build, null);
                }

            } catch (Exception e) {
                completeBuild(build, e);
            }

            buildsProcessed++;

            if (buildsProcessed >= buildsByArenaId.size()) {
                break;
            }
        }
    }

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

        build.budget.checkTime();
        return blocksPlaced;
    }

    private void completeBuild(AsyncBuild build, Exception error) {
        buildsByArenaId.remove(build.arenaId);
        DiagnosticLogger.arena("completeBuild: arenaId=%s, template=%s, success=%s, blocks=%d, durationMs=%d",
            build.arenaId, build.template.id(), error == null,
            build.budget.getCurrentBlocks(), build.budget.getElapsedMs());

        if (error != null) {
            handleBuildFailure(build, error);
        } else {
            handleBuildSuccess(build);
        }
    }

    private void handleBuildFailure(AsyncBuild build, Exception error) {
        totalBuildsFailed++;
        LOGGER.error("Async build failed for arena {}: {}", build.arenaId, error.getMessage());

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

        eventDispatcher.emitBuildFailed(
            build.template.id(), build.arenaId, error.getMessage(), error,
            build.budget.getElapsedMs(), true);
    }

    private void handleBuildSuccess(AsyncBuild build) {
        totalBuildsCompleted++;

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

        eventDispatcher.emitBuildCompleted(
            build.template.id(), build.arenaId,
            build.budget.getCurrentBlocks(), 0, build.budget.getElapsedMs());

        clearPlacerCache();
    }

    private void clearPlacerCache() {
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

    private void abortAllBuilds(String reason) {
        List<AsyncBuild> builds = new ArrayList<>(buildsByArenaId.values());
        activeBuildQueue.clear();
        for (AsyncBuild build : builds) {
            completeBuild(build, new RuntimeException(reason));
        }
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

    // === Supporting Types ===

    /**
     * Represents a build in progress.
     */
    static class AsyncBuild {
        final UUID arenaId;
        final ArenaTemplate template;
        final Consumer<AsyncBuildResult> callback;
        final BuildTransaction transaction;
        final BuildBudget budget;
        final List<BlockPlacement> placements;
        int placementIndex = 0;

        static AsyncBuild create(UUID arenaId, ArenaTemplate template, int originX, int originY, int originZ,
                                 Consumer<AsyncBuildResult> callback) {
            List<BlockPlacement> placements = AsyncBuildPlacementComputer.computePlacements(
                template, originX, originY, originZ);
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

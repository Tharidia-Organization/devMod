package com.devmod.arena.builder;

import com.devmod.arena.budget.BackpressureManager;
import com.devmod.arena.budget.BuildBudget;
import com.devmod.arena.event.TemplateEventDispatcher;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.monitor.MsptMonitor;
import com.devmod.arena.performance.PerformanceBudgetEnforcer;
import com.devmod.arena.performance.PerformanceBudgetEnforcer.PerformanceAction;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.config.ArenaTemplateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Async arena builder with tick distribution (DD12).
 *
 * <p>Distributes block placements across server ticks to avoid lag spikes.
 * Uses backpressure when MSPT exceeds threshold.
 */
public class AsyncArenaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncArenaBuilder.class);

    private final ArenaTelemetry telemetry;
    private final ArenaBuilder.BlockPlacer blockPlacer;
    private final BackpressureManager backpressure;
    private final Supplier<Double> msptSupplier;
    private final TemplateEventDispatcher eventDispatcher;
    private final InstanceOnlyGate instanceGate;
    private final MsptMonitor msptMonitor;
    private final PerformanceBudgetEnforcer performanceEnforcer;
    private boolean performanceTrackingActive = false;

    // Active builds
    private final Queue<AsyncBuild> activeBuildQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, AsyncBuild> buildsByArenaId = new LinkedHashMap<>();

    // Statistics
    private long totalBlocksPlaced = 0;
    private long totalBuildsCompleted = 0;
    private long totalBuildsFailed = 0;

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            ArenaBuilder.BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier) {
        this(telemetry, blockPlacer, msptSupplier, new BackpressureManager(), null);
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
        this(telemetry, blockPlacer, msptSupplier, new BackpressureManager(), configSnapshot);
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

        // Instance-only gate: block overworld builds when configured and using Minecraft block placer
        if (instanceGate != null && blockPlacer instanceof com.devmod.arena.integration.MinecraftBlockPlacer mcPlacer) {
            InstanceOnlyGate.Result gateResult = instanceGate.check(mcPlacer.level(), "AsyncArenaBuilder.submitBuild");
            if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                LOGGER.error("Instance-only gate blocked async build for template {} in {}", template.id(), mcPlacer.level().dimension().location());
                callback.accept(AsyncBuildResult.failure(arenaId, template.id(), "instance_only_blocked", 0, 0));
                return false;
            } else if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                LOGGER.warn("Instance-only gate: debug-only build for template {} in {}", template.id(), mcPlacer.level().dimension().location());
            }
        }

        if (buildsByArenaId.containsKey(arenaId)) {
            LOGGER.warn("Build already in progress for arena {}", arenaId);
            return false;
        }

        AsyncBuild build = new AsyncBuild(
            arenaId,
            template,
            originX,
            originY,
            originZ,
            callback
        );

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

        AsyncBuild(UUID arenaId, ArenaTemplate template, int originX, int originY, int originZ,
                   Consumer<AsyncBuildResult> callback) {
            this.arenaId = arenaId;
            this.template = template;
            this.callback = callback;
            this.transaction = new BuildTransaction(template.id());
            this.budget = BuildBudget.defaults();
            this.placements = computePlacements(template, originX, originY, originZ);

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

        private static List<BlockPlacement> computePlacements(
                ArenaTemplate template, int originX, int originY, int originZ) {
            List<BlockPlacement> placements = new ArrayList<>();

            // Floor
            if (template.floor() != null) {
                var floor = template.floor();
                Integer sizeXVal = template.sizeX();
                Integer sizeZVal = template.sizeZ();
                int sizeX = sizeXVal != null ? sizeXVal : template.size();
                int sizeZ = sizeZVal != null ? sizeZVal : template.size();
                int halfX = sizeX / 2;
                int halfZ = sizeZ / 2;

                for (int dx = -halfX; dx <= halfX; dx++) {
                    for (int dz = -halfZ; dz <= halfZ; dz++) {
                        for (int dy = 0; dy < floor.thickness(); dy++) {
                            placements.add(new BlockPlacement(
                                originX + dx, floor.y() + dy, originZ + dz, floor.material()
                            ));
                        }
                    }
                }
            }

            // Additional structures would be added here...

            return placements;
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

package com.devmod.arena.builder;

import com.devmod.arena.budget.BackpressureManager;
import com.devmod.arena.budget.BuildBudget;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    private final BlockPlacer blockPlacer;
    private final BackpressureManager backpressure;
    private final Supplier<Double> msptSupplier;

    // Active builds
    private final Queue<AsyncBuild> activeBuildQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, AsyncBuild> buildsByArenaId = new LinkedHashMap<>();

    // Statistics
    private long totalBlocksPlaced = 0;
    private long totalBuildsCompleted = 0;
    private long totalBuildsFailed = 0;

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.msptSupplier = msptSupplier;
        this.backpressure = new BackpressureManager();
    }

    public AsyncArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            Supplier<Double> msptSupplier,
            BackpressureManager backpressure) {
        this.telemetry = telemetry;
        this.blockPlacer = blockPlacer;
        this.msptSupplier = msptSupplier;
        this.backpressure = backpressure;
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

        return true;
    }

    /**
     * Called each server tick to process builds.
     * Register this with the server tick event.
     */
    public void onTick() {
        if (activeBuildQueue.isEmpty()) {
            return;
        }

        // Update backpressure based on current MSPT
        double currentMspt = msptSupplier.get();
        int blocksThisTick = backpressure.update(currentMspt);

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

            // Rollback
            build.transaction.rollback(
                blockPlacer::revertBlock,
                uuid -> true, // Entity removal placeholder
                pos -> {}     // Chunk release placeholder
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
        }
    }

    /**
     * Cancels a build in progress.
     */
    public boolean cancelBuild(UUID arenaId) {
        AsyncBuild build = buildsByArenaId.remove(arenaId);
        if (build != null) {
            activeBuildQueue.remove(build);

            // Rollback
            build.transaction.rollback(
                blockPlacer::revertBlock,
                uuid -> true,
                pos -> {}
            );

            LOGGER.info("Cancelled build for arena {}", arenaId);
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
        final int originX, originY, originZ;
        final Consumer<AsyncBuildResult> callback;
        final BuildTransaction transaction;
        final BuildBudget budget;
        final List<BlockPlacement> placements;
        int placementIndex = 0;

        AsyncBuild(UUID arenaId, ArenaTemplate template, int originX, int originY, int originZ,
                   Consumer<AsyncBuildResult> callback) {
            this.arenaId = arenaId;
            this.template = template;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
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
                int sizeX = template.sizeX() != null ? template.sizeX() : template.size();
                int sizeZ = template.sizeZ() != null ? template.sizeZ() : template.size();
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

    @FunctionalInterface
    public interface BlockPlacer {
        int placeBlock(int x, int y, int z, String material);
        default boolean revertBlock(long packedPos, int stateId) {
            return true;
        }
    }
}

package com.devmod.area.builder;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderParticles;
import com.devmod.area.aesthetic.AreaBuilderSounds;
import com.devmod.area.aesthetic.AreaBuilderTiming;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.snapshot.AreaSnapshotManager;

/**
 * Manages multi-tick area build tasks across the server.
 * Automatically ticks all active builds and provides progress feedback.
 */
public final class AreaBuildTaskManager {
    public static final AreaBuildTaskManager INSTANCE = new AreaBuildTaskManager();

    /** Threshold in blocks above which multi-tick building is used */
    public static final int MULTI_TICK_THRESHOLD = 10_000;

    /** Maximum number of concurrent build tasks to prevent server overload */
    public static final int MAX_CONCURRENT_BUILDS = 5;

    /** Maximum number of builds that can wait in queue */
    public static final int MAX_QUEUE_SIZE = 20;

    /** MED-04 fix: Maximum time in milliseconds for biome precompute before timeout */
    public static final long MAX_BIOME_PRECOMPUTE_MS = 60_000; // 60 seconds

    /** Active build tasks by area ID */
    private final Map<UUID, ActiveBuild> activeBuildTasks = new ConcurrentHashMap<>();

    /** Pending biome builds waiting on async precompute */
    private final Map<UUID, PendingBiomeBuild> pendingBiomeBuilds = new ConcurrentHashMap<>();
    /** Futures for pending biome precompute tasks */
    private final Map<UUID, CompletableFuture<?>> pendingBiomeFutures = new ConcurrentHashMap<>();

    /** Queue of builds waiting for available slots */
    private final Queue<QueuedBuild> buildQueue = new ConcurrentLinkedQueue<>();

    /** M-04 fix: Tick counter for periodic cleanup (runs every 1200 ticks = ~1 minute) */
    private static final int CLEANUP_INTERVAL_TICKS = 1200;
    private int tickCounter = 0;

    private AreaBuildTaskManager() {}

    /**
     * LOW-02 fix: Result of a build start operation for UI status updates.
     */
    public enum BuildStartResult {
        /** Build started immediately (synchronous small build) */
        STARTED_IMMEDIATE,
        /** Build started as multi-tick task */
        STARTED_MULTI_TICK,
        /** Build queued due to concurrent build limit */
        QUEUED,
        /** Build rejected - already in progress for this area */
        ALREADY_BUILDING,
        /** Build rejected - queue is full */
        QUEUE_FULL
    }

    /**
     * Starts a new area build.
     * Uses multi-tick for large areas, immediate for small areas.
     *
     * @param level      The server level
     * @param definition The area definition
     * @param player     The player who initiated the build (for feedback)
     * @param clearFirst Whether to clear the area before building
     * @return The result of the build start operation
     */
    @Nonnull
    public BuildStartResult startBuild(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                          @Nullable ServerPlayer player, boolean clearFirst) {
        return startBuild(level, definition, player, clearFirst, false);
    }

    /**
     * Starts a new area build with explicit multi-tick control.
     * Uses multi-tick for large areas, immediate for small areas, unless forced.
     *
     * @param level         The server level
     * @param definition    The area definition
     * @param player        The player who initiated the build (for feedback)
     * @param clearFirst    Whether to clear the area before building
     * @param forceMultiTick If true, forces multi-tick building regardless of size
     * @return The result of the build start operation
     */
    @Nonnull
    public BuildStartResult startBuild(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                          @Nullable ServerPlayer player, boolean clearFirst, boolean forceMultiTick) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(definition, "definition");

        // SEC-04 fix: Re-validate area still exists in registry to prevent race condition
        // Area could be deleted between network handler lookup and this method call
        MinecraftServer server = level.getServer();
        UUID areaId = definition.id();
        if (server != null && areaId != null) {
            AreaRegistry registry = AreaRegistry.get(server);
            if (registry.getArea(areaId).isEmpty()) {
                DevMod.LOGGER.warn("[Area] Build rejected: area {} no longer exists (deleted during request)",
                    definition.id());
                if (player != null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                            "area.message.error_area_not_found")), true);
                    AreaBuilderSounds.playError(level, player);
                }
                return BuildStartResult.ALREADY_BUILDING; // Reuse existing enum, semantically similar
            }
        }

        // SEC-08 fix: Block builds while a snapshot restore is in progress
        if (AreaSnapshotManager.INSTANCE.isRestoringArea(Objects.requireNonNull(areaId))) {
            DevMod.LOGGER.warn("[Area] Build rejected: snapshot restore in progress for area {}", areaId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                        "area.message.error_restore_in_progress")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return BuildStartResult.ALREADY_BUILDING; // Reuse existing enum for build rejection
        }

        // Check if already building this area
        if (isBuildingArea(definition.id())) {
            DevMod.LOGGER.warn("[Area] Build already in progress for area: {}", definition.id());
            if (player != null) {
                AreaBuilderSounds.playError(level, player);
            }
            return BuildStartResult.ALREADY_BUILDING;
        }

        // Check global concurrent build limit - queue if at capacity
        if (getInFlightBuildCount() >= MAX_CONCURRENT_BUILDS) {
            if (queueBuild(level, definition, player, clearFirst, forceMultiTick)) {
                int position = getQueuePosition(Objects.requireNonNull(definition.id())) + 1;
                DevMod.LOGGER.info("[Area] Build queued at position {}: {}", position, definition.name());
                if (player != null) {
                    player.displayClientMessage(
                        Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                            "area.message.build_queued", position)), true);
                }
                return BuildStartResult.QUEUED;
            }
            // Queue also full - reject
            DevMod.LOGGER.warn("[Area] Build rejected: queue full ({}/{})", buildQueue.size(), MAX_QUEUE_SIZE);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable("area.message.queue_full")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return BuildStartResult.QUEUE_FULL;
        }

        // Biome generation with multi-tick support
        // MED-01 fix: Auto-enable multi-tick for biome builds based on volume estimate
        if (definition.isBiomeGeneration()) {
            int estimatedBiomeBlocks = AreaBlockMapGenerator.estimateBiomeBlocks(definition);
            boolean shouldUseMultiTick = forceMultiTick || estimatedBiomeBlocks > MULTI_TICK_THRESHOLD;

            if (shouldUseMultiTick) {
                startMultiTickBiomeBuild(level, definition, player, clearFirst);
                return BuildStartResult.STARTED_MULTI_TICK;
            } else {
                buildImmediate(level, definition, player, clearFirst);
                return BuildStartResult.STARTED_IMMEDIATE;
            }
        }

        // Calculate actual block count (surface area, not volume)
        int estimatedBlocks = AreaBlockMapGenerator.estimateTotalBlocks(definition);

        // Use multi-tick if forced OR if area exceeds threshold
        if (forceMultiTick || estimatedBlocks > MULTI_TICK_THRESHOLD) {
            startMultiTickBuild(level, definition, player, clearFirst);
            return BuildStartResult.STARTED_MULTI_TICK;
        } else {
            // Small area - build immediately (sync clear is acceptable for small areas)
            buildImmediate(level, definition, player, clearFirst);
            return BuildStartResult.STARTED_IMMEDIATE;
        }
    }

    /**
     * Builds a small area immediately (synchronously).
     * MED-02 fix: Added clearFirst parameter for immediate builds.
     */
    private void buildImmediate(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                                @Nullable ServerPlayer player, boolean clearFirst) {
        if (player != null) {
            AreaBuilderSounds.playBuildStart(level, player);
        }

        AreaBuilder builder = new AreaBuilder(level, definition);

        // MED-02 fix: Sync clear for immediate (small) builds
        if (clearFirst) {
            builder.clear();
        }

        builder.build();

        if (player != null) {
            AreaBuilderSounds.playBuildComplete(level, player);
            AreaBuilderParticles.spawnBuildCompleteParticles(level, definition.centerPosition());
        }

        DevMod.LOGGER.info("[Area] Built area '{}' immediately", definition.name());
    }

    /**
     * Starts a multi-tick build for large areas.
     * MED-02 fix: Uses async clear step when clearFirst is true to prevent lag spikes.
     */
    private void startMultiTickBuild(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                                     @Nullable ServerPlayer player, boolean clearFirst) {
        if (player != null) {
            AreaBuilderSounds.playBuildStart(level, player);
        }

        UUID playerId = player != null ? player.getUUID() : null;
        int lastProgress = 0;

        AreaBuildTask task = new AreaBuildTask(
            level,
            definition,
            AreaBuilderTiming.BUILD_MAX_BLOCKS_PER_TICK,
            this::onBuildComplete
        );

        // MED-02 fix: Add clear step to task instead of sync clearing
        // This spreads the clearing over multiple ticks, preventing lag spikes
        if (clearFirst) {
            task.withClearStep();
        }

        activeBuildTasks.put(definition.id(), new ActiveBuild(task, level, playerId, clearFirst, true, lastProgress));

        DevMod.LOGGER.info("[Area] Started multi-tick build for area '{}' ({} estimated blocks{})",
            definition.name(), task.getTotalBlockCount(),
            clearFirst ? ", with async clear" : "");
    }

    /**
     * Starts a multi-tick build for biome areas.
     * Pre-computes all blocks using BiomeAreaGenerator, then uses AreaBuildTask for placement.
     */
    private void startMultiTickBiomeBuild(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                                          @Nullable ServerPlayer player, boolean clearFirst) {
        if (player != null) {
            AreaBuilderSounds.playBuildStart(level, player);
        }

        UUID playerId = player != null ? player.getUUID() : null;
        UUID areaId = definition.id();
        if (pendingBiomeBuilds.containsKey(areaId)) {
            DevMod.LOGGER.warn("[Area] Biome build already preparing for area: {}", areaId);
            return;
        }
        PendingBiomeBuild pending = new PendingBiomeBuild(
            definition,
            Objects.requireNonNull(level.dimension().location()),
            playerId,
            clearFirst,
            true,
            System.currentTimeMillis()
        );
        pendingBiomeBuilds.put(areaId, pending);

        MinecraftServer server = level.getServer();
        CompletableFuture<java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState>> precomputeFuture = CompletableFuture
            .supplyAsync(() -> {
                java.util.Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
                    Objects.requireNonNull(definition.shape()),
                    Objects.requireNonNull(definition.centerPosition()),
                    Objects.requireNonNull(definition.dimensions()),
                    definition.customShapeNbt()
                );
                return BiomeAreaGenerator.generateBlockMap(definition, floorPositions);
            })
            // HIGH-05 fix: Add timeout to prevent futures from hanging indefinitely
            .orTimeout(MAX_BIOME_PRECOMPUTE_MS, TimeUnit.MILLISECONDS)
            .whenComplete((blockMap, throwable) -> {
                server.execute(() -> {
                    pendingBiomeFutures.remove(areaId);
                    PendingBiomeBuild pendingBuild = pendingBiomeBuilds.remove(areaId);
                    if (pendingBuild == null) {
                        return;
                    }

                    if (throwable != null) {
                        // HIGH-05 fix: Distinguish timeout from other errors
                        boolean isTimeout = throwable instanceof TimeoutException ||
                            (throwable.getCause() instanceof TimeoutException);
                        if (isTimeout) {
                            DevMod.LOGGER.error("[Area] Biome precompute TIMEOUT for area {} after {}ms",
                                areaId, MAX_BIOME_PRECOMPUTE_MS);
                        } else {
                            DevMod.LOGGER.error("[Area] Biome precompute failed for area {}", areaId, throwable);
                        }
                        ServerPlayer pendingPlayer = pendingBuild.playerId() != null
                            ? server.getPlayerList().getPlayer(Objects.requireNonNull(pendingBuild.playerId()))
                            : null;
                        if (pendingPlayer != null) {
                            if (isTimeout) {
                                pendingPlayer.displayClientMessage(
                                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                                        "area.message.error_biome_timeout")), true);
                            }
                            AreaBuilderSounds.playError(level, pendingPlayer);
                        }
                        return;
                    }

                    if (blockMap == null || blockMap.isEmpty()) {
                        DevMod.LOGGER.warn("[Area] Biome generation produced no blocks for area '{}'",
                            pendingBuild.definition().name());
                        ServerPlayer pendingPlayer = pendingBuild.playerId() != null
                            ? server.getPlayerList().getPlayer(Objects.requireNonNull(pendingBuild.playerId()))
                            : null;
                        if (pendingPlayer != null) {
                            pendingPlayer.displayClientMessage(
                                Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                                    "area.message.error_biome_generation_empty")), true);
                            AreaBuilderSounds.playError(level, pendingPlayer);
                        }
                        return;
                    }

                    com.devmod.area.data.AreaRegistry registry = com.devmod.area.data.AreaRegistry.get(server);
                    AreaDefinition current = registry.getArea(Objects.requireNonNull(areaId)).orElse(null);
                    if (current == null || current.revision() != pendingBuild.definition().revision()) {
                        DevMod.LOGGER.info("[Area] Biome precompute discarded for area {} (definition changed)", areaId);
                        return;
                    }

                    ServerLevel targetLevel = server.getLevel(Objects.requireNonNull(
                        ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), pendingBuild.levelId())));
                    if (targetLevel == null) {
                        DevMod.LOGGER.warn("[Area] Biome build skipped: dimension {} not loaded", pendingBuild.levelId());
                        return;
                    }
                    if (activeBuildTasks.containsKey(areaId)) {
                        DevMod.LOGGER.warn("[Area] Biome build already active for area {}", areaId);
                        return;
                    }

                    // Create task with pre-computed blocks
                    AreaBuildTask task = new AreaBuildTask(
                        targetLevel,
                        pendingBuild.definition(),
                        blockMap,
                        AreaBuilderTiming.BUILD_MAX_BLOCKS_PER_TICK,
                        this::onBuildComplete
                    );

                    // MED-02 fix: Add async clear step for biome builds when clearFirst is true
                    if (pendingBuild.clearFirst()) {
                        task.withClearStep();
                    }

                    activeBuildTasks.put(areaId, new ActiveBuild(
                        task,
                        targetLevel,
                        pendingBuild.playerId(),
                        pendingBuild.clearFirst(),
                        pendingBuild.forceMultiTick(),
                        0
                    ));

                    DevMod.LOGGER.info("[Area] Started multi-tick biome build for area '{}' ({} blocks pre-computed{})",
                        pendingBuild.definition().name(), blockMap.size(),
                        pendingBuild.clearFirst() ? ", with async clear" : "");
                });
            });
        pendingBiomeFutures.put(areaId, precomputeFuture);
    }

    /**
     * Starts a build with skip capability for resume functionality (C-01 fix).
     * Skips the first N blocks that were already placed in a previous session.
     *
     * @param level         The server level
     * @param definition    The area definition
     * @param player        The player who initiated the build (for feedback)
     * @param forceMultiTick If true, forces multi-tick building
     * @param skipBlocks    Number of blocks to skip (already placed)
     * @param clearFirst    If true, adds a clear step before building (MED-RESUME fix)
     * @return true if build was started successfully, false if it failed
     */
    private boolean startBuildWithSkip(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                                    @Nullable ServerPlayer player, boolean forceMultiTick, int skipBlocks,
                                    boolean clearFirst) {
        // CRIT-02 fix: Validate skipBlocks parameter
        if (skipBlocks < 0) {
            DevMod.LOGGER.error("[Area] Invalid skipBlocks value {} for area '{}' - must be non-negative",
                skipBlocks, definition.name());
            if (player != null) {
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        // MED-02 fix: Use correct estimate for biome builds (volume) vs regular builds (surface)
        int estimatedTotalBlocks = definition.isBiomeGeneration()
            ? AreaBlockMapGenerator.estimateBiomeBlocks(definition)
            : AreaBlockMapGenerator.estimateTotalBlocks(definition);

        if (skipBlocks >= estimatedTotalBlocks) {
            DevMod.LOGGER.error("[Area] skipBlocks ({}) >= estimated total blocks ({}) for area '{}' - nothing to build",
                skipBlocks, estimatedTotalBlocks, definition.name());
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                        "area.message.error_resume_invalid")), true);
                AreaBuilderSounds.playError(level, player);
            }
            return false;
        }

        if (skipBlocks > 0) {
            DevMod.LOGGER.info("[Area] Resuming build with {} blocks already placed ({}% complete)",
                skipBlocks, (skipBlocks * 100) / estimatedTotalBlocks);
        }

        if (player != null) {
            AreaBuilderSounds.playBuildStart(level, player);
        }

        UUID playerId = player != null ? player.getUUID() : null;

        // Biome generation with skip
        if (definition.isBiomeGeneration()) {
            // Pre-compute floor positions for biome generation
            java.util.Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
                Objects.requireNonNull(definition.shape()),
                Objects.requireNonNull(definition.centerPosition()),
                Objects.requireNonNull(definition.dimensions()),
                definition.customShapeNbt()
            );

            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blockMap =
                BiomeAreaGenerator.generateBlockMap(definition, floorPositions);

            if (blockMap.isEmpty()) {
                DevMod.LOGGER.warn("[Area] Biome generation produced no blocks for area '{}' on resume", definition.name());
                if (player != null) {
                    AreaBuilderSounds.playError(level, player);
                }
                return false;
            }

            // Create task with skip capability
            AreaBuildTask task = new AreaBuildTask(
                level,
                definition,
                blockMap,
                AreaBuilderTiming.BUILD_MAX_BLOCKS_PER_TICK,
                this::onBuildComplete,
                skipBlocks
            );

            // MED-RESUME fix: Add clear step if resuming from interrupted clear
            if (clearFirst) {
                task.withClearStep();
            }

            activeBuildTasks.put(definition.id(), new ActiveBuild(task, level, playerId, clearFirst, true, 0));

            DevMod.LOGGER.info("[Area] Resumed biome build for area '{}' (skipping {} blocks, clearFirst={})",
                definition.name(), skipBlocks, clearFirst);
            return true;
        }

        // Standard multi-tick build with skip
        AreaBuildTask task = new AreaBuildTask(
            level,
            definition,
            AreaBuilderTiming.BUILD_MAX_BLOCKS_PER_TICK,
            this::onBuildComplete,
            skipBlocks
        );

        // MED-RESUME fix: Add clear step if resuming from interrupted clear
        if (clearFirst) {
            task.withClearStep();
        }

        activeBuildTasks.put(definition.id(), new ActiveBuild(task, level, playerId, clearFirst, forceMultiTick, 0));

        DevMod.LOGGER.info("[Area] Resumed build for area '{}' (skipping {} blocks, clearFirst={})",
            definition.name(), skipBlocks, clearFirst);
        return true;
    }

    /**
     * Called when a build task completes.
     */
    private void onBuildComplete(AreaBuildTask task) {
        activeBuildTasks.remove(task.getDefinition().id());

        // HIGH-07 fix: Close task to free memory on normal completion
        try {
            task.close();
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Area] Error closing completed build task: {}", e.getMessage());
        }

        DevMod.LOGGER.info("[Area] Multi-tick build complete for '{}': {} blocks placed",
            task.getDefinition().name(), task.getTotalBlocksPlaced());
    }

    /**
     * Ticks all active build tasks.
     * Called from server tick event.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    /**
     * Processes one tick of all active builds.
     */
    public void tick(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }

        // M-04 fix: Periodic cleanup of expired cooldowns (every ~1 minute)
        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            com.devmod.area.network.AreaNetworkHandler.cleanupExpiredCooldowns();
        }

        // Skip if nothing to do
        if (activeBuildTasks.isEmpty() && buildQueue.isEmpty() && pendingBiomeBuilds.isEmpty()) {
            return;
        }

        // MED-04 fix: Check for timed-out pending biome builds
        checkPendingBiomeBuildTimeouts(server);

        Iterator<Map.Entry<UUID, ActiveBuild>> iterator = activeBuildTasks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveBuild> entry = iterator.next();
            ActiveBuild build = entry.getValue();
            @Nullable UUID playerId = build.playerId;

            try {
                boolean completed = build.task.tick();

                // Send progress feedback
                if (!completed && playerId != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        int progress = (int) (build.task.getProgress() * 100);

                        // L-07 fix: Check for all crossed milestones (25%, 50%, 75%, 100%)
                        int[] crossedMilestones = AreaBuilderTiming.getCrossedMilestones(build.lastProgress, progress);
                        if (crossedMilestones.length > 0) {
                            // Re-fetch player to ensure still connected before sound/particle
                            ServerPlayer freshPlayer = server.getPlayerList().getPlayer(playerId);
                            if (freshPlayer != null) {
                                // MED-09 fix: Send IN_PROGRESS status update to client
                                UUID areaId = Objects.requireNonNull(entry.getKey());
                                com.devmod.area.network.AreaNetworkHandler.sendBuildProgress(freshPlayer, areaId, progress);

                                // Play sound/particles for each crossed milestone
                                for (int milestone : crossedMilestones) {
                                    AreaBuilderSounds.playBuildProgress(build.level, freshPlayer, milestone);
                                }

                                // Spawn particles at center (once per check, not per milestone)
                                BlockPos center = build.task.getDefinition().centerPosition();
                                AreaBuilderParticles.spawnBuildActiveParticles(build.level, center);
                            }
                        }

                        build.lastProgress = progress;
                    }
                }

                // Handle completion
                if (completed) {
                    iterator.remove();

                    // H-12 fix: Log BUILD_COMPLETE action to audit log
                    AreaDefinition completedDef = build.task.getDefinition();
                    ServerPlayer completionPlayer = playerId != null ? server.getPlayerList().getPlayer(playerId) : null;
                    com.devmod.area.data.AreaAuditLog.get(server).log(
                        com.devmod.area.data.AreaAuditLog.ActionType.BUILD_COMPLETE,
                        Objects.requireNonNull(completedDef.id()),
                        Objects.requireNonNull(completedDef.name()),
                        completionPlayer,
                        "blocks=" + build.task.getTotalBlocksPlaced()
                    );

                    // Play completion sound and particles
                    if (playerId != null) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            // MED-09 fix: Send COMPLETED status update to client
                            com.devmod.area.network.AreaNetworkHandler.sendBuildCompleted(
                                player, Objects.requireNonNull(completedDef.id()));

                            AreaBuilderSounds.playBuildComplete(build.level, player);
                            AreaBuilderParticles.spawnBuildCompleteParticles(
                                build.level,
                                build.task.getDefinition().centerPosition()
                            );
                        }
                    }
                }
            } catch (Exception e) {
                DevMod.LOGGER.error("[Area] Error ticking build task for area {}: {}",
                    entry.getKey(), e.getMessage());
                // HIGH-03 fix: Close task to prevent memory leak before removing
                try {
                    build.task.close();
                } catch (Exception closeEx) {
                    DevMod.LOGGER.warn("[Area] Error closing failed build task: {}", closeEx.getMessage());
                }
                iterator.remove();
            }
        }

        // Process queued builds when slots become available
        processQueue(server);
    }

    /**
     * MED-04 fix: Checks for timed-out pending biome builds and cancels them.
     * Prevents indefinite hangs if biome precompute fails or takes too long.
     */
    private void checkPendingBiomeBuildTimeouts(@Nonnull MinecraftServer server) {
        if (pendingBiomeBuilds.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        java.util.List<UUID> timedOut = new java.util.ArrayList<>();

        for (Map.Entry<UUID, PendingBiomeBuild> entry : pendingBiomeBuilds.entrySet()) {
            PendingBiomeBuild pending = entry.getValue();
            if (now - pending.startTime() > MAX_BIOME_PRECOMPUTE_MS) {
                timedOut.add(entry.getKey());
            }
        }

        for (UUID areaId : timedOut) {
            PendingBiomeBuild pending = pendingBiomeBuilds.remove(areaId);
            CompletableFuture<?> future = pendingBiomeFutures.remove(areaId);
            if (future != null) {
                future.cancel(true);
            }

            if (pending != null) {
                DevMod.LOGGER.error("[Area] Biome precompute timed out after {}ms for area '{}' ({})",
                    MAX_BIOME_PRECOMPUTE_MS, pending.definition().name(), areaId);

                // Notify player if available
                UUID playerId = pending.playerId();
                if (playerId != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.displayClientMessage(
                            Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                                "area.message.error_biome_timeout")), true);
                        // Get level for error sound
                        ServerLevel level = server.getLevel(Objects.requireNonNull(
                            ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION),
                                Objects.requireNonNull(pending.levelId()))));
                        if (level != null) {
                            AreaBuilderSounds.playError(level, player);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a build is in progress for an area.
     */
    public boolean isBuildingArea(UUID areaId) {
        return activeBuildTasks.containsKey(areaId) || pendingBiomeBuilds.containsKey(areaId);
    }

    /**
     * Gets the progress of a build (0-100).
     */
    public int getBuildProgress(UUID areaId) {
        ActiveBuild build = activeBuildTasks.get(areaId);
        return build != null ? (int) (build.task.getProgress() * 100) : -1;
    }

    /**
     * Cancels a build in progress.
     * Properly cleans up task resources to prevent memory leaks (C-03 fix).
     */
    public boolean cancelBuild(UUID areaId) {
        ActiveBuild removed = activeBuildTasks.remove(areaId);
        if (removed != null) {
            // Clean up task resources to prevent memory leak
            removed.task.close();
            DevMod.LOGGER.info("[Area] Cancelled build for area: {}", areaId);
            return true;
        }
        PendingBiomeBuild pending = pendingBiomeBuilds.remove(areaId);
        if (pending != null) {
            CompletableFuture<?> pendingFuture = pendingBiomeFutures.remove(areaId);
            if (pendingFuture != null) {
                pendingFuture.cancel(true);
            }
            DevMod.LOGGER.info("[Area] Cancelled pending biome build for area: {}", areaId);
            return true;
        }
        return false;
    }

    /**
     * Gets the number of active builds.
     */
    public int getActiveBuildCount() {
        return activeBuildTasks.size();
    }

    private int getInFlightBuildCount() {
        return activeBuildTasks.size() + pendingBiomeBuilds.size();
    }

    // ========================================================================
    // Build Queue Methods
    // ========================================================================

    /**
     * Queues a build for later execution when slots become available.
     *
     * @return true if queued successfully, false if queue is full
     */
    private boolean queueBuild(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
                               @Nullable ServerPlayer player, boolean clearFirst, boolean forceMultiTick) {
        if (buildQueue.size() >= MAX_QUEUE_SIZE) {
            return false;
        }

        // Check if already in queue
        if (isAreaQueued(Objects.requireNonNull(definition.id()))) {
            DevMod.LOGGER.debug("[Area] Area {} already in queue, skipping", definition.id());
            return true; // Already queued counts as success
        }

        buildQueue.offer(new QueuedBuild(
            definition.id(),
            definition,
            level.dimension().location(),
            player != null ? player.getUUID() : null,
            clearFirst,
            forceMultiTick,
            System.currentTimeMillis()
        ));
        return true;
    }

    /**
     * Gets the queue position of an area (0-indexed).
     *
     * @return Queue position, or -1 if not in queue
     */
    public int getQueuePosition(@Nonnull UUID areaId) {
        int pos = 0;
        for (QueuedBuild qb : buildQueue) {
            if (qb.areaId().equals(areaId)) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    /**
     * Checks if an area is currently queued.
     */
    public boolean isAreaQueued(@Nonnull UUID areaId) {
        return buildQueue.stream().anyMatch(qb -> qb.areaId().equals(areaId));
    }

    /**
     * Gets the number of queued builds.
     */
    public int getQueuedBuildCount() {
        return buildQueue.size();
    }

    /**
     * Removes an area from the queue.
     *
     * @return true if removed, false if not found
     */
    public boolean removeFromQueue(@Nonnull UUID areaId) {
        return buildQueue.removeIf(qb -> qb.areaId().equals(areaId));
    }

    /**
     * Processes queued builds when slots become available.
     * Called at end of tick().
     */
    private void processQueue(@Nonnull MinecraftServer server) {
        com.devmod.area.data.AreaRegistry areaRegistry = com.devmod.area.data.AreaRegistry.get(server);

        int attempts = buildQueue.size();
        int processed = 0;

        while (getInFlightBuildCount() < MAX_CONCURRENT_BUILDS && !buildQueue.isEmpty() && processed < attempts) {
            QueuedBuild next = buildQueue.poll();
            if (next == null) break;
            processed++;

            // SEC-08 fix: Defer queued builds if a restore is in progress for this area
            if (AreaSnapshotManager.INSTANCE.isRestoringArea(next.areaId())) {
                DevMod.LOGGER.info("[Area] Queued build deferred: restore in progress for area {}", next.areaId());
                buildQueue.offer(next);
                continue;
            }

            AreaDefinition definition = areaRegistry.getArea(next.areaId()).orElse(null);
            if (definition == null) {
                DevMod.LOGGER.warn("[Area] Queued build skipped: area {} not found", next.areaId());
                // MED-05 fix: Notify player when queued build is skipped
                notifyQueuedBuildSkipped(server, next.playerId(), "area.message.error_area_not_found");
                continue;
            }

            ResourceLocation levelId = definition.dimensionId();
            ServerLevel level = server.getLevel(Objects.requireNonNull(
                ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), Objects.requireNonNull(levelId))));
            if (level == null) {
                DevMod.LOGGER.warn("[Area] Queued build skipped: dimension {} not loaded", levelId);
                // MED-05 fix: Notify player when queued build is skipped due to dimension unload
                notifyQueuedBuildSkipped(server, next.playerId(), "area.message.error_dimension_unloaded");
                continue;
            }

            UUID playerId = next.playerId();
            ServerPlayer player = playerId != null
                ? server.getPlayerList().getPlayer(playerId)
                : null;

            DevMod.LOGGER.info("[Area] Starting queued build: {}", definition.name());

            // Start the build (won't recurse into queue since we have slots)
            BuildStartResult result = startBuild(level, definition, player, next.clearFirst(), next.forceMultiTick());

            // MED-03 fix: Send STARTED notification to client when queued build starts
            if (player != null && (result == BuildStartResult.STARTED_MULTI_TICK)) {
                com.devmod.area.network.AreaNetworkHandler.sendBuildStarted(player, next.areaId());
            } else if (player != null && result == BuildStartResult.STARTED_IMMEDIATE) {
                // Immediate builds complete synchronously, send COMPLETED
                com.devmod.area.network.AreaNetworkHandler.sendBuildCompleted(player, next.areaId());
            }
        }
    }

    /**
     * MED-05 fix: Notifies a player that their queued build was skipped.
     *
     * @param server    The server instance
     * @param playerId  The player UUID (may be null)
     * @param messageKey The translation key for the error message
     */
    private void notifyQueuedBuildSkipped(@Nonnull MinecraftServer server, @Nullable UUID playerId, @Nonnull String messageKey) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.displayClientMessage(
                Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(messageKey)), true);
        }
    }

    /**
     * Cancels all active builds initiated by a specific player.
     * Called on player disconnect to prevent orphan tasks.
     * Properly cleans up task resources to prevent memory leaks (C-03 fix).
     *
     * @param playerId The player UUID (null-safe)
     */
    public void cancelPlayerBuilds(@Nullable UUID playerId) {
        if (playerId == null) {
            return;
        }

        // Remove from active builds with proper cleanup (H-02 fix: use forEach instead of removeIf)
        // HIGH-02 fix: Add null safety for concurrent map iteration
        java.util.List<UUID> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<UUID, ActiveBuild> entry : activeBuildTasks.entrySet()) {
            ActiveBuild build = entry.getValue();
            if (build != null && playerId.equals(build.playerId)) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID areaId : toRemove) {
            ActiveBuild removed = activeBuildTasks.remove(areaId);
            if (removed != null) {
                removed.task.close(); // Clean up resources
                DevMod.LOGGER.info("[Area] Cancelled orphan build for disconnected player: {}", playerId);
            }
        }

        // Remove pending biome builds
        // HIGH-02 fix: Add null safety for concurrent map iteration
        java.util.List<UUID> pendingRemovals = new java.util.ArrayList<>();
        for (Map.Entry<UUID, PendingBiomeBuild> entry : pendingBiomeBuilds.entrySet()) {
            PendingBiomeBuild pending = entry.getValue();
            if (pending != null && playerId.equals(pending.playerId())) {
                pendingRemovals.add(entry.getKey());
            }
        }
        for (UUID areaId : pendingRemovals) {
            pendingBiomeBuilds.remove(areaId);
            CompletableFuture<?> pendingFuture = pendingBiomeFutures.remove(areaId);
            if (pendingFuture != null) {
                pendingFuture.cancel(true);
            }
        }

        // Remove from queue
        buildQueue.removeIf(qb -> playerId.equals(qb.playerId()));
    }

    // ========================================================================
    // Pause/Resume Methods
    // ========================================================================

    /**
     * Pauses an active build, saving its state for later resumption.
     *
     * @param areaId The area ID to pause
     * @param server The server instance
     * @return true if build was paused, false if not found or couldn't be saved
     */
    public boolean pauseBuild(@Nonnull UUID areaId, @Nonnull MinecraftServer server) {
        Objects.requireNonNull(areaId);
        Objects.requireNonNull(server);

        ActiveBuild build = activeBuildTasks.remove(areaId);
        if (build == null) {
            DevMod.LOGGER.debug("[Area] Cannot pause build {}: not found", areaId);
            return false;
        }

        // Create state from active build BEFORE closing (need task data)
        BuildTaskState state = BuildTaskState.fromActiveBuild(
            build.task,
            Objects.requireNonNull(build.level.dimension().location()),
            build.playerId,
            build.clearFirst,
            build.forceMultiTick,
            true // isPaused = true (explicit pause)
        );

        // LOW-02 fix: Clean up task resources to prevent memory leak
        // Must call close() AFTER creating state since fromActiveBuild reads task data
        build.task.close();

        // Save to registry
        AreaBuildStateRegistry stateRegistry = AreaBuildStateRegistry.get(server);
        if (!stateRegistry.saveState(state)) {
            DevMod.LOGGER.error("[Area] Failed to save pause state for area {}", areaId);
            return false;
        }

        DevMod.LOGGER.info("[Area] Paused build for area {} at {}% progress",
            areaId, state.getProgressPercent());
        return true;
    }

    /**
     * Resumes a paused build from saved state.
     *
     * @param server The server instance
     * @param areaId The area ID to resume
     * @param player The player requesting resume (for feedback, may differ from original)
     * @return true if build was resumed, false if not found or couldn't be started
     */
    public boolean resumeBuild(@Nonnull MinecraftServer server, @Nonnull UUID areaId,
                               @Nullable ServerPlayer player) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(areaId);

        // Check if already building
        if (isBuildingArea(areaId)) {
            DevMod.LOGGER.warn("[Area] Cannot resume build {}: already in progress", areaId);
            return false;
        }

        // Get saved state
        AreaBuildStateRegistry stateRegistry = AreaBuildStateRegistry.get(server);
        BuildTaskState state = stateRegistry.getState(areaId).orElse(null);
        if (state == null) {
            DevMod.LOGGER.warn("[Area] Cannot resume build {}: no saved state", areaId);
            return false;
        }

        // Get area definition
        com.devmod.area.data.AreaRegistry areaRegistry = com.devmod.area.data.AreaRegistry.get(server);
        AreaDefinition definition = areaRegistry.getArea(areaId).orElse(null);
        if (definition == null) {
            DevMod.LOGGER.warn("[Area] Cannot resume build {}: area definition not found", areaId);
            stateRegistry.removeState(areaId); // Clean up orphan state
            return false;
        }

        // H-02 fix: Check if area definition was modified since pause
        if (state.areaRevision() > 0 && definition.revision() != state.areaRevision()) {
            DevMod.LOGGER.warn("[Area] Cannot resume build {}: area definition was modified (revision {} -> {})",
                areaId, state.areaRevision(), definition.revision());
            stateRegistry.removeState(areaId); // Clean up stale state
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                        "area.message.error_resume_modified")), true);
            }
            return false;
        }

        // Get the level
        ServerLevel level = server.getLevel(Objects.requireNonNull(
            ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), Objects.requireNonNull(state.dimensionId()))));
        if (level == null) {
            DevMod.LOGGER.warn("[Area] Cannot resume build {}: dimension {} not loaded",
                areaId, state.dimensionId());
            return false;
        }

        // Check concurrent build limit
        if (getInFlightBuildCount() >= MAX_CONCURRENT_BUILDS) {
            DevMod.LOGGER.info("[Area] Cannot resume build {}: at max concurrent builds", areaId);
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(net.minecraft.network.chat.Component.translatable(
                        "area.message.max_concurrent")), true);
            }
            return false;
        }

        // Start the build with skip to resume from saved position (C-01 fix)
        // MED-RESUME fix: Recalculate skipBlocks excluding clear step blocks and determine if clear needed
        int skipBlocks = state.blocksPlaced();
        boolean needsClear = false;
        if (state.clearFirst() && !"clear".equals(state.currentStepName())) {
            // The clear step was completed, subtract its blocks from skipBlocks
            // since the resumed build won't include a clear step
            int clearBlocks = AreaBlockMapGenerator.estimateClearBlocks(definition);
            skipBlocks = Math.max(0, skipBlocks - clearBlocks);
            DevMod.LOGGER.info("[Area] Resume: adjusting skipBlocks from {} to {} (excluding {} clear blocks)",
                state.blocksPlaced(), skipBlocks, clearBlocks);
        } else if (state.clearFirst() && "clear".equals(state.currentStepName())) {
            // Was interrupted during clear - restart from beginning with clear step (clear is idempotent)
            skipBlocks = 0;
            needsClear = true;
            DevMod.LOGGER.info("[Area] Resume: restarting from beginning with clear step (interrupted during clear)");
        }

        DevMod.LOGGER.info("[Area] Resuming build for area {} from {}% (skipping {} blocks, clearFirst={})",
            areaId, state.getProgressPercent(), skipBlocks, needsClear);

        // Use the skip capability to resume from where we left off
        // MED-01 fix: Only remove state AFTER successful start to preserve progress on failure
        boolean started = startBuildWithSkip(level, definition, player, state.forceMultiTick(), skipBlocks, needsClear);

        if (started) {
            stateRegistry.removeState(areaId);
        } else {
            DevMod.LOGGER.warn("[Area] Resume failed for area {}, preserving saved state", areaId);
        }

        return started;
    }

    /**
     * Saves all active builds to persistent storage.
     * Called during server shutdown to preserve build progress.
     *
     * @param server The server instance
     * @return Number of builds saved
     */
    public int saveAllActiveBuilds(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);

        if (activeBuildTasks.isEmpty()) {
            return 0;
        }

        AreaBuildStateRegistry stateRegistry = AreaBuildStateRegistry.get(server);
        int saved = 0;

        for (Map.Entry<UUID, ActiveBuild> entry : activeBuildTasks.entrySet()) {
            ActiveBuild build = entry.getValue();

            BuildTaskState state = BuildTaskState.fromActiveBuild(
                build.task,
                Objects.requireNonNull(build.level.dimension().location()),
                build.playerId,
                build.clearFirst,
                build.forceMultiTick,
                false // isPaused = false (shutdown save, not explicit pause)
            );

            if (stateRegistry.saveState(state)) {
                saved++;
            }
        }

        DevMod.LOGGER.info("[Area] Saved {} build states for server shutdown", saved);
        return saved;
    }

    /**
     * Resumes builds that were saved during previous server shutdown.
     * Called during server startup.
     *
     * @param server The server instance
     * @return Number of builds resumed
     */
    public int resumeShutdownBuilds(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);

        AreaBuildStateRegistry stateRegistry = AreaBuildStateRegistry.get(server);
        java.util.List<BuildTaskState> shutdownStates = stateRegistry.getShutdownSavedStates();

        if (shutdownStates.isEmpty()) {
            return 0;
        }

        DevMod.LOGGER.info("[Area] Found {} builds to resume from shutdown", shutdownStates.size());

        int resumed = 0;
        for (BuildTaskState state : shutdownStates) {
            if (resumeBuild(server, Objects.requireNonNull(state.areaId()), null)) {
                resumed++;
            }
        }

        DevMod.LOGGER.info("[Area] Resumed {} builds from shutdown", resumed);
        return resumed;
    }

    /**
     * Clears all active builds and queued builds (used on server shutdown).
     * Properly cleans up task resources to prevent memory leaks (C-03 fix).
     */
    public void cleanup() {
        int activeCount = activeBuildTasks.size();
        int pendingCount = pendingBiomeBuilds.size();
        int queuedCount = buildQueue.size();

        // Clean up all active tasks before clearing
        for (ActiveBuild build : activeBuildTasks.values()) {
            build.task.close();
        }

        activeBuildTasks.clear();
        for (CompletableFuture<?> pendingFuture : pendingBiomeFutures.values()) {
            pendingFuture.cancel(true);
        }
        pendingBiomeFutures.clear();
        pendingBiomeBuilds.clear();
        buildQueue.clear();
        if (activeCount > 0 || pendingCount > 0 || queuedCount > 0) {
            DevMod.LOGGER.info("[Area] Cleaned up {} active, {} pending, and {} queued build tasks",
                activeCount, pendingCount, queuedCount);
        }
    }

    /**
     * Holds state for a pending biome build waiting on async precompute.
     */
    private record PendingBiomeBuild(
        @Nonnull AreaDefinition definition,
        @Nonnull ResourceLocation levelId,
        @Nullable UUID playerId,
        boolean clearFirst,
        boolean forceMultiTick,
        long startTime
    ) {}

    /**
     * Holds state for an active build.
     */
    private static class ActiveBuild {
        final @Nonnull AreaBuildTask task;
        final @Nonnull ServerLevel level;
        final @Nullable UUID playerId;
        final boolean clearFirst;
        final boolean forceMultiTick;
        volatile int lastProgress;

        ActiveBuild(@Nonnull AreaBuildTask task, @Nonnull ServerLevel level,
                    @Nullable UUID playerId, boolean clearFirst, boolean forceMultiTick, int lastProgress) {
            this.task = Objects.requireNonNull(task, "task");
            this.level = Objects.requireNonNull(level, "level");
            this.playerId = playerId;
            this.clearFirst = clearFirst;
            this.forceMultiTick = forceMultiTick;
            this.lastProgress = lastProgress;
        }
    }
}

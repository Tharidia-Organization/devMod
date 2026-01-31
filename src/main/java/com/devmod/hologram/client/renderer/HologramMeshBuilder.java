package com.devmod.hologram.client.renderer;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.Level;

import com.devmod.hologram.data.HologramFilter;

/**
 * Async builder for hologram meshes.
 * Uses a dedicated background thread and semaphore to limit concurrent builds.
 *
 * <p>This prevents blocking the render thread during expensive terrain scanning
 * and greedy meshing operations.
 */
public final class HologramMeshBuilder {
    private HologramMeshBuilder() {}

    /** Single background thread for mesh building. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DevMod-Hologram-Mesh-Builder");
        thread.setDaemon(true);
        return thread;
    });

    /** Semaphore to limit concurrent builds (only one at a time). */
    private static final Semaphore BUILD_SEMAPHORE = new Semaphore(1);

    /**
     * Build a hologram mesh asynchronously.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @return A future that completes with the built mesh
     */
    @Nonnull
    public static CompletableFuture<HologramMesh> buildAsync(@Nonnull Level level,
                                                              int minX, int maxX, int minZ, int maxZ) {
        return buildAsync(level, minX, maxX, minZ, maxZ, null, false);
    }

    /**
     * Build a hologram mesh asynchronously with filter support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting
     * @param highlightOnly If true, only show blocks matching filters
     * @return A future that completes with the built mesh
     */
    @Nonnull
    public static CompletableFuture<HologramMesh> buildAsync(@Nonnull Level level,
                                                              int minX, int maxX, int minZ, int maxZ,
                                                              @Nullable EnumSet<HologramFilter> filters,
                                                              boolean highlightOnly) {
        return buildAsync(level, minX, maxX, minZ, maxZ, filters, highlightOnly, false);
    }

    /**
     * Build a hologram mesh asynchronously with filter and texture support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting
     * @param highlightOnly If true, only show blocks matching filters
     * @param texturedMode If true, calculate UV coordinates for block textures
     * @return A future that completes with the built mesh
     */
    @Nonnull
    public static CompletableFuture<HologramMesh> buildAsync(@Nonnull Level level,
                                                              int minX, int maxX, int minZ, int maxZ,
                                                              @Nullable EnumSet<HologramFilter> filters,
                                                              boolean highlightOnly,
                                                              boolean texturedMode) {
        return buildAsync(level, minX, maxX, minZ, maxZ, filters, highlightOnly, texturedMode, false, 0, 1);
    }

    /**
     * Build a hologram mesh asynchronously with filter, texture, and Y-slice support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting
     * @param highlightOnly If true, only show blocks matching filters
     * @param texturedMode If true, calculate UV coordinates for block textures
     * @param ySliceEnabled If true, only show blocks within the Y-slice range
     * @param ySliceLevel The center Y level of the slice
     * @param ySliceThickness The thickness of the slice (number of layers)
     * @return A future that completes with the built mesh
     */
    @Nonnull
    public static CompletableFuture<HologramMesh> buildAsync(@Nonnull Level level,
                                                              int minX, int maxX, int minZ, int maxZ,
                                                              @Nullable EnumSet<HologramFilter> filters,
                                                              boolean highlightOnly,
                                                              boolean texturedMode,
                                                              boolean ySliceEnabled,
                                                              int ySliceLevel,
                                                              int ySliceThickness) {
        return CompletableFuture.supplyAsync(() -> {
            boolean acquired = BUILD_SEMAPHORE.tryAcquire();
            try {
                return HologramMesh.build(level, minX, maxX, minZ, maxZ, filters, highlightOnly, texturedMode,
                                         ySliceEnabled, ySliceLevel, ySliceThickness);
            } finally {
                if (acquired) {
                    BUILD_SEMAPHORE.release();
                }
            }
        }, EXECUTOR);
    }

    /**
     * Shutdown the executor service.
     * Call during mod shutdown.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}

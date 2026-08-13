package com.devmod.hologram.client.renderer;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.Level;

import com.devmod.hologram.data.HologramFilter;

/**
 * Async builder for hologram meshes.
 * Uses a single dedicated background thread, which is what serializes builds.
 *
 * <p>This prevents blocking the render thread during expensive greedy meshing.
 * The level itself is read up front on the calling thread; see
 * {@link HologramRegionSnapshot}.
 */
public final class HologramMeshBuilder {
    private HologramMeshBuilder() {}

    /** Single background thread for mesh building. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DevMod-Hologram-Mesh-Builder");
        thread.setDaemon(true);
        return thread;
    });

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
     * <p>Must be called on the client thread: the region is snapshotted synchronously
     * before the build is dispatched.
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
        // Find actual Y bounds (or use Y-slice bounds if enabled)
        int minY, maxY;
        if (ySliceEnabled) {
            minY = ySliceLevel - ySliceThickness / 2;
            maxY = ySliceLevel + ySliceThickness / 2;
        } else {
            int[] yBounds = HologramRegionSnapshot.findSurfaceBounds(level, minX, maxX, minZ, maxZ);
            minY = yBounds[0];
            maxY = yBounds[1];
        }

        HologramRegionSnapshot region = HologramRegionSnapshot.capture(level, minX, maxX, minZ, maxZ, minY, maxY);

        return CompletableFuture.supplyAsync(
            () -> HologramMesh.build(region, filters, highlightOnly, texturedMode), EXECUTOR);
    }

    /**
     * Shutdown the executor service.
     * Call during mod shutdown.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}

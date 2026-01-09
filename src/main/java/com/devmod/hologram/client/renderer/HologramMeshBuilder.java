package com.devmod.hologram.client.renderer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.annotation.Nonnull;

import net.minecraft.world.level.Level;

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
        thread.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
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
        return CompletableFuture.supplyAsync(() -> {
            // Try to acquire semaphore (non-blocking)
            // If another build is in progress, just build anyway but sequentially
            boolean acquired = BUILD_SEMAPHORE.tryAcquire();
            try {
                return new HologramMesh(level, minX, maxX, minZ, maxZ);
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

package com.devmod.arena.metrics;

import com.devmod.arena.builder.BuildTransaction;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Ensures baseline metric definitions match legacy (build_ms, entities_residual, blocks_residual).
 */
public final class MetricsCompatibilityLayer {

    private MetricsCompatibilityLayer() {}

    /**
     * Measures build_ms and block counts using the same perimeter as legacy (start before build, stop after last block).
     */
    public record BuildMetrics(long buildMs, long totalBlockPlacements, int trackedBlocks) {}

    public static BuildMetrics measureBuild(Runnable buildAction, BuildTransaction transaction) {
        long start = System.nanoTime();
        buildAction.run();
        long stop = System.nanoTime();
        return new BuildMetrics(
            (stop - start) / 1_000_000,
            transaction.getTotalBlockPlacements(),
            transaction.getBlockCount()
        );
    }

    /**
     * Residual metrics counted post-cleanup inside arena bounds.
     */
    public record ResidualMetrics(int entitiesResidual, int blocksResidual) {}

    public static ResidualMetrics measureResiduals(ServerLevel level, AABB bounds, int expectedNonAirBlocks) {
        int entities = countEntities(level, bounds);
        int nonAirBlocks = countNonAirBlocks(level, bounds);
        return new ResidualMetrics(entities, nonAirBlocks - expectedNonAirBlocks);
    }

    private static int countEntities(ServerLevel level, AABB bounds) {
        List<Entity> entities = level.getEntities((Entity) null, Objects.requireNonNull(bounds),
            e -> !e.isRemoved() && !(e instanceof net.minecraft.world.entity.player.Player));
        return entities.size();
    }

    private static int countNonAirBlocks(ServerLevel level, AABB bounds) {
        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.floor(bounds.maxX);
        int maxY = (int) Math.floor(bounds.maxY);
        int maxZ = (int) Math.floor(bounds.maxZ);

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}

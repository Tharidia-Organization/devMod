package com.devmod.hologram.client.renderer;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A detached copy of the block states in a scan region.
 *
 * <p>Both factory methods read the level and must be called on the client thread: the
 * client applies chunk loads and block updates on that thread, so a background reader can
 * otherwise observe a chunk section being swapped mid-scan. Once captured, the snapshot is
 * immutable and safe to read from the mesh builder thread.
 */
public final class HologramRegionSnapshot {
    private final BlockState[] states;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final int depth;

    private HologramRegionSnapshot(@Nonnull BlockState[] states, int minX, int minY, int minZ,
                                   int width, int height, int depth) {
        this.states = states;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    /**
     * Find the Y range spanned by the topmost non-air block of each column.
     *
     * @return {minY, maxY}, defaulting to {64, 64} when the region holds no blocks
     */
    @Nonnull
    public static int[] findSurfaceBounds(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ) {
        int foundMinY = level.getMaxBuildHeight();
        int foundMaxY = level.getMinBuildHeight();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // WORLD_SURFACE tracks the first free Y above the topmost non-air block, so
                // one lookup replaces a full-column scan. The state is still verified because
                // unloaded chunks report a heightmap of zero rather than "no terrain".
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (y < level.getMinBuildHeight() || level.getBlockState(pos.set(x, y, z)).isAir()) {
                    continue;
                }
                foundMinY = Math.min(foundMinY, y);
                foundMaxY = Math.max(foundMaxY, y);
            }
        }

        // If no blocks found, default to Y=64
        if (foundMinY > foundMaxY) {
            foundMinY = 64;
            foundMaxY = 64;
        }

        return new int[]{foundMinY, foundMaxY};
    }

    /**
     * Copy every block state in the given cuboid.
     */
    @Nonnull
    public static HologramRegionSnapshot capture(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                                 int minY, int maxY) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int height = maxY - minY + 1;

        BlockState[] states = new BlockState[width * height * depth];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    states[index++] = level.getBlockState(pos.set(minX + x, minY + y, minZ + z));
                }
            }
        }

        return new HologramRegionSnapshot(states, minX, minY, minZ, width, height, depth);
    }

    /**
     * Get the captured state at an absolute position, or air if it lies outside the region.
     */
    @Nonnull
    public BlockState getBlockState(int x, int y, int z) {
        int localX = x - minX;
        int localY = y - minY;
        int localZ = z - minZ;
        if (localX < 0 || localX >= width || localY < 0 || localY >= height || localZ < 0 || localZ >= depth) {
            return Blocks.AIR.defaultBlockState();
        }
        return states[(localX * height + localY) * depth + localZ];
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
}

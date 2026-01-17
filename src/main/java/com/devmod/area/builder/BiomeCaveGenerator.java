package com.devmod.area.builder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Generates caves in biome terrain using 3D Simplex noise.
 * Uses dual noise for worm-like tunnel shapes.
 * Extracted from BiomeAreaGenerator for better modularity.
 */
public final class BiomeCaveGenerator {

    private BiomeCaveGenerator() {}

    // ============================================================================
    // CAVE GENERATION CONSTANTS
    // ============================================================================

    /** Noise scale for cave generation (3D) */
    private static final double CAVE_NOISE_SCALE = 0.08;

    /** Secondary cave noise scale for worm-like tunnels */
    private static final double CAVE_NOISE_SCALE_SECONDARY = 0.15;

    /** Threshold above which caves are carved (higher = fewer caves) */
    private static final double CAVE_THRESHOLD = 0.55;

    /** Maximum height above floor for cave generation */
    private static final int MAX_CAVE_HEIGHT = 40;

    /** Minimum Y offset from floor for caves (don't carve into floor) */
    private static final int MIN_CAVE_Y_OFFSET = 2;

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Carves caves into a pre-computed block map using 3D Simplex noise.
     * Uses dual noise for worm-like tunnel shapes.
     *
     * @param blockMap       The pre-computed block map to carve into
     * @param floorPositions The floor positions defining the area
     * @param noise          The noise generator
     * @param baseY          The base Y level of the area
     * @param maxHeight      The maximum height of the area
     * @return Number of blocks carved (replaced with cave air)
     */
    public static int carveCavesInBlockMap(
        Map<BlockPos, BlockState> blockMap,
        Set<BlockPos> floorPositions,
        SimplexNoise noise,
        int baseY,
        int maxHeight
    ) {
        int blocksCarved = 0;
        int caveMaxY = Math.min(baseY + MAX_CAVE_HEIGHT, baseY + maxHeight - 2);

        for (BlockPos floorPos : floorPositions) {
            int x = floorPos.getX();
            int z = floorPos.getZ();

            // Carve from just above floor to max cave height
            for (int y = baseY + MIN_CAVE_Y_OFFSET; y <= caveMaxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);

                // Skip if no solid block here
                if (!blockMap.containsKey(pos)) continue;
                BlockState existing = blockMap.get(pos);
                if (existing == null || existing.isAir()) continue;

                // Calculate cave noise and check if should carve
                if (shouldCarveCave(x, y, z, caveMaxY, noise, existing)) {
                    blockMap.put(pos, Objects.requireNonNull(Blocks.CAVE_AIR.defaultBlockState()));
                    blocksCarved++;
                }
            }
        }

        return blocksCarved;
    }

    /**
     * Carves caves directly in a level using 3D Simplex noise.
     * Used for immediate builds (non-multi-tick).
     *
     * @param level          The server level
     * @param floorPositions The floor positions defining the area
     * @param noise          The noise generator
     * @param baseY          The base Y level of the area
     * @param maxHeight      The maximum height of the area
     * @return Number of blocks carved (replaced with cave air)
     */
    public static int carveCavesInLevel(
        ServerLevel level,
        Set<BlockPos> floorPositions,
        SimplexNoise noise,
        int baseY,
        int maxHeight
    ) {
        int blocksCarved = 0;
        int caveMaxY = Math.min(baseY + MAX_CAVE_HEIGHT, baseY + maxHeight - 2);

        for (BlockPos floorPos : floorPositions) {
            int x = floorPos.getX();
            int z = floorPos.getZ();

            // Carve from just above floor to max cave height
            for (int y = baseY + MIN_CAVE_Y_OFFSET; y <= caveMaxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);

                // Skip if air
                BlockState existing = level.getBlockState(pos);
                if (existing.isAir()) continue;

                // Calculate cave noise and check if should carve
                if (shouldCarveCave(x, y, z, caveMaxY, noise, existing)) {
                    level.setBlock(pos, Objects.requireNonNull(Blocks.CAVE_AIR.defaultBlockState()), 2);
                    blocksCarved++;
                }
            }
        }

        return blocksCarved;
    }

    // ============================================================================
    // INTERNAL METHODS
    // ============================================================================

    /**
     * Determines if a cave should be carved at the given position.
     */
    private static boolean shouldCarveCave(int x, int y, int z, int caveMaxY,
                                           SimplexNoise noise, BlockState existing) {
        // Don't carve into water (preserve lakes)
        if (existing.getBlock() == Blocks.WATER) {
            return false;
        }

        // Calculate 3D noise for cave shape
        // Primary noise creates larger caverns
        double n1 = noise.getValue(
            x * CAVE_NOISE_SCALE + 5000,
            y * CAVE_NOISE_SCALE * 1.5,  // Stretch vertically for taller caves
            z * CAVE_NOISE_SCALE + 5000
        );

        // Secondary noise creates connecting tunnels
        double n2 = noise.getValue(
            x * CAVE_NOISE_SCALE_SECONDARY + 10000,
            y * CAVE_NOISE_SCALE_SECONDARY * 0.8,
            z * CAVE_NOISE_SCALE_SECONDARY + 10000
        );

        // Combine noises - either can create a cave
        // This creates interconnected cave systems
        double combined = Math.max(n1, n2 * 0.9);

        // Apply depth-based threshold adjustment
        // Caves are more likely deeper underground
        int depthFromSurface = caveMaxY - y;
        double depthBonus = depthFromSurface * 0.005; // Slight increase at depth
        double threshold = CAVE_THRESHOLD - depthBonus;

        // Carve if noise exceeds threshold
        return combined > threshold;
    }
}

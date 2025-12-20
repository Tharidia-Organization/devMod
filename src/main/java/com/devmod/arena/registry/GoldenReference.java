package com.devmod.arena.registry;

import java.util.List;

/**
 * Golden reference data for default_flat_64.
 */
public record GoldenReference(
    Bounds arenaBounds,
    Bounds floorBounds,
    Bounds ceilingBounds,
    int floorBlocks,
    int wallBlocks,
    int ceilingBlocks,
    int underfloorBlocks,
    int totalBlocks,
    List<int[]> spawnSlots
) {
    public static GoldenReference defaultFlat64() {
        Bounds arena = new Bounds(-32, 64, -32, 31, 74, 31, 0, 0);
        Bounds floor = new Bounds(-32, 64, -32, 31, 64, 31, 0, 0);
        Bounds ceiling = new Bounds(-32, 74, -32, 31, 74, 31, 0, 0);

        int floorBlocks = 64 * 64 * 1;
        int wallBlocks = 2520; // 2*(sizeX + sizeZ - 2) * height = 252 * 10
        int ceilingBlocks = 64 * 64 * 1;
        int underfloorBlocks = 64 * 64 * 3;
        int total = floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks; // 23_000

        List<int[]> slots = List.of(
            new int[]{0, 65, 0},
            new int[]{10, 65, 0},
            new int[]{-10, 65, 0},
            new int[]{20, 65, 20}
        );

        return new GoldenReference(arena, floor, ceiling, floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, total, slots);
    }
}

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
    int hazardBlocks,
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
        int hazardBlocks = 0;
        int total = floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks + hazardBlocks; // 23_000

        List<int[]> slots = List.of(
            new int[]{0, 65, 0},
            new int[]{10, 65, 0},
            new int[]{-10, 65, 0},
            new int[]{0, 65, 10},
            new int[]{0, 65, -10},
            new int[]{20, 65, 20},
            new int[]{-20, 65, 20},
            new int[]{20, 65, -20},
            new int[]{-20, 65, -20},
            new int[]{25, 65, 0},
            new int[]{-25, 65, 0},
            new int[]{0, 65, 25},
            new int[]{0, 65, -25},
            new int[]{14, 65, -8},
            new int[]{-14, 65, 8}
        );

        return new GoldenReference(arena, floor, ceiling, floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks, total, slots);
    }

    /**
     * Golden reference for boss_ring_80 with a lava ring hazard.
     */
    public static GoldenReference bossRing80() {
        Bounds arena = new Bounds(-40, 64, -40, 39, 76, 39, 0, 0);
        Bounds floor = new Bounds(-40, 64, -40, 39, 64, 39, 0, 0);
        Bounds ceiling = new Bounds(-40, 76, -40, 39, 76, 39, 0, 0);

        int size = 80;
        int floorBlocks = size * size * 1; // 6,400
        int wallBlocks = (2 * (size + size - 2)) * 12; // perimeter * height = 316 * 12 = 3,792
        int ceilingBlocks = size * size * 1; // 6,400
        int underfloorBlocks = size * size * 3; // 19,200
        int hazardBlocks = (int) Math.round(Math.PI * ((32 * 32) - (30 * 30))); // ~390
        int total = floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks + hazardBlocks;

        List<int[]> slots = List.of(
            new int[]{0, 65, 0},       // player
            new int[]{-30, 65, 0},     // melee
            new int[]{30, 65, 0},      // ranged
            new int[]{35, 65, 35},     // corner
            new int[]{0, 65, 30}       // boss
        );

        return new GoldenReference(arena, floor, ceiling, floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks, total, slots);
    }
}

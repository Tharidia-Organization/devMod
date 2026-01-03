package com.devmod.arena.registry;

import java.util.List;

public record GoldenReference(
    Bounds arenaBounds,
    Bounds floorBounds,
    Bounds ceilingBounds,
    int floorBlocks,
    int wallBlocks,
    int ceilingBlocks,
    int underfloorBlocks,
    int hazardBlocks,
    int lightingBlocks,
    int totalBlocks,
    List<int[]> spawnSlots
) {
    public static GoldenReference defaultFlat64() {
        Bounds arena = new Bounds(-32, 64, -32, 31, 74, 31, 0, 0);
        Bounds floor = new Bounds(-32, 64, -32, 31, 64, 31, 0, 0);
        Bounds ceiling = new Bounds(-32, 74, -32, 31, 74, 31, 0, 0);

        int floorBlocks = 64 * 64 * 1;
        int wallBlocks = 2268; // 2*(sizeX + sizeZ - 2) * (height - overlaps) = 252 * 9
        int ceilingBlocks = 64 * 64 * 1;
        int underfloorBlocks = 64 * 64 * 3;
        int hazardBlocks = 0;
        // Lighting: blockLight=10, spacing=12, grid=5x5=25 ambient lights
        int lightingBlocks = 25;
        int total = floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks + hazardBlocks + lightingBlocks; // 22_773

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

        return new GoldenReference(arena, floor, ceiling, floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks, lightingBlocks, total, slots);
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
        int wallBlocks = (2 * (size + size - 2)) * 12; // perimeter * (height - floor overlap) = 316 * 12 = 3,792
        int ceilingBlocks = size * size * 1; // 6,400
        int underfloorBlocks = size * size * 3; // 19,200
        int hazardBlocks = (int) Math.round(Math.PI * ((32 * 32) - (30 * 30))); // ~390
        // Lighting: blockLight=12, spacing=8, grid=10x10=100 ambient + 1 explicit
        int lightingBlocks = 101;
        int total = floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks + hazardBlocks + lightingBlocks;

        List<int[]> slots = List.of(
            new int[]{0, 65, 0},       // center, player
            new int[]{-25, 65, 0},     // melee
            new int[]{25, 65, 0},      // ranged
            new int[]{0, 65, -25},     // melee
            new int[]{20, 65, 20},     // corner
            new int[]{-20, 65, 20},    // corner
            new int[]{20, 65, -20},    // corner
            new int[]{-20, 65, -20},   // corner
            new int[]{22, 65, 12},     // melee
            new int[]{-22, 65, 12},    // melee
            new int[]{22, 65, -12},    // ranged
            new int[]{-22, 65, -12},   // ranged
            new int[]{12, 65, 22},     // ranged
            new int[]{-12, 65, 22},    // ranged
            new int[]{0, 65, 25}       // boss
        );

        return new GoldenReference(arena, floor, ceiling, floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks, lightingBlocks, total, slots);
    }
}

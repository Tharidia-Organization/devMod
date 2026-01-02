package com.devmod.arena.builder;

/**
 * Block count breakdown for a dry run (no block placement).
 */
public record BuildDryRun(
    int floorBlocks,
    int wallBlocks,
    int ceilingBlocks,
    int underfloorBlocks,
    int hazardBlocks,
    int lightingBlocks
) {
    public int totalBlocks() {
        return floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks + hazardBlocks + lightingBlocks;
    }
}

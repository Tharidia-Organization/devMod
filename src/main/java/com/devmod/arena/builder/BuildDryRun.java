package com.devmod.arena.builder;

/**
 * Block count breakdown for a dry run (no block placement).
 */
public record BuildDryRun(
    int floorBlocks,
    int wallBlocks,
    int ceilingBlocks,
    int underfloorBlocks
) {
    public int totalBlocks() {
        return floorBlocks + wallBlocks + ceilingBlocks + underfloorBlocks;
    }
}

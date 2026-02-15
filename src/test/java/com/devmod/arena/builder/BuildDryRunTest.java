package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BuildDryRunTest {

    @Test
    void totalBlocks() {
        BuildDryRun run = new BuildDryRun(100, 200, 50, 300, 25, 10);
        assertEquals(685, run.totalBlocks());
    }

    @Test
    void zeroBlocks() {
        BuildDryRun run = new BuildDryRun(0, 0, 0, 0, 0, 0);
        assertEquals(0, run.totalBlocks());
    }

    @Test
    void recordAccessors() {
        BuildDryRun run = new BuildDryRun(1, 2, 3, 4, 5, 6);
        assertEquals(1, run.floorBlocks());
        assertEquals(2, run.wallBlocks());
        assertEquals(3, run.ceilingBlocks());
        assertEquals(4, run.underfloorBlocks());
        assertEquals(5, run.hazardBlocks());
        assertEquals(6, run.lightingBlocks());
    }
}

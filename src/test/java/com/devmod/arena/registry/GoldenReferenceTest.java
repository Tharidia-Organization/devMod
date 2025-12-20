package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoldenReferenceTest {

    @Test
    void defaultFlat64MatchesSpec() {
        GoldenReference ref = GoldenReference.defaultFlat64();

        assertEquals(23000, ref.totalBlocks());
        assertEquals(4096, ref.floorBlocks());
        assertEquals(2520, ref.wallBlocks());
        assertEquals(4096, ref.ceilingBlocks());
        assertEquals(12288, ref.underfloorBlocks());

        assertEquals(4, ref.spawnSlots().size());
    }
}

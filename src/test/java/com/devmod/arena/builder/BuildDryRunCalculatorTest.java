package com.devmod.arena.builder;

import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildDryRunCalculatorTest {

    @Test
    void defaultTemplateCountsMatchGoldenReference() {
        ArenaTemplate template = ArenaTemplate.defaultTemplate();

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

        assertEquals(4096, dryRun.floorBlocks());
        assertEquals(2268, dryRun.wallBlocks());
        assertEquals(4096, dryRun.ceilingBlocks());
        assertEquals(12288, dryRun.underfloorBlocks());
        assertEquals(0, dryRun.hazardBlocks());
        // Total includes ambient lighting blocks (25 for 64x64 arena with spacing 12)
        assertEquals(22773, dryRun.totalBlocks());
    }
}

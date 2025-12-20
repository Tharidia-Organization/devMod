package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildDryRunCalculatorTest {

    @Test
    void defaultTemplateCountsMatchGoldenReference() {
        ArenaTemplate template = ArenaTemplate.defaultTemplate();

        BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

        assertEquals(4096, dryRun.floorBlocks());
        assertEquals(2520, dryRun.wallBlocks());
        assertEquals(4096, dryRun.ceilingBlocks());
        assertEquals(12288, dryRun.underfloorBlocks());
        assertEquals(0, dryRun.hazardBlocks());
        assertEquals(23000, dryRun.totalBlocks());
    }
}

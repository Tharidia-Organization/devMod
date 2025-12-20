package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Pure helper to estimate block counts without placing blocks.
 */
public final class BuildDryRunCalculator {

    private BuildDryRunCalculator() {}

    public static BuildDryRun calculate(ArenaTemplate template) {
        int sizeX = template.sizeX() != null ? template.sizeX() : template.size();
        int sizeZ = template.sizeZ() != null ? template.sizeZ() : template.size();

        int floorBlocks = 0;
        if (template.floor() != null) {
            floorBlocks = sizeX * sizeZ * template.floor().thickness();
        }

        int wallBlocks = 0;
        if (template.walls() != null && template.walls().enabled()) {
            int perimeterPerLayer = 2 * (sizeX + sizeZ - 2);
            wallBlocks = perimeterPerLayer * template.walls().height() * template.walls().thickness();
        }

        int ceilingBlocks = 0;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            ceilingBlocks = sizeX * sizeZ * template.ceiling().thickness();
        }

        int underfloorBlocks = 0;
        if (template.underfloor() != null && template.floor() != null) {
            underfloorBlocks = sizeX * sizeZ * template.underfloor().depth();
        }

        return new BuildDryRun(floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks);
    }
}

package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Pure helper to estimate block counts without placing blocks.
 */
public final class BuildDryRunCalculator {

    private BuildDryRunCalculator() {}

    public static BuildDryRun calculate(ArenaTemplate template) {
        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal : template.size();

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

        // Hazards: rough estimate, per-type (lava_pool/void_pit use radius^2 pi, ring uses outer^2-inner^2)
        int hazardBlocks = 0;
        if (template.hazards() != null) {
            for (var hazard : template.hazards()) {
                if (hazard.params() == null) continue;
                switch (hazard.type()) {
                    case "lava_pool", "void_pit" -> {
                        Number r = (Number) hazard.params().getOrDefault("radius", 0);
                        hazardBlocks += (int) Math.round(Math.PI * r.doubleValue() * r.doubleValue());
                    }
                    case "lava_ring" -> {
                        Number inner = (Number) hazard.params().getOrDefault("innerRadius", 0);
                        Number outer = (Number) hazard.params().getOrDefault("outerRadius", 0);
                        double area = Math.PI * (outer.doubleValue() * outer.doubleValue() - inner.doubleValue() * inner.doubleValue());
                        hazardBlocks += (int) Math.round(area);
                    }
                    case "fire_zone" -> {
                        Object minObj = hazard.params().get("min");
                        Object maxObj = hazard.params().get("max");
                        if (minObj instanceof java.util.List<?> min && maxObj instanceof java.util.List<?> max && min.size() == 3 && max.size() == 3) {
                            int dx = Math.abs(((Number) max.get(0)).intValue() - ((Number) min.get(0)).intValue()) + 1;
                            int dz = Math.abs(((Number) max.get(2)).intValue() - ((Number) min.get(2)).intValue()) + 1;
                            hazardBlocks += dx * dz;
                        }
                    }
                    case "magma_floor" -> {
                        Number cov = (Number) hazard.params().getOrDefault("coverage", 0.0);
                        hazardBlocks += (int) Math.round(sizeX * sizeZ * Math.min(0.5, cov.doubleValue()));
                    }
                    default -> {
                        // other hazards ignored for dry-run component counts
                    }
                }
            }
        }

        return new BuildDryRun(floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks);
    }
}

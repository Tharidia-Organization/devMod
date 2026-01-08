package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;

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
            int wallLayers = template.walls().height();
            int wallThickness = template.walls().thickness();
            int overlapLayers = 0;
            if (template.ceiling() != null && template.ceiling().enabled()) {
                int wallStartY = template.walls().startY();
                int wallEndY = wallStartY + wallLayers - 1;
                int ceilingStartY = template.ceiling().y();
                int ceilingEndY = ceilingStartY + template.ceiling().thickness() - 1;
                int floorStartY = Integer.MIN_VALUE;
                int floorEndY = Integer.MIN_VALUE;
                if (template.floor() != null && template.floor().thickness() > 0) {
                    floorStartY = template.floor().y();
                    floorEndY = floorStartY + template.floor().thickness() - 1;
                }

                int overlapCeiling = countOverlap(wallStartY, wallEndY, ceilingStartY, ceilingEndY);
                int overlapFloor = template.floor() != null
                    ? countOverlap(wallStartY, wallEndY, floorStartY, floorEndY)
                    : 0;
                overlapLayers = mergeOverlap(overlapCeiling, ceilingStartY, ceilingEndY, overlapFloor, floorStartY, floorEndY, wallStartY, wallEndY);
            } else if (template.floor() != null && template.floor().thickness() > 0) {
                int wallStartY = template.walls().startY();
                int wallEndY = wallStartY + wallLayers - 1;
                int floorStartY = template.floor().y();
                int floorEndY = floorStartY + template.floor().thickness() - 1;
                overlapLayers = countOverlap(wallStartY, wallEndY, floorStartY, floorEndY);
            }
            int effectiveWallLayers = Math.max(0, wallLayers - overlapLayers);
            wallBlocks = perimeterPerLayer * wallThickness * effectiveWallLayers;
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
            int floorY = template.floor() != null ? template.floor().y() : Integer.MIN_VALUE;
            for (var hazard : template.hazards()) {
                if (hazard.params() == null) continue;
                if (isFloorOverlayHazard(hazard, floorY)) {
                    continue;
                }
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

        // Lighting: explicit sources + ambient grid
        int lightingBlocks = estimateLightingBlocks(template, sizeX, sizeZ);

        return new BuildDryRun(floorBlocks, wallBlocks, ceilingBlocks, underfloorBlocks, hazardBlocks, lightingBlocks);
    }

    private static boolean isFloorOverlayHazard(ArenaTemplate.Hazard hazard, int floorY) {
        if (floorY == Integer.MIN_VALUE) {
            return false;
        }
        switch (hazard.type()) {
            case "lava_ring", "lava_pool", "magma_floor", "fire_zone" -> {
                int hazardY = resolveHazardY(hazard, floorY);
                return hazardY == floorY;
            }
            default -> {
                return false;
            }
        }
    }

    private static int resolveHazardY(ArenaTemplate.Hazard hazard, int floorY) {
        Integer hazardY = hazard.y();
        int baseY = hazardY != null ? hazardY : floorY;
        if (hazard.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            baseY += floorY;
        }
        return baseY;
    }

    /**
     * Estimates the number of light source blocks to be placed.
     * Includes both explicit light sources from template and ambient grid lights.
     */
    private static int estimateLightingBlocks(ArenaTemplate template, int sizeX, int sizeZ) {
        if (template.lighting() == null) return 0;

        var lighting = template.lighting();
        int count = 0;

        // Count explicit light sources
        if (lighting.lightSources() != null) {
            count += lighting.lightSources().size();
        }

        // Count ambient grid lights (same formula as ArenaBuilder.placeAmbientLighting)
        if (lighting.ambientLight() && template.floor() != null) {
            int targetLight = lighting.blockLight();
            // Light decreases by 1 per block, so for level 15 source to maintain level N,
            // spacing should be approximately (15 - N) * 2 to ensure overlap
            // Max spacing 20 to match ArenaBuilder.placeAmbientLighting() for low light targets
            int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

            // Grid starts at spacing/2, then every 'spacing' blocks
            int gridX = (sizeX - spacing / 2 + spacing - 1) / spacing;
            int gridZ = (sizeZ - spacing / 2 + spacing - 1) / spacing;
            count += Math.max(0, gridX) * Math.max(0, gridZ);
        }

        return count;
    }

    private static int countOverlap(int wallStart, int wallEnd, int otherStart, int otherEnd) {
        if (otherStart == Integer.MIN_VALUE || otherEnd == Integer.MIN_VALUE) {
            return 0;
        }
        int overlapStart = Math.max(wallStart, otherStart);
        int overlapEnd = Math.min(wallEnd, otherEnd);
        if (overlapEnd < overlapStart) {
            return 0;
        }
        return overlapEnd - overlapStart + 1;
    }

    private static int mergeOverlap(int overlapA, int aStart, int aEnd, int overlapB, int bStart, int bEnd, int wallStart, int wallEnd) {
        if (overlapA == 0) {
            return overlapB;
        }
        if (overlapB == 0) {
            return overlapA;
        }
        int aOverlapStart = Math.max(wallStart, aStart);
        int aOverlapEnd = Math.min(wallEnd, aEnd);
        int bOverlapStart = Math.max(wallStart, bStart);
        int bOverlapEnd = Math.min(wallEnd, bEnd);
        if (bOverlapEnd < aOverlapStart || bOverlapStart > aOverlapEnd) {
            return overlapA + overlapB;
        }
        int overlapStart = Math.max(aOverlapStart, bOverlapStart);
        int overlapEnd = Math.min(aOverlapEnd, bOverlapEnd);
        int doubleCount = overlapEnd - overlapStart + 1;
        return overlapA + overlapB - Math.max(0, doubleCount);
    }
}

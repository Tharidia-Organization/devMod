package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;

public final class BuildDryRunCalculator {

    private BuildDryRunCalculator() {}

    public static BuildDryRun calculate(ArenaTemplate template) {
        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int arenaArea = countArenaArea(template, sizeX, sizeZ);

        int floorBlocks = 0;
        if (template.floor() != null) {
            floorBlocks = arenaArea * template.floor().thickness();
        }

        int wallBlocks = 0;
        if (template.walls() != null && template.walls().enabled()) {
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
            int perLayer = estimateWallBlocksPerLayer(template, sizeX, sizeZ, wallThickness);
            wallBlocks = perLayer * effectiveWallLayers;
        }

        int ceilingBlocks = 0;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            ceilingBlocks = arenaArea * template.ceiling().thickness();
        }

        int underfloorBlocks = 0;
        if (template.underfloor() != null && template.floor() != null) {
            underfloorBlocks = arenaArea * template.underfloor().depth();
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
                        hazardBlocks += estimateMagmaFloorBlocks(template, sizeX, sizeZ, cov.doubleValue());
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
        ArenaTemplate.SpawnSlot.YMode mode = hazard.yMode();
        if (mode == null || mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            int offset = hazardY != null ? hazardY : 0;
            return floorY + offset;
        }
        return baseY;
    }

    private static int getSizeX(ArenaTemplate template) {
        Integer sizeXVal = template.sizeX();
        return sizeXVal != null ? sizeXVal : template.size();
    }

    private static int getSizeZ(ArenaTemplate template) {
        Integer sizeZVal = template.sizeZ();
        return sizeZVal != null ? sizeZVal : template.size();
    }

    private static int minOffset(int size) {
        return -size / 2;
    }

    private static int maxOffset(int size) {
        return size - (size / 2) - 1;
    }

    private static ArenaTemplate.ArenaShape effectiveShape(ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        return shape != null ? shape : ArenaTemplate.ArenaShape.RECTANGULAR;
    }

    private static int countArenaArea(ArenaTemplate template, int sizeX, int sizeZ) {
        int minX = minOffset(sizeX);
        int maxX = maxOffset(sizeX);
        int minZ = minOffset(sizeZ);
        int maxZ = maxOffset(sizeZ);
        int count = 0;
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (isInArenaShape(dx, dz, template, sizeX, sizeZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isInArenaShape(int dx, int dz, ArenaTemplate template, int sizeX, int sizeZ) {
        ArenaTemplate.ArenaShape shape = effectiveShape(template);
        int minX = minOffset(sizeX);
        int maxX = maxOffset(sizeX);
        int minZ = minOffset(sizeZ);
        int maxZ = maxOffset(sizeZ);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        return switch (shape) {
            case RECTANGULAR -> dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ;
            case CIRCULAR -> {
                int radius = Math.max(halfX, halfZ);
                yield dx * dx + dz * dz <= radius * radius;
            }
            case RING -> {
                int outerRadius = Math.max(halfX, halfZ);
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                yield distSq <= outerRadius * outerRadius && distSq >= innerRadius * innerRadius;
            }
        };
    }

    private static int estimateWallBlocksPerLayer(ArenaTemplate template, int sizeX, int sizeZ, int thickness) {
        ArenaTemplate.ArenaShape shape = effectiveShape(template);
        if (shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            int perimeterPerLayer = 2 * (sizeX + sizeZ - 2);
            return perimeterPerLayer * thickness;
        }
        return countCircularWallBlocksPerLayer(template, sizeX, sizeZ, thickness);
    }

    private static int countCircularWallBlocksPerLayer(ArenaTemplate template, int sizeX, int sizeZ, int thickness) {
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int radius = Math.max(halfX, halfZ);
        int outerExtent = radius + thickness;
        int count = 0;
        for (int dx = -outerExtent; dx <= outerExtent; dx++) {
            for (int dz = -outerExtent; dz <= outerExtent; dz++) {
                if (isOnCircularBorder(dx, dz, template, thickness, sizeX, sizeZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isOnCircularBorder(int dx, int dz, ArenaTemplate template, int thickness, int sizeX, int sizeZ) {
        if (thickness <= 0) {
            return false;
        }
        ArenaTemplate.ArenaShape shape = effectiveShape(template);
        if (shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            return false;
        }

        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int outerRadius = Math.max(halfX, halfZ);
        int distSq = dx * dx + dz * dz;

        int outerWallInnerSq = outerRadius * outerRadius;
        int outerWallOuterSq = (outerRadius + thickness) * (outerRadius + thickness);
        boolean onOuterWall = distSq >= outerWallInnerSq && distSq < outerWallOuterSq;

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            return onOuterWall;
        }

        Integer innerRadiusVal = template.ringInnerRadius();
        int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
        int innerWallOuterSq = innerRadius * innerRadius;
        int innerWallInnerSq = Math.max(0, (innerRadius - thickness) * (innerRadius - thickness));
        boolean onInnerWall = distSq <= innerWallOuterSq && distSq >= innerWallInnerSq;

        return onOuterWall || onInnerWall;
    }

    private static int estimateMagmaFloorBlocks(ArenaTemplate template, int sizeX, int sizeZ, double coverage) {
        ArenaTemplate.ArenaShape shape = effectiveShape(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int radius = Math.max(halfX, halfZ);
        int arenaArea;
        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            arenaArea = (int) (Math.PI * radius * radius);
        } else if (shape == ArenaTemplate.ArenaShape.RING) {
            Integer innerRadiusVal = template.ringInnerRadius();
            int innerRadius = innerRadiusVal != null ? innerRadiusVal : radius / 2;
            arenaArea = (int) (Math.PI * (radius * radius - innerRadius * innerRadius));
        } else {
            arenaArea = sizeX * sizeZ;
        }
        return (int) Math.round(arenaArea * Math.min(0.5, coverage));
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
        if (lighting.ambientLight() && lighting.blockLight() > 0 && template.floor() != null) {
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

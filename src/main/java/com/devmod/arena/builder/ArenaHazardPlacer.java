package com.devmod.arena.builder;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;

/**
 * Handles hazard placement within arenas: lava rings, pools, void pits, spike traps,
 * fire zones, magma floors, falling blocks, and custom hazards.
 * Extracted from ArenaBuilder.
 */
class ArenaHazardPlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaHazardPlacer.class);

    private final ArenaStructurePlacer structurePlacer;
    private final ArenaTelemetry telemetry;
    @Nullable
    private final ArenaBuilder.CustomHazardHandler customHazardHandler;

    ArenaHazardPlacer(ArenaStructurePlacer structurePlacer, ArenaTelemetry telemetry,
                      @Nullable ArenaBuilder.CustomHazardHandler customHazardHandler) {
        this.structurePlacer = structurePlacer;
        this.telemetry = telemetry;
        this.customHazardHandler = customHazardHandler;
    }

    void placeHazards(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        List<ArenaTemplate.Hazard> hazards = template.hazards();
        if (hazards == null || hazards.isEmpty()) {
            return;
        }
        LOGGER.debug("Placing {} hazards for template '{}'", hazards.size(), template.id());
        int placedHazards = 0;
        for (ArenaTemplate.Hazard hazard : hazards) {
            switch (hazard.type()) {
                case "lava_ring" -> placeLavaRing(hazard, template, originX, originZ, tx);
                case "lava_pool" -> placeLavaPool(hazard, template, originX, originZ, tx);
                case "void_pit" -> placeVoidPit(hazard, template, originX, originZ, tx);
                case "spike_trap" -> placeSpikeTrap(hazard, template, originX, originZ, tx);
                case "fire_zone" -> placeFireZone(hazard, template, originX, originZ, tx);
                case "magma_floor" -> placeMagmaFloor(hazard, template, originX, originZ, tx);
                case "falling_blocks" -> placeFallingBlocks(hazard, template, originX, originZ, tx);
                case "custom" -> placeCustomHazard(hazard, template, originX, originZ, tx);
                default -> LOGGER.debug("Hazard type '{}' has no placement implementation", hazard.type());
            }
            placedHazards++;
        }
        if (placedHazards > 0) {
            telemetry.emit("arena.hazard.placed", Map.of(
                "templateId", template.id(),
                "count", placedHazards
            ));
        }
    }

    // === Hazard helper methods ===

    private int resolveY(ArenaTemplate.Hazard hazard, ArenaTemplate template) {
        Integer hazardY = hazard.y();
        int baseY = hazardY != null ? hazardY : template.floor().y();
        ArenaTemplate.SpawnSlot.YMode mode = hazard.yMode();
        if (mode == null || mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            int offset = hazardY != null ? hazardY : 0;
            return template.floor().y() + offset;
        }
        return baseY;
    }

    private int[] resolveCenter(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ) {
        Object centerObj = hazard.params() != null ? hazard.params().get("center") : null;
        if (centerObj instanceof java.util.List<?> list && list.size() == 3) {
            int[] c = new int[3];
            for (int i = 0; i < 3; i++) {
                Object v = list.get(i);
                c[i] = v instanceof Number n ? n.intValue() : 0;
            }
            return c;
        }
        return new int[]{originX, resolveY(hazard, template), originZ};
    }

    private void placeLavaRing(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int inner = ((Number) hazard.params().getOrDefault("innerRadius", 1)).intValue();
        int outer = ((Number) hazard.params().getOrDefault("outerRadius", inner + 1)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 >= inner * inner && r2 <= outer * outer) {
                    structurePlacer.placeBlock(center[0] + dx, y, center[2] + dz, material, tx);
                }
            }
        }
    }

    private void placeLavaPool(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) hazard.params().getOrDefault("material", "minecraft:lava");
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    structurePlacer.placeBlock(center[0] + dx, y, center[2] + dz, material, tx);
                }
            }
        }
    }

    private void placeVoidPit(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        int radius = ((Number) hazard.params().getOrDefault("radius", 3)).intValue();
        int depth = ((Number) hazard.params().getOrDefault("depth", 10)).intValue();
        int yTop = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    for (int dy = 0; dy < depth; dy++) {
                        structurePlacer.placeBlock(center[0] + dx, yTop - dy, center[2] + dz, "minecraft:void_air", tx);
                    }
                }
            }
        }
    }

    private void placeSpikeTrap(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object positionsObj = hazard.params() != null ? hazard.params().get("positions") : null;
        if (!(positionsObj instanceof java.util.List<?> positions)) return;
        String material = (String) hazard.params().getOrDefault("material", "minecraft:iron_bars");
        for (Object posObj : positions) {
            if (posObj instanceof java.util.List<?> p && p.size() == 3) {
                int x = ((Number) p.get(0)).intValue() + originX;
                int y = resolveY(hazard, template);
                int z = ((Number) p.get(2)).intValue() + originZ;
                structurePlacer.placeBlock(x, y, z, material, tx);
            }
        }
    }

    private void placeFireZone(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object minObj = hazard.params() != null ? hazard.params().get("min") : null;
        Object maxObj = hazard.params() != null ? hazard.params().get("max") : null;
        if (!(minObj instanceof java.util.List<?> min) || !(maxObj instanceof java.util.List<?> max) || min.size() != 3 || max.size() != 3) {
            return;
        }
        int minX = ((Number) min.get(0)).intValue() + originX;
        int minY = resolveY(hazard, template);
        int minZ = ((Number) min.get(2)).intValue() + originZ;
        int maxX = ((Number) max.get(0)).intValue() + originX;
        int maxZ = ((Number) max.get(2)).intValue() + originZ;
        String block = (String) hazard.params().getOrDefault("block", "minecraft:fire");
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                structurePlacer.placeBlock(x, minY, z, block, tx);
            }
        }
    }

    private void placeMagmaFloor(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        double coverage = ((Number) hazard.params().getOrDefault("coverage", 0.1d)).doubleValue();
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int y = resolveY(hazard, template);

        int arenaArea;
        int radius = Math.max(halfX, halfZ);
        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            arenaArea = (int) (Math.PI * radius * radius);
        } else if (shape == ArenaTemplate.ArenaShape.RING) {
            Integer innerRadiusVal = template.ringInnerRadius();
            int innerRadius = innerRadiusVal != null ? innerRadiusVal : radius / 2;
            arenaArea = (int) (Math.PI * (radius * radius - innerRadius * innerRadius));
        } else {
            arenaArea = sizeX * sizeZ;
        }

        int total = (int) Math.round(arenaArea * Math.min(coverage, 0.5));
        String block = (String) hazard.params().getOrDefault("block", "minecraft:magma_block");
        int placed = 0;

        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        outer:
        for (int dx = minX; dx <= maxX; dx += 2) {
            for (int dz = minZ; dz <= maxZ; dz += 2) {
                if (placed >= total) break outer;
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }
                structurePlacer.placeBlock(originX + dx, y, originZ + dz, block, tx);
                placed++;
            }
        }
    }

    private void placeFallingBlocks(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        Object areaObj = hazard.params() != null ? hazard.params().get("area") : null;
        String blockType = (String) hazard.params().getOrDefault("blockType", "minecraft:sand");
        int count = ((Number) hazard.params().getOrDefault("count", 5)).intValue();
        int interval = ((Number) hazard.params().getOrDefault("interval", 20)).intValue();

        int verticalSpacing = Math.max(1, interval / 10);

        if (areaObj instanceof java.util.List<?> area && area.size() == 3) {
            int centerX = ((Number) area.get(0)).intValue() + originX;
            int centerZ = ((Number) area.get(2)).intValue() + originZ;
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                structurePlacer.placeBlock(centerX, y + (i * verticalSpacing), centerZ, blockType, tx);
            }
        } else {
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                structurePlacer.placeBlock(originX, y + (i * verticalSpacing), originZ, blockType, tx);
            }
        }
    }

    private void placeCustomHazard(ArenaTemplate.Hazard hazard, ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        if (customHazardHandler != null) {
            try {
                customHazardHandler.placeCustom(hazard, template, originX, originZ, tx);
            } catch (Exception e) {
                LOGGER.error("Custom hazard placement failed for {}: {}", hazard.params(), e.getMessage());
                telemetry.emit("arena.hazard.custom_failed", Map.of(
                    "templateId", template.id(),
                    "hazardType", hazard.type(),
                    "error", e.getMessage()
                ));
            }
        } else {
            LOGGER.warn("Custom hazard encountered but no handler provided; skipping. Hazard params: {}", hazard.params());
            telemetry.emit("arena.hazard.custom_skipped", Map.of(
                "templateId", template.id(),
                "hazardType", hazard.type()
            ));
        }
    }
}

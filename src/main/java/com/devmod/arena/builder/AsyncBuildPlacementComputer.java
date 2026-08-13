package com.devmod.arena.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Computes block placements for async arena builds.
 * Generates an ordered list of {@link AsyncArenaBuilder.BlockPlacement} from an ArenaTemplate
 * by building floor, walls, ceiling, underfloor, hazards, and lighting.
 *
 * Extracted from AsyncArenaBuilder.AsyncBuild to reduce class size.
 */
final class AsyncBuildPlacementComputer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncBuildPlacementComputer.class);

    private AsyncBuildPlacementComputer() {}

    static List<AsyncArenaBuilder.BlockPlacement> computePlacements(
            ArenaTemplate template, int originX, int originY, int originZ) {
        List<AsyncArenaBuilder.BlockPlacement> placements = new ArrayList<>();

        ArenaTemplate.BuildSettings.Order buildOrder = resolveBuildOrder(template);
        if (buildOrder == ArenaTemplate.BuildSettings.Order.WALLS_FIRST) {
            if (template.walls() != null && template.walls().enabled()) {
                buildWalls(template, originX, originZ, placements);
            }
            if (template.floor() != null) {
                buildFloor(template, originX, originZ, placements);
            }
        } else {
            if (template.floor() != null) {
                buildFloor(template, originX, originZ, placements);
            }
            if (template.walls() != null && template.walls().enabled()) {
                buildWalls(template, originX, originZ, placements);
            }
        }

        ArenaTemplate.TerrainSettings terrainSettings = template.terrainSettings();
        boolean isDynamicTerrain = terrainSettings != null
            && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC;

        if (template.ceiling() != null && template.ceiling().enabled() && !isDynamicTerrain) {
            buildCeiling(template, originX, originZ, placements);
        }

        if (template.underfloor() != null && !isDynamicTerrain) {
            buildUnderfloor(template, originX, originZ, placements);
        }

        if (template.hazards() != null && !template.hazards().isEmpty()) {
            placeHazards(template, originX, originZ, placements);
        }

        if (template.lighting() != null) {
            placeLighting(template, originX, originZ, placements);
        }

        return placements;
    }

    // === Build order ===

    private static ArenaTemplate.BuildSettings.Order resolveBuildOrder(ArenaTemplate template) {
        if (template.buildSettings() == null || template.buildSettings().buildOrder() == null) {
            return ArenaTemplate.BuildSettings.Order.FLOOR_FIRST;
        }
        return template.buildSettings().buildOrder();
    }

    // === Floor ===

    private static void buildFloor(ArenaTemplate template, int originX, int originZ,
                                    List<AsyncArenaBuilder.BlockPlacement> placements) {
        if (template.floor() == null) {
            return;
        }

        ArenaTemplate.TerrainSettings terrainSettings = template.terrainSettings();
        if (terrainSettings != null
            && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC) {
            buildDynamicTerrainFloor(template, originX, originZ, terrainSettings, placements);
            return;
        }

        ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            boolean hasZoneFloorMaterials = zoneSettings.zones().stream()
                .anyMatch(z -> {
                    String mat = z.floorMaterial();
                    return mat != null && !mat.isEmpty();
                });
            if (hasZoneFloorMaterials) {
                buildZoneAwareFloor(template, originX, originZ, zoneSettings, placements);
                return;
            }
        }

        buildTemplateFloor(template, originX, originZ, placements);
    }

    private static void buildDynamicTerrainFloor(ArenaTemplate template, int originX, int originZ,
                                                  ArenaTemplate.TerrainSettings terrainSettings,
                                                  List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Floor floor = template.floor();
        if (floor == null) {
            return;
        }

        ArenaTemplate.TerrainSettings.DynamicSettings dynamicSettings = terrainSettings.dynamic();
        if (dynamicSettings == null) {
            dynamicSettings = ArenaTemplate.TerrainSettings.DynamicSettings.proxyOverworld();
        }

        int templateRadius = Math.max(ArenaShapeHelper.getSizeX(template), ArenaShapeHelper.getSizeZ(template)) / 2;
        int combatRadius = Math.min(dynamicSettings.combatRingRadius(), templateRadius);
        int blendRadius = dynamicSettings.blendRadius();

        if (combatRadius < dynamicSettings.combatRingRadius()) {
            LOGGER.warn("Template '{}': combatRingRadius {} exceeds template size {}, clamped to {}",
                template.id(), dynamicSettings.combatRingRadius(), templateRadius * 2, combatRadius);
        }

        int maxRadius = combatRadius + blendRadius;
        int minX = Math.max(ArenaShapeHelper.minOffsetX(template), -maxRadius);
        int maxX = Math.min(ArenaShapeHelper.maxOffsetX(template), maxRadius);
        int minZ = Math.max(ArenaShapeHelper.minOffsetZ(template), -maxRadius);
        int maxZ = Math.min(ArenaShapeHelper.maxOffsetZ(template), maxRadius);

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > maxRadius) {
                    continue;
                }

                double blendFactor = 1.0;
                if (dist > combatRadius) {
                    blendFactor = 1.0 - ((dist - combatRadius) / blendRadius);
                    blendFactor = Math.max(0.0, Math.min(1.0, blendFactor));
                }

                if (blendFactor < 1.0) {
                    long posHash = ((long) dx * 31 + dz) ^ template.id().hashCode();
                    double threshold = (posHash & 0xFFFF) / 65536.0;
                    if (threshold > blendFactor) {
                        continue;
                    }
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;

                    String material = floor.material();
                    if (dist <= combatRadius && floor.borderMaterial() != null && floor.borderWidth() > 0) {
                        if (dist >= combatRadius - floor.borderWidth()) {
                            material = floor.borderMaterial();
                        }
                    }

                    addBlock(worldX, worldY, worldZ, material, placements);
                }
            }
        }
    }

    private static void buildZoneAwareFloor(ArenaTemplate template, int originX, int originZ,
                                             ArenaTemplate.ZoneSettings zoneSettings,
                                             List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Floor floor = template.floor();
        if (floor == null) {
            return;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        ZoneLayout layout = ZoneLayoutHelper.buildZoneLayoutWithEnvironments(zoneSettings, halfX, halfZ);

        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }

                String material = floor.material();
                Optional<ArenaZone> zone = layout.getZoneAt(dx, dz);
                if (zone.isPresent()) {
                    Optional<ResourceLocation> zoneMaterial = zone.get().environment().floorMaterial();
                    if (zoneMaterial.isPresent()) {
                        material = zoneMaterial.get().toString();
                    }
                }

                if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                    boolean inBorder = ArenaShapeHelper.isOnFloorBorder(dx, dz, template, floor.borderWidth());
                    if (inBorder) {
                        material = floor.borderMaterial();
                    }
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;
                    addBlock(worldX, worldY, worldZ, material, placements);
                }
            }
        }
    }

    private static void buildTemplateFloor(ArenaTemplate template, int originX, int originZ,
                                            List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Floor floor = template.floor();
        if (floor == null) {
            return;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;

                    String material = floor.material();
                    if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                        boolean inBorder = ArenaShapeHelper.isOnFloorBorder(dx, dz, template, floor.borderWidth());
                        if (inBorder) {
                            material = floor.borderMaterial();
                        }
                    }

                    addBlock(worldX, worldY, worldZ, material, placements);
                }
            }
        }
    }

    // === Walls ===

    private static void buildWalls(ArenaTemplate template, int originX, int originZ,
                                    List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Walls walls = template.walls();
        if (walls == null || !walls.enabled()) {
            return;
        }

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        if (shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            int startX = originX - halfX;
            int startZ = originZ - halfZ;
            int endX = startX + sizeX - 1;
            int endZ = startZ + sizeZ - 1;

            for (int dy = 0; dy < walls.height(); dy++) {
                int worldY = walls.startY() + dy;

                for (int t = 0; t < walls.thickness(); t++) {
                    for (int x = startX; x <= endX; x++) {
                        addBlock(x, worldY, startZ - t, walls.material(), placements);
                        addBlock(x, worldY, endZ + t, walls.material(), placements);
                    }
                }

                for (int t = 0; t < walls.thickness(); t++) {
                    for (int z = startZ + 1; z <= endZ - 1; z++) {
                        addBlock(startX - t, worldY, z, walls.material(), placements);
                        addBlock(endX + t, worldY, z, walls.material(), placements);
                    }
                }
            }
        } else {
            int radius = Math.max(halfX, halfZ);
            int outerExtent = radius + walls.thickness();

            for (int dy = 0; dy < walls.height(); dy++) {
                int worldY = walls.startY() + dy;

                for (int dx = -outerExtent; dx <= outerExtent; dx++) {
                    for (int dz = -outerExtent; dz <= outerExtent; dz++) {
                        if (ArenaShapeHelper.isOnCircularBorder(dx, dz, template, walls.thickness())) {
                            addBlock(originX + dx, worldY, originZ + dz, walls.material(), placements);
                        }
                    }
                }
            }
        }
    }

    // === Ceiling ===

    private static void buildCeiling(ArenaTemplate template, int originX, int originZ,
                                      List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Ceiling ceiling = template.ceiling();
        if (ceiling == null || !ceiling.enabled()) {
            return;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }
                for (int dy = 0; dy < ceiling.thickness(); dy++) {
                    addBlock(originX + dx, ceiling.y() + dy, originZ + dz, ceiling.material(), placements);
                }
            }
        }
    }

    // === Underfloor ===

    private static void buildUnderfloor(ArenaTemplate template, int originX, int originZ,
                                         List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Underfloor underfloor = template.underfloor();
        ArenaTemplate.Floor floor = template.floor();
        if (underfloor == null || floor == null) {
            return;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        String material = underfloor.sameAsFloor() ? floor.material() : underfloor.material();

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }
                for (int dy = 1; dy <= underfloor.depth(); dy++) {
                    addBlock(originX + dx, floor.y() - dy, originZ + dz, material, placements);
                }
            }
        }
    }

    // === Hazards ===

    private static void placeHazards(ArenaTemplate template, int originX, int originZ,
                                      List<AsyncArenaBuilder.BlockPlacement> placements) {
        List<ArenaTemplate.Hazard> hazards = template.hazards();
        if (hazards == null || hazards.isEmpty()) {
            return;
        }
        if (template.floor() == null) {
            // Every hazard below resolves its Y through the floor; without one there is
            // no reference plane to place against.
            LOGGER.warn("Template '{}' declares hazards but has no floor, skipping hazard placement",
                template.id());
            return;
        }
        for (ArenaTemplate.Hazard hazard : hazards) {
            switch (hazard.type()) {
                case "lava_ring" -> placeLavaRing(hazard, template, originX, originZ, placements);
                case "lava_pool" -> placeLavaPool(hazard, template, originX, originZ, placements);
                case "void_pit" -> placeVoidPit(hazard, template, originX, originZ, placements);
                case "spike_trap" -> placeSpikeTrap(hazard, template, originX, originZ, placements);
                case "fire_zone" -> placeFireZone(hazard, template, originX, originZ, placements);
                case "magma_floor" -> placeMagmaFloor(hazard, template, originX, originZ, placements);
                case "falling_blocks" -> placeFallingBlocks(hazard, template, originX, originZ, placements);
                case "custom" -> LOGGER.warn("Custom hazard '{}' skipped in async builder", hazard.type());
                default -> LOGGER.debug("Hazard type '{}' has no placement implementation", hazard.type());
            }
        }
    }

    private static int resolveY(ArenaTemplate.Hazard hazard, ArenaTemplate template) {
        Integer hazardY = hazard.y();
        int baseY = hazardY != null ? hazardY : template.floor().y();
        ArenaTemplate.SpawnSlot.YMode mode = hazard.yMode();
        if (mode == null || mode == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            int offset = hazardY != null ? hazardY : 0;
            return template.floor().y() + offset;
        }
        return baseY;
    }

    private static int[] resolveCenter(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                        int originX, int originZ) {
        Object centerObj = hazard.params() != null ? hazard.params().get("center") : null;
        if (centerObj instanceof List<?> list && list.size() == 3) {
            int[] c = new int[3];
            for (int i = 0; i < 3; i++) {
                Object v = list.get(i);
                c[i] = v instanceof Number n ? n.intValue() : 0;
            }
            return c;
        }
        return new int[]{originX, resolveY(hazard, template), originZ};
    }

    /** Hazard params are optional; the sibling lookups already guard against null. */
    private static Map<String, Object> params(ArenaTemplate.Hazard hazard) {
        Map<String, Object> params = hazard.params();
        return params != null ? params : Map.of();
    }

    private static void placeLavaRing(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                       int originX, int originZ,
                                       List<AsyncArenaBuilder.BlockPlacement> placements) {
        int inner = ((Number) params(hazard).getOrDefault("innerRadius", 1)).intValue();
        int outer = ((Number) params(hazard).getOrDefault("outerRadius", inner + 1)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) params(hazard).getOrDefault("material", "minecraft:lava");

        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 >= inner * inner && r2 <= outer * outer) {
                    addBlock(center[0] + dx, y, center[2] + dz, material, placements);
                }
            }
        }
    }

    private static void placeLavaPool(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                       int originX, int originZ,
                                       List<AsyncArenaBuilder.BlockPlacement> placements) {
        int radius = ((Number) params(hazard).getOrDefault("radius", 3)).intValue();
        int y = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        String material = (String) params(hazard).getOrDefault("material", "minecraft:lava");
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    addBlock(center[0] + dx, y, center[2] + dz, material, placements);
                }
            }
        }
    }

    private static void placeVoidPit(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                      int originX, int originZ,
                                      List<AsyncArenaBuilder.BlockPlacement> placements) {
        int radius = ((Number) params(hazard).getOrDefault("radius", 3)).intValue();
        int depth = ((Number) params(hazard).getOrDefault("depth", 10)).intValue();
        int yTop = resolveY(hazard, template);
        int[] center = resolveCenter(hazard, template, originX, originZ);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    for (int dy = 0; dy < depth; dy++) {
                        addBlock(center[0] + dx, yTop - dy, center[2] + dz,
                            "minecraft:void_air", placements);
                    }
                }
            }
        }
    }

    private static void placeSpikeTrap(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                        int originX, int originZ,
                                        List<AsyncArenaBuilder.BlockPlacement> placements) {
        Object positionsObj = hazard.params() != null ? hazard.params().get("positions") : null;
        if (!(positionsObj instanceof List<?> positions)) {
            return;
        }
        String material = (String) params(hazard).getOrDefault("material", "minecraft:iron_bars");
        for (Object posObj : positions) {
            if (posObj instanceof List<?> p && p.size() == 3) {
                int x = ((Number) p.get(0)).intValue() + originX;
                int y = resolveY(hazard, template);
                int z = ((Number) p.get(2)).intValue() + originZ;
                addBlock(x, y, z, material, placements);
            }
        }
    }

    private static void placeFireZone(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                       int originX, int originZ,
                                       List<AsyncArenaBuilder.BlockPlacement> placements) {
        Object minObj = hazard.params() != null ? hazard.params().get("min") : null;
        Object maxObj = hazard.params() != null ? hazard.params().get("max") : null;
        if (!(minObj instanceof List<?> min) || !(maxObj instanceof List<?> max)
            || min.size() != 3 || max.size() != 3) {
            return;
        }
        int minX = ((Number) min.get(0)).intValue() + originX;
        int minY = resolveY(hazard, template);
        int minZ = ((Number) min.get(2)).intValue() + originZ;
        int maxX = ((Number) max.get(0)).intValue() + originX;
        int maxZ = ((Number) max.get(2)).intValue() + originZ;
        String block = (String) params(hazard).getOrDefault("block", "minecraft:fire");
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                addBlock(x, minY, z, block, placements);
            }
        }
    }

    private static void placeMagmaFloor(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                         int originX, int originZ,
                                         List<AsyncArenaBuilder.BlockPlacement> placements) {
        double coverage = ((Number) params(hazard).getOrDefault("coverage", 0.1d)).doubleValue();
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
        String block = (String) params(hazard).getOrDefault("block", "minecraft:magma_block");
        int placed = 0;

        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        outer:
        for (int dx = minX; dx <= maxX; dx += 2) {
            for (int dz = minZ; dz <= maxZ; dz += 2) {
                if (placed >= total) {
                    break outer;
                }
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }
                addBlock(originX + dx, y, originZ + dz, block, placements);
                placed++;
            }
        }
    }

    private static void placeFallingBlocks(ArenaTemplate.Hazard hazard, ArenaTemplate template,
                                            int originX, int originZ,
                                            List<AsyncArenaBuilder.BlockPlacement> placements) {
        Object areaObj = hazard.params() != null ? hazard.params().get("area") : null;
        String blockType = (String) params(hazard).getOrDefault("blockType", "minecraft:sand");
        int count = ((Number) params(hazard).getOrDefault("count", 5)).intValue();
        int interval = ((Number) params(hazard).getOrDefault("interval", 20)).intValue();

        int verticalSpacing = Math.max(1, interval / 10);

        if (areaObj instanceof List<?> area && area.size() == 3) {
            int centerX = ((Number) area.get(0)).intValue() + originX;
            int centerZ = ((Number) area.get(2)).intValue() + originZ;
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                addBlock(centerX, y + (i * verticalSpacing), centerZ, blockType, placements);
            }
        } else {
            int y = resolveY(hazard, template);
            for (int i = 0; i < count; i++) {
                addBlock(originX, y + (i * verticalSpacing), originZ, blockType, placements);
            }
        }
    }

    // === Lighting ===

    private static void placeLighting(ArenaTemplate template, int originX, int originZ,
                                       List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            placeZoneAwareLighting(template, originX, originZ, zoneSettings, placements);
            return;
        }

        placeTemplateLighting(template, originX, originZ, placements);
    }

    private static void placeZoneAwareLighting(ArenaTemplate template, int originX, int originZ,
                                                ArenaTemplate.ZoneSettings zoneSettings,
                                                List<AsyncArenaBuilder.BlockPlacement> placements) {
        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int floorY = template.floor() != null ? template.floor().y() : 64;

        ZoneLayout layout = ZoneLayoutHelper.buildZoneLayoutFromSettings(zoneSettings, halfX, halfZ);

        for (ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef : zoneSettings.zones()) {
            ZoneEnvironment environment = ZoneLayoutHelper.createZoneEnvironment(zoneDef);
            if (!environment.lighting().placeLightSources() && environment.lighting().blockLight() <= 0) {
                continue;
            }

            ArenaZone zone = layout.zones().stream()
                .filter(z -> z.name().equals(zoneDef.name()))
                .findFirst()
                .orElse(null);

            if (zone == null) {
                LOGGER.warn("Zone '{}' not found in layout, skipping lighting", zoneDef.name());
                continue;
            }

            placeZoneLighting(zone, originX, originZ, floorY,
                environment.lighting().blockLight(), template, placements);
        }
    }

    private static void placeZoneLighting(ArenaZone zone, int originX, int originZ, int floorY,
                                           int targetLight, ArenaTemplate template,
                                           List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaZone.ZoneBounds bounds = zone.bounds();

        int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

        int lightY = floorY + 1;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.walls() != null && template.walls().enabled()) {
            lightY = template.walls().startY() + template.walls().height() - 2;
        }

        String lightBlock = selectLightBlock(targetLight);

        int startX = originX + bounds.x1();
        int endX = originX + bounds.x2();
        int startZ = originZ + bounds.z1();
        int endZ = originZ + bounds.z2();

        for (int x = startX + spacing / 2; x < endX; x += spacing) {
            for (int z = startZ + spacing / 2; z < endZ; z += spacing) {
                if (zone.contains(x - originX, z - originZ)) {
                    addBlock(x, lightY, z, lightBlock, placements);
                }
            }
        }
    }

    private static void placeTemplateLighting(ArenaTemplate template, int originX, int originZ,
                                               List<AsyncArenaBuilder.BlockPlacement> placements) {
        ArenaTemplate.Lighting lighting = template.lighting();
        if (lighting == null) {
            return;
        }

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int minX = -halfX;
        int maxX = sizeX - halfX - 1;
        int minZ = -halfZ;
        int maxZ = sizeZ - halfZ - 1;

        if (lighting.lightSources() != null && !lighting.lightSources().isEmpty()) {
            for (ArenaTemplate.Lighting.LightSource lightSource : lighting.lightSources()) {
                int[] pos = lightSource.pos();
                if (pos != null && pos.length == 3) {
                    int relX = pos[0];
                    int relZ = pos[2];
                    if (relX < minX || relX > maxX || relZ < minZ || relZ > maxZ) {
                        continue;
                    }

                    int x = originX + pos[0];
                    int y = pos[1];
                    int z = originZ + pos[2];

                    if (template.floor() != null) {
                        y = template.floor().y() + pos[1];
                    }

                    addBlock(x, y, z, lightSource.block(), placements);
                }
            }
        }

        if (lighting.ambientLight() && lighting.blockLight() > 0) {
            placeAmbientLighting(template, originX, originZ, lighting.blockLight(), placements);
        }
    }

    private static void placeAmbientLighting(ArenaTemplate template, int originX, int originZ,
                                              int targetLight,
                                              List<AsyncArenaBuilder.BlockPlacement> placements) {
        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int startX = originX - (sizeX / 2);
        int startZ = originZ - (sizeZ / 2);

        int lightY;
        if (template.floor() != null) {
            lightY = template.floor().y() + 1;
        } else if (template.ceiling() != null && template.ceiling().enabled()) {
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.walls() != null && template.walls().enabled()) {
            lightY = template.walls().startY() + Math.max(1, template.walls().height() / 2);
        } else {
            lightY = 65;
        }

        if (template.ceiling() != null && template.ceiling().enabled()) {
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.floor() != null && template.walls() != null && template.walls().enabled()) {
            lightY = template.walls().startY() + template.walls().height() - 2;
        }

        int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));
        String lightBlock = selectLightBlock(targetLight);

        for (int dx = spacing / 2; dx < sizeX; dx += spacing) {
            for (int dz = spacing / 2; dz < sizeZ; dz += spacing) {
                int x = startX + dx;
                int z = startZ + dz;
                addBlock(x, lightY, z, lightBlock, placements);
            }
        }
    }

    private static String selectLightBlock(int targetLight) {
        if (targetLight >= 14) {
            return "minecraft:sea_lantern";
        } else if (targetLight >= 11) {
            return "minecraft:glowstone";
        } else if (targetLight >= 8) {
            return "minecraft:lantern";
        } else if (targetLight >= 5) {
            return "minecraft:soul_lantern";
        } else {
            return "minecraft:redstone_torch";
        }
    }

    // === Utility ===

    private static void addBlock(int x, int y, int z, String material,
                                  List<AsyncArenaBuilder.BlockPlacement> placements) {
        placements.add(new AsyncArenaBuilder.BlockPlacement(x, y, z, material));
    }
}

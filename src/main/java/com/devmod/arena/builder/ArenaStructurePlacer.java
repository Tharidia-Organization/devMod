package com.devmod.arena.builder;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Handles block placement for arena structures: floor, walls, ceiling, underfloor, and NBT structures.
 * Extracted from ArenaBuilder to separate block-placement concerns.
 */
class ArenaStructurePlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaStructurePlacer.class);

    private final ArenaBuilder.BlockPlacer blockPlacer;
    private final ArenaTelemetry telemetry;

    ArenaStructurePlacer(ArenaBuilder.BlockPlacer blockPlacer, ArenaTelemetry telemetry) {
        this.blockPlacer = blockPlacer;
        this.telemetry = telemetry;
    }

    void buildFloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        if (template.floor() == null) return;

        var terrainSettings = template.terrainSettings();
        if (terrainSettings != null && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC) {
            buildDynamicTerrainFloor(template, originX, originZ, terrainSettings, tx);
            return;
        }

        var zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            boolean hasZoneFloorMaterials = zoneSettings.zones().stream()
                .anyMatch(z -> {
                    String mat = z.floorMaterial();
                    return mat != null && !mat.isEmpty();
                });
            if (hasZoneFloorMaterials) {
                buildZoneAwareFloor(template, originX, originZ, zoneSettings, tx);
                return;
            }
        }

        buildTemplateFloor(template, originX, originZ, tx);
    }

    void buildWalls(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var walls = template.walls();
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
                        placeBlock(x, worldY, startZ - t, walls.material(), tx);
                        placeBlock(x, worldY, endZ + t, walls.material(), tx);
                    }
                }

                for (int t = 0; t < walls.thickness(); t++) {
                    for (int z = startZ + 1; z <= endZ - 1; z++) {
                        placeBlock(startX - t, worldY, z, walls.material(), tx);
                        placeBlock(endX + t, worldY, z, walls.material(), tx);
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
                            placeBlock(originX + dx, worldY, originZ + dz, walls.material(), tx);
                        }
                    }
                }
            }
        }
    }

    void buildCeiling(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var ceiling = template.ceiling();

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
                    placeBlock(originX + dx, ceiling.y() + dy, originZ + dz, ceiling.material(), tx);
                }
            }
        }
    }

    void buildUnderfloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var underfloor = template.underfloor();
        var floor = template.floor();
        if (floor == null) return;

        String material = underfloor.sameAsFloor() ? floor.material() : underfloor.material();

        int minX = ArenaShapeHelper.minOffsetX(template);
        int maxX = ArenaShapeHelper.maxOffsetX(template);
        int minZ = ArenaShapeHelper.minOffsetZ(template);
        int maxZ = ArenaShapeHelper.maxOffsetZ(template);

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                if (!ArenaShapeHelper.isInArenaShape(dx, dz, template)) {
                    continue;
                }

                for (int dy = 1; dy <= underfloor.depth(); dy++) {
                    placeBlock(originX + dx, floor.y() - dy, originZ + dz, material, tx);
                }
            }
        }
    }

    void placeStructure(ArenaTemplate template, int originX, int originY, int originZ, BuildTransaction tx) {
        ArenaTemplate.StructureNbt structureNbt = template.structureNbt();
        if (structureNbt == null) {
            return;
        }

        LOGGER.debug("Placing structure NBT '{}' for template '{}'", structureNbt.path(), template.id());

        int offsetX = originX;
        int offsetY = originY;
        int offsetZ = originZ;

        if (structureNbt.offset() != null) {
            offsetX += structureNbt.offset().x();
            offsetY += structureNbt.offset().y();
            offsetZ += structureNbt.offset().z();
        }

        telemetry.emit("arena.structure.placement_requested", Map.of(
            "templateId", template.id(),
            "path", structureNbt.path(),
            "offsetX", offsetX,
            "offsetY", offsetY,
            "offsetZ", offsetZ,
            "rotation", structureNbt.rotation() != null ? structureNbt.rotation() : "NONE",
            "mirror", structureNbt.mirror() != null ? structureNbt.mirror() : "NONE",
            "ignoreAir", structureNbt.ignoreAir()
        ));

        tx.trackStructurePlacement(structureNbt.path(), offsetX, offsetY, offsetZ);
    }

    void placeBlock(int x, int y, int z, String material, BuildTransaction tx) {
        long packedPos = CompactBlockTracker.pack(x, y, z);
        int previousStateId = blockPlacer.placeBlock(x, y, z, material);
        tx.trackBlock(packedPos, previousStateId);
    }

    // === Private floor building methods ===

    private void buildDynamicTerrainFloor(ArenaTemplate template, int originX, int originZ,
                                          ArenaTemplate.TerrainSettings terrainSettings, BuildTransaction tx) {
        var floor = template.floor();
        if (floor == null) return;

        var dynamicSettings = terrainSettings.dynamic();
        if (dynamicSettings == null) {
            dynamicSettings = ArenaTemplate.TerrainSettings.DynamicSettings.proxyOverworld();
        }

        int templateRadius = Math.max(ArenaShapeHelper.getSizeX(template), ArenaShapeHelper.getSizeZ(template)) / 2;
        int combatRadius = Math.min(dynamicSettings.combatRingRadius(), templateRadius);
        int blendRadius = dynamicSettings.blendRadius();

        if (combatRadius < dynamicSettings.combatRingRadius()) {
            LOGGER.warn("Template '{}': combatRingRadius {} exceeds template size {}, clamped to {}",
                template.id(), dynamicSettings.combatRingRadius(), templateRadius * 2, combatRadius);
            telemetry.emit("arena.floor.combat_radius_clamped", Map.of(
                "templateId", template.id(),
                "requested", dynamicSettings.combatRingRadius(),
                "clamped", combatRadius,
                "templateRadius", templateRadius
            ));
        }

        int maxRadius = combatRadius + blendRadius;
        int minX = Math.max(ArenaShapeHelper.minOffsetX(template), -maxRadius);
        int maxX = Math.min(ArenaShapeHelper.maxOffsetX(template), maxRadius);
        int minZ = Math.max(ArenaShapeHelper.minOffsetZ(template), -maxRadius);
        int maxZ = Math.min(ArenaShapeHelper.maxOffsetZ(template), maxRadius);

        int blocksPlaced = 0;
        int blendedBlocks = 0;

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
                    blendedBlocks++;
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

                    placeBlock(worldX, worldY, worldZ, material, tx);
                    blocksPlaced++;
                }
            }
        }

        LOGGER.debug("Built dynamic terrain floor for '{}': combatRadius={}, blendRadius={}, blocks={}, blended={}",
            template.id(), combatRadius, blendRadius, blocksPlaced, blendedBlocks);

        telemetry.emit("arena.floor.dynamic_terrain", Map.of(
            "templateId", template.id(),
            "combatRadius", combatRadius,
            "blendRadius", blendRadius,
            "blocksPlaced", blocksPlaced,
            "blendedBlocks", blendedBlocks
        ));
    }

    private void buildZoneAwareFloor(ArenaTemplate template, int originX, int originZ,
                                     ArenaTemplate.ZoneSettings zoneSettings, BuildTransaction tx) {
        var floor = template.floor();

        int halfX = ArenaShapeHelper.getSizeX(template) / 2;
        int halfZ = ArenaShapeHelper.getSizeZ(template) / 2;

        ZoneLayout layout = ZoneLayoutHelper.buildZoneLayoutWithEnvironments(zoneSettings, halfX, halfZ);

        java.util.Map<String, Integer> zoneBlockCounts = new java.util.HashMap<>();

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
                String zoneName = "default";

                Optional<ArenaZone> zone = layout.getZoneAt(dx, dz);
                if (zone.isPresent()) {
                    ArenaZone z = zone.get();
                    zoneName = z.name();
                    Optional<net.minecraft.resources.ResourceLocation> zoneMaterial =
                        z.environment().floorMaterial();
                    if (zoneMaterial.isPresent()) {
                        material = zoneMaterial.get().toString();
                    }
                }

                if (floor.borderMaterial() != null && floor.borderWidth() > 0) {
                    boolean inBorder = ArenaShapeHelper.isOnFloorBorder(dx, dz, template, floor.borderWidth());
                    if (inBorder) {
                        material = floor.borderMaterial();
                        zoneName = "border";
                    }
                }

                for (int dy = 0; dy < floor.thickness(); dy++) {
                    int worldX = originX + dx;
                    int worldY = floor.y() + dy;
                    int worldZ = originZ + dz;
                    placeBlock(worldX, worldY, worldZ, material, tx);
                }

                zoneBlockCounts.merge(zoneName, floor.thickness(), (a, b) -> a.intValue() + b.intValue());
            }
        }

        telemetry.emit("arena.floor.zone_aware", Map.of(
            "templateId", template.id(),
            "zoneCount", zoneSettings.zones().size(),
            "zoneCounts", zoneBlockCounts
        ));

        LOGGER.debug("Built zone-aware floor for '{}': {} zones, counts: {}",
            template.id(), zoneSettings.zones().size(), zoneBlockCounts);
    }

    private void buildTemplateFloor(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var floor = template.floor();

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

                    placeBlock(worldX, worldY, worldZ, material, tx);
                }
            }
        }
    }
}

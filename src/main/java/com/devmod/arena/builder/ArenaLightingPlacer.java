package com.devmod.arena.builder;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Handles lighting placement within arenas: template-level, zone-aware, and ambient grid lighting.
 * Extracted from ArenaBuilder.
 */
class ArenaLightingPlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaLightingPlacer.class);

    private final ArenaStructurePlacer structurePlacer;
    private final ArenaTelemetry telemetry;

    ArenaLightingPlacer(ArenaStructurePlacer structurePlacer, ArenaTelemetry telemetry) {
        this.structurePlacer = structurePlacer;
        this.telemetry = telemetry;
    }

    void placeLighting(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var zoneSettings = template.zoneSettings();
        if (zoneSettings != null && zoneSettings.enabled() && !zoneSettings.zones().isEmpty()) {
            placeZoneAwareLighting(template, originX, originZ, zoneSettings, tx);
            return;
        }

        placeTemplateLighting(template, originX, originZ, tx);
    }

    private void placeZoneAwareLighting(ArenaTemplate template, int originX, int originZ,
                                        ArenaTemplate.ZoneSettings zoneSettings, BuildTransaction tx) {
        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int floorY = template.floor() != null ? template.floor().y() : 64;

        int totalLights = 0;

        ZoneLayout layout = ZoneLayoutHelper.buildZoneLayoutFromSettings(zoneSettings, halfX, halfZ);

        for (var zoneDef : zoneSettings.zones()) {
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

            int zoneLights = placeZoneLighting(zone, originX, originZ, floorY,
                environment.lighting().blockLight(), template, tx);
            totalLights += zoneLights;

            LOGGER.debug("Placed {} lights in zone '{}' (target level: {}, env: {})",
                zoneLights, zoneDef.name(), environment.lighting().blockLight(),
                environment.hasRequirements() ? "custom" : "default");
        }

        if (totalLights > 0) {
            telemetry.emit("arena.lighting.zone_aware", Map.of(
                "templateId", template.id(),
                "totalLights", totalLights,
                "zoneCount", zoneSettings.zones().size()
            ));
        }
    }

    private int placeZoneLighting(ArenaZone zone, int originX, int originZ, int floorY,
                                  int targetLight, ArenaTemplate template, BuildTransaction tx) {
        var bounds = zone.bounds();

        int spacing = Math.max(4, Math.min(20, (15 - targetLight) * 2 + 2));

        int lightY = floorY + 1;
        if (template.ceiling() != null && template.ceiling().enabled()) {
            int ceilingThickness = Math.max(1, template.ceiling().thickness());
            lightY = template.ceiling().y() - ceilingThickness;
        } else if (template.walls() != null && template.walls().enabled()) {
            lightY = template.walls().startY() + template.walls().height() - 2;
        }

        String lightBlock = selectLightBlock(targetLight);
        int placedCount = 0;

        int startX = originX + bounds.x1();
        int endX = originX + bounds.x2();
        int startZ = originZ + bounds.z1();
        int endZ = originZ + bounds.z2();

        for (int x = startX + spacing / 2; x < endX; x += spacing) {
            for (int z = startZ + spacing / 2; z < endZ; z += spacing) {
                if (zone.contains(x - originX, z - originZ)) {
                    structurePlacer.placeBlock(x, lightY, z, lightBlock, tx);
                    placedCount++;
                }
            }
        }

        return placedCount;
    }

    private void placeTemplateLighting(ArenaTemplate template, int originX, int originZ, BuildTransaction tx) {
        var lighting = template.lighting();
        if (lighting == null) return;

        int placedLights = 0;
        int skippedLights = 0;

        int sizeX = ArenaShapeHelper.getSizeX(template);
        int sizeZ = ArenaShapeHelper.getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        int minX = -halfX;
        int maxX = sizeX - halfX - 1;
        int minZ = -halfZ;
        int maxZ = sizeZ - halfZ - 1;

        if (lighting.lightSources() != null && !lighting.lightSources().isEmpty()) {
            for (var lightSource : lighting.lightSources()) {
                int[] pos = lightSource.pos();
                if (pos != null && pos.length == 3) {
                    int relX = pos[0];
                    int relZ = pos[2];
                    if (relX < minX || relX > maxX || relZ < minZ || relZ > maxZ) {
                        LOGGER.warn("Light source at [{}, {}, {}] is outside arena bounds, skipping",
                            pos[0], pos[1], pos[2]);
                        skippedLights++;
                        continue;
                    }

                    int x = originX + pos[0];
                    int y = pos[1];
                    int z = originZ + pos[2];

                    if (template.floor() != null) {
                        y = template.floor().y() + pos[1];
                    }

                    structurePlacer.placeBlock(x, y, z, lightSource.block(), tx);
                    placedLights++;
                }
            }
        }

        if (skippedLights > 0) {
            LOGGER.warn("Skipped {} light sources outside arena bounds for template '{}'",
                skippedLights, template.id());
        }

        if (lighting.ambientLight() && lighting.blockLight() > 0) {
            placedLights += placeAmbientLighting(template, originX, originZ, lighting.blockLight(), tx);
        }

        if (placedLights > 0) {
            LOGGER.debug("Placed {} light sources for template '{}'", placedLights, template.id());
            telemetry.emit("arena.lighting.placed", Map.of(
                "templateId", template.id(),
                "count", placedLights,
                "ambientEnabled", lighting.ambientLight(),
                "targetBlockLight", lighting.blockLight()
            ));
        }
    }

    private int placeAmbientLighting(ArenaTemplate template, int originX, int originZ, int targetLight, BuildTransaction tx) {
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

        int placedCount = 0;
        for (int dx = spacing / 2; dx < sizeX; dx += spacing) {
            for (int dz = spacing / 2; dz < sizeZ; dz += spacing) {
                int x = startX + dx;
                int z = startZ + dz;

                structurePlacer.placeBlock(x, lightY, z, lightBlock, tx);
                placedCount++;
            }
        }

        return placedCount;
    }

    /**
     * Selects appropriate light-emitting block based on target light level.
     */
    static String selectLightBlock(int targetLight) {
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
}

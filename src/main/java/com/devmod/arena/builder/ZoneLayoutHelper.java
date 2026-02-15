package com.devmod.arena.builder;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;

/**
 * Shared zone layout construction logic used by structure and lighting placers.
 */
final class ZoneLayoutHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneLayoutHelper.class);

    private ZoneLayoutHelper() {}

    /**
     * Builds zone layout with proper ZoneEnvironment including floor materials.
     */
    static ZoneLayout buildZoneLayoutWithEnvironments(ArenaTemplate.ZoneSettings settings, int halfX, int halfZ) {
        var zones = settings.zones();
        int zoneCount = zones.size();

        ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

        if (zoneCount == 1) {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            var def = zones.get(0);
            ArenaZone zone = ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ)
                .withEnvironment(createZoneEnvironment(def));
            builder.addZone(zone);
        } else if (zoneCount == 2) {
            builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
            var def0 = zones.get(0);
            var def1 = zones.get(1);
            builder.addZone(ArenaZone.rectangular(def0.name(), -halfX, -halfZ, 0, halfZ)
                .withEnvironment(createZoneEnvironment(def0)));
            builder.addZone(ArenaZone.rectangular(def1.name(), 0, -halfZ, halfX, halfZ)
                .withEnvironment(createZoneEnvironment(def1)));
        } else if (zoneCount <= 4) {
            builder.strategy(ZoneLayout.LayoutStrategy.QUADRANT);
            if (zones.size() >= 1) {
                builder.addZone(ArenaZone.rectangular(zones.get(0).name(), 0, -halfZ, halfX, 0)
                    .withEnvironment(createZoneEnvironment(zones.get(0))));
            }
            if (zones.size() >= 2) {
                builder.addZone(ArenaZone.rectangular(zones.get(1).name(), -halfX, -halfZ, 0, 0)
                    .withEnvironment(createZoneEnvironment(zones.get(1))));
            }
            if (zones.size() >= 3) {
                builder.addZone(ArenaZone.rectangular(zones.get(2).name(), -halfX, 0, 0, halfZ)
                    .withEnvironment(createZoneEnvironment(zones.get(2))));
            }
            if (zones.size() >= 4) {
                builder.addZone(ArenaZone.rectangular(zones.get(3).name(), 0, 0, halfX, halfZ)
                    .withEnvironment(createZoneEnvironment(zones.get(3))));
            }
        } else {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            builder.addZone(ArenaZone.rectangular("main", -halfX, -halfZ, halfX, halfZ));
        }

        return builder.build();
    }

    /**
     * Builds a ZoneLayout from template zone settings for bounds checking (without environments).
     */
    static ZoneLayout buildZoneLayoutFromSettings(ArenaTemplate.ZoneSettings settings, int halfX, int halfZ) {
        var zones = settings.zones();
        int zoneCount = zones.size();

        ZoneLayout.Builder builder = ZoneLayout.builder(Math.max(halfX, halfZ));

        if (zoneCount == 1) {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            var def = zones.get(0);
            builder.addZone(ArenaZone.rectangular(def.name(), -halfX, -halfZ, halfX, halfZ));
        } else if (zoneCount == 2) {
            builder.strategy(ZoneLayout.LayoutStrategy.STRIPED_VERTICAL);
            var def0 = zones.get(0);
            var def1 = zones.get(1);
            builder.addZone(ArenaZone.rectangular(def0.name(), -halfX, -halfZ, 0, halfZ));
            builder.addZone(ArenaZone.rectangular(def1.name(), 0, -halfZ, halfX, halfZ));
        } else if (zoneCount <= 4) {
            builder.strategy(ZoneLayout.LayoutStrategy.QUADRANT);
            if (zones.size() >= 1) builder.addZone(ArenaZone.rectangular(zones.get(0).name(), 0, -halfZ, halfX, 0));
            if (zones.size() >= 2) builder.addZone(ArenaZone.rectangular(zones.get(1).name(), -halfX, -halfZ, 0, 0));
            if (zones.size() >= 3) builder.addZone(ArenaZone.rectangular(zones.get(2).name(), -halfX, 0, 0, halfZ));
            if (zones.size() >= 4) builder.addZone(ArenaZone.rectangular(zones.get(3).name(), 0, 0, halfX, halfZ));
        } else {
            builder.strategy(ZoneLayout.LayoutStrategy.SINGLE);
            builder.addZone(ArenaZone.rectangular("main", -halfX, -halfZ, halfX, halfZ));
        }

        return builder.build();
    }

    /**
     * Creates a ZoneEnvironment from an ArenaTemplate.ZoneDefinition.
     */
    static ZoneEnvironment createZoneEnvironment(ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef) {
        Optional<net.minecraft.resources.ResourceLocation> biome = Optional.empty();
        String biomeStr = zoneDef.biome();
        if (biomeStr != null && !biomeStr.isEmpty()) {
            biome = Optional.of(net.minecraft.resources.ResourceLocation.parse(biomeStr));
        }

        Optional<net.minecraft.resources.ResourceLocation> floorMaterial = Optional.empty();
        String floorStr = zoneDef.floorMaterial();
        if (floorStr != null && !floorStr.isEmpty()) {
            floorMaterial = Optional.of(net.minecraft.resources.ResourceLocation.parse(floorStr));
        }

        Integer skyLightVal = zoneDef.skyLight();
        Integer blockLightVal = zoneDef.blockLight();
        int skyLight = skyLightVal != null ? skyLightVal.intValue() : 15;
        int blockLight = blockLightVal != null ? blockLightVal.intValue() : 0;
        boolean placeSources = blockLight > 0;
        ZoneEnvironment.LightingConfig lighting = new ZoneEnvironment.LightingConfig(skyLight, blockLight, placeSources);

        ZoneEnvironment.TimeConfig time = ZoneEnvironment.TimeConfig.ANY;
        String timeStr = zoneDef.time();
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                time = ZoneEnvironment.TimeConfig.valueOf(timeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown time config '{}' for zone '{}', using ANY", timeStr, zoneDef.name());
            }
        }

        return new ZoneEnvironment(biome, floorMaterial, lighting, time);
    }
}

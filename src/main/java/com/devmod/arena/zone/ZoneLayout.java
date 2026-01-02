package com.devmod.arena.zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents a complete layout of zones within an arena.
 * Contains all zones, their transitions, and layout metadata.
 */
public record ZoneLayout(
    List<ArenaZone> zones,
    LayoutStrategy strategy,
    ZoneTransition defaultTransition,
    int totalRadius,
    Map<String, ZoneTransition> customTransitions
) {
    /**
     * Creates a single-zone layout covering the entire arena.
     */
    public static ZoneLayout single(int radius, ZoneEnvironment environment) {
        ArenaZone zone = ArenaZone.circular("main", radius).withEnvironment(environment);
        return new ZoneLayout(
            List.of(zone),
            LayoutStrategy.SINGLE,
            ZoneTransition.HARD,
            radius,
            Map.of()
        );
    }

    /**
     * Creates an empty layout builder.
     */
    public static Builder builder(int radius) {
        return new Builder(radius);
    }

    /**
     * Gets the zone containing the given position.
     */
    public Optional<ArenaZone> getZoneAt(BlockPos pos) {
        return getZoneAt(pos.getX(), pos.getZ());
    }

    /**
     * Gets the zone containing the given coordinates.
     */
    public Optional<ArenaZone> getZoneAt(double x, double z) {
        // Check zones in priority order (highest first)
        return zones.stream()
            .sorted(Comparator.comparingInt(ArenaZone::priority).reversed())
            .filter(zone -> zone.contains(x, z))
            .findFirst();
    }

    /**
     * Gets the zone assigned to a specific mob.
     */
    public Optional<ArenaZone> getZoneForMob(ResourceLocation mobId) {
        return zones.stream()
            .filter(zone -> zone.assignedMobs().contains(mobId))
            .findFirst();
    }

    /**
     * Gets all zones that could contain a specific mob type.
     */
    public List<ArenaZone> getValidZonesForMob(ResourceLocation mobId) {
        return zones.stream()
            .filter(zone -> zone.assignedMobs().isEmpty() || zone.assignedMobs().contains(mobId))
            .toList();
    }

    /**
     * Gets the transition between two zones.
     */
    public ZoneTransition getTransition(ArenaZone from, ArenaZone to) {
        String key = from.name() + "->" + to.name();
        return customTransitions.getOrDefault(key, defaultTransition);
    }

    /**
     * Returns the total area covered by all zones.
     */
    public double getTotalArea() {
        return zones.stream().mapToDouble(ArenaZone::getArea).sum();
    }

    /**
     * Returns the number of zones.
     */
    public int zoneCount() {
        return zones.size();
    }

    /**
     * Checks if this layout has multiple zones.
     */
    public boolean isMultiZone() {
        return zones.size() > 1;
    }

    /**
     * Gets a random spawn position within a specific zone.
     */
    public BlockPos getRandomPositionInZone(String zoneName, Random random, int yLevel) {
        return zones.stream()
            .filter(z -> z.name().equals(zoneName))
            .findFirst()
            .map(z -> z.getRandomPosition(random, yLevel))
            .orElse(new BlockPos(0, yLevel, 0));
    }

    // ============== Layout Strategies ==============

    /**
     * Strategy for arranging zones within an arena.
     */
    public enum LayoutStrategy {
        /** Single zone covering entire arena */
        SINGLE,
        /** Two zones split horizontally */
        STRIPED_HORIZONTAL,
        /** Two zones split vertically */
        STRIPED_VERTICAL,
        /** Four quadrant zones */
        QUADRANT,
        /** Concentric ring zones */
        RADIAL,
        /** Custom layout defined manually */
        CUSTOM
    }

    // ============== Builder ==============

    /**
     * Builder for creating zone layouts.
     */
    public static class Builder {
        private static final Logger LOGGER = LoggerFactory.getLogger(ZoneLayout.Builder.class);

        private final int radius;
        private final List<ArenaZone> zones = new ArrayList<>();
        private LayoutStrategy strategy = LayoutStrategy.CUSTOM;
        private ZoneTransition defaultTransition = ZoneTransition.HARD;
        private final Map<String, ZoneTransition> customTransitions = new HashMap<>();

        public Builder(int radius) {
            this.radius = radius;
        }

        /**
         * Clears zones with a warning if not empty.
         * Called by layout strategy methods that replace all zones.
         */
        private void clearZonesWithWarning(String strategyName) {
            if (!zones.isEmpty()) {
                LOGGER.warn("ZoneLayout.Builder.{}() is clearing {} previously added zones. " +
                    "Layout strategy methods replace all zones - call them before addZone() or use CUSTOM strategy.",
                    strategyName, zones.size());
                zones.clear();
            }
        }

        public Builder addZone(ArenaZone zone) {
            this.zones.add(zone);
            return this;
        }

        public Builder strategy(LayoutStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder defaultTransition(ZoneTransition transition) {
            this.defaultTransition = transition;
            return this;
        }

        public Builder customTransition(String fromZone, String toZone, ZoneTransition transition) {
            customTransitions.put(fromZone + "->" + toZone, transition);
            return this;
        }

        /**
         * Creates a striped layout (2 zones, horizontal split).
         */
        public Builder stripedHorizontal(ZoneEnvironment zone1Env, ZoneEnvironment zone2Env) {
            this.strategy = LayoutStrategy.STRIPED_HORIZONTAL;
            clearZonesWithWarning("stripedHorizontal");

            ArenaZone zone1 = new ArenaZone(
                "zone_north",
                ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(-radius, -radius, radius, 0),
                zone1Env,
                List.of(),
                0
            );
            ArenaZone zone2 = new ArenaZone(
                "zone_south",
                ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(-radius, 0, radius, radius),
                zone2Env,
                List.of(),
                0
            );

            zones.add(zone1);
            zones.add(zone2);
            return this;
        }

        /**
         * Creates a striped layout (2 zones, vertical split).
         */
        public Builder stripedVertical(ZoneEnvironment zone1Env, ZoneEnvironment zone2Env) {
            this.strategy = LayoutStrategy.STRIPED_VERTICAL;
            clearZonesWithWarning("stripedVertical");

            ArenaZone zone1 = new ArenaZone(
                "zone_west",
                ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(-radius, -radius, 0, radius),
                zone1Env,
                List.of(),
                0
            );
            ArenaZone zone2 = new ArenaZone(
                "zone_east",
                ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(0, -radius, radius, radius),
                zone2Env,
                List.of(),
                0
            );

            zones.add(zone1);
            zones.add(zone2);
            return this;
        }

        /**
         * Creates a quadrant layout (4 zones).
         */
        public Builder quadrant(ZoneEnvironment ne, ZoneEnvironment nw, ZoneEnvironment sw, ZoneEnvironment se) {
            this.strategy = LayoutStrategy.QUADRANT;
            clearZonesWithWarning("quadrant");

            zones.add(new ArenaZone("zone_ne", ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(0, -radius, radius, 0), ne, List.of(), 0));
            zones.add(new ArenaZone("zone_nw", ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(-radius, -radius, 0, 0), nw, List.of(), 0));
            zones.add(new ArenaZone("zone_sw", ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(-radius, 0, 0, radius), sw, List.of(), 0));
            zones.add(new ArenaZone("zone_se", ArenaZone.ZoneShape.RECTANGULAR,
                ArenaZone.ZoneBounds.rect(0, 0, radius, radius), se, List.of(), 0));

            return this;
        }

        /**
         * Creates a radial layout (concentric rings).
         */
        public Builder radial(List<ZoneEnvironment> ringEnvironments) {
            this.strategy = LayoutStrategy.RADIAL;
            clearZonesWithWarning("radial");

            int ringCount = ringEnvironments.size();
            int ringWidth = radius / ringCount;

            for (int i = ringCount - 1; i >= 0; i--) {
                int outerRadius = (i + 1) * ringWidth;
                int innerRadius = i * ringWidth;

                ArenaZone zone;
                if (i == 0) {
                    // Innermost is a circle
                    zone = ArenaZone.circular("ring_center", outerRadius)
                        .withEnvironment(ringEnvironments.get(i))
                        .withPriority(ringCount - i);
                } else {
                    // Outer rings - use proper inner/outer radius
                    zone = new ArenaZone(
                        "ring_" + i,
                        ArenaZone.ZoneShape.RING,
                        ArenaZone.ZoneBounds.ring(outerRadius, innerRadius),
                        ringEnvironments.get(i),
                        List.of(),
                        ringCount - i
                    );
                }
                zones.add(zone);
            }

            return this;
        }

        public ZoneLayout build() {
            if (zones.isEmpty()) {
                // Default to single zone
                zones.add(ArenaZone.circular("main", radius));
            }
            return new ZoneLayout(
                List.copyOf(zones),
                strategy,
                defaultTransition,
                radius,
                Map.copyOf(customTransitions)
            );
        }
    }
}

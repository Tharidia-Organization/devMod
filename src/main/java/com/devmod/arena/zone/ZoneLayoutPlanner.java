package com.devmod.arena.zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirements.BiomeRequirement;
import com.devmod.mob.MobRequirements.LightRequirement;
import com.devmod.mob.MobRequirements.SpaceRequirement;
import com.devmod.mob.MobRequirements.TimeRequirement;
import com.devmod.mob.MobRequirementsRegistry;

/**
 * Plans optimal zone layouts for arenas based on mob requirements.
 * Groups mobs by environmental compatibility and generates appropriate zone configurations.
 */
public class ZoneLayoutPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneLayoutPlanner.class);

    /** Maximum number of zones to create */
    private static final int MAX_ZONES = 6;

    /** Minimum zone size (radius) for spawning */
    private static final int MIN_ZONE_RADIUS = 8;

    /**
     * Plans a zone layout for the given mob list and arena size.
     *
     * @param mobIds List of mob IDs that will spawn in the arena
     * @param arenaRadius Total arena radius
     * @return Optimal zone layout for the mobs
     */
    public ZoneLayout planLayout(List<ResourceLocation> mobIds, int arenaRadius) {
        if (mobIds == null || mobIds.isEmpty()) {
            LOGGER.debug("No mobs specified, creating single default zone");
            return ZoneLayout.single(arenaRadius, ZoneEnvironment.DEFAULT);
        }

        // Get requirements for all mobs
        Map<ResourceLocation, MobRequirements> requirements = mobIds.stream()
            .distinct()
            .collect(Collectors.toMap(
                id -> id,
                id -> MobRequirementsRegistry.INSTANCE.getOrDefault(id)
            ));

        // Group mobs by environment compatibility
        List<MobGroup> groups = groupMobsByCompatibility(requirements);
        LOGGER.debug("Grouped {} mobs into {} compatible groups", mobIds.size(), groups.size());

        // Limit groups to MAX_ZONES
        if (groups.size() > MAX_ZONES) {
            groups = mergeSmallestGroups(groups, MAX_ZONES);
            LOGGER.debug("Merged groups to {} zones (max)", groups.size());
        }

        // Check if single zone is sufficient
        if (groups.size() == 1 || arenaRadius < MIN_ZONE_RADIUS * 2) {
            ZoneEnvironment env = determineEnvironment(groups.get(0));
            return ZoneLayout.single(arenaRadius, env);
        }

        // Select layout strategy based on group count
        ZoneLayout.LayoutStrategy strategy = selectStrategy(groups.size(), arenaRadius);

        // Build the layout
        return buildLayout(groups, arenaRadius, strategy);
    }

    /**
     * Groups mobs by environmental compatibility.
     */
    private List<MobGroup> groupMobsByCompatibility(Map<ResourceLocation, MobRequirements> requirements) {
        List<MobGroup> groups = new ArrayList<>();
        Set<ResourceLocation> assigned = new HashSet<>();

        // First pass: create groups for mobs with specific requirements
        for (var entry : requirements.entrySet()) {
            if (assigned.contains(entry.getKey())) continue;

            MobRequirements reqs = entry.getValue();

            // Skip mobs with default/any requirements for now
            if (!hasSignificantRequirements(reqs)) continue;

            // Find compatible mobs
            MobGroup group = new MobGroup(new ArrayList<>(), reqs);
            group.mobs.add(entry.getKey());
            assigned.add(entry.getKey());

            for (var other : requirements.entrySet()) {
                if (assigned.contains(other.getKey())) continue;
                if (isCompatible(reqs, other.getValue())) {
                    group.mobs.add(other.getKey());
                    assigned.add(other.getKey());
                }
            }

            groups.add(group);
        }

        // Second pass: add remaining mobs to default group
        List<ResourceLocation> defaultMobs = requirements.keySet().stream()
            .filter(id -> !assigned.contains(id))
            .toList();

        if (!defaultMobs.isEmpty()) {
            MobGroup defaultGroup = new MobGroup(new ArrayList<>(defaultMobs), null);
            groups.add(defaultGroup);
        }

        // Sort groups by size (largest first)
        groups.sort(Comparator.comparingInt((MobGroup g) -> g.mobs.size()).reversed());

        return groups;
    }

    /**
     * Checks if a mob has significant environmental requirements.
     */
    private boolean hasSignificantRequirements(MobRequirements reqs) {
        return !reqs.biome().isDefault() ||
               !reqs.light().isDefault() ||
               !reqs.floor().isDefault() ||
               reqs.time() != TimeRequirement.ANY;
    }

    /**
     * Checks if two mob requirements are compatible (can share a zone).
     */
    private boolean isCompatible(MobRequirements a, MobRequirements b) {
        // Check light compatibility
        if (!isLightCompatible(a.light(), b.light())) {
            return false;
        }

        // Check time compatibility
        if (!isTimeCompatible(a.time(), b.time())) {
            return false;
        }

        // Check biome compatibility (if both have preferences)
        if (!isBiomeCompatible(a.biome(), b.biome())) {
            return false;
        }

        return true;
    }

    private boolean isLightCompatible(LightRequirement a, LightRequirement b) {
        // Check if light ranges overlap
        boolean rangeOverlaps = a.minLight() <= b.maxLight() && b.minLight() <= a.maxLight();

        // Check preference compatibility
        boolean preferenceConflict = (a.prefersDark() && b.prefersLight()) ||
                                     (a.prefersLight() && b.prefersDark());

        return rangeOverlaps && !preferenceConflict;
    }

    private boolean isTimeCompatible(TimeRequirement a, TimeRequirement b) {
        if (a == TimeRequirement.ANY || b == TimeRequirement.ANY) {
            return true;
        }
        return a == b;
    }

    private boolean isBiomeCompatible(BiomeRequirement a, BiomeRequirement b) {
        // If either has no preference, compatible
        if (a.isDefault() || b.isDefault()) {
            return true;
        }

        // If both are required and different, not compatible
        if (a.required() && b.required()) {
            return a.preferredBiome().equals(b.preferredBiome());
        }

        // Check for overlap in valid biomes
        if (!a.validBiomes().isEmpty() && !b.validBiomes().isEmpty()) {
            return a.validBiomes().stream().anyMatch(b.validBiomes()::contains);
        }

        return true;
    }

    /**
     * Merges smallest groups until we have the target count.
     */
    private List<MobGroup> mergeSmallestGroups(List<MobGroup> groups, int targetCount) {
        List<MobGroup> result = new ArrayList<>(groups);

        while (result.size() > targetCount) {
            // Find two smallest groups
            result.sort(Comparator.comparingInt((MobGroup g) -> g.mobs.size()));

            if (result.size() >= 2) {
                MobGroup smallest = result.remove(0);
                MobGroup secondSmallest = result.remove(0);

                // Merge them
                List<ResourceLocation> mergedMobs = new ArrayList<>(smallest.mobs);
                mergedMobs.addAll(secondSmallest.mobs);

                // Merge requirements from both groups to preserve all constraints
                MobRequirements mergedReqs = mergeGroupRequirements(
                    smallest.baseRequirements, secondSmallest.baseRequirements);
                MobGroup merged = new MobGroup(mergedMobs, mergedReqs);
                result.add(merged);
            }
        }

        return result;
    }

    /**
     * Selects the best layout strategy for the number of groups.
     */
    private ZoneLayout.LayoutStrategy selectStrategy(int groupCount, int radius) {
        return switch (groupCount) {
            case 1 -> ZoneLayout.LayoutStrategy.SINGLE;
            case 2 -> ZoneLayout.LayoutStrategy.STRIPED_HORIZONTAL;
            case 3, 4 -> ZoneLayout.LayoutStrategy.QUADRANT;
            default -> radius >= 32 ? ZoneLayout.LayoutStrategy.RADIAL : ZoneLayout.LayoutStrategy.QUADRANT;
        };
    }

    /**
     * Determines the optimal environment for a mob group.
     */
    private ZoneEnvironment determineEnvironment(MobGroup group) {
        if (group.baseRequirements == null) {
            return ZoneEnvironment.DEFAULT;
        }

        MobRequirements reqs = group.baseRequirements;

        // Determine biome
        Optional<ResourceLocation> biome = reqs.biome().preferredBiome();

        // Determine floor material
        Optional<ResourceLocation> floor = reqs.floor().preferredBlock();

        // Determine lighting
        ZoneEnvironment.LightingConfig lighting;
        if (reqs.light().prefersDark()) {
            lighting = ZoneEnvironment.LightingConfig.dark(reqs.light().maxLight());
        } else if (reqs.light().prefersLight()) {
            lighting = ZoneEnvironment.LightingConfig.BRIGHT;
        } else {
            lighting = ZoneEnvironment.LightingConfig.DEFAULT;
        }

        // Determine time
        ZoneEnvironment.TimeConfig time = switch (reqs.time()) {
            case DAY -> ZoneEnvironment.TimeConfig.DAY;
            case NIGHT -> ZoneEnvironment.TimeConfig.NIGHT;
            case DUSK -> ZoneEnvironment.TimeConfig.DUSK;
            case DAWN -> ZoneEnvironment.TimeConfig.DAWN;
            case ANY -> ZoneEnvironment.TimeConfig.ANY;
        };

        return new ZoneEnvironment(biome, floor, lighting, time);
    }

    /**
     * Builds the zone layout using the selected strategy.
     */
    private ZoneLayout buildLayout(List<MobGroup> groups, int radius, ZoneLayout.LayoutStrategy strategy) {
        ZoneLayout.Builder builder = ZoneLayout.builder(radius).strategy(strategy);

        switch (strategy) {
            case SINGLE -> {
                ZoneEnvironment env = groups.isEmpty() ? ZoneEnvironment.DEFAULT : determineEnvironment(groups.get(0));
                return ZoneLayout.single(radius, env);
            }
            case STRIPED_HORIZONTAL, STRIPED_VERTICAL -> {
                ZoneEnvironment env1 = groups.size() > 0 ? determineEnvironment(groups.get(0)) : ZoneEnvironment.DEFAULT;
                ZoneEnvironment env2 = groups.size() > 1 ? determineEnvironment(groups.get(1)) : ZoneEnvironment.DEFAULT;

                if (strategy == ZoneLayout.LayoutStrategy.STRIPED_HORIZONTAL) {
                    builder.stripedHorizontal(env1, env2);
                } else {
                    builder.stripedVertical(env1, env2);
                }

                // Assign mobs to zones
                ZoneLayout layout = builder.build();
                return assignMobsToZones(layout, groups);
            }
            case QUADRANT -> {
                ZoneEnvironment ne = groups.size() > 0 ? determineEnvironment(groups.get(0)) : ZoneEnvironment.DEFAULT;
                ZoneEnvironment nw = groups.size() > 1 ? determineEnvironment(groups.get(1)) : ZoneEnvironment.DEFAULT;
                ZoneEnvironment sw = groups.size() > 2 ? determineEnvironment(groups.get(2)) : ZoneEnvironment.DEFAULT;
                ZoneEnvironment se = groups.size() > 3 ? determineEnvironment(groups.get(3)) : ZoneEnvironment.DEFAULT;

                builder.quadrant(ne, nw, sw, se);
                ZoneLayout layout = builder.build();
                return assignMobsToZones(layout, groups);
            }
            case RADIAL -> {
                List<ZoneEnvironment> envs = groups.stream()
                    .map(this::determineEnvironment)
                    .collect(Collectors.toList());

                builder.radial(envs);
                ZoneLayout layout = builder.build();
                return assignMobsToZones(layout, groups);
            }
            default -> {
                return ZoneLayout.single(radius, ZoneEnvironment.DEFAULT);
            }
        }
    }

    /**
     * Assigns mobs from groups to zones in a layout.
     */
    private ZoneLayout assignMobsToZones(ZoneLayout layout, List<MobGroup> groups) {
        List<ArenaZone> updatedZones = new ArrayList<>();

        for (int i = 0; i < layout.zones().size(); i++) {
            ArenaZone zone = layout.zones().get(i);
            List<ResourceLocation> mobs = i < groups.size() ? groups.get(i).mobs : List.of();
            updatedZones.add(zone.withMobs(mobs));
        }

        return new ZoneLayout(
            updatedZones,
            layout.strategy(),
            layout.defaultTransition(),
            layout.totalRadius(),
            layout.customTransitions()
        );
    }

    /**
     * Merges requirements from two groups, preserving constraints from both.
     * Uses the built-in MobRequirements.merge() which prefers non-default values.
     */
    private MobRequirements mergeGroupRequirements(MobRequirements a, MobRequirements b) {
        if (a == null) return b;
        if (b == null) return a;
        // merge() prefers non-default values from 'b' over 'a'
        return a.merge(b);
    }

    /**
     * Internal class representing a group of compatible mobs.
     */
    private static class MobGroup {
        final List<ResourceLocation> mobs;
        final MobRequirements baseRequirements;

        MobGroup(List<ResourceLocation> mobs, MobRequirements baseRequirements) {
            this.mobs = mobs;
            this.baseRequirements = baseRequirements;
        }
    }

    // ============== Public API ==============

    /**
     * Creates a planner instance.
     */
    public static ZoneLayoutPlanner create() {
        return new ZoneLayoutPlanner();
    }

    /**
     * Quickly checks if a mob list needs multiple zones.
     */
    public boolean requiresMultipleZones(List<ResourceLocation> mobIds) {
        if (mobIds == null || mobIds.size() <= 1) {
            return false;
        }

        Map<ResourceLocation, MobRequirements> reqs = mobIds.stream()
            .distinct()
            .collect(Collectors.toMap(id -> id, MobRequirementsRegistry.INSTANCE::getOrDefault));

        List<MobGroup> groups = groupMobsByCompatibility(reqs);
        return groups.size() > 1;
    }

    /**
     * Gets the recommended minimum arena radius for the given mobs.
     */
    public int getRecommendedRadius(List<ResourceLocation> mobIds) {
        if (mobIds == null || mobIds.isEmpty()) {
            return 16;
        }

        // Calculate based on mob space requirements
        int maxSpace = mobIds.stream()
            .map(MobRequirementsRegistry.INSTANCE::getOrDefault)
            .map(MobRequirements::space)
            .mapToInt(SpaceRequirement::minArenaRadius)
            .max()
            .orElse(16);

        // Add space for multiple mobs
        int mobCountFactor = Math.min(mobIds.size(), 10) * 2;

        // Check if multiple zones are needed
        if (requiresMultipleZones(mobIds)) {
            return Math.max(32, maxSpace + mobCountFactor);
        }

        return Math.max(16, maxSpace + mobCountFactor);
    }
}

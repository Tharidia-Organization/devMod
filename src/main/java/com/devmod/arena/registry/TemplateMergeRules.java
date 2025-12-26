package com.devmod.arena.registry;

import java.util.Map;

public final class TemplateMergeRules {

    public enum MergeStrategy {
        OVERRIDE,
        SHALLOW_MERGE,
        SKIP
    }

    public static final Map<String, MergeStrategy> FIELD_STRATEGIES = Map.ofEntries(
        Map.entry("id", MergeStrategy.SKIP),
        Map.entry("version", MergeStrategy.SKIP),
        Map.entry("extends", MergeStrategy.SKIP),
        Map.entry("size", MergeStrategy.OVERRIDE),
        Map.entry("sizeX", MergeStrategy.OVERRIDE),
        Map.entry("sizeZ", MergeStrategy.OVERRIDE),
        Map.entry("origin", MergeStrategy.SHALLOW_MERGE),
        Map.entry("floor", MergeStrategy.SHALLOW_MERGE),
        Map.entry("walls", MergeStrategy.SHALLOW_MERGE),
        Map.entry("ceiling", MergeStrategy.SHALLOW_MERGE),
        Map.entry("underfloor", MergeStrategy.SHALLOW_MERGE),
        Map.entry("palette", MergeStrategy.SHALLOW_MERGE),
        Map.entry("biome", MergeStrategy.SHALLOW_MERGE),
        Map.entry("lighting", MergeStrategy.SHALLOW_MERGE),
        Map.entry("lighting.lightSources", MergeStrategy.OVERRIDE),
        Map.entry("spawnSlots", MergeStrategy.OVERRIDE),
        Map.entry("playerSpawnOffset", MergeStrategy.OVERRIDE),
        Map.entry("mobSpawnStrategy", MergeStrategy.OVERRIDE),
        Map.entry("forbiddenZones", MergeStrategy.OVERRIDE),
        Map.entry("hazards", MergeStrategy.OVERRIDE),
        Map.entry("environment", MergeStrategy.SHALLOW_MERGE),
        Map.entry("environment.particles", MergeStrategy.OVERRIDE),
        Map.entry("compat", MergeStrategy.SHALLOW_MERGE),
        Map.entry("instanceSettings", MergeStrategy.SHALLOW_MERGE),
        Map.entry("structureNbt", MergeStrategy.OVERRIDE),
        Map.entry("limits", MergeStrategy.SHALLOW_MERGE),
        Map.entry("buildSettings", MergeStrategy.SHALLOW_MERGE),
        Map.entry("tags", MergeStrategy.OVERRIDE)
    );

    private TemplateMergeRules() {}

    public static MergeStrategy strategyFor(String field) {
        return FIELD_STRATEGIES.getOrDefault(field, MergeStrategy.OVERRIDE);
    }
}

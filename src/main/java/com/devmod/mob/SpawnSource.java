package com.devmod.mob;

import java.util.Set;

/**
 * Categorizes how a mob can spawn in the world.
 * Used to determine arena generation priority:
 * - STRUCTURE_ONLY: arena MUST match structure characteristics
 * - NATURAL_ONLY: use biome/light/floor requirements
 * - NATURAL_AND_STRUCTURE: default to natural unless quest requires structure
 */
public enum SpawnSource {
    /**
     * Mob spawns only in structures (e.g., Acolyte in Crypt/Haunted House).
     * Arena generation should prioritize structure characteristics.
     */
    STRUCTURE_ONLY,

    /**
     * Mob spawns naturally in the world via biome/light conditions.
     * Arena generation uses standard MobRequirements.
     */
    NATURAL_ONLY,

    /**
     * Mob can spawn both naturally AND in structures.
     * Default: use natural spawn conditions.
     * If quest is "dungeon/boss/lore": prefer structure characteristics.
     */
    NATURAL_AND_STRUCTURE,

    /**
     * Unable to determine spawn source (fallback).
     * Treated as NATURAL_ONLY for arena generation.
     */
    UNKNOWN;

    /**
     * Quest types that should prefer structure characteristics
     * when mob has NATURAL_AND_STRUCTURE spawn source.
     */
    public static final Set<String> STRUCTURE_PREFERRED_QUEST_TYPES = Set.of(
        "dungeon",
        "dungeon_run",
        "boss",
        "boss_fight",
        "lore",
        "elite",
        "raid"
    );

    /**
     * Determines if structure characteristics should be used for arena generation.
     *
     * @param questType The type of quest being started (nullable)
     * @return true if structure characteristics should be used
     */
    public boolean shouldUseStructure(String questType) {
        return switch (this) {
            case STRUCTURE_ONLY -> true;
            case NATURAL_ONLY, UNKNOWN -> false;
            case NATURAL_AND_STRUCTURE -> questType != null
                && STRUCTURE_PREFERRED_QUEST_TYPES.contains(questType.toLowerCase());
        };
    }

    /**
     * Returns true if this spawn source has any structure component.
     */
    public boolean hasStructureSpawn() {
        return this == STRUCTURE_ONLY || this == NATURAL_AND_STRUCTURE;
    }

    /**
     * Returns true if this spawn source has any natural component.
     */
    public boolean hasNaturalSpawn() {
        return this == NATURAL_ONLY || this == NATURAL_AND_STRUCTURE || this == UNKNOWN;
    }
}

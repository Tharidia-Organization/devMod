package com.devmod.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;

/**
 * Detects how a mob spawns in the world: naturally, in structures, or both.
 *
 * Detection strategy:
 * 1. Check SpawnPlacements registry for natural spawn data
 * 2. Check hardcoded structure mob mappings (for known mods)
 * 3. Use naming heuristics for unknown mobs
 * 4. Allow config overrides
 *
 * This follows the "90% coverage" principle - covers most cases
 * with manual override for edge cases.
 */
public class SpawnSourceDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpawnSourceDetector.class);

    // ============== Hardcoded Structure Mappings ==============
    // These are mobs known to spawn ONLY in specific structures.
    // Format: mobId -> List<structureId>

    private static final Map<String, List<String>> STRUCTURE_ONLY_MOBS = new LinkedHashMap<>();
    private static final Map<String, List<String>> STRUCTURE_ALSO_MOBS = new LinkedHashMap<>();

    static {
        // === THE GRAVEYARD ===
        // These mobs spawn ONLY in structures (Crypt, Haunted House)
        registerStructureOnly("graveyard:acolyte", List.of("graveyard:crypt", "graveyard:haunted_house"));
        registerStructureOnly("graveyard:corrupted_pillager", List.of("graveyard:crypt"));
        registerStructureOnly("graveyard:corrupted_vindicator", List.of("graveyard:crypt"));

        // These spawn naturally AND in structures
        registerStructureAlso("graveyard:ghoul", List.of("graveyard:large_graveyard", "graveyard:small_graveyard"));
        registerStructureAlso("graveyard:revenant", List.of("graveyard:crypt", "graveyard:ruins"));
        registerStructureAlso("graveyard:nightmare", List.of("graveyard:haunted_house"));
        registerStructureAlso("graveyard:skeleton_creeper", List.of("graveyard:ruins"));

        // === VANILLA ===
        // Structure-only mobs
        registerStructureOnly("minecraft:elder_guardian", List.of("minecraft:ocean_monument"));
        registerStructureOnly("minecraft:shulker", List.of("minecraft:end_city"));
        registerStructureOnly("minecraft:evoker", List.of("minecraft:mansion"));
        registerStructureOnly("minecraft:vindicator", List.of("minecraft:mansion"));
        registerStructureOnly("minecraft:illusioner", List.of("minecraft:mansion")); // Not naturally spawned

        // Mobs that spawn naturally AND in structures
        registerStructureAlso("minecraft:blaze", List.of("minecraft:fortress"));
        registerStructureAlso("minecraft:wither_skeleton", List.of("minecraft:fortress"));
        registerStructureAlso("minecraft:guardian", List.of("minecraft:ocean_monument"));
        registerStructureAlso("minecraft:piglin_brute", List.of("minecraft:bastion_remnant"));
        registerStructureAlso("minecraft:warden", List.of("minecraft:ancient_city"));

        // === TWILIGHT FOREST ===
        registerStructureOnly("twilightforest:lich", List.of("twilightforest:lich_tower"));
        registerStructureOnly("twilightforest:naga", List.of("twilightforest:naga_courtyard"));
        registerStructureOnly("twilightforest:hydra", List.of("twilightforest:hydra_lair"));
        registerStructureOnly("twilightforest:ur_ghast", List.of("twilightforest:dark_tower"));
        registerStructureOnly("twilightforest:knight_phantom", List.of("twilightforest:knight_stronghold"));
        registerStructureOnly("twilightforest:minoshroom", List.of("twilightforest:labyrinth"));
        registerStructureOnly("twilightforest:alpha_yeti", List.of("twilightforest:yeti_lair"));
        registerStructureOnly("twilightforest:snow_queen", List.of("twilightforest:aurora_palace"));

        registerStructureAlso("twilightforest:redcap", List.of("twilightforest:hollow_hill_small", "twilightforest:hollow_hill_medium"));
        registerStructureAlso("twilightforest:swarm_spider", List.of("twilightforest:hollow_hill_small"));
        registerStructureAlso("twilightforest:fire_beetle", List.of("twilightforest:hollow_hill_medium", "twilightforest:hollow_hill_large"));
        registerStructureAlso("twilightforest:slime_beetle", List.of("twilightforest:hollow_hill_medium", "twilightforest:hollow_hill_large"));
        registerStructureAlso("twilightforest:tower_golem", List.of("twilightforest:dark_tower"));

        // === ICE AND FIRE ===
        registerStructureOnly("iceandfire:fire_dragon", List.of("iceandfire:fire_dragon_cave"));
        registerStructureOnly("iceandfire:ice_dragon", List.of("iceandfire:ice_dragon_cave"));
        registerStructureOnly("iceandfire:lightning_dragon", List.of("iceandfire:lightning_dragon_cave"));
        registerStructureOnly("iceandfire:cyclops", List.of("iceandfire:cyclops_cave"));
        registerStructureAlso("iceandfire:gorgon", List.of("iceandfire:gorgon_temple"));

        // === CATACLYSM ===
        registerStructureOnly("cataclysm:netherite_monstrosity", List.of("cataclysm:burning_arena"));
        registerStructureOnly("cataclysm:ender_golem", List.of("cataclysm:ruined_citadel"));
        registerStructureOnly("cataclysm:ignis", List.of("cataclysm:burning_arena"));
        registerStructureOnly("cataclysm:the_harbinger", List.of("cataclysm:cursed_pyramid"));
        registerStructureOnly("cataclysm:the_leviathan", List.of("cataclysm:sunken_city"));
        registerStructureOnly("cataclysm:ancient_remnant", List.of("cataclysm:soul_black_smith"));

        // === MOWZIE'S MOBS ===
        registerStructureAlso("mowziesmobs:ferrous_wroughtnaut", List.of("mowziesmobs:wroughtnaut_chamber"));
        registerStructureOnly("mowziesmobs:barako", List.of("mowziesmobs:barakoa_village"));

        // === ALEX'S MOBS ===
        registerStructureOnly("alexsmobs:void_worm", List.of("alexsmobs:void_worm_structure"));
        registerStructureAlso("alexsmobs:bone_serpent", List.of("alexsmobs:bone_serpent_nest"));

        // === MUTANT CREATURES ===
        // These are spawned via potions/items, not structures, but treating as structure-like
        // for arena purposes since they don't naturally spawn

        // === BORN IN CHAOS ===
        registerStructureOnly("born_in_chaos_v1:dark_constructor", List.of("born_in_chaos_v1:dungeon"));
        registerStructureAlso("born_in_chaos_v1:nightmare_stalker", List.of("born_in_chaos_v1:dungeon"));
    }

    private static void registerStructureOnly(String mobId, List<String> structures) {
        STRUCTURE_ONLY_MOBS.put(mobId, structures);
    }

    private static void registerStructureAlso(String mobId, List<String> structures) {
        STRUCTURE_ALSO_MOBS.put(mobId, structures);
    }

    // ============== Config Overrides ==============
    // Loaded from config/devmod/spawn_sources.json

    private static final Map<String, SpawnSourceOverride> CONFIG_OVERRIDES = new HashMap<>();

    public record SpawnSourceOverride(
        SpawnSource source,
        List<String> structures,
        float confidence
    ) {}

    /**
     * Register a config-based override.
     */
    public static void registerOverride(String mobId, SpawnSourceOverride override) {
        CONFIG_OVERRIDES.put(mobId, override);
        LOGGER.debug("[SpawnSourceDetector] Registered override for {}: {}", mobId, override.source());
    }

    /**
     * Clear all config overrides (for reload).
     */
    public static void clearOverrides() {
        CONFIG_OVERRIDES.clear();
    }

    // ============== Detection ==============

    /**
     * Detects the spawn source for a mob.
     *
     * @param entityType The entity type to analyze
     * @return Detection result with spawn source, structures, and confidence
     */
    public static DetectionResult detect(EntityType<? extends Mob> entityType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(
            java.util.Objects.requireNonNull(entityType, "entityType cannot be null"));
        String mobIdStr = mobId.toString();

        // Priority 1: Config override
        SpawnSourceOverride override = CONFIG_OVERRIDES.get(mobIdStr);
        if (override != null) {
            StructureCharacteristics primaryStructure = findPrimaryStructure(override.structures());
            return new DetectionResult(
                override.source(),
                override.structures(),
                primaryStructure,
                override.confidence(),
                DetectionMethod.CONFIG_OVERRIDE
            );
        }

        // Priority 2: Hardcoded structure-only
        if (STRUCTURE_ONLY_MOBS.containsKey(mobIdStr)) {
            List<String> structures = STRUCTURE_ONLY_MOBS.get(mobIdStr);
            StructureCharacteristics primaryStructure = findPrimaryStructure(structures);
            return new DetectionResult(
                SpawnSource.STRUCTURE_ONLY,
                structures,
                primaryStructure,
                0.95f, // High confidence for hardcoded
                DetectionMethod.HARDCODED_MAPPING
            );
        }

        // Priority 3: Check if has natural spawn placements
        boolean hasNaturalSpawn = hasNaturalSpawnPlacement(entityType);

        // Priority 4: Hardcoded structure-also
        if (STRUCTURE_ALSO_MOBS.containsKey(mobIdStr)) {
            List<String> structures = STRUCTURE_ALSO_MOBS.get(mobIdStr);
            StructureCharacteristics primaryStructure = findPrimaryStructure(structures);
            return new DetectionResult(
                SpawnSource.NATURAL_AND_STRUCTURE,
                structures,
                primaryStructure,
                0.9f,
                DetectionMethod.HARDCODED_MAPPING
            );
        }

        // Priority 5: Heuristic detection
        DetectionResult heuristic = detectByHeuristics(mobIdStr);
        if (heuristic != null) {
            return heuristic;
        }

        // Priority 6: Default based on spawn placements
        if (hasNaturalSpawn) {
            return new DetectionResult(
                SpawnSource.NATURAL_ONLY,
                List.of(),
                null,
                0.7f,
                DetectionMethod.SPAWN_PLACEMENTS
            );
        }

        // Unknown - probably structure-only or special spawn
        return new DetectionResult(
            SpawnSource.UNKNOWN,
            List.of(),
            null,
            0.3f,
            DetectionMethod.FALLBACK
        );
    }

    /**
     * Check if entity has natural spawn placements registered.
     */
    private static boolean hasNaturalSpawnPlacement(EntityType<? extends Mob> entityType) {
        try {
            SpawnPlacementType placementType = SpawnPlacements.getPlacementType(
                java.util.Objects.requireNonNull(entityType, "entityType cannot be null"));
            // If placement type is not null and not NO_RESTRICTIONS with no predicate,
            // it likely has natural spawning
            return placementType != null;
        } catch (Exception e) {
            // Some entity types may not have spawn placements registered
            return false;
        }
    }

    /**
     * Detect spawn source using naming/pattern heuristics.
     */
    @Nullable
    private static DetectionResult detectByHeuristics(String mobIdStr) {
        ResourceLocation mobId = ResourceLocation.parse(
            java.util.Objects.requireNonNull(mobIdStr, "mobIdStr cannot be null"));
        String path = mobId.getPath();
        String pathLower = path.toLowerCase(Locale.ROOT);

        // Pattern: mobs with "boss" in name are often structure-only
        if (pathLower.contains("boss") || pathLower.contains("_lord") || pathLower.contains("_king")) {
            // Check for known boss structure patterns
            String namespace = mobId.getNamespace();
            List<String> possibleStructures = guessStructuresForBoss(namespace, pathLower);

            if (!possibleStructures.isEmpty()) {
                return new DetectionResult(
                    SpawnSource.STRUCTURE_ONLY,
                    possibleStructures,
                    findPrimaryStructure(possibleStructures),
                    0.6f, // Lower confidence for heuristic
                    DetectionMethod.HEURISTIC
                );
            }
        }

        // Pattern: mobs with "guardian" are often monument-related
        if (pathLower.contains("guardian") && !mobIdStr.startsWith("minecraft:")) {
            return new DetectionResult(
                SpawnSource.NATURAL_AND_STRUCTURE,
                List.of("minecraft:ocean_monument"),
                StructureCharacteristics.get("minecraft:ocean_monument").orElse(null),
                0.5f,
                DetectionMethod.HEURISTIC
            );
        }

        // Pattern: mobs with "dungeon" or "crypt" in name
        if (pathLower.contains("dungeon") || pathLower.contains("crypt") || pathLower.contains("tomb")) {
            return new DetectionResult(
                SpawnSource.STRUCTURE_ONLY,
                List.of(), // Unknown specific structure
                null,
                0.5f,
                DetectionMethod.HEURISTIC
            );
        }

        return null;
    }

    /**
     * Guess structures for a boss mob based on namespace.
     */
    private static List<String> guessStructuresForBoss(String namespace, String mobPath) {
        List<String> structures = new ArrayList<>();

        // Common patterns per mod
        switch (namespace) {
            case "twilightforest" -> {
                if (mobPath.contains("lich")) structures.add("twilightforest:lich_tower");
                else if (mobPath.contains("hydra")) structures.add("twilightforest:hydra_lair");
                else if (mobPath.contains("naga")) structures.add("twilightforest:naga_courtyard");
            }
            case "cataclysm" -> {
                if (mobPath.contains("ignis") || mobPath.contains("monstrosity"))
                    structures.add("cataclysm:burning_arena");
                else if (mobPath.contains("harbinger"))
                    structures.add("cataclysm:cursed_pyramid");
            }
            case "iceandfire" -> {
                if (mobPath.contains("dragon")) {
                    if (mobPath.contains("fire")) structures.add("iceandfire:fire_dragon_cave");
                    else if (mobPath.contains("ice")) structures.add("iceandfire:ice_dragon_cave");
                    else if (mobPath.contains("lightning")) structures.add("iceandfire:lightning_dragon_cave");
                }
            }
            default -> {
                // Unknown mod namespace
            }
            // Add more mod-specific patterns as needed
        }

        return structures;
    }

    /**
     * Find the primary (first registered) structure characteristics.
     */
    @Nullable
    private static StructureCharacteristics findPrimaryStructure(List<String> structureIds) {
        for (String structureId : structureIds) {
            var characteristics = StructureCharacteristics.get(structureId);
            if (characteristics.isPresent()) {
                return characteristics.get();
            }
        }
        return null;
    }

    // ============== Result ==============

    public record DetectionResult(
        SpawnSource spawnSource,
        List<String> structureIds,
        @Nullable StructureCharacteristics primaryStructure,
        float confidence,
        DetectionMethod method
    ) {
        /**
         * Returns true if we're confident in this detection.
         */
        public boolean isConfident() {
            return confidence >= 0.7f;
        }

        /**
         * Get a description for logging.
         */
        public String describe() {
            return String.format("%s (%.0f%% via %s) structures=%s",
                spawnSource.name(),
                confidence * 100,
                method.name().toLowerCase(Locale.ROOT),
                structureIds);
        }
    }

    public enum DetectionMethod {
        CONFIG_OVERRIDE,
        HARDCODED_MAPPING,
        SPAWN_PLACEMENTS,
        HEURISTIC,
        FALLBACK
    }

    // ============== Bulk Operations ==============

    /**
     * Get all mobs known to be structure-only.
     */
    public static Set<String> getStructureOnlyMobs() {
        return Set.copyOf(STRUCTURE_ONLY_MOBS.keySet());
    }

    /**
     * Get structures where a mob spawns (if known).
     */
    public static List<String> getStructuresForMob(String mobId) {
        if (STRUCTURE_ONLY_MOBS.containsKey(mobId)) {
            return STRUCTURE_ONLY_MOBS.get(mobId);
        }
        if (STRUCTURE_ALSO_MOBS.containsKey(mobId)) {
            return STRUCTURE_ALSO_MOBS.get(mobId);
        }
        return List.of();
    }
}

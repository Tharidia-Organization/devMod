package com.devmod.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import com.devmod.mob.MobRequirements.BiomeRequirement;
import com.devmod.mob.MobRequirements.BossRequirement;
import com.devmod.mob.MobRequirements.FloorRequirement;
import com.devmod.mob.MobRequirements.LightRequirement;
import com.devmod.mob.MobRequirements.RequirementSource;
import com.devmod.mob.MobRequirements.SpaceRequirement;
import com.devmod.mob.MobRequirements.TimeRequirement;

/**
 * Auto-detects mob requirements from vanilla and mod APIs.
 * Queries SpawnPlacements, MobSpawnSettings, EntityType dimensions, and MobCategory
 * to build a comprehensive MobRequirements for each mob type.
 */
public class MobRequirementsDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobRequirementsDetector.class);

    /**
     * Result of boss detection including confidence score.
     * Confidence varies by detection tier:
     * - Tier 1 (vanilla): 1.0
     * - Tier 2 (tags): 0.95
     * - Tier 3 (size): 0.75
     * - Tier 4 (modded patterns): 0.9
     * - Tier 5 (naming): 0.6
     */
    private record BossDetectionResult(BossRequirement requirement, float confidence) {
        static final BossDetectionResult NOT_BOSS = new BossDetectionResult(BossRequirement.NOT_BOSS, 0.0f);

        static BossDetectionResult vanillaBoss() {
            return new BossDetectionResult(BossRequirement.simpleBoss(), 1.0f);
        }

        static BossDetectionResult taggedBoss() {
            return new BossDetectionResult(BossRequirement.simpleBoss(), 0.95f);
        }

        static BossDetectionResult largeBoss() {
            return new BossDetectionResult(BossRequirement.simpleBoss(), 0.75f);
        }

        static BossDetectionResult moddedBoss(BossRequirement req) {
            return new BossDetectionResult(req, 0.9f);
        }

        static BossDetectionResult namedBoss(BossRequirement req) {
            return new BossDetectionResult(req, 0.6f);
        }
    }

    /**
     * Detects requirements for a mob using available vanilla APIs.
     * Does not require a server - uses static registries where possible.
     *
     * @param mobId The mob's resource location
     * @return Detected requirements with confidence score
     */
    public MobRequirements detect(ResourceLocation mobId) {
        Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobId);
        if (entityTypeOpt.isEmpty()) {
            LOGGER.warn("Cannot detect requirements for unknown mob: {}", mobId);
            return MobRequirements.defaultFor(mobId);
        }

        EntityType<?> entityType = entityTypeOpt.get();
        float confidence = 0.0f;

        // Detect space requirements from entity dimensions
        SpaceRequirement space = detectSpaceRequirements(entityType);
        if (!space.isDefault()) confidence += 0.2f;

        // Detect light requirements from mob category
        LightRequirement light = detectLightRequirements(entityType);
        if (!light.isDefault()) confidence += 0.2f;

        // Detect floor requirements from spawn placement type
        FloorRequirement floor = detectFloorRequirements(entityType);
        if (!floor.isDefault()) confidence += 0.2f;

        // Detect time requirements from mob category
        TimeRequirement time = detectTimeRequirements(entityType);
        if (time != TimeRequirement.ANY) confidence += 0.1f;

        // Detect boss requirements with tier-based confidence
        BossDetectionResult bossResult = detectBossWithConfidence(entityType, mobId);
        BossRequirement boss = bossResult.requirement();
        confidence += bossResult.confidence() * 0.15f; // Boss detection contributes up to 0.15 to overall confidence

        // Biome requirements need server access for full detection
        // For now, infer from category
        BiomeRequirement biome = detectBiomeRequirementsFromCategory(entityType);
        if (!biome.isDefault()) confidence += 0.2f;

        return new MobRequirements(
            mobId,
            biome,
            light,
            floor,
            space,
            time,
            boss,
            RequirementSource.AUTO_DETECTED,
            confidence
        );
    }

    /**
     * Detects requirements with access to server biome registry.
     * Provides more accurate biome detection by scanning all biomes.
     */
    public MobRequirements detectWithServer(ResourceLocation mobId, MinecraftServer server) {
        MobRequirements basic = detect(mobId);

        // Enhanced biome detection with server access
        BiomeRequirement enhancedBiome = detectBiomeRequirementsFromRegistry(mobId, server);
        if (!enhancedBiome.isDefault()) {
            return new MobRequirements(
                mobId,
                enhancedBiome,
                basic.light(),
                basic.floor(),
                basic.space(),
                basic.time(),
                basic.boss(),
                RequirementSource.AUTO_DETECTED,
                Math.min(1.0f, basic.confidence() + 0.2f)
            );
        }

        return basic;
    }

    private SpaceRequirement detectSpaceRequirements(EntityType<?> entityType) {
        var dimensions = entityType.getDimensions();
        float width = dimensions.width();
        float height = dimensions.height();

        // Check for known large mobs
        if (width > 2.0f || height > 3.0f) {
            return new SpaceRequirement(width, height, height + 2.0f, width + 2.0f);
        } else if (width < 0.6f && height < 1.0f) {
            return SpaceRequirement.SMALL;
        } else {
            return SpaceRequirement.fromDimensions(width, height);
        }
    }

    private LightRequirement detectLightRequirements(EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();

        return switch (category) {
            case MONSTER -> LightRequirement.DARK; // Monsters prefer dark
            case CREATURE -> LightRequirement.BRIGHT; // Passive mobs prefer light
            case AMBIENT -> LightRequirement.ANY;
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> LightRequirement.ANY;
            case AXOLOTLS -> LightRequirement.range(0, 11); // Axolotls spawn in dark water
            case MISC -> LightRequirement.ANY;
        };
    }

    private FloorRequirement detectFloorRequirements(EntityType<?> entityType) {
        // Try to get spawn placement info
        try {
            SpawnPlacementType placementType = SpawnPlacements.getPlacementType(Objects.requireNonNull(entityType));
            if (placementType != null) {
                String placementName = placementType.toString().toUpperCase(java.util.Locale.ROOT);
                if (placementName.contains("WATER")) {
                    return FloorRequirement.WATER;
                } else if (placementName.contains("LAVA")) {
                    return FloorRequirement.LAVA;
                } else if (placementName.contains("NO_RESTRICTIONS")) {
                    return FloorRequirement.FLYING;
                }
            }
        } catch (Exception e) {
            // SpawnPlacements may not have entry for all mobs
        }

        // Infer from category
        MobCategory category = entityType.getCategory();
        return switch (category) {
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> FloorRequirement.WATER;
            default -> FloorRequirement.SOLID_GROUND;
        };
    }

    private TimeRequirement detectTimeRequirements(EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();

        // Monsters typically spawn at night
        if (category == MobCategory.MONSTER) {
            return TimeRequirement.NIGHT;
        }

        return TimeRequirement.ANY;
    }

    /**
     * Multi-tier boss detection system with confidence scoring.
     * Tiers (in order of priority):
     * 1. Known vanilla bosses - definitive (confidence: 1.0)
     * 2. Entity type tags (c:bosses convention) (confidence: 0.95)
     * 3. Size-based detection (large entities) (confidence: 0.75)
     * 4. Known modded boss patterns (confidence: 0.9)
     * 5. Enhanced naming heuristics (confidence: 0.6)
     */
    private BossDetectionResult detectBossWithConfidence(EntityType<?> entityType, ResourceLocation mobId) {
        // Tier 1: Known vanilla bosses (definitive - have boss bars)
        if (isVanillaBoss(mobId)) {
            LOGGER.debug("Detected vanilla boss: {} (confidence: 1.0)", mobId);
            return BossDetectionResult.vanillaBoss();
        }

        // Tier 2: Check entity type tags (c:bosses convention from NeoForge/common tags)
        if (hasBossTag(entityType)) {
            LOGGER.debug("Detected boss via tag: {} (confidence: 0.95)", mobId);
            return BossDetectionResult.taggedBoss();
        }

        // Tier 3: Size-based detection - very large entities are often bosses
        if (isLargeEntity(entityType)) {
            LOGGER.debug("Detected boss via size: {} (confidence: 0.75)", mobId);
            return BossDetectionResult.largeBoss();
        }

        // Tier 4: Check for known modded bosses by namespace + path
        BossRequirement moddedBoss = detectModdedBoss(mobId);
        if (moddedBoss.isBoss()) {
            LOGGER.debug("Detected modded boss: {} (confidence: 0.9)", mobId);
            return BossDetectionResult.moddedBoss(moddedBoss);
        }

        // Tier 5: Enhanced naming heuristics
        BossRequirement namedBoss = detectBossFromNaming(mobId);
        if (namedBoss.isBoss()) {
            LOGGER.debug("Detected boss via naming: {} (confidence: 0.6)", mobId);
            return BossDetectionResult.namedBoss(namedBoss);
        }

        return BossDetectionResult.NOT_BOSS;
    }

    /**
     * Checks if the mob is a known vanilla boss.
     */
    private boolean isVanillaBoss(ResourceLocation mobId) {
        if (!"minecraft".equals(mobId.getNamespace())) {
            return false;
        }
        String path = mobId.getPath();
        return path.equals("ender_dragon") ||
               path.equals("wither") ||
               path.equals("warden") ||
               path.equals("elder_guardian");
    }

    /**
     * Checks if the entity type has the c:bosses tag (NeoForge convention).
     */
    private boolean hasBossTag(EntityType<?> entityType) {
        try {
            // Common tags convention: c:bosses
            TagKey<EntityType<?>> bossTag = TagKey.create(
                Objects.requireNonNull(Registries.ENTITY_TYPE),
                Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("c", "bosses"))
            );
            if (entityType.is(Objects.requireNonNull(bossTag))) {
                return true;
            }

            // Also check forge:bosses for older mods
            TagKey<EntityType<?>> forgeBossTag = TagKey.create(
                Objects.requireNonNull(Registries.ENTITY_TYPE),
                Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("forge", "bosses"))
            );
            return entityType.is(Objects.requireNonNull(forgeBossTag));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Tag system not yet initialized or invalid tag key - that's fine
            LOGGER.trace("Boss tag check failed for entity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the entity is large enough to likely be a boss.
     * Thresholds: height > 4.0 OR width > 3.0
     */
    private boolean isLargeEntity(EntityType<?> entityType) {
        var dimensions = entityType.getDimensions();
        // Very large entities (like dragons, giants) are almost always bosses
        return dimensions.height() > 4.0f || dimensions.width() > 3.0f;
    }

    /**
     * Detects known bosses from popular mods by namespace and path.
     */
    private BossRequirement detectModdedBoss(ResourceLocation mobId) {
        String namespace = mobId.getNamespace();
        String path = mobId.getPath().toLowerCase(java.util.Locale.ROOT);

        // Mowzie's Mobs
        if ("mowziesmobs".equals(namespace)) {
            if (path.equals("ferrous_wroughtnaut") || path.equals("frostmaw") ||
                path.equals("barako") || path.equals("naga")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Twilight Forest
        if ("twilightforest".equals(namespace)) {
            if (path.equals("naga") || path.equals("lich") || path.equals("hydra") ||
                path.equals("ur_ghast") || path.equals("snow_queen") ||
                path.equals("minoshroom") || path.equals("alpha_yeti") ||
                path.equals("knight_phantom") || path.equals("plateau_boss")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Alex's Mobs
        if ("alexsmobs".equals(namespace)) {
            if (path.equals("void_worm") || path.equals("warped_mosco")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Ice and Fire
        if ("iceandfire".equals(namespace)) {
            if (path.contains("dragon") || path.equals("gorgon") ||
                path.equals("cyclops") || path.equals("siren") ||
                path.equals("sea_serpent") || path.equals("death_worm")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Cataclysm - ALL mobs in this mod are boss-tier
        if ("cataclysm".equals(namespace)) {
            return BossRequirement.simpleBoss();
        }

        // Blue Skies
        if ("blue_skies".equals(namespace)) {
            if (path.contains("summoner") || path.contains("alchemist") ||
                path.equals("starlit_crusher") || path.equals("arachnarch")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Ars Nouveau
        if ("ars_nouveau".equals(namespace)) {
            if (path.equals("wilden_boss") || path.equals("wilden_chimera")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Born in Chaos
        if ("born_in_chaos_v1".equals(namespace) || "bornchaos".equals(namespace)) {
            if (path.contains("dire") || path.contains("supreme") ||
                path.contains("lord") || path.contains("king")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Mutant Creatures / Mutant More
        if (namespace.contains("mutant")) {
            if (path.contains("mutant")) {
                return BossRequirement.miniBoss(); // Mutants are mini-boss tier
            }
        }

        // L_Ender's Cataclysm (different from Cataclysm)
        if ("l_ender_cataclysm".equals(namespace)) {
            return BossRequirement.simpleBoss();
        }

        // Aquaculture / Aquamirae bosses
        if ("aquamirae".equals(namespace)) {
            if (path.equals("captain_cornelia") || path.equals("maze_mother")) {
                return BossRequirement.simpleBoss();
            }
        }

        // The Abyss
        if ("theabyss".equals(namespace)) {
            if (path.contains("boss") || path.contains("evolved") ||
                path.contains("mutated") || path.contains("titan")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Botania
        if ("botania".equals(namespace)) {
            if (path.equals("doppleganger") || path.contains("gaia")) {
                return BossRequirement.withMinPlayers(1); // Gaia Guardian
            }
        }

        // Atum 2
        if ("atum".equals(namespace)) {
            if (path.equals("pharaoh") || path.contains("boss") ||
                path.equals("sunspeaker") || path.equals("mummy_lord")) {
                return BossRequirement.simpleBoss();
            }
        }

        // The Undergarden
        if ("undergarden".equals(namespace)) {
            if (path.equals("forgotten_guardian") || path.equals("rotbeast") ||
                path.contains("boss")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Quark (Stoneling King is a mini-boss)
        if ("quark".equals(namespace)) {
            if (path.equals("stoneling") && path.contains("king")) {
                return BossRequirement.miniBoss();
            }
        }

        // Deeper and Darker
        if ("deeperdarker".equals(namespace) || "deeper_darker".equals(namespace)) {
            if (path.contains("warden") || path.contains("sculk") ||
                path.equals("stalker") || path.contains("boss")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Mekanism (Robit is not a boss, but some addons have bosses)
        // Leaving as NOT_BOSS for base mod

        // Create: Various addon bosses
        if (namespace.startsWith("create") && path.contains("boss")) {
            return BossRequirement.simpleBoss();
        }

        // Savage & Ravage (Griefer, Executioner are mini-bosses)
        if ("savageandravage".equals(namespace)) {
            if (path.equals("griefer") || path.equals("executioner")) {
                return BossRequirement.miniBoss();
            }
        }

        // Supplementaries (no real bosses)
        // Farmer's Delight (no bosses)
        // Architect's Palette (no bosses)

        // Aether / Aether Redux
        if ("aether".equals(namespace) || "aether_redux".equals(namespace)) {
            if (path.equals("slider") || path.equals("valkyrie_queen") ||
                path.equals("sun_spirit") || path.contains("boss")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Better End
        if ("betterend".equals(namespace)) {
            if (path.contains("dragon") || path.contains("boss") ||
                path.equals("shadow_walker")) {
                return BossRequirement.simpleBoss();
            }
        }

        // Better Nether (mostly ambient mobs, few bosses)
        if ("betternether".equals(namespace)) {
            if (path.contains("boss") || path.contains("lord")) {
                return BossRequirement.simpleBoss();
            }
        }

        return BossRequirement.NOT_BOSS;
    }

    /**
     * Detects bosses from naming patterns.
     */
    private BossRequirement detectBossFromNaming(ResourceLocation mobId) {
        String path = mobId.getPath().toLowerCase(java.util.Locale.ROOT);

        // Vanilla mini-bosses and raid bosses (powerful but no boss bar)
        if ("minecraft".equals(mobId.getNamespace())) {
            if (path.equals("ravager") || path.equals("evoker") ||
                path.equals("piglin_brute") || path.equals("illusioner")) {
                return BossRequirement.miniBoss();
            }
        }

        // Definitive boss naming patterns
        if (path.contains("_boss") || path.startsWith("boss_") || path.equals("boss")) {
            return BossRequirement.simpleBoss();
        }

        // Royalty/leadership patterns (often indicate bosses)
        if (path.contains("_lord") || path.startsWith("lord_") ||
            path.contains("_king") || path.startsWith("king_") ||
            path.contains("_queen") || path.startsWith("queen_") ||
            path.contains("_emperor") || path.startsWith("emperor_") ||
            path.contains("overlord") || path.contains("_matriarch") ||
            path.contains("_patriarch")) {
            return BossRequirement.simpleBoss();
        }

        // Power/rank patterns
        if (path.contains("_titan") || path.startsWith("titan_") ||
            path.contains("_colossus") || path.contains("_ancient") ||
            path.contains("_primordial") || path.contains("_supreme") ||
            path.contains("archmage") || path.contains("archdruid") ||
            path.contains("_alpha") || path.startsWith("alpha_")) {
            return BossRequirement.simpleBoss();
        }

        // Elite/champion patterns (often mini-bosses)
        if (path.contains("_elite") || path.startsWith("elite_") ||
            path.contains("champion") || path.contains("_master") ||
            path.startsWith("master_") || path.contains("_captain")) {
            return BossRequirement.miniBoss();
        }

        // Giant/huge variants
        if (path.startsWith("giant_") || path.contains("_giant") ||
            path.startsWith("mega_") || path.contains("_mega") ||
            path.startsWith("dire_") || path.contains("_dire")) {
            return BossRequirement.miniBoss();
        }

        return BossRequirement.NOT_BOSS;
    }

    private BiomeRequirement detectBiomeRequirementsFromCategory(EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();

        return switch (category) {
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS ->
                BiomeRequirement.fromTag(BiomeTags.IS_OCEAN);
            default -> BiomeRequirement.ANY;
        };
    }

    private BiomeRequirement detectBiomeRequirementsFromRegistry(ResourceLocation mobId, MinecraftServer server) {
        Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobId);
        if (entityTypeOpt.isEmpty()) {
            return BiomeRequirement.ANY;
        }
        EntityType<?> entityType = entityTypeOpt.get();

        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Objects.requireNonNull(Registries.BIOME));
        List<ResourceLocation> validBiomes = new ArrayList<>();

        // Scan all biomes for this mob's spawn entries
        for (var entry : biomeRegistry.entrySet()) {
            Biome biome = entry.getValue();
            MobSpawnSettings spawnSettings = biome.getMobSettings();

            for (MobCategory category : MobCategory.values()) {
                var spawners = spawnSettings.getMobs(Objects.requireNonNull(category));
                for (var spawner : spawners.unwrap()) {
                    if (spawner.type == entityType) {
                        validBiomes.add(entry.getKey().location());
                        break;
                    }
                }
            }
        }

        if (validBiomes.isEmpty()) {
            return BiomeRequirement.ANY;
        }

        // Determine preferred biome (first one found, or most specific)
        ResourceLocation preferred = selectPreferredBiome(validBiomes);

        return new BiomeRequirement(
            Optional.of(preferred),
            Optional.empty(),
            validBiomes,
            false
        );
    }

    private ResourceLocation selectPreferredBiome(List<ResourceLocation> biomes) {
        // Preference order: specific biomes > generic biomes
        Map<String, Integer> biomePreference = Map.of(
            "deep_dark", 10,
            "nether_wastes", 9,
            "soul_sand_valley", 9,
            "basalt_deltas", 9,
            "the_end", 8,
            "end_highlands", 8,
            "mushroom_fields", 7,
            "ocean", 1,
            "plains", 1
        );

        return biomes.stream()
            .max(Comparator.comparingInt(b -> biomePreference.getOrDefault(b.getPath(), 5)))
            .orElse(biomes.get(0));
    }

    /**
     * Detects requirements for a specific entity type without resource location lookup.
     */
    public MobRequirements detectForType(EntityType<?> entityType) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType));
        return detect(mobId);
    }
}

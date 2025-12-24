package com.devmod.endurance;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry that scans all available mob types from vanilla and mods,
 * automatically generating quest configurations for each.
 */

public class EnduranceQuestRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestRegistry.class);

    public static final EnduranceQuestRegistry INSTANCE = new EnduranceQuestRegistry();

    // All discovered hostile/neutral mobs eligible for quests
    private final Map<ResourceLocation, MobQuestConfig> mobConfigs = new LinkedHashMap<>();

    // Categorized mobs for filtering
    private final Map<String, List<ResourceLocation>> mobsByNamespace = new HashMap<>();
    private final Map<MobCategory, List<ResourceLocation>> mobsByCategory = new HashMap<>();
    private final Map<MobTier, List<ResourceLocation>> mobsByTier = new HashMap<>();

    // Special boss mobs (detected by various heuristics)
    private final Set<ResourceLocation> bossMobs = new HashSet<>();

    // Cache for actual mob attributes (populated on first world load)
    private final Map<ResourceLocation, ActualMobStats> actualStatsCache = new HashMap<>();
    private boolean attributesRefined = false;

    private boolean initialized = false;

    /**
     * Actual stats read from spawning a test entity.
     */
    public record ActualMobStats(double health, double damage, double speed, double armor, double knockbackResist) {
        public static ActualMobStats UNKNOWN = new ActualMobStats(-1, -1, -1, -1, -1);

        public boolean isValid() {
            return health > 0;
        }
    }

    /**
     * Mob difficulty tiers for wave scaling.
     */
    public enum MobTier {
        TRIVIAL(1, 0.5f),      // Silverfish, baby zombies
        EASY(2, 1.0f),         // Zombies, skeletons
        MEDIUM(3, 1.5f),       // Creepers, spiders
        HARD(4, 2.0f),         // Endermen, witches
        ELITE(5, 3.0f),        // Ravagers, piglin brutes
        BOSS(10, 5.0f);        // Wither, Ender Dragon, modded bosses

        public final int basePoints;
        public final float difficultyMultiplier;

        MobTier(int basePoints, float difficultyMultiplier) {
            this.basePoints = basePoints;
            this.difficultyMultiplier = difficultyMultiplier;
        }
    }

    /**
     * Difficulty presets that define how mob stats scale with players.
     * Different mob types benefit from different scaling approaches.
     */
    public enum MobDifficultyPreset {
        /** Many weak mobs - high count, low HP (silverfish, zombies, slimes) */
        SWARM(1.5f, 0.7f, 0.8f, "Swarm", "Many weak enemies"),
        /** Standard balanced scaling */
        STANDARD(1.0f, 1.0f, 1.0f, "Standard", "Balanced difficulty"),
        /** Few tanky mobs - low count, high HP (iron golems, wardens) */
        TANK(0.5f, 2.0f, 0.7f, "Tank", "Few tough enemies"),
        /** Fragile but deadly - medium count, low HP, high damage (creepers, phantoms) */
        GLASS_CANNON(0.8f, 0.5f, 1.5f, "Glass Cannon", "Fragile but deadly"),
        /** Single powerful enemy - very low count, very high HP (bosses) */
        BOSS_STYLE(0.3f, 3.0f, 1.2f, "Boss Style", "Single powerful foe");

        /** Multiplier for mob count scaling */
        public final float countMultiplier;
        /** Multiplier for mob HP scaling */
        public final float hpMultiplier;
        /** Multiplier for mob damage scaling */
        public final float damageMultiplier;
        /** Display name for UI */
        public final String displayName;
        /** Description for tooltips */
        public final String description;

        MobDifficultyPreset(float countMultiplier, float hpMultiplier, float damageMultiplier,
                           String displayName, String description) {
            this.countMultiplier = countMultiplier;
            this.hpMultiplier = hpMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * Configuration for a mob's quest parameters.
     */
    public static class MobQuestConfig {
        public final ResourceLocation mobId;
        public final EntityType<?> entityType;
        public final MobTier tier;
        public final MobDifficultyPreset difficultyPreset;
        public final String displayName;
        public final String namespace;

        // Wave configuration
        public final int baseCountPerWave;
        public final float countScalingPerWave;
        public final int maxPerWave;

        // Spawn configuration
        public final boolean canSpawnInGroups;
        public final int groupSize;
        public final float eliteChance; // Chance to spawn with buffs

        // Rewards
        public final int pointsPerKill;
        public final int bonusPointsForWaveClear;

        // Base stats (estimated from entity type defaults)
        public final float baseHealth;
        public final float baseDamage;
        public final float baseSpeed;

        public MobQuestConfig(ResourceLocation mobId, EntityType<?> entityType, MobTier tier) {
            this(mobId, entityType, tier, determineDifficultyPreset(mobId, tier));
        }

        public MobQuestConfig(ResourceLocation mobId, EntityType<?> entityType, MobTier tier, MobDifficultyPreset preset) {
            this.mobId = mobId;
            this.entityType = entityType;
            this.tier = tier;
            this.difficultyPreset = preset;
            this.namespace = mobId.getNamespace();

            // Generate display name from ID
            String path = mobId.getPath();
            this.displayName = Arrays.stream(path.split("_"))
                .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));

            // Estimate base stats from mob type
            this.baseHealth = estimateBaseHealth(mobId, tier);
            this.baseDamage = estimateBaseDamage(mobId, tier);
            this.baseSpeed = estimateBaseSpeed(mobId, tier);

            // Configure based on tier
            switch (tier) {
                case TRIVIAL -> {
                    this.baseCountPerWave = 8;
                    this.countScalingPerWave = 2.0f;
                    this.maxPerWave = 30;
                    this.canSpawnInGroups = true;
                    this.groupSize = 4;
                    this.eliteChance = 0.05f;
                    this.pointsPerKill = 5;
                    this.bonusPointsForWaveClear = 25;
                }
                case EASY -> {
                    this.baseCountPerWave = 5;
                    this.countScalingPerWave = 1.5f;
                    this.maxPerWave = 20;
                    this.canSpawnInGroups = true;
                    this.groupSize = 3;
                    this.eliteChance = 0.1f;
                    this.pointsPerKill = 10;
                    this.bonusPointsForWaveClear = 50;
                }
                case MEDIUM -> {
                    this.baseCountPerWave = 4;
                    this.countScalingPerWave = 1.2f;
                    this.maxPerWave = 15;
                    this.canSpawnInGroups = true;
                    this.groupSize = 2;
                    this.eliteChance = 0.15f;
                    this.pointsPerKill = 20;
                    this.bonusPointsForWaveClear = 100;
                }
                case HARD -> {
                    this.baseCountPerWave = 3;
                    this.countScalingPerWave = 1.0f;
                    this.maxPerWave = 10;
                    this.canSpawnInGroups = false;
                    this.groupSize = 1;
                    this.eliteChance = 0.2f;
                    this.pointsPerKill = 35;
                    this.bonusPointsForWaveClear = 175;
                }
                case ELITE -> {
                    this.baseCountPerWave = 2;
                    this.countScalingPerWave = 0.5f;
                    this.maxPerWave = 6;
                    this.canSpawnInGroups = false;
                    this.groupSize = 1;
                    this.eliteChance = 0.3f;
                    this.pointsPerKill = 50;
                    this.bonusPointsForWaveClear = 250;
                }
                case BOSS -> {
                    this.baseCountPerWave = 1;
                    this.countScalingPerWave = 0.2f;
                    this.maxPerWave = 3;
                    this.canSpawnInGroups = false;
                    this.groupSize = 1;
                    this.eliteChance = 0.5f;
                    this.pointsPerKill = 200;
                    this.bonusPointsForWaveClear = 1000;
                }
                default -> {
                    this.baseCountPerWave = 5;
                    this.countScalingPerWave = 1.0f;
                    this.maxPerWave = 15;
                    this.canSpawnInGroups = true;
                    this.groupSize = 2;
                    this.eliteChance = 0.1f;
                    this.pointsPerKill = 15;
                    this.bonusPointsForWaveClear = 75;
                }
            }
        }

        /**
         * Determine difficulty preset based on mob characteristics.
         */
        private static MobDifficultyPreset determineDifficultyPreset(ResourceLocation mobId, MobTier tier) {
            String path = mobId.getPath().toLowerCase();

            // Boss-style mobs
            if (tier == MobTier.BOSS || path.contains("warden") || path.contains("elder_guardian")) {
                return MobDifficultyPreset.BOSS_STYLE;
            }

            // Tank mobs
            if (path.contains("golem") || path.contains("ravager") || path.contains("hoglin") ||
                path.contains("zoglin") || path.contains("piglin_brute")) {
                return MobDifficultyPreset.TANK;
            }

            // Glass cannon mobs
            if (path.contains("creeper") || path.contains("phantom") || path.contains("vex") ||
                path.contains("blaze") || path.contains("ghast")) {
                return MobDifficultyPreset.GLASS_CANNON;
            }

            // Swarm mobs
            if (path.contains("silverfish") || path.contains("slime") || path.contains("magma_cube") ||
                path.contains("zombie") || path.contains("husk") || path.contains("drowned") ||
                tier == MobTier.TRIVIAL) {
                return MobDifficultyPreset.SWARM;
            }

            // Default
            return MobDifficultyPreset.STANDARD;
        }

        /**
         * Estimate base health from mob type.
         */
        private static float estimateBaseHealth(ResourceLocation mobId, MobTier tier) {
            String path = mobId.getPath().toLowerCase();

            // Known mob base health values
            if (path.equals("wither")) return 300f;
            if (path.equals("ender_dragon")) return 200f;
            if (path.equals("warden")) return 500f;
            if (path.equals("elder_guardian")) return 80f;
            if (path.contains("golem")) return 100f;
            if (path.contains("ravager")) return 100f;
            if (path.contains("ghast")) return 10f;
            if (path.contains("enderman")) return 40f;
            if (path.contains("creeper")) return 20f;
            if (path.contains("zombie") || path.contains("skeleton")) return 20f;
            if (path.contains("spider")) return 16f;
            if (path.contains("silverfish")) return 8f;
            if (path.contains("slime")) return 16f;

            // Default by tier
            return switch (tier) {
                case TRIVIAL -> 8f;
                case EASY -> 20f;
                case MEDIUM -> 24f;
                case HARD -> 40f;
                case ELITE -> 80f;
                case BOSS -> 200f;
            };
        }

        /**
         * Estimate base damage from mob type.
         */
        private static float estimateBaseDamage(ResourceLocation mobId, MobTier tier) {
            String path = mobId.getPath().toLowerCase();

            // Known mob base damage values
            if (path.equals("warden")) return 30f;
            if (path.contains("ravager")) return 12f;
            if (path.contains("golem")) return 15f;
            if (path.contains("creeper")) return 43f; // Explosion
            if (path.contains("enderman")) return 7f;
            if (path.contains("zombie") || path.contains("skeleton")) return 3f;
            if (path.contains("spider")) return 2f;

            // Default by tier
            return switch (tier) {
                case TRIVIAL -> 1f;
                case EASY -> 3f;
                case MEDIUM -> 4f;
                case HARD -> 6f;
                case ELITE -> 10f;
                case BOSS -> 15f;
            };
        }

        /**
         * Estimate base speed from mob type.
         */
        private static float estimateBaseSpeed(ResourceLocation mobId, MobTier tier) {
            String path = mobId.getPath().toLowerCase();

            if (path.contains("spider")) return 0.3f;
            if (path.contains("enderman")) return 0.3f;
            if (path.contains("wolf")) return 0.3f;
            if (path.contains("phantom")) return 0.35f;
            if (path.contains("golem")) return 0.15f;

            // Default
            return 0.23f;
        }

        /**
         * Calculate mob count for a specific wave (single player).
         */
        public int getMobCountForWave(int waveNumber) {
            return getMobCountForWave(waveNumber, 1, QuestType.PVE_COOP);
        }

        /**
         * Calculate mob count for a specific wave with player scaling.
         * Applies difficulty preset count multiplier.
         *
         * @param waveNumber Current wave number (1-based)
         * @param playerCount Number of players in the party
         * @param questType Quest type for difficulty multiplier
         * @return Scaled mob count
         */
        public int getMobCountForWave(int waveNumber, int playerCount, QuestType questType) {
            int baseCount = (int)(baseCountPerWave + (waveNumber - 1) * countScalingPerWave);
            int capped = Math.min(baseCount, maxPerWave);
            // Apply difficulty preset multiplier
            int presetAdjusted = (int) Math.ceil(capped * difficultyPreset.countMultiplier);
            return DifficultyScaler.INSTANCE.scaleMobCount(presetAdjusted, playerCount, questType);
        }

        /**
         * Get scaled health for this mob type.
         * @param playerCount Number of players
         * @param questType Quest type
         * @return Scaled health value
         */
        public float getScaledHealth(int playerCount, QuestType questType) {
            float scaled = DifficultyScaler.INSTANCE.scaleMobHealth(baseHealth, playerCount, questType);
            return scaled * difficultyPreset.hpMultiplier;
        }

        /**
         * Get scaled damage for this mob type.
         * @param playerCount Number of players
         * @return Scaled damage value
         */
        public float getScaledDamage(int playerCount) {
            // Damage scales more gently
            float playerScale = 1.0f + (playerCount - 1) * 0.05f;
            return baseDamage * playerScale * difficultyPreset.damageMultiplier;
        }

        /**
         * Get a summary string for UI display.
         */
        public String getStatsSummary() {
            return String.format("HP: %.0f | DMG: %.0f | %s", baseHealth, baseDamage, difficultyPreset.displayName);
        }
    }

    private EnduranceQuestRegistry() {}

    /**
     * Initialize the registry by scanning all registered entity types.
     * Should be called after mod loading is complete.
     */
    public void initialize() {
        if (initialized) return;

        LOGGER.info("[EnduranceQuest] Scanning entity registry for quest-eligible mobs...");

        int totalMobs = 0;
        int questEligible = 0;

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            totalMobs++;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType));

            if (isQuestEligible(entityType)) {
                MobTier tier = determineTier(entityType, id);
                MobQuestConfig config = new MobQuestConfig(id, entityType, tier);

                mobConfigs.put(id, config);

                // Index by namespace
                mobsByNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);

                // Index by category
                MobCategory category = entityType.getCategory();
                mobsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(id);

                // Index by tier
                mobsByTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(id);

                // Track bosses
                if (tier == MobTier.BOSS) {
                    bossMobs.add(id);
                }

                questEligible++;
                LOGGER.debug("[EnduranceQuest] Registered: {} (Tier: {}, Namespace: {})",
                    id, tier, id.getNamespace());
            }
        }

        initialized = true;
        LOGGER.info("[EnduranceQuest] Scan complete! Found {} quest-eligible mobs out of {} total entities.",
            questEligible, totalMobs);
        LOGGER.info("[EnduranceQuest] Mobs by namespace: {}",
            mobsByNamespace.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        LOGGER.info("[EnduranceQuest] Mobs by tier: {}",
            mobsByTier.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().size())));
    }

    /**
     * Refine mob tiers using actual entity attributes.
     * Should be called when a ServerLevel becomes available (e.g., on world load).
     * This spawns test entities temporarily to read their real stats.
     *
     * @param level ServerLevel to spawn test entities in
     */
    public void refineWithAttributes(ServerLevel level) {
        if (attributesRefined || level == null) {
            return;
        }

        ensureInitialized();
        LOGGER.info("[EnduranceQuest] Refining mob tiers with actual attributes...");

        int refined = 0;
        int errors = 0;
        Map<MobTier, Integer> tierChanges = new EnumMap<>(MobTier.class);

        for (Map.Entry<ResourceLocation, MobQuestConfig> entry : mobConfigs.entrySet()) {
            ResourceLocation id = entry.getKey();
            MobQuestConfig config = entry.getValue();

            try {
                ActualMobStats stats = readActualStats(config.entityType, level);
                if (stats.isValid()) {
                    actualStatsCache.put(id, stats);

                    // Determine tier based on actual attributes
                    MobTier newTier = determineTierFromAttributes(stats, id);
                    MobTier oldTier = config.tier;

                    if (newTier != oldTier) {
                        // Update the config with new tier
                        updateMobTier(id, config, newTier);
                        tierChanges.merge(newTier, 1, (a, b) -> a + b);
                        refined++;
                        LOGGER.debug("[EnduranceQuest] Refined {} from {} to {} (HP:{}, DMG:{})",
                            id, oldTier, newTier, stats.health(), stats.damage());
                    }
                }
            } catch (Exception e) {
                errors++;
                LOGGER.debug("[EnduranceQuest] Could not read attributes for {}: {}", id, e.getMessage());
            }
        }

        attributesRefined = true;
        LOGGER.info("[EnduranceQuest] Attribute refinement complete. {} mobs refined, {} errors.", refined, errors);
        if (!tierChanges.isEmpty()) {
            LOGGER.info("[EnduranceQuest] Tier changes: {}", tierChanges);
        }
        LOGGER.info("[EnduranceQuest] Final tier distribution: {}",
            mobsByTier.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().size())));
    }

    /**
     * Read actual stats from a test entity spawn.
     */
    private ActualMobStats readActualStats(EntityType<?> entityType, ServerLevel level) {
        Entity testEntity = null;
        try {
            testEntity = entityType.create(Objects.requireNonNull(level));
            if (testEntity instanceof Mob mob) {
                double health = mob.getAttributeValue(Objects.requireNonNull(Attributes.MAX_HEALTH));
                double damage = getAttributeSafe(mob, Objects.requireNonNull(Attributes.ATTACK_DAMAGE), 2.0);
                double speed = getAttributeSafe(mob, Attributes.MOVEMENT_SPEED, 0.25);
                double armor = getAttributeSafe(mob, Attributes.ARMOR, 0.0);
                double knockback = getAttributeSafe(mob, Attributes.KNOCKBACK_RESISTANCE, 0.0);

                return new ActualMobStats(health, damage, speed, armor, knockback);
            }
        } finally {
            if (testEntity != null) {
                testEntity.discard();
            }
        }
        return ActualMobStats.UNKNOWN;
    }

    /**
     * Safely get an attribute value with fallback.
     */
    private double getAttributeSafe(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double fallback) {
        try {
            var instance = mob.getAttribute(Objects.requireNonNull(attribute));
            return instance != null ? instance.getValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Determine tier based on actual mob attributes.
     * Uses health and damage as primary indicators.
     */
    private MobTier determineTierFromAttributes(ActualMobStats stats, ResourceLocation id) {
        double health = stats.health();
        double damage = stats.damage();
        String path = id.getPath().toLowerCase();

        // First check for boss indicators in name (these override stats)
        if (path.contains("boss") || path.contains("final") || path.contains("lord") ||
            path.contains("king") || path.contains("queen") || path.contains("emperor")) {
            return MobTier.BOSS;
        }

        // Attribute-based thresholds
        // BOSS: HP > 200 OR (HP > 100 AND damage > 20)
        if (health > 200 || (health > 100 && damage > 20)) {
            return MobTier.BOSS;
        }

        // ELITE: HP 100-200 OR (HP > 60 AND damage > 12)
        if (health >= 100 || (health > 60 && damage > 12)) {
            return MobTier.ELITE;
        }

        // HARD: HP 50-100 OR (HP > 30 AND damage > 8)
        if (health >= 50 || (health > 30 && damage > 8)) {
            return MobTier.HARD;
        }

        // MEDIUM: HP 25-50 OR (HP > 15 AND damage > 4)
        if (health >= 25 || (health > 15 && damage > 4)) {
            return MobTier.MEDIUM;
        }

        // TRIVIAL: HP < 15 AND damage < 3
        if (health < 15 && damage < 3) {
            return MobTier.TRIVIAL;
        }

        // EASY: everything else
        return MobTier.EASY;
    }

    /**
     * Update a mob's tier and re-index it.
     */
    private void updateMobTier(ResourceLocation id, MobQuestConfig oldConfig, MobTier newTier) {
        // Remove from old tier index
        MobTier oldTier = oldConfig.tier;
        List<ResourceLocation> oldTierList = mobsByTier.get(oldTier);
        if (oldTierList != null) {
            oldTierList.remove(id);
        }

        // Create new config with updated tier
        MobQuestConfig newConfig = new MobQuestConfig(id, oldConfig.entityType, newTier, oldConfig.difficultyPreset);
        mobConfigs.put(id, newConfig);

        // Add to new tier index
        mobsByTier.computeIfAbsent(newTier, k -> new ArrayList<>()).add(id);

        // Update boss set
        if (newTier == MobTier.BOSS && !bossMobs.contains(id)) {
            bossMobs.add(id);
        } else if (newTier != MobTier.BOSS && bossMobs.contains(id)) {
            bossMobs.remove(id);
        }
    }

    /**
     * Get actual stats for a mob (if available from attribute refinement).
     */
    public Optional<ActualMobStats> getActualStats(ResourceLocation id) {
        return Optional.ofNullable(actualStatsCache.get(id));
    }

    /**
     * Check if attribute refinement has been performed.
     */
    public boolean isAttributesRefined() {
        return attributesRefined;
    }

    /**
     * Check if an entity type is eligible for quests (hostile/neutral mob).
     * Filters out spawners, projectiles, and other non-fightable entities.
     */
    private boolean isQuestEligible(EntityType<?> entityType) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entityType));
        String path = id.getPath().toLowerCase();

        // === EXCLUSIONS: Filter out non-mob entities ===
        // Spawners, projectiles, effects, and utility entities
        if (path.contains("spawner") || path.contains("spawn_egg") ||
            path.contains("projectile") || path.contains("fireball") || path.contains("arrow") ||
            path.contains("effect") || path.contains("particle") || path.contains("display") ||
            path.contains("marker") || path.contains("area_effect") || path.contains("lightning") ||
            path.contains("tnt") || path.contains("falling_block") || path.contains("item") ||
            path.contains("experience") || path.contains("orb") || path.contains("boat") ||
            path.contains("minecart") || path.contains("armor_stand") || path.contains("painting") ||
            path.contains("item_frame") || path.contains("leash") || path.contains("ender_pearl") ||
            path.contains("egg") || path.contains("snowball") || path.contains("potion") ||
            path.contains("trident") || path.contains("shulker_bullet") || path.contains("llama_spit") ||
            path.contains("evoker_fangs") || path.contains("eye_of_ender")) {
            return false;
        }

        MobCategory category = entityType.getCategory();

        // Include hostile monsters
        if (category == MobCategory.MONSTER) {
            return true;
        }

        // Include some specific neutral mobs that can be hostile
        // Known neutral mobs that can fight
        if (path.contains("golem") || path.contains("wolf") || path.contains("bee") ||
            path.contains("polar_bear") || path.contains("dolphin") || path.contains("llama") ||
            path.contains("panda") || path.contains("fox")) {
            return true;
        }

        // Check for modded boss indicators (but only if not already excluded)
        if (path.contains("boss") || path.contains("miniboss") || path.contains("elite")) {
            return true;
        }

        return false;
    }

    /**
     * Determine the difficulty tier for a mob based on various heuristics.
     * Improved to handle modded mobs better using namespace patterns and common naming conventions.
     */
    private MobTier determineTier(EntityType<?> entityType, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();

        // ============ BOSS TIER ============
        // Known vanilla bosses
        if (path.equals("ender_dragon") || path.equals("wither")) {
            return MobTier.BOSS;
        }
        // Boss keywords in name
        if (path.contains("boss") || path.contains("final_boss") || path.contains("lord") ||
            path.contains("king") || path.contains("queen") || path.contains("emperor") ||
            path.contains("overlord") || path.contains("ancient") || path.contains("titan")) {
            return MobTier.BOSS;
        }

        // ============ ELITE TIER ============
        // Known vanilla elites
        if (path.contains("ravager") || path.contains("piglin_brute") || path.contains("warden")) {
            return MobTier.ELITE;
        }
        // Elite keywords in name
        if (path.contains("elite") || path.contains("champion") || path.contains("miniboss") ||
            path.contains("captain") || path.contains("commander") || path.contains("general") ||
            path.contains("archon") || path.contains("greater") || path.contains("alpha") ||
            path.contains("summoner") || path.contains("necromancer") || path.contains("lich") ||
            path.contains("archmage") || path.contains("giant") || path.contains("golem") ||
            path.contains("behemoth") || path.contains("colossus")) {
            return MobTier.ELITE;
        }

        // ============ HARD TIER ============
        // Known vanilla hard mobs
        if (path.contains("enderman") || path.contains("witch") || path.contains("evoker") ||
            path.contains("vindicator") || path.contains("blaze") || path.contains("ghast") ||
            path.contains("guardian") || path.contains("elder") || path.contains("shulker")) {
            return MobTier.HARD;
        }
        // Hard keywords in name (magic users, warriors, dangerous creatures)
        if (path.contains("knight") || path.contains("warrior") || path.contains("mage") ||
            path.contains("wizard") || path.contains("sorcerer") || path.contains("demon") ||
            path.contains("devil") || path.contains("wraith") || path.contains("specter") ||
            path.contains("revenant") || path.contains("wight") || path.contains("shade") ||
            path.contains("assassin") || path.contains("berserker") || path.contains("brute") ||
            path.contains("drake") || path.contains("wyrm") || path.contains("dragon") ||
            path.contains("golem") || path.contains("construct") || path.contains("automaton") ||
            path.contains("sentinel") || path.contains("keeper") || path.contains("cultist") ||
            path.contains("priest") || path.contains("warlock") || path.contains("pyromancer") ||
            path.contains("cryomancer") || path.contains("electromancer") || path.contains("archillager") ||
            path.contains("illusioner") || path.contains("dead_king")) {
            return MobTier.HARD;
        }
        // Mod-specific patterns for known hard mob mods
        if (namespace.equals("irons_spellbooks") || namespace.equals("ars_nouveau") ||
            namespace.equals("cataclysm") || namespace.equals("born_in_chaos") ||
            namespace.equals("mutant_monsters") || namespace.equals("mowzies_mobs") ||
            namespace.equals("twilightforest") || namespace.equals("alexsmobs")) {
            // These mods typically have harder mobs - check for specific trivial ones
            if (!path.contains("small") && !path.contains("baby") && !path.contains("mini")) {
                return MobTier.HARD;
            }
        }

        // ============ MEDIUM TIER ============
        // Known vanilla medium mobs
        if (path.contains("creeper") || path.contains("spider") || path.contains("skeleton") ||
            path.contains("pillager") || path.contains("drowned") || path.contains("husk") ||
            path.contains("stray") || path.contains("phantom") || path.contains("hoglin") ||
            path.contains("piglin") || path.contains("cave_spider")) {
            return MobTier.MEDIUM;
        }
        // Medium keywords in name
        if (path.contains("soldier") || path.contains("guard") || path.contains("archer") ||
            path.contains("hunter") || path.contains("scout") || path.contains("raider") ||
            path.contains("bandit") || path.contains("thief") || path.contains("undead") ||
            path.contains("ghoul") || path.contains("vampire") || path.contains("werewolf") ||
            path.contains("wolf") || path.contains("bear") || path.contains("crawler") ||
            path.contains("lurker") || path.contains("stalker") || path.contains("horror") ||
            path.contains("mimic") || path.contains("shade") || path.contains("spirit")) {
            return MobTier.MEDIUM;
        }

        // ============ TRIVIAL TIER ============
        // Known vanilla trivial mobs
        if (path.contains("silverfish") || path.contains("slime") || path.contains("magma_cube") ||
            path.contains("vex") || path.contains("bat") || path.contains("endermite")) {
            return MobTier.TRIVIAL;
        }
        // Trivial keywords
        if (path.contains("small") || path.contains("baby") || path.contains("mini") ||
            path.contains("lesser") || path.contains("tiny") || path.contains("weak") ||
            path.contains("larva") || path.contains("spawn") || path.contains("minion")) {
            return MobTier.TRIVIAL;
        }

        // ============ DEFAULT HANDLING ============
        // For modded mobs from unknown mods, use MEDIUM as default instead of EASY
        // This prevents modded threats from being underestimated
        if (!namespace.equals("minecraft")) {
            MobCategory category = entityType.getCategory();
            if (category == MobCategory.MONSTER) {
                return MobTier.MEDIUM; // Modded monsters default to MEDIUM
            }
        }

        // Vanilla unknown hostiles default to EASY
        return MobTier.EASY;
    }

    // ========== Public API ==========

    /**
     * Get all registered mob configurations.
     */
    public Collection<MobQuestConfig> getAllMobConfigs() {
        ensureInitialized();
        return Collections.unmodifiableCollection(mobConfigs.values());
    }

    /**
     * Get mob config by ID.
     */
    public Optional<MobQuestConfig> getMobConfig(ResourceLocation id) {
        ensureInitialized();
        return Optional.ofNullable(mobConfigs.get(id));
    }

    /**
     * Get all mobs from a specific mod/namespace.
     */
    public List<MobQuestConfig> getMobsByNamespace(String namespace) {
        ensureInitialized();
        return mobsByNamespace.getOrDefault(namespace, Collections.emptyList()).stream()
            .map(mobConfigs::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get all mobs of a specific tier.
     */
    public List<MobQuestConfig> getMobsByTier(MobTier tier) {
        ensureInitialized();
        return mobsByTier.getOrDefault(tier, Collections.emptyList()).stream()
            .map(mobConfigs::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get all boss mobs.
     */
    public List<MobQuestConfig> getBossMobs() {
        ensureInitialized();
        return bossMobs.stream()
            .map(mobConfigs::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get all available namespaces (mod IDs).
     */
    public Set<String> getAvailableNamespaces() {
        ensureInitialized();
        return Collections.unmodifiableSet(mobsByNamespace.keySet());
    }

    /**
     * Get total count of quest-eligible mobs.
     */
    public int getTotalMobCount() {
        ensureInitialized();
        return mobConfigs.size();
    }

    /**
     * Search mobs by name (partial match).
     */
    public List<MobQuestConfig> searchMobs(String query) {
        ensureInitialized();
        String lowerQuery = query.toLowerCase();
        return mobConfigs.values().stream()
            .filter(config -> config.displayName.toLowerCase().contains(lowerQuery) ||
                             config.mobId.toString().toLowerCase().contains(lowerQuery))
            .toList();
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}

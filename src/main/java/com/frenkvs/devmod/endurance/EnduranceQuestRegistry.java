package com.frenkvs.devmod.endurance;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry that scans all available mob types from vanilla and mods,
 * automatically generating quest configurations for each.
 */
@SuppressWarnings("null") // Minecraft/NeoForge API null-safety (EntityType, BuiltInRegistries)
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

    private boolean initialized = false;

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
     * Configuration for a mob's quest parameters.
     */
    public static class MobQuestConfig {
        public final ResourceLocation mobId;
        public final EntityType<?> entityType;
        public final MobTier tier;
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

        public MobQuestConfig(ResourceLocation mobId, EntityType<?> entityType, MobTier tier) {
            this.mobId = mobId;
            this.entityType = entityType;
            this.tier = tier;
            this.namespace = mobId.getNamespace();

            // Generate display name from ID
            String path = mobId.getPath();
            this.displayName = Arrays.stream(path.split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));

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
         * Calculate mob count for a specific wave (single player).
         */
        public int getMobCountForWave(int waveNumber) {
            return getMobCountForWave(waveNumber, 1, QuestType.PVE_COOP);
        }

        /**
         * Calculate mob count for a specific wave with player scaling.
         *
         * @param waveNumber Current wave number (1-based)
         * @param playerCount Number of players in the party
         * @param questType Quest type for difficulty multiplier
         * @return Scaled mob count
         */
        public int getMobCountForWave(int waveNumber, int playerCount, QuestType questType) {
            int baseCount = (int)(baseCountPerWave + (waveNumber - 1) * countScalingPerWave);
            int capped = Math.min(baseCount, maxPerWave);
            return DifficultyScaler.INSTANCE.scaleMobCount(capped, playerCount, questType);
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
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

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
     * Check if an entity type is eligible for quests (hostile/neutral mob).
     */
    private boolean isQuestEligible(EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();

        // Include hostile and some creatures (for neutral mobs like wolves, iron golems)
        if (category == MobCategory.MONSTER) {
            return true;
        }

        // Include some specific neutral mobs that can be hostile
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        String path = id.getPath().toLowerCase();

        // Known neutral mobs that can fight
        if (path.contains("golem") || path.contains("wolf") || path.contains("bee") ||
            path.contains("polar_bear") || path.contains("dolphin") || path.contains("llama") ||
            path.contains("panda") || path.contains("fox")) {
            return true;
        }

        // Check for modded boss indicators
        if (path.contains("boss") || path.contains("miniboss") || path.contains("elite")) {
            return true;
        }

        return false;
    }

    /**
     * Determine the difficulty tier for a mob based on various heuristics.
     */
    private MobTier determineTier(EntityType<?> entityType, ResourceLocation id) {
        String path = id.getPath().toLowerCase();

        // Known bosses
        if (path.equals("ender_dragon") || path.equals("wither") ||
            path.contains("boss") || path.contains("final_boss")) {
            return MobTier.BOSS;
        }

        // Elite mobs
        if (path.contains("ravager") || path.contains("piglin_brute") ||
            path.contains("warden") || path.contains("elite") ||
            path.contains("champion") || path.contains("miniboss")) {
            return MobTier.ELITE;
        }

        // Hard mobs
        if (path.contains("enderman") || path.contains("witch") ||
            path.contains("evoker") || path.contains("vindicator") ||
            path.contains("blaze") || path.contains("ghast") ||
            path.contains("guardian") || path.contains("elder")) {
            return MobTier.HARD;
        }

        // Medium mobs
        if (path.contains("creeper") || path.contains("spider") ||
            path.contains("skeleton") || path.contains("pillager") ||
            path.contains("drowned") || path.contains("husk") ||
            path.contains("stray") || path.contains("phantom")) {
            return MobTier.MEDIUM;
        }

        // Trivial mobs
        if (path.contains("silverfish") || path.contains("slime") ||
            path.contains("magma_cube") || path.contains("vex") ||
            path.contains("bat")) {
            return MobTier.TRIVIAL;
        }

        // Default to easy
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

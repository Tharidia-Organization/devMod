package com.devmod.endurance.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.SpawnAffix;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Persists global mob configuration to a JSON file.
 * This allows OPs to save permanent mob config overrides that
 * survive server restarts.
 *
 * File: config/devmod/global_mob_config.json
 */
public final class GlobalMobConfigStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalMobConfigStorage.class);
    private static final String CONFIG_DIR = "devmod";
    private static final String CONFIG_FILE = "global_mob_config.json";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // Cached global config
    private static EnduranceMobPoolConfig cachedConfig = null;
    private static boolean loaded = false;

    private GlobalMobConfigStorage() {}

    /**
     * Get the config directory path.
     */
    private static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIR);
    }

    /**
     * Get the config file path.
     */
    private static Path getConfigFile() {
        return getConfigDirectory().resolve(CONFIG_FILE);
    }

    /**
     * Ensure the config directory exists.
     */
    private static void ensureDirectory() {
        try {
            Path dir = getConfigDirectory();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                LOGGER.info("[GlobalMobConfig] Created config directory: {}", dir);
            }
        } catch (IOException e) {
            LOGGER.error("[GlobalMobConfig] Failed to create config directory", e);
        }
    }

    /**
     * Load the global mob config from disk.
     * @return The loaded config, or empty if not found
     */
    public static Optional<EnduranceMobPoolConfig> load() {
        if (loaded && cachedConfig != null) {
            return Optional.of(cachedConfig);
        }

        ensureDirectory();
        Path file = getConfigFile();

        if (!Files.exists(file)) {
            LOGGER.info("[GlobalMobConfig] No config file found, using defaults");
            loaded = true;
            return Optional.empty();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            StoredConfig stored = GSON.fromJson(json, StoredConfig.class);

            if (stored != null) {
                cachedConfig = stored.toPoolConfig();
                LOGGER.info("[GlobalMobConfig] Loaded global mob config with {} mob entries",
                    stored.mobConfigs != null ? stored.mobConfigs.size() : 0);
                loaded = true;
                return Optional.of(cachedConfig);
            }
        } catch (Exception e) {
            LOGGER.error("[GlobalMobConfig] Failed to load config", e);
        }

        loaded = true;
        return Optional.empty();
    }

    /**
     * Save the global mob config to disk.
     */
    public static void save(EnduranceMobPoolConfig config) {
        ensureDirectory();

        StoredConfig stored = StoredConfig.fromPoolConfig(config);

        try {
            String json = GSON.toJson(stored);
            Files.writeString(getConfigFile(), json, StandardCharsets.UTF_8);
            cachedConfig = config;
            LOGGER.info("[GlobalMobConfig] Saved global mob config with {} mob entries",
                stored.mobConfigs != null ? stored.mobConfigs.size() : 0);
        } catch (IOException e) {
            LOGGER.error("[GlobalMobConfig] Failed to save config", e);
        }
    }

    /**
     * Get the cached config if loaded.
     */
    public static Optional<EnduranceMobPoolConfig> getCached() {
        return Optional.ofNullable(cachedConfig);
    }

    /**
     * Clear the cached config (for testing or reload).
     */
    public static void clearCache() {
        cachedConfig = null;
        loaded = false;
    }

    /**
     * Check if config has been loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }

    // ========== Storage Format ==========

    /**
     * JSON-serializable storage format.
     */
    private static class StoredConfig {
        List<StoredMobConfig> mobConfigs;
        float globalHealthMult = 1.0f;
        float globalDamageMult = 1.0f;
        float globalSpeedMult = 1.0f;
        float globalEliteChanceMult = 1.0f;
        Map<String, Float> affixWeights;

        EnduranceMobPoolConfig toPoolConfig() {
            EnduranceMobPoolConfig pool = new EnduranceMobPoolConfig();

            // Apply global multipliers
            pool.setGlobalHealthMult(globalHealthMult);
            pool.setGlobalDamageMult(globalDamageMult);
            pool.setGlobalSpeedMult(globalSpeedMult);
            pool.setGlobalEliteChanceMult(globalEliteChanceMult);

            // Apply affix weights
            if (affixWeights != null) {
                for (Map.Entry<String, Float> entry : affixWeights.entrySet()) {
                    try {
                        SpawnAffix affix = SpawnAffix.valueOf(entry.getKey());
                        pool.setAffixWeight(affix, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[GlobalMobConfig] Unknown affix: {}", entry.getKey());
                    }
                }
            }

            // Apply mob configs
            if (mobConfigs != null) {
                for (StoredMobConfig stored : mobConfigs) {
                    try {
                        EnduranceMobConfig mobConfig = stored.toMobConfig();
                        pool.setMobConfig(mobConfig);
                    } catch (Exception e) {
                        LOGGER.warn("[GlobalMobConfig] Failed to parse mob config: {}", stored.mobId, e);
                    }
                }
            }

            return pool;
        }

        static StoredConfig fromPoolConfig(EnduranceMobPoolConfig pool) {
            StoredConfig stored = new StoredConfig();

            // Store global multipliers
            stored.globalHealthMult = pool.getGlobalHealthMult();
            stored.globalDamageMult = pool.getGlobalDamageMult();
            stored.globalSpeedMult = pool.getGlobalSpeedMult();
            stored.globalEliteChanceMult = pool.getGlobalEliteChanceMult();

            // Store affix weights
            stored.affixWeights = new HashMap<>();
            for (SpawnAffix affix : SpawnAffix.values()) {
                float weight = pool.getAffixWeight(affix);
                if (Math.abs(weight - 1.0f) > 0.001f) {
                    stored.affixWeights.put(affix.name(), weight);
                }
            }

            // Store mob configs (only those that differ from defaults)
            stored.mobConfigs = new ArrayList<>();
            for (EnduranceMobConfig config : pool.getAllMobConfigs()) {
                stored.mobConfigs.add(StoredMobConfig.fromMobConfig(config));
            }

            return stored;
        }
    }

    /**
     * JSON-serializable per-mob config.
     */
    private static class StoredMobConfig {
        String mobId;
        boolean enabled = true;
        float baseHealth;
        float baseDamage;
        float baseSpeed;
        float baseArmor;
        int baseCountPerWave;
        float countScalingPerWave;
        int maxPerWave;
        float eliteChance;
        String tier;
        String difficultyPreset;

        EnduranceMobConfig toMobConfig() {
            ResourceLocation id = ResourceLocation.parse(java.util.Objects.requireNonNull(mobId));
            MobTier mobTier = tier != null ? MobTier.valueOf(tier) : MobTier.MEDIUM;
            MobDifficultyPreset preset = difficultyPreset != null
                ? MobDifficultyPreset.valueOf(difficultyPreset)
                : MobDifficultyPreset.STANDARD;

            return new EnduranceMobConfig(
                id, enabled,
                baseHealth, baseDamage, baseSpeed, baseArmor,
                baseCountPerWave, countScalingPerWave, maxPerWave, eliteChance,
                mobTier, preset
            );
        }

        static StoredMobConfig fromMobConfig(EnduranceMobConfig config) {
            StoredMobConfig stored = new StoredMobConfig();
            stored.mobId = config.mobId().toString();
            stored.enabled = config.enabled();
            stored.baseHealth = config.baseHealth();
            stored.baseDamage = config.baseDamage();
            stored.baseSpeed = config.baseSpeed();
            stored.baseArmor = config.baseArmor();
            stored.baseCountPerWave = config.baseCountPerWave();
            stored.countScalingPerWave = config.countScalingPerWave();
            stored.maxPerWave = config.maxPerWave();
            stored.eliteChance = config.eliteChance();
            stored.tier = config.tier().name();
            stored.difficultyPreset = config.difficultyPreset().name();
            return stored;
        }
    }
}

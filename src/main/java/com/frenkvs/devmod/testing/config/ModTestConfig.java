package com.frenkvs.devmod.testing.config;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuration loader for mod-specific test templates.
 * Loads test definitions from JSON config files instead of hardcoding.
 *
 * Config location priority:
 * 1. External config: config/devmod/test_templates/{modId}.json
 * 2. Built-in: resources/data/devmod/test_templates/{modId}.json
 */
public class ModTestConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModTestConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Get config directory lazily via ConfigPaths.
     */
    private static Path getConfigDir() {
        return ConfigPaths.getTestTemplatesDir();
    }

    /**
     * Initialize config directory (creates if needed).
     */
    public static void init() {
        try {
            Files.createDirectories(getConfigDir());
        } catch (IOException e) {
            LOGGER.error("Failed to create test template config directory", e);
        }
    }

    /**
     * Load test template configuration for a mod.
     *
     * @param modId The mod ID
     * @return Template config or null if not found
     */
    public static TestTemplateConfig loadConfig(String modId) {
        // Try external config first
        Path configDir = getConfigDir();
        if (configDir != null) {
            Path externalConfig = configDir.resolve(modId + ".json");
            if (Files.exists(externalConfig)) {
                try (Reader reader = Files.newBufferedReader(externalConfig, StandardCharsets.UTF_8)) {
                    TestTemplateConfig config = GSON.fromJson(reader, TestTemplateConfig.class);
                    if (config != null) {
                        LOGGER.info("Loaded external test config for {}", modId);
                        return config;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load external test config for {}", modId, e);
                }
            }
        }

        // Try built-in resource
        String resourcePath = "/data/devmod/test_templates/" + modId + ".json";
        try (InputStream is = ModTestConfig.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    TestTemplateConfig config = GSON.fromJson(reader, TestTemplateConfig.class);
                    if (config != null) {
                        LOGGER.info("Loaded built-in test config for {}", modId);
                        return config;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("No built-in test config for {}", modId);
        }

        return null;
    }

    /**
     * Save a default config file for a mod (for development).
     */
    public static void saveDefaultConfig(String modId, TestTemplateConfig config) {
        Path configDir = getConfigDir();
        if (configDir == null) return;

        Path configFile = configDir.resolve(modId + ".json");
        try {
            Files.writeString(configFile, GSON.toJson(config), StandardCharsets.UTF_8);
            LOGGER.info("Saved default test config for {} to {}", modId, configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save test config for {}", modId, e);
        }
    }

    /**
     * Configuration data class for mod test templates.
     */
    public static class TestTemplateConfig {
        public String modId;
        public String displayName;
        public String templateName;

        // Content definitions
        public List<SchoolDefinition> magicSchools;
        public List<String> armorSets;
        public List<MobDefinition> mobs;
        public List<String> structures;
        public List<CraftingStationDefinition> craftingStations;
        public List<SpellbookTierDefinition> spellbookTiers;

        // Test definitions
        public List<TestDefinition> coreTests;
        public List<TestDefinition> customTests;

        // Integration settings
        public List<String> compatibleMods;
        public boolean generateSchoolTests = true;
        public boolean generateArmorTests = true;
        public boolean generateMobTests = true;
        public boolean generateStructureTests = true;
    }

    public static class SchoolDefinition {
        public String id;
        public String name;
        public String description;
        public List<String> spellExamples;
    }

    public static class MobDefinition {
        public String id;
        public String name;
        public String type; // Boss, Hostile, Neutral
        public String spawnLocation;
    }

    public static class CraftingStationDefinition {
        public String id;
        public String name;
        public String description;
        public String priority; // HIGH, MEDIUM, LOW
    }

    public static class SpellbookTierDefinition {
        public String id;
        public String name;
        public String material;
        public int spellSlots;
        public String priority;
    }

    public static class TestDefinition {
        public String id;
        public String category;
        public String name;
        public String description;
        public String steps;
        public String priority; // HIGH, MEDIUM, LOW
        public String progressCondition; // killed_mobs, interacted, armor_used, etc.
        public int progressThreshold = 1;
    }
}

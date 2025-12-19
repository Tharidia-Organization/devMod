package com.frenkvs.devmod;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages user-created presets for mob configuration.
 * Presets store stat multipliers that can be applied to any mob type.
 */
public class MobPresetManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PRESETS_FILE = "mob_presets.json";
    private static final int MAX_USER_PRESETS = 10;

    // User-created presets (preserves insertion order)
    private static final Map<String, MobPreset> userPresets = new LinkedHashMap<>();

    /**
     * A mob preset stores stat values (not multipliers) for quick application.
     */
    public record MobPreset(
        String name,
        double health,
        double armor,
        double damage,
        double speed,
        double followRange,
        double attackRange,
        double knockbackResist
    ) {
        /**
         * Create a preset with a display name and current stats.
         */
        public static MobPreset create(String name, double health, double armor, double damage,
                                       double speed, double followRange, double attackRange,
                                       double knockbackResist) {
            return new MobPreset(name, health, armor, damage, speed, followRange, attackRange, knockbackResist);
        }
    }

    /**
     * Save a user preset. Returns true if successful.
     */
    public static boolean savePreset(String name, MobPreset preset) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String key = name.trim();

        // Check limit (only if adding new, not updating existing)
        if (!userPresets.containsKey(key) && userPresets.size() >= MAX_USER_PRESETS) {
            LOGGER.warn("Cannot save preset '{}': maximum {} presets reached", key, MAX_USER_PRESETS);
            return false;
        }

        userPresets.put(key, preset);
        save();
        LOGGER.info("Saved user preset: {}", key);
        return true;
    }

    /**
     * Delete a user preset by name.
     */
    public static boolean deletePreset(String name) {
        if (userPresets.remove(name) != null) {
            save();
            LOGGER.info("Deleted user preset: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Get a user preset by name.
     */
    public static MobPreset getPreset(String name) {
        return userPresets.get(name);
    }

    /**
     * Get all user presets (returns copy).
     */
    public static Map<String, MobPreset> getAllPresets() {
        return new LinkedHashMap<>(userPresets);
    }

    /**
     * Get preset names in order.
     */
    public static String[] getPresetNames() {
        return userPresets.keySet().toArray(new String[0]);
    }

    /**
     * Check if a preset exists.
     */
    public static boolean hasPreset(String name) {
        return userPresets.containsKey(name);
    }

    /**
     * Get the number of user presets.
     */
    public static int getPresetCount() {
        return userPresets.size();
    }

    /**
     * Check if more presets can be created.
     */
    public static boolean canAddPreset() {
        return userPresets.size() < MAX_USER_PRESETS;
    }

    /**
     * Save presets to disk.
     */
    public static void save() {
        Path file = ConfigPaths.getConfigDir().resolve(PRESETS_FILE);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(userPresets, writer);
            }
            LOGGER.debug("Saved {} user presets", userPresets.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save mob presets", e);
        }
    }

    /**
     * Load presets from disk.
     */
    public static void load() {
        Path file = ConfigPaths.getConfigDir().resolve(PRESETS_FILE);
        if (!Files.exists(file)) {
            LOGGER.debug("No mob presets file found");
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<Map<String, MobPreset>>() {}.getType();
            Map<String, MobPreset> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                userPresets.clear();
                userPresets.putAll(loaded);
                LOGGER.info("Loaded {} user mob presets", userPresets.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load mob presets", e);
        }
    }
}

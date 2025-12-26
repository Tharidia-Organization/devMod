package com.devmod.util;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Configuration-driven damage type label management.
 * Loads damage type -> display label mappings from JSON config file.
 *
 * Config format (damage_types.json):
 * {
 *   "minecraft:on_fire": "§6Fire Damage",
 *   "minecraft:lava": "§cLava Damage",
 *   "mymod:custom_damage": "§bCustom Damage"
 * }
 *
 * Supports:
 * - Minecraft color codes (§ prefixed)
 * - Custom mod damage types
 * - Hot-reloading via reload() method
 * - Fallback to hardcoded defaults if config missing
 */
public final class DamageTypeConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod/DamageTypeConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Singleton instance
    public static final DamageTypeConfig INSTANCE = new DamageTypeConfig();

    // Loaded config mappings: ResourceLocation string -> display label
    private Map<String, String> damageLabels = new HashMap<>();

    // Track if config was loaded successfully
    private boolean configLoaded = false;

    private DamageTypeConfig() {
        // Initialize with defaults, will be overwritten by load()
        initializeDefaults();
    }

    /**
     * Loads the damage type config from file.
     * Should be called during mod initialization.
     */
    public void load() {
        Path configFile = ConfigPaths.getDamageTypesFile();

        try {
            // Ensure config directory exists
            Files.createDirectories(configFile.getParent());

            if (Files.exists(configFile)) {
                // Load existing config
                String json = Files.readString(configFile);
                Type mapType = new TypeToken<LinkedHashMap<String, String>>(){}.getType();
                Map<String, String> loaded = GSON.fromJson(json, mapType);

                if (loaded != null && !loaded.isEmpty()) {
                    damageLabels = loaded;
                    configLoaded = true;
                    LOGGER.info("[DevMod] Loaded {} damage type mappings from config", damageLabels.size());
                } else {
                    LOGGER.warn("[DevMod] Config file was empty, using defaults");
                    initializeDefaults();
                    saveDefaults();
                }
            } else {
                // Create default config file
                LOGGER.info("[DevMod] Creating default damage_types.json");
                initializeDefaults();
                saveDefaults();
                configLoaded = true;
            }
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to load damage_types.json: {}", e.getMessage());
            initializeDefaults();
        }
    }

    /**
     * Reloads the config from disk.
     * Can be called at runtime to pick up changes.
     */
    public void reload() {
        LOGGER.info("[DevMod] Reloading damage type config...");
        load();
    }

    // Default label for unknown/unmapped damage types
    private static final String UNKNOWN_DAMAGE_LABEL = "§7Unknown Damage";

    /**
     * Gets the display label for a damage type.
     *
     * @param damageTypeKey The ResourceKey of the damage type (e.g., DamageTypes.FALL)
     * @return The formatted label with color codes, or null if not mapped
     */
    public String getLabel(ResourceKey<?> damageTypeKey) {
        if (damageTypeKey == null) return null;
        String key = damageTypeKey.location().toString();
        return damageLabels.get(key);
    }

    /**
     * Gets the display label by ResourceLocation string.
     *
     * @param resourceLocation The full resource location (e.g., "minecraft:fall")
     * @return The formatted label with color codes, or null if not mapped
     */
    public String getLabel(String resourceLocation) {
        return damageLabels.get(resourceLocation);
    }

    /**
     * Gets the display label for a damage type with fallback.
     * Always returns a non-null label, using a generic fallback for unknown types.
     *
     * @param damageTypeKey The ResourceKey of the damage type
     * @return The formatted label, or "Unknown Damage" if not mapped
     */
    public String getLabelWithFallback(ResourceKey<?> damageTypeKey) {
        if (damageTypeKey == null) return UNKNOWN_DAMAGE_LABEL;
        String key = damageTypeKey.location().toString();
        return damageLabels.getOrDefault(key, formatUnknownLabel(key));
    }

    /**
     * Gets the display label by ResourceLocation string with fallback.
     * Always returns a non-null label, using a generic fallback for unknown types.
     *
     * @param resourceLocation The full resource location (e.g., "minecraft:fall")
     * @return The formatted label, or formatted type name if not mapped
     */
    public String getLabelWithFallback(String resourceLocation) {
        if (resourceLocation == null) return UNKNOWN_DAMAGE_LABEL;
        return damageLabels.getOrDefault(resourceLocation, formatUnknownLabel(resourceLocation));
    }

    /**
     * Formats an unknown damage type into a readable label.
     * Converts "minecraft:some_damage" -> "§7Some Damage"
     */
    private String formatUnknownLabel(String resourceLocation) {
        if (resourceLocation == null || resourceLocation.isEmpty()) {
            return UNKNOWN_DAMAGE_LABEL;
        }

        // Extract the path part after the colon (e.g., "some_damage" from "minecraft:some_damage")
        String path = resourceLocation;
        int colonIndex = resourceLocation.indexOf(':');
        if (colonIndex >= 0 && colonIndex < resourceLocation.length() - 1) {
            path = resourceLocation.substring(colonIndex + 1);
        }

        // Convert snake_case to Title Case
        StringBuilder result = new StringBuilder("§7");
        boolean capitalizeNext = true;
        for (char c : path.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Checks if a damage type is configured.
     */
    public boolean hasLabel(ResourceKey<?> damageTypeKey) {
        if (damageTypeKey == null) return false;
        return damageLabels.containsKey(damageTypeKey.location().toString());
    }

    /**
     * Gets all configured damage type mappings.
     * Returns a copy to prevent external modification.
     */
    public Map<String, String> getAllMappings() {
        return new LinkedHashMap<>(damageLabels);
    }

    /**
     * Adds or updates a damage type mapping at runtime.
     * Changes are not persisted until save() is called.
     */
    public void setLabel(String resourceLocation, String label) {
        damageLabels.put(resourceLocation, label);
    }

    /**
     * Saves the current config to disk.
     */
    public void save() {
        try {
            Path configFile = ConfigPaths.getDamageTypesFile();
            Files.createDirectories(configFile.getParent());
            String json = GSON.toJson(damageLabels);
            Files.writeString(configFile, json);
            LOGGER.info("[DevMod] Saved damage type config");
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to save damage_types.json: {}", e.getMessage());
        }
    }

    private void saveDefaults() {
        save();
    }

    /**
     * Initializes the default damage type mappings.
     * These match the original hardcoded values in DamageHandler.
     */
    private void initializeDefaults() {
        damageLabels = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order

        // === Fire damage types ===
        damageLabels.put("minecraft:on_fire", "§6Fire Damage");
        damageLabels.put("minecraft:in_fire", "§6In Fire");
        damageLabels.put("minecraft:lava", "§cLava Damage");
        damageLabels.put("minecraft:campfire", "§6Campfire");
        damageLabels.put("minecraft:hot_floor", "§6Hot Floor");

        // === Environmental ===
        damageLabels.put("minecraft:fall", "§eFall Damage");
        damageLabels.put("minecraft:drown", "§bDrowning");
        damageLabels.put("minecraft:in_wall", "§8Suffocation");
        damageLabels.put("minecraft:cactus", "§2Cactus");
        damageLabels.put("minecraft:fell_out_of_world", "§0Void Damage");
        damageLabels.put("minecraft:lightning_bolt", "§eLightning");
        damageLabels.put("minecraft:starve", "§4Starvation");
        damageLabels.put("minecraft:freeze", "§bFreezing");
        damageLabels.put("minecraft:cramming", "§7Cramming");
        damageLabels.put("minecraft:sweet_berry_bush", "§2Berry Bush");
        damageLabels.put("minecraft:stalagmite", "§7Stalagmite");
        damageLabels.put("minecraft:fly_into_wall", "§7Fly Into Wall");
        damageLabels.put("minecraft:outside_border", "§4World Border");
        damageLabels.put("minecraft:dry_out", "§eDrying Out");

        // === Effect-based ===
        damageLabels.put("minecraft:wither", "§5Wither");
        damageLabels.put("minecraft:magic", "§dMagic Damage");
        damageLabels.put("minecraft:indirect_magic", "§dIndirect Magic");
        damageLabels.put("minecraft:dragon_breath", "§5Dragon Breath");

        // === Explosions ===
        damageLabels.put("minecraft:explosion", "§cExplosion");
        damageLabels.put("minecraft:player_explosion", "§cExplosion");
        damageLabels.put("minecraft:bad_respawn_point", "§cBad Respawn Point");

        // === Projectiles ===
        damageLabels.put("minecraft:arrow", "§7Arrow");
        damageLabels.put("minecraft:trident", "§3Trident");
        damageLabels.put("minecraft:fireball", "§cFireball");
        damageLabels.put("minecraft:unattributed_fireball", "§cFireball");
        damageLabels.put("minecraft:wither_skull", "§5Wither Skull");
        damageLabels.put("minecraft:mob_projectile", "§7Mob Projectile");
        damageLabels.put("minecraft:thrown", "§fThrown Object");
        damageLabels.put("minecraft:wind_charge", "§fWind Charge");
        damageLabels.put("minecraft:spit", "§aLlama Spit");
        damageLabels.put("minecraft:fireworks", "§dFireworks");

        // === Mob-specific ===
        damageLabels.put("minecraft:sonic_boom", "§9Sonic Boom");
        damageLabels.put("minecraft:thorns", "§aThorns");
        damageLabels.put("minecraft:sting", "§eBee Sting");

        // === Falling objects ===
        damageLabels.put("minecraft:falling_anvil", "§8Falling Anvil");
        damageLabels.put("minecraft:falling_block", "§8Falling Block");
        damageLabels.put("minecraft:falling_stalactite", "§7Falling Stalactite");

        // === Generic/Combat ===
        damageLabels.put("minecraft:generic", "§7Generic Damage");
        damageLabels.put("minecraft:generic_kill", "§4Instant Kill");
        damageLabels.put("minecraft:mob_attack", "§cMob Attack");
        damageLabels.put("minecraft:mob_attack_no_aggro", "§7Mob Attack");
        damageLabels.put("minecraft:player_attack", "§cPlayer Attack");

        // === Special: Mace Smash (detected separately in DamageHandler) ===
        damageLabels.put("devmod:mace_smash", "§d§lMACE SMASH!");

        LOGGER.debug("[DevMod] Initialized {} default damage type mappings", damageLabels.size());
    }

    /**
     * Returns whether the config was successfully loaded from file.
     */
    public boolean isConfigLoaded() {
        return configLoaded;
    }
}

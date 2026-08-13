package com.devmod.util;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.resources.ResourceKey;

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
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

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
        } catch (JsonParseException e) {
            // GSON.fromJson throws unchecked on malformed JSON; load() runs from server start
            // handlers, so letting it escape would abort startup.
            LOGGER.error("[DevMod] Malformed JSON in {}: {} - using defaults",
                configFile, e.getMessage());
            initializeDefaults();
            quarantine(configFile);
        }
    }

    /**
     * Move an unparseable config out of the way so the defaults can be written without
     * destroying the user's file.
     */
    private void quarantine(Path configFile) {
        try {
            String timestamp = LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path target = configFile.resolveSibling(configFile.getFileName() + "." + timestamp + ".corrupt");
            Files.move(configFile, target);
            LOGGER.warn("[DevMod] Moved unparseable damage_types.json to {}", target);
        } catch (IOException e) {
            LOGGER.warn("[DevMod] Could not set aside unparseable damage_types.json: {}", e.getMessage());
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
    private static final String UNKNOWN_DAMAGE_LABEL = "\u00A77Unknown Damage";

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
     * Converts "minecraft:some_damage" -> "\u00A77Some Damage"
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
        StringBuilder result = new StringBuilder("\u00A77");
        boolean capitalizeNext = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
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
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
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
        damageLabels.put("minecraft:on_fire", "\u00A76Fire Damage");
        damageLabels.put("minecraft:in_fire", "\u00A76In Fire");
        damageLabels.put("minecraft:lava", "\u00A7cLava Damage");
        damageLabels.put("minecraft:campfire", "\u00A76Campfire");
        damageLabels.put("minecraft:hot_floor", "\u00A76Hot Floor");

        // === Environmental ===
        damageLabels.put("minecraft:fall", "\u00A7eFall Damage");
        damageLabels.put("minecraft:drown", "\u00A7bDrowning");
        damageLabels.put("minecraft:in_wall", "\u00A78Suffocation");
        damageLabels.put("minecraft:cactus", "\u00A72Cactus");
        damageLabels.put("minecraft:fell_out_of_world", "\u00A70Void Damage");
        damageLabels.put("minecraft:lightning_bolt", "\u00A7eLightning");
        damageLabels.put("minecraft:starve", "\u00A74Starvation");
        damageLabels.put("minecraft:freeze", "\u00A7bFreezing");
        damageLabels.put("minecraft:cramming", "\u00A77Cramming");
        damageLabels.put("minecraft:sweet_berry_bush", "\u00A72Berry Bush");
        damageLabels.put("minecraft:stalagmite", "\u00A77Stalagmite");
        damageLabels.put("minecraft:fly_into_wall", "\u00A77Fly Into Wall");
        damageLabels.put("minecraft:outside_border", "\u00A74World Border");
        damageLabels.put("minecraft:dry_out", "\u00A7eDrying Out");

        // === Effect-based ===
        damageLabels.put("minecraft:wither", "\u00A75Wither");
        damageLabels.put("minecraft:magic", "\u00A7dMagic Damage");
        damageLabels.put("minecraft:indirect_magic", "\u00A7dIndirect Magic");
        damageLabels.put("minecraft:dragon_breath", "\u00A75Dragon Breath");

        // === Explosions ===
        damageLabels.put("minecraft:explosion", "\u00A7cExplosion");
        damageLabels.put("minecraft:player_explosion", "\u00A7cExplosion");
        damageLabels.put("minecraft:bad_respawn_point", "\u00A7cBad Respawn Point");

        // === Projectiles ===
        damageLabels.put("minecraft:arrow", "\u00A77Arrow");
        damageLabels.put("minecraft:trident", "\u00A73Trident");
        damageLabels.put("minecraft:fireball", "\u00A7cFireball");
        damageLabels.put("minecraft:unattributed_fireball", "\u00A7cFireball");
        damageLabels.put("minecraft:wither_skull", "\u00A75Wither Skull");
        damageLabels.put("minecraft:mob_projectile", "\u00A77Mob Projectile");
        damageLabels.put("minecraft:thrown", "\u00A7fThrown Object");
        damageLabels.put("minecraft:wind_charge", "\u00A7fWind Charge");
        damageLabels.put("minecraft:spit", "\u00A7aLlama Spit");
        damageLabels.put("minecraft:fireworks", "\u00A7dFireworks");

        // === Mob-specific ===
        damageLabels.put("minecraft:sonic_boom", "\u00A79Sonic Boom");
        damageLabels.put("minecraft:thorns", "\u00A7aThorns");
        damageLabels.put("minecraft:sting", "\u00A7eBee Sting");

        // === Falling objects ===
        damageLabels.put("minecraft:falling_anvil", "\u00A78Falling Anvil");
        damageLabels.put("minecraft:falling_block", "\u00A78Falling Block");
        damageLabels.put("minecraft:falling_stalactite", "\u00A77Falling Stalactite");

        // === Generic/Combat ===
        damageLabels.put("minecraft:generic", "\u00A77Generic Damage");
        damageLabels.put("minecraft:generic_kill", "\u00A74Instant Kill");
        damageLabels.put("minecraft:mob_attack", "\u00A7cMob Attack");
        damageLabels.put("minecraft:mob_attack_no_aggro", "\u00A77Mob Attack");
        damageLabels.put("minecraft:player_attack", "\u00A7cPlayer Attack");

        // === Special: Mace Smash (detected separately in DamageHandler) ===
        damageLabels.put("devmod:mace_smash", "\u00A7d\u00A7lMACE SMASH!");

        LOGGER.debug("[DevMod] Initialized {} default damage type mappings", damageLabels.size());
    }

    /**
     * Returns whether the config was successfully loaded from file.
     */
    public boolean isConfigLoaded() {
        return configLoaded;
    }
}

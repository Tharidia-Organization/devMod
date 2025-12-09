package com.frenkvs.devmod.testing.stats;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks damage dealt and taken statistics.
 * Extracted from TesterProgress for single responsibility.
 *
 * <p>Supports automatic persistence to disk via {@link #save()} and {@link #load()}.
 * Statistics are preserved between game sessions.
 */
@SuppressWarnings("null")
public class DamageStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod/DamageStatistics");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final DamageStatistics INSTANCE = new DamageStatistics();

    // Track if data has changed since last save (for efficient periodic saves)
    private volatile boolean dirty = false;

    private double totalDamageDealt = 0;
    private double totalDamageTaken = 0;
    private int hitsDealt = 0;
    private int hitsTaken = 0;
    private double highestSingleHit = 0;
    private final Map<String, Double> damageByBodyPart = new ConcurrentHashMap<>();
    private final Map<String, Double> damageByWeapon = new ConcurrentHashMap<>();
    private final Map<String, Integer> hitsByBodyPart = new ConcurrentHashMap<>();

    private DamageStatistics() {}

    /**
     * Record damage dealt to an entity.
     */
    public void recordDamageDealt(double damage, String bodyPart, Item weapon) {
        totalDamageDealt += damage;
        hitsDealt++;
        dirty = true;

        if (damage > highestSingleHit) {
            highestSingleHit = damage;
        }

        if (bodyPart != null) {
            String part = bodyPart.toUpperCase();
            damageByBodyPart.merge(part, damage, Double::sum);
            hitsByBodyPart.merge(part, 1, Integer::sum);
        }

        if (weapon != null) {
            String weaponKey = weapon.getDescriptionId();
            damageByWeapon.merge(weaponKey, damage, Double::sum);
        }
    }

    /**
     * Record damage taken by the player.
     */
    public void recordDamageTaken(double damage) {
        totalDamageTaken += damage;
        hitsTaken++;
        dirty = true;
    }

    // === Getters ===
    public double getTotalDamageDealt() { return totalDamageDealt; }
    public double getTotalDamageTaken() { return totalDamageTaken; }
    public int getHitsDealt() { return hitsDealt; }
    public int getHitsTaken() { return hitsTaken; }
    public double getHighestSingleHit() { return highestSingleHit; }
    public double getDamageOnBodyPart(String part) { return damageByBodyPart.getOrDefault(part.toUpperCase(), 0.0); }
    public int getHitsOnBodyPart(String part) { return hitsByBodyPart.getOrDefault(part.toUpperCase(), 0); }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("totalDealt", totalDamageDealt);
        json.addProperty("totalTaken", totalDamageTaken);
        json.addProperty("hitsDealt", hitsDealt);
        json.addProperty("hitsTaken", hitsTaken);
        json.addProperty("highestSingle", highestSingleHit);
        json.add("byBodyPart", doubleMapToJson(damageByBodyPart));
        json.add("byWeapon", doubleMapToJson(damageByWeapon));
        json.add("hitsByBodyPart", intMapToJson(hitsByBodyPart));
        return json;
    }

    public void fromJson(JsonObject json) {
        totalDamageDealt = getDouble(json, "totalDealt", 0);
        totalDamageTaken = getDouble(json, "totalTaken", 0);
        hitsDealt = getInt(json, "hitsDealt", 0);
        hitsTaken = getInt(json, "hitsTaken", 0);
        highestSingleHit = getDouble(json, "highestSingle", 0);
        if (json.has("byBodyPart")) jsonToDoubleMap(json.getAsJsonObject("byBodyPart"), damageByBodyPart);
        if (json.has("byWeapon")) jsonToDoubleMap(json.getAsJsonObject("byWeapon"), damageByWeapon);
        if (json.has("hitsByBodyPart")) jsonToIntMap(json.getAsJsonObject("hitsByBodyPart"), hitsByBodyPart);
    }

    public void reset() {
        totalDamageDealt = 0;
        totalDamageTaken = 0;
        hitsDealt = 0;
        hitsTaken = 0;
        highestSingleHit = 0;
        damageByBodyPart.clear();
        damageByWeapon.clear();
        hitsByBodyPart.clear();
    }

    // JSON helpers
    private JsonObject doubleMapToJson(Map<String, Double> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    private JsonObject intMapToJson(Map<String, Integer> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    private void jsonToDoubleMap(JsonObject obj, Map<String, Double> map) {
        map.clear();
        for (String key : obj.keySet()) {
            map.put(key, obj.get(key).getAsDouble());
        }
    }

    private void jsonToIntMap(JsonObject obj, Map<String, Integer> map) {
        map.clear();
        for (String key : obj.keySet()) {
            map.put(key, obj.get(key).getAsInt());
        }
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    private double getDouble(JsonObject obj, String key, double defaultValue) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultValue;
    }

    // === File Persistence ===

    /**
     * Returns true if data has changed since last save.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Saves damage statistics to disk.
     * Call this on world save or player disconnect.
     */
    public void save() {
        if (!dirty) {
            LOGGER.debug("DamageStatistics not dirty, skipping save");
            return;
        }

        try {
            Path configFile = ConfigPaths.getDamageStatisticsFile();
            Files.createDirectories(configFile.getParent());

            JsonObject json = toJson();
            json.addProperty("savedAt", System.currentTimeMillis());

            String content = GSON.toJson(json);
            Files.writeString(configFile, content);

            dirty = false;
            LOGGER.info("[DevMod] Saved damage statistics ({} hits dealt, {} total damage)",
                hitsDealt, String.format("%.1f", totalDamageDealt));
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to save damage statistics: {}", e.getMessage());
        }
    }

    /**
     * Loads damage statistics from disk.
     * Call this on world load or player login.
     */
    public void load() {
        try {
            Path configFile = ConfigPaths.getDamageStatisticsFile();

            if (!Files.exists(configFile)) {
                LOGGER.debug("No saved damage statistics found, starting fresh");
                return;
            }

            String content = Files.readString(configFile);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            fromJson(json);
            dirty = false;  // Just loaded, not dirty

            LOGGER.info("[DevMod] Loaded damage statistics ({} hits dealt, {} total damage)",
                hitsDealt, String.format("%.1f", totalDamageDealt));
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to load damage statistics: {}", e.getMessage());
            // Don't reset on load failure - keep any in-memory data
        }
    }

    /**
     * Saves only if dirty. Use this for periodic auto-save.
     */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }
}

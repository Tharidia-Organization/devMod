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
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks damage dealt and taken statistics.
 * Extracted from TesterProgress for single responsibility.
 *
 * <p>Supports automatic persistence to disk via {@link #save()} and {@link #load()}.
 * Statistics are preserved between game sessions.
 */

public class DamageStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod/DamageStatistics");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final DamageStatistics INSTANCE = new DamageStatistics();

    // Track if data has changed since last save (for efficient periodic saves)
    private volatile boolean dirty = false;

    // Thread-safe counters using atomic adders
    private final DoubleAdder totalDamageDealt = new DoubleAdder();
    private final DoubleAdder totalDamageTaken = new DoubleAdder();
    private final LongAdder hitsDealt = new LongAdder();
    private final LongAdder hitsTaken = new LongAdder();
    private volatile double highestSingleHit = 0;
    private final Object highestHitLock = new Object();
    private final Map<String, Double> damageByBodyPart = new ConcurrentHashMap<>();
    private final Map<String, Double> damageByWeapon = new ConcurrentHashMap<>();
    private final Map<String, Integer> hitsByBodyPart = new ConcurrentHashMap<>();

    private DamageStatistics() {}

    /**
     * Record damage dealt to an entity.
     */
    public void recordDamageDealt(double damage, String bodyPart, Item weapon) {
        totalDamageDealt.add(damage);
        hitsDealt.increment();
        dirty = true;

        // Thread-safe update of highest single hit
        synchronized (highestHitLock) {
            if (damage > highestSingleHit) {
                highestSingleHit = damage;
            }
        }

        if (bodyPart != null) {
            String part = bodyPart.toUpperCase();
            damageByBodyPart.merge(part, damage, DamageStatistics::safeDoubleSum);
            hitsByBodyPart.merge(part, 1, DamageStatistics::safeIntSum);
        }

        if (weapon != null) {
            String weaponKey = weapon.getDescriptionId();
            damageByWeapon.merge(weaponKey, damage, DamageStatistics::safeDoubleSum);
        }
    }

    /**
     * Record damage taken by the player.
     */
    public void recordDamageTaken(double damage) {
        totalDamageTaken.add(damage);
        hitsTaken.increment();
        dirty = true;
    }

    // === Getters ===
    public double getTotalDamageDealt() { return totalDamageDealt.sum(); }
    public double getTotalDamageTaken() { return totalDamageTaken.sum(); }
    public int getHitsDealt() { return (int) hitsDealt.sum(); }
    public int getHitsTaken() { return (int) hitsTaken.sum(); }
    public double getHighestSingleHit() { return highestSingleHit; }
    public double getDamageOnBodyPart(String part) { return damageByBodyPart.getOrDefault(part.toUpperCase(), 0.0); }
    public int getHitsOnBodyPart(String part) { return hitsByBodyPart.getOrDefault(part.toUpperCase(), 0); }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("totalDealt", totalDamageDealt.sum());
        json.addProperty("totalTaken", totalDamageTaken.sum());
        json.addProperty("hitsDealt", hitsDealt.sum());
        json.addProperty("hitsTaken", hitsTaken.sum());
        json.addProperty("highestSingle", highestSingleHit);
        json.add("byBodyPart", doubleMapToJson(damageByBodyPart));
        json.add("byWeapon", doubleMapToJson(damageByWeapon));
        json.add("hitsByBodyPart", intMapToJson(hitsByBodyPart));
        return json;
    }

    public void fromJson(JsonObject json) {
        // Reset adders and add loaded values
        totalDamageDealt.reset();
        totalDamageDealt.add(getDouble(json, "totalDealt", 0));
        totalDamageTaken.reset();
        totalDamageTaken.add(getDouble(json, "totalTaken", 0));
        hitsDealt.reset();
        hitsDealt.add(getLong(json, "hitsDealt", 0));
        hitsTaken.reset();
        hitsTaken.add(getLong(json, "hitsTaken", 0));
        highestSingleHit = getDouble(json, "highestSingle", 0);
        if (json.has("byBodyPart")) jsonToDoubleMap(json.getAsJsonObject("byBodyPart"), damageByBodyPart);
        if (json.has("byWeapon")) jsonToDoubleMap(json.getAsJsonObject("byWeapon"), damageByWeapon);
        if (json.has("hitsByBodyPart")) jsonToIntMap(json.getAsJsonObject("hitsByBodyPart"), hitsByBodyPart);
    }

    public void reset() {
        totalDamageDealt.reset();
        totalDamageTaken.reset();
        hitsDealt.reset();
        hitsTaken.reset();
        highestSingleHit = 0;
        damageByBodyPart.clear();
        damageByWeapon.clear();
        hitsByBodyPart.clear();
    }

    private static Double safeDoubleSum(Double left, Double right) {
        double leftValue = left == null ? 0d : left.doubleValue();
        double rightValue = right == null ? 0d : right.doubleValue();
        return leftValue + rightValue;
    }

    private static Integer safeIntSum(Integer left, Integer right) {
        int leftValue = left == null ? 0 : left.intValue();
        int rightValue = right == null ? 0 : right.intValue();
        return leftValue + rightValue;
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

    private long getLong(JsonObject obj, String key, long defaultValue) {
        return obj.has(key) ? obj.get(key).getAsLong() : defaultValue;
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
                hitsDealt.sum(), String.format("%.1f", totalDamageDealt.sum()));
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
                hitsDealt.sum(), String.format("%.1f", totalDamageDealt.sum()));
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

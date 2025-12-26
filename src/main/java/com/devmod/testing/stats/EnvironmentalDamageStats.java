package com.devmod.testing.stats;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.damagesource.DamageSource;

import com.devmod.testing.stats.HazardTypeRegistry.HazardType;

import com.google.gson.JsonObject;

/**
 * Tracks environmental damage taken (fall, fire, lava, etc.).
 * Extracted from TesterProgress for single responsibility.
 *
 * <p>Now uses {@link HazardTypeRegistry} for config-driven hazard classification
 * instead of hardcoded string matching.
 */

public class EnvironmentalDamageStats {
    public static final EnvironmentalDamageStats INSTANCE = new EnvironmentalDamageStats();

    // Use EnumMap for efficient per-hazard-type tracking
    private final Map<HazardType, Double> damageByType = new EnumMap<>(HazardType.class);
    private int environmentalDeaths = 0;
    private boolean survivedExplosion = false;

    private EnvironmentalDamageStats() {
        // Initialize all hazard types to 0
        for (HazardType type : HazardType.values()) {
            damageByType.put(type, 0.0);
        }
    }

    /**
     * Record environmental damage and categorize it using the HazardTypeRegistry.
     * @return true if this was the first explosion survived
     */
    public boolean recordEnvironmentalDamage(double damage, DamageSource source) {
        boolean firstExplosionSurvived = false;

        if (source != null && damage > 0) {
            // Use config-driven registry for classification
            HazardType hazardType = HazardTypeRegistry.INSTANCE.classify(source);

            // Accumulate damage for this hazard type
            damageByType.merge(hazardType, damage, EnvironmentalDamageStats::safeDoubleSum);

            // Special handling for explosion achievement
            if (hazardType == HazardType.EXPLOSION && !survivedExplosion) {
                survivedExplosion = true;
                firstExplosionSurvived = true;
            }
        }

        return firstExplosionSurvived;
    }

    public void recordEnvironmentalDeath() {
        environmentalDeaths++;
    }

    // === Getters (legacy compatibility + new generic access) ===

    /**
     * Get damage taken for a specific hazard type.
     */
    public double getDamage(HazardType type) {
        Double value = damageByType.get(type);
        return value != null ? value : 0.0;
    }

    /**
     * Get all damage by type (read-only view).
     */
    public Map<HazardType, Double> getAllDamageByType() {
        return Map.copyOf(damageByType);
    }

    // Legacy getters for backward compatibility
    public double getFallDamageTaken() { return getDamage(HazardType.FALL); }
    public double getFireDamageTaken() { return getDamage(HazardType.FIRE); }
    public double getLavaDamageTaken() { return getDamage(HazardType.LAVA); }
    public double getDrowningDamageTaken() { return getDamage(HazardType.DROWNING); }
    public double getExplosionDamageTaken() { return getDamage(HazardType.EXPLOSION); }
    public double getPoisonDamageTaken() { return getDamage(HazardType.POISON); }
    public double getWitherDamageTaken() { return getDamage(HazardType.WITHER); }
    public double getFreezingDamageTaken() { return getDamage(HazardType.FREEZING); }
    public double getLightningDamageTaken() { return getDamage(HazardType.LIGHTNING); }
    public double getCactusDamageTaken() { return getDamage(HazardType.CACTUS); }
    public double getVoidDamageTaken() { return getDamage(HazardType.VOID); }
    public double getMagicDamageTaken() { return getDamage(HazardType.MAGIC); }
    public double getUnknownDamageTaken() { return getDamage(HazardType.UNKNOWN); }

    public int getEnvironmentalDeaths() { return environmentalDeaths; }
    public boolean hasSurvivedExplosion() { return survivedExplosion; }

    /**
     * Get total environmental damage taken across all hazard types.
     */
    public double getTotalEnvironmentalDamage() {
        return damageByType.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        // Save all hazard types dynamically
        JsonObject hazardDamage = new JsonObject();
        for (Map.Entry<HazardType, Double> entry : damageByType.entrySet()) {
            hazardDamage.addProperty(entry.getKey().name().toLowerCase(), entry.getValue());
        }
        json.add("hazardDamage", hazardDamage);

        // Legacy fields for backward compatibility
        json.addProperty("fall", getDamage(HazardType.FALL));
        json.addProperty("fire", getDamage(HazardType.FIRE));
        json.addProperty("lava", getDamage(HazardType.LAVA));
        json.addProperty("drowning", getDamage(HazardType.DROWNING));
        json.addProperty("explosion", getDamage(HazardType.EXPLOSION));
        json.addProperty("poison", getDamage(HazardType.POISON));
        json.addProperty("wither", getDamage(HazardType.WITHER));

        json.addProperty("deaths", environmentalDeaths);
        json.addProperty("survivedExplosion", survivedExplosion);
        return json;
    }

    public void fromJson(JsonObject json) {
        // Try new format first
        if (json.has("hazardDamage") && json.get("hazardDamage").isJsonObject()) {
            JsonObject hazardDamage = json.getAsJsonObject("hazardDamage");
            for (HazardType type : HazardType.values()) {
                String key = type.name().toLowerCase();
                double value = hazardDamage.has(key) ? hazardDamage.get(key).getAsDouble() : 0.0;
                damageByType.put(type, value);
            }
        } else {
            // Fall back to legacy format
            damageByType.put(HazardType.FALL, getDouble(json, "fall", 0));
            damageByType.put(HazardType.FIRE, getDouble(json, "fire", 0));
            damageByType.put(HazardType.LAVA, getDouble(json, "lava", 0));
            damageByType.put(HazardType.DROWNING, getDouble(json, "drowning", 0));
            damageByType.put(HazardType.EXPLOSION, getDouble(json, "explosion", 0));
            damageByType.put(HazardType.POISON, getDouble(json, "poison", 0));
            damageByType.put(HazardType.WITHER, getDouble(json, "wither", 0));
        }

        environmentalDeaths = getInt(json, "deaths", 0);
        survivedExplosion = getBool(json, "survivedExplosion", false);
    }

    public void reset() {
        for (HazardType type : HazardType.values()) {
            damageByType.put(type, 0.0);
        }
        environmentalDeaths = 0;
        survivedExplosion = false;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    private double getDouble(JsonObject obj, String key, double defaultValue) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultValue;
    }

    private boolean getBool(JsonObject obj, String key, boolean defaultValue) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : defaultValue;
    }

    private static Double safeDoubleSum(Double left, Double right) {
        double leftValue = left == null ? 0.0 : left.doubleValue();
        double rightValue = right == null ? 0.0 : right.doubleValue();
        return leftValue + rightValue;
    }
}

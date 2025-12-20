package com.frenkvs.devmod.testing.stats;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks potion and effect usage statistics.
 * Extracted from TesterProgress for single responsibility.
 */

public class PotionStatistics {
    public static final PotionStatistics INSTANCE = new PotionStatistics();

    private int potionsUsed = 0;
    private int potionEffectsGained = 0;
    private final Map<String, Integer> effectsByType = new ConcurrentHashMap<>();
    private int splashPotionsThrown = 0;
    private int lingeringPotionsThrown = 0;

    private PotionStatistics() {}

    /**
     * Record potion usage.
     */
    public void recordPotionUsed(String effectType, boolean isSplash, boolean isLingering) {
        potionsUsed++;
        if (effectType != null) {
            effectsByType.merge(effectType, 1, PotionStatistics::safeIntSum);
        }
        if (isSplash) splashPotionsThrown++;
        if (isLingering) lingeringPotionsThrown++;
    }

    /**
     * Record gaining a potion effect.
     */
    public void recordEffectGained(String effectType) {
        potionEffectsGained++;
        if (effectType != null) {
            effectsByType.merge(effectType, 1, PotionStatistics::safeIntSum);
        }
    }

    // === Getters ===
    public int getPotionsUsed() { return potionsUsed; }
    public int getPotionEffectsGained() { return potionEffectsGained; }
    public int getEffectUsageCount(String effectType) { return effectsByType.getOrDefault(effectType, 0); }
    public int getUniqueEffectsUsed() { return effectsByType.size(); }
    public int getSplashPotionsThrown() { return splashPotionsThrown; }
    public int getLingeringPotionsThrown() { return lingeringPotionsThrown; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("used", potionsUsed);
        json.addProperty("effectsGained", potionEffectsGained);
        json.addProperty("splash", splashPotionsThrown);
        json.addProperty("lingering", lingeringPotionsThrown);

        JsonObject byType = new JsonObject();
        for (Map.Entry<String, Integer> entry : effectsByType.entrySet()) {
            byType.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("byType", byType);

        return json;
    }

    public void fromJson(JsonObject json) {
        potionsUsed = getInt(json, "used", 0);
        potionEffectsGained = getInt(json, "effectsGained", 0);
        splashPotionsThrown = getInt(json, "splash", 0);
        lingeringPotionsThrown = getInt(json, "lingering", 0);

        if (json.has("byType")) {
            JsonObject byType = json.getAsJsonObject("byType");
            effectsByType.clear();
            for (String key : byType.keySet()) {
                effectsByType.put(key, byType.get(key).getAsInt());
            }
        }
    }

    public void reset() {
        potionsUsed = 0;
        potionEffectsGained = 0;
        effectsByType.clear();
        splashPotionsThrown = 0;
        lingeringPotionsThrown = 0;
    }

    private static Integer safeIntSum(Integer left, Integer right) {
        int leftValue = left == null ? 0 : left.intValue();
        int rightValue = right == null ? 0 : right.intValue();
        return leftValue + rightValue;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}

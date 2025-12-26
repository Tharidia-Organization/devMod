package com.devmod.testing.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;

/**
 * Tracks enchantment-related statistics.
 * Extracted from TesterProgress for single responsibility.
 */

public class EnchantmentStatistics {
    public static final EnchantmentStatistics INSTANCE = new EnchantmentStatistics();

    private int itemsEnchanted = 0;
    private int enchantedWeaponKills = 0;
    private final Map<String, Integer> killsByEnchantment = new ConcurrentHashMap<>();

    private EnchantmentStatistics() {}

    /**
     * Record an item being enchanted.
     */
    public void recordItemEnchanted() {
        itemsEnchanted++;
    }

    /**
     * Record an enchantment-related kill.
     */
    public void recordEnchantedKill(String enchantmentType) {
        enchantedWeaponKills++;
        if (enchantmentType != null) {
            killsByEnchantment.merge(enchantmentType, 1, EnchantmentStatistics::safeIntSum);
        }
    }

    // === Getters ===
    public int getItemsEnchanted() { return itemsEnchanted; }
    public int getEnchantedWeaponKills() { return enchantedWeaponKills; }
    public int getUniqueEnchantmentsUsed() { return killsByEnchantment.size(); }
    public int getKillsWithEnchantment(String enchantmentType) {
        return killsByEnchantment.getOrDefault(enchantmentType, 0);
    }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("itemsEnchanted", itemsEnchanted);
        json.addProperty("kills", enchantedWeaponKills);

        JsonObject byType = new JsonObject();
        for (Map.Entry<String, Integer> entry : killsByEnchantment.entrySet()) {
            byType.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("byType", byType);

        return json;
    }

    public void fromJson(JsonObject json) {
        itemsEnchanted = getInt(json, "itemsEnchanted", 0);
        enchantedWeaponKills = getInt(json, "kills", 0);

        if (json.has("byType")) {
            JsonObject byType = json.getAsJsonObject("byType");
            killsByEnchantment.clear();
            for (String key : byType.keySet()) {
                killsByEnchantment.put(key, byType.get(key).getAsInt());
            }
        }
    }

    public void reset() {
        itemsEnchanted = 0;
        enchantedWeaponKills = 0;
        killsByEnchantment.clear();
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

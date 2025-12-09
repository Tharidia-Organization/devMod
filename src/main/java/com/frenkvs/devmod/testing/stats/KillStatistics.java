package com.frenkvs.devmod.testing.stats;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all kill-related statistics.
 * Extracted from TesterProgress for single responsibility.
 */
@SuppressWarnings("null")
public class KillStatistics {
    public static final KillStatistics INSTANCE = new KillStatistics();

    private final Map<String, Integer> killsByMobType = new ConcurrentHashMap<>();
    private final Map<String, Integer> killsByWeapon = new ConcurrentHashMap<>();
    private final Map<String, Integer> killsByBodyPart = new ConcurrentHashMap<>();
    private int totalKills = 0;
    private int headshots = 0;
    private int criticalHits = 0;
    private int maceSmashKills = 0;
    private int arrowKills = 0;
    private int meleeKills = 0;

    // Streak tracking
    private int currentKillStreak = 0;
    private int maxKillStreak = 0;
    private long lastKillTime = 0;
    private static final long STREAK_TIMEOUT_MS = 10000;

    private KillStatistics() {}

    /**
     * Record a mob kill with context.
     * @return true if this was a first blood (very first kill)
     */
    public boolean recordKill(EntityType<?> mobType, Item weapon, String bodyPart,
                               boolean isCritical, boolean isHeadshot, boolean isMaceSmash,
                               boolean isArrow) {
        String mobKey = EntityType.getKey(mobType).toString();
        String weaponKey = weapon != null ? weapon.getDescriptionId() : "empty_hand";

        boolean wasFirstBlood = totalKills == 0;

        totalKills++;
        killsByMobType.merge(mobKey, 1, Integer::sum);
        killsByWeapon.merge(weaponKey, 1, Integer::sum);
        if (bodyPart != null) {
            killsByBodyPart.merge(bodyPart.toUpperCase(), 1, Integer::sum);
        }

        if (isHeadshot) headshots++;
        if (isCritical) criticalHits++;
        if (isMaceSmash) maceSmashKills++;
        if (isArrow) {
            arrowKills++;
        } else {
            meleeKills++;
        }

        // Update streak
        updateStreak();

        return wasFirstBlood;
    }

    private void updateStreak() {
        long now = System.currentTimeMillis();
        if (now - lastKillTime <= STREAK_TIMEOUT_MS) {
            currentKillStreak++;
        } else {
            currentKillStreak = 1;
        }
        lastKillTime = now;
        if (currentKillStreak > maxKillStreak) {
            maxKillStreak = currentKillStreak;
        }
    }

    /**
     * Check if player has killed with all body parts.
     */
    public boolean hasKilledWithAllBodyParts() {
        return killsByBodyPart.containsKey("HEAD") &&
               killsByBodyPart.containsKey("BODY") &&
               killsByBodyPart.containsKey("ARMS") &&
               killsByBodyPart.containsKey("LEGS");
    }

    // === Getters ===
    public int getTotalKills() { return totalKills; }
    public int getKillsOfType(String mobType) { return killsByMobType.getOrDefault(mobType, 0); }
    public int getKillsWithWeapon(String weapon) { return killsByWeapon.getOrDefault(weapon, 0); }
    public int getKillsOnBodyPart(String part) { return killsByBodyPart.getOrDefault(part.toUpperCase(), 0); }
    public int getHeadshots() { return headshots; }
    public int getCriticalHits() { return criticalHits; }
    public int getMaceSmashKills() { return maceSmashKills; }
    public int getArrowKills() { return arrowKills; }
    public int getMeleeKills() { return meleeKills; }
    public int getCurrentKillStreak() { return currentKillStreak; }
    public int getMaxKillStreak() { return maxKillStreak; }
    public Map<String, Integer> getKillsByBodyPart() { return killsByBodyPart; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("total", totalKills);
        json.addProperty("headshots", headshots);
        json.addProperty("criticalHits", criticalHits);
        json.addProperty("maceSmash", maceSmashKills);
        json.addProperty("arrow", arrowKills);
        json.addProperty("melee", meleeKills);
        json.addProperty("maxStreak", maxKillStreak);
        json.add("byMobType", mapToJson(killsByMobType));
        json.add("byWeapon", mapToJson(killsByWeapon));
        json.add("byBodyPart", mapToJson(killsByBodyPart));
        return json;
    }

    public void fromJson(JsonObject json) {
        totalKills = getInt(json, "total", 0);
        headshots = getInt(json, "headshots", 0);
        criticalHits = getInt(json, "criticalHits", 0);
        maceSmashKills = getInt(json, "maceSmash", 0);
        arrowKills = getInt(json, "arrow", 0);
        meleeKills = getInt(json, "melee", 0);
        maxKillStreak = getInt(json, "maxStreak", 0);
        if (json.has("byMobType")) jsonToMap(json.getAsJsonObject("byMobType"), killsByMobType);
        if (json.has("byWeapon")) jsonToMap(json.getAsJsonObject("byWeapon"), killsByWeapon);
        if (json.has("byBodyPart")) jsonToMap(json.getAsJsonObject("byBodyPart"), killsByBodyPart);
    }

    public void reset() {
        killsByMobType.clear();
        killsByWeapon.clear();
        killsByBodyPart.clear();
        totalKills = 0;
        headshots = 0;
        criticalHits = 0;
        maceSmashKills = 0;
        arrowKills = 0;
        meleeKills = 0;
        currentKillStreak = 0;
        maxKillStreak = 0;
        lastKillTime = 0;
    }

    // JSON helpers
    private JsonObject mapToJson(Map<String, Integer> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    private void jsonToMap(JsonObject obj, Map<String, Integer> map) {
        map.clear();
        for (String key : obj.keySet()) {
            map.put(key, obj.get(key).getAsInt());
        }
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}

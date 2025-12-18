package com.frenkvs.devmod.testing.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks interactions with modded content (weapons, mobs, effects, armor).
 * Extracted from TesterProgress for single responsibility.
 */

public class ModInteractionTracker {
    public static final ModInteractionTracker INSTANCE = new ModInteractionTracker();

    private final Map<String, Integer> killsByModId = new ConcurrentHashMap<>();
    private final Map<String, Integer> weaponsUsedByMod = new ConcurrentHashMap<>();
    private final Map<String, Integer> mobsKilledByMod = new ConcurrentHashMap<>();
    private final Map<String, Integer> effectsUsedByMod = new ConcurrentHashMap<>();
    private final Map<String, Integer> armorUsedByMod = new ConcurrentHashMap<>();
    private final Set<String> modsInteractedWith = ConcurrentHashMap.newKeySet();

    private ModInteractionTracker() {}

    /**
     * Record a kill with mod tracking.
     */
    public void recordModKill(EntityType<?> mobType, Item weapon) {
        // Track mob's mod
        ResourceLocation mobId = EntityType.getKey(mobType);
        String mobModId = mobId.getNamespace();
        if (!mobModId.equals("minecraft")) {
            mobsKilledByMod.merge(mobModId, 1, Integer::sum);
            modsInteractedWith.add(mobModId);
        }

        // Track weapon's mod
        if (weapon != null) {
            ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon);
            String weaponModId = weaponId.getNamespace();
            if (!weaponModId.equals("minecraft")) {
                weaponsUsedByMod.merge(weaponModId, 1, Integer::sum);
                killsByModId.merge(weaponModId, 1, Integer::sum);
                modsInteractedWith.add(weaponModId);
            }
        }
    }

    /**
     * Record effect from a mod.
     */
    public void recordModEffect(String effectType) {
        if (effectType != null && effectType.contains(":")) {
            String modId = effectType.split(":")[0];
            if (!modId.equals("minecraft")) {
                effectsUsedByMod.merge(modId, 1, Integer::sum);
                modsInteractedWith.add(modId);
            }
        }
    }

    /**
     * Record armor usage from a mod.
     */
    public void recordModArmor(String modId) {
        if (modId != null && !modId.equals("minecraft")) {
            armorUsedByMod.merge(modId, 1, Integer::sum);
            modsInteractedWith.add(modId);
        }
    }

    // === Getters ===
    public int getKillsWithModWeapon(String modId) { return killsByModId.getOrDefault(modId, 0); }
    public int getWeaponUsageFromMod(String modId) { return weaponsUsedByMod.getOrDefault(modId, 0); }
    public int getMobsKilledFromMod(String modId) { return mobsKilledByMod.getOrDefault(modId, 0); }
    public int getEffectsUsedFromMod(String modId) { return effectsUsedByMod.getOrDefault(modId, 0); }
    public int getArmorUsageFromMod(String modId) { return armorUsedByMod.getOrDefault(modId, 0); }
    public boolean hasInteractedWithMod(String modId) { return modsInteractedWith.contains(modId); }
    public Set<String> getModsInteractedWith() { return Collections.unmodifiableSet(modsInteractedWith); }
    public int getUniqueModsInteractedCount() { return modsInteractedWith.size(); }

    public int getTotalModdedKills() {
        return killsByModId.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getTotalModdedMobsKilled() {
        return mobsKilledByMod.values().stream().mapToInt(Integer::intValue).sum();
    }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("killsByModId", mapToJson(killsByModId));
        json.add("weaponsUsedByMod", mapToJson(weaponsUsedByMod));
        json.add("mobsKilledByMod", mapToJson(mobsKilledByMod));
        json.add("effectsUsedByMod", mapToJson(effectsUsedByMod));
        json.add("armorUsedByMod", mapToJson(armorUsedByMod));

        JsonArray modsArray = new JsonArray();
        for (String modId : modsInteractedWith) {
            modsArray.add(modId);
        }
        json.add("modsInteractedWith", modsArray);
        return json;
    }

    public void fromJson(JsonObject json) {
        if (json.has("killsByModId")) jsonToMap(json.getAsJsonObject("killsByModId"), killsByModId);
        if (json.has("weaponsUsedByMod")) jsonToMap(json.getAsJsonObject("weaponsUsedByMod"), weaponsUsedByMod);
        if (json.has("mobsKilledByMod")) jsonToMap(json.getAsJsonObject("mobsKilledByMod"), mobsKilledByMod);
        if (json.has("effectsUsedByMod")) jsonToMap(json.getAsJsonObject("effectsUsedByMod"), effectsUsedByMod);
        if (json.has("armorUsedByMod")) jsonToMap(json.getAsJsonObject("armorUsedByMod"), armorUsedByMod);

        if (json.has("modsInteractedWith")) {
            JsonArray modsArray = json.getAsJsonArray("modsInteractedWith");
            modsInteractedWith.clear();
            for (int i = 0; i < modsArray.size(); i++) {
                modsInteractedWith.add(modsArray.get(i).getAsString());
            }
        }
    }

    public void reset() {
        killsByModId.clear();
        weaponsUsedByMod.clear();
        mobsKilledByMod.clear();
        effectsUsedByMod.clear();
        armorUsedByMod.clear();
        modsInteractedWith.clear();
    }

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
}

package com.frenkvs.devmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages food stats configuration and persistence.
 * Priority: Component > CustomData > Global > Vanilla Default
 */
public final class FoodConfigManager {
    private FoodConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("devmod");
    private static final Path FOOD_CONFIG = CONFIG_DIR.resolve("food_stats.json");
    private static final int MAX_BACKUPS = 5;

    // Global stats cache (item ID -> stats)
    private static final Map<String, FoodStats> GLOBAL_STATS = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get food stats for an item. Priority: Component > CustomData > Global > Vanilla Default
     */
    public static FoodStats getStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new FoodStats();
        }

        // 1. Try component
        FoodStats fromComponent = getFromComponent(stack);
        if (fromComponent != null) {
            return fromComponent;
        }

        // 2. Try CustomData (legacy)
        FoodStats fromCustomData = getFromCustomData(stack);
        if (fromCustomData != null) {
            return fromCustomData;
        }

        // 3. Try global config
        String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();
        FoodStats global = GLOBAL_STATS.get(itemId);
        if (global != null) {
            return global.copy();
        }

        // 4. Vanilla defaults
        return getVanillaDefaults(stack);
    }

    /**
     * Check if an item has food properties.
     */
    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(Objects.requireNonNull(DataComponents.FOOD));
    }

    /**
     * Set specific stats on an item (writes to component).
     */
    public static void setSpecificStats(ItemStack stack, FoodStats stats) {
        if (stack == null || stack.isEmpty() || stats == null) return;

        DataComponentType<CompoundTag> componentType = FoodComponents.foodStatsComponent();
        if (componentType != null) {
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            stack.set(componentType, tag);
        }
    }

    /**
     * Set global stats for an item type.
     */
    public static void setGlobalStats(String itemId, FoodStats stats) {
        if (itemId == null || stats == null) return;
        GLOBAL_STATS.put(itemId, stats.copy());
        saveConfig();
    }

    /**
     * Clear specific stats from an item.
     */
    public static void clearSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        DataComponentType<CompoundTag> componentType = FoodComponents.foodStatsComponent();
        if (componentType != null) {
            stack.remove(componentType);
        }
    }

    /**
     * Clear global stats for an item type.
     */
    public static void clearGlobalStats(String itemId) {
        if (itemId == null) return;
        GLOBAL_STATS.remove(itemId);
        saveConfig();
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static FoodStats getFromComponent(ItemStack stack) {
        DataComponentType<CompoundTag> componentType = FoodComponents.foodStatsComponent();
        if (componentType == null) return null;

        CompoundTag tag = stack.get(componentType);
        if (tag != null && !tag.isEmpty()) {
            FoodStats stats = new FoodStats();
            stats.load(tag);
            return stats;
        }
        return null;
    }

    private static FoodStats getFromCustomData(ItemStack stack) {
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData == null) return null;

        CompoundTag tag = customData.copyTag();
        if (tag.contains("FoodModStats")) {
            FoodStats stats = new FoodStats();
            stats.load(tag.getCompound("FoodModStats"));
            return stats;
        }
        return null;
    }

    private static FoodStats getVanillaDefaults(ItemStack stack) {
        FoodStats stats = new FoodStats();

        FoodProperties food = stack.get(Objects.requireNonNull(DataComponents.FOOD));
        if (food != null) {
            stats.nutrition = food.nutrition();
            stats.saturation = food.saturation();
            stats.canAlwaysEat = food.canAlwaysEat();

            // Extract effects from FoodProperties
            // Note: In 1.21.1, effects are accessed differently
            // We'll populate this when we have access to the actual API
        }

        // Default consumption time
        stats.consumptionTime = 32; // Default for food

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIG PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    public static void loadConfig() {
        try {
            if (!Files.exists(FOOD_CONFIG)) {
                return;
            }

            String json = Files.readString(FOOD_CONFIG);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root.has("global")) {
                JsonObject global = root.getAsJsonObject("global");
                for (Map.Entry<String, JsonElement> entry : global.entrySet()) {
                    FoodStats stats = parseStatsFromJson(entry.getValue().getAsJsonObject());
                    GLOBAL_STATS.put(entry.getKey(), stats);
                }
            }

            DevMod.LOGGER.info("[FoodConfig] Loaded {} global food stats", GLOBAL_STATS.size());
        } catch (Exception e) {
            DevMod.LOGGER.error("[FoodConfig] Failed to load config", e);
        }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);

            // Create backup if config exists
            if (Files.exists(FOOD_CONFIG)) {
                createBackup();
            }

            JsonObject root = new JsonObject();
            JsonObject global = new JsonObject();

            for (Map.Entry<String, FoodStats> entry : GLOBAL_STATS.entrySet()) {
                global.add(entry.getKey(), statsToJson(entry.getValue()));
            }

            root.add("global", global);

            Files.writeString(FOOD_CONFIG, GSON.toJson(root));
            DevMod.LOGGER.info("[FoodConfig] Saved {} global food stats", GLOBAL_STATS.size());
        } catch (Exception e) {
            DevMod.LOGGER.error("[FoodConfig] Failed to save config", e);
        }
    }

    private static void createBackup() {
        try {
            Path backupDir = CONFIG_DIR.resolve("backups");
            Files.createDirectories(backupDir);

            // Rotate backups
            for (int i = MAX_BACKUPS - 1; i > 0; i--) {
                Path older = backupDir.resolve("food_stats." + i + ".json");
                Path newer = backupDir.resolve("food_stats." + (i - 1) + ".json");
                if (Files.exists(newer)) {
                    Files.move(newer, older, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Create new backup
            Files.copy(FOOD_CONFIG, backupDir.resolve("food_stats.0.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DevMod.LOGGER.warn("[FoodConfig] Failed to create backup", e);
        }
    }

    private static JsonObject statsToJson(FoodStats stats) {
        JsonObject obj = new JsonObject();
        obj.addProperty("nutrition", stats.nutrition);
        obj.addProperty("saturation", stats.saturation);
        obj.addProperty("consumptionTime", stats.consumptionTime);
        obj.addProperty("canAlwaysEat", stats.canAlwaysEat);
        obj.addProperty("isMeat", stats.isMeat);
        obj.addProperty("isFastFood", stats.isFastFood);

        JsonArray effectsArray = new JsonArray();
        for (FoodStats.FoodEffect effect : stats.effects) {
            JsonObject effectObj = new JsonObject();
            effectObj.addProperty("effectId", effect.effectId);
            effectObj.addProperty("duration", effect.duration);
            effectObj.addProperty("amplifier", effect.amplifier);
            effectObj.addProperty("probability", effect.probability);
            effectsArray.add(effectObj);
        }
        obj.add("effects", effectsArray);

        return obj;
    }

    private static FoodStats parseStatsFromJson(JsonObject obj) {
        FoodStats stats = new FoodStats();
        if (obj.has("nutrition")) stats.nutrition = obj.get("nutrition").getAsInt();
        if (obj.has("saturation")) stats.saturation = obj.get("saturation").getAsFloat();
        if (obj.has("consumptionTime")) stats.consumptionTime = obj.get("consumptionTime").getAsInt();
        if (obj.has("canAlwaysEat")) stats.canAlwaysEat = obj.get("canAlwaysEat").getAsBoolean();
        if (obj.has("isMeat")) stats.isMeat = obj.get("isMeat").getAsBoolean();
        if (obj.has("isFastFood")) stats.isFastFood = obj.get("isFastFood").getAsBoolean();

        if (obj.has("effects")) {
            JsonArray effectsArray = obj.getAsJsonArray("effects");
            for (JsonElement elem : effectsArray) {
                JsonObject effectObj = elem.getAsJsonObject();
                FoodStats.FoodEffect effect = new FoodStats.FoodEffect();
                if (effectObj.has("effectId")) effect.effectId = effectObj.get("effectId").getAsString();
                if (effectObj.has("duration")) effect.duration = effectObj.get("duration").getAsInt();
                if (effectObj.has("amplifier")) effect.amplifier = effectObj.get("amplifier").getAsInt();
                if (effectObj.has("probability")) effect.probability = effectObj.get("probability").getAsFloat();
                stats.effects.add(effect);
            }
        }

        return stats;
    }
}

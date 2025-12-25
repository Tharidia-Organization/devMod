package com.devmod.config;
import com.devmod.DevMod;

import com.devmod.stats.FuelStats;
import com.devmod.components.FuelComponents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages fuel stats configuration and persistence.
 * Priority: Component > CustomData > Global > Vanilla Default
 */
public final class FuelConfigManager {
    private FuelConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("devmod");
    private static final Path FUEL_CONFIG = CONFIG_DIR.resolve("fuel_stats.json");
    private static final int MAX_BACKUPS = 5;

    // Global stats cache (item ID -> stats)
    private static final Map<String, FuelStats> GLOBAL_STATS = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize and load config. Should be called on mod startup.
     */
    public static void initialize() {
        loadConfig();
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get fuel stats for an item. Priority: Component > CustomData > Global > Vanilla Default
     */
    public static FuelStats getStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new FuelStats();
        }

        // 1. Try component
        FuelStats fromComponent = getFromComponent(stack);
        if (fromComponent != null) {
            return fromComponent;
        }

        // 2. Try CustomData (legacy)
        FuelStats fromCustomData = getFromCustomData(stack);
        if (fromCustomData != null) {
            return fromCustomData;
        }

        // 3. Try global config
        String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();
        FuelStats global = GLOBAL_STATS.get(itemId);
        if (global != null) {
            return global.copy();
        }

        // 4. Vanilla defaults
        return getVanillaDefaults(stack);
    }

    /**
     * Check if an item is a fuel using vanilla fuel registry.
     * IMPORTANT: This method must NOT call stack.getBurnTime() to avoid infinite recursion.
     */
    public static boolean isFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            // Check if item has custom fuel stats first
            String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()))).toString();
            if (GLOBAL_STATS.containsKey(itemId)) return true;

            // Check vanilla fuel map directly - DO NOT use stack.getBurnTime()!
            @SuppressWarnings("deprecation")
            boolean vanillaFuel = AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem());
            return vanillaFuel;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get burn time for an item from vanilla fuel registry.
     * IMPORTANT: This method must NOT call stack.getBurnTime() to avoid infinite recursion
     * when called from FurnaceFuelBurnTimeEvent handler, as getBurnTime triggers that event.
     */
    public static int getVanillaBurnTime(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            // Use vanilla fuel map directly - DO NOT use stack.getBurnTime() here!
            // stack.getBurnTime() triggers FurnaceFuelBurnTimeEvent which calls FuelEvents
            // which calls getStats() which calls getVanillaDefaults() which calls this method
            // = infinite recursion / StackOverflowError
            @SuppressWarnings("deprecation")
            Integer vanillaBurnTime = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
            return vanillaBurnTime != null ? vanillaBurnTime : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Set specific stats on an item (writes to component).
     */
    public static void setSpecificStats(ItemStack stack, FuelStats stats) {
        if (stack == null || stack.isEmpty() || stats == null) return;

        DataComponentType<CompoundTag> componentType = FuelComponents.fuelStatsComponent();
        if (componentType != null) {
            CompoundTag tag = new CompoundTag();
            stats.save(tag);
            stack.set(componentType, tag);
        }
    }

    /**
     * Set global stats for an item type.
     */
    public static void setGlobalStats(String itemId, FuelStats stats) {
        if (itemId == null || stats == null) return;
        GLOBAL_STATS.put(itemId, stats.copy());
        saveConfig();
    }

    /**
     * Set global stats for an item.
     */
    public static void setGlobalStats(Item item, FuelStats stats) {
        if (item == null || stats == null) return;
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        setGlobalStats(itemId, stats);
    }

    /**
     * Clear specific stats from an item.
     */
    public static void clearSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        DataComponentType<CompoundTag> componentType = FuelComponents.fuelStatsComponent();
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

    /**
     * Get global stats for a specific item.
     */
    public static FuelStats getGlobalStats(Item item) {
        if (item == null) return new FuelStats();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        FuelStats stats = GLOBAL_STATS.get(itemId);
        return stats != null ? stats.copy() : new FuelStats();
    }

    /**
     * Check if an item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        if (item == null) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return GLOBAL_STATS.containsKey(itemId);
    }

    /**
     * Get an immutable view of all global fuel overrides.
     */
    public static Map<String, FuelStats> getAllGlobalStats() {
        return Map.copyOf(GLOBAL_STATS);
    }

    /**
     * Clears all global statistics (used for GameTests).
     */
    public static void clearAllGlobalStats() {
        GLOBAL_STATS.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static FuelStats getFromComponent(ItemStack stack) {
        DataComponentType<CompoundTag> componentType = FuelComponents.fuelStatsComponent();
        if (componentType == null) return null;

        CompoundTag tag = stack.get(componentType);
        if (tag != null && !tag.isEmpty()) {
            return FuelStats.load(tag);
        }
        return null;
    }

    private static FuelStats getFromCustomData(ItemStack stack) {
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData == null) return null;

        CompoundTag tag = customData.copyTag();
        if (tag.contains("FuelModStats")) {
            return FuelStats.load(tag.getCompound("FuelModStats"));
        }
        return null;
    }

    private static FuelStats getVanillaDefaults(ItemStack stack) {
        FuelStats stats = new FuelStats();
        stats.burnTime = getVanillaBurnTime(stack);
        // Default cook times (vanilla values)
        stats.furnaceCookTime = 200;
        stats.blastFurnaceCookTime = 100;
        stats.smokerCookTime = 100;
        stats.campfireCookTime = 600;
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIG PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    public static void loadConfig() {
        try {
            if (!Files.exists(FUEL_CONFIG)) {
                return;
            }

            String json = Files.readString(FUEL_CONFIG);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root.has("global")) {
                JsonObject global = root.getAsJsonObject("global");
                for (Map.Entry<String, JsonElement> entry : global.entrySet()) {
                    FuelStats stats = parseStatsFromJson(entry.getValue().getAsJsonObject());
                    GLOBAL_STATS.put(entry.getKey(), stats);
                }
            }

            DevMod.LOGGER.info("[FuelConfig] Loaded {} global fuel stats", GLOBAL_STATS.size());
        } catch (Exception e) {
            DevMod.LOGGER.error("[FuelConfig] Failed to load config", e);
        }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);

            // Create backup if config exists
            if (Files.exists(FUEL_CONFIG)) {
                createBackup();
            }

            JsonObject root = new JsonObject();
            JsonObject global = new JsonObject();

            for (Map.Entry<String, FuelStats> entry : GLOBAL_STATS.entrySet()) {
                global.add(entry.getKey(), statsToJson(entry.getValue()));
            }

            root.add("global", global);

            Files.writeString(FUEL_CONFIG, GSON.toJson(root));
            DevMod.LOGGER.info("[FuelConfig] Saved {} global fuel stats", GLOBAL_STATS.size());
        } catch (Exception e) {
            DevMod.LOGGER.error("[FuelConfig] Failed to save config", e);
        }
    }

    private static void createBackup() {
        try {
            Path backupDir = CONFIG_DIR.resolve("backups");
            Files.createDirectories(backupDir);

            // Rotate backups
            for (int i = MAX_BACKUPS - 1; i > 0; i--) {
                Path older = backupDir.resolve("fuel_stats." + i + ".json");
                Path newer = backupDir.resolve("fuel_stats." + (i - 1) + ".json");
                if (Files.exists(newer)) {
                    Files.move(newer, older, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Create new backup
            Files.copy(FUEL_CONFIG, backupDir.resolve("fuel_stats.0.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DevMod.LOGGER.warn("[FuelConfig] Failed to create backup", e);
        }
    }

    private static JsonObject statsToJson(FuelStats stats) {
        JsonObject obj = new JsonObject();
        obj.addProperty("burnTime", stats.burnTime);
        obj.addProperty("overrideDefault", stats.overrideDefault);
        obj.addProperty("efficiencyMultiplier", stats.efficiencyMultiplier);
        obj.addProperty("furnaceCookTime", stats.furnaceCookTime);
        obj.addProperty("blastFurnaceCookTime", stats.blastFurnaceCookTime);
        obj.addProperty("smokerCookTime", stats.smokerCookTime);
        obj.addProperty("campfireCookTime", stats.campfireCookTime);
        obj.addProperty("customCookTimesEnabled", stats.customCookTimesEnabled);
        return obj;
    }

    private static FuelStats parseStatsFromJson(JsonObject obj) {
        FuelStats stats = new FuelStats();
        if (obj.has("burnTime")) stats.burnTime = obj.get("burnTime").getAsInt();
        if (obj.has("overrideDefault")) stats.overrideDefault = obj.get("overrideDefault").getAsBoolean();
        if (obj.has("efficiencyMultiplier")) stats.efficiencyMultiplier = obj.get("efficiencyMultiplier").getAsFloat();
        if (obj.has("furnaceCookTime")) stats.furnaceCookTime = obj.get("furnaceCookTime").getAsInt();
        if (obj.has("blastFurnaceCookTime")) stats.blastFurnaceCookTime = obj.get("blastFurnaceCookTime").getAsInt();
        if (obj.has("smokerCookTime")) stats.smokerCookTime = obj.get("smokerCookTime").getAsInt();
        if (obj.has("campfireCookTime")) stats.campfireCookTime = obj.get("campfireCookTime").getAsInt();
        if (obj.has("customCookTimesEnabled")) stats.customCookTimesEnabled = obj.get("customCookTimesEnabled").getAsBoolean();
        return stats;
    }
}

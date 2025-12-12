package com.frenkvs.devmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages armor configuration storage and retrieval.
 * Follows the same pattern as WeaponConfigManager for consistency.
 *
 * Storage hierarchy:
 * 1. NBT Data (per-item instance) - highest priority
 * 2. Global Config (per-item type) - from JSON file
 * 3. Default Values - ArmorStats with all zeros
 */
public class ArmorConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArmorConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings (same as WeaponConfigManager)
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // NBT key for armor mod stats
    private static final String NBT_KEY = "ArmorModStats";

    // Global stats map (per item type)
    private static final Map<Item, ArmorStats> globalArmorStats = new HashMap<>();

    private static Path dataDirectory = null;

    /**
     * Initialize with config directory path.
     * Should be called during mod initialization.
     */
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            LOGGER.error("[ArmorConfig] Failed to create data directory", e);
        }
        load();
    }

    // ========== Stats Retrieval ==========

    /**
     * Gets the final statistics for a specific armor piece.
     * Priority: NBT -> Global -> Default
     */
    public static ArmorStats getStats(ItemStack stack) {
        stack = Objects.requireNonNull(stack);

        // 1. Check if the armor has SPECIFIC modifications (NBT)
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains(NBT_KEY)) {
            return ArmorStats.load(customData.copyTag().getCompound(NBT_KEY));
        }

        // 2. If no specific modifications, use GLOBAL ones
        if (globalArmorStats.containsKey(stack.getItem())) {
            return globalArmorStats.get(stack.getItem());
        }

        // 3. Otherwise return default statistics (all zeros)
        return new ArmorStats();
    }

    /**
     * Get global stats for a specific item type.
     */
    public static ArmorStats getGlobalStats(Item item) {
        return globalArmorStats.getOrDefault(item, new ArmorStats());
    }

    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return globalArmorStats.containsKey(item);
    }

    /**
     * Check if item has specific (NBT) config.
     */
    public static boolean hasSpecificConfig(ItemStack stack) {
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        return customData != null && customData.contains(NBT_KEY);
    }

    // ========== Stats Modification ==========

    /**
     * Set global stats for an item type.
     * Automatically saves to disk.
     */
    public static void setGlobalStats(Item item, ArmorStats stats) {
        globalArmorStats.put(item, stats);
        save(); // AUTO-SAVE on modification
    }

    /**
     * Set specific stats on an item instance (NBT).
     */
    public static void setSpecificStats(ItemStack stack, ArmorStats stats) {
        var customDataType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
        CustomData.update(customDataType, Objects.requireNonNull(stack), tag -> {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            stats.save(statsTag);
            tag.put(NBT_KEY, statsTag);
        });
    }

    /**
     * Remove specific stats from an item (clears NBT).
     */
    public static void clearSpecificStats(ItemStack stack) {
        var customDataType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
        CustomData.update(customDataType, Objects.requireNonNull(stack), tag -> {
            tag.remove(NBT_KEY);
        });
    }

    /**
     * Remove global stats for an item type.
     */
    public static void clearGlobalStats(Item item) {
        globalArmorStats.remove(item);
        save();
    }

    /**
     * Clear all global stats (used for testing).
     */
    public static void clearAllGlobalStats() {
        globalArmorStats.clear();
    }

    // ========== Armor Detection ==========

    /**
     * Check if an item is armor.
     */
    public static boolean isArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
    }

    /**
     * Check if an item is armor by Item reference.
     */
    public static boolean isArmor(Item item) {
        return item instanceof ArmorItem;
    }

    /**
     * Get the equipment slot for an armor item.
     * Returns null if not armor.
     */
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return null;
    }

    // ========== Persistence ==========

    /**
     * Load armor configurations from file.
     */
    public static void load() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path backupFile = dataDirectory.resolve("armor_configs.json.bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, ArmorStats>>(){}.getType();
                Map<String, ArmorStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    globalArmorStats.clear();
                    loaded.forEach((key, stats) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(Objects.requireNonNull(key));
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = Objects.requireNonNull(BuiltInRegistries.ITEM.get(resLoc));
                            globalArmorStats.put(item, stats);
                        } else {
                            LOGGER.warn("[ArmorConfig] Unknown item in config: {}", key);
                        }
                    });
                    LOGGER.info("[ArmorConfig] Loaded {} armor configurations from {}",
                            globalArmorStats.size(), fileToLoad.getFileName());
                }
            } catch (Exception e) {
                LOGGER.error("[ArmorConfig] Failed to load armor configs from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save armor configurations to file.
     */
    public static void save() {
        if (dataDirectory == null) return;

        // Create timestamped backup before saving
        createTimestampedBackup();

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path tempFile = dataDirectory.resolve("armor_configs.json.tmp");
        Path backupFile = dataDirectory.resolve("armor_configs.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, ArmorStats> toSave = new HashMap<>();
                globalArmorStats.forEach((item, stats) -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
                    toSave.put(key.toString(), stats);
                });
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            // Create backup of existing file
            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, configFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            LOGGER.info("[ArmorConfig] Saved {} armor configurations", globalArmorStats.size());

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[ArmorConfig] Failed to save armor configs (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[ArmorConfig] Failed to save armor configs", e);
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    /**
     * Creates a timestamped backup of the current config file.
     */
    private static void createTimestampedBackup() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            Path backupDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            String backupName = "armor_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("[ArmorConfig] Created backup: {}", backupFile);

            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to create backup", e);
        }
    }

    /**
     * Removes old backups keeping only MAX_BACKUPS most recent.
     */
    private static void cleanupOldBackups(Path backupDir) {
        try {
            var backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .toList();

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                LOGGER.debug("[ArmorConfig] Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to cleanup old backups", e);
        }
    }

    /**
     * Restores config from the most recent backup.
     */
    public static boolean restoreFromBackup() {
        if (dataDirectory == null) return false;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path backupDir = dataDirectory.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[ArmorConfig] No backup directory found");
            return false;
        }

        try {
            var latestBackup = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                });

            if (latestBackup.isEmpty()) {
                LOGGER.warn("[ArmorConfig] No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[ArmorConfig] Restored from backup: {}", backup);

            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("[ArmorConfig] Failed to restore from backup", e);
            return false;
        }
    }

    /**
     * Lists available backup files.
     */
    public static Map<String, String> listBackups() {
        Map<String, String> backups = new HashMap<>();
        if (dataDirectory == null) return backups;

        Path backupDir = dataDirectory.resolve("backups");
        if (!Files.exists(backupDir)) return backups;

        try {
            Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .forEach(p -> {
                    try {
                        backups.put(p.getFileName().toString(), Files.getLastModifiedTime(p).toString());
                    } catch (IOException e) {
                        // Skip
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to list backups", e);
        }

        return backups;
    }

    /**
     * Get count of configured armor types.
     */
    public static int getConfiguredCount() {
        return globalArmorStats.size();
    }
}

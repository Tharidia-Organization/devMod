package com.frenkvs.devmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

public class WeaponConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaponConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final net.minecraft.core.component.DataComponentType<CustomData> CUSTOM_DATA =
            Objects.requireNonNull(DataComponents.CUSTOM_DATA);

    // Mappa per le impostazioni GLOBALI (Per tipo di oggetto)
    private static final Map<Item, WeaponStats> globalStats = new HashMap<>();

    private static Path dataDirectory = null;

    /**
     * Initialize with config directory path.
     */
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            LOGGER.error("[WeaponConfig] Failed to create data directory", e);
        }
        load();
    }

    // Ottiene le statistiche finali per un'arma specifica
    public static WeaponStats getStats(ItemStack stack) {
        stack = Objects.requireNonNull(stack);
        // 1. Controlla se l'arma ha modifiche SPECIFICHE (NBT)
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains("WeaponModStats")) {
            return WeaponStats.load(customData.copyTag().getCompound("WeaponModStats"));
        }

        // 2. Se non ha modifiche specifiche, usa quelle GLOBALI
        if (globalStats.containsKey(stack.getItem())) {
            return globalStats.get(stack.getItem());
        }

        // 3. Altrimenti ritorna statistiche base
        return new WeaponStats();
    }

    public static void setGlobalStats(Item item, WeaponStats stats) {
        globalStats.put(item, stats);
        save(); // AUTO-SAVE on modification
    }

    public static void setSpecificStats(ItemStack stack, WeaponStats stats) {
        CustomData.update(Objects.requireNonNull(CUSTOM_DATA), Objects.requireNonNull(stack), tag -> {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            stats.save(statsTag);
            tag.put("WeaponModStats", statsTag);
        });
    }

    /**
     * Ottiene le statistiche globali di default (usate per le labels).
     * Ritorna un WeaponStats con i valori di default.
     */
    public static WeaponStats getGlobalStats() {
        return new WeaponStats();
    }

    /**
     * Get global stats for a specific item type.
     */
    public static WeaponStats getGlobalStats(Item item) {
        return globalStats.getOrDefault(item, new WeaponStats());
    }

    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return globalStats.containsKey(item);
    }

    /**
     * Pulisce tutte le statistiche globali (usato per GameTests).
     */
    public static void clearAllGlobalStats() {
        globalStats.clear();
    }

    // ========== Persistence ==========

    /**
     * Load weapon configurations from file.
     */
    public static void load() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path backupFile = dataDirectory.resolve("weapon_configs.json.bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, WeaponStats>>(){}.getType();
                Map<String, WeaponStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    globalStats.clear();
                    loaded.forEach((key, stats) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(key);
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = BuiltInRegistries.ITEM.get(resLoc);
                            globalStats.put(item, stats);
                        } else {
                            LOGGER.warn("[WeaponConfig] Unknown item in config: {}", key);
                        }
                    });
                    LOGGER.info("[WeaponConfig] Loaded {} weapon configurations from {}",
                            globalStats.size(), fileToLoad.getFileName());
                }
            } catch (Exception e) {
                LOGGER.error("[WeaponConfig] Failed to load weapon configs from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save weapon configurations to file.
     */
    public static void save() {
        if (dataDirectory == null) return;

        // Create timestamped backup before saving
        createTimestampedBackup();

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path tempFile = dataDirectory.resolve("weapon_configs.json.tmp");
        Path backupFile = dataDirectory.resolve("weapon_configs.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, WeaponStats> toSave = new HashMap<>();
                globalStats.forEach((item, stats) -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    toSave.put(key.toString(), stats);
                });
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            // Create backup of existing file
            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, configFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            LOGGER.info("[WeaponConfig] Saved {} weapon configurations", globalStats.size());

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[WeaponConfig] Failed to save weapon configs (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[WeaponConfig] Failed to save weapon configs", e);
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    /**
     * Creates a timestamped backup of the current config file.
     */
    private static void createTimestampedBackup() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            Path backupDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            String backupName = "weapon_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("[WeaponConfig] Created backup: {}", backupFile);

            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to create backup", e);
        }
    }

    /**
     * Removes old backups keeping only MAX_BACKUPS most recent.
     */
    private static void cleanupOldBackups(Path backupDir) {
        try {
            var backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
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
                LOGGER.debug("[WeaponConfig] Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to cleanup old backups", e);
        }
    }

    /**
     * Restores config from the most recent backup.
     */
    public static boolean restoreFromBackup() {
        if (dataDirectory == null) return false;

        Path configFile = dataDirectory.resolve("weapon_configs.json");
        Path backupDir = dataDirectory.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[WeaponConfig] No backup directory found");
            return false;
        }

        try {
            var latestBackup = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                });

            if (latestBackup.isEmpty()) {
                LOGGER.warn("[WeaponConfig] No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[WeaponConfig] Restored from backup: {}", backup);

            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("[WeaponConfig] Failed to restore from backup", e);
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
                .filter(p -> p.getFileName().toString().startsWith("weapon_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .forEach(p -> {
                    try {
                        backups.put(p.getFileName().toString(), Files.getLastModifiedTime(p).toString());
                    } catch (IOException e) {
                        // Skip
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("[WeaponConfig] Failed to list backups", e);
        }

        return backups;
    }
}

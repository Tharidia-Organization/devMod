package com.frenkvs.devmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration manager for usable item stats (cooldowns, use duration, throwable properties).
 * Follows the same pattern as WeaponConfigManager.
 */
public class UsableConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsableConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final net.minecraft.core.component.DataComponentType<CustomData> CUSTOM_DATA =
            Objects.requireNonNull(DataComponents.CUSTOM_DATA);

    // Map for GLOBAL settings (Per item type) - ConcurrentHashMap for thread safety
    private static final Map<Item, UsableStats> globalStats = new ConcurrentHashMap<>();

    private static Path dataDirectory = null;

    /**
     * Initialize with config directory path.
     */
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            LOGGER.error("[UsableConfig] Failed to create data directory", e);
        }
        load();
    }

    /**
     * Gets the final statistics for a specific usable item.
     * Priority: Component > CustomData > Global > Default
     */
    public static UsableStats getStats(ItemStack stack) {
        stack = Objects.requireNonNull(stack);

        // 1a. Check typed data component first (new path)
        var usableComponent = UsableComponents.usableStatsComponent();
        if (usableComponent != null) {
            CompoundTag componentTag = stack.get(usableComponent);
            if (componentTag != null && !componentTag.isEmpty()) {
                return UsableStats.load(componentTag.copy());
            }
        }

        // 1b. Legacy CustomData path
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains("UsableModStats")) {
            CompoundTag legacy = customData.copyTag().getCompound("UsableModStats");
            UsableStats stats = UsableStats.load(legacy);
            // Migrate forward to typed component when missing
            var migrateComponent = UsableComponents.usableStatsComponent();
            if (migrateComponent != null) {
                try {
                    stack.set(migrateComponent, legacy.copy());
                } catch (Exception e) {
                    LOGGER.debug("[UsableConfig] Failed to migrate legacy UsableModStats to component: {}", e.getMessage());
                }
            }
            return stats;
        }

        // 2. If it has no specific modifications, use GLOBAL ones and materialize onto the stack
        if (globalStats.containsKey(stack.getItem())) {
            UsableStats global = globalStats.get(stack.getItem()).copy();
            try {
                setSpecificStats(stack, global);
            } catch (Exception e) {
                LOGGER.debug("[UsableConfig] Failed to materialize global stats onto stack: {}", e.getMessage());
            }
            return global;
        }

        // 3. Otherwise return base statistics
        return new UsableStats();
    }

    public static void setGlobalStats(Item item, UsableStats stats) {
        UsableStats copy = stats.copy();
        globalStats.put(item, copy);
        save(); // AUTO-SAVE on modification
    }

    /**
     * Set global stats without saving to disk.
     * Used for client-side sync from server.
     */
    public static void setGlobalStatsClientOnly(Item item, UsableStats stats) {
        UsableStats copy = stats.copy();
        globalStats.put(item, copy);
    }

    public static void setSpecificStats(ItemStack stack, UsableStats stats) {
        UsableStats copy = stats.copy();
        CustomData.update(Objects.requireNonNull(CUSTOM_DATA), Objects.requireNonNull(stack), tag -> {
            CompoundTag statsTag = new CompoundTag();
            copy.save(statsTag);
            tag.put("UsableModStats", statsTag.copy());
            // Also mirror to typed data component for forward compatibility
            var setComponent = UsableComponents.usableStatsComponent();
            if (setComponent != null) {
                try {
                    stack.set(setComponent, statsTag.copy());
                } catch (Exception e) {
                    LOGGER.warn("[UsableConfig] Failed to apply usable_stats component", e);
                }
            }
        });
    }

    /**
     * Gets the default global statistics.
     */
    public static UsableStats getGlobalStats() {
        return new UsableStats();
    }

    /**
     * Get global stats for a specific item type.
     */
    public static UsableStats getGlobalStats(Item item) {
        return globalStats.getOrDefault(item, new UsableStats());
    }

    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return globalStats.containsKey(item);
    }

    /**
     * Clears all global statistics (used for GameTests).
     */
    public static void clearAllGlobalStats() {
        globalStats.clear();
    }

    /**
     * Get an immutable view of all global usable overrides.
     */
    public static Map<Item, UsableStats> getAllGlobalStats() {
        return Map.copyOf(globalStats);
    }

    // ========== Persistence ==========

    /**
     * Load usable configurations from file.
     */
    public static void load() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("usable_configs.json");
        Path backupFile = dataDirectory.resolve("usable_configs.json.bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, UsableStats>>(){}.getType();
                Map<String, UsableStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    globalStats.clear();
                    loaded.forEach((key, stats) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(key);
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = BuiltInRegistries.ITEM.get(resLoc);
                            globalStats.put(item, stats);
                        } else {
                            LOGGER.warn("[UsableConfig] Unknown item in config: {}", key);
                        }
                    });
                    LOGGER.info("[UsableConfig] Loaded {} usable configurations from {}",
                            globalStats.size(), fileToLoad.getFileName());
                }
            } catch (Exception e) {
                LOGGER.error("[UsableConfig] Failed to load usable configs from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save usable configurations to file.
     */
    public static void save() {
        if (dataDirectory == null) return;

        // Create timestamped backup before saving
        createTimestampedBackup();

        Path configFile = dataDirectory.resolve("usable_configs.json");
        Path tempFile = dataDirectory.resolve("usable_configs.json.tmp");
        Path backupFile = dataDirectory.resolve("usable_configs.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, UsableStats> toSave = new HashMap<>();
                globalStats.forEach((item, stats) -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
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

            LOGGER.info("[UsableConfig] Saved {} usable configurations", globalStats.size());

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[UsableConfig] Failed to save usable configs (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[UsableConfig] Failed to save usable configs", e);
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    private static void createTimestampedBackup() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("usable_configs.json");
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            Path backupDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            String backupName = "usable_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFilePath = backupDir.resolve(backupName);

            Files.copy(configFile, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("[UsableConfig] Created backup: {}", backupFilePath);

            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("[UsableConfig] Failed to create backup", e);
        }
    }

    private static void cleanupOldBackups(Path backupDir) {
        try {
            var backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("usable_configs_")
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
                LOGGER.debug("[UsableConfig] Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("[UsableConfig] Failed to cleanup old backups", e);
        }
    }

    /**
     * Restores config from the most recent backup.
     */
    public static boolean restoreFromBackup() {
        if (dataDirectory == null) return false;

        Path configFile = dataDirectory.resolve("usable_configs.json");
        Path backupDir = dataDirectory.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[UsableConfig] No backup directory found");
            return false;
        }

        try {
            var latestBackup = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("usable_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                });

            if (latestBackup.isEmpty()) {
                LOGGER.warn("[UsableConfig] No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[UsableConfig] Restored from backup: {}", backup);

            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("[UsableConfig] Failed to restore from backup", e);
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
                .filter(p -> p.getFileName().toString().startsWith("usable_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .forEach(p -> {
                    try {
                        backups.put(p.getFileName().toString(), Files.getLastModifiedTime(p).toString());
                    } catch (IOException e) {
                        // Skip
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("[UsableConfig] Failed to list backups", e);
        }

        return backups;
    }
}

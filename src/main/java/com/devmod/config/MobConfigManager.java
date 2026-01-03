package com.devmod.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import com.devmod.util.ConfigPaths;

public class MobConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Lock for thread-safe config modifications
    private static final ReentrantReadWriteLock CONFIG_LOCK = new ReentrantReadWriteLock();

    // A map that links the entity TYPE (e.g. Zombie) to its SAVED STATISTICS
    private static final Map<EntityType<?>, SavedStats> globalConfigs = new ConcurrentHashMap<>();

    // Simple class to hold data in memory
    public record SavedStats(double range, double damage, double maxHealth, double armor, double attackReach) {
        // Constructor for backwards compatibility (without attackReach)
        public SavedStats(double range, double damage, double maxHealth, double armor) {
            this(range, damage, maxHealth, armor, 2.0); // Default attack reach
        }
    }

    // Save a global configuration
    public static void setGlobalStats(EntityType<?> type, double range, double damage, double maxHealth, double armor) {
        setGlobalStats(type, range, damage, maxHealth, armor, 2.0); // Default attack reach
    }

    // Save a global configuration with attack reach
    public static void setGlobalStats(EntityType<?> type, double range, double damage, double maxHealth, double armor, double attackReach) {
        CONFIG_LOCK.writeLock().lock();
        try {
            globalConfigs.put(type, new SavedStats(range, damage, maxHealth, armor, attackReach));
            // Auto-save after each modification (while still holding lock)
            saveInternal();
        } finally {
            CONFIG_LOCK.writeLock().unlock();
        }
    }

    // Retrieve the configuration (or null if it doesn't exist)
    public static SavedStats getGlobalStats(EntityType<?> type) {
        return globalConfigs.get(type);
    }

    // Check if we have modifications for this type
    public static boolean hasConfig(EntityType<?> type) {
        return globalConfigs.containsKey(type);
    }

    /**
     * Saves configurations to JSON file (thread-safe wrapper)
     */
    public static void save() {
        CONFIG_LOCK.readLock().lock();
        try {
            saveInternal();
        } finally {
            CONFIG_LOCK.readLock().unlock();
        }
    }

    /**
     * Internal save method - caller must hold appropriate lock
     */
    private static void saveInternal() {
        Path file = ConfigPaths.getMobConfigsFile();

        // Create backup before saving (prevents data loss on corruption)
        createBackup();

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Convert EntityType to String (ResourceLocation) for serialization
            Map<String, SavedStats> serializable = new HashMap<>();
            for (Map.Entry<EntityType<?>, SavedStats> entry : globalConfigs.entrySet()) {
                EntityType<?> entityType = entry.getKey();
                if (entityType == null) continue;
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                if (key != null) {
                    serializable.put(key.toString(), entry.getValue());
                }
            }

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(serializable, writer);
                LOGGER.info("Saved {} mob configurations to {}", serializable.size(), file);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save mob configurations", e);
        }
    }

    /**
     * Loads configurations from JSON file
     */
    public static void load() {
        Path file = ConfigPaths.getMobConfigsFile();

        if (!Files.exists(file)) {
            LOGGER.info("No mob configurations file found, starting fresh");
            return;
        }

        CONFIG_LOCK.writeLock().lock();
        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<Map<String, SavedStats>>() {}.getType();
            Map<String, SavedStats> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                globalConfigs.clear();
                for (Map.Entry<String, SavedStats> entry : loaded.entrySet()) {
                    String keyStr = entry.getKey();
                    if (keyStr == null || keyStr.isEmpty()) continue;
                    ResourceLocation resLoc = ResourceLocation.tryParse(keyStr);
                    if (resLoc != null && BuiltInRegistries.ENTITY_TYPE.containsKey(resLoc)) {
                        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(resLoc);
                        if (entityType != null) {
                            globalConfigs.put(entityType, entry.getValue());
                        }
                    }
                }
                LOGGER.info("Loaded {} mob configurations from {}", globalConfigs.size(), file);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load mob configurations", e);
        } finally {
            CONFIG_LOCK.writeLock().unlock();
        }
    }

    /**
     * Clears all global configurations (used for GameTests).
     * Does not save to disk to avoid overwriting user configurations.
     */
    public static void clearAllGlobalStats() {
        CONFIG_LOCK.writeLock().lock();
        try {
            globalConfigs.clear();
        } finally {
            CONFIG_LOCK.writeLock().unlock();
        }
        LOGGER.debug("Cleared all mob configurations (in-memory only)");
    }

    /**
     * Clears all global configurations and persists the empty config to disk.
     */
    public static void clearAllGlobalStatsAndSave() {
        CONFIG_LOCK.writeLock().lock();
        try {
            globalConfigs.clear();
            saveInternal();
        } finally {
            CONFIG_LOCK.writeLock().unlock();
        }
        LOGGER.info("Cleared all mob configurations and saved to disk");
    }

    /**
     * Returns the number of configured mob types.
     */
    public static int getGlobalConfigCount() {
        CONFIG_LOCK.readLock().lock();
        try {
            return globalConfigs.size();
        } finally {
            CONFIG_LOCK.readLock().unlock();
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    /**
     * Creates a timestamped backup of the current config file.
     * Called automatically before each save.
     */
    private static void createBackup() {
        Path configFile = ConfigPaths.getMobConfigsFile();
        if (!Files.exists(configFile)) {
            return; // Nothing to backup
        }

        try {
            Path parent = configFile.getParent();
            if (parent == null) {
                LOGGER.warn("Mob config path has no parent directory: {}", configFile);
                return;
            }
            Path backupDir = parent.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(BACKUP_DATE_FORMAT);
            String backupName = "mob_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("Created config backup: {}", backupFile);

            // Cleanup old backups
            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create config backup", e);
        }
    }

    /**
     * Removes old backups keeping only MAX_BACKUPS most recent.
     */
    private static void cleanupOldBackups(Path backupDir) {
        try {
            List<Path> backups;
            try (var backupStream = Files.list(backupDir)) {
                backups = backupStream
                    .filter(p -> p.getFileName().toString().startsWith("mob_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            }

            // Delete old backups beyond MAX_BACKUPS
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                LOGGER.debug("Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to cleanup old backups", e);
        }
    }

    /**
     * Attempts to restore config from the most recent backup.
     * Returns true if restoration was successful.
     */
    public static boolean restoreFromBackup() {
        Path configFile = ConfigPaths.getMobConfigsFile();
        Path parent = configFile.getParent();
        if (parent == null) {
            LOGGER.warn("Mob config path has no parent directory: {}", configFile);
            return false;
        }
        Path backupDir = parent.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("No backup directory found");
            return false;
        }

        try {
            Optional<Path> latestBackup;
            try (var backupStream = Files.list(backupDir)) {
                latestBackup = backupStream
                    .filter(p -> p.getFileName().toString().startsWith("mob_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .max((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                        } catch (IOException e) {
                            return 0;
                        }
                    });
            }

            if (latestBackup.isEmpty()) {
                LOGGER.warn("No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Restored config from backup: {}", backup);

            // Reload after restore
            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to restore from backup", e);
            return false;
        }
    }

    /**
     * Lists available backup files with their timestamps.
     * Returns a map of backup filename -> last modified time.
     */
    public static Map<String, String> listBackups() {
        Map<String, String> backups = new HashMap<>();
        Path configFile = ConfigPaths.getMobConfigsFile();
        Path parent = configFile.getParent();
        if (parent == null) {
            LOGGER.warn("Mob config path has no parent directory: {}", configFile);
            return backups;
        }
        Path backupDir = parent.resolve("backups");

        if (!Files.exists(backupDir)) {
            return backups;
        }

        try {
            try (var backupStream = Files.list(backupDir)) {
                backupStream
                    .filter(p -> p.getFileName().toString().startsWith("mob_configs_")
                              && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                    .forEach(p -> {
                        try {
                            String name = p.getFileName().toString();
                            String time = Files.getLastModifiedTime(p).toString();
                            backups.put(name, time);
                        } catch (IOException e) {
                            // Skip this file
                        }
                    });
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list backups", e);
        }

        return backups;
    }

    /**
     * Restores from a specific backup file by name.
     */
    public static boolean restoreFromBackup(String backupName) {
        Path configFile = ConfigPaths.getMobConfigsFile();
        Path parent = configFile.getParent();
        if (parent == null) {
            LOGGER.warn("Mob config path has no parent directory: {}", configFile);
            return false;
        }
        Path backupDir = parent.resolve("backups");
        Path backupFile = backupDir.resolve(backupName);

        if (!Files.exists(backupFile)) {
            LOGGER.warn("Backup file not found: {}", backupName);
            return false;
        }

        try {
            Files.copy(backupFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Restored config from backup: {}", backupName);
            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to restore from backup: {}", backupName, e);
            return false;
        }
    }
}

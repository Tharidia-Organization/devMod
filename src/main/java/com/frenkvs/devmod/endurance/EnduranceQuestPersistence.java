package com.frenkvs.devmod.endurance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence of player quest statistics.
 * Manages loading, saving, and updating player stats data.
 */
public class EnduranceQuestPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, EnduranceQuestManager.PlayerQuestStats> playerStats = new ConcurrentHashMap<>();
    private Path dataDirectory;

    public EnduranceQuestPersistence() {}

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize the persistence system with the data directory.
     */
    public void initialize(Path dataDir) {
        this.dataDirectory = dataDir;
        loadPlayerStats();
    }

    /**
     * Get the data directory.
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }

    // ═══════════════════════════════════════════════════════════════
    // PLAYER STATS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get player statistics, creating new ones if not found.
     */
    public EnduranceQuestManager.PlayerQuestStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, id -> new EnduranceQuestManager.PlayerQuestStats(id));
    }

    /**
     * Update player stats after a quest attempt.
     */
    public void updatePlayerStats(UUID playerId, EnduranceQuest quest, boolean completed) {
        EnduranceQuestManager.PlayerQuestStats stats = getPlayerStats(playerId);
        stats.recordQuestAttempt(quest.getMobId(), completed, quest.getPointsEarnedThisSession(),
            quest.getCurrentWave(), quest.getSessionDuration(), quest.getMobsKilledThisSession());
        savePlayerStats();
    }

    /**
     * Clear all player stats.
     */
    public void clearAllStats() {
        playerStats.clear();
    }

    /**
     * Get all player stats (read-only).
     */
    public Map<UUID, EnduranceQuestManager.PlayerQuestStats> getAllStats() {
        return new HashMap<>(playerStats);
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD / SAVE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load player stats from disk.
     */
    public void loadPlayerStats() {
        if (dataDirectory == null) return;

        Path statsFile = dataDirectory.resolve("player_stats.json");
        Path backupFile = dataDirectory.resolve("player_stats.json.bak");

        // Try main file first, then backup
        Path fileToLoad = Files.exists(statsFile) ? statsFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, EnduranceQuestManager.PlayerQuestStats>>(){}.getType();
                Map<String, EnduranceQuestManager.PlayerQuestStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    loaded.forEach((key, value) -> {
                        try {
                            playerStats.put(UUID.fromString(key), value);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("[EnduranceQuest] Invalid UUID in stats file: {}", key);
                        }
                    });
                    LOGGER.info("[EnduranceQuest] Loaded stats for {} players from {}",
                            playerStats.size(), fileToLoad.getFileName());
                } else {
                    LOGGER.warn("[EnduranceQuest] Stats file was empty or corrupted: {}", fileToLoad);
                }
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Failed to load player stats from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save player stats to disk.
     */
    public void savePlayerStats() {
        if (dataDirectory == null) return;

        Path statsFile = dataDirectory.resolve("player_stats.json");
        Path tempFile = dataDirectory.resolve("player_stats.json.tmp");
        Path backupFile = dataDirectory.resolve("player_stats.json.bak");

        try {
            // Ensure directory exists
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, EnduranceQuestManager.PlayerQuestStats> toSave = new HashMap<>();
                playerStats.forEach((uuid, stats) -> toSave.put(uuid.toString(), stats));
                GSON.toJson(toSave, writer);
                writer.flush(); // CRITICAL: Force flush before close
            }

            // Create backup of existing file
            if (Files.exists(statsFile)) {
                Files.copy(statsFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, statsFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Fallback for filesystems that don't support atomic move
            try {
                Files.move(tempFile, statsFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[EnduranceQuest] Failed to save player stats (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[EnduranceQuest] Failed to save player stats", e);
        }
    }

    /**
     * Delete all stats files.
     */
    public void deleteAllStatsFiles() {
        if (dataDirectory == null) return;

        try {
            Path statsFile = dataDirectory.resolve("player_stats.json");
            Path backupFile = dataDirectory.resolve("player_stats.json.bak");
            Path tempFile = dataDirectory.resolve("player_stats.json.tmp");

            Files.deleteIfExists(statsFile);
            Files.deleteIfExists(backupFile);
            Files.deleteIfExists(tempFile);

            LOGGER.info("[EnduranceQuest] All player stats files deleted");
        } catch (IOException e) {
            LOGGER.error("[EnduranceQuest] Failed to delete player stats files", e);
        }
    }
}

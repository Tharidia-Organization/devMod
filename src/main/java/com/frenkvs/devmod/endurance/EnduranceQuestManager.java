package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.util.I18n;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.frenkvs.devmod.telemetry.TelemetryService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all Endurance Quest operations.
 * Handles quest creation, player sessions, persistence, and coordination.
 */
public class EnduranceQuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final EnduranceQuestManager INSTANCE = new EnduranceQuestManager();

    // Active quests per player (player UUID -> active quest)
    private final Map<UUID, ActiveQuestSession> activeSessions = new ConcurrentHashMap<>();

    // Quest templates (mob ID -> quest template with best records)
    private final Map<ResourceLocation, EnduranceQuest> questTemplates = new ConcurrentHashMap<>();

    // Player statistics (player UUID -> stats)
    private final Map<UUID, PlayerQuestStats> playerStats = new ConcurrentHashMap<>();

    // Arena manager reference
    private ArenaManager arenaManager;

    // Data directory
    private Path dataDirectory;

    private boolean initialized = false;

    private EnduranceQuestManager() {}

    /**
     * Initialize the manager. Should be called during server start.
     */
    public void initialize(Path configDir) {
        if (initialized) return;

        // configDir is already config/devmod/, so just add endurance_quests
        this.dataDirectory = configDir.resolve("endurance_quests");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[EnduranceQuest] Failed to create data directory", e);
        }

        // Initialize the registry first
        EnduranceQuestRegistry.INSTANCE.initialize();

        // Create quest templates for all registered mobs
        for (EnduranceQuestRegistry.MobQuestConfig mobConfig : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            EnduranceQuest template = new EnduranceQuest(mobConfig);
            questTemplates.put(mobConfig.mobId, template);
        }

        // Load persisted data
        loadPlayerStats();

        // Initialize arena manager
        this.arenaManager = new ArenaManager();

        // Initialize reward system
        RewardSystem.INSTANCE.initialize(configDir);

        // Initialize gamification system (leaderboards, badges, challenges)
        GamificationManager.INSTANCE.initialize(configDir);

        // Initialize analytics system for session tracking
        EnduranceAnalytics.INSTANCE.initialize(configDir);

        initialized = true;
        LOGGER.info("[EnduranceQuest] Manager initialized with {} quest types", questTemplates.size());
    }

    /**
     * Shutdown the manager. Should be called during server stop.
     * Cleans up all active sessions and saves data.
     *
     * IMPROVEMENT: Award partial rewards to players with active quests
     * so they don't lose all progress on server shutdown.
     */
    public void shutdown() {
        if (!initialized) return;

        LOGGER.info("[EnduranceQuest] Shutting down with {} active sessions", activeSessions.size());

        // Force-end all active sessions with partial rewards
        for (ActiveQuestSession session : activeSessions.values()) {
            try {
                EnduranceQuest quest = session.quest;
                UUID playerId = session.getPlayerId();

                // Award partial tokens based on waves completed (50% of normal rate)
                int wavesCompleted = quest.getCurrentWave();
                int partialTokens = wavesCompleted * 15; // ~50% of normal wave rewards

                if (partialTokens > 0) {
                    RewardSystem.PlayerWallet wallet = RewardSystem.INSTANCE.getWallet(playerId);
                    // SAFETY: Check wallet is not null before using
                    if (wallet != null) {
                        wallet.addCurrency(RewardSystem.Currency.TOKENS, partialTokens);
                        LOGGER.info("[EnduranceQuest] Awarded {} partial tokens to player {} (completed {} waves before shutdown)",
                            partialTokens, playerId, wavesCompleted);
                    } else {
                        LOGGER.warn("[EnduranceQuest] Could not find wallet for player {}", playerId);
                    }
                }

                quest.fail(true); // Mark as abandoned
                if (arenaManager != null) {
                    arenaManager.destroyArena(session.arena);
                }
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Error cleaning up session for player {}", session.getPlayerId(), e);
            }
        }
        activeSessions.clear();

        // Save all player stats (includes partial rewards)
        savePlayerStats();

        // Save reward system data
        RewardSystem.INSTANCE.saveAll();

        // Save gamification data (leaderboards, badges, challenges)
        GamificationManager.INSTANCE.saveAll();

        // Clear templates (will be rebuilt on next init)
        questTemplates.clear();
        playerStats.clear();

        initialized = false;
        LOGGER.info("[EnduranceQuest] Shutdown complete");
    }

    /**
     * Check if manager is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ========== Quest Management ==========

    /**
     * Start a new quest for a player.
     */
    public StartQuestResult startQuest(ServerPlayer player, ResourceLocation mobId, QuestSettings settings) {
        UUID playerId = player.getUUID();

        // Check if player already has an active quest
        if (activeSessions.containsKey(playerId)) {
            return new StartQuestResult(false, I18n.translate("devmod.endurance.active_quest").getString(), null);
        }

        // Get quest template
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return new StartQuestResult(false, "Unknown quest type: " + mobId, null);
        }

        // Create new quest instance
        EnduranceQuest quest = new EnduranceQuest(template.getMobConfig());
        quest.setTotalWaves(settings.totalWaves);
        quest.setEndlessMode(settings.endlessMode);

        // Create arena
        ServerLevel level = player.serverLevel();
        ArenaManager.Arena arena = arenaManager.createArena(level, player.blockPosition(), settings.arenaSize);

        if (arena == null) {
            return new StartQuestResult(false, "Failed to create arena", null);
        }

        // Start the quest
        quest.start(arena.getId());

        // Create session (will store original inventory and gamemode)
        ActiveQuestSession session = new ActiveQuestSession(playerId, quest, arena, System.currentTimeMillis());
        activeSessions.put(playerId, session);

        // Prepare player for quest: save state, set survival, clear inventory, give kit
        preparePlayerForQuest(player, session);

        // Teleport player to arena center
        arenaManager.teleportToArena(player, arena);

        // Initialize all subsystems (Combo, Mutator, Perk, Reward) BEFORE starting wave
        EnduranceEventHandler.onQuestStart(player, session);

        // INTEGRATION: Start telemetry dungeon session for tracking
        String dungeonId = "endurance_" + mobId.toString().replace(":", "_");
        TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);

        // Start the first wave
        WaveManager.INSTANCE.startWave(session);

        // Notify subsystems that wave 1 has started
        EnduranceEventHandler.onWaveStart(player, session, quest.getCurrentWave());

        LOGGER.info("[EnduranceQuest] Player {} started quest: {}", player.getName().getString(), quest.getDisplayName());

        return new StartQuestResult(true, "Quest started!", session);
    }

    /**
     * Get active quest session for a player.
     */
    public Optional<ActiveQuestSession> getActiveSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Get active quest session for a player.
     */
    public Optional<ActiveQuestSession> getActiveSession(Player player) {
        return getActiveSession(player.getUUID());
    }

    /**
     * Abandon current quest.
     */
    public void abandonQuest(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ActiveQuestSession session = activeSessions.remove(playerId);

        if (session != null) {
            session.quest.fail(true);

            // Restore player's original state (inventory, game mode)
            restorePlayerAfterQuest(player, session);

            // Cleanup arena
            arenaManager.destroyArena(session.arena);

            // Cleanup subsystems and award partial rewards
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session
            TelemetryService.INSTANCE.endDungeonSession(player, "abandoned");

            // Update stats
            updatePlayerStats(playerId, session.quest, false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            // Notify player
            player.sendSystemMessage(I18n.translate("devmod.endurance.quest_abandoned",
                session.quest.getCurrentWave(), session.quest.getPointsEarnedThisSession())
                .withStyle(ChatFormatting.YELLOW));

            LOGGER.info("[EnduranceQuest] Player {} abandoned quest: {}",
                player.getName().getString(), session.quest.getDisplayName());
        }
    }

    /**
     * Handle player death during quest.
     */
    public void handlePlayerDeath(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null) {
            session.quest.fail(false);

            // Don't remove session immediately - allow respawn option
            session.setAwaitingRespawnChoice(true);

            // Send death screen to client (primary UI)
            com.frenkvs.devmod.NetworkHandler.sendQuestDeathScreen(
                player,
                session.quest.getCurrentWave(),
                session.quest.getTotalWaves(),
                session.quest.isEndlessMode(),
                session.quest.getPointsEarnedThisSession(),
                session.quest.getDeathsThisSession(),
                100 // Respawn cost
            );

            // Also send chat messages as fallback
            player.sendSystemMessage(I18n.translate("devmod.death.divider")
                .withStyle(ChatFormatting.DARK_RED));
            player.sendSystemMessage(I18n.translate("devmod.endurance.you_died_icon")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.death.wave_points",
                session.quest.getCurrentWave(), session.quest.getPointsEarnedThisSession())
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.death.keybind_hint")
                .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(I18n.translate("devmod.death.divider")
                .withStyle(ChatFormatting.DARK_RED));

            LOGGER.info("[EnduranceQuest] Player {} died in quest: {} at wave {}",
                player.getName().getString(), session.quest.getDisplayName(), session.quest.getCurrentWave());
        }
    }

    /**
     * Handle player choosing to continue after death (with penalty) or give up.
     */
    public void handleRespawnChoice(ServerPlayer player, boolean continueQuest) {
        UUID playerId = player.getUUID();
        ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null && session.isAwaitingRespawnChoice()) {
            if (continueQuest) {
                // Continue from current wave with death penalty
                session.quest.continueAfterDeath();
                session.setAwaitingRespawnChoice(false);

                // Teleport back to arena
                arenaManager.teleportToArena(player, session.arena);

                // Restart the wave (respawn mobs)
                WaveManager.INSTANCE.startWave(session);

                // Notify subsystems
                EnduranceEventHandler.onWaveStart(player, session, session.quest.getCurrentWave());

                // Notify player of penalty
                player.sendSystemMessage(I18n.translate("devmod.endurance.respawned_penalty", session.quest.getDeathsThisSession())
                    .withStyle(net.minecraft.ChatFormatting.RED));

                LOGGER.info("[EnduranceQuest] Player {} continuing quest after death at wave {}",
                    player.getName().getString(), session.quest.getCurrentWave());
            } else {
                // End quest
                activeSessions.remove(playerId);

                // Restore player's original state (inventory, game mode)
                restorePlayerAfterQuest(player, session);

                arenaManager.destroyArena(session.arena);

                // Cleanup subsystems and award partial rewards
                EnduranceEventHandler.onQuestEnd(player, session, false);

                // INTEGRATION: End telemetry dungeon session
                TelemetryService.INSTANCE.endDungeonSession(player, "death_give_up");

                updatePlayerStats(playerId, session.quest, false);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                LOGGER.info("[EnduranceQuest] Player {} gave up after death", player.getName().getString());
            }
        }
    }

    /**
     * Complete current wave.
     */
    public void completeWave(ServerPlayer player) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.quest.getState() == EnduranceQuestState.IN_PROGRESS) {
            session.quest.completeWave();

            if (session.quest.getState() == EnduranceQuestState.COMPLETED) {
                // Quest fully completed!
                activeSessions.remove(player.getUUID());

                // Restore player's original state (inventory, game mode)
                restorePlayerAfterQuest(player, session);

                arenaManager.destroyArena(session.arena);

                // Cleanup subsystems and award full rewards
                EnduranceEventHandler.onQuestEnd(player, session, true);

                // INTEGRATION: End telemetry dungeon session with success
                TelemetryService.INSTANCE.endDungeonSession(player, "completed");

                updatePlayerStats(player.getUUID(), session.quest, true);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                LOGGER.info("[EnduranceQuest] Player {} COMPLETED quest: {}!",
                    player.getName().getString(), session.quest.getDisplayName());
            }
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.quest.getState() == EnduranceQuestState.WAVE_COMPLETE) {
            session.quest.continueToNextWave();
            session.resetWaveKills();

            // Start spawning mobs for the new wave
            WaveManager.INSTANCE.startWave(session);

            // Notify subsystems that new wave has started
            EnduranceEventHandler.onWaveStart(player, session, session.quest.getCurrentWave());

            LOGGER.info("[EnduranceQuest] Player {} starting wave {}",
                player.getName().getString(), session.quest.getCurrentWave());
        }
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ActiveQuestSession session = activeSessions.remove(playerId);

        if (session != null && session.quest.getState() == EnduranceQuestState.WAVE_COMPLETE) {
            // Restore player's original state (inventory, game mode)
            restorePlayerAfterQuest(player, session);

            // Partial completion - save progress
            arenaManager.destroyArena(session.arena);

            // Cleanup subsystems and award partial rewards for completed waves
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session with checkpoint exit
            TelemetryService.INSTANCE.endDungeonSession(player, "checkpoint_exit");

            updatePlayerStats(playerId, session.quest, false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint (wave {})",
                player.getName().getString(), session.quest.getCurrentWave());
        }
    }

    // ========== Combat Events ==========

    /**
     * Record a mob kill by the player.
     */
    public void recordKill(ServerPlayer player, ResourceLocation killedMobId) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.quest.getMobId().equals(killedMobId)) {
            session.quest.recordKill();
            session.incrementKillCount();
        }
    }

    /**
     * Record damage dealt by player.
     */
    public void recordDamageDealt(ServerPlayer player, float damage) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.quest.recordDamageDealt(damage);
        }
    }

    /**
     * Record damage taken by player.
     */
    public void recordDamageTaken(ServerPlayer player, float damage) {
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.quest.recordDamageTaken(damage);
        }
    }

    // ========== Query Methods ==========

    /**
     * Get all available quest types.
     */
    public Collection<EnduranceQuest> getAllQuestTemplates() {
        return Collections.unmodifiableCollection(questTemplates.values());
    }

    /**
     * Get quest template by mob ID.
     */
    public Optional<EnduranceQuest> getQuestTemplate(ResourceLocation mobId) {
        return Optional.ofNullable(questTemplates.get(mobId));
    }

    /**
     * Get player statistics.
     */
    public PlayerQuestStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, id -> new PlayerQuestStats(id));
    }

    /**
     * Check if player is in a quest.
     */
    public boolean isPlayerInQuest(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get all active sessions (for sync purposes).
     */
    public Map<UUID, ActiveQuestSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    // ========== Persistence ==========

    private void updatePlayerStats(UUID playerId, EnduranceQuest quest, boolean completed) {
        PlayerQuestStats stats = getPlayerStats(playerId);
        stats.recordQuestAttempt(quest.getMobId(), completed, quest.getPointsEarnedThisSession(),
            quest.getCurrentWave(), quest.getSessionDuration());
        savePlayerStats();
    }

    private void loadPlayerStats() {
        Path statsFile = dataDirectory.resolve("player_stats.json");
        Path backupFile = dataDirectory.resolve("player_stats.json.bak");

        // Try main file first, then backup
        Path fileToLoad = Files.exists(statsFile) ? statsFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, java.nio.charset.StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, PlayerQuestStats>>(){}.getType();
                Map<String, PlayerQuestStats> loaded = GSON.fromJson(reader, type);
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

    private void savePlayerStats() {
        if (dataDirectory == null) return;

        Path statsFile = dataDirectory.resolve("player_stats.json");
        Path tempFile = dataDirectory.resolve("player_stats.json.tmp");
        Path backupFile = dataDirectory.resolve("player_stats.json.bak");

        try {
            // Ensure directory exists
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, java.nio.charset.StandardCharsets.UTF_8)) {
                Map<String, PlayerQuestStats> toSave = new HashMap<>();
                playerStats.forEach((uuid, stats) -> toSave.put(uuid.toString(), stats));
                GSON.toJson(toSave, writer);
                writer.flush(); // CRITICAL: Force flush before close
            }

            // Create backup of existing file
            if (Files.exists(statsFile)) {
                Files.copy(statsFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, statsFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Fallback for filesystems that don't support atomic move
            try {
                Files.move(tempFile, statsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[EnduranceQuest] Failed to save player stats (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[EnduranceQuest] Failed to save player stats", e);
        }
    }

    // ========== Player State Management ==========

    /**
     * Prepare a player for the quest: save current state, set survival mode,
     * clear inventory, and give a starter kit.
     */
    private void preparePlayerForQuest(ServerPlayer player, ActiveQuestSession session) {
        // Save original game mode
        session.setOriginalGameMode(player.gameMode.getGameModeForPlayer());

        // Save inventory (main inventory, armor, offhand)
        ListTag inventoryTag = new ListTag();
        player.getInventory().save(inventoryTag);
        session.setSavedInventory(inventoryTag);

        // Clear the player's inventory completely
        player.getInventory().clearContent();

        // Set to survival mode
        player.setGameMode(GameType.SURVIVAL);

        // Give starter kit
        giveStarterKit(player);

        // Heal player to full
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);

        LOGGER.info("[EnduranceQuest] Prepared player {} for quest (saved {} inventory slots, was in {} mode)",
            player.getName().getString(), inventoryTag.size(), session.getOriginalGameMode());
    }

    /**
     * Give the player a starter kit for the endurance quest.
     */
    private void giveStarterKit(ServerPlayer player) {
        var inventory = player.getInventory();

        // Iron Sword (main weapon)
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        inventory.add(sword);

        // Bow + Arrows (ranged option)
        ItemStack bow = new ItemStack(Items.BOW);
        inventory.add(bow);
        inventory.add(new ItemStack(Items.ARROW, 32));

        // Shield (defense)
        inventory.add(new ItemStack(Items.SHIELD));

        // Basic armor set (iron)
        player.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));      // Head slot
        player.getInventory().armor.set(2, new ItemStack(Items.IRON_CHESTPLATE));  // Chest slot
        player.getInventory().armor.set(1, new ItemStack(Items.IRON_LEGGINGS));    // Legs slot
        player.getInventory().armor.set(0, new ItemStack(Items.IRON_BOOTS));       // Feet slot

        // Food (golden apples for emergency healing)
        inventory.add(new ItemStack(Items.GOLDEN_APPLE, 3));
        inventory.add(new ItemStack(Items.COOKED_BEEF, 16));

        // Utility items
        inventory.add(new ItemStack(Items.TORCH, 16));

        LOGGER.debug("[EnduranceQuest] Gave starter kit to {}", player.getName().getString());
    }

    /**
     * Restore a player's original state after the quest ends.
     */
    private void restorePlayerAfterQuest(ServerPlayer player, ActiveQuestSession session) {
        // Clear quest inventory
        player.getInventory().clearContent();

        // Restore original inventory
        ListTag savedInventory = session.getSavedInventory();
        if (savedInventory != null && !savedInventory.isEmpty()) {
            player.getInventory().load(savedInventory);
        }

        // Restore original game mode
        GameType originalMode = session.getOriginalGameMode();
        if (originalMode != null) {
            player.setGameMode(originalMode);
        }

        // Heal player
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);

        LOGGER.info("[EnduranceQuest] Restored player {} state (game mode: {})",
            player.getName().getString(), originalMode);
    }

    // ========== Inner Classes ==========

    /**
     * Settings for starting a quest.
     */
    public static class QuestSettings {
        public int totalWaves = 10;
        public boolean endlessMode = false;
        public int arenaSize = 64; // blocks (4 chunks)

        public QuestSettings() {}

        public QuestSettings waves(int waves) {
            this.totalWaves = waves;
            return this;
        }

        public QuestSettings endless() {
            this.endlessMode = true;
            return this;
        }

        public QuestSettings arenaSize(int size) {
            this.arenaSize = size;
            return this;
        }
    }

    /**
     * Result of starting a quest.
     */
    public record StartQuestResult(boolean success, String message, ActiveQuestSession session) {}

    /**
     * Active quest session for a player.
     */
    public static class ActiveQuestSession {
        private final UUID playerId;
        private final EnduranceQuest quest;
        private final ArenaManager.Arena arena;
        private final long startTime;
        private int killsInCurrentWave = 0;
        private boolean awaitingRespawnChoice = false;

        // Saved player state (to restore after quest)
        private GameType originalGameMode;
        private ListTag savedInventory;
        private ListTag savedArmor;
        private ListTag savedOffhand;

        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaManager.Arena arena, long startTime) {
            this.playerId = playerId;
            this.quest = quest;
            this.arena = arena;
            this.startTime = startTime;
        }

        public UUID getPlayerId() { return playerId; }
        public EnduranceQuest getQuest() { return quest; }
        public ArenaManager.Arena getArena() { return arena; }
        public long getStartTime() { return startTime; }
        public int getKillsInCurrentWave() { return killsInCurrentWave; }
        public boolean isAwaitingRespawnChoice() { return awaitingRespawnChoice; }

        public void setAwaitingRespawnChoice(boolean awaiting) {
            this.awaitingRespawnChoice = awaiting;
        }

        public void incrementKillCount() {
            killsInCurrentWave++;
        }

        public void resetWaveKills() {
            killsInCurrentWave = 0;
        }

        public boolean isWaveComplete() {
            return killsInCurrentWave >= quest.getCurrentWaveMobCount();
        }

        // Saved state getters/setters
        public GameType getOriginalGameMode() { return originalGameMode; }
        public void setOriginalGameMode(GameType mode) { this.originalGameMode = mode; }
        public ListTag getSavedInventory() { return savedInventory; }
        public void setSavedInventory(ListTag inv) { this.savedInventory = inv; }
        public ListTag getSavedArmor() { return savedArmor; }
        public void setSavedArmor(ListTag armor) { this.savedArmor = armor; }
        public ListTag getSavedOffhand() { return savedOffhand; }
        public void setSavedOffhand(ListTag offhand) { this.savedOffhand = offhand; }
    }

    /**
     * Persistent player statistics.
     */
    public static class PlayerQuestStats {
        private final UUID playerId;
        private int totalQuestsAttempted = 0;
        private int totalQuestsCompleted = 0;
        private int totalPointsEarned = 0;
        private int totalMobsKilled = 0;
        private long totalPlayTime = 0; // milliseconds
        private final Map<String, MobQuestRecord> mobRecords = new HashMap<>();

        public PlayerQuestStats(UUID playerId) {
            this.playerId = playerId;
        }

        public void recordQuestAttempt(ResourceLocation mobId, boolean completed, int points, int wavesReached, long duration) {
            totalQuestsAttempted++;
            if (completed) totalQuestsCompleted++;
            totalPointsEarned += points;
            totalPlayTime += duration;

            MobQuestRecord record = mobRecords.computeIfAbsent(mobId.toString(), k -> new MobQuestRecord());
            record.attempts++;
            if (completed) record.completions++;
            if (points > record.bestScore) record.bestScore = points;
            if (wavesReached > record.highestWave) record.highestWave = wavesReached;
        }

        public UUID getPlayerId() { return playerId; }
        public int getTotalQuestsAttempted() { return totalQuestsAttempted; }
        public int getTotalQuestsCompleted() { return totalQuestsCompleted; }
        public int getTotalPointsEarned() { return totalPointsEarned; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public Map<String, MobQuestRecord> getMobRecords() { return mobRecords; }

        public float getCompletionRate() {
            return totalQuestsAttempted > 0 ? (float) totalQuestsCompleted / totalQuestsAttempted : 0;
        }
    }

    /**
     * Record for a specific mob's quest attempts.
     */
    public static class MobQuestRecord {
        public int attempts = 0;
        public int completions = 0;
        public int bestScore = 0;
        public int highestWave = 0;
    }
}

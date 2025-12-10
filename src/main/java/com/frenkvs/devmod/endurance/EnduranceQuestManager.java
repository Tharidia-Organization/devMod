package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.instance.DynamicDimensionManager;
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

    // Instance dimension mode flag - when true, quests run in isolated temporary dimensions
    private boolean useInstanceDimensions = false;

    private EnduranceQuestManager() {}

    // ========== Instance Dimension Mode ==========

    /**
     * Enable or disable instance dimension mode.
     * When enabled, quests run in isolated temporary dimensions instead of overworld arenas.
     */
    public void setUseInstanceDimensions(boolean use) {
        this.useInstanceDimensions = use;
        LOGGER.info("[EnduranceQuest] Instance dimension mode: {}", use ? "ENABLED" : "DISABLED");
    }

    /**
     * Check if instance dimension mode is enabled.
     */
    public boolean isUseInstanceDimensions() {
        return useInstanceDimensions;
    }

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

        // Get quest template first (before any state modification)
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return new StartQuestResult(false, "Unknown quest type: " + mobId, null);
        }

        // Create new quest instance upfront (before atomic check)
        EnduranceQuest quest = new EnduranceQuest(template.getMobConfig());
        quest.setTotalWaves(settings.totalWaves);
        quest.setEndlessMode(settings.endlessMode);

        // Create placeholder session for atomic insert
        // NOTE: Arena is null here - will be set after successful creation
        ActiveQuestSession placeholderSession = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());

        // ATOMIC: Use putIfAbsent to prevent race condition
        // This ensures only one thread can start a quest for this player
        ActiveQuestSession existingSession = activeSessions.putIfAbsent(playerId, placeholderSession);
        if (existingSession != null) {
            return new StartQuestResult(false, I18n.translate("devmod.endurance.active_quest").getString(), null);
        }

        // === INSTANCE DIMENSION MODE ===
        if (useInstanceDimensions) {
            return startQuestInInstanceDimension(player, mobId, quest, settings);
        }

        // === LEGACY OVERWORLD ARENA MODE ===
        // Create arena in current dimension
        ServerLevel level = player.serverLevel();
        ArenaManager.Arena arena = arenaManager.createArena(level, player.blockPosition(), settings.arenaSize);

        if (arena == null) {
            // CLEANUP: Remove placeholder session on failure
            activeSessions.remove(playerId);
            return new StartQuestResult(false, "Failed to create arena", null);
        }

        // Start the quest
        quest.start(arena.getId());

        // Create the real session with arena and replace placeholder
        ActiveQuestSession session = new ActiveQuestSession(playerId, quest, arena, System.currentTimeMillis());
        activeSessions.put(playerId, session); // Replaces placeholder

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
     * Start a quest in an isolated instance dimension.
     * This is the new preferred method using the Instance Dimension System.
     *
     * IMPORTANT: This method is NON-BLOCKING. It returns immediately with a "pending" status
     * and the quest setup is completed asynchronously when the instance is ready.
     */
    private StartQuestResult startQuestInInstanceDimension(ServerPlayer player, ResourceLocation mobId,
                                                           EnduranceQuest quest, QuestSettings settings) {
        UUID playerId = player.getUUID();

        // Get the placeholder session that was atomically inserted in startQuest()
        // Mark it as pending for async completion
        ActiveQuestSession pendingSession = activeSessions.get(playerId);
        if (pendingSession == null) {
            // This should never happen - startQuest already inserted the placeholder
            LOGGER.error("[EnduranceQuest] No placeholder session found for instance quest start");
            return new StartQuestResult(false, "Internal error: missing session", null);
        }
        pendingSession.setPending(true);

        // Show loading overlay on client
        com.frenkvs.devmod.NetworkHandler.sendInstanceLoadingShow(player, "Creating dimension...");

        // Notify player that instance is being created (chat backup)
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DevMod] Creating instance dimension...")
            .withStyle(ChatFormatting.YELLOW));

        // Use InstanceArenaManager to create the instance and arena asynchronously
        var future = InstanceArenaManager.INSTANCE.startInstanceQuest(player, mobId, settings);

        // Handle completion asynchronously - NO BLOCKING
        future.thenAccept(result -> {
            // This runs on the server thread (guaranteed by createDimensionAsync)
            completeInstanceQuestSetup(player, playerId, mobId, quest, settings, result);
        });

        // Return immediately with pending status
        return new StartQuestResult(true, "Creating instance dimension...", pendingSession);
    }

    /**
     * Complete the quest setup after instance dimension is created.
     * Called asynchronously when the instance is ready.
     */
    private void completeInstanceQuestSetup(ServerPlayer player, UUID playerId, ResourceLocation mobId,
                                            EnduranceQuest quest, QuestSettings settings,
                                            InstanceArenaManager.InstanceQuestResult result) {
        // Verify player is still online
        if (player.getServer() == null || player.getServer().getPlayerList().getPlayer(playerId) == null) {
            LOGGER.warn("[EnduranceQuest] Player {} disconnected during instance creation", playerId);
            activeSessions.remove(playerId);
            if (result.success() && result.instanceId() != null) {
                InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
            }
            return;
        }

        // Check if instance creation failed
        if (!result.success()) {
            LOGGER.error("[EnduranceQuest] Instance creation failed for player {}: {}",
                player.getName().getString(), result.message());
            activeSessions.remove(playerId);
            // Hide loading overlay and show error
            com.frenkvs.devmod.NetworkHandler.sendInstanceLoadingHide(player);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DevMod] Failed to create instance: " + result.message())
                .withStyle(ChatFormatting.RED));
            return;
        }

        // Get the arena from the instance
        ArenaManager.Arena arena = result.arena();
        if (arena == null) {
            LOGGER.error("[EnduranceQuest] Instance created but arena is null for player {}",
                player.getName().getString());
            activeSessions.remove(playerId);
            // Hide loading overlay and show error
            com.frenkvs.devmod.NetworkHandler.sendInstanceLoadingHide(player);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DevMod] Instance created but arena is null")
                .withStyle(ChatFormatting.RED));
            if (result.instanceId() != null) {
                InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
            }
            return;
        }

        // Hide loading overlay - quest is starting!
        com.frenkvs.devmod.NetworkHandler.sendInstanceLoadingHide(player);

        // Start the quest
        quest.start(arena.getId());

        // Create the real session with instance ID reference
        ActiveQuestSession session = new ActiveQuestSession(playerId, quest, arena, System.currentTimeMillis());
        session.setInstanceId(result.instanceId());
        activeSessions.put(playerId, session); // Replace pending session

        // Prepare player for quest: save state, set survival, clear inventory, give kit
        preparePlayerForQuest(player, session);

        // Player is already teleported by InstanceArenaManager

        // Initialize all subsystems (Combo, Mutator, Perk, Reward) BEFORE starting wave
        EnduranceEventHandler.onQuestStart(player, session);

        // INTEGRATION: Start telemetry dungeon session for tracking
        String dungeonId = "endurance_instance_" + mobId.toString().replace(":", "_");
        TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);

        // Start the first wave
        WaveManager.INSTANCE.startWave(session);

        // Notify subsystems that wave 1 has started
        EnduranceEventHandler.onWaveStart(player, session, quest.getCurrentWave());

        LOGGER.info("[EnduranceQuest] Player {} started INSTANCE quest: {} (instance: {})",
            player.getName().getString(), quest.getDisplayName(), result.instanceId());

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DevMod] Quest started in instance dimension!")
            .withStyle(ChatFormatting.GREEN));
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
            // Handle pending sessions (instance still being created)
            if (session.isPending()) {
                LOGGER.info("[EnduranceQuest] Player {} abandoned pending quest before instance was ready",
                    player.getName().getString());
                // Force cleanup of any in-progress instance creation
                if (session.getInstanceId() != null) {
                    InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DevMod] Quest cancelled.")
                    .withStyle(ChatFormatting.YELLOW));
                return;
            }

            session.quest.fail(true);

            // Cleanup wave state and boss fight systems FIRST (while player is still in arena)
            cleanupQuestSystems(session);

            // Cleanup subsystems and award partial rewards BEFORE teleport
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "abandoned");

            // Update stats
            updatePlayerStats(playerId, session.quest, false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            // Notify player BEFORE teleport (message will still be visible)
            player.sendSystemMessage(I18n.translate("devmod.endurance.quest_abandoned",
                session.quest.getCurrentWave(), session.quest.getPointsEarnedThisSession())
                .withStyle(ChatFormatting.YELLOW));

            // === NOW do the state restoration and cleanup ===
            // For Instance mode: cleanupArenaOrInstance triggers teleport + full state restore
            // For Legacy mode: restorePlayerAfterQuest handles it locally

            // Restore player's original state (no-op for Instance mode)
            restorePlayerAfterQuest(player, session);

            // Cleanup arena/instance (triggers teleport + recovery for Instance mode)
            cleanupArenaOrInstance(session, false);

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
            // Ignore deaths during pending sessions (instance still being created)
            if (session.isPending()) {
                LOGGER.debug("[EnduranceQuest] Ignoring death for player {} - session is pending",
                    player.getName().getString());
                return;
            }

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

                // Teleport back to arena (handle both instance and legacy modes)
                if (session.isInInstanceDimension()) {
                    // Instance mode: use DynamicDimensionManager
                    DynamicDimensionManager.INSTANCE.teleportToInstance(player, session.getInstanceId());
                } else if (session.arena != null && arenaManager != null) {
                    // Legacy mode: use arenaManager
                    arenaManager.teleportToArena(player, session.arena);
                } else {
                    LOGGER.error("[EnduranceQuest] Cannot teleport player {} - no arena or instance available",
                        player.getName().getString());
                }

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

                // Cleanup wave state and boss fight systems FIRST
                cleanupQuestSystems(session);

                // Cleanup subsystems and award partial rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, false);

                // INTEGRATION: End telemetry dungeon session BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "death_give_up");

                updatePlayerStats(playerId, session.quest, false);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                // === NOW do the state restoration and cleanup ===
                restorePlayerAfterQuest(player, session);
                cleanupArenaOrInstance(session, false);

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

                // Cleanup wave state and boss fight systems FIRST
                cleanupQuestSystems(session);

                // Cleanup subsystems and award full rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, true);

                // INTEGRATION: End telemetry dungeon session with success BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "completed");

                updatePlayerStats(player.getUUID(), session.quest, true);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                LOGGER.info("[EnduranceQuest] Player {} COMPLETED quest: {}!",
                    player.getName().getString(), session.quest.getDisplayName());

                // === NOW do the state restoration and cleanup ===
                restorePlayerAfterQuest(player, session);
                cleanupArenaOrInstance(session, true);
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
            // Cleanup wave state and boss fight systems FIRST
            cleanupQuestSystems(session);

            // Cleanup subsystems and award partial rewards BEFORE teleport
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "checkpoint_exit");

            updatePlayerStats(playerId, session.quest, false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint (wave {})",
                player.getName().getString(), session.quest.getCurrentWave());

            // === NOW do the state restoration and cleanup ===
            restorePlayerAfterQuest(player, session);
            cleanupArenaOrInstance(session, false);
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
     *
     * NOTE: When using Instance Dimension mode, inventory/state is saved by RecoverySystem
     * BEFORE this method is called. We only save to session for legacy (overworld arena) mode.
     */
    private void preparePlayerForQuest(ServerPlayer player, ActiveQuestSession session) {
        // Save original game mode (always needed for both modes)
        session.setOriginalGameMode(player.gameMode.getGameModeForPlayer());

        // Only save inventory locally for LEGACY mode (non-instance)
        // In Instance mode, RecoverySystem already saved a full snapshot
        if (!session.isInInstanceDimension()) {
            ListTag inventoryTag = new ListTag();
            player.getInventory().save(inventoryTag);
            session.setSavedInventory(inventoryTag);
            LOGGER.info("[EnduranceQuest] Prepared player {} for quest (saved {} inventory slots, was in {} mode)",
                player.getName().getString(), inventoryTag.size(), session.getOriginalGameMode());
        } else {
            LOGGER.info("[EnduranceQuest] Prepared player {} for INSTANCE quest (state saved by RecoverySystem)",
                player.getName().getString());
        }

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
     *
     * NOTE: When using Instance Dimension mode, full state restoration (including inventory,
     * position, effects) is handled by RecoverySystem via InstanceManager.endInstanceQuest().
     * This method only performs local restoration for LEGACY mode.
     */
    private void restorePlayerAfterQuest(ServerPlayer player, ActiveQuestSession session) {
        // In Instance mode, RecoverySystem handles FULL restoration (inventory, position, etc.)
        // DO NOT touch player state here - let RecoverySystem do it atomically
        if (session.isInInstanceDimension()) {
            LOGGER.debug("[EnduranceQuest] Instance mode: skipping local restore (RecoverySystem handles it)");
            return;
        }

        // === LEGACY MODE: Full local restoration ===

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

    /**
     * Cleanup quest-related systems (WaveManager, BossWaveSystem) when quest ends.
     * This ensures all state is properly reset for the next quest.
     */
    private void cleanupQuestSystems(ActiveQuestSession session) {
        ArenaManager.Arena arena = session.getArena();
        UUID arenaId = arena.getId();

        // Cleanup WaveManager state (removes tracked mobs, resets wave state)
        WaveManager.INSTANCE.cleanupWave(arenaId, arena.getLevel());

        // Cleanup BossWaveSystem if there's an active boss fight
        BossWaveSystem.INSTANCE.endBossFight(arenaId, false);

        LOGGER.debug("[EnduranceQuest] Cleaned up quest systems for arena {}", arenaId);
    }

    /**
     * Cleanup the arena or instance dimension when a quest ends.
     * If the session used an instance dimension, destroys the instance.
     * Otherwise, destroys the legacy overworld arena.
     *
     * @param session The quest session to cleanup
     * @param success Whether the quest was completed successfully
     */
    private void cleanupArenaOrInstance(ActiveQuestSession session, boolean success) {
        if (session.isInInstanceDimension()) {
            // Instance dimension mode - use InstanceArenaManager for cleanup
            UUID instanceId = session.getInstanceId();
            if (instanceId != null) {
                InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, success);
                LOGGER.debug("[EnduranceQuest] Scheduled instance {} for destruction (success: {})",
                    instanceId, success);
            }
        } else {
            // Legacy overworld arena mode
            arenaManager.destroyArena(session.arena);
            LOGGER.debug("[EnduranceQuest] Destroyed legacy arena {}", session.arena.getId());
        }
    }

    /**
     * Clear ALL player stats and quest data. Used for full player reset.
     * This deletes all endurance quest records, stats, and progress.
     */
    public void clearAllPlayerStats() {
        LOGGER.info("[EnduranceQuest] Clearing all player stats and quest data...");

        // Clear in-memory stats
        playerStats.clear();
        questTemplates.clear();

        // Delete player stats file
        if (dataDirectory != null) {
            try {
                Path statsFile = dataDirectory.resolve("player_stats.json");
                Path backupFile = dataDirectory.resolve("player_stats.json.bak");
                Path tempFile = dataDirectory.resolve("player_stats.json.tmp");

                Files.deleteIfExists(statsFile);
                Files.deleteIfExists(backupFile);
                Files.deleteIfExists(tempFile);

                LOGGER.info("[EnduranceQuest] All player stats cleared successfully");
            } catch (IOException e) {
                LOGGER.error("[EnduranceQuest] Failed to delete player stats files", e);
            }
        }

        // Reset RewardSystem
        try {
            RewardSystem.INSTANCE.resetAll();
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Could not reset RewardSystem: {}", e.getMessage());
        }

        // Reset GamificationManager
        try {
            GamificationManager.INSTANCE.resetAll();
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Could not reset GamificationManager: {}", e.getMessage());
        }

        // Reinitialize quest templates
        for (EnduranceQuestRegistry.MobQuestConfig mobConfig : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            EnduranceQuest template = new EnduranceQuest(mobConfig);
            questTemplates.put(mobConfig.mobId, template);
        }
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

        // Instance dimension ID (null if using legacy overworld arena)
        private UUID instanceId;

        // Pending flag - true while instance is being created asynchronously
        private boolean pending = false;

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

        // Instance dimension getters/setters
        public UUID getInstanceId() { return instanceId; }
        public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
        public boolean isInInstanceDimension() { return instanceId != null; }

        // Pending state (while instance is being created)
        public boolean isPending() { return pending; }
        public void setPending(boolean pending) { this.pending = pending; }
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

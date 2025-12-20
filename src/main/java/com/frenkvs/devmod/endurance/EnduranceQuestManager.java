package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.ListTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all Endurance Quest operations.
 * Handles quest creation, player sessions, persistence, and coordination.
 *
 * Delegates to:
 * - EnduranceQuestPersistence: Player stats loading/saving
 * - EndurancePlayerStateManager: Player state management during quests
 * - EnduranceSessionHandler: Session lifecycle events
 */

public class EnduranceQuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestManager.class);

    public static final EnduranceQuestManager INSTANCE = new EnduranceQuestManager();

    // Active quests per player (player UUID -> active quest)
    private final Map<UUID, ActiveQuestSession> activeSessions = new ConcurrentHashMap<>();

    // Quest templates (mob ID -> quest template with best records)
    private final Map<ResourceLocation, EnduranceQuest> questTemplates = new ConcurrentHashMap<>();

    // Delegate classes
    private final EnduranceQuestPersistence persistence = new EnduranceQuestPersistence();
    private EnduranceSessionHandler sessionHandler;

    // Arena manager reference
    private ArenaManager arenaManager;

    // Data directory
    private Path dataDirectory;

    private boolean initialized = false;

    // Instance dimension mode flag - when true, quests run in isolated temporary dimensions
    private boolean useInstanceDimensions = true;

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

        // Initialize persistence
        persistence.initialize(dataDirectory);

        // Initialize arena manager
        this.arenaManager = new ArenaManager();

        // Initialize session handler with dependencies
        this.sessionHandler = new EnduranceSessionHandler(activeSessions, arenaManager, persistence);

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

                // Flush telemetry/stats and cleanup systems without granting full rewards
                handleForcedShutdownCleanup(session);
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Error cleaning up session for player {}", session.getPlayerId(), e);
            }
        }
        activeSessions.clear();

        // Save all player stats (includes partial rewards)
        persistence.savePlayerStats();

        // Save reward system data
        RewardSystem.INSTANCE.saveAll();

        // Save gamification data (leaderboards, badges, challenges)
        GamificationManager.INSTANCE.saveAll();

        // Clear templates (will be rebuilt on next init)
        questTemplates.clear();

        initialized = false;
        LOGGER.info("[EnduranceQuest] Shutdown complete");
    }

    /**
     * Check if manager is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ========== Party Quest Flow (Separate Phases) ==========

    /**
     * PHASE 1: Prepare arena for a party quest WITHOUT teleporting or starting.
     * Creates the arena and returns the info needed for teleportation.
     *
     * Used by QuestStartSequence to separate arena creation from teleport.
     *
     * @param leader The party leader
     * @param mobId The mob type for the quest
     * @param settings Quest settings (includes party info)
     * @return PreparedArenaResult with arena info, or failure message
     */
    public PreparedArenaResult prepareArenaForParty(ServerPlayer leader, ResourceLocation mobId, QuestSettings settings) {
        // Validate quest type
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            return PreparedArenaResult.failure("Unknown quest type: " + mobId);
        }

        if (useInstanceDimensions) {
            // Instance path (supports party)
            var result = InstanceArenaManager.INSTANCE.startInstanceQuestForParty(leader, mobId, settings);
            if (result.success()) {
                LOGGER.info("[EnduranceQuest] Prepared INSTANCE arena {} for party quest (mob: {}, instance: {})",
                    Objects.requireNonNull(result.arena()).getId(), mobId, result.instanceId());
                return PreparedArenaResult.success(result.arena(), mobId, template.getMobConfig(), result.instanceId());
            }
            return PreparedArenaResult.failure(result.message());
        }

        // Config gate: if instance-only is enabled, block legacy path
        com.devmod.arena.config.ArenaTemplateConfig cfg = com.devmod.arena.config.ArenaTemplateConfig.load();
        if (cfg.instanceOnly()) {
            LOGGER.error("[EnduranceQuest] Instance-only mode enabled, legacy overworld path blocked");
            return PreparedArenaResult.failure("Instance-only mode: legacy overworld arenas are disabled");
        }

        // Legacy overworld fallback (should not be used when instance mode forced)
        ServerLevel level = leader.serverLevel();
        ArenaManager.Arena arena = arenaManager.createArena(level, leader.blockPosition(), settings.arenaSize);

        if (arena == null) {
            return PreparedArenaResult.failure("Failed to create arena");
        }

        LOGGER.info("[EnduranceQuest] Prepared arena {} for party quest (mob: {})",
            arena.getId(), mobId);

        return PreparedArenaResult.success(arena, mobId, template.getMobConfig(), null);
    }

    /**
     * PHASE 2: Teleport players to a prepared arena.
     * Players should be teleported BEFORE starting the quest.
     *
     * @param players List of players to teleport
     * @param arena The prepared arena
     * @return Map of player UUID to their spawn position in arena
     */
    public Map<UUID, net.minecraft.core.BlockPos> teleportPlayersToArena(List<ServerPlayer> players, ArenaManager.Arena arena) {
        Map<UUID, net.minecraft.core.BlockPos> spawnPositions = new HashMap<>();
        net.minecraft.core.BlockPos center = arena.getCenter();
        int playerCount = players.size();

        // Calculate spread positions for players (circle around center)
        double radius = Math.min(arena.getSize() / 4.0, 10.0);

        for (int i = 0; i < playerCount; i++) {
            ServerPlayer player = players.get(i);
            if (player == null || !player.isAlive()) continue;

            // Calculate position in a circle
            double angle = (2 * Math.PI * i) / playerCount;
            double x = center.getX() + 0.5 + radius * Math.cos(angle);
            double z = center.getZ() + 0.5 + radius * Math.sin(angle);

            // Teleport player
            player.teleportTo(x, center.getY(), z);

            // Store spawn position
            spawnPositions.put(player.getUUID(), new net.minecraft.core.BlockPos((int) x, center.getY(), (int) z));

            LOGGER.debug("[EnduranceQuest] Teleported {} to arena at ({}, {}, {})",
                player.getName().getString(), x, center.getY(), z);
        }

        LOGGER.info("[EnduranceQuest] Teleported {} players to arena {}", spawnPositions.size(), arena.getId());
        return spawnPositions;
    }

    /**
     * Check if a player is inside the specified arena.
     * Used for arrival confirmation.
     *
     * @param player The player to check
     * @param arena The arena to check against
     * @return true if player is inside arena bounds
     */
    public boolean isPlayerInArena(ServerPlayer player, ArenaManager.Arena arena) {
        if (player == null || arena == null) return false;
        return arena.contains(Objects.requireNonNull(player.position()));
    }

    /**
     * Destroy an arena (cleanup resources).
     * Used when a party quest sequence is cancelled after arena creation.
     *
     * @param arena The arena to destroy
     */
    public void destroyArena(ArenaManager.Arena arena) {
        if (arena != null && arenaManager != null) {
            arenaManager.destroyArena(arena);
        }
    }

    /**
     * PHASE 3: Start a quest for players using a pre-created arena.
     * All players should already be teleported to the arena.
     *
     * @param players List of players to start the quest for
     * @param arena The prepared arena
     * @param mobId The mob type for the quest
     * @param settings Quest settings
     * @return Map of player UUID to StartQuestResult
     */
    public Map<UUID, StartQuestResult> startPreparedQuest(
            List<ServerPlayer> players, ArenaManager.Arena arena,
            ResourceLocation mobId, QuestSettings settings, @javax.annotation.Nullable UUID instanceId) {

        Map<UUID, StartQuestResult> results = new HashMap<>();

        // Get quest template
        EnduranceQuest template = questTemplates.get(mobId);
        if (template == null) {
            for (ServerPlayer player : players) {
                results.put(player.getUUID(), new StartQuestResult(false, "Unknown quest type: " + mobId, null));
            }
            return results;
        }

        // Start quest for each player
        for (ServerPlayer player : players) {
            if (player == null || !player.isAlive()) continue;

            UUID playerId = player.getUUID();

            // Create quest instance
            EnduranceQuest quest = new EnduranceQuest(template.getMobConfig());
            quest.setTotalWaves(settings.totalWaves);
            quest.setEndlessMode(settings.endlessMode);

            // Create placeholder session for atomic insert
            ActiveQuestSession placeholderSession = new ActiveQuestSession(playerId, quest, null, System.currentTimeMillis());

            // Atomic insert
            ActiveQuestSession existingSession = activeSessions.putIfAbsent(playerId, placeholderSession);
            if (existingSession != null) {
                results.put(playerId, new StartQuestResult(false,
                    Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null));
                continue;
            }

            // Start the quest
            quest.start(arena.getId());

            // Create the real session with arena and party settings
            ActiveQuestSession session = new ActiveQuestSession(
                playerId, quest, arena, System.currentTimeMillis(),
                settings.partyId, settings.questType, settings.getPlayerCount()
            );
            if (instanceId != null) {
                session.setInstanceId(instanceId);
            }
            activeSessions.put(playerId, session); // Replaces placeholder

            // Prepare player (save state, give kit - NO TELEPORT, already done)
            EndurancePlayerStateManager.INSTANCE.preparePlayerForQuest(player, session);

            // Initialize all subsystems
            EnduranceEventHandler.onQuestStart(player, session);

            // Start telemetry
            String dungeonId = "endurance_party_" + mobId.toString().replace(":", "_");
            TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);

            results.put(playerId, new StartQuestResult(true, "Quest started!", session));

            LOGGER.info("[EnduranceQuest] Started prepared quest for player {}: {}",
                player.getName().getString(), quest.getDisplayName());
        }

        // Start wave 1 for the arena (only once, shared between all players)
        // Use the first successful session
        for (StartQuestResult result : results.values()) {
            if (result.success() && result.session() != null) {
                WaveManager.INSTANCE.startWave(result.session());

                // Notify all players that wave 1 started
                for (ServerPlayer player : players) {
                    if (player != null && results.get(player.getUUID()) != null && results.get(player.getUUID()).success()) {
                        EnduranceEventHandler.onWaveStart(player, result.session(), 1);
                    }
                }
                break;
            }
        }

        return results;
    }

    /**
     * Result of preparing an arena for party quest.
     */
    public record PreparedArenaResult(
        boolean success,
        String errorMessage,
        ArenaManager.Arena arena,
        ResourceLocation mobId,
        EnduranceQuestRegistry.MobQuestConfig mobConfig,
        @javax.annotation.Nullable UUID instanceId
    ) {
        public static PreparedArenaResult success(ArenaManager.Arena arena, ResourceLocation mobId,
                                                   EnduranceQuestRegistry.MobQuestConfig mobConfig,
                                                   @javax.annotation.Nullable UUID instanceId) {
            return new PreparedArenaResult(true, null, arena, mobId, mobConfig, instanceId);
        }

        public static PreparedArenaResult failure(String message) {
            return new PreparedArenaResult(false, message, null, null, null, null);
        }
    }

    // ========== Single Player Quest Flow ==========

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
            return new StartQuestResult(false, Objects.requireNonNull(I18n.translate("devmod.endurance.active_quest")).getString(), null);
        }

        // === INSTANCE DIMENSION MODE (forced) ===
        return startQuestInInstanceDimension(player, mobId, quest, settings);
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
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Creating instance dimension...")
            .withStyle(ChatFormatting.YELLOW)));

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
        var server = player.getServer();
        if (server == null || server.getPlayerList().getPlayer(Objects.requireNonNull(playerId)) == null) {
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
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Failed to create instance: " + result.message())
                .withStyle(ChatFormatting.RED)));
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
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Instance created but arena is null")
                .withStyle(ChatFormatting.RED)));
            if (result.instanceId() != null) {
                InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
            }
            return;
        }

        // Hide loading overlay - quest is starting!
        com.frenkvs.devmod.NetworkHandler.sendInstanceLoadingHide(player);

        // Start the quest
        quest.start(arena.getId());

        // Create the real session with instance ID and party settings
        ActiveQuestSession session = new ActiveQuestSession(
            playerId, quest, arena, System.currentTimeMillis(),
            settings.partyId, settings.questType, settings.getPlayerCount()
        );
        session.setInstanceId(result.instanceId());
        activeSessions.put(playerId, session); // Replace pending session

        // Prepare player for quest: save state, set survival, clear inventory, give kit
        EndurancePlayerStateManager.INSTANCE.preparePlayerForQuest(player, session);

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

        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest started in instance dimension!")
            .withStyle(ChatFormatting.GREEN)));
    }

    // ========== Session Management (Delegated) ==========

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
        sessionHandler.abandonQuest(player);
    }

    /**
     * Handle player death during quest.
     */
    public void handlePlayerDeath(ServerPlayer player) {
        sessionHandler.handlePlayerDeath(player);
    }

    /**
     * Handle player choosing to continue after death (with penalty) or give up.
     */
    public void handleRespawnChoice(ServerPlayer player, boolean continueQuest) {
        sessionHandler.handleRespawnChoice(player, continueQuest);
    }

    /**
     * Complete current wave.
     */
    public void completeWave(ServerPlayer player) {
        sessionHandler.completeWave(player);
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        sessionHandler.continueToNextWave(player);
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        sessionHandler.exitAtCheckpoint(player);
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
        return persistence.getPlayerStats(playerId);
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

    // ========== Data Reset ==========

    /**
     * Clear ALL player stats and quest data. Used for full player reset.
     * This deletes all endurance quest records, stats, and progress.
     */
    public void clearAllPlayerStats() {
        LOGGER.info("[EnduranceQuest] Clearing all player stats and quest data...");

        // Clear in-memory stats and templates
        persistence.clearAllStats();
        questTemplates.clear();

        // Delete player stats files
        persistence.deleteAllStatsFiles();

        LOGGER.info("[EnduranceQuest] All player stats cleared successfully");

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

    /**
     * Cleanup path used only during server shutdown to avoid dangling state
     * and to ensure telemetry/stats are flushed without granting full rewards.
     */
    private void handleForcedShutdownCleanup(ActiveQuestSession session) {
        try {
            EnduranceQuest quest = session.quest;
            UUID questId = quest.getQuestId();
            UUID playerId = session.getPlayerId();

            // Record end-of-session telemetry and stats (abandoned outcome)
            EnduranceTelemetryService.INSTANCE.recordQuestEnd(
                questId,
                EnduranceQuestState.FAILED,
                quest.getCurrentWave(),
                quest.getSessionDuration(),
                quest.getMobsKilledThisSession(),
                quest.getTotalDamageDealtThisSession(),
                quest.getDamageTakenThisSession()
            );
            persistence.updatePlayerStats(playerId, quest, false);

            // Stop trackers and sessions to avoid leaks
            CombatTracker.INSTANCE.stopTracking(questId);
            ComboSystem.INSTANCE.endSession(playerId);
            EnduranceEventCombat.removeComboSession(playerId);
            MutatorSystem.INSTANCE.endSession(questId);
            EnduranceEventCombat.removeMutatorSession(questId);
            PerkSystem.INSTANCE.endSession(playerId);

            // Cleanup arena/boss state (handles both legacy and instance modes)
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
            EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, arenaManager, false);
        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed shutdown cleanup for session {}", session.getPlayerId(), e);
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

        // Party/Multiplayer settings
        public QuestType questType = QuestType.PVE_COOP;
        public UUID partyId = null;
        public java.util.List<UUID> partyMemberIds = java.util.List.of();

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

        public QuestSettings questType(QuestType type) {
            this.questType = type;
            // Auto-adjust arena size based on quest type
            this.arenaSize = type.defaultArenaSize;
            return this;
        }

        public QuestSettings party(UUID partyId, java.util.List<UUID> memberIds) {
            this.partyId = partyId;
            this.partyMemberIds = memberIds;
            return this;
        }

        public boolean isMultiplayer() {
            return partyId != null && !partyMemberIds.isEmpty();
        }

        public int getPlayerCount() {
            return Math.max(1, partyMemberIds.size());
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
        final EnduranceQuest quest;
        final ArenaManager.Arena arena;
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

        // Party/Multiplayer scaling fields
        private UUID partyId;
        private QuestType questType = QuestType.PVE_COOP;
        private int playerCount = 1;

        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaManager.Arena arena, long startTime) {
            this.playerId = playerId;
            this.quest = quest;
            this.arena = arena;
            this.startTime = startTime;
        }

        /**
         * Constructor with party/multiplayer parameters.
         */
        public ActiveQuestSession(UUID playerId, EnduranceQuest quest, ArenaManager.Arena arena,
                                  long startTime, UUID partyId, QuestType questType, int playerCount) {
            this.playerId = playerId;
            this.quest = quest;
            this.arena = arena;
            this.startTime = startTime;
            this.partyId = partyId;
            this.questType = questType;
            this.playerCount = Math.max(1, playerCount);
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

        // Party/Multiplayer getters/setters
        public UUID getPartyId() { return partyId; }
        public void setPartyId(UUID partyId) { this.partyId = partyId; }
        public QuestType getQuestType() { return questType; }
        public void setQuestType(QuestType questType) { this.questType = questType; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = Math.max(1, playerCount); }
        public boolean isMultiplayer() { return partyId != null && playerCount > 1; }
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

        public void recordQuestAttempt(ResourceLocation mobId, boolean completed, int points, int wavesReached, long duration, int mobsKilled) {
            totalQuestsAttempted++;
            if (completed) totalQuestsCompleted++;
            totalPointsEarned += points;
            totalMobsKilled += mobsKilled;
            totalPlayTime += duration;

            MobQuestRecord record = mobRecords.computeIfAbsent(mobId.toString(), k -> new MobQuestRecord());
            record.attempts++;
            if (completed) record.completions++;
            if (points > record.bestScore) record.bestScore = points;
            if (wavesReached > record.highestWave) record.highestWave = wavesReached;
        }

        public UUID getPlayerId() { return playerId; }
        public int getTotalQuestsAttempted() { return totalQuestsAttempted; }
        public int getTotalMobsKilled() { return totalMobsKilled; }
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

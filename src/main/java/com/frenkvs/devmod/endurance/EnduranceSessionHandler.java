package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.instance.DynamicDimensionManager;
import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Handles active quest session lifecycle events.
 * Manages abandoning, death handling, respawn choices, and wave completion.
 */
public class EnduranceSessionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceSessionHandler.class);

    private final Map<UUID, EnduranceQuestManager.ActiveQuestSession> activeSessions;
    private final ArenaManager arenaManager;
    private final EnduranceQuestPersistence persistence;

    public EnduranceSessionHandler(Map<UUID, EnduranceQuestManager.ActiveQuestSession> activeSessions,
                                    ArenaManager arenaManager,
                                    EnduranceQuestPersistence persistence) {
        this.activeSessions = activeSessions;
        this.arenaManager = arenaManager;
        this.persistence = persistence;
    }

    // ═══════════════════════════════════════════════════════════════
    // ABANDON QUEST
    // ═══════════════════════════════════════════════════════════════

    /**
     * Abandon current quest.
     */
    public void abandonQuest(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.remove(playerId);

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

            session.getQuest().fail(true);

            // Cleanup wave state and boss fight systems FIRST (while player is still in arena)
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

            // Cleanup subsystems and award partial rewards BEFORE teleport
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "abandoned");

            // Update stats
            persistence.updatePlayerStats(playerId, session.getQuest(), false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            // Notify player BEFORE teleport (message will still be visible)
            player.sendSystemMessage(I18n.translate("devmod.endurance.quest_abandoned",
                session.getQuest().getCurrentWave(), session.getQuest().getPointsEarnedThisSession())
                .withStyle(ChatFormatting.YELLOW));

            // === NOW do the state restoration and cleanup ===
            // For Instance mode: cleanupArenaOrInstance triggers teleport + full state restore
            // For Legacy mode: restorePlayerAfterQuest handles it locally

            // Restore player's original state (no-op for Instance mode)
            EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);

            // Cleanup arena/instance (triggers teleport + recovery for Instance mode)
            EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, arenaManager, false);

            LOGGER.info("[EnduranceQuest] Player {} abandoned quest: {}",
                player.getName().getString(), session.getQuest().getDisplayName());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DEATH HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle player death during quest.
     */
    public void handlePlayerDeath(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null) {
            // Ignore deaths during pending sessions (instance still being created)
            if (session.isPending()) {
                LOGGER.debug("[EnduranceQuest] Ignoring death for player {} - session is pending",
                    player.getName().getString());
                return;
            }

            session.getQuest().fail(false);

            // Don't remove session immediately - allow respawn option
            session.setAwaitingRespawnChoice(true);

            // Send death screen to client (primary UI)
            com.frenkvs.devmod.NetworkHandler.sendQuestDeathScreen(
                player,
                session.getQuest().getCurrentWave(),
                session.getQuest().getTotalWaves(),
                session.getQuest().isEndlessMode(),
                session.getQuest().getPointsEarnedThisSession(),
                session.getQuest().getDeathsThisSession(),
                100 // Respawn cost
            );

            // Also send chat messages as fallback
            player.sendSystemMessage(I18n.translate("devmod.death.divider")
                .withStyle(ChatFormatting.DARK_RED));
            player.sendSystemMessage(I18n.translate("devmod.endurance.you_died_icon")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.sendSystemMessage(I18n.translate("devmod.death.wave_points",
                session.getQuest().getCurrentWave(), session.getQuest().getPointsEarnedThisSession())
                .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(I18n.translate("devmod.death.keybind_hint")
                .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(I18n.translate("devmod.death.divider")
                .withStyle(ChatFormatting.DARK_RED));

            LOGGER.info("[EnduranceQuest] Player {} died in quest: {} at wave {}",
                player.getName().getString(), session.getQuest().getDisplayName(), session.getQuest().getCurrentWave());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESPAWN CHOICE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle player choosing to continue after death (with penalty) or give up.
     */
    public void handleRespawnChoice(ServerPlayer player, boolean continueQuest) {
        UUID playerId = player.getUUID();
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null && session.isAwaitingRespawnChoice()) {
            if (continueQuest) {
                // Continue from current wave with death penalty
                session.getQuest().continueAfterDeath();
                session.setAwaitingRespawnChoice(false);

                // Teleport back to arena (handle both instance and legacy modes)
                if (session.isInInstanceDimension()) {
                    // Instance mode: use DynamicDimensionManager
                    DynamicDimensionManager.INSTANCE.teleportToInstance(player, session.getInstanceId());
                } else if (session.getArena() != null && arenaManager != null) {
                    // Legacy mode: use arenaManager
                    arenaManager.teleportToArena(player, session.getArena());
                } else {
                    LOGGER.error("[EnduranceQuest] Cannot teleport player {} - no arena or instance available",
                        player.getName().getString());
                }

                // Restart the wave (respawn mobs)
                WaveManager.INSTANCE.startWave(session);

                // Notify subsystems
                EnduranceEventHandler.onWaveStart(player, session, session.getQuest().getCurrentWave());

                // Notify player of penalty
                player.sendSystemMessage(I18n.translate("devmod.endurance.respawned_penalty", session.getQuest().getDeathsThisSession())
                    .withStyle(ChatFormatting.RED));

                LOGGER.info("[EnduranceQuest] Player {} continuing quest after death at wave {}",
                    player.getName().getString(), session.getQuest().getCurrentWave());
            } else {
                // End quest
                activeSessions.remove(playerId);

                // Cleanup wave state and boss fight systems FIRST
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                // Cleanup subsystems and award partial rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, false);

                // INTEGRATION: End telemetry dungeon session BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "death_give_up");

                persistence.updatePlayerStats(playerId, session.getQuest(), false);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                // === NOW do the state restoration and cleanup ===
                EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);
                EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, arenaManager, false);

                LOGGER.info("[EnduranceQuest] Player {} gave up after death", player.getName().getString());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVE COMPLETION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Complete current wave.
     */
    public void completeWave(ServerPlayer player) {
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.getQuest().getState() == EnduranceQuestState.IN_PROGRESS) {
            session.getQuest().completeWave();

            if (session.getQuest().getState() == EnduranceQuestState.COMPLETED) {
                // Quest fully completed!
                activeSessions.remove(player.getUUID());

                // Cleanup wave state and boss fight systems FIRST
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                // Cleanup subsystems and award full rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, true);

                // INTEGRATION: End telemetry dungeon session with success BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "completed");

                persistence.updatePlayerStats(player.getUUID(), session.getQuest(), true);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

                LOGGER.info("[EnduranceQuest] Player {} COMPLETED quest: {}!",
                    player.getName().getString(), session.getQuest().getDisplayName());

                // === NOW do the state restoration and cleanup ===
                EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);
                EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, arenaManager, true);
            }
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session != null && session.getQuest().getState() == EnduranceQuestState.WAVE_COMPLETE) {
            session.getQuest().continueToNextWave();
            session.resetWaveKills();

            // Start spawning mobs for the new wave
            WaveManager.INSTANCE.startWave(session);

            // Notify subsystems that new wave has started
            EnduranceEventHandler.onWaveStart(player, session, session.getQuest().getCurrentWave());

            LOGGER.info("[EnduranceQuest] Player {} starting wave {}",
                player.getName().getString(), session.getQuest().getCurrentWave());
        }
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.remove(playerId);

        if (session != null && session.getQuest().getState() == EnduranceQuestState.WAVE_COMPLETE) {
            // Cleanup wave state and boss fight systems FIRST
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

            // Cleanup subsystems and award partial rewards BEFORE teleport
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "checkpoint_exit");

            persistence.updatePlayerStats(playerId, session.getQuest(), false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, QuestSyncPayload.empty());

            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint (wave {})",
                player.getName().getString(), session.getQuest().getCurrentWave());

            // === NOW do the state restoration and cleanup ===
            EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);
            EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, arenaManager, false);
        }
    }
}

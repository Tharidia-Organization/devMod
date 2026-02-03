package com.devmod.endurance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.party.QuestSequencePayload;
import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.runtime.InstanceRegistry;
import com.devmod.runtime.PlayerInstanceState;
import com.devmod.runtime.RecoverySystem;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
import com.devmod.util.I18n;

public class EnduranceSessionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceSessionHandler.class);

    private final Map<UUID, EnduranceQuestManager.ActiveQuestSession> activeSessions;
    private final EnduranceQuestPersistence persistence;

    public EnduranceSessionHandler(Map<UUID, EnduranceQuestManager.ActiveQuestSession> activeSessions,
                                    EnduranceQuestPersistence persistence) {
        this.activeSessions = activeSessions;
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
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null && session.getPartyId() != null) {
            EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "abandon");
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] You are now spectating the party run. Rejoin after this wave.")
                .withStyle(SharedColorTokens.Chat.YELLOW)));
            return;
        }

        session = activeSessions.get(playerId);

        if (session != null) {
            boolean practice = session.isPracticeMode();
            // Handle pending sessions (instance still being created)
            if (session.isPending()) {
                LOGGER.info("[EnduranceQuest] Player {} abandoned pending quest before instance was ready",
                    player.getName().getString());
                session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED, "pending quest abandoned");
                // Force cleanup of any in-progress instance creation
                if (session.getInstanceId() != null) {
                    InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
                }
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.CANCELLED, 0);
                player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest cancelled.")
                    .withStyle(SharedColorTokens.Chat.YELLOW)));
                // Remove session after handling pending case
                activeSessions.remove(playerId);
                return;
            }

            try {
                session.getQuest().fail(true);

                // Cleanup config overrides for this quest
                EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

                if (session.isRespawnCountdownActive()) {
                    EnduranceTelemetryService.INSTANCE.recordGiveupDuringRespawn(session.getQuest().getQuestId());
                }
                cancelSoloSequence(player, session);

                // Cleanup wave state and boss fight systems FIRST (while player is still in arena)
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                // Cleanup subsystems and award partial rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, false);

                if (!practice) {
                    // INTEGRATION: End telemetry dungeon session BEFORE teleport
                    TelemetryService.INSTANCE.endDungeonSession(player, "abandoned");

                    // Update stats
                    persistence.updatePlayerStats(playerId, session.getQuest(), false);
                }

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

                // Notify player BEFORE teleport (message will still be visible)
                player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.quest_abandoned",
                    session.getQuest().getCurrentWave(), session.getQuest().getPointsEarnedThisSession())
                    .withStyle(SharedColorTokens.Chat.YELLOW)));
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Failed to abandon quest cleanly for {}",
                    player.getName().getString(), e);
            } finally {
                // Remove session AFTER cleanup to ensure all subsystems are properly cleaned
                activeSessions.remove(playerId);
                // === NOW do the state restoration and cleanup ===
                restoreAndCleanup(player, session, false, "Quest abandoned");
            }

            EnduranceLogger.phase(Phase.QUEST_ABANDON, player, session.getQuest().getQuestId(),
                "Abandoned at wave %d/%d, points=%d",
                session.getQuest().getCurrentWave(), session.getQuest().getTotalWaves(),
                session.getQuest().getPointsEarnedThisSession());
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
            if (session.getPartyId() != null) {
                EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "death");
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                    "[DevMod] You are down. Spectating until the next wave.")
                    .withStyle(SharedColorTokens.Chat.RED)));
                return;
            }
            // Handle deaths during pending sessions (instance still being created)
            // Cancel the quest cleanly to avoid leaving player in a broken state
            if (session.isPending()) {
                LOGGER.info("[EnduranceQuest] Player {} died during pending session - canceling quest",
                    player.getName().getString());
                session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED, "died during pending");
                // Force cleanup of any in-progress instance creation
                if (session.getInstanceId() != null) {
                    InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
                }
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    com.devmod.party.QuestSequencePayload.Phase.CANCELLED, 0);
                player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                    "[DevMod] Quest cancelled - you died before the instance was ready.")
                    .withStyle(SharedColorTokens.Chat.YELLOW)));
                activeSessions.remove(playerId);
                return;
            }

            session.getQuest().fail(false);
            session.clearPendingWaveStart();
            session.setRespawnRequested(false);
            session.setRespawnCountdownActive(false);

            // Don't remove session immediately - allow respawn option
            session.setAwaitingRespawnChoice(true);
            session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.AWAITING_RESPAWN, "player died");

            // Structured logging for player death
            EnduranceLogger.phase(Phase.PLAYER_DEATH, player, session.getQuest().getQuestId(),
                "Died at wave %d/%d, deaths=%d, points=%d",
                session.getQuest().getCurrentWave(), session.getQuest().getTotalWaves(),
                session.getQuest().getDeathsThisSession(), session.getQuest().getPointsEarnedThisSession());

            // Send death screen to client (primary UI)
            com.devmod.network.NetworkHandler.sendQuestDeathScreen(
                player,
                session.getQuest().getCurrentWave(),
                session.getQuest().getTotalWaves(),
                session.getQuest().isEndlessMode(),
                session.getQuest().getPointsEarnedThisSession(),
                session.getQuest().getDeathsThisSession(),
                100 // Respawn cost
            );

            // Also send chat messages as fallback
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.death.divider")
                .withStyle(SharedColorTokens.Chat.DARK_RED)));
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.you_died_icon")
                .withStyle(SharedColorTokens.Chat.RED, ChatFormatting.BOLD)));
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.death.wave_points",
                session.getQuest().getCurrentWave(), session.getQuest().getPointsEarnedThisSession())
                .withStyle(SharedColorTokens.Chat.GRAY)));
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.death.keybind_hint")
                .withStyle(SharedColorTokens.Chat.YELLOW)));
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.death.divider")
                .withStyle(SharedColorTokens.Chat.DARK_RED)));

            LOGGER.info("[EnduranceQuest] Player {} died in quest: {} at wave {} (state={}, awaitingRespawn={}, respawnRequested={}, instanceId={}, dimension={})",
                player.getName().getString(),
                session.getQuest().getDisplayName(),
                session.getQuest().getCurrentWave(),
                session.getQuest().getState(),
                session.isAwaitingRespawnChoice(),
                session.isRespawnRequested(),
                session.getInstanceId(),
                player.level().dimension().location());
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

        if (session != null && session.getPartyId() != null) {
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] Respawn choice is handled by the party run.")
                .withStyle(SharedColorTokens.Chat.YELLOW)));
            return;
        }

        if (session != null && session.isAwaitingRespawnChoice()) {
            if (continueQuest) {
                if (player.isDeadOrDying()) {
                    LOGGER.info("[EnduranceQuest] Respawn choice received for {} (dead=true, state={}, instanceId={}, dimension={})",
                        player.getName().getString(),
                        session.getQuest().getState(),
                        session.getInstanceId(),
                        player.level().dimension().location());
                    session.setRespawnRequested(true);
                    session.setAwaitingRespawnChoice(false);
                    LOGGER.info("[EnduranceQuest] Player {} accepted respawn; waiting for vanilla respawn event",
                        player.getName().getString());
                    return;
                }

                LOGGER.info("[EnduranceQuest] Respawn choice received for {} (dead=false, state={}, instanceId={}, dimension={})",
                    player.getName().getString(),
                    session.getQuest().getState(),
                    session.getInstanceId(),
                    player.level().dimension().location());
                continueQuestAfterRespawn(player, session);
            } else {
                // End quest
                // NOTE: Remove session in finally block to ensure cleanup completes even on exceptions

                try {
                    // Cleanup config overrides for this quest
                    EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

                    if (session.isRespawnCountdownActive()) {
                        EnduranceTelemetryService.INSTANCE.recordGiveupDuringRespawn(session.getQuest().getQuestId());
                    }
                    cancelSoloSequence(player, session);

                    // Cleanup wave state and boss fight systems FIRST
                    EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                    // Cleanup subsystems and award partial rewards BEFORE teleport
                    EnduranceEventHandler.onQuestEnd(player, session, false);

                    if (!session.isPracticeMode()) {
                        // INTEGRATION: End telemetry dungeon session BEFORE teleport
                        TelemetryService.INSTANCE.endDungeonSession(player, "death_give_up");

                        persistence.updatePlayerStats(playerId, session.getQuest(), false);
                    }

                    // Send empty sync to clear client HUD
                    PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));
                } catch (Exception e) {
                    LOGGER.error("[EnduranceQuest] Failed to give up after death cleanly for {}",
                        player.getName().getString(), e);
                } finally {
                    // Remove session AFTER cleanup to ensure all subsystems are properly cleaned
                    activeSessions.remove(playerId);
                    // === NOW do the state restoration and cleanup ===
                    restoreAndCleanup(player, session, false, "Quest ended");
                }

                EnduranceLogger.phase(Phase.QUEST_FAIL, player, session.getQuest().getQuestId(),
                    "Gave up at wave %d/%d, points=%d",
                    session.getQuest().getCurrentWave(), session.getQuest().getTotalWaves(),
                    session.getQuest().getPointsEarnedThisSession());
                LOGGER.info("[EnduranceQuest] Player {} gave up after death", player.getName().getString());
            }
        }
    }

    /**
     * Handle vanilla respawn after death. If the player used the vanilla respawn button,
     * redirect them back into the quest instance and restart the wave.
     */
    public void handleVanillaRespawn(ServerPlayer player) {
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.getPartyId() != null) {
            return;
        }
        if (!session.isAwaitingRespawnChoice() && !session.isRespawnRequested()) {
            return;
        }

        LOGGER.info("[EnduranceQuest] Vanilla respawn detected for {} (state={}, awaitingRespawn={}, respawnRequested={}, instanceId={}, dimension={})",
            player.getName().getString(),
            session.getQuest().getState(),
            session.isAwaitingRespawnChoice(),
            session.isRespawnRequested(),
            session.getInstanceId(),
            player.level().dimension().location());
        continueQuestAfterRespawn(player, session);
    }

    private void continueQuestAfterRespawn(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        // Reset wave state before respawn to avoid stale mobs/state.
        EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
        session.resetWaveKills();

        LOGGER.info("[EnduranceQuest] continueQuestAfterRespawn start for {} (state={}, instanceId={}, dimension={})",
            player.getName().getString(),
            session.getQuest().getState(),
            session.getInstanceId(),
            player.level().dimension().location());

        session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.TELEPORTING, "respawn");
        boolean teleported = EnduranceQuestManager.INSTANCE.teleportPlayerToArena(
            player,
            session,
            true,
            session.isInInstanceDimension()
        );

        LOGGER.info("[EnduranceQuest] continueQuestAfterRespawn teleport result for {} (success={}, arenaId={})",
            player.getName().getString(),
            teleported,
            session.getArena() != null ? session.getArena().getId() : null);

        if (!teleported) {
            LOGGER.error("[EnduranceQuest] Cannot respawn player {} - arena/instance unavailable, forcing quest end",
                player.getName().getString());
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] Respawn failed - arena is unavailable. Ending quest and restoring your state.")
                .withStyle(SharedColorTokens.Chat.RED)));

            // FALLBACK: Force end the quest and restore player to safety
            // This prevents the player from being stuck in a broken state
            try {
                session.getQuest().fail(true);
                EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
                EnduranceEventHandler.onQuestEnd(player, session, false);

                if (!session.isPracticeMode()) {
                    TelemetryService.INSTANCE.endDungeonSession(player, "respawn_teleport_failed");
                    persistence.updatePlayerStats(player.getUUID(), session.getQuest(), false);
                }

                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Error during respawn failure cleanup for {}",
                    player.getName().getString(), e);
            } finally {
                activeSessions.remove(player.getUUID());
                restoreAndCleanup(player, session, false, "Respawn teleport failed");
            }
            return;
        }
        session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.ACTIVE, "respawned");

        cleanupDroppedItems(session);
        EndurancePlayerStateManager.INSTANCE.resetQuestLoadout(player, session);

        // Continue from current wave with death penalty
        session.getQuest().continueAfterDeath();
        session.setAwaitingRespawnChoice(false);
        session.setRespawnRequested(false);

        // Structured logging for player respawn
        EnduranceLogger.phase(Phase.PLAYER_RESPAWN, player, session.getQuest().getQuestId(),
            "Respawned at wave %d, total deaths=%d",
            session.getQuest().getCurrentWave(), session.getQuest().getDeathsThisSession());

        // Check if there's already an active wave or pending countdown
        // to avoid scheduling duplicate wave starts (which causes boss to spawn immediately)
        ArenaContext arena = session.getArena();
        boolean hasActiveWave = arena != null && arena.getId() != null
            && WaveManager.INSTANCE.getWaveState(arena.getId()).isPresent();
        boolean hasPendingCountdown = session.isWaveStartPending() || session.isBossIntroPending();

        // Always apply safe window for invulnerability after respawn
        session.scheduleSafeWindow(EnduranceQuestManager.SAFE_WINDOW_TICKS);
        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.SAFE_WINDOW,
            (int) Math.ceil(EnduranceQuestManager.SAFE_WINDOW_TICKS / 20.0),
            session.getQuest().getDisplayName(),
            "Safe window",
            List.of("Invulnerability active"));

        if (!hasActiveWave && !hasPendingCountdown) {
            // No wave in progress and no countdown pending - schedule new wave/boss start
            // Check if this is a boss wave to schedule the correct intro
            int currentWave = session.getQuest().getCurrentWave();
            UUID questId = session.getQuest().getQuestId();
            boolean isBossWave = !session.isPracticeMode()
                && BossWaveSystem.INSTANCE.isBossWave(currentWave, questId);

            if (isBossWave) {
                // Boss wave: schedule boss intro instead of regular wave countdown
                session.scheduleBossIntro(EnduranceQuestManager.BOSS_INTRO_TICKS);
                session.setRespawnCountdownActive(true);
                LOGGER.info("[EnduranceQuest] Scheduled BOSS intro after respawn for player {} (wave {})",
                    player.getName().getString(), currentWave);
            } else {
                // Regular wave: schedule normal wave countdown
                session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
                session.setRespawnCountdownActive(true);
                LOGGER.info("[EnduranceQuest] Scheduled wave start after respawn for player {}",
                    player.getName().getString());
            }
        } else {
            // Wave is already in progress or countdown is pending - just rejoin
            LOGGER.info("[EnduranceQuest] Skipping wave start schedule on respawn (hasActiveWave={}, hasPendingCountdown={})",
                hasActiveWave, hasPendingCountdown);
        }

        // Notify player of penalty
        player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.respawned_penalty", session.getQuest().getDeathsThisSession())
            .withStyle(SharedColorTokens.Chat.RED)));

        LOGGER.info("[EnduranceQuest] Player {} continuing quest after death at wave {}",
            player.getName().getString(), session.getQuest().getCurrentWave());
    }

    private void cleanupDroppedItems(EnduranceQuestManager.ActiveQuestSession session) {
        ArenaContext arena = session.getArena();
        if (arena == null) {
            return;
        }

        var level = arena.getLevel();
        net.minecraft.world.phys.AABB bounds;
        var handle = session.getArenaHandle();
        if (handle != null && handle.bounds() != null) {
            var hb = handle.bounds();
            bounds = new net.minecraft.world.phys.AABB(
                hb.minX(), hb.minY(), hb.minZ(),
                hb.maxX() + 1.0, hb.maxY() + 1.0, hb.maxZ() + 1.0
            );
        } else {
            bounds = arena.getBounds();
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
            java.util.Objects.requireNonNull(bounds, "bounds"));
        if (items.isEmpty()) {
            return;
        }

        for (ItemEntity item : items) {
            item.discard();
        }

        LOGGER.debug("[EnduranceQuest] Removed {} dropped items from arena {}", items.size(), arena.getId());
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
                // NOTE: Remove session in finally block to ensure cleanup completes even on exceptions

                try {
                    // Cleanup config overrides for this quest
                    EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

                    // Cleanup wave state and boss fight systems FIRST
                    EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                    // Cleanup subsystems and award full rewards BEFORE teleport
                    EnduranceEventHandler.onQuestEnd(player, session, true);

                    if (!session.isPracticeMode()) {
                        // INTEGRATION: End telemetry dungeon session with success BEFORE teleport
                        TelemetryService.INSTANCE.endDungeonSession(player, "completed");

                        persistence.updatePlayerStats(player.getUUID(), session.getQuest(), true);
                    }

                    // Send empty sync to clear client HUD
                    PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

                    cancelSoloSequence(player, session);

                    EnduranceLogger.phase(Phase.QUEST_COMPLETE, player, session.getQuest().getQuestId(),
                        "Completed all %d waves, points=%d, deaths=%d",
                        session.getQuest().getTotalWaves(), session.getQuest().getPointsEarnedThisSession(),
                        session.getQuest().getDeathsThisSession());
                    LOGGER.info("[EnduranceQuest] Player {} COMPLETED quest: {}!",
                        player.getName().getString(), session.getQuest().getDisplayName());
                } catch (Exception e) {
                    LOGGER.error("[EnduranceQuest] Failed to complete quest cleanly for {}",
                        player.getName().getString(), e);
                } finally {
                    // Remove session AFTER cleanup to ensure all subsystems are properly cleaned
                    activeSessions.remove(player.getUUID());
                    // === NOW do the state restoration and cleanup ===
                    restoreAndCleanup(player, session, true, "Quest completed");
                }
            }
        }
    }

    /**
     * Continue to next wave after checkpoint.
     */
    public void continueToNextWave(ServerPlayer player) {
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(player.getUUID());
        LOGGER.info("[CheckpointDebug] continueToNextWave called for player {}, session={}, partyId={}",
            player.getName().getString(),
            session != null ? "present" : "null",
            session != null ? session.getPartyId() : "N/A");

        if (session != null && session.getPartyId() != null) {
            LOGGER.info("[CheckpointDebug] Delegating to party continueToNextWave for partyId={}",
                session.getPartyId());
            EnduranceQuestManager.INSTANCE.requestPartyContinue(player.getUUID());
            return;
        }
        if (session != null && session.getQuest().getState() == EnduranceQuestState.WAVE_COMPLETE) {
            LOGGER.info("[CheckpointDebug] Quest state is WAVE_COMPLETE, starting next wave");

            // Structured logging for checkpoint
            EnduranceLogger.phase(Phase.CHECKPOINT, player, session.getQuest().getQuestId(),
                "Checkpoint reached: wave %d/%d complete, continuing to wave %d",
                session.getQuest().getCurrentWave(), session.getQuest().getTotalWaves(),
                session.getQuest().getCurrentWave() + 1);

            // Clear completed wave state before starting new wave
            // This allows EnduranceEventTick to start the next wave
            ArenaContext arena = session.getArena();
            if (arena != null && arena.getId() != null) {
                WaveManager.INSTANCE.clearCompletedWaveState(arena.getId());
                LOGGER.info("[CheckpointDebug] Cleared completed wave state for arena {}", arena.getId());
            }

            session.getQuest().continueToNextWave();
            session.resetWaveKills();
            session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
            session.setRespawnCountdownActive(false);

            LOGGER.info("[EnduranceQuest] Player {} starting wave {}",
                player.getName().getString(), session.getQuest().getCurrentWave());
        } else if (session != null) {
            LOGGER.warn("[CheckpointDebug] Quest state is {} (expected WAVE_COMPLETE), cannot continue",
                session.getQuest().getState());
        }
    }

    /**
     * Exit at checkpoint (between waves).
     */
    public void exitAtCheckpoint(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EnduranceQuestManager.ActiveQuestSession session = activeSessions.get(playerId);

        if (session != null && session.getPartyId() != null) {
            EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "exit_checkpoint");
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] You are now spectating the party run.")
                .withStyle(SharedColorTokens.Chat.YELLOW)));
            return;
        }

        session = activeSessions.get(playerId);

        if (session != null && session.getQuest().getState() == EnduranceQuestState.WAVE_COMPLETE) {
            boolean practice = session.isPracticeMode();
            try {
                // Cleanup config overrides for this quest
                EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

                // Cleanup wave state and boss fight systems FIRST
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                // Cleanup subsystems and award partial rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, false);

                if (!practice) {
                    // INTEGRATION: End telemetry dungeon session BEFORE teleport
                    TelemetryService.INSTANCE.endDungeonSession(player, "checkpoint_exit");

                    persistence.updatePlayerStats(playerId, session.getQuest(), false);
                }

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

                cancelSoloSequence(player, session);

                LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint (wave {})",
                    player.getName().getString(), session.getQuest().getCurrentWave());
            } catch (Exception e) {
                LOGGER.error("[EnduranceQuest] Failed to exit at checkpoint cleanly for {}",
                    player.getName().getString(), e);
            } finally {
                // Remove session AFTER cleanup to ensure all subsystems are properly cleaned
                activeSessions.remove(playerId);
                // === NOW do the state restoration and cleanup ===
                restoreAndCleanup(player, session, false, "Quest exited");
            }
        }
    }

    /**
     * Force-fail a quest session due to critical system issues (e.g., missing instance dimension).
     * This is a non-player-initiated failure that should recover the player safely.
     */
    public void forceFailQuest(ServerPlayer player,
                               EnduranceQuestManager.ActiveQuestSession session,
                               String reason) {
        if (player == null || session == null) {
            return;
        }
        if (session.getPartyId() != null) {
            return;
        }
        EnduranceQuestManager.ActiveQuestSession.LifecycleState state = session.getLifecycleState();
        if (state == EnduranceQuestManager.ActiveQuestSession.LifecycleState.CLEANUP
            || state == EnduranceQuestManager.ActiveQuestSession.LifecycleState.COMPLETED
            || state == EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED) {
            return;
        }

        String safeReason = reason != null && !reason.isBlank() ? reason : "Quest failed";
        UUID playerId = player.getUUID();

        // Handle pending sessions (instance still being created)
        if (session.isPending()) {
            LOGGER.warn("[EnduranceQuest] Force-failing pending quest for {} (reason={})",
                player.getName().getString(), safeReason);
            session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED, safeReason);
            if (session.getInstanceId() != null) {
                InstanceArenaManager.INSTANCE.forceEndPlayerQuest(playerId);
            }
            EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                QuestSequencePayload.Phase.CANCELLED, 0);
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] Quest failed: " + safeReason).withStyle(SharedColorTokens.Chat.RED)));
            activeSessions.remove(playerId);
            return;
        }

        try {
            session.getQuest().fail(true);
            EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());
            cancelSoloSequence(player, session);
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
            EnduranceEventHandler.onQuestEnd(player, session, false);

            if (!session.isPracticeMode()) {
                TelemetryService.INSTANCE.endDungeonSession(player, "teleport_failed");
                persistence.updatePlayerStats(playerId, session.getQuest(), false);
            }

            PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] Quest failed: " + safeReason + ". Restoring your state.")
                .withStyle(SharedColorTokens.Chat.RED)));
        } catch (Exception e) {
            LOGGER.error("[EnduranceQuest] Failed to force-fail quest for {} (reason={})",
                player.getName().getString(), safeReason, e);
        } finally {
            activeSessions.remove(playerId);
            restoreAndCleanup(player, session, false, safeReason);
        }

        EnduranceLogger.phase(Phase.QUEST_FAIL, player, session.getQuest().getQuestId(),
            "Forced failure: reason=%s, wave=%d/%d, points=%d",
            safeReason,
            session.getQuest().getCurrentWave(),
            session.getQuest().getTotalWaves(),
            session.getQuest().getPointsEarnedThisSession());
    }

    private void cancelSoloSequence(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null || player == null) {
            return;
        }
        session.clearAllSequences();
        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
            QuestSequencePayload.Phase.CANCELLED, 0);
    }

    private InstanceRecoveryResult ensureInstanceRecovery(ServerPlayer player,
                                                          EnduranceQuestManager.ActiveQuestSession session,
                                                          String reason) {
        if (session == null || player == null || !session.isInInstanceDimension()) {
            return InstanceRecoveryResult.NOT_APPLICABLE;
        }
        UUID playerId = player.getUUID();
        var snapshotOpt = RecoverySystem.INSTANCE.loadSnapshot(playerId);
        if (snapshotOpt.isEmpty()) {
            return InstanceRecoveryResult.NO_SNAPSHOT;
        }
        var snapshot = snapshotOpt.get();
        if (snapshot.getState() == PlayerInstanceState.RETURNING) {
            return InstanceRecoveryResult.RECOVERY_PENDING;
        }
        RecoverySystem.INSTANCE.updateSnapshotState(playerId, PlayerInstanceState.RETURNING);
        ServerPlayer recoveryPlayer = player;
        if (recoveryPlayer.isDeadOrDying()) {
            var server = recoveryPlayer.getServer();
            if (server != null) {
                // Respawn creates a NEW player entity at spawn point
                recoveryPlayer = server.getPlayerList().respawn(
                    recoveryPlayer,
                    false,
                    net.minecraft.world.entity.Entity.RemovalReason.KILLED
                );

                // Schedule recovery on next tick to ensure the new player entity is fully added to the world
                // This fixes issues where teleportTo() fails immediately after respawn
                if (recoveryPlayer != null) {
                    // CRITICAL: Apply immediate invulnerability to protect player during the transition tick
                    // Without this, the player can die again at spawn before recovery teleports them to safety
                    // Resistance V (amplifier 4) = 100% damage reduction for 3 seconds
                    recoveryPlayer.addEffect(new MobEffectInstance(
                        Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE),
                        60, // 3 seconds - more than enough for recovery to complete
                        4,  // Amplifier 4 = Resistance V = 100% damage reduction
                        false,
                        false
                    ));
                    final ServerPlayer finalPlayer = recoveryPlayer;
                    final com.devmod.runtime.PlayerInstanceSnapshot finalSnapshot = snapshot;
                    final String finalReason = reason;
                    LOGGER.info("[RecoveryFix] Scheduling delayed recovery for {} (tick+1) after respawn. " +
                        "Respawned player pos=({}, {}, {}), dimension={}",
                        finalPlayer.getName().getString(),
                        finalPlayer.getX(), finalPlayer.getY(), finalPlayer.getZ(),
                        finalPlayer.level().dimension().location());
                    server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
                        // Re-fetch player to ensure we have the correct entity reference
                        ServerPlayer currentPlayer = server.getPlayerList().getPlayer(playerId);
                        if (currentPlayer != null) {
                            LOGGER.info("[RecoveryFix] Executing delayed recovery for {} (re-fetched). " +
                                "Current pos=({}, {}, {}), dimension={}",
                                currentPlayer.getName().getString(),
                                currentPlayer.getX(), currentPlayer.getY(), currentPlayer.getZ(),
                                currentPlayer.level().dimension().location());
                            RecoverySystem.INSTANCE.performRecovery(currentPlayer, finalSnapshot, finalReason);
                        } else {
                            // Fallback to the reference we got from respawn
                            LOGGER.warn("[RecoveryFix] Could not re-fetch player {}, using respawn reference",
                                playerId);
                            RecoverySystem.INSTANCE.performRecovery(finalPlayer, finalSnapshot, finalReason);
                        }
                    }));
                    return InstanceRecoveryResult.RECOVERED;
                }
            }
        }
        if (recoveryPlayer != null) {
            RecoverySystem.INSTANCE.performRecovery(recoveryPlayer, snapshot, reason);
        } else {
            LOGGER.error("[EnduranceQuest] Recovery failed - respawn returned null for {}", playerId);
            return InstanceRecoveryResult.NO_SNAPSHOT;
        }
        return InstanceRecoveryResult.RECOVERED;
    }

    private void restoreAndCleanup(ServerPlayer player,
                                   EnduranceQuestManager.ActiveQuestSession session,
                                   boolean success,
                                   String reason) {
        EnduranceLogger.phase(Phase.CLEANUP, player, session.getQuest().getQuestId(),
            "Starting restore: success=%s, reason=%s, instanceMode=%s",
            success, reason, session.isInInstanceDimension());
        session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.CLEANUP, reason);

        try {
            ServerPlayer activePlayer = player;
            if (!session.isInInstanceDimension() && activePlayer.isDeadOrDying()) {
                var server = activePlayer.getServer();
                if (server != null) {
                    ServerPlayer respawned = server.getPlayerList().respawn(
                        activePlayer,
                        false,
                        net.minecraft.world.entity.Entity.RemovalReason.KILLED
                    );
                    if (respawned != null) {
                        activePlayer = respawned;
                        LOGGER.info("[EnduranceQuest] Respawned player {} before legacy restore (reason={})",
                            activePlayer.getName().getString(), reason);
                    }
                }
            }

            boolean restored = EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(activePlayer, session);
            EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, success);
            InstanceRecoveryResult recoveryResult = ensureInstanceRecovery(activePlayer, session, reason);
            if (session.isInInstanceDimension()
                && recoveryResult == InstanceRecoveryResult.NO_SNAPSHOT
            ) {
                var server = activePlayer.getServer();
                ServerPlayer fallbackPlayer = activePlayer;
                if (server != null) {
                    ServerPlayer current = server.getPlayerList().getPlayer(activePlayer.getUUID());
                    if (current != null) {
                        fallbackPlayer = current;
                    }
                }
                InstanceRegistry.INSTANCE.unmapPlayer(fallbackPlayer.getUUID());
                if (fallbackPlayer.isDeadOrDying() && fallbackPlayer.getServer() != null) {
                    fallbackPlayer = fallbackPlayer.getServer().getPlayerList().respawn(
                        fallbackPlayer,
                        false,
                        net.minecraft.world.entity.Entity.RemovalReason.KILLED
                    );
                }
                if (fallbackPlayer != null
                    && DynamicDimensionManager.INSTANCE.isInstanceDimension(fallbackPlayer.level().dimension())) {
                    boolean teleported = DynamicDimensionManager.INSTANCE.teleportToOverworld(fallbackPlayer);
                    if (teleported) {
                        LOGGER.warn("[EnduranceQuest] Fallback teleport used for {} after missing snapshot ({})",
                            fallbackPlayer.getName().getString(), reason);
                    } else {
                        LOGGER.error("[EnduranceQuest] Fallback teleport failed for {} after missing snapshot ({})",
                            fallbackPlayer.getName().getString(), reason);
                    }
                } else if (fallbackPlayer == null) {
                    LOGGER.error("[EnduranceQuest] Fallback recovery failed - respawn returned null for {}", activePlayer.getUUID());
                }
            }
            boolean restoreSuccess = restored
                || recoveryResult == InstanceRecoveryResult.RECOVERED
                || recoveryResult == InstanceRecoveryResult.RECOVERY_PENDING;

            // Structured logging for restore result
            EnduranceLogger.phase(Phase.CLEANUP, activePlayer, session.getQuest().getQuestId(),
                "Restore complete: localRestore=%s, recoveryResult=%s, finalSuccess=%s",
                restored, recoveryResult, restoreSuccess);

            if (!session.isPracticeMode()) {
                EnduranceTelemetryService.INSTANCE.recordInventoryRestore(
                    session.getQuest().getQuestId(),
                    restoreSuccess,
                    recoveryResult == InstanceRecoveryResult.RECOVERED
                );
            }
            session.transitionTo(
                success
                    ? EnduranceQuestManager.ActiveQuestSession.LifecycleState.COMPLETED
                    : EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED,
                reason
            );
        } catch (Exception e) {
            EnduranceLogger.error(player, session.getQuest().getQuestId(),
                "Failed to restore player state: %s", e.getMessage());
            LOGGER.error("[EnduranceQuest] Failed to restore player state after quest ({})", reason, e);
            session.transitionTo(EnduranceQuestManager.ActiveQuestSession.LifecycleState.FAILED, reason);
        }
    }

    private enum InstanceRecoveryResult {
        NOT_APPLICABLE,
        RECOVERY_PENDING,
        RECOVERED,
        NO_SNAPSHOT
    }
}

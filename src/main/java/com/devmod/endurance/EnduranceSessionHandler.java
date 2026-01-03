package com.devmod.endurance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.party.QuestSequencePayload;
import com.devmod.runtime.DynamicDimensionManager;
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
                EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
                    QuestSequencePayload.Phase.CANCELLED, 0);
                player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Quest cancelled.")
                    .withStyle(SharedColorTokens.Chat.YELLOW)));
                return;
            }

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

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "abandoned");

            // Update stats
            persistence.updatePlayerStats(playerId, session.getQuest(), false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

            // Notify player BEFORE teleport (message will still be visible)
            player.sendSystemMessage(Objects.requireNonNull(I18n.translate("devmod.endurance.quest_abandoned",
                session.getQuest().getCurrentWave(), session.getQuest().getPointsEarnedThisSession())
                .withStyle(SharedColorTokens.Chat.YELLOW)));

            // === NOW do the state restoration and cleanup ===
            restoreAndCleanup(player, session, false, "Quest abandoned");

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
            session.clearPendingWaveStart();
            session.setRespawnRequested(false);
            session.setRespawnCountdownActive(false);

            // Don't remove session immediately - allow respawn option
            session.setAwaitingRespawnChoice(true);

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
                if (player.isDeadOrDying()) {
                    session.setRespawnRequested(true);
                    session.setAwaitingRespawnChoice(false);
                    LOGGER.info("[EnduranceQuest] Player {} accepted respawn; waiting for vanilla respawn event",
                        player.getName().getString());
                    return;
                }

                continueQuestAfterRespawn(player, session);
            } else {
                // End quest
                activeSessions.remove(playerId);

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

                // INTEGRATION: End telemetry dungeon session BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "death_give_up");

                persistence.updatePlayerStats(playerId, session.getQuest(), false);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

                // === NOW do the state restoration and cleanup ===
                restoreAndCleanup(player, session, false, "Quest ended");

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
        if (!session.isAwaitingRespawnChoice() && !session.isRespawnRequested()) {
            return;
        }

        continueQuestAfterRespawn(player, session);
    }

    private void continueQuestAfterRespawn(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        // Reset wave state before respawn to avoid stale mobs/state.
        EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);
        session.resetWaveKills();

        boolean teleported = false;

        // Teleport back to arena (instance-only flow)
        if (session.isInInstanceDimension()) {
            UUID instanceId = session.getInstanceId();
            if (instanceId != null) {
                teleported = DynamicDimensionManager.INSTANCE.teleportToInstance(player, instanceId);
                if (teleported && session.getArena() != null) {
                    teleported = !EnduranceQuestManager.INSTANCE.teleportPlayersToArena(
                        List.of(player), session.getArena(), session.getArenaHandle()).isEmpty();
                }
            }
        }

        if (!teleported) {
            LOGGER.error("[EnduranceQuest] Cannot respawn player {} - arena/instance unavailable",
                player.getName().getString());
            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                "[DevMod] Respawn failed - arena is unavailable.")
                .withStyle(SharedColorTokens.Chat.RED)));
            return;
        }

        cleanupDroppedItems(session);
        EndurancePlayerStateManager.INSTANCE.resetQuestLoadout(player, session);

        // Continue from current wave with death penalty
        session.getQuest().continueAfterDeath();
        session.setAwaitingRespawnChoice(false);
        session.setRespawnRequested(false);

        // Delay wave start to give time for instance load
        session.scheduleSafeWindow(EnduranceQuestManager.SAFE_WINDOW_TICKS);
        session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
        session.setRespawnCountdownActive(true);
        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session, QuestSequencePayload.Phase.SAFE_WINDOW,
            (int) Math.ceil(EnduranceQuestManager.SAFE_WINDOW_TICKS / 20.0),
            session.getQuest().getDisplayName(),
            "Safe window",
            List.of("Invulnerability active"));

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
                activeSessions.remove(player.getUUID());

                // Cleanup config overrides for this quest
                EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

                // Cleanup wave state and boss fight systems FIRST
                EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

                // Cleanup subsystems and award full rewards BEFORE teleport
                EnduranceEventHandler.onQuestEnd(player, session, true);

                // INTEGRATION: End telemetry dungeon session with success BEFORE teleport
                TelemetryService.INSTANCE.endDungeonSession(player, "completed");

                persistence.updatePlayerStats(player.getUUID(), session.getQuest(), true);

                // Send empty sync to clear client HUD
                PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

                cancelSoloSequence(player, session);

                LOGGER.info("[EnduranceQuest] Player {} COMPLETED quest: {}!",
                    player.getName().getString(), session.getQuest().getDisplayName());

                // === NOW do the state restoration and cleanup ===
                restoreAndCleanup(player, session, true, "Quest completed");
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
            session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
            session.setRespawnCountdownActive(false);

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
            // Cleanup config overrides for this quest
            EnduranceConfigManager.INSTANCE.cleanupQuest(session.getQuest().getQuestId());

            // Cleanup wave state and boss fight systems FIRST
            EndurancePlayerStateManager.INSTANCE.cleanupQuestSystems(session);

            // Cleanup subsystems and award partial rewards BEFORE teleport
            EnduranceEventHandler.onQuestEnd(player, session, false);

            // INTEGRATION: End telemetry dungeon session BEFORE teleport
            TelemetryService.INSTANCE.endDungeonSession(player, "checkpoint_exit");

            persistence.updatePlayerStats(playerId, session.getQuest(), false);

            // Send empty sync to clear client HUD
            PacketDistributor.sendToPlayer(player, Objects.requireNonNull(QuestSyncPayload.empty()));

            cancelSoloSequence(player, session);

            LOGGER.info("[EnduranceQuest] Player {} exited at checkpoint (wave {})",
                player.getName().getString(), session.getQuest().getCurrentWave());

            // === NOW do the state restoration and cleanup ===
            restoreAndCleanup(player, session, false, "Quest exited");
        }
    }

    private void cancelSoloSequence(ServerPlayer player, EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null || player == null) {
            return;
        }
        session.clearAllSequences();
        EnduranceQuestManager.INSTANCE.sendSoloSequenceUpdate(player, session,
            QuestSequencePayload.Phase.CANCELLED, 0);
    }

    private boolean ensureInstanceRecovery(ServerPlayer player,
                                           EnduranceQuestManager.ActiveQuestSession session,
                                           String reason) {
        if (session == null || player == null || !session.isInInstanceDimension()) {
            return false;
        }
        UUID playerId = player.getUUID();
        return RecoverySystem.INSTANCE.loadSnapshot(playerId)
            .map(snapshot -> {
                RecoverySystem.INSTANCE.performRecovery(player, snapshot, reason);
                return true;
            })
            .orElse(false);
    }

    private void restoreAndCleanup(ServerPlayer player,
                                   EnduranceQuestManager.ActiveQuestSession session,
                                   boolean success,
                                   String reason) {
        boolean restored = EndurancePlayerStateManager.INSTANCE.restorePlayerAfterQuest(player, session);
        EndurancePlayerStateManager.INSTANCE.cleanupArenaOrInstance(session, success);
        boolean fallbackUsed = ensureInstanceRecovery(player, session, reason);
        boolean restoreSuccess = restored || session.isInInstanceDimension();
        EnduranceTelemetryService.INSTANCE.recordInventoryRestore(
            session.getQuest().getQuestId(),
            restoreSuccess,
            fallbackUsed
        );
    }
}

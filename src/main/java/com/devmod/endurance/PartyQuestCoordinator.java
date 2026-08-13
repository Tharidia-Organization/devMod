package com.devmod.endurance;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.debug.DiagnosticLogger;
import com.devmod.endurance.EnduranceQuestManager.ActiveQuestSession;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.endurance.services.InstanceServicesFacade;
import com.devmod.endurance.services.PlayerStateServicesFacade;
import com.devmod.runtime.InstanceData;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.TelemetryService;

/**
 * Coordinates party quest sessions: registration, member management, wave advancement,
 * spectator/rejoin logic, and party run termination.
 * Extracted from EnduranceQuestManager to separate party coordination from core quest logic.
 */
class PartyQuestCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyQuestCoordinator.class);

    // Active party quests (party UUID -> party session)
    private final Map<UUID, PartyQuestSession> partySessions = new ConcurrentHashMap<>();
    // Quest UUID -> party UUID lookup for shared party runs
    private final Map<UUID, UUID> questToParty = new ConcurrentHashMap<>();
    // Lock for atomic party session registration/removal
    private final Object partySessionLock = new Object();

    // Reference to parent manager's active sessions and arena setup
    private final Map<UUID, ActiveQuestSession> activeSessions;
    private final ArenaSetupManager arenaSetup;

    PartyQuestCoordinator(Map<UUID, ActiveQuestSession> activeSessions, ArenaSetupManager arenaSetup) {
        this.activeSessions = activeSessions;
        this.arenaSetup = arenaSetup;
    }

    // ========== Session CRUD ==========

    PartyQuestSession registerPartySession(PartyQuestSession session) {
        if (session == null) {
            return null;
        }
        synchronized (partySessionLock) {
            partySessions.put(session.getPartyId(), session);
            questToParty.put(session.getQuestId(), session.getPartyId());
        }
        var party = com.devmod.party.PartyManager.INSTANCE.getParty(session.getPartyId());
        if (party != null && party.getState() != com.devmod.party.PartyData.PartyState.IN_QUEST) {
            com.devmod.party.PartyManager.INSTANCE.forceStartQuest(session.getPartyId(), session.getInstanceId());
        }
        updatePartyPlayerCount(session);
        return session;
    }

    Optional<PartyQuestSession> getPartySession(UUID partyId) {
        return Optional.ofNullable(partySessions.get(partyId));
    }

    Optional<PartyQuestSession> getPartySessionByPlayer(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return Optional.empty();
        }
        return getPartySession(session.getPartyId());
    }

    Optional<PartyQuestSession> getPartySessionByQuest(UUID questId) {
        UUID partyId = questToParty.get(questId);
        if (partyId == null) {
            return Optional.empty();
        }
        return getPartySession(partyId);
    }

    boolean isPartyQuest(UUID questId) {
        return questToParty.containsKey(questId);
    }

    void removePartySession(UUID partyId) {
        synchronized (partySessionLock) {
            PartyQuestSession session = partySessions.remove(partyId);
            if (session != null) {
                questToParty.remove(session.getQuestId());
            }
        }
    }

    boolean isPartyRunActiveForPlayer(UUID playerId) {
        return getPartySessionByPlayer(playerId).map(PartyQuestSession::isActive).orElse(false);
    }

    void clearAll() {
        partySessions.clear();
        questToParty.clear();
    }

    // ========== Wave Management ==========

    void completePartyWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.IN_PROGRESS) {
            return;
        }

        quest.completeWave();
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            endPartyRun(partySession, true, "completed");
        }
    }

    void requestPartyContinue(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        partySession.markWaveReady(playerId);
        syncPartyState(partySession.getPartyId());
        if (partySession.isReadyForNextWave()) {
            advancePartyToNextWave(partySession);
        }
    }

    void advancePartyToNextWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        int waveNumber = quest.getCurrentWave();
        if (!partySession.markWaveAdvance(waveNumber)) {
            return;
        }
        partySession.clearWaveReady();
        UUID arenaId = partySession.getArenaId();
        if (arenaId != null) {
            WaveManager.INSTANCE.clearCompletedWaveState(arenaId);
        }
        quest.continueToNextWave();
        updatePartyPlayerCount(partySession);

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession session = activeSessions.get(memberId);
            if (session == null) {
                continue;
            }
            session.resetWaveKills();
            session.scheduleWaveStart(EnduranceQuestManager.WAVE_START_COUNTDOWN_TICKS);
            session.setRespawnCountdownActive(false);

            if (session.isPartySpectator() && server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(memberId));
                if (player != null) {
                    rejoinPartyMember(player);
                }
            }
        }
    }

    // ========== Member State Management ==========

    void markPartyMemberInactive(UUID playerId, String reason) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        session.setPartySpectator(true);
        partySession.markSpectator(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());

        if (partySession.isWiped()) {
            EnduranceQuest quest = partySession.getQuest();
            quest.fail(false);
            endPartyRun(partySession, false, reason != null ? reason : "party_wipe");
            return;
        }
        tryAdvancePartyWave(partySession);
    }

    void markPartyMemberActive(UUID playerId) {
        ActiveQuestSession session = activeSessions.get(playerId);
        if (session == null || session.getPartyId() == null) {
            return;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        session.setPartySpectator(false);
        partySession.markActive(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());
    }

    void updatePartyPlayerCount(PartyQuestSession partySession) {
        if (partySession == null) {
            return;
        }
        int playerCount = Math.max(1, partySession.getActiveMemberCount());
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession session = activeSessions.get(memberId);
            if (session != null) {
                session.setPlayerCount(playerCount);
            }
        }
    }

    private void tryAdvancePartyWave(PartyQuestSession partySession) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        EnduranceQuest quest = partySession.getQuest();
        if (quest.getState() != EnduranceQuestState.WAVE_COMPLETE) {
            return;
        }
        if (!partySession.isReadyForNextWave()) {
            return;
        }
        advancePartyToNextWave(partySession);
    }

    private void syncPartyState(UUID partyId) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partyId);
        }
    }

    // ========== Attach / Rejoin ==========

    boolean attachPartyMemberSession(ServerPlayer player, PartyQuestSession partySession) {
        if (player == null || partySession == null || !partySession.isActive()) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (activeSessions.containsKey(playerId)) {
            return false;
        }

        ActiveQuestSession templateSession = null;
        for (UUID memberId : partySession.getMembers()) {
            ActiveQuestSession existing = activeSessions.get(memberId);
            if (existing != null) {
                templateSession = existing;
                break;
            }
        }
        ArenaContext arena = templateSession != null ? templateSession.getArena() : null;
        ArenaHandle arenaHandle = templateSession != null ? templateSession.getArenaHandle() : null;
        if (arena == null) {
            arena = partySession.getArena();
        }
        if (arenaHandle == null) {
            arenaHandle = partySession.getArenaHandle();
        }
        final ArenaHandle finalArenaHandle = arenaHandle;
        if (arena == null || arenaHandle == null) {
            return false;
        }

        ActiveQuestSession placeholder = new ActiveQuestSession(playerId, partySession.getQuest(), null, System.currentTimeMillis());
        ActiveQuestSession existing = activeSessions.putIfAbsent(playerId, placeholder);
        if (existing != null) {
            return false;
        }

        UUID instanceId = partySession.getInstanceId();
        if (instanceId != null && !prepareLateJoinInstance(player, instanceId, partySession)) {
            activeSessions.remove(playerId);
            return false;
        }

        EnduranceQuest quest = partySession.getQuest();
        ActiveQuestSession session = new ActiveQuestSession(
            playerId,
            quest,
            arena,
            System.currentTimeMillis(),
            partySession.getPartyId(),
            partySession.getQuestType(),
            Math.max(1, partySession.getActiveMemberCount())
        );
        if (instanceId != null) {
            session.setInstanceId(instanceId);
        }
        session.setArenaHandle(arenaHandle);
        PlayerStateServicesFacade.INSTANCE.loadSnapshot(player.getUUID()).ifPresent(snapshot -> {
            snapshot.withArenaTemplate(
                finalArenaHandle.templateId(),
                finalArenaHandle.templateVersion(),
                finalArenaHandle.policyId(),
                finalArenaHandle.policyVersion()
            );
            PlayerStateServicesFacade.INSTANCE.saveSnapshot(snapshot);
        });
        if (templateSession != null) {
            session.setDifficultyLabel(templateSession.getDifficultyLabel());
            session.setQuestTypeLabel(templateSession.getQuestTypeLabel());
            session.setKitId(templateSession.getKitId());
            session.setPracticeMode(templateSession.isPracticeMode());
            for (var entry : templateSession.getConfigOverrides().entrySet()) {
                session.setConfigOverride(entry.getKey(), entry.getValue());
            }
            session.setMobPoolConfig(templateSession.getMobPoolConfig());
        }
        session.transitionTo(ActiveQuestSession.LifecycleState.ACTIVE, "party member attached");
        session.setPartySpectator(true);
        activeSessions.put(playerId, session);

        // Apply arena overrides - delegate to the manager which owns the serialization logic
        EnduranceQuestManager.INSTANCE.applyAndSyncArenaOverrides(player, session);
        PlayerStateServicesFacade.INSTANCE.preparePlayerForQuest(player, session);
        EnduranceEventHandler.onQuestStart(player, session);
        if (!session.isPracticeMode()) {
            String dungeonId = "endurance_party_" + quest.getMobId().toString().replace(":", "_");
            TelemetryService.INSTANCE.startDungeonSession(player, dungeonId);
        }

        partySession.markSpectator(playerId);
        updatePartyPlayerCount(partySession);
        syncPartyState(partySession.getPartyId());
        return true;
    }

    private boolean prepareLateJoinInstance(ServerPlayer player, UUID instanceId, PartyQuestSession partySession) {
        Optional<InstanceData> instanceOpt = InstanceServicesFacade.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            return false;
        }
        InstanceData instance = instanceOpt.get();
        if (!instance.addPlayer(player.getUUID())) {
            return false;
        }
        InstanceServicesFacade.INSTANCE.save();
        var snapshot = PlayerStateServicesFacade.INSTANCE.createSnapshotFromPlayer(player, instance);
        snapshot.setState(com.devmod.runtime.PlayerInstanceState.PREPARING);
        var party = com.devmod.party.PartyManager.INSTANCE.getParty(partySession.getPartyId());
        if (party != null) {
            snapshot.setPartyLeaderId(party.getLeaderId());
            snapshot.setPartyMembers(new HashSet<>(party.getMembers()));
        } else {
            snapshot.setPartyMembers(new HashSet<>(partySession.getMembers()));
        }
        PlayerStateServicesFacade.INSTANCE.saveSnapshot(snapshot);
        InstanceServicesFacade.INSTANCE.mapPlayer(player.getUUID(), instanceId);
        return true;
    }

    boolean rejoinPartyMember(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ActiveQuestSession session = activeSessions.get(player.getUUID());
        if (session == null || session.getPartyId() == null) {
            return false;
        }
        PartyQuestSession partySession = partySessions.get(session.getPartyId());
        if (partySession == null || !partySession.isActive()) {
            return false;
        }
        if (!session.isPartySpectator()) {
            return true;
        }

        boolean teleported = EnduranceQuestManager.INSTANCE.teleportPlayerToArena(
            player,
            session,
            true,
            session.isInInstanceDimension()
        );

        if (teleported) {
            player.setGameMode(GameType.SURVIVAL);
            PlayerStateServicesFacade.INSTANCE.resetQuestLoadout(player, session);
            PlayerStateServicesFacade.INSTANCE.applySafeWindowEffects(player, EnduranceQuestManager.SAFE_WINDOW_TICKS);
            markPartyMemberActive(player.getUUID());
            player.sendSystemMessage(Objects.requireNonNull(
                net.minecraft.network.chat.Component.literal("[DevMod] Rejoined party run.")
                    .withStyle(SharedColorTokens.Chat.GREEN)));
        }

        return teleported;
    }

    // ========== End Party Run ==========

    void endPartyRun(PartyQuestSession partySession, boolean completed, String reason) {
        if (partySession == null || !partySession.isActive()) {
            return;
        }
        DiagnosticLogger.quest("endPartyRun: partyId=%s, completed=%s, reason=%s, members=%d",
            partySession.getPartyId(), completed, reason, partySession.getMembers().size());

        partySession.end(completed ? PartyQuestSession.Status.COMPLETED : PartyQuestSession.Status.FAILED);

        UUID questId = partySession.getQuestId();
        ActiveQuestSession cleanupSession = null;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        try {
            for (UUID memberId : partySession.getMembers()) {
                ActiveQuestSession session = activeSessions.remove(memberId);
                if (session == null) {
                    continue;
                }
                if (cleanupSession == null) {
                    cleanupSession = session;
                }
                ServerPlayer player = server != null ? server.getPlayerList().getPlayer(Objects.requireNonNull(memberId)) : null;

                if (player != null) {
                    try {
                        EnduranceEventHandler.onQuestEnd(player, session, completed);
                    } catch (Exception e) {
                        LOGGER.error("[EnduranceQuest] Failed onQuestEnd for party member {}",
                            player.getName().getString(), e);
                    }
                    if (!session.isPracticeMode()) {
                        try {
                            TelemetryService.INSTANCE.endDungeonSession(
                                player, completed ? "completed" : (reason != null ? reason : "failed"));
                        } catch (Exception e) {
                            LOGGER.warn("[EnduranceQuest] Failed to end telemetry session for party member {}",
                                player.getName().getString(), e);
                        }
                    }
                    try {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player, Objects.requireNonNull(QuestSyncPayload.empty()));
                    } catch (Exception e) {
                        LOGGER.warn("[EnduranceQuest] Failed to sync quest end to party member {}",
                            player.getName().getString(), e);
                    }
                    try {
                        PlayerStateServicesFacade.INSTANCE.restorePlayerAfterQuest(player, session);
                    } catch (Exception e) {
                        LOGGER.warn("[EnduranceQuest] Failed to restore party member {} state",
                            player.getName().getString(), e);
                    }
                } else {
                    // Offline member: onQuestEnd (and therefore the QuestEnded event) cannot run
                    // without a ServerPlayer, so drop their per-player subsystem state here or it
                    // is carried into their next quest.
                    try {
                        PerkSystem.INSTANCE.endSession(memberId);
                        MomentumTracker.INSTANCE.endSession(memberId);
                        com.devmod.endurance.combat.ComboSystemFacade.get().endSession(memberId);
                    } catch (Exception e) {
                        LOGGER.warn("[EnduranceQuest] Failed to clean up offline party member {}", memberId, e);
                    }
                }
            }

            try {
                EnduranceConfigManager.INSTANCE.cleanupQuest(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to cleanup quest config for party run {}", questId, e);
            }
            try {
                EnduranceEventCombat.removeMutatorSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to remove mutator session for party run {}", questId, e);
            }
            try {
                MutatorSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end mutator session for party run {}", questId, e);
            }
            try {
                CombatTracker.INSTANCE.stopTracking(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to stop combat tracking for party run {}", questId, e);
            }
            try {
                TensionSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end tension session for party run {}", questId, e);
            }
            try {
                com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end bargain session for party run {}", questId, e);
            }
            try {
                com.devmod.endurance.hazard.ArenaHazardSystem.INSTANCE.endSession(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end hazard session for party run {}", questId, e);
            }
            try {
                DirectiveChainManager.INSTANCE.endChain(questId);
            } catch (Exception e) {
                LOGGER.warn("[EnduranceQuest] Failed to end directive chain for party run {}", questId, e);
            }
        } finally {
            if (cleanupSession != null) {
                try {
                    PlayerStateServicesFacade.INSTANCE.cleanupQuestSystems(cleanupSession);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceQuest] Failed to cleanup quest systems for party run {}", questId, e);
                }
                try {
                    PlayerStateServicesFacade.INSTANCE.cleanupArenaOrInstance(cleanupSession, completed);
                } catch (Exception e) {
                    LOGGER.warn("[EnduranceQuest] Failed to cleanup instance for party run {}", questId, e);
                }
            } else {
                UUID instanceId = partySession.getInstanceId();
                if (instanceId != null) {
                    InstanceServicesFacade.INSTANCE.safeCleanup(instanceId, completed);
                }
            }
        }

        com.devmod.party.PartyManager.INSTANCE.finishQuest(Objects.requireNonNull(partySession.getPartyId()));
        if (server != null) {
            var party = com.devmod.party.PartyManager.INSTANCE.getParty(Objects.requireNonNull(partySession.getPartyId()));
            if (party != null) {
                UUID leaderId = party.getLeaderId();
                ServerPlayer leaderPlayer = server.getPlayerList().getPlayer(Objects.requireNonNull(leaderId));
                if (leaderPlayer == null) {
                    UUID newLeader = party.getMembers().stream()
                        .filter(id -> !id.equals(leaderId))
                        .filter(id -> server.getPlayerList().getPlayer(Objects.requireNonNull(id)) != null)
                        .findFirst()
                        .orElse(null);
                    if (newLeader != null) {
                        com.devmod.party.PartyManager.INSTANCE.transferLeadership(leaderId, newLeader);
                    }
                }
            }
            com.devmod.network.handlers.PartyNetworkHandler.syncPartyToAllMembers(server, partySession.getPartyId());
        }

        removePartySession(partySession.getPartyId());
        LOGGER.info("[EnduranceQuest] Party run ended partyId={} questId={} completed={}",
            partySession.getPartyId(), questId, completed);
    }
}

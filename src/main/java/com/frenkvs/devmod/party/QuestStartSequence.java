package com.frenkvs.devmod.party;

import com.devmod.arena.api.ArenaHandle;
import com.frenkvs.devmod.endurance.EnduranceQuestManager;
import com.frenkvs.devmod.endurance.InstanceArenaManager;
import com.frenkvs.devmod.endurance.QuestType;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the quest start sequence for party quests.
 *
 * FLOW:
 * 1. PRE-VALIDATION: Check all members (online, not in combat, not dead, etc.)
 * 2. COUNTDOWN: 5-4-3-2-1 countdown with cancel option
 * 3. TELEPORT: Teleport all members to arena simultaneously
 * 4. WAITING_FOR_ARRIVALS: Wait for all clients to confirm arrival (timeout 30s)
 * 5. WAVE_COUNTDOWN: 3-2-1 before starting wave 1
 * 6. STARTED: Begin the quest
 */
public class QuestStartSequence {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestStartSequence.class);

    // Singleton
    public static final QuestStartSequence INSTANCE = new QuestStartSequence();

    // Active sequences by party ID
    private final Map<UUID, ActiveSequence> activeSequences = new ConcurrentHashMap<>();

    // Countdown durations (in ticks, 20 ticks = 1 second)
    private static final int PRE_TELEPORT_COUNTDOWN_TICKS = 100; // 5 seconds
    private static final int ARRIVAL_TIMEOUT_TICKS = 600;        // 30 seconds timeout
    private static final int WAVE_COUNTDOWN_TICKS = 60;          // 3 seconds
    private static final int TICK_INTERVAL = 20;                 // 1 second

    private QuestStartSequence() {}

    /**
     * Start the quest sequence for a party.
     * Returns validation result - if failed, sequence does not start.
     */
    public ValidationResult startSequence(MinecraftServer server, PartyData party, ServerPlayer leader) {
        UUID partyId = party.getPartyId();

        // Check if sequence already running
        if (activeSequences.containsKey(partyId)) {
            return ValidationResult.failure("devmod.party.sequence_already_running");
        }

        // Run pre-validation
        ValidationResult validation = validatePartyForQuest(server, party);
        if (!validation.success) {
            return validation;
        }

        // Create and start sequence
        ActiveSequence sequence = new ActiveSequence(partyId, party.getQuestType(), validation.onlineMembers, leader.getUUID());
        activeSequences.put(partyId, sequence);

        // Notify all members that countdown is starting
        broadcastSequenceUpdate(sequence);

        LOGGER.info("[QuestSequence] Started sequence for party {} with {} members", partyId, sequence.members.size());

        return ValidationResult.success(validation.onlineMembers);
    }

    /**
     * Cancel the sequence (called by leader or system).
     */
    public boolean cancelSequence(UUID partyId, UUID requesterId, String reason) {
        ActiveSequence sequence = activeSequences.get(partyId);
        if (sequence == null) {
            return false;
        }

        // Only leader can cancel manually
        if (requesterId != null && !requesterId.equals(sequence.leaderId)) {
            LOGGER.warn("[QuestSequence] Non-leader {} tried to cancel sequence for party {}", requesterId, partyId);
            return false;
        }

        // Can only cancel during countdown phase
        if (sequence.phase != QuestSequencePayload.Phase.COUNTDOWN_START) {
            LOGGER.warn("[QuestSequence] Cannot cancel sequence in phase {}", sequence.phase);
            return false;
        }

        activeSequences.remove(partyId);

        // Cleanup arena if it was created
        cleanupPreparedArena(sequence);

        // Notify all members
        sequence.phase = QuestSequencePayload.Phase.CANCELLED;
        broadcastSequenceUpdate(sequence);

        // Send cancellation message
        for (ServerPlayer member : sequence.members) {
            if (member != null && member.isAlive()) {
                member.sendSystemMessage(I18n.translate("devmod.party.sequence_cancelled", reason));
            }
        }

        LOGGER.info("[QuestSequence] Cancelled sequence for party {}: {}", partyId, reason);
        return true;
    }

    /**
     * Cleanup prepared arena when sequence is cancelled or fails.
     */
    private void cleanupPreparedArena(ActiveSequence sequence) {
        UUID instanceId = sequence.instanceId;
        if (instanceId == null && sequence.preparedArenaHandle != null) {
            instanceId = sequence.preparedArenaHandle.instanceId();
        }
        if (instanceId != null) {
            LOGGER.info("[QuestSequence] Ending instance {} due to sequence cancel/failure",
                instanceId);
            InstanceArenaManager.INSTANCE.endInstanceQuest(instanceId, false);
            sequence.instanceId = null;
        } else if (sequence.preparedArena != null) {
            LOGGER.error("[QuestSequence] Legacy arena cleanup invoked; instanceId missing for arena {}",
                sequence.preparedArena.getId());
            sequence.preparedArena = null;
        }
        sequence.preparedArenaHandle = null;
    }

    /**
     * Confirm player arrival in arena.
     * Verifies player is actually inside the arena bounds before confirming.
     */
    public void confirmArrival(UUID partyId, UUID playerId, MinecraftServer server) {
        ActiveSequence sequence = activeSequences.get(partyId);
        if (sequence == null) {
            LOGGER.warn("[QuestSequence] Arrival confirm for unknown party: {}", partyId);
            return;
        }

        if (sequence.phase != QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS) {
            LOGGER.debug("[QuestSequence] Arrival confirm in wrong phase: {}", sequence.phase);
            return;
        }

        // Get the player
        ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
        if (player == null) {
            LOGGER.warn("[QuestSequence] Arrival confirm for offline player: {}", playerId);
            return;
        }

        // Verify player is actually in the arena
        if (sequence.preparedArena == null) {
            LOGGER.warn("[QuestSequence] No prepared arena for arrival check");
            return;
        }

        if (!EnduranceQuestManager.INSTANCE.isPlayerInArena(player, sequence.preparedArena)) {
            LOGGER.warn("[QuestSequence] Player {} sent arrival confirm but is NOT in arena bounds!",
                player.getName().getString());
            // Don't confirm - player might be cheating or lagging
            return;
        }

        if (sequence.arrivedMembers.add(playerId)) {
            LOGGER.info("[QuestSequence] Player {} confirmed arrival ({}/{}) - position verified",
                player.getName().getString(), sequence.arrivedMembers.size(), sequence.members.size());

            // Broadcast updated arrival status
            broadcastSequenceUpdate(sequence);

            // Check if all arrived
            if (sequence.arrivedMembers.size() >= sequence.members.size()) {
                LOGGER.info("[QuestSequence] All members arrived! Starting wave countdown");
                sequence.phase = QuestSequencePayload.Phase.SYNCING;
                sequence.ticksRemaining = WAVE_COUNTDOWN_TICKS;
                broadcastSequenceUpdate(sequence);
            }
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use confirmArrival(UUID, UUID, MinecraftServer) instead
     */
    @Deprecated
    public void confirmArrival(UUID partyId, UUID playerId) {
        LOGGER.warn("[QuestSequence] Using deprecated confirmArrival without server - cannot verify position");
        ActiveSequence sequence = activeSequences.get(partyId);
        if (sequence == null) return;

        // Still allow but log warning
        if (sequence.phase == QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS) {
            if (sequence.arrivedMembers.add(playerId)) {
                broadcastSequenceUpdate(sequence);
                if (sequence.arrivedMembers.size() >= sequence.members.size()) {
                    sequence.phase = QuestSequencePayload.Phase.SYNCING;
                    sequence.ticksRemaining = WAVE_COUNTDOWN_TICKS;
                    broadcastSequenceUpdate(sequence);
                }
            }
        }
    }

    /**
     * Validate party before starting quest.
     * Checks: all online, not in combat, not dead, minimum players.
     */
    public ValidationResult validatePartyForQuest(MinecraftServer server, PartyData party) {
        List<ServerPlayer> onlineMembers = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        QuestType questType = party.getQuestType();

        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(Objects.requireNonNull(memberId));

            if (member == null) {
                String name = party.getMemberName(memberId);
                issues.add(I18n.translate("devmod.party.validation.offline", name != null ? name : "Unknown").getString());
                continue;
            }

            // Check if player is dead
            if (member.isDeadOrDying()) {
                issues.add(I18n.translate("devmod.party.validation.dead", member.getName().getString()).getString());
                continue;
            }

            // Check if player is in combat (took damage in last 5 seconds)
            if (member.getLastHurtByMobTimestamp() > 0 &&
                (server.getTickCount() - member.getLastHurtByMobTimestamp()) < 100) {
                issues.add(I18n.translate("devmod.party.validation.in_combat", member.getName().getString()).getString());
                continue;
            }

            // Check if player already has an active quest
            if (EnduranceQuestManager.INSTANCE.getActiveSession(member).isPresent()) {
                issues.add(I18n.translate("devmod.party.validation.has_quest", member.getName().getString()).getString());
                continue;
            }

            onlineMembers.add(member);
        }

        // Check minimum player count
        if (onlineMembers.size() < questType.minPlayers) {
            issues.add(I18n.translate("devmod.party.validation.not_enough_players",
                questType.minPlayers, onlineMembers.size()).getString());
        }

        if (!issues.isEmpty()) {
            return ValidationResult.failure(String.join("\n", issues));
        }

        return ValidationResult.success(onlineMembers);
    }

    /**
     * Called every server tick to process active sequences.
     */
    public void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveSequence>> iterator = activeSequences.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveSequence> entry = iterator.next();
            ActiveSequence sequence = entry.getValue();

            sequence.ticksRemaining--;

            // Send countdown updates every second
            if (sequence.ticksRemaining > 0 && sequence.ticksRemaining % TICK_INTERVAL == 0) {
                broadcastSequenceUpdate(sequence);
            }

            // Phase transitions
            if (sequence.ticksRemaining <= 0) {
                switch (sequence.phase) {
                    case COUNTDOWN_START -> {
                        // Countdown finished, do teleport
                        sequence.phase = QuestSequencePayload.Phase.TELEPORTING;
                        broadcastSequenceUpdate(sequence);
                        performTeleport(server, sequence);

                        // Move to waiting for arrivals
                        sequence.phase = QuestSequencePayload.Phase.WAITING_FOR_ARRIVALS;
                        sequence.ticksRemaining = ARRIVAL_TIMEOUT_TICKS;
                        broadcastSequenceUpdate(sequence);
                    }
                    case WAITING_FOR_ARRIVALS -> {
                        // Timeout! Check if we have enough players
                        int arrived = sequence.arrivedMembers.size();
                        int total = sequence.members.size();

                        if (arrived >= sequence.questType.minPlayers) {
                            // Enough players arrived, continue
                            LOGGER.warn("[QuestSequence] Arrival timeout but {} of {} arrived, proceeding", arrived, total);
                            sequence.phase = QuestSequencePayload.Phase.SYNCING;
                            sequence.ticksRemaining = WAVE_COUNTDOWN_TICKS;
                            broadcastSequenceUpdate(sequence);
                        } else {
                            // Not enough, cancel and cleanup arena
                            LOGGER.error("[QuestSequence] Arrival timeout, only {} of {} arrived, cancelling", arrived, total);

                            // Cleanup the prepared arena since quest won't start
                            cleanupPreparedArena(sequence);

                            sequence.phase = QuestSequencePayload.Phase.CANCELLED;
                            broadcastSequenceUpdate(sequence);

                            for (ServerPlayer member : sequence.members) {
                                if (member != null && member.isAlive()) {
                                    member.sendSystemMessage(I18n.translate("devmod.party.arrival_timeout"));
                                }
                            }
                            iterator.remove();
                        }
                    }
                    case SYNCING -> {
                        // Wave countdown finished, start quest!
                        sequence.phase = QuestSequencePayload.Phase.STARTING;
                        broadcastSequenceUpdate(sequence);
                        startQuestForAll(server, sequence);

                        // Final notification
                        sequence.phase = QuestSequencePayload.Phase.STARTED;
                        broadcastSequenceUpdate(sequence);
                        iterator.remove();
                    }
                    default -> iterator.remove();
                }
            }
        }
    }

    /**
     * Teleport all members to the arena.
     * Uses the new separated flow: prepareArena -> teleport -> (later) startQuest
     */
    private void performTeleport(MinecraftServer server, ActiveSequence sequence) {
        LOGGER.info("[QuestSequence] Teleporting {} members for party {}", sequence.members.size(), sequence.partyId);

        // Verify we have members
        if (sequence.members.isEmpty()) {
            LOGGER.error("[QuestSequence] No members to teleport!");
            return;
        }

        // Create quest settings
        List<UUID> memberIds = sequence.members.stream().map(ServerPlayer::getUUID).toList();
        EnduranceQuestManager.QuestSettings settings = new EnduranceQuestManager.QuestSettings();
        settings.totalWaves = (int) (10 * sequence.questType.difficultyMultiplier);
        settings.endlessMode = false;
        settings.arenaSize = sequence.questType.defaultArenaSize;
        settings.party(sequence.partyId, new ArrayList<>(memberIds));
        settings.questType = sequence.questType;

        // Store settings for later use
        sequence.questSettings = settings;

        // Get the leader (first member or specific leader)
        ServerPlayer leader = sequence.members.stream()
            .filter(p -> p.getUUID().equals(sequence.leaderId))
            .findFirst()
            .orElse(sequence.members.get(0));

        // Get mob type from party settings (falls back to zombie if not set)
        PartyData party = PartyManager.INSTANCE.getParty(sequence.partyId);
        ResourceLocation mobId = party != null ? party.getEffectiveMobId() : ResourceLocation.withDefaultNamespace("zombie");

        // PHASE 1: Prepare arena (create without teleporting)
        EnduranceQuestManager.PreparedArenaResult arenaResult =
            EnduranceQuestManager.INSTANCE.prepareArenaForParty(leader, mobId, settings);

        if (!arenaResult.success()) {
            LOGGER.error("[QuestSequence] Failed to prepare arena: {}", arenaResult.errorMessage());
            // Cancel the sequence
            sequence.phase = QuestSequencePayload.Phase.CANCELLED;
            broadcastSequenceUpdate(sequence);
            for (ServerPlayer member : sequence.members) {
                if (member != null && member.isAlive()) {
                    // Prefer specific reason (e.g., instance mode unsupported for party)
                    String reason = arenaResult.errorMessage() != null
                        ? arenaResult.errorMessage()
                        : I18n.translate("devmod.party.arena_creation_failed").getString();
                    if (reason == null) reason = "Arena creation failed";
                    member.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(reason)));
                }
            }
            return;
        }

        // Store arena and mob info for later quest start
        sequence.preparedArena = arenaResult.arena();
        sequence.preparedArenaHandle = arenaResult.handle();
        sequence.mobId = mobId;
        sequence.instanceId = arenaResult.instanceId();

        LOGGER.info("[QuestSequence] Arena {} prepared, teleporting players", arenaResult.arena().getId());

        // PHASE 2: Teleport handling
        if (!EnduranceQuestManager.INSTANCE.isUseInstanceDimensions()) {
            EnduranceQuestManager.INSTANCE.teleportPlayersToArena(sequence.members, arenaResult.arena());
            for (ServerPlayer member : sequence.members) {
                if (member != null && member.isAlive()) {
                    member.sendSystemMessage(I18n.translate("devmod.party.teleported_to_arena"));
                }
            }
        } else {
            // InstanceManager already teleported; align to template spawns if available
            if (arenaResult.handle() != null) {
                EnduranceQuestManager.INSTANCE.teleportPlayersToArena(
                    sequence.members,
                    arenaResult.arena(),
                    arenaResult.handle()
                );
            }
            for (ServerPlayer member : sequence.members) {
                if (member != null && member.isAlive()) {
                    member.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal("[DevMod] Teleporting party to instance...")));
                }
            }
        }
    }

    /**
     * Start the quest for all members using the pre-created arena.
     * Uses startPreparedQuest() which doesn't teleport (players already in arena).
     */
    private void startQuestForAll(MinecraftServer server, ActiveSequence sequence) {
        LOGGER.info("[QuestSequence] Starting quest for party {}", sequence.partyId);

        // Verify we have a prepared arena
        if (sequence.preparedArena == null) {
            LOGGER.error("[QuestSequence] No prepared arena! Cannot start quest.");
            for (ServerPlayer member : sequence.members) {
                if (member != null && member.isAlive()) {
                    member.sendSystemMessage(I18n.translate("devmod.party.quest_start_failed"));
                }
            }
            return;
        }

        // Only start for members who confirmed arrival
        List<ServerPlayer> membersToStart = new ArrayList<>();
        for (ServerPlayer member : sequence.members) {
            if (member != null && member.isAlive()) {
                if (sequence.arrivedMembers.contains(member.getUUID())) {
                    membersToStart.add(member);
                } else {
                    LOGGER.warn("[QuestSequence] Skipping {} - did not confirm arrival", member.getName().getString());
                    member.sendSystemMessage(I18n.translate("devmod.party.missed_teleport"));
                }
            }
        }

        if (membersToStart.isEmpty()) {
            LOGGER.error("[QuestSequence] No members to start quest for!");
            return;
        }

        // Use the new startPreparedQuest which doesn't teleport (already done)
        Map<UUID, EnduranceQuestManager.StartQuestResult> results =
            EnduranceQuestManager.INSTANCE.startPreparedQuest(
                membersToStart,
                sequence.preparedArena,
                sequence.mobId,
                sequence.questSettings,
                sequence.instanceId,
                sequence.preparedArenaHandle
            );

        // Count successes and failures
        int successCount = 0;
        for (Map.Entry<UUID, EnduranceQuestManager.StartQuestResult> entry : results.entrySet()) {
            ServerPlayer member = membersToStart.stream()
                .filter(p -> p.getUUID().equals(entry.getKey()))
                .findFirst()
                .orElse(null);

            if (member == null) continue;

            if (entry.getValue().success()) {
                successCount++;
                member.sendSystemMessage(I18n.translate("devmod.party.quest_started"));
            } else {
                LOGGER.warn("[QuestSequence] Failed to start quest for {}: {}",
                    member.getName().getString(), entry.getValue().message());
                member.sendSystemMessage(I18n.translate("devmod.party.quest_start_failed"));
            }
        }

        // Update party state if at least some members started
        if (successCount > 0) {
            PartyData party = PartyManager.INSTANCE.getParty(sequence.partyId);
            if (party != null) {
                party.startQuest(sequence.partyId);
            }
            LOGGER.info("[QuestSequence] Quest started for {}/{} members", successCount, membersToStart.size());
        }
    }

    /**
     * Broadcast sequence update to all members.
     */
    private void broadcastSequenceUpdate(ActiveSequence sequence) {
        int secondsRemaining = sequence.ticksRemaining / 20;

        // Build arrived members list for UI
        List<UUID> arrivedList = new ArrayList<>(sequence.arrivedMembers);

        QuestSequencePayload payload = new QuestSequencePayload(
            sequence.partyId,
            sequence.phase,
            secondsRemaining,
            sequence.members.size(),
            arrivedList
        );

        for (ServerPlayer member : sequence.members) {
            if (member != null && member.connection != null) {
                PacketDistributor.sendToPlayer(member, payload);
            }
        }
    }

    /**
     * Check if a party has an active sequence.
     */
    public boolean hasActiveSequence(UUID partyId) {
        return activeSequences.containsKey(partyId);
    }

    /**
     * Get current phase for a party's sequence.
     */
    public QuestSequencePayload.Phase getCurrentPhase(UUID partyId) {
        ActiveSequence sequence = activeSequences.get(partyId);
        return sequence != null ? sequence.phase : null;
    }

    /**
     * Handle player disconnect during sequence.
     */
    public void onPlayerDisconnect(UUID playerId) {
        for (Map.Entry<UUID, ActiveSequence> entry : activeSequences.entrySet()) {
            ActiveSequence sequence = entry.getValue();
            boolean hadPlayer = sequence.members.removeIf(p -> p.getUUID().equals(playerId));
            if (hadPlayer) {
                sequence.arrivedMembers.remove(playerId);

                // Check if still have enough players
                if (sequence.members.size() < sequence.questType.minPlayers) {
                    cancelSequence(entry.getKey(), null, "Not enough players");
                } else {
                    // Notify remaining members
                    broadcastSequenceUpdate(sequence);
                }
            }
        }
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * Validation result for party quest start.
     */
    public record ValidationResult(
        boolean success,
        String errorMessage,
        List<ServerPlayer> onlineMembers
    ) {
        public static ValidationResult success(List<ServerPlayer> members) {
            return new ValidationResult(true, null, members);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, List.of());
        }
    }

    /**
     * Active quest start sequence state.
     */
    private static class ActiveSequence {
        final UUID partyId;
        final UUID leaderId;
        final QuestType questType;
        final List<ServerPlayer> members;
        final Set<UUID> arrivedMembers;
        QuestSequencePayload.Phase phase;
        int ticksRemaining;
        EnduranceQuestManager.QuestSettings questSettings;
        UUID instanceId;

        // Pre-created arena (set during TELEPORTING phase)
        com.frenkvs.devmod.endurance.ArenaManager.Arena preparedArena;
        ArenaHandle preparedArenaHandle;
        ResourceLocation mobId;

        ActiveSequence(UUID partyId, QuestType questType, List<ServerPlayer> members, UUID leaderId) {
            this.partyId = partyId;
            this.leaderId = leaderId;
            this.questType = questType;
            this.members = new ArrayList<>(members);
            this.arrivedMembers = ConcurrentHashMap.newKeySet();
            this.phase = QuestSequencePayload.Phase.COUNTDOWN_START;
            this.ticksRemaining = PRE_TELEPORT_COUNTDOWN_TICKS;
        }
    }
}

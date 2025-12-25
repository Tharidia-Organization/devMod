package com.devmod.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.QuestType;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;
/**
 * Singleton manager for all party operations.
 * Handles party creation, invites, member management, and lifecycle.
 * Thread-safe for server-side concurrent access.
 */
public class PartyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyManager.class);

    /** Singleton instance */
    public static final PartyManager INSTANCE = new PartyManager();

    /** All active parties by party ID */
    private final Map<UUID, PartyData> parties = new ConcurrentHashMap<>();

    /** Player UUID -> Party ID mapping for quick lookup */
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();

    /** Player UUID -> List of pending invites received */
    private final Map<UUID, List<PartyInvite>> playerPendingInvites = new ConcurrentHashMap<>();

    /** Listeners for party events */
    private final List<PartyEventListener> listeners = new ArrayList<>();

    private PartyManager() {
        // Singleton
    }

    // === Party Creation ===

    /**
     * Create a new party with the specified player as leader.
     *
     * @param leaderId UUID of the party leader
     * @param leaderName Display name of the leader
     * @param questType Quest type for the party
     * @return Created party, or null if player already in a party
     */
    @Nullable
    public PartyData createParty(UUID leaderId, String leaderName, QuestType questType) {
        // Check if player is already in a party
        if (playerToParty.containsKey(leaderId)) {
            LOGGER.debug("[PartyManager] Player {} is already in a party", leaderId);
            return null;
        }

        PartyData party = new PartyData(leaderId, leaderName, questType);
        parties.put(party.getPartyId(), party);
        playerToParty.put(leaderId, party.getPartyId());

        LOGGER.info("[PartyManager] Party created partyId={} leaderId={} leaderName={} questType={}",
                party.getPartyId(), leaderId, leaderName, questType);

        EnduranceTelemetryService.INSTANCE.recordPartyCreated(
            party.getPartyId(), leaderId, leaderName, questType
        );

        notifyListeners(listener -> listener.onPartyCreated(party));
        return party;
    }

    /**
     * Get a party by its ID.
     *
     * @param partyId Party UUID
     * @return Party data or null
     */
    @Nullable
    public PartyData getParty(UUID partyId) {
        return parties.get(partyId);
    }

    /**
     * Get the party a player is in.
     *
     * @param playerId Player UUID
     * @return Party data or null if not in a party
     */
    @Nullable
    public PartyData getPlayerParty(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }

    /**
     * Check if a player is in any party.
     *
     * @param playerId Player UUID
     * @return true if in a party
     */
    public boolean isInParty(UUID playerId) {
        return playerToParty.containsKey(playerId);
    }

    // === Invite System ===

    /**
     * Send an invite from a party leader to a target player.
     *
     * @param senderId UUID of the sender (must be party leader)
     * @param targetId UUID of the player to invite
     * @param targetName Display name of the target (for logging)
     * @return Created invite, or null if cannot invite
     */
    @Nullable
    public PartyInvite sendInvite(UUID senderId, UUID targetId, String targetName) {
        // Validate sender is in a party and is the leader
        PartyData party = getPlayerParty(senderId);
        if (party == null) {
            LOGGER.debug("[PartyManager] Cannot send invite - sender {} not in a party", senderId);
            return null;
        }
        if (!party.isLeader(senderId)) {
            LOGGER.debug("[PartyManager] Cannot send invite - sender {} is not the leader", senderId);
            return null;
        }

        // Validate target is not already in a party
        if (playerToParty.containsKey(targetId)) {
            LOGGER.debug("[PartyManager] Cannot invite {} - already in a party", targetId);
            return null;
        }

        // Create the invite
        PartyInvite invite = party.createInvite(targetId, targetName);
        if (invite == null) {
            return null;
        }

        // Track invite for target player
        playerPendingInvites.computeIfAbsent(targetId, k -> new ArrayList<>()).add(invite);

        LOGGER.info("[PartyManager] Invite sent partyId={} senderId={} targetId={} targetName={}",
                party.getPartyId(), senderId, targetId, targetName);

        EnduranceTelemetryService.INSTANCE.recordInviteSent(
            party.getPartyId(), senderId, targetId
        );

        notifyListeners(listener -> listener.onInviteSent(invite));
        return invite;
    }

    /**
     * Handle a player's response to an invite.
     *
     * @param playerId UUID of the player responding
     * @param inviteId UUID of the invite
     * @param accepted true to accept, false to decline
     * @return true if response was processed
     */
    public boolean handleInviteResponse(UUID playerId, UUID inviteId, boolean accepted) {
        // Find the invite
        List<PartyInvite> invites = playerPendingInvites.get(playerId);
        if (invites == null) {
            return false;
        }

        PartyInvite invite = invites.stream()
                .filter(inv -> inv.getInviteId().equals(inviteId))
                .findFirst()
                .orElse(null);

        if (invite == null || !invite.canRespond()) {
            LOGGER.debug("[PartyManager] Invite {} not found or cannot respond", inviteId);
            return false;
        }

        // Get the party
        PartyData party = parties.get(invite.getPartyId());
        if (party == null || party.getState() == PartyData.PartyState.DISBANDED) {
            invite.cancel();
            invites.remove(invite);
            return false;
        }

        if (accepted) {
            // Check if player joined another party while invite was pending
            if (playerToParty.containsKey(playerId)) {
                invite.decline();
                invites.remove(invite);
                return false;
            }

            // Accept and join
            if (invite.accept()) {
                // Get player name from somewhere (network handler should provide this)
                String playerName = "Player"; // This will be set by the network handler
                if (party.addMember(playerId, playerName)) {
                    playerToParty.put(playerId, party.getPartyId());
                    LOGGER.info("[PartyManager] Member joined partyId={} playerId={} playerName={}",
                            party.getPartyId(), playerId, playerName);

                    EnduranceTelemetryService.INSTANCE.recordPartyJoin(
                        party.getPartyId(), playerId, playerName, party.getMemberCount()
                    );

                    EnduranceTelemetryService.INSTANCE.recordInviteResponse(
                        party.getPartyId(), playerId, true
                    );

                    notifyListeners(listener -> listener.onMemberJoined(party, playerId));
                }
            }
        } else {
            invite.decline();
            LOGGER.info("[PartyManager] Invite declined partyId={} playerId={}",
                    party.getPartyId(), playerId);

            EnduranceTelemetryService.INSTANCE.recordInviteResponse(
                party.getPartyId(), playerId, false
            );

            notifyListeners(listener -> listener.onInviteDeclined(invite));
        }

        invites.remove(invite);
        return true;
    }

    /**
     * Accept an invite with the player's name provided.
     *
     * @param playerId UUID of the player
     * @param playerName Display name of the player
     * @param inviteId Invite UUID
     * @return true if accepted successfully
     */
    public boolean acceptInvite(UUID playerId, String playerName, UUID inviteId) {
        List<PartyInvite> invites = playerPendingInvites.get(playerId);
        if (invites == null) {
            return false;
        }

        PartyInvite invite = invites.stream()
                .filter(inv -> inv.getInviteId().equals(inviteId))
                .findFirst()
                .orElse(null);

        if (invite == null || !invite.canRespond()) {
            return false;
        }

        PartyData party = parties.get(invite.getPartyId());
        if (party == null || party.getState() == PartyData.PartyState.DISBANDED) {
            invite.cancel();
            invites.remove(invite);
            return false;
        }

        // Check if player joined another party
        if (playerToParty.containsKey(playerId)) {
            invite.decline();
            invites.remove(invite);
            return false;
        }

        if (invite.accept() && party.addMember(playerId, playerName)) {
            playerToParty.put(playerId, party.getPartyId());
            invites.remove(invite);
            LOGGER.info("[PartyManager] Member joined partyId={} playerId={} playerName={}",
                    party.getPartyId(), playerId, playerName);

            EnduranceTelemetryService.INSTANCE.recordPartyJoin(
                party.getPartyId(), playerId, playerName, party.getMemberCount()
            );

            EnduranceTelemetryService.INSTANCE.recordInviteResponse(
                party.getPartyId(), playerId, true
            );

            notifyListeners(listener -> listener.onMemberJoined(party, playerId));
            return true;
        }

        return false;
    }

    /**
     * Get all pending invites for a player.
     *
     * @param playerId Player UUID
     * @return List of pending invites (unmodifiable)
     */
    public List<PartyInvite> getPendingInvites(UUID playerId) {
        List<PartyInvite> invites = playerPendingInvites.get(playerId);
        if (invites == null) {
            return Collections.emptyList();
        }
        // Return only valid pending invites
        return invites.stream()
                .filter(PartyInvite::canRespond)
                .collect(Collectors.toUnmodifiableList());
    }

    // === Member Management ===

    /**
     * Remove a player from their party (leave).
     *
     * @param playerId UUID of the player leaving
     * @return true if left successfully
     */
    public boolean leaveParty(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        if (partyId == null) {
            return false;
        }

        PartyData party = parties.get(partyId);
        if (party == null) {
            playerToParty.remove(playerId);
            return false;
        }

        // If leader leaves, disband the party
        if (party.isLeader(playerId)) {
            return disbandParty(playerId);
        }

        if (party.removeMember(playerId)) {
            playerToParty.remove(playerId);
            LOGGER.info("[PartyManager] Member left partyId={} playerId={}", partyId, playerId);

            EnduranceTelemetryService.INSTANCE.recordPartyLeave(
                partyId, playerId, "left", party.getMemberCount()
            );

            notifyListeners(listener -> listener.onMemberLeft(party, playerId));
            return true;
        }

        return false;
    }

    /**
     * Kick a member from a party (leader only).
     *
     * @param leaderId UUID of the leader performing the kick
     * @param targetId UUID of the player to kick
     * @return true if kicked successfully
     */
    public boolean kickMember(UUID leaderId, UUID targetId) {
        PartyData party = getPlayerParty(leaderId);
        if (party == null || !party.isLeader(leaderId)) {
            return false;
        }

        if (party.kickMember(leaderId, targetId)) {
            playerToParty.remove(targetId);
            LOGGER.info("[PartyManager] Member kicked partyId={} targetId={} leaderId={}",
                    party.getPartyId(), targetId, leaderId);

            EnduranceTelemetryService.INSTANCE.recordPartyLeave(
                party.getPartyId(), targetId, "kicked", party.getMemberCount()
            );

            notifyListeners(listener -> listener.onMemberKicked(party, targetId, leaderId));
            return true;
        }

        return false;
    }

    /**
     * Transfer party leadership to another member.
     *
     * @param currentLeaderId UUID of current leader
     * @param newLeaderId UUID of new leader
     * @return true if transferred
     */
    public boolean transferLeadership(UUID currentLeaderId, UUID newLeaderId) {
        PartyData party = getPlayerParty(currentLeaderId);
        if (party == null) {
            return false;
        }

        if (party.transferLeadership(currentLeaderId, newLeaderId)) {
            LOGGER.info("[PartyManager] Leadership transferred partyId={} fromLeaderId={} toLeaderId={}",
                    party.getPartyId(), currentLeaderId, newLeaderId);
            notifyListeners(listener -> listener.onLeadershipTransferred(party, currentLeaderId, newLeaderId));
            return true;
        }

        return false;
    }

    // === Party Lifecycle ===

    /**
     * Disband a party (leader only).
     *
     * @param leaderId UUID of the leader
     * @return true if disbanded
     */
    public boolean disbandParty(UUID leaderId) {
        PartyData party = getPlayerParty(leaderId);
        if (party == null || !party.isLeader(leaderId)) {
            return false;
        }

        UUID partyId = party.getPartyId();

        // Remove all members from tracking
        for (UUID memberId : party.getMembers()) {
            playerToParty.remove(memberId);
        }

        // Clear pending invites for this party
        for (List<PartyInvite> invites : playerPendingInvites.values()) {
            invites.removeIf(inv -> inv.getPartyId().equals(partyId));
        }

        int memberCount = party.getMemberCount();
        party.disband();
        parties.remove(partyId);

        LOGGER.info("[PartyManager] Party disbanded partyId={} leaderId={}", partyId, leaderId);

        EnduranceTelemetryService.INSTANCE.recordPartyDisbanded(
            partyId, memberCount, "leader_disbanded"
        );

        notifyListeners(listener -> listener.onPartyDisbanded(party));
        return true;
    }

    /**
     * Mark a party as starting a quest.
     *
     * @param partyId Party UUID
     * @param instanceId Instance UUID
     * @return true if started
     */
    public boolean startQuest(UUID partyId, UUID instanceId) {
        PartyData party = parties.get(partyId);
        if (party == null) {
            return false;
        }

        if (party.startQuest(instanceId)) {
            notifyListeners(listener -> listener.onQuestStarted(party, instanceId));
            return true;
        }

        return false;
    }

    /**
     * Mark a party's quest as finished.
     *
     * @param partyId Party UUID
     */
    public void finishQuest(UUID partyId) {
        PartyData party = parties.get(partyId);
        if (party != null) {
            party.finishQuest();
            notifyListeners(listener -> listener.onQuestFinished(party));
        }
    }

    // === Maintenance ===

    /**
     * Tick method to cleanup expired invites.
     * Should be called periodically (e.g., every second).
     */
    public void tick() {
        // Cleanup expired invites in parties
        for (PartyData party : parties.values()) {
            int expired = party.cleanupExpiredInvites();
            if (expired > 0) {
                LOGGER.debug("[PartyManager] Cleaned up {} expired invites from party {}",
                        expired, party.getPartyId());
            }
        }

        // Cleanup expired invites in player tracking
        for (Map.Entry<UUID, List<PartyInvite>> entry : playerPendingInvites.entrySet()) {
            List<PartyInvite> invites = entry.getValue();
            invites.removeIf(invite -> {
                if (invite.isExpired()) {
                    invite.markExpired();
                    notifyListeners(listener -> listener.onInviteExpired(invite));
                    return true;
                }
                return false;
            });
        }

        // Remove empty invite lists
        playerPendingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Handle player disconnect - leave party if in one.
     *
     * @param playerId UUID of disconnecting player
     */
    public void handlePlayerDisconnect(UUID playerId) {
        // Clear pending invites
        playerPendingInvites.remove(playerId);

        // Leave party if in one
        PartyData party = getPlayerParty(playerId);
        if (party != null) {
            if (party.isLeader(playerId)) {
                // Transfer leadership or disband
                Set<UUID> members = party.getMembers();
                UUID newLeader = members.stream()
                        .filter(id -> !id.equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (newLeader != null) {
                    party.transferLeadership(playerId, newLeader);
                    party.removeMember(playerId);
                    playerToParty.remove(playerId);
                } else {
                    disbandParty(playerId);
                }
            } else {
                leaveParty(playerId);
            }
        }
    }

    /**
     * Reset all party data. Used for server shutdown/restart.
     */
    public void reset() {
        parties.clear();
        playerToParty.clear();
        playerPendingInvites.clear();
        LOGGER.info("[PartyManager] Reset all party data");
    }

    // === Event Listeners ===

    public void addListener(PartyEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PartyEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(java.util.function.Consumer<PartyEventListener> action) {
        for (PartyEventListener listener : listeners) {
            try {
                // Listener failures should not interrupt party operations.
                action.accept(listener);
            } catch (Exception e) {
                LOGGER.error("[PartyManager] Error notifying listener", e);
            }
        }
    }

    /**
     * Interface for party event callbacks.
     */
    public interface PartyEventListener {
        default void onPartyCreated(PartyData party) {}
        default void onPartyDisbanded(PartyData party) {}
        default void onMemberJoined(PartyData party, UUID memberId) {}
        default void onMemberLeft(PartyData party, UUID memberId) {}
        default void onMemberKicked(PartyData party, UUID memberId, UUID kickerId) {}
        default void onLeadershipTransferred(PartyData party, UUID oldLeader, UUID newLeader) {}
        default void onInviteSent(PartyInvite invite) {}
        default void onInviteDeclined(PartyInvite invite) {}
        default void onInviteExpired(PartyInvite invite) {}
        default void onQuestStarted(PartyData party, UUID instanceId) {}
        default void onQuestFinished(PartyData party) {}
    }

    // === Debug/Stats ===

    public int getActivePartyCount() {
        return parties.size();
    }

    public int getPlayersInPartiesCount() {
        return playerToParty.size();
    }

    public Collection<PartyData> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    // === Optional-style Accessors ===

    /**
     * Get a party by ID as Optional.
     */
    public Optional<PartyData> getPartyOpt(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    /**
     * Get the party a player is in as Optional.
     */
    public Optional<PartyData> getPartyByPlayer(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId != null ? Optional.ofNullable(parties.get(partyId)) : Optional.empty();
    }

    // === Network-oriented Methods with Result Types ===

    /**
     * Result of sending an invite.
     */
    public record InviteResult(
        boolean success,
        @Nullable String errorMessage,
        @Nullable UUID inviteId,
        long expiresAt
    ) {
        public static InviteResult success(UUID inviteId, long expiresAt) {
            return new InviteResult(true, null, inviteId, expiresAt);
        }
        public static InviteResult failure(String error) {
            return new InviteResult(false, error, null, 0);
        }
    }

    /**
     * Result of responding to an invite.
     */
    public record ResponseResult(
        boolean success,
        @Nullable String errorMessage,
        @Nullable UUID partyId,
        @Nullable UUID senderId
    ) {
        public static ResponseResult success(UUID partyId, UUID senderId) {
            return new ResponseResult(true, null, partyId, senderId);
        }
        public static ResponseResult failure(String error) {
            return new ResponseResult(false, error, null, null);
        }
    }

    /**
     * Send an invite with full sender info, returning a result object.
     *
     * @param senderId UUID of the sender
     * @param senderName Display name of the sender
     * @param targetId UUID of the target player
     * @param targetName Display name of the target
     * @param questType Quest type for the party (creates party if needed)
     * @return InviteResult with success/failure info
     */
    public InviteResult sendInvite(UUID senderId, String senderName, UUID targetId,
            String targetName, QuestType questType) {
        // Check if target is already in a party
        if (playerToParty.containsKey(targetId)) {
            return InviteResult.failure("Player is already in a party");
        }

        // Get or create party for sender
        PartyData party = getPlayerParty(senderId);
        if (party == null) {
            // Create a new party
            party = createParty(senderId, senderName, questType);
            if (party == null) {
                return InviteResult.failure("Failed to create party");
            }
        }

        // Check if sender is leader
        if (!party.isLeader(senderId)) {
            return InviteResult.failure("Only the party leader can invite");
        }

        // Check party capacity
        if (!party.canInvite()) {
            return InviteResult.failure("Party is full or has too many pending invites");
        }

        // Create invite
        PartyInvite invite = party.createInvite(targetId, targetName);
        if (invite == null) {
            return InviteResult.failure("Cannot create invite");
        }

        // Track for target player
        playerPendingInvites.computeIfAbsent(targetId, k -> new ArrayList<>()).add(invite);

        LOGGER.info("[PartyManager] Invite sent partyId={} senderId={} senderName={} targetId={} targetName={} questType={}",
                party.getPartyId(), senderId, senderName, targetId, targetName, questType);

        EnduranceTelemetryService.INSTANCE.recordInviteSent(
            party.getPartyId(), senderId, targetId
        );

        notifyListeners(listener -> listener.onInviteSent(invite));
        return InviteResult.success(invite.getInviteId(), invite.getExpiresAt());
    }

    /**
     * Handle invite response with player name, returning a result object.
     *
     * @param playerId UUID of the responding player
     * @param playerName Display name of the responding player
     * @param inviteId UUID of the invite
     * @param accepted true if accepting, false if declining
     * @return ResponseResult with success/failure info
     */
    public ResponseResult handleInviteResponse(UUID playerId, String playerName,
            UUID inviteId, boolean accepted) {
        // Find the invite
        List<PartyInvite> invites = playerPendingInvites.get(playerId);
        if (invites == null || invites.isEmpty()) {
            return ResponseResult.failure("No pending invites");
        }

        PartyInvite invite = invites.stream()
                .filter(inv -> inv.getInviteId().equals(inviteId))
                .findFirst()
                .orElse(null);

        if (invite == null) {
            return ResponseResult.failure("Invite not found");
        }

        if (!invite.canRespond()) {
            invites.remove(invite);
            return ResponseResult.failure("Invite expired or already responded");
        }

        // Get the party
        PartyData party = parties.get(invite.getPartyId());
        if (party == null || party.getState() == PartyData.PartyState.DISBANDED) {
            invite.cancel();
            invites.remove(invite);
            return ResponseResult.failure("Party no longer exists");
        }

        UUID senderId = invite.getSenderId();

        if (accepted) {
            // Check if player joined another party while invite was pending
            if (playerToParty.containsKey(playerId)) {
                invite.decline();
                invites.remove(invite);
                return ResponseResult.failure("You are already in a party");
            }

            // Accept and join
            if (invite.accept() && party.addMember(playerId, playerName)) {
                playerToParty.put(playerId, party.getPartyId());
                invites.remove(invite);

                // Clear all other pending invites for this player
                invites.clear();

                LOGGER.info("[PartyManager] Member joined via invite partyId={} playerId={} playerName={} senderId={}",
                        party.getPartyId(), playerId, playerName, senderId);

                EnduranceTelemetryService.INSTANCE.recordPartyJoin(
                    party.getPartyId(), playerId, playerName, party.getMemberCount()
                );

                EnduranceTelemetryService.INSTANCE.recordInviteResponse(
                    party.getPartyId(), playerId, true
                );

                notifyListeners(listener -> listener.onMemberJoined(party, playerId));

                return ResponseResult.success(party.getPartyId(), senderId);
            } else {
                return ResponseResult.failure("Failed to join party - may be full");
            }
        } else {
            invite.decline();
            invites.remove(invite);
            LOGGER.info("[PartyManager] Invite declined partyId={} playerId={} playerName={}",
                    party.getPartyId(), playerId, playerName);

            EnduranceTelemetryService.INSTANCE.recordInviteResponse(
                party.getPartyId(), playerId, false
            );

            notifyListeners(listener -> listener.onInviteDeclined(invite));

            return ResponseResult.success(null, senderId);
        }
    }
}

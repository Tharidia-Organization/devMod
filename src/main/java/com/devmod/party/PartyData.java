package com.devmod.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.QuestType;
import com.devmod.endurance.config.EnduranceMobPoolConfig;

public class PartyData {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyData.class);

    /** Unique identifier for this party */
    private final UUID partyId;

    /** UUID of the party leader */
    private volatile UUID leaderId;

    /** Display name of the leader for UI purposes */
    private volatile String leaderName;

    /** Set of all member UUIDs (including leader) */
    private final Set<UUID> members;

    /** Map of member UUIDs to their display names */
    private final Map<UUID, String> memberNames;

    /** Map of member UUIDs to their ready status */
    private final Map<UUID, Boolean> memberReady;

    /** Map of member UUIDs to their selected kit ID */
    private final Map<UUID, String> memberKits;

    /** Pending invites sent from this party */
    private final Map<UUID, PartyInvite> pendingInvites;

    /** Quest type this party is configured for */
    private volatile QuestType questType;

    /** Selected mob type for the endurance quest (null = use default zombie) */
    @Nullable
    private volatile ResourceLocation selectedMobId;

    /** Current state of the party */
    private volatile PartyState state;

    /** Instance ID if party is currently in a quest */
    @Nullable
    private volatile UUID instanceId;

    /** Pending mob pool config for the next party run (session scope) */
    @Nullable
    private volatile EnduranceMobPoolConfig mobPoolConfig;

    /** Timestamp when the party was created */
    private final long createdAt;

    private static final String DEFAULT_KIT_ID = "STARTER";

    /**
     * Possible states of a party.
     */
    public enum PartyState {
        /** Party is forming, accepting members */
        FORMING,
        /** All members ready, can start quest */
        READY,
        /** Party is currently in an active quest */
        IN_QUEST,
        /** Party has been disbanded */
        DISBANDED
    }

    /**
     * Create a new party with the specified leader.
     *
     * @param leaderId UUID of the party leader
     * @param leaderName Display name of the leader
     * @param questType Initial quest type for the party
     */
    public PartyData(UUID leaderId, String leaderName, QuestType questType) {
        this.partyId = UUID.randomUUID();
        this.leaderId = leaderId;
        this.leaderName = leaderName;
        this.questType = questType;
        this.state = PartyState.FORMING;
        this.createdAt = System.currentTimeMillis();

        // Thread-safe collections
        this.members = ConcurrentHashMap.newKeySet();
        this.memberNames = new ConcurrentHashMap<>();
        this.memberReady = new ConcurrentHashMap<>();
        this.memberKits = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();

        // Add leader as first member
        this.members.add(leaderId);
        this.memberNames.put(leaderId, leaderName);
        this.memberReady.put(leaderId, true); // Leader is always ready
        this.memberKits.put(leaderId, DEFAULT_KIT_ID);
    }

    // === Member Management ===

    /**
     * Add a member to the party.
     *
     * @param playerId UUID of the player to add
     * @param playerName Display name of the player
     * @return true if added successfully, false if party is full or not accepting
     */
    public boolean addMember(UUID playerId, String playerName) {
        if (state != PartyState.FORMING) {
            LOGGER.debug("[Party] Cannot add member - party not in FORMING state");
            return false;
        }
        if (members.size() >= questType.getMaxPlayers()) {
            LOGGER.debug("[Party] Cannot add member - party is full ({}/{})",
                    members.size(), questType.getMaxPlayers());
            return false;
        }
        if (members.contains(playerId)) {
            LOGGER.debug("[Party] Player {} is already a member", playerId);
            return false;
        }

        members.add(playerId);
        memberNames.put(playerId, playerName);
        memberReady.put(playerId, false);
        memberKits.put(playerId, DEFAULT_KIT_ID);

        LOGGER.info("[Party] {} joined party {} ({}/{})",
                playerName, partyId, members.size(), questType.getMaxPlayers());

        // Remove any pending invite for this player
        pendingInvites.values().removeIf(invite ->
                invite.getReceiverId().equals(playerId));

        return true;
    }

    /**
     * Remove a member from the party.
     *
     * @param playerId UUID of the player to remove
     * @return true if removed, false if not a member or is the leader
     */
    public boolean removeMember(UUID playerId) {
        if (playerId.equals(leaderId)) {
            LOGGER.debug("[Party] Cannot remove leader via removeMember");
            return false;
        }
        if (!members.contains(playerId)) {
            return false;
        }

        members.remove(playerId);
        memberNames.remove(playerId);
        memberReady.remove(playerId);
        memberKits.remove(playerId);

        LOGGER.info("[Party] Player {} left party {}", playerId, partyId);
        return true;
    }

    /**
     * Kick a member from the party (leader only action).
     *
     * @param requesterId UUID of the player requesting the kick
     * @param targetId UUID of the player to kick
     * @return true if kicked successfully
     */
    public boolean kickMember(UUID requesterId, UUID targetId) {
        if (!requesterId.equals(leaderId)) {
            LOGGER.debug("[Party] Only leader can kick members");
            return false;
        }
        return removeMember(targetId);
    }

    /**
     * Check if a player is in this party.
     *
     * @param playerId UUID to check
     * @return true if member
     */
    public boolean hasMember(UUID playerId) {
        return members.contains(playerId);
    }

    // === Ready Status ===

    /**
     * Set a member's ready status.
     *
     * @param playerId UUID of the member
     * @param ready Ready status
     * @return true if status was updated
     */
    public boolean setReady(UUID playerId, boolean ready) {
        if (!members.contains(playerId)) {
            return false;
        }
        memberReady.put(playerId, ready);
        updatePartyState();
        return true;
    }

    public boolean setMemberKit(UUID playerId, String kitId) {
        if (!members.contains(playerId)) {
            return false;
        }
        if (state == PartyState.IN_QUEST) {
            return false;
        }
        String normalized = (kitId == null || kitId.isBlank()) ? DEFAULT_KIT_ID : kitId;
        String previous = memberKits.put(playerId, normalized);
        if (!Objects.equals(previous, normalized)) {
            memberReady.put(playerId, false);
            updatePartyState();
        }
        return true;
    }

    @Nullable
    public String getMemberKitId(UUID playerId) {
        return memberKits.get(playerId);
    }

    public Map<UUID, String> getMemberKitIds() {
        return Collections.unmodifiableMap(memberKits);
    }

    /**
     * Check if a member is ready.
     *
     * @param playerId UUID to check
     * @return true if ready, false if not ready or not a member
     */
    public boolean isReady(UUID playerId) {
        return memberReady.getOrDefault(playerId, false);
    }

    /**
     * Check if all members are ready.
     *
     * @return true if all members are ready
     */
    public boolean allMembersReady() {
        return members.stream().allMatch(id -> memberReady.getOrDefault(id, false));
    }

    // === Invite Management ===

    /**
     * Check if the party can send more invites.
     *
     * @return true if can invite
     */
    public boolean canInvite() {
        if (state != PartyState.FORMING) {
            return false;
        }
        int totalPotentialMembers = members.size() + pendingInvites.size();
        return totalPotentialMembers < questType.getMaxPlayers();
    }

    /**
     * Create and track a new invite.
     *
     * @param targetId UUID of the player to invite
     * @param targetName Display name (for validation only, not stored)
     * @return The created invite, or null if cannot invite
     */
    @Nullable
    public PartyInvite createInvite(UUID targetId, String targetName) {
        if (!canInvite()) {
            return null;
        }
        if (members.contains(targetId)) {
            LOGGER.debug("[Party] Cannot invite {} - already a member", targetId);
            return null;
        }
        if (pendingInvites.values().stream()
                .anyMatch(inv -> inv.getReceiverId().equals(targetId) && inv.canRespond())) {
            LOGGER.debug("[Party] Cannot invite {} - already has pending invite", targetId);
            return null;
        }

        PartyInvite invite = new PartyInvite(leaderId, leaderName, targetId, partyId, questType);
        pendingInvites.put(invite.getInviteId(), invite);

        LOGGER.info("[Party] Invite sent to {} for party {}", targetId, partyId);
        return invite;
    }

    /**
     * Get a pending invite by ID.
     *
     * @param inviteId Invite UUID
     * @return The invite or null
     */
    @Nullable
    public PartyInvite getInvite(UUID inviteId) {
        return pendingInvites.get(inviteId);
    }

    @Nullable
    public PartyInvite removeInvite(UUID inviteId, boolean cancel) {
        if (inviteId == null) {
            return null;
        }
        PartyInvite invite = pendingInvites.remove(inviteId);
        if (invite != null && cancel) {
            invite.cancel();
        }
        return invite;
    }

    public List<PartyInvite> clearPendingInvites() {
        if (pendingInvites.isEmpty()) {
            return List.of();
        }
        List<PartyInvite> invites = new ArrayList<>(pendingInvites.values());
        for (PartyInvite invite : invites) {
            invite.cancel();
        }
        pendingInvites.clear();
        return invites;
    }

    /**
     * Remove expired invites.
     *
     * @return Number of expired invites removed
     */
    public int cleanupExpiredInvites() {
        return cleanupExpiredInvites(System.currentTimeMillis()).expiredCount();
    }

    static record InviteCleanupResult(int expiredCount, long nextExpiryAt) {}

    InviteCleanupResult cleanupExpiredInvites(long nowMillis) {
        List<UUID> expired = new ArrayList<>();
        long nextExpiry = Long.MAX_VALUE;
        for (Map.Entry<UUID, PartyInvite> entry : pendingInvites.entrySet()) {
            PartyInvite invite = entry.getValue();
            if (invite.isExpiredAt(nowMillis)) {
                invite.markExpired();
                expired.add(entry.getKey());
            } else {
                long expiresAt = invite.getExpiresAt();
                if (expiresAt < nextExpiry) {
                    nextExpiry = expiresAt;
                }
            }
        }
        expired.forEach(pendingInvites::remove);
        return new InviteCleanupResult(expired.size(), nextExpiry);
    }

    // === Leadership ===

    /**
     * Transfer leadership to another member.
     *
     * @param requesterId UUID of current leader (must match)
     * @param newLeaderId UUID of new leader (must be a member)
     * @return true if transferred
     */
    public boolean transferLeadership(UUID requesterId, UUID newLeaderId) {
        if (!requesterId.equals(leaderId)) {
            return false;
        }
        if (!members.contains(newLeaderId)) {
            return false;
        }
        if (newLeaderId.equals(leaderId)) {
            return false;
        }

        String newLeaderName = memberNames.get(newLeaderId);
        this.leaderId = newLeaderId;
        this.leaderName = newLeaderName;

        // New leader is always ready
        memberReady.put(newLeaderId, true);

        LOGGER.info("[Party] Leadership transferred to {} in party {}", newLeaderName, partyId);
        return true;
    }

    /**
     * Check if a player is the leader.
     *
     * @param playerId UUID to check
     * @return true if leader
     */
    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    // === Quest State ===

    /**
     * Check if the party can start a quest.
     *
     * @return true if can start
     */
    public boolean canStartQuest() {
        if (state != PartyState.FORMING && state != PartyState.READY) {
            return false;
        }
        // For solo testing, allow single player in PVE_COOP
        if (questType.allowsSoloPlay() && members.size() == 1) {
            return true;
        }
        return members.size() >= questType.getMinPlayers() && allMembersReady();
    }

    /**
     * Mark the party as starting a quest.
     *
     * @param instanceId Instance ID for the quest
     * @return true if state changed
     */
    public boolean startQuest(UUID instanceId) {
        if (!canStartQuest()) {
            return false;
        }
        this.instanceId = instanceId;
        this.state = PartyState.IN_QUEST;
        LOGGER.info("[Party] Party {} started quest in instance {}", partyId, instanceId);
        return true;
    }

    public boolean forceStartQuest(UUID instanceId) {
        if (state == PartyState.DISBANDED) {
            return false;
        }
        this.instanceId = instanceId;
        this.state = PartyState.IN_QUEST;
        return true;
    }

    /**
     * Mark the quest as finished, return to forming state.
     */
    public void finishQuest() {
        this.instanceId = null;
        this.state = PartyState.FORMING;
        clearMobPoolConfig();
        // Reset ready status for non-leaders
        for (UUID memberId : members) {
            if (!memberId.equals(leaderId)) {
                memberReady.put(memberId, false);
            }
        }
        LOGGER.info("[Party] Party {} finished quest", partyId);
    }

    /**
     * Disband the party.
     */
    public void disband() {
        this.state = PartyState.DISBANDED;
        clearMobPoolConfig();
        // Cancel all pending invites
        pendingInvites.values().forEach(PartyInvite::cancel);
        pendingInvites.clear();
        LOGGER.info("[Party] Party {} disbanded", partyId);
    }

    // === Mob Pool Config (session scope, party-level) ===

    public void setMobPoolConfig(@Nullable EnduranceMobPoolConfig config) {
        this.mobPoolConfig = config != null ? config.copy() : null;
    }

    public @Nullable EnduranceMobPoolConfig getMobPoolConfig() {
        EnduranceMobPoolConfig config = mobPoolConfig;
        return config != null ? config.copy() : null;
    }

    public boolean hasMobPoolConfig() {
        return mobPoolConfig != null && mobPoolConfig.hasModifications();
    }

    public void clearMobPoolConfig() {
        mobPoolConfig = null;
    }

    /**
     * Change the quest type (only when forming and no members beyond leader).
     *
     * @param newType New quest type
     * @return true if changed
     */
    public boolean setQuestType(QuestType newType) {
        if (state != PartyState.FORMING) {
            return false;
        }
        // Only allow change if current members fit in new type
        if (members.size() > newType.getMaxPlayers()) {
            return false;
        }
        this.questType = newType;
        LOGGER.info("[Party] Party {} changed quest type to {}", partyId, newType);
        return true;
    }

    // === Internal ===

    private void updatePartyState() {
        if (state == PartyState.FORMING && canStartQuest()) {
            state = PartyState.READY;
        } else if (state == PartyState.READY && !canStartQuest()) {
            state = PartyState.FORMING;
        }
    }

    // === Getters ===

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    public String getMemberName(UUID memberId) {
        return memberNames.get(memberId);
    }

    public Map<UUID, String> getMemberNames() {
        return Collections.unmodifiableMap(memberNames);
    }

    public Map<UUID, Boolean> getMemberReadyStatus() {
        return Collections.unmodifiableMap(memberReady);
    }

    public Collection<PartyInvite> getPendingInvites() {
        return Collections.unmodifiableCollection(pendingInvites.values());
    }

    public int getPendingInviteCount() {
        return pendingInvites.size();
    }

    public QuestType getQuestType() {
        return questType;
    }

    /**
     * Get the selected mob type for this party's quest.
     * Returns null if no mob is selected (use default).
     */
    @Nullable
    public ResourceLocation getSelectedMobId() {
        return selectedMobId;
    }

    /**
     * Set the mob type for this party's quest.
     * Only the leader can change this while party is forming.
     *
     * @param requesterId UUID of the player requesting the change (must be leader)
     * @param mobId The mob ResourceLocation (e.g., "minecraft:zombie")
     * @return true if changed successfully
     */
    public boolean setSelectedMobId(UUID requesterId, ResourceLocation mobId) {
        if (!requesterId.equals(leaderId)) {
            LOGGER.debug("[Party] Only leader can change mob type");
            return false;
        }
        if (state != PartyState.FORMING) {
            LOGGER.debug("[Party] Cannot change mob type - party not in FORMING state");
            return false;
        }
        this.selectedMobId = mobId;
        LOGGER.info("[Party] Party {} selected mob type: {}", partyId, mobId);
        return true;
    }

    /**
     * Get the mob ID to use for quest, with fallback to default zombie.
     * @return The selected mob ID, or minecraft:zombie if none selected
     */
    public ResourceLocation getEffectiveMobId() {
        ResourceLocation mobId = selectedMobId;
        return mobId != null ? mobId : ResourceLocation.withDefaultNamespace("zombie");
    }

    public PartyState getState() {
        return state;
    }

    @Nullable
    public UUID getInstanceId() {
        return instanceId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isFull() {
        return members.size() >= questType.getMaxPlayers();
    }

    public int getRemainingSlots() {
        return Math.max(0, questType.getMaxPlayers() - members.size() - pendingInvites.size());
    }

    @Override
    public String toString() {
        return "PartyData{" +
                "partyId=" + partyId +
                ", leader=" + leaderName +
                ", members=" + members.size() + "/" + questType.getMaxPlayers() +
                ", questType=" + questType +
                ", state=" + state +
                '}';
    }
}

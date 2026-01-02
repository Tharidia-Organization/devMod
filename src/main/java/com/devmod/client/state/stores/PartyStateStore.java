package com.devmod.client.state.stores;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.BaseStateStore;

/**
 * Client-side store for party/group state.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Party membership</li>
 *   <li>Party members and their status</li>
 *   <li>Party leader</li>
 *   <li>Invitations pending</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PartyStateStore extends BaseStateStore<PartyStateStore.PartyState> {

    public PartyStateStore() {
        super("PartyStore");
    }

    /**
     * Check if player is in a party.
     */
    public boolean isInParty() {
        PartyState state = getState();
        return state != null && state.partyId() != null;
    }

    /**
     * Check if player is the party leader.
     */
    public boolean isLeader() {
        PartyState state = getState();
        return state != null && state.isLeader();
    }

    /**
     * Get party member count.
     */
    public int getMemberCount() {
        PartyState state = getState();
        return state != null ? state.members().size() : 0;
    }

    /**
     * Get a member by UUID.
     */
    public Optional<PartyMember> getMember(UUID memberId) {
        PartyState state = getState();
        if (state == null) return Optional.empty();

        return state.members().stream()
            .filter(m -> m.playerId().equals(memberId))
            .findFirst();
    }

    /**
     * Check if there are pending invitations.
     */
    public boolean hasPendingInvites() {
        PartyState state = getState();
        return state != null && !state.pendingInvites().isEmpty();
    }

    /**
     * Party member data.
     */
    public record PartyMember(
        UUID playerId,
        String playerName,
        boolean isLeader,
        boolean isOnline,
        float health,
        float maxHealth,
        boolean isInQuest,
        @Nullable String currentQuestId,
        int currentWave,
        long lastUpdateTime
    ) {
        /**
         * Get health as percentage (0.0 to 1.0).
         */
        public float getHealthPercent() {
            return maxHealth > 0 ? health / maxHealth : 0f;
        }

        /**
         * Check if member is in danger (low health).
         */
        public boolean isInDanger() {
            return getHealthPercent() < 0.25f;
        }
    }

    /**
     * Pending party invitation.
     */
    public record PartyInvite(
        UUID fromPlayerId,
        String fromPlayerName,
        UUID partyId,
        long inviteTime,
        long expiresAt
    ) {
        /**
         * Check if invitation has expired.
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        /**
         * Get remaining time in seconds.
         */
        public int getRemainingSeconds() {
            long remaining = expiresAt - System.currentTimeMillis();
            return remaining > 0 ? (int)(remaining / 1000) : 0;
        }
    }

    /**
     * Immutable party state snapshot.
     */
    public record PartyState(
        @Nullable UUID partyId,
        @Nullable String partyName,
        UUID localPlayerId,
        boolean isLeader,
        List<PartyMember> members,
        List<PartyInvite> pendingInvites,
        int maxMembers,
        boolean isPublic,
        long serverTimestamp
    ) {
        /**
         * Check if party has space for more members.
         */
        public boolean hasSpace() {
            return members.size() < maxMembers;
        }

        /**
         * Get number of online members.
         */
        public int getOnlineCount() {
            return (int) members.stream().filter(PartyMember::isOnline).count();
        }

        /**
         * Get leader member.
         */
        public Optional<PartyMember> getLeader() {
            return members.stream().filter(PartyMember::isLeader).findFirst();
        }

        /**
         * Create empty state (not in party).
         */
        public static PartyState notInParty(UUID localPlayerId) {
            return new PartyState(
                null,
                null,
                localPlayerId,
                false,
                List.of(),
                List.of(),
                0,
                false,
                System.currentTimeMillis()
            );
        }
    }
}

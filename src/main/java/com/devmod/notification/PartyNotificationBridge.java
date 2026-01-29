package com.devmod.notification;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.endurance.QuestType;
import com.devmod.party.PartyData;
import com.devmod.party.PartyInvite;
import com.devmod.party.PartyManager;

/**
 * Bridges PartyManager events into the Unified Notification Center.
 */
public final class PartyNotificationBridge implements PartyManager.PartyEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyNotificationBridge.class);
    private static final PartyNotificationBridge INSTANCE = new PartyNotificationBridge();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private PartyNotificationBridge() {}

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            PartyManager.INSTANCE.addListener(INSTANCE);
            LOGGER.info("[PartyNotificationBridge] Registered party notification listener");
        }
    }

    public static void unregister() {
        if (REGISTERED.compareAndSet(true, false)) {
            PartyManager.INSTANCE.removeListener(INSTANCE);
            LOGGER.info("[PartyNotificationBridge] Unregistered party notification listener");
        }
    }

    @Override
    public void onInviteSent(PartyInvite invite) {
        NotificationService.INSTANCE.notifyPartyInvite(
            invite.getReceiverId(),
            invite.getInviteId(),
            invite.getSenderName(),
            invite.getQuestType(),
            invite.getExpiresAt()
        );
    }

    @Override
    public void onInviteDeclined(PartyInvite invite) {
        PartyData party = PartyManager.INSTANCE.getPartyOpt(invite.getPartyId()).orElse(null);
        String receiverName = resolvePlayerName(party, invite.getReceiverId());
        NotificationService.INSTANCE.notifyParty(invite.getSenderId(), "invite_declined", Map.of("player", receiverName));
    }

    @Override
    public void onInviteExpired(PartyInvite invite) {
        PartyData party = PartyManager.INSTANCE.getPartyOpt(invite.getPartyId()).orElse(null);
        String receiverName = resolvePlayerName(party, invite.getReceiverId());
        NotificationService.INSTANCE.notifyParty(invite.getSenderId(), "invite_expired", Map.of("player", receiverName));
    }

    @Override
    public void onMemberJoined(PartyData party, UUID memberId) {
        String joinedName = resolvePlayerName(party, memberId);
        notifyMembersExcluding(party, memberId, "join", Map.of("player", joinedName));
    }

    @Override
    public void onMemberLeft(PartyData party, UUID memberId) {
        String leftName = resolvePlayerName(party, memberId);
        notifyMembersExcluding(party, memberId, "leave", Map.of("player", leftName));
    }

    @Override
    public void onMemberKicked(PartyData party, UUID memberId, UUID kickerId) {
        String leaderName = resolvePlayerName(party, kickerId);
        NotificationService.INSTANCE.notifyParty(memberId, "kicked", Map.of("leader", leaderName));
    }

    @Override
    public void onPartyDisbanded(PartyData party) {
        String leaderName = party.getLeaderName();
        if (leaderName == null || leaderName.isBlank()) {
            leaderName = "Leader";
        }
        notifyMembersExcluding(party, party.getLeaderId(), "disbanded", Map.of("leader", leaderName));
    }

    @Override
    public void onLeadershipTransferred(PartyData party, UUID oldLeader, UUID newLeader) {
        String newLeaderName = resolvePlayerName(party, newLeader);
        notifyMembersExcluding(party, null, "leader_changed", Map.of("leader", newLeaderName));
    }

    @Override
    public void onQuestStarted(PartyData party, UUID instanceId) {
        QuestType questType = party.getQuestType();
        String questName = questType != null ? questType.getDisplayName() : "Quest";
        notifyMembersExcluding(party, null, "quest_started", Map.of("quest", questName));
    }

    @Override
    public void onQuestFinished(PartyData party) {
        QuestType questType = party.getQuestType();
        String questName = questType != null ? questType.getDisplayName() : "Quest";
        notifyMembersExcluding(party, null, "quest_finished", Map.of("quest", questName));
    }

    private void notifyMembersExcluding(PartyData party, @Nullable UUID excludeMemberId, String eventType,
                                        Map<String, String> params) {
        for (UUID memberId : party.getMembers()) {
            if (excludeMemberId != null && memberId.equals(excludeMemberId)) {
                continue;
            }
            NotificationService.INSTANCE.notifyParty(memberId, eventType, params);
        }
    }

    private String resolvePlayerName(@Nullable PartyData party, UUID playerId) {
        if (party != null) {
            String name = party.getMemberName(playerId);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
            if (player != null) {
                return player.getName().getString();
            }
        }

        return "Player";
    }
}

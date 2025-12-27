package com.devmod.notification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private void notifyMembersExcluding(PartyData party, UUID excludeMemberId, String eventType,
                                        Map<String, String> params) {
        for (UUID memberId : party.getMembers()) {
            if (excludeMemberId != null && memberId.equals(excludeMemberId)) {
                continue;
            }
            NotificationService.INSTANCE.notifyParty(memberId, eventType, params);
        }
    }

    private String resolvePlayerName(PartyData party, UUID playerId) {
        String name = party.getMemberName(playerId);
        if (name != null && !name.isBlank()) {
            return name;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return player.getName().getString();
            }
        }

        return "Player";
    }
}

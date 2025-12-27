package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.template.MessageTemplateRegistry;
import com.devmod.network.NetworkHandler;
import com.devmod.party.ArrivalConfirmPayload;
import com.devmod.party.CancelSequencePayload;
import com.devmod.party.InviteResponsePayload;
import com.devmod.party.NamedInvitePayload;
import com.devmod.party.PartyActionPayload;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.party.PartyNotificationPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.party.QuestStartSequence;
import com.devmod.util.I18n;

public final class PartyNetworkHandler extends NetworkHandlerBase {

    private PartyNetworkHandler() {}

    // =================================================================================
    // PARTY ACTION (server-side)
    // =================================================================================
    public static void handlePartyAction(PartyActionPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "party_action", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("party_action", player.getName().getString());
                return; // Fail closed: rate limited
            }

            UUID playerId = player.getUUID();
            String playerName = player.getName().getString();

            switch (payload.action()) {
                case CREATE_PARTY -> {
                    var questType = payload.getQuestType();
                    PartyData party = PartyManager.INSTANCE.createParty(playerId, playerName, questType);
                    if (party != null) {
                        LOGGER.info("[Party] {} created party {} (type: {})", playerName, party.getPartyId(), questType);
                        sendPartySyncToPlayer(player);
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.party.already_in_party"));
                    }
                }

                case TOGGLE_READY -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null) {
                        boolean currentReady = party.isReady(playerId);
                        party.setReady(playerId, !currentReady);
                        LOGGER.debug("[Party] {} toggled ready: {}", playerName, !currentReady);
                        syncPartyToAllMembers(player.server, party.getPartyId());
                    }
                }

                case LEAVE_PARTY -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null) {
                        UUID partyId = party.getPartyId();
                        if (PartyManager.INSTANCE.leaveParty(playerId)) {
                            LOGGER.info("[Party] {} left party {}", playerName, partyId);
                            notifyPartyMembers(player.server, partyId,
                                PartyNotificationPayload.memberLeft(playerId, playerName), null);
                            syncPartyToAllMembers(player.server, partyId);
                            sendPartySyncToPlayer(player);
                        }
                    }
                }

                case KICK_MEMBER -> {
                    if (payload.targetPlayerId() != null) {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && PartyManager.INSTANCE.kickMember(playerId, payload.targetPlayerId())) {
                            UUID partyId = party.getPartyId();
                            String kickedName = party.getMemberName(payload.targetPlayerId());
                            LOGGER.info("[Party] {} kicked {} from party {}", playerName, kickedName, partyId);

                            ServerPlayer kickedPlayer = player.server.getPlayerList().getPlayer(nn(payload.targetPlayerId()));
                            if (kickedPlayer != null) {
                                sendPartyNotification(kickedPlayer,
                                    PartyNotificationPayload.youWereKicked(playerId, playerName));
                                sendPartySyncToPlayer(kickedPlayer);
                            }

                            // Send mailbox notification (persists even if player is offline)
                            sendKickedMailbox(payload.targetPlayerId(), kickedName, playerName);

                            syncPartyToAllMembers(player.server, partyId);
                        }
                    }
                }

                case SET_QUEST_TYPE -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId)) {
                        var newType = payload.getQuestType();
                        if (party.setQuestType(newType)) {
                            LOGGER.info("[Party] {} changed quest type to {} in party {}",
                                playerName, newType, party.getPartyId());
                            syncPartyToAllMembers(player.server, party.getPartyId());
                        }
                    }
                }

                case SET_MOB_TYPE -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId)) {
                        ResourceLocation mobId = payload.getMobResourceLocation();
                        if (mobId != null) {
                            var mobConfig = com.devmod.endurance.EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId);
                            if (mobConfig.isPresent()) {
                                if (party.setSelectedMobId(playerId, mobId)) {
                                    LOGGER.info("[Party] {} changed mob type to {} in party {}",
                                        playerName, mobId, party.getPartyId());
                                    syncPartyToAllMembers(player.server, party.getPartyId());
                                }
                            } else {
                                LOGGER.warn("[Party] {} tried to set invalid mob type: {}", playerName, mobId);
                                player.sendSystemMessage(I18n.translate("devmod.party.invalid_mob"));
                            }
                        }
                    }
                }

                case DISBAND_PARTY -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId)) {
                        UUID partyId = party.getPartyId();
                        var members = new ArrayList<>(party.getMembers());
                        // Capture member names before disbanding
                        var memberNames = new java.util.HashMap<UUID, String>();
                        for (UUID memberId : members) {
                            memberNames.put(memberId, party.getMemberName(memberId));
                        }

                        if (PartyManager.INSTANCE.disbandParty(playerId)) {
                            LOGGER.info("[Party] {} disbanded party {}", playerName, partyId);

                            for (UUID memberId : members) {
                                if (!memberId.equals(playerId)) {
                                    // Send mailbox notification (persists even if player is offline)
                                    sendDisbandedMailbox(
                                        memberId,
                                        memberNames.getOrDefault(memberId, "Player"),
                                        playerName
                                    );
                                }

                                ServerPlayer member = player.server.getPlayerList().getPlayer(nn(memberId));
                                if (member != null) {
                                    if (!memberId.equals(playerId)) {
                                        sendPartyNotification(member,
                                            PartyNotificationPayload.partyDisbanded(playerId, playerName));
                                    }
                                    sendPartySyncToPlayer(member);
                                }
                            }
                        }
                    }
                }

                case START_QUEST -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId) && party.canStartQuest()) {
                        LOGGER.info("[Party] {} starting quest for party {}", playerName, party.getPartyId());

                        QuestStartSequence.ValidationResult result = QuestStartSequence.INSTANCE.startSequence(
                            player.server, party, player);

                        if (!result.success()) {
                            String msg = result.errorMessage();
                            // If it looks like a translation key, translate; otherwise show literal
                            if (msg != null && msg.startsWith("devmod.")) {
                                player.sendSystemMessage(I18n.translate(msg));
                            } else {
                                player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                                    msg != null ? msg : "Quest start failed")));
                            }
                        }
                    } else {
                        player.sendSystemMessage(I18n.translate("devmod.party.cannot_start"));
                    }
                }
            }
        }), "party action");
    }

    // =================================================================================
    // INVITE RESPONSE (server-side)
    // =================================================================================
    public static void handleInviteResponse(InviteResponsePayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "invite_response", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("invite_response", player.getName().getString());
                return; // Fail closed: rate limited
            }

            UUID playerId = player.getUUID();
            String playerName = player.getName().getString();
            UUID inviteId = payload.inviteId();

            PartyManager.ResponseResult result = PartyManager.INSTANCE.handleInviteResponse(
                playerId, playerName, inviteId, payload.accepted());

            if (result.success()) {
                if (payload.accepted()) {
                    LOGGER.info("[Party] {} accepted invite {}", playerName, inviteId);
                    sendPartySyncToPlayer(player);
                    if (result.partyId() != null) {
                        notifyPartyMembers(player.server, result.partyId(),
                            PartyNotificationPayload.memberJoined(playerId, playerName), playerId);
                        syncPartyToAllMembers(player.server, result.partyId());
                    }
                } else {
                    LOGGER.info("[Party] {} declined invite {}", playerName, inviteId);
                }
            } else {
                String errorMsg = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                player.sendSystemMessage(I18n.translate("devmod.party.invite_error", errorMsg));
            }
        }), "invite response");
    }

    // =================================================================================
    // NAMED INVITE (server-side)
    // =================================================================================
    public static void handleNamedInvite(NamedInvitePayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "named_invite", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("named_invite", player.getName().getString());
                return; // Fail closed: rate limited
            }

            UUID playerId = player.getUUID();
            String playerName = player.getName().getString();
            String targetName = payload.targetPlayerName();

            if (targetName == null || targetName.isBlank() || targetName.length() > 16) {
                player.sendSystemMessage(I18n.translate("devmod.party.invalid_name"));
                return;
            }

            ServerPlayer targetPlayer = player.server.getPlayerList().getPlayerByName(targetName);
            if (targetPlayer == null) {
                player.sendSystemMessage(I18n.translate("devmod.party.player_not_found", targetName));
                return;
            }

            if (targetPlayer.getUUID().equals(playerId)) {
                player.sendSystemMessage(I18n.translate("devmod.party.cannot_invite_self"));
                return;
            }

            PartyData existingParty = PartyManager.INSTANCE.getPlayerParty(playerId);
            PartyData party;
            if (existingParty == null) {
                party = PartyManager.INSTANCE.createParty(playerId, playerName, payload.getQuestType());
                if (party == null) {
                    player.sendSystemMessage(I18n.translate("devmod.party.create_failed"));
                    return;
                }
                LOGGER.info("[Party] {} created party {} via named invite", playerName, party.getPartyId());
            } else {
                party = existingParty;
            }

            if (!party.getLeaderId().equals(playerId)) {
                player.sendSystemMessage(I18n.translate("devmod.party.not_leader"));
                return;
            }

            var invite = PartyManager.INSTANCE.sendInvite(playerId, targetPlayer.getUUID(), targetName);
            if (invite != null) {
                player.sendSystemMessage(I18n.translate("devmod.party.invite_sent", targetName));
                sendPartyNotification(targetPlayer,
                    PartyNotificationPayload.inviteReceived(
                        invite.getInviteId(),
                        playerName,
                        party.getQuestType(),
                        invite.getExpiresAt()
                    ));
                sendPartySyncToPlayer(player);
            } else {
                player.sendSystemMessage(I18n.translate("devmod.party.invite_failed", targetName));
            }
        }), "named invite");
    }

    // =================================================================================
    // PARTY NOTIFICATION (client-side)
    // =================================================================================
    public static void handlePartyNotification(PartyNotificationPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePartyNotification(payload))), "party notification");
        }
    }

    // =================================================================================
    // PARTY SYNC (client-side)
    // =================================================================================
    public static void handlePartySync(PartySyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handlePartySync(payload))), "party sync");
        }
    }

    // =================================================================================
    // QUEST SEQUENCE HANDLERS
    // =================================================================================
    public static void handleQuestSequence(QuestSequencePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleQuestSequence(payload))), "quest sequence");
        }
    }

    public static void handleArrivalConfirm(ArrivalConfirmPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "arrival_confirm", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("arrival_confirm", player.getName().getString());
                return; // Fail closed: rate limited
            }

            var server = player.getServer();
            if (server != null) {
                QuestStartSequence.INSTANCE.confirmArrival(payload.partyId(), player.getUUID(), server);
            }
        }), "arrival confirm");
    }

    public static void handleCancelSequence(CancelSequencePayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check
            var validation = security().validatePacket(player, "cancel_sequence", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("cancel_sequence", player.getName().getString());
                return; // Fail closed: rate limited
            }

            boolean cancelled = QuestStartSequence.INSTANCE.cancelSequence(
                payload.partyId(), player.getUUID(), "Cancelled by leader");
            if (!cancelled) {
                player.sendSystemMessage(I18n.translate("devmod.party.cannot_cancel"));
            }
        }), "cancel sequence");
    }

    // =================================================================================
    // HELPER METHODS
    // =================================================================================
    public static void sendPartySyncToPlayer(ServerPlayer player) {
        var partyOpt = PartyManager.INSTANCE.getPartyByPlayer(player.getUUID());
        PartySyncPayload payload;

        if (partyOpt.isPresent()) {
            payload = PartySyncPayload.fromParty(partyOpt.get(),
                uuid -> player.server.getPlayerList().getPlayer(nn(uuid)) != null);
        } else {
            payload = PartySyncPayload.empty();
        }

        sendPacket(player, payload);
    }

    public static void syncPartyToAllMembers(MinecraftServer server, UUID partyId) {
        Optional<PartyData> partyOpt = PartyManager.INSTANCE.getPartyOpt(partyId);
        if (partyOpt.isEmpty()) return;

        var party = partyOpt.get();
        PartySyncPayload payload = PartySyncPayload.fromParty(party,
            uuid -> server.getPlayerList().getPlayer(nn(uuid)) != null);

        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(nn(memberId));
            if (member != null) {
                sendPacket(member, payload);
            }
        }
    }

    public static void notifyPartyMembers(MinecraftServer server, UUID partyId,
            PartyNotificationPayload notification, @Nullable UUID excludePlayer) {
        Optional<PartyData> partyOpt = PartyManager.INSTANCE.getPartyOpt(partyId);
        if (partyOpt.isEmpty()) return;

        for (UUID memberId : partyOpt.get().getMembers()) {
            if (excludePlayer != null && memberId.equals(excludePlayer)) continue;

            ServerPlayer member = server.getPlayerList().getPlayer(nn(memberId));
            if (member != null) {
                sendPacket(member, notification);
            }
        }
    }

    public static void sendPartyNotification(ServerPlayer player, PartyNotificationPayload notification) {
        sendPacket(player, notification);
    }

    // =================================================================================
    // MAILBOX NOTIFICATIONS FOR OFFLINE PERSISTENCE
    // =================================================================================

    private static final Logger LOGGER_MAILBOX = LoggerFactory.getLogger(PartyNetworkHandler.class.getName() + ".Mailbox");

    /**
     * Send mailbox notification when a player is kicked from a party.
     * This persists even if the player is offline.
     */
    private static void sendKickedMailbox(UUID kickedPlayerId, String kickedName, String leaderName) {
        try {
            var unused = MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "social.party_update",
                kickedPlayerId,
                Map.of(
                    "player_name", kickedName != null ? kickedName : "Player",
                    "update_type", "Kicked from Party",
                    "details", "You were kicked from the party by " + leaderName + ".",
                    "action_hint", "You can join or create a new party anytime."
                ),
                null
            ).exceptionally(e -> {
                LOGGER_MAILBOX.error("[Party] Async failure sending kicked mailbox to {}", kickedPlayerId, e);
                return Optional.empty();
            });
            LOGGER_MAILBOX.debug("[Party] Sent kicked mailbox to {}", kickedPlayerId);
        } catch (Exception e) {
            LOGGER_MAILBOX.error("[Party] Failed to send kicked mailbox notification", e);
        }
    }

    /**
     * Send mailbox notification when a party is disbanded.
     * This persists even if the player is offline.
     */
    private static void sendDisbandedMailbox(UUID memberId, String memberName, String leaderName) {
        try {
            var unused = MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "social.party_update",
                memberId,
                Map.of(
                    "player_name", memberName != null ? memberName : "Player",
                    "update_type", "Party Disbanded",
                    "details", "The party was disbanded by " + leaderName + ".",
                    "action_hint", "You can join or create a new party anytime."
                ),
                null
            ).exceptionally(e -> {
                LOGGER_MAILBOX.error("[Party] Async failure sending disbanded mailbox to {}", memberId, e);
                return Optional.empty();
            });
            LOGGER_MAILBOX.debug("[Party] Sent disbanded mailbox to {}", memberId);
        } catch (Exception e) {
            LOGGER_MAILBOX.error("[Party] Failed to send disbanded mailbox notification", e);
        }
    }
}

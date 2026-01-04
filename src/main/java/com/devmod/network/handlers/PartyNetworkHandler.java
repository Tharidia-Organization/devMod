package com.devmod.network.handlers;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.party.ArrivalConfirmPayload;
import com.devmod.party.CancelSequencePayload;
import com.devmod.party.InviteResponsePayload;
import com.devmod.party.NamedInvitePayload;
import com.devmod.party.PartyActionPayload;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.party.QuestStartSequence;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.PartyQuestSession;
import com.devmod.util.I18n;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for party system payloads.
 * Handles party creation, invites, quest sequences, and synchronization.
 */
public final class PartyNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final PartyNetworkHandler INSTANCE = new PartyNetworkHandler();

    private PartyNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "party";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // PARTY_ACTION: Client -> Server party actions (create, leave, kick, etc.)
        event.registrar(ChannelId.PARTY_ACTION.asString()).playToServer(
            nn(PartyActionPayload.TYPE),
            nn(PartyActionPayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handlePartyAction, PayloadLimits.SMALL)
        );

        // PARTY_SYNC: Server -> Client party state sync
        event.registrar(ChannelId.PARTY_SYNC.asString()).playToClient(
            nn(PartySyncPayload.TYPE),
            nn(PartySyncPayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handlePartySync, PayloadLimits.SMALL)
        );

        // QUEST_SEQUENCE: Server -> Client quest start sequence updates
        event.registrar(ChannelId.QUEST_SEQUENCE.asString()).playToClient(
            nn(QuestSequencePayload.TYPE),
            nn(QuestSequencePayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handleQuestSequence, PayloadLimits.SMALL)
        );

        // NAMED_INVITE: Client -> Server invite by player name
        event.registrar(ChannelId.NAMED_INVITE.asString()).playToServer(
            nn(NamedInvitePayload.TYPE),
            nn(NamedInvitePayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handleNamedInvite, PayloadLimits.SMALL)
        );

        // ARRIVAL_CONFIRM: Client -> Server confirm arrival at arena
        event.registrar(ChannelId.ARRIVAL_CONFIRM.asString()).playToServer(
            nn(ArrivalConfirmPayload.TYPE),
            nn(ArrivalConfirmPayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handleArrivalConfirm, PayloadLimits.SMALL)
        );

        // CANCEL_SEQUENCE: Client -> Server cancel quest start sequence
        event.registrar(ChannelId.CANCEL_SEQUENCE.asString()).playToServer(
            nn(CancelSequencePayload.TYPE),
            nn(CancelSequencePayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handleCancelSequence, PayloadLimits.SMALL)
        );

        // INVITE_RESPONSE: Client -> Server accept/decline invite
        event.registrar(ChannelId.INVITE_RESPONSE.asString()).playToServer(
            nn(InviteResponsePayload.TYPE),
            nn(InviteResponsePayload.STREAM_CODEC),
            validated(PartyNetworkHandler::handleInviteResponse, PayloadLimits.SMALL)
        );
    }

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
                        if (isPartyRunActive(party)) {
                            var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
                            if (sessionOpt.isPresent()) {
                                var session = sessionOpt.get();
                                if (session.isPartySpectator()) {
                                    EnduranceQuestManager.INSTANCE.rejoinPartyMember(player);
                                } else {
                                    EnduranceQuestManager.INSTANCE.requestPartyContinue(playerId);
                                }
                            } else {
                                var partySessionOpt = EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId());
                                if (partySessionOpt.isPresent()) {
                                    if (EnduranceQuestManager.INSTANCE.attachPartyMemberSession(player, partySessionOpt.get())) {
                                        EnduranceQuestManager.INSTANCE.rejoinPartyMember(player);
                                    }
                                }
                            }
                            return;
                        }
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
                        if (party.getState() == PartyData.PartyState.IN_QUEST
                            && EnduranceQuestManager.INSTANCE.getPartySession(partyId)
                                .map(PartyQuestSession::isActive).orElse(false)) {
                            EnduranceQuestManager.INSTANCE.markPartyMemberInactive(playerId, "leave_party");
                            player.setGameMode(GameType.SPECTATOR);
                            player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                                "[DevMod] You are now spectating the party run.")));
                            syncPartyToAllMembers(player.server, partyId);
                        } else if (PartyManager.INSTANCE.leaveParty(playerId)) {
                            LOGGER.info("[Party] {} left party {}", playerName, partyId);
                            syncPartyToAllMembers(player.server, partyId);
                            sendPartySyncToPlayer(player);
                        }
                    }
                }

                case KICK_MEMBER -> {
                    if (payload.targetPlayerId() != null) {
                        PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                        if (party != null && isPartyRunActive(party)) {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_kick_in_quest"));
                            return;
                        }
                        if (party != null && PartyManager.INSTANCE.kickMember(playerId, payload.targetPlayerId())) {
                            UUID partyId = party.getPartyId();
                            String kickedName = party.getMemberName(payload.targetPlayerId());
                            LOGGER.info("[Party] {} kicked {} from party {}", playerName, kickedName, partyId);

                            ServerPlayer kickedPlayer = player.server.getPlayerList().getPlayer(nn(payload.targetPlayerId()));
                            if (kickedPlayer != null) {
                                sendPartySyncToPlayer(kickedPlayer);
                            }

                            syncPartyToAllMembers(player.server, partyId);
                        }
                    }
                }

                case SET_QUEST_TYPE -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId)) {
                        if (isPartyRunActive(party)) {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_change_in_quest"));
                            return;
                        }
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
                        if (isPartyRunActive(party)) {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_change_in_quest"));
                            return;
                        }
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
                        if (isPartyRunActive(party)) {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_disband_in_quest"));
                            return;
                        }
                        UUID partyId = party.getPartyId();
                        var members = new ArrayList<>(party.getMembers());
                        if (PartyManager.INSTANCE.disbandParty(playerId)) {
                            LOGGER.info("[Party] {} disbanded party {}", playerName, partyId);

                            for (UUID memberId : members) {
                                if (!memberId.equals(playerId)) {
                                    ServerPlayer member = player.server.getPlayerList().getPlayer(nn(memberId));
                                    if (member != null) {
                                        sendPartySyncToPlayer(member);
                                    }
                                }
                            }
                        }
                    }
                }

                case START_QUEST -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party != null && party.isLeader(playerId) && party.canStartQuest()) {
                        if (isPartyRunActive(party)) {
                            player.sendSystemMessage(I18n.translate("devmod.party.cannot_start"));
                            return;
                        }
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

                case SET_KIT -> {
                    PartyData party = PartyManager.INSTANCE.getPlayerParty(playerId);
                    if (party == null) {
                        return;
                    }
                    if (isPartyRunActive(party)) {
                        player.sendSystemMessage(I18n.translate("devmod.party.cannot_change_in_quest"));
                        return;
                    }
                    String kitId = payload.kitId();
                    if (!isValidKitSelection(player, kitId)) {
                        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
                            "[DevMod] Invalid kit selection")));
                        return;
                    }
                    if (party.setMemberKit(playerId, kitId)) {
                        LOGGER.info("[Party] {} selected kit {} in party {}", playerName, kitId, party.getPartyId());
                        syncPartyToAllMembers(player.server, party.getPartyId());
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
                            syncPartyToAllMembers(player.server, result.partyId());
                        }
                    } else {
                        LOGGER.info("[Party] {} declined invite {}", playerName, inviteId);
                }
            } else {
                String rawMsg = result.errorMessage();
                String errorMsg = rawMsg != null ? rawMsg : "Unknown error";
                if (errorMsg.startsWith("devmod.")) {
                    player.sendSystemMessage(I18n.translate(errorMsg));
                } else {
                    player.sendSystemMessage(I18n.translate("devmod.party.invite_error", errorMsg));
                }
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
            if (isPartyRunActive(party)) {
                player.sendSystemMessage(I18n.translate("devmod.party.cannot_invite_in_quest"));
                return;
            }

            var invite = PartyManager.INSTANCE.sendInvite(playerId, targetPlayer.getUUID(), targetName);
            if (invite != null) {
                player.sendSystemMessage(I18n.translate("devmod.party.invite_sent", targetName));
                sendPartySyncToPlayer(player);
            } else {
                player.sendSystemMessage(I18n.translate("devmod.party.invite_failed", targetName));
            }
        }), "named invite");
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
            PartyData party = partyOpt.get();
            var partySession = EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId()).orElse(null);
            java.util.function.Predicate<UUID> spectatorChecker = partySession != null && partySession.isActive()
                ? partySession::isSpectator
                : null;
            java.util.function.Predicate<UUID> readyChecker = partySession != null && partySession.isActive()
                ? partySession::isWaveReady
                : null;
            payload = PartySyncPayload.fromParty(party,
                uuid -> player.server.getPlayerList().getPlayer(nn(uuid)) != null,
                spectatorChecker,
                readyChecker);
        } else {
            payload = PartySyncPayload.empty();
        }

        sendPacket(player, payload);
    }

    public static void syncPartyToAllMembers(MinecraftServer server, UUID partyId) {
        Optional<PartyData> partyOpt = PartyManager.INSTANCE.getPartyOpt(partyId);
        if (partyOpt.isEmpty()) return;

        var party = partyOpt.get();
        var partySession = EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId()).orElse(null);
        java.util.function.Predicate<UUID> spectatorChecker = partySession != null && partySession.isActive()
            ? partySession::isSpectator
            : null;
        java.util.function.Predicate<UUID> readyChecker = partySession != null && partySession.isActive()
            ? partySession::isWaveReady
            : null;
        PartySyncPayload payload = PartySyncPayload.fromParty(party,
            uuid -> server.getPlayerList().getPlayer(nn(uuid)) != null,
            spectatorChecker,
            readyChecker);

        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(nn(memberId));
            if (member != null) {
                sendPacket(member, payload);
            }
        }
    }

    private static boolean isPartyRunActive(PartyData party) {
        if (party == null) {
            return false;
        }
        if (party.getState() == PartyData.PartyState.IN_QUEST) {
            return true;
        }
        return EnduranceQuestManager.INSTANCE.getPartySession(party.getPartyId())
            .map(PartyQuestSession::isActive)
            .orElse(false);
    }

    private static boolean isValidKitSelection(ServerPlayer player, String kitId) {
        if (player == null || kitId == null || kitId.isBlank()) {
            return false;
        }
        if ("TEMPORARY".equals(kitId)) {
            return KitManager.INSTANCE.hasTemporaryKit(player.getUUID());
        }
        if (kitId.length() == 8) {
            UUID playerId = player.getUUID();
            return KitManager.INSTANCE.getSyncedCustomKit(playerId, kitId).isPresent()
                || KitManager.INSTANCE.getCustomKit(kitId).isPresent();
        }
        return KitManager.INSTANCE.getKitById(kitId) != null;
    }

}

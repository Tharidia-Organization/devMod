package com.devmod.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.QuestSequenceOverlay;
import com.devmod.client.party.ClientPartyCache;
import com.devmod.client.party.PartyUiCache;
import com.devmod.party.PartyNotificationPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public final class ClientPartyHandlers {

    private ClientPartyHandlers() {}

    public static void handlePartyNotification(PartyNotificationPayload payload) {
        ClientPartyCache.handleNotification(payload);

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (payload.notificationType()) {
            case INVITE_RECEIVED -> {
                PartyUiCache.setLastInvite(payload);
                ActionRegistry.invoke(ActionIds.UI_PARTY_INVITE_POPUP_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.EVENT, payload));
            }
            case YOU_WERE_KICKED, PARTY_DISBANDED -> player.displayClientMessage(
                I18n.translate("devmod.party." + payload.notificationType().name().toLowerCase()), true);
            case MEMBER_JOINED, MEMBER_LEFT -> player.displayClientMessage(
                I18n.translate("devmod.party.member_" +
                    (payload.notificationType() == PartyNotificationPayload.NotificationType.MEMBER_JOINED ? "joined" : "left"),
                    payload.playerName()), true);
            default -> {}
        }
    }

    public static void handlePartySync(PartySyncPayload payload) {
        ClientPartyCache.update(payload);
    }

    public static void handleQuestSequence(QuestSequencePayload payload) {
        QuestSequenceOverlay.INSTANCE.update(payload);
    }
}

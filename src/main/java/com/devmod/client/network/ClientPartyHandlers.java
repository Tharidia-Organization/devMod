package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.overlay.QuestSequenceOverlay;
import com.devmod.client.party.ClientPartyCache;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;

@OnlyIn(Dist.CLIENT)
public final class ClientPartyHandlers {

    private ClientPartyHandlers() {}

    public static void handlePartySync(PartySyncPayload payload) {
        ClientPartyCache.update(payload);
    }

    public static void handleQuestSequence(QuestSequencePayload payload) {
        QuestSequenceOverlay.INSTANCE.update(payload);
    }
}

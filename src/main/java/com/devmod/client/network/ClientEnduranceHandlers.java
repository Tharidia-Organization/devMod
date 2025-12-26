package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.endurance.ClientChallengeCache;
import com.devmod.client.endurance.ClientPersonalRecordsCache;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.EnduranceUiCache;
import com.devmod.client.overlay.InstanceLoadingOverlay;
import com.devmod.endurance.ClientShopCache;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public final class ClientEnduranceHandlers {

    private ClientEnduranceHandlers() {}

    public static void handleQuestSync(QuestSyncPayload payload) {
        ClientQuestCache.update(payload);
    }

    public static void handleShopSync(ShopSyncPayload payload) {
        ClientShopCache.update(payload);
    }

    public static void handlePerkChoices(PerkChoicesPayload payload) {
        EnduranceUiCache.setLastPerkChoices(payload);
        ActionRegistry.invoke(ActionIds.UI_PERK_SELECTION_OPEN,
            ClientActionContexts.forClient(ActionOrigin.EVENT, payload));
    }

    public static void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload) {
        EnduranceUiCache.setLastDirectiveChoices(payload);
    }

    public static void handleQuestCompletion(QuestCompletionPayload payload) {
        EnduranceUiCache.setLastQuestCompletion(payload);
        ActionRegistry.invoke(ActionIds.UI_QUEST_COMPLETION_OPEN,
            ClientActionContexts.forClient(ActionOrigin.EVENT, payload));
    }

    public static void handleInstanceLoading(boolean show, String status) {
        if (show) {
            String translatedStatus = I18n.translate(status).getString();
            InstanceLoadingOverlay.show(translatedStatus);
        } else {
            InstanceLoadingOverlay.hide();
        }
    }

    public static void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload) {
        ClientPersonalRecordsCache.update(payload);
    }

    public static void handleQuestDeath() {
        ActionRegistry.invoke(ActionIds.UI_QUEST_DEATH_OPEN,
            ClientActionContexts.forClient(ActionOrigin.EVENT));
    }

    public static void handleChallengeSync(ChallengeSyncPayload payload) {
        ClientChallengeCache.INSTANCE.handleSync(payload);
    }
}

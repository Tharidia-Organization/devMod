package com.devmod.client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.endurance.ClientChallengeCache;
import com.devmod.client.endurance.ClientMobPoolConfigCache;
import com.devmod.client.endurance.ClientPartyStatsCache;
import com.devmod.client.endurance.ClientPersonalRecordsCache;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.client.endurance.EnduranceQuestScreen;
import com.devmod.client.endurance.EnduranceUiCache;
import com.devmod.client.endurance.MobPoolEditorScreen;
import com.devmod.client.endurance.QuestDeathScreen;
import com.devmod.client.overlay.InstanceLoadingOverlay;
import com.devmod.client.party.PartyScreen;
import com.devmod.endurance.ClientShopCache;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.MobPoolConfigSyncPayload;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEnduranceHandlers.class);

    private ClientEnduranceHandlers() {}

    public static void handleQuestSync(QuestSyncPayload payload) {
        ClientQuestCache.update(payload);
    }

    public static void handleShopSync(ShopSyncPayload payload) {
        ClientShopCache.update(payload);
    }

    public static void handleMobPoolConfigSync(MobPoolConfigSyncPayload payload) {
        ClientMobPoolConfigCache.update(payload);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof MobPoolEditorScreen screen) {
            screen.applyServerConfig(payload);
        }
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
        LOGGER.info("[EnduranceQuest][Client] QuestCompletion packet received: wave={}/{}, tokens={}",
            payload.finalWave(), payload.totalWaves(), payload.tokensEarned());
        // Quest is ending - clear all death screen suppression flags
        // This prevents showing QuestDeathScreen after the quest has ended
        ClientQuestCache.forceStopDeathScreenSuppression();
        ClientQuestCache.setPendingQuestEnd(false);
        EnduranceUiCache.setLastQuestCompletion(payload);
        // Clear party stats cache since quest is ending
        ClientPartyStatsCache.clear();
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

    /**
     * Handle quest death packet from server.
     * Opens QuestDeathScreen directly instead of via ActionRegistry to bypass
     * precondition timing issues (cache may not be updated when packet arrives).
     */
    public static void handleQuestDeath() {
        // Start suppressing vanilla DeathScreen - persists through respawn
        ClientQuestCache.startDeathScreenSuppression();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            LOGGER.info("[EnduranceQuest][Client] QuestDeath packet received (player={}, dimension={}, isDead={}, hasActiveQuest={})",
                mc.player.getName().getString(),
                mc.player.level().dimension().location(),
                mc.player.isDeadOrDying(),
                ClientQuestCache.hasActiveQuest());
        } else {
            LOGGER.info("[EnduranceQuest][Client] QuestDeath packet received (player=unknown)");
        }
        if (mc != null) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "quest_death",
                () -> new QuestDeathScreen());
        }
    }

    public static void handleChallengeSync(ChallengeSyncPayload payload) {
        ClientChallengeCache.INSTANCE.handleSync(payload);
    }

    public static void handleKitSyncConfirm(KitSyncConfirmPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            if (mc.screen instanceof EnduranceQuestScreen screen) {
                screen.onKitSyncConfirm(payload);
            } else if (mc.screen instanceof PartyScreen partyScreen) {
                partyScreen.onKitSyncConfirm(payload);
            }
        }
    }
}

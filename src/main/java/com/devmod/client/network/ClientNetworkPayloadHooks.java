package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.client.arena.hud.BuildProgressHud;
import com.devmod.client.config.ClientMechanicsCache;
import com.devmod.endurance.BadgeUnlockPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.ComboDecayPayload;
import com.devmod.endurance.InstanceLoadingPayload;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.QuestCompletionPayload;
import com.devmod.endurance.QuestDeathPayload;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.RecordBannerPayload;
import com.devmod.endurance.ShopSyncPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.TokenGainPayload;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.endurance.resonance.ResonanceNotificationPayload;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.network.GlobalConfigSyncPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.RecipeClientSyncPayload;
import com.devmod.network.ShieldImpactPayload;
import com.devmod.network.ShieldShatterPayload;
import com.devmod.network.ShieldStatePayload;
import com.devmod.party.PartyNotificationPayload;
import com.devmod.party.PartySyncPayload;
import com.devmod.party.QuestSequencePayload;
@OnlyIn(Dist.CLIENT)
public final class ClientNetworkPayloadHooks implements NetworkHandler.ClientPayloadHooks {

    @Override
    public void handleGlobalConfigSync(GlobalConfigSyncPayload payload) {
        ClientConfigHandlers.handleGlobalConfigSync(payload);
    }

    @Override
    public void handleRecipeClientSync(RecipeClientSyncPayload payload) {
        ClientConfigHandlers.handleRecipeClientSync(payload);
    }

    @Override
    public void handleGameMechanicsSync(GameMechanicsSyncPayload payload) {
        ClientMechanicsCache cache = ClientMechanicsCache.INSTANCE;
        if (payload.questId() == null) {
            cache.applyGlobalSync(payload.mechanicsConfig());
        } else {
            cache.applyQuestSync(payload.questId(),
                payload.questOverrides() != null ? payload.questOverrides() : payload.mechanicsConfig());
        }
    }

    @Override
    public void handleEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        ClientConfigFeedbackPayload.handleEditorApplyConfirm(payload);
    }

    @Override
    public void handleConfigEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        ClientConfigHandlers.handleEditorApplyConfirm(payload);
    }

    @Override
    public void handleResonanceTriggered(ResonanceNotificationPayload payload) {
        ClientOverlayHandlers.handleResonanceTriggered(payload);
    }

    @Override
    public void handleContractSync(ContractSyncPayload payload) {
        ClientOverlayHandlers.handleContractSync(payload);
    }

    @Override
    public void handleMobConfigConfirm(MobConfigConfirmPayload payload) {
        ClientConfigFeedbackPayload.handleMobConfigConfirm(payload);
    }

    @Override
    public void handleConfigMobConfigConfirm(MobConfigConfirmPayload payload) {
        ClientConfigHandlers.handleMobConfigConfirm(payload);
    }

    @Override
    public void handleQuestSync(QuestSyncPayload payload) {
        ClientEnduranceHandlers.handleQuestSync(payload);
    }

    @Override
    public void handleShopSync(ShopSyncPayload payload) {
        ClientEnduranceHandlers.handleShopSync(payload);
    }

    @Override
    public void handleQuestDeath(QuestDeathPayload payload) {
        ClientEnduranceHandlers.handleQuestDeath();
    }

    @Override
    public void handlePerkChoices(PerkChoicesPayload payload) {
        ClientEnduranceHandlers.handlePerkChoices(payload);
    }

    @Override
    public void handleWaveDirectiveChoices(WaveDirectiveChoicesPayload payload) {
        ClientEnduranceHandlers.handleWaveDirectiveChoices(payload);
    }

    @Override
    public void handleQuestCompletion(QuestCompletionPayload payload) {
        ClientEnduranceHandlers.handleQuestCompletion(payload);
    }

    @Override
    public void handleInstanceLoading(InstanceLoadingPayload payload) {
        ClientEnduranceHandlers.handleInstanceLoading(payload.show(), payload.status());
    }

    @Override
    public void handlePersonalRecordsSync(PersonalRecordsSyncPayload payload) {
        ClientEnduranceHandlers.handlePersonalRecordsSync(payload);
    }

    @Override
    public void handlePartyNotification(PartyNotificationPayload payload) {
        ClientPartyHandlers.handlePartyNotification(payload);
    }

    @Override
    public void handlePartySync(PartySyncPayload payload) {
        ClientPartyHandlers.handlePartySync(payload);
    }

    @Override
    public void handleQuestSequence(QuestSequencePayload payload) {
        ClientPartyHandlers.handleQuestSequence(payload);
    }

    @Override
    public void handleShieldState(ShieldStatePayload payload) {
        ClientShieldHandlers.handleShieldState(payload.isShattered(), payload.isActive());
    }

    @Override
    public void handleShieldImpact(ShieldImpactPayload payload) {
        ClientShieldHandlers.handleShieldImpact(
            payload.impactX(), payload.impactY(), payload.impactZ(), payload.damage());
    }

    @Override
    public void handleShieldShatter(ShieldShatterPayload payload) {
        ClientShieldHandlers.handleShieldShatter(payload.centerX(), payload.centerY(), payload.centerZ());
    }

    @Override
    public void handleBossAlert(BossAlertPayload payload) {
        ClientOverlayHandlers.handleBossAlert(payload.alertDurationMs(), payload.bossType());
    }

    @Override
    public void handleBadgeUnlock(BadgeUnlockPayload payload) {
        ClientOverlayHandlers.handleBadgeUnlock(payload.badgeName(), payload.rarity());
    }

    @Override
    public void handleTokenGain(TokenGainPayload payload) {
        ClientOverlayHandlers.handleTokenGain(payload.amount());
    }

    @Override
    public void handleRecordBanner(RecordBannerPayload payload) {
        ClientOverlayHandlers.handleRecordBanner(payload.recordType(), payload.recordValue());
    }

    @Override
    public void handleComboDecay(ComboDecayPayload payload) {
        ClientOverlayHandlers.handleComboDecay(
            payload.lostCombo(), payload.previousRankOrdinal(), payload.newRankOrdinal());
    }

    @Override
    public void handleTensionUpdate(TensionUpdatePayload payload) {
        ClientOverlayHandlers.handleTensionUpdate(
            payload.tensionPercent(), payload.tensionLevel(), payload.bossImminent());
    }

    @Override
    public void handleStaminaSync(StaminaSyncPayload payload) {
        ClientOverlayHandlers.handleStaminaSync(payload.currentStamina(), payload.maxStamina());
    }

    @Override
    public void handleBuildProgress(BuildProgressPayload payload) {
        BuildProgressHud.getInstance().handlePayload(payload);
    }

    @Override
    public void handleChallengeSync(ChallengeSyncPayload payload) {
        ClientEnduranceHandlers.handleChallengeSync(payload);
    }
}

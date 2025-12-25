package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.arena.network.BuildProgressPayload;
import com.devmod.client.arena.hud.BuildProgressHud;
import com.devmod.endurance.BadgeUnlockPayload;
import com.devmod.endurance.BossAlertPayload;
import com.devmod.endurance.ComboDecayPayload;
import com.devmod.endurance.RecordBannerPayload;
import com.devmod.endurance.TensionUpdatePayload;
import com.devmod.endurance.TokenGainPayload;
import com.devmod.endurance.challenges.ChallengeSyncPayload;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.endurance.resonance.ResonanceNotificationPayload;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.NetworkHandler;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkPayloadHooks implements NetworkHandler.ClientPayloadHooks {

    @Override
    public void handleEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        ClientConfigFeedbackPayload.handleEditorApplyConfirm(payload);
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

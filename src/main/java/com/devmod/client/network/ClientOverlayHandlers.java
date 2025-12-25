package com.devmod.client.network;

import com.devmod.client.abilities.ClientStaminaCache;
import com.devmod.client.overlay.BadgePopupOverlay;
import com.devmod.client.overlay.ComboDecayOverlay;
import com.devmod.client.overlay.ContractHudOverlay;
import com.devmod.client.overlay.EnduranceQuestOverlay;
import com.devmod.client.overlay.ResonanceHudOverlay;
import com.devmod.client.overlay.RecordBannerOverlay;
import com.devmod.client.overlay.TokenGainOverlay;
import com.devmod.endurance.contracts.ContractSyncPayload;
import com.devmod.endurance.resonance.ResonanceNotificationPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side handlers for overlay network packets.
 * This class is only loaded on the client.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientOverlayHandlers {

    private ClientOverlayHandlers() {}

    public static void handleBossAlert(long alertDurationMs, String bossType) {
        EnduranceQuestOverlay.onBossAlert(alertDurationMs, bossType);
    }

    public static void handleBadgeUnlock(String badgeName, String rarity) {
        BadgePopupOverlay.showBadge(badgeName, rarity);
    }

    public static void handleTokenGain(int amount) {
        TokenGainOverlay.show(amount);
    }

    public static void handleRecordBanner(String recordType, String recordValue) {
        RecordBannerOverlay.showRecord(recordType, recordValue);
    }

    public static void handleComboDecay(int lostCombo, int previousRankOrdinal, int newRankOrdinal) {
        ComboDecayOverlay.show(lostCombo, previousRankOrdinal, newRankOrdinal);
    }

    public static void handleStaminaSync(float currentStamina, float maxStamina) {
        ClientStaminaCache.update(currentStamina, maxStamina);
    }

    public static void handleResonanceTriggered(ResonanceNotificationPayload payload) {
        ResonanceHudOverlay.INSTANCE.onResonanceTriggered(payload);
    }

    public static void handleContractSync(ContractSyncPayload payload) {
        ContractHudOverlay.INSTANCE.onContractSync(payload);
    }

    public static void handleTensionUpdate(float tensionPercent, int tensionLevel, boolean bossImminent) {
        ClientTensionCache.update(tensionPercent, tensionLevel, bossImminent);
    }
}

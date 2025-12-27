package com.devmod.client.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.abilities.ClientStaminaCache;
import com.devmod.client.overlay.ContractHudOverlay;
import com.devmod.client.overlay.EnduranceQuestOverlay;
import com.devmod.endurance.contracts.ContractSyncPayload;

@OnlyIn(Dist.CLIENT)
public final class ClientOverlayHandlers {

    private ClientOverlayHandlers() {}

    public static void handleBossAlert(long alertDurationMs, String bossType) {
        EnduranceQuestOverlay.onBossAlert(alertDurationMs, bossType);
    }

    public static void handleStaminaSync(float currentStamina, float maxStamina) {
        ClientStaminaCache.update(currentStamina, maxStamina);
    }

    public static void handleContractSync(ContractSyncPayload payload) {
        ContractHudOverlay.INSTANCE.onContractSync(payload);
    }

    public static void handleTensionUpdate(float tensionPercent, int tensionLevel, boolean bossImminent) {
        ClientTensionCache.update(tensionPercent, tensionLevel, bossImminent);
    }
}

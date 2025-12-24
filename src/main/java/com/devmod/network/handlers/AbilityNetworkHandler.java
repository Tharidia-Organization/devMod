package com.devmod.network.handlers;

import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.ClientStaminaCache;
import com.devmod.abilities.DashAbilitySystem;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.abilities.StaminaSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for ability system packets (dash, dodge, stamina).
 * Extracted from NetworkHandler for single responsibility.
 */
public final class AbilityNetworkHandler extends NetworkHandlerBase {

    private AbilityNetworkHandler() {}

    // =================================================================================
    // ABILITY ACTION (server-side)
    // =================================================================================
    public static void handleAbilityAction(AbilityActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (payload.ability()) {
                    case DASH -> {
                        boolean success = DashAbilitySystem.INSTANCE.tryDash(player);
                        if (!success) {
                            // Could send feedback to client here
                        }
                    }
                    case DODGE -> {
                        var direction = payload.getDodgeDirection();
                        boolean success = DodgeAbilitySystem.INSTANCE.tryDodge(player, direction);
                        if (!success) {
                            // Could send feedback to client here
                        }
                    }
                }
            }
        });
    }

    // =================================================================================
    // STAMINA SYNC (client-side)
    // =================================================================================
    public static void handleStaminaSync(StaminaSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStaminaCache.update(payload.currentStamina(), payload.maxStamina()));
    }

    /**
     * Send stamina sync to a player.
     * Called periodically from StaminaSystem to update client HUD.
     */
    public static void sendStaminaSync(ServerPlayer player, float currentStamina, float maxStamina) {
        StaminaSyncPayload payload = new StaminaSyncPayload(currentStamina, maxStamina);
        sendPacket(player, payload);
    }
}

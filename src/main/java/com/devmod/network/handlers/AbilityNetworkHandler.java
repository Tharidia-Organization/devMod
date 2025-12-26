package com.devmod.network.handlers;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.DashAbilitySystem;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.abilities.StaminaSyncPayload;

public final class AbilityNetworkHandler extends NetworkHandlerBase {

    private AbilityNetworkHandler() {}

    // =================================================================================
    // ABILITY ACTION (server-side)
    // =================================================================================
    public static void handleAbilityAction(AbilityActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check to prevent ability spam
            var validation = security().validatePacket(player, "ability_action", false);
            if (!validation.isSuccess()) {
                String errorMessage = Objects.requireNonNullElse(
                    validation.getErrorMessage(),
                    "Ability action rejected"
                );
                if (errorMessage.toLowerCase().contains("rate limit")) {
                    security().recordRateLimitHit("ability_action", player.getName().getString());
                } else {
                    security().recordRejection("ability_action", errorMessage);
                }
                return; // Fail closed: rate limited
            }

            var ability = payload.ability();
            if (ability == null) {
                security().recordRejection("ability_action", "Invalid ability type");
                return;
            }

            switch (ability) {
                case DASH -> {
                    boolean success = DashAbilitySystem.INSTANCE.tryDash(player);
                    if (!success) {
                        // Could send feedback to client here
                    }
                }
                case DODGE -> {
                    int dirOrdinal = payload.direction();
                    if (dirOrdinal < 0 || dirOrdinal >= DodgeAbilitySystem.DodgeDirection.values().length) {
                        security().recordRejection("ability_action", "Invalid dodge direction: " + dirOrdinal);
                        return;
                    }
                    var direction = payload.getDodgeDirection();
                    boolean success = DodgeAbilitySystem.INSTANCE.tryDodge(player, direction);
                    if (!success) {
                        // Could send feedback to client here
                    }
                }
            }
        });
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

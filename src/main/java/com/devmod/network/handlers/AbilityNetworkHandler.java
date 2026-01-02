package com.devmod.network.handlers;

import java.util.Locale;
import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.DashAbilitySystem;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.abilities.StaminaSyncPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PayloadValidation.PayloadLimits;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for ability system payloads.
 * Handles stamina sync and ability actions (dash, dodge).
 */
public final class AbilityNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final AbilityNetworkHandler INSTANCE = new AbilityNetworkHandler();

    private AbilityNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "ability";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // STAMINA_SYNC: Server -> Client stamina updates for HUD
        event.registrar(ChannelId.STAMINA_SYNC.asString()).playToClient(
            nn(StaminaSyncPayload.TYPE),
            nn(StaminaSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleStaminaSync(payload))), "stamina sync");
                }
            }, PayloadLimits.SMALL)
        );

        // ABILITY_ACTION: Client -> Server ability activation requests
        event.registrar(ChannelId.ABILITY_ACTION.asString()).playToServer(
            nn(AbilityActionPayload.TYPE),
            nn(AbilityActionPayload.STREAM_CODEC),
            validated(AbilityNetworkHandler::handleAbilityAction, PayloadLimits.QUEST_ACTION)
        );
    }

    // =================================================================================
    // ABILITY ACTION (server-side)
    // =================================================================================
    public static void handleAbilityAction(AbilityActionPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
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
                if (errorMessage.toLowerCase(Locale.ROOT).contains("rate limit")) {
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
        }), "ability action");
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

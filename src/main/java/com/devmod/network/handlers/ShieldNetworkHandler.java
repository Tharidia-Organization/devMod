package com.devmod.network.handlers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.ShieldImpactPayload;
import com.devmod.network.ShieldShatterPayload;
import com.devmod.network.ShieldStatePayload;

/**
 * P2: Domain-specific network handler for shield visual effect payloads.
 * Handles shield state, impact, and shatter effects.
 */
public final class ShieldNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final ShieldNetworkHandler INSTANCE = new ShieldNetworkHandler();

    private ShieldNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "shield";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(ChannelId.SHIELD_STATE.asString()).playToClient(
            nn(ShieldStatePayload.TYPE),
            nn(ShieldStatePayload.STREAM_CODEC),
            ShieldNetworkHandler::handleShieldState
        );
        event.registrar(ChannelId.SHIELD_IMPACT.asString()).playToClient(
            nn(ShieldImpactPayload.TYPE),
            nn(ShieldImpactPayload.STREAM_CODEC),
            ShieldNetworkHandler::handleShieldImpact
        );
        event.registrar(ChannelId.SHIELD_SHATTER.asString()).playToClient(
            nn(ShieldShatterPayload.TYPE),
            nn(ShieldShatterPayload.STREAM_CODEC),
            ShieldNetworkHandler::handleShieldShatter
        );
    }

    // =================================================================================
    // SHIELD STATE SYNC (client-side)
    // =================================================================================
    public static void handleShieldState(ShieldStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldState(payload))), "shield state");
        }
    }

    // =================================================================================
    // SHIELD IMPACT (client-side)
    // =================================================================================
    public static void handleShieldImpact(ShieldImpactPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() -> {
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldImpact(payload));
                LOGGER.debug("Shield impact at ({}, {}, {}) (deflection={})",
                    payload.impactX(), payload.impactY(), payload.impactZ(), payload.wasDeflection());
            }), "shield impact");
        }
    }

    // =================================================================================
    // SHIELD SHATTER (client-side)
    // =================================================================================
    public static void handleShieldShatter(ShieldShatterPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() -> {
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldShatter(payload));
                LOGGER.debug("Shield shattered at ({}, {}, {}) (damage={})",
                    payload.centerX(), payload.centerY(), payload.centerZ(), payload.finalDamage());
            }), "shield shatter");
        }
    }
}

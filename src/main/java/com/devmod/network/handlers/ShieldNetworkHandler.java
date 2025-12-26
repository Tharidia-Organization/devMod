package com.devmod.network.handlers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.NetworkHandler;
import com.devmod.network.ShieldImpactPayload;
import com.devmod.network.ShieldShatterPayload;
import com.devmod.network.ShieldStatePayload;
public final class ShieldNetworkHandler extends NetworkHandlerBase {

    private ShieldNetworkHandler() {}

    // =================================================================================
    // SHIELD STATE SYNC (client-side)
    // =================================================================================
    public static void handleShieldState(ShieldStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldState(payload)));
        }
    }

    // =================================================================================
    // SHIELD IMPACT (client-side)
    // =================================================================================
    public static void handleShieldImpact(ShieldImpactPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> {
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldImpact(payload));
                LOGGER.debug("Shield impact at ({}, {}, {}) (deflection={})",
                    payload.impactX(), payload.impactY(), payload.impactZ(), payload.wasDeflection());
            });
        }
    }

    // =================================================================================
    // SHIELD SHATTER (client-side)
    // =================================================================================
    public static void handleShieldShatter(ShieldShatterPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> {
                NetworkHandler.withClientHooks(hooks -> hooks.handleShieldShatter(payload));
                LOGGER.debug("Shield shattered at ({}, {}, {}) (damage={})",
                    payload.centerX(), payload.centerY(), payload.centerZ(), payload.finalDamage());
            });
        }
    }
}

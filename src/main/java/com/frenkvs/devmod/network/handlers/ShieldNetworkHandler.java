package com.frenkvs.devmod.network.handlers;

import com.frenkvs.devmod.network.ShieldImpactPayload;
import com.frenkvs.devmod.network.ShieldShatterPayload;
import com.frenkvs.devmod.network.ShieldStatePayload;
import com.frenkvs.devmod.rendering.shield.EnergyShieldRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for shield visual effect packets.
 * Extracted from NetworkHandler for single responsibility.
 */
public final class ShieldNetworkHandler extends NetworkHandlerBase {

    private ShieldNetworkHandler() {}

    // =================================================================================
    // SHIELD STATE SYNC (client-side)
    // =================================================================================
    public static void handleShieldState(ShieldStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.isShattered()) {
                EnergyShieldRenderer.triggerShatter(new Vec3(0, 0, 0));
            } else if (!payload.isActive()) {
                EnergyShieldRenderer.clearEffects();
            }
        });
    }

    // =================================================================================
    // SHIELD IMPACT (client-side)
    // =================================================================================
    public static void handleShieldImpact(ShieldImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Vec3 impactPoint = new Vec3(payload.impactX(), payload.impactY(), payload.impactZ());
            EnergyShieldRenderer.recordImpact(impactPoint, payload.damage());
            LOGGER.debug("Shield impact at {} (deflection={})", impactPoint, payload.wasDeflection());
        });
    }

    // =================================================================================
    // SHIELD SHATTER (client-side)
    // =================================================================================
    public static void handleShieldShatter(ShieldShatterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Vec3 center = new Vec3(payload.centerX(), payload.centerY(), payload.centerZ());
            EnergyShieldRenderer.triggerShatter(center);
            LOGGER.debug("Shield shattered at {} (damage={})", center, payload.finalDamage());
        });
    }
}

package com.devmod.client.network;

import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.rendering.shield.EnergyShieldRenderer;

@OnlyIn(Dist.CLIENT)
public final class ClientShieldHandlers {

    private ClientShieldHandlers() {}

    public static void handleShieldState(int entityId, boolean isShattered, boolean isActive) {
        // A shattered state carries no impact position; the ripple comes from ShieldShatterPayload.
        if (!isShattered && !isActive) {
            EnergyShieldRenderer.clearEffects(entityId);
        }
    }

    public static void handleShieldImpact(int entityId, double impactX, double impactY, double impactZ, float damage) {
        Vec3 impactPoint = new Vec3(impactX, impactY, impactZ);
        EnergyShieldRenderer.recordImpact(entityId, impactPoint, damage);
    }

    public static void handleShieldShatter(int entityId, double centerX, double centerY, double centerZ) {
        Vec3 center = new Vec3(centerX, centerY, centerZ);
        EnergyShieldRenderer.triggerShatter(entityId, center);
    }
}

package com.devmod.client.network;

import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.rendering.shield.EnergyShieldRenderer;

/**
 * Client-side handlers for Shield network packets.
 * This class is only loaded on the client.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientShieldHandlers {

    private ClientShieldHandlers() {}

    public static void handleShieldState(boolean isShattered, boolean isActive) {
        if (isShattered) {
            EnergyShieldRenderer.triggerShatter(new Vec3(0, 0, 0));
        } else if (!isActive) {
            EnergyShieldRenderer.clearEffects();
        }
    }

    public static void handleShieldImpact(double impactX, double impactY, double impactZ, float damage) {
        Vec3 impactPoint = new Vec3(impactX, impactY, impactZ);
        EnergyShieldRenderer.recordImpact(impactPoint, damage);
    }

    public static void handleShieldShatter(double centerX, double centerY, double centerZ) {
        Vec3 center = new Vec3(centerX, centerY, centerZ);
        EnergyShieldRenderer.triggerShatter(center);
    }
}

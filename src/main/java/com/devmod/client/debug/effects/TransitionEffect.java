package com.devmod.client.debug.effects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;

/**
 * Interface for zone transition visual effects.
 * Implementations provide different visual styles for zone boundaries.
 */
public interface TransitionEffect {

    /**
     * Renders the transition effect for a zone boundary.
     *
     * @param poseStack The pose stack for transforms
     * @param bufferSource Buffer source for rendering
     * @param cameraPos Camera position
     * @param zone The zone to render
     * @param baseY The Y level of the arena floor
     * @param time Current render time for animations
     */
    void render(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Vec3 cameraPos,
        ArenaZone zone,
        int baseY,
        float time
    );

    /**
     * Gets the unique name for this effect.
     */
    String getName();

    /**
     * Whether this effect requires particle spawning.
     */
    default boolean usesParticles() {
        return false;
    }

    /**
     * Whether this effect should render glow/bloom.
     */
    default boolean usesGlow() {
        return false;
    }
}

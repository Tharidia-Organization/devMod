package com.devmod.client.nexus;

import javax.annotation.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * Custom dimension effects for the Nexus hub dimension.
 * Provides a dark, atmospheric void sky with no clouds or weather.
 */
@OnlyIn(Dist.CLIENT)
public class NexusDimensionEffects extends DimensionSpecialEffects {

    public NexusDimensionEffects() {
        // Float.NaN = no cloud level, true = has ground, NONE = no vanilla sky, false = no bright lightmap, true = constant ambient light
        super(Float.NaN, true, SkyType.NONE, false, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // Deep dark blue-black fog for the Nexus atmosphere
        return new Vec3(0.02, 0.05, 0.08);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        // No sunrise/sunset in the Nexus
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        // Cancel default sky rendering - Nexus has a void sky
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        // Cancel default cloud rendering
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        // Cancel default weather rendering
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        // Cancel rain ticking
        return true;
    }
}

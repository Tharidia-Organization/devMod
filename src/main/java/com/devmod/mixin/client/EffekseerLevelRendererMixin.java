package com.devmod.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

import com.devmod.client.vfx.effekseer.render.EffekRenderer;

@Mixin(LevelRenderer.class)
public class EffekseerLevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void devmod$renderEffekseer(DeltaTracker deltaTracker, boolean drawBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f viewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        EffekRenderer.renderWorldEffeks(partial, viewMatrix, projectionMatrix, camera);

        // Telepad player occlusion is handled by PlayerRendererMixin using portal geometry checks.
    }
}

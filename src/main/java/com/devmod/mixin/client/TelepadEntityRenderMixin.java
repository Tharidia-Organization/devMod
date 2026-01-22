package com.devmod.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

import com.devmod.DevMod;
import com.devmod.clone.client.renderer.TelepadDepthRenderer;

/**
 * Mixin to inject telepad depth rendering during renderLevel.
 * Inject at RETURN to test if the mixin is working at all.
 */
@Mixin(LevelRenderer.class)
public class TelepadEntityRenderMixin {

    @Unique
    private static boolean devmod$loggedOnce = false;

    @Unique
    private static int devmod$frameCount = 0;

    /**
     * Inject at RETURN of renderLevel to test mixin is working.
     * This will render AFTER everything else (for testing only).
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void devmod$renderDepthTest(DeltaTracker deltaTracker, boolean renderBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {

        devmod$frameCount++;

        // Log every 60 frames (roughly every second)
        if (devmod$frameCount % 60 == 1) {
            DevMod.LOGGER.info("[TelepadEntityRenderMixin] MIXIN IS WORKING! Frame {}, active telepads: {}",
                devmod$frameCount, TelepadDepthRenderer.getActiveTelepadPositions().size());
        }

        if (!TelepadDepthRenderer.getActiveTelepadPositions().isEmpty()) {
            if (!devmod$loggedOnce) {
                DevMod.LOGGER.info("[TelepadEntityRenderMixin] renderLevel RETURN - rendering depth for {} telepads",
                    TelepadDepthRenderer.getActiveTelepadPositions().size());
                devmod$loggedOnce = true;
            }

            // Get camera position
            var cameraPos = camera.getPosition();

            // Create a fresh PoseStack for our rendering
            var poseStack = new com.mojang.blaze3d.vertex.PoseStack();

            // Render depth layers for all active telepads
            TelepadDepthRenderer.renderDepthLayersBeforeEntities(poseStack, cameraPos.x, cameraPos.y, cameraPos.z);
        }
    }
}

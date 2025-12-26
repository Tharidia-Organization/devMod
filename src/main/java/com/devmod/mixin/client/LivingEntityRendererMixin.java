package com.devmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.client.collision.transform.ModelPartTransformCapture;
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {

    /**
     * Called before the entity model is rendered.
     * Starts capturing transforms for this entity.
     */
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void devmod$beginTransformCapture(T entity, float entityYaw, float partialTick,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int packedLight, CallbackInfo ci) {
        // Only capture if OBB system needs it
        if (shouldCaptureTransforms()) {
            ModelPartTransformCapture.beginCapture(entity);
        }
    }

    /**
     * Called after the entity model is rendered.
     * Ends transform capture.
     */
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void devmod$endTransformCapture(T entity, float entityYaw, float partialTick,
                                             PoseStack poseStack, MultiBufferSource buffer,
                                             int packedLight, CallbackInfo ci) {
        if (ModelPartTransformCapture.isCapturing()) {
            ModelPartTransformCapture.endCapture();
        }
    }

    /**
     * Checks if we should capture transforms.
     * Only capture when OBB system is enabled to avoid unnecessary overhead.
     */
    private static boolean shouldCaptureTransforms() {
        try {
            return com.devmod.config.Config.OBB_HITBOX_ENABLED.get();
        } catch (Exception e) {
            return false;
        }
    }
}

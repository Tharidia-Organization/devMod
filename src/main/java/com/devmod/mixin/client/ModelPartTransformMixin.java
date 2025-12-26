package com.devmod.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;

import com.devmod.client.collision.transform.ModelPartTransformCapture;
@Mixin(ModelPart.class)
public class ModelPartTransformMixin {

    @Shadow
    public float x;
    @Shadow
    public float y;
    @Shadow
    public float z;
    @Shadow
    public float xRot;
    @Shadow
    public float yRot;
    @Shadow
    public float zRot;

    /**
     * Captures the transform after it's been applied to the PoseStack.
     * This gives us the exact world-space transform for each ModelPart.
     */
    @Inject(method = "translateAndRotate", at = @At("TAIL"))
    private void devmod$captureTransform(PoseStack poseStack, CallbackInfo ci) {
        // Only capture if the system is actively recording
        if (!ModelPartTransformCapture.isCapturing()) {
            return;
        }

        // Get the current transform matrix from the pose stack
        Matrix4f currentTransform = new Matrix4f(poseStack.last().pose());

        // Store the transform with local rotation data for this ModelPart
        // The ModelPart instance itself is "this" (cast from mixin)
        ModelPart self = (ModelPart) (Object) this;
        ModelPartTransformCapture.captureTransform(self, currentTransform, xRot, yRot, zRot);
    }
}

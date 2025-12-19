package com.frenkvs.devmod.mixin;

import com.frenkvs.devmod.collision.transform.ModelPartTransformCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture ModelPart transforms during rendering.
 * This intercepts the translateAndRotate method which is called for every
 * ModelPart during entity rendering, allowing us to extract the exact
 * transforms used for collision detection.
 *
 * Works with:
 * - Vanilla entities
 * - Modded entities using standard ModelPart system
 * - Any renderer that calls ModelPart.translateAndRotate()
 */
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

package com.devmod.mixin.client;

import java.util.Objects;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

import com.devmod.client.effects.ShakeManager;

@Mixin(Camera.class)
@SuppressWarnings("NullAway.Init")
public abstract class CameraShakeMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("DevMod/CameraShake");

    @Shadow
    @Nullable
    private Quaternionf rotation;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 getPosition();

    /**
     * Applies accumulated shake effects after camera setup is complete.
     */
    @Inject(method = "setup", at = @At("TAIL"))
    void devmod$applyScreenShake(BlockGetter level, Entity entity, boolean detached,
                                 boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {
        if (!ShakeManager.INSTANCE.isEnabled()) {
            return;
        }
        if (ShakeManager.INSTANCE.getActiveCount() == 0) {
            return;
        }

        Quaternionf rotation = Objects.requireNonNull(this.rotation, "rotation");

        // Get accumulated shake values
        Vector3f shakeRotation = ShakeManager.INSTANCE.getAccumulatedRotation(partialTicks);
        Vector3f shakeOffset = ShakeManager.INSTANCE.getAccumulatedOffset(partialTicks);

        // ALWAYS log when we have active shakes to debug
        LOGGER.info("[DevMod/CameraShake] Active shakes: {}, rotation={}, offset={}, partialTicks={}",
            ShakeManager.INSTANCE.getActiveCount(), shakeRotation, shakeOffset, partialTicks);

        // Apply rotation shake
        if (shakeRotation.lengthSquared() > 0.0001f) {
            // Convert shake rotation to camera-local space
            // We need to rotate the shake vector by the inverse of camera rotation
            Quaternionf inverseRotation = new Quaternionf(rotation).invert();
            Vector3f localShake = inverseRotation.transform(new Vector3f(shakeRotation));

            // Apply as euler angles (degrees)
            // Multiply by factor to make the effect more noticeable
            float shakeFactor = 1.0f;
            float newXRot = this.xRot + localShake.x * shakeFactor;
            float newYRot = this.yRot + localShake.y * shakeFactor;

            // Also apply roll (Z rotation) directly to quaternion
            if (Math.abs(localShake.z) > 0.001f) {
                Quaternionf rollQuat = new Quaternionf().rotateZ((float) Math.toRadians(localShake.z * shakeFactor));
                rotation.mul(rollQuat);
            }

            // Update pitch and yaw
            setRotation(newYRot, newXRot);
        }

        // Apply position offset shake
        if (shakeOffset.lengthSquared() > 0.0001f) {
            // Transform offset to camera-local space
            Quaternionf inverseRotation = new Quaternionf(rotation).invert();
            Vector3f localOffset = inverseRotation.transform(new Vector3f(shakeOffset));

            // Apply offset
            net.minecraft.world.phys.Vec3 currentPos = getPosition();
            setPosition(
                currentPos.x + localOffset.x,
                currentPos.y + localOffset.y,
                currentPos.z + localOffset.z
            );
        }
    }
}

package com.devmod.collision.integration;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.devmod.collision.bodypart.BodyPartHierarchy;
import com.devmod.collision.bodypart.BodyPartInstance;
import com.devmod.collision.obb.OBBRaycast;
import com.devmod.collision.obb.OrientedBoundingBox;
import com.devmod.collision.registry.BodyPartRegistry;
import com.devmod.collision.transform.AnimationSnapshot;
import com.devmod.collision.transform.TransformProviderRegistry;
import com.devmod.config.Config;
import com.devmod.shared.BodyPart;
import com.devmod.shared.HitResult;

public final class OBBHitHelper {

    private OBBHitHelper() {} // Utility class

    // ==================== Configuration ====================

    /**
     * Checks if OBB system is enabled globally.
     *
     * @return true if OBB hitboxes should be used
     */
    public static boolean useOBBSystem() {
        try {
            return Config.OBB_HITBOX_ENABLED.get();
        } catch (Exception e) {
            // Config not loaded yet, default to disabled
            return false;
        }
    }

    // ==================== Main API ====================

    /**
     * Performs OBB-aware body part raycast.
     * Falls back to pitch-based detection if OBB fails.
     *
     * <p>Note: callers should check {@link #useOBBSystem()} first and fall
     * back to their own AABB implementation when OBB is disabled, to avoid
     * a circular dependency between collision and combat.
     *
     * @param attacker The attacking entity
     * @param target   The target entity
     * @return HitResult with body part and hit point
     */
    @Nonnull
    public static HitResult rayTraceBodyPart(@Nonnull LivingEntity attacker,
                                                       @Nonnull LivingEntity target) {
        try {
            return rayTraceBodyPartOBB(attacker, target);
        } catch (Exception e) {
            return pitchBasedFallback(attacker, target);
        }
    }

    /**
     * Performs OBB raycast without fallback.
     * Use this when you specifically need OBB behavior.
     *
     * @param attacker The attacking entity
     * @param target   The target entity
     * @return HitResult with body part and hit point
     * @throws IllegalStateException if OBB calculation fails
     */
    @Nonnull
    public static HitResult rayTraceBodyPartOBB(@Nonnull LivingEntity attacker,
                                                         @Nonnull LivingEntity target) {
        // 1. Get hierarchy for target
        BodyPartRegistry.INSTANCE.initialize(); // Ensure initialized
        BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(target);

        // 2. Get current animation snapshot (uses appropriate provider for client/server)
        long currentTick = target.level().getGameTime();
        AnimationSnapshot snapshot = TransformProviderRegistry.getProvider().extractTransforms(
            target, 1.0f, currentTick);

        // 3. Compute world-space OBBs for all parts
        BodyPartInstance[] parts = hierarchy.computeWorldTransforms(snapshot);

        try {
            // 4. Build ray from attacker
            Vec3 rayOrigin = Objects.requireNonNull(attacker.getEyePosition(), "rayOrigin");
            Vec3 rayDir = Objects.requireNonNull(Objects.requireNonNull(attacker.getViewVector(1.0f), "rayDir").normalize());
            float reach = getDynamicReach(attacker);

            // 5. Extract OBBs from instances
            OrientedBoundingBox[] obbs = new OrientedBoundingBox[parts.length];
            for (int i = 0; i < parts.length; i++) {
                obbs[i] = parts[i].getWorldOBB();
            }

            // 6. Find closest hit
            OBBRaycast.IndexedHitResult hitResult = OBBRaycast.findClosestHitWithResult(
                rayOrigin, rayDir, reach, obbs);

            // 7. Convert to HitResult
            if (hitResult.hit()) {
                BodyPartInstance hitPart = parts[hitResult.index()];
                Vec3 hitPoint = Objects.requireNonNull(
                    Objects.requireNonNull(hitResult.result(), "hit result").hitPoint(),
                    "hitPoint");
                return new HitResult(hitPart.getBodyPartType(), hitPoint);
            }

            // 8. No OBB hit - use pitch-based fallback
            return pitchBasedFallback(attacker, target);

        } finally {
            // Always release pooled instances
            BodyPartInstance.releaseMultiple(parts);
        }
    }

    /**
     * Performs simplified OBB raycast using entity position only (no bone transforms).
     * Faster but less accurate than full OBB raycast.
     *
     * @param attacker The attacking entity
     * @param target   The target entity
     * @return HitResult with body part and hit point
     */
    @Nonnull
    public static HitResult rayTraceBodyPartSimple(@Nonnull LivingEntity attacker,
                                                             @Nonnull LivingEntity target) {
        BodyPartRegistry.INSTANCE.initialize();
        BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(target);

        // Use simple transforms (no bone animation)
        long currentTick = target.level().getGameTime();
        Vec3 targetPos = Objects.requireNonNull(target.position(), "target position");
        BodyPartInstance[] parts = hierarchy.computeSimpleTransforms(
            targetPos, target.yBodyRot, currentTick);

        try {
            Vec3 rayOrigin = Objects.requireNonNull(attacker.getEyePosition(), "rayOrigin");
            Vec3 rayDir = Objects.requireNonNull(Objects.requireNonNull(attacker.getViewVector(1.0f), "rayDir").normalize());
            float reach = getDynamicReach(attacker);

            OrientedBoundingBox[] obbs = new OrientedBoundingBox[parts.length];
            for (int i = 0; i < parts.length; i++) {
                obbs[i] = parts[i].getWorldOBB();
            }

            OBBRaycast.IndexedHitResult hitResult = OBBRaycast.findClosestHitWithResult(
                rayOrigin, rayDir, reach, obbs);

            if (hitResult.hit()) {
                BodyPartInstance hitPart = parts[hitResult.index()];
                Vec3 hitPoint = Objects.requireNonNull(
                    Objects.requireNonNull(hitResult.result(), "hit result").hitPoint(),
                    "hitPoint");
                return new HitResult(hitPart.getBodyPartType(), hitPoint);
            }

            return pitchBasedFallback(attacker, target);

        } finally {
            BodyPartInstance.releaseMultiple(parts);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Gets dynamic reach from attacker's attributes.
     * Compatible with Better Combat, Epic Knights, etc.
     */
    private static float getDynamicReach(@Nonnull LivingEntity attacker) {
        try {
            var reachAttr = attacker.getAttribute(Objects.requireNonNull(
                net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
                "reach attribute"));
            if (reachAttr != null) {
                double value = reachAttr.getValue();
                if (value > 0.1) {
                    return (float) (value + 0.5); // Add margin
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return 4.0f; // Default reach with margin
    }

    /**
     * Pitch-based fallback when raycast doesn't hit any OBB.
     */
    @Nonnull
    private static HitResult pitchBasedFallback(@Nonnull LivingEntity attacker,
                                                         @Nonnull LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        double height = target.getBbHeight();
        double pitch = attacker.getXRot();

        if (pitch < -15) {
            return new HitResult(BodyPart.HEAD,
                center.add(0, height * 0.35, 0));
        }
        if (pitch > 25) {
            return new HitResult(BodyPart.LEGS,
                center.add(0, -height * 0.3, 0));
        }

        return new HitResult(BodyPart.BODY, center);
    }

    // ==================== Utility Methods ====================

    /**
     * Gets the body part instances for an entity (for debug rendering).
     * Caller is responsible for releasing instances when done.
     *
     * @param entity The entity
     * @return Array of body part instances (caller must release)
     */
    @Nonnull
    public static BodyPartInstance[] getBodyPartInstances(@Nonnull LivingEntity entity) {
        BodyPartRegistry.INSTANCE.initialize();
        BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(entity);

        long currentTick = entity.level().getGameTime();
        AnimationSnapshot snapshot = TransformProviderRegistry.getProvider().extractTransforms(
            entity, 1.0f, currentTick);

        return hierarchy.computeWorldTransforms(snapshot);
    }

    /**
     * Gets simple body part instances (no bone transforms).
     * Caller is responsible for releasing instances when done.
     *
     * @param entity The entity
     * @return Array of body part instances (caller must release)
     */
    @Nonnull
    public static BodyPartInstance[] getSimpleBodyPartInstances(@Nonnull LivingEntity entity) {
        BodyPartRegistry.INSTANCE.initialize();
        BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(entity);

        long currentTick = entity.level().getGameTime();
        return hierarchy.computeSimpleTransforms(Objects.requireNonNull(entity.position()), entity.yBodyRot, currentTick);
    }

    /**
     * Checks if an entity has custom OBB body parts registered.
     *
     * @param entity The entity
     * @return true if custom parts are available
     */
    public static boolean hasCustomBodyParts(@Nonnull LivingEntity entity) {
        return BodyPartRegistry.INSTANCE.hasCustomParts(Objects.requireNonNull(entity.getType()));
    }
}

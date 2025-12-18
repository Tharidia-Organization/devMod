package com.frenkvs.devmod.collision.integration;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.HitHelper;
import com.frenkvs.devmod.collision.bodypart.BodyPartHierarchy;
import com.frenkvs.devmod.collision.bodypart.BodyPartInstance;
import com.frenkvs.devmod.collision.obb.OBBRaycast;
import com.frenkvs.devmod.collision.obb.OrientedBoundingBox;
import com.frenkvs.devmod.collision.registry.BodyPartRegistry;
import com.frenkvs.devmod.collision.transform.AnimationSnapshot;
import com.frenkvs.devmod.collision.transform.ModelPartTransformExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Drop-in replacement/enhancement for HitHelper using OBB system.
 *
 * Maintains API compatibility with existing code while adding
 * rotation-aware body part detection.
 *
 * Usage:
 * <pre>
 * // Instead of:
 * HitHelper.HitResult result = HitHelper.rayTraceBodyPartWithHitPoint(attacker, target);
 *
 * // Use:
 * HitHelper.HitResult result = OBBHitHelper.rayTraceBodyPart(attacker, target);
 * </pre>
 *
 * The system automatically falls back to AABB-based detection when:
 * - OBB system is disabled in config
 * - Entity has no registered body parts
 * - An error occurs during OBB calculation
 */

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
     * Falls back to AABB method if OBB is disabled or unavailable.
     *
     * @param attacker The attacking entity
     * @param target   The target entity
     * @return HitResult with body part and hit point
     */
    @Nonnull
    public static HitHelper.HitResult rayTraceBodyPart(@Nonnull LivingEntity attacker,
                                                       @Nonnull LivingEntity target) {
        // Check if OBB system is enabled
        if (!useOBBSystem()) {
            return Objects.requireNonNull(
                HitHelper.rayTraceBodyPartWithHitPoint(attacker, target),
                "fallback hit result");
        }

        try {
            return rayTraceBodyPartOBB(attacker, target);
        } catch (Exception e) {
            // Log error and fallback to AABB
            // DevMod.LOGGER.warn("OBB raycast failed, falling back to AABB", e);
            return Objects.requireNonNull(
                HitHelper.rayTraceBodyPartWithHitPoint(attacker, target),
                "fallback hit result");
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
    public static HitHelper.HitResult rayTraceBodyPartOBB(@Nonnull LivingEntity attacker,
                                                         @Nonnull LivingEntity target) {
        // 1. Get hierarchy for target
        BodyPartRegistry.INSTANCE.initialize(); // Ensure initialized
        BodyPartHierarchy hierarchy = BodyPartRegistry.INSTANCE.getHierarchy(target);

        // 2. Get current animation snapshot
        long currentTick = target.level().getGameTime();
        AnimationSnapshot snapshot = ModelPartTransformExtractor.extractTransforms(
            target, 1.0f, currentTick);

        // 3. Compute world-space OBBs for all parts
        BodyPartInstance[] parts = hierarchy.computeWorldTransforms(snapshot);

        try {
            // 4. Build ray from attacker
            Vec3 rayOrigin = Objects.requireNonNull(attacker.getEyePosition(), "rayOrigin");
            Vec3 rayDir = Objects.requireNonNull(attacker.getViewVector(1.0f), "rayDir").normalize();
            float reach = getDynamicReach(attacker);

            // 5. Extract OBBs from instances
            OrientedBoundingBox[] obbs = new OrientedBoundingBox[parts.length];
            for (int i = 0; i < parts.length; i++) {
                obbs[i] = parts[i].getWorldOBB();
            }

            // 6. Find closest hit
            OBBRaycast.IndexedHitResult hitResult = OBBRaycast.findClosestHitWithResult(
                rayOrigin, rayDir, reach, obbs);

            // 7. Convert to HitHelper.HitResult
            if (hitResult.hit()) {
                BodyPartInstance hitPart = parts[hitResult.index()];
                Vec3 hitPoint = Objects.requireNonNull(
                    Objects.requireNonNull(hitResult.result(), "hit result").hitPoint(),
                    "hitPoint");
                return new HitHelper.HitResult(hitPart.getBodyPartType(), hitPoint);
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
    public static HitHelper.HitResult rayTraceBodyPartSimple(@Nonnull LivingEntity attacker,
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
            Vec3 rayDir = Objects.requireNonNull(attacker.getViewVector(1.0f), "rayDir").normalize();
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
                return new HitHelper.HitResult(hitPart.getBodyPartType(), hitPoint);
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
    private static HitHelper.HitResult pitchBasedFallback(@Nonnull LivingEntity attacker,
                                                         @Nonnull LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        double height = target.getBbHeight();
        double pitch = attacker.getXRot();

        if (pitch < -15) {
            return new HitHelper.HitResult(HitHelper.BodyPart.HEAD,
                center.add(0, height * 0.35, 0));
        }
        if (pitch > 25) {
            return new HitHelper.HitResult(HitHelper.BodyPart.LEGS,
                center.add(0, -height * 0.3, 0));
        }

        return new HitHelper.HitResult(HitHelper.BodyPart.BODY, center);
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
        AnimationSnapshot snapshot = ModelPartTransformExtractor.extractTransforms(
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
        return hierarchy.computeSimpleTransforms(entity.position(), entity.yBodyRot, currentTick);
    }

    /**
     * Checks if an entity has custom OBB body parts registered.
     *
     * @param entity The entity
     * @return true if custom parts are available
     */
    public static boolean hasCustomBodyParts(@Nonnull LivingEntity entity) {
        return BodyPartRegistry.INSTANCE.hasCustomParts(entity.getType());
    }
}

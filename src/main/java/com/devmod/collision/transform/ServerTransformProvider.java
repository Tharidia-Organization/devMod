package com.devmod.collision.transform;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;
import org.slf4j.Logger;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.mojang.logging.LogUtils;

/**
 * Server-side transform provider that computes simplified transforms
 * based on entity pose data only (no bone animations).
 *
 * This implementation:
 * - Works without client-side rendering data
 * - Provides reasonable approximations using yBodyRot, yHeadRot, xRot
 * - Is suitable for dedicated server collision detection
 *
 * Accuracy is lower than client-side captures but sufficient for
 * server-side hit detection.
 */
public final class ServerTransformProvider implements TransformProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ServerTransformProvider INSTANCE = new ServerTransformProvider();

    private ServerTransformProvider() {} // Singleton

    // Transform cache with TTL
    private final Map<Integer, AnimationSnapshot> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 256;
    private volatile long lastCleanupTick = 0;
    private static final long CLEANUP_INTERVAL_TICKS = 100;

    @Override
    @Nonnull
    public AnimationSnapshot extractTransforms(@Nonnull LivingEntity entity,
                                                float partialTick,
                                                long currentTick) {
        int entityId = Objects.requireNonNull(entity).getId();

        // Periodic cleanup
        if (currentTick - lastCleanupTick > CLEANUP_INTERVAL_TICKS) {
            lastCleanupTick = currentTick;
            cleanupCache(currentTick);
        }

        // Enforce max cache size
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictOldestEntries(currentTick);
        }

        // Check cache first
        AnimationSnapshot cached = cache.get(entityId);
        if (cached != null && !cached.isStale(currentTick)) {
            return cached;
        }

        // Compute fresh transforms
        AnimationSnapshot snapshot = computeTransforms(entity, partialTick, currentTick);
        cache.put(entityId, snapshot);
        return snapshot;
    }

    /**
     * Computes simplified transforms from entity pose data.
     */
    @Nonnull
    private AnimationSnapshot computeTransforms(LivingEntity entity,
                                                float partialTick,
                                                long currentTick) {
        AnimationSnapshot.Builder builder = AnimationSnapshot.builder(
            entity.getId(), currentTick, partialTick);

        // Set entity position and rotation
        Vec3 position = Objects.requireNonNull(entity.getPosition(partialTick));
        float yBodyRot = lerpAngle(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float yHeadRot = lerpAngle(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float xHeadRot = lerp(partialTick, entity.xRotO, entity.getXRot());

        builder.position(position)
               .bodyRotation(yBodyRot)
               .headRotation(yHeadRot, xHeadRot);

        // Compute simplified bone transforms
        Map<String, Matrix4f> boneTransforms = computeSimpleBoneTransforms(
            entity, partialTick, yBodyRot, yHeadRot, xHeadRot);
        builder.addPartTransforms(boneTransforms);

        return builder.build();
    }

    /**
     * Computes simplified bone transforms using entity pose only.
     */
    @Nonnull
    private Map<String, Matrix4f> computeSimpleBoneTransforms(LivingEntity entity,
                                                               float partialTick,
                                                               float yBodyRot,
                                                               float yHeadRot,
                                                               float xHeadRot) {
        Map<String, Matrix4f> transforms = new HashMap<>();

        // Head rotation relative to body
        float headYaw = yHeadRot - yBodyRot;
        float headPitch = xHeadRot;

        Matrix4f headTransform = new Matrix4f();
        headTransform.rotateY((float) Math.toRadians(-headYaw));
        headTransform.rotateX((float) Math.toRadians(-headPitch));
        transforms.put("head", headTransform);

        // Body is identity (base reference)
        transforms.put("body", new Matrix4f());

        // Arms - estimate based on attack animation
        float armSwing = entity.attackAnim;
        if (armSwing > 0) {
            float swingProgress = armSwing * (float) Math.PI * 2;
            Matrix4f rightArmTransform = new Matrix4f();
            rightArmTransform.rotateX(-swingProgress * 0.5f);
            transforms.put("rightArm", rightArmTransform);
        } else {
            transforms.put("rightArm", new Matrix4f());
        }
        transforms.put("leftArm", new Matrix4f());

        // Legs - estimate based on walking animation
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);

        if (limbSwingAmount > 0.01f) {
            float legSwing = (float) Math.cos(limbSwing * 0.6662f) * limbSwingAmount;

            Matrix4f rightLegTransform = new Matrix4f();
            rightLegTransform.rotateX(legSwing * 0.5f);
            transforms.put("rightLeg", rightLegTransform);

            Matrix4f leftLegTransform = new Matrix4f();
            leftLegTransform.rotateX(-legSwing * 0.5f);
            transforms.put("leftLeg", leftLegTransform);
        } else {
            transforms.put("rightLeg", new Matrix4f());
            transforms.put("leftLeg", new Matrix4f());
        }

        return transforms;
    }

    @Override
    public void clearCache(int entityId) {
        cache.remove(entityId);
    }

    @Override
    public void clearAllCaches() {
        cache.clear();
    }

    @Override
    public int getCacheSize() {
        return cache.size();
    }

    private void cleanupCache(long currentTick) {
        int sizeBefore = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isStale(currentTick));
        int sizeAfter = cache.size();

        // Log cache cleanup metrics periodically (every 1000 ticks = ~50 seconds)
        if (currentTick % 1000 == 0 && sizeAfter > 0) {
            LOGGER.debug("[ServerTransformProvider] Cache cleanup: {} -> {} entries (evicted {})",
                sizeBefore, sizeAfter, sizeBefore - sizeAfter);
        }
    }

    private void evictOldestEntries(long currentTick) {
        int toRemove = cache.size() - MAX_CACHE_SIZE + 16;
        cache.entrySet().stream()
            .filter(e -> e.getValue().isStale(currentTick))
            .limit(Math.max(1, toRemove))
            .map(Map.Entry::getKey)
            .toList()
            .forEach(cache::remove);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float lerpAngle(float delta, float start, float end) {
        float diff = end - start;
        while (diff < -180) diff += 360;
        while (diff >= 180) diff -= 360;
        return start + delta * diff;
    }
}

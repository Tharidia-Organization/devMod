package com.frenkvs.devmod.collision.transform;

import com.frenkvs.devmod.collision.compat.GeckoLibCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts transformation data from Minecraft's ModelPart system.
 *
 * This is the key integration point with Minecraft's rendering.
 * It reads the current rotation/position of model parts and converts
 * them to Matrix4f transforms for OBB calculation.
 *
 * Approaches (in order of preference):
 * 1. Direct access to ModelPart.x/y/z/xRot/yRot/zRot fields
 * 2. Calculate from entity pose data (yBodyRot, animation ticks)
 *
 * Must be called on render thread for accurate transforms.
 */
public final class ModelPartTransformExtractor {

    private ModelPartTransformExtractor() {} // Utility class

    // ==================== Transform Cache ====================

    /**
     * Cache of recent transforms per entity.
     * Key: entity ID
     * Value: AnimationSnapshot with transforms
     */
    private static final Map<Integer, AnimationSnapshot> TRANSFORM_CACHE = new ConcurrentHashMap<>();

    // ==================== Public API ====================

    /**
     * Gets current transforms for all model parts of an entity.
     * Uses caching to avoid recalculation within TTL.
     *
     * @param entity      The entity
     * @param partialTick For interpolation (0-1)
     * @param currentTick Current game tick
     * @return Snapshot of all bone transforms
     */
    @Nonnull
    public static AnimationSnapshot extractTransforms(@Nonnull LivingEntity entity,
                                                      float partialTick,
                                                      long currentTick) {
        int entityId = entity.getId();

        // Check cache first
        AnimationSnapshot cached = TRANSFORM_CACHE.get(entityId);
        if (cached != null && !cached.isStale(currentTick)) {
            return cached;
        }

        // Extract fresh transforms
        AnimationSnapshot snapshot = extractTransformsUncached(entity, partialTick, currentTick);

        // Update cache
        TRANSFORM_CACHE.put(entityId, snapshot);

        return snapshot;
    }

    /**
     * Extracts transforms without caching.
     */
    @Nonnull
    public static AnimationSnapshot extractTransformsUncached(@Nonnull LivingEntity entity,
                                                              float partialTick,
                                                              long currentTick) {
        AnimationSnapshot.Builder builder = AnimationSnapshot.builder(
            entity.getId(), currentTick, partialTick);

        // Set entity position and rotation
        Vec3 position = entity.getPosition(partialTick);
        float yBodyRot = lerpAngle(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float yHeadRot = lerpAngle(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float xHeadRot = lerp(partialTick, entity.xRotO, entity.getXRot());

        builder.position(position)
               .bodyRotation(yBodyRot)
               .headRotation(yHeadRot, xHeadRot);

        // Try to extract bone transforms from model
        // Note: This requires access to the entity's model, which is client-side only
        Map<String, Matrix4f> boneTransforms = extractBoneTransforms(entity, partialTick);
        builder.addPartTransforms(boneTransforms);

        return builder.build();
    }

    /**
     * Extracts bone transforms from entity model.
     * This is the core method that reads ModelPart rotations.
     *
     * Priority:
     * 1. Use Mixin-captured transforms (most accurate, follows animations exactly)
     * 2. Try GeckoLib transforms for modded entities
     * 3. Fall back to calculated transforms (based on animation state)
     */
    @Nonnull
    private static Map<String, Matrix4f> extractBoneTransforms(@Nonnull LivingEntity entity,
                                                               float partialTick) {
        Map<String, Matrix4f> transforms = new HashMap<>();

        // Try to use Mixin-captured transforms first (most accurate)
        if (ModelPartTransformCapture.hasTransforms(entity)) {
            Map<String, ModelPartTransformCapture.CapturedTransform> captured =
                ModelPartTransformCapture.getTransforms(entity);

            for (var entry : captured.entrySet()) {
                transforms.put(entry.getKey(), new Matrix4f(entry.getValue().worldTransform()));
            }

            // If we got transforms from Mixin, use those
            if (!transforms.isEmpty()) {
                return transforms;
            }
        }

        // Try GeckoLib for modded entities that use it
        if (GeckoLibCompat.isGeckoLibEntity(entity)) {
            Map<String, Matrix4f> geckoTransforms = GeckoLibCompat.extractGeckoLibTransforms(entity);
            if (!geckoTransforms.isEmpty()) {
                // Map GeckoLib bone names to our standard names
                for (var entry : geckoTransforms.entrySet()) {
                    String standardName = GeckoLibCompat.mapBoneName(entry.getKey());
                    transforms.put(standardName, entry.getValue());
                }
                return transforms;
            }
        }

        // Fall back to calculated transforms for humanoid entities
        if (isHumanoid(entity)) {
            extractHumanoidTransforms(entity, partialTick, transforms);
        }

        return transforms;
    }

    /**
     * Extracts transforms for humanoid entities using animation data.
     */
    private static void extractHumanoidTransforms(@Nonnull LivingEntity entity,
                                                  float partialTick,
                                                  @Nonnull Map<String, Matrix4f> transforms) {
        // Head rotation
        float headYaw = entity.yHeadRot - entity.yBodyRot;
        float headPitch = entity.getXRot();

        Matrix4f headTransform = new Matrix4f();
        headTransform.rotateY((float) Math.toRadians(-headYaw));
        headTransform.rotateX((float) Math.toRadians(-headPitch));
        transforms.put("head", headTransform);

        // Body is identity (base reference)
        transforms.put("body", new Matrix4f());

        // Arms - calculate based on attack/use animation
        float armSwing = entity.attackAnim;
        if (armSwing > 0) {
            // Right arm swing during attack
            float swingProgress = armSwing * (float) Math.PI * 2;
            Matrix4f rightArmTransform = new Matrix4f();
            rightArmTransform.rotateX(-swingProgress * 0.5f);
            transforms.put("rightArm", rightArmTransform);
        } else {
            transforms.put("rightArm", new Matrix4f());
        }
        transforms.put("leftArm", new Matrix4f());

        // Legs - calculate based on walking animation
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
    }

    /**
     * Checks if an entity uses humanoid model.
     */
    private static boolean isHumanoid(@Nonnull LivingEntity entity) {
        // Check common humanoid entity types
        String className = entity.getClass().getSimpleName().toLowerCase();
        return className.contains("player") ||
               className.contains("zombie") ||
               className.contains("skeleton") ||
               className.contains("piglin") ||
               className.contains("villager") ||
               className.contains("illager") ||
               className.contains("witch");
    }

    // ==================== ModelPart Direct Access ====================

    /**
     * Creates a transform matrix from ModelPart rotation values.
     *
     * @param xRot X rotation in radians
     * @param yRot Y rotation in radians
     * @param zRot Z rotation in radians
     * @param x    X offset
     * @param y    Y offset
     * @param z    Z offset
     * @return Transform matrix
     */
    @Nonnull
    public static Matrix4f createTransformFromModelPart(float xRot, float yRot, float zRot,
                                                        float x, float y, float z) {
        Matrix4f transform = new Matrix4f();

        // Apply translation
        transform.translate(x / 16.0f, y / 16.0f, z / 16.0f);

        // Apply rotations in ZYX order (Minecraft's convention)
        if (zRot != 0) transform.rotateZ(zRot);
        if (yRot != 0) transform.rotateY(yRot);
        if (xRot != 0) transform.rotateX(xRot);

        return transform;
    }

    /**
     * Extracts transform from a ModelPart directly.
     * Note: ModelPart coordinates are in 1/16 block units.
     *
     * @param part The model part
     * @return Transform matrix
     */
    @Nonnull
    public static Matrix4f extractFromModelPart(@Nonnull ModelPart part) {
        return createTransformFromModelPart(
            part.xRot, part.yRot, part.zRot,
            part.x, part.y, part.z
        );
    }

    /**
     * Extracts transform from a ModelPart with scale.
     */
    @Nonnull
    public static Matrix4f extractFromModelPartWithScale(@Nonnull ModelPart part,
                                                         float xScale, float yScale, float zScale) {
        Matrix4f transform = extractFromModelPart(part);
        transform.scale(xScale, yScale, zScale);
        return transform;
    }

    // ==================== HumanoidModel Helper ====================

    /**
     * Extracts all transforms from a HumanoidModel.
     * Call this from render thread when model is available.
     *
     * @param model The humanoid model
     * @return Map of bone name to transform
     */
    @Nonnull
    public static Map<String, Matrix4f> extractFromHumanoidModel(@Nonnull HumanoidModel<?> model) {
        Map<String, Matrix4f> transforms = new HashMap<>();

        transforms.put("head", extractFromModelPart(model.head));
        transforms.put("body", extractFromModelPart(model.body));
        transforms.put("rightArm", extractFromModelPart(model.rightArm));
        transforms.put("leftArm", extractFromModelPart(model.leftArm));
        transforms.put("rightLeg", extractFromModelPart(model.rightLeg));
        transforms.put("leftLeg", extractFromModelPart(model.leftLeg));

        return transforms;
    }

    // ==================== Cache Management ====================

    /**
     * Clears cached transforms for an entity.
     * Call when entity is removed or changes significantly.
     */
    public static void clearCache(int entityId) {
        TRANSFORM_CACHE.remove(entityId);
    }

    /**
     * Clears all cached transforms.
     * Call during world unload.
     */
    public static void clearAllCaches() {
        TRANSFORM_CACHE.clear();
    }

    /**
     * Removes stale entries from cache.
     * Call periodically to prevent memory leaks.
     */
    public static void cleanupCache(long currentTick) {
        TRANSFORM_CACHE.entrySet().removeIf(entry -> entry.getValue().isStale(currentTick));
    }

    /**
     * Gets current cache size for debugging.
     */
    public static int getCacheSize() {
        return TRANSFORM_CACHE.size();
    }

    // ==================== Utility Methods ====================

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float lerpAngle(float delta, float start, float end) {
        float diff = end - start;
        while (diff < -180) diff += 360;
        while (diff >= 180) diff -= 360;
        return start + delta * diff;
    }

    /**
     * Creates a rotation quaternion from Euler angles (in radians).
     */
    @Nonnull
    public static Quaternionf quaternionFromEuler(float xRot, float yRot, float zRot) {
        Quaternionf q = new Quaternionf();
        q.rotateZYX(zRot, yRot, xRot);
        return q;
    }
}

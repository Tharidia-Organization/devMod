package com.frenkvs.devmod.collision.transform;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of an entity's current pose/animation state.
 * Captured once per tick and reused for all hit tests.
 *
 * Contains:
 * - Entity position and rotation
 * - Per-bone transforms from the model
 * - Validity tracking
 *
 * Usage:
 * <pre>
 * AnimationSnapshot snapshot = AnimationSnapshot.capture(entity, partialTick);
 * Matrix4f headTransform = snapshot.getPartTransform("head");
 * </pre>
 */

public record AnimationSnapshot(
    int entityId,
    long tickCaptured,
    float partialTick,
    Vec3 entityPosition,
    float yBodyRot,
    float yHeadRot,
    float xHeadRot,
    Map<String, Matrix4f> partTransforms,
    boolean isValid
) {
    /**
     * Invalid/empty snapshot singleton.
     */
    public static final AnimationSnapshot INVALID = new AnimationSnapshot(
        0, 0, 0, Objects.requireNonNull(Vec3.ZERO), 0, 0, 0, Collections.emptyMap(), false
    );

    /**
     * Maximum age in ticks before snapshot is considered stale.
     */
    public static final int MAX_AGE_TICKS = 2;

    /**
     * Gets transform for a bone, with fallback to identity.
     *
     * @param boneName The bone/model part name (e.g., "head", "leftArm")
     * @return The transform matrix, or identity if not found
     */
    @Nullable
    public Matrix4f getPartTransform(String boneName) {
        return partTransforms.get(Objects.requireNonNull(boneName));
    }

    /**
     * Gets transform for a bone, with fallback to provided default.
     */
    @Nonnull
    public Matrix4f getPartTransformOrDefault(String boneName, Matrix4f defaultTransform) {
        Matrix4f transform = partTransforms.get(Objects.requireNonNull(boneName));
        return transform != null ? transform : Objects.requireNonNull(defaultTransform);
    }

    /**
     * Gets transform for a bone, with fallback to identity matrix.
     */
    @Nonnull
    public Matrix4f getPartTransformOrIdentity(String boneName) {
        Matrix4f transform = partTransforms.get(Objects.requireNonNull(boneName));
        return transform != null ? transform : new Matrix4f();
    }

    /**
     * Checks if a bone transform is available.
     */
    public boolean hasPartTransform(String boneName) {
        return partTransforms.containsKey(Objects.requireNonNull(boneName));
    }

    /**
     * Checks if snapshot is still valid (within TTL).
     *
     * @param currentTick Current game tick
     * @return true if snapshot is stale and should be refreshed
     */
    public boolean isStale(long currentTick) {
        return !isValid || (currentTick - tickCaptured) > MAX_AGE_TICKS;
    }

    /**
     * Checks if this snapshot has any bone transforms.
     */
    public boolean hasTransforms() {
        return !partTransforms.isEmpty();
    }

    /**
     * Gets the number of bone transforms in this snapshot.
     */
    public int getTransformCount() {
        return partTransforms.size();
    }

    // ==================== Builder ====================

    /**
     * Creates a new builder for constructing AnimationSnapshot.
     */
    @Nonnull
    public static Builder builder(int entityId, long tick, float partialTick) {
        return new Builder(entityId, tick, partialTick);
    }

    /**
     * Creates a simple snapshot with only entity position (no bone transforms).
     */
    @Nonnull
    public static AnimationSnapshot simple(int entityId, long tick, Vec3 position,
                                           float yBodyRot, float yHeadRot, float xHeadRot) {
        return new AnimationSnapshot(
            entityId, tick, 1.0f, Objects.requireNonNull(position), yBodyRot, yHeadRot, xHeadRot,
            Collections.emptyMap(), true
        );
    }

    /**
     * Builder for creating AnimationSnapshot instances.
     */
    public static final class Builder {
        private final int entityId;
        private final long tickCaptured;
        private final float partialTick;
        private Vec3 entityPosition = Vec3.ZERO;
        private float yBodyRot = 0;
        private float yHeadRot = 0;
        private float xHeadRot = 0;
        private final Map<String, Matrix4f> partTransforms = new HashMap<>();
        private boolean isValid = true;

        private Builder(int entityId, long tick, float partialTick) {
            this.entityId = entityId;
            this.tickCaptured = tick;
            this.partialTick = partialTick;
        }

        @Nonnull
        public Builder position(Vec3 position) {
            this.entityPosition = Objects.requireNonNull(position);
            return this;
        }

        @Nonnull
        public Builder position(double x, double y, double z) {
            this.entityPosition = new Vec3(x, y, z);
            return this;
        }

        @Nonnull
        public Builder bodyRotation(float yRot) {
            this.yBodyRot = yRot;
            return this;
        }

        @Nonnull
        public Builder headRotation(float yRot, float xRot) {
            this.yHeadRot = yRot;
            this.xHeadRot = xRot;
            return this;
        }

        @Nonnull
        public Builder addPartTransform(String boneName, Matrix4f transform) {
            // Store a copy to ensure immutability
            this.partTransforms.put(Objects.requireNonNull(boneName), new Matrix4f(Objects.requireNonNull(transform)));
            return this;
        }

        @Nonnull
        public Builder addPartTransforms(Map<String, Matrix4f> transforms) {
            for (var entry : Objects.requireNonNull(transforms).entrySet()) {
                addPartTransform(entry.getKey(), entry.getValue());
            }
            return this;
        }

        @Nonnull
        public Builder valid(boolean valid) {
            this.isValid = valid;
            return this;
        }

        @Nonnull
        public AnimationSnapshot build() {
            return new AnimationSnapshot(
                entityId, tickCaptured, partialTick, entityPosition,
                yBodyRot, yHeadRot, xHeadRot,
                Collections.unmodifiableMap(new HashMap<>(partTransforms)),
                isValid
            );
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a snapshot from a LivingEntity.
     * Note: This only captures basic position/rotation, not bone transforms.
     * Use ModelPartTransformExtractor for full bone transforms.
     */
    @Nonnull
    public static AnimationSnapshot fromEntity(net.minecraft.world.entity.LivingEntity entity,
                                               long tick, float partialTick) {
        Objects.requireNonNull(entity);
        return builder(entity.getId(), tick, partialTick)
            .position(Objects.requireNonNull(entity.getPosition(partialTick)))
            .bodyRotation(entity.yBodyRot)
            .headRotation(entity.yHeadRot, entity.getXRot())
            .build();
    }

    /**
     * Creates a snapshot with interpolated position.
     */
    @Nonnull
    public static AnimationSnapshot fromEntityInterpolated(net.minecraft.world.entity.LivingEntity entity,
                                                           long tick, float partialTick) {
        Objects.requireNonNull(entity);
        // Interpolate body rotation
        float yBodyRot = lerpAngle(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float yHeadRot = lerpAngle(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float xHeadRot = lerp(partialTick, entity.xRotO, entity.getXRot());

        return builder(entity.getId(), tick, partialTick)
            .position(Objects.requireNonNull(entity.getPosition(partialTick)))
            .bodyRotation(yBodyRot)
            .headRotation(yHeadRot, xHeadRot)
            .build();
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

    @Override
    public String toString() {
        return String.format("AnimationSnapshot[entity=%d, tick=%d, pos=%s, transforms=%d, valid=%b]",
            entityId, tickCaptured, entityPosition, partTransforms.size(), isValid);
    }
}

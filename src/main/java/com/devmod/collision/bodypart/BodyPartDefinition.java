package com.devmod.collision.bodypart;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

import com.devmod.collision.obb.OrientedBoundingBox;
import com.devmod.combat.HitHelper;

public record BodyPartDefinition(
    @Nonnull String id,                          // Unique identifier (e.g., "head", "left_arm")
    @Nonnull HitHelper.BodyPart bodyPartType,    // Maps to existing enum for compatibility
    @Nonnull Vec3 localOffset,                   // Offset from parent bone origin
    @Nonnull Vec3 halfExtents,                   // OBB half-extents (size/2)
    @Nullable String parentBoneId,               // ModelPart name to attach to (null = entity root)
    @Nullable String parentPartId,               // Parent body part for hierarchy (null = root)
    int renderColor,                             // Debug visualization color (ARGB)
    float damageMultiplier                       // Per-part damage multiplier (1.0 = normal)
) {
    public BodyPartDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bodyPartType, "bodyPartType");
        Objects.requireNonNull(localOffset, "localOffset");
        Objects.requireNonNull(halfExtents, "halfExtents");
        // parentBoneId/parentPartId may be null by design (root-level parts)
    }

    /**
     * Sentinel value for root-level parts (no parent).
     */
    @Nullable
    public static final String ROOT = null;

    /**
     * Default colors for body parts (ARGB format).
     */
    public static final int COLOR_HEAD = BodyPartColors.HEAD;
    public static final int COLOR_ARMS = BodyPartColors.ARMS;
    public static final int COLOR_BODY = BodyPartColors.BODY;
    public static final int COLOR_LEGS = BodyPartColors.LEGS;

    /**
     * Creates a base OBB from this definition (no rotation).
     * The OBB is centered at localOffset with the defined halfExtents.
     */
    @Nonnull
    public OrientedBoundingBox createBaseOBB() {
        return new OrientedBoundingBox(localOffset, halfExtents);
    }

    /**
     * Creates an OBB at the world origin (for later transformation).
     */
    @Nonnull
    public OrientedBoundingBox createOriginOBB() {
        return new OrientedBoundingBox(Objects.requireNonNull(Vec3.ZERO, "Vec3.ZERO"), halfExtents);
    }

    /**
     * Checks if this part is a root part (no parent).
     */
    public boolean isRoot() {
        return parentPartId == null;
    }

    /**
     * Checks if this part has a bone attachment.
     */
    public boolean hasBone() {
        return parentBoneId != null;
    }

    /**
     * Gets the full size (2 * halfExtents).
     */
    @Nonnull
    public Vec3 getFullSize() {
        Vec3 scaled = Objects.requireNonNull(halfExtents, "halfExtents").scale(2);
        return Objects.requireNonNull(scaled, "fullSize");
    }

    /**
     * Creates a copy with modified damage multiplier.
     */
    @Nonnull
    public BodyPartDefinition withDamageMultiplier(float newMultiplier) {
        return new BodyPartDefinition(id, bodyPartType, Objects.requireNonNull(localOffset, "localOffset"),
            Objects.requireNonNull(halfExtents, "halfExtents"),
            parentBoneId, parentPartId, renderColor, newMultiplier);
    }

    /**
     * Creates a copy with modified half-extents (for scaling).
     */
    @Nonnull
    public BodyPartDefinition withHalfExtents(@Nonnull Vec3 newHalfExtents) {
        return new BodyPartDefinition(id, bodyPartType, Objects.requireNonNull(localOffset, "localOffset"),
            Objects.requireNonNull(newHalfExtents, "newHalfExtents"),
            parentBoneId, parentPartId, renderColor, damageMultiplier);
    }

    /**
     * Creates a copy with modified offset.
     */
    @Nonnull
    public BodyPartDefinition withOffset(@Nonnull Vec3 newOffset) {
        return new BodyPartDefinition(id, bodyPartType, Objects.requireNonNull(newOffset, "newOffset"),
            Objects.requireNonNull(halfExtents, "halfExtents"),
            parentBoneId, parentPartId, renderColor, damageMultiplier);
    }

    // ==================== Builder ====================

    /**
     * Builder for creating BodyPartDefinition instances.
     */
    public static class Builder {
        private String id;
        private HitHelper.BodyPart bodyPartType = HitHelper.BodyPart.BODY;
        private Vec3 localOffset = Vec3.ZERO;
        private Vec3 halfExtents = new Vec3(0.25, 0.25, 0.25);
        @Nullable
        private String parentBoneId = null;
        @Nullable
        private String parentPartId = null;
        private int renderColor = COLOR_BODY;
        private float damageMultiplier = 1.0f;

        public Builder(@Nonnull String id) {
            this.id = id;
        }

        @Nonnull
        public Builder type(@Nonnull HitHelper.BodyPart type) {
            this.bodyPartType = type;
            // Set default color based on type
            this.renderColor = switch (type) {
                case HEAD -> COLOR_HEAD;
                case ARMS -> COLOR_ARMS;
                case BODY -> COLOR_BODY;
                case LEGS -> COLOR_LEGS;
            };
            return this;
        }

        @Nonnull
        public Builder offset(@Nonnull Vec3 offset) {
            this.localOffset = offset;
            return this;
        }

        @Nonnull
        public Builder offset(double x, double y, double z) {
            this.localOffset = new Vec3(x, y, z);
            return this;
        }

        @Nonnull
        public Builder halfExtents(@Nonnull Vec3 extents) {
            this.halfExtents = extents;
            return this;
        }

        @Nonnull
        public Builder halfExtents(double x, double y, double z) {
            this.halfExtents = new Vec3(x, y, z);
            return this;
        }

        @Nonnull
        public Builder size(@Nonnull Vec3 fullSize) {
            this.halfExtents = new Vec3(fullSize.x / 2, fullSize.y / 2, fullSize.z / 2);
            return this;
        }

        @Nonnull
        public Builder size(double x, double y, double z) {
            this.halfExtents = new Vec3(x / 2, y / 2, z / 2);
            return this;
        }

        @Nonnull
        public Builder bone(@Nullable String boneId) {
            this.parentBoneId = boneId;
            return this;
        }

        @Nonnull
        public Builder parent(@Nullable String parentId) {
            this.parentPartId = parentId;
            return this;
        }

        @Nonnull
        public Builder color(int argbColor) {
            this.renderColor = argbColor;
            return this;
        }

        @Nonnull
        public Builder damageMultiplier(float multiplier) {
            this.damageMultiplier = multiplier;
            return this;
        }

        @Nonnull
        public BodyPartDefinition build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Body part id is required");
            }
            HitHelper.BodyPart safeType = Objects.requireNonNull(bodyPartType, "bodyPartType");
            Vec3 safeOffset = Objects.requireNonNull(localOffset, "localOffset");
            Vec3 safeHalfExtents = Objects.requireNonNull(halfExtents, "halfExtents");
            return new BodyPartDefinition(
                id, safeType, safeOffset, safeHalfExtents,
                parentBoneId, parentPartId, renderColor, damageMultiplier
            );
        }
    }

    /**
     * Creates a new builder.
     */
    @Nonnull
    public static Builder builder(@Nonnull String id) {
        return new Builder(id);
    }

    // ==================== Common Presets ====================

    /**
     * Creates a head definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition head(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("head", HitHelper.BodyPart.HEAD, offset, halfExtents,
            boneId, "body", COLOR_HEAD, 2.0f);
    }

    /**
     * Creates a body/torso definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition body(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("body", HitHelper.BodyPart.BODY, offset, halfExtents,
            boneId, null, COLOR_BODY, 1.0f);
    }

    /**
     * Creates a left arm definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition leftArm(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("left_arm", HitHelper.BodyPart.ARMS, offset, halfExtents,
            boneId, "body", COLOR_ARMS, 0.75f);
    }

    /**
     * Creates a right arm definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition rightArm(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("right_arm", HitHelper.BodyPart.ARMS, offset, halfExtents,
            boneId, "body", COLOR_ARMS, 0.75f);
    }

    /**
     * Creates a left leg definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition leftLeg(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("left_leg", HitHelper.BodyPart.LEGS, offset, halfExtents,
            boneId, null, COLOR_LEGS, 0.5f);
    }

    /**
     * Creates a right leg definition with common defaults.
     */
    @Nonnull
    public static BodyPartDefinition rightLeg(@Nonnull Vec3 offset, @Nonnull Vec3 halfExtents, @Nullable String boneId) {
        return new BodyPartDefinition("right_leg", HitHelper.BodyPart.LEGS, offset, halfExtents,
            boneId, null, COLOR_LEGS, 0.5f);
    }
}

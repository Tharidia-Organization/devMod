package com.devmod.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SINGLE SOURCE OF TRUTH for the body part subdivision of an entity hitbox.
 *
 * Boxes are expressed in the target's local frame: the origin sits at the entity's
 * horizontal centre, Y stays in world coordinates and +Z points where the body faces
 * ({@code yBodyRot}). The lateral split (arms versus torso, dragon front versus tail)
 * is only meaningful in that frame - built on world axes it would make the hit result
 * depend on the attacker's compass direction instead of on the target's facing.
 *
 * Both consumers share this class so hit detection and the debug overlay cannot drift:
 * {@link HitHelper} raycasts the local boxes, {@code BodyPartCalculator} renders them.
 */
public final class BodyPartGeometry {

    /** Which subdivision layout applies to the target hitbox. */
    public enum Kind { HUMANOID, HORIZONTAL, TALL }

    /** One body part box in the local frame described on {@link BodyPartGeometry}. */
    public record LocalPart(HitHelper.BodyPart part, AABB box) {}

    private final Kind kind;
    private final List<LocalPart> parts;
    private final double centerX;
    private final double centerZ;
    private final double sinYaw;
    private final double cosYaw;

    private BodyPartGeometry(Kind kind, List<LocalPart> parts,
                             double centerX, double centerZ, double yawRad) {
        this.kind = kind;
        this.parts = List.copyOf(parts);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.sinYaw = Math.sin(yawRad);
        this.cosYaw = Math.cos(yawRad);
    }

    /**
     * Builds the layout for the given entity.
     *
     * Half-extents are read from the world bounding box without correction because
     * entity hitboxes are square in XZ, which makes them invariant under the yaw
     * rotation applied here.
     */
    @Nonnull
    public static BodyPartGeometry of(@Nonnull LivingEntity entity) {
        AABB mainBox = Objects.requireNonNull(entity.getBoundingBox(), "bounding box");
        Vec3 center = Objects.requireNonNull(mainBox.getCenter(), "hitbox centre");
        double width = mainBox.getXsize();
        double height = mainBox.getYsize();
        double depth = mainBox.getZsize();
        double yawRad = Math.toRadians(entity.yBodyRot);

        // ADAPTIVE MODE: detect non-humanoid hitboxes.
        // Ratio > 2.0 = horizontal body (dragon, fish, serpent)
        // Ratio < 0.5 = vertical body (enderman, tall boss)
        double aspectRatio = Math.max(width, depth) / height;
        Kind kind;
        List<LocalPart> parts;
        if (aspectRatio > 2.0) {
            kind = Kind.HORIZONTAL;
            parts = horizontalParts(mainBox, width, depth);
        } else if (height > 3.0 && aspectRatio < 0.5) {
            kind = Kind.TALL;
            parts = tallParts(mainBox, width, height, depth);
        } else {
            kind = Kind.HUMANOID;
            parts = humanoidParts(mainBox, width, height, depth);
        }

        return new BodyPartGeometry(kind, parts, center.x, center.z, yawRad);
    }

    /**
     * Body parts ordered from most to least specific.
     * Callers resolving a raycast must pick the nearest clip, not the first match;
     * this order only breaks ties on the shared faces between adjacent boxes.
     */
    @Nonnull
    public List<LocalPart> parts() {
        return parts;
    }

    @Nonnull
    public Kind kind() {
        return kind;
    }

    /** Maps a world point into the local frame. */
    @Nonnull
    public Vec3 toLocal(@Nonnull Vec3 world) {
        double dx = world.x - centerX;
        double dz = world.z - centerZ;
        return new Vec3(dx * cosYaw + dz * sinYaw, world.y, -dx * sinYaw + dz * cosYaw);
    }

    /** Maps a local point back into world space. */
    @Nonnull
    public Vec3 toWorld(@Nonnull Vec3 local) {
        double dx = local.x * cosYaw - local.z * sinYaw;
        double dz = local.x * sinYaw + local.z * cosYaw;
        return new Vec3(centerX + dx, local.y, centerZ + dz);
    }

    /**
     * World-space AABB enclosing a rotated local box.
     *
     * Lossy for yaws that are not multiples of 90 degrees: an axis-aligned box cannot
     * represent a rotated slab, so the result is the enclosing box. Intended for the
     * debug overlay only - hit detection must raycast the local boxes directly.
     */
    @Nonnull
    public AABB toWorldBounds(@Nonnull AABB localBox) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double[] xs = { localBox.minX, localBox.maxX };
        double[] zs = { localBox.minZ, localBox.maxZ };
        for (double x : xs) {
            for (double z : zs) {
                Vec3 corner = toWorld(new Vec3(x, 0, z));
                minX = Math.min(minX, corner.x);
                maxX = Math.max(maxX, corner.x);
                minZ = Math.min(minZ, corner.z);
                maxZ = Math.max(maxZ, corner.z);
            }
        }

        return new AABB(minX, localBox.minY, minZ, maxX, localBox.maxY, maxZ);
    }

    @Nonnull
    private static List<LocalPart> humanoidParts(AABB mainBox, double width, double height, double depth) {
        double halfWidth = width / 2;
        double halfDepth = depth / 2;

        // HEAD (TOP 25%)
        // BUG-006 FIX: clamp between 0.3 and 0.6 blocks - 25% is too large for very
        // tall mobs and too small for tiny ones.
        double headHeight = Math.max(0.3, Math.min(0.6, height * 0.25));
        double torsoTop = mainBox.maxY - headHeight;

        // TORSO + ARMS (MIDDLE 40%), arms taking the outer 30% of width on each side
        double torsoBottom = torsoTop - height * 0.40;
        double armWidth = width * 0.30;
        double halfBodyWidth = (width - 2 * armWidth) / 2;

        List<LocalPart> parts = new ArrayList<>();
        parts.add(new LocalPart(HitHelper.BodyPart.HEAD, new AABB(
            -halfWidth, torsoTop, -halfDepth,
            halfWidth, mainBox.maxY, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.BODY, new AABB(
            -halfBodyWidth, torsoBottom, -halfDepth,
            halfBodyWidth, torsoTop, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.ARMS, new AABB(
            -halfWidth, torsoBottom, -halfDepth,
            -halfWidth + armWidth, torsoTop, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.ARMS, new AABB(
            halfWidth - armWidth, torsoBottom, -halfDepth,
            halfWidth, torsoTop, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.LEGS, new AABB(
            -halfWidth, mainBox.minY, -halfDepth,
            halfWidth, torsoBottom, halfDepth)));
        return parts;
    }

    @Nonnull
    private static List<LocalPart> horizontalParts(AABB mainBox, double width, double depth) {
        // The body runs along the facing axis, which is local +Z by construction.
        double halfWidth = width / 2;
        double halfLength = depth / 2;
        double frontSize = depth * 0.30;
        double middleEnd = frontSize + depth * 0.40;

        List<LocalPart> parts = new ArrayList<>();
        parts.add(new LocalPart(HitHelper.BodyPart.HEAD, new AABB(
            -halfWidth, mainBox.minY, halfLength - frontSize,
            halfWidth, mainBox.maxY, halfLength)));
        parts.add(new LocalPart(HitHelper.BodyPart.BODY, new AABB(
            -halfWidth, mainBox.minY, halfLength - middleEnd,
            halfWidth, mainBox.maxY, halfLength - frontSize)));
        parts.add(new LocalPart(HitHelper.BodyPart.LEGS, new AABB(
            -halfWidth, mainBox.minY, -halfLength,
            halfWidth, mainBox.maxY, -halfLength + frontSize)));
        return parts;
    }

    @Nonnull
    private static List<LocalPart> tallParts(AABB mainBox, double width, double height, double depth) {
        double halfWidth = width / 2;
        double halfDepth = depth / 2;

        // BUG-006 FIX: minimum head zone - 15% is too small for Enderman (~2.9) or
        // Iron Golem (~2.7) sized mobs.
        double headHeight = Math.max(0.5, height * 0.15);
        double upperBodyTop = mainBox.maxY - headHeight;
        double lowerBodyTop = upperBodyTop - height * 0.35;
        double legsTop = lowerBodyTop - height * 0.30;

        List<LocalPart> parts = new ArrayList<>();
        parts.add(new LocalPart(HitHelper.BodyPart.HEAD, new AABB(
            -halfWidth, upperBodyTop, -halfDepth,
            halfWidth, mainBox.maxY, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.BODY, new AABB(
            -halfWidth, lowerBodyTop, -halfDepth,
            halfWidth, upperBodyTop, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.ARMS, new AABB(
            -halfWidth, legsTop, -halfDepth,
            halfWidth, lowerBodyTop, halfDepth)));
        parts.add(new LocalPart(HitHelper.BodyPart.LEGS, new AABB(
            -halfWidth, mainBox.minY, -halfDepth,
            halfWidth, legsTop, halfDepth)));
        return parts;
    }
}

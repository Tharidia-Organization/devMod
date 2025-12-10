package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.HitHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SINGLE SOURCE OF TRUTH for body part AABB calculation
 *
 * Centralizes all hitbox subdivision logic into body parts.
 * Used by:
 * - BodyPartRenderer (debug overlay rendering)
 * - HitHelper (raycast detection)
 * - Other systems that need consistent body part AABBs
 *
 * DESIGN:
 * - Immutable BodyPartAABB record for each part
 * - Static calculateAllBodyParts() method returns complete array
 * - Supports adaptive mode (humanoid, horizontal, tall)
 */
public class BodyPartCalculator {

    /**
     * Immutable record representing a body part with AABB and color
     */
    public record BodyPartAABB(HitHelper.BodyPart part, AABB box, int color) {}

    // Color definitions (ARGB format) - synchronized with BodyPartRenderer
    private static final int COLOR_HEAD = 0xFF00FFFF;  // Cyan
    private static final int COLOR_ARMS = 0xFFFFFF00;  // Yellow
    private static final int COLOR_BODY = 0xFF00FF00;  // Green
    private static final int COLOR_LEGS = 0xFFFF0000;  // Red

    /**
     * Calculates all body part AABBs for an entity.
     * SINGLE SOURCE OF TRUTH for body part boxes.
     *
     * @param entity The entity to calculate body parts for
     * @return Array of BodyPartAABB with all body parts
     */
    @Nonnull
    public static BodyPartAABB[] calculateAllBodyParts(@Nonnull LivingEntity entity) {
        AABB mainBox = entity.getBoundingBox();
        Vec3 center = mainBox.getCenter();
        double width = mainBox.getXsize();
        double height = mainBox.getYsize();
        double depth = mainBox.getZsize();

        // ADAPTIVE MODE: Detect non-humanoid hitboxes
        double aspectRatio = Math.max(width, depth) / height;
        boolean isHorizontalBody = aspectRatio > 2.0;
        boolean isTallBody = height > 3.0 && aspectRatio < 0.5;

        if (isHorizontalBody) {
            return calculateHorizontalBodyParts(mainBox, center, width, height, depth);
        } else if (isTallBody) {
            return calculateTallBodyParts(mainBox, center, width, height, depth);
        } else {
            return calculateHumanoidBodyParts(mainBox, center, width, height, depth);
        }
    }

    /**
     * Calcola body part per singola parte specifica.
     * Usato quando serve solo una parte (es. hit highlight).
     */
    @Nonnull
    public static BodyPartAABB calculateBodyPart(@Nonnull LivingEntity entity, @Nonnull HitHelper.BodyPart part) {
        BodyPartAABB[] allParts = calculateAllBodyParts(entity);
        for (BodyPartAABB bodyPart : allParts) {
            if (bodyPart.part() == part) {
                return bodyPart;
            }
        }
        // Fallback: ritorna la prima parte (non dovrebbe mai accadere)
        return Objects.requireNonNull(allParts[0]);
    }

    /**
     * Calcola body parts per entità umanoidi standard
     * Ritorna: [HEAD, LEFT_ARM, RIGHT_ARM, BODY, LEGS]
     */
    @Nonnull
    private static BodyPartAABB[] calculateHumanoidBodyParts(AABB mainBox, Vec3 center,
                                                             double width, double height, double depth) {
        List<BodyPartAABB> parts = new ArrayList<>();

        // HEAD (TOP 25%)
        double headHeight = height * 0.25;
        AABB headBox = new AABB(
            center.x - width/2, mainBox.maxY - headHeight, center.z - depth/2,
            center.x + width/2, mainBox.maxY, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.HEAD, headBox, COLOR_HEAD));

        // TORSO + ARMS (MIDDLE 40%)
        double torsoTop = mainBox.maxY - headHeight;
        double torsoHeight = height * 0.40;
        double torsoBottom = torsoTop - torsoHeight;
        double armWidth = width * 0.30;

        // LEFT ARM
        AABB leftArmBox = new AABB(
            mainBox.minX, torsoBottom, center.z - depth/2,
            mainBox.minX + armWidth, torsoTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.ARMS, leftArmBox, COLOR_ARMS));

        // RIGHT ARM
        AABB rightArmBox = new AABB(
            mainBox.maxX - armWidth, torsoBottom, center.z - depth/2,
            mainBox.maxX, torsoTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.ARMS, rightArmBox, COLOR_ARMS));

        // BODY (CENTER)
        double bodyWidth = width - (2 * armWidth);
        AABB bodyBox = new AABB(
            center.x - bodyWidth/2, torsoBottom, center.z - depth/2,
            center.x + bodyWidth/2, torsoTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.BODY, bodyBox, COLOR_BODY));

        // LEGS (BOTTOM 35%)
        AABB legsBox = new AABB(
            center.x - width/2, mainBox.minY, center.z - depth/2,
            center.x + width/2, torsoBottom, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.LEGS, legsBox, COLOR_LEGS));

        return Objects.requireNonNull(parts.toArray(new BodyPartAABB[0]));
    }

    /**
     * Calcola body parts per entità con corpo orizzontale (draghi)
     * Ritorna: [HEAD, BODY, TAIL]
     */
    @Nonnull
    private static BodyPartAABB[] calculateHorizontalBodyParts(AABB mainBox, Vec3 center,
                                                               double width, double height, double depth) {
        List<BodyPartAABB> parts = new ArrayList<>();

        boolean primaryAxisIsX = width > depth;
        double bodyLength = primaryAxisIsX ? width : depth;
        double bodyWidth = primaryAxisIsX ? depth : width;

        // FRONT 30% (HEAD)
        double frontSize = bodyLength * 0.30;
        AABB frontBox;
        if (primaryAxisIsX) {
            frontBox = new AABB(
                mainBox.maxX - frontSize, mainBox.minY, center.z - bodyWidth/2,
                mainBox.maxX, mainBox.maxY, center.z + bodyWidth/2
            );
        } else {
            frontBox = new AABB(
                center.x - bodyWidth/2, mainBox.minY, mainBox.maxZ - frontSize,
                center.x + bodyWidth/2, mainBox.maxY, mainBox.maxZ
            );
        }
        parts.add(new BodyPartAABB(HitHelper.BodyPart.HEAD, frontBox, COLOR_HEAD));

        // MIDDLE 40% (BODY)
        double middleStart = frontSize;
        double middleEnd = frontSize + (bodyLength * 0.40);
        AABB middleBox;
        if (primaryAxisIsX) {
            middleBox = new AABB(
                mainBox.maxX - middleEnd, mainBox.minY, center.z - bodyWidth/2,
                mainBox.maxX - middleStart, mainBox.maxY, center.z + bodyWidth/2
            );
        } else {
            middleBox = new AABB(
                center.x - bodyWidth/2, mainBox.minY, mainBox.maxZ - middleEnd,
                center.x + bodyWidth/2, mainBox.maxY, mainBox.maxZ - middleStart
            );
        }
        parts.add(new BodyPartAABB(HitHelper.BodyPart.BODY, middleBox, COLOR_BODY));

        // BACK 30% (LEGS/TAIL)
        AABB backBox;
        if (primaryAxisIsX) {
            backBox = new AABB(
                mainBox.minX, mainBox.minY, center.z - bodyWidth/2,
                mainBox.minX + frontSize, mainBox.maxY, center.z + bodyWidth/2
            );
        } else {
            backBox = new AABB(
                center.x - bodyWidth/2, mainBox.minY, mainBox.minZ,
                center.x + bodyWidth/2, mainBox.maxY, mainBox.minZ + frontSize
            );
        }
        parts.add(new BodyPartAABB(HitHelper.BodyPart.LEGS, backBox, COLOR_LEGS));

        return Objects.requireNonNull(parts.toArray(new BodyPartAABB[0]));
    }

    /**
     * Calcola body parts per entità alte (enderman, boss)
     * Ritorna: [HEAD, UPPER_BODY, LOWER_BODY, LEGS]
     */
    @Nonnull
    private static BodyPartAABB[] calculateTallBodyParts(AABB mainBox, Vec3 center,
                                                         double width, double height, double depth) {
        List<BodyPartAABB> parts = new ArrayList<>();

        // HEAD (TOP 15% - tighter)
        double headHeight = height * 0.15;
        AABB headBox = new AABB(
            center.x - width/2, mainBox.maxY - headHeight, center.z - depth/2,
            center.x + width/2, mainBox.maxY, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.HEAD, headBox, COLOR_HEAD));

        // UPPER BODY (35%)
        double upperBodyTop = mainBox.maxY - headHeight;
        double upperBodyHeight = height * 0.35;
        AABB upperBodyBox = new AABB(
            center.x - width/2, upperBodyTop - upperBodyHeight, center.z - depth/2,
            center.x + width/2, upperBodyTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.BODY, upperBodyBox, COLOR_BODY));

        // LOWER BODY (30%)
        double lowerBodyTop = upperBodyTop - upperBodyHeight;
        double lowerBodyHeight = height * 0.30;
        AABB lowerBodyBox = new AABB(
            center.x - width/2, lowerBodyTop - lowerBodyHeight, center.z - depth/2,
            center.x + width/2, lowerBodyTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.ARMS, lowerBodyBox, COLOR_ARMS));

        // LEGS (BOTTOM 20%)
        double legsTop = lowerBodyTop - lowerBodyHeight;
        AABB legsBox = new AABB(
            center.x - width/2, mainBox.minY, center.z - depth/2,
            center.x + width/2, legsTop, center.z + depth/2
        );
        parts.add(new BodyPartAABB(HitHelper.BodyPart.LEGS, legsBox, COLOR_LEGS));

        return Objects.requireNonNull(parts.toArray(new BodyPartAABB[0]));
    }

    /**
     * Ottieni colore per body part (per compatibilità con BodyPartRenderer)
     */
    public static int getColorForBodyPart(@Nonnull HitHelper.BodyPart part) {
        return switch (part) {
            case HEAD -> COLOR_HEAD;
            case ARMS -> COLOR_ARMS;
            case BODY -> COLOR_BODY;
            case LEGS -> COLOR_LEGS;
        };
    }
}

package com.devmod.client.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.combat.BodyPartGeometry;
import com.devmod.combat.HitHelper;

public class BodyPartCalculator {

    /**
     * Immutable record representing a body part with AABB and color
     */
    public record BodyPartAABB(HitHelper.BodyPart part, AABB box, int color) {}

    // Color definitions - delegating to OverlayTheme.BodyPart (single source of truth)
    private static final int COLOR_HEAD = OverlayTheme.BodyPart.HEAD;
    private static final int COLOR_ARMS = OverlayTheme.BodyPart.ARMS;
    private static final int COLOR_BODY = OverlayTheme.BodyPart.BODY;
    private static final int COLOR_LEGS = OverlayTheme.BodyPart.LEGS;

    /**
     * Calculates all body part AABBs for an entity.
     *
     * Geometry comes from {@link BodyPartGeometry}, shared with HitHelper so the overlay
     * cannot drift from real hit detection. The boxes are yaw-rotated slabs, so what is
     * drawn is the enclosing axis-aligned box of each one.
     *
     * @param entity The entity to calculate body parts for
     * @return Array of BodyPartAABB with all body parts
     */
    @Nonnull
    public static BodyPartAABB[] calculateAllBodyParts(@Nonnull LivingEntity entity) {
        BodyPartGeometry geometry = BodyPartGeometry.of(entity);

        List<BodyPartAABB> parts = new ArrayList<>();
        for (BodyPartGeometry.LocalPart local : geometry.parts()) {
            parts.add(new BodyPartAABB(
                local.part(),
                geometry.toWorldBounds(local.box()),
                getColorForBodyPart(local.part())));
        }

        return Objects.requireNonNull(parts.toArray(new BodyPartAABB[0]));
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

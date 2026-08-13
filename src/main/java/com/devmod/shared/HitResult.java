package com.devmod.shared;

import net.minecraft.world.phys.Vec3;

/**
 * Raycast result pairing a {@link BodyPart} with an optional impact position.
 *
 * <p>Extracted from {@code combat.HitHelper} to break circular dependencies
 * between the combat, damage, and collision packages.
 */
public record HitResult(BodyPart part, Vec3 hitPoint) {

    public static HitResult of(BodyPart part, Vec3 hitPoint) {
        return new HitResult(part, hitPoint);
    }

    public static HitResult of(BodyPart part) {
        return new HitResult(part, null);
    }
}

package com.devmod.shared;

/**
 * Body part hit zone used by combat, damage, and collision systems.
 *
 * <p>Extracted from {@code combat.HitHelper} to break circular dependencies
 * between the combat, damage, and collision packages.
 */
public enum BodyPart {
    HEAD,
    BODY,
    ARMS,
    LEGS
}

package com.devmod.combat;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.debug.DiagnosticLogger;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.loading.FMLEnvironment;

public class ShieldDeflector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShieldDeflector.class);

    private static final Random RANDOM = new Random();

    // Default shield radius for deflection calculations
    private static final float DEFAULT_SHIELD_RADIUS = 1.2f;

    /**
     * Deflects a projectile off an energy shield.
     *
     * @param projectile The projectile to deflect
     * @param shieldOwner The entity wearing the shield
     * @param spread Maximum angular spread in radians (0 = perfect reflection)
     * @param speedMultiplier Speed modifier after deflection (1.0 = same speed)
     * @param returnToSender If true, deflects toward the projectile's shooter
     * @param shieldRadius Radius of the shield sphere
     * @return true if deflection was successful
     */
    public static boolean deflectProjectile(Projectile projectile, LivingEntity shieldOwner,
                                             float spread, float speedMultiplier,
                                             boolean returnToSender, float shieldRadius) {
        if (projectile == null || shieldOwner == null) {
            return false;
        }

        final @Nonnull Vec3 projectilePos = Objects.requireNonNull(projectile.position(), "projectile position");
        final @Nonnull Vec3 projectileVel = Objects.requireNonNull(projectile.getDeltaMovement(), "projectile velocity");

        if (projectileVel.lengthSqr() < 0.0001) {
            return false; // Projectile not moving
        }

        // Shield center is at owner's chest height
        final @Nonnull Vec3 shieldCenter = Objects.requireNonNull(
            Objects.requireNonNull(shieldOwner.position(), "shield owner position")
                .add(0, shieldOwner.getBbHeight() * 0.5, 0),
            "shield center");

        // Calculate impact point on shield sphere
        Vec3 impactPoint = calculateImpactPoint(projectilePos, projectileVel, shieldCenter, shieldRadius);
        if (impactPoint == null) {
            // Fallback: use projectile position
            impactPoint = projectilePos;
        }
        final @Nonnull Vec3 impactPointNonNull = Objects.requireNonNull(impactPoint, "impact point");

        // Calculate surface normal at impact point (points outward from center)
        final @Nonnull Vec3 impactOffset = Objects.requireNonNull(impactPointNonNull.subtract(shieldCenter), "impact offset");
        final @Nonnull Vec3 surfaceNormal = Objects.requireNonNull(impactOffset.normalize(), "surface normal");

        // Calculate deflection direction
        final @Nonnull Vec3 normalizedVelocity = Objects.requireNonNull(projectileVel.normalize(), "projectile velocity norm");
        final @Nonnull Vec3 deflectionDir;
        if (returnToSender) {
            deflectionDir = Objects.requireNonNull(
                calculateReturnToSender(projectile, impactPointNonNull, surfaceNormal, spread),
                "deflection direction");
        } else {
            deflectionDir = Objects.requireNonNull(
                calculateReflection(normalizedVelocity, surfaceNormal, spread),
                "deflection direction");
        }

        // Apply speed multiplier
        double originalSpeed = projectileVel.length();
        double newSpeed = originalSpeed * speedMultiplier;
        Vec3 newVelocity = Objects.requireNonNull(deflectionDir.scale(newSpeed), "new velocity");

        // Apply new velocity to projectile
        projectile.setDeltaMovement(newVelocity);

        // Remove ownership so it can damage anyone (including original shooter)
        if (returnToSender) {
            // Clear owner so projectile can hit the original shooter
            projectile.setOwner(null);
        }

        // Trigger visual feedback (client-only)
        float damage = estimateProjectileDamage(projectile);
        recordImpactClientSafe(impactPointNonNull, damage);

        LOGGER.debug("Deflected {} at angle {}, speed {}->{}",
            projectile.getType().getDescriptionId(),
            Math.toDegrees(Math.acos(normalizedVelocity.dot(deflectionDir))),
            originalSpeed, newSpeed);

        DiagnosticLogger.combat("shieldDeflect: projectile=%s, owner=%s, angle=%.1f, speed=%.2f->%.2f, returnToSender=%s",
            projectile.getType().getDescriptionId(),
            shieldOwner.getName().getString(),
            Math.toDegrees(Math.acos(normalizedVelocity.dot(deflectionDir))),
            originalSpeed, newSpeed, returnToSender);

        return true;
    }

    /**
     * Simplified deflection with default parameters.
     */
    public static boolean deflectProjectile(Projectile projectile, LivingEntity shieldOwner) {
        return deflectProjectile(projectile, shieldOwner, 0.15f, 0.8f, false, DEFAULT_SHIELD_RADIUS);
    }

    /**
     * Calculates the impact point on a sphere using ray-sphere intersection.
     *
     * @param rayOrigin Starting point of the ray (projectile position)
     * @param rayDir Direction of the ray (projectile velocity, doesn't need to be normalized)
     * @param sphereCenter Center of the sphere
     * @param sphereRadius Radius of the sphere
     * @return Impact point, or null if no intersection
     */
    private static @Nullable Vec3 calculateImpactPoint(@Nonnull Vec3 rayOrigin, @Nonnull Vec3 rayDir,
                                                       @Nonnull Vec3 sphereCenter, float sphereRadius) {
        // Ray-sphere intersection using quadratic formula
        Vec3 oc = Objects.requireNonNull(rayOrigin.subtract(sphereCenter), "oc vector");
        Vec3 d = Objects.requireNonNull(rayDir.normalize(), "normalized ray direction");

        double a = d.dot(d); // Should be 1 if normalized, but kept for safety
        double b = 2.0 * oc.dot(d);
        double c = oc.dot(oc) - sphereRadius * sphereRadius;

        double discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            // No intersection - projectile is not hitting sphere
            // This can happen if projectile spawned inside or is moving away
            return null;
        }

        // Find the closest positive intersection
        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-b - sqrtDisc) / (2 * a);
        double t2 = (-b + sqrtDisc) / (2 * a);

        double t;
        if (t1 > 0) {
            t = t1; // First intersection (entering sphere)
        } else if (t2 > 0) {
            t = t2; // Second intersection (exiting, projectile started inside)
        } else {
            return null; // Both behind ray origin
        }

        final @Nonnull Vec3 scaledD = Objects.requireNonNull(d.scale(t), "scaled direction");
        final @Nonnull Vec3 impact = Objects.requireNonNull(rayOrigin.add(scaledD), "impact point");
        return impact;
    }

    /**
     * Calculates reflection direction with optional spread.
     *
     * @param incidentDir Normalized incoming direction
     * @param normal Normalized surface normal
     * @param spread Maximum angular deviation in radians
     * @return Normalized reflection direction
     */
    private static @Nonnull Vec3 calculateReflection(@Nonnull Vec3 incidentDir, @Nonnull Vec3 normal, float spread) {
        // Standard reflection formula: R = I - 2(I·N)N
        double dot = incidentDir.dot(normal);
        Vec3 reflectionBase = Objects.requireNonNull(
            incidentDir.subtract(Objects.requireNonNull(normal.scale(2.0 * dot), "scaled normal")), "reflection base");
        Vec3 reflection = Objects.requireNonNull(reflectionBase.normalize(), "reflection direction");

        // Apply random spread
        if (spread > 0) {
            reflection = Objects.requireNonNull(applySpread(reflection, spread), "spread reflection");
        }

        return reflection;
    }

    /**
     * Calculates direction back toward the original shooter.
     *
     * @param projectile The projectile being deflected
     * @param impactPoint Point of shield impact
     * @param surfaceNormal Shield surface normal at impact
     * @param spread Maximum angular deviation
     * @return Normalized direction toward shooter (with spread)
     */
    private static @Nonnull Vec3 calculateReturnToSender(Projectile projectile, @Nonnull Vec3 impactPoint,
                                                         @Nonnull Vec3 surfaceNormal, float spread) {
        Entity owner = projectile.getOwner();
        if (owner == null) {
            // No known owner, use regular reflection
            Vec3 velocity = Objects.requireNonNull(projectile.getDeltaMovement(), "projectile velocity");
            return calculateReflection(Objects.requireNonNull(velocity.normalize(), "normalized velocity"), surfaceNormal, spread);
        }

        // Direction from impact point to owner (where the projectile came from)
        Vec3 eyePos = Objects.requireNonNull(owner.getEyePosition(), "owner eye position");
        Vec3 toOwner = Objects.requireNonNull(eyePos.subtract(impactPoint), "toOwner");
        toOwner = Objects.requireNonNull(toOwner.normalize(), "direction to owner");

        // Apply spread around this direction
        if (spread > 0) {
            toOwner = Objects.requireNonNull(applySpread(toOwner, spread), "spread toOwner");
        }

        return toOwner;
    }

    /**
     * Applies random angular spread to a direction vector.
     *
     * @param direction Normalized base direction
     * @param maxSpread Maximum spread angle in radians
     * @return New normalized direction with random deviation
     */
    private static @Nonnull Vec3 applySpread(@Nonnull Vec3 direction, float maxSpread) {
        final @Nonnull Vec3 baseDirection = Objects.requireNonNull(direction, "spread base direction");
        // Generate random angles within the spread cone
        double spreadAngle = RANDOM.nextDouble() * maxSpread;
        double rotationAngle = RANDOM.nextDouble() * Math.PI * 2;

        // Create perpendicular vectors for rotation
        final @Nonnull Vec3 perp1 = getPerpendicularVector(baseDirection);
        final @Nonnull Vec3 perp2 = Objects.requireNonNull(baseDirection.cross(perp1).normalize(), "perp2");

        // Apply rotation using Rodrigues' formula simplified
        double cosSpread = Math.cos(spreadAngle);
        double sinSpread = Math.sin(spreadAngle);
        double cosRot = Math.cos(rotationAngle);
        double sinRot = Math.sin(rotationAngle);

        // Rotate direction by spreadAngle around axis (perp1 * cosRot + perp2 * sinRot)
        final @Nonnull Vec3 rotAxis = Objects.requireNonNull(
            Objects.requireNonNull(
                Objects.requireNonNull(perp1.scale(cosRot), "perp1 scaled")
                    .add(Objects.requireNonNull(perp2.scale(sinRot), "perp2 scaled")),
                "rotation axis base"
            ).normalize(),
            "rotation axis");

        // Rodrigues' rotation formula
        final @Nonnull Vec3 scaledDir = Objects.requireNonNull(baseDirection.scale(cosSpread), "scaled direction");
        final @Nonnull Vec3 cross = Objects.requireNonNull(rotAxis.cross(baseDirection), "rotAxis cross");
        final @Nonnull Vec3 scaledCross = Objects.requireNonNull(cross.scale(sinSpread), "scaled cross");
        final @Nonnull Vec3 scaledAxis = Objects.requireNonNull(rotAxis.scale(rotAxis.dot(direction) * (1 - cosSpread)), "scaled rotAxis");

        final @Nonnull Vec3 result = Objects.requireNonNull(scaledDir.add(scaledCross).add(scaledAxis), "spread combination");

        return Objects.requireNonNull(result.normalize(), "spread result");
    }

    /**
     * Gets a vector perpendicular to the given vector.
     */
    private static @Nonnull Vec3 getPerpendicularVector(@Nonnull Vec3 v) {
        Vec3 base = Math.abs(v.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return Objects.requireNonNull(base.cross(v).normalize(), "perpendicular vector");
    }

    /**
     * Estimates the damage a projectile would deal (for visual intensity).
     */
    private static float estimateProjectileDamage(Projectile projectile) {
        // Base damage estimation based on projectile type and speed
        double speed = projectile.getDeltaMovement().length();

        // Arrow base damage is typically 2-5, scale with speed
        float baseDamage = 2.0f;

        // Speed factor (arrows at full draw are ~3.0 blocks/tick)
        float speedFactor = (float) Math.min(speed / 3.0, 2.0);

        return baseDamage * speedFactor;
    }

    /**
     * Checks if a projectile should be deflected by the shield.
     *
     * @param projectile The projectile to check
     * @param shieldOwner The entity with the shield
     * @param shieldRadius Shield radius
     * @return true if projectile is within deflection range
     */
    public static boolean isWithinDeflectionRange(Projectile projectile, LivingEntity shieldOwner, float shieldRadius) {
        if (projectile == null || shieldOwner == null) {
            return false;
        }

        final @Nonnull Vec3 shieldCenter = Objects.requireNonNull(
            Objects.requireNonNull(shieldOwner.position(), "shield owner position")
                .add(0, shieldOwner.getBbHeight() * 0.5, 0),
            "shield center");
        final @Nonnull Vec3 projectilePos = Objects.requireNonNull(projectile.position(), "projectile position");
        double distSq = projectilePos.distanceToSqr(shieldCenter);

        // Slightly larger than shield radius to catch incoming projectiles
        double threshold = (shieldRadius + 0.5) * (shieldRadius + 0.5);
        return distSq <= threshold;
    }

    /**
     * Checks if projectile is moving toward the shield owner.
     */
    public static boolean isMovingToward(Projectile projectile, LivingEntity target) {
        if (projectile == null || target == null) {
            return false;
        }

        Vec3 toTarget = Objects.requireNonNull(target.position(), "target position")
            .add(0, target.getBbHeight() * 0.5, 0)
            .subtract(Objects.requireNonNull(projectile.position(), "projectile position"));
        toTarget = Objects.requireNonNull(toTarget.normalize(), "direction to target");
        Vec3 velocity = Objects.requireNonNull(
            Objects.requireNonNull(projectile.getDeltaMovement(), "projectile velocity").normalize(),
            "normalized velocity");

        // Projectile is moving toward target if dot product is positive
        return toTarget.dot(velocity) > 0;
    }

    // ========== Client-safe helpers ==========

    private static volatile Method recordImpactMethod;
    private static volatile boolean recordImpactMethodInitialized = false;

    /**
     * Records shield impact for visual feedback (client-only, server-safe).
     * Uses reflection to avoid loading client classes on dedicated server.
     */
    private static void recordImpactClientSafe(Vec3 impactPoint, float damage) {
        if (!FMLEnvironment.dist.isClient()) {
            return; // No-op on server
        }

        Method method = getCachedRecordImpactMethod();
        if (method != null) {
            try {
                method.invoke(null, impactPoint, damage);
            } catch (Exception e) {
                LOGGER.debug("Could not record shield impact VFX: {}", e.getMessage());
            }
        }
    }

    @javax.annotation.Nullable
    private static Method getCachedRecordImpactMethod() {
        if (!recordImpactMethodInitialized) {
            synchronized (ShieldDeflector.class) {
                if (!recordImpactMethodInitialized) {
                    try {
                        Class<?> rendererClass = Class.forName("com.devmod.client.rendering.shield.EnergyShieldRenderer");
                        recordImpactMethod = rendererClass.getMethod("recordImpact", Vec3.class, float.class);
                    } catch (Exception e) {
                        LOGGER.debug("Could not cache recordImpact method: {}", e.getMessage());
                    } finally {
                        recordImpactMethodInitialized = true;
                    }
                }
            }
        }
        return recordImpactMethod;
    }
}

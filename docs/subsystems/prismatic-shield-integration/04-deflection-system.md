# 04 - Deflection System

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

## Obiettivo

Sostituire la logica semplice `velocity.reverse()` con un sistema di deflessione realistico basato su ray-sphere intersection.

## Problema Attuale

```java
// DamageHandler.java - troppo semplice
if (stats.shieldReflectProjectiles && source.getDirectEntity() instanceof Projectile) {
    Vec3 vel = projectile.getDeltaMovement();
    projectile.setDeltaMovement(vel.reverse()); // Rimbalza dritto indietro
}
```

Questo causa:
- Frecce che tornano **esattamente** al mittente (irrealistico)
- Nessuna variazione angolare
- Comportamento prevedibile e noioso

## Soluzione: Ray-Sphere Intersection

### Teoria

1. Modella lo scudo come sfera centrata sul player
2. Calcola il punto di intersezione tra traiettoria proiettile e sfera
3. Calcola la normale della sfera in quel punto
4. Rifletti la velocità rispetto alla normale

```
          Normal (N)
             ↑
             |
    Incoming ↘ ↗ Reflected
      (V)      |     (R)
               |
         ─────●───── Shield Surface
               Point of Impact
```

Formula: `R = V - 2(V·N)N`

### Implementazione

```java
package com.devmod.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/**
 * Calculates realistic projectile deflection using ray-sphere intersection.
 */
public class ShieldDeflector {

    /**
     * Result of a deflection calculation.
     */
    public record DeflectionResult(
        Vec3 newVelocity,
        Vec3 hitPoint,
        Vec3 surfaceNormal,
        boolean deflected
    ) {
        public static DeflectionResult miss() {
            return new DeflectionResult(null, null, null, false);
        }
    }

    /**
     * Calculates deflected velocity for a projectile hitting a shield.
     *
     * @param projectile The incoming projectile
     * @param shieldOwner The entity whose shield was hit
     * @param shieldRadius Radius of the shield sphere
     * @param deflectionAngle Maximum random deflection angle (radians)
     * @return Deflection result with new velocity and hit point
     */
    public static DeflectionResult calculateDeflection(
            Projectile projectile,
            LivingEntity shieldOwner,
            float shieldRadius,
            float deflectionAngle) {

        Vec3 shieldCenter = shieldOwner.position()
            .add(0, shieldOwner.getBbHeight() / 2, 0);

        Vec3 projectilePos = projectile.position();
        Vec3 projectileVel = projectile.getDeltaMovement();

        // Normalize velocity for ray direction
        Vec3 rayDir = projectileVel.normalize();

        // === Ray-Sphere Intersection ===
        // Ray: P = projectilePos + t * rayDir
        // Sphere: |P - shieldCenter|^2 = shieldRadius^2
        //
        // Expanding: |projectilePos + t*rayDir - shieldCenter|^2 = r^2
        // Let L = projectilePos - shieldCenter
        // |L + t*rayDir|^2 = r^2
        // t^2(rayDir·rayDir) + 2t(L·rayDir) + (L·L) - r^2 = 0
        //
        // This is quadratic: at^2 + bt + c = 0
        // where: a = rayDir·rayDir (=1 if normalized)
        //        b = 2(L·rayDir)
        //        c = L·L - r^2

        Vec3 L = projectilePos.subtract(shieldCenter);

        double a = rayDir.dot(rayDir);
        double b = 2.0 * L.dot(rayDir);
        double c = L.dot(L) - (shieldRadius * shieldRadius);

        double discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            // No intersection
            return DeflectionResult.miss();
        }

        // Find closest intersection point (smallest positive t)
        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-b - sqrtDisc) / (2 * a);
        double t2 = (-b + sqrtDisc) / (2 * a);

        double t;
        if (t1 > 0) {
            t = t1;
        } else if (t2 > 0) {
            t = t2;
        } else {
            // Both intersections behind the projectile
            return DeflectionResult.miss();
        }

        // Calculate hit point
        Vec3 hitPoint = projectilePos.add(rayDir.scale(t));

        // Calculate surface normal (points outward from center)
        Vec3 normal = hitPoint.subtract(shieldCenter).normalize();

        // === Reflection Formula ===
        // R = V - 2(V·N)N
        double dot = projectileVel.dot(normal);
        Vec3 reflected = projectileVel.subtract(normal.scale(2 * dot));

        // === Add Randomness ===
        if (deflectionAngle > 0) {
            reflected = addRandomDeflection(reflected, deflectionAngle);
        }

        // Preserve speed
        double originalSpeed = projectileVel.length();
        Vec3 finalVelocity = reflected.normalize().scale(originalSpeed);

        return new DeflectionResult(finalVelocity, hitPoint, normal, true);
    }

    /**
     * Adds random angular deviation to a velocity vector.
     */
    private static Vec3 addRandomDeflection(Vec3 velocity, float maxAngle) {
        java.util.Random rand = new java.util.Random();

        // Random rotation around the velocity axis
        double yawOffset = (rand.nextDouble() - 0.5) * 2 * maxAngle;
        double pitchOffset = (rand.nextDouble() - 0.5) * 2 * maxAngle;

        // Convert to spherical, add offset, convert back
        double speed = velocity.length();
        double yaw = Math.atan2(velocity.z, velocity.x) + yawOffset;
        double pitch = Math.asin(velocity.y / speed) + pitchOffset;

        // Clamp pitch to avoid gimbal issues
        pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch));

        double cosPitch = Math.cos(pitch);
        return new Vec3(
            Math.cos(yaw) * cosPitch * speed,
            Math.sin(pitch) * speed,
            Math.sin(yaw) * cosPitch * speed
        );
    }

    /**
     * Simplified deflection for performance (no ray-sphere, just normal calculation).
     * Use when projectile is already very close to shield.
     */
    public static Vec3 quickDeflect(Projectile projectile, LivingEntity shieldOwner) {
        Vec3 shieldCenter = shieldOwner.position()
            .add(0, shieldOwner.getBbHeight() / 2, 0);

        Vec3 toProjectile = projectile.position().subtract(shieldCenter);
        Vec3 normal = toProjectile.normalize();

        Vec3 velocity = projectile.getDeltaMovement();
        double dot = velocity.dot(normal);

        // Only deflect if projectile is moving toward shield
        if (dot >= 0) {
            return velocity; // Moving away, no deflection
        }

        Vec3 reflected = velocity.subtract(normal.scale(2 * dot));
        return reflected;
    }
}
```

## Integrazione in DamageHandler

```java
// DamageHandler.java - sostituire logica esistente

import com.devmod.combat.ShieldDeflector;
import com.devmod.combat.ShieldDeflector.DeflectionResult;

private static void applyShieldBlock(Player player, ArmorStats stats,
                                     DamageSource source, float damage) {
    // ... existing damage reduction logic ...

    // === PROJECTILE DEFLECTION ===
    if (stats.shieldReflectProjectiles &&
            source.getDirectEntity() instanceof Projectile projectile) {

        // Calculate shield radius based on player size
        float shieldRadius = player.getBbWidth() * 0.8f + 0.5f;

        // Maximum deflection angle (in radians)
        // Higher values = more random spread
        float deflectionAngle = 0.15f; // ~8.6 degrees

        DeflectionResult result = ShieldDeflector.calculateDeflection(
            projectile,
            player,
            shieldRadius,
            deflectionAngle
        );

        if (result.deflected()) {
            // Apply new velocity
            projectile.setDeltaMovement(result.newVelocity());

            // Change owner so deflected projectile can hurt enemies
            projectile.setOwner(player);

            // Trigger impact VFX at hit point (client-side)
            if (player.level().isClientSide && result.hitPoint() != null) {
                Vec3 localHit = result.hitPoint().subtract(player.position());
                ShieldImpactManager.registerImpact(player.getId(), localHit, damage);
            }

            // Optional: play deflection sound
            player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }
}
```

## Configurazione

Aggiungere a `ArmorStats.java`:

```java
// Shield Deflection Settings
public float shieldDeflectionSpread = 0.15f; // Max angular spread (radians)
public boolean shieldDeflectToOwner = false; // If true, deflect toward original shooter
public float shieldDeflectSpeedMult = 0.8f;  // Speed multiplier after deflection
```

## Modalità Deflessione Avanzate

### 1. Deflect-to-Sender
Frecce che tornano esattamente al mittente (come un'abilità speciale):

```java
public static Vec3 deflectToSender(Projectile projectile, LivingEntity shieldOwner) {
    var owner = projectile.getOwner();
    if (owner == null) {
        return quickDeflect(projectile, shieldOwner);
    }

    Vec3 toOwner = owner.position()
        .add(0, owner.getBbHeight() / 2, 0)
        .subtract(projectile.position())
        .normalize();

    double speed = projectile.getDeltaMovement().length();
    return toOwner.scale(speed);
}
```

### 2. Scatter Deflection
Proiettili che si dividono in più frammenti:

```java
public static List<Vec3> scatterDeflect(Projectile projectile, LivingEntity shieldOwner,
                                        int fragmentCount) {
    Vec3 baseDeflection = quickDeflect(projectile, shieldOwner);
    double speed = baseDeflection.length() / fragmentCount; // Split energy

    List<Vec3> fragments = new ArrayList<>();
    for (int i = 0; i < fragmentCount; i++) {
        Vec3 scattered = addRandomDeflection(baseDeflection.normalize().scale(speed), 0.5f);
        fragments.add(scattered);
    }
    return fragments;
}
```

## Testing

1. **Test base deflection**
   - Spara freccia al giocatore con scudo
   - Verificare che rimbalzi con angolo realistico (non dritto indietro)

2. **Test angolo incidenza**
   - Spara da angoli diversi
   - Verificare che angolo riflesso sia coerente con fisica

3. **Test spread**
   - Spara molte frecce
   - Verificare distribuzione angolare delle deflessioni

4. **Test performance**
   - Molti proiettili simultanei
   - Verificare nessun lag significativo

## Performance Notes

| Operazione | Costo |
|------------|-------|
| Ray-sphere intersection | ~20 operazioni floating point |
| Reflection calculation | ~10 operazioni |
| Random deflection | ~30 operazioni |
| **Totale per proiettile** | **~60 operazioni** (trascurabile) |

Il sistema è O(1) per proiettile, quindi scala linearmente con il numero di proiettili deflessi.

# 03 - Impact Effects

## Obiettivo

Implementare effetti visivi per quando lo scudo:
1. **Blocca** un attacco (flash)
2. **Si rompe** (shatter)

## 1. Shield Impact Flash

### Comportamento
- Durata: 400ms (10 ticks)
- Propagazione radiale dal punto d'impatto
- Colore: bianco brillante che sfuma nel colore scudo
- Intensità proporzionale al danno bloccato

### Implementazione

```java
package com.frenkvs.devmod.client.vfx;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages shield impact flash effects.
 * Each impact creates a temporary glow at the hit point.
 */
public class ShieldImpactManager {

    private static final List<ShieldImpact> activeImpacts = new ArrayList<>();
    private static final int MAX_IMPACTS = 8; // Per entity
    private static final long IMPACT_DURATION_MS = 400;

    /**
     * Registers a new shield impact.
     *
     * @param entityId Entity ID of the shield owner
     * @param localHitPoint Hit point in entity-local coordinates
     * @param damageBlocked Amount of damage blocked (affects intensity)
     */
    public static void registerImpact(int entityId, Vec3 localHitPoint, float damageBlocked) {
        // Limit impacts per entity
        long count = activeImpacts.stream()
            .filter(i -> i.entityId == entityId)
            .count();
        if (count >= MAX_IMPACTS) {
            // Remove oldest impact for this entity
            activeImpacts.stream()
                .filter(i -> i.entityId == entityId)
                .findFirst()
                .ifPresent(activeImpacts::remove);
        }

        float intensity = Math.min(1.0f, damageBlocked / 20.0f); // Cap at 20 damage
        activeImpacts.add(new ShieldImpact(entityId, localHitPoint, intensity));
    }

    /**
     * Gets the most recent impact for an entity (for shader).
     * Returns null if no active impact.
     */
    public static ShieldImpact getActiveImpact(int entityId) {
        return activeImpacts.stream()
            .filter(i -> i.entityId == entityId && !i.isExpired())
            .reduce((first, second) -> second) // Get last (most recent)
            .orElse(null);
    }

    /**
     * Ticks all impacts, removing expired ones.
     */
    public static void tick() {
        Iterator<ShieldImpact> it = activeImpacts.iterator();
        while (it.hasNext()) {
            ShieldImpact impact = it.next();
            if (impact.isExpired()) {
                it.remove();
            }
        }
    }

    /**
     * Clears all impacts (e.g., on world unload).
     */
    public static void clear() {
        activeImpacts.clear();
    }

    /**
     * Represents a single shield impact.
     */
    public static class ShieldImpact {
        public final int entityId;
        public final Vec3 localHitPoint;
        public final float intensity;
        public final long startTime;

        public ShieldImpact(int entityId, Vec3 localHitPoint, float intensity) {
            this.entityId = entityId;
            this.localHitPoint = localHitPoint;
            this.intensity = intensity;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > IMPACT_DURATION_MS;
        }

        /**
         * Gets fade progress (0.0 = just started, 1.0 = expired).
         */
        public float getFadeProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, (float) elapsed / IMPACT_DURATION_MS);
        }

        /**
         * Gets current intensity (fades over time).
         */
        public float getCurrentIntensity() {
            float fade = getFadeProgress();
            // Ease-out curve for smooth fade
            return intensity * (1.0f - fade * fade);
        }

        /**
         * Gets time since impact in seconds (for shader).
         */
        public float getTimeSinceImpact() {
            return (System.currentTimeMillis() - startTime) / 1000.0f;
        }
    }
}
```

### Integrazione con Shader

```java
// In EnergyShieldRenderer.renderShield()

ShieldImpactManager.ShieldImpact impact =
    ShieldImpactManager.getActiveImpact(entity.getId());

float impactTime = impact != null ? impact.getTimeSinceImpact() : 999.0f;
Vec3 impactPoint = impact != null ? impact.localHitPoint : Vec3.ZERO;

EnergyShieldShader.bind(
    time,
    stats.getShieldColorVec3(),
    stats.shieldBlockStrength,
    impactTime,      // Passa al shader
    impactPoint      // Passa al shader
);
```

## 2. Shield Shatter Effect

### Comportamento
- Trigger: scudo esaurisce durabilità o forza
- Visual: frammenti che si staccano e cadono
- Durata: 1.5s con fisica simulata
- Suono: vetro che si rompe (opzionale)

### Implementazione

```java
package com.frenkvs.devmod.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Renders shield shatter effect when a shield breaks.
 */
public class ShieldShatterEffect {

    private static final List<ShatterInstance> activeEffects = new ArrayList<>();
    private static final long SHATTER_DURATION_MS = 1500;
    private static final int FRAGMENT_COUNT = 24;

    // Golden ratio for natural-looking distribution
    private static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

    /**
     * Triggers a shatter effect at entity position.
     *
     * @param entityId Entity whose shield broke
     * @param center Shield center position
     * @param radius Shield radius
     * @param color Shield color
     */
    public static void trigger(int entityId, Vec3 center, float radius, int color) {
        activeEffects.add(new ShatterInstance(entityId, center, radius, color));
    }

    /**
     * Ticks all effects.
     */
    public static void tick() {
        activeEffects.removeIf(ShatterInstance::isExpired);
        for (ShatterInstance effect : activeEffects) {
            effect.tick();
        }
    }

    /**
     * Renders all active effects.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              Vec3 cameraPos, float partialTick) {
        for (ShatterInstance effect : activeEffects) {
            effect.render(poseStack, bufferSource, cameraPos, partialTick);
        }
    }

    /**
     * A single shatter effect instance.
     */
    private static class ShatterInstance {
        private final int entityId;
        private final Vec3 center;
        private final int color;
        private final long startTime;
        private final List<Fragment> fragments;

        public ShatterInstance(int entityId, Vec3 center, float radius, int color) {
            this.entityId = entityId;
            this.center = center;
            this.color = color;
            this.startTime = System.currentTimeMillis();
            this.fragments = generateFragments(radius);
        }

        /**
         * Generates fragments using golden ratio spiral for even distribution.
         */
        private List<Fragment> generateFragments(float radius) {
            List<Fragment> frags = new ArrayList<>();
            Random rand = new Random();

            for (int i = 0; i < FRAGMENT_COUNT; i++) {
                // Fibonacci sphere distribution
                double y = 1.0 - (i / (double)(FRAGMENT_COUNT - 1)) * 2.0;
                double radiusAtY = Math.sqrt(1.0 - y * y);
                double theta = PHI * i * 2.0 * Math.PI;

                double x = Math.cos(theta) * radiusAtY;
                double z = Math.sin(theta) * radiusAtY;

                Vec3 pos = new Vec3(x * radius, y * radius, z * radius);
                Vec3 velocity = pos.normalize().scale(0.5 + rand.nextDouble() * 0.5);

                // Add some randomness
                velocity = velocity.add(
                    (rand.nextDouble() - 0.5) * 0.2,
                    rand.nextDouble() * 0.3,
                    (rand.nextDouble() - 0.5) * 0.2
                );

                float size = 0.1f + rand.nextFloat() * 0.15f;
                float rotation = rand.nextFloat() * 360f;
                float rotationSpeed = (rand.nextFloat() - 0.5f) * 20f;

                frags.add(new Fragment(pos, velocity, size, rotation, rotationSpeed));
            }

            return frags;
        }

        public void tick() {
            for (Fragment frag : fragments) {
                frag.tick();
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > SHATTER_DURATION_MS;
        }

        public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                          Vec3 cameraPos, float partialTick) {
            float progress = (System.currentTimeMillis() - startTime) / (float) SHATTER_DURATION_MS;
            float alpha = 1.0f - progress; // Fade out

            int a = (int)(alpha * ((color >> 24) & 0xFF));
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int fadedColor = (a << 24) | (r << 16) | (g << 8) | b;

            VertexConsumer consumer = bufferSource.getBuffer(
                net.minecraft.client.renderer.RenderType.translucent()
            );

            for (Fragment frag : fragments) {
                frag.render(poseStack, consumer, center, cameraPos, fadedColor, partialTick);
            }
        }
    }

    /**
     * A single fragment of the shattered shield.
     */
    private static class Fragment {
        private Vec3 position;
        private Vec3 velocity;
        private final float size;
        private float rotation;
        private final float rotationSpeed;

        private static final double GRAVITY = -0.05;
        private static final double DRAG = 0.98;

        public Fragment(Vec3 pos, Vec3 vel, float size, float rotation, float rotationSpeed) {
            this.position = pos;
            this.velocity = vel;
            this.size = size;
            this.rotation = rotation;
            this.rotationSpeed = rotationSpeed;
        }

        public void tick() {
            // Apply gravity
            velocity = velocity.add(0, GRAVITY, 0);
            // Apply drag
            velocity = velocity.scale(DRAG);
            // Update position
            position = position.add(velocity);
            // Update rotation
            rotation += rotationSpeed;
        }

        public void render(PoseStack poseStack, VertexConsumer consumer,
                          Vec3 center, Vec3 cameraPos, int color, float partialTick) {
            Vec3 worldPos = center.add(position);

            // Billboard rotation (face camera)
            poseStack.pushPose();
            poseStack.translate(
                worldPos.x - cameraPos.x,
                worldPos.y - cameraPos.y,
                worldPos.z - cameraPos.z
            );

            // Rotate to face camera
            Vec3 toCamera = cameraPos.subtract(worldPos).normalize();
            float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
            float pitch = (float) Math.asin(toCamera.y);

            poseStack.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));

            // Draw quad
            float hs = size / 2;
            var matrix = poseStack.last().pose();

            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            consumer.addVertex(matrix, -hs, -hs, 0).setColor(r, g, b, a).setNormal(0, 0, 1);
            consumer.addVertex(matrix,  hs, -hs, 0).setColor(r, g, b, a).setNormal(0, 0, 1);
            consumer.addVertex(matrix,  hs,  hs, 0).setColor(r, g, b, a).setNormal(0, 0, 1);
            consumer.addVertex(matrix, -hs,  hs, 0).setColor(r, g, b, a).setNormal(0, 0, 1);

            poseStack.popPose();
        }
    }
}
```

## 3. Integrazione con DamageHandler

```java
// In DamageHandler.java - applyShieldBlock()

public static void applyShieldBlock(Player player, ArmorStats stats,
                                    DamageSource source, float damage) {
    // ... existing blocking logic ...

    // Trigger impact flash (client-side)
    if (player.level().isClientSide) {
        Vec3 hitPoint = calculateHitPoint(player, source);
        Vec3 localHitPoint = hitPoint.subtract(player.position());
        ShieldImpactManager.registerImpact(player.getId(), localHitPoint, damage);
    }

    // Check if shield breaks
    float shieldHealth = getShieldHealth(player); // New method needed
    if (shieldHealth <= 0) {
        // Trigger shatter effect (client-side)
        if (player.level().isClientSide) {
            float radius = player.getBbWidth() * 0.8f + 0.5f;
            ShieldShatterEffect.trigger(
                player.getId(),
                player.position().add(0, player.getBbHeight() / 2, 0),
                radius,
                stats.shieldColor | 0xCC000000
            );
        }
    }
}
```

## 4. Event Registration

```java
// In DevMod.java o ClientSetup.java

@SubscribeEvent
public static void onClientTick(ClientTickEvent.Pre event) {
    ShieldImpactManager.tick();
    ShieldShatterEffect.tick();
}

@SubscribeEvent
public static void onRenderLevel(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
        Minecraft mc = Minecraft.getInstance();
        ShieldShatterEffect.render(
            event.getPoseStack(),
            mc.renderBuffers().bufferSource(),
            event.getCamera().getPosition(),
            event.getPartialTick().getGameTimeDeltaPartialTick()
        );
    }
}
```

## Performance Considerations

| Aspect | Strategy |
|--------|----------|
| Fragment count | Max 24 per shatter (tunable) |
| Active effects | Max 4 shatter effects simultaneously |
| Impact flashes | Max 8 per entity, auto-cleanup |
| Render distance | Skip shatter effects > 32 blocks away |

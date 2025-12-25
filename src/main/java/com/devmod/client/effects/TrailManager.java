package com.devmod.client.effects;

import com.devmod.effects.TrailEffect;

import com.devmod.config.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

/**
 * Manages projectile trail effects.
 * Tracks projectiles and renders glowing trails behind them.
 */
public class TrailManager {

    public static final TrailManager INSTANCE = new TrailManager();

    private final Map<Integer, TrailEffect> activeTrails = new ConcurrentHashMap<>();
    private final Set<Class<? extends Entity>> trackedTypes = new HashSet<>();

    private TrailManager() {
        // Register default tracked entity types
        registerDefaults();
    }

    /**
     * Checks if trails are enabled (from config).
     */
    public boolean isEnabled() {
        try {
            return Config.PROJECTILE_TRAILS_ENABLED.get();
        } catch (Exception e) {
            return false; // Default disabled if config not loaded
        }
    }

    /**
     * Gets the global intensity multiplier (from config).
     */
    public float getGlobalIntensity() {
        try {
            return Config.PROJECTILE_TRAILS_INTENSITY.get().floatValue();
        } catch (Exception e) {
            return 1.0f; // Default intensity if config not loaded
        }
    }

    private void registerDefaults() {
        // Arrows and bolts
        trackedTypes.add(AbstractArrow.class);

        // Thrown items
        trackedTypes.add(ThrownPotion.class);
        trackedTypes.add(ThrownTrident.class);
        trackedTypes.add(ThrowableProjectile.class);

        // Special projectiles
        trackedTypes.add(FireworkRocketEntity.class);
        trackedTypes.add(ShulkerBullet.class);

        // Fireballs
        trackedTypes.add(WitherSkull.class);
        trackedTypes.add(DragonFireball.class);
        trackedTypes.add(LargeFireball.class);
        trackedTypes.add(SmallFireball.class);

        // Experience orbs (optional, looks cool)
        trackedTypes.add(ExperienceOrb.class);

        // Eye of Ender (locating strongholds)
        trackedTypes.add(EyeOfEnder.class);
    }

    /**
     * Updates all trails. Called each tick.
     */
    public void tick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            activeTrails.clear();
            return;
        }
        var level = Objects.requireNonNull(mc.level);

        // Update existing trails
        Iterator<Map.Entry<Integer, TrailEffect>> iterator = activeTrails.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrailEffect> entry = iterator.next();
            TrailEffect trail = entry.getValue();

            trail.update();

            if (trail.isFinished()) {
                iterator.remove();
                continue;
            }

            // Try to update position from entity
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive()) {
                // Only add point if entity has moved
                Vec3 pos = Objects.requireNonNull(entity.position().add(0, entity.getBbHeight() * 0.5, 0));
                trail.addPoint(pos);
            }
        }

        // Scan for new projectiles to track
        for (Entity entity : level.entitiesForRendering()) {
            if (shouldTrack(entity) && !activeTrails.containsKey(entity.getId())) {
                TrailEffect trail = createTrailForEntity(entity);
                if (trail != null) {
                    activeTrails.put(entity.getId(), trail);
                }
            }
        }
    }

    /**
     * Renders all active trails.
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (!isEnabled() || activeTrails.isEmpty()) return;

        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        PoseStack.Pose pose = Objects.requireNonNull(poseStack.last());
        Matrix4f matrix = Objects.requireNonNull(pose.pose());

        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        PoseStack.Pose safePose = Objects.requireNonNull(pose);

        for (TrailEffect trail : activeTrails.values()) {
            renderTrail(consumer, safeMatrix, safePose, trail, cameraPos);
        }
    }

    private void renderTrail(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                             TrailEffect trail, Vec3 cameraPos) {
        Vec3 cam = Objects.requireNonNull(cameraPos);
        var points = trail.getPoints();
        if (points.size() < 2) return;

        int color = trail.getColor();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float globalIntensity = getGlobalIntensity();

        // Render line segments between consecutive points
        for (int i = 0; i < points.size() - 1; i++) {
            TrailEffect.TrailPoint p1 = points.get(i);
            TrailEffect.TrailPoint p2 = points.get(i + 1);

            float alpha1 = trail.getPointAlpha(p1) * globalIntensity;
            float alpha2 = trail.getPointAlpha(p2) * globalIntensity;

            if (alpha1 < 0.01f && alpha2 < 0.01f) continue;

            // Convert to camera-relative coordinates
            Vec3 p1Pos = Objects.requireNonNull(p1.position);
            Vec3 p2Pos = Objects.requireNonNull(p2.position);
            Vec3 rel1 = Objects.requireNonNull(p1Pos.subtract(cam));
            Vec3 rel2 = Objects.requireNonNull(p2Pos.subtract(cam));

            // Draw line segment
            consumer.addVertex(Objects.requireNonNull(matrix), (float) rel1.x, (float) rel1.y, (float) rel1.z)
                .setColor(r, g, b, (int) (alpha1 * 255))
                .setNormal(Objects.requireNonNull(pose), 0, 1, 0);

            consumer.addVertex(Objects.requireNonNull(matrix), (float) rel2.x, (float) rel2.y, (float) rel2.z)
                .setColor(r, g, b, (int) (alpha2 * 255))
                .setNormal(Objects.requireNonNull(pose), 0, 1, 0);
        }
    }

    private boolean shouldTrack(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;

        // Special case: Track players using Elytra
        if (entity instanceof Player player && player.isFallFlying()) {
            return player.getDeltaMovement().lengthSqr() > 0.1; // Only when moving fast
        }

        // Check if entity type is registered for tracking
        for (Class<? extends Entity> type : trackedTypes) {
            if (type.isInstance(entity)) {
                // Additional checks for specific types
                if (entity instanceof AbstractArrow arrow) {
                    // Only track arrows that are in flight (moving)
                    return arrow.getDeltaMovement().lengthSqr() > 0.01;
                }
                if (entity instanceof ItemEntity) {
                    // Don't track dropped items (too many)
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private TrailEffect createTrailForEntity(Entity entity) {
        // Determine trail parameters based on entity type
        int color;
        float width;
        int maxPoints;
        float fadeTime;

        if (entity instanceof AbstractArrow) {
            color = 0xFFFFAA00;  // Orange for arrows
            width = 1.5f;
            maxPoints = 30;
            fadeTime = 20f;
        } else if (entity instanceof ThrownPotion) {
            color = 0xFF9900FF;  // Purple for potions
            width = 2f;
            maxPoints = 25;
            fadeTime = 15f;
        } else if (entity instanceof ThrownTrident) {
            color = 0xFF00FFFF;  // Cyan for tridents
            width = 2f;
            maxPoints = 40;
            fadeTime = 25f;
        } else if (entity instanceof FireworkRocketEntity) {
            color = 0xFFFF5555;  // Red for fireworks
            width = 2.5f;
            maxPoints = 50;
            fadeTime = 30f;
        } else if (entity instanceof WitherSkull) {
            color = 0xFF333333;  // Dark gray for wither skulls
            width = 3f;
            maxPoints = 35;
            fadeTime = 20f;
        } else if (entity instanceof DragonFireball || entity instanceof LargeFireball) {
            color = 0xFFFF4400;  // Orange-red for fireballs
            width = 3f;
            maxPoints = 40;
            fadeTime = 25f;
        } else if (entity instanceof SmallFireball) {
            color = 0xFFFF6600;  // Orange for blaze fireballs
            width = 2f;
            maxPoints = 25;
            fadeTime = 15f;
        } else if (entity instanceof ShulkerBullet) {
            color = 0xFFFF00FF;  // Magenta for shulker bullets
            width = 2f;
            maxPoints = 60;
            fadeTime = 40f;
        } else if (entity instanceof ExperienceOrb) {
            color = 0xFF55FF55;  // Green for XP orbs
            width = 1f;
            maxPoints = 15;
            fadeTime = 10f;
        } else if (entity instanceof EyeOfEnder) {
            color = 0xFF00FF88;  // Ender green for Eyes of Ender
            width = 2.5f;
            maxPoints = 60;
            fadeTime = 40f;
        } else if (entity instanceof Player player && player.isFallFlying()) {
            color = 0xFFAADDFF;  // Light blue for Elytra flight
            width = 2f;
            maxPoints = 80;
            fadeTime = 30f;
        } else {
            // Default trail
            color = 0xFFFFFFFF;
            width = 1.5f;
            maxPoints = 25;
            fadeTime = 15f;
        }

        return new TrailEffect(entity.getId(), color, width, maxPoints, fadeTime);
    }

    // ==================== Utility Methods ====================

    public int getActiveCount() {
        return activeTrails.size();
    }

    /**
     * Registers an entity type to be tracked for trails.
     */
    public void registerEntityType(Class<? extends Entity> type) {
        trackedTypes.add(type);
    }

    /**
     * Unregisters an entity type from trail tracking.
     */
    public void unregisterEntityType(Class<? extends Entity> type) {
        trackedTypes.remove(type);
    }

    /**
     * Clears all active trails.
     */
    public void clear() {
        activeTrails.clear();
    }

    /**
     * Manually adds a trail for a specific entity.
     * Useful for custom projectiles from other mods.
     */
    public void addTrailForEntity(Entity entity, int color) {
        if (entity == null || activeTrails.containsKey(entity.getId())) return;

        TrailEffect trail = new TrailEffect(entity.getId(), color, 2f, 30, 20f);
        activeTrails.put(entity.getId(), trail);
    }
}

package com.devmod.client.panels.tracking;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
public class EntityTracker {

    private final WeakReference<Entity> entityRef;
    private final int entityId;

    // Smoothed position for visual stability
    private Vec3 smoothedPosition;
    private Vec3 lastKnownPosition;

    // Smoothing factor (0-1, higher = more responsive)
    private static final float SMOOTHING = 0.3f;

    // Offset from entity center (usually above head)
    private Vec3 offset = new Vec3(0, 0.5, 0);

    /**
     * Create a tracker for the given entity.
     */
    public EntityTracker(Entity entity) {
        this.entityRef = new WeakReference<>(entity);
        this.entityId = entity.getId();
        this.smoothedPosition = calculateTargetPosition(entity);
        this.lastKnownPosition = smoothedPosition;
    }

    /**
     * Update the tracker. Should be called every tick.
     */
    public void tick() {
        Entity entity = entityRef.get();
        if (entity == null || !entity.isAlive()) {
            return;
        }

        Vec3 targetPos = calculateTargetPosition(entity);

        // Smooth interpolation
        smoothedPosition = new Vec3(
            lerp(smoothedPosition.x, targetPos.x, SMOOTHING),
            lerp(smoothedPosition.y, targetPos.y, SMOOTHING),
            lerp(smoothedPosition.z, targetPos.z, SMOOTHING)
        );

        lastKnownPosition = smoothedPosition;
    }

    /**
     * Calculate the target position for the panel.
     * Places it above the entity's head.
     */
    private Vec3 calculateTargetPosition(Entity entity) {
        double height = entity.getBbHeight();
        if (entity instanceof LivingEntity living) {
            height = living.getBbHeight();
        }

        return entity.position().add(offset.x, height + offset.y, offset.z);
    }

    /**
     * Check if the tracked entity is still valid.
     */
    public boolean isValid() {
        Entity entity = entityRef.get();
        return entity != null && entity.isAlive();
    }

    /**
     * Get the smoothed position for rendering.
     */
    public Vec3 getSmoothedPosition() {
        return smoothedPosition;
    }

    /**
     * Get the last known position (used when entity becomes invalid).
     */
    public Vec3 getLastKnownPosition() {
        return lastKnownPosition;
    }

    /**
     * Get the tracked entity, or null if it's been garbage collected or removed.
     */
    @Nullable
    public Entity getEntity() {
        return entityRef.get();
    }

    /**
     * Get the entity ID (valid even if entity is gone).
     */
    public int getEntityId() {
        return entityId;
    }

    /**
     * Alias for getEntityId() for backwards compatibility.
     */
    public int getTargetId() {
        return entityId;
    }

    /**
     * Set the offset from entity center.
     */
    public void setOffset(Vec3 offset) {
        this.offset = offset;
    }

    /**
     * Get the current offset.
     */
    public Vec3 getOffset() {
        return offset;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}

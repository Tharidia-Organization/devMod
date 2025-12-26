package com.devmod.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.world.phys.Vec3;

/**
 * Represents a trail effect that follows an entity or projectile.
 * Stores position history and renders as a glowing line.
 */
public class TrailEffect {

    private final int entityId;
    private final List<TrailPoint> points = new ArrayList<>();

    // Trail configuration
    private final int maxPoints;
    private final int color;
    private final float width;
    private final float fadeTime;  // Ticks for points to fade out

    private boolean finished = false;
    private long lastUpdateTime = 0;
    private static final long STALE_THRESHOLD_MS = 2000; // Remove if no update for 2 seconds

    public TrailEffect(int entityId, int color, float width, int maxPoints, float fadeTime) {
        this.entityId = entityId;
        this.color = color;
        this.width = width;
        this.maxPoints = maxPoints;
        this.fadeTime = fadeTime;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Adds a new point to the trail.
     */
    public void addPoint(@Nonnull Vec3 position) {
        Vec3 safePosition = Objects.requireNonNull(position);
        lastUpdateTime = System.currentTimeMillis();

        // Add new point
        points.add(new TrailPoint(safePosition, System.currentTimeMillis()));

        // Remove oldest points if exceeding max
        while (points.size() > maxPoints) {
            points.remove(0);
        }
    }

    /**
     * Updates the trail, removing faded points.
     */
    public void update() {
        long now = System.currentTimeMillis();

        // Check if trail is stale (entity no longer exists or moved)
        if (now - lastUpdateTime > STALE_THRESHOLD_MS) {
            finished = true;
            return;
        }

        // Remove fully faded points
        long fadeMs = (long) (fadeTime * 50); // Convert ticks to ms
        points.removeIf(p -> (now - p.timestamp) > fadeMs);

        // Mark finished if no points left
        if (points.isEmpty() && now - lastUpdateTime > 500) {
            finished = true;
        }
    }

    /**
     * Gets all trail points for rendering.
     */
    public List<TrailPoint> getPoints() {
        return points;
    }

    /**
     * Gets the alpha for a point based on its age.
     */
    public float getPointAlpha(TrailPoint point) {
        long age = System.currentTimeMillis() - point.timestamp;
        long fadeMs = (long) (fadeTime * 50);
        if (age >= fadeMs) return 0f;
        return 1f - (float) age / fadeMs;
    }

    public int getEntityId() {
        return entityId;
    }

    public int getColor() {
        return color;
    }

    public float getWidth() {
        return width;
    }

    public boolean isFinished() {
        return finished;
    }

    public void markFinished() {
        this.finished = true;
    }

    /**
     * Represents a single point in the trail.
     */
    public static class TrailPoint {
        @Nonnull public final Vec3 position;
        public final long timestamp;

        public TrailPoint(@Nonnull Vec3 position, long timestamp) {
            this.position = Objects.requireNonNull(position);
            this.timestamp = timestamp;
        }
    }
}

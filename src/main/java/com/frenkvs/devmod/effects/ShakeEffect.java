package com.frenkvs.devmod.effects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Represents a screen shake effect with configurable parameters.
 * Inspired by Perception mod but implemented independently.
 *
 * Supports:
 * - Rotation shake (pitch/yaw/roll)
 * - Position offset shake
 * - FOV pulse
 * - Distance-based attenuation
 * - Fade in/out with easing
 */
public class ShakeEffect {

    private final UUID uuid;
    private final ShakeSource source;

    // Timing
    private final int duration;        // Total duration in ticks
    private final int fadeInTicks;     // Fade in duration
    private final int fadeOutTicks;    // Fade out duration
    private int elapsedTicks = 0;

    // Rotation parameters
    private final float rotationAmplitude;  // Max rotation in degrees
    private final float rotationFrequency;  // Oscillations per second

    // Offset parameters
    private final float offsetAmplitude;    // Max offset in blocks
    private final float offsetFrequency;

    // FOV parameters
    private final float fovAmplitude;       // FOV change amount
    private final float fovFrequency;

    // Distance attenuation
    private final float maxDistance;        // Max effect distance
    private final EasingFunction distanceEasing;

    // Current interpolated values (updated each tick)
    private Vector3f lastRotation = new Vector3f();
    private Vector3f currentRotation = new Vector3f();
    private Vector3f lastOffset = new Vector3f();
    private Vector3f currentOffset = new Vector3f();
    private float lastFov = 0;
    private float currentFov = 0;

    private boolean finished = false;

    private ShakeEffect(Builder builder) {
        this.uuid = UUID.randomUUID();
        this.source = builder.source;
        this.duration = builder.duration;
        this.fadeInTicks = builder.fadeInTicks;
        this.fadeOutTicks = builder.fadeOutTicks;
        this.rotationAmplitude = builder.rotationAmplitude;
        this.rotationFrequency = builder.rotationFrequency;
        this.offsetAmplitude = builder.offsetAmplitude;
        this.offsetFrequency = builder.offsetFrequency;
        this.fovAmplitude = builder.fovAmplitude;
        this.fovFrequency = builder.fovFrequency;
        this.maxDistance = builder.maxDistance;
        this.distanceEasing = builder.distanceEasing;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Updates the shake effect. Called once per tick.
     */
    public void update(Player player) {
        if (finished) return;

        elapsedTicks++;

        if (elapsedTicks >= duration) {
            finished = true;
            return;
        }

        // Store last values for interpolation
        lastRotation.set(currentRotation);
        lastOffset.set(currentOffset);
        lastFov = currentFov;

        // Calculate intensity with fade in/out
        float intensity = calculateIntensity();

        // Calculate distance attenuation
        float distanceMult = calculateDistanceMultiplier(player);
        float finalIntensity = intensity * distanceMult;

        if (finalIntensity < 0.001f) {
            currentRotation.set(0, 0, 0);
            currentOffset.set(0, 0, 0);
            currentFov = 0;
            return;
        }

        // Calculate time-based oscillation
        float time = elapsedTicks / 20f; // Convert to seconds

        // Rotation shake (use different phase offsets for each axis)
        if (rotationAmplitude > 0) {
            float rotX = (float) Math.sin(time * rotationFrequency * Math.PI * 2) * rotationAmplitude * finalIntensity;
            float rotY = (float) Math.sin(time * rotationFrequency * Math.PI * 2 + 1.3) * rotationAmplitude * finalIntensity * 0.7f;
            float rotZ = (float) Math.sin(time * rotationFrequency * Math.PI * 2 + 2.7) * rotationAmplitude * finalIntensity * 0.5f;
            currentRotation.set(rotX, rotY, rotZ);
        }

        // Offset shake
        if (offsetAmplitude > 0) {
            float offX = (float) Math.sin(time * offsetFrequency * Math.PI * 2 + 0.5) * offsetAmplitude * finalIntensity;
            float offY = (float) Math.sin(time * offsetFrequency * Math.PI * 2 + 1.8) * offsetAmplitude * finalIntensity;
            float offZ = (float) Math.sin(time * offsetFrequency * Math.PI * 2 + 3.1) * offsetAmplitude * finalIntensity;
            currentOffset.set(offX, offY, offZ);
        }

        // FOV pulse
        if (fovAmplitude > 0) {
            currentFov = (float) Math.sin(time * fovFrequency * Math.PI * 2) * fovAmplitude * finalIntensity;
        }
    }

    /**
     * Gets interpolated rotation for smooth rendering.
     */
    public Vector3f getRotation(float partialTicks) {
        return new Vector3f(
            lerp(lastRotation.x, currentRotation.x, partialTicks),
            lerp(lastRotation.y, currentRotation.y, partialTicks),
            lerp(lastRotation.z, currentRotation.z, partialTicks)
        );
    }

    /**
     * Gets interpolated offset for smooth rendering.
     */
    public Vector3f getOffset(float partialTicks) {
        return new Vector3f(
            lerp(lastOffset.x, currentOffset.x, partialTicks),
            lerp(lastOffset.y, currentOffset.y, partialTicks),
            lerp(lastOffset.z, currentOffset.z, partialTicks)
        );
    }

    /**
     * Gets interpolated FOV change for smooth rendering.
     */
    public float getFovChange(float partialTicks) {
        return lerp(lastFov, currentFov, partialTicks);
    }

    private float calculateIntensity() {
        // Fade in
        if (elapsedTicks < fadeInTicks) {
            return easeOutQuad((float) elapsedTicks / fadeInTicks);
        }

        // Fade out
        int fadeOutStart = duration - fadeOutTicks;
        if (elapsedTicks > fadeOutStart) {
            float fadeProgress = (float) (elapsedTicks - fadeOutStart) / fadeOutTicks;
            return 1f - easeInQuad(fadeProgress);
        }

        // Full intensity
        return 1f;
    }

    private float calculateDistanceMultiplier(Player player) {
        if (maxDistance <= 0 || source == null) return 1f;

        Vec3 sourcePos = source.getPosition();
        if (sourcePos == null) return 1f;

        double distance = player.position().distanceTo(sourcePos);
        if (distance >= maxDistance) return 0f;
        if (distance <= 0) return 1f;

        float normalizedDist = (float) (distance / maxDistance);
        return 1f - distanceEasing.apply(normalizedDist);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ShakeSource source;
        private int duration = 20;           // 1 second default
        private int fadeInTicks = 2;
        private int fadeOutTicks = 5;
        private float rotationAmplitude = 2f; // degrees
        private float rotationFrequency = 8f; // Hz
        private float offsetAmplitude = 0f;
        private float offsetFrequency = 10f;
        private float fovAmplitude = 0f;
        private float fovFrequency = 5f;
        private float maxDistance = 32f;
        private EasingFunction distanceEasing = EasingFunction.LINEAR;

        public Builder source(Entity entity) {
            this.source = new EntitySource(entity);
            return this;
        }

        public Builder source(Vec3 position) {
            this.source = new PositionSource(position);
            return this;
        }

        public Builder source(Supplier<Vec3> positionSupplier) {
            this.source = new DynamicSource(positionSupplier);
            return this;
        }

        public Builder duration(int ticks) {
            this.duration = ticks;
            return this;
        }

        public Builder fadeIn(int ticks) {
            this.fadeInTicks = ticks;
            return this;
        }

        public Builder fadeOut(int ticks) {
            this.fadeOutTicks = ticks;
            return this;
        }

        public Builder rotation(float amplitude, float frequency) {
            this.rotationAmplitude = amplitude;
            this.rotationFrequency = frequency;
            return this;
        }

        public Builder offset(float amplitude, float frequency) {
            this.offsetAmplitude = amplitude;
            this.offsetFrequency = frequency;
            return this;
        }

        public Builder fov(float amplitude, float frequency) {
            this.fovAmplitude = amplitude;
            this.fovFrequency = frequency;
            return this;
        }

        public Builder maxDistance(float distance) {
            this.maxDistance = distance;
            return this;
        }

        public Builder distanceEasing(EasingFunction easing) {
            this.distanceEasing = easing;
            return this;
        }

        public ShakeEffect build() {
            return new ShakeEffect(this);
        }
    }

    // ==================== Source Types ====================

    public interface ShakeSource {
        Vec3 getPosition();
    }

    private static class EntitySource implements ShakeSource {
        private final Entity entity;

        EntitySource(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Vec3 getPosition() {
            return entity.isAlive() ? entity.position() : null;
        }
    }

    private static class PositionSource implements ShakeSource {
        private final Vec3 position;

        PositionSource(Vec3 position) {
            this.position = position;
        }

        @Override
        public Vec3 getPosition() {
            return position;
        }
    }

    private static class DynamicSource implements ShakeSource {
        private final Supplier<Vec3> supplier;

        DynamicSource(Supplier<Vec3> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Vec3 getPosition() {
            return supplier.get();
        }
    }

    // ==================== Easing Functions ====================

    public enum EasingFunction {
        LINEAR(t -> t),
        EASE_IN_QUAD(t -> t * t),
        EASE_OUT_QUAD(t -> 1f - (1f - t) * (1f - t)),
        EASE_IN_OUT_QUAD(t -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f),
        EASE_IN_CUBIC(t -> t * t * t),
        EASE_OUT_CUBIC(t -> 1f - (float) Math.pow(1f - t, 3)),
        EASE_IN_EXPO(t -> t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10)),
        EASE_OUT_EXPO(t -> t == 1 ? 1 : 1f - (float) Math.pow(2, -10 * t));

        private final java.util.function.Function<Float, Float> function;

        EasingFunction(java.util.function.Function<Float, Float> function) {
            this.function = function;
        }

        public float apply(float t) {
            return function.apply(Math.max(0, Math.min(1, t)));
        }
    }
}

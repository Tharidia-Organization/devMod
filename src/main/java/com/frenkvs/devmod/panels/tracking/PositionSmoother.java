package com.frenkvs.devmod.panels.tracking;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Utility for smoothing 3D positions with various algorithms.
 *
 * Supports:
 * - Linear interpolation (lerp)
 * - Exponential smoothing
 * - Spring physics
 * - Critically damped spring (no oscillation)
 */
public class PositionSmoother {

    // === Current State ===
    @Nonnull
    private Vec3 currentPosition;
    @Nonnull
    private Vec3 velocity = Objects.requireNonNull(Vec3.ZERO, "Vec3.ZERO");

    // === Configuration ===
    private SmoothingMode mode = SmoothingMode.EXPONENTIAL;
    private float smoothingFactor = 0.15f;
    private float springStiffness = 100f;
    private float springDamping = 10f;

    /**
     * Available smoothing algorithms.
     */
    public enum SmoothingMode {
        /** Simple linear interpolation */
        LINEAR,
        /** Exponential smoothing (more natural) */
        EXPONENTIAL,
        /** Spring physics (may oscillate) */
        SPRING,
        /** Critically damped spring (smooth without oscillations) */
        CRITICALLY_DAMPED
    }

    public PositionSmoother(@Nonnull Vec3 initialPosition) {
        this.currentPosition = Objects.requireNonNull(initialPosition, "initialPosition");
    }

    public PositionSmoother(@Nonnull Vec3 initialPosition, @Nonnull SmoothingMode mode) {
        this.currentPosition = Objects.requireNonNull(initialPosition, "initialPosition");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * Updates position towards the target.
     *
     * @param target Target position
     * @param deltaTime Elapsed time in seconds (typically 0.05 per tick)
     * @return New smoothed position
     */
    @Nonnull
    public Vec3 update(@Nonnull Vec3 target, float deltaTime) {
        currentPosition = Objects.requireNonNull(switch (mode) {
            case LINEAR -> updateLinear(target, deltaTime);
            case EXPONENTIAL -> updateExponential(target, deltaTime);
            case SPRING -> updateSpring(target, deltaTime);
            case CRITICALLY_DAMPED -> updateCriticallyDamped(target, deltaTime);
        });

        return currentPosition;
    }

    /**
     * Linear interpolation: moves by a fixed fraction towards the target.
     */
    @Nonnull
    private Vec3 updateLinear(@Nonnull Vec3 target, float deltaTime) {
        Vec3 diff = Objects.requireNonNull(target.subtract(currentPosition));
        float factor = Math.min(1.0f, smoothingFactor * deltaTime * 20);
        return Objects.requireNonNull(currentPosition.add(Objects.requireNonNull(diff.scale(factor))));
    }

    /**
     * Exponential smoothing: faster when far away, slows down when approaching.
     */
    @Nonnull
    private Vec3 updateExponential(@Nonnull Vec3 target, float deltaTime) {
        // Formula: pos = pos + (target - pos) * (1 - e^(-factor * dt))
        float factor = (float) (1 - Math.exp(-smoothingFactor * 20 * deltaTime));
        Vec3 diff = Objects.requireNonNull(target.subtract(currentPosition));
        return Objects.requireNonNull(currentPosition.add(Objects.requireNonNull(diff.scale(factor))));
    }

    /**
     * Spring physics: may oscillate around the target.
     */
    @Nonnull
    private Vec3 updateSpring(@Nonnull Vec3 target, float deltaTime) {
        // F = -k * x - d * v (spring force + damping)
        Vec3 displacement = Objects.requireNonNull(currentPosition.subtract(target));
        Vec3 springForce = Objects.requireNonNull(displacement.scale(-springStiffness));
        Vec3 dampingForce = Objects.requireNonNull(velocity.scale(-springDamping));
        Vec3 acceleration = Objects.requireNonNull(springForce.add(dampingForce));

        // Integrazione semi-implicita
        velocity = Objects.requireNonNull(velocity.add(Objects.requireNonNull(acceleration.scale(deltaTime))));
        return Objects.requireNonNull(currentPosition.add(Objects.requireNonNull(velocity.scale(deltaTime))));
    }

    /**
     * Critically damped spring: smooth and fast without oscillations.
     * Used for UI where you don't want bouncing.
     */
    @Nonnull
    private Vec3 updateCriticallyDamped(@Nonnull Vec3 target, float deltaTime) {
        // Damping critico: d = 2 * sqrt(k)
        float omega = (float) Math.sqrt(springStiffness);
        float criticalDamping = 2 * omega;

        Vec3 displacement = Objects.requireNonNull(currentPosition.subtract(target));
        Vec3 springForce = Objects.requireNonNull(displacement.scale(-springStiffness));
        Vec3 dampingForce = Objects.requireNonNull(velocity.scale(-criticalDamping));
        Vec3 acceleration = Objects.requireNonNull(springForce.add(dampingForce));

        velocity = Objects.requireNonNull(velocity.add(Objects.requireNonNull(acceleration.scale(deltaTime))));
        return Objects.requireNonNull(currentPosition.add(Objects.requireNonNull(velocity.scale(deltaTime))));
    }

    /**
     * Sets position immediately (without smoothing).
     */
    public void setPosition(@Nonnull Vec3 position) {
        this.currentPosition = Objects.requireNonNull(position, "position");
        this.velocity = Objects.requireNonNull(Vec3.ZERO, "Vec3.ZERO");
    }

    /**
     * Resets velocity (stops movement).
     */
    public void resetVelocity() {
        this.velocity = Objects.requireNonNull(Vec3.ZERO, "Vec3.ZERO");
    }

    // === Getters/Setters ===

    public Vec3 getCurrentPosition() {
        return currentPosition;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public SmoothingMode getMode() {
        return mode;
    }

    public void setMode(SmoothingMode mode) {
        this.mode = mode;
    }

    public float getSmoothingFactor() {
        return smoothingFactor;
    }

    public void setSmoothingFactor(float factor) {
        this.smoothingFactor = Math.max(0.01f, Math.min(1.0f, factor));
    }

    public void setSpringStiffness(float stiffness) {
        this.springStiffness = Math.max(1f, stiffness);
    }

    public void setSpringDamping(float damping) {
        this.springDamping = Math.max(0f, damping);
    }

    /**
     * Configures for standard UI movement (smooth, no bounce).
     */
    public void configureForUI() {
        this.mode = SmoothingMode.CRITICALLY_DAMPED;
        this.springStiffness = 150f;
    }

    /**
     * Configures for entity tracking (follows smoothly).
     */
    public void configureForEntityTracking() {
        this.mode = SmoothingMode.EXPONENTIAL;
        this.smoothingFactor = 0.12f;
    }

    /**
     * Configures for combat effects (reactive).
     */
    public void configureForCombat() {
        this.mode = SmoothingMode.EXPONENTIAL;
        this.smoothingFactor = 0.25f;
    }

    /**
     * Calculates distance from target.
     */
    public double distanceFromTarget(@Nonnull Vec3 target) {
        return currentPosition.distanceTo(target);
    }

    /**
     * Checks if position is "settled" (close to target and nearly stopped).
     */
    public boolean isSettled(@Nonnull Vec3 target, double positionThreshold, double velocityThreshold) {
        return currentPosition.distanceTo(target) < positionThreshold
            && velocity.length() < velocityThreshold;
    }
}

package com.frenkvs.devmod.panels.tracking;

import net.minecraft.world.phys.Vec3;

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
    private Vec3 currentPosition;
    private Vec3 velocity = Vec3.ZERO;

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

    public PositionSmoother(Vec3 initialPosition) {
        this.currentPosition = initialPosition;
    }

    public PositionSmoother(Vec3 initialPosition, SmoothingMode mode) {
        this.currentPosition = initialPosition;
        this.mode = mode;
    }

    /**
     * Updates position towards the target.
     *
     * @param target Target position
     * @param deltaTime Elapsed time in seconds (typically 0.05 per tick)
     * @return New smoothed position
     */
    public Vec3 update(Vec3 target, float deltaTime) {
        currentPosition = switch (mode) {
            case LINEAR -> updateLinear(target, deltaTime);
            case EXPONENTIAL -> updateExponential(target, deltaTime);
            case SPRING -> updateSpring(target, deltaTime);
            case CRITICALLY_DAMPED -> updateCriticallyDamped(target, deltaTime);
        };

        return currentPosition;
    }

    /**
     * Linear interpolation: moves by a fixed fraction towards the target.
     */
    private Vec3 updateLinear(Vec3 target, float deltaTime) {
        Vec3 diff = target.subtract(currentPosition);
        float factor = Math.min(1.0f, smoothingFactor * deltaTime * 20);
        return currentPosition.add(diff.scale(factor));
    }

    /**
     * Exponential smoothing: faster when far away, slows down when approaching.
     */
    private Vec3 updateExponential(Vec3 target, float deltaTime) {
        // Formula: pos = pos + (target - pos) * (1 - e^(-factor * dt))
        float factor = (float) (1 - Math.exp(-smoothingFactor * 20 * deltaTime));
        Vec3 diff = target.subtract(currentPosition);
        return currentPosition.add(diff.scale(factor));
    }

    /**
     * Spring physics: may oscillate around the target.
     */
    private Vec3 updateSpring(Vec3 target, float deltaTime) {
        // F = -k * x - d * v (spring force + damping)
        Vec3 displacement = currentPosition.subtract(target);
        Vec3 springForce = displacement.scale(-springStiffness);
        Vec3 dampingForce = velocity.scale(-springDamping);
        Vec3 acceleration = springForce.add(dampingForce);

        // Integrazione semi-implicita
        velocity = velocity.add(acceleration.scale(deltaTime));
        return currentPosition.add(velocity.scale(deltaTime));
    }

    /**
     * Critically damped spring: smooth and fast without oscillations.
     * Used for UI where you don't want bouncing.
     */
    private Vec3 updateCriticallyDamped(Vec3 target, float deltaTime) {
        // Damping critico: d = 2 * sqrt(k)
        float omega = (float) Math.sqrt(springStiffness);
        float criticalDamping = 2 * omega;

        Vec3 displacement = currentPosition.subtract(target);
        Vec3 springForce = displacement.scale(-springStiffness);
        Vec3 dampingForce = velocity.scale(-criticalDamping);
        Vec3 acceleration = springForce.add(dampingForce);

        velocity = velocity.add(acceleration.scale(deltaTime));
        return currentPosition.add(velocity.scale(deltaTime));
    }

    /**
     * Sets position immediately (without smoothing).
     */
    public void setPosition(Vec3 position) {
        this.currentPosition = position;
        this.velocity = Vec3.ZERO;
    }

    /**
     * Resets velocity (stops movement).
     */
    public void resetVelocity() {
        this.velocity = Vec3.ZERO;
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
    public double distanceFromTarget(Vec3 target) {
        return currentPosition.distanceTo(target);
    }

    /**
     * Checks if position is "settled" (close to target and nearly stopped).
     */
    public boolean isSettled(Vec3 target, double positionThreshold, double velocityThreshold) {
        return currentPosition.distanceTo(target) < positionThreshold
            && velocity.length() < velocityThreshold;
    }
}

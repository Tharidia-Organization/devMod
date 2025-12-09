package com.frenkvs.devmod.panels.tracking;

import net.minecraft.world.phys.Vec3;

/**
 * Utility per smoothing di posizioni 3D con vari algoritmi.
 *
 * Supporta:
 * - Interpolazione lineare (lerp)
 * - Smoothing esponenziale
 * - Spring physics (molla)
 * - Critically damped spring (nessuna oscillazione)
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
     * Algoritmi di smoothing disponibili.
     */
    public enum SmoothingMode {
        /** Interpolazione lineare semplice */
        LINEAR,
        /** Smoothing esponenziale (piu' naturale) */
        EXPONENTIAL,
        /** Fisica a molla (puo' oscillare) */
        SPRING,
        /** Molla critically damped (smooth senza oscillazioni) */
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
     * Aggiorna la posizione verso il target.
     *
     * @param target Posizione target
     * @param deltaTime Tempo trascorso in secondi (tipicamente 0.05 per tick)
     * @return Nuova posizione smoothed
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
     * Interpolazione lineare: si muove di una frazione fissa verso il target.
     */
    private Vec3 updateLinear(Vec3 target, float deltaTime) {
        Vec3 diff = target.subtract(currentPosition);
        float factor = Math.min(1.0f, smoothingFactor * deltaTime * 20);
        return currentPosition.add(diff.scale(factor));
    }

    /**
     * Smoothing esponenziale: piu' veloce quando lontano, rallenta avvicinandosi.
     */
    private Vec3 updateExponential(Vec3 target, float deltaTime) {
        // Formula: pos = pos + (target - pos) * (1 - e^(-factor * dt))
        float factor = (float) (1 - Math.exp(-smoothingFactor * 20 * deltaTime));
        Vec3 diff = target.subtract(currentPosition);
        return currentPosition.add(diff.scale(factor));
    }

    /**
     * Fisica a molla: puo' oscillare attorno al target.
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
     * Critically damped spring: smooth e veloce senza oscillazioni.
     * Usato per UI dove non vuoi "bouncing".
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
     * Imposta immediatamente la posizione (senza smoothing).
     */
    public void setPosition(Vec3 position) {
        this.currentPosition = position;
        this.velocity = Vec3.ZERO;
    }

    /**
     * Resetta la velocita' (ferma il movimento).
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
     * Configura per movimento UI standard (smooth, no bounce).
     */
    public void configureForUI() {
        this.mode = SmoothingMode.CRITICALLY_DAMPED;
        this.springStiffness = 150f;
    }

    /**
     * Configura per tracking entita' (segue fluidamente).
     */
    public void configureForEntityTracking() {
        this.mode = SmoothingMode.EXPONENTIAL;
        this.smoothingFactor = 0.12f;
    }

    /**
     * Configura per effetti di combattimento (reattivo).
     */
    public void configureForCombat() {
        this.mode = SmoothingMode.EXPONENTIAL;
        this.smoothingFactor = 0.25f;
    }

    /**
     * Calcola la distanza dal target.
     */
    public double distanceFromTarget(Vec3 target) {
        return currentPosition.distanceTo(target);
    }

    /**
     * Verifica se la posizione e' "settled" (vicina al target e quasi ferma).
     */
    public boolean isSettled(Vec3 target, double positionThreshold, double velocityThreshold) {
        return currentPosition.distanceTo(target) < positionThreshold
            && velocity.length() < velocityThreshold;
    }
}

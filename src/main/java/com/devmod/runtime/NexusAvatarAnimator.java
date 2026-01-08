package com.devmod.runtime;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.devmod.config.Config;

/**
 * Handles tick-based animation for the Nexus avatar entity.
 * Provides floating movement, slow rotation, and ambient particle effects.
 */
public final class NexusAvatarAnimator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusAvatarAnimator.class);

    // Floating animation parameters
    private static final double DEFAULT_FLOAT_AMPLITUDE = 0.3;  // blocks up/down
    private static final double DEFAULT_FLOAT_SPEED = 0.05;     // radians per tick
    private static final float DEFAULT_ROTATE_SPEED = 0.5f;     // degrees per tick

    // Particle settings
    private static final int DEFAULT_PARTICLE_INTERVAL = 10;    // ticks between particle bursts
    private static final int END_ROD_COUNT = 3;
    private static final int ENCHANT_COUNT = 5;

    // Animation state
    private double phase = 0;
    private int particleTick = 0;
    private double baseY = Double.NaN;
    private boolean initialized = false;

    /**
     * Initialize the animator with the avatar's starting position.
     * Call this once when the avatar is first spawned.
     */
    public void initialize(@Nonnull Entity avatar) {
        this.baseY = avatar.getY();
        this.phase = 0;
        this.particleTick = 0;
        this.initialized = true;
        LOGGER.debug("[NexusAvatar] Animator initialized at Y={}", baseY);
    }

    /**
     * Initialize the animator with a specific target Y position.
     * Use this at spawn time to avoid race conditions with entity positioning.
     *
     * @param targetY The intended base Y position for floating animation
     */
    public void initializeWithTargetY(double targetY) {
        this.baseY = targetY;
        this.phase = 0;
        this.particleTick = 0;
        this.initialized = true;
        LOGGER.debug("[NexusAvatar] Animator initialized with target Y={}", baseY);
    }

    /**
     * Reset the animator state. Call when avatar is respawned.
     */
    public void reset() {
        this.baseY = Double.NaN;
        this.phase = 0;
        this.particleTick = 0;
        this.initialized = false;
    }

    /**
     * Called every server tick to animate the avatar.
     *
     * @param avatar The avatar entity to animate
     * @param level The server level for particle spawning
     */
    public void tick(@Nonnull Entity avatar, @Nonnull ServerLevel level) {
        Objects.requireNonNull(avatar, "avatar");
        Objects.requireNonNull(level, "level");

        if (!initialized) {
            LOGGER.warn("[NexusAvatar] Lazy initialization triggered - baseY may be incorrect. Call initializeWithTargetY() at spawn time.");
            initialize(avatar);
        }

        // Get config values with defaults
        double floatAmplitude = getFloatAmplitude();
        double floatSpeed = getFloatSpeed();
        float rotateSpeed = getRotateSpeed();
        int particleInterval = getParticleInterval();
        boolean particlesEnabled = areParticlesEnabled();
        boolean rotateEnabled = isRotateEnabled();
        boolean floatEnabled = isFloatEnabled();

        // 1. Floating Y position (sine wave)
        if (floatEnabled) {
            phase += floatSpeed;
            if (phase > Math.PI * 2) {
                phase -= Math.PI * 2; // Keep phase bounded
            }
            double yOffset = Math.sin(phase) * floatAmplitude;
            avatar.setPos(avatar.getX(), baseY + yOffset, avatar.getZ());
        }

        // 2. Slow rotation
        if (rotateEnabled) {
            float newYaw = (avatar.getYRot() + rotateSpeed) % 360f;
            avatar.setYRot(newYaw);
            avatar.setYBodyRot(newYaw);
            avatar.setYHeadRot(newYaw);
        }

        // 3. Ambient particles
        if (particlesEnabled) {
            particleTick++;
            if (particleTick >= particleInterval) {
                particleTick = 0;
                spawnParticles(level, avatar.position());
            }
        }
    }

    /**
     * Spawn ambient particles around the avatar position.
     */
    private void spawnParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos) {
        // End rod particles - floating mystical effect above the avatar
        level.sendParticles(
            ParticleTypes.END_ROD,
            pos.x, pos.y + 1.8, pos.z,
            END_ROD_COUNT,
            0.3, 0.5, 0.3,  // spread
            0.02            // speed
        );

        // Enchant particles - swirling around the avatar
        level.sendParticles(
            ParticleTypes.ENCHANT,
            pos.x, pos.y + 0.5, pos.z,
            ENCHANT_COUNT,
            0.5, 0.3, 0.5,  // spread
            0.1             // speed
        );

        // Occasional soul particle for mystical feel
        if (phase < 0.1) {  // ~once per full cycle
            level.sendParticles(
                ParticleTypes.SOUL,
                pos.x, pos.y + 1.0, pos.z,
                1,
                0.2, 0.2, 0.2,
                0.01
            );
        }
    }

    // === Config Accessors with Defaults ===

    private boolean isFloatEnabled() {
        try {
            return Config.NEXUS_AVATAR_FLOAT_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private double getFloatAmplitude() {
        try {
            return Config.NEXUS_AVATAR_FLOAT_AMPLITUDE.get();
        } catch (Exception e) {
            return DEFAULT_FLOAT_AMPLITUDE;
        }
    }

    private double getFloatSpeed() {
        try {
            return Config.NEXUS_AVATAR_FLOAT_SPEED.get();
        } catch (Exception e) {
            return DEFAULT_FLOAT_SPEED;
        }
    }

    private boolean isRotateEnabled() {
        try {
            return Config.NEXUS_AVATAR_ROTATE.get();
        } catch (Exception e) {
            return true;
        }
    }

    private float getRotateSpeed() {
        try {
            return Config.NEXUS_AVATAR_ROTATE_SPEED.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_ROTATE_SPEED;
        }
    }

    private boolean areParticlesEnabled() {
        try {
            return Config.NEXUS_AVATAR_PARTICLES_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    private int getParticleInterval() {
        try {
            return Config.NEXUS_AVATAR_PARTICLE_INTERVAL.get();
        } catch (Exception e) {
            return DEFAULT_PARTICLE_INTERVAL;
        }
    }

    /**
     * Check if the animator has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}

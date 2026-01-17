package com.devmod.hologram.data;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Visual states for holograms, used for UI feedback and particles.
 * Each state has associated primary and secondary colors (Regola 1 - Bibbia Estetica).
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum HologramState {
    /**
     * Hologram is active and visible (default state).
     */
    IDLE(0x00d4aa, 0x008b72, ParticleTypes.END_ROD, 1, 0.5),

    /**
     * Editor is open, modifications in progress.
     */
    EDITING(0xf1c40f, 0xe67e22, ParticleTypes.ENCHANT, 3, 0.8),

    /**
     * Syncing with server.
     */
    UPDATING(0x3498db, 0x2980b9, ParticleTypes.ELECTRIC_SPARK, 2, 0.3),

    /**
     * Previewing changes before save.
     */
    PREVIEW(0x2ecc71, 0x27ae60, ParticleTypes.HAPPY_VILLAGER, 2, 0.6),

    /**
     * Error occurred or sync failed.
     */
    ERROR(0xe74c3c, 0xc0392b, ParticleTypes.SMOKE, 3, 0.4),

    /**
     * Hologram is locked and cannot be modified.
     */
    LOCKED(0x95a5a6, 0x7f8c8d, ParticleTypes.END_ROD, 1, 0.3);

    private final int primary;
    private final int secondary;
    private final ParticleOptions particleType;
    private final int baseParticleCount;
    private final double particleSpread;

    HologramState(int primary, int secondary, ParticleOptions particleType, int baseCount, double spread) {
        this.primary = primary;
        this.secondary = secondary;
        this.particleType = particleType;
        this.baseParticleCount = baseCount;
        this.particleSpread = spread;
    }

    /**
     * Primary color for this state (used for borders, highlights).
     */
    public int getPrimary() {
        return primary;
    }

    /**
     * Secondary color for this state (used for accents).
     */
    public int getSecondary() {
        return secondary;
    }

    /**
     * Primary color with full alpha.
     */
    public int getPrimaryWithAlpha() {
        return 0xFF000000 | primary;
    }

    /**
     * Secondary color with full alpha.
     */
    public int getSecondaryWithAlpha() {
        return 0xFF000000 | secondary;
    }

    /**
     * Particle type for this state (Regola 3).
     */
    public ParticleOptions getParticleType() {
        return particleType;
    }

    /**
     * Base particle count per tick.
     */
    public int getBaseParticleCount() {
        return baseParticleCount;
    }

    /**
     * Particle spread radius.
     */
    public double getParticleSpread() {
        return particleSpread;
    }
}

package com.devmod.hologram.runtime;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import com.devmod.hologram.data.HologramState;

/**
 * Standardized particle effects for holograms (Regola 3 - Bibbia Estetica).
 *
 * <p>Each hologram state has associated particle patterns:
 * <ul>
 *   <li>IDLE: END_ROD with slow upward drift</li>
 *   <li>EDITING: ENCHANT with spiral pattern</li>
 *   <li>UPDATING: ELECTRIC_SPARK with vertical lines</li>
 *   <li>PREVIEW: HAPPY_VILLAGER with burst</li>
 *   <li>ERROR: SMOKE with dispersal</li>
 *   <li>SPAWN: PORTAL + END_ROD column</li>
 * </ul>
 */
public final class HologramParticles {
    private HologramParticles() {}

    /**
     * Default particle spawn interval in ticks.
     */
    public static final int DEFAULT_INTERVAL = 10;

    /**
     * Spawns particles for a hologram based on its state.
     *
     * @param level    The server level
     * @param pos      The hologram position
     * @param state    The current hologram state
     * @param interval The spawn interval in ticks (uses state's base count when 0)
     */
    public static void spawn(ServerLevel level, Vec3 pos, HologramState state, int interval) {
        if (level == null || pos == null || state == null) {
            return;
        }

        int actualInterval = interval > 0 ? interval : DEFAULT_INTERVAL;
        if (level.getGameTime() % actualInterval != 0) {
            return;
        }

        ParticleOptions particle = state.getParticleType();
        int count = state.getBaseParticleCount();
        double spread = state.getParticleSpread();

        spawnParticles(level, pos, particle, count, spread, state);
    }

    /**
     * Spawns spawn effect particles (used when creating a new hologram).
     */
    public static void spawnSpawnEffect(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) {
            return;
        }

        // Portal particles in a column
        for (int i = 0; i < 30; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 1.0;
            double offsetY = level.random.nextDouble() * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 1.0;

            level.sendParticles(
                ParticleTypes.PORTAL,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0.1
            );
        }

        // End rod particles rising
        for (int i = 0; i < 15; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;

            level.sendParticles(
                ParticleTypes.END_ROD,
                pos.x + offsetX, pos.y, pos.z + offsetZ,
                1, 0, 0.1, 0, 0.05
            );
        }
    }

    /**
     * Spawns remove effect particles (used when deleting a hologram).
     */
    public static void spawnRemoveEffect(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) {
            return;
        }

        // Smoke dispersal
        for (int i = 0; i < 20; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 1.5;
            double offsetY = (level.random.nextDouble() - 0.5) * 1.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 1.5;

            level.sendParticles(
                ParticleTypes.SMOKE,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0.02
            );
        }
    }

    private static void spawnParticles(
        ServerLevel level,
        Vec3 pos,
        ParticleOptions particle,
        int count,
        double spread,
        HologramState state
    ) {
        switch (state) {
            case IDLE -> spawnIdlePattern(level, pos, particle, count, spread);
            case EDITING -> spawnEditingPattern(level, pos, particle, count, spread);
            case UPDATING -> spawnUpdatingPattern(level, pos, particle, count, spread);
            case PREVIEW -> spawnPreviewPattern(level, pos, particle, count, spread);
            case ERROR -> spawnErrorPattern(level, pos, particle, count, spread);
            case LOCKED -> spawnLockedPattern(level, pos, particle, spread);
        }
    }

    private static void spawnIdlePattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread
    ) {
        // Slow upward drift
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * spread;
            double offsetZ = (level.random.nextDouble() - 0.5) * spread;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y, pos.z + offsetZ,
                1, 0, 0.02, 0, 0.01
            );
        }
    }

    private static void spawnEditingPattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread
    ) {
        // Spiral around hologram
        double time = level.getGameTime() * 0.1;
        for (int i = 0; i < count; i++) {
            double angle = time + (i * Math.PI * 2 / count);
            double radius = spread * 0.5;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = (level.random.nextDouble() - 0.5) * 0.5;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0.01
            );
        }
    }

    private static void spawnUpdatingPattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread
    ) {
        // Vertical lines
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * spread * 0.3;
            double offsetZ = (level.random.nextDouble() - 0.5) * spread * 0.3;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y - 0.5, pos.z + offsetZ,
                1, 0, 0.5, 0, 0.1
            );
        }
    }

    private static void spawnPreviewPattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread
    ) {
        // Burst outward
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = level.random.nextDouble() * spread;
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;
            double offsetY = (level.random.nextDouble() - 0.5) * 0.5;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0.02
            );
        }
    }

    private static void spawnErrorPattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double spread
    ) {
        // Dispersal
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * spread;
            double offsetY = (level.random.nextDouble() - 0.5) * spread;
            double offsetZ = (level.random.nextDouble() - 0.5) * spread;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0.02
            );
        }
    }

    private static void spawnLockedPattern(
        ServerLevel level, Vec3 pos, ParticleOptions particle, double spread
    ) {
        // Minimal particles, slow
        if (level.random.nextInt(3) == 0) {
            double offsetX = (level.random.nextDouble() - 0.5) * spread * 0.3;
            double offsetZ = (level.random.nextDouble() - 0.5) * spread * 0.3;

            level.sendParticles(
                particle,
                pos.x + offsetX, pos.y, pos.z + offsetZ,
                1, 0, 0.01, 0, 0.005
            );
        }
    }
}

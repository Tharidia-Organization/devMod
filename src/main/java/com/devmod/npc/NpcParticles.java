package com.devmod.npc;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Unified NPC particle system.
 * All NPCs use this standardized particle scheme.
 *
 * <p>BIBBIA ESTETICA R3: Particles NPC - INVIOLABILE
 * <ul>
 *   <li>Colore particles NON configurabile per NPC (usa default)</li>
 *   <li>Intensità scala con particleInterval config</li>
 *   <li>MAI particles custom - solo vanilla Minecraft</li>
 *   <li>Particles solo se particlesEnabled = true</li>
 * </ul>
 */
public final class NpcParticles {
    private NpcParticles() {}

    /**
     * Particle configuration per NPC state.
     * Uses runtime lookup to avoid storing mutable references in enum constants.
     */
    public enum StateParticles {
        /** IDLE: Spirale ascendente */
        IDLE(1, 0.3, Pattern.SPIRAL_UP),

        /** TALKING: Verso player */
        TALKING(2, 0.4, Pattern.TOWARD_PLAYER),

        /** THINKING: Orbitale */
        THINKING(1, 0.2, Pattern.ORBITAL),

        /** ACTION: Burst */
        ACTION(3, 0.5, Pattern.BURST),

        /** ERROR: Dispersione */
        ERROR(2, 0.3, Pattern.DISPERSE),

        /** SPAWN: Colonna */
        SPAWN(20, 0.8, Pattern.COLUMN);

        private final int baseCount;
        private final double spread;
        private final Pattern pattern;

        StateParticles(int baseCount, double spread, Pattern pattern) {
            this.baseCount = baseCount;
            this.spread = spread;
            this.pattern = Objects.requireNonNull(pattern);
        }

        /**
         * Gets the particle type via runtime lookup to avoid storing mutable references.
         */
        public ParticleOptions getParticle() {
            return switch (this) {
                case IDLE -> ParticleTypes.END_ROD;
                case TALKING -> ParticleTypes.ENCHANT;
                case THINKING -> ParticleTypes.SOUL;
                case ACTION -> ParticleTypes.HAPPY_VILLAGER;
                case ERROR -> ParticleTypes.SMOKE;
                case SPAWN -> ParticleTypes.PORTAL;
            };
        }

        public int getBaseCount() {
            return baseCount;
        }

        public double getSpread() {
            return spread;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * Particle spawn patterns.
     */
    public enum Pattern {
        SPIRAL_UP,
        TOWARD_PLAYER,
        ORBITAL,
        BURST,
        DISPERSE,
        COLUMN
    }

    /**
     * Spawn particles for NPC state at position.
     * Should be called client-side only.
     *
     * @param level The level
     * @param pos NPC position
     * @param state Current NPC state
     * @param interval Particle interval from config (used for timing check)
     * @param gameTime Current game time (level.getGameTime())
     */
    public static void spawn(Level level, Vec3 pos, NpcState state, int interval, long gameTime) {
        if (level == null || !level.isClientSide || pos == null || state == null) return;
        if (interval <= 0 || gameTime % interval != 0) return;

        StateParticles config = getConfigForState(state);
        if (config == null) return;

        spawnWithPattern(level, pos, config);
    }

    /**
     * Spawn particles for NPC spawn effect.
     * This is a one-time burst effect.
     */
    public static void spawnEffect(Level level, Vec3 pos) {
        if (level == null || !level.isClientSide || pos == null) return;

        // Portal particles
        for (int i = 0; i < 20; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.8;
            double offsetY = level.random.nextDouble() * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.8;
            level.addParticle(Objects.requireNonNull(ParticleTypes.PORTAL),
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                0, 0.1, 0);
        }

        // End rod particles
        for (int i = 0; i < 10; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = level.random.nextDouble() * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(Objects.requireNonNull(ParticleTypes.END_ROD),
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                0, 0.05, 0);
        }
    }

    @Nullable
    private static StateParticles getConfigForState(NpcState state) {
        return switch (state) {
            case IDLE -> StateParticles.IDLE;
            case TALKING -> StateParticles.TALKING;
            case THINKING -> StateParticles.THINKING;
            case ACTION -> StateParticles.ACTION;
            case ERROR -> StateParticles.ERROR;
            case UNAVAILABLE -> null; // No particles for unavailable
        };
    }

    private static void spawnWithPattern(Level level, Vec3 pos, StateParticles config) {
        ParticleOptions particle = config.getParticle();
        int count = config.getBaseCount();
        double spread = config.getSpread();
        Pattern pattern = config.getPattern();

        for (int i = 0; i < count; i++) {
            double[] offset = calculateOffset(level, spread, pattern, i, count);
            double[] velocity = calculateVelocity(pattern);

            level.addParticle(Objects.requireNonNull(particle),
                pos.x + offset[0],
                pos.y + 1.0 + offset[1],
                pos.z + offset[2],
                velocity[0], velocity[1], velocity[2]);
        }
    }

    private static double[] calculateOffset(Level level, double spread, Pattern pattern, int index, int total) {
        return switch (pattern) {
            case SPIRAL_UP -> {
                double angle = (level.getGameTime() * 0.1 + index * Math.PI * 2 / total) % (Math.PI * 2);
                yield new double[]{
                    Math.cos(angle) * spread,
                    (level.getGameTime() * 0.02 % 1.0) * 0.5,
                    Math.sin(angle) * spread
                };
            }
            case TOWARD_PLAYER -> new double[]{
                (level.random.nextDouble() - 0.5) * spread,
                level.random.nextDouble() * 0.5,
                (level.random.nextDouble() - 0.5) * spread
            };
            case ORBITAL -> {
                double angle = level.getGameTime() * 0.05 + index * Math.PI * 2 / total;
                yield new double[]{
                    Math.cos(angle) * spread * 0.5,
                    0,
                    Math.sin(angle) * spread * 0.5
                };
            }
            case BURST -> new double[]{
                (level.random.nextDouble() - 0.5) * spread * 2,
                level.random.nextDouble() * spread,
                (level.random.nextDouble() - 0.5) * spread * 2
            };
            case DISPERSE -> new double[]{
                (level.random.nextDouble() - 0.5) * spread,
                level.random.nextDouble() * 0.3,
                (level.random.nextDouble() - 0.5) * spread
            };
            case COLUMN -> new double[]{
                (level.random.nextDouble() - 0.5) * spread * 0.3,
                level.random.nextDouble() * 2.0,
                (level.random.nextDouble() - 0.5) * spread * 0.3
            };
        };
    }

    private static double[] calculateVelocity(Pattern pattern) {
        return switch (pattern) {
            case SPIRAL_UP -> new double[]{0, 0.02, 0};
            case TOWARD_PLAYER -> new double[]{0, 0.01, 0};
            case ORBITAL -> new double[]{0, 0, 0};
            case BURST -> new double[]{0, 0.05, 0};
            case DISPERSE -> new double[]{0, -0.01, 0};
            case COLUMN -> new double[]{0, 0.1, 0};
        };
    }
}

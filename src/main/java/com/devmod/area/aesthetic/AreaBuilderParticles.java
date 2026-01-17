package com.devmod.area.aesthetic;

import java.util.Objects;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * BIBBIA ESTETICA - REGOLA 3: PARTICLES AREA PREVIEW
 *
 * Sistema particellare per visualizzare bounds e stato.
 * Queste particelle definiscono il feedback visivo del sistema Area Builder.
 */
public final class AreaBuilderParticles {

    private AreaBuilderParticles() {}

    // ============================================================================
    // PREVIEW BOUNDS - CORNERS E EDGES
    // ============================================================================

    /** Particle for corner markers */
    public static final ParticleOptions CORNER = ParticleTypes.END_ROD;

    /** Particle for edge markers */
    public static final ParticleOptions EDGE = ParticleTypes.ELECTRIC_SPARK;

    /** Particle for floor area - color from EditorState */
    public static ParticleOptions getFloorParticle(EditorState state) {
        float[] rgb = state.getPrimaryRGB();
        return new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 1.0f);
    }

    // ============================================================================
    // BUILDING PROGRESS
    // ============================================================================

    /** Particle during active building */
    public static final ParticleOptions BUILD_ACTIVE = ParticleTypes.HAPPY_VILLAGER;

    /** Particle burst on completion */
    public static final ParticleOptions BUILD_COMPLETE = ParticleTypes.TOTEM_OF_UNDYING;

    // ============================================================================
    // BIOME GENERATION
    // ============================================================================

    /** Particle for biome preview */
    public static final ParticleOptions BIOME_PREVIEW = ParticleTypes.CHERRY_LEAVES;

    /** Particle for terrain generation progress */
    public static final ParticleOptions TERRAIN_GEN = ParticleTypes.CLOUD;

    // ============================================================================
    // ERRORS
    // ============================================================================

    /** Particle for errors */
    public static final ParticleOptions ERROR = ParticleTypes.SMOKE;

    /** Particle for conflict zones */
    public static final ParticleOptions CONFLICT_ZONE = ParticleTypes.ANGRY_VILLAGER;

    // ============================================================================
    // CONSTANTS
    // ============================================================================

    /** Particles per corner marker */
    public static final int CORNER_COUNT = 3;

    /** Spread for corner particles */
    public static final double CORNER_SPREAD = 0.1;

    /** Interval in ticks for corner particles */
    public static final int CORNER_INTERVAL = 5;

    /** Particles per block for edges */
    public static final int EDGE_PER_BLOCK = 1;

    /** Spread for edge particles */
    public static final double EDGE_SPREAD = 0.2;

    /** Interval in ticks for edge particles */
    public static final int EDGE_INTERVAL = 10;

    /** Particles per square block for floor (0.5 = 1 per 2 blocks) */
    public static final double FLOOR_DENSITY = 0.5;

    /** Spread for floor particles */
    public static final double FLOOR_SPREAD = 0.5;

    /** Interval in ticks for floor particles */
    public static final int FLOOR_INTERVAL = 20;

    /** Particles during building */
    public static final int BUILD_ACTIVE_COUNT = 2;

    /** Spread for build particles */
    public static final double BUILD_SPREAD = 0.3;

    /** Particles for completion burst */
    public static final int BUILD_COMPLETE_COUNT = 50;

    /** Spread for completion burst */
    public static final double BUILD_COMPLETE_SPREAD = 2.0;

    /** Particles for biome preview */
    public static final int BIOME_PREVIEW_COUNT = 5;

    /** Spread for biome preview */
    public static final double BIOME_PREVIEW_SPREAD = 1.0;

    /** Interval for biome preview */
    public static final int BIOME_PREVIEW_INTERVAL = 10;

    /** Particles for errors */
    public static final int ERROR_COUNT = 5;

    /** Spread for error particles */
    public static final double ERROR_SPREAD = 0.5;

    /** Interval for error particles */
    public static final int ERROR_INTERVAL = 5;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Spawns corner particles at a position.
     */
    public static void spawnCornerParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(
            Objects.requireNonNull(CORNER, "CORNER"),
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            CORNER_COUNT,
            CORNER_SPREAD, CORNER_SPREAD, CORNER_SPREAD,
            0.01
        );
    }

    /**
     * Spawns edge particles along a line between two positions.
     */
    public static void spawnEdgeParticles(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 start = Objects.requireNonNull(Vec3.atCenterOf(Objects.requireNonNull(from, "from")), "start");
        Vec3 end = Objects.requireNonNull(Vec3.atCenterOf(Objects.requireNonNull(to, "to")), "end");
        Vec3 diff = Objects.requireNonNull(end.subtract(start), "diff");
        Vec3 direction = Objects.requireNonNull(diff.normalize(), "direction");
        double distance = start.distanceTo(end);

        for (double d = 0; d < distance; d += 1.0) {
            Vec3 scaled = Objects.requireNonNull(direction.scale(d), "scaled");
            Vec3 point = Objects.requireNonNull(start.add(scaled), "point");
            level.sendParticles(
                Objects.requireNonNull(EDGE, "EDGE"),
                point.x, point.y, point.z,
                EDGE_PER_BLOCK,
                EDGE_SPREAD, EDGE_SPREAD, EDGE_SPREAD,
                0.01
            );
        }
    }

    /**
     * Spawns floor particles in an area with state color.
     */
    public static void spawnFloorParticles(ServerLevel level, BlockPos center, int radius, EditorState state) {
        ParticleOptions particle = Objects.requireNonNull(getFloorParticle(state), "particle");
        int count = (int) (radius * radius * FLOOR_DENSITY);

        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * radius * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * radius * 2;
            level.sendParticles(
                particle,
                center.getX() + 0.5 + offsetX,
                center.getY() + 0.1,
                center.getZ() + 0.5 + offsetZ,
                1,
                FLOOR_SPREAD, 0.0, FLOOR_SPREAD,
                0.0
            );
        }
    }

    /**
     * Spawns building progress particles.
     */
    public static void spawnBuildActiveParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(
            Objects.requireNonNull(BUILD_ACTIVE, "BUILD_ACTIVE"),
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            BUILD_ACTIVE_COUNT,
            BUILD_SPREAD, BUILD_SPREAD, BUILD_SPREAD,
            0.0
        );
    }

    /**
     * Spawns completion burst particles.
     */
    public static void spawnBuildCompleteParticles(ServerLevel level, BlockPos center) {
        level.sendParticles(
            Objects.requireNonNull(BUILD_COMPLETE, "BUILD_COMPLETE"),
            center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
            BUILD_COMPLETE_COUNT,
            BUILD_COMPLETE_SPREAD, BUILD_COMPLETE_SPREAD, BUILD_COMPLETE_SPREAD,
            0.5
        );
    }

    /**
     * Spawns error particles at a position.
     */
    public static void spawnErrorParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(
            Objects.requireNonNull(ERROR, "ERROR"),
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            ERROR_COUNT,
            ERROR_SPREAD, ERROR_SPREAD, ERROR_SPREAD,
            0.02
        );
    }
}

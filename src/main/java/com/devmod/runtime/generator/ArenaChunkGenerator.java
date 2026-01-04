package com.devmod.runtime.generator;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.runtime.biome.ZoneBiomeSource;

/**
 * Base class for arena chunk generators.
 * Handles zone-aware biome resolution and arena bounds checking.
 *
 * <p>This generator optimizes chunk generation by only generating terrain
 * for chunks that intersect the arena bounds. Chunks outside the arena
 * are left as void (or bedrock barrier depending on configuration).</p>
 *
 * <p>Subclasses implement specific terrain generation strategies:
 * <ul>
 *   <li>{@link ArenaFlatChunkGenerator} - Flat terrain like vanilla superflat</li>
 *   <li>ArenaNaturalChunkGenerator - Natural terrain with hills (future)</li>
 * </ul></p>
 */
public abstract class ArenaChunkGenerator extends ChunkGenerator {

    protected final ZoneBiomeSource zoneBiomeSource;
    protected final ArenaBounds arenaBounds;
    protected final ArenaZone.ZoneShape arenaShape;

    /**
     * Creates an arena chunk generator.
     *
     * @param biomeSource The zone-aware biome source
     * @param bounds The arena bounds (coordinates that define the arena area)
     * @param shape The shape of the arena (RECTANGULAR, CIRCULAR, RING)
     */
    protected ArenaChunkGenerator(
            ZoneBiomeSource biomeSource,
            ArenaBounds bounds,
            ArenaZone.ZoneShape shape
    ) {
        super(biomeSource);
        this.zoneBiomeSource = biomeSource;
        this.arenaBounds = bounds;
        this.arenaShape = shape;
    }

    /**
     * Checks if a chunk intersects the arena bounds.
     * Used to optimize generation by skipping chunks outside the arena.
     */
    protected boolean chunkIntersectsArena(ChunkPos chunkPos) {
        if (arenaBounds == null) {
            return true; // No bounds = generate everything
        }

        // Convert chunk pos to block coordinates
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        return switch (arenaShape) {
            case RECTANGULAR -> rectangleIntersects(
                    chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
                    arenaBounds.minX(), arenaBounds.minZ(), arenaBounds.maxX(), arenaBounds.maxZ()
            );
            case CIRCULAR, RING -> circleIntersectsChunk(chunkPos, arenaBounds);
        };
    }

    /**
     * Checks if a block position is inside the arena shape.
     */
    protected boolean positionInArena(int x, int z) {
        if (arenaBounds == null) {
            return true;
        }

        return switch (arenaShape) {
            case RECTANGULAR -> x >= arenaBounds.minX() && x <= arenaBounds.maxX()
                    && z >= arenaBounds.minZ() && z <= arenaBounds.maxZ();
            case CIRCULAR -> {
                double dx = x - arenaBounds.centerX();
                double dz = z - arenaBounds.centerZ();
                yield dx * dx + dz * dz <= arenaBounds.radius() * arenaBounds.radius();
            }
            case RING -> {
                double dx = x - arenaBounds.centerX();
                double dz = z - arenaBounds.centerZ();
                double distSq = dx * dx + dz * dz;
                yield distSq <= arenaBounds.radius() * arenaBounds.radius()
                        && distSq >= arenaBounds.innerRadius() * arenaBounds.innerRadius();
            }
        };
    }

    /**
     * Gets the biome source as a ZoneBiomeSource.
     */
    public ZoneBiomeSource getZoneBiomeSource() {
        return zoneBiomeSource;
    }

    /**
     * Gets the arena bounds.
     */
    @Nullable
    public ArenaBounds getArenaBounds() {
        return arenaBounds;
    }

    /**
     * Gets the arena shape.
     */
    public ArenaZone.ZoneShape getArenaShape() {
        return arenaShape;
    }

    // ============== Helper Methods ==============

    private boolean rectangleIntersects(
            int ax1, int az1, int ax2, int az2,
            int bx1, int bz1, int bx2, int bz2
    ) {
        return ax1 <= bx2 && ax2 >= bx1 && az1 <= bz2 && az2 >= bz1;
    }

    private boolean circleIntersectsChunk(ChunkPos chunk, ArenaBounds bounds) {
        // Find the closest point on the chunk to the circle center
        int closestX = Math.max(chunk.getMinBlockX(),
                Math.min(bounds.centerX(), chunk.getMaxBlockX()));
        int closestZ = Math.max(chunk.getMinBlockZ(),
                Math.min(bounds.centerZ(), chunk.getMaxBlockZ()));

        // Calculate distance from closest point to center
        double dx = closestX - bounds.centerX();
        double dz = closestZ - bounds.centerZ();
        double distanceSq = dx * dx + dz * dz;

        // Check if this distance is less than or equal to radius
        return distanceSq <= bounds.radius() * bounds.radius();
    }

    // ============== ChunkGenerator Contract ==============

    @Override
    public void applyCarvers(
            @Nonnull WorldGenRegion level,
            long seed,
            @Nonnull RandomState random,
            @Nonnull BiomeManager biomeManager,
            @Nonnull StructureManager structureManager,
            @Nonnull ChunkAccess chunk,
            @Nonnull GenerationStep.Carving step
    ) {
        // No carvers for arena dimensions - we control the terrain
    }

    // ============== Structure Generation - DISABLED ==============
    // These overrides completely disable structure generation to improve dimension creation performance.
    // Without these, Minecraft still does expensive structure placement checks even with empty structure sets.

    @Override
    public void createStructures(
            @Nonnull net.minecraft.core.RegistryAccess registryAccess,
            @Nonnull net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState,
            @Nonnull StructureManager structureManager,
            @Nonnull ChunkAccess chunk,
            @Nonnull net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager
    ) {
        // No structures in arena dimensions - skip entirely for performance
    }


    @Override
    public void buildSurface(
            @Nonnull WorldGenRegion level,
            @Nonnull StructureManager structureManager,
            @Nonnull RandomState random,
            @Nonnull ChunkAccess chunk
    ) {
        // Surface building is handled by fillFromNoise or subclass
    }

    @Override
    public void spawnOriginalMobs(@Nonnull WorldGenRegion level) {
        // No natural mob spawning in arena dimensions
    }

    @Override
    public int getGenDepth() {
        return 384; // Standard world height
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(
            int x, int z,
            @Nonnull Heightmap.Types type,
            @Nonnull LevelHeightAccessor level,
            @Nonnull RandomState random
    ) {
        // Return floor height for height queries
        return positionInArena(x, z) ? 64 : getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, @Nonnull LevelHeightAccessor level, @Nonnull RandomState random) {
        // Return empty column for queries
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(@Nonnull List<String> info, @Nonnull RandomState random, @Nonnull BlockPos pos) {
        info.add("Arena Generator: " + this.getClass().getSimpleName());
        if (arenaBounds != null) {
            info.add("Arena Shape: " + arenaShape);
            info.add("In Arena: " + positionInArena(pos.getX(), pos.getZ()));
        }
    }

    // ============== Arena Bounds Record ==============

    /**
     * Defines the bounds of an arena.
     */
    public record ArenaBounds(
            int minX, int minZ,
            int maxX, int maxZ,
            int centerX, int centerZ,
            int radius,
            int innerRadius  // For ring shapes
    ) {
        /**
         * Creates rectangular bounds.
         */
        public static ArenaBounds rectangular(int minX, int minZ, int maxX, int maxZ) {
            int centerX = (minX + maxX) / 2;
            int centerZ = (minZ + maxZ) / 2;
            int radius = Math.max(maxX - centerX, maxZ - centerZ);
            return new ArenaBounds(minX, minZ, maxX, maxZ, centerX, centerZ, radius, 0);
        }

        /**
         * Creates circular bounds.
         */
        public static ArenaBounds circular(int centerX, int centerZ, int radius) {
            return new ArenaBounds(
                    centerX - radius, centerZ - radius,
                    centerX + radius, centerZ + radius,
                    centerX, centerZ, radius, 0
            );
        }

        /**
         * Creates ring bounds.
         */
        public static ArenaBounds ring(int centerX, int centerZ, int outerRadius, int innerRadius) {
            return new ArenaBounds(
                    centerX - outerRadius, centerZ - outerRadius,
                    centerX + outerRadius, centerZ + outerRadius,
                    centerX, centerZ, outerRadius, innerRadius
            );
        }

        /**
         * Creates bounds from an ArenaZone.ZoneBounds.
         */
        public static ArenaBounds fromZoneBounds(ArenaZone.ZoneBounds bounds, ArenaZone.ZoneShape shape) {
            BlockPos center = bounds.getCenter();
            int radius = bounds.radius().orElse(Math.max(bounds.getWidth(), bounds.getDepth()) / 2);
            int innerRadius = bounds.innerRadius().orElse(radius / 2);

            return new ArenaBounds(
                    bounds.x1(), bounds.z1(),
                    bounds.x2(), bounds.z2(),
                    center.getX(), center.getZ(),
                    radius,
                    shape == ArenaZone.ZoneShape.RING ? innerRadius : 0
            );
        }
    }
}

package com.devmod.runtime.generator;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.runtime.biome.ZoneBiomeSource;

/**
 * Arena chunk generator that produces flat terrain within arena bounds.
 *
 * <p>This generator is optimized for arena dimensions:
 * <ul>
 *   <li>Only generates terrain for chunks inside arena bounds</li>
 *   <li>Applies shape masking for circular/ring arenas</li>
 *   <li>Uses ZoneBiomeSource for multi-biome support</li>
 *   <li>Generates void outside the arena</li>
 * </ul></p>
 *
 * <p>The generation follows this logic:
 * <pre>
 * For each chunk:
 *   if (chunkIntersectsArena) {
 *     for each block column in chunk:
 *       if (positionInArena) {
 *         generate flat layers
 *       } else {
 *         leave as void (or barrier)
 *       }
 *   } else {
 *     skip generation entirely (void chunk)
 *   }
 * </pre></p>
 */
public class ArenaFlatChunkGenerator extends ArenaChunkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaFlatChunkGenerator.class);

    /**
     * Codec for serialization.
     * Note: Like ZoneBiomeSource, this is primarily for runtime use.
     * The actual configuration comes from the ArenaTemplate at dimension creation.
     */
    public static final MapCodec<ArenaFlatChunkGenerator> CODEC = MapCodec.unit(
            ArenaFlatChunkGenerator::createPlaceholder
    );

    private final FlatLevelGeneratorSettings flatSettings;
    private final List<BlockState> layerStates;
    private static final int UNINITIALIZED_FLOOR_Y = Integer.MIN_VALUE;
    private volatile int floorY;
    private final boolean useBarrierOutside;

    /**
     * Creates an arena flat chunk generator.
     *
     * @param biomeSource The zone-aware biome source
     * @param bounds The arena bounds
     * @param shape The arena shape
     * @param flatSettings The flat level settings (layers, biome, etc.)
     * @param useBarrierOutside Whether to place barrier blocks outside arena (vs void)
     */
    public ArenaFlatChunkGenerator(
            ZoneBiomeSource biomeSource,
            @Nullable ArenaBounds bounds,
            ArenaZone.ZoneShape shape,
            FlatLevelGeneratorSettings flatSettings,
            boolean useBarrierOutside
    ) {
        super(biomeSource, bounds, shape);
        this.flatSettings = flatSettings;
        this.layerStates = buildLayerStates(flatSettings);
        this.floorY = UNINITIALIZED_FLOOR_Y;
        this.useBarrierOutside = useBarrierOutside;
    }

    /**
     * Convenience constructor with default barrier behavior (no barrier, just void).
     */
    public ArenaFlatChunkGenerator(
            ZoneBiomeSource biomeSource,
            @Nullable ArenaBounds bounds,
            ArenaZone.ZoneShape shape,
            FlatLevelGeneratorSettings flatSettings
    ) {
        this(biomeSource, bounds, shape, flatSettings, false);
    }

    /**
     * Creates a placeholder instance for codec deserialization.
     */
    private static ArenaFlatChunkGenerator createPlaceholder() {
        return new ArenaFlatChunkGenerator(
                ZoneBiomeSource.createPlaceholder(), // Placeholder biome source
                null, // No bounds
                ArenaZone.ZoneShape.RECTANGULAR,
                null, // No settings
                false
        );
    }

    /**
     * Builds the layer block states from flat settings.
     */
    private List<BlockState> buildLayerStates(FlatLevelGeneratorSettings settings) {
        if (settings == null) {
            // Default: single bedrock layer
            return List.of(Blocks.BEDROCK.defaultBlockState());
        }

        // FlatLevelGeneratorSettings has getLayers() which returns List<BlockState>
        // representing the Y layers from bottom to top
        return settings.getLayers();
    }

    /**
     * Calculates the floor Y level (top of the generated layers).
     */
    private int calculateFloorY(@Nullable FlatLevelGeneratorSettings settings) {
        if (settings == null || layerStates.isEmpty()) {
            return 64;
        }
        // Floor is at minY + number of layers
        return getMinY() + layerStates.size();
    }

    private int resolveFloorY() {
        int current = floorY;
        if (current != UNINITIALIZED_FLOOR_Y) {
            return current;
        }
        synchronized (this) {
            if (floorY == UNINITIALIZED_FLOOR_Y) {
                floorY = calculateFloorY(flatSettings);
            }
            return floorY;
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            @Nonnull Blender blender,
            @Nonnull RandomState random,
            @Nonnull StructureManager structureManager,
            @Nonnull ChunkAccess chunk
    ) {
        ChunkPos chunkPos = chunk.getPos();

        // Optimization: skip chunks entirely outside arena
        if (!chunkIntersectsArena(chunkPos)) {
            LOGGER.debug("Skipping chunk {} - outside arena bounds", chunkPos);
            return CompletableFuture.completedFuture(chunk);
        }

        // Generate flat terrain for this chunk
        generateFlatTerrain(chunk);

        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Generates flat terrain for a chunk, respecting arena bounds and shape.
     */
    private void generateFlatTerrain(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        // Iterate over each column in the chunk
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = minX + localX;
                int worldZ = minZ + localZ;

                // Check if this position is inside the arena shape
                if (positionInArena(worldX, worldZ)) {
                    // Generate flat layers
                    generateColumn(chunk, pos, localX, localZ, oceanFloor, worldSurface);
                } else if (useBarrierOutside) {
                    // Place barrier outside arena
                    placeBarrierColumn(chunk, pos, localX, localZ);
                }
                // else: leave as void (air) - default chunk state
            }
        }
    }

    /**
     * Generates a single column of flat terrain.
     */
    private void generateColumn(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int localX, int localZ,
            Heightmap oceanFloor,
            Heightmap worldSurface
    ) {
        int y = getMinY();

        for (BlockState state : layerStates) {
            pos.set(localX, y, localZ);
            chunk.setBlockState(pos, Objects.requireNonNull(state, "layer state"), false);

            // Update heightmaps
            if (!state.isAir()) {
                oceanFloor.update(localX, y, localZ, state);
                worldSurface.update(localX, y, localZ, state);
            }

            y++;
        }
    }

    /**
     * Places barrier blocks for a column outside the arena.
     */
    private void placeBarrierColumn(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            int localX, int localZ
    ) {
        // Place barrier at floor level only (not a full column)
        pos.set(localX, getFloorY() - 1, localZ);
        chunk.setBlockState(pos, Objects.requireNonNull(Blocks.BARRIER.defaultBlockState(), "barrier state"), false);
    }

    @Override
    public int getBaseHeight(
            int x, int z,
            @Nonnull Heightmap.Types type,
            @Nonnull LevelHeightAccessor level,
            @Nonnull RandomState random
    ) {
        if (!positionInArena(x, z)) {
            return getMinY();
        }
        return getFloorY();
    }

    /**
     * Gets the floor Y level.
     */
    public int getFloorY() {
        return resolveFloorY();
    }
}

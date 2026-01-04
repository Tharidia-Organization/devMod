package com.devmod.runtime.generator;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.runtime.biome.ZoneBiomeSource;

/**
 * Dynamic arena chunk generator that supports natural terrain generation.
 *
 * <p>Two modes of operation:</p>
 * <ul>
 *   <li><b>PROXY_WORLDGEN</b>: Delegates to a source dimension's chunk generator
 *       (e.g., minecraft:overworld) to get full worldgen including modded biomes,
 *       carvers, and features. Caves enabled.</li>
 *   <li><b>CONTROLLED_NATURAL</b>: Lightweight DevMod-controlled natural terrain
 *       with basic height variation and biome features. Fallback when proxy unavailable.</li>
 * </ul>
 *
 * <p>Arena mask is applied to create a stable combat ring while allowing
 * natural worldgen outside the ring with edge blending.</p>
 *
 * <p>Determinism: Uses seed derived from questId/partyId/worldSeed for reproducible terrain.</p>
 */
public class DynamicArenaChunkGenerator extends ArenaChunkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicArenaChunkGenerator.class);

    // Pre-cached block states for terrain generation
    @Nonnull private static final BlockState GRASS = Objects.requireNonNull(Blocks.GRASS_BLOCK.defaultBlockState());
    @Nonnull private static final BlockState DIRT = Objects.requireNonNull(Blocks.DIRT.defaultBlockState());
    @Nonnull private static final BlockState STONE = Objects.requireNonNull(Blocks.STONE.defaultBlockState());
    @Nonnull private static final BlockState BEDROCK = Objects.requireNonNull(Blocks.BEDROCK.defaultBlockState());

    public static final MapCodec<DynamicArenaChunkGenerator> CODEC = MapCodec.unit(
            DynamicArenaChunkGenerator::createPlaceholder
    );

    /**
     * Generation mode.
     */
    public enum Mode {
        /** Proxy to source dimension's worldgen */
        PROXY_WORLDGEN,
        /** Controlled natural generation by DevMod */
        CONTROLLED_NATURAL
    }

    /**
     * Biome selection policy.
     *
     * <p>Resolution is handled by {@link BiomePolicyResolver}.</p>
     *
     * @see BiomePolicyResolver#resolve(DynamicConfig, net.minecraft.server.MinecraftServer, ResourceLocation, long)
     */
    public enum BiomePolicy {
        /**
         * Match biome to mob's preferred environment.
         * Uses a lookup table of common mob types to their natural habitats.
         * Falls back to plains for unknown mobs.
         */
        MATCH_MOB,
        /** Use fixed biome from template configuration */
        FIXED,
        /**
         * Random biome from a tag.
         * Deterministically selects from the configured biome tag using the arena seed.
         */
        RANDOM_FROM_TAG
    }

    /**
     * Dynamic terrain configuration.
     */
    public record DynamicConfig(
        Mode mode,
        @Nullable ResourceKey<Level> sourceDimension,
        BiomePolicy biomePolicy,
        @Nullable ResourceLocation fixedBiome,
        @Nullable ResourceLocation biomeTag,
        boolean allowCaves,
        int blendRadius,
        int combatRingRadius
    ) {
        public static DynamicConfig proxyOverworld(int combatRingRadius, int blendRadius) {
            return new DynamicConfig(
                Mode.PROXY_WORLDGEN,
                Level.OVERWORLD,
                BiomePolicy.MATCH_MOB,
                null,
                null,
                true,
                blendRadius,
                combatRingRadius
            );
        }

        public static DynamicConfig controlledNatural(int combatRingRadius, int blendRadius) {
            return new DynamicConfig(
                Mode.CONTROLLED_NATURAL,
                null,
                BiomePolicy.MATCH_MOB,
                null,
                null,
                false,
                blendRadius,
                combatRingRadius
            );
        }
    }

    /**
     * Immutable snapshot of proxy state for thread-safe access.
     * Single volatile reference ensures atomic visibility of all fields.
     */
    private record ProxyState(
        @Nullable ChunkGenerator generator,
        @Nullable RandomState randomState,
        boolean valid
    ) {
        static final ProxyState INVALID = new ProxyState(null, null, false);

        boolean isAvailable() {
            return valid && generator != null && randomState != null;
        }
    }

    private final DynamicConfig config;
    private final long seed;
    private volatile ProxyState proxyState = ProxyState.INVALID;

    /**
     * Creates a dynamic arena chunk generator.
     *
     * @param biomeSource The zone-aware biome source
     * @param bounds Arena bounds
     * @param shape Arena shape
     * @param config Dynamic terrain configuration
     * @param seed Deterministic seed (derived from questId/partyId/worldSeed)
     */
    public DynamicArenaChunkGenerator(
            ZoneBiomeSource biomeSource,
            @Nullable ArenaBounds bounds,
            ArenaZone.ZoneShape shape,
            DynamicConfig config,
            long seed
    ) {
        super(biomeSource, bounds, shape);
        this.config = Objects.requireNonNull(config, "config");
        this.seed = seed;
    }

    private static DynamicArenaChunkGenerator createPlaceholder() {
        return new DynamicArenaChunkGenerator(
                ZoneBiomeSource.createPlaceholder(),
                null,
                ArenaZone.ZoneShape.RECTANGULAR,
                DynamicConfig.controlledNatural(32, 3),
                0L
        );
    }

    /**
     * Initializes the proxy generator from the source dimension.
     * Must be called after the dimension is created and server is available.
     *
     * @param server The Minecraft server
     * @return true if proxy was initialized successfully
     */
    public boolean initializeProxy(MinecraftServer server) {
        if (config.mode() != Mode.PROXY_WORLDGEN) {
            return true; // Not needed for controlled mode
        }

        ResourceKey<Level> sourceDim = config.sourceDimension();
        if (sourceDim == null) {
            LOGGER.warn("[DynamicArena] PROXY_WORLDGEN mode but no sourceDimension configured, falling back to controlled");
            return false;
        }

        ServerLevel sourceLevel = server.getLevel(sourceDim);
        if (sourceLevel == null) {
            LOGGER.warn("[DynamicArena] Source dimension {} not found, falling back to controlled",
                    sourceDim.location());
            return false;
        }

        ChunkGenerator generator = sourceLevel.getChunkSource().getGenerator();
        RandomState randomState = sourceLevel.getChunkSource().randomState();

        // Atomic update via immutable ProxyState
        this.proxyState = new ProxyState(generator, randomState, true);

        LOGGER.info("[DynamicArena] Initialized proxy generator from {} (generator={})",
                sourceDim.location(),
                generator.getClass().getSimpleName());

        return true;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            @Nonnull Blender blender,
            @Nonnull RandomState randomState,
            @Nonnull StructureManager structureManager,
            @Nonnull ChunkAccess chunk
    ) {
        ChunkPos chunkPos = chunk.getPos();

        // Optimization: skip chunks entirely outside expanded arena bounds
        if (!chunkIntersectsExpandedArena(chunkPos)) {
            return CompletableFuture.completedFuture(chunk);
        }

        // Capture proxy state snapshot atomically
        ProxyState proxy = this.proxyState;
        if (config.mode() == Mode.PROXY_WORLDGEN && proxy.isAvailable()) {
            // requireNonNull satisfies @Nullable annotations for the compiler;
            // isAvailable() already verified non-null, so this never throws
            ChunkGenerator gen = Objects.requireNonNull(proxy.generator());
            RandomState state = Objects.requireNonNull(proxy.randomState());
            // Delegate to proxy generator for full worldgen
            // NOTE: applyArenaMask is intentionally NOT called here - ArenaBuilder handles arena construction
            // after worldgen. Add applyArenaMask call here if early terrain flattening is needed.
            return gen.fillFromNoise(blender, state, structureManager, chunk);
        } else {
            // Controlled natural generation
            generateControlledNatural(chunk, randomState);
            // NOTE: Arena mask not applied - ArenaBuilder handles arena construction after worldgen
            return CompletableFuture.completedFuture(chunk);
        }
    }

    /**
     * Checks if chunk intersects expanded arena bounds (arena + blend radius).
     */
    private boolean chunkIntersectsExpandedArena(ChunkPos chunkPos) {
        if (arenaBounds == null) {
            return true;
        }

        int expandedRadius = config.combatRingRadius() + config.blendRadius() + 32; // Extra buffer for worldgen
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        // Simple AABB check with expanded bounds
        return chunkMaxX >= arenaBounds.centerX() - expandedRadius &&
               chunkMinX <= arenaBounds.centerX() + expandedRadius &&
               chunkMaxZ >= arenaBounds.centerZ() - expandedRadius &&
               chunkMinZ <= arenaBounds.centerZ() + expandedRadius;
    }

    /**
     * Generates controlled natural terrain (fallback mode).
     * Uses Perlin-like height variation with biome-appropriate surface.
     */
    @SuppressWarnings("UnusedVariable") // randomState reserved for future procedural variation
    private void generateControlledNatural(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = minX + localX;
                int worldZ = minZ + localZ;

                // Multi-octave noise for less repetitive terrain (fallback mode)
                // Combines multiple frequencies to reduce obvious patterns
                int baseHeight = 64;
                int heightVariation = calculateNoiseHeight(worldX, worldZ, seed);
                int surfaceY = baseHeight + heightVariation;

                // Generate terrain column
                for (int y = getMinY(); y <= surfaceY; y++) {
                    pos.set(localX, y, localZ);

                    // Determine block type based on depth (using pre-cached states)
                    final BlockState state;
                    if (y == surfaceY) {
                        state = GRASS;
                    } else if (y >= surfaceY - 3) {
                        state = DIRT;
                    } else if (y <= getMinY() + 2) {
                        state = BEDROCK;
                    } else {
                        state = STONE;
                    }

                    chunk.setBlockState(pos, state, false);
                    oceanFloor.update(localX, y, localZ, state);
                    worldSurface.update(localX, y, localZ, state);
                }
            }
        }
    }

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
        ProxyState proxy = this.proxyState;
        if (config.allowCaves() && proxy.isAvailable()) {
            ChunkGenerator gen = Objects.requireNonNull(proxy.generator());
            RandomState state = Objects.requireNonNull(proxy.randomState());
            // Delegate carving to proxy generator for caves
            // IMPORTANT: Use the parameter seed (world seed) for consistency with proxy terrain
            // Using this.seed would cause caves to be misaligned with surface features
            gen.applyCarvers(level, seed, state, biomeManager, structureManager, chunk, step);
        }
        // Otherwise: no carvers (controlled mode doesn't have caves)
    }

    @Override
    public void buildSurface(
            @Nonnull WorldGenRegion level,
            @Nonnull StructureManager structureManager,
            @Nonnull RandomState random,
            @Nonnull ChunkAccess chunk
    ) {
        ProxyState proxy = this.proxyState;
        if (config.mode() == Mode.PROXY_WORLDGEN && proxy.isAvailable()) {
            ChunkGenerator gen = Objects.requireNonNull(proxy.generator());
            gen.buildSurface(level, structureManager, random, chunk);
        }
        // Controlled mode: surface is handled in fillFromNoise
    }

    @Override
    public int getBaseHeight(
            int x, int z,
            @Nonnull Heightmap.Types type,
            @Nonnull LevelHeightAccessor level,
            @Nonnull RandomState random
    ) {
        ProxyState proxy = this.proxyState;
        if (config.mode() == Mode.PROXY_WORLDGEN && proxy.isAvailable()) {
            ChunkGenerator gen = Objects.requireNonNull(proxy.generator());
            RandomState state = Objects.requireNonNull(proxy.randomState());
            return gen.getBaseHeight(x, z, type, level, state);
        }

        // Controlled mode: use same noise as generateControlledNatural
        return 64 + calculateNoiseHeight(x, z, seed);
    }

    public DynamicConfig getConfig() {
        return config;
    }

    public long getSeed() {
        return seed;
    }

    /**
     * Calculates height variation using multi-octave gradient noise.
     * Uses hash-based gradient noise for aperiodic, natural-looking terrain.
     *
     * @param x World X coordinate
     * @param z World Z coordinate
     * @param noiseSeed Seed for deterministic generation
     * @return Height variation in blocks (-8 to +8 range)
     */
    private static int calculateNoiseHeight(int x, int z, long noiseSeed) {
        double total = 0;
        double amplitude = 1.0;
        // Base frequency 0.02 = wavelength ~50 blocks. For a 64-block radius arena,
        // this gives 2-3 gentle hills - intentionally smooth for combat visibility.
        // Higher frequency would create choppy terrain that impedes gameplay.
        double frequency = 0.02;
        double maxValue = 0;

        // 4 octaves: provides good variety without excessive cost.
        // Each octave adds finer detail: 50→25→12→6 block wavelengths.
        // The 4th octave adds ~1 block detail, worthwhile for less blocky terrain.
        // 31337: arbitrary large prime-like offset to decorrelate octave patterns.
        for (int i = 0; i < 4; i++) {
            total += valueNoise(x * frequency, z * frequency, noiseSeed + i * 31337L) * amplitude;
            maxValue += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }

        // Normalize to -8 to +8 range
        return (int) ((total / maxValue) * 8);
    }

    /**
     * Value noise with smooth interpolation.
     * Aperiodic - uses hash grid with bilinear interpolation and smoothstep.
     * Note: This is VALUE noise (random scalars at grid points), not GRADIENT noise
     * (Perlin-style with gradients). Simpler and sufficient for terrain variation.
     *
     * @param x Scaled X coordinate
     * @param z Scaled Z coordinate
     * @param seed Noise seed
     * @return Value between -1 and 1
     */
    private static double valueNoise(double x, double z, long seed) {
        // Grid cell coordinates (floor for correct negative handling)
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        // Fractional position within cell (always positive after floor)
        double fx = x - x0;
        double fz = z - z0;

        // Smoothstep (3t² - 2t³) for C¹ continuous interpolation.
        // Perlin's improved curve (6t⁵ - 15t⁴ + 10t³) provides C² continuity
        // but C¹ is sufficient here - terrain is low-frequency and quantized to int.
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);

        // Hash values at grid corners
        double n00 = hashToDouble(x0, z0, seed);
        double n10 = hashToDouble(x1, z0, seed);
        double n01 = hashToDouble(x0, z1, seed);
        double n11 = hashToDouble(x1, z1, seed);

        // Bilinear interpolation
        double nx0 = n00 + sx * (n10 - n00);
        double nx1 = n01 + sx * (n11 - n01);
        return nx0 + sz * (nx1 - nx0);
    }

    /**
     * Hash coordinates to a double in range [-1, 1).
     * Uses MurmurHash3 finalizer (fmix64) for excellent avalanche properties.
     * Initial mixing uses splitmix64-style constants.
     * Note: Upper bound is exclusive (1.0 never produced), which is
     * standard for noise functions and has no visible effect.
     */
    private static double hashToDouble(int x, int z, long seed) {
        // MurmurHash3 fmix64 finalizer - excellent avalanche
        long hash = seed ^ (x * 0x517cc1b727220a95L) ^ (z * 0x5851f42d4c957f2dL);
        hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL;
        hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
        hash = hash ^ (hash >>> 33);
        // Convert to [-1, 1): Keep upper 53 bits of the 64-bit hash.
        // >>> 11 shifts them into positions 0-52, discarding noisy low bits.
        // IEEE 754 double has 53-bit mantissa, so division gives full-precision [0, 1).
        return ((hash >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
    }

    /**
     * Invalidates the proxy generator.
     * Called automatically when the source dimension is unloaded.
     *
     * <p>Hooked via {@link com.devmod.events.CommonModEvents#onLevelUnload} which
     * notifies {@link com.devmod.runtime.DynamicDimensionManager#onDimensionUnload}.</p>
     *
     * <p>In practice, source dimensions (like overworld) are rarely unloaded during
     * normal gameplay, so this is primarily infrastructure for edge cases.</p>
     */
    public void invalidateProxy() {
        this.proxyState = ProxyState.INVALID;
        LOGGER.debug("[DynamicArena] Proxy generator invalidated, falling back to controlled mode");
    }
}

package com.devmod.area.builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import com.devmod.DevMod;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;

/**
 * Generates real biome terrain within area bounds.
 * Coordinates terrain, caves, and features using dedicated helper classes.
 *
 * @see BiomeTerrainCalculator for terrain height calculations
 * @see BiomeCaveGenerator for cave carving
 * @see BiomeFeatureGenerator for trees and vegetation
 * @see BiomeBlockProvider for biome-specific blocks
 */
public final class BiomeAreaGenerator {

    private BiomeAreaGenerator() {}

    // ============================================================================
    // MAIN GENERATION
    // ============================================================================

    /**
     * Generates biome terrain for an area.
     *
     * @param level   The server level
     * @param area    The area definition (must have biome config)
     * @param blocks  The set of floor positions to fill
     * @return Number of blocks placed
     */
    public static int generate(@Nonnull ServerLevel level, @Nonnull AreaDefinition area, @Nonnull Set<BlockPos> blocks) {
        BiomeGenerationConfig config = area.biomeConfig();
        if (config == null) {
            DevMod.LOGGER.warn("[Area] BiomeAreaGenerator called with null biome config");
            return 0;
        }

        Set<BlockPos> floorPositions = blocks;
        if (floorPositions.isEmpty()) {
            DevMod.LOGGER.warn("[BiomeAreaGenerator] Empty floor positions for area '{}'; regenerating from definition",
                area.name());
            floorPositions = AreaShapeGenerator.generateFloor(
                Objects.requireNonNull(area.shape()),
                Objects.requireNonNull(area.centerPosition()),
                Objects.requireNonNull(area.dimensions()),
                area.customShapeNbt()
            );
            if (floorPositions.isEmpty()) {
                DevMod.LOGGER.warn("[BiomeAreaGenerator] No floor positions available for area '{}'", area.name());
                return 0;
            }
        }

        // HIGH-02 fix: Validate biome config data before processing
        if (config.biomeId() == null || config.biomeId().getPath().length() > 256) {
            DevMod.LOGGER.warn("[Area] Invalid biome ID in config: {}", config.biomeId());
            return 0;
        }

        long seed = config.getEffectiveSeed();
        RandomSource random = RandomSource.create(seed);
        SimplexNoise noise = new SimplexNoise(Objects.requireNonNull(random));

        TerrainStyle style = config.terrainStyle();
        int baseY = area.dimensions().floorY();
        int maxHeight = area.dimensions().height();
        int seaLevel = resolveSeaLevel(config.seaLevel(), baseY, maxHeight);

        // Detailed logging for debugging
        DevMod.LOGGER.debug("[BiomeAreaGenerator] Generating terrain for area '{}':", area.name());
        DevMod.LOGGER.debug("[BiomeAreaGenerator]   biomeId={}", config.biomeId());
        DevMod.LOGGER.debug("[BiomeAreaGenerator]   terrainStyle={}", style);
        DevMod.LOGGER.debug("[BiomeAreaGenerator]   seed={} (effective={})", config.seed(), seed);
        DevMod.LOGGER.debug("[BiomeAreaGenerator]   dimensions={}x{}x{}, baseY={}, maxHeight={}",
            area.dimensions().width(), area.dimensions().length(), area.dimensions().height(), baseY, maxHeight);
        DevMod.LOGGER.debug("[BiomeAreaGenerator]   seaLevel={}, generateFeatures={}", seaLevel, config.generateFeatures());

        int blocksPlaced = 0;
        int minTerrainY = Integer.MAX_VALUE;
        int maxTerrainY = Integer.MIN_VALUE;
        int validHeights = 0;

        // CRIT-02 fix: Sort positions for deterministic RandomSource consumption
        List<BlockPos> sortedPositions = AreaBlockMapGenerator.sortPositionsDeterministically(floorPositions);

        for (BlockPos floorPos : sortedPositions) {
            int terrainHeight = BiomeTerrainCalculator.calculateTerrainHeight(
                floorPos.getX(), floorPos.getZ(),
                noise, style, baseY, maxHeight
            );

            // Track height statistics for debugging
            if (terrainHeight >= 0) {
                minTerrainY = Math.min(minTerrainY, terrainHeight);
                maxTerrainY = Math.max(maxTerrainY, terrainHeight);
                validHeights++;
            }

            blocksPlaced += generateColumn(
                level, floorPos, terrainHeight, baseY, seaLevel,
                config, random
            );
        }

        // Log terrain height statistics for comparison with preview
        if (validHeights > 0) {
            DevMod.LOGGER.debug("[BiomeAreaGenerator]   terrainHeights: {} positions, minY={}, maxY={}, range={}",
                validHeights, minTerrainY, maxTerrainY, maxTerrainY - minTerrainY);
        }

        // Carve caves if structures enabled (repurposing generateStructures flag for caves)
        if (config.generateStructures()) {
            int caveBlocks = BiomeCaveGenerator.carveCavesInLevel(level, floorPositions, noise, baseY, maxHeight);
            blocksPlaced += caveBlocks;
            DevMod.LOGGER.debug("[BiomeAreaGenerator]   caves: {} blocks carved", caveBlocks);
        }

        // Generate features if enabled
        if (config.generateFeatures()) {
            int featureBlocks = BiomeFeatureGenerator.generateFeatures(level, floorPositions, config, random, baseY, maxHeight);
            blocksPlaced += featureBlocks;
            DevMod.LOGGER.debug("[BiomeAreaGenerator]   features: {} blocks placed", featureBlocks);
        }

        return blocksPlaced;
    }

    /**
     * Pre-computes all blocks for biome terrain without placing them.
     * Use this for multi-tick building to avoid lag.
     *
     * @param area   The area definition (must have biome config)
     * @param blocks The set of floor positions to fill
     * @return Map of positions to block states to be placed
     */
    @Nonnull
    public static Map<BlockPos, BlockState> generateBlockMap(@Nonnull AreaDefinition area, @Nonnull Set<BlockPos> blocks) {
        // HIGH-02 fix: Use LinkedHashMap to preserve deterministic insertion order for resume
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();

        BiomeGenerationConfig config = area.biomeConfig();
        if (config == null) {
            DevMod.LOGGER.warn("[Area] BiomeAreaGenerator.generateBlockMap called with null biome config");
            return result;
        }

        Set<BlockPos> floorPositions = blocks;
        if (floorPositions.isEmpty()) {
            DevMod.LOGGER.warn("[BiomeAreaGenerator] Empty floor positions for area '{}'; regenerating from definition",
                area.name());
            floorPositions = AreaShapeGenerator.generateFloor(
                Objects.requireNonNull(area.shape()),
                Objects.requireNonNull(area.centerPosition()),
                Objects.requireNonNull(area.dimensions()),
                area.customShapeNbt()
            );
            if (floorPositions.isEmpty()) {
                DevMod.LOGGER.warn("[BiomeAreaGenerator] No floor positions available for area '{}'", area.name());
                return result;
            }
        }

        long seed = config.getEffectiveSeed();
        RandomSource random = RandomSource.create(seed);
        SimplexNoise noise = new SimplexNoise(Objects.requireNonNull(random));

        TerrainStyle style = config.terrainStyle();
        int baseY = area.dimensions().floorY();
        int maxHeight = area.dimensions().height();
        int seaLevel = resolveSeaLevel(config.seaLevel(), baseY, maxHeight);

        DevMod.LOGGER.debug("[BiomeAreaGenerator] Pre-computing block map for area '{}' ({} floor positions)",
            area.name(), floorPositions.size());

        // CRIT-02 fix: Sort positions for deterministic RandomSource consumption
        List<BlockPos> sortedPositions = AreaBlockMapGenerator.sortPositionsDeterministically(floorPositions);

        for (BlockPos floorPos : sortedPositions) {
            int terrainHeight = BiomeTerrainCalculator.calculateTerrainHeight(
                floorPos.getX(), floorPos.getZ(),
                noise, style, baseY, maxHeight
            );

            computeColumnBlocks(result, floorPos, terrainHeight, baseY, seaLevel, config, random);
        }

        // Carve caves if structures enabled (repurposing generateStructures flag for caves)
        if (config.generateStructures()) {
            int caveBlocksCarved = BiomeCaveGenerator.carveCavesInBlockMap(result, floorPositions, noise, baseY, maxHeight);
            DevMod.LOGGER.debug("[BiomeAreaGenerator]   caves: {} blocks carved", caveBlocksCarved);
        }

        // Pre-compute features if enabled
        if (config.generateFeatures()) {
            BiomeFeatureGenerator.computeFeatureBlocks(result, floorPositions, config, random, baseY, maxHeight);
        }

        DevMod.LOGGER.debug("[BiomeAreaGenerator] Pre-computed {} blocks for area '{}'",
            result.size(), area.name());

        return result;
    }

    // ============================================================================
    // COLUMN GENERATION
    // ============================================================================

    /**
     * Generates a vertical column of terrain blocks.
     */
    private static int generateColumn(ServerLevel level, BlockPos basePos, int terrainHeight,
                                      int baseY, int seaLevel,
                                      BiomeGenerationConfig config, RandomSource random) {
        if (terrainHeight < baseY) {
            return 0; // Skip (floating islands gap)
        }

        int blocksPlaced = 0;

        // Get biome-appropriate blocks
        BiomeBlockProvider.BiomeBlocks blocks = BiomeBlockProvider.getBiomeBlocks(config);

        for (int y = baseY; y <= terrainHeight; y++) {
            BlockPos pos = new BlockPos(basePos.getX(), y, basePos.getZ());
            BlockState state;

            int depthFromSurface = terrainHeight - y;

            if (y == terrainHeight) {
                // Top layer
                if (y < seaLevel - 1) {
                    // Underwater
                    state = blocks.underwaterTop();
                } else {
                    state = blocks.topBlock();
                }
            } else if (depthFromSurface <= 3) {
                // Sub-surface (1-3 blocks deep)
                state = blocks.subSurface();
            } else if (config.generateOres() && BiomeBlockProvider.shouldGenerateOre(y, random)) {
                // Ore generation
                state = BiomeBlockProvider.getOreForDepth(y, random);
            } else {
                // Deep underground
                state = blocks.deepBlock();
            }

            level.setBlock(pos, Objects.requireNonNull(state), 2);
            blocksPlaced++;
        }

        // Fill water up to sea level if below
        if (terrainHeight < seaLevel - 1) {
            for (int y = terrainHeight + 1; y < seaLevel; y++) {
                BlockPos waterPos = new BlockPos(basePos.getX(), y, basePos.getZ());
                level.setBlock(waterPos, Objects.requireNonNull(Blocks.WATER.defaultBlockState()), 2);
                blocksPlaced++;
            }
        }

        return blocksPlaced;
    }

    /**
     * Computes blocks for a column without placing them.
     */
    private static void computeColumnBlocks(Map<BlockPos, BlockState> result, BlockPos basePos, int terrainHeight,
                                            int baseY, int seaLevel,
                                            BiomeGenerationConfig config, RandomSource random) {
        if (terrainHeight < baseY) {
            return; // Skip (floating islands gap)
        }

        BiomeBlockProvider.BiomeBlocks blocks = BiomeBlockProvider.getBiomeBlocks(config);

        for (int y = baseY; y <= terrainHeight; y++) {
            BlockPos pos = new BlockPos(basePos.getX(), y, basePos.getZ());
            BlockState state;

            int depthFromSurface = terrainHeight - y;

            if (y == terrainHeight) {
                if (y < seaLevel - 1) {
                    state = blocks.underwaterTop();
                } else {
                    state = blocks.topBlock();
                }
            } else if (depthFromSurface <= 3) {
                state = blocks.subSurface();
            } else if (config.generateOres() && BiomeBlockProvider.shouldGenerateOre(y, random)) {
                state = BiomeBlockProvider.getOreForDepth(y, random);
            } else {
                state = blocks.deepBlock();
            }

            result.put(pos, Objects.requireNonNull(state));
        }

        // Fill water up to sea level if below
        if (terrainHeight < seaLevel - 1) {
            for (int y = terrainHeight + 1; y < seaLevel; y++) {
                BlockPos waterPos = new BlockPos(basePos.getX(), y, basePos.getZ());
                result.put(waterPos, Blocks.WATER.defaultBlockState());
            }
        }
    }

    private static int resolveSeaLevel(int seaLevel, int baseY, int maxHeight) {
        int maxY = baseY + maxHeight - 1;
        if (seaLevel < baseY || seaLevel > maxY) {
            return baseY;
        }
        return seaLevel;
    }
}

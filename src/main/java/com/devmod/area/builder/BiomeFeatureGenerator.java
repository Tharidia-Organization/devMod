package com.devmod.area.builder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.data.BiomeGenerationConfig;

/**
 * Generates biome-appropriate features (trees, vegetation, mushrooms, cacti).
 * Supports both direct level placement and pre-computed block maps.
 * Extracted from BiomeAreaGenerator for better modularity.
 */
public final class BiomeFeatureGenerator {

    private BiomeFeatureGenerator() {}

    // ============================================================================
    // BIOME FEATURE TYPE
    // ============================================================================

    /**
     * Biome feature types for vegetation and tree selection.
     */
    public enum BiomeFeatureType {
        PLAINS, MEADOW, FOREST, BIRCH_FOREST, DARK_FOREST, TAIGA, JUNGLE, SWAMP,
        DESERT, BADLANDS, SAVANNA, SNOWY, MUSHROOM, CHERRY, MANGROVE,
        NETHER, END, OCEAN, CAVE
    }

    // ============================================================================
    // PUBLIC API - LEVEL-BASED
    // ============================================================================

    /**
     * Generates biome-appropriate features directly in the level.
     * Used for immediate (non-multi-tick) builds.
     *
     * @param level          The server level
     * @param floorBlocks    Floor positions defining the area
     * @param config         Biome generation config
     * @param random         RandomSource for generation
     * @param baseY          Base Y level of the area
     * @param maxHeight      Maximum height of the area
     * @return Number of blocks placed
     */
    public static int generateFeatures(ServerLevel level, Set<BlockPos> floorBlocks,
                                        BiomeGenerationConfig config, RandomSource random,
                                        int baseY, int maxHeight) {
        int blocksPlaced = 0;
        String biomePath = config.biomeId().getPath().toLowerCase(Locale.ROOT);
        BiomeFeatureType featureType = getBiomeFeatureType(biomePath);

        // CRIT-02 fix: Sort positions for deterministic RandomSource consumption
        List<BlockPos> sortedBlocks = AreaBlockMapGenerator.sortPositionsDeterministically(
            Objects.requireNonNull(floorBlocks));

        // Collect valid surface positions
        java.util.List<BlockPos> surfacePositions = new java.util.ArrayList<>();
        for (BlockPos floorPos : sortedBlocks) {
            BlockPos surfacePos = findSurfacePos(level, floorPos, baseY, maxHeight);
            if (surfacePos != null) {
                surfacePositions.add(surfacePos);
            }
        }

        if (surfacePositions.isEmpty()) return 0;

        // Generate trees based on biome (lower density)
        int treeCount = Math.max(1, surfacePositions.size() / 100); // ~1% tree density
        for (int i = 0; i < treeCount && !surfacePositions.isEmpty(); i++) {
            int idx = random.nextInt(surfacePositions.size());
            BlockPos treePos = surfacePositions.get(idx);
            blocksPlaced += generateTree(level, treePos, featureType, random, floorBlocks, baseY, maxHeight);
        }

        // Generate vegetation (grass, flowers, etc.)
        for (BlockPos surfacePos : surfacePositions) {
            if (random.nextFloat() > 0.15f) continue; // 15% vegetation coverage

            BlockPos featurePos = Objects.requireNonNull(surfacePos.above());

            // Skip if not air above
            if (!level.getBlockState(featurePos).isAir()) continue;
            if (!isWithinBounds(featurePos, floorBlocks, baseY, maxHeight)) continue;

            blocksPlaced += generateVegetation(level, featurePos, featureType, random);
        }

        return blocksPlaced;
    }

    // ============================================================================
    // PUBLIC API - BLOCK MAP-BASED
    // ============================================================================

    /**
     * Pre-computes feature blocks without placing them.
     * Used for multi-tick builds.
     *
     * @param result      The block map to add features to
     * @param floorBlocks Floor positions defining the area
     * @param config      Biome generation config
     * @param random      RandomSource for generation
     * @param baseY       Base Y level of the area
     * @param maxHeight   Maximum height of the area
     */
    public static void computeFeatureBlocks(Map<BlockPos, BlockState> result, Set<BlockPos> floorBlocks,
                                             BiomeGenerationConfig config, RandomSource random,
                                             int baseY, int maxHeight) {
        String biomePath = config.biomeId().getPath().toLowerCase(Locale.ROOT);
        BiomeFeatureType featureType = getBiomeFeatureType(biomePath);

        // CRIT-02 fix: Sort positions for deterministic RandomSource consumption
        List<BlockPos> sortedFloorBlocks = AreaBlockMapGenerator.sortPositionsDeterministically(
            Objects.requireNonNull(floorBlocks));

        // Collect valid surface positions from the pre-computed terrain
        java.util.List<BlockPos> surfacePositions = new java.util.ArrayList<>();
        for (BlockPos floorPos : sortedFloorBlocks) {
            // Find highest block in result at this XZ
            BlockPos highest = null;
            for (int y = baseY + maxHeight; y >= baseY; y--) {
                BlockPos checkPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());
                if (result.containsKey(checkPos)) {
                    highest = checkPos;
                    break;
                }
            }
            if (highest != null) {
                surfacePositions.add(highest);
            }
        }

        if (surfacePositions.isEmpty()) return;

        // Generate trees
        int treeCount = Math.max(1, surfacePositions.size() / 100);
        for (int i = 0; i < treeCount && !surfacePositions.isEmpty(); i++) {
            int idx = random.nextInt(surfacePositions.size());
            BlockPos treePos = surfacePositions.get(idx);
            computeTreeBlocks(result, treePos, featureType, random, floorBlocks, baseY, maxHeight);
        }

        // Generate vegetation
        for (BlockPos surfacePos : surfacePositions) {
            if (random.nextFloat() > 0.15f) continue;

            BlockPos featurePos = Objects.requireNonNull(surfacePos.above());
            if (!isWithinBounds(featurePos, floorBlocks, baseY, maxHeight)) continue;
            if (result.containsKey(featurePos)) continue; // Already has a block

            BlockState vegState = getVegetationState(featureType, random);
            if (vegState != null) {
                result.put(featurePos, vegState);
            }
        }
    }

    // ============================================================================
    // FEATURE TYPE DETECTION
    // ============================================================================

    /**
     * Determines the feature type for a biome based on its path.
     *
     * @param biomePath Biome resource location path (lowercase)
     * @return BiomeFeatureType for vegetation/tree selection
     */
    public static BiomeFeatureType getBiomeFeatureType(String biomePath) {
        if (biomePath.contains("mushroom")) return BiomeFeatureType.MUSHROOM;
        if (biomePath.contains("cherry")) return BiomeFeatureType.CHERRY;
        if (biomePath.contains("mangrove")) return BiomeFeatureType.MANGROVE;
        if (biomePath.contains("jungle")) return BiomeFeatureType.JUNGLE;
        if (biomePath.contains("dark_forest")) return BiomeFeatureType.DARK_FOREST;
        if (biomePath.contains("birch")) return BiomeFeatureType.BIRCH_FOREST;
        if (biomePath.contains("taiga") || biomePath.contains("grove")) return BiomeFeatureType.TAIGA;
        if (biomePath.contains("swamp")) return BiomeFeatureType.SWAMP;
        if (biomePath.contains("desert")) return BiomeFeatureType.DESERT;
        if (biomePath.contains("badlands")) return BiomeFeatureType.BADLANDS;
        if (biomePath.contains("savanna")) return BiomeFeatureType.SAVANNA;
        if (biomePath.contains("meadow")) return BiomeFeatureType.MEADOW;
        if (biomePath.contains("snowy") || biomePath.contains("frozen") || biomePath.contains("ice")) return BiomeFeatureType.SNOWY;
        if (biomePath.contains("forest")) return BiomeFeatureType.FOREST;
        if (biomePath.contains("nether") || biomePath.contains("soul") || biomePath.contains("basalt") ||
            biomePath.contains("crimson") || biomePath.contains("warped")) return BiomeFeatureType.NETHER;
        if (biomePath.contains("end")) return BiomeFeatureType.END;
        if (biomePath.contains("ocean") || biomePath.contains("river") || biomePath.contains("beach")) return BiomeFeatureType.OCEAN;
        if (biomePath.contains("cave") || biomePath.contains("deep_dark") || biomePath.contains("lush")) return BiomeFeatureType.CAVE;
        return BiomeFeatureType.PLAINS;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Checks if a position is within the area bounds.
     */
    static boolean isWithinBounds(BlockPos pos, Set<BlockPos> floorPositions, int baseY, int maxHeight) {
        if (pos.getY() < baseY || pos.getY() >= baseY + maxHeight) {
            return false;
        }
        BlockPos floorKey = new BlockPos(pos.getX(), baseY, pos.getZ());
        return floorPositions.contains(floorKey);
    }

    /**
     * Finds the surface position above a floor position in a level.
     */
    @Nullable
    private static BlockPos findSurfacePos(ServerLevel level, BlockPos floorPos, int baseY, int maxHeight) {
        int topY = baseY + maxHeight - 1;
        for (int y = topY; y >= baseY; y--) {
            BlockPos checkPos = new BlockPos(floorPos.getX(), y, floorPos.getZ());
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && level.getBlockState(Objects.requireNonNull(checkPos.above())).isAir()) {
                return checkPos;
            }
        }
        return null;
    }

    // ============================================================================
    // TREE GENERATION - LEVEL-BASED
    // ============================================================================

    private static int generateTree(ServerLevel level, BlockPos pos, BiomeFeatureType type, RandomSource random,
                                    Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        BlockPos treeBase = Objects.requireNonNull(pos.above());
        if (!isWithinBounds(treeBase, floorBlocks, baseY, maxHeight)) return 0;
        if (!level.getBlockState(treeBase).isAir()) return 0;

        Block log;
        Block leaves;
        int minTreeHeight;
        int maxTreeHeight;

        switch (type) {
            case BIRCH_FOREST -> {
                log = Blocks.BIRCH_LOG;
                leaves = Blocks.BIRCH_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 7;
            }
            case TAIGA, SNOWY -> {
                log = Blocks.SPRUCE_LOG;
                leaves = Blocks.SPRUCE_LEAVES;
                minTreeHeight = 6;
                maxTreeHeight = 10;
            }
            case JUNGLE -> {
                log = Blocks.JUNGLE_LOG;
                leaves = Blocks.JUNGLE_LEAVES;
                minTreeHeight = 8;
                maxTreeHeight = 12;
            }
            case DARK_FOREST -> {
                log = Blocks.DARK_OAK_LOG;
                leaves = Blocks.DARK_OAK_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case SAVANNA -> {
                log = Blocks.ACACIA_LOG;
                leaves = Blocks.ACACIA_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case CHERRY -> {
                log = Blocks.CHERRY_LOG;
                leaves = Blocks.CHERRY_LEAVES;
                minTreeHeight = 4;
                maxTreeHeight = 6;
            }
            case MANGROVE -> {
                log = Blocks.MANGROVE_LOG;
                leaves = Blocks.MANGROVE_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case MUSHROOM -> {
                return generateGiantMushroom(level, treeBase, random, floorBlocks, baseY, maxHeight);
            }
            case DESERT, BADLANDS -> {
                return generateCactus(level, treeBase, random, floorBlocks, baseY, maxHeight);
            }
            case NETHER -> {
                return generateNetherVegetation(level, pos, random, floorBlocks, baseY, maxHeight);
            }
            case END, OCEAN, CAVE -> {
                return 0;
            }
            default -> {
                log = Blocks.OAK_LOG;
                leaves = Blocks.OAK_LEAVES;
                minTreeHeight = 4;
                maxTreeHeight = 6;
            }
        }

        int treeHeight = minTreeHeight + random.nextInt(maxTreeHeight - minTreeHeight + 1);
        int maxAllowedHeight = baseY + maxHeight - treeBase.getY();
        if (maxAllowedHeight <= 0) return 0;
        treeHeight = Math.min(treeHeight, maxAllowedHeight);

        return buildSimpleTree(level, treeBase, log, leaves, treeHeight, floorBlocks, baseY, maxHeight);
    }

    private static int buildSimpleTree(ServerLevel level, BlockPos base, Block log, Block leaves, int height,
                                       Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        int blocksPlaced = 0;

        // Trunk
        for (int y = 0; y < height; y++) {
            BlockPos trunkPos = Objects.requireNonNull(base.above(y));
            if (isWithinBounds(trunkPos, floorBlocks, baseY, maxHeight)) {
                level.setBlock(trunkPos, Objects.requireNonNull(log.defaultBlockState()), 2);
                blocksPlaced++;
            }
        }

        // Leaves - simple sphere shape
        BlockPos leafCenter = base.above(height - 2);
        int leafRadius = 2;

        for (int dx = -leafRadius; dx <= leafRadius; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                    if (Math.abs(dx) == leafRadius && Math.abs(dz) == leafRadius) continue; // Corner cut
                    BlockPos leafPos = Objects.requireNonNull(leafCenter.offset(dx, dy, dz));
                    if (level.getBlockState(leafPos).isAir() &&
                        isWithinBounds(leafPos, floorBlocks, baseY, maxHeight)) {
                        level.setBlock(leafPos, Objects.requireNonNull(leaves.defaultBlockState()), 2);
                        blocksPlaced++;
                    }
                }
            }
        }

        return blocksPlaced;
    }

    private static int generateGiantMushroom(ServerLevel level, BlockPos base, RandomSource random,
                                             Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        if (!isWithinBounds(base, floorBlocks, baseY, maxHeight)) return 0;

        int blocksPlaced = 0;
        boolean isBrown = random.nextBoolean();
        Block stem = Blocks.MUSHROOM_STEM;
        Block cap = isBrown ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK;

        int height = 5 + random.nextInt(3);
        int maxStemHeight = baseY + maxHeight - base.getY();
        if (maxStemHeight <= 0) return 0;
        height = Math.min(height, maxStemHeight);

        // Stem
        for (int y = 0; y < height; y++) {
            BlockPos stemPos = Objects.requireNonNull(base.above(y));
            if (isWithinBounds(stemPos, floorBlocks, baseY, maxHeight)) {
                level.setBlock(stemPos, Objects.requireNonNull(stem.defaultBlockState()), 2);
                blocksPlaced++;
            }
        }

        // Cap
        BlockPos capCenter = base.above(height);
        int capRadius = isBrown ? 3 : 2;

        for (int dx = -capRadius; dx <= capRadius; dx++) {
            for (int dz = -capRadius; dz <= capRadius; dz++) {
                if (Math.abs(dx) == capRadius && Math.abs(dz) == capRadius) continue;
                BlockPos capPos = Objects.requireNonNull(capCenter.offset(dx, 0, dz));
                if (level.getBlockState(capPos).isAir() &&
                    isWithinBounds(capPos, floorBlocks, baseY, maxHeight)) {
                    level.setBlock(capPos, Objects.requireNonNull(cap.defaultBlockState()), 2);
                    blocksPlaced++;
                }
            }
        }

        return blocksPlaced;
    }

    private static int generateCactus(ServerLevel level, BlockPos base, RandomSource random,
                                      Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        if (!isWithinBounds(base, floorBlocks, baseY, maxHeight)) return 0;

        int height = 1 + random.nextInt(3);
        int maxCactusHeight = baseY + maxHeight - base.getY();
        if (maxCactusHeight <= 0) return 0;
        height = Math.min(height, maxCactusHeight);
        int blocksPlaced = 0;

        for (int y = 0; y < height; y++) {
            BlockPos cactusPos = Objects.requireNonNull(base.above(y));
            if (isWithinBounds(cactusPos, floorBlocks, baseY, maxHeight)) {
                level.setBlock(cactusPos, Objects.requireNonNull(Blocks.CACTUS.defaultBlockState()), 2);
                blocksPlaced++;
            }
        }

        return blocksPlaced;
    }

    private static int generateNetherVegetation(ServerLevel level, BlockPos pos, RandomSource random,
                                                Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        BlockPos above = Objects.requireNonNull(pos.above());

        if (!level.getBlockState(above).isAir()) return 0;
        if (!isWithinBounds(above, floorBlocks, baseY, maxHeight)) return 0;

        float r = random.nextFloat();
        BlockState feature;

        if (r < 0.3f) {
            feature = Blocks.CRIMSON_FUNGUS.defaultBlockState();
        } else if (r < 0.6f) {
            feature = Blocks.WARPED_FUNGUS.defaultBlockState();
        } else if (r < 0.8f) {
            feature = Blocks.CRIMSON_ROOTS.defaultBlockState();
        } else {
            feature = Blocks.WARPED_ROOTS.defaultBlockState();
        }

        level.setBlock(above, Objects.requireNonNull(feature), 2);
        return 1;
    }

    private static int generateVegetation(ServerLevel level, BlockPos pos,
                                          BiomeFeatureType type, RandomSource random) {
        BlockState feature = getVegetationState(type, random);

        if (feature != null) {
            level.setBlock(Objects.requireNonNull(pos), feature, 2);
            return 1;
        }
        return 0;
    }

    // ============================================================================
    // TREE GENERATION - BLOCK MAP-BASED
    // ============================================================================

    private static void computeTreeBlocks(Map<BlockPos, BlockState> result, BlockPos pos,
                                          BiomeFeatureType type, RandomSource random,
                                          Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        BlockPos treeBase = Objects.requireNonNull(pos.above());
        if (!isWithinBounds(treeBase, floorBlocks, baseY, maxHeight)) return;
        if (result.containsKey(treeBase)) return;

        Block log;
        Block leaves;
        int minTreeHeight;
        int maxTreeHeight;

        switch (type) {
            case BIRCH_FOREST -> {
                log = Blocks.BIRCH_LOG;
                leaves = Blocks.BIRCH_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 7;
            }
            case TAIGA, SNOWY -> {
                log = Blocks.SPRUCE_LOG;
                leaves = Blocks.SPRUCE_LEAVES;
                minTreeHeight = 6;
                maxTreeHeight = 10;
            }
            case JUNGLE -> {
                log = Blocks.JUNGLE_LOG;
                leaves = Blocks.JUNGLE_LEAVES;
                minTreeHeight = 8;
                maxTreeHeight = 12;
            }
            case DARK_FOREST -> {
                log = Blocks.DARK_OAK_LOG;
                leaves = Blocks.DARK_OAK_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case SAVANNA -> {
                log = Blocks.ACACIA_LOG;
                leaves = Blocks.ACACIA_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case CHERRY -> {
                log = Blocks.CHERRY_LOG;
                leaves = Blocks.CHERRY_LEAVES;
                minTreeHeight = 4;
                maxTreeHeight = 6;
            }
            case MANGROVE -> {
                log = Blocks.MANGROVE_LOG;
                leaves = Blocks.MANGROVE_LEAVES;
                minTreeHeight = 5;
                maxTreeHeight = 8;
            }
            case MUSHROOM -> {
                computeGiantMushroomBlocks(result, treeBase, random, floorBlocks, baseY, maxHeight);
                return;
            }
            case DESERT, BADLANDS -> {
                computeCactusBlocks(result, treeBase, random, floorBlocks, baseY, maxHeight);
                return;
            }
            case NETHER -> {
                computeNetherVegetationBlocks(result, pos, random, floorBlocks, baseY, maxHeight);
                return;
            }
            case END, OCEAN, CAVE -> {
                return;
            }
            default -> {
                log = Blocks.OAK_LOG;
                leaves = Blocks.OAK_LEAVES;
                minTreeHeight = 4;
                maxTreeHeight = 6;
            }
        }

        int treeHeight = minTreeHeight + random.nextInt(maxTreeHeight - minTreeHeight + 1);
        int maxAllowedHeight = baseY + maxHeight - treeBase.getY();
        if (maxAllowedHeight <= 0) return;
        treeHeight = Math.min(treeHeight, maxAllowedHeight);

        // Trunk
        for (int y = 0; y < treeHeight; y++) {
            BlockPos trunkPos = Objects.requireNonNull(treeBase.above(y));
            if (isWithinBounds(trunkPos, floorBlocks, baseY, maxHeight)) {
                result.put(trunkPos, Objects.requireNonNull(log.defaultBlockState()));
            }
        }

        // Leaves
        BlockPos leafCenter = treeBase.above(treeHeight - 2);
        int leafRadius = 2;

        for (int dx = -leafRadius; dx <= leafRadius; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                    if (Math.abs(dx) == leafRadius && Math.abs(dz) == leafRadius) continue;
                    BlockPos leafPos = Objects.requireNonNull(leafCenter.offset(dx, dy, dz));
                    if (!result.containsKey(leafPos) &&
                        isWithinBounds(leafPos, floorBlocks, baseY, maxHeight)) {
                        result.put(leafPos, Objects.requireNonNull(leaves.defaultBlockState()));
                    }
                }
            }
        }
    }

    private static void computeGiantMushroomBlocks(Map<BlockPos, BlockState> result, BlockPos base, RandomSource random,
                                                   Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        if (!isWithinBounds(base, floorBlocks, baseY, maxHeight)) return;

        boolean isBrown = random.nextBoolean();
        Block stem = Blocks.MUSHROOM_STEM;
        Block cap = isBrown ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK;
        int height = 5 + random.nextInt(3);
        int maxStemHeight = baseY + maxHeight - base.getY();
        if (maxStemHeight <= 0) return;
        height = Math.min(height, maxStemHeight);

        for (int y = 0; y < height; y++) {
            BlockPos stemPos = Objects.requireNonNull(base.above(y));
            if (isWithinBounds(stemPos, floorBlocks, baseY, maxHeight)) {
                result.put(stemPos, Objects.requireNonNull(stem.defaultBlockState()));
            }
        }

        BlockPos capCenter = base.above(height);
        int capRadius = isBrown ? 3 : 2;

        for (int dx = -capRadius; dx <= capRadius; dx++) {
            for (int dz = -capRadius; dz <= capRadius; dz++) {
                if (Math.abs(dx) == capRadius && Math.abs(dz) == capRadius) continue;
                BlockPos capPos = Objects.requireNonNull(capCenter.offset(dx, 0, dz));
                if (!result.containsKey(capPos) &&
                    isWithinBounds(capPos, floorBlocks, baseY, maxHeight)) {
                    result.put(capPos, Objects.requireNonNull(cap.defaultBlockState()));
                }
            }
        }
    }

    private static void computeCactusBlocks(Map<BlockPos, BlockState> result, BlockPos base, RandomSource random,
                                            Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        if (!isWithinBounds(base, floorBlocks, baseY, maxHeight)) return;

        int height = 1 + random.nextInt(3);
        int maxCactusHeight = baseY + maxHeight - base.getY();
        if (maxCactusHeight <= 0) return;
        height = Math.min(height, maxCactusHeight);

        for (int y = 0; y < height; y++) {
            BlockPos cactusPos = Objects.requireNonNull(base.above(y));
            if (isWithinBounds(cactusPos, floorBlocks, baseY, maxHeight)) {
                result.put(cactusPos, Objects.requireNonNull(Blocks.CACTUS.defaultBlockState()));
            }
        }
    }

    private static void computeNetherVegetationBlocks(Map<BlockPos, BlockState> result, BlockPos pos, RandomSource random,
                                                      Set<BlockPos> floorBlocks, int baseY, int maxHeight) {
        BlockPos above = Objects.requireNonNull(pos.above());
        if (!isWithinBounds(above, floorBlocks, baseY, maxHeight)) return;
        if (result.containsKey(above)) return;

        float r = random.nextFloat();
        BlockState feature;

        if (r < 0.3f) {
            feature = Blocks.CRIMSON_FUNGUS.defaultBlockState();
        } else if (r < 0.6f) {
            feature = Blocks.WARPED_FUNGUS.defaultBlockState();
        } else if (r < 0.8f) {
            feature = Blocks.CRIMSON_ROOTS.defaultBlockState();
        } else {
            feature = Blocks.WARPED_ROOTS.defaultBlockState();
        }

        result.put(above, Objects.requireNonNull(feature));
    }

    // ============================================================================
    // VEGETATION STATE
    // ============================================================================

    /**
     * Gets vegetation state for a biome type.
     *
     * @param type   Biome feature type
     * @param random RandomSource for selection
     * @return BlockState for vegetation, or null if none
     */
    @Nullable
    public static BlockState getVegetationState(BiomeFeatureType type, RandomSource random) {
        float r = random.nextFloat();

        return switch (type) {
            case PLAINS, MEADOW -> {
                if (r < 0.4f) yield Blocks.SHORT_GRASS.defaultBlockState();
                else if (r < 0.6f) yield Blocks.TALL_GRASS.defaultBlockState();
                else if (r < 0.7f) yield Blocks.POPPY.defaultBlockState();
                else if (r < 0.8f) yield Blocks.DANDELION.defaultBlockState();
                else if (r < 0.9f) yield Blocks.CORNFLOWER.defaultBlockState();
                else yield Blocks.OXEYE_DAISY.defaultBlockState();
            }
            case FOREST, BIRCH_FOREST, DARK_FOREST -> {
                if (r < 0.5f) yield Blocks.SHORT_GRASS.defaultBlockState();
                else if (r < 0.7f) yield Blocks.FERN.defaultBlockState();
                else if (r < 0.85f) yield Blocks.BROWN_MUSHROOM.defaultBlockState();
                else yield Blocks.RED_MUSHROOM.defaultBlockState();
            }
            case TAIGA, SNOWY -> {
                if (r < 0.4f) yield Blocks.FERN.defaultBlockState();
                else if (r < 0.6f) yield Blocks.LARGE_FERN.defaultBlockState();
                else if (r < 0.8f) yield Blocks.SWEET_BERRY_BUSH.defaultBlockState();
                else yield Blocks.SHORT_GRASS.defaultBlockState();
            }
            case JUNGLE -> {
                if (r < 0.3f) yield Blocks.FERN.defaultBlockState();
                else if (r < 0.5f) yield Blocks.LARGE_FERN.defaultBlockState();
                else if (r < 0.7f) yield Blocks.MELON.defaultBlockState();
                else yield Blocks.BAMBOO.defaultBlockState();
            }
            case SWAMP -> {
                if (r < 0.4f) yield Blocks.SHORT_GRASS.defaultBlockState();
                else if (r < 0.6f) yield Blocks.BLUE_ORCHID.defaultBlockState();
                else if (r < 0.8f) yield Blocks.LILY_PAD.defaultBlockState();
                else yield Blocks.BROWN_MUSHROOM.defaultBlockState();
            }
            case DESERT -> r < 0.7f ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case BADLANDS -> r < 0.5f ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case SAVANNA -> {
                if (r < 0.6f) yield Blocks.SHORT_GRASS.defaultBlockState();
                else if (r < 0.8f) yield Blocks.TALL_GRASS.defaultBlockState();
                else yield Blocks.DEAD_BUSH.defaultBlockState();
            }
            case MUSHROOM -> r < 0.5f ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState();
            case CHERRY -> {
                if (r < 0.3f) yield Blocks.SHORT_GRASS.defaultBlockState();
                else if (r < 0.5f) yield Blocks.PINK_PETALS.defaultBlockState();
                else if (r < 0.7f) yield Blocks.PEONY.defaultBlockState();
                else yield Blocks.LILAC.defaultBlockState();
            }
            case CAVE -> {
                if (r < 0.3f) yield Blocks.MOSS_CARPET.defaultBlockState();
                else if (r < 0.5f) yield Blocks.AZALEA.defaultBlockState();
                else if (r < 0.7f) yield Blocks.SPORE_BLOSSOM.defaultBlockState();
                else yield Blocks.GLOW_LICHEN.defaultBlockState();
            }
            default -> null;
        };
    }
}

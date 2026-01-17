package com.devmod.area.builder;

import java.util.Locale;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.data.BiomeGenerationConfig;

/**
 * Provides biome-specific block types for terrain generation.
 * Includes surface blocks, sub-surface, deep blocks, and ores.
 * Extracted from BiomeAreaGenerator for better modularity.
 */
public final class BiomeBlockProvider {

    private BiomeBlockProvider() {}

    // ============================================================================
    // BIOME BLOCKS RECORD
    // ============================================================================

    /**
     * Block types for a biome.
     *
     * @param topBlock      Surface block (grass, sand, etc.)
     * @param subSurface    Blocks 1-3 deep (dirt, sandstone, etc.)
     * @param deepBlock     Deep underground block (stone, netherrack, etc.)
     * @param underwaterTop Surface block when underwater (gravel, sand, etc.)
     */
    public record BiomeBlocks(
        BlockState topBlock,
        BlockState subSurface,
        BlockState deepBlock,
        BlockState underwaterTop
    ) {}

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Gets appropriate blocks for a biome based on its resource location path.
     *
     * @param config The biome generation config
     * @return BiomeBlocks with appropriate block states
     */
    public static BiomeBlocks getBiomeBlocks(BiomeGenerationConfig config) {
        String biomePath = config.biomeId().getPath().toLowerCase(Locale.ROOT);

        // Default blocks
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState sub = Blocks.DIRT.defaultBlockState();
        BlockState deep = Blocks.STONE.defaultBlockState();
        BlockState underwater = Blocks.GRAVEL.defaultBlockState();

        // Biome-specific overrides
        if (biomePath.contains("desert") || biomePath.contains("badland")) {
            top = Blocks.SAND.defaultBlockState();
            sub = Blocks.SANDSTONE.defaultBlockState();
            underwater = Blocks.SAND.defaultBlockState();
        } else if (biomePath.contains("beach")) {
            top = Blocks.SAND.defaultBlockState();
            sub = Blocks.SAND.defaultBlockState();
        } else if (biomePath.contains("snowy") || biomePath.contains("frozen") || biomePath.contains("ice")) {
            top = Blocks.SNOW_BLOCK.defaultBlockState();
            sub = Blocks.DIRT.defaultBlockState();
            underwater = Blocks.GRAVEL.defaultBlockState();
        } else if (biomePath.contains("mushroom")) {
            top = Blocks.MYCELIUM.defaultBlockState();
            sub = Blocks.DIRT.defaultBlockState();
        } else if (biomePath.contains("swamp")) {
            top = Blocks.GRASS_BLOCK.defaultBlockState();
            sub = Blocks.DIRT.defaultBlockState();
            underwater = Blocks.CLAY.defaultBlockState();
        } else if (biomePath.contains("jungle")) {
            top = Blocks.GRASS_BLOCK.defaultBlockState();
            sub = Blocks.DIRT.defaultBlockState();
        } else if (biomePath.contains("taiga") || biomePath.contains("grove")) {
            top = Blocks.GRASS_BLOCK.defaultBlockState();
            sub = Blocks.PODZOL.defaultBlockState();
        } else if (biomePath.contains("mountain") || biomePath.contains("peak") || biomePath.contains("stony")) {
            top = Blocks.STONE.defaultBlockState();
            sub = Blocks.STONE.defaultBlockState();
        } else if (biomePath.contains("nether") || biomePath.contains("soul") || biomePath.contains("basalt")) {
            top = Blocks.NETHERRACK.defaultBlockState();
            sub = Blocks.NETHERRACK.defaultBlockState();
            deep = Blocks.NETHERRACK.defaultBlockState();
        } else if (biomePath.contains("end")) {
            top = Blocks.END_STONE.defaultBlockState();
            sub = Blocks.END_STONE.defaultBlockState();
            deep = Blocks.END_STONE.defaultBlockState();
        } else if (biomePath.contains("deep_dark")) {
            top = Blocks.SCULK.defaultBlockState();
            sub = Blocks.DEEPSLATE.defaultBlockState();
            deep = Blocks.DEEPSLATE.defaultBlockState();
        } else if (biomePath.contains("cherry")) {
            top = Blocks.GRASS_BLOCK.defaultBlockState();
            sub = Blocks.DIRT.defaultBlockState();
        } else if (biomePath.contains("mangrove")) {
            top = Blocks.MUD.defaultBlockState();
            sub = Blocks.MUD.defaultBlockState();
            underwater = Blocks.MUD.defaultBlockState();
        }

        return new BiomeBlocks(top, sub, deep, underwater);
    }

    // ============================================================================
    // ORE GENERATION
    // ============================================================================

    /**
     * Determines if ore should generate at a given Y level.
     * Lower Y levels have higher ore chances.
     *
     * @param y      The Y level
     * @param random RandomSource for probability
     * @return true if ore should generate
     */
    public static boolean shouldGenerateOre(int y, RandomSource random) {
        // Lower Y = higher ore chance
        float chance = 0.02f + (64 - Math.min(y, 64)) * 0.001f;
        return random.nextFloat() < chance;
    }

    /**
     * Gets appropriate ore for a depth level.
     * Deeper levels have rarer ores (diamonds, emeralds).
     * Below Y=0 uses deepslate variants.
     *
     * @param y      The Y level
     * @param random RandomSource for ore selection
     * @return BlockState of the ore
     */
    public static BlockState getOreForDepth(int y, RandomSource random) {
        if (y < 0) {
            return getDeepslateOre(random);
        } else if (y < 32) {
            return getDeepOre(random);
        } else {
            return getSurfaceOre(random);
        }
    }

    // ============================================================================
    // INTERNAL ORE METHODS
    // ============================================================================

    private static BlockState getDeepslateOre(RandomSource random) {
        float r = random.nextFloat();
        if (r < 0.3f) return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
        if (r < 0.5f) return Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
        if (r < 0.65f) return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        if (r < 0.75f) return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        if (r < 0.85f) return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        if (r < 0.92f) return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
        if (r < 0.98f) return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        return Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
    }

    private static BlockState getDeepOre(RandomSource random) {
        float r = random.nextFloat();
        if (r < 0.3f) return Blocks.IRON_ORE.defaultBlockState();
        if (r < 0.5f) return Blocks.COAL_ORE.defaultBlockState();
        if (r < 0.7f) return Blocks.COPPER_ORE.defaultBlockState();
        if (r < 0.85f) return Blocks.GOLD_ORE.defaultBlockState();
        if (r < 0.95f) return Blocks.DIAMOND_ORE.defaultBlockState();
        return Blocks.REDSTONE_ORE.defaultBlockState();
    }

    private static BlockState getSurfaceOre(RandomSource random) {
        float r = random.nextFloat();
        if (r < 0.4f) return Blocks.COAL_ORE.defaultBlockState();
        if (r < 0.7f) return Blocks.IRON_ORE.defaultBlockState();
        if (r < 0.9f) return Blocks.COPPER_ORE.defaultBlockState();
        return Blocks.GOLD_ORE.defaultBlockState();
    }
}

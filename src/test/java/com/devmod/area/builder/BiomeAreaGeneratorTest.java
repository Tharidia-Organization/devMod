package com.devmod.area.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;

/**
 * Unit tests for BiomeAreaGenerator.
 *
 * Tests cover:
 * - Block map generation for different terrain styles
 * - Deterministic output with fixed seeds
 * - Biome-specific block selection
 * - Cave carving in block maps
 * - Feature generation in block maps
 * - Edge cases and error handling
 */
@DisplayName("BiomeAreaGenerator")
class BiomeAreaGeneratorTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private AreaDefinition createBiomeArea(String name, BiomeGenerationConfig config) {
        return AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name(Objects.requireNonNull(name))
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(16, 16, 32, 64))
            .center(new BlockPos(0, 64, 0))
            .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
            .palette(Objects.requireNonNull(AreaPalette.empty("default")))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .biomeConfig(config)
            .build();
    }

    private Set<BlockPos> generateFloorPositions(int width, int length, int baseY) {
        Set<BlockPos> positions = new HashSet<>();
        int halfW = width / 2;
        int halfL = length / 2;
        for (int x = -halfW; x < halfW; x++) {
            for (int z = -halfL; z < halfL; z++) {
                positions.add(new BlockPos(x, baseY, z));
            }
        }
        return positions;
    }

    // ========================================================================
    // Block Map Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("generateBlockMap")
    class GenerateBlockMapTests {

        @Test
        @DisplayName("generates blocks for valid biome config")
        void generateBlockMap_validConfig_generatesBlocks() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            AreaDefinition area = createBiomeArea("test_plains", config);
            Set<BlockPos> floorPositions = generateFloorPositions(8, 8, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            assertFalse(blocks.isEmpty(), "Should generate blocks");
            assertTrue(blocks.size() > floorPositions.size(), "Should generate multiple layers");
        }

        @Test
        @DisplayName("returns empty map for null biome config")
        void generateBlockMap_nullConfig_returnsEmpty() {
            AreaDefinition area = AreaDefinition.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .name("no_biome")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(8, 8, 16, 64))
                .center(new BlockPos(0, 64, 0))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .palette(Objects.requireNonNull(AreaPalette.empty("default")))
                .options(Objects.requireNonNull(AreaOptions.DEFAULT))
                .biomeConfig(null)
                .build();

            Set<BlockPos> floorPositions = generateFloorPositions(8, 8, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            assertTrue(blocks.isEmpty(), "Should return empty map for null biome config");
        }

        @Test
        @DisplayName("is deterministic with same seed")
        void generateBlockMap_sameSeed_isDeterministic() {
            long seed = 42L;
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("forest")),
                seed,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            AreaDefinition area = createBiomeArea("deterministic_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(8, 8, 64);

            Map<BlockPos, BlockState> blocks1 = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> blocks2 = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            assertEquals(blocks1.size(), blocks2.size(), "Same seed should produce same block count");

            // Verify block states match at specific positions
            for (BlockPos pos : blocks1.keySet()) {
                BlockState state1 = blocks1.get(pos);
                BlockState state2 = blocks2.get(pos);
                assertEquals(state1, state2,
                    "Same seed should produce identical blocks at " + pos);
            }
        }

        @Test
        @DisplayName("different seeds produce different results")
        void generateBlockMap_differentSeeds_differentResults() {
            BiomeGenerationConfig config1 = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                111L,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            BiomeGenerationConfig config2 = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                222L,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            AreaDefinition area1 = createBiomeArea("seed_test_1", config1);
            AreaDefinition area2 = createBiomeArea("seed_test_2", config2);
            Set<BlockPos> floorPositions = generateFloorPositions(16, 16, 64);

            Map<BlockPos, BlockState> blocks1 = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area1), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> blocks2 = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area2), Objects.requireNonNull(floorPositions));

            // With different seeds and NATURAL terrain, heights should differ
            // Count positions where the highest block differs
            int differences = 0;
            for (BlockPos floorPos : floorPositions) {
                int maxY1 = -1;
                int maxY2 = -1;
                for (BlockPos pos : blocks1.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        maxY1 = Math.max(maxY1, pos.getY());
                    }
                }
                for (BlockPos pos : blocks2.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        maxY2 = Math.max(maxY2, pos.getY());
                    }
                }
                if (maxY1 != maxY2) {
                    differences++;
                }
            }

            assertTrue(differences > 0,
                "Different seeds should produce different terrain heights");
        }
    }

    // ========================================================================
    // Terrain Style Tests
    // ========================================================================

    @Nested
    @DisplayName("Terrain Styles")
    class TerrainStyleTests {

        @Test
        @DisplayName("FLAT style produces uniform height")
        void flatStyle_producesUniformHeight() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("flat_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(8, 8, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find all surface Y positions
            Set<Integer> surfaceYs = new HashSet<>();
            for (BlockPos floorPos : floorPositions) {
                int maxY = -1;
                for (BlockPos pos : blocks.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        maxY = Math.max(maxY, pos.getY());
                    }
                }
                if (maxY >= 0) {
                    surfaceYs.add(maxY);
                }
            }

            assertEquals(1, surfaceYs.size(),
                "FLAT terrain should have uniform height, got: " + surfaceYs);
        }

        @Test
        @DisplayName("NATURAL style produces height variation")
        void naturalStyle_producesHeightVariation() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            AreaDefinition area = createBiomeArea("natural_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(16, 16, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find min and max surface Y
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (BlockPos floorPos : floorPositions) {
                int columnMaxY = -1;
                for (BlockPos pos : blocks.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        columnMaxY = Math.max(columnMaxY, pos.getY());
                    }
                }
                if (columnMaxY >= 0) {
                    minY = Math.min(minY, columnMaxY);
                    maxY = Math.max(maxY, columnMaxY);
                }
            }

            assertTrue(maxY > minY,
                "NATURAL terrain should have height variation (min=" + minY + ", max=" + maxY + ")");
        }

        @Test
        @DisplayName("AMPLIFIED style produces larger height variation")
        void amplifiedStyle_producesLargerVariation() {
            long seed = 54321L;

            BiomeGenerationConfig naturalConfig = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("mountains")),
                seed,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            BiomeGenerationConfig amplifiedConfig = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("mountains")),
                seed,
                false,
                false,
                false,
                63,
                TerrainStyle.AMPLIFIED
            );

            Set<BlockPos> floorPositions = generateFloorPositions(16, 16, 64);

            Map<BlockPos, BlockState> naturalBlocks = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("natural", naturalConfig)), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> amplifiedBlocks = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("amplified", amplifiedConfig)), Objects.requireNonNull(floorPositions));

            // Calculate height range for each
            int naturalRange = calculateHeightRange(naturalBlocks, floorPositions);
            int amplifiedRange = calculateHeightRange(amplifiedBlocks, floorPositions);

            assertTrue(amplifiedRange >= naturalRange,
                "AMPLIFIED should have >= height range than NATURAL " +
                "(natural=" + naturalRange + ", amplified=" + amplifiedRange + ")");
        }

        @Test
        @DisplayName("FLOATING style creates gaps in terrain")
        void floatingStyle_createsGaps() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLOATING
            );

            AreaDefinition area = createBiomeArea("floating_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(32, 32, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Count columns with no blocks (gaps)
            int columnsWithBlocks = 0;
            for (BlockPos floorPos : floorPositions) {
                boolean hasBlocks = false;
                for (BlockPos pos : blocks.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        hasBlocks = true;
                        break;
                    }
                }
                if (hasBlocks) {
                    columnsWithBlocks++;
                }
            }

            int gaps = floorPositions.size() - columnsWithBlocks;
            assertTrue(gaps > 0,
                "FLOATING style should create gaps (found " + columnsWithBlocks +
                " columns with blocks out of " + floorPositions.size() + ")");
        }

        private int calculateHeightRange(Map<BlockPos, BlockState> blocks, Set<BlockPos> floorPositions) {
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (BlockPos floorPos : floorPositions) {
                int columnMaxY = -1;
                for (BlockPos pos : blocks.keySet()) {
                    if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                        columnMaxY = Math.max(columnMaxY, pos.getY());
                    }
                }
                if (columnMaxY >= 0) {
                    minY = Math.min(minY, columnMaxY);
                    maxY = Math.max(maxY, columnMaxY);
                }
            }
            return maxY - minY;
        }
    }

    // ========================================================================
    // Biome-Specific Block Tests
    // ========================================================================

    @Nested
    @DisplayName("Biome-Specific Blocks")
    class BiomeBlockTests {

        @Test
        @DisplayName("desert biome uses sand surface")
        void desertBiome_usesSand() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("desert")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("desert_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(4, 4, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find surface blocks
            boolean hasSand = false;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getValue().is(Objects.requireNonNull(Blocks.SAND))) {
                    hasSand = true;
                    break;
                }
            }

            assertTrue(hasSand, "Desert biome should use sand");
        }

        @Test
        @DisplayName("snowy biome uses snow surface")
        void snowyBiome_usesSnow() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("snowy_plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("snowy_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(4, 4, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find surface blocks
            boolean hasSnow = false;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getValue().is(Objects.requireNonNull(Blocks.SNOW_BLOCK))) {
                    hasSnow = true;
                    break;
                }
            }

            assertTrue(hasSnow, "Snowy biome should use snow");
        }

        @Test
        @DisplayName("mushroom biome uses mycelium surface")
        void mushroomBiome_usesMycelium() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("mushroom_fields")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("mushroom_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(4, 4, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find surface blocks
            boolean hasMycelium = false;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getValue().is(Objects.requireNonNull(Blocks.MYCELIUM))) {
                    hasMycelium = true;
                    break;
                }
            }

            assertTrue(hasMycelium, "Mushroom biome should use mycelium");
        }

        @Test
        @DisplayName("plains biome uses grass surface")
        void plainsBiome_usesGrass() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("plains_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(4, 4, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find surface blocks
            boolean hasGrass = false;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getValue().is(Objects.requireNonNull(Blocks.GRASS_BLOCK))) {
                    hasGrass = true;
                    break;
                }
            }

            assertTrue(hasGrass, "Plains biome should use grass");
        }

        @Test
        @DisplayName("nether biome uses netherrack")
        void netherBiome_usesNetherrack() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("nether_wastes")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("nether_test", config);
            Set<BlockPos> floorPositions = generateFloorPositions(4, 4, 64);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Find surface blocks
            boolean hasNetherrack = false;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                if (entry.getValue().is(Objects.requireNonNull(Blocks.NETHERRACK))) {
                    hasNetherrack = true;
                    break;
                }
            }

            assertTrue(hasNetherrack, "Nether biome should use netherrack");
        }
    }

    // ========================================================================
    // Cave Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Cave Generation")
    class CaveGenerationTests {

        @Test
        @DisplayName("caves are carved when generateStructures is true")
        void caves_carvedWhenEnabled() {
            BiomeGenerationConfig configWithCaves = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                true, // generateStructures = caves
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            BiomeGenerationConfig configWithoutCaves = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false, // no caves
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            Set<BlockPos> floorPositions = generateFloorPositions(32, 32, 64);

            Map<BlockPos, BlockState> blocksWithCaves = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("with_caves", configWithCaves)), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> blocksWithoutCaves = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("without_caves", configWithoutCaves)), Objects.requireNonNull(floorPositions));

            // Count CAVE_AIR blocks
            long caveAirWithCaves = blocksWithCaves.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.CAVE_AIR)))
                .count();

            long caveAirWithoutCaves = blocksWithoutCaves.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.CAVE_AIR)))
                .count();

            assertTrue(caveAirWithCaves > caveAirWithoutCaves,
                "Should have more cave air with caves enabled " +
                "(with=" + caveAirWithCaves + ", without=" + caveAirWithoutCaves + ")");
        }
    }

    // ========================================================================
    // Ore Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Ore Generation")
    class OreGenerationTests {

        @Test
        @DisplayName("ores are generated when enabled")
        void ores_generatedWhenEnabled() {
            BiomeGenerationConfig configWithOres = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                true, // ores enabled
                63,
                TerrainStyle.NATURAL
            );

            BiomeGenerationConfig configWithoutOres = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false, // no ores
                63,
                TerrainStyle.NATURAL
            );

            Set<BlockPos> floorPositions = generateFloorPositions(32, 32, 32); // Low Y for ores

            Map<BlockPos, BlockState> blocksWithOres = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("with_ores", configWithOres)), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> blocksWithoutOres = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("without_ores", configWithoutOres)), Objects.requireNonNull(floorPositions));

            // Count ore blocks
            long oreCountWith = blocksWithOres.values().stream()
                .filter(this::isOre)
                .count();

            long oreCountWithout = blocksWithoutOres.values().stream()
                .filter(this::isOre)
                .count();

            assertTrue(oreCountWith > oreCountWithout,
                "Should have more ores when enabled (with=" + oreCountWith + ", without=" + oreCountWithout + ")");
        }

        private boolean isOre(BlockState state) {
            return state.is(Objects.requireNonNull(Blocks.COAL_ORE)) || state.is(Objects.requireNonNull(Blocks.IRON_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.COPPER_ORE)) || state.is(Objects.requireNonNull(Blocks.GOLD_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.DIAMOND_ORE)) || state.is(Objects.requireNonNull(Blocks.REDSTONE_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.LAPIS_ORE)) || state.is(Objects.requireNonNull(Blocks.EMERALD_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.DEEPSLATE_COAL_ORE)) || state.is(Objects.requireNonNull(Blocks.DEEPSLATE_IRON_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.DEEPSLATE_COPPER_ORE)) || state.is(Objects.requireNonNull(Blocks.DEEPSLATE_GOLD_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.DEEPSLATE_DIAMOND_ORE)) || state.is(Objects.requireNonNull(Blocks.DEEPSLATE_REDSTONE_ORE)) ||
                   state.is(Objects.requireNonNull(Blocks.DEEPSLATE_LAPIS_ORE)) || state.is(Objects.requireNonNull(Blocks.DEEPSLATE_EMERALD_ORE));
        }
    }

    // ========================================================================
    // Feature Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Feature Generation")
    class FeatureGenerationTests {

        @Test
        @DisplayName("features are generated when enabled")
        void features_generatedWhenEnabled() {
            BiomeGenerationConfig configWithFeatures = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("forest")),
                12345L,
                false,
                true, // features enabled
                false,
                63,
                TerrainStyle.FLAT
            );

            BiomeGenerationConfig configWithoutFeatures = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("forest")),
                12345L,
                false,
                false, // no features
                false,
                63,
                TerrainStyle.FLAT
            );

            Set<BlockPos> floorPositions = generateFloorPositions(32, 32, 64);

            Map<BlockPos, BlockState> blocksWithFeatures = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("with_features", configWithFeatures)), Objects.requireNonNull(floorPositions));
            Map<BlockPos, BlockState> blocksWithoutFeatures = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(createBiomeArea("without_features", configWithoutFeatures)), Objects.requireNonNull(floorPositions));

            // Count vegetation/tree blocks (anything above surface that isn't terrain)
            long featureCountWith = blocksWithFeatures.values().stream()
                .filter(this::isVegetationOrTree)
                .count();

            long featureCountWithout = blocksWithoutFeatures.values().stream()
                .filter(this::isVegetationOrTree)
                .count();

            assertTrue(featureCountWith > featureCountWithout,
                "Should have more features when enabled " +
                "(with=" + featureCountWith + ", without=" + featureCountWithout + ")");
        }

        private boolean isVegetationOrTree(BlockState state) {
            return state.is(Objects.requireNonNull(Blocks.OAK_LOG)) || state.is(Objects.requireNonNull(Blocks.OAK_LEAVES)) ||
                   state.is(Objects.requireNonNull(Blocks.BIRCH_LOG)) || state.is(Objects.requireNonNull(Blocks.BIRCH_LEAVES)) ||
                   state.is(Objects.requireNonNull(Blocks.SPRUCE_LOG)) || state.is(Objects.requireNonNull(Blocks.SPRUCE_LEAVES)) ||
                   state.is(Objects.requireNonNull(Blocks.SHORT_GRASS)) || state.is(Objects.requireNonNull(Blocks.TALL_GRASS)) ||
                   state.is(Objects.requireNonNull(Blocks.FERN)) || state.is(Objects.requireNonNull(Blocks.LARGE_FERN)) ||
                   state.is(Objects.requireNonNull(Blocks.POPPY)) || state.is(Objects.requireNonNull(Blocks.DANDELION)) ||
                   state.is(Objects.requireNonNull(Blocks.RED_MUSHROOM)) || state.is(Objects.requireNonNull(Blocks.BROWN_MUSHROOM));
        }
    }

    // ========================================================================
    // Water/Sea Level Tests
    // ========================================================================

    @Nested
    @DisplayName("Water Generation")
    class WaterGenerationTests {

        @Test
        @DisplayName("water fills below sea level")
        void water_fillsBelowSeaLevel() {
            int seaLevel = 68;
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                seaLevel,
                TerrainStyle.NATURAL
            );

            // Use low baseY so terrain is below sea level
            AreaDefinition area = AreaDefinition.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .name("water_test")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(16, 16, 32, 50)) // baseY = 50
                .center(new BlockPos(0, 50, 0))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .palette(Objects.requireNonNull(AreaPalette.empty("default")))
                .options(Objects.requireNonNull(AreaOptions.DEFAULT))
                .biomeConfig(config)
                .build();

            Set<BlockPos> floorPositions = generateFloorPositions(8, 8, 50);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            // Check for water blocks between terrain surface and sea level
            long waterCount = blocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.WATER)))
                .count();

            assertTrue(waterCount > 0,
                "Should generate water below sea level (found " + waterCount + " water blocks)");
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles empty floor positions")
        void handlesEmptyFloorPositions() {
            BiomeGenerationConfig config = Objects.requireNonNull(BiomeGenerationConfig.DEFAULT);
            AreaDefinition area = createBiomeArea("empty_test", config);

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(
                Objects.requireNonNull(area), new HashSet<>());

            // When floor positions are empty, the generator regenerates them from area definition
            assertFalse(blocks.isEmpty(), "Empty floor positions should trigger regeneration from area shape");
        }

        @Test
        @DisplayName("handles single position")
        void handlesSinglePosition() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.FLAT
            );

            AreaDefinition area = createBiomeArea("single_test", config);
            Set<BlockPos> floorPositions = Set.of(new BlockPos(0, 64, 0));

            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));

            assertFalse(blocks.isEmpty(), "Should generate blocks for single position");
        }

        @Test
        @DisplayName("handles large area efficiently")
        void handlesLargeArea() {
            BiomeGenerationConfig config = new BiomeGenerationConfig(
                Objects.requireNonNull(ResourceLocation.withDefaultNamespace("plains")),
                12345L,
                false,
                false,
                false,
                63,
                TerrainStyle.NATURAL
            );

            AreaDefinition area = AreaDefinition.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .name("large_test")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(64, 64, 64, 64))
                .center(new BlockPos(0, 64, 0))
                .dimension(Objects.requireNonNull(ResourceLocation.withDefaultNamespace("overworld")))
                .palette(Objects.requireNonNull(AreaPalette.empty("default")))
                .options(Objects.requireNonNull(AreaOptions.DEFAULT))
                .biomeConfig(config)
                .build();

            Set<BlockPos> floorPositions = generateFloorPositions(64, 64, 64);

            long startTime = System.currentTimeMillis();
            Map<BlockPos, BlockState> blocks = BiomeAreaGenerator.generateBlockMap(Objects.requireNonNull(area), Objects.requireNonNull(floorPositions));
            long elapsed = System.currentTimeMillis() - startTime;

            assertFalse(blocks.isEmpty(), "Should generate blocks for large area");
            assertTrue(elapsed < 5000, "Large area generation should complete in < 5 seconds (took " + elapsed + "ms)");
        }
    }
}

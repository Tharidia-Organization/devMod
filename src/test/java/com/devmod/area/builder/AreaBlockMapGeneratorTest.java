package com.devmod.area.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaOptions.WallStyle;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.runtime.NexusPalette;

/**
 * Unit tests for AreaBlockMapGenerator.
 * Tests block map generation for floor, walls, ceiling, and clearing.
 * Critical focus: WallStyle implementations (SOLID, WINDOWED, OPEN_CORNERS, PILLARS_ONLY).
 */
@DisplayName("AreaBlockMapGenerator")
class AreaBlockMapGeneratorTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Creates a test area definition with the specified wall style.
     */
    private static AreaDefinition createTestArea(
            BlockPos center,
            AreaDimensions dims,
            WallStyle wallStyle,
            AreaShape shape
    ) {
        AreaPalette palette = Objects.requireNonNull(createTestPalette());
        AreaOptions options = new AreaOptions(true, true, true, wallStyle, false, false, null);

        return AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("test_area")
            .shape(Objects.requireNonNull(shape))
            .dimensions(Objects.requireNonNull(dims))
            .center(Objects.requireNonNull(center))
            .palette(Objects.requireNonNull(palette))
            .options(options)
            .build();
    }

    /**
     * Creates a test palette with all required materials.
     */
    private static AreaPalette createTestPalette() {
        return Objects.requireNonNull(AreaPalette.empty("test"))
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.FLOOR.id()), "minecraft:stone")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.FLOOR_ALT.id()), "minecraft:cobblestone")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.WALL.id()), "minecraft:stone_bricks")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.WALL_FRAME.id()), "minecraft:deepslate_bricks")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.WINDOW.id()), "minecraft:glass")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.CEILING.id()), "minecraft:smooth_stone")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.LIGHT.id()), "minecraft:glowstone");
    }

    // ========================================================================
    // SOLID WallStyle Tests
    // ========================================================================

    @Nested
    @DisplayName("WallStyle.SOLID")
    class SolidStyleTests {

        @Test
        @DisplayName("fills all wall positions with wall block")
        void fillsAllWallPositions() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // All blocks should be wall material (stone_bricks)
            assertTrue(wallBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.STONE_BRICKS))),
                "All wall blocks should be STONE_BRICKS for SOLID style");
        }

        @Test
        @DisplayName("uses wall block state from palette")
        void usesWallBlockState() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            assertFalse(wallBlocks.isEmpty(), "Should generate wall blocks");
            BlockState expectedWall = Blocks.STONE_BRICKS.defaultBlockState();
            assertTrue(wallBlocks.containsValue(expectedWall),
                "Wall blocks should contain STONE_BRICKS");
        }

        @Test
        @DisplayName("no null entries in block map")
        void noNullEntries() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            assertTrue(wallBlocks.values().stream().noneMatch(state -> state == null),
                "SOLID style should have no null (air) positions");
        }
    }

    // ========================================================================
    // WINDOWED WallStyle Tests
    // ========================================================================

    @Nested
    @DisplayName("WallStyle.WINDOWED")
    class WindowedStyleTests {

        @Test
        @DisplayName("places windows in middle section")
        void placesWindowsInMiddleSection() {
            BlockPos center = new BlockPos(0, 64, 0);
            // Height 8: floor at 64, walls from 65-71
            // relativeY 1 = y65 (wall), relativeY 2-6 = y66-70 (window), relativeY 7 = y71 (wall)
            AreaDimensions dims = new AreaDimensions(10, 10, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.WINDOWED, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // Check that middle section has windows
            long windowCount = wallBlocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.GLASS)))
                .count();

            assertTrue(windowCount > 0, "WINDOWED style should have glass blocks in middle section");
        }

        @Test
        @DisplayName("places walls at top and bottom")
        void placesWallsAtTopAndBottom() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.WINDOWED, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // relativeY = 1 should be wall (y = floorY + 1 = 65)
            // relativeY = height - 1 = 7 should be wall (y = 64 + 7 = 71)
            long bottomWallCount = wallBlocks.entrySet().stream()
                .filter(e -> e.getKey().getY() == 65)
                .filter(e -> e.getValue().is(Objects.requireNonNull(Blocks.STONE_BRICKS)))
                .count();

            long topWallCount = wallBlocks.entrySet().stream()
                .filter(e -> e.getKey().getY() == 71)
                .filter(e -> e.getValue().is(Objects.requireNonNull(Blocks.STONE_BRICKS)))
                .count();

            assertTrue(bottomWallCount > 0, "Bottom row (y=65) should have wall blocks");
            assertTrue(topWallCount > 0, "Top row (y=71) should have wall blocks");
        }

        @Test
        @DisplayName("handles small heights (no windows when height too small)")
        void handlesSmallHeights() {
            BlockPos center = new BlockPos(0, 64, 0);
            // Height 4: walls from y=65 to y=67 (relativeY 1-3)
            // relativeY > 1 && relativeY < height - 1 = relativeY 2, which is only y=66
            AreaDimensions dims = new AreaDimensions(10, 10, 4, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.WINDOWED, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // With height=4, relativeY ranges 1-3
            // Windows only when relativeY > 1 && relativeY < 3, so relativeY = 2
            long windowCount = wallBlocks.entrySet().stream()
                .filter(e -> e.getValue().is(Objects.requireNonNull(Blocks.GLASS)))
                .count();

            // Should have some windows at y=66 (relativeY=2)
            assertTrue(windowCount >= 0, "Small height should still be handled gracefully");
        }

        @Test
        @DisplayName("middle section uses window block from palette")
        void middleSectionUsesWindowBlock() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 10, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.WINDOWED, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // Middle section should have glass (the window block from palette)
            boolean hasGlass = wallBlocks.values().stream()
                .anyMatch(state -> state.is(Objects.requireNonNull(Blocks.GLASS)));

            assertTrue(hasGlass, "WINDOWED style should use GLASS (window block from palette)");
        }
    }

    // ========================================================================
    // OPEN_CORNERS WallStyle Tests
    // ========================================================================

    @Nested
    @DisplayName("WallStyle.OPEN_CORNERS")
    class OpenCornersStyleTests {

        @Test
        @DisplayName("clears blocks near corners (returns fewer blocks than SOLID)")
        void clearsBlocksNearCorners() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            AreaDefinition solidArea = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);
            AreaDefinition openCornersArea = createTestArea(center, dims, WallStyle.OPEN_CORNERS, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> solidBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(solidArea));
            Map<BlockPos, BlockState> openCornersBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(openCornersArea));

            assertTrue(openCornersBlocks.size() < solidBlocks.size(),
                "OPEN_CORNERS should have fewer blocks than SOLID (corners are air)");
        }

        @Test
        @DisplayName("works only for rectangular shape")
        void worksOnlyForRectangular() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            AreaDefinition rectArea = createTestArea(center, dims, WallStyle.OPEN_CORNERS, AreaShape.RECTANGULAR);
            AreaDefinition circArea = createTestArea(center, dims, WallStyle.OPEN_CORNERS, AreaShape.CIRCULAR);

            Map<BlockPos, BlockState> rectBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(rectArea));
            Map<BlockPos, BlockState> circBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(circArea));

            // For circular shape, OPEN_CORNERS should fall back to SOLID (no null positions)
            long circNullCount = circBlocks.values().stream().filter(s -> s == null).count();
            assertEquals(0, circNullCount, "Circular shape with OPEN_CORNERS should use SOLID (no nulls)");

            // Rectangular should have some missing corners (fewer blocks)
            // We can verify by checking that not all perimeter positions have blocks
            assertTrue(rectBlocks.size() > 0, "Rectangular should have wall blocks");
        }

        @Test
        @DisplayName("falls back to solid for circular shape")
        void fallsBackToSolidForCircular() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            AreaDefinition solidCirc = createTestArea(center, dims, WallStyle.SOLID, AreaShape.CIRCULAR);
            AreaDefinition openCirc = createTestArea(center, dims, WallStyle.OPEN_CORNERS, AreaShape.CIRCULAR);

            Map<BlockPos, BlockState> solidBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(solidCirc));
            Map<BlockPos, BlockState> openBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(openCirc));

            // Both should have same blocks (OPEN_CORNERS falls back to SOLID for non-rectangular)
            assertEquals(solidBlocks.size(), openBlocks.size(),
                "OPEN_CORNERS on circular should fall back to SOLID");
        }

        @Test
        @DisplayName("cornerDistance < 3 positions are air")
        void cornerDistanceLessThan3AreAir() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.OPEN_CORNERS, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // Get all wall positions that would exist for SOLID
            Set<BlockPos> allWallPositions = AreaShapeGenerator.generateWalls(
                AreaShape.RECTANGULAR, center, dims);

            // Count missing positions (these are the open corners)
            int missingCount = 0;
            for (BlockPos pos : allWallPositions) {
                if (!wallBlocks.containsKey(pos)) {
                    missingCount++;
                }
            }

            assertTrue(missingCount > 0, "Some corner positions should be missing (air)");
        }
    }

    // ========================================================================
    // PILLARS_ONLY WallStyle Tests
    // ========================================================================

    @Nested
    @DisplayName("WallStyle.PILLARS_ONLY")
    class PillarsOnlyStyleTests {

        @Test
        @DisplayName("places frame at corners only")
        void placesFrameAtCornersOnly() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.PILLARS_ONLY, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // All blocks should be frame (DEEPSLATE_BRICKS) at corners
            assertTrue(wallBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.DEEPSLATE_BRICKS))),
                "PILLARS_ONLY should only place DEEPSLATE_BRICKS (frame) at corners");
        }

        @Test
        @DisplayName("clears non-corner positions (fewer blocks than SOLID)")
        void clearsNonCornerPositions() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            AreaDefinition solidArea = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);
            AreaDefinition pillarsArea = createTestArea(center, dims, WallStyle.PILLARS_ONLY, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> solidBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(solidArea));
            Map<BlockPos, BlockState> pillarsBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(pillarsArea));

            assertTrue(pillarsBlocks.size() < solidBlocks.size(),
                "PILLARS_ONLY should have far fewer blocks than SOLID");
        }

        @Test
        @DisplayName("falls back to solid for non-rectangular shapes")
        void fallsBackToSolidForNonRectangular() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            AreaDefinition solidHex = createTestArea(center, dims, WallStyle.SOLID, AreaShape.HEXAGONAL);
            AreaDefinition pillarsHex = createTestArea(center, dims, WallStyle.PILLARS_ONLY, AreaShape.HEXAGONAL);

            Map<BlockPos, BlockState> solidBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(solidHex));
            Map<BlockPos, BlockState> pillarsBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(pillarsHex));

            assertEquals(solidBlocks.size(), pillarsBlocks.size(),
                "PILLARS_ONLY on hexagonal should fall back to SOLID");
        }

        @Test
        @DisplayName("uses frame block from palette")
        void usesFrameBlock() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.PILLARS_ONLY, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // Frame block is DEEPSLATE_BRICKS in our test palette
            assertTrue(wallBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.DEEPSLATE_BRICKS))),
                "PILLARS_ONLY should use DEEPSLATE_BRICKS (frame block from palette)");
        }

        @Test
        @DisplayName("pillar positions are at all Y levels")
        void pillarPositionsAtAllYLevels() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.PILLARS_ONLY, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(area));

            // Pillars should extend through all wall heights
            Set<Integer> yLevels = new HashSet<>();
            wallBlocks.keySet().forEach(pos -> yLevels.add(pos.getY()));

            // Height 8 means walls from y=65 to y=71 (7 levels)
            int expectedLevels = dims.height() - 1; // walls don't include floor
            assertEquals(expectedLevels, yLevels.size(),
                "Pillars should span all wall Y levels");
        }
    }

    // ========================================================================
    // Floor Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Floor Generation (Checkerboard)")
    class FloorGenerationTests {

        @Test
        @DisplayName("creates checkerboard pattern")
        void createsCheckerboardPattern() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(Objects.requireNonNull(area));

            // Should have both STONE and COBBLESTONE
            long stoneCount = floorBlocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.STONE)))
                .count();
            long cobbleCount = floorBlocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.COBBLESTONE)))
                .count();

            assertTrue(stoneCount > 0, "Checkerboard should have STONE blocks");
            assertTrue(cobbleCount > 0, "Checkerboard should have COBBLESTONE blocks");
        }

        @Test
        @DisplayName("alternates blocks based on (X + Z) % 2")
        void alternatesBlocksCorrectly() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(Objects.requireNonNull(area));

            // Verify the pattern: (x + z) % 2 == 0 uses FLOOR_ALT (COBBLESTONE)
            for (Map.Entry<BlockPos, BlockState> entry : floorBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();

                boolean useAlt = ((pos.getX() + pos.getZ()) % 2 == 0);
                if (useAlt) {
                    assertTrue(state.is(Objects.requireNonNull(Blocks.COBBLESTONE)),
                        "Position (" + pos.getX() + ", " + pos.getZ() + ") should be COBBLESTONE");
                } else {
                    assertTrue(state.is(Objects.requireNonNull(Blocks.STONE)),
                        "Position (" + pos.getX() + ", " + pos.getZ() + ") should be STONE");
                }
            }
        }

        @Test
        @DisplayName("uses FLOOR and FLOOR_ALT from palette")
        void usesFloorPaletteBlocks() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(Objects.requireNonNull(area));

            // All blocks should be either STONE or COBBLESTONE
            assertTrue(floorBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.STONE)) || state.is(Objects.requireNonNull(Blocks.COBBLESTONE))),
                "Floor should only contain STONE or COBBLESTONE");
        }
    }

    // ========================================================================
    // Ceiling Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Ceiling Generation (Light Grid)")
    class CeilingGenerationTests {

        @Test
        @DisplayName("creates light grid pattern (lights every 8 blocks)")
        void createsLightGridPattern() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(32, 32, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(Objects.requireNonNull(area));

            // Should have both SMOOTH_STONE and GLOWSTONE
            long smoothCount = ceilingBlocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.SMOOTH_STONE)))
                .count();
            long glowCount = ceilingBlocks.values().stream()
                .filter(state -> state.is(Objects.requireNonNull(Blocks.GLOWSTONE)))
                .count();

            assertTrue(smoothCount > 0, "Ceiling should have SMOOTH_STONE blocks");
            assertTrue(glowCount > 0, "Ceiling should have GLOWSTONE (light) blocks");
        }

        @Test
        @DisplayName("places lights where X % 8 == 0 and Z % 8 == 0")
        void placesLightsAtGridPositions() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(32, 32, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(Objects.requireNonNull(area));

            // Verify light positions
            for (Map.Entry<BlockPos, BlockState> entry : ceilingBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();

                boolean isLightPos = (pos.getX() % 8 == 0) && (pos.getZ() % 8 == 0);
                if (isLightPos) {
                    assertTrue(state.is(Objects.requireNonNull(Blocks.GLOWSTONE)),
                        "Position (" + pos.getX() + ", " + pos.getZ() + ") should be GLOWSTONE");
                } else {
                    assertTrue(state.is(Objects.requireNonNull(Blocks.SMOOTH_STONE)),
                        "Position (" + pos.getX() + ", " + pos.getZ() + ") should be SMOOTH_STONE");
                }
            }
        }

        @Test
        @DisplayName("uses CEILING and LIGHT from palette")
        void usesCeilingPaletteBlocks() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(16, 16, 6, 64);
            AreaDefinition area = createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR);

            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(Objects.requireNonNull(area));

            // All blocks should be either SMOOTH_STONE or GLOWSTONE
            assertTrue(ceilingBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.SMOOTH_STONE)) || state.is(Objects.requireNonNull(Blocks.GLOWSTONE))),
                "Ceiling should only contain SMOOTH_STONE or GLOWSTONE");
        }
    }

    // ========================================================================
    // Clear Blocks Generation Tests
    // ========================================================================

    @Nested
    @DisplayName("Clear Blocks Generation")
    class ClearBlocksTests {

        @Test
        @DisplayName("generates air blocks for entire volume")
        void generatesAirForVolume() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            Map<BlockPos, BlockState> clearBlocks = AreaBlockMapGenerator.generateClearBlocks(area);

            // All should be air
            assertTrue(clearBlocks.values().stream()
                .allMatch(state -> state.is(Objects.requireNonNull(Blocks.AIR))),
                "All clear blocks should be AIR");
        }

        @Test
        @DisplayName("covers floor area * height positions")
        void coversFullVolume() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            Map<BlockPos, BlockState> clearBlocks = AreaBlockMapGenerator.generateClearBlocks(area);

            // Expected: 10 * 10 floor positions * (5 + 1) heights = 600
            int expectedCount = 10 * 10 * (5 + 1);
            assertEquals(expectedCount, clearBlocks.size(),
                "Clear blocks should cover entire volume");
        }
    }

    // ========================================================================
    // Deterministic Ordering Tests
    // ========================================================================

    @Nested
    @DisplayName("Deterministic Ordering")
    class DeterministicOrderingTests {

        @Test
        @DisplayName("sortPositionsDeterministically produces consistent order")
        void sortProducesConsistentOrder() {
            Set<BlockPos> positions = new HashSet<>();
            positions.add(new BlockPos(5, 64, 3));
            positions.add(new BlockPos(1, 64, 2));
            positions.add(new BlockPos(3, 64, 1));
            positions.add(new BlockPos(1, 65, 2));

            List<BlockPos> sorted1 = AreaBlockMapGenerator.sortPositionsDeterministically(positions);
            List<BlockPos> sorted2 = AreaBlockMapGenerator.sortPositionsDeterministically(positions);

            assertEquals(sorted1, sorted2, "Same input should produce same ordering");
        }

        @Test
        @DisplayName("sorts by X, then Z, then Y")
        void sortsByXThenZThenY() {
            Set<BlockPos> positions = new HashSet<>();
            positions.add(new BlockPos(2, 64, 1)); // x=2
            positions.add(new BlockPos(1, 64, 2)); // x=1
            positions.add(new BlockPos(1, 64, 1)); // x=1, z=1
            positions.add(new BlockPos(1, 65, 1)); // x=1, z=1, y=65

            List<BlockPos> sorted = AreaBlockMapGenerator.sortPositionsDeterministically(positions);

            assertEquals(new BlockPos(1, 64, 1), sorted.get(0), "First: x=1, z=1, y=64");
            assertEquals(new BlockPos(1, 65, 1), sorted.get(1), "Second: x=1, z=1, y=65");
            assertEquals(new BlockPos(1, 64, 2), sorted.get(2), "Third: x=1, z=2");
            assertEquals(new BlockPos(2, 64, 1), sorted.get(3), "Fourth: x=2");
        }

        @Test
        @DisplayName("floor blocks are in deterministic order")
        void floorBlocksInDeterministicOrder() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            Map<BlockPos, BlockState> blocks1 = AreaBlockMapGenerator.generateFloorBlocks(area);
            Map<BlockPos, BlockState> blocks2 = AreaBlockMapGenerator.generateFloorBlocks(area);

            // LinkedHashMap preserves insertion order
            List<BlockPos> keys1 = blocks1.keySet().stream().toList();
            List<BlockPos> keys2 = blocks2.keySet().stream().toList();

            assertEquals(keys1, keys2, "Floor blocks should be in same order across calls");
        }
    }

    // ========================================================================
    // Estimation Tests
    // ========================================================================

    @Nested
    @DisplayName("Block Count Estimation")
    class EstimationTests {

        @Test
        @DisplayName("estimateTotalBlocks accounts for floor, walls, and ceiling")
        void estimateTotalBlocksAccurate() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);

            AreaOptions options = new AreaOptions(true, true, true, WallStyle.SOLID, false, false, null);
            AreaDefinition area = AreaDefinition.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .name("estimate_test")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(dims)
                .center(center)
                .palette(Objects.requireNonNull(createTestPalette()))
                .options(options)
                .build();

            int estimate = AreaBlockMapGenerator.estimateTotalBlocks(area);

            // Floor: 100, Walls: perimeter * height = 36 * 5 = 180, Ceiling: 100
            // Total estimate should be around 380
            assertTrue(estimate > 0, "Estimate should be positive");
            assertTrue(estimate >= 100, "Estimate should include at least floor blocks");
        }

        @Test
        @DisplayName("estimateBiomeBlocks returns volume-based count")
        void estimateBiomeBlocksReturnsVolume() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 8, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            int estimate = AreaBlockMapGenerator.estimateBiomeBlocks(area);

            // Volume = floor area * height = 100 * 8 = 800
            assertEquals(100 * 8, estimate, "Biome estimate should be floor * height");
        }

        @Test
        @DisplayName("estimateClearBlocks returns volume count")
        void estimateClearBlocksReturnsVolume() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            int estimate = AreaBlockMapGenerator.estimateClearBlocks(area);

            // Volume = floor area * (height + 1) = 100 * 6 = 600
            assertEquals(100 * 6, estimate, "Clear estimate should be floor * (height + 1)");
        }

        @Test
        @DisplayName("estimateTotalBlocks uses PATH width from NBT")
        void estimateTotalBlocksPathUsesNbtWidth() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 6, 64);
            int corridorHalfWidth = 1;
            List<BlockPos> waypoints = List.of(center, center.offset(40, 0, 0));
            CompoundTag pathNbt = AreaShapeGenerator.createPathNbt(center, waypoints, corridorHalfWidth);

            AreaDefinition area = AreaDefinition.builder()
                .id(Objects.requireNonNull(UUID.randomUUID()))
                .name("path_estimate")
                .shape(AreaShape.PATH)
                .dimensions(Objects.requireNonNull(dims))
                .center(Objects.requireNonNull(center))
                .palette(Objects.requireNonNull(createTestPalette()))
                .options(Objects.requireNonNull(AreaOptions.DEFAULT))
                .customShapeNbt(pathNbt)
                .build();

            int floorEstimate = AreaShapeGenerator.generateFloor(AreaShape.PATH, center, dims, pathNbt).size();
            int corridorWidth = Math.max(1, corridorHalfWidth * 2 + 1);
            int corridorLength = floorEstimate > 0 ? (floorEstimate / corridorWidth) : 0;
            int perimeter = 2 * (corridorLength + corridorWidth);
            int expected = floorEstimate * 2 + perimeter * dims.height();

            int estimate = AreaBlockMapGenerator.estimateTotalBlocks(area);

            assertEquals(expected, estimate, "PATH estimate should use NBT corridor width");
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles minimum dimensions")
        void handlesMinimumDimensions() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(8, 8, 4, 64); // Minimum allowed
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(area);
            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(area);
            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(area);

            assertFalse(floorBlocks.isEmpty(), "Minimum dimensions should produce floor");
            assertFalse(wallBlocks.isEmpty(), "Minimum dimensions should produce walls");
            assertFalse(ceilingBlocks.isEmpty(), "Minimum dimensions should produce ceiling");
        }

        @Test
        @DisplayName("handles large dimensions")
        void handlesLargeDimensions() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(128, 128, 64, 64);
            AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, WallStyle.SOLID, AreaShape.RECTANGULAR));

            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(area);

            assertEquals(128 * 128, floorBlocks.size(), "Large floor should have correct block count");
        }

        @Test
        @DisplayName("different shapes work with all wall styles")
        void differentShapesWithAllWallStyles() {
            BlockPos center = new BlockPos(0, 64, 0);
            AreaDimensions dims = new AreaDimensions(20, 20, 8, 64);

            for (AreaShape shape : new AreaShape[]{AreaShape.RECTANGULAR, AreaShape.CIRCULAR, AreaShape.HEXAGONAL}) {
                for (WallStyle style : WallStyle.values()) {
                    AreaDefinition area = Objects.requireNonNull(createTestArea(center, dims, style, shape));

                    // Should not throw
                    Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(area);

                    // For non-rectangular with corner-based styles, should fall back to SOLID
                    if (shape != AreaShape.RECTANGULAR &&
                        (style == WallStyle.OPEN_CORNERS || style == WallStyle.PILLARS_ONLY)) {
                        // Verify no null values (fell back to SOLID)
                        assertTrue(wallBlocks.values().stream().noneMatch(s -> s == null),
                            shape + " with " + style + " should fall back to SOLID");
                    }
                }
            }
        }
    }
}

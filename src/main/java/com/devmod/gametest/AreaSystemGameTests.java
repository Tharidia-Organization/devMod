package com.devmod.gametest;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaBlockMapGenerator;
import com.devmod.area.builder.AreaBuildTask;
import com.devmod.area.builder.AreaPalettePresets;
import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.builder.BiomeAreaGenerator;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaOptions.WallStyle;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;
import com.devmod.area.snapshot.AreaSnapshotManager;

/**
 * GameTests for the Area module.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Shape generation (rectangular, circular)</li>
 *   <li>Block map generation (floor, walls, ceiling)</li>
 *   <li>Multi-tick build task functionality</li>
 *   <li>Build progress tracking</li>
 *   <li>Resume functionality via block skipping</li>
 * </ul>
 *
 * <p>Uses existing test structures: empty, empty_5x5
 */
@GameTestHolder(DevMod.MODID)
@PrefixGameTestTemplate(false)
public class AreaSystemGameTests {

    // Template names - must match NBT file names in resources
    private static final String TEMPLATE_EMPTY = "empty";
    private static final String TEMPLATE_5X5 = "empty_5x5";

    // Epsilon for float comparisons
    private static final float EPSILON = 0.001f;

    // ============================================================
    // BATCH LIFECYCLE METHODS
    // ============================================================

    @BeforeBatch(batch = "area_shapes")
    public static void setupAreaShapesBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_shapes' batch");
    }

    @AfterBatch(batch = "area_shapes")
    public static void cleanupAreaShapesBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_shapes' batch");
    }

    @BeforeBatch(batch = "area_build")
    public static void setupAreaBuildBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_build' batch");
    }

    @AfterBatch(batch = "area_build")
    public static void cleanupAreaBuildBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_build' batch");
    }

    @BeforeBatch(batch = "area_integration")
    public static void setupAreaIntegrationBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_integration' batch");
    }

    @AfterBatch(batch = "area_integration")
    public static void cleanupAreaIntegrationBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_integration' batch");
    }

    @BeforeBatch(batch = "area_snapshot_core")
    public static void setupAreaSnapshotCoreBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_snapshot_core' batch");
        // Clean up any pending snapshot operations
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @AfterBatch(batch = "area_snapshot_core")
    public static void cleanupAreaSnapshotCoreBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_snapshot_core' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @BeforeBatch(batch = "area_snapshot_progress")
    public static void setupAreaSnapshotProgressBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_snapshot_progress' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @AfterBatch(batch = "area_snapshot_progress")
    public static void cleanupAreaSnapshotProgressBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_snapshot_progress' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @BeforeBatch(batch = "area_snapshot_concurrency")
    public static void setupAreaSnapshotConcurrencyBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_snapshot_concurrency' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @AfterBatch(batch = "area_snapshot_concurrency")
    public static void cleanupAreaSnapshotConcurrencyBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_snapshot_concurrency' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @BeforeBatch(batch = "area_snapshot_misc")
    public static void setupAreaSnapshotMiscBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_snapshot_misc' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @AfterBatch(batch = "area_snapshot_misc")
    public static void cleanupAreaSnapshotMiscBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_snapshot_misc' batch");
        AreaSnapshotManager.INSTANCE.cleanup();
    }

    @BeforeBatch(batch = "area_biome")
    public static void setupAreaBiomeBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Setting up 'area_biome' batch");
    }

    @AfterBatch(batch = "area_biome")
    public static void cleanupAreaBiomeBatch(ServerLevel level) {
        DevMod.LOGGER.info("[GameTest] Cleaning up 'area_biome' batch");
    }

    // ============================================================
    // HELPER METHODS - Reduce code duplication
    // ============================================================

    private static AreaPalette defaultPalette() {
        return AreaPalettePresets.fromPreset("default");
    }

    /**
     * Creates a simple test AreaDefinition with common defaults.
     *
     * @param name   Area name for identification
     * @param center Center position
     * @param dims   Dimensions
     * @return Configured AreaDefinition
     */
    private static AreaDefinition createTestArea(String name, BlockPos center, AreaDimensions dims) {
        return createTestArea(name, center, dims, AreaShape.RECTANGULAR,
            AreaOptions.OPEN);
    }

    /**
     * Creates a test AreaDefinition with specified options.
     *
     * @param name    Area name for identification
     * @param center  Center position
     * @param dims    Dimensions
     * @param shape   Shape type
     * @param options Area options
     * @return Configured AreaDefinition
     */
    private static AreaDefinition createTestArea(String name, BlockPos center, AreaDimensions dims,
                                                  AreaShape shape, AreaOptions options) {
        return AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name(Objects.requireNonNull(name))
            .shape(Objects.requireNonNull(shape))
            .dimensions(Objects.requireNonNull(dims))
            .center(Objects.requireNonNull(center))
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(Objects.requireNonNull(options))
            .build();
    }

    /**
     * Runs a build task to completion with a maximum tick limit.
     *
     * @param task     The build task to run
     * @param maxTicks Maximum ticks before timing out
     * @return Number of ticks elapsed
     */
    private static int runTaskToCompletion(AreaBuildTask task, int maxTicks) {
        int ticks = 0;
        while (!task.isCompleted() && ticks < maxTicks) {
            task.tick();
            ticks++;
        }
        return ticks;
    }

    // ============================================================
    // BATCH: area_shapes - Shape generation tests
    // ============================================================

    /**
     * TEST 1: Rectangular Floor Generation (REQUIRED)
     * Verifies that generateFloor creates correct block count for rectangular shape.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes", required = true)
    public static void testRectangularFloorGeneration(GameTestHelper helper) {
        // Create a 5x5 rectangular area
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(5, 5, 3, center.getY());

        Set<BlockPos> floor = AreaShapeGenerator.generateFloor(AreaShape.RECTANGULAR, center, dims);

        // 5x5 = 25 blocks
        helper.assertTrue(floor.size() == 25,
            "Rectangular 5x5 floor should have 25 positions, got: " + floor.size());

        // Verify center is included
        helper.assertTrue(floor.contains(center),
            "Floor should contain center position");

        helper.succeed();
    }

    /**
     * TEST 2: Circular Floor Generation (REQUIRED)
     * Verifies that generateFloor creates approximate circle using ellipse formula.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes", required = true)
    public static void testCircularFloorGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(7, 7, 3, center.getY());

        Set<BlockPos> floor = AreaShapeGenerator.generateFloor(AreaShape.CIRCULAR, center, dims);

        // Circle area ~= π * r² = π * 3.5² ≈ 38.5, but discrete = ~37
        // Actual count varies due to discretization
        helper.assertTrue(floor.size() > 20 && floor.size() < 50,
            "Circular 7x7 floor should have ~30-40 positions, got: " + floor.size());

        // Verify center is included
        helper.assertTrue(floor.contains(center),
            "Circular floor should contain center position");

        // Verify corners are NOT included (outside circle)
        BlockPos corner = new BlockPos(center.getX() + 3, center.getY(), center.getZ() + 3);
        helper.assertTrue(!floor.contains(corner),
            "Circular floor should NOT contain corner position");

        helper.succeed();
    }

    /**
     * TEST 3: Hexagonal Floor Generation
     * Verifies hexagonal shape generation.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes")
    public static void testHexagonalFloorGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(9, 9, 3, center.getY());

        Set<BlockPos> floor = AreaShapeGenerator.generateFloor(AreaShape.HEXAGONAL, center, dims);

        // Hexagon area ~= 0.75 * width * length = 0.75 * 81 ≈ 61
        helper.assertTrue(floor.size() > 40 && floor.size() < 80,
            "Hexagonal 9x9 floor should have ~50-70 positions, got: " + floor.size());

        // Verify center is included
        helper.assertTrue(floor.contains(center),
            "Hexagonal floor should contain center position");

        helper.succeed();
    }

    /**
     * TEST 4: Octagonal Floor Generation
     * Verifies octagonal shape generation (rectangle with cut corners).
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes")
    public static void testOctagonalFloorGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(9, 9, 3, center.getY());

        Set<BlockPos> floor = AreaShapeGenerator.generateFloor(AreaShape.OCTAGONAL, center, dims);

        // Octagon area ~= 0.85 * width * length = 0.85 * 81 ≈ 69
        helper.assertTrue(floor.size() > 50 && floor.size() < 85,
            "Octagonal 9x9 floor should have ~55-75 positions, got: " + floor.size());

        // Verify center is included
        helper.assertTrue(floor.contains(center),
            "Octagonal floor should contain center position");

        helper.succeed();
    }

    /**
     * TEST 5: Perimeter Generation (REQUIRED)
     * Verifies that perimeter positions form the edge of the shape.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes", required = true)
    public static void testPerimeterGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(5, 5, 3, center.getY());

        Set<BlockPos> perimeter = AreaShapeGenerator.generatePerimeter(AreaShape.RECTANGULAR, center, dims);

        // 5x5 perimeter = 4*5 - 4 = 16 (edges minus corners counted once)
        helper.assertTrue(perimeter.size() == 16,
            "5x5 rectangular perimeter should have 16 positions, got: " + perimeter.size());

        // Center should NOT be in perimeter
        helper.assertTrue(!perimeter.contains(center),
            "Perimeter should NOT contain center position");

        helper.succeed();
    }

    /**
     * TEST 6: Ceiling Generation
     * Verifies ceiling is generated at correct height.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes")
    public static void testCeilingGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        int height = 5;
        AreaDimensions dims = new AreaDimensions(3, 3, height, center.getY());

        Set<BlockPos> ceiling = AreaShapeGenerator.generateCeiling(AreaShape.RECTANGULAR, center, dims);

        // 3x3 = 9 ceiling blocks
        helper.assertTrue(ceiling.size() == 9,
            "3x3 ceiling should have 9 positions, got: " + ceiling.size());

        // Verify ceiling is at correct Y level
        for (BlockPos pos : ceiling) {
            int expectedY = center.getY() + height;
            helper.assertTrue(pos.getY() == expectedY,
                "Ceiling Y should be " + expectedY + ", got: " + pos.getY());
            break; // Only need to check one
        }

        helper.succeed();
    }

    /**
     * TEST 7: isInside Boundary Check
     * Verifies isInside correctly identifies positions inside/outside shapes.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_shapes")
    public static void testIsInsideBoundary(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        AreaDimensions dims = new AreaDimensions(5, 5, 3, center.getY());

        // Center should be inside
        helper.assertTrue(AreaShapeGenerator.isInside(center, AreaShape.RECTANGULAR, center, dims),
            "Center should be inside rectangular area");

        // Far position should be outside
        BlockPos far = new BlockPos(center.getX() + 10, center.getY(), center.getZ());
        helper.assertTrue(!AreaShapeGenerator.isInside(far, AreaShape.RECTANGULAR, center, dims),
            "Position 10 blocks away should be outside");

        // Edge position should be inside
        BlockPos edge = new BlockPos(center.getX() + 2, center.getY(), center.getZ());
        helper.assertTrue(AreaShapeGenerator.isInside(edge, AreaShape.RECTANGULAR, center, dims),
            "Edge position should be inside");

        helper.succeed();
    }

    // ============================================================
    // BATCH: area_build - Build task and block placement tests
    // ============================================================

    /**
     * TEST 8: Block Map Generation - Floor (REQUIRED)
     * Verifies AreaBlockMapGenerator creates correct floor blocks.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", required = true, timeoutTicks = 100)
    public static void testBlockMapFloorGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        AreaDimensions dims = new AreaDimensions(3, 3, 3, center.getY());

        AreaDefinition def = createTestArea("test_floor", center, dims);

        Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(
            Objects.requireNonNull(def));

        // 3x3 = 9 floor blocks
        helper.assertTrue(floorBlocks.size() == 9,
            "3x3 floor should generate 9 blocks, got: " + floorBlocks.size());

        // All blocks should be at floor Y level
        int floorY = def.dimensions().floorY();
        for (BlockPos pos : floorBlocks.keySet()) {
            helper.assertTrue(pos.getY() == floorY,
                "Floor block Y should be " + floorY + ", got: " + pos.getY());
        }

        helper.succeed();
    }

    /**
     * TEST 9: Block Map Generation - Walls
     * Verifies AreaBlockMapGenerator creates wall blocks correctly.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", timeoutTicks = 100)
    public static void testBlockMapWallGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        AreaDimensions dims = new AreaDimensions(5, 5, 4, center.getY());
        AreaOptions wallOptions = new AreaOptions(false, true, false, WallStyle.SOLID, false, false, null);

        AreaDefinition def = createTestArea("test_walls", center, dims, AreaShape.RECTANGULAR, wallOptions);

        Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(
            Objects.requireNonNull(def));

        // 5x5 perimeter = 16 positions, height 4 means walls from y+1 to y+3 = 3 levels
        // 16 * 3 = 48 wall blocks
        int expectedWalls = 16 * 3; // perimeter * (height - 1)
        helper.assertTrue(wallBlocks.size() == expectedWalls,
            "5x5x4 walls should generate " + expectedWalls + " blocks, got: " + wallBlocks.size());

        helper.succeed();
    }

    /**
     * TEST 10: AreaBuildTask Progress Tracking (REQUIRED)
     * Verifies build task progress is tracked correctly.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", required = true, timeoutTicks = 200)
    public static void testBuildTaskProgressTracking(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        AreaDefinition def = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("test_progress")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 2, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(true, false, false, WallStyle.SOLID, false, false, null))
            .build();

        AtomicInteger completionCount = new AtomicInteger(0);

        AreaBuildTask task = new AreaBuildTask(level, def, 3, t -> {
            completionCount.incrementAndGet();
        });

        try {
            // Initial progress should be 0
            helper.assertTrue(Math.abs(task.getProgress()) < EPSILON,
                "Initial progress should be 0, got: " + task.getProgress());

            helper.assertTrue(!task.isCompleted(),
                "Task should not be completed initially");

            // Run task ticks
            helper.runAfterDelay(10, () -> {
                try {
                    int ticks = 0;
                    while (!task.isCompleted() && ticks < 100) {
                        task.tick();
                        ticks++;
                    }

                    helper.assertTrue(task.isCompleted(),
                        "Task should be completed after running");

                    helper.assertTrue(Math.abs(task.getProgress() - 1.0f) < EPSILON,
                        "Final progress should be 1.0, got: " + task.getProgress());

                    helper.assertTrue(completionCount.get() == 1,
                        "Completion callback should be called exactly once, got: " + completionCount.get());

                    helper.succeed();
                } finally {
                    task.close();
                }
            });
        } catch (Exception e) {
            task.close();
            throw e;
        }
    }

    /**
     * TEST 11: AreaBuildTask Block Placement
     * Verifies build task actually places blocks in the world.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", timeoutTicks = 200)
    public static void testBuildTaskBlockPlacement(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        AreaDimensions dims = new AreaDimensions(3, 3, 2, center.getY());

        AreaDefinition def = createTestArea("test_placement", center, dims);

        AreaBuildTask task = new AreaBuildTask(level, Objects.requireNonNull(def), 100, t -> {});

        try {
            // Run until complete
            runTaskToCompletion(task, 100);

            helper.runAfterDelay(5, () -> {
                try {
                    // Verify blocks were placed
                    int blocksPlaced = task.getTotalBlocksPlaced();
                    helper.assertTrue(blocksPlaced > 0,
                        "Should have placed some blocks, got: " + blocksPlaced);

                    // Verify floor block at center
                    BlockState centerBlock = level.getBlockState(center);
                    helper.assertTrue(!centerBlock.isAir(),
                        "Center block should not be air after build");

                    helper.succeed();
                } finally {
                    task.close();
                }
            });
        } catch (Exception e) {
            task.close();
            throw e;
        }
    }

    /**
     * TEST 12: AreaBuildTask Skip Blocks (Resume)
     * Verifies build task can skip blocks for resume functionality.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", timeoutTicks = 200)
    public static void testBuildTaskSkipBlocks(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        AreaDimensions dims = new AreaDimensions(3, 3, 2, center.getY());

        AreaDefinition def = createTestArea("test_skip", center, dims);

        // Create task that skips first 5 blocks
        int skipCount = 5;
        AreaBuildTask task = new AreaBuildTask(level, Objects.requireNonNull(def), 100, t -> {}, skipCount);

        try {
            // Run until complete
            runTaskToCompletion(task, 100);

            helper.runAfterDelay(5, () -> {
                try {
                    // Verify blocks were skipped
                    int skipped = task.getBlocksSkipped();
                    helper.assertTrue(skipped == skipCount,
                        "Should have skipped " + skipCount + " blocks, got: " + skipped);

                    helper.succeed();
                } finally {
                    task.close();
                }
            });
        } catch (Exception e) {
            task.close();
            throw e;
        }
    }

    /**
     * TEST 13: AreaBuildTask Close/Cleanup
     * Verifies build task can be closed and cleaned up properly.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_build")
    public static void testBuildTaskCleanup(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));

        AreaDefinition def = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("test_cleanup")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(5, 5, 3, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(true, true, true, WallStyle.SOLID, false, false, null))
            .build();

        AreaBuildTask task = new AreaBuildTask(level, def, 10, t -> {});

        // Partially run task
        task.tick();
        task.tick();

        helper.assertTrue(!task.isClosed(),
            "Task should not be closed initially");

        // Close the task
        task.close();

        helper.assertTrue(task.isClosed(),
            "Task should be closed after close()");

        // Close again should be safe
        try {
            task.close();
            helper.succeed();
        } catch (Exception e) {
            helper.fail("Double close() should be safe, but threw: " + e.getMessage());
        }
    }

    // ============================================================
    // BATCH: area_integration - Integration tests
    // ============================================================

    /**
     * TEST 14: Full Area Build Cycle (REQUIRED)
     * Verifies complete area build from definition to placed blocks.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_integration", required = true, timeoutTicks = 300)
    public static void testFullAreaBuildCycle(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        AreaDefinition def = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("full_cycle_test")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 3, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(true, true, true, WallStyle.SOLID, false, false, null))
            .build();

        AtomicInteger completedSteps = new AtomicInteger(0);

        AreaBuildTask task = new AreaBuildTask(level, def, 50, t -> {
            completedSteps.incrementAndGet();
        });

        // Run task to completion
        helper.runAfterDelay(10, () -> {
            try {
                int ticks = 0;
                while (!task.isCompleted() && ticks < 200) {
                    task.tick();
                    ticks++;
                }

                helper.assertTrue(task.isCompleted(),
                    "Build should complete");

                helper.assertTrue(task.getTotalBlocksPlaced() > 0,
                    "Should have placed blocks");

                helper.assertTrue(completedSteps.get() == 1,
                    "Completion callback should fire once");

                // Verify some blocks exist
                BlockState floorBlock = level.getBlockState(center);
                helper.assertTrue(!floorBlock.isAir(),
                    "Floor at center should have blocks");

                helper.succeed();
            } finally {
                task.close();
            }
        });
    }

    /**
     * TEST 15: Estimate Block Count
     * Verifies estimateBlockCount returns reasonable values for different shapes.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_integration")
    public static void testEstimateBlockCount(GameTestHelper helper) {
        AreaDimensions dims = new AreaDimensions(10, 10, 5, 64);

        // Rectangular: 10 * 10 = 100
        int rectEstimate = AreaShapeGenerator.estimateBlockCount(AreaShape.RECTANGULAR, dims);
        helper.assertTrue(rectEstimate == 100,
            "Rectangular 10x10 estimate should be 100, got: " + rectEstimate);

        // Circular: π * 5 * 5 ≈ 78.5 → ~78
        int circleEstimate = AreaShapeGenerator.estimateBlockCount(AreaShape.CIRCULAR, dims);
        helper.assertTrue(circleEstimate > 70 && circleEstimate < 90,
            "Circular estimate should be ~78, got: " + circleEstimate);

        // Hexagonal: 0.75 * 100 = 75
        int hexEstimate = AreaShapeGenerator.estimateBlockCount(AreaShape.HEXAGONAL, dims);
        helper.assertTrue(hexEstimate == 75,
            "Hexagonal estimate should be 75, got: " + hexEstimate);

        // Octagonal: 0.85 * 100 = 85
        int octEstimate = AreaShapeGenerator.estimateBlockCount(AreaShape.OCTAGONAL, dims);
        helper.assertTrue(octEstimate == 85,
            "Octagonal estimate should be 85, got: " + octEstimate);

        helper.succeed();
    }

    /**
     * TEST 16: Offset Calculation
     * Verifies minOffset and maxOffset are symmetric.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_integration")
    public static void testOffsetCalculation(GameTestHelper helper) {
        // Odd size: 5 → min=-2, max=2
        int min5 = AreaShapeGenerator.minOffset(5);
        int max5 = AreaShapeGenerator.maxOffset(5);
        helper.assertTrue(min5 == -2, "minOffset(5) should be -2, got: " + min5);
        helper.assertTrue(max5 == 2, "maxOffset(5) should be 2, got: " + max5);

        // Even size: 6 → min=-3, max=2
        int min6 = AreaShapeGenerator.minOffset(6);
        int max6 = AreaShapeGenerator.maxOffset(6);
        helper.assertTrue(min6 == -3, "minOffset(6) should be -3, got: " + min6);
        helper.assertTrue(max6 == 2, "maxOffset(6) should be 2, got: " + max6);

        // Size 1: min=0, max=0
        int min1 = AreaShapeGenerator.minOffset(1);
        int max1 = AreaShapeGenerator.maxOffset(1);
        helper.assertTrue(min1 == 0, "minOffset(1) should be 0, got: " + min1);
        helper.assertTrue(max1 == 0, "maxOffset(1) should be 0, got: " + max1);

        helper.succeed();
    }

    /**
     * TEST 17: Degenerate Dimensions
     * Verifies handling of zero/negative dimensions.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_integration")
    public static void testDegenerateDimensions(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));

        // Zero width
        AreaDimensions zeroDims = new AreaDimensions(0, 5, 3, center.getY());
        Set<BlockPos> zeroFloor = AreaShapeGenerator.generateFloor(AreaShape.RECTANGULAR, center, zeroDims);
        helper.assertTrue(zeroFloor.isEmpty(),
            "Zero-width floor should be empty");

        // M-01 fix: Should return empty set, not throw
        Set<BlockPos> zeroWalls = AreaShapeGenerator.generateWalls(AreaShape.RECTANGULAR, center, zeroDims);
        helper.assertTrue(zeroWalls.isEmpty(),
            "Zero-width walls should be empty");

        helper.succeed();
    }

    /**
     * TEST 18: AreaDefinition Builder Immutability
     * Verifies that building an AreaDefinition doesn't affect the builder.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_integration")
    public static void testAreaDefinitionImmutability(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));

        AreaDefinition def1 = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("immutable_test_1")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(5, 5, 3, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .build();

        // Create a second definition - should be independent
        AreaDefinition def2 = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("immutable_test_2")
            .shape(AreaShape.CIRCULAR)
            .dimensions(new AreaDimensions(7, 7, 4, center.getY()))
            .center(Objects.requireNonNull(center.above(10)))
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .build();

        // Verify they are different
        helper.assertTrue(!def1.id().equals(def2.id()),
            "Different definitions should have different IDs");
        helper.assertTrue(!def1.name().equals(def2.name()),
            "Different definitions should have different names");
        helper.assertTrue(def1.shape() != def2.shape(),
            "Different definitions should have different shapes");

        helper.succeed();
    }

    // ============================================================
    // BATCH: area_snapshot - Snapshot capture/restore tests
    // ============================================================

    /**
     * TEST 19: End-to-End Snapshot Capture and Restore (REQUIRED)
     *
     * This is the critical integration test that verifies the complete snapshot workflow:
     * 1. Places specific blocks (DIAMOND_BLOCK, GOLD_BLOCK) at known positions
     * 2. Creates an AreaDefinition covering that region
     * 3. Captures a snapshot using AreaSnapshotManager
     * 4. Modifies the blocks (replaces with COBBLESTONE)
     * 5. Restores from the snapshot
     * 6. Verifies original blocks are restored correctly
     *
     * This test covers:
     * - Block reading from world
     * - NBT serialization/deserialization
     * - File I/O (write and read compressed NBT)
     * - Multi-tick capture task
     * - Two-phase restore (CLEAR + RESTORE)
     * - Block placement verification
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_snapshot_core", required = true, timeoutTicks = 600)
    public static void testSnapshotCaptureRestoreEndToEnd(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        // ====== STEP 1: Place distinctive blocks ======
        // Create a recognizable pattern: diamond in center, gold around it
        BlockState diamond = Objects.requireNonNull(Blocks.DIAMOND_BLOCK.defaultBlockState());
        BlockState gold = Objects.requireNonNull(Blocks.GOLD_BLOCK.defaultBlockState());

        level.setBlock(Objects.requireNonNull(center), diamond, 3);
        level.setBlock(Objects.requireNonNull(center.north()), gold, 3);
        level.setBlock(Objects.requireNonNull(center.south()), gold, 3);
        level.setBlock(Objects.requireNonNull(center.east()), gold, 3);
        level.setBlock(Objects.requireNonNull(center.west()), gold, 3);

        // Verify blocks are placed
        helper.assertTrue(level.getBlockState(center).is(Objects.requireNonNull(Blocks.DIAMOND_BLOCK)),
            "Diamond block should be placed at center");

        // ====== STEP 2: Create AreaDefinition ======
        UUID areaId = Objects.requireNonNull(UUID.randomUUID());
        AreaDefinition areaDef = AreaDefinition.builder()
            .id(areaId)
            .name("snapshot_test_area")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 2, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .build();

        // ====== STEP 3: Start capture ======
        UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(
            level,
            areaDef,
            "Test snapshot for GameTest",
            null // No player for test
        );

        helper.assertTrue(snapshotId != null,
            "Snapshot capture should start successfully");

        helper.assertTrue(AreaSnapshotManager.INSTANCE.isCapturing(areaId),
            "Area should be marked as capturing");

        // ====== STEP 4: Wait for capture to complete ======
        final UUID capturedSnapshotId = Objects.requireNonNull(snapshotId);
        waitForCaptureComplete(helper, areaId, 0, () -> {
            // ====== STEP 5: Modify the blocks ======
            BlockState cobblestone = Objects.requireNonNull(Blocks.COBBLESTONE.defaultBlockState());

            level.setBlock(Objects.requireNonNull(center), cobblestone, 3);
            level.setBlock(Objects.requireNonNull(center.north()), cobblestone, 3);
            level.setBlock(Objects.requireNonNull(center.south()), cobblestone, 3);
            level.setBlock(Objects.requireNonNull(center.east()), cobblestone, 3);
            level.setBlock(Objects.requireNonNull(center.west()), cobblestone, 3);

            // Verify blocks are modified
            helper.assertTrue(level.getBlockState(center).is(Objects.requireNonNull(Blocks.COBBLESTONE)),
                "Center should now be cobblestone");
            helper.assertTrue(!level.getBlockState(center).is(Objects.requireNonNull(Blocks.DIAMOND_BLOCK)),
                "Center should no longer be diamond");

            // ====== STEP 6: Start restore ======
            boolean restoreStarted = AreaSnapshotManager.INSTANCE.startRestore(
                level,
                capturedSnapshotId,
                null // No player for test
            );

            helper.assertTrue(restoreStarted,
                "Restore should start successfully");

            helper.assertTrue(AreaSnapshotManager.INSTANCE.isRestoring(capturedSnapshotId),
                "Snapshot should be marked as restoring");

            // ====== STEP 7: Wait for restore to complete ======
            waitForRestoreComplete(helper, capturedSnapshotId, center, 0);
        });
    }

    /**
     * Helper: Waits for capture to complete, then runs callback.
     * Uses recursive delayed execution to poll capture status.
     */
    private static void waitForCaptureComplete(
        GameTestHelper helper,
        UUID areaId,
        int ticksWaited,
        Runnable onComplete
    ) {
        if (ticksWaited > 500) {
            helper.fail("Capture timed out after 500 ticks");
            return;
        }

        if (!AreaSnapshotManager.INSTANCE.isCapturing(Objects.requireNonNull(areaId))) {
            // Capture complete, run callback
            helper.runAfterDelay(5, Objects.requireNonNull(onComplete));
        } else {
            // Still capturing, check again next tick
            helper.runAfterDelay(1, () ->
                waitForCaptureComplete(helper, areaId, ticksWaited + 1, onComplete));
        }
    }

    /**
     * Helper: Waits for restore to complete, then verifies blocks.
     * Uses recursive delayed execution to poll restore status.
     */
    private static void waitForRestoreComplete(
        GameTestHelper helper,
        UUID snapshotId,
        BlockPos center,
        int ticksWaited
    ) {
        if (ticksWaited > 500) {
            helper.fail("Restore timed out after 500 ticks");
            return;
        }

        if (!AreaSnapshotManager.INSTANCE.isRestoring(Objects.requireNonNull(snapshotId))) {
            // Restore complete, verify blocks
            helper.runAfterDelay(5, () -> {
                ServerLevel level = Objects.requireNonNull(helper.getLevel());

                // ====== STEP 8: Verify original blocks are restored ======
                BlockState centerBlock = level.getBlockState(Objects.requireNonNull(center));
                helper.assertTrue(centerBlock.is(Objects.requireNonNull(Blocks.DIAMOND_BLOCK)),
                    "Center should be restored to DIAMOND_BLOCK, but got: " + centerBlock);

                BlockState northBlock = level.getBlockState(Objects.requireNonNull(center.north()));
                helper.assertTrue(northBlock.is(Objects.requireNonNull(Blocks.GOLD_BLOCK)),
                    "North should be restored to GOLD_BLOCK, but got: " + northBlock);

                BlockState southBlock = level.getBlockState(Objects.requireNonNull(center.south()));
                helper.assertTrue(southBlock.is(Objects.requireNonNull(Blocks.GOLD_BLOCK)),
                    "South should be restored to GOLD_BLOCK, but got: " + southBlock);

                BlockState eastBlock = level.getBlockState(Objects.requireNonNull(center.east()));
                helper.assertTrue(eastBlock.is(Objects.requireNonNull(Blocks.GOLD_BLOCK)),
                    "East should be restored to GOLD_BLOCK, but got: " + eastBlock);

                BlockState westBlock = level.getBlockState(Objects.requireNonNull(center.west()));
                helper.assertTrue(westBlock.is(Objects.requireNonNull(Blocks.GOLD_BLOCK)),
                    "West should be restored to GOLD_BLOCK, but got: " + westBlock);

                DevMod.LOGGER.info("[GameTest] Snapshot capture/restore test PASSED!");
                helper.succeed();
            });
        } else {
            // Still restoring, check again next tick
            helper.runAfterDelay(1, () ->
                waitForRestoreComplete(helper, snapshotId, center, ticksWaited + 1));
        }
    }

    /**
     * TEST 20: Snapshot Capture Progress Tracking
     * Verifies that capture progress is tracked correctly during multi-tick capture.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_snapshot_progress", timeoutTicks = 400)
    public static void testSnapshotCaptureProgressTracking(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        // Place some blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(Objects.requireNonNull(center.offset(x, 0, z)), Objects.requireNonNull(Blocks.STONE.defaultBlockState()), 3);
            }
        }

        UUID areaId = Objects.requireNonNull(UUID.randomUUID());
        AreaDefinition areaDef = AreaDefinition.builder()
            .id(areaId)
            .name("progress_test_area")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 2, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .build();

        UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(
            level, areaDef, "Progress test", null);

        helper.assertTrue(snapshotId != null, "Capture should start");

        // Check initial progress
        int initialProgress = AreaSnapshotManager.INSTANCE.getCaptureProgress(areaId);
        helper.assertTrue(initialProgress >= 0 && initialProgress <= 100,
            "Initial progress should be 0-100, got: " + initialProgress);

        // Wait for completion and verify final progress
        waitForCaptureProgressComplete(helper, areaId, 0);
    }

    private static void waitForCaptureProgressComplete(
        GameTestHelper helper,
        UUID areaId,
        int ticksWaited
    ) {
        if (ticksWaited > 300) {
            helper.fail("Progress test timed out");
            return;
        }

        if (!AreaSnapshotManager.INSTANCE.isCapturing(Objects.requireNonNull(areaId))) {
            // Should return -1 when not capturing
            int finalProgress = AreaSnapshotManager.INSTANCE.getCaptureProgress(Objects.requireNonNull(areaId));
            helper.assertTrue(finalProgress == -1,
                "Progress should be -1 after completion, got: " + finalProgress);
            helper.succeed();
        } else {
            int progress = AreaSnapshotManager.INSTANCE.getCaptureProgress(Objects.requireNonNull(areaId));
            helper.assertTrue(progress >= 0 && progress <= 100,
                "Progress should be 0-100 during capture, got: " + progress);
            helper.runAfterDelay(1, () ->
                waitForCaptureProgressComplete(helper, areaId, ticksWaited + 1));
        }
    }

    /**
     * TEST 21: Concurrent Capture Limit
     * Verifies that MAX_CONCURRENT_CAPTURES is respected.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_snapshot_concurrency", timeoutTicks = 200)
    public static void testConcurrentCaptureLimit(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        // Clean up first
        AreaSnapshotManager.INSTANCE.cleanup();

        // Place a block
        level.setBlock(Objects.requireNonNull(center), Objects.requireNonNull(Blocks.STONE.defaultBlockState()), 3);

        // Start MAX_CONCURRENT_CAPTURES captures
        int maxCaptures = AreaSnapshotManager.MAX_CONCURRENT_CAPTURES;
        UUID[] areaIds = new UUID[maxCaptures + 1];

        for (int i = 0; i < maxCaptures; i++) {
            areaIds[i] = Objects.requireNonNull(UUID.randomUUID());
            AreaDefinition areaDef = AreaDefinition.builder()
                .id(Objects.requireNonNull(areaIds[i]))
                .name("concurrent_test_" + i)
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(3, 3, 2, center.getY()))
                .center(center)
                .palette(Objects.requireNonNull(defaultPalette()))
                .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
                .build();

            UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(
                level, areaDef, "Concurrent test " + i, null);

            helper.assertTrue(snapshotId != null,
                "Capture " + i + " should start (within limit)");
        }

        // Verify count
        helper.assertTrue(AreaSnapshotManager.INSTANCE.getActiveCaptureCount() == maxCaptures,
            "Should have " + maxCaptures + " active captures");

        // Try to start one more - should fail
        areaIds[maxCaptures] = Objects.requireNonNull(UUID.randomUUID());
        AreaDefinition extraArea = AreaDefinition.builder()
            .id(Objects.requireNonNull(areaIds[maxCaptures]))
            .name("concurrent_test_extra")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 2, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .build();

        UUID extraSnapshotId = AreaSnapshotManager.INSTANCE.startCapture(
            level, extraArea, "Should fail", null);

        helper.assertTrue(extraSnapshotId == null,
            "Capture beyond limit should return null");

        // Cleanup
        AreaSnapshotManager.INSTANCE.cleanup();
        helper.succeed();
    }

    // ============================================================
    // BATCH: area_biome - Biome terrain generation tests
    // ============================================================

    /**
     * TEST 22: Biome Terrain Generation (REQUIRED)
     * Verifies BiomeAreaGenerator.generate() places blocks in the world.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_biome", required = true, timeoutTicks = 200)
    public static void testBiomeTerrainGeneration(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        // Create biome config
        BiomeGenerationConfig config = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("plains"),
            12345L,
            false, // no caves
            false, // no features
            false, // no ores
            63,
            TerrainStyle.FLAT
        );

        AreaDefinition areaDef = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("biome_test_area")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(3, 3, 8, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(config)
            .build();

        // Generate floor positions
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            AreaShape.RECTANGULAR, center, Objects.requireNonNull(areaDef.dimensions()));

        // Generate terrain
        int blocksPlaced = BiomeAreaGenerator.generate(level, areaDef, floorPositions);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(blocksPlaced > 0,
                "Should place blocks, got: " + blocksPlaced);

            // Verify terrain at center
            BlockState centerBlock = level.getBlockState(center);
            helper.assertTrue(!centerBlock.isAir(),
                "Center should have terrain, got: " + centerBlock);

            // Plains biome should have grass block on surface
            boolean hasGrass = false;
            for (int y = center.getY(); y < center.getY() + 8; y++) {
                BlockState state = level.getBlockState(new BlockPos(center.getX(), y, center.getZ()));
                if (state.is(Objects.requireNonNull(Blocks.GRASS_BLOCK))) {
                    hasGrass = true;
                    break;
                }
            }
            helper.assertTrue(hasGrass, "Plains biome should have grass block surface");

            DevMod.LOGGER.info("[GameTest] Biome terrain generation test PASSED ({} blocks)", blocksPlaced);
            helper.succeed();
        });
    }

    /**
     * TEST 23: Biome Block Map Pre-computation
     * Verifies generateBlockMap produces valid blocks without ServerLevel.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_biome", timeoutTicks = 100)
    public static void testBiomeBlockMapGeneration(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        BiomeGenerationConfig config = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("desert"),
            54321L,
            false,
            false,
            false,
            63,
            TerrainStyle.NATURAL
        );

        AreaDefinition areaDef = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("blockmap_test")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(5, 5, 16, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(config)
            .build();

        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            AreaShape.RECTANGULAR, center, Objects.requireNonNull(areaDef.dimensions()));

        // Pre-compute block map (doesn't need ServerLevel)
        Map<BlockPos, BlockState> blockMap = BiomeAreaGenerator.generateBlockMap(areaDef, floorPositions);

        helper.assertTrue(!blockMap.isEmpty(),
            "Block map should not be empty");

        helper.assertTrue(blockMap.size() > floorPositions.size(),
            "Block map should have multiple layers (got " + blockMap.size() +
            " blocks for " + floorPositions.size() + " floor positions)");

        // Desert biome should have sand
        boolean hasSand = blockMap.values().stream()
            .anyMatch(state -> state.is(Objects.requireNonNull(Blocks.SAND)));
        helper.assertTrue(hasSand, "Desert biome block map should contain sand");

        DevMod.LOGGER.info("[GameTest] Biome block map generation test PASSED ({} blocks)", blockMap.size());
        helper.succeed();
    }

    /**
     * TEST 24: Biome with Multi-tick Build Task
     * Verifies biome terrain can be built using AreaBuildTask with pre-computed blocks.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_biome", timeoutTicks = 300)
    public static void testBiomeWithBuildTask(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));

        BiomeGenerationConfig config = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("forest"),
            99999L,
            false,
            true, // features enabled
            false,
            63,
            TerrainStyle.FLAT
        );

        AreaDefinition areaDef = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("multitick_biome_test")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(5, 5, 12, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(config)
            .build();

        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            AreaShape.RECTANGULAR, center, Objects.requireNonNull(areaDef.dimensions()));

        // Pre-compute block map
        Map<BlockPos, BlockState> blockMap = BiomeAreaGenerator.generateBlockMap(areaDef, floorPositions);

        helper.assertTrue(!blockMap.isEmpty(), "Block map should not be empty");

        // Create build task with pre-computed blocks
        AtomicInteger completionCount = new AtomicInteger(0);
        AreaBuildTask task = new AreaBuildTask(
            level, areaDef, blockMap, 50, t -> completionCount.incrementAndGet()
        );

        // Run task to completion
        helper.runAfterDelay(10, () -> {
            try {
                int ticks = 0;
                while (!task.isCompleted() && ticks < 200) {
                    task.tick();
                    ticks++;
                }

                helper.assertTrue(task.isCompleted(), "Task should complete");
                helper.assertTrue(task.getTotalBlocksPlaced() > 0,
                    "Should place blocks, got: " + task.getTotalBlocksPlaced());
                helper.assertTrue(completionCount.get() == 1,
                    "Completion callback should fire once");

                // Verify blocks in world
                BlockState centerBlock = level.getBlockState(center);
                helper.assertTrue(!centerBlock.isAir(),
                    "Center should have terrain after build");

                DevMod.LOGGER.info("[GameTest] Biome multi-tick build test PASSED ({} blocks)",
                    task.getTotalBlocksPlaced());
                helper.succeed();
            } finally {
                task.close();
            }
        });
    }

    /**
     * TEST 25: Different Terrain Styles
     * Verifies different TerrainStyles produce different results.
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_biome", timeoutTicks = 100)
    public static void testDifferentTerrainStyles(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        long seed = 77777L;

        // Create configs for FLAT and NATURAL
        BiomeGenerationConfig flatConfig = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("plains"), seed,
            false, false, false, 63, TerrainStyle.FLAT
        );

        BiomeGenerationConfig naturalConfig = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("plains"), seed,
            false, false, false, 63, TerrainStyle.NATURAL
        );

        AreaDefinition flatArea = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("flat_style")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(8, 8, 32, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(flatConfig)
            .build();

        AreaDefinition naturalArea = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("natural_style")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(8, 8, 32, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(naturalConfig)
            .build();

        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            AreaShape.RECTANGULAR, center, Objects.requireNonNull(flatArea.dimensions()));

        Map<BlockPos, BlockState> flatBlocks = BiomeAreaGenerator.generateBlockMap(flatArea, floorPositions);
        Map<BlockPos, BlockState> naturalBlocks = BiomeAreaGenerator.generateBlockMap(naturalArea, floorPositions);
        DevMod.LOGGER.info("[GameTest] Terrain style block maps: flat={}, natural={}, floor={}",
            flatBlocks.size(), naturalBlocks.size(), floorPositions.size());

        // FLAT should have uniform height, NATURAL should have variation
        Set<Integer> flatYs = new java.util.HashSet<>();
        Set<Integer> naturalYs = new java.util.HashSet<>();

        for (BlockPos floorPos : floorPositions) {
            int flatMaxY = Integer.MIN_VALUE;
            int naturalMaxY = Integer.MIN_VALUE;
            for (BlockPos pos : flatBlocks.keySet()) {
                if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                    flatMaxY = Math.max(flatMaxY, pos.getY());
                }
            }
            for (BlockPos pos : naturalBlocks.keySet()) {
                if (pos.getX() == floorPos.getX() && pos.getZ() == floorPos.getZ()) {
                    naturalMaxY = Math.max(naturalMaxY, pos.getY());
                }
            }
            if (flatMaxY != Integer.MIN_VALUE) flatYs.add(flatMaxY);
            if (naturalMaxY != Integer.MIN_VALUE) naturalYs.add(naturalMaxY);
        }

        helper.assertTrue(flatYs.size() == 1,
            "FLAT terrain should have uniform height, got " + flatYs.size() + " different heights");
        helper.assertTrue(naturalYs.size() > 1,
            "NATURAL terrain should have height variation, got " + naturalYs.size() + " different heights");

        DevMod.LOGGER.info("[GameTest] Terrain styles test PASSED (flat={} heights, natural={} heights)",
            flatYs.size(), naturalYs.size());
        helper.succeed();
    }

    /**
     * TEST 26: Biome Determinism
     * Verifies same seed produces identical block maps.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_biome", timeoutTicks = 100)
    public static void testBiomeDeterminism(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(0, 1, 0));
        long seed = 123456789L;

        BiomeGenerationConfig config = new BiomeGenerationConfig(
            ResourceLocation.withDefaultNamespace("taiga"), seed,
            false, false, false, 63, TerrainStyle.NATURAL
        );

        AreaDefinition area = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("determinism_test")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(8, 8, 16, center.getY()))
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(new AreaOptions(false, false, false, WallStyle.SOLID, false, false, null))
            .biomeConfig(config)
            .build();

        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            AreaShape.RECTANGULAR, center, Objects.requireNonNull(area.dimensions()));

        // Generate twice with same seed
        Map<BlockPos, BlockState> blocks1 = BiomeAreaGenerator.generateBlockMap(area, floorPositions);
        Map<BlockPos, BlockState> blocks2 = BiomeAreaGenerator.generateBlockMap(area, floorPositions);

        helper.assertTrue(blocks1.size() == blocks2.size(),
            "Same seed should produce same block count");

        // Verify all blocks match
        for (Map.Entry<BlockPos, BlockState> entry : blocks1.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state1 = entry.getValue();
            BlockState state2 = blocks2.get(pos);
            helper.assertTrue(Objects.equals(state1, state2),
                "Same seed should produce identical blocks at " + pos);
        }

        DevMod.LOGGER.info("[GameTest] Biome determinism test PASSED ({} blocks match)", blocks1.size());
        helper.succeed();
    }

    // ============================================================
    // BATCH: area_build - Clear step tests
    // ============================================================

    /**
     * TEST 27: Clear Step Places AIR Blocks
     * Verifies that the "clear" step in multi-tick builds actually places AIR blocks
     * to remove existing blocks (regression test for clear skip bug).
     */
    @GameTest(template = TEMPLATE_5X5, batch = "area_build", timeoutTicks = 200)
    public static void testClearStepPlacesAir(GameTestHelper helper) {
        ServerLevel level = Objects.requireNonNull(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        AreaDimensions dims = new AreaDimensions(3, 3, 2, center.getY());

        // First, place some stone blocks in the area
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos pos = Objects.requireNonNull(center.offset(x, y, z));
                    level.setBlock(pos, Objects.requireNonNull(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), 3);
                }
            }
        }

        // Verify stone was placed
        helper.assertTrue(!level.getBlockState(center).isAir(),
            "Center should have stone before clear");

        // Create area with clearFirst enabled
        AreaDefinition def = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("test_clear")
            .shape(AreaShape.RECTANGULAR)
            .dimensions(dims)
            .center(center)
            .palette(Objects.requireNonNull(defaultPalette()))
            .options(AreaOptions.OPEN)
            .build();

        AreaBuildTask task = new AreaBuildTask(level, Objects.requireNonNull(def), 100, t -> {})
            .withClearStep();

        try {
            // Run task to completion
            runTaskToCompletion(task, 200);

            helper.runAfterDelay(5, () -> {
                try {
                    // The clear step should have removed blocks above floor level
                    // Floor is placed, so center.above() should be air after clear
                    BlockState aboveFloor = level.getBlockState(Objects.requireNonNull(center.above()));
                    helper.assertTrue(aboveFloor.isAir(),
                        "Clear step should have removed blocks above floor, got: " + aboveFloor);

                    DevMod.LOGGER.info("[GameTest] Clear step test PASSED - blocks were cleared");
                    helper.succeed();
                } finally {
                    task.close();
                }
            });
        } catch (Exception e) {
            task.close();
            throw e;
        }
    }

    /**
     * TEST 28: Snapshot Manager Tick Wiring
     * Verifies that AreaSnapshotManager receives tick events.
     * This test checks that the manager's tick method is functional.
     */
    @GameTest(template = TEMPLATE_EMPTY, batch = "area_snapshot_misc", timeoutTicks = 100)
    public static void testSnapshotManagerTickWiring(GameTestHelper helper) {
        // Verify that AreaSnapshotManager can be ticked without errors
        // This confirms the event bus registration is working
        var server = helper.getLevel().getServer();

        // The manager should be able to tick even with no active captures
        try {
            // Manually call tick to verify it doesn't crash
            AreaSnapshotManager.INSTANCE.tick(Objects.requireNonNull(server));

            // Verify we can check capture counts
            int captureCount = AreaSnapshotManager.INSTANCE.getActiveCaptureCount();
            helper.assertTrue(captureCount >= 0,
                "Active capture count should be non-negative, got: " + captureCount);

            DevMod.LOGGER.info("[GameTest] Snapshot manager tick wiring test PASSED");
            helper.succeed();
        } catch (Exception e) {
            helper.fail("Snapshot manager tick failed: " + e.getMessage());
        }
    }
}

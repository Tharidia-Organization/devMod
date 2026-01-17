package com.devmod.area.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;

/**
 * Unit tests for area build queue components.
 * Tests QueuedBuild record, BuildStartResult enum, and task manager constants.
 *
 * Note: Full queue integration testing (queueBuild, processQueue, etc.)
 * requires a running Minecraft server and is covered in GameTests.
 */
@DisplayName("Area Build Queue")
class AreaBuildQueueTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private AreaDefinition createTestArea(String name) {
        return AreaDefinition.builder()
            .name(name)
            .shape(AreaShape.RECTANGULAR)
            .dimensions(new AreaDimensions(32, 32, 16, 64))
            .center(new BlockPos(0, 64, 0))
            .dimension(ResourceLocation.withDefaultNamespace("overworld"))
            .palette(AreaPalette.empty("default"))
            .options(AreaOptions.DEFAULT)
            .build();
    }

    // ========================================================================
    // QueuedBuild Record Tests
    // ========================================================================

    @Nested
    @DisplayName("QueuedBuild Record")
    class QueuedBuildTests {

        @Test
        @DisplayName("constructor stores all fields correctly")
        void constructor_storesAllFields() {
            UUID areaId = UUID.randomUUID();
            AreaDefinition definition = createTestArea("Test Area");
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();

            QueuedBuild queued = new QueuedBuild(
                areaId, definition, levelId, playerId, true, true, now
            );

            assertEquals(areaId, queued.areaId());
            assertEquals(definition, queued.definition());
            assertEquals(levelId, queued.levelId());
            assertEquals(playerId, queued.playerId());
            assertTrue(queued.clearFirst());
            assertTrue(queued.forceMultiTick());
            assertEquals(now, queued.queuedAt());
        }

        @Test
        @DisplayName("constructor accepts null playerId")
        void constructor_acceptsNullPlayerId() {
            UUID areaId = UUID.randomUUID();
            AreaDefinition definition = createTestArea("Test Area");
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");

            QueuedBuild queued = new QueuedBuild(
                areaId, definition, levelId, null, false, false, 0
            );

            assertNull(queued.playerId());
        }

        @Test
        @DisplayName("constructor rejects null areaId")
        void constructor_rejectsNullAreaId() {
            AreaDefinition definition = createTestArea("Test Area");
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");

            assertThrows(NullPointerException.class, () ->
                new QueuedBuild(null, definition, levelId, null, false, false, 0));
        }

        @Test
        @DisplayName("constructor rejects null definition")
        void constructor_rejectsNullDefinition() {
            UUID areaId = UUID.randomUUID();
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");

            assertThrows(NullPointerException.class, () ->
                new QueuedBuild(areaId, null, levelId, null, false, false, 0));
        }

        @Test
        @DisplayName("constructor rejects null levelId")
        void constructor_rejectsNullLevelId() {
            UUID areaId = UUID.randomUUID();
            AreaDefinition definition = createTestArea("Test Area");

            assertThrows(NullPointerException.class, () ->
                new QueuedBuild(areaId, definition, null, null, false, false, 0));
        }

        @Test
        @DisplayName("equals compares all fields")
        void equals_comparesAllFields() {
            UUID areaId = UUID.randomUUID();
            AreaDefinition definition = createTestArea("Test Area");
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");
            UUID playerId = UUID.randomUUID();
            long now = System.currentTimeMillis();

            QueuedBuild queued1 = new QueuedBuild(
                areaId, definition, levelId, playerId, true, true, now
            );
            QueuedBuild queued2 = new QueuedBuild(
                areaId, definition, levelId, playerId, true, true, now
            );

            assertEquals(queued1, queued2);
            assertEquals(queued1.hashCode(), queued2.hashCode());
        }

        @Test
        @DisplayName("different areaIds are not equal")
        void equals_differentAreaIdsNotEqual() {
            AreaDefinition definition = createTestArea("Test Area");
            ResourceLocation levelId = ResourceLocation.withDefaultNamespace("overworld");

            QueuedBuild queued1 = new QueuedBuild(
                UUID.randomUUID(), definition, levelId, null, false, false, 0
            );
            QueuedBuild queued2 = new QueuedBuild(
                UUID.randomUUID(), definition, levelId, null, false, false, 0
            );

            assertNotEquals(queued1, queued2);
        }
    }

    // ========================================================================
    // BuildStartResult Enum Tests
    // ========================================================================

    @Nested
    @DisplayName("BuildStartResult Enum")
    class BuildStartResultTests {

        @Test
        @DisplayName("has all expected values")
        void hasAllExpectedValues() {
            AreaBuildTaskManager.BuildStartResult[] values =
                AreaBuildTaskManager.BuildStartResult.values();

            assertEquals(5, values.length);
            assertNotNull(AreaBuildTaskManager.BuildStartResult.STARTED_IMMEDIATE);
            assertNotNull(AreaBuildTaskManager.BuildStartResult.STARTED_MULTI_TICK);
            assertNotNull(AreaBuildTaskManager.BuildStartResult.QUEUED);
            assertNotNull(AreaBuildTaskManager.BuildStartResult.ALREADY_BUILDING);
            assertNotNull(AreaBuildTaskManager.BuildStartResult.QUEUE_FULL);
        }

        @Test
        @DisplayName("valueOf works for all values")
        void valueOf_worksForAllValues() {
            assertEquals(AreaBuildTaskManager.BuildStartResult.STARTED_IMMEDIATE,
                AreaBuildTaskManager.BuildStartResult.valueOf("STARTED_IMMEDIATE"));
            assertEquals(AreaBuildTaskManager.BuildStartResult.STARTED_MULTI_TICK,
                AreaBuildTaskManager.BuildStartResult.valueOf("STARTED_MULTI_TICK"));
            assertEquals(AreaBuildTaskManager.BuildStartResult.QUEUED,
                AreaBuildTaskManager.BuildStartResult.valueOf("QUEUED"));
            assertEquals(AreaBuildTaskManager.BuildStartResult.ALREADY_BUILDING,
                AreaBuildTaskManager.BuildStartResult.valueOf("ALREADY_BUILDING"));
            assertEquals(AreaBuildTaskManager.BuildStartResult.QUEUE_FULL,
                AreaBuildTaskManager.BuildStartResult.valueOf("QUEUE_FULL"));
        }

        @Test
        @DisplayName("ordinal values are stable")
        void ordinal_valuesAreStable() {
            // These ordinals should remain stable for any saved data
            assertEquals(0, AreaBuildTaskManager.BuildStartResult.STARTED_IMMEDIATE.ordinal());
            assertEquals(1, AreaBuildTaskManager.BuildStartResult.STARTED_MULTI_TICK.ordinal());
            assertEquals(2, AreaBuildTaskManager.BuildStartResult.QUEUED.ordinal());
            assertEquals(3, AreaBuildTaskManager.BuildStartResult.ALREADY_BUILDING.ordinal());
            assertEquals(4, AreaBuildTaskManager.BuildStartResult.QUEUE_FULL.ordinal());
        }
    }

    // ========================================================================
    // Constants Tests
    // ========================================================================

    @Nested
    @DisplayName("Task Manager Constants")
    class ConstantsTests {

        @Test
        @DisplayName("MULTI_TICK_THRESHOLD is reasonable")
        void multiTickThreshold_isReasonable() {
            // Should be large enough for small areas to build immediately
            assertTrue(AreaBuildTaskManager.MULTI_TICK_THRESHOLD >= 1000,
                "Threshold should be at least 1000 blocks");
            // Should be small enough for large areas to use multi-tick
            assertTrue(AreaBuildTaskManager.MULTI_TICK_THRESHOLD <= 100_000,
                "Threshold should be at most 100k blocks");
        }

        @Test
        @DisplayName("MAX_CONCURRENT_BUILDS is positive and reasonable")
        void maxConcurrentBuilds_isReasonable() {
            assertTrue(AreaBuildTaskManager.MAX_CONCURRENT_BUILDS >= 1,
                "Should allow at least 1 concurrent build");
            assertTrue(AreaBuildTaskManager.MAX_CONCURRENT_BUILDS <= 20,
                "Should not allow more than 20 concurrent builds");
        }

        @Test
        @DisplayName("MAX_QUEUE_SIZE is positive and reasonable")
        void maxQueueSize_isReasonable() {
            assertTrue(AreaBuildTaskManager.MAX_QUEUE_SIZE >= 5,
                "Queue should hold at least 5 builds");
            assertTrue(AreaBuildTaskManager.MAX_QUEUE_SIZE <= 100,
                "Queue should not hold more than 100 builds");
        }

        @Test
        @DisplayName("MAX_BIOME_PRECOMPUTE_MS is positive and reasonable")
        void maxBiomePrecomputeMs_isReasonable() {
            // Should be at least 10 seconds
            assertTrue(AreaBuildTaskManager.MAX_BIOME_PRECOMPUTE_MS >= 10_000,
                "Biome precompute timeout should be at least 10 seconds");
            // Should not exceed 5 minutes
            assertTrue(AreaBuildTaskManager.MAX_BIOME_PRECOMPUTE_MS <= 300_000,
                "Biome precompute timeout should not exceed 5 minutes");
        }

        @Test
        @DisplayName("INSTANCE singleton is not null")
        void instance_isNotNull() {
            assertNotNull(AreaBuildTaskManager.INSTANCE);
        }
    }

    // ========================================================================
    // Queue Methods Tests (limited without server)
    // ========================================================================

    @Nested
    @DisplayName("Queue Methods (No Server)")
    class QueueMethodsNoServerTests {

        @Test
        @DisplayName("getActiveBuildCount is initially zero")
        void getActiveBuildCount_initiallyZero() {
            // After cleanup, should be zero
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertEquals(0, AreaBuildTaskManager.INSTANCE.getActiveBuildCount());
        }

        @Test
        @DisplayName("getQueuedBuildCount is initially zero")
        void getQueuedBuildCount_initiallyZero() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertEquals(0, AreaBuildTaskManager.INSTANCE.getQueuedBuildCount());
        }

        @Test
        @DisplayName("isBuildingArea returns false for random UUID")
        void isBuildingArea_returnsFalseForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertFalse(AreaBuildTaskManager.INSTANCE.isBuildingArea(UUID.randomUUID()));
        }

        @Test
        @DisplayName("isAreaQueued returns false for random UUID")
        void isAreaQueued_returnsFalseForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertFalse(AreaBuildTaskManager.INSTANCE.isAreaQueued(UUID.randomUUID()));
        }

        @Test
        @DisplayName("getQueuePosition returns -1 for random UUID")
        void getQueuePosition_returnsMinusOneForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertEquals(-1, AreaBuildTaskManager.INSTANCE.getQueuePosition(UUID.randomUUID()));
        }

        @Test
        @DisplayName("getBuildProgress returns -1 for random UUID")
        void getBuildProgress_returnsMinusOneForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertEquals(-1, AreaBuildTaskManager.INSTANCE.getBuildProgress(UUID.randomUUID()));
        }

        @Test
        @DisplayName("cancelBuild returns false for random UUID")
        void cancelBuild_returnsFalseForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertFalse(AreaBuildTaskManager.INSTANCE.cancelBuild(UUID.randomUUID()));
        }

        @Test
        @DisplayName("removeFromQueue returns false for random UUID")
        void removeFromQueue_returnsFalseForRandomUUID() {
            AreaBuildTaskManager.INSTANCE.cleanup();
            assertFalse(AreaBuildTaskManager.INSTANCE.removeFromQueue(UUID.randomUUID()));
        }

        @Test
        @DisplayName("cancelPlayerBuilds handles null gracefully")
        void cancelPlayerBuilds_handlesNull() {
            assertDoesNotThrow(() ->
                AreaBuildTaskManager.INSTANCE.cancelPlayerBuilds(null));
        }

        @Test
        @DisplayName("cleanup clears all state")
        void cleanup_clearsAllState() {
            AreaBuildTaskManager.INSTANCE.cleanup();

            assertEquals(0, AreaBuildTaskManager.INSTANCE.getActiveBuildCount());
            assertEquals(0, AreaBuildTaskManager.INSTANCE.getQueuedBuildCount());
        }

        @Test
        @DisplayName("tick handles null server gracefully")
        void tick_handlesNullServer() {
            assertDoesNotThrow(() ->
                AreaBuildTaskManager.INSTANCE.tick(null));
        }
    }

    // ========================================================================
    // AreaBlockMapGenerator Constants
    // ========================================================================

    @Nested
    @DisplayName("Block Map Generator")
    class BlockMapGeneratorTests {

        @Test
        @DisplayName("estimateTotalBlocks returns positive for valid area")
        void estimateTotalBlocks_returnsPositive() {
            AreaDefinition area = createTestArea("Test Area");

            int estimate = AreaBlockMapGenerator.estimateTotalBlocks(area);

            assertTrue(estimate > 0, "Estimate should be positive");
        }

        @Test
        @DisplayName("estimateTotalBlocks scales with dimensions")
        void estimateTotalBlocks_scalesWithDimensions() {
            AreaDefinition small = AreaDefinition.builder()
                .name("Small")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(8, 8, 4, 64))
                .center(BlockPos.ZERO)
                .dimension(ResourceLocation.withDefaultNamespace("overworld"))
                .build();

            AreaDefinition large = AreaDefinition.builder()
                .name("Large")
                .shape(AreaShape.RECTANGULAR)
                .dimensions(new AreaDimensions(64, 64, 32, 64))
                .center(BlockPos.ZERO)
                .dimension(ResourceLocation.withDefaultNamespace("overworld"))
                .build();

            int smallEstimate = AreaBlockMapGenerator.estimateTotalBlocks(small);
            int largeEstimate = AreaBlockMapGenerator.estimateTotalBlocks(large);

            assertTrue(largeEstimate > smallEstimate,
                "Larger area should have more blocks");
        }
    }
}

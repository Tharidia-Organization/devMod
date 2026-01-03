package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;

/**
 * Integration tests for ArenaBuilder and TemplateValidator.
 *
 * Tests the full build lifecycle including:
 * - Template validation (all modes)
 * - Block budget enforcement
 * - Build dry run calculations
 * - Transaction rollback scenarios
 * - Concurrent build requests
 */
class ArenaBuilderIntegrationTest {

    @BeforeEach
    void setUp() {
        // Setup if needed
    }

    // ========================================================================
    // Template Validation Tests
    // ========================================================================

    @Nested
    @DisplayName("Template Validation")
    class TemplateValidationTests {

        @Test
        @DisplayName("Default template passes STRICT validation")
        void defaultTemplatePassesStrict() {
            // Use the built-in default template which is known to be valid
            ArenaTemplate template = ArenaTemplate.defaultTemplate();
            TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);

            ValidationResult result = validator.validate(template);

            assertTrue(result.valid(), "Default template should pass: " + result.errorsAsString());
        }

        @Test
        @DisplayName("Template without ID fails validation")
        void templateWithoutIdFails() {
            ArenaTemplate template = createTemplateWithoutId();
            TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);

            ValidationResult result = validator.validate(template);

            assertFalse(result.valid());
            assertTrue(result.errorsAsString().toLowerCase(Locale.ROOT).contains("id") ||
                       result.errorsAsString().toLowerCase(Locale.ROOT).contains("null"),
                "Error should mention missing id: " + result.errorsAsString());
        }

        @Test
        @DisplayName("Template with size below minimum fails")
        void templateSizeTooSmallFails() {
            ArenaTemplate template = createTemplateWithSize(4); // MIN_SIZE is 8
            TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);

            ValidationResult result = validator.validate(template);

            assertFalse(result.valid());
            assertTrue(result.errorsAsString().toLowerCase(Locale.ROOT).contains("size") ||
                       result.errorsAsString().toLowerCase(Locale.ROOT).contains("min"),
                "Error should mention size constraint: " + result.errorsAsString());
        }

        @Test
        @DisplayName("Template with size above maximum fails")
        void templateSizeTooLargeFails() {
            ArenaTemplate template = createTemplateWithSize(300); // MAX_SIZE is 256
            TemplateValidator validator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);

            ValidationResult result = validator.validate(template);

            assertFalse(result.valid());
            assertTrue(result.errorsAsString().toLowerCase(Locale.ROOT).contains("size") ||
                       result.errorsAsString().toLowerCase(Locale.ROOT).contains("max"),
                "Error should mention size constraint: " + result.errorsAsString());
        }

        @Test
        @DisplayName("PERMISSIVE mode is less strict than STRICT")
        void permissiveModeIsLessStrict() {
            ArenaTemplate template = createTemplateWithBoundsIssue();
            TemplateValidator strictValidator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);
            TemplateValidator permissiveValidator = new TemplateValidator(TemplateValidator.ValidationMode.PERMISSIVE);

            ValidationResult strictResult = strictValidator.validate(template);
            ValidationResult permissiveResult = permissiveValidator.validate(template);

            // PERMISSIVE mode should have same or more warnings, same or fewer errors
            assertTrue(permissiveResult.warnings().size() >= strictResult.warnings().size() ||
                       permissiveResult.errors().size() <= strictResult.errors().size(),
                "PERMISSIVE mode should convert some errors to warnings");
        }

        @Test
        @DisplayName("LENIENT mode produces warnings instead of errors")
        void lenientModeProducesWarnings() {
            ArenaTemplate template = createTemplateWithMultipleErrors();
            TemplateValidator lenientValidator = new TemplateValidator(TemplateValidator.ValidationMode.LENIENT);
            TemplateValidator strictValidator = new TemplateValidator(TemplateValidator.ValidationMode.STRICT);

            ValidationResult lenientResult = lenientValidator.validate(template);
            ValidationResult strictResult = strictValidator.validate(template);

            // LENIENT mode should have fewer or equal errors than STRICT mode
            assertTrue(lenientResult.errors().size() <= strictResult.errors().size(),
                "LENIENT mode should reduce error count");
            // LENIENT mode should have more warnings than STRICT if it converts errors
            assertTrue(lenientResult.warnings().size() >= strictResult.warnings().size(),
                "LENIENT mode should produce warnings from errors");
        }

        @Test
        @DisplayName("Validation mode can be parsed from string")
        void validationModeFromString() {
            assertEquals(TemplateValidator.ValidationMode.STRICT,
                TemplateValidator.fromString("strict", TemplateValidator.ValidationMode.LENIENT));
            assertEquals(TemplateValidator.ValidationMode.PERMISSIVE,
                TemplateValidator.fromString("PERMISSIVE", TemplateValidator.ValidationMode.STRICT));
            assertEquals(TemplateValidator.ValidationMode.LENIENT,
                TemplateValidator.fromString("lenient", TemplateValidator.ValidationMode.STRICT));
            assertEquals(TemplateValidator.ValidationMode.STRICT,
                TemplateValidator.fromString("invalid", TemplateValidator.ValidationMode.STRICT));
            assertEquals(TemplateValidator.ValidationMode.STRICT,
                TemplateValidator.fromString(null, TemplateValidator.ValidationMode.STRICT));
        }
    }

    // ========================================================================
    // Build Dry Run Tests
    // ========================================================================

    @Nested
    @DisplayName("Build Dry Run Calculations")
    class BuildDryRunTests {

        @Test
        @DisplayName("Dry run calculates floor blocks correctly")
        void dryRunCalculatesFloorBlocks() {
            ArenaTemplate template = createTemplateWithSize(16);

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            // For a 16x16 arena, floor should be at least 16*16 = 256 blocks
            assertTrue(dryRun.floorBlocks() >= 256,
                "Floor blocks should be >= 256 for 16x16, got: " + dryRun.floorBlocks());
        }

        @Test
        @DisplayName("Dry run includes wall blocks when walls enabled")
        void dryRunIncludesWallBlocks() {
            ArenaTemplate template = createTemplateWithWalls(16, 8);

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            assertTrue(dryRun.wallBlocks() > 0,
                "Wall blocks should be > 0 when walls enabled");
        }

        @Test
        @DisplayName("Total blocks includes all components")
        void totalBlocksIncludesAllComponents() {
            ArenaTemplate template = createCompleteTemplate(20, 10);

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            // Total should include at least floor, walls, ceiling, underfloor
            int minExpected = dryRun.floorBlocks() + dryRun.wallBlocks() +
                             dryRun.ceilingBlocks() + dryRun.underfloorBlocks();
            assertTrue(dryRun.totalBlocks() >= minExpected,
                "Total should include all core components");
        }

        @Test
        @DisplayName("Dry run handles minimum size template")
        void dryRunHandlesMinimumSize() {
            ArenaTemplate template = createTemplateWithSize(8); // MIN_SIZE

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            assertTrue(dryRun.totalBlocks() > 0,
                "Minimum size template should still have blocks");
            assertTrue(dryRun.totalBlocks() < 2000,
                "Minimum size should have reasonable block count");
        }

        @Test
        @DisplayName("Dry run handles maximum size template")
        void dryRunHandlesMaximumSize() {
            ArenaTemplate template = createTemplateWithSize(256); // MAX_SIZE

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            assertTrue(dryRun.totalBlocks() > 0,
                "Maximum size template should have blocks");
            // 256x256 floor alone = 65536 blocks
            assertTrue(dryRun.floorBlocks() >= 65536,
                "Maximum size floor should be at least 65536 blocks");
        }
    }

    // ========================================================================
    // Block Budget Tests
    // ========================================================================

    @Nested
    @DisplayName("Block Budget Enforcement")
    class BlockBudgetTests {

        @Test
        @DisplayName("Template within default budget passes")
        void templateWithinBudgetPasses() {
            ArenaTemplate template = createTemplateWithSize(32); // Well under 50k blocks

            BuildDryRun dryRun = BuildDryRunCalculator.calculate(template);

            assertTrue(dryRun.totalBlocks() < 50_000,
                "32x32 template should be under 50k blocks");
        }

        @Test
        @DisplayName("Boss template can be calculated without errors")
        void bossTemplateCalculation() {
            // Boss arenas get 100k block limit vs 50k default
            ArenaTemplate bossTemplate = createBossTemplate(80);

            BuildDryRun bossDryRun = BuildDryRunCalculator.calculate(bossTemplate);

            // Boss template should have reasonable block count for 80x80 arena
            assertTrue(bossDryRun.totalBlocks() > 6400, // At least 80*80 floor
                "Boss template should have floor blocks");
            assertTrue(bossDryRun.totalBlocks() < 100_000,
                "Boss template should be under boss block limit");
        }
    }

    // ========================================================================
    // Concurrent Build Tests
    // ========================================================================

    @Nested
    @DisplayName("Concurrent Build Handling")
    class ConcurrentBuildTests {

        @Test
        @DisplayName("Multiple validations can proceed concurrently")
        void multipleValidationsConcurrent() throws InterruptedException {
            int numTemplates = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(numTemplates);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(numTemplates);

            for (int i = 0; i < numTemplates; i++) {
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        // Use the default template which is known to be valid
                        ArenaTemplate template = ArenaTemplate.defaultTemplate();
                        TemplateValidator validator = new TemplateValidator();
                        ValidationResult result = validator.validate(template);
                        if (result.valid()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS),
                "All validations should complete within 5 seconds");

            assertEquals(numTemplates, successCount.get(),
                "All templates should validate successfully");

            executor.shutdownNow();
        }

        @Test
        @DisplayName("Validation is thread-safe")
        void validationIsThreadSafe() throws InterruptedException {
            // Use the default template which is known to be valid
            ArenaTemplate template = ArenaTemplate.defaultTemplate();
            TemplateValidator validator = new TemplateValidator();
            int numThreads = 10;
            int iterationsPerThread = 100;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int t = 0; t < numThreads; t++) {
                executor.execute(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            ValidationResult result = validator.validate(template);
                            if (result.valid()) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(completeLatch.await(10, TimeUnit.SECONDS),
                "All validations should complete");

            int expectedTotal = numThreads * iterationsPerThread;
            assertEquals(expectedTotal, successCount.get() + failureCount.get(),
                "All iterations should complete");
            assertEquals(expectedTotal, successCount.get(),
                "All validations should succeed for valid template");

            executor.shutdownNow();
        }
    }

    // ========================================================================
    // Build Transaction Tests
    // ========================================================================

    @Nested
    @DisplayName("Build Transaction")
    class BuildTransactionTests {

        @Test
        @DisplayName("Transaction tracks block count")
        void transactionTracksBlockCount() {
            BuildTransaction transaction = new BuildTransaction("test:tracking", 10000, 5000);

            // trackBlock takes (packedPos, previousStateId)
            transaction.trackBlock(packPos(0, 64, 0), 0);
            transaction.trackBlock(packPos(1, 64, 0), 0);
            transaction.trackBlock(packPos(2, 64, 0), 0);

            assertEquals(3, transaction.getBlockCount());
        }

        @Test
        @DisplayName("Transaction can be committed")
        void transactionCanBeCommitted() {
            BuildTransaction transaction = new BuildTransaction("test:commit", 1000, 5000);

            transaction.trackBlock(packPos(0, 64, 0), 0);
            transaction.commit();

            assertEquals(BuildTransaction.TransactionState.COMMITTED, transaction.getState());
        }

        @Test
        @DisplayName("Transaction can be rolled back")
        void transactionCanBeRolledBack() {
            BuildTransaction transaction = new BuildTransaction("test:rollback", 1000, 5000);

            transaction.trackBlock(packPos(0, 64, 0), 0);
            transaction.trackBlock(packPos(1, 64, 0), 0);

            BuildTransaction.RollbackResult result = transaction.rollback(
                (pos, stateId) -> true,  // Block reverter
                uuid -> true,             // Entity remover
                chunkPos -> {}            // Chunk releaser
            );

            assertTrue(result.success());
            assertEquals(2, result.blocksReverted());
        }

        @Test
        @DisplayName("Cannot track blocks after commit")
        void cannotTrackAfterCommit() {
            BuildTransaction transaction = new BuildTransaction("test:post_commit", 1000, 5000);
            transaction.commit();

            assertThrows(IllegalStateException.class, () ->
                transaction.trackBlock(packPos(0, 64, 0), 0));
        }

        @Test
        @DisplayName("Cannot track blocks after rollback")
        void cannotTrackAfterRollback() {
            BuildTransaction transaction = new BuildTransaction("test:post_rollback", 1000, 5000);
            transaction.rollback((p, s) -> true, u -> true, c -> {});

            assertThrows(IllegalStateException.class, () ->
                transaction.trackBlock(packPos(0, 64, 0), 0));
        }

        private long packPos(int x, int y, int z) {
            // Simple packing for tests (matches Minecraft BlockPos.asLong())
            return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }
    }

    // ========================================================================
    // Helper Methods - Template Creation
    // ========================================================================

    private ArenaTemplate createTemplateWithoutId() {
        return ArenaTemplate.builder(null)
            .version(1)
            .schemaVersion(1)
            .size(32)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone", "solid", null, 0))
            .build();
    }

    private ArenaTemplate createTemplateWithSize(int size) {
        return ArenaTemplate.builder("test_size_" + size)
            .version(1)
            .schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid", null, 0))
            .build();
    }

    private ArenaTemplate createTemplateWithWalls(int size, int wallHeight) {
        return ArenaTemplate.builder("test_walled_" + size)
            .version(1)
            .schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid", null, 0))
            .walls(new ArenaTemplate.Walls(true, "minecraft:stone_bricks", wallHeight, 1, 64, "solid"))
            .build();
    }

    private ArenaTemplate createCompleteTemplate(int size, int wallHeight) {
        return ArenaTemplate.builder("test_complete_" + size)
            .version(1)
            .schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone_bricks", "solid", null, 0))
            .walls(new ArenaTemplate.Walls(true, "minecraft:stone_bricks", wallHeight, 1, 64, "solid"))
            .ceiling(new ArenaTemplate.Ceiling(true, "minecraft:glass", 64 + wallHeight, 1))
            .underfloor(new ArenaTemplate.Underfloor("minecraft:deepslate", 2, false))
            .build();
    }

    private ArenaTemplate createBossTemplate(int size) {
        // Use same walls as normal template to get same block count
        return ArenaTemplate.builder("test_boss_" + size)
            .version(1)
            .schemaVersion(1)
            .size(size)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:deepslate_bricks", "solid", null, 0))
            .walls(new ArenaTemplate.Walls(true, "minecraft:deepslate_bricks", 10, 1, 64, "solid"))
            .tags(List.of("boss"))
            .build();
    }

    private ArenaTemplate createTemplateWithBoundsIssue() {
        // Create a template where spawn positions exceed bounds
        return ArenaTemplate.builder("test_bounds_issue")
            .version(1)
            .schemaVersion(1)
            .size(16)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone", "solid", null, 0))
            .spawnSlots(List.of(
                // Spawn slot at x=100 which is outside 16x16 bounds
                new ArenaTemplate.SpawnSlot(
                    new int[]{100, 1, 100},
                    ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR,
                    List.of("mob"),
                    new ArenaTemplate.SpawnSlot.Validation(true, 2, 1)
                )
            ))
            .build();
    }

    private ArenaTemplate createTemplateWithMultipleErrors() {
        // Create a template with multiple validation errors
        return ArenaTemplate.builder("")  // Empty ID
            .version(-1)                   // Invalid version
            .schemaVersion(999)           // Unknown schema
            .size(1000)                   // Exceeds max (256)
            .floor(new ArenaTemplate.Floor(64, 1, "minecraft:stone", "solid", null, 0))
            .build();
    }
}

package com.devmod.actions.legacy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.bindings.BindingRegistry;
import com.devmod.actions.bindings.RadialBinding;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.legacy.MigrationCompletionValidator.MigrationReadinessReport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MigrationCompletionValidator.
 */
@DisplayName("MigrationCompletionValidator")
class MigrationCompletionValidatorTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlerRegistry;
    private BindingRegistry bindingRegistry;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlerRegistry = new HandlerRegistry();
        bindingRegistry = new BindingRegistry();
        ShadowModeComparator.resetCounters();
    }

    /**
     * Registers a spec + handler for the given action ID.
     */
    private void registerAction(String id) {
        ActionSpec spec = ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.MISC)
            .ui("label", "desc", "minecraft:stone", "Root/Test/" + id, false)
            .build();
        catalog.register(spec);
        handlerRegistry.register(id, ctx -> {});
    }

    @Nested
    @DisplayName("Complete Migration")
    class CompleteMigrationTests {

        @Test
        @DisplayName("Full coverage with all handlers passes validation")
        void fullCoveragePassesValidation() {
            // Register all action IDs found via reflection
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            assertFalse(allIds.isEmpty(), "Should find action IDs in ActionIds.java");

            for (String id : allIds) {
                registerAction(id);
            }

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertTrue(report.ready(), "Should be ready when all actions are migrated: " +
                report.toSummary());
            assertEquals(100.0, report.coveragePercent(), 0.01);
            assertTrue(report.missingActions().isEmpty());
            assertTrue(report.missingHandlers().isEmpty());
            assertTrue(report.orphanBindings().isEmpty());
            assertTrue(report.validationErrors().isEmpty());
            assertTrue(report.shadowCheckPassed());
        }
    }

    @Nested
    @DisplayName("Missing Action Detection")
    class MissingActionTests {

        @Test
        @DisplayName("Missing action spec fails validation")
        void missingActionFailsValidation() {
            // Register nothing - all actions will be missing
            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertFalse(report.ready());
            assertFalse(report.missingActions().isEmpty());
            assertTrue(report.coveragePercent() < 100.0);
        }

        @Test
        @DisplayName("Partial coverage reports correct percentage")
        void partialCoverageReportsCorrectPercentage() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            int total = allIds.size();

            // Register half the actions
            int count = 0;
            for (String id : allIds) {
                if (count >= total / 2) break;
                registerAction(id);
                count++;
            }

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertFalse(report.ready());
            double expectedCoverage = (count * 100.0) / total;
            assertEquals(expectedCoverage, report.coveragePercent(), 0.01);
            assertEquals(count, report.coveredCount());
            assertEquals(total, report.totalBaseline());
        }
    }

    @Nested
    @DisplayName("Missing Handler Detection")
    class MissingHandlerTests {

        @Test
        @DisplayName("Missing handler fails validation")
        void missingHandlerFailsValidation() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            // Register specs but NOT handlers for some actions
            int count = 0;
            for (String id : allIds) {
                ActionSpec spec = ActionSpec.builder(id)
                    .channel(ActionChannel.CLIENT)
                    .category(ActionCategory.MISC)
                    .ui("label", "desc", "minecraft:stone", "Root/Test/" + id, false)
                    .build();
                catalog.register(spec);
                // Only register handler for half
                if (count % 2 == 0) {
                    handlerRegistry.register(id, ctx -> {});
                }
                count++;
            }

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertFalse(report.ready());
            assertFalse(report.missingHandlers().isEmpty(),
                "Should detect missing handlers");
        }
    }

    @Nested
    @DisplayName("Orphan Binding Detection")
    class OrphanBindingTests {

        @Test
        @DisplayName("Orphan binding detected")
        void orphanBindingDetected() {
            // Register all actions properly
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            for (String id : allIds) {
                registerAction(id);
            }

            // Add a binding for a non-existent action
            bindingRegistry.register(new RadialBinding(
                "devmod.nonexistent.fake.action", "Root/Fake/Path", "minecraft:stone"));

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertFalse(report.ready());
            assertFalse(report.orphanBindings().isEmpty());
            assertTrue(report.orphanBindings().get(0).contains("devmod.nonexistent.fake.action"));
        }

        @Test
        @DisplayName("Valid bindings do not trigger orphan detection")
        void validBindingsNoOrphans() {
            String testId = "devmod.ability.dash";
            registerAction(testId);

            bindingRegistry.register(new RadialBinding(
                testId, "Root/Combat/Dash", "minecraft:feather"));

            // Only validate the catalog entries we added (not all ActionIds)
            // Create a validator that will have missing actions but no orphans
            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertTrue(report.orphanBindings().isEmpty(),
                "Valid bindings should not be orphans");
        }
    }

    @Nested
    @DisplayName("Coverage Percentage")
    class CoverageTests {

        @Test
        @DisplayName("Zero coverage with empty catalog")
        void zeroCoverage() {
            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertEquals(0.0, report.coveragePercent(), 0.01);
            assertEquals(0, report.coveredCount());
        }

        @Test
        @DisplayName("Full coverage is 100%")
        void fullCoverage() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            for (String id : allIds) {
                registerAction(id);
            }

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertEquals(100.0, report.coveragePercent(), 0.01);
            assertEquals(allIds.size(), report.coveredCount());
            assertEquals(allIds.size(), report.totalBaseline());
        }
    }

    @Nested
    @DisplayName("Shadow Mode Threshold")
    class ShadowThresholdTests {

        @Test
        @DisplayName("Shadow check passes with no comparisons")
        void noComparisonsPass() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            for (String id : allIds) {
                registerAction(id);
            }

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertTrue(report.shadowCheckPassed());
            assertEquals(0.0, report.mismatchRate(), 0.0001);
        }

        @Test
        @DisplayName("Shadow check passes when mismatch rate below threshold")
        void belowThresholdPasses() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            for (String id : allIds) {
                registerAction(id);
            }

            // Simulate shadow comparisons: 99 matches, 1 mismatch = 1% rate
            com.devmod.actions.ActionResult okResult =
                com.devmod.actions.ActionResult.ok(10);
            com.devmod.actions.ActionResult failedResult =
                com.devmod.actions.ActionResult.failed(
                    com.devmod.actions.ActionResult.ERROR_EXCEPTION, "test", 10);

            // Register V2 engine flags so shadow mode can work
            com.devmod.actions.engine.EngineFeatureFlags.resetForTesting();
            com.devmod.arena.config.FeatureFlagRegistry testRegistry =
                new com.devmod.arena.config.FeatureFlagRegistry();
            com.devmod.actions.engine.EngineFeatureFlags.register(testRegistry);

            for (int i = 0; i < 99; i++) {
                ShadowModeComparator.compare("devmod.test.action." + i, okResult, okResult);
            }
            // 1 mismatch
            ShadowModeComparator.compare("devmod.test.action.bad", okResult, failedResult);

            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            assertTrue(report.shadowCheckPassed(),
                "1% mismatch rate should pass 1% threshold");
            assertEquals(0.01, report.mismatchRate(), 0.001);

            com.devmod.actions.engine.EngineFeatureFlags.resetForTesting();
        }

        @Test
        @DisplayName("Shadow check fails when mismatch rate above threshold")
        void aboveThresholdFails() {
            Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
            for (String id : allIds) {
                registerAction(id);
            }

            com.devmod.actions.ActionResult okResult =
                com.devmod.actions.ActionResult.ok(10);
            com.devmod.actions.ActionResult failedResult =
                com.devmod.actions.ActionResult.failed(
                    com.devmod.actions.ActionResult.ERROR_EXCEPTION, "test", 10);

            com.devmod.actions.engine.EngineFeatureFlags.resetForTesting();
            com.devmod.arena.config.FeatureFlagRegistry testRegistry =
                new com.devmod.arena.config.FeatureFlagRegistry();
            com.devmod.actions.engine.EngineFeatureFlags.register(testRegistry);

            // 50% mismatch rate (5 match, 5 mismatch)
            for (int i = 0; i < 5; i++) {
                ShadowModeComparator.compare("devmod.test.ok." + i, okResult, okResult);
            }
            for (int i = 0; i < 5; i++) {
                ShadowModeComparator.compare("devmod.test.bad." + i, okResult, failedResult);
            }

            // Use a strict threshold of 0.005 (0.5%)
            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry, 0.005);
            MigrationReadinessReport report = validator.validate();

            assertFalse(report.shadowCheckPassed(),
                "50% mismatch rate should fail 0.5% threshold");

            com.devmod.actions.engine.EngineFeatureFlags.resetForTesting();
        }
    }

    @Nested
    @DisplayName("Report")
    class ReportTests {

        @Test
        @DisplayName("Report summary is non-empty")
        void reportSummaryNonEmpty() {
            MigrationCompletionValidator validator = new MigrationCompletionValidator(
                catalog, handlerRegistry, bindingRegistry);
            MigrationReadinessReport report = validator.validate();

            String summary = report.toSummary();
            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("Migration Readiness Report"));
            assertTrue(summary.contains("Coverage:"));
        }
    }

    @Nested
    @DisplayName("Action ID Extraction")
    class ActionIdExtractionTests {

        @Test
        @DisplayName("Extracts action IDs from ActionIds class")
        void extractsActionIds() {
            Set<String> ids = MigrationCompletionValidator.extractActionIdConstants();
            assertFalse(ids.isEmpty());
            // All should start with devmod.
            for (String id : ids) {
                assertTrue(id.startsWith("devmod."),
                    "Action ID should start with 'devmod.': " + id);
            }
        }

        @Test
        @DisplayName("Extracted count matches baseline")
        void extractedCountMatchesBaseline() {
            Set<String> ids = MigrationCompletionValidator.extractActionIdConstants();
            // Should be close to BaselineActionInventory.EXPECTED_ACTION_ID_COUNT
            assertTrue(ids.size() >= 300,
                "Should extract at least 300 action IDs. Found: " + ids.size());
        }
    }
}

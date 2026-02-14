package com.devmod.actions.legacy;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.bindings.BindingRegistry;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.EngineFeatureFlags;
import com.devmod.actions.legacy.CutoverOrchestrator.CutoverReport;
import com.devmod.actions.legacy.CutoverOrchestrator.CutoverState;
import com.devmod.actions.legacy.MigrationCompletionValidator.MigrationReadinessReport;
import com.devmod.arena.config.FeatureFlagRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CutoverOrchestrator.
 */
@DisplayName("CutoverOrchestrator")
class CutoverOrchestratorTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlerRegistry;
    private BindingRegistry bindingRegistry;
    private FeatureFlagRegistry flagRegistry;
    private MigrationCompletionValidator validator;
    private CutoverOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlerRegistry = new HandlerRegistry();
        bindingRegistry = new BindingRegistry();
        flagRegistry = new FeatureFlagRegistry();
        ShadowModeComparator.resetCounters();

        EngineFeatureFlags.resetForTesting();
        EngineFeatureFlags.register(flagRegistry);

        validator = new MigrationCompletionValidator(
            catalog, handlerRegistry, bindingRegistry);
        orchestrator = new CutoverOrchestrator(validator, flagRegistry);
    }

    @AfterEach
    void tearDown() {
        EngineFeatureFlags.resetForTesting();
    }

    /**
     * Registers all action IDs to make migration "complete".
     */
    private void registerAllActions() {
        Set<String> allIds = MigrationCompletionValidator.extractActionIdConstants();
        for (String id : allIds) {
            ActionSpec spec = ActionSpec.builder(id)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.MISC)
                .ui("label", "desc", "minecraft:stone", "Root/Test/" + id, false)
                .build();
            catalog.register(spec);
            handlerRegistry.register(id, ctx -> {});
        }
    }

    @Nested
    @DisplayName("Initial State")
    class InitialStateTests {

        @Test
        @DisplayName("Starts in IDLE state")
        void startsIdle() {
            assertEquals(CutoverState.IDLE, orchestrator.getState());
        }
    }

    @Nested
    @DisplayName("Pre-Check Passes -> Cutover Succeeds")
    class SuccessfulCutoverTests {

        @Test
        @DisplayName("Full cutover sequence succeeds")
        void fullCutoverSequence() {
            registerAllActions();

            // 1. Pre-check
            MigrationReadinessReport report = orchestrator.preCutoverCheck();
            assertTrue(report.ready());
            assertEquals(CutoverState.CHECKING, orchestrator.getState());

            // 2. Enable V2
            orchestrator.enableV2();
            assertEquals(CutoverState.CUTTING_OVER, orchestrator.getState());
            assertTrue(flagRegistry.isEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED));
            assertTrue(flagRegistry.isEnabled(EngineFeatureFlags.V2_ACTIVE_MODE));

            // 3. Disable shadow mode
            orchestrator.disableShadowMode();
            assertFalse(flagRegistry.isEnabled(EngineFeatureFlags.V2_SHADOW_MODE));

            // 4. Verify V2 active
            assertTrue(orchestrator.verifyV2Active());
            assertEquals(CutoverState.ACTIVE, orchestrator.getState());
        }
    }

    @Nested
    @DisplayName("Pre-Check Fails -> Cutover Blocked")
    class FailedPreCheckTests {

        @Test
        @DisplayName("Failed pre-check blocks enableV2")
        void failedPreCheckBlocksEnableV2() {
            // Don't register any actions - pre-check will fail
            MigrationReadinessReport report = orchestrator.preCutoverCheck();
            assertFalse(report.ready());
            assertEquals(CutoverState.IDLE, orchestrator.getState());

            // Attempt to enable V2 should fail
            assertThrows(IllegalStateException.class, () -> orchestrator.enableV2());
        }

        @Test
        @DisplayName("Pre-check returns to IDLE on failure")
        void preCheckReturnsToIdleOnFailure() {
            orchestrator.preCutoverCheck();
            assertEquals(CutoverState.IDLE, orchestrator.getState());
        }
    }

    @Nested
    @DisplayName("Rollback")
    class RollbackTests {

        @Test
        @DisplayName("Rollback restores V1")
        void rollbackRestoresV1() {
            registerAllActions();

            orchestrator.preCutoverCheck();
            orchestrator.enableV2();
            assertEquals(CutoverState.CUTTING_OVER, orchestrator.getState());

            // Verify V2 was enabled
            assertTrue(flagRegistry.isEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED));

            // Rollback
            orchestrator.rollback();
            assertEquals(CutoverState.ROLLED_BACK, orchestrator.getState());
            assertFalse(flagRegistry.isEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED));
            assertFalse(flagRegistry.isEnabled(EngineFeatureFlags.V2_ACTIVE_MODE));
            assertFalse(flagRegistry.isEnabled(EngineFeatureFlags.V2_SHADOW_MODE));
        }

        @Test
        @DisplayName("Rollback from ACTIVE state")
        void rollbackFromActive() {
            registerAllActions();

            orchestrator.preCutoverCheck();
            orchestrator.enableV2();
            orchestrator.disableShadowMode();
            orchestrator.verifyV2Active();
            assertEquals(CutoverState.ACTIVE, orchestrator.getState());

            orchestrator.rollback();
            assertEquals(CutoverState.ROLLED_BACK, orchestrator.getState());
            assertFalse(flagRegistry.isEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED));
        }
    }

    @Nested
    @DisplayName("State Machine Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("Cannot enableV2 without pre-check")
        void cannotEnableV2WithoutPreCheck() {
            assertThrows(IllegalStateException.class, () -> orchestrator.enableV2());
        }

        @Test
        @DisplayName("Cannot disableShadowMode without enableV2")
        void cannotDisableShadowModeWithoutEnableV2() {
            assertThrows(IllegalStateException.class, () -> orchestrator.disableShadowMode());
        }

        @Test
        @DisplayName("Cannot verifyV2Active without enableV2")
        void cannotVerifyV2ActiveWithoutEnableV2() {
            assertThrows(IllegalStateException.class, () -> orchestrator.verifyV2Active());
        }

        @Test
        @DisplayName("Cannot pre-check when already ACTIVE")
        void cannotPreCheckWhenActive() {
            registerAllActions();
            orchestrator.preCutoverCheck();
            orchestrator.enableV2();
            orchestrator.disableShadowMode();
            orchestrator.verifyV2Active();

            assertThrows(IllegalStateException.class, () -> orchestrator.preCutoverCheck());
        }
    }

    @Nested
    @DisplayName("Cannot Cutover Twice")
    class DoubleCutoverTests {

        @Test
        @DisplayName("Cannot enable V2 twice")
        void cannotEnableV2Twice() {
            registerAllActions();

            orchestrator.preCutoverCheck();
            orchestrator.enableV2();

            // Already in CUTTING_OVER, cannot enableV2 again
            assertThrows(IllegalStateException.class, () -> orchestrator.enableV2());
        }
    }

    @Nested
    @DisplayName("Report Generation")
    class ReportTests {

        @Test
        @DisplayName("Report captures events")
        void reportCapturesEvents() {
            registerAllActions();

            orchestrator.preCutoverCheck();
            orchestrator.enableV2();

            CutoverReport report = orchestrator.generateCutoverReport();
            assertNotNull(report);
            assertEquals(CutoverState.CUTTING_OVER, report.finalState());
            assertFalse(report.events().isEmpty());
            assertNotNull(report.readinessReport());
            assertNotNull(report.generatedAt());
        }

        @Test
        @DisplayName("Report summary is non-empty")
        void reportSummaryNonEmpty() {
            orchestrator.preCutoverCheck(); // Will fail, but still generates events

            CutoverReport report = orchestrator.generateCutoverReport();
            String summary = report.toSummary();
            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("Cutover Report"));
        }

        @Test
        @DisplayName("Report includes rollback events")
        void reportIncludesRollbackEvents() {
            registerAllActions();

            orchestrator.preCutoverCheck();
            orchestrator.enableV2();
            orchestrator.rollback();

            CutoverReport report = orchestrator.generateCutoverReport();
            assertEquals(CutoverState.ROLLED_BACK, report.finalState());

            boolean hasRollback = report.events().stream()
                .anyMatch(e -> e.type().contains("ROLLBACK"));
            assertTrue(hasRollback, "Report should contain rollback events");
        }
    }
}

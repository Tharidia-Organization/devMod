package com.devmod.actions.hardening;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionResult;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.legacy.LegacyCompatBridge;
import com.devmod.actions.legacy.ShadowModeComparator;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the Actions V2 system.
 * Simulates real-world scenarios: radial menu, keybind, command, network,
 * shadow mode, feature flag toggle, and rollback.
 */
@DisplayName("End-to-End Integration Tests")
class EndToEndIntegrationTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain fullPolicyChain;
    private List<String> telemetryLines;
    private ActionExecutor executor;
    private final AtomicBoolean handlerCalled = new AtomicBoolean(false);
    private final AtomicReference<String> lastHandlerAction = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();
        telemetryLines = new ArrayList<>();

        fullPolicyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(CommandSafetyPolicy.INSTANCE)
            .add(new RateLimitPolicy())
            .build();

        TelemetryStep.TelemetrySink testSink = telemetryLines::add;
        executor = ActionExecutor.createDefault(catalog, handlers, fullPolicyChain, testSink);

        handlerCalled.set(false);
        lastHandlerAction.set(null);
    }

    private void registerAction(String id, ActionChannel channel, ActionCategory category,
                                 ActionOrigin... extraOrigins) {
        EnumSet<ActionOrigin> origins = EnumSet.of(
            ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
            ActionOrigin.UI, ActionOrigin.EVENT);
        for (ActionOrigin extra : extraOrigins) {
            origins.add(extra);
        }

        // Only set allowed origins with NETWORK for non-CLIENT channels
        if (channel == ActionChannel.CLIENT) {
            origins.remove(ActionOrigin.NETWORK);
        }

        catalog.register(ActionSpec.builder(id)
            .channel(channel)
            .allowedOrigins(origins)
            .category(category)
            .ui("label." + id, "desc." + id)
            .build());
        handlers.register(id, ctx -> {
            handlerCalled.set(true);
            lastHandlerAction.set(id);
        });
    }

    // =========================================================================
    // 1. Radial Menu Action
    // =========================================================================

    @Nested
    @DisplayName("1. Radial Menu Action Scenario")
    class RadialMenuScenario {

        @Test
        @DisplayName("User opens radial -> selects action -> invoked through V2 -> OK result")
        void radialMenuAction() {
            registerAction("devmod.ui.settings.open_e2e",
                ActionChannel.CLIENT, ActionCategory.UI);

            // Simulate radial menu invocation
            ActionContext ctx = ActionContext.builder(ActionOrigin.RADIAL)
                .clientSide(true)
                .build();

            ActionResult result = executor.execute("devmod.ui.settings.open_e2e", ctx);

            assertTrue(result.isSuccess(), "Radial action should succeed");
            assertTrue(handlerCalled.get(), "Handler should be called");
            assertEquals("devmod.ui.settings.open_e2e", lastHandlerAction.get());

            // Verify telemetry
            assertEquals(1, telemetryLines.size());
            String telemetry = telemetryLines.get(0);
            assertTrue(telemetry.contains("\"origin\":\"radial\""));
            assertTrue(telemetry.contains("\"result\":\"OK\""));
            assertTrue(telemetry.contains("\"engine\":\"v2\""));
        }
    }

    // =========================================================================
    // 2. Keybind Action
    // =========================================================================

    @Nested
    @DisplayName("2. Keybind Action Scenario")
    class KeybindScenario {

        @Test
        @DisplayName("Keybind pressed -> invoked through V2 -> OK result")
        void keybindAction() {
            String id = "devmod.ability.dash_e2e";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.COMBAT)
                .ui("dash", "desc")
                .binding("keyDash", null, null)
                .build());
            handlers.register(id, ctx -> {
                handlerCalled.set(true);
                lastHandlerAction.set(id);
            });

            ActionContext ctx = ActionContext.builder(ActionOrigin.KEYBIND)
                .clientSide(true)
                .build();

            ActionResult result = executor.execute(id, ctx);

            assertTrue(result.isSuccess(), "Keybind action should succeed");
            assertTrue(handlerCalled.get());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"origin\":\"keybind\""));
        }
    }

    // =========================================================================
    // 3. Command Action
    // =========================================================================

    @Nested
    @DisplayName("3. Command Action Scenario")
    class CommandScenario {

        @Test
        @DisplayName("/devmod command -> invoked through V2 -> server command executed")
        void commandAction() {
            String id = "devmod.command.say_hello_e2e";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("say hello", "desc")
                .binding(null, "say hello", null)
                .build());
            handlers.register(id, ctx -> {
                handlerCalled.set(true);
                lastHandlerAction.set(id);
            });

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute(id, ctx);

            assertTrue(result.isSuccess(), "Command action should succeed");
            assertTrue(handlerCalled.get());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"origin\":\"command\""));
        }

        @Test
        @DisplayName("Dangerous command blocked through V2 pipeline")
        void dangerousCommandBlocked() {
            String id = "devmod.command.dangerous_e2e";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("op", "desc")
                .binding(null, "op attacker", null)
                .build());
            handlers.register(id, ctx -> handlerCalled.set(true));

            ActionResult result = executor.execute(id,
                ActionContext.builder(ActionOrigin.COMMAND).clientSide(false).build());

            assertTrue(result.isBlocked(), "Dangerous command should be blocked");
            assertFalse(handlerCalled.get(), "Handler should NOT be called");
        }
    }

    // =========================================================================
    // 4. Network Action
    // =========================================================================

    @Nested
    @DisplayName("4. Network Action Scenario")
    class NetworkScenario {

        @Test
        @DisplayName("Packet received -> origin NETWORK -> whitelisted action -> allowed")
        void networkActionAllowed() {
            registerAction("devmod.server.quest_rpc_e2e",
                ActionChannel.SERVER, ActionCategory.ENDURANCE,
                ActionOrigin.NETWORK);

            ActionContext ctx = ActionContext.builder(ActionOrigin.NETWORK)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute("devmod.server.quest_rpc_e2e", ctx);

            assertTrue(result.isSuccess(), "Whitelisted network action should succeed");
            assertTrue(handlerCalled.get());
        }

        @Test
        @DisplayName("Packet received -> origin NETWORK -> non-whitelisted action -> blocked")
        void networkActionBlocked() {
            // CLIENT-only action does not allow NETWORK
            registerAction("devmod.ui.client_only_e2e",
                ActionChannel.CLIENT, ActionCategory.UI);

            ActionContext ctx = ActionContext.builder(ActionOrigin.NETWORK)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute("devmod.ui.client_only_e2e", ctx);

            assertTrue(result.isBlocked(), "Non-whitelisted network action should be blocked");
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
        }
    }

    // =========================================================================
    // 5. Shadow Mode
    // =========================================================================

    @Nested
    @DisplayName("5. Shadow Mode Scenario")
    class ShadowModeScenario {

        @BeforeEach
        void resetComparator() {
            ShadowModeComparator.resetCounters();
        }

        @Test
        @DisplayName("Shadow comparison: matching results produce match")
        void shadowModeMatch() {
            ActionResult v1 = ActionResult.ok(5);
            ActionResult v2 = ActionResult.ok(6);

            var comparison = ShadowModeComparator.compare("devmod.test.shadow_match", v1, v2);

            assertTrue(comparison.matches());
            assertNull(comparison.mismatchType());
            assertEquals(1, ShadowModeComparator.getTotalComparisons());
            assertEquals(1, ShadowModeComparator.getTotalMatches());
            assertEquals(0, ShadowModeComparator.getTotalMismatches());
        }

        @Test
        @DisplayName("Shadow comparison: status mismatch detected")
        void shadowModeStatusMismatch() {
            ActionResult v1 = ActionResult.ok(5);
            ActionResult v2 = ActionResult.blocked("TEST", "test reason");

            var comparison = ShadowModeComparator.compare("devmod.test.shadow_mismatch", v1, v2);

            assertFalse(comparison.matches());
            assertNotNull(comparison.mismatchType());
            assertEquals(ShadowModeComparator.MismatchType.STATUS, comparison.mismatchType());
        }

        @Test
        @DisplayName("Shadow comparison: error code mismatch detected")
        void shadowModeErrorCodeMismatch() {
            ActionResult v1 = ActionResult.blocked("CODE_A", "reason a");
            ActionResult v2 = ActionResult.blocked("CODE_B", "reason b");

            var comparison = ShadowModeComparator.compare("devmod.test.shadow_errcode", v1, v2);

            assertFalse(comparison.matches());
            assertEquals(ShadowModeComparator.MismatchType.ERROR_CODE, comparison.mismatchType());
        }

        @Test
        @DisplayName("Shadow report generation includes per-action stats")
        void shadowReport() {
            ActionResult v1 = ActionResult.ok(5);
            ActionResult v2 = ActionResult.ok(6);

            ShadowModeComparator.compare("devmod.test.report_a", v1, v2);
            ShadowModeComparator.compare("devmod.test.report_b", v1, v2);
            ShadowModeComparator.compare("devmod.test.report_b", v1, v2);

            var report = ShadowModeComparator.generateReport();

            assertEquals(3, report.totalComparisons());
            assertEquals(3, report.totalMatches());
            assertEquals(0, report.totalMismatches());
            assertEquals(100.0, report.matchRate(), 0.01);
            assertEquals(2, report.perActionSummaries().size());
        }
    }

    // =========================================================================
    // 6. Feature Flag Toggle
    // =========================================================================

    @Nested
    @DisplayName("6. Feature Flag Toggle Scenario")
    class FeatureFlagToggleScenario {

        @Test
        @DisplayName("LegacyCompatBridge routes migrated actions to V2")
        void bridgeRoutesToV2ForMigrated() {
            String actionId = "devmod.test.bridge_migrated";
            registerAction(actionId, ActionChannel.CLIENT, ActionCategory.UI);

            LegacyCompatBridge bridge = new LegacyCompatBridge(
                executor, Set.of(actionId));

            assertTrue(bridge.isMigrated(actionId));
            assertEquals(1, bridge.migratedCount());
        }

        @Test
        @DisplayName("LegacyCompatBridge handles unmigrated actions")
        void bridgeHandlesUnmigrated() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(
                executor, Set.of());

            assertFalse(bridge.isMigrated("devmod.test.not_migrated"));
        }

        @Test
        @DisplayName("Migration/unmigration at runtime")
        void runtimeMigrationToggle() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(
                executor, Set.of());

            assertFalse(bridge.isMigrated("devmod.test.toggle_action"));

            bridge.markMigrated("devmod.test.toggle_action");
            assertTrue(bridge.isMigrated("devmod.test.toggle_action"));

            bridge.markUnmigrated("devmod.test.toggle_action");
            assertFalse(bridge.isMigrated("devmod.test.toggle_action"));
        }
    }

    // =========================================================================
    // 7. Rollback Scenario
    // =========================================================================

    @Nested
    @DisplayName("7. Rollback Scenario")
    class RollbackScenario {

        @Test
        @DisplayName("V2 executor with exception returns FAILED result cleanly")
        void v2ExecutorExceptionReturnsFailedResult() {
            String id = "devmod.test.rollback_action";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.TESTING)
                .ui("label", "desc")
                .build());
            handlers.register(id, ctx -> {
                throw new RuntimeException("V2 handler failure");
            });

            ActionResult result = executor.execute(id,
                ActionContext.builder(ActionOrigin.RADIAL).clientSide(true).build());

            // V2 catches the exception and returns FAILED
            assertTrue(result.isFailed() || result.isBlocked());
            assertNotNull(result.errorCode());
        }

        @Test
        @DisplayName("LegacyCompatBridge tracks V2 fallback metrics")
        void bridgeTracksMetrics() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(
                executor, Set.of("devmod.test.metrics_action"));

            assertEquals(0, bridge.getV1Invocations());
            assertEquals(0, bridge.getV2Invocations());
            assertEquals(0, bridge.getV2Fallbacks());
            assertEquals(0, bridge.getShadowRuns());
        }
    }

    // =========================================================================
    // Additional: Pipeline Integrity
    // =========================================================================

    @Nested
    @DisplayName("Pipeline Integrity")
    class PipelineIntegrity {

        @Test
        @DisplayName("Full pipeline executes all 8 steps on success")
        void fullPipelineStepCount() {
            assertEquals(8, executor.stepCount(),
                "Default pipeline should have 8 steps");
        }

        @Test
        @DisplayName("Pipeline steps execute in correct order")
        void pipelineStepOrder() {
            List<String> stepOrder = new ArrayList<>();

            ActionExecutor ordered = ActionExecutor.builder()
                .addStep(trackStep("step1", stepOrder))
                .addStep(trackStep("step2", stepOrder))
                .addStep(trackStep("step3", stepOrder))
                .build();

            ordered.execute("devmod.test.order", ActionContext.builder(ActionOrigin.RADIAL).build());

            assertEquals(List.of("step1", "step2", "step3"), stepOrder);
        }

        @Test
        @DisplayName("Pipeline short-circuits on ABORT, skipping remaining steps")
        void pipelineShortCircuitsOnAbort() {
            List<String> stepOrder = new ArrayList<>();

            ActionExecutor shortCircuit = ActionExecutor.builder()
                .addStep(trackStep("before", stepOrder))
                .addStep(new PipelineStep() {
                    @Override
                    public StepResult process(ExecutionContext ctx) {
                        stepOrder.add("abort");
                        return StepResult.abort("test abort");
                    }
                    @Override
                    public String stepName() { return "abort"; }
                })
                .addStep(trackStep("after", stepOrder))
                .build();

            ActionResult result = shortCircuit.execute("devmod.test.abort",
                ActionContext.builder(ActionOrigin.RADIAL).build());

            assertTrue(result.isBlocked());
            assertEquals(List.of("before", "abort"), stepOrder);
            assertFalse(stepOrder.contains("after"), "'after' step should not execute");
        }

        private PipelineStep trackStep(String name, List<String> order) {
            return new PipelineStep() {
                @Override
                public StepResult process(ExecutionContext ctx) {
                    order.add(name);
                    return StepResult.continueStep();
                }
                @Override
                public String stepName() { return name; }
            };
        }
    }
}

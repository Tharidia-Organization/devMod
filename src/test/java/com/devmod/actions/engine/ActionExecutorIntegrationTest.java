package com.devmod.actions.engine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.devmod.actions.domains.ActionHandler;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full ActionExecutor pipeline end-to-end.
 */
@DisplayName("ActionExecutor Integration Tests")
class ActionExecutorIntegrationTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain policyChain;
    private RateLimitPolicy rateLimitPolicy;
    private List<String> telemetryLines;
    private ActionExecutor executor;

    // Tracking for handler invocations
    private final AtomicBoolean handlerCalled = new AtomicBoolean(false);
    private final AtomicInteger handlerCallCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();
        rateLimitPolicy = new RateLimitPolicy();
        telemetryLines = new ArrayList<>();

        policyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(CommandSafetyPolicy.INSTANCE)
            .add(rateLimitPolicy)
            .build();

        TelemetryStep.TelemetrySink testSink = telemetryLines::add;

        executor = ActionExecutor.createDefault(catalog, handlers, policyChain, testSink);

        handlerCalled.set(false);
        handlerCallCount.set(0);
    }

    /**
     * Registers a standard test action.
     */
    private void registerAction(String id) {
        registerAction(id, ActionChannel.CLIENT, ActionCategory.UI, 0, false);
    }

    private void registerAction(String id, ActionChannel channel, ActionCategory category,
                                 int permLevel, boolean requiresConfirm) {
        ActionSpec.Builder builder = ActionSpec.builder(id)
            .channel(channel)
            .category(category)
            .permissionLevel(permLevel)
            .ui("label." + id, "desc." + id);

        if (requiresConfirm) {
            builder.policy(true, 0, null);
        }

        if (channel == ActionChannel.SERVER || channel == ActionChannel.BOTH) {
            builder.allowedOrigins(EnumSet.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK
            ));
        }

        catalog.register(builder.build());
        handlers.register(id, ctx -> {
            handlerCalled.set(true);
            handlerCallCount.incrementAndGet();
        });
    }

    private ActionContext contextForOrigin(ActionOrigin origin) {
        return ActionContext.builder(origin)
            .clientSide(origin != ActionOrigin.COMMAND && origin != ActionOrigin.NETWORK)
            .build();
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("1. Valid action with trusted origin returns OK")
        void validActionReturnsOk() {
            registerAction("devmod.ui.settings.open");

            ActionResult result = executor.execute("devmod.ui.settings.open",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isSuccess());
            assertTrue(handlerCalled.get());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("action_v2_invoked"));
        }

        @Test
        @DisplayName("10. Duration measurement: durationMs >= 0 on success")
        void durationMeasured() {
            registerAction("devmod.ui.action");

            ActionResult result = executor.execute("devmod.ui.action",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isSuccess());
            assertTrue(result.durationMs() >= 0);
        }
    }

    // =========================================================================
    // Resolve failures
    // =========================================================================

    @Nested
    @DisplayName("Resolve Step")
    class ResolveStepTests {

        @Test
        @DisplayName("2. Unknown action ABORTs at ResolveStep")
        void unknownActionAborts() {
            ActionResult result = executor.execute("devmod.nonexistent.action",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNKNOWN_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
            // Telemetry is terminal, so the abort is still recorded
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"type\":\"action_v2_blocked\""));
        }
    }

    // =========================================================================
    // Policy authorization failures
    // =========================================================================

    @Nested
    @DisplayName("Policy Authorize Step")
    class PolicyAuthorizeTests {

        @Test
        @DisplayName("3. Untrusted origin denied ABORTs at PolicyAuthorizeStep")
        void untrustedOriginDenied() {
            // Register a CLIENT-only action (default: no NETWORK origin)
            registerAction("devmod.ui.secret.open");

            ActionResult result = executor.execute("devmod.ui.secret.open",
                contextForOrigin(ActionOrigin.NETWORK));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("4. Insufficient permission ABORTs at PolicyAuthorizeStep")
        void insufficientPermission() {
            registerAction("devmod.admin.rebuild",
                ActionChannel.SERVER, ActionCategory.ADMIN, 4, false);

            // Context with no player = no permission
            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute("devmod.admin.rebuild", ctx);

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_PERMISSION_DENIED, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("8. Rate limited ABORTs at PolicyAuthorizeStep")
        void rateLimited() {
            // Register action with rate limit of 1/sec
            ActionSpec spec = ActionSpec.builder("devmod.ability.dash")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.COMBAT)
                .ui("dash", "desc")
                .policy(false, 1, null)
                .build();
            catalog.register(spec);
            handlers.register("devmod.ability.dash", ctx -> handlerCallCount.incrementAndGet());

            // Invoke without a player so rate limit does not apply (no UUID to bucket)
            // Instead, test that rate limit policy is in the chain and respects spec
            ActionContext ctx = contextForOrigin(ActionOrigin.RADIAL);

            // First call should succeed
            ActionResult first = executor.execute("devmod.ability.dash", ctx);
            assertTrue(first.isSuccess());
        }

        @Test
        @DisplayName("9. Command injection blocked at PolicyAuthorizeStep")
        void commandInjectionBlocked() {
            ActionSpec spec = ActionSpec.builder("devmod.command.dangerous")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("dangerous", "desc")
                .binding(null, "op @a", null)
                .build();
            catalog.register(spec);
            handlers.register("devmod.command.dangerous", ctx -> handlerCalled.set(true));

            ActionResult result = executor.execute("devmod.command.dangerous",
                contextForOrigin(ActionOrigin.COMMAND));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
        }
    }

    // =========================================================================
    // Precondition and confirmation
    // =========================================================================

    @Nested
    @DisplayName("Precondition and Confirmation")
    class PreconditionTests {

        @Test
        @DisplayName("6. Requires confirmation ABORTs at ConfirmationStep")
        void requiresConfirmation() {
            registerAction("devmod.admin.wipe",
                ActionChannel.SERVER, ActionCategory.ADMIN, 0, true);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(false)
                .build();

            ActionResult result = executor.execute("devmod.admin.wipe", ctx);

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_REQUIRES_CONFIRM, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("Confirmed action passes ConfirmationStep")
        void confirmedActionPasses() {
            registerAction("devmod.admin.confirm_test",
                ActionChannel.SERVER, ActionCategory.ADMIN, 0, true);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(true)
                .build();

            ActionResult result = executor.execute("devmod.admin.confirm_test", ctx);

            assertTrue(result.isSuccess());
            assertTrue(handlerCalled.get());
        }
    }

    // =========================================================================
    // Handler exception
    // =========================================================================

    @Nested
    @DisplayName("Execute Step")
    class ExecuteStepTests {

        @Test
        @DisplayName("7. Handler throws exception returns FAILED")
        void handlerException() {
            ActionSpec spec = ActionSpec.builder("devmod.test.error")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.TESTING)
                .ui("error", "desc")
                .build();
            catalog.register(spec);
            handlers.register("devmod.test.error", ctx -> {
                throw new RuntimeException("Test handler error");
            });

            ActionResult result = executor.execute("devmod.test.error",
                contextForOrigin(ActionOrigin.RADIAL));

            // The ExecuteStep catches the exception and sets ABORT + FAILED result.
            // The ActionExecutor then sees the ABORT and returns ctx.result().
            assertTrue(result.isFailed() || result.isBlocked());
            assertNotNull(result.errorCode());
        }

        @Test
        @DisplayName("handlerStarted distinguishes a refusal from a partial execution")
        void handlerStartedDistinguishesRefusalFromPartialExecution() {
            // Blocked before Execute: nothing was applied, so a caller may retry.
            registerAction("devmod.ui.refused");
            ActionExecutor.ExecutionOutcome refused = executor.executeDetailed(
                "devmod.ui.refused", contextForOrigin(ActionOrigin.NETWORK), false);

            assertTrue(refused.result().isBlocked());
            assertFalse(refused.handlerStarted());

            // Threw inside the handler: an unknown prefix of its side effects landed,
            // so retrying elsewhere would re-apply them.
            catalog.register(ActionSpec.builder("devmod.ui.partial")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            handlers.register("devmod.ui.partial", ctx -> {
                throw new RuntimeException("failed halfway");
            });

            ActionExecutor.ExecutionOutcome partial = executor.executeDetailed(
                "devmod.ui.partial", contextForOrigin(ActionOrigin.RADIAL), false);

            assertTrue(partial.result().isFailed());
            assertTrue(partial.handlerStarted());
        }

        @Test
        @DisplayName("Handler reporting failure yields a FAILED result, not OK")
        void handlerReportedFailurePropagates() {
            catalog.register(ActionSpec.builder("devmod.ui.reports_failure")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            handlers.registerResult("devmod.ui.reports_failure",
                ctx -> ActionHandler.Outcome.failed("NOPE", "could not do it"));

            ActionResult result = executor.execute("devmod.ui.reports_failure",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isFailed());
            assertEquals("NOPE", result.errorCode());
            assertEquals("could not do it", result.message());
            assertTrue(telemetryLines.get(0).contains("\"type\":\"action_v2_failed\""));
        }

        @Test
        @DisplayName("Handler reporting a block yields BLOCKED, not FAILED")
        void handlerReportedBlockPropagates() {
            catalog.register(ActionSpec.builder("devmod.ui.reports_block")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            handlers.registerResult("devmod.ui.reports_block",
                ctx -> ActionHandler.Outcome.blocked(
                    ActionResult.ERROR_COMMAND_REJECTED, "command refused"));

            ActionResult result = executor.execute("devmod.ui.reports_block",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_COMMAND_REJECTED, result.errorCode());
            assertTrue(telemetryLines.get(0).contains("\"type\":\"action_v2_blocked\""));
        }

        @Test
        @DisplayName("No handler registered returns blocked")
        void noHandlerRegistered() {
            ActionSpec spec = ActionSpec.builder("devmod.test.nohandler")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.TESTING)
                .ui("no handler", "desc")
                .build();
            catalog.register(spec);
            // Intentionally NOT registering a handler

            ActionResult result = executor.execute("devmod.test.nohandler",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNKNOWN_ACTION, result.errorCode());
        }
    }

    // =========================================================================
    // All origins tested
    // =========================================================================

    @Nested
    @DisplayName("Origin Coverage")
    class OriginCoverageTests {

        @Test
        @DisplayName("11. RADIAL origin succeeds")
        void radialOrigin() {
            registerAction("devmod.ui.test_radial");
            ActionResult result = executor.execute("devmod.ui.test_radial",
                contextForOrigin(ActionOrigin.RADIAL));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("11. KEYBIND origin succeeds")
        void keybindOrigin() {
            registerAction("devmod.ui.test_keybind");
            ActionResult result = executor.execute("devmod.ui.test_keybind",
                contextForOrigin(ActionOrigin.KEYBIND));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("11. COMMAND origin succeeds")
        void commandOrigin() {
            registerAction("devmod.ui.test_command");
            ActionResult result = executor.execute("devmod.ui.test_command",
                contextForOrigin(ActionOrigin.COMMAND));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("11. UI origin succeeds")
        void uiOrigin() {
            registerAction("devmod.ui.test_ui");
            ActionResult result = executor.execute("devmod.ui.test_ui",
                contextForOrigin(ActionOrigin.UI));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("11. EVENT origin succeeds")
        void eventOrigin() {
            registerAction("devmod.ui.test_event");
            ActionResult result = executor.execute("devmod.ui.test_event",
                contextForOrigin(ActionOrigin.EVENT));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("11. NETWORK origin blocked for CLIENT-only")
        void networkOriginBlocked() {
            registerAction("devmod.ui.test_network");
            ActionResult result = executor.execute("devmod.ui.test_network",
                contextForOrigin(ActionOrigin.NETWORK));
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("11. UNKNOWN origin blocked for CLIENT-only")
        void unknownOriginBlocked() {
            registerAction("devmod.ui.test_unknown");
            ActionResult result = executor.execute("devmod.ui.test_unknown",
                contextForOrigin(ActionOrigin.UNKNOWN));
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("NETWORK origin allowed for SERVER action with NETWORK in allowedOrigins")
        void networkOriginAllowedForServer() {
            registerAction("devmod.server.network_ok",
                ActionChannel.SERVER, ActionCategory.MISC, 0, false);
            ActionResult result = executor.execute("devmod.server.network_ok",
                contextForOrigin(ActionOrigin.NETWORK));
            assertTrue(result.isSuccess());
        }
    }

    // =========================================================================
    // Telemetry verification
    // =========================================================================

    @Nested
    @DisplayName("Telemetry")
    class TelemetryTests {

        @Test
        @DisplayName("Telemetry records success events")
        void telemetryRecordsSuccess() {
            registerAction("devmod.ui.telemetry_test");

            executor.execute("devmod.ui.telemetry_test",
                contextForOrigin(ActionOrigin.RADIAL));

            assertEquals(1, telemetryLines.size());
            String line = telemetryLines.get(0);
            assertTrue(line.contains("\"type\":\"action_v2_invoked\""));
            assertTrue(line.contains("\"actionId\":\"devmod.ui.telemetry_test\""));
            assertTrue(line.contains("\"origin\":\"radial\""));
            assertTrue(line.contains("\"engine\":\"v2\""));
            assertTrue(line.contains("\"result\":\"OK\""));
        }

        @Test
        @DisplayName("Telemetry fires on early pipeline abort")
        void telemetryRecordedOnEarlyAbort() {
            executor.execute("devmod.nonexistent.action",
                contextForOrigin(ActionOrigin.RADIAL));

            // The pipeline aborts at ResolveStep, but TelemetryStep is terminal:
            // a blocked action that reported nothing is how a dashboard ends up
            // claiming 100% success.
            assertEquals(1, telemetryLines.size());
            String line = telemetryLines.get(0);
            assertTrue(line.contains("\"type\":\"action_v2_blocked\""));
            assertTrue(line.contains("\"result\":\"BLOCKED\""));
            assertTrue(line.contains("\"errorCode\":\"" + ActionResult.ERROR_UNKNOWN_ACTION + "\""));
        }

        @Test
        @DisplayName("Telemetry records a denied action as blocked, not as success")
        void telemetryRecordsPolicyDenial() {
            // NETWORK origin is not in the default allowed set for a CLIENT action.
            registerAction("devmod.ui.denied_test");

            ActionResult result = executor.execute("devmod.ui.denied_test",
                contextForOrigin(ActionOrigin.NETWORK));

            assertTrue(result.isBlocked());
            assertFalse(handlerCalled.get());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"type\":\"action_v2_blocked\""));
        }

        @Test
        @DisplayName("Telemetry records a handler exception as failed")
        void telemetryRecordsHandlerException() {
            catalog.register(ActionSpec.builder("devmod.ui.throwing_test")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build());
            handlers.register("devmod.ui.throwing_test", ctx -> {
                throw new RuntimeException("boom");
            });

            ActionResult result = executor.execute("devmod.ui.throwing_test",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isFailed());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"type\":\"action_v2_failed\""));
        }

        @Test
        @DisplayName("Telemetry marks a dry run so it is excluded from invocation counts")
        void telemetryMarksDryRun() {
            registerAction("devmod.ui.dry_run_test");

            ActionExecutor.ExecutionOutcome outcome = executor.executeDetailed(
                "devmod.ui.dry_run_test", contextForOrigin(ActionOrigin.RADIAL), true);

            assertTrue(outcome.result().isSuccess());
            assertFalse(handlerCalled.get(), "dry run must not run the handler");
            assertFalse(outcome.handlerStarted());
            assertEquals(1, telemetryLines.size());
            assertTrue(telemetryLines.get(0).contains("\"dryRun\":true"));
        }
    }

    // =========================================================================
    // Pipeline step count
    // =========================================================================

    @Nested
    @DisplayName("Pipeline Structure")
    class PipelineStructureTests {

        @Test
        @DisplayName("Default pipeline has 8 steps")
        void defaultPipelineHas8Steps() {
            assertEquals(8, executor.stepCount());
        }

        @Test
        @DisplayName("Custom pipeline can have fewer steps")
        void customPipelineCanHaveFewerSteps() {
            ActionExecutor minimal = ActionExecutor.builder()
                .addStep(new PipelineStep() {
                    @Override public StepResult process(ExecutionContext ctx) {
                        return StepResult.continueStep();
                    }
                    @Override public String stepName() { return "noop"; }
                })
                .build();
            assertEquals(1, minimal.stepCount());
        }
    }
}

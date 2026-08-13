package com.devmod.actions.engine.steps;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPrecondition;
import com.devmod.actions.ActionPreconditionRegistry;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for each PipelineStep in isolation.
 * Verifies each step's name, null-safety, and behavior with minimal mock context.
 */
@DisplayName("PipelineStep Contract Tests")
class PipelineStepContractTest {

    private ActionContext radialContext;
    private ActionContext networkContext;
    private ActionContext commandContext;

    @BeforeEach
    void setUp() {
        radialContext = ActionContext.builder(ActionOrigin.RADIAL)
            .clientSide(true)
            .build();
        networkContext = ActionContext.builder(ActionOrigin.NETWORK)
            .clientSide(false)
            .build();
        commandContext = ActionContext.builder(ActionOrigin.COMMAND)
            .clientSide(false)
            .build();
    }

    private ActionSpec makeClientSpec(String id) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private ActionSpec makeServerSpec(String id) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .allowedOrigins(EnumSet.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK
            ))
            .category(ActionCategory.ADMIN)
            .ui("label." + id, "desc." + id)
            .build();
    }

    private ExecutionContext ctxWith(String actionId, ActionContext actionCtx) {
        return new ExecutionContext(actionId, actionCtx);
    }

    // =========================================================================
    // ResolveStep
    // =========================================================================

    @Nested
    @DisplayName("ResolveStep")
    class ResolveStepTests {

        private ActionCatalog catalog;
        private ResolveStep step;

        @BeforeEach
        void setUp() {
            catalog = new ActionCatalog();
            step = new ResolveStep(catalog);
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("Resolve", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.test.missing", radialContext);
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("known action sets resolvedSpec and returns CONTINUE")
        void knownActionContinues() {
            ActionSpec spec = makeClientSpec("devmod.ui.test");
            catalog.register(spec);

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            StepResult result = step.process(ctx);

            assertTrue(result.shouldContinue());
            assertNotNull(ctx.resolvedSpec());
            assertEquals("devmod.ui.test", ctx.resolvedSpec().id());
        }

        @Test
        @DisplayName("unknown action returns ABORT with error result")
        void unknownActionAborts() {
            ExecutionContext ctx = ctxWith("devmod.test.missing", radialContext);
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertNotNull(ctx.result());
            assertTrue(ctx.result().isBlocked());
            assertEquals(ActionResult.ERROR_UNKNOWN_ACTION, ctx.result().errorCode());
        }
    }

    // =========================================================================
    // ValidatePayloadStep
    // =========================================================================

    @Nested
    @DisplayName("ValidatePayloadStep")
    class ValidatePayloadStepTests {

        private ValidatePayloadStep step;

        @BeforeEach
        void setUp() {
            step = new ValidatePayloadStep();
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("ValidatePayload", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("ABORTs when no resolvedSpec")
        void abortsWithoutResolvedSpec() {
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.isAbort());
        }

        @Test
        @DisplayName("CONTINUEs when resolvedSpec is set")
        void continuesWithResolvedSpec() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }
    }

    // =========================================================================
    // PolicyAuthorizeStep
    // =========================================================================

    @Nested
    @DisplayName("PolicyAuthorizeStep")
    class PolicyAuthorizeStepTests {

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(PolicyChain.empty());
            assertFalse(step.stepName().isEmpty());
            assertEquals("PolicyAuthorize", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(PolicyChain.empty());
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("empty chain allows all")
        void emptyChainAllows() {
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(PolicyChain.empty());
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("OriginPolicy denies untrusted origin for CLIENT action")
        void originPolicyDeniesUntrusted() {
            PolicyChain chain = PolicyChain.builder()
                .add(OriginPolicy.INSTANCE)
                .build();
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(chain);

            ExecutionContext ctx = ctxWith("devmod.ui.test", networkContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertNotNull(ctx.result());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, ctx.result().errorCode());
        }

        @Test
        @DisplayName("PermissionPolicy denies insufficient permission")
        void permissionPolicyDenies() {
            PolicyChain chain = PolicyChain.builder()
                .add(PermissionPolicy.INSTANCE)
                .build();
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(chain);

            ActionSpec spec = ActionSpec.builder("devmod.admin.test")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(EnumSet.of(
                    ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                    ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK
                ))
                .category(ActionCategory.ADMIN)
                .permissionLevel(4)
                .ui("admin", "desc")
                .build();

            ExecutionContext ctx = ctxWith("devmod.admin.test", commandContext);
            ctx.setResolvedSpec(spec);
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertEquals(ActionResult.ERROR_PERMISSION_DENIED, ctx.result().errorCode());
        }

        @Test
        @DisplayName("ABORTs when no resolvedSpec")
        void abortsWithoutSpec() {
            PolicyAuthorizeStep step = new PolicyAuthorizeStep(PolicyChain.empty());
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.isAbort());
        }
    }

    // =========================================================================
    // PreconditionStep
    // =========================================================================

    @Nested
    @DisplayName("PreconditionStep")
    class PreconditionStepTests {

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            PreconditionStep step = new PreconditionStep();
            assertFalse(step.stepName().isEmpty());
            assertEquals("Precondition", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            PreconditionStep step = new PreconditionStep();
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("no preconditionRef passes")
        void noPreconditionRefPasses() {
            PreconditionStep step = new PreconditionStep();
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("unresolvable preconditionRef fails open at runtime")
        void unknownRefPasses() {
            PreconditionStep step = new PreconditionStep();
            ActionSpec spec = specWithPrecondition("some_unknown_ref");

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(spec);
            StepResult result = step.process(ctx);
            // Deliberate fail-open: an unresolvable ref must not take the action down.
            // ActionCatalogValidator's "preconditionRef" ERROR rule is what stops one
            // from reaching production - see ActionCatalogTest.
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("resolvable preconditionRef that passes lets the action through")
        void satisfiedRefContinues() {
            PreconditionStep step = new PreconditionStep(
                Map.of("alwaysTrue", ActionPreconditions.always()));

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(specWithPrecondition("alwaysTrue"));
            StepResult result = step.process(ctx);

            assertTrue(result.shouldContinue());
            assertNull(ctx.result());
        }

        @Test
        @DisplayName("resolvable preconditionRef that fails BLOCKs the action")
        void unsatisfiedRefBlocks() {
            ActionPrecondition never = ActionPreconditions.withMessage(
                context -> false, "devmod.action.unavailable");
            PreconditionStep step = new PreconditionStep(Map.of("neverTrue", never));

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(specWithPrecondition("neverTrue"));
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertNotNull(ctx.result());
            assertTrue(ctx.result().isBlocked());
            assertEquals(ActionResult.ERROR_PRECONDITION_FAILED, ctx.result().errorCode());
        }

        @Test
        @DisplayName("permission-level refs resolve without being enumerated")
        void parameterisedRefsResolve() {
            ActionPreconditionRegistry registry = ActionPreconditionRegistry.empty();

            assertNotNull(registry.resolve("requiresPermission2"));
            assertNotNull(registry.resolve("requiresPermissionOrClient_2"));
            assertTrue(registry.canResolve("requiresPermission4"));
            // Out of the 0-4 range ActionSpec allows, and a mistyped separator
            assertFalse(registry.canResolve("requiresPermission9"));
            assertFalse(registry.canResolve("requiresPermission_2"));
            // A null or empty ref means "no gate", which is always satisfiable
            assertTrue(registry.canResolve(null));
            assertTrue(registry.canResolve(""));
        }

        @Test
        @DisplayName("ABORTs when no resolvedSpec")
        void abortsWithoutSpec() {
            PreconditionStep step = new PreconditionStep();
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.isAbort());
        }

        private ActionSpec specWithPrecondition(String ref) {
            return ActionSpec.builder("devmod.ui.test")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .policy(false, 0, ref)
                .build();
        }
    }

    // =========================================================================
    // ConfirmationStep
    // =========================================================================

    @Nested
    @DisplayName("ConfirmationStep")
    class ConfirmationStepTests {

        private ConfirmationStep step;

        @BeforeEach
        void setUp() {
            step = new ConfirmationStep();
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("Confirmation", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("action without requiresConfirm passes")
        void noConfirmPasses() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("requiresConfirm without confirmation ABORTs")
        void unconfirmedAborts() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.wipe")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .policy(true, 0, null)
                .ui("wipe", "desc")
                .build();

            ActionContext unconfirmed = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(false)
                .build();

            ExecutionContext ctx = ctxWith("devmod.admin.wipe", unconfirmed);
            ctx.setResolvedSpec(spec);
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertEquals(ActionResult.ERROR_REQUIRES_CONFIRM, ctx.result().errorCode());
        }

        @Test
        @DisplayName("requiresConfirm with confirmation passes")
        void confirmedPasses() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.wipe")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .policy(true, 0, null)
                .ui("wipe", "desc")
                .build();

            ActionContext confirmed = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(true)
                .build();

            ExecutionContext ctx = ctxWith("devmod.admin.wipe", confirmed);
            ctx.setResolvedSpec(spec);
            StepResult result = step.process(ctx);

            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("ABORTs when no resolvedSpec")
        void abortsWithoutSpec() {
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.isAbort());
        }
    }

    // =========================================================================
    // ExecuteStep
    // =========================================================================

    @Nested
    @DisplayName("ExecuteStep")
    class ExecuteStepTests {

        private HandlerRegistry handlers;
        private ExecuteStep step;

        @BeforeEach
        void setUp() {
            handlers = new HandlerRegistry();
            step = new ExecuteStep(handlers);
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("Execute", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("handler invoked and result set on success")
        void handlerInvokedOnSuccess() {
            handlers.register("devmod.ui.test", actionCtx -> {});

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);

            assertTrue(result.shouldContinue());
            assertNotNull(ctx.result());
            assertTrue(ctx.result().isSuccess());
        }

        @Test
        @DisplayName("missing handler ABORTs with UNKNOWN_ACTION")
        void missingHandlerAborts() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertNotNull(ctx.result());
            assertEquals(ActionResult.ERROR_UNKNOWN_ACTION, ctx.result().errorCode());
        }

        @Test
        @DisplayName("handler exception ABORTs with EXCEPTION")
        void handlerExceptionAborts() {
            handlers.register("devmod.ui.test", actionCtx -> {
                throw new RuntimeException("test error");
            });

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            StepResult result = step.process(ctx);

            assertTrue(result.isAbort());
            assertNotNull(ctx.result());
            assertEquals(ActionResult.ERROR_EXCEPTION, ctx.result().errorCode());
        }

        @Test
        @DisplayName("ABORTs when no resolvedSpec")
        void abortsWithoutSpec() {
            ExecutionContext ctx = ctxWith("devmod.test.action", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.isAbort());
        }
    }

    // =========================================================================
    // FeedbackStep
    // =========================================================================

    @Nested
    @DisplayName("FeedbackStep")
    class FeedbackStepTests {

        private FeedbackStep step;

        @BeforeEach
        void setUp() {
            step = new FeedbackStep();
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("Feedback", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("always returns CONTINUE even with no result")
        void alwaysContinuesNoResult() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("always returns CONTINUE with OK result")
        void alwaysContinuesWithOk() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResult(ActionResult.ok(0));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("always returns CONTINUE with blocked result")
        void alwaysContinuesWithBlocked() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResult(ActionResult.blocked("TEST", "test reason"));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }
    }

    // =========================================================================
    // TelemetryStep
    // =========================================================================

    @Nested
    @DisplayName("TelemetryStep")
    class TelemetryStepTests {

        private List<String> lines;
        private TelemetryStep step;

        @BeforeEach
        void setUp() {
            lines = new ArrayList<>();
            step = new TelemetryStep(lines::add);
        }

        @Test
        @DisplayName("stepName is non-empty")
        void stepNameNonEmpty() {
            assertFalse(step.stepName().isEmpty());
            assertEquals("Telemetry", step.stepName());
        }

        @Test
        @DisplayName("process never returns null")
        void processNeverReturnsNull() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            StepResult result = step.process(ctx);
            assertNotNull(result);
        }

        @Test
        @DisplayName("always returns CONTINUE")
        void alwaysContinues() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            ctx.setResult(ActionResult.ok(5));
            StepResult result = step.process(ctx);
            assertTrue(result.shouldContinue());
        }

        @Test
        @DisplayName("logs telemetry line for OK result")
        void logsTelemetryForOk() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            ctx.setResult(ActionResult.ok(5));
            step.process(ctx);

            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("action_v2_invoked"));
            assertTrue(lines.get(0).contains("devmod.ui.test"));
        }

        @Test
        @DisplayName("logs telemetry line for blocked result")
        void logsTelemetryForBlocked() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            ctx.setResult(ActionResult.blocked("TEST_ERROR", "test"));
            step.process(ctx);

            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("action_v2_blocked"));
        }

        @Test
        @DisplayName("no-ops when result is null")
        void noOpWhenNullResult() {
            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            step.process(ctx);
            assertTrue(lines.isEmpty());
        }

        @Test
        @DisplayName("sink exception does not throw")
        void sinkExceptionDoesNotThrow() {
            TelemetryStep failStep = new TelemetryStep(line -> {
                throw new RuntimeException("sink failure");
            });

            ExecutionContext ctx = ctxWith("devmod.ui.test", radialContext);
            ctx.setResolvedSpec(makeClientSpec("devmod.ui.test"));
            ctx.setResult(ActionResult.ok(5));

            assertDoesNotThrow(() -> failStep.process(ctx));
        }
    }
}

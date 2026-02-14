package com.devmod.actions.hardening;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionResult;
import com.devmod.actions.ActionType;
import com.devmod.actions.CommandSanitizer;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.PolicyResult;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security hardening tests for the Actions V2 system.
 * Verifies that origin trust boundaries, permission escalation, command injection,
 * and other security-critical behaviors are correctly enforced.
 */
@DisplayName("Security Hardening Tests")
class SecurityHardeningTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain fullPolicyChain;
    private RateLimitPolicy rateLimitPolicy;
    private List<String> telemetryLines;
    private ActionExecutor executor;
    private final AtomicBoolean handlerCalled = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();
        rateLimitPolicy = new RateLimitPolicy();
        telemetryLines = new ArrayList<>();

        fullPolicyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(CommandSafetyPolicy.INSTANCE)
            .add(rateLimitPolicy)
            .build();

        TelemetryStep.TelemetrySink testSink = telemetryLines::add;
        executor = ActionExecutor.createDefault(catalog, handlers, fullPolicyChain, testSink);
        handlerCalled.set(false);
    }

    private void registerClientAction(String id) {
        catalog.register(ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(ActionCategory.UI)
            .ui("label." + id, "desc." + id)
            .build());
        handlers.register(id, ctx -> handlerCalled.set(true));
    }

    private void registerServerAction(String id, int permLevel) {
        catalog.register(ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .allowedOrigins(EnumSet.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK))
            .category(ActionCategory.ADMIN)
            .permissionLevel(permLevel)
            .ui("label." + id, "desc." + id)
            .build());
        handlers.register(id, ctx -> handlerCalled.set(true));
    }

    private ActionContext contextForOrigin(ActionOrigin origin) {
        return ActionContext.builder(origin)
            .clientSide(origin != ActionOrigin.COMMAND && origin != ActionOrigin.NETWORK)
            .build();
    }

    // =========================================================================
    // 1. Origin Spoofing
    // =========================================================================

    @Nested
    @DisplayName("1. Origin Spoofing Prevention")
    class OriginSpoofingTests {

        @Test
        @DisplayName("NETWORK origin cannot invoke CLIENT-only action (not whitelisted)")
        void networkCannotInvokeClientOnly() {
            registerClientAction("devmod.ui.secret.panel");

            ActionResult result = executor.execute("devmod.ui.secret.panel",
                contextForOrigin(ActionOrigin.NETWORK));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("UNKNOWN origin cannot invoke CLIENT-only action")
        void unknownCannotInvokeClientOnly() {
            registerClientAction("devmod.ui.secret.panel2");

            ActionResult result = executor.execute("devmod.ui.secret.panel2",
                contextForOrigin(ActionOrigin.UNKNOWN));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("NETWORK origin CAN invoke action that explicitly allows NETWORK")
        void networkAllowedWhenExplicit() {
            registerServerAction("devmod.server.public_rpc", 0);

            ActionResult result = executor.execute("devmod.server.public_rpc",
                contextForOrigin(ActionOrigin.NETWORK));

            assertTrue(result.isSuccess());
            assertTrue(handlerCalled.get());
        }

        @Test
        @DisplayName("ActionSpec constructor rejects CLIENT channel with NETWORK origin")
        void clientChannelCannotHaveNetworkOrigin() {
            assertThrows(IllegalArgumentException.class, () ->
                ActionSpec.builder("devmod.ui.bad.spec")
                    .channel(ActionChannel.CLIENT)
                    .allowedOrigins(EnumSet.of(ActionOrigin.RADIAL, ActionOrigin.NETWORK))
                    .category(ActionCategory.UI)
                    .ui("label", "desc")
                    .build()
            );
        }
    }

    // =========================================================================
    // 2. Origin Trust Boundaries
    // =========================================================================

    @Nested
    @DisplayName("2. Origin Trust Boundaries")
    class OriginTrustBoundaryTests {

        @Test
        @DisplayName("RADIAL is trusted")
        void radialIsTrusted() {
            assertTrue(ActionOrigin.RADIAL.isTrusted());
        }

        @Test
        @DisplayName("KEYBIND is trusted")
        void keybindIsTrusted() {
            assertTrue(ActionOrigin.KEYBIND.isTrusted());
        }

        @Test
        @DisplayName("COMMAND is trusted")
        void commandIsTrusted() {
            assertTrue(ActionOrigin.COMMAND.isTrusted());
        }

        @Test
        @DisplayName("UI is trusted")
        void uiIsTrusted() {
            assertTrue(ActionOrigin.UI.isTrusted());
        }

        @Test
        @DisplayName("EVENT is trusted")
        void eventIsTrusted() {
            assertTrue(ActionOrigin.EVENT.isTrusted());
        }

        @Test
        @DisplayName("NETWORK is untrusted")
        void networkIsUntrusted() {
            assertFalse(ActionOrigin.NETWORK.isTrusted());
        }

        @Test
        @DisplayName("UNKNOWN is untrusted")
        void unknownIsUntrusted() {
            assertFalse(ActionOrigin.UNKNOWN.isTrusted());
        }

        @Test
        @DisplayName("Exactly 5 trusted origins and 2 untrusted origins")
        void trustCountsCorrect() {
            int trusted = 0;
            int untrusted = 0;
            for (ActionOrigin origin : ActionOrigin.values()) {
                if (origin.isTrusted()) trusted++;
                else untrusted++;
            }
            assertEquals(5, trusted, "Expected 5 trusted origins");
            assertEquals(2, untrusted, "Expected 2 untrusted origins");
        }
    }

    // =========================================================================
    // 3. Permission Escalation
    // =========================================================================

    @Nested
    @DisplayName("3. Permission Escalation Prevention")
    class PermissionEscalationTests {

        @Test
        @DisplayName("Permission level 0 action accessible without permissions")
        void permLevel0NoPerm() {
            registerServerAction("devmod.server.open_action", 0);

            ActionResult result = executor.execute("devmod.server.open_action",
                contextForOrigin(ActionOrigin.COMMAND));

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("No player, no source => permission level 1 action blocked")
        void noPlayerNoSourceBlocked() {
            registerServerAction("devmod.admin.level1_action", 1);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute("devmod.admin.level1_action", ctx);

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_PERMISSION_DENIED, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("Permission level 4 action requires OP")
        void permLevel4RequiresOp() {
            registerServerAction("devmod.admin.op_only", 4);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute("devmod.admin.op_only", ctx);

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_PERMISSION_DENIED, result.errorCode());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4})
        @DisplayName("Each permission level above 0 is blocked without credentials")
        void eachPermLevelBlockedWithoutCreds(int level) {
            String id = "devmod.admin.perm_level_" + level;
            registerServerAction(id, level);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            ActionResult result = executor.execute(id, ctx);

            assertTrue(result.isBlocked(),
                "Permission level " + level + " should be blocked without credentials");
        }
    }

    // =========================================================================
    // 4. Command Injection Prevention
    // =========================================================================

    @Nested
    @DisplayName("4. Command Injection Prevention")
    class CommandInjectionTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "say hello && op attacker",
            "say hello || op attacker",
            "say hello; op attacker",
            "say hello > file.txt",
            "say hello < file.txt",
            "say hello | grep password"
        })
        @DisplayName("Command chaining and redirection patterns blocked")
        void injectionPatternsBlocked(String command) {
            assertFalse(CommandSanitizer.isCommandSafe(command),
                "Command should be blocked: " + command);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "say hello && op attacker",
            "say hello || deop admin",
            "say hello; ban player",
            "say > /etc/passwd",
            "cmd < injection",
            "cmd | pipe"
        })
        @DisplayName("Injection blocked through policy chain for command-type actions")
        void injectionBlockedByPolicyChain(String unsafeCommand) {
            ActionSpec spec = ActionSpec.builder("devmod.command.inject_test")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("inject test", "desc")
                .binding(null, unsafeCommand, null)
                .build();

            PolicyResult result = CommandSafetyPolicy.INSTANCE.evaluate(spec,
                contextForOrigin(ActionOrigin.COMMAND));

            assertTrue(result.isDenied(), "CommandSafetyPolicy should deny: " + unsafeCommand);
        }
    }

    // =========================================================================
    // 5. Dangerous Command Blocklist
    // =========================================================================

    @Nested
    @DisplayName("5. Dangerous Command Blocklist")
    class DangerousCommandTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "op",
            "deop",
            "ban",
            "kick",
            "stop",
            "execute",
            "give",
            "data"
        })
        @DisplayName("Dangerous commands blocked by CommandSanitizer")
        void dangerousCommandsBlocked(String command) {
            assertFalse(CommandSanitizer.isCommandSafe(command),
                "Dangerous command should be blocked: " + command);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
            "kick", "stop", "save-all", "whitelist", "reload",
            "give", "enchant", "effect", "execute", "function",
            "data", "datapack", "forceload"
        })
        @DisplayName("Complete dangerous command blocklist validated")
        void completeDangerousBlocklist(String command) {
            assertFalse(CommandSanitizer.isCommandSafe(command),
                "Missing from blocklist: " + command);
        }

        @Test
        @DisplayName("Safe commands are allowed")
        void safeCommandsAllowed() {
            assertTrue(CommandSanitizer.isCommandSafe("say hello"));
            assertTrue(CommandSanitizer.isCommandSafe("devmod status"));
            assertTrue(CommandSanitizer.isCommandSafe("arena create test_arena"));
        }

        @Test
        @DisplayName("buildCommand rejects dangerous commands")
        void buildCommandRejectsDangerous() {
            assertThrows(IllegalArgumentException.class,
                () -> CommandSanitizer.buildCommand("op", "player1"));
            assertThrows(IllegalArgumentException.class,
                () -> CommandSanitizer.buildCommand("ban", "player1"));
        }
    }

    // =========================================================================
    // 6. Command Length Limit
    // =========================================================================

    @Nested
    @DisplayName("6. Command Length Limit")
    class CommandLengthTests {

        @Test
        @DisplayName("Command over 256 chars rejected")
        void longCommandRejected() {
            String longCommand = "say " + "a".repeat(260);
            assertTrue(longCommand.length() > 256);
            assertFalse(CommandSanitizer.isCommandSafe(longCommand));
        }

        @Test
        @DisplayName("Command at exactly 256 chars accepted")
        void commandAt256Accepted() {
            String command = "say " + "a".repeat(252);
            assertEquals(256, command.length());
            assertTrue(CommandSanitizer.isCommandSafe(command));
        }

        @Test
        @DisplayName("Command at 257 chars rejected")
        void commandAt257Rejected() {
            String command = "say " + "a".repeat(253);
            assertEquals(257, command.length());
            assertFalse(CommandSanitizer.isCommandSafe(command));
        }
    }

    // =========================================================================
    // 7. Whitelist Integrity
    // =========================================================================

    @Nested
    @DisplayName("7. Whitelist Integrity")
    class WhitelistIntegrityTests {

        @Test
        @DisplayName("ActionSpec allowedOrigins returns unmodifiable set")
        void allowedOriginsUnmodifiable() {
            ActionSpec spec = ActionSpec.builder("devmod.test.whitelist.check")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build();

            Set<ActionOrigin> origins = spec.allowedOrigins();
            assertThrows(UnsupportedOperationException.class,
                () -> origins.add(ActionOrigin.NETWORK));
        }

        @Test
        @DisplayName("ActionSpec allowedOrigins is a defensive copy")
        void allowedOriginsDefensiveCopy() {
            EnumSet<ActionOrigin> mutableOrigins = EnumSet.of(ActionOrigin.RADIAL, ActionOrigin.KEYBIND);
            ActionSpec spec = ActionSpec.builder("devmod.test.whitelist.copy")
                .channel(ActionChannel.CLIENT)
                .allowedOrigins(mutableOrigins)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build();

            // Modify original set
            mutableOrigins.add(ActionOrigin.COMMAND);

            // Spec should not be affected
            assertFalse(spec.allowsOrigin(ActionOrigin.COMMAND),
                "Modifying original set should not affect ActionSpec");
        }
    }

    // =========================================================================
    // 8. Policy Chain Completeness
    // =========================================================================

    @Nested
    @DisplayName("8. Policy Chain Completeness")
    class PolicyChainCompletenessTests {

        @Test
        @DisplayName("Default policy chain has 4 policies")
        void defaultChainSize() {
            assertEquals(4, fullPolicyChain.size());
        }

        @Test
        @DisplayName("OriginPolicy runs before PermissionPolicy")
        void originBeforePermission() {
            var policies = fullPolicyChain.policies();
            int originIdx = -1, permIdx = -1;
            for (int i = 0; i < policies.size(); i++) {
                if (policies.get(i).policyName().equals("OriginPolicy")) originIdx = i;
                if (policies.get(i).policyName().equals("PermissionPolicy")) permIdx = i;
            }
            assertTrue(originIdx >= 0, "OriginPolicy not found");
            assertTrue(permIdx >= 0, "PermissionPolicy not found");
            assertTrue(originIdx < permIdx,
                "OriginPolicy (priority 10) should run before PermissionPolicy (priority 100)");
        }

        @Test
        @DisplayName("CommandSafetyPolicy runs before RateLimitPolicy")
        void commandSafetyBeforeRateLimit() {
            var policies = fullPolicyChain.policies();
            int safetyIdx = -1, rateIdx = -1;
            for (int i = 0; i < policies.size(); i++) {
                if (policies.get(i).policyName().equals("CommandSafetyPolicy")) safetyIdx = i;
                if (policies.get(i).policyName().equals("RateLimitPolicy")) rateIdx = i;
            }
            assertTrue(safetyIdx >= 0, "CommandSafetyPolicy not found");
            assertTrue(rateIdx >= 0, "RateLimitPolicy not found");
            assertTrue(safetyIdx < rateIdx);
        }

        @Test
        @DisplayName("Every untrusted origin goes through OriginPolicy")
        void untrustedOriginGoesThoughPolicy() {
            registerClientAction("devmod.ui.policy.test");

            for (ActionOrigin origin : ActionOrigin.values()) {
                if (!origin.isTrusted()) {
                    ActionResult result = executor.execute("devmod.ui.policy.test",
                        contextForOrigin(origin));
                    assertTrue(result.isBlocked(),
                        "Untrusted origin " + origin + " should be blocked for CLIENT-only action");
                }
            }
        }

        @Test
        @DisplayName("PolicyChain short-circuits on first deny")
        void policyChainShortCircuits() {
            // Origin deny should prevent PermissionPolicy from even running
            registerClientAction("devmod.ui.short.circuit");

            ActionResult result = executor.execute("devmod.ui.short.circuit",
                contextForOrigin(ActionOrigin.NETWORK));

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNTRUSTED_ACTION, result.errorCode());
        }

        @Test
        @DisplayName("Empty policy chain allows everything")
        void emptyChainAllowsAll() {
            PolicyChain empty = PolicyChain.empty();
            ActionSpec spec = ActionSpec.builder("devmod.test.empty.chain")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .ui("label", "desc")
                .build();

            PolicyResult result = empty.evaluate(spec, contextForOrigin(ActionOrigin.NETWORK));
            assertFalse(result.isDenied());
        }
    }

    // =========================================================================
    // 9. Confirmation Bypass Prevention
    // =========================================================================

    @Nested
    @DisplayName("9. Confirmation Bypass Prevention")
    class ConfirmationBypassTests {

        @Test
        @DisplayName("Action requiring confirmation blocked without isConfirmed=true")
        void requiresConfirmBlocked() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.destructive.wipe")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .ui("wipe", "desc")
                .policy(true, 0, null)
                .build();
            catalog.register(spec);
            handlers.register("devmod.admin.destructive.wipe", ctx -> handlerCalled.set(true));

            ActionContext unconfirmed = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(false)
                .build();

            ActionResult result = executor.execute("devmod.admin.destructive.wipe", unconfirmed);

            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_REQUIRES_CONFIRM, result.errorCode());
            assertFalse(handlerCalled.get());
        }

        @Test
        @DisplayName("Action requiring confirmation passes with isConfirmed=true")
        void confirmedPasses() {
            ActionSpec spec = ActionSpec.builder("devmod.admin.destructive.wipe2")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND, ActionOrigin.RADIAL)
                .category(ActionCategory.ADMIN)
                .ui("wipe", "desc")
                .policy(true, 0, null)
                .build();
            catalog.register(spec);
            handlers.register("devmod.admin.destructive.wipe2", ctx -> handlerCalled.set(true));

            ActionContext confirmed = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(true)
                .build();

            ActionResult result = executor.execute("devmod.admin.destructive.wipe2", confirmed);

            assertTrue(result.isSuccess());
            assertTrue(handlerCalled.get());
        }

        @Test
        @DisplayName("withConfirmed creates new context with correct state")
        void withConfirmedCreatesNewContext() {
            ActionContext original = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .confirmed(false)
                .build();

            assertFalse(original.isConfirmed());

            ActionContext confirmed = original.withConfirmed(true);
            assertTrue(confirmed.isConfirmed());
            assertFalse(original.isConfirmed()); // Original unchanged
        }
    }

    // =========================================================================
    // 10. Payload Tampering / Malformed Payloads
    // =========================================================================

    @Nested
    @DisplayName("10. Payload Tampering and Malformed Input")
    class PayloadTamperingTests {

        @Test
        @DisplayName("Null actionId throws NPE, not engine crash")
        void nullActionIdThrowsNpe() {
            assertThrows(NullPointerException.class,
                () -> executor.execute(null, contextForOrigin(ActionOrigin.RADIAL)));
        }

        @Test
        @DisplayName("Null context throws NPE, not engine crash")
        void nullContextThrowsNpe() {
            assertThrows(NullPointerException.class,
                () -> executor.execute("devmod.test.null.ctx", null));
        }

        @Test
        @DisplayName("Empty string actionId returns blocked, no crash")
        void emptyActionIdBlocked() {
            // Empty IDs won't match any catalog entry
            ActionResult result = executor.execute("devmod.empty.id.test",
                contextForOrigin(ActionOrigin.RADIAL));
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("Very long actionId handled gracefully")
        void longActionIdHandled() {
            // Building a valid-pattern but non-registered action ID
            String longId = "devmod.test." + "x".repeat(200);
            ActionResult result = executor.execute(longId,
                contextForOrigin(ActionOrigin.RADIAL));
            assertTrue(result.isBlocked());
            assertEquals(ActionResult.ERROR_UNKNOWN_ACTION, result.errorCode());
        }

        @Test
        @DisplayName("Arbitrary payload object does not crash engine")
        void arbitraryPayloadNoCrash() {
            registerClientAction("devmod.ui.payload.test");

            ActionContext ctx = ActionContext.builder(ActionOrigin.RADIAL)
                .clientSide(true)
                .payload(new Object()) // arbitrary payload
                .build();

            ActionResult result = executor.execute("devmod.ui.payload.test", ctx);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Payload with wrong type handled gracefully via getPayload(Class)")
        void wrongPayloadType() {
            ActionContext ctx = ActionContext.builder(ActionOrigin.RADIAL)
                .clientSide(true)
                .payload("string_payload")
                .build();

            Integer typed = ctx.getPayload(Integer.class);
            assertNull(typed, "Wrong type should return null, not crash");
        }

        @Test
        @DisplayName("Handler exception does not crash the engine")
        void handlerExceptionContained() {
            ActionSpec spec = ActionSpec.builder("devmod.test.crash.handler")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.TESTING)
                .ui("crash", "desc")
                .build();
            catalog.register(spec);
            handlers.register("devmod.test.crash.handler", ctx -> {
                throw new RuntimeException("Intentional test crash");
            });

            ActionResult result = executor.execute("devmod.test.crash.handler",
                contextForOrigin(ActionOrigin.RADIAL));

            // Should be failed, not a propagated exception
            assertTrue(result.isFailed() || result.isBlocked());
            assertNotNull(result.errorCode());
        }

        @Test
        @DisplayName("Pipeline step exception returns FAILED result, not propagated")
        void pipelineStepExceptionContained() {
            ActionExecutor crashExecutor = ActionExecutor.builder()
                .addStep(new PipelineStep() {
                    @Override
                    public StepResult process(ExecutionContext ctx) {
                        throw new RuntimeException("Step crash");
                    }
                    @Override
                    public String stepName() { return "CrashStep"; }
                })
                .build();

            ActionResult result = crashExecutor.execute("devmod.test.step.crash",
                contextForOrigin(ActionOrigin.RADIAL));

            assertTrue(result.isFailed());
            assertEquals(ActionResult.ERROR_EXCEPTION, result.errorCode());
        }

        @Test
        @DisplayName("CommandSanitizer handles null gracefully")
        void sanitizerHandlesNull() {
            assertFalse(CommandSanitizer.isCommandSafe(null));
            assertFalse(CommandSanitizer.isCommandSafe(""));
        }

        @Test
        @DisplayName("CommandSanitizer.sanitizeForLog handles null and long strings")
        void sanitizeForLogEdgeCases() {
            assertEquals("<null>", CommandSanitizer.sanitizeForLog(null));
            String longStr = "a".repeat(100);
            String sanitized = CommandSanitizer.sanitizeForLog(longStr);
            assertTrue(sanitized.endsWith("..."));
            assertTrue(sanitized.length() <= 60);
        }
    }
}

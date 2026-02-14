package com.devmod.actions.policy;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PolicyChain and individual policy implementations.
 */
@DisplayName("PolicyChain - Policy Evaluation Pipeline")
class PolicyChainTest {

    // =========================================================================
    // Helper: create ActionSpec for testing
    // =========================================================================

    private static ActionSpec testSpec(String id) {
        return ActionSpec.builder(id)
            .category(ActionCategory.UI)
            .ui("label", "desc")
            .build();
    }

    private static ActionSpec testSpec(String id, ActionChannel channel,
                                        ActionOrigin... allowedOrigins) {
        return ActionSpec.builder(id)
            .channel(channel)
            .allowedOrigins(allowedOrigins)
            .category(ActionCategory.UI)
            .ui("label", "desc")
            .build();
    }

    private static ActionSpec testSpecWithPermission(String id, int permLevel) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .permissionLevel(permLevel)
            .category(ActionCategory.ADMIN)
            .ui("label", "desc")
            .build();
    }

    private static ActionContext testContext(ActionOrigin origin) {
        return ActionContext.builder(origin)
            .clientSide(origin != ActionOrigin.COMMAND && origin != ActionOrigin.NETWORK)
            .build();
    }

    // =========================================================================
    // PolicyChain behavior tests
    // =========================================================================

    @Nested
    @DisplayName("Chain Behavior")
    class ChainBehaviorTests {

        @Test
        @DisplayName("Empty chain allows by default")
        void emptyChainAllows() {
            PolicyChain chain = PolicyChain.empty();
            ActionSpec spec = testSpec("devmod.test.action");
            ActionContext ctx = testContext(ActionOrigin.RADIAL);

            PolicyResult result = chain.evaluate(spec, ctx);
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Single allow policy passes")
        void singleAllowPolicy() {
            PolicyChain chain = PolicyChain.builder()
                .add(new AlwaysAllowPolicy())
                .build();

            PolicyResult result = chain.evaluate(
                testSpec("devmod.test.action"),
                testContext(ActionOrigin.RADIAL)
            );

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Single deny policy blocks")
        void singleDenyPolicy() {
            PolicyChain chain = PolicyChain.builder()
                .add(new AlwaysDenyPolicy())
                .build();

            PolicyResult result = chain.evaluate(
                testSpec("devmod.test.action"),
                testContext(ActionOrigin.RADIAL)
            );

            assertTrue(result.isDenied());
            assertEquals("AlwaysDeny", result.policyName());
        }

        @Test
        @DisplayName("Chain short-circuits on first deny")
        void chainShortCircuitsOnDeny() {
            CountingPolicy counter = new CountingPolicy();

            PolicyChain chain = PolicyChain.builder()
                .add(new AlwaysDenyPolicy()) // priority 0 - will deny
                .add(counter)                // priority 500 - should not be reached
                .build();

            chain.evaluate(
                testSpec("devmod.test.action"),
                testContext(ActionOrigin.RADIAL)
            );

            assertEquals(0, counter.callCount, "Counter policy should not have been called");
        }

        @Test
        @DisplayName("All allow policies pass through")
        void allAllowPoliciesPass() {
            CountingPolicy counter = new CountingPolicy();

            PolicyChain chain = PolicyChain.builder()
                .add(new AlwaysAllowPolicy())
                .add(counter)
                .build();

            PolicyResult result = chain.evaluate(
                testSpec("devmod.test.action"),
                testContext(ActionOrigin.RADIAL)
            );

            assertTrue(result.allowed());
            assertEquals(1, counter.callCount, "Counter policy should have been called");
        }

        @Test
        @DisplayName("Policies are sorted by priority")
        void policiesSortedByPriority() {
            StringBuilder order = new StringBuilder();

            PolicyChain chain = PolicyChain.builder()
                .add(new TrackingPolicy("C", 300, order))
                .add(new TrackingPolicy("A", 100, order))
                .add(new TrackingPolicy("B", 200, order))
                .build();

            chain.evaluate(
                testSpec("devmod.test.action"),
                testContext(ActionOrigin.RADIAL)
            );

            assertEquals("A,B,C,", order.toString());
        }

        @Test
        @DisplayName("Chain size reflects number of policies")
        void chainSizeReflectsCount() {
            PolicyChain chain = PolicyChain.builder()
                .add(new AlwaysAllowPolicy())
                .add(new AlwaysAllowPolicy())
                .add(new AlwaysAllowPolicy())
                .build();

            assertEquals(3, chain.size());
        }
    }

    // =========================================================================
    // OriginPolicy tests
    // =========================================================================

    @Nested
    @DisplayName("OriginPolicy")
    class OriginPolicyTests {

        @Test
        @DisplayName("Allows matching trusted origin")
        void allowsTrustedOrigin() {
            ActionSpec spec = testSpec("devmod.ui.open",
                ActionChannel.CLIENT, ActionOrigin.RADIAL, ActionOrigin.KEYBIND);

            PolicyResult result = OriginPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.RADIAL));

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Denies untrusted origin when not in allowed set")
        void deniesUntrustedOrigin() {
            ActionSpec spec = testSpec("devmod.ui.open",
                ActionChannel.CLIENT, ActionOrigin.RADIAL);

            PolicyResult result = OriginPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.KEYBIND));

            assertTrue(result.isDenied());
            assertTrue(result.reason().contains("devmod.ui.open"));
            assertEquals("OriginPolicy", result.policyName());
        }

        @Test
        @DisplayName("Denies NETWORK origin when not explicitly allowed")
        void deniesNetworkWhenNotAllowed() {
            ActionSpec spec = ActionSpec.builder("devmod.server.action")
                .channel(ActionChannel.SERVER)
                .allowedOrigins(ActionOrigin.COMMAND)
                .category(ActionCategory.ADMIN)
                .ui("l", "d")
                .build();

            PolicyResult result = OriginPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.NETWORK));

            assertTrue(result.isDenied());
        }

        @Test
        @DisplayName("Allows NETWORK origin when explicitly allowed")
        void allowsNetworkWhenAllowed() {
            ActionSpec spec = ActionSpec.builder("devmod.rpc.action")
                .channel(ActionChannel.BOTH)
                .allowedOrigins(ActionOrigin.NETWORK, ActionOrigin.RADIAL)
                .category(ActionCategory.MISC)
                .ui("l", "d")
                .build();

            PolicyResult result = OriginPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.NETWORK));

            assertTrue(result.allowed());
        }
    }

    // =========================================================================
    // PermissionPolicy tests
    // =========================================================================

    @Nested
    @DisplayName("PermissionPolicy")
    class PermissionPolicyTests {

        @Test
        @DisplayName("Permission level 0 always allows")
        void permissionZeroAllows() {
            ActionSpec spec = testSpec("devmod.ui.action");

            PolicyResult result = PermissionPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.RADIAL));

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Denies when no player and no source with perm > 0")
        void deniesWhenNoPlayerAndPermRequired() {
            ActionSpec spec = testSpecWithPermission("devmod.admin.action", 2);

            ActionContext ctx = ActionContext.builder(ActionOrigin.COMMAND)
                .clientSide(false)
                .build();

            PolicyResult result = PermissionPolicy.INSTANCE.evaluate(spec, ctx);

            assertTrue(result.isDenied());
            assertTrue(result.reason().contains("permission level 2"));
            assertEquals("PermissionPolicy", result.policyName());
        }
    }

    // =========================================================================
    // CommandSafetyPolicy tests
    // =========================================================================

    @Nested
    @DisplayName("CommandSafetyPolicy")
    class CommandSafetyPolicyTests {

        @Test
        @DisplayName("Allows non-command action types")
        void allowsNonCommandTypes() {
            ActionSpec spec = ActionSpec.builder("devmod.ui.action")
                .category(ActionCategory.UI)
                .actionType(ActionType.NAVIGATE_SCREEN)
                .ui("l", "d")
                .build();

            PolicyResult result = CommandSafetyPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.RADIAL));

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Allows safe commands")
        void allowsSafeCommands() {
            ActionSpec spec = ActionSpec.builder("devmod.command.time_day")
                .channel(ActionChannel.SERVER)
                .category(ActionCategory.TOOLS)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("l", "d")
                .binding(null, "time set day", null)
                .build();

            PolicyResult result = CommandSafetyPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.COMMAND));

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Blocks dangerous commands")
        void blocksDangerousCommands() {
            ActionSpec spec = ActionSpec.builder("devmod.command.dangerous")
                .channel(ActionChannel.SERVER)
                .category(ActionCategory.ADMIN)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("l", "d")
                .binding(null, "op @a", null)
                .build();

            PolicyResult result = CommandSafetyPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.COMMAND));

            assertTrue(result.isDenied());
            assertEquals("CommandSafetyPolicy", result.policyName());
        }

        @Test
        @DisplayName("Allows command action with no command hint")
        void allowsCommandActionWithNoHint() {
            ActionSpec spec = ActionSpec.builder("devmod.command.nohint")
                .channel(ActionChannel.SERVER)
                .category(ActionCategory.TOOLS)
                .actionType(ActionType.RUN_SERVER_COMMAND)
                .ui("l", "d")
                .build();

            PolicyResult result = CommandSafetyPolicy.INSTANCE.evaluate(spec,
                testContext(ActionOrigin.COMMAND));

            assertTrue(result.allowed());
        }
    }

    // =========================================================================
    // Integration test: full chain
    // =========================================================================

    @Nested
    @DisplayName("Full Chain Integration")
    class FullChainTests {

        @Test
        @DisplayName("Standard chain allows valid action")
        void standardChainAllowsValid() {
            PolicyChain chain = PolicyChain.builder()
                .add(OriginPolicy.INSTANCE)
                .add(PermissionPolicy.INSTANCE)
                .add(CommandSafetyPolicy.INSTANCE)
                .build();

            ActionSpec spec = ActionSpec.builder("devmod.ui.settings.open")
                .channel(ActionChannel.CLIENT)
                .category(ActionCategory.UI)
                .actionType(ActionType.NAVIGATE_SCREEN)
                .ui("settings", "open settings")
                .build();

            PolicyResult result = chain.evaluate(spec, testContext(ActionOrigin.RADIAL));
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Standard chain blocks wrong origin first")
        void standardChainBlocksOriginFirst() {
            PolicyChain chain = PolicyChain.builder()
                .add(OriginPolicy.INSTANCE)
                .add(PermissionPolicy.INSTANCE)
                .add(CommandSafetyPolicy.INSTANCE)
                .build();

            ActionSpec spec = ActionSpec.builder("devmod.ui.settings.open")
                .channel(ActionChannel.CLIENT)
                .allowedOrigins(ActionOrigin.RADIAL)
                .category(ActionCategory.UI)
                .ui("settings", "desc")
                .build();

            PolicyResult result = chain.evaluate(spec, testContext(ActionOrigin.KEYBIND));
            assertTrue(result.isDenied());
            assertEquals("OriginPolicy", result.policyName());
        }
    }

    // =========================================================================
    // PolicyResult tests
    // =========================================================================

    @Nested
    @DisplayName("PolicyResult")
    class PolicyResultTests {

        @Test
        @DisplayName("allow() returns cached instance")
        void allowReturnsCached() {
            assertSame(PolicyResult.allow(), PolicyResult.allow());
        }

        @Test
        @DisplayName("deny() creates new instance with reason")
        void denyCreatesNew() {
            PolicyResult result = PolicyResult.deny("test reason", "TestPolicy");
            assertTrue(result.isDenied());
            assertFalse(result.allowed());
            assertEquals("test reason", result.reason());
            assertEquals("TestPolicy", result.policyName());
        }
    }

    // =========================================================================
    // Test helper policies
    // =========================================================================

    private static class AlwaysAllowPolicy implements ActionPolicy {
        @Override
        public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
            return PolicyResult.allow();
        }

        @Override
        public String policyName() {
            return "AlwaysAllow";
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    private static class AlwaysDenyPolicy implements ActionPolicy {
        @Override
        public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
            return PolicyResult.deny("Always denied", "AlwaysDeny");
        }

        @Override
        public String policyName() {
            return "AlwaysDeny";
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    private static class CountingPolicy implements ActionPolicy {
        int callCount = 0;

        @Override
        public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
            callCount++;
            return PolicyResult.allow();
        }

        @Override
        public String policyName() {
            return "CountingPolicy";
        }

        @Override
        public int priority() {
            return 500;
        }
    }

    private static class TrackingPolicy implements ActionPolicy {
        private final String name;
        private final int prio;
        private final StringBuilder tracker;

        TrackingPolicy(String name, int prio, StringBuilder tracker) {
            this.name = name;
            this.prio = prio;
            this.tracker = tracker;
        }

        @Override
        public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
            tracker.append(name).append(",");
            return PolicyResult.allow();
        }

        @Override
        public String policyName() {
            return name;
        }

        @Override
        public int priority() {
            return prio;
        }
    }
}

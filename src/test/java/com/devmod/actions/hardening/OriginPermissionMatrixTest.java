package com.devmod.actions.hardening;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.policy.CommandSafetyPolicy;
import com.devmod.actions.policy.OriginPolicy;
import com.devmod.actions.policy.PermissionPolicy;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.PolicyResult;
import com.devmod.actions.policy.RateLimitPolicy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full matrix test covering all combinations of ActionOrigin, permission levels,
 * trusted/untrusted, and client-safe/not.
 *
 * <p>Generates a human-readable matrix output showing the authorization grid.
 */
@DisplayName("Origin x Permission Matrix Tests")
class OriginPermissionMatrixTest {

    private ActionCatalog catalog;
    private HandlerRegistry handlers;
    private PolicyChain fullPolicyChain;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        catalog = new ActionCatalog();
        handlers = new HandlerRegistry();

        fullPolicyChain = PolicyChain.builder()
            .add(OriginPolicy.INSTANCE)
            .add(PermissionPolicy.INSTANCE)
            .add(CommandSafetyPolicy.INSTANCE)
            .add(new RateLimitPolicy())
            .build();

        executor = ActionExecutor.createDefault(catalog, handlers, fullPolicyChain, line -> {});
    }

    private ActionContext contextForOrigin(ActionOrigin origin) {
        return ActionContext.builder(origin)
            .clientSide(origin != ActionOrigin.COMMAND && origin != ActionOrigin.NETWORK)
            .build();
    }

    // =========================================================================
    // Origin x Channel Matrix
    // =========================================================================

    @Nested
    @DisplayName("Origin x Channel Matrix")
    class OriginChannelMatrix {

        @Test
        @DisplayName("All trusted origins allowed for CLIENT-only actions")
        void trustedOriginsAllowedForClient() {
            for (ActionOrigin origin : ActionOrigin.values()) {
                if (!origin.isTrusted()) continue;

                String id = "devmod.matrix.client_" + origin.name().toLowerCase();
                catalog.register(ActionSpec.builder(id)
                    .channel(ActionChannel.CLIENT)
                    .category(ActionCategory.UI)
                    .ui("label", "desc")
                    .build());
                handlers.register(id, ctx -> {});

                ActionResult result = executor.execute(id, contextForOrigin(origin));
                assertTrue(result.isSuccess(),
                    origin + " should be allowed for CLIENT action but got: " + result.errorCode());
            }
        }

        @Test
        @DisplayName("All untrusted origins blocked for CLIENT-only actions")
        void untrustedOriginsBlockedForClient() {
            for (ActionOrigin origin : ActionOrigin.values()) {
                if (origin.isTrusted()) continue;

                String id = "devmod.matrix.client_block_" + origin.name().toLowerCase();
                catalog.register(ActionSpec.builder(id)
                    .channel(ActionChannel.CLIENT)
                    .category(ActionCategory.UI)
                    .ui("label", "desc")
                    .build());
                handlers.register(id, ctx -> {});

                ActionResult result = executor.execute(id, contextForOrigin(origin));
                assertTrue(result.isBlocked(),
                    origin + " should be blocked for CLIENT action");
            }
        }

        @Test
        @DisplayName("NETWORK origin allowed for SERVER action with NETWORK in allowedOrigins")
        void networkAllowedForServer() {
            String id = "devmod.matrix.server_network_ok";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.SERVER)
                .allowedOrigins(EnumSet.of(
                    ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                    ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK))
                .category(ActionCategory.ADMIN)
                .ui("label", "desc")
                .build());
            handlers.register(id, ctx -> {});

            ActionResult result = executor.execute(id, contextForOrigin(ActionOrigin.NETWORK));
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("NETWORK origin blocked for SERVER action without NETWORK in allowedOrigins")
        void networkBlockedWithoutExplicit() {
            String id = "devmod.matrix.server_network_denied";
            catalog.register(ActionSpec.builder(id)
                .channel(ActionChannel.SERVER)
                .trustedOriginsOnly()
                .category(ActionCategory.ADMIN)
                .ui("label", "desc")
                .build());
            handlers.register(id, ctx -> {});

            ActionResult result = executor.execute(id, contextForOrigin(ActionOrigin.NETWORK));
            assertTrue(result.isBlocked());
        }
    }

    // =========================================================================
    // Origin x Permission Level Matrix
    // =========================================================================

    @Nested
    @DisplayName("Origin x Permission Level Matrix")
    class OriginPermissionLevelMatrix {

        @Test
        @DisplayName("Permission level 0 accessible from all allowed origins (no perm check)")
        void permLevel0AllOrigins() {
            for (ActionOrigin origin : ActionOrigin.values()) {
                if (!origin.isTrusted()) continue;

                String id = "devmod.matrix.perm0_" + origin.name().toLowerCase();
                catalog.register(ActionSpec.builder(id)
                    .channel(ActionChannel.CLIENT)
                    .category(ActionCategory.UI)
                    .permissionLevel(0)
                    .ui("label", "desc")
                    .build());
                handlers.register(id, ctx -> {});

                ActionResult result = executor.execute(id, contextForOrigin(origin));
                assertTrue(result.isSuccess(),
                    "Perm 0 + " + origin + " should succeed");
            }
        }

        @Test
        @DisplayName("Permission levels 1-4 blocked without credentials for all origins")
        void permLevels1to4BlockedNoCreds() {
            for (int level = 1; level <= 4; level++) {
                for (ActionOrigin origin : ActionOrigin.values()) {
                    if (!origin.isTrusted()) continue;

                    String id = "devmod.matrix.perm" + level + "_" + origin.name().toLowerCase();
                    catalog.register(ActionSpec.builder(id)
                        .channel(ActionChannel.SERVER)
                        .allowedOrigins(EnumSet.allOf(ActionOrigin.class))
                        .category(ActionCategory.ADMIN)
                        .permissionLevel(level)
                        .ui("label", "desc")
                        .build());
                    handlers.register(id, ctx -> {});

                    // Context without player/source = no permissions
                    ActionContext ctx = ActionContext.builder(origin)
                        .clientSide(false)
                        .build();

                    ActionResult result = executor.execute(id, ctx);
                    assertTrue(result.isBlocked(),
                        "Perm " + level + " + " + origin + " without creds should be blocked");
                    assertEquals(ActionResult.ERROR_PERMISSION_DENIED, result.errorCode());
                }
            }
        }
    }

    // =========================================================================
    // Full Matrix Output
    // =========================================================================

    @Nested
    @DisplayName("Full Authorization Grid Output")
    class FullMatrixOutput {

        @Test
        @DisplayName("Generate human-readable matrix of all origin x permission x channel combinations")
        void generateFullMatrix() {
            StringBuilder matrix = new StringBuilder();
            matrix.append("\n=== AUTHORIZATION MATRIX ===\n");
            matrix.append(String.format("%-10s %-8s %-8s %-6s %s%n",
                "Origin", "Channel", "PermLvl", "Result", "ErrorCode"));
            matrix.append("-".repeat(60)).append("\n");

            ActionChannel[] channels = { ActionChannel.CLIENT, ActionChannel.SERVER };
            int[] permLevels = { 0, 1, 2, 3, 4 };
            int totalTests = 0;
            int totalAllowed = 0;
            int totalDenied = 0;

            for (ActionOrigin origin : ActionOrigin.values()) {
                for (ActionChannel channel : channels) {
                    for (int perm : permLevels) {
                        // Skip invalid combinations (CLIENT + NETWORK origin)
                        if (channel == ActionChannel.CLIENT && !origin.isTrusted()
                            && origin != ActionOrigin.UNKNOWN) {
                            // CLIENT + NETWORK is structurally impossible in ActionSpec
                            // but we test what happens when invoked
                        }

                        String id = "devmod.matrix.full_" + origin.name().toLowerCase()
                            + "_" + channel.name().toLowerCase() + "_" + perm;

                        try {
                            ActionSpec.Builder builder = ActionSpec.builder(id)
                                .channel(channel)
                                .category(ActionCategory.UI)
                                .permissionLevel(perm)
                                .ui("label", "desc");

                            if (channel == ActionChannel.SERVER) {
                                builder.allowedOrigins(EnumSet.allOf(ActionOrigin.class));
                            }

                            catalog.register(builder.build());
                            handlers.register(id, ctx -> {});

                            ActionContext ctx = ActionContext.builder(origin)
                                .clientSide(channel == ActionChannel.CLIENT)
                                .build();

                            ActionResult result = executor.execute(id, ctx);

                            String status = result.isSuccess() ? "ALLOW" : "DENY";
                            String errorCode = result.errorCode() != null ? result.errorCode() : "-";

                            matrix.append(String.format("%-10s %-8s %-8d %-6s %s%n",
                                origin, channel, perm, status, errorCode));

                            totalTests++;
                            if (result.isSuccess()) totalAllowed++;
                            else totalDenied++;

                        } catch (IllegalArgumentException e) {
                            // Invalid spec combination (e.g., CLIENT + NETWORK origin)
                            matrix.append(String.format("%-10s %-8s %-8d %-6s %s%n",
                                origin, channel, perm, "N/A", "Invalid combo"));
                            totalTests++;
                        }
                    }
                }
            }

            matrix.append("-".repeat(60)).append("\n");
            matrix.append(String.format("Total: %d tests, %d allowed, %d denied%n",
                totalTests, totalAllowed, totalDenied));

            System.out.println(matrix);

            assertTrue(totalTests > 0, "Matrix should have tests");
            assertTrue(totalDenied > 0, "Matrix should have denials");
            assertTrue(totalAllowed > 0, "Matrix should have allowances");
        }
    }

    // =========================================================================
    // OriginPolicy Direct Evaluation Matrix
    // =========================================================================

    @Nested
    @DisplayName("OriginPolicy Direct Evaluation")
    class OriginPolicyDirectTests {

        @Test
        @DisplayName("OriginPolicy correctly evaluates all origin x allowedOrigins combinations")
        void originPolicyMatrix() {
            for (ActionOrigin invokeOrigin : ActionOrigin.values()) {
                for (ActionOrigin allowedOrigin : ActionOrigin.values()) {
                    Set<ActionOrigin> allowed = EnumSet.of(allowedOrigin);

                    // Skip CLIENT + NETWORK combination
                    if (allowedOrigin == ActionOrigin.NETWORK) {
                        ActionSpec spec = ActionSpec.builder("devmod.matrix.origin_direct_"
                            + invokeOrigin.name().toLowerCase() + "_" + allowedOrigin.name().toLowerCase())
                            .channel(ActionChannel.SERVER)
                            .allowedOrigins(allowed)
                            .category(ActionCategory.UI)
                            .ui("label", "desc")
                            .build();

                        PolicyResult result = OriginPolicy.INSTANCE.evaluate(
                            spec, contextForOrigin(invokeOrigin));

                        if (invokeOrigin == allowedOrigin) {
                            assertFalse(result.isDenied(),
                                invokeOrigin + " invoking with " + allowedOrigin + " allowed should pass");
                        } else {
                            assertTrue(result.isDenied(),
                                invokeOrigin + " invoking with only " + allowedOrigin + " allowed should fail");
                        }
                    } else {
                        ActionSpec spec = ActionSpec.builder("devmod.matrix.origin_direct_"
                            + invokeOrigin.name().toLowerCase() + "_" + allowedOrigin.name().toLowerCase())
                            .channel(ActionChannel.CLIENT)
                            .allowedOrigins(allowed)
                            .category(ActionCategory.UI)
                            .ui("label", "desc")
                            .build();

                        PolicyResult result = OriginPolicy.INSTANCE.evaluate(
                            spec, contextForOrigin(invokeOrigin));

                        if (invokeOrigin == allowedOrigin) {
                            assertFalse(result.isDenied());
                        } else {
                            assertTrue(result.isDenied());
                        }
                    }
                }
            }
        }
    }
}

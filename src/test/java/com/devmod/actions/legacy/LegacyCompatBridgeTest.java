package com.devmod.actions.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.EngineFeatureFlags;
import com.devmod.actions.engine.EngineFeatureFlags.RoutingMode;
import com.devmod.arena.config.FeatureFlagRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LegacyCompatBridge: routing between V1 and V2 pipelines.
 *
 * <p>Since invoke() requires Minecraft runtime (ActionContext, ActionRegistry),
 * these tests focus on:
 * <ul>
 *   <li>Migration management (markMigrated, isMigrated, migratedCount)</li>
 *   <li>Integration with EngineFeatureFlags routing modes</li>
 *   <li>Source-level analysis of routing logic</li>
 * </ul>
 */
@DisplayName("LegacyCompatBridge")
class LegacyCompatBridgeTest {

    /** No-op executor for tests that only exercise migration management. */
    private static final ActionExecutor NOOP_EXECUTOR = ActionExecutor.builder().build();

    @BeforeEach
    void resetFlags() {
        EngineFeatureFlags.resetForTesting();
    }

    // =========================================================================
    // Migration management
    // =========================================================================

    @Nested
    @DisplayName("Migration Management")
    class MigrationManagement {

        @Test
        @DisplayName("Newly created bridge has correct migrated count")
        void initialMigratedCount() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(
                NOOP_EXECUTOR, Set.of("devmod.test.a", "devmod.test.b"));

            assertEquals(2, bridge.migratedCount());
            assertTrue(bridge.isMigrated("devmod.test.a"));
            assertTrue(bridge.isMigrated("devmod.test.b"));
            assertFalse(bridge.isMigrated("devmod.test.c"));
        }

        @Test
        @DisplayName("markMigrated adds action to set")
        void markMigrated() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, Set.of());

            bridge.markMigrated("devmod.test.new");

            assertTrue(bridge.isMigrated("devmod.test.new"));
            assertEquals(1, bridge.migratedCount());
        }

        @Test
        @DisplayName("markMigrated is idempotent")
        void markMigratedIdempotent() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, Set.of());

            bridge.markMigrated("devmod.test.a");
            bridge.markMigrated("devmod.test.a");

            assertEquals(1, bridge.migratedCount());
        }

        @Test
        @DisplayName("markUnmigrated removes action from set")
        void markUnmigrated() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(
                NOOP_EXECUTOR, Set.of("devmod.test.a"));

            bridge.markUnmigrated("devmod.test.a");

            assertFalse(bridge.isMigrated("devmod.test.a"));
            assertEquals(0, bridge.migratedCount());
        }

        @Test
        @DisplayName("markUnmigrated is safe for non-existent action")
        void markUnmigratedNonExistent() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, Set.of());

            assertDoesNotThrow(() -> bridge.markUnmigrated("devmod.test.nope"));
            assertEquals(0, bridge.migratedCount());
        }

        @Test
        @DisplayName("Empty initial set")
        void emptyInitialSet() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, Set.of());

            assertEquals(0, bridge.migratedCount());
            assertFalse(bridge.isMigrated("devmod.test.a"));
        }

        @Test
        @DisplayName("Initial set is copied (mutations to original don't affect bridge)")
        void initialSetCopied() {
            java.util.Set<String> mutable = new java.util.HashSet<>(Set.of("devmod.test.a"));
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, mutable);

            mutable.add("devmod.test.b");

            assertFalse(bridge.isMigrated("devmod.test.b"),
                "Bridge should not see mutations to the original set");
        }
    }

    // =========================================================================
    // Deprecated constructor
    // =========================================================================

    @Nested
    @DisplayName("Deprecated Constructor")
    class DeprecatedConstructor {

        @Test
        @DisplayName("Three-arg constructor still works")
        void threeArgConstructor() throws Exception {
            // Use reflection to call the deprecated constructor without a compile-time warning
            var ctor = LegacyCompatBridge.class.getConstructor(
                ActionExecutor.class, Set.class, boolean.class);
            LegacyCompatBridge bridge = ctor.newInstance(NOOP_EXECUTOR, Set.of("devmod.test.a"), true);

            assertTrue(bridge.isMigrated("devmod.test.a"));
            assertEquals(1, bridge.migratedCount());
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("Initial metrics are zero")
        void initialMetricsZero() {
            LegacyCompatBridge bridge = new LegacyCompatBridge(NOOP_EXECUTOR, Set.of());

            assertEquals(0, bridge.getV1Invocations());
            assertEquals(0, bridge.getV2Invocations());
            assertEquals(0, bridge.getV2Fallbacks());
            assertEquals(0, bridge.getShadowRuns());
        }
    }

    // =========================================================================
    // EngineFeatureFlags integration
    // =========================================================================

    @Nested
    @DisplayName("EngineFeatureFlags Integration")
    class FeatureFlagIntegration {

        /** Use the global singleton since EngineFeatureFlags accessors read from it. */
        private final FeatureFlagRegistry registry = FeatureFlagRegistry.INSTANCE;

        @BeforeEach
        void registerAndReset() {
            EngineFeatureFlags.resetForTesting();
            EngineFeatureFlags.register(registry);
            // Ensure all engine flags start disabled
            registry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, false);
            registry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, false);
            registry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, false);
        }

        @Test
        @DisplayName("Default mode is V1_ONLY (engine disabled)")
        void defaultMode() {
            assertEquals(RoutingMode.V1_ONLY, EngineFeatureFlags.currentMode());
        }

        @Test
        @DisplayName("Engine enabled without shadow/active is V2_ONLY")
        void engineEnabledV2Only() {
            registry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, true);

            assertEquals(RoutingMode.V2_ONLY, EngineFeatureFlags.currentMode());
        }

        @Test
        @DisplayName("Shadow mode requires engine enabled")
        void shadowRequiresEngine() {
            // Shadow enabled but engine disabled -> V1_ONLY
            registry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, true);

            assertEquals(RoutingMode.V1_ONLY, EngineFeatureFlags.currentMode());
            assertFalse(EngineFeatureFlags.isShadowMode());
        }

        @Test
        @DisplayName("Shadow mode when engine is enabled")
        void shadowMode() {
            registry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, true);
            registry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, true);

            assertEquals(RoutingMode.SHADOW, EngineFeatureFlags.currentMode());
            assertTrue(EngineFeatureFlags.isShadowMode());
        }

        @Test
        @DisplayName("Active mode requires engine enabled")
        void activeRequiresEngine() {
            // Active enabled but engine disabled -> V1_ONLY
            registry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, true);

            assertEquals(RoutingMode.V1_ONLY, EngineFeatureFlags.currentMode());
            assertFalse(EngineFeatureFlags.isActiveMode());
        }

        @Test
        @DisplayName("Active mode when engine is enabled")
        void activeMode() {
            registry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, true);
            registry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, true);

            assertEquals(RoutingMode.V2_ACTIVE, EngineFeatureFlags.currentMode());
            assertTrue(EngineFeatureFlags.isActiveMode());
        }

        @Test
        @DisplayName("Shadow takes precedence over active when both enabled")
        void shadowPrecedence() {
            registry.setEnabled(EngineFeatureFlags.V2_ENGINE_ENABLED, true);
            registry.setEnabled(EngineFeatureFlags.V2_SHADOW_MODE, true);
            registry.setEnabled(EngineFeatureFlags.V2_ACTIVE_MODE, true);

            assertEquals(RoutingMode.SHADOW, EngineFeatureFlags.currentMode());
        }

        @Test
        @DisplayName("Register is idempotent")
        void registerIdempotent() {
            assertDoesNotThrow(() -> EngineFeatureFlags.register(registry));
        }
    }

    // =========================================================================
    // Source-level analysis
    // =========================================================================

    @Nested
    @DisplayName("Source-Level Structure")
    class SourceLevelStructure {

        private String readBridgeSource() throws IOException {
            Path srcRoot = Path.of("src/main/java/com/devmod/actions/legacy/LegacyCompatBridge.java");
            if (!Files.exists(srcRoot)) {
                srcRoot = Path.of("").toAbsolutePath()
                    .resolve("src/main/java/com/devmod/actions/legacy/LegacyCompatBridge.java");
            }
            assertTrue(Files.exists(srcRoot), "LegacyCompatBridge.java not found at: " + srcRoot);
            return Files.readString(srcRoot);
        }

        @Test
        @DisplayName("Class is final")
        void classIsFinal() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("public final class LegacyCompatBridge"));
        }

        @Test
        @DisplayName("Has invoke method that reads EngineFeatureFlags.currentMode()")
        void invokeUsesFeatureFlags() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("EngineFeatureFlags.currentMode()"));
        }

        @Test
        @DisplayName("Has invokeShadow method for SHADOW mode")
        void hasShadowMethod() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("invokeShadow"));
        }

        @Test
        @DisplayName("Has invokeV2Active method for V2_ACTIVE mode")
        void hasV2ActiveMethod() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("invokeV2Active"));
        }

        @Test
        @DisplayName("Has invokeV2Only method for V2_ONLY mode")
        void hasV2OnlyMethod() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("invokeV2Only"));
        }

        @Test
        @DisplayName("Shadow mode calls ShadowModeComparator.compare")
        void shadowCallsComparator() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("ShadowModeComparator.compare("));
        }

        @Test
        @DisplayName("V2 Active mode falls back to V1 on failure")
        void v2ActiveFallsBack() throws IOException {
            String src = readBridgeSource();
            // Should check for v2Result.isFailed() and fall back
            assertTrue(src.contains("v2Result.isFailed()"));
            assertTrue(src.contains("falling back to V1"));
        }

        @Test
        @DisplayName("V2 Active mode does not fall back once the handler has run")
        void v2ActiveSkipsFallbackAfterHandlerStarted() throws IOException {
            String src = readBridgeSource();
            // FAILED is only produced after ExecuteStep entered the handler, so an
            // unguarded fallback re-applies whatever prefix already succeeded.
            assertTrue(src.contains("outcome.handlerStarted()"),
                "fallback must be guarded on whether the handler was entered");
        }

        @Test
        @DisplayName("Shadow mode runs V2 as a dry run so side effects do not fire twice")
        void shadowModeUsesDryRun() throws IOException {
            String src = readBridgeSource();
            // The V2 handlers delegate straight back into V1, so executing them in the
            // shadow leg would double every teleport, grant and toggle.
            assertTrue(src.contains("executeDetailed(actionId, context, true)"),
                "shadow leg must execute V2 with dryRun=true");
        }

        @Test
        @DisplayName("V2 Active mode catches V2 exceptions")
        void v2ActiveCatchesExceptions() throws IOException {
            String src = readBridgeSource();
            Pattern catchPattern = Pattern.compile(
                "invokeV2Active[\\s\\S]*?catch\\s*\\(Exception e\\)");
            Matcher m = catchPattern.matcher(src);
            assertTrue(m.find(), "invokeV2Active should catch exceptions");
        }

        @Test
        @DisplayName("Shadow mode catches V2 exceptions")
        void shadowCatchesExceptions() throws IOException {
            String src = readBridgeSource();
            Pattern catchPattern = Pattern.compile(
                "invokeShadow[\\s\\S]*?catch\\s*\\(Exception e\\)");
            Matcher m = catchPattern.matcher(src);
            assertTrue(m.find(), "invokeShadow should catch exceptions");
        }

        @Test
        @DisplayName("Uses ConcurrentHashMap for thread safety")
        void usesConcurrentSet() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("ConcurrentHashMap.newKeySet()"));
        }

        @Test
        @DisplayName("Has markUnmigrated method for rollback")
        void hasMarkUnmigrated() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("public void markUnmigrated(String actionId)"));
        }

        @Test
        @DisplayName("Has V2 fallback metric counter")
        void hasFallbackMetric() throws IOException {
            String src = readBridgeSource();
            assertTrue(src.contains("v2Fallbacks"));
        }

        @Test
        @DisplayName("Four routing modes handled in invoke")
        void fourRoutingModes() throws IOException {
            String src = readBridgeSource();
            // All RoutingMode values should be referenced
            for (RoutingMode mode : RoutingMode.values()) {
                assertTrue(src.contains(mode.name()),
                    "Should handle routing mode: " + mode.name());
            }
        }
    }
}

package com.devmod.arena.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for FeatureFlagRegistry.
 */
class FeatureFlagRegistryTest {

    private FeatureFlagRegistry registry;

    @BeforeEach
    void setUp() {
        // Create a fresh registry for each test
        registry = new FeatureFlagRegistry();
    }

    // ============================================================================
    // BASIC OPERATIONS
    // ============================================================================

    @Nested
    @DisplayName("Basic Flag Operations")
    class BasicOperations {

        @Test
        @DisplayName("Should have default flags")
        void shouldHaveDefaultFlags() {
            // Arena flags should exist
            assertTrue(registry.get(FeatureFlagRegistry.ARENA_TEMPLATE_ENABLED).isPresent());
            assertTrue(registry.get(FeatureFlagRegistry.ARENA_INSTANCE_ONLY).isPresent());
            assertTrue(registry.get(FeatureFlagRegistry.ARENA_ROUTING_ENABLED).isPresent());

            // Global flags should exist
            assertTrue(registry.get(FeatureFlagRegistry.MAILBOX_ENABLED).isPresent());
            assertTrue(registry.get(FeatureFlagRegistry.TELEMETRY_ENABLED).isPresent());
            assertTrue(registry.get(FeatureFlagRegistry.PARTY_SYSTEM_ENABLED).isPresent());
        }

        @Test
        @DisplayName("Should return false for unknown flag")
        void shouldReturnFalseForUnknownFlag() {
            assertFalse(registry.isEnabled("unknown.flag.that.does.not.exist"));
        }

        @Test
        @DisplayName("Should set and get flag value")
        void shouldSetAndGetFlagValue() {
            // Given a flag with a default value
            boolean defaultValue = registry.isEnabled(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED);

            // When setting to opposite
            registry.setEnabled(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED, !defaultValue);

            // Then it should be flipped
            assertEquals(!defaultValue, registry.isEnabled(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED));
        }

        @Test
        @DisplayName("Should register custom flag")
        void shouldRegisterCustomFlag() {
            // Given a new custom flag
            FeatureFlagRegistry.MutableFeatureFlag customFlag =
                new FeatureFlagRegistry.MutableFeatureFlag(
                    "custom.test.flag",
                    true,
                    "Test description"
                );

            // When registering
            registry.register(customFlag);

            // Then it should be accessible
            assertTrue(registry.get("custom.test.flag").isPresent());
            assertTrue(registry.isEnabled("custom.test.flag"));
        }
    }

    // ============================================================================
    // GLOBAL FLAGS
    // ============================================================================

    @Nested
    @DisplayName("Global Feature Flags")
    class GlobalFlags {

        @Test
        @DisplayName("Should have all global flag constants defined")
        void shouldHaveAllGlobalFlagConstants() {
            assertNotNull(FeatureFlagRegistry.MAILBOX_ENABLED);
            assertNotNull(FeatureFlagRegistry.TELEMETRY_ENABLED);
            assertNotNull(FeatureFlagRegistry.PARTY_SYSTEM_ENABLED);
            assertNotNull(FeatureFlagRegistry.NOTIFICATION_ENABLED);
            assertNotNull(FeatureFlagRegistry.ENDURANCE_ENABLED);
            assertNotNull(FeatureFlagRegistry.SEASON_PASS_ENABLED);
            assertNotNull(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED);
            assertNotNull(FeatureFlagRegistry.ITEM_EDITOR_ENABLED);
            assertNotNull(FeatureFlagRegistry.MOB_CONFIG_ENABLED);
            assertNotNull(FeatureFlagRegistry.COMPAT_AUTODETECT_ENABLED);
        }

        @Test
        @DisplayName("Global flags should follow naming convention")
        void globalFlagsShouldFollowNamingConvention() {
            assertTrue(FeatureFlagRegistry.MAILBOX_ENABLED.startsWith("global."));
            assertTrue(FeatureFlagRegistry.TELEMETRY_ENABLED.startsWith("global."));
            assertTrue(FeatureFlagRegistry.PARTY_SYSTEM_ENABLED.startsWith("global."));
            assertTrue(FeatureFlagRegistry.NOTIFICATION_ENABLED.startsWith("global."));
            assertTrue(FeatureFlagRegistry.ENDURANCE_ENABLED.startsWith("global."));
            assertTrue(FeatureFlagRegistry.SEASON_PASS_ENABLED.startsWith("global."));
        }

        @Test
        @DisplayName("Endurance should be enabled by default")
        void enduranceShouldBeEnabledByDefault() {
            assertTrue(registry.isEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED));
        }

        @Test
        @DisplayName("Season pass should be disabled by default")
        void seasonPassShouldBeDisabledByDefault() {
            assertFalse(registry.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED));
        }
    }

    // ============================================================================
    // DEPENDENCIES
    // ============================================================================

    @Nested
    @DisplayName("Flag Dependencies")
    class FlagDependencies {

        @Test
        @DisplayName("Season pass should require endurance")
        void seasonPassShouldRequireEndurance() {
            // Given endurance is disabled
            registry.setEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED, false);

            // When trying to enable season pass
            boolean result = registry.setEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED, true);

            // Then it should fail
            assertFalse(result);
            assertFalse(registry.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED));
        }

        @Test
        @DisplayName("Should enable season pass when endurance is enabled")
        void shouldEnableSeasonPassWhenEnduranceEnabled() {
            // Given endurance is enabled
            registry.setEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED, true);

            // When enabling season pass
            boolean result = registry.setEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED, true);

            // Then it should succeed
            assertTrue(result);
            assertTrue(registry.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED));
        }

        @Test
        @DisplayName("Disabling endurance should cascade to season pass")
        void disablingEnduranceShouldCascadeToSeasonPass() {
            // Given both are enabled
            registry.setEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED, true);
            registry.setEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED, true);
            assertTrue(registry.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED));

            // When disabling endurance
            registry.setEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED, false);

            // Then season pass should also be disabled
            assertFalse(registry.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED));
        }

        @Test
        @DisplayName("Should get dependencies for a flag")
        void shouldGetDependencies() {
            var deps = registry.getDependencies(FeatureFlagRegistry.SEASON_PASS_ENABLED);
            assertTrue(deps.contains(FeatureFlagRegistry.ENDURANCE_ENABLED));
        }

        @Test
        @DisplayName("Should get dependents for a flag")
        void shouldGetDependents() {
            var dependents = registry.getDependents(FeatureFlagRegistry.ENDURANCE_ENABLED);
            assertTrue(dependents.contains(FeatureFlagRegistry.SEASON_PASS_ENABLED));
        }

        @Test
        @DisplayName("Arena routing should require template enabled")
        void arenaRoutingShouldRequireTemplate() {
            // Given template is disabled
            registry.setEnabled(FeatureFlagRegistry.ARENA_INSTANCE_ONLY, true);
            registry.setEnabled(FeatureFlagRegistry.ARENA_TEMPLATE_ENABLED, false);

            // When trying to enable routing
            boolean result = registry.setEnabled(FeatureFlagRegistry.ARENA_ROUTING_ENABLED, true);

            // Then it should fail
            assertFalse(result);
        }
    }

    // ============================================================================
    // SNAPSHOT
    // ============================================================================

    @Nested
    @DisplayName("Snapshot Operations")
    class SnapshotOperations {

        @Test
        @DisplayName("Should create snapshot of all flags")
        void shouldCreateSnapshot() {
            Map<String, Boolean> snapshot = registry.snapshot();

            assertNotNull(snapshot);
            assertFalse(snapshot.isEmpty());
            assertTrue(snapshot.containsKey(FeatureFlagRegistry.MAILBOX_ENABLED));
            assertTrue(snapshot.containsKey(FeatureFlagRegistry.ENDURANCE_ENABLED));
        }

        @Test
        @DisplayName("Snapshot should reflect current state")
        void snapshotShouldReflectCurrentState() {
            registry.setEnabled(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED, true);

            Map<String, Boolean> snapshot = registry.snapshot();

            assertTrue(snapshot.get(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED));
        }

        @Test
        @DisplayName("Should get all flags via all()")
        void shouldGetAllFlags() {
            var all = registry.all();

            assertNotNull(all);
            assertFalse(all.isEmpty());

            // Verify at least the global flags are present
            assertTrue(all.size() >= 10);
        }
    }

    // ============================================================================
    // TELEMETRY
    // ============================================================================

    @Nested
    @DisplayName("Telemetry")
    class TelemetryTests {

        @Test
        @DisplayName("Should call telemetry on flag change")
        void shouldCallTelemetryOnChange() {
            AtomicBoolean telemetryCalled = new AtomicBoolean(false);

            // Create registry with telemetry
            FeatureFlagRegistry registryWithTelemetry = new FeatureFlagRegistry(
                (flagId, oldValue, newValue) -> telemetryCalled.set(true)
            );

            // When changing a flag
            registryWithTelemetry.setEnabled(FeatureFlagRegistry.DEBUG_OVERLAY_ENABLED, true);

            // Then telemetry should be called
            assertTrue(telemetryCalled.get());
        }

        @Test
        @DisplayName("Should not call telemetry when value unchanged")
        void shouldNotCallTelemetryWhenUnchanged() {
            AtomicBoolean telemetryCalled = new AtomicBoolean(false);

            FeatureFlagRegistry registryWithTelemetry = new FeatureFlagRegistry(
                (flagId, oldValue, newValue) -> telemetryCalled.set(true)
            );

            // Get current value
            boolean current = registryWithTelemetry.isEnabled(FeatureFlagRegistry.MAILBOX_ENABLED);

            // Set to same value
            registryWithTelemetry.setEnabled(FeatureFlagRegistry.MAILBOX_ENABLED, current);

            // Telemetry should not be called
            assertFalse(telemetryCalled.get());
        }
    }

    // ============================================================================
    // SINGLETON
    // ============================================================================

    @Nested
    @DisplayName("Singleton Instance")
    class SingletonTests {

        @Test
        @DisplayName("INSTANCE should not be null")
        void instanceShouldNotBeNull() {
            assertNotNull(FeatureFlagRegistry.INSTANCE);
        }

        @Test
        @DisplayName("INSTANCE should have default flags")
        void instanceShouldHaveDefaultFlags() {
            assertTrue(FeatureFlagRegistry.INSTANCE.get(FeatureFlagRegistry.MAILBOX_ENABLED).isPresent());
        }
    }

    // ============================================================================
    // EDGE CASES
    // ============================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle setting unknown flag")
        void shouldHandleSettingUnknownFlag() {
            boolean result = registry.setEnabled("completely.unknown.flag", true);
            assertFalse(result);
        }

        @Test
        @DisplayName("Optional should be empty for unknown flag")
        void optionalShouldBeEmptyForUnknownFlag() {
            var opt = registry.get("unknown.flag");
            assertTrue(opt.isEmpty());
        }
    }
}

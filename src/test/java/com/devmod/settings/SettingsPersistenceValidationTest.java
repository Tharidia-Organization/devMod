package com.devmod.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.client.ui.unified.persistence.SettingsData;
import com.devmod.client.ui.unified.persistence.SettingsData.CombatSettings;
import com.devmod.client.ui.unified.persistence.SettingsData.DebugSettings;
import com.devmod.client.ui.unified.persistence.SettingsData.GeneralSettings;
import com.devmod.client.ui.unified.persistence.SettingsData.OnboardingSettings;
import com.devmod.client.ui.unified.persistence.SettingsData.TelemetrySettings;
import com.devmod.client.ui.unified.persistence.SettingsData.VisualizerSettings;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L1 Settings Persistence Validation Tests.
 *
 * Validates:
 * - SettingsData structure and defaults
 * - Deep copy functionality
 * - Reset operations
 * - Render distance validation
 * - Heatmap settings management
 */
@DisplayName("L1: Settings Persistence Validation")
class SettingsPersistenceValidationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-14: Settings Data Structure
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-14: Settings Data Structure")
    class SettingsDataStructureTest {

        @Test
        @DisplayName("SettingsData has all required nested objects")
        void settingsDataHasAllNestedObjects() {
            SettingsData settings = new SettingsData();

            assertNotNull(settings.general, "GeneralSettings should be initialized");
            assertNotNull(settings.debug, "DebugSettings should be initialized");
            assertNotNull(settings.visualizers, "VisualizerSettings should be initialized");
            assertNotNull(settings.combat, "CombatSettings should be initialized");
            assertNotNull(settings.telemetry, "TelemetrySettings should be initialized");
            assertNotNull(settings.onboarding, "OnboardingSettings should be initialized");
        }

        @Test
        @DisplayName("Schema version is set correctly")
        void schemaVersionIsCorrect() {
            SettingsData settings = new SettingsData();
            assertEquals(1, settings.version, "Schema version should be 1");
        }

        @Test
        @DisplayName("GeneralSettings has correct defaults")
        void generalSettingsDefaults() {
            GeneralSettings general = new GeneralSettings();

            assertTrue(general.showOverlay, "showOverlay default should be true");
            assertFalse(general.showRender, "showRender default should be false");
            assertFalse(general.renderAsBlocks, "renderAsBlocks default should be false");
            assertEquals(0xFFFF0000, general.followRangeColor, "followRangeColor default should be red");
        }

        @Test
        @DisplayName("DebugSettings all default to false")
        void debugSettingsDefaults() {
            DebugSettings debug = new DebugSettings();

            assertFalse(debug.debugRenderer, "debugRenderer default should be false");
            assertFalse(debug.lightLevels, "lightLevels default should be false");
            assertFalse(debug.lineOfSight, "lineOfSight default should be false");
            assertFalse(debug.pathfinding, "pathfinding default should be false");
            assertFalse(debug.roomBounds, "roomBounds default should be false");
            assertFalse(debug.bossPhaseOverlay, "bossPhaseOverlay default should be false");
            assertFalse(debug.entityDensityOverlay, "entityDensityOverlay default should be false");
            assertFalse(debug.skillEfficacyOverlay, "skillEfficacyOverlay default should be false");
            assertFalse(debug.spawnabilityOverlay, "spawnabilityOverlay default should be false");
        }

        @Test
        @DisplayName("CombatSettings has correct multiplier defaults")
        void combatSettingsDefaults() {
            CombatSettings combat = new CombatSettings();

            assertEquals(2.0f, combat.headMultiplier, 0.001f, "Head multiplier should be 2.0");
            assertEquals(1.0f, combat.bodyMultiplier, 0.001f, "Body multiplier should be 1.0");
            assertEquals(0.8f, combat.legsMultiplier, 0.001f, "Legs multiplier should be 0.8");
            assertEquals(0.0f, combat.armorPenetrationBase, 0.001f, "Armor penetration should be 0.0");
        }

        @Test
        @DisplayName("TelemetrySettings has correct defaults")
        void telemetrySettingsDefaults() {
            TelemetrySettings telemetry = new TelemetrySettings();

            assertFalse(telemetry.autoExport, "autoExport default should be false");
            assertEquals("json", telemetry.exportFormat, "exportFormat default should be 'json'");
            assertEquals(7, telemetry.dataRetentionDays, "dataRetentionDays default should be 7");
            assertTrue(telemetry.recordSessions, "recordSessions default should be true");
        }

        @Test
        @DisplayName("OnboardingSettings all default to false")
        void onboardingSettingsDefaults() {
            OnboardingSettings onboarding = new OnboardingSettings();

            assertFalse(onboarding.hasSeenWelcome, "hasSeenWelcome default should be false");
            assertFalse(onboarding.tutorialCompleted, "tutorialCompleted default should be false");
            assertFalse(onboarding.hasSeenEnduranceIntro, "hasSeenEnduranceIntro default should be false");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-15: Visualizer Settings
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-15: Visualizer Settings")
    class VisualizerSettingsTest {

        @Test
        @DisplayName("VisualizerSettings has correct defaults")
        void visualizerSettingsDefaults() {
            VisualizerSettings visualizers = new VisualizerSettings();

            assertEquals(0.5f, visualizers.heatmapOpacity, 0.001f, "heatmapOpacity default should be 0.5");
            assertFalse(visualizers.verticalLevels, "verticalLevels default should be false");
            assertFalse(visualizers.safeSpots, "safeSpots default should be false");
            assertEquals(64, visualizers.renderDistance, "renderDistance default should be 64");
        }

        @Test
        @DisplayName("Heatmaps map is initialized with all types")
        void heatmapsMapInitialized() {
            VisualizerSettings visualizers = new VisualizerSettings();

            assertNotNull(visualizers.heatmaps, "Heatmaps map should not be null");
            // Should have entries for all heatmap types
            assertTrue(visualizers.heatmaps.size() >= 6, "Should have at least 6 heatmap types");
        }

        @Test
        @DisplayName("Render distance validation - minimum")
        void renderDistanceMinimumValidation() {
            VisualizerSettings visualizers = new VisualizerSettings();

            visualizers.setRenderDistance(5); // Below minimum
            assertEquals(16, visualizers.getRenderDistance(), "Should clamp to minimum 16");
        }

        @Test
        @DisplayName("Render distance validation - maximum")
        void renderDistanceMaximumValidation() {
            VisualizerSettings visualizers = new VisualizerSettings();

            visualizers.setRenderDistance(500); // Above maximum
            assertEquals(128, visualizers.getRenderDistance(), "Should clamp to maximum 128");
        }

        @Test
        @DisplayName("Render distance squared calculation")
        void renderDistanceSquaredCalculation() {
            VisualizerSettings visualizers = new VisualizerSettings();

            visualizers.setRenderDistance(64);
            assertEquals(64.0 * 64.0, visualizers.getRenderDistanceSq(), 0.001,
                "Render distance squared should be 4096");
        }

        @Test
        @DisplayName("Render distance constants are valid")
        void renderDistanceConstantsValid() {
            assertEquals(16, VisualizerSettings.MIN_RENDER_DISTANCE);
            assertEquals(128, VisualizerSettings.MAX_RENDER_DISTANCE);
            assertEquals(64, VisualizerSettings.DEFAULT_RENDER_DISTANCE);

            assertTrue(VisualizerSettings.MIN_RENDER_DISTANCE < VisualizerSettings.DEFAULT_RENDER_DISTANCE);
            assertTrue(VisualizerSettings.DEFAULT_RENDER_DISTANCE < VisualizerSettings.MAX_RENDER_DISTANCE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-16: Deep Copy Functionality
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-16: Deep Copy Functionality")
    class DeepCopyTest {

        @Test
        @DisplayName("Copy creates independent object")
        void copyCreatesIndependentObject() {
            SettingsData original = new SettingsData();
            original.general.showOverlay = false;
            original.debug.debugRenderer = true;
            original.combat.headMultiplier = 3.0f;

            SettingsData copy = original.copy();

            // Modify original
            original.general.showOverlay = true;
            original.debug.debugRenderer = false;

            // Copy should be unchanged
            assertFalse(copy.general.showOverlay, "Copy should not be affected by original change");
            assertTrue(copy.debug.debugRenderer, "Copy should preserve its value");
            assertEquals(3.0f, copy.combat.headMultiplier, 0.001f);
        }

        @Test
        @DisplayName("Copy preserves all general settings")
        void copyPreservesGeneralSettings() {
            SettingsData original = new SettingsData();
            original.general.showOverlay = false;
            original.general.showRender = true;
            original.general.renderAsBlocks = true;
            original.general.followRangeColor = 0xFF00FF00;

            SettingsData copy = original.copy();

            assertFalse(copy.general.showOverlay);
            assertTrue(copy.general.showRender);
            assertTrue(copy.general.renderAsBlocks);
            assertEquals(0xFF00FF00, copy.general.followRangeColor);
        }

        @Test
        @DisplayName("Copy preserves all debug settings")
        void copyPreservesDebugSettings() {
            SettingsData original = new SettingsData();
            original.debug.debugRenderer = true;
            original.debug.lightLevels = true;
            original.debug.pathfinding = true;

            SettingsData copy = original.copy();

            assertTrue(copy.debug.debugRenderer);
            assertTrue(copy.debug.lightLevels);
            assertTrue(copy.debug.pathfinding);
        }

        @Test
        @DisplayName("Copy preserves visualizer settings including heatmaps")
        void copyPreservesVisualizerSettings() {
            SettingsData original = new SettingsData();
            original.visualizers.heatmapOpacity = 0.8f;
            original.visualizers.verticalLevels = true;
            original.visualizers.renderDistance = 96;
            original.visualizers.heatmaps.put("DEATH", true);

            SettingsData copy = original.copy();

            assertEquals(0.8f, copy.visualizers.heatmapOpacity, 0.001f);
            assertTrue(copy.visualizers.verticalLevels);
            assertEquals(96, copy.visualizers.renderDistance);
            assertTrue(copy.visualizers.heatmaps.get("DEATH"));
        }

        @Test
        @DisplayName("Heatmaps copy is independent from original")
        void heatmapsCopyIsIndependent() {
            SettingsData original = new SettingsData();
            original.visualizers.heatmaps.put("DEATH", true);

            SettingsData copy = original.copy();

            // Modify original
            original.visualizers.heatmaps.put("DEATH", false);

            // Copy should be unchanged
            assertTrue(copy.visualizers.heatmaps.get("DEATH"),
                "Copy's heatmap should not be affected by original modification");
        }

        @Test
        @DisplayName("Copy preserves onboarding state")
        void copyPreservesOnboardingState() {
            SettingsData original = new SettingsData();
            original.onboarding.hasSeenWelcome = true;
            original.onboarding.tutorialCompleted = true;
            original.onboarding.hasSeenEnduranceIntro = true;

            SettingsData copy = original.copy();

            assertTrue(copy.onboarding.hasSeenWelcome);
            assertTrue(copy.onboarding.tutorialCompleted);
            assertTrue(copy.onboarding.hasSeenEnduranceIntro);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-17: Reset Functionality
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-17: Reset Functionality")
    class ResetFunctionalityTest {

        @Test
        @DisplayName("resetToDefaults resets general settings")
        void resetToDefaultsResetsGeneralSettings() {
            SettingsData settings = new SettingsData();
            settings.general.showOverlay = false;
            settings.general.showRender = true;
            settings.general.followRangeColor = 0xFF0000FF;

            settings.resetToDefaults();

            assertTrue(settings.general.showOverlay, "showOverlay should be reset to true");
            assertFalse(settings.general.showRender, "showRender should be reset to false");
            assertEquals(0xFFFF0000, settings.general.followRangeColor,
                "followRangeColor should be reset to red");
        }

        @Test
        @DisplayName("resetToDefaults resets debug settings")
        void resetToDefaultsResetsDebugSettings() {
            SettingsData settings = new SettingsData();
            settings.debug.debugRenderer = true;
            settings.debug.lightLevels = true;
            settings.debug.pathfinding = true;

            settings.resetToDefaults();

            assertFalse(settings.debug.debugRenderer);
            assertFalse(settings.debug.lightLevels);
            assertFalse(settings.debug.pathfinding);
        }

        @Test
        @DisplayName("resetToDefaults preserves onboarding state")
        void resetToDefaultsPreservesOnboardingState() {
            SettingsData settings = new SettingsData();
            settings.onboarding.hasSeenWelcome = true;
            settings.onboarding.tutorialCompleted = true;

            settings.resetToDefaults();

            assertTrue(settings.onboarding.hasSeenWelcome,
                "onboarding.hasSeenWelcome should be preserved");
            assertTrue(settings.onboarding.tutorialCompleted,
                "onboarding.tutorialCompleted should be preserved");
        }

        @Test
        @DisplayName("resetAll resets everything including onboarding")
        void resetAllResetsEverything() {
            SettingsData settings = new SettingsData();
            settings.general.showOverlay = false;
            settings.debug.debugRenderer = true;
            settings.onboarding.hasSeenWelcome = true;
            settings.onboarding.tutorialCompleted = true;

            settings.resetAll();

            // General should be reset
            assertTrue(settings.general.showOverlay);
            assertFalse(settings.debug.debugRenderer);

            // Onboarding should also be reset
            assertFalse(settings.onboarding.hasSeenWelcome,
                "onboarding.hasSeenWelcome should be reset");
            assertFalse(settings.onboarding.tutorialCompleted,
                "onboarding.tutorialCompleted should be reset");
        }

        @Test
        @DisplayName("resetToDefaults resets combat multipliers")
        void resetToDefaultsResetsCombatMultipliers() {
            SettingsData settings = new SettingsData();
            settings.combat.headMultiplier = 5.0f;
            settings.combat.bodyMultiplier = 2.0f;
            settings.combat.legsMultiplier = 0.5f;

            settings.resetToDefaults();

            assertEquals(2.0f, settings.combat.headMultiplier, 0.001f);
            assertEquals(1.0f, settings.combat.bodyMultiplier, 0.001f);
            assertEquals(0.8f, settings.combat.legsMultiplier, 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-18: Settings Value Validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-18: Settings Value Validation")
    class SettingsValueValidationTest {

        @Test
        @DisplayName("Combat multipliers have sensible default values")
        void combatMultipliersAreSensible() {
            CombatSettings combat = new CombatSettings();

            // Head should be highest multiplier
            assertTrue(combat.headMultiplier > combat.bodyMultiplier,
                "Head multiplier should be greater than body");
            // Legs should be lowest multiplier
            assertTrue(combat.legsMultiplier < combat.bodyMultiplier,
                "Legs multiplier should be less than body");
            // All should be positive
            assertTrue(combat.headMultiplier > 0);
            assertTrue(combat.bodyMultiplier > 0);
            assertTrue(combat.legsMultiplier > 0);
        }

        @Test
        @DisplayName("Telemetry retention days is reasonable")
        void telemetryRetentionIsReasonable() {
            TelemetrySettings telemetry = new TelemetrySettings();

            assertTrue(telemetry.dataRetentionDays >= 1, "Retention should be at least 1 day");
            assertTrue(telemetry.dataRetentionDays <= 365, "Retention shouldn't exceed a year");
        }

        @Test
        @DisplayName("Heatmap opacity is in valid range")
        void heatmapOpacityIsValid() {
            VisualizerSettings visualizers = new VisualizerSettings();

            assertTrue(visualizers.heatmapOpacity >= 0.0f, "Opacity should be >= 0");
            assertTrue(visualizers.heatmapOpacity <= 1.0f, "Opacity should be <= 1");
        }

        @Test
        @DisplayName("Export format is a valid string")
        void exportFormatIsValid() {
            TelemetrySettings telemetry = new TelemetrySettings();

            assertNotNull(telemetry.exportFormat);
            assertFalse(telemetry.exportFormat.isEmpty());
            // Should be a known format
            assertTrue(
                telemetry.exportFormat.equals("json") ||
                telemetry.exportFormat.equals("csv") ||
                telemetry.exportFormat.equals("xml"),
                "Export format should be json, csv, or xml");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L1-19: Version Migration Support
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L1-19: Version Migration Support")
    class VersionMigrationTest {

        @Test
        @DisplayName("New settings have current version")
        void newSettingsHaveCurrentVersion() {
            SettingsData settings = new SettingsData();
            assertEquals(1, settings.version, "New settings should have version 1");
        }

        @Test
        @DisplayName("Version is preserved in copy")
        void versionIsPreservedInCopy() {
            SettingsData original = new SettingsData();
            original.version = 5;

            SettingsData copy = original.copy();
            assertEquals(5, copy.version, "Version should be preserved in copy");
        }

        @Test
        @DisplayName("Version is not affected by reset")
        void versionNotAffectedByReset() {
            SettingsData settings = new SettingsData();
            settings.version = 3;

            settings.resetToDefaults();

            // resetToDefaults creates new nested objects but doesn't change version
            // Version is only modified during explicit migration
            // Note: This tests the current behavior - version is NOT reset
            assertEquals(3, settings.version, "Version should not be modified by resetToDefaults");
        }
    }
}

package com.devmod.client.ui.unified.persistence;

import java.util.Map;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.rendering.HeatmapType;

public class SettingsData {

    // Schema version for migration support
    public int version = 1;

    // === General Settings ===
    public GeneralSettings general = new GeneralSettings();

    // === Debug Overlays ===
    public DebugSettings debug = new DebugSettings();

    // === Visualizers ===
    public VisualizerSettings visualizers = new VisualizerSettings();

    // === Combat Settings ===
    public CombatSettings combat = new CombatSettings();

    // === Telemetry Settings ===
    public TelemetrySettings telemetry = new TelemetrySettings();

    // === Onboarding Settings ===
    public OnboardingSettings onboarding = new OnboardingSettings();

    /**
     * General mod visibility settings.
     */
    public static class GeneralSettings {
        public boolean showOverlay = true;
        public boolean showRender = false;
        public boolean renderAsBlocks = false;
        public int followRangeColor = DesignTokens.Basic.RED; // Red
        public boolean partyHudEnabled = true; // Party member HUD during quests
        public boolean hubAdvancedMode = false; // Show advanced options in hub screens
    }

    /**
     * Debug overlay toggle states.
     */
    public static class DebugSettings {
        public boolean developerMode = false;
        public boolean debugRenderer = false;
        public boolean lightLevels = false;
        public boolean lineOfSight = false;
        public boolean pathfinding = false;
        public boolean roomBounds = false;
        public boolean showRoomGaps = true; // Show gap detection for room bounds (default on)
        public boolean bossPhaseOverlay = false;
        public boolean entityDensityOverlay = false;
        public boolean skillEfficacyOverlay = false;
        public boolean spawnabilityOverlay = false;
        public boolean lightOverlayPerfWarned = false;
        public boolean spawnabilityOverlayPerfWarned = false;
    }

    /**
     * Heatmap and visualizer settings.
     */
    public static class VisualizerSettings {
        // Heatmap enabled states (stored as map for flexibility)
        public Map<String, Boolean> heatmaps = new java.util.HashMap<>();

        // General visualizer settings
        public float heatmapOpacity = 0.5f;
        public boolean verticalLevels = false;
        public boolean safeSpots = false;

        // Render distance for all visualizers (in blocks)
        // Range: 16-128 blocks, default 64
        public int renderDistance = 64;

        // Constants for validation
        public static final int MIN_RENDER_DISTANCE = 16;
        public static final int MAX_RENDER_DISTANCE = 128;
        public static final int DEFAULT_RENDER_DISTANCE = 64;

        public VisualizerSettings() {
            // Initialize all heatmap types as disabled
            for (HeatmapType type : HeatmapType.values()) {
                heatmaps.put(type.name(), false);
            }
        }

        public boolean isHeatmapEnabled(HeatmapType type) {
            return heatmaps.getOrDefault(type.name(), false);
        }

        public void setHeatmapEnabled(HeatmapType type, boolean enabled) {
            heatmaps.put(type.name(), enabled);
        }

        /**
         * Get render distance, clamped to valid range.
         */
        public int getRenderDistance() {
            return Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, renderDistance));
        }

        /**
         * Get render distance squared (for distance checks).
         */
        public double getRenderDistanceSq() {
            int dist = getRenderDistance();
            return (double) dist * dist;
        }

        /**
         * Set render distance with validation.
         */
        public void setRenderDistance(int distance) {
            this.renderDistance = Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, distance));
        }
    }

    /**
     * Combat damage multiplier settings.
     */
    public static class CombatSettings {
        // Global body part multipliers
        public float headMultiplier = 2.0f;
        public float bodyMultiplier = 1.0f;
        public float legsMultiplier = 0.8f;

        // Armor penetration base
        public float armorPenetrationBase = 0.0f;
    }

    /**
     * Telemetry and export settings.
     */
    public static class TelemetrySettings {
        public boolean autoExport = false;
        public String exportFormat = "json";
        public int dataRetentionDays = 7;
        public boolean recordSessions = true;
    }

    /**
     * Onboarding and tutorial settings.
     */
    public static class OnboardingSettings {
        public boolean hasSeenWelcome = false;
        public boolean tutorialCompleted = false;
        public boolean hasSeenEnduranceIntro = false;
    }

    /**
     * Creates a deep copy of this SettingsData.
     */
    public SettingsData copy() {
        SettingsData copy = new SettingsData();
        copy.version = this.version;

        // General
        copy.general.showOverlay = this.general.showOverlay;
        copy.general.showRender = this.general.showRender;
        copy.general.renderAsBlocks = this.general.renderAsBlocks;
        copy.general.followRangeColor = this.general.followRangeColor;
        copy.general.partyHudEnabled = this.general.partyHudEnabled;
        copy.general.hubAdvancedMode = this.general.hubAdvancedMode;

        // Debug
        copy.debug.developerMode = this.debug.developerMode;
        copy.debug.debugRenderer = this.debug.debugRenderer;
        copy.debug.lightLevels = this.debug.lightLevels;
        copy.debug.lineOfSight = this.debug.lineOfSight;
        copy.debug.pathfinding = this.debug.pathfinding;
        copy.debug.roomBounds = this.debug.roomBounds;
        copy.debug.showRoomGaps = this.debug.showRoomGaps;
        copy.debug.bossPhaseOverlay = this.debug.bossPhaseOverlay;
        copy.debug.entityDensityOverlay = this.debug.entityDensityOverlay;
        copy.debug.skillEfficacyOverlay = this.debug.skillEfficacyOverlay;
        copy.debug.spawnabilityOverlay = this.debug.spawnabilityOverlay;
        copy.debug.lightOverlayPerfWarned = this.debug.lightOverlayPerfWarned;
        copy.debug.spawnabilityOverlayPerfWarned = this.debug.spawnabilityOverlayPerfWarned;

        // Visualizers
        copy.visualizers.heatmaps.putAll(this.visualizers.heatmaps);
        copy.visualizers.heatmapOpacity = this.visualizers.heatmapOpacity;
        copy.visualizers.verticalLevels = this.visualizers.verticalLevels;
        copy.visualizers.safeSpots = this.visualizers.safeSpots;
        copy.visualizers.renderDistance = this.visualizers.renderDistance;

        // Combat
        copy.combat.headMultiplier = this.combat.headMultiplier;
        copy.combat.bodyMultiplier = this.combat.bodyMultiplier;
        copy.combat.legsMultiplier = this.combat.legsMultiplier;
        copy.combat.armorPenetrationBase = this.combat.armorPenetrationBase;

        // Telemetry
        copy.telemetry.autoExport = this.telemetry.autoExport;
        copy.telemetry.exportFormat = this.telemetry.exportFormat;
        copy.telemetry.dataRetentionDays = this.telemetry.dataRetentionDays;
        copy.telemetry.recordSessions = this.telemetry.recordSessions;

        // Onboarding
        copy.onboarding.hasSeenWelcome = this.onboarding.hasSeenWelcome;
        copy.onboarding.tutorialCompleted = this.onboarding.tutorialCompleted;
        copy.onboarding.hasSeenEnduranceIntro = this.onboarding.hasSeenEnduranceIntro;

        return copy;
    }

    /**
     * Resets all settings to defaults (preserves onboarding state).
     */
    public void resetToDefaults() {
        this.general = new GeneralSettings();
        this.debug = new DebugSettings();
        this.visualizers = new VisualizerSettings();
        this.combat = new CombatSettings();
        this.telemetry = new TelemetrySettings();
        // Note: onboarding is intentionally NOT reset to preserve welcome screen state
    }

    /**
     * Resets EVERYTHING including onboarding/tutorial state.
     * Use this for a complete fresh start experience.
     */
    public void resetAll() {
        resetToDefaults();
        this.onboarding = new OnboardingSettings();
    }
}

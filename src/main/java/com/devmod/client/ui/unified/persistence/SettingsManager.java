package com.devmod.client.ui.unified.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;

import com.devmod.ModConfig;
import com.devmod.client.overlay.BossPhaseOverlay;
import com.devmod.client.overlay.EntityDensityOverlay;
import com.devmod.client.overlay.PartyHudOverlay;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.rendering.HeatmapVisualizer;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.LineOfSightVisualizer;
import com.devmod.client.rendering.PathfindingDebugger;
import com.devmod.client.rendering.RoomBoundsVisualizer;
import com.devmod.rendering.HeatmapType;
import com.devmod.util.ConfigPaths;

public class SettingsManager {
    public static final SettingsManager INSTANCE = new SettingsManager();
    private static final Logger LOGGER = LogUtils.getLogger();


    private final Gson gson;
    private SettingsData currentSettings;
    private boolean dirty = false;

    // Async executor for file I/O operations - prevents main thread blocking
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DevMod-Settings-Save");
        t.setDaemon(true);
        return t;
    });

    private SettingsManager() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();
        this.currentSettings = new SettingsData();
    }

    /**
     * Gets the current settings data.
     */
    public SettingsData getSettings() {
        return currentSettings;
    }

    /**
     * Marks settings as modified (will save on next save call).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Returns true if settings have unsaved changes.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Loads settings from disk. Should be called during mod initialization.
     */
    public void load() {
        Path configPath = getConfigPath();

        if (!Files.exists(configPath)) {
            LOGGER.info("[DevMod] No settings file found, using defaults");
            currentSettings = new SettingsData();
            applyToSystems();
            return;
        }

        try {
            String json = Files.readString(configPath);
            SettingsData loaded = gson.fromJson(json, SettingsData.class);

            if (loaded != null) {
                // Handle version migration if needed
                if (loaded.version < currentSettings.version) {
                    LOGGER.info("[DevMod] Migrating settings from v{} to v{}", loaded.version, currentSettings.version);
                    migrateSettings(loaded);
                } else {
                    // Always ensure nested objects are initialized (Gson may leave them null)
                    ensureNestedObjectsInitialized(loaded);
                }

                currentSettings = loaded;
                applyToSystems();
                LOGGER.info("[DevMod] Settings loaded successfully");
            }
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to load settings: {}", e.getMessage());
            tryLoadFromBackup(configPath);
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to parse settings: {}", e.getMessage());
            // Try to recover from backup
            tryLoadFromBackup(configPath);
        }

        dirty = false;
    }

    /**
     * Saves current settings to disk.
     * ASYNC: Runs on background thread to prevent main thread blocking/freezing.
     */
    public void save() {
        // First sync from systems to ensure we have latest state
        syncFromSystems();

        // Capture JSON snapshot for async save
        final String jsonSnapshot = gson.toJson(currentSettings);
        final Path configPath = ConfigPaths.getSettingsFile();

        // Submit async save task - does NOT block main thread.
        // saveExecutor is single-threaded, so overlapping saves serialize in submission
        // order and the newest snapshot wins; skipping the submit would drop those edits.
        saveExecutor.execute(() -> {
            try {
                // Create config directory if needed
                Files.createDirectories(configPath.getParent());

                // Create rolling backup of existing file before overwriting (for recovery from corruption)
                // Keeps 3 backup files: .backup.1, .backup.2, .backup.3 (newest to oldest)
                if (Files.exists(configPath)) {
                    rotateBackups(configPath);
                }

                Files.writeString(configPath, jsonSnapshot);
                dirty = false;
                LOGGER.debug("[DevMod] Settings saved successfully (async)");
            } catch (IOException e) {
                LOGGER.error("[DevMod] Failed to save settings: {}", e.getMessage());
            }
        });
    }

    /**
     * Applies current settings to all mod systems.
     */
    public void applyToSystems() {
        // === General Settings ===
        ModConfig.setShowOverlay(currentSettings.general.showOverlay);
        ModConfig.setShowRender(currentSettings.general.showRender);
        ModConfig.setRenderAsBlocks(currentSettings.general.renderAsBlocks);
        ModConfig.setFollowRangeColor(currentSettings.general.followRangeColor);
        PartyHudOverlay.setEnabled(currentSettings.general.partyHudEnabled);

        // === Debug Overlays ===
        if (currentSettings.debug.debugRenderer) {
            DebugRenderer.INSTANCE.enable();
        } else {
            DebugRenderer.INSTANCE.disable();
        }

        LightLevelOverlay.INSTANCE.setEnabled(currentSettings.debug.lightLevels);
        LineOfSightVisualizer.INSTANCE.setEnabled(currentSettings.debug.lineOfSight);
        PathfindingDebugger.INSTANCE.setEnabled(currentSettings.debug.pathfinding);
        RoomBoundsVisualizer.INSTANCE.setEnabled(currentSettings.debug.roomBounds);
        RoomBoundsVisualizer.INSTANCE.setShowGaps(currentSettings.debug.showRoomGaps);
        BossPhaseOverlay.setEnabled(currentSettings.debug.bossPhaseOverlay);
        EntityDensityOverlay.setEnabled(currentSettings.debug.entityDensityOverlay);
        com.devmod.client.overlay.SkillEfficacyOverlay.setEnabled(currentSettings.debug.skillEfficacyOverlay);
        com.devmod.client.rendering.SpawnabilityOverlay.INSTANCE.setEnabled(currentSettings.debug.spawnabilityOverlay);

        // === Visualizers (Heatmaps) ===
        for (HeatmapType type : HeatmapType.values()) {
            boolean enabled = currentSettings.visualizers.isHeatmapEnabled(type);
            HeatmapVisualizer.INSTANCE.setEnabled(type, enabled);
        }

        LOGGER.debug("[DevMod] Settings applied to all systems");
    }

    /**
     * Syncs current state from all mod systems to settings data.
     */
    public void syncFromSystems() {
        // === General Settings ===
        currentSettings.general.showOverlay = ModConfig.isShowOverlay();
        currentSettings.general.showRender = ModConfig.isShowRender();
        currentSettings.general.renderAsBlocks = ModConfig.isRenderAsBlocks();
        currentSettings.general.followRangeColor = ModConfig.getFollowRangeColor();
        currentSettings.general.partyHudEnabled = PartyHudOverlay.isEnabled();

        // === Debug Overlays ===
        currentSettings.debug.debugRenderer = DebugRenderer.INSTANCE.isEnabled();
        currentSettings.debug.lightLevels = LightLevelOverlay.INSTANCE.isEnabled();
        currentSettings.debug.lineOfSight = LineOfSightVisualizer.INSTANCE.isEnabled();
        currentSettings.debug.pathfinding = PathfindingDebugger.INSTANCE.isEnabled();
        currentSettings.debug.roomBounds = RoomBoundsVisualizer.INSTANCE.isEnabled();
        currentSettings.debug.showRoomGaps = RoomBoundsVisualizer.INSTANCE.isShowingGaps();
        currentSettings.debug.bossPhaseOverlay = BossPhaseOverlay.isEnabled();
        currentSettings.debug.entityDensityOverlay = EntityDensityOverlay.isEnabled();
        currentSettings.debug.skillEfficacyOverlay = com.devmod.client.overlay.SkillEfficacyOverlay.isEnabled();
        currentSettings.debug.spawnabilityOverlay = com.devmod.client.rendering.SpawnabilityOverlay.INSTANCE.isEnabled();

        // === Visualizers (Heatmaps) ===
        for (HeatmapType type : HeatmapType.values()) {
            currentSettings.visualizers.setHeatmapEnabled(type, HeatmapVisualizer.INSTANCE.isEnabled(type));
        }
    }

    /**
     * Resets all settings to defaults and applies them.
     * Preserves onboarding/tutorial state.
     */
    public void resetToDefaults() {
        currentSettings.resetToDefaults();
        applyToSystems();
        markDirty();
    }

    /**
     * Performs a COMPLETE reset of DevMod - returns to first-time user experience.
     * Resets:
     * - All settings (debug, visualizers, combat, telemetry)
     * - Onboarding/Tutorial state (will show welcome screen again)
     * - Tester profile (XP, achievements, badges)
     * - Quest data
     * - Endurance quest records
     *
     * Use this when you want to experience the mod as a new user again.
     */
    public void resetAll() {
        LOGGER.info("[DevMod] Performing complete reset...");

        // Reset settings including onboarding
        currentSettings.resetAll();
        applyToSystems();

        // Reset TesterProfile
        try {
            com.devmod.testing.TesterProfile.INSTANCE.reset();
            LOGGER.debug("[DevMod] TesterProfile reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset TesterProfile: {}", e.getMessage());
        }

        // Reset TutorialManager
        try {
            com.devmod.client.testing.TutorialManager.INSTANCE.reset();
            LOGGER.debug("[DevMod] TutorialManager reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset TutorialManager: {}", e.getMessage());
        }

        // Reset QuestManager
        try {
            com.devmod.quest.QuestManager.INSTANCE.clearAllQuests();
            LOGGER.debug("[DevMod] QuestManager reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset QuestManager: {}", e.getMessage());
        }

        // Reset ItemEditorDataManager (presets, favorites, history)
        try {
            com.devmod.client.ui.editor.ItemEditorDataManager.INSTANCE.resetAll();
            LOGGER.debug("[DevMod] ItemEditorDataManager reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset ItemEditorDataManager: {}", e.getMessage());
        }

        // Reset EnduranceQuestManager (player stats, rewards, gamification)
        try {
            com.devmod.endurance.EnduranceQuestManager.INSTANCE.clearAllPlayerStats();
            LOGGER.debug("[DevMod] EnduranceQuestManager reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset EnduranceQuestManager: {}", e.getMessage());
        }

        // Reset TestingSession (QA testing quests)
        try {
            com.devmod.client.testing.TestingSession.INSTANCE.resetSession();
            LOGGER.debug("[DevMod] TestingSession reset");
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Could not reset TestingSession: {}", e.getMessage());
        }

        // Delete config files to ensure fresh start
        deleteConfigFile(ConfigPaths.getTesterProfileFile());
        deleteConfigFile(ConfigPaths.getTesterProgressFile());
        deleteConfigFile(ConfigPaths.getQuestDataFile());
        deleteConfigFile(ConfigPaths.getTutorialFile());
        deleteConfigFile(ConfigPaths.getQASessionFile());
        // DamageStatistics owns its own file since the counters stopped being
        // embedded in tester_progress.json; without this the damage totals
        // survive a "complete reset".
        deleteConfigFile(ConfigPaths.getDamageStatisticsFile());

        markDirty();
        save();

        LOGGER.info("[DevMod] Complete reset done! Restart the game for full effect.");
    }

    /**
     * Safely deletes a config file if it exists.
     */
    private void deleteConfigFile(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                LOGGER.debug("[DevMod] Deleted config file: {}", path.getFileName());
            }
        } catch (IOException e) {
            LOGGER.warn("[DevMod] Could not delete {}: {}", path.getFileName(), e.getMessage());
        }
    }

    /**
     * Exports current settings to a file for sharing.
     */
    public void exportToFile(Path path) {
        syncFromSystems();
        try {
            String json = gson.toJson(currentSettings);
            Files.writeString(path, json);
            LOGGER.info("[DevMod] Settings exported to {}", path);
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export settings: {}", e.getMessage());
        }
    }

    /**
     * Imports settings from a file.
     */
    public boolean importFromFile(Path path) {
        try {
            String json = Files.readString(path);
            SettingsData imported = gson.fromJson(json, SettingsData.class);

            if (imported != null) {
                currentSettings = imported;
                applyToSystems();
                markDirty();
                LOGGER.info("[DevMod] Settings imported from {}", path);
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to import settings: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to parse imported settings: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Gets the config file path.
     */
    private Path getConfigPath() {
        return ConfigPaths.getSettingsFile();
    }

    /**
     * Rotates backup files to maintain rolling backups.
     * Keeps 3 backup files: .backup.1 (newest), .backup.2, .backup.3 (oldest)
     */
    private void rotateBackups(Path configPath) {
        try {
            // Delete oldest backup (.backup.3)
            Path backup3 = configPath.resolveSibling("settings.json.backup.3");
            if (Files.exists(backup3)) {
                Files.delete(backup3);
            }

            // Rotate .backup.2 -> .backup.3
            Path backup2 = configPath.resolveSibling("settings.json.backup.2");
            if (Files.exists(backup2)) {
                Files.move(backup2, backup3, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Rotate .backup.1 -> .backup.2
            Path backup1 = configPath.resolveSibling("settings.json.backup.1");
            if (Files.exists(backup1)) {
                Files.move(backup1, backup2, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Copy current file to .backup.1
            Files.copy(configPath, backup1, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            LOGGER.debug("[DevMod] Backup rotation completed");
        } catch (IOException e) {
            LOGGER.warn("[DevMod] Could not rotate backups: {}", e.getMessage());
        }
    }

    /**
     * Attempts to load settings from backup files when main file is corrupted.
     * Tries backups in order: .backup.1, .backup.2, .backup.3
     */
    private void tryLoadFromBackup(Path mainConfigPath) {
        // Try each backup in order (newest to oldest)
        for (int i = 1; i <= 3; i++) {
            Path backupPath = mainConfigPath.resolveSibling("settings.json.backup." + i);
            if (!Files.exists(backupPath)) {
                continue;
            }

            try {
                String json = Files.readString(backupPath);
                SettingsData loaded = gson.fromJson(json, SettingsData.class);

                if (loaded != null) {
                    ensureNestedObjectsInitialized(loaded);
                    currentSettings = loaded;
                    applyToSystems();
                    LOGGER.info("[DevMod] Settings recovered from backup.{} successfully!", i);
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Backup.{} also corrupted: {}", i, e.getMessage());
            }
        }

        LOGGER.info("[DevMod] No valid backup found, using defaults");
    }

    /**
     * Migrates settings from older versions.
     */
    private void migrateSettings(SettingsData old) {
        // Ensure all nested objects are initialized (handles null from Gson deserialization)
        ensureNestedObjectsInitialized(old);

        // Future migration logic goes here
        // For now, just update version
        old.version = 1;
    }

    /**
     * Ensures all nested objects in SettingsData are initialized.
     * This fixes null pointer issues when Gson deserializes JSON missing certain fields.
     */
    private void ensureNestedObjectsInitialized(SettingsData data) {
        if (data.general == null) {
            data.general = new SettingsData.GeneralSettings();
        }
        if (data.debug == null) {
            data.debug = new SettingsData.DebugSettings();
        }
        if (data.visualizers == null) {
            data.visualizers = new SettingsData.VisualizerSettings();
        }
        if (data.combat == null) {
            data.combat = new SettingsData.CombatSettings();
        }
        if (data.telemetry == null) {
            data.telemetry = new SettingsData.TelemetrySettings();
        }
        if (data.onboarding == null) {
            data.onboarding = new SettingsData.OnboardingSettings();
        }
    }
}

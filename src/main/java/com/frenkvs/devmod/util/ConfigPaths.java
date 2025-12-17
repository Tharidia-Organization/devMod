package com.frenkvs.devmod.util;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Centralized path management for all DevMod configuration files.
 * Uses FMLPaths to ensure paths work correctly in both dev and production environments.
 *
 * Why this class exists:
 * - Path.of("config/devmod") depends on working directory (breaks in production)
 * - Paths.get("run/config/...") only works in dev environment
 * - FMLPaths.CONFIGDIR.get() always returns the correct config directory
 */
public final class ConfigPaths {

    private ConfigPaths() {}

    // ==================== CONFIG DIRECTORIES ====================

    /**
     * Main DevMod config directory: config/devmod/
     */
    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("devmod");
    }

    /**
     * Test templates directory: config/devmod/test_templates/
     */
    public static Path getTestTemplatesDir() {
        return getConfigDir().resolve("test_templates");
    }

    /**
     * Mob configs directory: config/devmod/mob_configs/
     */
    public static Path getMobConfigsDir() {
        return getConfigDir().resolve("mob_configs");
    }

    /**
     * Mob configurations file: config/devmod/mob_configs.json
     */
    public static Path getMobConfigsFile() {
        return getConfigDir().resolve("mob_configs.json");
    }

    // ==================== SETTINGS FILES ====================

    /**
     * Main settings file: config/devmod/settings.json
     */
    public static Path getSettingsFile() {
        return getConfigDir().resolve("settings.json");
    }

    /**
     * Telemetry settings: config/devmod/telemetry_settings.json
     */
    public static Path getTelemetrySettingsFile() {
        return getConfigDir().resolve("telemetry_settings.json");
    }

    /**
     * Telemetry rooms config: config/devmod/telemetry_rooms.json
     */
    public static Path getTelemetryRoomsFile() {
        return getConfigDir().resolve("telemetry_rooms.json");
    }

    // ==================== QA TESTING FILES ====================

    /**
     * QA session state: config/devmod/qa_session.json
     */
    public static Path getQASessionFile() {
        return getConfigDir().resolve("qa_session.json");
    }

    /**
     * Tutorial progress: config/devmod/tutorial_progress.json
     */
    public static Path getTutorialFile() {
        return getConfigDir().resolve("tutorial_progress.json");
    }

    /**
     * Tester profile (XP, achievements): config/devmod/tester_profile.json
     */
    public static Path getTesterProfileFile() {
        return getConfigDir().resolve("tester_profile.json");
    }

    /**
     * Tester progress statistics: config/devmod/tester_progress.json
     */
    public static Path getTesterProgressFile() {
        return getConfigDir().resolve("tester_progress.json");
    }

    /**
     * Quest data file: config/devmod/quest_data.json
     */
    public static Path getQuestDataFile() {
        return getConfigDir().resolve("quest_data.json");
    }

    /**
     * Damage types config: config/devmod/damage_types.json
     */
    public static Path getDamageTypesFile() {
        return getConfigDir().resolve("damage_types.json");
    }

    /**
     * Damage statistics file: config/devmod/damage_statistics.json
     * Stores combat telemetry data between sessions.
     */
    public static Path getDamageStatisticsFile() {
        return getConfigDir().resolve("damage_statistics.json");
    }

    /**
     * Impact HUD presets file: config/devmod/impact_hud_presets.json
     */
    public static Path getImpactHudPresetsFile() {
        return getConfigDir().resolve("impact_hud_presets.json");
    }

    // ==================== ITEM EDITOR ====================

    /**
     * Item editor data directory: config/devmod/item_editor/
     */
    public static Path getItemEditorDir() {
        return getConfigDir().resolve("item_editor");
    }

    /**
     * Item editor exports directory: config/devmod/item_editor/exports/
     */
    public static Path getItemEditorExportsDir() {
        return getItemEditorDir().resolve("exports");
    }

    /**
     * Weapon whitelist file: config/devmod/weapon_whitelist.json
     */
    public static Path getWeaponWhitelistFile() {
        return getConfigDir().resolve("weapon_whitelist.json");
    }

    /**
     * Weapon blacklist file: config/devmod/weapon_blacklist.json
     */
    public static Path getWeaponBlacklistFile() {
        return getConfigDir().resolve("weapon_blacklist.json");
    }

    // ==================== GAME DIRECTORIES ====================

    /**
     * Game root directory (where saves/, logs/, config/ are located)
     */
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /**
     * QA reports output directory: qa_reports/
     */
    public static Path getQAReportsDir() {
        return getGameDir().resolve("qa_reports");
    }

    /**
     * Telemetry export directory: telemetry/
     */
    public static Path getTelemetryExportDir() {
        return getGameDir().resolve("telemetry");
    }

    /**
     * Latest log file: logs/latest.log
     */
    public static Path getLatestLogFile() {
        return getGameDir().resolve("logs").resolve("latest.log");
    }
}

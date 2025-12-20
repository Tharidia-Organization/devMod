package com.devmod.arena.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the arena template system (feature flags, budgets, alerts).
 *
 * Sources (priority):
 * 1) System properties (devmod.arena.*)
 * 2) Environment variables (DEVMOD_ARENA_*)
 * 3) Code defaults
 *
 * Note: This is a lightweight holder; integration points (builder/registry/alerts)
 * can consume the snapshot for consistency during a build.
 */
public class ArenaTemplateConfig {

    // === Defaults ===
    private static final String DEFAULT_TEMPLATE_DIR = "config/devmod/arena_templates/";
    private static final boolean DEFAULT_INSTANCE_ONLY = true;
    private static final boolean DEFAULT_ARENA_TEMPLATE_ENABLED = true;
    private static final boolean DEFAULT_ROUTING_ENABLED = true;
    private static final boolean DEFAULT_GAMIFICATION_ENABLED = false;
    private static final int DEFAULT_MAX_BLOCKS = 8000;
    private static final int DEFAULT_MAX_BUILD_TIME_MS = 5000;
    private static final int DEFAULT_BOSS_MAX_BLOCKS = 100000;
    private static final int DEFAULT_BOSS_MAX_BUILD_TIME_MS = 15000;
    private static final String DEFAULT_AUTOSMOKE_CRON = "0 3 * * *";
    private static final String DEFAULT_AUTOSMOKE_TZ = "UTC";
    private static final String DEFAULT_STRUCTURE_MANIFEST = "config/devmod/structures_manifest.json";
    private static final long DEFAULT_STRUCTURE_MAX_FILE_SIZE = 512 * 1024;
    private static final int DEFAULT_STRUCTURE_MAX_BLOCKS = 100_000;
    private static final int DEFAULT_STRUCTURE_MAX_ENTITIES = 50;
    private static final List<String> DEFAULT_STRUCTURE_NAMESPACES = List.of("devmod");
    private static final List<String> DEFAULT_CUSTOM_HAZARD_BUILDERS = List.of();

    // DD6: Policy resolver lock settings
    private static final long DEFAULT_LOCK_TIMEOUT_MS = 5000;
    private static final long DEFAULT_LOCK_CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final long DEFAULT_LOCK_STALE_THRESHOLD_MS = 60 * 1000; // 60 seconds

    private String templateDirectory = DEFAULT_TEMPLATE_DIR;
    private boolean instanceOnly = DEFAULT_INSTANCE_ONLY;
    private boolean arenaTemplateEnabled = DEFAULT_ARENA_TEMPLATE_ENABLED;
    private boolean routingEnabled = DEFAULT_ROUTING_ENABLED;
    private boolean gamificationEnabled = DEFAULT_GAMIFICATION_ENABLED;
    private int defaultMaxBlocks = DEFAULT_MAX_BLOCKS;
    private int defaultMaxBuildTimeMs = DEFAULT_MAX_BUILD_TIME_MS;
    private int bossMaxBlocks = DEFAULT_BOSS_MAX_BLOCKS;
    private int bossMaxBuildTimeMs = DEFAULT_BOSS_MAX_BUILD_TIME_MS;
    private String autosmokeSchedule = DEFAULT_AUTOSMOKE_CRON;
    private String autosmokeTimezone = DEFAULT_AUTOSMOKE_TZ;
    private AlertThresholds alertThresholds = AlertThresholds.defaults();
    private String structureManifestPath = DEFAULT_STRUCTURE_MANIFEST;
    private long structureMaxFileSizeBytes = DEFAULT_STRUCTURE_MAX_FILE_SIZE;
    private int structureMaxBlockCount = DEFAULT_STRUCTURE_MAX_BLOCKS;
    private int structureMaxEntityCount = DEFAULT_STRUCTURE_MAX_ENTITIES;
    private List<String> structureAllowedNamespaces = DEFAULT_STRUCTURE_NAMESPACES;
    private List<String> customHazardBuilders = DEFAULT_CUSTOM_HAZARD_BUILDERS;

    // DD6: Policy resolver lock settings
    private long lockTimeoutMs = DEFAULT_LOCK_TIMEOUT_MS;
    private long lockCleanupIntervalMs = DEFAULT_LOCK_CLEANUP_INTERVAL_MS;
    private long lockStaleThresholdMs = DEFAULT_LOCK_STALE_THRESHOLD_MS;

    public record AlertThresholds(
        int warnBuildMs,
        int errorBuildMs,
        int warnMspt,
        int errorMspt,
        int warnTps,
        int errorTps,
        int warnEntitiesResidual,
        int errorEntitiesResidual,
        int warnBlocksResidual,
        int errorBlocksResidual
    ) {
        public static AlertThresholds defaults() {
            return new AlertThresholds(3000, 8000, 40, 50, 18, 15, 0, 5, 0, 10);
        }
    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {}

    public static ArenaTemplateConfig load() {
        ArenaTemplateConfig cfg = new ArenaTemplateConfig();
        cfg.templateDirectory = getString("devmod.arena.templateDirectory", "DEVMOD_TEMPLATE_DIR", DEFAULT_TEMPLATE_DIR);
        cfg.instanceOnly = getBool("devmod.arena.instanceOnly", "DEVMOD_INSTANCE_ONLY", DEFAULT_INSTANCE_ONLY);
        cfg.arenaTemplateEnabled = getBool("devmod.arena.templateEnabled", "DEVMOD_ARENA_TEMPLATE_ENABLED", DEFAULT_ARENA_TEMPLATE_ENABLED);
        cfg.routingEnabled = getBool("devmod.arena.routingEnabled", "DEVMOD_ROUTING_ENABLED", DEFAULT_ROUTING_ENABLED);
        cfg.gamificationEnabled = getBool("devmod.arena.gamificationEnabled", "DEVMOD_GAMIFICATION_ENABLED", DEFAULT_GAMIFICATION_ENABLED);
        cfg.defaultMaxBlocks = getInt("devmod.arena.maxBlocks", "DEVMOD_ARENA_MAX_BLOCKS", DEFAULT_MAX_BLOCKS);
        cfg.defaultMaxBuildTimeMs = getInt("devmod.arena.maxBuildTimeMs", "DEVMOD_ARENA_MAX_BUILD_MS", DEFAULT_MAX_BUILD_TIME_MS);
        cfg.bossMaxBlocks = getInt("devmod.arena.bossMaxBlocks", "DEVMOD_ARENA_BOSS_MAX_BLOCKS", DEFAULT_BOSS_MAX_BLOCKS);
        cfg.bossMaxBuildTimeMs = getInt("devmod.arena.bossMaxBuildTimeMs", "DEVMOD_ARENA_BOSS_MAX_BUILD_MS", DEFAULT_BOSS_MAX_BUILD_TIME_MS);
        cfg.autosmokeSchedule = getString("devmod.arena.autosmokeSchedule", "DEVMOD_ARENA_AUTOSMOKE_CRON", DEFAULT_AUTOSMOKE_CRON);
        cfg.autosmokeTimezone = getString("devmod.arena.autosmokeTimezone", "DEVMOD_ARENA_AUTOSMOKE_TZ", DEFAULT_AUTOSMOKE_TZ);
        cfg.alertThresholds = AlertThresholds.defaults(); // simple default; can add overrides later
        cfg.structureManifestPath = getString("devmod.arena.structureManifest", "DEVMOD_ARENA_STRUCTURE_MANIFEST", DEFAULT_STRUCTURE_MANIFEST);
        cfg.structureMaxFileSizeBytes = getLong("devmod.arena.structureMaxFileSizeBytes", "DEVMOD_ARENA_STRUCTURE_MAX_SIZE", DEFAULT_STRUCTURE_MAX_FILE_SIZE);
        cfg.structureMaxBlockCount = getInt("devmod.arena.structureMaxBlockCount", "DEVMOD_ARENA_STRUCTURE_MAX_BLOCKS", DEFAULT_STRUCTURE_MAX_BLOCKS);
        cfg.structureMaxEntityCount = getInt("devmod.arena.structureMaxEntityCount", "DEVMOD_ARENA_STRUCTURE_MAX_ENTITIES", DEFAULT_STRUCTURE_MAX_ENTITIES);
        cfg.structureAllowedNamespaces = parseCsv(
            getString("devmod.arena.structureNamespaces", "DEVMOD_ARENA_STRUCTURE_NAMESPACES", ""),
            DEFAULT_STRUCTURE_NAMESPACES
        );
        cfg.customHazardBuilders = parseCsv(
            getString("devmod.arena.customHazards", "DEVMOD_ARENA_CUSTOM_HAZARDS", ""),
            DEFAULT_CUSTOM_HAZARD_BUILDERS
        );
        cfg.lockTimeoutMs = getLong("devmod.arena.lockTimeoutMs", "DEVMOD_ARENA_LOCK_TIMEOUT_MS", DEFAULT_LOCK_TIMEOUT_MS);
        cfg.lockCleanupIntervalMs = getLong("devmod.arena.lockCleanupIntervalMs", "DEVMOD_ARENA_LOCK_CLEANUP_INTERVAL_MS", DEFAULT_LOCK_CLEANUP_INTERVAL_MS);
        cfg.lockStaleThresholdMs = getLong("devmod.arena.lockStaleThresholdMs", "DEVMOD_ARENA_LOCK_STALE_THRESHOLD_MS", DEFAULT_LOCK_STALE_THRESHOLD_MS);
        return cfg;
    }

    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Feature flag chain
        if (routingEnabled && !arenaTemplateEnabled) {
            errors.add("routingEnabled=true requires arenaTemplateEnabled=true");
        }
        if (gamificationEnabled && !arenaTemplateEnabled) {
            errors.add("gamificationEnabled=true requires arenaTemplateEnabled=true");
        }
        if (arenaTemplateEnabled && !instanceOnly) {
            warnings.add("arenaTemplateEnabled=true with instanceOnly=false (legacy overworld allowed)");
        }

        // Threshold sanity
        if (alertThresholds.warnBuildMs() >= alertThresholds.errorBuildMs()) {
            errors.add("warnBuildMs must be < errorBuildMs");
        }
        if (alertThresholds.warnMspt() >= alertThresholds.errorMspt()) {
            errors.add("warnMspt must be < errorMspt");
        }
        if (alertThresholds.warnTps() <= alertThresholds.errorTps()) {
            errors.add("warnTps must be > errorTps");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    public ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
            Path.of(templateDirectory),
            instanceOnly,
            arenaTemplateEnabled,
            routingEnabled,
            gamificationEnabled,
            defaultMaxBlocks,
            defaultMaxBuildTimeMs,
            bossMaxBlocks,
            bossMaxBuildTimeMs,
            autosmokeSchedule,
            autosmokeTimezone,
            alertThresholds,
            structureManifestPath,
            structureMaxFileSizeBytes,
            structureMaxBlockCount,
            structureMaxEntityCount,
            structureAllowedNamespaces,
            customHazardBuilders,
            lockTimeoutMs,
            lockCleanupIntervalMs,
            lockStaleThresholdMs
        );
    }

    public record ConfigSnapshot(
        Path templateDirectory,
        boolean instanceOnly,
        boolean arenaTemplateEnabled,
        boolean routingEnabled,
        boolean gamificationEnabled,
        int defaultMaxBlocks,
        int defaultMaxBuildTimeMs,
        int bossMaxBlocks,
        int bossMaxBuildTimeMs,
        String autosmokeSchedule,
        String autosmokeTimezone,
        AlertThresholds alertThresholds,
        String structureManifestPath,
        long structureMaxFileSizeBytes,
        int structureMaxBlockCount,
        int structureMaxEntityCount,
        List<String> structureAllowedNamespaces,
        List<String> customHazardBuilders,
        long lockTimeoutMs,
        long lockCleanupIntervalMs,
        long lockStaleThresholdMs
    ) {}

    // === Helpers ===
    private static String getString(String sys, String env, String def) {
        String s = System.getProperty(sys);
        if (s != null && !s.isBlank()) return s;
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) return e;
        return def;
    }

    private static boolean getBool(String sys, String env, boolean def) {
        String s = System.getProperty(sys);
        if (s != null) return Boolean.parseBoolean(s);
        String e = System.getenv(env);
        if (e != null) return Boolean.parseBoolean(e);
        return def;
    }

    private static int getInt(String sys, String env, int def) {
        String s = System.getProperty(sys);
        if (s != null && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) {
            try {
                return Integer.parseInt(e);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static long getLong(String sys, String env, long def) {
        String s = System.getProperty(sys);
        if (s != null && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {}
        }
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) {
            try {
                return Long.parseLong(e);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    // === Getters/Setters (if needed externally) ===
    public String templateDirectory() { return templateDirectory; }
    public boolean instanceOnly() { return instanceOnly; }
    public boolean arenaTemplateEnabled() { return arenaTemplateEnabled; }
    public boolean routingEnabled() { return routingEnabled; }
    public boolean gamificationEnabled() { return gamificationEnabled; }
    public int defaultMaxBlocks() { return defaultMaxBlocks; }
    public int defaultMaxBuildTimeMs() { return defaultMaxBuildTimeMs; }
    public int bossMaxBlocks() { return bossMaxBlocks; }
    public int bossMaxBuildTimeMs() { return bossMaxBuildTimeMs; }
    public String autosmokeSchedule() { return autosmokeSchedule; }
    public String autosmokeTimezone() { return autosmokeTimezone; }
    public AlertThresholds alertThresholds() { return alertThresholds; }
    public String structureManifestPath() { return structureManifestPath; }
    public long structureMaxFileSizeBytes() { return structureMaxFileSizeBytes; }
    public int structureMaxBlockCount() { return structureMaxBlockCount; }
    public int structureMaxEntityCount() { return structureMaxEntityCount; }
    public List<String> structureAllowedNamespaces() { return structureAllowedNamespaces; }
    public List<String> customHazardBuilders() { return customHazardBuilders; }
    public long lockTimeoutMs() { return lockTimeoutMs; }
    public long lockCleanupIntervalMs() { return lockCleanupIntervalMs; }
    public long lockStaleThresholdMs() { return lockStaleThresholdMs; }

    private static List<String> parseCsv(String csv, List<String> def) {
        if (csv == null || csv.isBlank()) return def;
        String[] parts = csv.split(",");
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? def : result;
    }
}

package com.devmod.arena.registry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.FeatureFlagManager;
import com.devmod.arena.config.FeatureFlagRegistry;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;

public class TemplateRegistryBootstrap implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateRegistryBootstrap.class);
    private static final long WATCHER_DEBOUNCE_MS = 250L;

    private Path templateDirectory;
    private TemplateValidator.ValidationMode validationMode;
    private final ArenaTemplateRegistry registry;
    private final ArenaTelemetry telemetry;
    private final FeatureFlagManager featureFlagManager;
    private ArenaTemplateConfig config;
    private ArenaTemplateConfig.ConfigSnapshot configSnapshot;
    private final AtomicBoolean watcherReloadInProgress = new AtomicBoolean(false);
    @Nullable
    private TemplateDirectoryWatcher watcher;

    @Nullable
    private TemplateLoader.LoadResult lastLoadResult;

    private TemplateRegistryBootstrap(ArenaTelemetry telemetry,
                                      Path templateDirectory,
                                      TemplateValidator.ValidationMode validationMode,
                                      ArenaTemplateConfig config,
                                      ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this.telemetry = telemetry;
        this.templateDirectory = templateDirectory;
        this.validationMode = validationMode;
        this.config = config;
        this.configSnapshot = configSnapshot;
        this.registry = new ArenaTemplateRegistry(telemetry, InstanceLimitConfig.load().toLimits(), configSnapshot);
        if (configSnapshot != null && configSnapshot.customHazardBuilders() != null) {
            configSnapshot.customHazardBuilders().forEach(CustomHazardRegistry.getInstance()::register);
        }
        FeatureFlagRegistry flagRegistry = FeatureFlagRegistry.fromConfig(config, (flagId, oldValue, newValue) ->
            telemetry.emit("arena.feature_flag.changed", Map.of(
                "flagId", flagId,
                "oldValue", oldValue,
                "newValue", newValue
            )));
        this.featureFlagManager = new FeatureFlagManager(flagRegistry);
    }

    /**
    * Creates a bootstrap instance using env/sysprops for directory and validation mode.
    */
    public static TemplateRegistryBootstrap createDefault(ArenaTelemetry telemetry) {
        ArenaTemplateConfig config = ArenaTemplateConfig.load();
        ArenaTemplateConfig.ValidationResult cfgValidation = config.validate();
        if (!cfgValidation.valid()) {
            cfgValidation.errors().forEach(err -> LOGGER.error("ArenaTemplateConfig error: {}", err));
        }
        cfgValidation.warnings().forEach(w -> LOGGER.warn("ArenaTemplateConfig warning: {}", w));

        Path dir = resolveTemplateDirectory(config);

        TemplateValidator.ValidationMode mode = TemplateValidator.fromString(
            config.schemaValidationMode(),
            TemplateValidator.ValidationMode.STRICT
        );

        TemplateRegistryBootstrap bootstrap = new TemplateRegistryBootstrap(telemetry, dir, mode, config, config.snapshot());
        bootstrap.applyConfig(config); // notify listeners and refresh registry snapshot immediately
        return bootstrap;
    }

    /**
     * Loads templates from directory into the registry. Returns the raw load result.
     */
    public TemplateLoader.LoadResult initialize() {
        LOGGER.info("Loading arena templates from {} (mode={})", templateDirectory, validationMode);
        long startTime = System.currentTimeMillis();
        final TemplateLoader.LoadResult result = registry.loadFromDirectory(templateDirectory, validationMode);
        lastLoadResult = result;
        long durationMs = System.currentTimeMillis() - startTime;

        if (!result.success()) {
            LOGGER.error("Loaded {} templates with {} errors", result.templates().size(), result.errors().size());
            result.errors().forEach(err -> LOGGER.error("Template load error: {}", err));
        } else {
            LOGGER.info("Loaded {} templates successfully", result.templates().size());
        }

        // Emit telemetry
        telemetry.emit("arena.bootstrap.initialize", Map.of(
            "success", result.success(),
            "templateCount", result.templates().size(),
            "errorCount", result.errors().size(),
            "durationMs", durationMs,
            "directory", templateDirectory.toString(),
            "validationMode", validationMode.name()
        ));

        return result;
    }

    /**
     * Hot-reloads templates atomically from the directory.
     */
    public ArenaTemplateRegistry.ReloadResult reload() {
        LOGGER.info("Reloading arena templates from {}", templateDirectory);
        return registry.reloadFromDirectoryAtomic(templateDirectory);
    }

    public ArenaTemplateRegistry registry() {
        return registry;
    }

    public FeatureFlagManager featureFlags() {
        return featureFlagManager;
    }

    /**
     * Reloads configuration from disk and reapplies it to all subsystems, then reloads templates.
     * This ensures feature flags, structure validation, and custom hazard registry are updated
     * before performing a template hot-reload.
     */
    public ArenaTemplateRegistry.ReloadResult reloadWithConfig() {
        long startTime = System.currentTimeMillis();
        ArenaTemplateConfig newConfig = ArenaTemplateConfig.load();
        ArenaTemplateConfig.ValidationResult validation = newConfig.validate();
        if (!validation.valid()) {
            validation.errors().forEach(err -> LOGGER.error("ArenaTemplateConfig error: {}", err));
        }
        validation.warnings().forEach(w -> LOGGER.warn("ArenaTemplateConfig warning: {}", w));

        applyConfig(newConfig);
        ArenaTemplateRegistry.ReloadResult result = registry.reloadFromDirectoryAtomic(templateDirectory);
        long durationMs = System.currentTimeMillis() - startTime;

        // Emit telemetry
        telemetry.emit("arena.bootstrap.reload", Map.of(
            "success", result.success(),
            "loadedCount", result.loadedCount(),
            "errorCount", result.errors().size(),
            "durationMs", durationMs,
            "configValid", validation.valid(),
            "source", "command"
        ));

        return result;
    }

    public TemplateValidator.ValidationMode validationMode() {
        return validationMode;
    }

    public Path templateDirectory() {
        return templateDirectory;
    }

    public ArenaTemplateConfig.ConfigSnapshot configSnapshot() {
        return configSnapshot;
    }

    public ArenaTemplateConfig config() {
        return config;
    }

    /**
     * Applies a new config at runtime: refreshes feature flags and snapshots.
     * Does not re-bootstrap the registry; intended for hot-reload paths.
     */
    public void applyConfig(ArenaTemplateConfig newConfig) {
        this.config = newConfig;
        this.configSnapshot = newConfig.snapshot();
        this.validationMode = TemplateValidator.fromString(
            newConfig.schemaValidationMode(),
            TemplateValidator.ValidationMode.STRICT
        );
        Path resolvedDir = resolveTemplateDirectory(newConfig);
        if (resolvedDir != null && !resolvedDir.equals(this.templateDirectory)) {
            LOGGER.info("Template directory updated: {} -> {}", this.templateDirectory, resolvedDir);
            this.templateDirectory = resolvedDir;
        }
        featureFlagManager.applyConfig(newConfig);
        // Refresh structure validation and custom hazards using new snapshot
        if (configSnapshot.customHazardBuilders() != null) {
            CustomHazardRegistry.getInstance().reset(Set.copyOf(configSnapshot.customHazardBuilders()));
        }
        registry.applyConfigSnapshot(configSnapshot);
        try {
            Path manifestPath = configSnapshot.structureManifestPath() != null
                ? Path.of(configSnapshot.structureManifestPath())
                : Path.of("config/devmod/structures_manifest.json");
            StructureValidationInitializer.configure(
                registry,
                manifestPath,
                Thread.currentThread().getContextClassLoader(),
                configSnapshot
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh structure validation on config reload: {}", e.getMessage());
        }
        refreshWatcher();
    }

    public List<String> lastErrors() {
        return lastLoadResult != null ? lastLoadResult.errors() : List.of();
    }

    /**
     * Returns the full result of the last template load operation.
     * Provides access to templates, errors, and success status.
     * @return the last LoadResult, or null if initialize() has not been called
     */
    @Nullable
    public TemplateLoader.LoadResult getLastLoadResult() {
        return lastLoadResult;
    }

    /**
     * Returns whether the directory watcher is currently active and monitoring for changes.
     * The watcher is enabled via system property {@code devmod.template.watch=true}
     * or environment variable {@code DEVMOD_TEMPLATE_WATCH=true}.
     * @return true if watcher is running, false otherwise
     */
    public boolean isWatcherActive() {
        return watcher != null;
    }

    /**
     * Returns whether a watcher-triggered reload is currently in progress.
     * Useful for diagnostics and avoiding concurrent reload operations.
     * @return true if reload is in progress
     */
    public boolean isReloadInProgress() {
        return watcherReloadInProgress.get();
    }

    @Override
    public void close() {
        closeWatcher();
        try {
            registry.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close ArenaTemplateRegistry: {}", e.getMessage());
        }
    }

    private void refreshWatcher() {
        boolean enabled = resolveWatchEnabled();
        if (!enabled) {
            closeWatcher();
            return;
        }
        if (templateDirectory == null) {
            return;
        }
        if (watcher != null && templateDirectory.equals(watcher.directory())) {
            return;
        }
        closeWatcher();
        watcher = new TemplateDirectoryWatcher(templateDirectory, this::reloadFromWatcher, WATCHER_DEBOUNCE_MS);
        watcher.start();
    }

    private void reloadFromWatcher() {
        if (!watcherReloadInProgress.compareAndSet(false, true)) {
            LOGGER.debug("Watcher reload already in progress, skipping");
            return;
        }
        try {
            LOGGER.info("Template directory change detected, running hot reload");
            long startTime = System.currentTimeMillis();
            ArenaTemplateRegistry.ReloadResult result = reloadFromWatcherInternal();
            long durationMs = System.currentTimeMillis() - startTime;

            // Emit watcher-specific telemetry
            telemetry.emit("arena.bootstrap.reload", Map.of(
                "success", result.success(),
                "loadedCount", result.loadedCount(),
                "errorCount", result.errors().size(),
                "durationMs", durationMs,
                "source", "watcher"
            ));
        } finally {
            watcherReloadInProgress.set(false);
        }
    }

    private ArenaTemplateRegistry.ReloadResult reloadFromWatcherInternal() {
        ArenaTemplateConfig newConfig = ArenaTemplateConfig.load();
        ArenaTemplateConfig.ValidationResult validation = newConfig.validate();
        if (!validation.valid()) {
            validation.errors().forEach(err -> LOGGER.error("ArenaTemplateConfig error: {}", err));
        }
        validation.warnings().forEach(w -> LOGGER.warn("ArenaTemplateConfig warning: {}", w));
        applyConfig(newConfig);
        return registry.reloadFromDirectoryAtomic(templateDirectory);
    }

    private void closeWatcher() {
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
    }

    private static Path resolveTemplateDirectory(ArenaTemplateConfig config) {
        String dirProp = System.getProperty("devmod.template.dir");
        if (dirProp != null && !dirProp.isBlank()) {
            return Path.of(dirProp);
        }
        String dirEnv = System.getenv("DEVMOD_TEMPLATE_DIR");
        if (dirEnv != null && !dirEnv.isBlank()) {
            return Path.of(dirEnv);
        }
        String cfgDir = config != null ? config.templateDirectory() : null;
        if (cfgDir != null && !cfgDir.isBlank()) {
            return Path.of(cfgDir);
        }
        return Path.of("config/devmod/arena_templates/");
    }

    private static boolean resolveWatchEnabled() {
        String prop = System.getProperty("devmod.template.watch");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        String env = System.getenv("DEVMOD_TEMPLATE_WATCH");
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        return false;
    }
}

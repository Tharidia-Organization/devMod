package com.devmod.arena.registry;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.FeatureFlagManager;
import com.devmod.arena.config.FeatureFlagRegistry;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Bootstrap helper to create and load {@link ArenaTemplateRegistry} with sane defaults.
 *
 * <p>Default directory: {@code config/devmod/arena_templates/} (override via
 * sysprop {@code devmod.template.dir} or env {@code DEVMOD_TEMPLATE_DIR}).</p>
 *
 * <p>Validation mode: sysprop {@code devmod.template.validationMode} or env
 * {@code DEVMOD_TEMPLATE_VALIDATION_MODE} (STRICT default).</p>
 */
public class TemplateRegistryBootstrap implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateRegistryBootstrap.class);

    private final Path templateDirectory;
    private final TemplateValidator.ValidationMode validationMode;
    private final ArenaTemplateRegistry registry;
    private final FeatureFlagManager featureFlagManager;
    private ArenaTemplateConfig config;
    private ArenaTemplateConfig.ConfigSnapshot configSnapshot;

    private TemplateLoader.LoadResult lastLoadResult;

    private TemplateRegistryBootstrap(ArenaTelemetry telemetry,
                                      Path templateDirectory,
                                      TemplateValidator.ValidationMode validationMode,
                                      ArenaTemplateConfig config,
                                      ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
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

        String dirProp = System.getProperty("devmod.template.dir");
        String dirEnv = System.getenv("DEVMOD_TEMPLATE_DIR");
        Path dir = Path.of(
            dirProp != null && !dirProp.isBlank() ? dirProp :
                dirEnv != null && !dirEnv.isBlank() ? dirEnv :
                    config.templateDirectory() != null ? config.templateDirectory() : "config/devmod/arena_templates/"
        );

        String modeProp = System.getProperty("devmod.template.validationMode");
        String modeEnv = System.getenv("DEVMOD_TEMPLATE_VALIDATION_MODE");
        TemplateValidator.ValidationMode mode = TemplateValidator.fromString(
            modeProp != null ? modeProp : modeEnv,
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
        lastLoadResult = registry.loadFromDirectory(templateDirectory, validationMode);
        if (!lastLoadResult.success()) {
            LOGGER.error("Loaded {} templates with {} errors", lastLoadResult.templates().size(), lastLoadResult.errors().size());
            lastLoadResult.errors().forEach(err -> LOGGER.error("Template load error: {}", err));
        } else {
            LOGGER.info("Loaded {} templates successfully", lastLoadResult.templates().size());
        }
        return lastLoadResult;
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
        ArenaTemplateConfig newConfig = ArenaTemplateConfig.load();
        ArenaTemplateConfig.ValidationResult validation = newConfig.validate();
        if (!validation.valid()) {
            validation.errors().forEach(err -> LOGGER.error("ArenaTemplateConfig error: {}", err));
        }
        validation.warnings().forEach(w -> LOGGER.warn("ArenaTemplateConfig warning: {}", w));

        applyConfig(newConfig);
        return registry.reloadFromDirectoryAtomic(templateDirectory);
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
    }

    public List<String> lastErrors() {
        return lastLoadResult != null ? lastLoadResult.errors() : List.of();
    }

    @Override
    public void close() {
        try {
            registry.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close ArenaTemplateRegistry: {}", e.getMessage());
        }
    }
}

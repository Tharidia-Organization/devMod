package com.devmod.arena.autosmoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class AutosmokeGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutosmokeGuard.class);

    private static final AutosmokeGuard INSTANCE = new AutosmokeGuard();

    /** Environment variable name for environment detection */
    public static final String ENV_VAR_NAME = "DEVMOD_ENV";

    /** Value that indicates production environment */
    public static final String PRODUCTION_ENV_VALUE = "production";

    /** System property for feature flag */
    public static final String FEATURE_FLAG_PROPERTY = "devmod.autosmoke.enabled";

    /** Default path for .production marker file */
    public static final String DEFAULT_MARKER_PATH = ".production";

    /** Configurable marker file path */
    private Path productionMarkerPath;

    /** Feature flag override (null = use system property) */
    private Boolean featureFlagOverride;

    private AutosmokeGuard() {
        this.productionMarkerPath = Paths.get(DEFAULT_MARKER_PATH);
    }

    public static AutosmokeGuard getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if autosmoke can run in the current environment.
     * ALL THREE checks must pass.
     *
     * @return true if autosmoke is allowed to run
     */
    public boolean canRun() {
        GuardResult result = checkAll();
        return result.allowed();
    }

    /**
     * Performs all checks and returns detailed result
     *
     * @return The guard check result with details
     */
    public GuardResult checkAll() {
        // Check 1: Environment variable
        boolean envCheck = checkEnvironmentVariable();

        // Check 2: Feature flag
        boolean flagCheck = checkFeatureFlag();

        // Check 3: Production marker file
        boolean markerCheck = checkProductionMarker();

        boolean allowed = envCheck && flagCheck && markerCheck;

        GuardResult result = new GuardResult(allowed, envCheck, flagCheck, markerCheck);

        if (!allowed) {
            LOGGER.warn("Autosmoke blocked: {}", result.getBlockReasons());
        } else {
            LOGGER.debug("Autosmoke guard passed all checks");
        }

        return result;
    }

    /**
     * Check 1: Environment variable check
     * Passes if DEVMOD_ENV is NOT set to "production"
     */
    private boolean checkEnvironmentVariable() {
        String envValue = System.getenv(ENV_VAR_NAME);
        if (envValue == null) {
            // No env var set - allow (assume dev environment)
            return true;
        }
        return !PRODUCTION_ENV_VALUE.equalsIgnoreCase(envValue.trim());
    }

    /**
     * Check 2: Feature flag check
     * Passes if devmod.autosmoke.enabled is true (or not set - default true in dev)
     */
    private boolean checkFeatureFlag() {
        // Check override first
        if (featureFlagOverride != null) {
            return featureFlagOverride;
        }

        // Check system property
        String propValue = System.getProperty(FEATURE_FLAG_PROPERTY);
        if (propValue == null) {
            // Not set - default to true in dev
            return true;
        }

        return Boolean.parseBoolean(propValue);
    }

    /**
     * Check 3: Production marker file check
     * Passes if .production file does NOT exist
     */
    private boolean checkProductionMarker() {
        return !Files.exists(productionMarkerPath);
    }

    /**
     * Sets the path for the production marker file
     *
     * @param path The path to the marker file
     */
    public void setProductionMarkerPath(Path path) {
        this.productionMarkerPath = path;
    }

    /**
     * Sets the path for the production marker file
     *
     * @param path The path string to the marker file
     */
    public void setProductionMarkerPath(String path) {
        this.productionMarkerPath = Paths.get(path);
    }

    /**
     * Gets the current production marker path
     */
    public Path getProductionMarkerPath() {
        return productionMarkerPath;
    }

    /**
     * Sets a feature flag override (for testing)
     *
     * @param enabled The override value (null to use system property)
     */
    public void setFeatureFlagOverride(Boolean enabled) {
        this.featureFlagOverride = enabled;
    }

    /**
     * Gets the current environment variable value
     */
    public String getCurrentEnvValue() {
        return System.getenv(ENV_VAR_NAME);
    }

    /**
     * Gets the current feature flag value
     */
    public boolean getCurrentFeatureFlagValue() {
        return checkFeatureFlag();
    }

    /**
     * Checks if the production marker file exists
     */
    public boolean productionMarkerExists() {
        return Files.exists(productionMarkerPath);
    }

    /**
     * Result of guard checks
     */
    public record GuardResult(
        boolean allowed,
        boolean envCheckPassed,
        boolean flagCheckPassed,
        boolean markerCheckPassed
    ) {
        /**
         * Gets a human-readable description of why autosmoke was blocked
         */
        public String getBlockReasons() {
            if (allowed) {
                return "None - autosmoke is allowed";
            }

            StringBuilder reasons = new StringBuilder();
            if (!envCheckPassed) {
                reasons.append("ENV check failed (").append(ENV_VAR_NAME)
                       .append("=production); ");
            }
            if (!flagCheckPassed) {
                reasons.append("Feature flag disabled (")
                       .append(FEATURE_FLAG_PROPERTY).append("=false); ");
            }
            if (!markerCheckPassed) {
                reasons.append(".production marker file exists; ");
            }

            return reasons.toString().trim();
        }

        /**
         * Gets the number of checks that passed
         */
        public int passedCount() {
            int count = 0;
            if (envCheckPassed) count++;
            if (flagCheckPassed) count++;
            if (markerCheckPassed) count++;
            return count;
        }
    }
}

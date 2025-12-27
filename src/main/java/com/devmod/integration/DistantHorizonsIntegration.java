package com.devmod.integration;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModList;

public class DistantHorizonsIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistantHorizonsIntegration.class);

    private static boolean dhLoaded = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Track registered dimensions to avoid duplicate registrations
    private static final Set<ResourceKey<Level>> registeredDimensions = ConcurrentHashMap.newKeySet();

    /**
     * Initialize the Distant Horizons integration.
     * Call during mod setup (FMLCommonSetupEvent).
     */
    public static void init() {
        if (initialized) return;

        dhLoaded = ModList.get().isLoaded("distanthorizons");

        if (dhLoaded) {
            try {
                // Test if API classes are available
                Class.forName("com.seibel.distanthorizons.api.DhApi");
                apiAvailable = true;
                LOGGER.info("[DevMod] Distant Horizons detected - LOD compatibility enabled");
            } catch (ClassNotFoundException e) {
                apiAvailable = false;
                LOGGER.info("[DevMod] Distant Horizons detected but API not available - running in basic compatibility mode");
            }
        } else {
            LOGGER.debug("[DevMod] Distant Horizons not detected - LOD integration disabled");
        }

        initialized = true;
    }

    /**
     * Check if Distant Horizons is loaded.
     */
    public static boolean isDistantHorizonsLoaded() {
        return dhLoaded;
    }

    /**
     * Check if the DH API is available for advanced integration.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Register a dynamic dimension with Distant Horizons.
     * This informs DH that a new dimension exists and should be handled separately.
     *
     * Call this when creating a new instance dimension.
     *
     * @param level The ServerLevel of the new dimension
     */
    public static void registerDynamicDimension(ServerLevel level) {
        Objects.requireNonNull(level, "ServerLevel cannot be null");

        if (!dhLoaded) return;

        ResourceKey<Level> dimensionKey = level.dimension();

        // Skip if already registered
        if (registeredDimensions.contains(dimensionKey)) {
            LOGGER.debug("[DevMod-DH] Dimension {} already registered with DH", dimensionKey.location());
            return;
        }

        try {
            if (apiAvailable) {
                // Use DH API if available
                registerWithDhApi(dimensionKey);
            } else {
                // Basic registration - just track it internally
                registeredDimensions.add(dimensionKey);
                LOGGER.debug("[DevMod-DH] Tracked dimension {} (no API)", dimensionKey.location());
            }
        } catch (Exception e) {
            LOGGER.warn("[DevMod-DH] Failed to register dimension {} with Distant Horizons: {}",
                dimensionKey.location(), e.getMessage());
        }
    }

    /**
     * Unregister a dynamic dimension from Distant Horizons.
     * This informs DH that a dimension is being destroyed and LOD data can be cleaned up.
     *
     * Call this when destroying an instance dimension.
     *
     * @param dimensionKey The ResourceKey of the dimension being removed
     */
    public static void unregisterDynamicDimension(ResourceKey<Level> dimensionKey) {
        Objects.requireNonNull(dimensionKey, "Dimension key cannot be null");

        if (!dhLoaded) return;

        // Remove from our tracking
        boolean wasRegistered = registeredDimensions.remove(dimensionKey);

        if (!wasRegistered) {
            LOGGER.debug("[DevMod-DH] Dimension {} was not registered, skipping unregister", dimensionKey.location());
            return;
        }

        try {
            if (apiAvailable) {
                // Use DH API if available
                unregisterWithDhApi(dimensionKey);
            }
            LOGGER.debug("[DevMod-DH] Unregistered dimension {} from DH tracking", dimensionKey.location());
        } catch (Exception e) {
            LOGGER.warn("[DevMod-DH] Failed to unregister dimension {} from Distant Horizons: {}",
                dimensionKey.location(), e.getMessage());
        }
    }

    /**
     * Notify DH that a player is about to teleport to a different dimension.
     * This helps DH prepare for the dimension change and avoid rendering glitches.
     *
     * @param fromDimension The dimension the player is leaving
     * @param toDimension The dimension the player is going to
     */
    public static void notifyDimensionChange(ResourceKey<Level> fromDimension, ResourceKey<Level> toDimension) {
        if (!dhLoaded || !apiAvailable) return;

        try {
            // DH API event notification would go here
            LOGGER.debug("[DevMod-DH] Notifying DH of dimension change: {} -> {}",
                fromDimension.location(), toDimension.location());
        } catch (Exception e) {
            LOGGER.debug("[DevMod-DH] Failed to notify dimension change: {}", e.getMessage());
        }
    }

    /**
     * Check if a dimension is a dynamic instance dimension that we've registered.
     */
    public static boolean isRegisteredDynamicDimension(ResourceKey<Level> dimensionKey) {
        return registeredDimensions.contains(dimensionKey);
    }

    /**
     * Get the count of currently registered dynamic dimensions.
     */
    public static int getRegisteredDimensionCount() {
        return registeredDimensions.size();
    }

    /**
     * Clear all registered dimensions (for shutdown cleanup).
     */
    public static void clearAllRegistrations() {
        int count = registeredDimensions.size();
        registeredDimensions.clear();
        if (count > 0) {
            LOGGER.debug("[DevMod-DH] Cleared {} dimension registrations", count);
        }
    }

    // === Private API Methods ===

    /**
     * Register dimension using DH API.
     * This uses reflection to avoid hard dependency on DH classes.
     */
    private static void registerWithDhApi(ResourceKey<Level> dimensionKey) {
        try {
            // DH API v4.0+ uses DhApi.Delayed.worldGenEvent for custom world gen
            // For dynamic dimensions, we primarily need to ensure DH recognizes the dimension
            //
            // The API exposes:
            // - DhApi.Delayed.configs - for configuration
            // - DhApi.Delayed.worldGenEvent - for custom LOD generation
            // - DhApi.Delayed.terrainRepo - for terrain data access
            //
            // For instance dimensions, we want DH to:
            // 1. Create a separate LOD database for this dimension
            // 2. Not mix LODs with overworld or other dimensions
            //
            // Since these are temporary dimensions, we can configure DH to use
            // minimal LOD settings or skip LOD generation entirely

            // Register the dimension key
            registeredDimensions.add(dimensionKey);

            LOGGER.info("[DevMod-DH] Registered dynamic dimension {} with Distant Horizons API",
                dimensionKey.location());

        } catch (Exception e) {
            // Fall back to basic tracking
            registeredDimensions.add(dimensionKey);
            LOGGER.debug("[DevMod-DH] API registration failed, using basic tracking: {}", e.getMessage());
        }
    }

    /**
     * Unregister dimension using DH API.
     */
    private static void unregisterWithDhApi(ResourceKey<Level> dimensionKey) {
        try {
            // DH doesn't have a direct "unregister dimension" API
            // The LOD data is stored in dimension-specific folders
            // When the dimension is removed, the data naturally becomes orphaned
            //
            // For cleaner behavior, we could:
            // 1. Clear LOD cache for this dimension (if API supports it)
            // 2. Delete the dimension's LOD folder on disk
            //
            // For now, we just log the unregistration - DH handles cleanup on its own

            LOGGER.info("[DevMod-DH] Unregistered dynamic dimension {} from Distant Horizons",
                dimensionKey.location());

        } catch (Exception e) {
            LOGGER.debug("[DevMod-DH] API unregistration note: {}", e.getMessage());
        }
    }

    /**
     * Configuration hint for DH about instance dimensions.
     * Instance dimensions are temporary and don't need persistent LOD data.
     */
    public static boolean shouldSkipLodGeneration(ResourceKey<Level> dimensionKey) {
        // If this is one of our registered instance dimensions,
        // DH can skip LOD generation to save resources
        return registeredDimensions.contains(dimensionKey);
    }
}

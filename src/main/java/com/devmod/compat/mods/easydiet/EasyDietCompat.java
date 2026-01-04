package com.devmod.compat.mods.easydiet;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Tharidia Easy-Diet mod.
 *
 * <p>Provides integration with the Easy-Diet nutrition system, enabling:
 * <ul>
 *   <li>Reading player diet data (6 nutritional categories)</li>
 *   <li>Querying nutrition balance and well-fed/malnourished state</li>
 *   <li>Adding nutrition from DevMod-configured food items</li>
 *   <li>Integration with Endurance quest system for combat modifiers</li>
 * </ul>
 *
 * <p>Easy-Diet tracks 6 nutritional categories:
 * <ul>
 *   <li>GRAIN - Cereals, bread, pasta</li>
 *   <li>PROTEIN - Meat, fish, eggs</li>
 *   <li>VEGETABLE - Vegetables, greens</li>
 *   <li>FRUIT - Fruits, berries</li>
 *   <li>SUGAR - Sweets, honey</li>
 *   <li>WATER - Drinks, soups</li>
 * </ul>
 *
 * @author DevMod Team (Francesco Caccavo integration)
 * @see EasyDietApi
 */
public class EasyDietCompat implements CompatModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(EasyDietCompat.class);

    /** The mod ID for Tharidia Easy-Diet. */
    public static final String MOD_ID = "tharidia_easydiet";

    /** Whether the mod is loaded and available. */
    private static volatile boolean available = false;

    /** Whether initialization has been attempted. */
    private static volatile boolean initialized = false;

    /** Error message if initialization failed. */
    @Nullable
    private static String initError = null;

    // =========================================================================
    // COMPAT MODULE INTERFACE
    // =========================================================================

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Tharidia Easy-Diet";
    }

    @Override
    public int priority() {
        // P3 - Combat/Attributes (Nutrition Systems)
        // After combat mods, before utility mods
        return 35;
    }

    @Override
    public void initCommon() {
        if (initialized) {
            LOGGER.debug("[Compat:easydiet] Already initialized, skipping");
            return;
        }
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:easydiet] Easy-Diet not found, skipping initialization");
            return;
        }

        try {
            String version = Compat.getVersion(MOD_ID);
            LOGGER.info("[Compat:easydiet] Easy-Diet v{} detected, initializing reflection API...", version);

            // Eagerly initialize reflection to avoid lag spikes on first use during gameplay
            // NOTE: We call this BEFORE setting available=true to ensure atomic initialization
            // FIX Q7: Class verification is now done inside doReflectionInit() - no need to duplicate
            EasyDietApi.initReflectionEager();

            // FIX Q10: Use isReady() boolean method instead of string comparison
            if (EasyDietApi.isReady()) {
                available = true;
                LOGGER.info("[Compat:easydiet] Easy-Diet integration ready");
            } else {
                initError = "Reflection initialization failed: " + EasyDietApi.getReflectionStatus();
                LOGGER.warn("[Compat:easydiet] {}", initError);
            }

        } catch (Exception e) {
            initError = "Initialization failed: " + e.getMessage();
            LOGGER.error("[Compat:easydiet] {}", initError, e);
        }
    }

    @Override
    public void initClient() {
        if (!available) {
            return;
        }
        LOGGER.debug("[Compat:easydiet] Client initialization complete");
    }

    @Override
    public void shutdown() {
        LOGGER.debug("[Compat:easydiet] Shutdown complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Nutrition system integration: diet tracking, combat modifiers, food editor support";
    }

    @Override
    public boolean isActive() {
        return available;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Check if Easy-Diet is available.
     *
     * @return true if Easy-Diet is loaded and API is accessible
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get the initialization error message, if any.
     *
     * @return error message, or null if initialization succeeded
     */
    @Nullable
    public static String getInitError() {
        return initError;
    }

    /**
     * Get status summary for debugging.
     *
     * @return status string
     */
    public static String getStatusSummary() {
        if (!initialized) {
            return "Easy-Diet: not initialized";
        }
        if (!available) {
            return "Easy-Diet: not available" + (initError != null ? " (" + initError + ")" : "");
        }
        return "Easy-Diet: active (v" + Compat.getVersion(MOD_ID) + ")";
    }
}

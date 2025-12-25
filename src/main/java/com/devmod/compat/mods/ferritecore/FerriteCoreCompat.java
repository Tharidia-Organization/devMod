package com.devmod.compat.mods.ferritecore;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility module for FerriteCore.
 *
 * FerriteCore provides:
 * - Memory usage optimizations
 * - BlockState memory deduplication
 * - Reduced memory footprint for models
 * - Optimized property maps
 *
 * This integration allows DevMod to:
 * - Detect FerriteCore presence
 * - Track memory savings (when available)
 * - Include FerriteCore status in telemetry
 *
 * Note: FerriteCore works transparently with minimal API exposure.
 *
 * @see <a href="https://github.com/malte0811/FerriteCore">FerriteCore GitHub</a>
 */
public class FerriteCoreCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(FerriteCoreCompat.class);
    public static final String MOD_ID = "ferritecore";

    private static boolean available = false;
    private static boolean initialized = false;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "FerriteCore";
    }

    @Override
    public int priority() {
        // High priority - memory optimization
        return 41;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:ferritecore] FerriteCore not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:ferritecore] FerriteCore detected");
        LOGGER.debug("[Compat:ferritecore] Version: {}", Compat.getVersion(MOD_ID));

        // FerriteCore has minimal API - mostly works transparently
        LOGGER.info("[Compat:ferritecore] Memory optimizations active");
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:ferritecore] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Memory optimization detection, BlockState deduplication";
    }

    /**
     * Check if FerriteCore is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get FerriteCore performance info for telemetry.
     *
     * @return Map of performance info
     */
    public static Map<String, Object> getPerformanceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!available) {
            return info;
        }

        info.put("available", true);
        info.put("version", Compat.getVersion(MOD_ID));
        info.put("memoryOptimizations", true);

        // FerriteCore optimizations (known features)
        List<String> optimizations = Arrays.asList(
            "blockstate_deduplication",
            "property_map_optimization",
            "model_memory_reduction"
        );
        info.put("knownOptimizations", optimizations);

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "FerriteCore: not available";
        }
        return "FerriteCore: memory optimizations active";
    }
}

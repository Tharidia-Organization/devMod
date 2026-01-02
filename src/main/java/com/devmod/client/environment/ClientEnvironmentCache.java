package com.devmod.client.environment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side cache for environment overrides received from the server.
 * Used by mixins to override visual aspects like time of day.
 */
public final class ClientEnvironmentCache {
    private ClientEnvironmentCache() {}

    /** Cached frozen time per dimension key (string form) */
    private static final Map<String, EnvironmentOverride> OVERRIDES = new ConcurrentHashMap<>();

    /**
     * Sets an environment override for a dimension.
     *
     * @param dimensionKey The dimension key string (e.g., "devmod:arena_1")
     * @param frozenTime The time to freeze at
     * @param biomeId The biome ID (for future use)
     */
    public static void setOverride(String dimensionKey, long frozenTime, String biomeId) {
        OVERRIDES.put(dimensionKey, new EnvironmentOverride(frozenTime, biomeId));
    }

    /**
     * Clears an environment override for a dimension.
     */
    public static void clearOverride(String dimensionKey) {
        OVERRIDES.remove(dimensionKey);
    }

    /**
     * Gets the frozen time override for a dimension.
     *
     * @param dimensionKey The dimension key
     * @return The frozen time, or empty if no override exists
     */
    public static Optional<Long> getTimeOverride(ResourceKey<Level> dimensionKey) {
        String key = dimensionKey.location().toString();
        EnvironmentOverride override = OVERRIDES.get(key);
        if (override != null) {
            return Optional.of(override.frozenTime());
        }
        return Optional.empty();
    }

    /**
     * Gets the frozen time override for a dimension by string key.
     */
    @Nullable
    public static Long getTimeOverride(String dimensionKey) {
        EnvironmentOverride override = OVERRIDES.get(dimensionKey);
        return override != null ? override.frozenTime() : null;
    }

    /**
     * Checks if a dimension has an environment override.
     */
    public static boolean hasOverride(String dimensionKey) {
        return OVERRIDES.containsKey(dimensionKey);
    }

    /**
     * Clears all cached overrides (call on disconnect).
     */
    public static void clearAll() {
        OVERRIDES.clear();
    }

    /**
     * Environment override data.
     */
    public record EnvironmentOverride(long frozenTime, String biomeId) {}
}

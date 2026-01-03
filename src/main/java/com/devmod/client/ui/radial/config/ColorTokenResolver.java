package com.devmod.client.ui.radial.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Resolves color token names from JSON to actual color values from DesignTokens.Radial.
 * Caches lookups for performance.
 *
 * <p>Token names match field names in DesignTokens.Radial (e.g., "CAT_ANALYZE", "ANALYZE_DEBUG").
 * This allows JSON config to reference design tokens by name instead of hardcoding hex values,
 * ensuring consistency with the design system.</p>
 *
 * <p>Usage in JSON config:
 * <pre>
 * { "id": "debug", "colorToken": "ANALYZE_DEBUG" }
 * </pre>
 * </p>
 */
public final class ColorTokenResolver {

    private static final Logger LOGGER = LogManager.getLogger(ColorTokenResolver.class);
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    /** Default color when token is not found (white with full opacity) */
    public static final int DEFAULT_COLOR = DesignTokens.Text.WHITE;

    private ColorTokenResolver() {} // Utility class

    /**
     * Resolve a color token name to its integer ARGB value.
     * Token names match field names in DesignTokens.Radial.
     *
     * @param tokenName The token name (e.g., "CAT_ANALYZE", "ANALYZE_DEBUG")
     * @return The ARGB color value, or DEFAULT_COLOR if not found
     */
    public static int resolve(String tokenName) {
        if (tokenName == null || tokenName.isEmpty()) {
            return DEFAULT_COLOR;
        }

        ensureInitialized();

        Integer cached = CACHE.get(tokenName);
        if (cached != null) {
            return cached;
        }

        // Token not found
        LOGGER.warn("[ColorTokenResolver] Unknown token: {}", tokenName);
        return DEFAULT_COLOR;
    }

    /**
     * Resolve a color from either a hex string or a token name.
     * If the value starts with "0x" or "#", it's parsed as hex.
     * Otherwise, it's treated as a token name.
     *
     * @param colorOrToken Either a hex color (e.g., "#FF4488FF") or a token name (e.g., "ANALYZE_DEBUG")
     * @return The ARGB color value
     */
    public static int resolveColorOrToken(String colorOrToken) {
        if (colorOrToken == null || colorOrToken.isEmpty()) {
            return DEFAULT_COLOR;
        }

        // Check for hex format
        if (colorOrToken.startsWith("0x") || colorOrToken.startsWith("0X")) {
            return parseHex(colorOrToken.substring(2));
        }
        if (colorOrToken.startsWith("#")) {
            return parseHex(colorOrToken.substring(1));
        }

        // Treat as token name
        return resolve(colorOrToken);
    }

    /**
     * Parse a hex color string (without prefix) to an int.
     *
     * @param hex Hex string (e.g., "FF4488FF" for ARGB, "4488FF" for RGB)
     * @return The ARGB color value
     */
    public static int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return DEFAULT_COLOR;
        }

        try {
            // Handle both ARGB (8 chars) and RGB (6 chars) formats
            if (hex.length() == 6) {
                // RGB -> add full opacity
                return DesignTokens.Mask.ALPHA | (int) Long.parseLong(hex, 16);
            } else if (hex.length() == 8) {
                // ARGB
                return (int) Long.parseLong(hex, 16);
            } else {
                LOGGER.warn("[ColorTokenResolver] Invalid hex length: {}", hex);
                return DEFAULT_COLOR;
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("[ColorTokenResolver] Invalid hex color: {}", hex);
            return DEFAULT_COLOR;
        }
    }

    /**
     * Check if a token name is valid (exists in the registry).
     *
     * @param tokenName The token name to check
     * @return true if the token exists
     */
    public static boolean isValidToken(String tokenName) {
        ensureInitialized();
        return tokenName != null && CACHE.containsKey(tokenName);
    }

    /**
     * Get all available token names.
     *
     * @return Set of all valid token names (unmodifiable)
     */
    public static Set<String> availableTokens() {
        ensureInitialized();
        return Set.copyOf(CACHE.keySet());
    }

    /**
     * Get the number of registered tokens.
     *
     * @return Number of available tokens
     */
    public static int tokenCount() {
        ensureInitialized();
        return CACHE.size();
    }

    /**
     * Initialize the cache by reading DesignTokens.Radial via reflection.
     * Thread-safe and idempotent.
     */
    private static void ensureInitialized() {
        if (initialized) return;

        synchronized (ColorTokenResolver.class) {
            if (initialized) return;

            try {
                Class<?> radialClass = DesignTokens.Radial.class;
                for (Field field : radialClass.getDeclaredFields()) {
                    // Only consider public static final int fields
                    int mods = field.getModifiers();
                    if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
                        continue;
                    }
                    if (field.getType() != int.class) {
                        continue;
                    }

                    String name = field.getName();
                    int value = field.getInt(null);
                    CACHE.put(name, value);
                }

                LOGGER.debug("[ColorTokenResolver] Loaded {} color tokens from DesignTokens.Radial", CACHE.size());
            } catch (Exception e) {
                LOGGER.error("[ColorTokenResolver] Failed to initialize: {}", e.getMessage());
            }

            initialized = true;
        }
    }

    /**
     * Force re-initialization (for testing purposes only).
     */
    static void reset() {
        CACHE.clear();
        initialized = false;
    }
}

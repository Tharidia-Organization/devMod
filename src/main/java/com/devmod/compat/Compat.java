package com.devmod.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

public final class Compat {
    private static final Logger LOGGER = LoggerFactory.getLogger(Compat.class);

    // Cache for mod loaded state to avoid repeated ModList queries
    private static final ConcurrentHashMap<String, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> VERSION_CACHE = new ConcurrentHashMap<>();

    private Compat() {
        // Utility class
    }

    /**
     * Check if a mod is loaded.
     * Results are cached for performance.
     *
     * @param modId The mod ID to check
     * @return true if the mod is present and loaded
     */
    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isEmpty()) {
            return false;
        }

        return LOADED_CACHE.computeIfAbsent(modId, id -> {
            try {
                boolean loaded = ModList.get().isLoaded(id);
                if (loaded) {
                    LOGGER.debug("[Compat] Mod detected: {}", id);
                }
                return loaded;
            } catch (Exception e) {
                LOGGER.warn("[Compat] Error checking mod {}: {}", id, e.getMessage());
                return false;
            }
        });
    }

    /**
     * Check if multiple mods are all loaded.
     *
     * @param modIds The mod IDs to check
     * @return true if ALL mods are present
     */
    public static boolean areAllLoaded(String... modIds) {
        for (String modId : modIds) {
            if (!isLoaded(modId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if any of the specified mods are loaded.
     *
     * @param modIds The mod IDs to check
     * @return true if ANY mod is present
     */
    public static boolean isAnyLoaded(String... modIds) {
        for (String modId : modIds) {
            if (isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the version string of a loaded mod.
     *
     * @param modId The mod ID
     * @return The version string, or null if not loaded
     */
    @Nullable
    public static String getVersion(String modId) {
        if (!isLoaded(modId)) {
            return null;
        }

        return VERSION_CACHE.computeIfAbsent(modId, id -> {
            try {
                return ModList.get()
                    .getModContainerById(id)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
            } catch (Exception e) {
                LOGGER.warn("[Compat] Error getting version for {}: {}", id, e.getMessage());
                return "unknown";
            }
        });
    }

    /**
     * Get the version as an Optional.
     */
    public static Optional<String> getVersionOpt(String modId) {
        return Optional.ofNullable(getVersion(modId));
    }

    /**
     * Check if we're running on the client side.
     */
    public static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    /**
     * Check if we're running on the server side.
     */
    public static boolean isServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }

    /**
     * Safely load a class without throwing ClassNotFoundException.
     * Returns null if the class cannot be loaded.
     */
    @Nullable
    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to load class {}: {}", className, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a class exists without loading it.
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className, false, Compat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Execute a runnable only if the specified mod is loaded.
     * Catches and logs any exceptions.
     */
    public static void runIfLoaded(String modId, Runnable action) {
        if (isLoaded(modId)) {
            try {
                action.run();
            } catch (Exception e) {
                LOGGER.error("[Compat:{}] Error executing action: {}", modId, e.getMessage(), e);
            }
        }
    }

    /**
     * Log a compatibility status message.
     */
    public static void logStatus(String modId, String feature, boolean enabled) {
        if (enabled) {
            LOGGER.info("[Compat:{}] {} enabled", modId, feature);
        } else {
            LOGGER.debug("[Compat:{}] {} disabled (mod not present)", modId, feature);
        }
    }

    /**
     * Clear all caches. Useful for testing.
     */
    public static void clearCaches() {
        LOADED_CACHE.clear();
        VERSION_CACHE.clear();
    }

    // === Reflection Helpers ===

    /**
     * Load a class from multiple possible fully-qualified names.
     * Returns the first one found, or null if none exist.
     *
     * @param classNames Possible class names to try
     * @return The loaded class, or null if none found
     */
    @Nullable
    public static Class<?> loadClassFromAny(String... classNames) {
        for (String className : classNames) {
            Class<?> clazz = loadClass(className);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    /**
     * Safely get a method from a class.
     *
     * @param clazz The class to search
     * @param methodName The method name
     * @param parameterTypes The parameter types
     * @return The method, or null if not found
     */
    @Nullable
    public static Method getMethod(@Nullable Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get method {}.{}: {}", clazz.getName(), methodName, e.getMessage());
            return null;
        }
    }

    /**
     * Safely get a declared method from a class (including private methods).
     *
     * @param clazz The class to search
     * @param methodName The method name
     * @param parameterTypes The parameter types
     * @return The method (made accessible), or null if not found
     */
    @Nullable
    public static Method getDeclaredMethod(@Nullable Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get declared method {}.{}: {}", clazz.getName(), methodName, e.getMessage());
            return null;
        }
    }

    /**
     * Safely get a field from a class.
     *
     * @param clazz The class to search
     * @param fieldName The field name
     * @return The field, or null if not found
     */
    @Nullable
    public static Field getField(@Nullable Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get field {}.{}: {}", clazz.getName(), fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Safely get a declared field from a class (including private fields).
     *
     * @param clazz The class to search
     * @param fieldName The field name
     * @return The field (made accessible), or null if not found
     */
    @Nullable
    public static Field getDeclaredField(@Nullable Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        try {
            Field field = clazz.getDeclaredField(fieldName);
            if (!field.canAccess(null) && !field.trySetAccessible()) {
                return null;
            }
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get declared field {}.{}: {}", clazz.getName(), fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Safely invoke a method and return the result.
     *
     * @param method The method to invoke
     * @param instance The instance (null for static methods)
     * @param args The arguments
     * @return The result, or null if invocation failed
     */
    @Nullable
    public static Object invoke(@Nullable Method method, @Nullable Object instance, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(instance, args);
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to invoke {}: {}", method.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Safely get a static field value.
     *
     * @param field The field
     * @return The value, or null if access failed
     */
    @Nullable
    public static Object getStaticFieldValue(@Nullable Field field) {
        if (field == null) return null;
        try {
            return field.get(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get static field {}: {}", field.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Safely get a field value from an instance.
     *
     * @param field The field
     * @param instance The instance
     * @return The value, or null if access failed
     */
    @Nullable
    public static Object getFieldValue(@Nullable Field field, @Nullable Object instance) {
        if (field == null || instance == null) return null;
        try {
            return field.get(instance);
        } catch (Exception e) {
            LOGGER.debug("[Compat] Failed to get field {} from instance: {}", field.getName(), e.getMessage());
            return null;
        }
    }

    // === Common Mod IDs (convenience constants) ===

    public static final class Mods {
        // P1 - UI/Input
        public static final String CLOTH_CONFIG = "cloth_config";
        public static final String CONTROLLING = "controlling";
        public static final String EMI = "emi";
        public static final String JOURNEY_MAP = "journeymap";
        public static final String CURIOS = "curios";
        public static final String ACCESSORIES = "accessories";

        // P2 - Dimension/World
        public static final String DISTANT_HORIZONS = "distanthorizons";
        public static final String C2ME = "c2me";
        public static final String TERRABLENDER = "terrablender";
        public static final String LITTLETILES = "littletiles";

        // P3 - Combat
        public static final String EPIC_FIGHT = "epicfight";
        public static final String PEHKUI = "pehkui";
        public static final String IRONS_SPELLBOOKS = "irons_spellbooks";
        public static final String SPELL_ENGINE = "spell_engine";
        public static final String SPELL_POWER = "spell_power";
        public static final String RANGED_WEAPON_API = "ranged_weapon_api";
        public static final String GECKOLIB = "geckolib";
        public static final String AZURELIB = "azurelib";
        public static final String APOTHIC_ATTRIBUTES = "apothicattributes";
        public static final String PLAYER_ANIMATOR = "playeranimator";

        // P3 - Combat (continued)
        public static final String SMART_BRAIN_LIB = "smartbrainlib";
        public static final String RELICS = "relics";
        public static final String EMOTECRAFT = "emotecraft";

        // P4 - Performance
        public static final String SPARK = "spark";
        public static final String MODERNFIX = "modernfix";
        public static final String FERRITECORE = "ferritecore";
        public static final String LITHIUM = "lithium";
        public static final String SODIUM = "sodium";
        public static final String ENTITY_CULLING = "entityculling";
        public static final String IRIS = "iris";

        // P5 - QoL
        public static final String EASY_NPC = "easy_npc";
        public static final String DUMMMMMMY = "dummmmmmy";
        public static final String MOWZIES_MOBS = "mowziesmobs";

        // P1 - Config (additional)
        public static final String YACL = "yet_another_config_lib_v3";
        public static final String FANCY_MENU = "fancymenu";

        // P3 - Combat (additional)
        public static final String PUFFISH_SKILLS = "puffish_skills";
        public static final String ARCHERS = "archers";
        public static final String PALADINS = "paladins";
        public static final String WIZARDS = "wizards";
        public static final String RUNES = "runes";
        public static final String SHIELD_API = "shield_api";

        private Mods() {}
    }
}

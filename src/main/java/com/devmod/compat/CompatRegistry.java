package com.devmod.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionRegistry;

public final class CompatRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatRegistry.class);

    // All registered modules
    private static final Map<String, CompatModule> MODULES = new ConcurrentHashMap<>();

    // Modules that have been successfully initialized
    private static final Set<String> INITIALIZED_COMMON = ConcurrentHashMap.newKeySet();
    private static final Set<String> INITIALIZED_CLIENT = ConcurrentHashMap.newKeySet();

    // State tracking
    private static boolean commonInitDone = false;
    private static boolean clientInitDone = false;

    private CompatRegistry() {
        // Utility class
    }

    /**
     * Register a compatibility module.
     * Should be called during mod construction (before FMLCommonSetupEvent).
     *
     * @param module The module to register
     */
    public static void register(CompatModule module) {
        Objects.requireNonNull(module, "Module cannot be null");
        String modId = module.modId();

        if (MODULES.containsKey(modId)) {
            LOGGER.warn("[CompatRegistry] Module for {} already registered, replacing", modId);
        }

        MODULES.put(modId, module);
        LOGGER.debug("[CompatRegistry] Registered module: {} ({})", module.displayName(), modId);
    }

    /**
     * Register multiple modules at once.
     */
    public static void registerAll(CompatModule... modules) {
        for (CompatModule module : modules) {
            register(module);
        }
    }

    /**
     * Initialize all modules for common (server + client) side.
     * Call during FMLCommonSetupEvent.
     */
    public static void initCommon() {
        if (commonInitDone) {
            LOGGER.warn("[CompatRegistry] initCommon() called multiple times");
            return;
        }

        LOGGER.info("[CompatRegistry] Initializing {} registered modules...", MODULES.size());

        // Sort by priority
        List<CompatModule> sorted = MODULES.values().stream()
            .sorted(Comparator.comparingInt(CompatModule::priority))
            .collect(Collectors.toList());

        int enabled = 0;
        int skipped = 0;

        for (CompatModule module : sorted) {
            String modId = module.modId();

            // Check if mod is loaded
            if (!Compat.isLoaded(modId)) {
                LOGGER.debug("[Compat:{}] Skipped - mod not present", modId);
                skipped++;
                continue;
            }

            // Check version compatibility
            if (!module.isVersionCompatible()) {
                String minVer = module.getMinVersion();
                String loadedVer = Compat.getVersion(modId);
                LOGGER.warn("[Compat:{}] Skipped - version {} < required {}", modId, loadedVer, minVer);
                skipped++;
                continue;
            }

            // Initialize the module
            try {
                module.initCommon();
                INITIALIZED_COMMON.add(modId);
                enabled++;
                LOGGER.info("[Compat:{}] {} enabled (v{})",
                    modId, module.displayName(), Compat.getVersion(modId));
            } catch (Exception e) {
                LOGGER.error("[Compat:{}] Failed to initialize: {}", modId, e.getMessage(), e);
            }
        }

        commonInitDone = true;
        LOGGER.info("[CompatRegistry] Initialization complete: {} enabled, {} skipped", enabled, skipped);
    }

    /**
     * Initialize all modules for client side.
     * Call during FMLClientSetupEvent.
     */
    public static void initClient() {
        if (clientInitDone) {
            LOGGER.warn("[CompatRegistry] initClient() called multiple times");
            return;
        }

        if (!commonInitDone) {
            LOGGER.warn("[CompatRegistry] initClient() called before initCommon()");
        }

        LOGGER.info("[CompatRegistry] Initializing client for {} modules...", INITIALIZED_COMMON.size());

        int enabled = 0;

        for (String modId : INITIALIZED_COMMON) {
            CompatModule module = MODULES.get(modId);
            if (module == null) continue;

            try {
                module.initClient();
                INITIALIZED_CLIENT.add(modId);
                enabled++;
                LOGGER.debug("[Compat:{}] Client initialized", modId);
            } catch (Exception e) {
                LOGGER.error("[Compat:{}] Failed to initialize client: {}", modId, e.getMessage(), e);
            }
        }

        clientInitDone = true;
        LOGGER.info("[CompatRegistry] Client initialization complete: {} modules", enabled);
    }

    /**
     * Register actions from all initialized modules.
     * Call after initCommon() and when the ActionRegistry is ready.
     *
     * @param registry The action registry
     */
    public static void registerActions(@Nullable ActionRegistry registry) {
        if (registry == null) {
            LOGGER.debug("[CompatRegistry] ActionRegistry is null, skipping action registration");
            return;
        }

        for (String modId : INITIALIZED_COMMON) {
            CompatModule module = MODULES.get(modId);
            if (module == null) continue;

            try {
                module.registerActions(registry);
            } catch (Exception e) {
                LOGGER.error("[Compat:{}] Failed to register actions: {}", modId, e.getMessage(), e);
            }
        }
    }

    /**
     * Shutdown all modules.
     * Call during server stopping or mod unloading.
     */
    public static void shutdown() {
        LOGGER.info("[CompatRegistry] Shutting down {} modules...", INITIALIZED_COMMON.size());

        for (String modId : INITIALIZED_COMMON) {
            CompatModule module = MODULES.get(modId);
            if (module == null) continue;

            try {
                module.shutdown();
            } catch (Exception e) {
                LOGGER.error("[Compat:{}] Error during shutdown: {}", modId, e.getMessage(), e);
            }
        }

        INITIALIZED_COMMON.clear();
        INITIALIZED_CLIENT.clear();
        commonInitDone = false;
        clientInitDone = false;
    }

    /**
     * Get a registered module by mod ID.
     *
     * @param modId The mod ID
     * @return The module, or null if not registered
     */
    @Nullable
    public static CompatModule getModule(String modId) {
        return MODULES.get(modId);
    }

    /**
     * Get a typed module by mod ID.
     *
     * @param modId The mod ID
     * @param type The expected module type
     * @return The module cast to the expected type, or null
     */
    @Nullable
    public static <T extends CompatModule> T getModule(String modId, Class<T> type) {
        CompatModule module = MODULES.get(modId);
        if (module != null && type.isInstance(module)) {
            return type.cast(module);
        }
        return null;
    }

    /**
     * Check if a module is registered and initialized.
     */
    public static boolean isModuleActive(String modId) {
        return INITIALIZED_COMMON.contains(modId);
    }

    /**
     * Get all registered module IDs.
     */
    public static Set<String> getRegisteredModIds() {
        return Collections.unmodifiableSet(MODULES.keySet());
    }

    /**
     * Get all active (initialized) module IDs.
     */
    public static Set<String> getActiveModIds() {
        return Collections.unmodifiableSet(INITIALIZED_COMMON);
    }

    /**
     * Get a status report for logging/debugging.
     */
    public static String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DevMod Compatibility Status ===\n");
        sb.append(String.format("Registered: %d, Active: %d%n%n", MODULES.size(), INITIALIZED_COMMON.size()));

        List<String> sortedIds = new ArrayList<>(MODULES.keySet());
        Collections.sort(sortedIds);

        for (String modId : sortedIds) {
            CompatModule module = Objects.requireNonNull(MODULES.get(modId), "Missing module for " + modId);
            boolean loaded = Compat.isLoaded(modId);
            boolean active = INITIALIZED_COMMON.contains(modId);
            String version = Compat.getVersion(modId);

            String status;
            if (active) {
                status = "ACTIVE";
            } else if (loaded) {
                status = "LOADED (init failed)";
            } else {
                status = "NOT PRESENT";
            }

            sb.append(String.format("  [%s] %s v%s - %s%n",
                status.substring(0, 1), module.displayName(),
                version != null ? version : "?", status));
        }

        return sb.toString();
    }

    /**
     * Get count of active modules.
     */
    public static int getActiveCount() {
        return INITIALIZED_COMMON.size();
    }

    /**
     * Get count of registered modules.
     */
    public static int getRegisteredCount() {
        return MODULES.size();
    }
}

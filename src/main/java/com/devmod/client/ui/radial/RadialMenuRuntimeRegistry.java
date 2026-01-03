package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.devmod.client.ui.radial.config.RadialMenuDefinitionLoader;
import com.devmod.client.ui.radial.config.VisibilitySupplierRegistry;
import com.devmod.client.ui.radial.model.MacroCategory;

/**
 * Runtime registry that combines:
 * <ol>
 *   <li>Categories loaded from JSON config</li>
 *   <li>Categories registered programmatically via RadialMenuBuilder</li>
 *   <li>Legacy categories from RadialMenuRegistry (for backward compatibility)</li>
 * </ol>
 *
 * <p>Thread-safe for dynamic registration during gameplay.</p>
 *
 * <p>Usage:
 * <pre>
 * // Initialize during client setup
 * RadialMenuRuntimeRegistry.initialize(mobEditorSupplier);
 *
 * // Get categories for a macro
 * List&lt;RadialCategory&gt; categories = RadialMenuRuntimeRegistry.getCategories(MacroCategory.TOOLS);
 *
 * // Dynamically add a category
 * RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
 *     .category("my_category")
 *     ...
 *     .register();
 * </pre>
 * </p>
 */
public final class RadialMenuRuntimeRegistry {

    private static final Logger LOGGER = LogManager.getLogger(RadialMenuRuntimeRegistry.class);

    /** Categories loaded from JSON config or defaults */
    private static final Map<MacroCategory, List<RadialCategory>> CATEGORIES =
        new EnumMap<>(MacroCategory.class);

    /** Categories added dynamically at runtime via RadialMenuBuilder */
    private static final Map<MacroCategory, List<RadialCategory>> DYNAMIC_ADDITIONS =
        new EnumMap<>(MacroCategory.class);

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /** Supplier for special items that need screen context (e.g., mob editor) */
    @Nullable
    private static Supplier<RadialMenuItem> mobEditorSupplier;

    private RadialMenuRuntimeRegistry() {} // Utility class

    /**
     * Initialize the registry. Called during client mod setup.
     *
     * @param mobEditorItemSupplier Supplier for mob editor item (may be null)
     */
    public static void initialize(@Nullable Supplier<RadialMenuItem> mobEditorItemSupplier) {
        synchronized (INIT_LOCK) {
            if (initialized) {
                LOGGER.warn("[RadialMenuRuntimeRegistry] Already initialized");
                return;
            }

            mobEditorSupplier = mobEditorItemSupplier;

            // Register default visibility suppliers
            VisibilitySupplierRegistry.registerDefaults();

            // Initialize storage
            for (MacroCategory macro : MacroCategory.values()) {
                CATEGORIES.put(macro, new CopyOnWriteArrayList<>());
                DYNAMIC_ADDITIONS.put(macro, new CopyOnWriteArrayList<>());
            }

            // Load from JSON config (or fall back to embedded defaults)
            Map<MacroCategory, List<RadialCategory>> loaded =
                RadialMenuDefinitionLoader.load(mobEditorItemSupplier);

            for (var entry : loaded.entrySet()) {
                getConfigList(entry.getKey()).addAll(entry.getValue());
            }

            initialized = true;
            LOGGER.info("[RadialMenuRuntimeRegistry] Initialized with {} macros",
                MacroCategory.values().length);
        }
    }

    /**
     * Check if the registry is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get all categories for a macro category.
     * Includes both config-loaded and dynamically registered categories.
     *
     * @param macro The macro category
     * @return List of categories (never null)
     */
    public static List<RadialCategory> getCategories(MacroCategory macro) {
        ensureInitialized();

        List<RadialCategory> result = new ArrayList<>();
        List<RadialCategory> configCategories = CATEGORIES.get(macro);
        List<RadialCategory> dynamicCategories = DYNAMIC_ADDITIONS.get(macro);

        if (configCategories != null) {
            result.addAll(configCategories);
        }
        if (dynamicCategories != null) {
            result.addAll(dynamicCategories);
        }

        return result;
    }

    /**
     * Get all categories organized by macro category.
     *
     * @return Map of all categories
     */
    public static Map<MacroCategory, List<RadialCategory>> getAllCategories() {
        ensureInitialized();

        Map<MacroCategory, List<RadialCategory>> result = new EnumMap<>(MacroCategory.class);
        for (MacroCategory macro : MacroCategory.values()) {
            result.put(macro, getCategories(macro));
        }
        return result;
    }

    /**
     * Add a category dynamically at runtime.
     * Used by RadialMenuBuilder and mod integrations.
     *
     * @param macro The macro category to add to
     * @param category The category to add
     */
    public static void addCategory(MacroCategory macro, RadialCategory category) {
        ensureInitialized();

        if (macro == null || category == null) {
            LOGGER.warn("[RadialMenuRuntimeRegistry] Cannot add null macro or category");
            return;
        }

        getDynamicList(macro).add(category);
        LOGGER.debug("[RadialMenuRuntimeRegistry] Added dynamic category {} to {}",
            category.getId(), macro);
    }

    /**
     * Remove a dynamically added category by ID.
     *
     * @param macro The macro category
     * @param categoryId The category ID to remove
     * @return true if a category was removed
     */
    public static boolean removeCategory(MacroCategory macro, String categoryId) {
        ensureInitialized();

        if (macro == null || categoryId == null) {
            return false;
        }

        return getDynamicList(macro).removeIf(cat -> categoryId.equals(cat.getId()));
    }

    /**
     * Find a category by ID across all macros.
     *
     * @param categoryId The category ID to find
     * @return The category, or null if not found
     */
    @Nullable
    public static RadialCategory findCategory(String categoryId) {
        ensureInitialized();

        for (MacroCategory macro : MacroCategory.values()) {
            for (RadialCategory cat : getCategories(macro)) {
                if (categoryId.equals(cat.getId())) {
                    return cat;
                }
            }
        }
        return null;
    }

    /**
     * Get the count of categories for a macro.
     *
     * @param macro The macro category
     * @return Number of categories
     */
    public static int getCategoryCount(MacroCategory macro) {
        ensureInitialized();
        return getCategories(macro).size();
    }

    /**
     * Get total category count across all macros.
     *
     * @return Total number of categories
     */
    public static int getTotalCategoryCount() {
        ensureInitialized();

        int total = 0;
        for (MacroCategory macro : MacroCategory.values()) {
            total += getCategories(macro).size();
        }
        return total;
    }

    /**
     * Reload configuration from disk.
     * Preserves dynamically registered categories.
     */
    public static void reload() {
        synchronized (INIT_LOCK) {
            if (!initialized) {
                LOGGER.warn("[RadialMenuRuntimeRegistry] Cannot reload - not initialized");
                return;
            }

            // Clear config-loaded categories
            for (MacroCategory macro : MacroCategory.values()) {
                getConfigList(macro).clear();
            }

            // Reload from config
            Map<MacroCategory, List<RadialCategory>> loaded =
                RadialMenuDefinitionLoader.load(mobEditorSupplier);

            for (var entry : loaded.entrySet()) {
                getConfigList(entry.getKey()).addAll(entry.getValue());
            }

            LOGGER.info("[RadialMenuRuntimeRegistry] Reloaded configuration");
        }
    }

    /**
     * Get the mob editor item supplier for categories that need it.
     *
     * @return The supplier, or null if not set
     */
    @Nullable
    public static Supplier<RadialMenuItem> getMobEditorSupplier() {
        return mobEditorSupplier;
    }

    /**
     * Ensure the registry is initialized, throwing if not.
     */
    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "RadialMenuRuntimeRegistry not initialized. Call initialize() first.");
        }
    }

    private static List<RadialCategory> getConfigList(MacroCategory macro) {
        List<RadialCategory> list = CATEGORIES.get(macro);
        if (list == null) {
            throw new IllegalStateException("Missing config list for macro: " + macro);
        }
        return list;
    }

    private static List<RadialCategory> getDynamicList(MacroCategory macro) {
        List<RadialCategory> list = DYNAMIC_ADDITIONS.get(macro);
        if (list == null) {
            throw new IllegalStateException("Missing dynamic list for macro: " + macro);
        }
        return list;
    }

    /**
     * Reset the registry (for testing purposes only).
     */
    static void reset() {
        synchronized (INIT_LOCK) {
            for (MacroCategory macro : MacroCategory.values()) {
                if (CATEGORIES.containsKey(macro)) {
                    CATEGORIES.get(macro).clear();
                }
                if (DYNAMIC_ADDITIONS.containsKey(macro)) {
                    DYNAMIC_ADDITIONS.get(macro).clear();
                }
            }
            mobEditorSupplier = null;
            initialized = false;
        }
    }

    /**
     * Initialize registry for testing without requiring Minecraft.
     * Does not load config from disk - starts with empty category lists.
     * For testing purposes only.
     */
    static void initializeForTest() {
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            // Register default visibility suppliers
            VisibilitySupplierRegistry.registerDefaults();

            // Initialize storage with empty lists
            for (MacroCategory macro : MacroCategory.values()) {
                CATEGORIES.put(macro, new CopyOnWriteArrayList<>());
                DYNAMIC_ADDITIONS.put(macro, new CopyOnWriteArrayList<>());
            }

            initialized = true;
            LOGGER.info("[RadialMenuRuntimeRegistry] Initialized for testing");
        }
    }
}

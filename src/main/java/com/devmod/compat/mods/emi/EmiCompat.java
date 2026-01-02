package com.devmod.compat.mods.emi;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class EmiCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmiCompat.class);
    public static final String MOD_ID = "emi";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    @Nullable private static Class<?> emiApiClass;
    @Nullable private static Method getStackFromItemStackMethod;
    @Nullable private static Method viewRecipesMethod;
    @Nullable private static Method viewUsesMethod;
    @Nullable private static Method isCheatModeMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "EMI";
    }

    @Override
    public int priority() {
        // Medium priority - UI enhancement
        return 25;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:emi] EMI not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:emi] EMI detected");
        LOGGER.debug("[Compat:emi] Version: {}", Compat.getVersion(MOD_ID));
    }

    @Override
    public void initClient() {
        if (!available) return;

        // EMI API is client-side only
        tryLoadClientApi();

        if (apiAvailable) {
            LOGGER.info("[Compat:emi] EMI API available for recipe lookup");
        } else {
            LOGGER.debug("[Compat:emi] EMI API not available");
        }
    }

    /**
     * Attempt to load EMI's client API via reflection.
     */
    private void tryLoadClientApi() {
        try {
            // EMI API classes
            final Class<?> apiClass = Class.forName("dev.emi.emi.api.EmiApi");
            final Class<?> stackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            emiApiClass = apiClass;

            // Get EmiStack.of(ItemStack) method
            getStackFromItemStackMethod = stackClass.getMethod("of", ItemStack.class);

            // Get EmiApi methods
            viewRecipesMethod = apiClass.getMethod("displayRecipes", stackClass);
            viewUsesMethod = apiClass.getMethod("displayUses", stackClass);

            // Try to get cheat mode check
            try {
                isCheatModeMethod = apiClass.getMethod("isCheatMode");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:emi] Cheat mode method not available", e);
            }

            apiAvailable = true;
            LOGGER.debug("[Compat:emi] EMI API loaded successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:emi] EMI API classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[Compat:emi] EMI API method not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("[Compat:emi] Error loading EMI API: {}", e.getMessage());
        }
    }

    @Override
    public String getFeatureDescription() {
        return "Recipe lookup integration, item search, crafting info";
    }

    /**
     * Check if EMI is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the EMI API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Convert an ItemStack to an EmiStack.
     *
     * @param stack The ItemStack
     * @return The EmiStack object, or null
     */
    @Nullable
    public static Object toEmiStack(ItemStack stack) {
        final Method stackMethod = getStackFromItemStackMethod;
        if (!apiAvailable || stack == null || stack.isEmpty() || stackMethod == null) {
            return null;
        }

        try {
            return stackMethod.invoke(null, stack);
        } catch (Exception e) {
            LOGGER.debug("[Compat:emi] Failed to convert to EmiStack: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Open the recipe viewer for an item.
     * Shows all recipes that produce this item.
     *
     * @param stack The ItemStack to look up
     * @return true if the recipe view was opened
     */
    public static boolean showRecipes(ItemStack stack) {
        final Method recipesMethod = viewRecipesMethod;
        if (!apiAvailable || recipesMethod == null) {
            return false;
        }

        Object emiStack = toEmiStack(stack);
        if (emiStack == null) {
            return false;
        }

        try {
            Object result = recipesMethod.invoke(null, emiStack);
            if (result instanceof Boolean) {
                boolean opened = (Boolean) result;
                if (opened) {
                    LOGGER.debug("[Compat:emi] Opened recipes for {}", stack.getHoverName().getString());
                }
                return opened;
            }
            return true; // Assume success if no boolean returned
        } catch (Exception e) {
            LOGGER.debug("[Compat:emi] Failed to show recipes: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open the usage viewer for an item.
     * Shows all recipes that use this item as an ingredient.
     *
     * @param stack The ItemStack to look up
     * @return true if the usage view was opened
     */
    public static boolean showUses(ItemStack stack) {
        final Method usesMethod = viewUsesMethod;
        if (!apiAvailable || usesMethod == null) {
            return false;
        }

        Object emiStack = toEmiStack(stack);
        if (emiStack == null) {
            return false;
        }

        try {
            Object result = usesMethod.invoke(null, emiStack);
            if (result instanceof Boolean) {
                boolean opened = (Boolean) result;
                if (opened) {
                    LOGGER.debug("[Compat:emi] Opened uses for {}", stack.getHoverName().getString());
                }
                return opened;
            }
            return true;
        } catch (Exception e) {
            LOGGER.debug("[Compat:emi] Failed to show uses: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if EMI is in cheat mode.
     *
     * @return true if cheat mode is enabled
     */
    public static boolean isCheatModeEnabled() {
        final Method cheatMethod = isCheatModeMethod;
        if (!apiAvailable || cheatMethod == null) {
            return false;
        }

        try {
            Object result = cheatMethod.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Open recipe view or usage view based on context.
     * If shift is held, shows uses; otherwise shows recipes.
     *
     * @param stack The ItemStack
     * @param showUses true to show uses, false for recipes
     * @return true if view was opened
     */
    public static boolean showItemInfo(ItemStack stack, boolean showUses) {
        if (showUses) {
            return showUses(stack);
        } else {
            return showRecipes(stack);
        }
    }

    /**
     * Search for items in EMI's index.
     * Note: This requires EMI screen to be open.
     *
     * @param query The search query
     * @return true if search was initiated
     */
    public static boolean searchItems(String query) {
        final Class<?> apiClass = emiApiClass;
        if (!apiAvailable || query == null || apiClass == null) {
            return false;
        }

        try {
            // Try to set search text
            Method setSearchTextMethod = apiClass.getMethod("setSearchText", String.class);
            setSearchTextMethod.invoke(null, query);
            LOGGER.debug("[Compat:emi] Set search to: {}", query);
            return true;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[Compat:emi] Search method not available");
        } catch (Exception e) {
            LOGGER.debug("[Compat:emi] Failed to search: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get EMI integration info for display.
     */
    public static Map<String, Object> getIntegrationInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("available", available);
        info.put("apiAvailable", apiAvailable);
        if (available) {
            info.put("version", Compat.getVersion(MOD_ID));
            info.put("cheatMode", isCheatModeEnabled());
        }
        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "EMI: not available";
        }
        if (!apiAvailable) {
            return "EMI: detected (client API not loaded)";
        }
        String cheatMode = isCheatModeEnabled() ? " [cheat]" : "";
        return "EMI: API available" + cheatMode;
    }
}

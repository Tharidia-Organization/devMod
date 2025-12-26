package com.devmod.compat.mods.relics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class RelicsCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelicsCompat.class);
    public static final String MOD_ID = "relics";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> relicItemClass;
    private static Method getRelicDataMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Relics";
    }

    @Override
    public int priority() {
        // Medium priority - equipment/artifacts
        return 32;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:relics] Relics not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:relics] Relics detected");
        LOGGER.debug("[Compat:relics] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Relics API classes via reflection.
     */
    private void loadApi() {
        try {
            // Relics package structure
            String[] packages = {
                "it.hurts.sskirillss.relics.items.relics.base",
                "it.hurts.sskirillss.relics.items.relics",
                "it.hurts.sskirillss.relics.items"
            };

            for (String pkg : packages) {
                try {
                    relicItemClass = Class.forName(pkg + ".RelicItem");
                    LOGGER.debug("[Compat:relics] Found RelicItem at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:relics] RelicItem class not found in {}", pkg);
                }
            }

            // Get methods
            if (relicItemClass != null) {
                try {
                    getRelicDataMethod = relicItemClass.getMethod("getRelicData");
                } catch (NoSuchMethodException e) {
                    LOGGER.trace("[Compat:relics] getRelicData not available", e);
                }

                apiAvailable = true;
                LOGGER.info("[Compat:relics] Relics API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:relics] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:relics] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Relic detection, level/XP tracking, ability monitoring";
    }

    /**
     * Check if Relics is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an item is a Relic.
     *
     * @param stack The item stack
     * @return true if item is a relic
     */
    public static boolean isRelic(ItemStack stack) {
        if (!apiAvailable || stack == null || stack.isEmpty() || relicItemClass == null) {
            return false;
        }
        return relicItemClass.isInstance(stack.getItem());
    }

    /**
     * Get relic info from an item stack.
     *
     * @param stack The relic item stack
     * @return Map of relic properties
     */
    public static Map<String, Object> getRelicInfo(ItemStack stack) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!isRelic(stack)) {
            return info;
        }

        info.put("isRelic", true);
        info.put("itemName", stack.getHoverName().getString());

        try {
            Object relicItem = stack.getItem();

            // In Minecraft 1.21+, data is stored via Data Components
            // Try to get level via reflection from the relic's custom data
            try {
                // Try ItemStack.get() for custom data component
                Method getComponentMethod = stack.getClass().getMethod("get",
                    net.minecraft.core.component.DataComponentType.class);

                // Relics mod may use custom data components - try reflection
                Class<?> relicsDataClass = Class.forName(
                    "it.hurts.sskirillss.relics.init.DataComponentRegistry");
                var levelField = relicsDataClass.getField("LEVEL");
                var levelComponent = levelField.get(null);

                Object level = getComponentMethod.invoke(stack, levelComponent);
                if (level instanceof Number numLevel) {
                    info.put("level", numLevel.intValue());
                }
            } catch (Exception ignored) {
                LOGGER.trace("[Compat:relics] Data component lookup failed", ignored);
            }

            // Try item-based level getter
            try {
                Method getLevelMethod = relicItem.getClass().getMethod("getLevel", ItemStack.class);
                Object level = getLevelMethod.invoke(relicItem, stack);
                if (level instanceof Number numLevel) {
                    info.put("level", numLevel.intValue());
                }
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:relics] getLevel method not available", e);
            }

            // Try to get relic data via API
            if (getRelicDataMethod != null) {
                Object relicData = getRelicDataMethod.invoke(relicItem);
                if (relicData != null) {
                    // Extract properties from relic data
                    try {
                        Method getIdMethod = relicData.getClass().getMethod("getId");
                        Object id = getIdMethod.invoke(relicData);
                        if (id != null) {
                            info.put("relicId", id.toString());
                        }
                    } catch (Exception e) {
                        LOGGER.trace("[Compat:relics] Failed to read relic id", e);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:relics] Error getting relic info: {}", e.getMessage());
        }

        return info;
    }

    /**
     * Get all equipped relics for a player.
     * Checks inventory and Curios slots if available.
     *
     * @param player The player
     * @return List of relic info maps
     */
    public static List<Map<String, Object>> getEquippedRelics(Player player) {
        List<Map<String, Object>> relics = new ArrayList<>();

        if (!apiAvailable || player == null) {
            return relics;
        }

        // Check main inventory and hotbar
        for (ItemStack stack : player.getInventory().items) {
            if (isRelic(stack)) {
                Map<String, Object> info = getRelicInfo(stack);
                info.put("slot", "inventory");
                relics.add(info);
            }
        }

        // Check armor slots
        for (ItemStack stack : player.getInventory().armor) {
            if (isRelic(stack)) {
                Map<String, Object> info = getRelicInfo(stack);
                info.put("slot", "armor");
                relics.add(info);
            }
        }

        // Check offhand
        ItemStack offhand = player.getOffhandItem();
        if (isRelic(offhand)) {
            Map<String, Object> info = getRelicInfo(offhand);
            info.put("slot", "offhand");
            relics.add(info);
        }

        return relics;
    }

    /**
     * Get the total relic level sum for a player.
     * Useful for power level calculations.
     *
     * @param player The player
     * @return Sum of all relic levels
     */
    public static int getTotalRelicLevel(Player player) {
        if (!apiAvailable || player == null) {
            return 0;
        }

        int total = 0;
        for (Map<String, Object> relic : getEquippedRelics(player)) {
            Object level = relic.get("level");
            if (level instanceof Number) {
                total += ((Number) level).intValue();
            }
        }

        return total;
    }

    /**
     * Count equipped relics for a player.
     *
     * @param player The player
     * @return Number of relics
     */
    public static int getRelicCount(Player player) {
        return getEquippedRelics(player).size();
    }

    /**
     * Get relics summary for telemetry.
     *
     * @param player The player
     * @return Summary map
     */
    public static Map<String, Object> getRelicsSummary(Player player) {
        Map<String, Object> summary = new LinkedHashMap<>();

        if (!apiAvailable || player == null) {
            return summary;
        }

        List<Map<String, Object>> relics = getEquippedRelics(player);
        summary.put("relicCount", relics.size());
        summary.put("totalLevel", getTotalRelicLevel(player));

        // List relic names
        List<String> names = new ArrayList<>();
        for (Map<String, Object> relic : relics) {
            Object name = relic.get("itemName");
            if (name != null) {
                names.add(name.toString());
            }
        }
        if (!names.isEmpty()) {
            summary.put("relicNames", names);
        }

        return summary;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Relics: not available";
        }
        if (!apiAvailable) {
            return "Relics: detected (API not loaded)";
        }
        return "Relics: API available";
    }
}

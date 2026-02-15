package com.devmod.compat.mods.shieldapi;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class ShieldApiCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShieldApiCompat.class);
    public static final String MOD_ID = "shield_api";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> customShieldItemClass;
    private static Method getShieldPropertiesMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Shield API";
    }

    @Override
    public int priority() {
        // Medium priority - combat mechanics
        return 34;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:shieldapi] Shield API not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:shieldapi] Shield API detected");
        LOGGER.debug("[Compat:shieldapi] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Shield API classes via reflection.
     */
    private void loadApi() {
        try {
            // Shield API package structure
            String[] packages = {
                "net.shield_api.api",
                "net.shield_api.item"
            };

            for (String pkg : packages) {
                try {
                    customShieldItemClass = Class.forName(pkg + ".CustomShieldItem");
                    LOGGER.debug("[Compat:shieldapi] Found CustomShieldItem at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:shieldapi] CustomShieldItem not found in {}", pkg);
                }
            }

            if (customShieldItemClass != null) {
                // Get methods
                try {
                    getShieldPropertiesMethod = customShieldItemClass
                        .getMethod("getShieldProperties", ItemStack.class);
                } catch (NoSuchMethodException e) {
                    LOGGER.trace("[Compat:shieldapi] getShieldProperties not available", e);
                }

                apiAvailable = true;
                LOGGER.info("[Compat:shieldapi] Shield API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:shieldapi] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:shieldapi] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Custom shield detection, block angle, cooldown tracking";
    }

    /**
     * Check if Shield API is available.
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
     * Check if an item is a custom Shield API shield.
     *
     * @param stack The item stack
     * @return true if custom shield
     */
    public static boolean isCustomShield(ItemStack stack) {
        if (!apiAvailable || stack == null || stack.isEmpty() || customShieldItemClass == null) {
            return false;
        }
        return customShieldItemClass.isInstance(stack.getItem());
    }

    /**
     * Check if an item is any type of shield.
     *
     * @param stack The item stack
     * @return true if any shield
     */
    public static boolean isShield(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Check custom shield first
        if (isCustomShield(stack)) {
            return true;
        }

        // Check if vanilla shield or has shield use action
        return stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    /**
     * Get shield properties for a custom shield.
     *
     * @param stack The shield item stack
     * @return Map of shield properties
     */
    public static Map<String, Object> getShieldProperties(ItemStack stack) {
        Map<String, Object> props = new LinkedHashMap<>();

        if (!isCustomShield(stack)) {
            // Return basic info for vanilla shields
            if (isShield(stack)) {
                props.put("isShield", true);
                props.put("type", "vanilla");
            }
            return props;
        }

        props.put("isShield", true);
        props.put("type", "custom");
        props.put("itemName", stack.getHoverName().getString());

        try {
            Object shieldItem = stack.getItem();

            // Get shield properties object
            if (getShieldPropertiesMethod != null) {
                Object shieldProps = getShieldPropertiesMethod.invoke(shieldItem, stack);

                if (shieldProps != null) {
                    // Extract properties
                    try {
                        Method getBlockAngleMethod = shieldProps.getClass().getMethod("getBlockAngle");
                        Object angle = getBlockAngleMethod.invoke(shieldProps);
                        if (angle instanceof Number) {
                            props.put("blockAngle", ((Number) angle).floatValue());
                        }
                    } catch (NoSuchMethodException e) {
                        LOGGER.trace("[Compat:shieldapi] getBlockAngle not available", e);
                    }

                    try {
                        Method getCooldownMethod = shieldProps.getClass().getMethod("getCooldown");
                        Object cooldown = getCooldownMethod.invoke(shieldProps);
                        if (cooldown instanceof Number) {
                            props.put("cooldown", ((Number) cooldown).intValue());
                        }
                    } catch (NoSuchMethodException e) {
                        LOGGER.trace("[Compat:shieldapi] getCooldown not available", e);
                    }

                    try {
                        Method getDamageReductionMethod = shieldProps.getClass()
                            .getMethod("getDamageReduction");
                        Object reduction = getDamageReductionMethod.invoke(shieldProps);
                        if (reduction instanceof Number) {
                            props.put("damageReduction", ((Number) reduction).floatValue());
                        }
                    } catch (NoSuchMethodException e) {
                        LOGGER.trace("[Compat:shieldapi] getDamageReduction not available", e);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:shieldapi] Error getting shield properties: {}", e.getMessage());
        }

        return props;
    }

    /**
     * Check if a player is currently blocking with a shield.
     *
     * @param player The player
     * @return true if blocking
     */
    public static boolean isBlocking(Player player) {
        if (player == null) {
            return false;
        }
        return player.isBlocking();
    }

    /**
     * Get the shield a player is using to block.
     *
     * @param player The player
     * @return Shield item stack, or empty if not blocking
     */
    public static ItemStack getBlockingShield(Player player) {
        if (!isBlocking(player)) {
            return ItemStack.EMPTY;
        }

        // Check both hands
        ItemStack mainHand = player.getMainHandItem();
        if (isShield(mainHand) && player.isUsingItem() &&
            player.getUseItem() == mainHand) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        if (isShield(offHand) && player.isUsingItem() &&
            player.getUseItem() == offHand) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get shield combat info for telemetry.
     *
     * @param player The player
     * @return Shield combat info
     */
    public static Map<String, Object> getShieldCombatInfo(Player player) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (player == null) {
            return info;
        }

        // Check offhand (common shield position)
        ItemStack offHand = player.getOffhandItem();
        if (isShield(offHand)) {
            Map<String, Object> shieldProps = getShieldProperties(offHand);
            info.put("offhandShield", shieldProps);
        }

        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (isShield(mainHand)) {
            Map<String, Object> shieldProps = getShieldProperties(mainHand);
            info.put("mainhandShield", shieldProps);
        }

        info.put("isBlocking", isBlocking(player));

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Shield API: not available";
        }
        if (!apiAvailable) {
            return "Shield API: detected (API not loaded)";
        }
        return "Shield API: API available";
    }
}

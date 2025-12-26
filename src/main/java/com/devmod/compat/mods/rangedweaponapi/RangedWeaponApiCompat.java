package com.devmod.compat.mods.rangedweaponapi;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
public class RangedWeaponApiCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RangedWeaponApiCompat.class);
    public static final String MOD_ID = "ranged_weapon_api";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> rangedWeaponItemClass;
    private static Class<?> crossbowItemClass;

    private static Method getDamageMethod;
    private static Method getPullTimeMethod;
    private static Method getVelocityMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Ranged Weapon API";
    }

    @Override
    public int priority() {
        // Medium priority - weapon framework
        return 22;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Ranged Weapon API classes
            // The package structure may vary between versions
            try {
                rangedWeaponItemClass = Class.forName("net.ranged_weapon.api.RangedWeaponItem");
            } catch (ClassNotFoundException e) {
                // Try alternative package paths
                try {
                    rangedWeaponItemClass = Class.forName("net.ranged_weapon.item.RangedWeaponItem");
                } catch (ClassNotFoundException e2) {
                    LOGGER.debug("[Compat:ranged_weapon_api] RangedWeaponItem class not found");
                }
            }

            try {
                crossbowItemClass = Class.forName("net.ranged_weapon.api.CrossbowItem");
            } catch (ClassNotFoundException e) {
                try {
                    crossbowItemClass = Class.forName("net.ranged_weapon.item.CrossbowItem");
                } catch (ClassNotFoundException e2) {
                    LOGGER.debug("[Compat:ranged_weapon_api] CrossbowItem class not found");
                }
            }

            // Try to find property methods
            if (rangedWeaponItemClass != null) {
                try {
                    getDamageMethod = rangedWeaponItemClass.getMethod("getDamage");
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:ranged_weapon_api] getDamage method not found");
                }

                try {
                    getPullTimeMethod = rangedWeaponItemClass.getMethod("getPullTime");
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:ranged_weapon_api] getPullTime method not found");
                }

                try {
                    getVelocityMethod = rangedWeaponItemClass.getMethod("getVelocity");
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:ranged_weapon_api] getVelocity method not found");
                }
            }

            // Consider available if mod is loaded, even if specific classes weren't found
            available = Compat.isLoaded(MOD_ID);

            if (available) {
                LOGGER.info("[Compat:ranged_weapon_api] Ranged Weapon API detected");
                LOGGER.debug("[Compat:ranged_weapon_api] Version: {}", Compat.getVersion(MOD_ID));
                if (rangedWeaponItemClass != null) {
                    LOGGER.debug("[Compat:ranged_weapon_api] RangedWeaponItem class found");
                }
            }
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:ranged_weapon_api] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:ranged_weapon_api] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Ranged weapon detection, damage stats, item editor support";
    }

    /**
     * Check if Ranged Weapon API is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if an item is a custom ranged weapon from this API.
     *
     * @param stack The item to check
     * @return true if the item is a Ranged Weapon API item
     */
    public static boolean isRangedWeapon(ItemStack stack) {
        if (!available || stack == null || stack.isEmpty()) {
            return false;
        }

        if (rangedWeaponItemClass != null) {
            return rangedWeaponItemClass.isInstance(stack.getItem());
        }

        return false;
    }

    /**
     * Check if an item is a custom crossbow from this API.
     *
     * @param stack The item to check
     * @return true if the item is a Ranged Weapon API crossbow
     */
    public static boolean isCustomCrossbow(ItemStack stack) {
        if (!available || stack == null || stack.isEmpty()) {
            return false;
        }

        if (crossbowItemClass != null) {
            return crossbowItemClass.isInstance(stack.getItem());
        }

        return false;
    }

    /**
     * Get the base damage of a ranged weapon.
     *
     * @param stack The weapon
     * @return Base damage, or -1 if not available
     */
    public static double getDamage(ItemStack stack) {
        if (!isRangedWeapon(stack) || getDamageMethod == null) {
            return -1;
        }

        try {
            Object result = getDamageMethod.invoke(stack.getItem());
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:ranged_weapon_api] Failed to get damage: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the pull time of a ranged weapon (in ticks).
     *
     * @param stack The weapon
     * @return Pull time in ticks, or -1 if not available
     */
    public static int getPullTime(ItemStack stack) {
        if (!isRangedWeapon(stack) || getPullTimeMethod == null) {
            return -1;
        }

        try {
            Object result = getPullTimeMethod.invoke(stack.getItem());
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:ranged_weapon_api] Failed to get pull time: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the projectile velocity of a ranged weapon.
     *
     * @param stack The weapon
     * @return Velocity multiplier, or -1 if not available
     */
    public static double getVelocity(ItemStack stack) {
        if (!isRangedWeapon(stack) || getVelocityMethod == null) {
            return -1;
        }

        try {
            Object result = getVelocityMethod.invoke(stack.getItem());
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:ranged_weapon_api] Failed to get velocity: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get a summary string of weapon stats.
     *
     * @param stack The weapon
     * @return Stats string like "Damage: 5.0 | Pull: 20t" or empty string
     */
    public static String getWeaponStatsSummary(ItemStack stack) {
        if (!isRangedWeapon(stack)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        double damage = getDamage(stack);
        if (damage > 0) {
            sb.append("Damage: ").append(String.format("%.1f", damage));
        }

        int pullTime = getPullTime(stack);
        if (pullTime > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Pull: ").append(pullTime).append("t");
        }

        double velocity = getVelocity(stack);
        if (velocity > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Velocity: ").append(String.format("%.1fx", velocity));
        }

        return sb.toString();
    }

    /**
     * Check if the API provides full property access.
     */
    public static boolean hasPropertyAccess() {
        return rangedWeaponItemClass != null && getDamageMethod != null;
    }
}

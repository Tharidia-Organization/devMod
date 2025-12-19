package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Integration with Better Combat mod.
 * Allows reading attack information (combo name, extended reach, etc.)
 *
 * NOTE: This class is loaded only if Better Combat is present.
 * Uses reflection to avoid hard dependency.
 */
public class BetterCombatIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterCombatIntegration.class);
    private static boolean initialized = false;
    private static boolean available = false;

    // Cache for reflection methods
    private static Class<?> playerAttackHelperClass;
    private static Class<?> weaponAttributesClass;

    /**
     * Initializes Better Combat integration.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Better Combat classes via reflection
            playerAttackHelperClass = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
            weaponAttributesClass = Class.forName("net.bettercombat.api.WeaponAttributes");
            available = true;
            LOGGER.info("[DevMod] Better Combat integration initialized successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[DevMod] Better Combat classes not found - integration disabled");
            available = false;
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Error initializing Better Combat integration: {}", e.getMessage());
            available = false;
        }
    }

    /**
     * Checks if Better Combat is available and working.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Gets the player's current attack name (if using Better Combat).
     * @return attack name (e.g., "Slash", "Thrust", "Spin Attack"), or null
     */
    @Nullable
    public static String getCurrentAttackName(Player player) {
        if (!available || player == null) return null;

        try {
            // Better Combat stores attack info in player's persistent data or capability
            // This is a simplified approach - actual implementation may vary by BC version

            // Try to get weapon attributes from held item
            var mainHand = player.getMainHandItem();
            if (mainHand.isEmpty()) return null;

            // Use reflection to access Better Combat's weapon registry
            var getWeaponAttributesMethod = playerAttackHelperClass.getMethod(
                "getWeaponAttributes",
                net.minecraft.world.item.ItemStack.class
            );

            Object weaponAttrs = getWeaponAttributesMethod.invoke(null, mainHand);
            if (weaponAttrs == null) return null;

            // Get the attack name from weapon attributes
            var getAttackNameMethod = weaponAttributesClass.getMethod("attackName");
            Object attackName = getAttackNameMethod.invoke(weaponAttrs);

            return attackName != null ? attackName.toString() : null;

        } catch (NoSuchMethodException e) {
            // Method signature might be different in this BC version
            LOGGER.debug("[DevMod] Better Combat API method not found: {}", e.getMessage());
        } catch (Exception e) {
            // BC might not be in an attackable state
            LOGGER.debug("[DevMod] Failed to get Better Combat attack name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Gets the weapon's extended reach (Better Combat can modify it).
     * @return reach bonus, or 0 if not available
     */
    public static double getExtendedReach(Player player) {
        if (!available || player == null) return 0;

        try {
            var mainHand = player.getMainHandItem();
            if (mainHand.isEmpty()) return 0;

            var getWeaponAttributesMethod = playerAttackHelperClass.getMethod(
                "getWeaponAttributes",
                net.minecraft.world.item.ItemStack.class
            );

            Object weaponAttrs = getWeaponAttributesMethod.invoke(null, mainHand);
            if (weaponAttrs == null) return 0;

            // Get attack reach from weapon attributes
            var getReachMethod = weaponAttributesClass.getMethod("attackRange");
            Object reach = getReachMethod.invoke(weaponAttrs);

            if (reach instanceof Number) {
                return ((Number) reach).doubleValue();
            }

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Better Combat extended reach: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * Checks if the player is executing a Better Combat combo attack.
     */
    public static boolean isInCombo(Player player) {
        if (!available || player == null) return false;

        try {
            // Check if player has combo state
            var isAttackingMethod = playerAttackHelperClass.getMethod(
                "isAttacking",
                Player.class
            );

            Object result = isAttackingMethod.invoke(null, player);
            return result instanceof Boolean && (Boolean) result;

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to check Better Combat combo state: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Gets the current combo count (1, 2, 3, etc.)
     * @return combo count, or 0 if not in combo
     */
    public static int getComboCount(Player player) {
        if (!available || player == null) return 0;

        try {
            var getComboCountMethod = playerAttackHelperClass.getMethod(
                "getComboCount",
                Player.class
            );

            Object result = getComboCountMethod.invoke(null, player);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }

        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Better Combat combo count: {}", e.getMessage());
        }

        return 0;
    }
}

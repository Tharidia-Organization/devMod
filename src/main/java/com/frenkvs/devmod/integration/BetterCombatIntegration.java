package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Integrazione con Better Combat mod.
 * Permette di leggere informazioni sugli attacchi (nome combo, reach esteso, ecc.)
 *
 * NOTA: Questa classe viene caricata solo se Better Combat è presente.
 * Usa reflection per evitare hard dependency.
 */
public class BetterCombatIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterCombatIntegration.class);
    private static boolean initialized = false;
    private static boolean available = false;

    // Cache dei metodi reflection
    private static Class<?> playerAttackHelperClass;
    private static Class<?> weaponAttributesClass;

    /**
     * Inizializza l'integrazione Better Combat.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            // Prova a caricare le classi Better Combat via reflection
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
     * Verifica se Better Combat è disponibile e funzionante.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Ottiene il nome dell'attacco corrente del player (se sta usando Better Combat).
     * @return nome dell'attacco (es. "Slash", "Thrust", "Spin Attack"), o null
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
     * Ottiene il reach esteso dell'arma (Better Combat può modificarlo).
     * @return reach bonus, o 0 se non disponibile
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
     * Verifica se il player sta eseguendo un attacco combo di Better Combat.
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
     * Ottiene il numero del combo attuale (1, 2, 3, ecc.)
     * @return numero combo, o 0 se non in combo
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

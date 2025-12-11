package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages integrations with external mods (Pehkui, Better Combat, Distant Horizons, etc.)
 * All dependencies are soft (optional) - the mod works without them.
 */
public class ModIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIntegrationManager.class);

    private static boolean pehkuiLoaded = false;
    private static boolean betterCombatLoaded = false;
    private static boolean distantHorizonsLoaded = false;
    private static boolean initialized = false;

    /**
     * Initializes the manager by checking which mods are present.
     * Call during FMLCommonSetupEvent.
     */
    public static void init() {
        if (initialized) return;

        pehkuiLoaded = ModList.get().isLoaded("pehkui");
        betterCombatLoaded = ModList.get().isLoaded("bettercombat");
        distantHorizonsLoaded = ModList.get().isLoaded("distanthorizons");

        // Initialize specific integrations
        if (betterCombatLoaded) {
            BetterCombatIntegration.init();
        }

        if (distantHorizonsLoaded) {
            DistantHorizonsIntegration.init();
        }

        LOGGER.info("[DevMod] Mod Integration Status:");
        LOGGER.info("  - Pehkui: {}", pehkuiLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - Better Combat: {}", betterCombatLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - Distant Horizons: {}", distantHorizonsLoaded ? "ENABLED" : "not found");

        initialized = true;
    }

    /**
     * Gets the visual scale of an entity from Pehkui.
     * @return scale (1.0 = normal), or null if Pehkui is not present
     */
    public static Float getPehkuiScale(LivingEntity entity) {
        if (!pehkuiLoaded || entity == null) return null;
        try {
            return PehkuiIntegration.getScale(entity);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Pehkui scale for entity {}: {}", entity.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Gets the hitbox scale of an entity from Pehkui.
     * Can be different from the visual scale.
     * @return hitbox scale, or null if Pehkui is not present
     */
    public static Float getPehkuiHitboxScale(LivingEntity entity) {
        if (!pehkuiLoaded || entity == null) return null;
        try {
            return PehkuiIntegration.getHitboxScale(entity);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Pehkui hitbox scale for entity {}: {}", entity.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks if an entity has been scaled by Pehkui.
     */
    public static boolean isScaledByPehkui(LivingEntity entity) {
        Float scale = getPehkuiScale(entity);
        return scale != null && Math.abs(scale - 1.0f) > 0.01f;
    }

    // === Getters ===

    public static boolean isPehkuiLoaded() {
        return pehkuiLoaded;
    }

    public static boolean isBetterCombatLoaded() {
        return betterCombatLoaded;
    }

    public static boolean isDistantHorizonsLoaded() {
        return distantHorizonsLoaded;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // === Better Combat Integration Methods ===

    /**
     * Gets the current Better Combat attack name.
     * @return attack name, or null if BC is not active
     */
    @Nullable
    public static String getBetterCombatAttackName(Player player) {
        if (!betterCombatLoaded || player == null) return null;
        try {
            return BetterCombatIntegration.getCurrentAttackName(player);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Better Combat attack name: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the extended reach from Better Combat.
     * @return reach bonus, or 0
     */
    public static double getBetterCombatReach(Player player) {
        if (!betterCombatLoaded || player == null) return 0;
        try {
            return BetterCombatIntegration.getExtendedReach(player);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Better Combat reach: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Checks if the player is in a Better Combat combo.
     */
    public static boolean isInBetterCombatCombo(Player player) {
        if (!betterCombatLoaded || player == null) return false;
        try {
            return BetterCombatIntegration.isInCombo(player);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to check Better Combat combo state: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the Better Combat combo count.
     */
    public static int getBetterCombatComboCount(Player player) {
        if (!betterCombatLoaded || player == null) return 0;
        try {
            return BetterCombatIntegration.getComboCount(player);
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Failed to get Better Combat combo count: {}", e.getMessage());
            return 0;
        }
    }
}

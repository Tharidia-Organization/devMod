package com.frenkvs.devmod.integration;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestisce le integrazioni con mod esterne (Pehkui, Better Combat, ecc.)
 * Tutte le dipendenze sono soft (opzionali) - la mod funziona anche senza.
 */
public class ModIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIntegrationManager.class);

    private static boolean pehkuiLoaded = false;
    private static boolean betterCombatLoaded = false;
    private static boolean initialized = false;

    /**
     * Inizializza il manager controllando quali mod sono presenti.
     * Chiamare durante FMLCommonSetupEvent.
     */
    public static void init() {
        if (initialized) return;

        pehkuiLoaded = ModList.get().isLoaded("pehkui");
        betterCombatLoaded = ModList.get().isLoaded("bettercombat");

        // Inizializza integrazioni specifiche
        if (betterCombatLoaded) {
            BetterCombatIntegration.init();
        }

        LOGGER.info("[DevMod] Mod Integration Status:");
        LOGGER.info("  - Pehkui: {}", pehkuiLoaded ? "ENABLED" : "not found");
        LOGGER.info("  - Better Combat: {}", betterCombatLoaded ? "ENABLED" : "not found");

        initialized = true;
    }

    /**
     * Ottiene la scala visiva di un'entità da Pehkui.
     * @return scala (1.0 = normale), o null se Pehkui non è presente
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
     * Ottiene la scala hitbox di un'entità da Pehkui.
     * Può essere diversa dalla scala visiva.
     * @return scala hitbox, o null se Pehkui non è presente
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
     * Verifica se un'entità è stata scalata da Pehkui.
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

    public static boolean isInitialized() {
        return initialized;
    }

    // === Better Combat Integration Methods ===

    /**
     * Ottiene il nome dell'attacco corrente di Better Combat.
     * @return nome attacco, o null se BC non attivo
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
     * Ottiene il reach esteso da Better Combat.
     * @return reach bonus, o 0
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
     * Verifica se il player è in un combo Better Combat.
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
     * Ottiene il numero del combo Better Combat.
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

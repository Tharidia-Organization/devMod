package com.devmod.compat.mods.rpgseries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class RpgSeriesCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpgSeriesCompat.class);

    // Multiple mod IDs for the series
    public static final String ARCHERS_MOD_ID = "archers";
    public static final String PALADINS_MOD_ID = "paladins";
    public static final String WIZARDS_MOD_ID = "wizards";
    public static final String RUNES_MOD_ID = "runes";
    public static final String ROGUES_MOD_ID = "rogues";

    private static volatile boolean archersAvailable = false;
    private static volatile boolean paladinsAvailable = false;
    private static volatile boolean wizardsAvailable = false;
    private static volatile boolean runesAvailable = false;
    private static volatile boolean roguesAvailable = false;
    private static volatile boolean initialized = false;
    private static volatile boolean anyAvailable = false;

    // Cached reflection references
    private static Class<?> spellCasterItemClass;
    private static Class<?> rangedWeaponItemClass;

    @Override
    public String modId() {
        return ARCHERS_MOD_ID; // Primary ID for CompatModule
    }

    @Override
    public String displayName() {
        return "RpgSeries (Archers/Paladins/Wizards)";
    }

    @Override
    public int priority() {
        // Medium priority - combat/class systems
        return 31;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        // Check each mod in the series
        archersAvailable = Compat.isLoaded(ARCHERS_MOD_ID);
        paladinsAvailable = Compat.isLoaded(PALADINS_MOD_ID);
        wizardsAvailable = Compat.isLoaded(WIZARDS_MOD_ID);
        runesAvailable = Compat.isLoaded(RUNES_MOD_ID);
        roguesAvailable = Compat.isLoaded(ROGUES_MOD_ID);

        anyAvailable = archersAvailable || paladinsAvailable || wizardsAvailable ||
                       runesAvailable || roguesAvailable;

        if (!anyAvailable) {
            LOGGER.debug("[Compat:rpgseries] No RpgSeries mods found");
            return;
        }

        LOGGER.info("[Compat:rpgseries] RpgSeries mods detected:");
        if (archersAvailable) LOGGER.info("  - Archers: {}", Compat.getVersion(ARCHERS_MOD_ID));
        if (paladinsAvailable) LOGGER.info("  - Paladins: {}", Compat.getVersion(PALADINS_MOD_ID));
        if (wizardsAvailable) LOGGER.info("  - Wizards: {}", Compat.getVersion(WIZARDS_MOD_ID));
        if (runesAvailable) LOGGER.info("  - Runes: {}", Compat.getVersion(RUNES_MOD_ID));
        if (roguesAvailable) LOGGER.info("  - Rogues: {}", Compat.getVersion(ROGUES_MOD_ID));

        loadApi();
    }

    /**
     * Load RpgSeries API classes via reflection.
     */
    private void loadApi() {
        try {
            // Try to find common SpellEngine classes used by these mods
            try {
                spellCasterItemClass = Class.forName(
                    "net.spell_engine.api.item.weapon.SpellCasterItem");
            } catch (ClassNotFoundException e) {
                LOGGER.trace("[Compat:rpgseries] SpellCasterItem not found", e);
            }

            try {
                rangedWeaponItemClass = Class.forName(
                    "net.ranged_weapon_api.api.RangedWeaponItem");
            } catch (ClassNotFoundException e) {
                LOGGER.trace("[Compat:rpgseries] RangedWeaponItem not found", e);
            }

            LOGGER.debug("[Compat:rpgseries] API classes loaded");

        } catch (Exception e) {
            LOGGER.debug("[Compat:rpgseries] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!anyAvailable) return;
        LOGGER.debug("[Compat:rpgseries] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Class weapon detection, ability tracking, combat integration";
    }

    /**
     * Check if any RpgSeries mod is available.
     */
    public static boolean isAvailable() {
        return anyAvailable;
    }

    /**
     * Check specific mod availability.
     */
    public static boolean isArchersAvailable() {
        return archersAvailable;
    }

    public static boolean isPaladinsAvailable() {
        return paladinsAvailable;
    }

    public static boolean isWizardsAvailable() {
        return wizardsAvailable;
    }

    public static boolean isRunesAvailable() {
        return runesAvailable;
    }

    public static boolean isRoguesAvailable() {
        return roguesAvailable;
    }

    /**
     * Get which RpgSeries mods are loaded.
     *
     * @return Set of loaded mod IDs
     */
    public static Set<String> getLoadedMods() {
        Set<String> loaded = new LinkedHashSet<>();

        if (archersAvailable) loaded.add(ARCHERS_MOD_ID);
        if (paladinsAvailable) loaded.add(PALADINS_MOD_ID);
        if (wizardsAvailable) loaded.add(WIZARDS_MOD_ID);
        if (runesAvailable) loaded.add(RUNES_MOD_ID);
        if (roguesAvailable) loaded.add(ROGUES_MOD_ID);

        return loaded;
    }

    /**
     * Check if an item is from any RpgSeries mod.
     *
     * @param stack The item stack
     * @return true if from RpgSeries
     */
    public static boolean isRpgSeriesItem(ItemStack stack) {
        if (!anyAvailable || stack == null || stack.isEmpty()) {
            return false;
        }

        String itemId = stack.getItem().toString();

        // Check namespace
        return itemId.startsWith(ARCHERS_MOD_ID + ":") ||
               itemId.startsWith(PALADINS_MOD_ID + ":") ||
               itemId.startsWith(WIZARDS_MOD_ID + ":") ||
               itemId.startsWith(RUNES_MOD_ID + ":") ||
               itemId.startsWith(ROGUES_MOD_ID + ":");
    }

    /**
     * Get the class type for an RpgSeries item.
     *
     * @param stack The item stack
     * @return Class name (Archer, Paladin, Wizard, etc.) or null
     */
    public static String getRpgClass(ItemStack stack) {
        if (!anyAvailable || stack == null || stack.isEmpty()) {
            return null;
        }

        String itemId = stack.getItem().toString();

        if (itemId.startsWith(ARCHERS_MOD_ID + ":")) return "Archer";
        if (itemId.startsWith(PALADINS_MOD_ID + ":")) return "Paladin";
        if (itemId.startsWith(WIZARDS_MOD_ID + ":")) return "Wizard";
        if (itemId.startsWith(RUNES_MOD_ID + ":")) return "Rune User";
        if (itemId.startsWith(ROGUES_MOD_ID + ":")) return "Rogue";

        return null;
    }

    /**
     * Check if an item is a spell-casting weapon.
     *
     * @param stack The item stack
     * @return true if spell caster
     */
    public static boolean isSpellCaster(ItemStack stack) {
        if (stack == null || stack.isEmpty() || spellCasterItemClass == null) {
            return false;
        }
        return spellCasterItemClass.isInstance(stack.getItem());
    }

    /**
     * Check if an item is a ranged weapon from RpgSeries.
     *
     * @param stack The item stack
     * @return true if ranged weapon
     */
    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty() || rangedWeaponItemClass == null) {
            return false;
        }
        return rangedWeaponItemClass.isInstance(stack.getItem());
    }

    /**
     * Get equipped class items info for a player.
     *
     * @param player The player
     * @return Map of class equipment info
     */
    public static Map<String, Object> getEquippedClassInfo(Player player) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!anyAvailable || player == null) {
            return info;
        }

        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (isRpgSeriesItem(mainHand)) {
            String rpgClass = getRpgClass(mainHand);
            if (rpgClass != null) {
                info.put("mainHandClass", rpgClass);
                info.put("mainHandItem", mainHand.getHoverName().getString());
            }
            if (isSpellCaster(mainHand)) {
                info.put("isSpellCaster", true);
            }
            if (isRangedWeapon(mainHand)) {
                info.put("isRangedWeapon", true);
            }
        }

        // Check offhand
        ItemStack offHand = player.getOffhandItem();
        if (isRpgSeriesItem(offHand)) {
            String rpgClass = getRpgClass(offHand);
            if (rpgClass != null) {
                info.put("offHandClass", rpgClass);
                info.put("offHandItem", offHand.getHoverName().getString());
            }
        }

        // Determine primary class based on equipment
        String primaryClass = (String) info.get("mainHandClass");
        if (primaryClass == null) {
            primaryClass = (String) info.get("offHandClass");
        }
        if (primaryClass != null) {
            info.put("primaryClass", primaryClass);
        }

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!anyAvailable) {
            return "RpgSeries: not available";
        }

        List<String> loaded = new ArrayList<>();
        if (archersAvailable) loaded.add("Archers");
        if (paladinsAvailable) loaded.add("Paladins");
        if (wizardsAvailable) loaded.add("Wizards");
        if (runesAvailable) loaded.add("Runes");
        if (roguesAvailable) loaded.add("Rogues");

        return "RpgSeries: " + String.join(", ", loaded);
    }
}

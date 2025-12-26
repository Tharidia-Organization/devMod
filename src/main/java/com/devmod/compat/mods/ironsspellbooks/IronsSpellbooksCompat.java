package com.devmod.compat.mods.ironsspellbooks;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
public class IronsSpellbooksCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsSpellbooksCompat.class);
    public static final String MOD_ID = "irons_spellbooks";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> magicDataClass;
    private static Class<?> abstractSpellClass;
    private static Class<?> magicHelperClass;

    private static Method getPlayerMagicDataMethod;
    private static Method getManaMethod;
    private static Method getMaxManaMethod;
    private static Method isCastingMethod;
    private static Method getCastingSpellMethod;

    // School type constants (for reference)
    public static final String SCHOOL_FIRE = "fire";
    public static final String SCHOOL_ICE = "ice";
    public static final String SCHOOL_LIGHTNING = "lightning";
    public static final String SCHOOL_HOLY = "holy";
    public static final String SCHOOL_ENDER = "ender";
    public static final String SCHOOL_BLOOD = "blood";
    public static final String SCHOOL_EVOCATION = "evocation";
    public static final String SCHOOL_NATURE = "nature";
    public static final String SCHOOL_ELDRITCH = "eldritch";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Iron's Spells 'n Spellbooks";
    }

    @Override
    public int priority() {
        // High priority - major magic system
        return 25;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Iron's Spellbooks API classes via reflection
            magicDataClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            abstractSpellClass = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            magicHelperClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicHelper");

            // Get MagicHelper.getPlayerMagicData(Player)
            getPlayerMagicDataMethod = magicHelperClass.getMethod("getPlayerMagicData", Player.class);

            // Get MagicData methods
            getManaMethod = magicDataClass.getMethod("getMana");
            getMaxManaMethod = magicDataClass.getMethod("getMaxMana");
            isCastingMethod = magicDataClass.getMethod("isCasting");

            // Try to get getCastingSpell method
            try {
                getCastingSpellMethod = magicDataClass.getMethod("getCastingSpell");
            } catch (NoSuchMethodException e) {
                // May have different name in some versions
                LOGGER.debug("[Compat:irons_spellbooks] getCastingSpell method not found, using fallback");
            }

            available = true;
            LOGGER.info("[Compat:irons_spellbooks] Iron's Spellbooks detected and available");
            LOGGER.debug("[Compat:irons_spellbooks] Version: {}", Compat.getVersion(MOD_ID));
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:irons_spellbooks] Iron's Spellbooks classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            available = false;
            LOGGER.warn("[Compat:irons_spellbooks] API method not found: {}", e.getMessage());
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:irons_spellbooks] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:irons_spellbooks] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Mana tracking, spell damage integration, magic stats in HUD";
    }

    /**
     * Check if Iron's Spellbooks is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get the MagicData for a player.
     *
     * @param player The player
     * @return MagicData object, or null if not available
     */
    @Nullable
    public static Object getMagicData(Player player) {
        if (!available || player == null || getPlayerMagicDataMethod == null) {
            return null;
        }

        try {
            return getPlayerMagicDataMethod.invoke(null, player);
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get MagicData: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the current mana of a player.
     *
     * @param player The player
     * @return Current mana, or -1 if not available
     */
    public static float getMana(Player player) {
        Object magicData = getMagicData(player);
        if (magicData == null || getManaMethod == null) {
            return -1f;
        }

        try {
            Object result = getManaMethod.invoke(magicData);
            if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get mana: {}", e.getMessage());
        }

        return -1f;
    }

    /**
     * Get the maximum mana of a player.
     *
     * @param player The player
     * @return Max mana, or -1 if not available
     */
    public static float getMaxMana(Player player) {
        Object magicData = getMagicData(player);
        if (magicData == null || getMaxManaMethod == null) {
            return -1f;
        }

        try {
            Object result = getMaxManaMethod.invoke(magicData);
            if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get max mana: {}", e.getMessage());
        }

        return -1f;
    }

    /**
     * Get the mana percentage (0.0 to 1.0) for a player.
     *
     * @param player The player
     * @return Mana percentage, or -1 if not available
     */
    public static float getManaPercentage(Player player) {
        float mana = getMana(player);
        float maxMana = getMaxMana(player);

        if (mana < 0 || maxMana <= 0) {
            return -1f;
        }

        return mana / maxMana;
    }

    /**
     * Check if a player is currently casting a spell.
     *
     * @param player The player
     * @return true if casting, false otherwise
     */
    public static boolean isCasting(Player player) {
        Object magicData = getMagicData(player);
        if (magicData == null || isCastingMethod == null) {
            return false;
        }

        try {
            Object result = isCastingMethod.invoke(magicData);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to check casting state: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get the spell currently being cast.
     *
     * @param player The player
     * @return The AbstractSpell being cast, or null
     */
    @Nullable
    public static Object getCastingSpell(Player player) {
        if (getCastingSpellMethod == null) {
            return null;
        }

        Object magicData = getMagicData(player);
        if (magicData == null) {
            return null;
        }

        try {
            return getCastingSpellMethod.invoke(magicData);
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get casting spell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the name of the spell currently being cast.
     *
     * @param player The player
     * @return Spell name, or null if not casting
     */
    @Nullable
    public static String getCastingSpellName(Player player) {
        Object spell = getCastingSpell(player);
        if (spell == null) {
            return null;
        }

        try {
            Method getSpellIdMethod = abstractSpellClass.getMethod("getSpellId");
            Object spellId = getSpellIdMethod.invoke(spell);
            if (spellId != null) {
                return spellId.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get spell name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if an entity has magic capabilities (is a magic user).
     *
     * @param entity The entity to check
     * @return true if the entity has magic data
     */
    public static boolean hasMagicData(LivingEntity entity) {
        if (!available || entity == null) {
            return false;
        }

        if (entity instanceof Player player) {
            return getMagicData(player) != null;
        }

        // For non-players, we'd need different API
        return false;
    }

    /**
     * Get a simple magic status string for display.
     *
     * @param player The player
     * @return Status string like "100/200 Mana" or "Casting: Fireball"
     */
    public static String getMagicStatusString(Player player) {
        if (!available) {
            return "";
        }

        float mana = getMana(player);
        float maxMana = getMaxMana(player);

        if (mana < 0) {
            return "";
        }

        if (isCasting(player)) {
            String spellName = getCastingSpellName(player);
            if (spellName != null) {
                return String.format("Casting: %s (%.0f/%.0f)", spellName, mana, maxMana);
            }
        }

        return String.format("Mana: %.0f/%.0f", mana, maxMana);
    }

    /**
     * Get mana regeneration rate if available.
     * This is a placeholder - actual implementation depends on mod API.
     *
     * @param player The player
     * @return Mana regen per second, or -1 if not available
     */
    public static float getManaRegenRate(Player player) {
        // This would require additional API access
        // Placeholder for future implementation
        return -1f;
    }

    /**
     * Check if Iron's Spellbooks API is fully functional.
     * Performs a simple test to ensure reflection works.
     */
    public static boolean isApiFullyFunctional() {
        return available
            && magicDataClass != null
            && getPlayerMagicDataMethod != null
            && getManaMethod != null;
    }
}

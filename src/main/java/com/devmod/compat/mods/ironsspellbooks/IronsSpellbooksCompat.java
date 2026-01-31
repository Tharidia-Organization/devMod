package com.devmod.compat.mods.ironsspellbooks;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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

            // Get MagicData.getPlayerMagicData(LivingEntity) - static method on MagicData class
            getPlayerMagicDataMethod = magicDataClass.getMethod("getPlayerMagicData", LivingEntity.class);

            // Get MagicData methods
            getManaMethod = magicDataClass.getMethod("getMana");
            isCastingMethod = magicDataClass.getMethod("isCasting");

            // getMaxMana may not exist in newer versions - max mana is via player attributes
            try {
                getMaxManaMethod = magicDataClass.getMethod("getMaxMana");
            } catch (NoSuchMethodException e) {
                LOGGER.debug("[Compat:irons_spellbooks] getMaxMana not on MagicData - will use player attributes");
            }

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
     * In newer versions, max mana comes from player attributes instead of MagicData.
     *
     * @param player The player
     * @return Max mana, or -1 if not available
     */
    public static float getMaxMana(Player player) {
        if (!available || player == null) {
            return -1f;
        }

        // Try MagicData.getMaxMana() first (older versions)
        if (getMaxManaMethod != null) {
            Object magicData = getMagicData(player);
            if (magicData != null) {
                try {
                    Object result = getMaxManaMethod.invoke(magicData);
                    if (result instanceof Number) {
                        return ((Number) result).floatValue();
                    }
                } catch (Exception e) {
                    LOGGER.debug("[Compat:irons_spellbooks] Failed to get max mana from MagicData: {}", e.getMessage());
                }
            }
        }

        // Fallback: get max mana from player attribute (newer versions)
        try {
            Class<?> attributeRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            java.lang.reflect.Field maxManaField = attributeRegistry.getField("MAX_MANA");
            Object maxManaHolder = maxManaField.get(null);

            if (maxManaHolder != null) {
                // Use reflection to call player.getAttributeValue(holder)
                java.lang.reflect.Method getAttrValue = Player.class.getMethod("getAttributeValue",
                    net.minecraft.core.Holder.class);
                Object result = getAttrValue.invoke(player, maxManaHolder);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get max mana from attributes: {}", e.getMessage());
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
     * Find the likely casting item in the player's hands.
     */
    @Nullable
    public static ItemStack findCastingItem(Player player) {
        if (!available || player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack main = player.getMainHandItem();
        if (isSpellbookItem(main)) {
            return main;
        }

        ItemStack off = player.getOffhandItem();
        if (isSpellbookItem(off)) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isSpellbookItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null && MOD_ID.equals(key.getNamespace())) {
            return true;
        }
        String className = stack.getItem().getClass().getName();
        return className.startsWith("io.redspace.ironsspellbooks");
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
     * Retrieves the MANA_REGEN attribute value from Iron's Spellbooks.
     *
     * @param player The player
     * @return Mana regen per second, or -1 if not available
     */
    public static float getManaRegenRate(Player player) {
        if (!available || player == null) {
            return -1f;
        }

        try {
            // Get MANA_REGEN attribute from Iron's Spellbooks AttributeRegistry
            Class<?> attributeRegistry = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            java.lang.reflect.Field manaRegenField = attributeRegistry.getField("MANA_REGEN");
            Object manaRegenHolder = manaRegenField.get(null);

            if (manaRegenHolder != null) {
                // Use reflection to call player.getAttributeValue(holder)
                java.lang.reflect.Method getAttrValue = Player.class.getMethod("getAttributeValue",
                    net.minecraft.core.Holder.class);
                Object result = getAttrValue.invoke(player, manaRegenHolder);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:irons_spellbooks] AttributeRegistry not found: {}", e.getMessage());
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[Compat:irons_spellbooks] MANA_REGEN field not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("[Compat:irons_spellbooks] Failed to get mana regen rate: {}", e.getMessage());
        }

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

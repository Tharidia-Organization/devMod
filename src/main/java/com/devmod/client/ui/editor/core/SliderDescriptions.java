package com.devmod.client.ui.editor.core;

import net.minecraft.client.resources.language.I18n;

import java.util.Objects;

/**
 * Centralized registry for slider descriptions.
 * Uses Minecraft's i18n system for translations.
 *
 * All description keys follow the pattern: devmod.slider.{category}.{id}.desc
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.2
 */
public final class SliderDescriptions {

    private SliderDescriptions() {} // Utility class

    // ═══════════════════════════════════════════════════════════════
    // TRANSLATION KEY PREFIXES
    // ═══════════════════════════════════════════════════════════════

    private static final String PREFIX = "devmod.slider.";
    private static final String DESC_SUFFIX = ".desc";

    // ═══════════════════════════════════════════════════════════════
    // WEAPON MODULE - COMBAT TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String ATTACK_DAMAGE = "weapon.attack_damage";
    public static final String ATTACK_SPEED = "weapon.attack_speed";
    public static final String ATTACK_REACH = "weapon.attack_reach";
    public static final String ATTACK_KNOCKBACK = "weapon.knockback";
    public static final String DAMAGE_BONUS = "weapon.damage_bonus";
    public static final String SWEEPING_RATIO = "weapon.sweeping_ratio";
    public static final String ARMOR_PENETRATION = "weapon.armor_penetration";
    public static final String BASE_DAMAGE_BONUS = "weapon.base_damage_bonus";
    public static final String ARMOR_SHRED = "weapon.armor_shred";

    // ═══════════════════════════════════════════════════════════════
    // WEAPON MODULE - SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String CRIT_CHANCE = "weapon.crit_chance";
    public static final String CRIT_DAMAGE = "weapon.crit_damage";
    public static final String LIFESTEAL = "weapon.lifesteal";
    public static final String FIRE_DAMAGE = "weapon.fire_damage";
    public static final String MAGIC_DAMAGE = "weapon.magic_damage";

    // ═══════════════════════════════════════════════════════════════
    // WEAPON MODULE - DAMAGE TYPES TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String VS_UNDEAD = "weapon.vs_undead";
    public static final String VS_ARTHROPODS = "weapon.vs_arthropods";
    public static final String VS_PLAYERS = "weapon.vs_players";
    public static final String TRUE_DAMAGE = "weapon.true_damage";

    // ═══════════════════════════════════════════════════════════════
    // ARMOR MODULE - DAMAGE REDUCTION TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String PHYSICAL_REDUCTION = "armor.physical_reduction";
    public static final String FIRE_REDUCTION = "armor.fire_reduction";
    public static final String MAGIC_REDUCTION = "armor.magic_reduction";
    public static final String EXPLOSION_REDUCTION = "armor.explosion_reduction";
    public static final String PROJECTILE_REDUCTION = "armor.projectile_reduction";

    // ═══════════════════════════════════════════════════════════════
    // ARMOR MODULE - VANILLA STATS TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String ARMOR_BONUS = "armor.armor_bonus";
    public static final String TOUGHNESS_BONUS = "armor.toughness_bonus";
    public static final String KNOCKBACK_RESISTANCE = "armor.knockback_resistance";

    // ═══════════════════════════════════════════════════════════════
    // ARMOR MODULE - SPECIAL TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String THORNS_DAMAGE = "armor.thorns_damage";
    public static final String SHIELD_BLOCK_STRENGTH = "armor.shield_block_strength";
    public static final String SHIELD_RECOVERY = "armor.shield_recovery";

    // ═══════════════════════════════════════════════════════════════
    // RANGED MODULE - MECHANICS TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String DRAW_SPEED = "ranged.draw_speed";
    public static final String CHARGE_TIME = "ranged.charge_time";
    public static final String ACCURACY = "ranged.accuracy";
    public static final String RANGE = "ranged.range";

    // ═══════════════════════════════════════════════════════════════
    // RANGED MODULE - PROJECTILE TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String PROJECTILE_SPEED = "ranged.projectile_speed";
    public static final String PROJECTILE_GRAVITY = "ranged.projectile_gravity";
    public static final String PROJECTILE_SPREAD = "ranged.projectile_spread";
    public static final String RANGED_BASE_DAMAGE = "ranged.base_damage";
    public static final String PIERCING = "ranged.piercing";
    public static final String MULTISHOT_COUNT = "ranged.multishot_count";

    // ═══════════════════════════════════════════════════════════════
    // RANGED MODULE - DAMAGE TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String RANGED_CRIT_CHANCE = "ranged.crit_chance";
    public static final String RANGED_CRIT_DAMAGE = "ranged.crit_damage";

    // ═══════════════════════════════════════════════════════════════
    // RANGED MODULE - TRIDENT TAB
    // ═══════════════════════════════════════════════════════════════

    public static final String LOYALTY_SPEED = "ranged.loyalty_speed";
    public static final String RIPTIDE_DISTANCE = "ranged.riptide_distance";

    // ═══════════════════════════════════════════════════════════════
    // GENERAL MODULE
    // ═══════════════════════════════════════════════════════════════

    public static final String STACK_SIZE = "general.stack_size";
    public static final String DURABILITY = "general.durability";
    public static final String REPAIR_COST = "general.repair_cost";

    // ═══════════════════════════════════════════════════════════════
    // TOGGLE DESCRIPTIONS
    // ═══════════════════════════════════════════════════════════════

    public static final String UNBREAKABLE = "general.unbreakable";
    public static final String THORNS_REFLECT = "armor.thorns_reflect";
    public static final String SHIELD_REFLECT = "armor.shield_reflect";
    public static final String MULTISHOT = "ranged.multishot";
    public static final String INFINITY = "ranged.infinity";
    public static final String RIPTIDE_WATER = "ranged.riptide_water";
    public static final String CHANNELING = "ranged.channeling";

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the translation key for a slider description.
     *
     * @param key The slider key (e.g., ATTACK_DAMAGE)
     * @return The full translation key
     */
    public static String getKey(String key) {
        return PREFIX + key + DESC_SUFFIX;
    }

    /**
     * Get the translated description for a slider.
     * Falls back to the key if no translation is found.
     *
     * @param key The slider key (e.g., ATTACK_DAMAGE)
     * @return The translated description
     */
    public static String get(String key) {
        String translationKey = getKey(key);
        String translated = I18n.get(Objects.requireNonNull(translationKey, "translationKey"));
        if (translated == null) return "";
        // If translation returns the key itself, it means no translation was found
        return translated.equals(translationKey) ? "" : translated;
    }

    /**
     * Check if a translation exists for the given key.
     *
     * @param key The slider key
     * @return true if a translation exists
     */
    public static boolean hasTranslation(String key) {
        String translationKey = getKey(key);
        return I18n.exists(Objects.requireNonNull(translationKey, "translationKey"));
    }

    /**
     * Get a description with fallback support.
     * If no translation exists, returns the fallback text.
     *
     * @param key The slider key
     * @param fallback Fallback text if no translation exists
     * @return The description
     */
    public static String getOrFallback(String key, String fallback) {
        String translated = get(key);
        return translated.isEmpty() ? fallback : translated;
    }
}

package com.devmod.damage;

import com.devmod.config.ArmorConfigManager;
import com.devmod.stats.ArmorStats;
import com.devmod.config.Config;
import com.devmod.combat.HitHelper;
import com.devmod.combat.signature.SoulImprintManager;
import com.devmod.stats.WeaponStats;
// DamageBreakdown is now in the same package
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Centralized damage calculation logic extracted from DamageHandler.
 * Handles armor penetration, true damage, custom armor reduction, and damage type bonuses.
 *
 * <p>This class provides pure calculation methods without side effects,
 * making the damage pipeline easier to test and maintain.</p>
 */
public final class DamageCalculator {

    private DamageCalculator() {} // Utility class

    /**
     * Result of a complete damage calculation.
     *
     * @param finalDamage The final damage after all modifiers
     * @param armorPenBonus Bonus damage from armor penetration
     * @param trueDamagePortion Portion of damage that bypassed armor
     * @param armorReduction Custom armor reduction applied (0.0-0.8)
     * @param bodyPartMultiplier Multiplier from body part hit
     */
    public record CalculationResult(
        float finalDamage,
        float armorPenBonus,
        float trueDamagePortion,
        float armorReduction,
        float bodyPartMultiplier
    ) {
        /**
         * Creates a result for environmental damage (no modifiers).
         */
        public static CalculationResult environmental(float damage) {
            return new CalculationResult(damage, 0f, 0f, 0f, 1.0f);
        }
    }

    /**
     * Result of a damage calculation with HUD breakdown.
     *
     * @param result Core calculation data
     * @param breakdown HUD damage breakdown
     */
    public record CalculationDetails(
        CalculationResult result,
        DamageBreakdown breakdown
    ) {}

    /**
     * Calculates final damage applying all weapon stats modifiers.
     *
     * @param baseDamage Original damage amount
     * @param stats Weapon stats to apply
     * @param bodyPartMultiplier Multiplier from hit location
     * @param victim Entity taking damage
     * @param source Damage source for type detection
     * @return Complete calculation result
     */
    public static CalculationResult calculate(
            float baseDamage,
            WeaponStats stats,
            float bodyPartMultiplier,
            LivingEntity victim,
            DamageSource source) {

        float damage = (baseDamage + stats.baseDamageBonus) * bodyPartMultiplier;

        // Damage bonus (additive)
        if (stats.damageBonus > 0) {
            damage += baseDamage * stats.damageBonus;
        }

        // Armor penetration + shred
        float armorPenBonus = 0f;
        float targetArmor = Math.max(0f, victim.getArmorValue() - stats.armorShred);
        if (stats.armorPenetration > 0) {
            armorPenBonus = calculateArmorPenBonus(stats.armorPenetration, targetArmor, damage);
            damage += armorPenBonus;
        }

        // True damage calculation - extract portion that bypasses armor
        float trueDamagePortion = 0f;
        if (stats.trueDamagePercent > 0) {
            trueDamagePortion = damage * stats.trueDamagePercent;
            damage = damage * (1f - stats.trueDamagePercent);
        }

        // Custom armor reduction (DevMod ArmorStats)
        float armorReduction = 0f;
        if (victim instanceof Player playerVictim) {
            armorReduction = calculateCustomArmorReduction(playerVictim, source);
            if (armorReduction > 0) {
                damage = damage * (1.0f - armorReduction);
            }
        }

        // Add back true damage (bypasses armor)
        damage += trueDamagePortion;

        // Damage type bonuses
        damage = applyDamageTypeBonuses(damage, stats, victim);

        // Elemental bonuses
        if (stats.fireDamageBonus > 0) {
            damage *= (1f + stats.fireDamageBonus / 100f);
        }
        if (stats.magicDamageBonus > 0) {
            damage *= (1f + stats.magicDamageBonus / 100f);
        }

        return new CalculationResult(damage, armorPenBonus, trueDamagePortion, armorReduction, bodyPartMultiplier);
    }

    /**
     * Applies signature weapon trait effects to damage.
     *
     * @param baseDamage The calculated damage before trait effects
     * @param weapon The weapon being used
     * @param isHeadshot Whether this is a headshot
     * @param isBoss Whether target is a boss mob
     * @param targetHealthPercent Target's current health percentage (0.0-1.0)
     * @return Modified damage with trait effects applied
     */
    public static float applySignatureTraitEffects(
            float baseDamage,
            ItemStack weapon,
            boolean isHeadshot,
            boolean isBoss,
            float targetHealthPercent) {

        if (weapon == null || weapon.isEmpty()) {
            return baseDamage;
        }

        SoulImprintManager.TraitEffects effects = SoulImprintManager.INSTANCE.getTraitEffects(weapon);
        if (!effects.hasEffects()) {
            return baseDamage;
        }

        float damage = baseDamage;

        // Base damage percent bonus
        if (effects.damagePercent() > 0) {
            damage *= (1f + effects.damagePercent());
        }

        // Headshot bonus
        if (isHeadshot && effects.headshotBonus() > 0) {
            damage *= (1f + effects.headshotBonus());
        }

        // Boss damage bonus
        if (isBoss && effects.bossDamage() > 0) {
            damage *= (1f + effects.bossDamage());
        }

        // Execute threshold - massive damage to low health targets
        if (effects.executeThreshold() > 0 && targetHealthPercent <= effects.executeThreshold()) {
            damage *= 10f; // Execute effect
        }

        return damage;
    }

    /**
     * Get lifesteal amount from signature weapon traits.
     */
    public static float getTraitLifesteal(ItemStack weapon, float damageDealt) {
        if (weapon == null || weapon.isEmpty()) {
            return 0f;
        }

        SoulImprintManager.TraitEffects effects = SoulImprintManager.INSTANCE.getTraitEffects(weapon);
        return damageDealt * effects.lifesteal();
    }

    /**
     * Get crit chance bonus from signature weapon traits.
     */
    public static float getTraitCritChanceBonus(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0f;
        }

        return SoulImprintManager.INSTANCE.getTraitEffects(weapon).critChance();
    }

    /**
     * Get damage reduction from signature weapon traits (for defensive traits).
     */
    public static float getTraitDamageReduction(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0f;
        }

        return SoulImprintManager.INSTANCE.getTraitEffects(weapon).damageReduction();
    }

    /**
     * Calculates final damage and builds a HUD breakdown in one step.
     */
    public static CalculationDetails calculateWithBreakdown(
            ItemStack weapon,
            LivingEntity attacker,
            LivingEntity victim,
            HitHelper.BodyPart bodyPart,
            float baseDamage,
            WeaponStats stats,
            DamageSource source) {
        CalculationResult result = calculate(baseDamage, stats, bodyPart, victim, source);
        ItemStack safeWeapon = weapon == null ? ItemStack.EMPTY : weapon;
        DamageBreakdown breakdown = new DamageBreakdown(
            safeWeapon,
            attacker,
            victim,
            baseDamage,
            result.bodyPartMultiplier(),
            result.armorPenBonus()
        );

        return new CalculationDetails(result, breakdown);
    }

    /**
     * Builds a breakdown for environmental damage (no weapon stats modifiers).
     */
    public static CalculationDetails calculateEnvironmentalWithBreakdown(
            LivingEntity victim,
            float baseDamage) {
        CalculationResult result = CalculationResult.environmental(baseDamage);
        DamageBreakdown breakdown = new DamageBreakdown(
            ItemStack.EMPTY,
            null,
            victim,
            baseDamage,
            1.0f,
            0f
        );

        return new CalculationDetails(result, breakdown);
    }

    /**
     * Calculates final damage using the body part hit to select the multiplier.
     */
    public static CalculationResult calculate(
            float baseDamage,
            WeaponStats stats,
            HitHelper.BodyPart bodyPart,
            LivingEntity victim,
            DamageSource source) {
        float bodyPartMultiplier = getBodyPartMultiplier(stats, bodyPart);
        return calculate(baseDamage, stats, bodyPartMultiplier, victim, source);
    }

    /**
     * Resolves the body part multiplier from weapon stats.
     */
    public static float getBodyPartMultiplier(WeaponStats stats, HitHelper.BodyPart bodyPart) {
        if (stats == null || bodyPart == null) {
            return 1.0f;
        }

        return switch (bodyPart) {
            case HEAD -> stats.headMult;
            case BODY -> stats.bodyMult;
            case ARMS -> stats.armsMult;
            case LEGS -> stats.legsMult;
        };
    }

    /**
     * Applies ranged-specific modifiers (speed scaling, crit).
     *
     * @param result Base calculation result
     * @param speedMultiplier Projectile speed multiplier (1.0 = no change)
     * @param critChance Chance to crit (0.0-1.0)
     * @param critDamage Crit damage multiplier
     * @return Modified damage
     */
    public static float applyRangedModifiers(CalculationResult result, float speedMultiplier, float critChance, float critDamage) {
        float damage = result.finalDamage;

        if (speedMultiplier > 0) {
            damage *= speedMultiplier;
        }

        if (critChance > 0 && Math.random() < critChance) {
            damage *= critDamage;
        }

        return damage;
    }

    /**
     * Calculates armor penetration bonus based on configured formula.
     *
     * @param armorPen Armor penetration percentage (0.0-1.0)
     * @param armorValue Target's effective armor value
     * @param baseDamage Base damage before armor pen
     * @return Bonus damage to add
     */
    public static float calculateArmorPenBonus(float armorPen, float armorValue, float baseDamage) {
        Config.ArmorPenFormula formula;
        double multiplier;
        double flatBonus;

        try {
            formula = Config.ARMOR_PEN_FORMULA.get();
            multiplier = Config.ARMOR_PEN_MULTIPLIER.get();
            flatBonus = Config.ARMOR_PEN_FLAT_BONUS.get();
        } catch (Exception e) {
            formula = Config.ArmorPenFormula.SIMPLE;
            multiplier = 0.5;
            flatBonus = 2.0;
        }

        return switch (formula) {
            case SIMPLE -> {
                float ignoredArmor = armorValue * armorPen;
                yield ignoredArmor * (float) multiplier;
            }
            case VANILLA_ACCURATE -> {
                float effectiveArmor = Math.min(20f, Math.max(armorValue / 5f, armorValue - baseDamage / 2f));
                float armorReduction = effectiveArmor / 25f;
                float blockedDamage = baseDamage * armorReduction;
                yield blockedDamage * armorPen * (float) multiplier;
            }
            case PERCENTAGE -> {
                float effectiveArmor = Math.min(20f, armorValue);
                float normalReduction = effectiveArmor / 25f;
                float reducedReduction = normalReduction * (1f - armorPen);
                float bonusDamage = baseDamage * (normalReduction - reducedReduction);
                yield bonusDamage * (float) multiplier;
            }
            case FLAT_BONUS -> armorPen * (float) flatBonus;
        };
    }

    /**
     * Calculates custom armor reduction from DevMod ArmorStats.
     * Sums reduction from all equipped armor pieces, capped at 80%.
     *
     * @param player The player taking damage
     * @param source The damage source
     * @return Total reduction percentage (0.0-0.8)
     */
    public static float calculateCustomArmorReduction(Player player, DamageSource source) {
        float totalReduction = 0f;

        boolean isFire = source.is(Objects.requireNonNull(DamageTypeTags.IS_FIRE));
        boolean isExplosion = source.is(Objects.requireNonNull(DamageTypeTags.IS_EXPLOSION));
        boolean isProjectile = source.is(Objects.requireNonNull(DamageTypeTags.IS_PROJECTILE));
        boolean isMagic = source.is(Objects.requireNonNull(DamageTypeTags.WITCH_RESISTANT_TO));
        boolean isPhysical = !isFire && !isExplosion && !isMagic;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof ArmorItem)) continue;

            ArmorStats stats = ArmorConfigManager.getStats(armor);
            if (stats.isDefault()) continue;

            totalReduction += stats.getReductionFor(isPhysical, isFire, isMagic, isExplosion, isProjectile);
        }

        return Math.min(totalReduction, 0.8f);
    }

    /**
     * Applies damage type bonuses (vs players, undead, arthropods).
     */
    private static float applyDamageTypeBonuses(float damage, WeaponStats stats, LivingEntity victim) {
        if (victim instanceof Player) {
            return damage * (1f + stats.damageVsPlayers);
        }

        if (victim instanceof net.minecraft.world.entity.Mob) {
            try {
                if (victim.getType().is(Objects.requireNonNull(net.minecraft.tags.EntityTypeTags.UNDEAD))) {
                    return damage * (1f + stats.damageVsUndead);
                } else if (victim.getType().is(Objects.requireNonNull(net.minecraft.tags.EntityTypeTags.ARTHROPOD))) {
                    return damage * (1f + stats.damageVsArthropods);
                }
            } catch (Exception ignored) {
                // Tag not available, no bonus
            }
        }

        return damage;
    }
}

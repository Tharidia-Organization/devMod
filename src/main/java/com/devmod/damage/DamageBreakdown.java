package com.devmod.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.devmod.integration.ModIntegrationManager;

public class DamageBreakdown {
    private final float baseWeaponDamage;
    private final List<EnchantBonus> enchantBonuses;
    private final float pehkuiSizeBonus;
    private final float pehkuiScale;
    private final float bodyPartMultiplier;
    private final float armorPenetrationBonus;
    private final float finalDamage;

    // Cached formula strings (computed once at construction)
    private final String cachedFormulaString;
    private final String cachedCompactString;

    public record EnchantBonus(String name, int level, float bonus) {}

    /**
     * Creates a damage breakdown.
     * @param weapon The weapon used
     * @param attacker The attacking entity (used for Pehkui scale bonus)
     * @param target The target entity (used for enchant conditionals like Smite vs undead)
     * @param baseDmg Base weapon damage
     * @param bodyPartMult Body part multiplier
     * @param armorPenBonus Armor penetration bonus
     */
    public DamageBreakdown(ItemStack weapon, @Nullable LivingEntity attacker, LivingEntity target,
                           float baseDmg, float bodyPartMult, float armorPenBonus) {
        this(weapon, attacker, target, baseDmg, bodyPartMult, armorPenBonus, Float.NaN);
    }

    /**
     * Creates a damage breakdown with the damage that was actually applied.
     * <p>
     * The display terms below (enchant bonuses in particular) are informational: vanilla has
     * already folded Sharpness/Smite into the incoming amount, and {@link DamageCalculator}
     * applies further modifiers this class does not model. Re-deriving the total from the
     * display terms therefore shows the player a number that was never dealt.
     *
     * @param actualFinalDamage damage produced by {@link DamageCalculator}, or {@link Float#NaN}
     *                          to fall back to deriving it from the display terms
     */
    public DamageBreakdown(ItemStack weapon, @Nullable LivingEntity attacker, LivingEntity target,
                           float baseDmg, float bodyPartMult, float armorPenBonus,
                           float actualFinalDamage) {
        this.baseWeaponDamage = baseDmg;
        this.bodyPartMultiplier = bodyPartMult;
        this.armorPenetrationBonus = armorPenBonus;
        this.enchantBonuses = new ArrayList<>();

        // Calculate enchant bonus
        calculateEnchantBonuses(weapon, target);

        // Pehkui size bonus (25% of base for each 1.0 of scale above 1.0)
        // BUG-001 FIX: Use ATTACKER scale, not target scale
        // A larger attacker should deal more damage, not the other way around
        Float scale = attacker != null ? ModIntegrationManager.getPehkuiScale(attacker) : null;
        if (scale != null && scale > 1.0f) {
            this.pehkuiScale = scale;
            this.pehkuiSizeBonus = baseDmg * 0.25f * (scale - 1.0f);
        } else {
            this.pehkuiScale = 1.0f;
            this.pehkuiSizeBonus = 0f;
        }

        float enchantTotal = (float) this.enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
        float subtotal = baseWeaponDamage + enchantTotal + pehkuiSizeBonus;
        this.finalDamage = Float.isNaN(actualFinalDamage)
            ? (subtotal * bodyPartMultiplier) + armorPenetrationBonus
            : actualFinalDamage;

        // BUG-010 FIX: Cache formula strings at construction (immutable data)
        this.cachedFormulaString = buildFormulaString();
        this.cachedCompactString = buildCompactString();
    }

    /**
     * Creates a damage breakdown from pre-computed values (for network sync).
     * Used when receiving ImpactSyncPayload from server.
     */
    public DamageBreakdown(float baseDmg, float enchantTotal, float pehkuiBonus,
                           float bodyPartMult, float armorPenBonus, float finalDmg) {
        this.baseWeaponDamage = baseDmg;
        this.bodyPartMultiplier = bodyPartMult;
        this.armorPenetrationBonus = armorPenBonus;
        this.enchantBonuses = new ArrayList<>();
        if (enchantTotal > 0) {
            // Store as a single combined entry for display
            this.enchantBonuses.add(new EnchantBonus("Enchants", 0, enchantTotal));
        }
        this.pehkuiSizeBonus = pehkuiBonus;
        this.pehkuiScale = pehkuiBonus > 0 ? 1.0f + (pehkuiBonus / (baseDmg * 0.25f)) : 1.0f;
        this.finalDamage = finalDmg;
        this.cachedFormulaString = buildFormulaString();
        this.cachedCompactString = buildCompactString();
    }

    private void calculateEnchantBonuses(ItemStack weapon, LivingEntity target) {
        if (weapon.isEmpty()) return;

        // NeoForge 1.21: Use DataComponents to access enchantments
        ItemEnchantments enchantments = weapon.get(Objects.requireNonNull(DataComponents.ENCHANTMENTS));
        if (enchantments == null || enchantments.isEmpty()) return;

        // Iterate over enchantments
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            Holder<Enchantment> safeHolder = Objects.requireNonNull(holder, "enchantment holder");
            int level = enchantments.getLevel(safeHolder);
            if (level <= 0) continue;

            String enchName = Objects.requireNonNull(safeHolder.getRegisteredName(), "enchantment id");

            // Sharpness: +1.0 + 0.5 per additional level (always applies)
            if (enchName.contains("sharpness")) {
                float bonus = 1.0f + (level - 1) * 0.5f;
                enchantBonuses.add(new EnchantBonus("Sharpness " + toRoman(level), level, bonus));
            }
            // Smite: +2.5 per level vs undead ONLY
            // BUG-004 FIX: Only add bonus if target exists and is undead
            else if (enchName.contains("smite")) {
                if (target != null && target.isInvertedHealAndHarm()) {
                    float bonus = level * 2.5f;
                    enchantBonuses.add(new EnchantBonus("Smite " + toRoman(level), level, bonus));
                }
                // Note: Smite enchant exists but doesn't apply - don't show it
            }
            // Bane of Arthropods: +2.5 per level vs arthropods ONLY
            // BUG-004 FIX: Only add bonus if target exists and is arthropod
            else if (enchName.contains("bane_of_arthropods")) {
                if (target != null && isArthropod(target)) {
                    float bonus = level * 2.5f;
                    enchantBonuses.add(new EnchantBonus("Bane " + toRoman(level), level, bonus));
                }
                // Note: Bane enchant exists but doesn't apply - don't show it
            }
            // Fire Aspect: adds fire damage over time, not direct damage
            else if (enchName.contains("fire_aspect")) {
                // Fire aspect doesn't add direct damage, but we note it for completeness
                enchantBonuses.add(new EnchantBonus("Fire Aspect " + toRoman(level), level, 0f));
            }
        }
    }

    private boolean isArthropod(LivingEntity entity) {
        String typeName = entity.getType().getDescriptionId().toLowerCase(Locale.ROOT);
        return typeName.contains("spider") ||
               typeName.contains("silverfish") ||
               typeName.contains("endermite") ||
               typeName.contains("bee");
    }

    public float getTotalEnchantBonus() {
        return (float) enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
    }

    public float getBaseWeaponDamage() { return baseWeaponDamage; }
    public List<EnchantBonus> getEnchantBonuses() { return enchantBonuses; }
    public float getPehkuiSizeBonus() { return pehkuiSizeBonus; }
    public float getPehkuiScale() { return pehkuiScale; }
    public float getBodyPartMultiplier() { return bodyPartMultiplier; }
    public float getArmorPenetrationBonus() { return armorPenetrationBonus; }
    public float getFinalDamage() { return finalDamage; }

    private static String toRoman(int num) {
        return switch(num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    /**
     * Returns cached formula string for HUD.
     * Example: "(12.0+3.0+3.0) * 0.90 = 16.2"
     */
    public String getFormulaString() {
        return cachedFormulaString;
    }

    /**
     * Returns cached compact string for debug.
     */
    public String toCompactString() {
        return cachedCompactString;
    }

    /**
     * Builds formula string (called once at construction).
     */
    private String buildFormulaString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("(%.1f", baseWeaponDamage));

        for (EnchantBonus eb : enchantBonuses) {
            if (eb.bonus() > 0) {
                sb.append(String.format("+%.1f", eb.bonus()));
            }
        }

        if (pehkuiSizeBonus > 0) {
            sb.append(String.format("+%.1f", pehkuiSizeBonus));
        }

        sb.append(String.format(") * %.2f", bodyPartMultiplier));

        if (armorPenetrationBonus > 0) {
            sb.append(String.format(" + %.1f", armorPenetrationBonus));
        }

        sb.append(String.format(" = %.1f", finalDamage));
        return sb.toString();
    }

    /**
     * Builds compact string (called once at construction).
     * Note: Uses inline calculation to avoid this-escape warning from calling getTotalEnchantBonus()
     */
    private String buildCompactString() {
        float enchantTotal = (float) enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
        return String.format("Base:%.1f + Ench:%.1f + Pehkui:%.1f * BodyPart:%.2f = %.1f",
            baseWeaponDamage, enchantTotal, pehkuiSizeBonus, bodyPartMultiplier, finalDamage);
    }
}

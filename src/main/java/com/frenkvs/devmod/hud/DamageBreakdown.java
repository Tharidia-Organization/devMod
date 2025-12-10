package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.integration.ModIntegrationManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detailed breakdown of damage calculation for the HUD.
 * Shows each component: base, enchants, Pehkui bonus, body part multiplier.
 */
public class DamageBreakdown {
    public final float baseWeaponDamage;
    public final List<EnchantBonus> enchantBonuses;
    public final float pehkuiSizeBonus;
    public final float pehkuiScale;
    public final float bodyPartMultiplier;
    public final float armorPenetrationBonus;
    public final float finalDamage;

    public record EnchantBonus(String name, int level, float bonus) {}

    public DamageBreakdown(ItemStack weapon, LivingEntity target,
                           float baseDmg, float bodyPartMult, float armorPenBonus) {
        this.baseWeaponDamage = baseDmg;
        this.bodyPartMultiplier = bodyPartMult;
        this.armorPenetrationBonus = armorPenBonus;
        this.enchantBonuses = new ArrayList<>();

        // Calculate enchant bonus
        calculateEnchantBonuses(weapon, target);

        // Pehkui size bonus (25% of base for each 1.0 of scale above 1.0)
        Float scale = ModIntegrationManager.getPehkuiScale(target);
        if (scale != null && scale > 1.0f) {
            this.pehkuiScale = scale;
            this.pehkuiSizeBonus = baseDmg * 0.25f * (scale - 1.0f);
        } else {
            this.pehkuiScale = 1.0f;
            this.pehkuiSizeBonus = 0f;
        }

        // Final calculation: (base + enchants + pehkui) * bodyPartMult + armorPen
        // Note: calculate enchant bonus inline to avoid this-escape warning
        float enchantTotal = (float) this.enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
        float subtotal = baseWeaponDamage + enchantTotal + pehkuiSizeBonus;
        this.finalDamage = (subtotal * bodyPartMultiplier) + armorPenetrationBonus;
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

            // Sharpness: +1.0 + 0.5 per additional level
            if (enchName.contains("sharpness")) {
                float bonus = 1.0f + (level - 1) * 0.5f;
                enchantBonuses.add(new EnchantBonus("Sharpness " + toRoman(level), level, bonus));
            }
            // Smite: +2.5 per level vs undead
            else if (enchName.contains("smite")) {
                if (target.isInvertedHealAndHarm()) { // Undead check
                    float bonus = level * 2.5f;
                    enchantBonuses.add(new EnchantBonus("Smite " + toRoman(level), level, bonus));
                }
            }
            // Bane of Arthropods: +2.5 per livello vs arthropods
            else if (enchName.contains("bane_of_arthropods")) {
                // Arthropod check - spiders, silverfish, endermites, bees
                if (isArthropod(target)) {
                    float bonus = level * 2.5f;
                    enchantBonuses.add(new EnchantBonus("Bane " + toRoman(level), level, bonus));
                }
            }
            // Fire Aspect: nota che aggiunge danno fuoco, non diretto
            else if (enchName.contains("fire_aspect")) {
                // Fire aspect non aggiunge danno diretto, ma lo notiamo
                enchantBonuses.add(new EnchantBonus("Fire Aspect " + toRoman(level), level, 0f));
            }
        }
    }

    private boolean isArthropod(LivingEntity entity) {
        String typeName = entity.getType().getDescriptionId().toLowerCase();
        return typeName.contains("spider") ||
               typeName.contains("silverfish") ||
               typeName.contains("endermite") ||
               typeName.contains("bee");
    }

    public float getTotalEnchantBonus() {
        return (float) enchantBonuses.stream().mapToDouble(EnchantBonus::bonus).sum();
    }

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
     * Generates formula string for HUD.
     * Example: "(12.0+3.0+3.0) * 0.90 = 16.2"
     */
    public String getFormulaString() {
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
     * Generates compact string for debug.
     */
    public String toCompactString() {
        return String.format("Base:%.1f + Ench:%.1f + Pehkui:%.1f * BodyPart:%.2f = %.1f",
            baseWeaponDamage, getTotalEnchantBonus(), pehkuiSizeBonus, bodyPartMultiplier, finalDamage);
    }
}

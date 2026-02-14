package com.devmod.client.tooltip;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.devmod.DevMod;
import com.devmod.components.ArmorComponents;
import com.devmod.components.WeaponComponents;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;

/**
 * Appends DevMod combat stats to item tooltips when a weapon or armor
 * has custom stats stored via data components.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class StatsTooltipHandler {

    private static final int COLOR_HEADER = 0x39CDBE;   // Accent teal
    private static final int COLOR_LABEL = 0xB9C3D4;    // Text secondary
    private static final int COLOR_VALUE = 0xE7ECF4;    // Text primary
    private static final int COLOR_DAMAGE = 0xE06B5B;   // Error / red
    private static final int COLOR_CRIT = 0xF2A74B;     // Amber
    private static final int COLOR_FIRE = 0xFF6633;
    private static final int COLOR_MAGIC = 0x9966FF;
    private static final int COLOR_HEAL = 0x45D483;     // Success green
    private static final int COLOR_ARMOR = 0x4E9CFF;    // Blue
    private static final int COLOR_MUTED = 0x7A8899;

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        List<Component> weaponLines = buildWeaponTooltip(stack);
        if (!weaponLines.isEmpty()) {
            event.getToolTip().addAll(weaponLines);
            return;
        }

        List<Component> armorLines = buildArmorTooltip(stack);
        if (!armorLines.isEmpty()) {
            event.getToolTip().addAll(armorLines);
        }
    }

    private static List<Component> buildWeaponTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        DataComponentType<CompoundTag> type = WeaponComponents.weaponStatsComponent();
        if (type == null) return lines;

        CompoundTag tag = stack.get(type);
        if (tag == null || tag.isEmpty()) return lines;

        WeaponStats stats = WeaponStats.fromTag(tag);
        if (stats.isDefault()) return lines;

        // Separator
        lines.add(Component.empty());
        lines.add(Component.literal("--- DevMod Stats ---")
            .withStyle(Style.EMPTY.withColor(COLOR_HEADER).withItalic(true)));

        // Attack damage override
        if (stats.getAttackDamage() > 0) {
            lines.add(statLine("Attack Damage", String.format("%.1f", stats.getAttackDamage()), COLOR_DAMAGE));
        }

        // Attack speed override
        if (stats.getAttackSpeed() > 0) {
            lines.add(statLine("Attack Speed", String.format("%.2f", stats.getAttackSpeed()), COLOR_VALUE));
        }

        // Attack reach
        if (stats.getAttackReach() > 0) {
            lines.add(statLine("Attack Reach", String.format("%.1f", stats.getAttackReach()), COLOR_VALUE));
        }

        // Knockback
        if (stats.getAttackKnockback() > 0) {
            lines.add(statLine("Knockback", String.format("+%.1f", stats.getAttackKnockback()), COLOR_VALUE));
        }

        // Armor penetration
        if (stats.getArmorPenetration() > 0) {
            lines.add(statLine("Armor Penetration", String.format("%.0f%%", stats.getArmorPenetration() * 100), COLOR_CRIT));
        }

        // Armor shred
        if (stats.getArmorShred() > 0) {
            lines.add(statLine("Armor Shred", String.format("%.0f", stats.getArmorShred()), COLOR_CRIT));
        }

        // Critical hit
        if (stats.getCritChance() > 0) {
            lines.add(statLine("Crit Chance", String.format("%.0f%%", stats.getCritChance() * 100), COLOR_CRIT));
        }
        if (Float.compare(stats.getCritDamage(), 1.5f) != 0) {
            lines.add(statLine("Crit Damage", String.format("x%.1f", stats.getCritDamage()), COLOR_CRIT));
        }

        // Sweeping
        if (stats.getSweepingRatio() > 0) {
            lines.add(statLine("Sweeping", String.format("%.0f%%", stats.getSweepingRatio() * 100), COLOR_VALUE));
        }

        // Damage bonuses
        if (stats.getDamageBonus() > 0) {
            lines.add(statLine("Damage Bonus", String.format("+%.0f%%", stats.getDamageBonus() * 100), COLOR_DAMAGE));
        }
        if (stats.getBaseDamageBonus() > 0) {
            lines.add(statLine("Base Damage Bonus", String.format("+%.1f", stats.getBaseDamageBonus()), COLOR_DAMAGE));
        }

        // Elemental
        if (stats.getFireDamageBonus() > 0) {
            lines.add(statLine("Fire Damage", String.format("+%.1f", stats.getFireDamageBonus()), COLOR_FIRE));
        }
        if (stats.getMagicDamageBonus() > 0) {
            lines.add(statLine("Magic Damage", String.format("+%.1f", stats.getMagicDamageBonus()), COLOR_MAGIC));
        }
        if (stats.getTrueDamagePercent() > 0) {
            lines.add(statLine("True Damage", String.format("%.0f%%", stats.getTrueDamagePercent() * 100), COLOR_DAMAGE));
        }

        // Lifesteal
        if (stats.getLifesteal() > 0) {
            lines.add(statLine("Lifesteal", String.format("%.0f%%", stats.getLifesteal() * 100), COLOR_HEAL));
        }

        // Vs bonuses
        if (stats.getDamageVsUndead() > 0) {
            lines.add(statLine("vs Undead", String.format("+%.0f%%", stats.getDamageVsUndead() * 100), COLOR_VALUE));
        }
        if (stats.getDamageVsArthropods() > 0) {
            lines.add(statLine("vs Arthropods", String.format("+%.0f%%", stats.getDamageVsArthropods() * 100), COLOR_VALUE));
        }
        if (stats.getDamageVsPlayers() > 0) {
            lines.add(statLine("vs Players", String.format("+%.0f%%", stats.getDamageVsPlayers() * 100), COLOR_VALUE));
        }

        // Hit location multipliers (only show non-default)
        boolean hasNonDefaultHitLoc = false;
        if (Float.compare(stats.getHeadMult(), 1.5f) != 0 || Float.compare(stats.getBodyMult(), 1.0f) != 0
            || Float.compare(stats.getArmsMult(), 0.8f) != 0 || Float.compare(stats.getLegsMult(), 0.7f) != 0) {
            hasNonDefaultHitLoc = true;
        }
        if (hasNonDefaultHitLoc) {
            lines.add(Component.literal("  Hit Zones: ")
                .withStyle(Style.EMPTY.withColor(COLOR_MUTED))
                .append(Component.literal(String.format("H:%.1fx B:%.1fx A:%.1fx L:%.1fx",
                    stats.getHeadMult(), stats.getBodyMult(), stats.getArmsMult(), stats.getLegsMult()))
                    .withStyle(Style.EMPTY.withColor(COLOR_VALUE))));
        }

        // Durability info
        if (stats.isUnbreakable()) {
            lines.add(Component.literal("  Unbreakable")
                .withStyle(Style.EMPTY.withColor(COLOR_HEAL).withBold(true)));
        } else if (stats.getMaxDurability() > 0) {
            lines.add(statLine("Max Durability", String.valueOf(stats.getMaxDurability()), COLOR_VALUE));
        }

        return lines;
    }

    private static List<Component> buildArmorTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        if (!ArmorComponents.isArmorStatsBound()) return lines;

        CompoundTag tag;
        try {
            tag = stack.get(ArmorComponents.armorStatsComponent());
        } catch (IllegalStateException e) {
            return lines;
        }
        if (tag == null || tag.isEmpty()) return lines;

        ArmorStats stats = ArmorStats.fromTag(tag);
        if (stats.isDefault()) return lines;

        // Separator
        lines.add(Component.empty());
        lines.add(Component.literal("--- DevMod Stats ---")
            .withStyle(Style.EMPTY.withColor(COLOR_HEADER).withItalic(true)));

        // Vanilla-like bonuses
        if (stats.getArmorBonus() > 0) {
            lines.add(statLine("Armor Bonus", String.format("+%.0f", stats.getArmorBonus()), COLOR_ARMOR));
        }
        if (stats.getToughnessBonus() > 0) {
            lines.add(statLine("Toughness Bonus", String.format("+%.0f", stats.getToughnessBonus()), COLOR_ARMOR));
        }
        if (stats.getKnockbackResistance() > 0) {
            lines.add(statLine("KB Resistance", String.format("+%.0f%%", stats.getKnockbackResistance() * 100), COLOR_ARMOR));
        }

        // Damage reductions
        if (stats.getPhysicalReduction() > 0) {
            lines.add(statLine("Physical Reduction", String.format("%.0f%%", stats.getPhysicalReduction() * 100), COLOR_ARMOR));
        }
        if (stats.getFireReduction() > 0) {
            lines.add(statLine("Fire Reduction", String.format("%.0f%%", stats.getFireReduction() * 100), COLOR_FIRE));
        }
        if (stats.getMagicReduction() > 0) {
            lines.add(statLine("Magic Reduction", String.format("%.0f%%", stats.getMagicReduction() * 100), COLOR_MAGIC));
        }
        if (stats.getExplosionReduction() > 0) {
            lines.add(statLine("Explosion Reduction", String.format("%.0f%%", stats.getExplosionReduction() * 100), COLOR_CRIT));
        }
        if (stats.getProjectileReduction() > 0) {
            lines.add(statLine("Projectile Reduction", String.format("%.0f%%", stats.getProjectileReduction() * 100), COLOR_VALUE));
        }

        // Thorns
        if (stats.isThornsReflect() && stats.getThornsPercent() > 0) {
            lines.add(statLine("Thorns Reflect", String.format("%.0f%%", stats.getThornsPercent() * 100), COLOR_DAMAGE));
        }

        // Shield stats (only show non-default)
        if (Float.compare(stats.getShieldBlockStrength(), 0.5f) != 0) {
            lines.add(statLine("Shield Strength", String.format("%.0f%%", stats.getShieldBlockStrength() * 100), COLOR_ARMOR));
        }
        if (stats.isShieldReflectProjectiles()) {
            lines.add(Component.literal("  Reflects Projectiles")
                .withStyle(Style.EMPTY.withColor(COLOR_HEAL)));
        }

        return lines;
    }

    private static Component statLine(String label, String value, int valueColor) {
        return Component.literal("  " + label + ": ")
            .withStyle(Style.EMPTY.withColor(COLOR_LABEL))
            .append(Component.literal(value)
                .withStyle(Style.EMPTY.withColor(valueColor)));
    }
}

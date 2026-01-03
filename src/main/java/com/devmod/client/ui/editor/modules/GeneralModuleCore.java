package com.devmod.client.ui.editor.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.devmod.client.ui.editor.EditorStartTab;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.sections.ModuleSummarySection;
import com.devmod.config.FuelConfigManager;

/**
 * Core data management for GeneralModule.
 * Handles item type detection and summary building.
 */
public class GeneralModuleCore {

    public GeneralModuleCore() {
        // Default constructor
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM TYPE DETECTION
    // ═══════════════════════════════════════════════════════════════

    public boolean isWeaponItem(@Nullable ItemStack item) {
        return item != null && (
            item.getItem() instanceof SwordItem ||
            item.getItem() instanceof TieredItem ||
            item.getItem() instanceof ProjectileWeaponItem
        );
    }

    public boolean isArmorItem(@Nullable ItemStack item) {
        return item != null && item.getItem() instanceof ArmorItem;
    }

    public boolean isUsableItem(@Nullable ItemStack item) {
        if (item == null) return false;
        Item i = item.getItem();
        return i instanceof SnowballItem || i instanceof EggItem ||
               i instanceof EnderpearlItem || i instanceof ThrowablePotionItem ||
               i instanceof BowItem || i instanceof CrossbowItem ||
               i instanceof ShieldItem || i instanceof SpyglassItem ||
               i instanceof BrushItem ||
               item.has(Objects.requireNonNull(DataComponents.FOOD, "FOOD"));
    }

    public boolean isFoodItem(@Nullable ItemStack item) {
        return item != null && item.has(Objects.requireNonNull(DataComponents.FOOD, "FOOD"));
    }

    public boolean isFuelItem(@Nullable ItemStack item) {
        if (item == null) return false;
        return FuelConfigManager.isFuel(item);
    }

    public boolean hasEnchantments(@Nullable ItemStack item) {
        if (item == null) return false;
        var enchants = item.getOrDefault(
            Objects.requireNonNull(DataComponents.ENCHANTMENTS),
            Objects.requireNonNull(ItemEnchantments.EMPTY)
        );
        return !enchants.isEmpty();
    }

    public boolean hasRecipe(@Nullable ItemStack item) {
        return item != null;
    }

    // ═══════════════════════════════════════════════════════════════
    // AVAILABLE MODULES
    // ═══════════════════════════════════════════════════════════════

    public List<EditorStartTab> getAvailableModules(ItemStack item) {
        List<EditorStartTab> modules = new ArrayList<>();

        if (isWeaponItem(item)) {
            modules.add(EditorStartTab.WEAPON);
        }
        if (isArmorItem(item)) {
            modules.add(EditorStartTab.ARMOR);
        }
        if (hasRecipe(item)) {
            modules.add(EditorStartTab.RECIPE);
        }
        if (isUsableItem(item)) {
            modules.add(EditorStartTab.USABLE);
        }
        if (isFoodItem(item)) {
            modules.add(EditorStartTab.FOOD);
        }
        if (isFuelItem(item)) {
            modules.add(EditorStartTab.FUEL);
        }
        modules.add(EditorStartTab.GENERAL);

        return modules;
    }

    // ═══════════════════════════════════════════════════════════════
    // SUMMARY BUILDERS
    // ═══════════════════════════════════════════════════════════════

    public ModuleSummarySection buildWeaponSummary(ItemStack item) {
        double damage = 0;
        double speed = 0;

        if (item.getItem() instanceof SwordItem sword) {
            damage = sword.getTier().getAttackDamageBonus() + 1;
            speed = -2.4;
        } else if (item.getItem() instanceof TieredItem tiered) {
            damage = tiered.getTier().getAttackDamageBonus();
        }

        return ModuleSummarySection.builder("summary-weapon", "Weapon Stats")
            .accentColor(DesignTokens.Accent.RED())
            .addStat("Base Damage", damage, "%.1f", DesignTokens.Accent.RED(), "VAN")
            .addStat("Attack Speed", 4.0 + speed, "%.2f/s", DesignTokens.Text.PRIMARY(), "VAN")
            .addStat("DPS", damage * (4.0 + speed), "%.1f", DesignTokens.Accent.ORANGE())
            .build();
    }

    public ModuleSummarySection buildArmorSummary(ItemStack item) {
        double defense = 0;
        double toughness = 0;

        if (item.getItem() instanceof ArmorItem armor) {
            defense = armor.getDefense();
            toughness = armor.getToughness();
        }

        return ModuleSummarySection.builder("summary-armor", "Armor Stats")
            .accentColor(DesignTokens.Accent.BLUE())
            .addStat("Defense", defense, "%.0f", DesignTokens.Accent.BLUE(), "VAN")
            .addStat("Toughness", toughness, "%.1f", DesignTokens.Text.PRIMARY(), "VAN")
            .build();
    }

    public ModuleSummarySection buildGeneralSummary(ItemStack item) {
        ModuleSummarySection.Builder builder = ModuleSummarySection.builder("summary-general", "Item Properties")
            .accentColor(DesignTokens.Accent.INFO());

        builder.addStat("Max Stack", item.getMaxStackSize(), "%.0f", DesignTokens.Text.PRIMARY());

        if (item.isDamageableItem()) {
            int current = item.getMaxDamage() - item.getDamageValue();
            int max = item.getMaxDamage();
            double percent = (double) current / max * 100;
            int durabilityColor = percent > 50 ? DesignTokens.Accent.GREEN()
                                : percent > 20 ? DesignTokens.Accent.ORANGE()
                                : DesignTokens.Accent.RED();
            builder.addStat("Durability", percent, "%.0f%%", durabilityColor);
        }

        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════
    // INFO EXTRACTION
    // ═══════════════════════════════════════════════════════════════

    public List<String> extractItemInfo(ItemStack item) {
        List<String> info = new ArrayList<>();

        info.add("Name: " + item.getHoverName().getString());
        Item baseItem = Objects.requireNonNull(item.getItem(), "item");
        info.add("Type: " + baseItem.getClass().getSimpleName());
        info.add("Registry: " + BuiltInRegistries.ITEM.getKey(baseItem));
        info.add("Rarity: " + item.getRarity().name());
        info.add("Max Stack: " + item.getMaxStackSize());

        if (item.isDamageableItem()) {
            info.add("Max Durability: " + item.getMaxDamage());
            info.add("Current Damage: " + item.getDamageValue());
        } else {
            info.add("Not Damageable");
        }

        List<String> capabilities = new ArrayList<>();
        if (isWeaponItem(item)) capabilities.add("Weapon");
        if (isArmorItem(item)) capabilities.add("Armor");
        if (item.isEnchantable()) capabilities.add("Enchantable");
        if (item.isDamageableItem()) capabilities.add("Damageable");
        if (!capabilities.isEmpty()) {
            info.add("Capabilities: " + String.join(", ", capabilities));
        }

        return info;
    }
}

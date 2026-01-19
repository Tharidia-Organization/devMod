package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

import com.devmod.foundry.progression.FoundrySpecialization;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.quality.QualityCalculator;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;
import com.devmod.foundry.tool.material.FoundryMaterialStats;
import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierStats;

/**
 * Builder and stat calculator for foundry tools.
 */
public final class FoundryToolBuilder {
    private static final ResourceLocation ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("devmod", "foundry_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath("devmod", "foundry_attack_speed");
    private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath("devmod", "foundry_armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath("devmod", "foundry_armor_toughness");
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID = ResourceLocation.fromNamespaceAndPath("devmod", "foundry_knockback_resistance");

    private FoundryToolBuilder() {}

    public static ItemStack buildTool(FoundryToolDefinition definition, List<ItemStack> partStacks, Map<ResourceLocation, Integer> modifiers) {
        List<ResourceLocation> materialIds = new ArrayList<>();
        List<FoundryPartType> partTypes = definition.parts();
        MaterialQuality[] partQualities = new MaterialQuality[partStacks.size()];
        if (partStacks.size() != partTypes.size()) {
            return ItemStack.EMPTY;
        }

        for (ItemStack stack : partStacks) {
            if (!(stack.getItem() instanceof FoundryPartItem partItem)) {
                return ItemStack.EMPTY;
            }
            Optional<ResourceLocation> materialId = partItem.getMaterialId(stack);
            if (materialId.isEmpty()) {
                return ItemStack.EMPTY;
            }
            materialIds.add(materialId.get());
            partQualities[materialIds.size() - 1] = partItem.getQuality(stack);
        }

        MaterialQuality toolQuality = QualityCalculator.calculateToolQuality(partQualities, 0.0f);
        FoundryToolStats stats = computeStats(definition, partTypes, materialIds, modifiers, toolQuality);
        Item item = BuiltInRegistries.ITEM.get(definition.itemId());
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(item);
        FoundryToolData data = new FoundryToolData(definition.id(), List.copyOf(materialIds), Map.copyOf(modifiers),
            toolQuality, 1, 0, 0, 0, 0, null, null);
        data.writeToStack(result);
        applyStats(result, definition.kind(), stats);
        return result;
    }

    public static FoundryToolStats computeStats(
        FoundryToolDefinition definition,
        List<FoundryPartType> partTypes,
        List<ResourceLocation> materialIds,
        Map<ResourceLocation, Integer> modifiers,
        MaterialQuality quality
    ) {
        int durability = definition.baseStats().durability();
        float miningSpeed = definition.baseStats().miningSpeed();
        float attackDamage = definition.baseStats().attackDamage();
        float attackSpeed = definition.baseStats().attackSpeed();
        int miningLevel = definition.baseStats().miningLevel();
        float armor = definition.baseStats().armor();
        float toughness = definition.baseStats().toughness();
        float knockbackResistance = definition.baseStats().knockbackResistance();

        float durabilityMultiplier = 1.0f;
        float miningSpeedMultiplier = 1.0f;
        float attackDamageMultiplier = 1.0f;
        Map<ResourceLocation, Integer> combinedModifiers = new LinkedHashMap<>(modifiers);

        for (int i = 0; i < partTypes.size(); i++) {
            FoundryPartType partType = partTypes.get(i);
            ResourceLocation materialId = materialIds.get(i);
            FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
            if (material == null) {
                continue;
            }
            FoundryMaterialStats stats = material.getStats(partType.statKey());
            durability += stats.durability();
            miningSpeed += stats.miningSpeed();
            attackDamage += stats.attackDamage();
            attackSpeed += stats.attackSpeed();
            durabilityMultiplier *= stats.durabilityMultiplier();
            miningSpeedMultiplier *= stats.miningSpeedMultiplier();
            attackDamageMultiplier *= stats.attackDamageMultiplier();
            miningLevel = Math.max(miningLevel, stats.miningLevel());
            float armorBonus = stats.armor();
            float toughnessBonus = stats.toughness();
            float knockbackBonus = stats.knockbackResistance();
            if (definition.kind().isArmor()
                && armorBonus == 0.0f
                && toughnessBonus == 0.0f
                && knockbackBonus == 0.0f) {
                armorBonus = Math.max(0.0f, stats.durability() / 100.0f);
                toughnessBonus = Math.max(0.0f, stats.miningLevel() * 0.2f);
                knockbackBonus = Math.max(0.0f, stats.durability() / 2000.0f);
            }
            armor += armorBonus;
            toughness += toughnessBonus;
            knockbackResistance += knockbackBonus;
            for (ResourceLocation trait : material.traits()) {
                combinedModifiers.merge(trait, 1, Integer::sum);
            }
        }

        durability = Math.round(durability * durabilityMultiplier);
        miningSpeed = miningSpeed * miningSpeedMultiplier;
        attackDamage = attackDamage * attackDamageMultiplier;

        for (Map.Entry<ResourceLocation, Integer> entry : combinedModifiers.entrySet()) {
            FoundryModifierDefinition modifier = FoundryModifierRegistry.all().stream()
                .filter(def -> Objects.equals(def.id(), entry.getKey()))
                .findFirst()
                .orElse(null);
            if (modifier == null) {
                continue;
            }
            int level = entry.getValue();
            FoundryModifierStats bonus = modifier.bonuses();
            durability += bonus.durability() * level;
            miningSpeed += bonus.miningSpeed() * level;
            attackDamage += bonus.attackDamage() * level;
            attackSpeed += bonus.attackSpeed() * level;
            armor += bonus.armor() * level;
            toughness += bonus.toughness() * level;
            knockbackResistance += bonus.knockbackResistance() * level;
        }

        FoundryToolStats raw = new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel, armor, toughness, knockbackResistance);
        return applyQuality(raw, quality);
    }

    private static FoundryToolStats applyQuality(FoundryToolStats stats, MaterialQuality quality) {
        float mult = quality.getStatMultiplier();
        int durability = Math.max(1, Math.round(stats.durability() * mult));
        float miningSpeed = stats.miningSpeed() * mult;
        float attackDamage = stats.attackDamage() * mult;
        float attackSpeed = stats.attackSpeed() * mult;
        float armor = stats.armor() * mult;
        float toughness = stats.toughness() * mult;
        float knockbackResistance = stats.knockbackResistance() * mult;
        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, stats.miningLevel(), armor, toughness, knockbackResistance);
    }

    public static FoundryToolStats applySpecialization(
        FoundryToolStats stats,
        FoundryToolKind kind,
        @Nullable ResourceLocation specializationId
    ) {
        FoundrySpecialization specialization = FoundrySpecialization.fromId(specializationId);
        if (specialization == null) {
            return stats;
        }
        return specialization.applyToStats(stats, kind);
    }

    public static void applyStats(ItemStack stack, FoundryToolKind kind, FoundryToolStats stats) {
        int maxDamage = Math.max(1, stats.durability());
        int existingDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.MAX_DAMAGE, maxDamage);
        stack.set(DataComponents.DAMAGE, Math.min(existingDamage, Math.max(0, maxDamage - 1)));

        ItemAttributeModifiers.Builder attributeBuilder = ItemAttributeModifiers.builder();
        if (kind.isArmor()) {
            if (stats.armor() != 0.0f) {
                attributeBuilder.add(
                    Attributes.ARMOR,
                    new AttributeModifier(ARMOR_ID, stats.armor(), AttributeModifier.Operation.ADD_VALUE),
                    kind.getSlotGroup()
                );
            }
            if (stats.toughness() != 0.0f) {
                attributeBuilder.add(
                    Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(ARMOR_TOUGHNESS_ID, stats.toughness(), AttributeModifier.Operation.ADD_VALUE),
                    kind.getSlotGroup()
                );
            }
            if (stats.knockbackResistance() != 0.0f) {
                attributeBuilder.add(
                    Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(KNOCKBACK_RESISTANCE_ID, stats.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE),
                    kind.getSlotGroup()
                );
            }
        } else {
            attributeBuilder
                .add(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ATTACK_DAMAGE_ID, stats.attackDamage(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND
                )
                .add(
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(ATTACK_SPEED_ID, stats.attackSpeed(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND
                );
        }
        ItemAttributeModifiers attributes = attributeBuilder.build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);

        Tool tool = buildToolComponent(kind, stats.miningSpeed(), stats.miningLevel());
        if (tool != null) {
            stack.set(DataComponents.TOOL, tool);
        }
    }

    @Nullable
    private static Tool buildToolComponent(FoundryToolKind kind, float miningSpeed, int miningLevel) {
        if (kind.getMineableTag() == null) {
            return null;
        }
        List<Tool.Rule> rules = new ArrayList<>();
        if (kind.usesMiningLevel()) {
            if (miningLevel < 1) {
                rules.add(Tool.Rule.deniesDrops(BlockTags.NEEDS_STONE_TOOL));
            }
            if (miningLevel < 2) {
                rules.add(Tool.Rule.deniesDrops(BlockTags.NEEDS_IRON_TOOL));
            }
            if (miningLevel < 3) {
                rules.add(Tool.Rule.deniesDrops(BlockTags.NEEDS_DIAMOND_TOOL));
            }
        }
        rules.add(Tool.Rule.minesAndDrops(kind.getMineableTag(), miningSpeed));
        return new Tool(rules, 1.0f, 1);
    }
}

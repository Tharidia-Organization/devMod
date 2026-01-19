package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

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

    private FoundryToolBuilder() {}

    public static ItemStack buildTool(FoundryToolDefinition definition, List<ItemStack> partStacks, Map<ResourceLocation, Integer> modifiers) {
        List<ResourceLocation> materialIds = new ArrayList<>();
        List<FoundryPartType> partTypes = definition.parts();
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
        }

        FoundryToolStats stats = computeStats(definition, partTypes, materialIds, modifiers);
        Item item = BuiltInRegistries.ITEM.get(definition.itemId());
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(item);
        FoundryToolData data = new FoundryToolData(definition.id(), List.copyOf(materialIds), Map.copyOf(modifiers));
        data.writeToStack(result);
        applyStats(result, definition.kind(), stats);
        return result;
    }

    public static FoundryToolStats computeStats(
        FoundryToolDefinition definition,
        List<FoundryPartType> partTypes,
        List<ResourceLocation> materialIds,
        Map<ResourceLocation, Integer> modifiers
    ) {
        int durability = definition.baseStats().durability();
        float miningSpeed = definition.baseStats().miningSpeed();
        float attackDamage = definition.baseStats().attackDamage();
        float attackSpeed = definition.baseStats().attackSpeed();
        int miningLevel = definition.baseStats().miningLevel();

        float durabilityMultiplier = 1.0f;
        float miningSpeedMultiplier = 1.0f;
        float attackDamageMultiplier = 1.0f;

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
        }

        durability = Math.round(durability * durabilityMultiplier);
        miningSpeed = miningSpeed * miningSpeedMultiplier;
        attackDamage = attackDamage * attackDamageMultiplier;

        for (Map.Entry<ResourceLocation, Integer> entry : modifiers.entrySet()) {
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
        }

        return new FoundryToolStats(durability, miningSpeed, attackDamage, attackSpeed, miningLevel);
    }

    public static void applyStats(ItemStack stack, FoundryToolKind kind, FoundryToolStats stats) {
        int maxDamage = Math.max(1, stats.durability());
        int existingDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.MAX_DAMAGE, maxDamage);
        stack.set(DataComponents.DAMAGE, Math.min(existingDamage, Math.max(0, maxDamage - 1)));

        ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ATTACK_DAMAGE_ID, stats.attackDamage(), AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(ATTACK_SPEED_ID, stats.attackSpeed(), AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);

        Tool tool = buildToolComponent(kind, stats.miningSpeed(), stats.miningLevel());
        if (tool != null) {
            stack.set(DataComponents.TOOL, tool);
        }
    }

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

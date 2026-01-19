package com.devmod.foundry.tool.modifier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Modifier definition for foundry tools.
 */
public record FoundryModifierDefinition(
    ResourceLocation id,
    Ingredient ingredient,
    int maxLevel,
    FoundryModifierSlot slotType,
    int slots,
    FoundryModifierStats bonuses
) {}

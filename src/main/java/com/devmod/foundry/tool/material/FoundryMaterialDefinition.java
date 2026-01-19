package com.devmod.foundry.tool.material;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Material definition used by the foundry tool system.
 */
public record FoundryMaterialDefinition(
    ResourceLocation id,
    Ingredient ingredient,
    int color,
    Map<String, FoundryMaterialStats> stats,
    List<ResourceLocation> traits
) {
    public FoundryMaterialStats getStats(String key) {
        return Objects.requireNonNull(stats.getOrDefault(key, FoundryMaterialStats.EMPTY));
    }
}

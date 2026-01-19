package com.devmod.foundry.tool.material;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Material definition used by the foundry tool system.
 */
public record FoundryMaterialDefinition(
    ResourceLocation id,
    Ingredient ingredient,
    int color,
    int tier,
    @Nullable FoundryMaterialMelting melting,
    Map<String, FoundryMaterialStats> stats,
    List<ResourceLocation> traits
) {
    public FoundryMaterialStats getStats(String key) {
        return Objects.requireNonNull(stats.getOrDefault(key, FoundryMaterialStats.EMPTY));
    }

    public float getBasePurity() {
        return melting != null ? melting.getBasePurity() : 1.0f;
    }

    public int getOptimalLow(int fallback) {
        return melting != null ? melting.getOptimalLow(fallback) : fallback;
    }

    public int getOptimalHigh(int fallback) {
        return melting != null ? melting.getOptimalHigh(fallback) : fallback;
    }
}

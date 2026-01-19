package com.devmod.foundry.tool;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/**
 * Tool definition loaded from datapack JSON.
 */
public record FoundryToolDefinition(
    ResourceLocation id,
    ResourceLocation itemId,
    FoundryToolKind kind,
    List<FoundryPartType> parts,
    FoundryToolStats baseStats,
    int baseUpgrades,
    int baseAbilities,
    int priority
) {
    public boolean matches(List<FoundryPartType> provided) {
        if (provided.size() != parts.size()) {
            return false;
        }
        Map<ResourceLocation, Integer> expected = countParts(parts);
        Map<ResourceLocation, Integer> actual = countParts(provided);
        return expected.equals(actual);
    }

    private static Map<ResourceLocation, Integer> countParts(List<FoundryPartType> types) {
        Map<ResourceLocation, Integer> counts = new java.util.HashMap<>();
        for (FoundryPartType type : types) {
            counts.merge(Objects.requireNonNull(type.id()), 1, Integer::sum);
        }
        return counts;
    }
}

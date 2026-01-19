package com.devmod.foundry.tool.material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Registry for foundry tool materials.
 */
public final class FoundryMaterialRegistry {
    private FoundryMaterialRegistry() {}

    private static final Map<ResourceLocation, FoundryMaterialDefinition> MATERIALS = new LinkedHashMap<>();

    public static void clear() {
        MATERIALS.clear();
    }

    public static void register(FoundryMaterialDefinition material) {
        MATERIALS.put(material.id(), material);
    }

    public static Collection<FoundryMaterialDefinition> all() {
        return java.util.List.copyOf(MATERIALS.values());
    }

    public static FoundryMaterialDefinition get(ResourceLocation id) {
        return MATERIALS.get(id);
    }

    public static Optional<FoundryMaterialDefinition> findMaterial(ItemStack stack) {
        for (FoundryMaterialDefinition material : MATERIALS.values()) {
            if (material.ingredient().test(stack)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }
}

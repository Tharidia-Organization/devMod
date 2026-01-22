package com.devmod.foundry.tool.modifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Registry for foundry tool modifiers.
 */
public final class FoundryModifierRegistry {
    private FoundryModifierRegistry() {}

    private static final Map<ResourceLocation, FoundryModifierDefinition> MODIFIERS = new LinkedHashMap<>();

    public static void clear() {
        MODIFIERS.clear();
    }

    public static void register(FoundryModifierDefinition definition) {
        MODIFIERS.put(definition.id(), definition);
    }

    @Nullable
    public static FoundryModifierDefinition get(ResourceLocation id) {
        return MODIFIERS.get(id);
    }

    public static Collection<FoundryModifierDefinition> all() {
        return java.util.List.copyOf(MODIFIERS.values());
    }

    public static Optional<FoundryModifierDefinition> findModifier(ItemStack stack) {
        for (FoundryModifierDefinition modifier : MODIFIERS.values()) {
            if (modifier.ingredient().test(stack)) {
                return Optional.of(modifier);
            }
        }
        return Optional.empty();
    }
}

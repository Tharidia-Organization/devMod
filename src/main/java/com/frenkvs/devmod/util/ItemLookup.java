package com.frenkvs.devmod.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Utility to safely resolve items by ID.
 */
public final class ItemLookup {

    private ItemLookup() {}

    public static Item getItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }
}

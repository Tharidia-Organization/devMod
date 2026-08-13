package com.devmod.util;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ItemLookup {

    private ItemLookup() {}

    /**
     * Looks up an item by id. BuiltInRegistries.ITEM is a DefaultedRegistry whose get()
     * returns minecraft:air for unknown ids, so membership is checked explicitly.
     *
     * @return the item, or null if the id is not registered
     */
    @Nullable
    public static Item getItem(@Nullable ResourceLocation id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }
}

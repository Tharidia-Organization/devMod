package com.devmod.area;

import java.util.Objects;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * Item registrations for the Area module.
 */
public final class AreaItems {
    private AreaItems() {}

    /**
     * Area Editor block item.
     */
    public static final DeferredHolder<Item, BlockItem> AREA_EDITOR =
        DevMod.ITEMS.register("area_editor",
            () -> new BlockItem(Objects.requireNonNull(AreaBlocks.AREA_EDITOR.get()), new Item.Properties()));

    /**
     * Nexus Editor Central block item.
     * Not normally obtainable - spawns automatically.
     */
    public static final DeferredHolder<Item, BlockItem> NEXUS_EDITOR_CENTRAL =
        DevMod.ITEMS.register("nexus_editor_central",
            () -> new BlockItem(Objects.requireNonNull(AreaBlocks.NEXUS_EDITOR_CENTRAL.get()), new Item.Properties()));

    /**
     * Initialize item registrations.
     * Called during module initialization to trigger static field loading.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Area] Area items initialized");
    }
}

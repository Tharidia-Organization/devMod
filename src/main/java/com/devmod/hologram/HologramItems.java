package com.devmod.hologram;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * Hologram module item registrations.
 * Registers block items for hologram blocks.
 */
public final class HologramItems {
    private HologramItems() {}

    /**
     * Block item for the hologram projector.
     */
    public static final DeferredHolder<Item, BlockItem> HOLOGRAM_PROJECTOR = DevMod.ITEMS.register(
        "hologram_projector",
        () -> new BlockItem(HologramBlocks.HOLOGRAM_PROJECTOR.get(), new Item.Properties())
    );

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Hologram] Hologram items initialized");
    }
}

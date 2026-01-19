package com.devmod.hologram;

import java.util.Objects;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.hologram.item.HologramPlacerItem;

/**
 * Hologram module item registrations.
 * Registers block items for hologram blocks and tools.
 */
public final class HologramItems {
    private HologramItems() {}

    /**
     * Block item for the hologram projector (3D terrain display).
     */
    public static final DeferredHolder<Item, BlockItem> HOLOGRAM_PROJECTOR = DevMod.ITEMS.register(
        "hologram_projector",
        () -> new BlockItem(Objects.requireNonNull(HologramBlocks.HOLOGRAM_PROJECTOR.get()), new Item.Properties())
    );

    /**
     * Tool for creating and editing TextDisplay holograms.
     * Requires OP level 2+ to use.
     */
    public static final DeferredHolder<Item, HologramPlacerItem> HOLOGRAM_PLACER = DevMod.ITEMS.register(
        "hologram_placer",
        HologramPlacerItem::new
    );

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Hologram] Hologram items initialized (projector, placer)");
    }
}

package com.devmod.clone;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.clone.item.BioscannerItem;

/**
 * Clone module item registrations.
 * Registers all clone-related items including block items.
 */
public final class CloneItems {
    private CloneItems() {}

    /**
     * The telepad block item.
     */
    public static final DeferredHolder<Item, BlockItem> TELEPAD = DevMod.ITEMS.register(
        "telepad",
        () -> new BlockItem(CloneBlocks.TELEPAD.get(), new Item.Properties())
    );

    /**
     * The bioscanner item for scanning entities.
     */
    public static final DeferredHolder<Item, BioscannerItem> BIOSCANNER = DevMod.ITEMS.register(
        "bioscanner",
        BioscannerItem::new
    );

    /**
     * The imprinter block item.
     */
    public static final DeferredHolder<Item, BlockItem> IMPRINTER = DevMod.ITEMS.register(
        "imprinter",
        () -> new BlockItem(CloneBlocks.IMPRINTER.get(), new Item.Properties())
    );

    /**
     * The neurocell block item.
     */
    public static final DeferredHolder<Item, BlockItem> NEUROCELL = DevMod.ITEMS.register(
        "neurocell",
        () -> new BlockItem(CloneBlocks.NEUROCELL.get(), new Item.Properties())
    );

    /**
     * The neurolink block item.
     */
    public static final DeferredHolder<Item, BlockItem> NEUROLINK = DevMod.ITEMS.register(
        "neurolink",
        () -> new BlockItem(CloneBlocks.NEUROLINK.get(), new Item.Properties())
    );

    /**
     * The reformer block item.
     */
    public static final DeferredHolder<Item, BlockItem> REFORMER = DevMod.ITEMS.register(
        "reformer",
        () -> new BlockItem(CloneBlocks.REFORMER.get(), new Item.Properties())
    );

    /**
     * The large neurocell block item (3x3x3).
     */
    public static final DeferredHolder<Item, BlockItem> NEUROCELL_L = DevMod.ITEMS.register(
        "neurocell_l",
        () -> new BlockItem(CloneBlocks.NEUROCELL_L.get(), new Item.Properties())
    );

    /**
     * The centrifuge block item.
     */
    public static final DeferredHolder<Item, BlockItem> CENTRIFUGE = DevMod.ITEMS.register(
        "centrifuge",
        () -> new BlockItem(java.util.Objects.requireNonNull(CloneBlocks.CENTRIFUGE.get()), new Item.Properties())
    );

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Clone] Clone items initialized");
    }
}

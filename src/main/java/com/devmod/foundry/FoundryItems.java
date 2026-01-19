package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;

/**
 * Foundry module item registrations.
 */
public final class FoundryItems {
    private FoundryItems() {}

    public static final DeferredHolder<Item, Item> FOUNDRY_BRICKS_ITEM = DevMod.ITEMS.register(
        "foundry_bricks",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_BRICKS.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CONTROLLER_ITEM = DevMod.ITEMS.register(
        "foundry_controller",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CONTROLLER.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_DRAIN_ITEM = DevMod.ITEMS.register(
        "foundry_drain",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_DRAIN.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_TANK_ITEM = DevMod.ITEMS.register(
        "foundry_tank",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_TANK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FAUCET_ITEM = DevMod.ITEMS.register(
        "foundry_faucet",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_FAUCET.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CASTING_TABLE_ITEM = DevMod.ITEMS.register(
        "foundry_casting_table",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_TABLE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CASTING_BASIN_ITEM = DevMod.ITEMS.register(
        "foundry_casting_basin",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_BASIN.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_INGOT_CAST = DevMod.ITEMS.register(
        "foundry_ingot_cast",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_NUGGET_CAST = DevMod.ITEMS.register(
        "foundry_nugget_cast",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static void init() {
        // Static init only.
    }
}

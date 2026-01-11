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

    // ==================== CLONE MACHINE ITEMS (Oritech-style) ====================

    /**
     * Clone Pulverizer block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_PULVERIZER = DevMod.ITEMS.register(
        "clone_pulverizer",
        () -> new BlockItem(CloneBlocks.CLONE_PULVERIZER.get(), new Item.Properties())
    );

    /**
     * Clone Foundry block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_FOUNDRY = DevMod.ITEMS.register(
        "clone_foundry",
        () -> new BlockItem(CloneBlocks.CLONE_FOUNDRY.get(), new Item.Properties())
    );

    /**
     * Clone Pump block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_PUMP = DevMod.ITEMS.register(
        "clone_pump",
        () -> new BlockItem(CloneBlocks.CLONE_PUMP.get(), new Item.Properties())
    );

    /**
     * Clone Steam Engine block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_STEAM_ENGINE = DevMod.ITEMS.register(
        "clone_steam_engine",
        () -> new BlockItem(CloneBlocks.CLONE_STEAM_ENGINE.get(), new Item.Properties())
    );

    /**
     * Clone Lava Generator block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_LAVA_GENERATOR = DevMod.ITEMS.register(
        "clone_lava_generator",
        () -> new BlockItem(CloneBlocks.CLONE_LAVA_GENERATOR.get(), new Item.Properties())
    );

    /**
     * Clone Refinery block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_REFINERY = DevMod.ITEMS.register(
        "clone_refinery",
        () -> new BlockItem(CloneBlocks.CLONE_REFINERY.get(), new Item.Properties())
    );

    /**
     * Clone Shrinker block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_SHRINKER = DevMod.ITEMS.register(
        "clone_shrinker",
        () -> new BlockItem(CloneBlocks.CLONE_SHRINKER.get(), new Item.Properties())
    );

    /**
     * Clone Treefeller block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_TREEFELLER = DevMod.ITEMS.register(
        "clone_treefeller",
        () -> new BlockItem(CloneBlocks.CLONE_TREEFELLER.get(), new Item.Properties())
    );

    /**
     * Clone Tech Door block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_TECH_DOOR = DevMod.ITEMS.register(
        "clone_tech_door",
        () -> new BlockItem(CloneBlocks.CLONE_TECH_DOOR.get(), new Item.Properties())
    );

    /**
     * Clone Bio Generator block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_BIO_GENERATOR = DevMod.ITEMS.register(
        "clone_bio_generator",
        () -> new BlockItem(CloneBlocks.CLONE_BIO_GENERATOR.get(), new Item.Properties())
    );

    /**
     * Clone Assembler block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_ASSEMBLER = DevMod.ITEMS.register(
        "clone_assembler",
        () -> new BlockItem(CloneBlocks.CLONE_ASSEMBLER.get(), new Item.Properties())
    );

    /**
     * Clone Centrifuge L block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_CENTRIFUGE_L = DevMod.ITEMS.register(
        "clone_centrifuge_l",
        () -> new BlockItem(CloneBlocks.CLONE_CENTRIFUGE_L.get(), new Item.Properties())
    );

    /**
     * Clone Fuel Generator block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_FUEL_GENERATOR = DevMod.ITEMS.register(
        "clone_fuel_generator",
        () -> new BlockItem(CloneBlocks.CLONE_FUEL_GENERATOR.get(), new Item.Properties())
    );

    /**
     * Clone Atomic Forge block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_ATOMIC_FORGE = DevMod.ITEMS.register(
        "clone_atomic_forge",
        () -> new BlockItem(CloneBlocks.CLONE_ATOMIC_FORGE.get(), new Item.Properties())
    );

    /**
     * Clone Laser Arm block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_LASER_ARM = DevMod.ITEMS.register(
        "clone_laser_arm",
        () -> new BlockItem(CloneBlocks.CLONE_LASER_ARM.get(), new Item.Properties())
    );

    /**
     * Clone Drill block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_DRILL = DevMod.ITEMS.register(
        "clone_drill",
        () -> new BlockItem(CloneBlocks.CLONE_DRILL.get(), new Item.Properties())
    );

    /**
     * Clone Fertilizer block item.
     */
    public static final DeferredHolder<Item, BlockItem> CLONE_FERTILIZER = DevMod.ITEMS.register(
        "clone_fertilizer",
        () -> new BlockItem(CloneBlocks.CLONE_FERTILIZER.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_CRUSHER = DevMod.ITEMS.register(
        "clone_crusher",
        () -> new BlockItem(CloneBlocks.CLONE_CRUSHER.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_SMELTER = DevMod.ITEMS.register(
        "clone_smelter",
        () -> new BlockItem(CloneBlocks.CLONE_SMELTER.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_STORAGE_UNIT = DevMod.ITEMS.register(
        "clone_storage_unit",
        () -> new BlockItem(CloneBlocks.CLONE_STORAGE_UNIT.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_ENERGY_PIPE = DevMod.ITEMS.register(
        "clone_energy_pipe",
        () -> new BlockItem(CloneBlocks.CLONE_ENERGY_PIPE.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_TANK = DevMod.ITEMS.register(
        "clone_tank",
        () -> new BlockItem(CloneBlocks.CLONE_TANK.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_BATTERY = DevMod.ITEMS.register(
        "clone_battery",
        () -> new BlockItem(CloneBlocks.CLONE_BATTERY.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> CLONE_SOLAR_PANEL = DevMod.ITEMS.register(
        "clone_solar_panel", () -> new BlockItem(CloneBlocks.CLONE_SOLAR_PANEL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLONE_MOTOR = DevMod.ITEMS.register(
        "clone_motor", () -> new BlockItem(CloneBlocks.CLONE_MOTOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLONE_CONVEYOR = DevMod.ITEMS.register(
        "clone_conveyor", () -> new BlockItem(CloneBlocks.CLONE_CONVEYOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLONE_REACTOR = DevMod.ITEMS.register(
        "clone_reactor", () -> new BlockItem(CloneBlocks.CLONE_REACTOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLONE_PROCESSOR = DevMod.ITEMS.register(
        "clone_processor", () -> new BlockItem(CloneBlocks.CLONE_PROCESSOR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLONE_CHARGER = DevMod.ITEMS.register(
        "clone_charger", () -> new BlockItem(CloneBlocks.CLONE_CHARGER.get(), new Item.Properties()));

    /**
     * Called during mod initialization to ensure items are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Clone] Clone items initialized");
    }
}

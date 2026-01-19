package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.foundry.item.FoundryFluxItem;
import com.devmod.foundry.item.FoundryGuideItem;
import com.devmod.foundry.item.FoundrySpecializationItem;
import com.devmod.foundry.item.FoundrySpecializationResetItem;
import com.devmod.foundry.progression.FoundrySpecialization;

/**
 * Foundry module item registrations.
 */
public final class FoundryItems {
    private FoundryItems() {}

    private static final float FLUX_STANDARD_BONUS = 0.08f;
    private static final float FLUX_REFINED_BONUS = 0.14f;
    private static final float FLUX_PURE_BONUS = 0.22f;

    public static final DeferredHolder<Item, Item> FOUNDRY_BRICKS_ITEM = DevMod.ITEMS.register(
        "foundry_bricks",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_BRICKS.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CRACKED_BRICKS_ITEM = DevMod.ITEMS.register(
        "foundry_cracked_bricks",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CRACKED_BRICKS.get()), new Item.Properties())
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

    public static final DeferredHolder<Item, Item> FOUNDRY_GLASS_ITEM = DevMod.ITEMS.register(
        "foundry_glass",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_GLASS.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_WINDOW_ITEM = DevMod.ITEMS.register(
        "foundry_window",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_WINDOW.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_GAUGE_ITEM = DevMod.ITEMS.register(
        "foundry_gauge",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_GAUGE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FUEL_TANK_ITEM = DevMod.ITEMS.register(
        "foundry_fuel_tank",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_FUEL_TANK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FAUCET_ITEM = DevMod.ITEMS.register(
        "foundry_faucet",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_FAUCET.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CHANNEL_ITEM = DevMod.ITEMS.register(
        "foundry_channel",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CHANNEL.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_DUCT_ITEM = DevMod.ITEMS.register(
        "foundry_duct",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_DUCT.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CHUTE_ITEM = DevMod.ITEMS.register(
        "foundry_chute",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CHUTE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CASTING_TABLE_ITEM = DevMod.ITEMS.register(
        "foundry_casting_table",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_TABLE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_CASTING_BASIN_ITEM = DevMod.ITEMS.register(
        "foundry_casting_basin",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_BASIN.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_PART_BUILDER_ITEM = DevMod.ITEMS.register(
        "foundry_part_builder",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_PART_BUILDER.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_TOOL_STATION_ITEM = DevMod.ITEMS.register(
        "foundry_tool_station",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_TOOL_STATION.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_TOOL_ANVIL_ITEM = DevMod.ITEMS.register(
        "foundry_tool_anvil",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.FOUNDRY_TOOL_ANVIL.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_INGOT_CAST = DevMod.ITEMS.register(
        "foundry_ingot_cast",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_NUGGET_CAST = DevMod.ITEMS.register(
        "foundry_nugget_cast",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_GUIDE = DevMod.ITEMS.register(
        "foundry_guide",
        () -> new FoundryGuideItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_SPECIALIZATION_WEAPONSMITH = DevMod.ITEMS.register(
        "foundry_specialization_weaponsmith",
        () -> new FoundrySpecializationItem(FoundrySpecialization.WEAPONSMITH, new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_SPECIALIZATION_TOOLSMITH = DevMod.ITEMS.register(
        "foundry_specialization_toolsmith",
        () -> new FoundrySpecializationItem(FoundrySpecialization.TOOLSMITH, new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_SPECIALIZATION_ALLOYIST = DevMod.ITEMS.register(
        "foundry_specialization_alloyist",
        () -> new FoundrySpecializationItem(FoundrySpecialization.ALLOYIST, new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_SPECIALIZATION_RESET = DevMod.ITEMS.register(
        "foundry_specialization_reset",
        () -> new FoundrySpecializationResetItem(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FLUX = DevMod.ITEMS.register(
        "foundry_flux",
        () -> new FoundryFluxItem(new Item.Properties(), FLUX_STANDARD_BONUS)
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FLUX_REFINED = DevMod.ITEMS.register(
        "foundry_flux_refined",
        () -> new FoundryFluxItem(new Item.Properties(), FLUX_REFINED_BONUS)
    );

    public static final DeferredHolder<Item, Item> FOUNDRY_FLUX_PURE = DevMod.ITEMS.register(
        "foundry_flux_pure",
        () -> new FoundryFluxItem(new Item.Properties(), FLUX_PURE_BONUS)
    );

    // Material ingots
    public static final DeferredHolder<Item, Item> STEEL_INGOT = DevMod.ITEMS.register(
        "steel_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> BRONZE_INGOT = DevMod.ITEMS.register(
        "bronze_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> COBALT_INGOT = DevMod.ITEMS.register(
        "cobalt_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> MANYULLYN_INGOT = DevMod.ITEMS.register(
        "manyullyn_ingot",
        () -> new Item(new Item.Properties())
    );

    // Material block items
    public static final DeferredHolder<Item, Item> STEEL_BLOCK_ITEM = DevMod.ITEMS.register(
        "steel_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.STEEL_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> BRONZE_BLOCK_ITEM = DevMod.ITEMS.register(
        "bronze_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.BRONZE_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> COBALT_BLOCK_ITEM = DevMod.ITEMS.register(
        "cobalt_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.COBALT_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> MANYULLYN_BLOCK_ITEM = DevMod.ITEMS.register(
        "manyullyn_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.MANYULLYN_BLOCK.get()), new Item.Properties())
    );

    // New material ingots
    public static final DeferredHolder<Item, Item> TIN_INGOT = DevMod.ITEMS.register(
        "tin_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> LEAD_INGOT = DevMod.ITEMS.register(
        "lead_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> SILVER_INGOT = DevMod.ITEMS.register(
        "silver_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> NICKEL_INGOT = DevMod.ITEMS.register(
        "nickel_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ELECTRUM_INGOT = DevMod.ITEMS.register(
        "electrum_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> INVAR_INGOT = DevMod.ITEMS.register(
        "invar_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ARDITE_INGOT = DevMod.ITEMS.register(
        "ardite_ingot",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> VOID_METAL_INGOT = DevMod.ITEMS.register(
        "void_metal_ingot",
        () -> new Item(new Item.Properties())
    );

    // New material block items
    public static final DeferredHolder<Item, Item> TIN_BLOCK_ITEM = DevMod.ITEMS.register(
        "tin_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.TIN_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> LEAD_BLOCK_ITEM = DevMod.ITEMS.register(
        "lead_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.LEAD_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> SILVER_BLOCK_ITEM = DevMod.ITEMS.register(
        "silver_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.SILVER_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> NICKEL_BLOCK_ITEM = DevMod.ITEMS.register(
        "nickel_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.NICKEL_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ELECTRUM_BLOCK_ITEM = DevMod.ITEMS.register(
        "electrum_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.ELECTRUM_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> INVAR_BLOCK_ITEM = DevMod.ITEMS.register(
        "invar_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.INVAR_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ARDITE_BLOCK_ITEM = DevMod.ITEMS.register(
        "ardite_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.ARDITE_BLOCK.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> VOID_METAL_BLOCK_ITEM = DevMod.ITEMS.register(
        "void_metal_block",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.VOID_METAL_BLOCK.get()), new Item.Properties())
    );

    // Nuggets
    public static final DeferredHolder<Item, Item> STEEL_NUGGET = DevMod.ITEMS.register(
        "steel_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> BRONZE_NUGGET = DevMod.ITEMS.register(
        "bronze_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> COBALT_NUGGET = DevMod.ITEMS.register(
        "cobalt_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> MANYULLYN_NUGGET = DevMod.ITEMS.register(
        "manyullyn_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> TIN_NUGGET = DevMod.ITEMS.register(
        "tin_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> LEAD_NUGGET = DevMod.ITEMS.register(
        "lead_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> SILVER_NUGGET = DevMod.ITEMS.register(
        "silver_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> NICKEL_NUGGET = DevMod.ITEMS.register(
        "nickel_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ELECTRUM_NUGGET = DevMod.ITEMS.register(
        "electrum_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> INVAR_NUGGET = DevMod.ITEMS.register(
        "invar_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ARDITE_NUGGET = DevMod.ITEMS.register(
        "ardite_nugget",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> VOID_METAL_NUGGET = DevMod.ITEMS.register(
        "void_metal_nugget",
        () -> new Item(new Item.Properties())
    );

    // Raw ores (natural metals only, not alloys)
    public static final DeferredHolder<Item, Item> RAW_TIN = DevMod.ITEMS.register(
        "raw_tin",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> RAW_LEAD = DevMod.ITEMS.register(
        "raw_lead",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> RAW_SILVER = DevMod.ITEMS.register(
        "raw_silver",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> RAW_NICKEL = DevMod.ITEMS.register(
        "raw_nickel",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> RAW_COBALT = DevMod.ITEMS.register(
        "raw_cobalt",
        () -> new Item(new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> RAW_ARDITE = DevMod.ITEMS.register(
        "raw_ardite",
        () -> new Item(new Item.Properties())
    );

    // Ore block items - Overworld
    public static final DeferredHolder<Item, Item> TIN_ORE_ITEM = DevMod.ITEMS.register(
        "tin_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.TIN_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> DEEPSLATE_TIN_ORE_ITEM = DevMod.ITEMS.register(
        "deepslate_tin_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.DEEPSLATE_TIN_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> LEAD_ORE_ITEM = DevMod.ITEMS.register(
        "lead_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.LEAD_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> DEEPSLATE_LEAD_ORE_ITEM = DevMod.ITEMS.register(
        "deepslate_lead_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.DEEPSLATE_LEAD_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> SILVER_ORE_ITEM = DevMod.ITEMS.register(
        "silver_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.SILVER_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> DEEPSLATE_SILVER_ORE_ITEM = DevMod.ITEMS.register(
        "deepslate_silver_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.DEEPSLATE_SILVER_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> NICKEL_ORE_ITEM = DevMod.ITEMS.register(
        "nickel_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.NICKEL_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> DEEPSLATE_NICKEL_ORE_ITEM = DevMod.ITEMS.register(
        "deepslate_nickel_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.DEEPSLATE_NICKEL_ORE.get()), new Item.Properties())
    );

    // Ore block items - Nether
    public static final DeferredHolder<Item, Item> COBALT_ORE_ITEM = DevMod.ITEMS.register(
        "cobalt_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.COBALT_ORE.get()), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> ARDITE_ORE_ITEM = DevMod.ITEMS.register(
        "ardite_ore",
        () -> new BlockItem(Objects.requireNonNull(FoundryBlocks.ARDITE_ORE.get()), new Item.Properties())
    );

    public static void init() {
        // Static init only.
    }
}

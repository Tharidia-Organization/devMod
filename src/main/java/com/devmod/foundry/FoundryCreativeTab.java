package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Creative mode tab for Foundry items.
 */
public final class FoundryCreativeTab {
    private FoundryCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Objects.requireNonNull(Registries.CREATIVE_MODE_TAB),
        DevMod.MODID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FOUNDRY_TAB = TABS.register(
        "foundry",
        () -> CreativeModeTab.builder()
            .title(Objects.requireNonNull(Component.translatable("itemGroup.devmod.foundry")))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> Objects.requireNonNull(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_BRICKS_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CRACKED_BRICKS_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_TANK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_GLASS_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_WINDOW_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_GAUGE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FUEL_TANK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_DRAIN_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FAUCET_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CHANNEL_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_DUCT_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CHUTE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CASTING_TABLE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CASTING_BASIN_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_PART_BUILDER_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_TOOL_STATION_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_TOOL_ANVIL_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_STENCIL_TABLE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_BLANK_STENCIL.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_INGOT_CAST.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_NUGGET_CAST.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_GUIDE.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_GUIDE_NPC.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_SPECIALIZATION_WEAPONSMITH.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_SPECIALIZATION_TOOLSMITH.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_SPECIALIZATION_ALLOYIST.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_SPECIALIZATION_RESET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FLUX.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FLUX_REFINED.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FLUX_PURE.get()));
                for (var pattern : com.devmod.foundry.tool.FoundryToolItems.getPatternItems()) {
                    output.accept(pattern);
                }
                for (var part : com.devmod.foundry.tool.FoundryToolItems.getPartItems()) {
                    output.accept(part);
                }
                for (var tool : com.devmod.foundry.tool.FoundryToolItems.getToolItems()) {
                    output.accept(tool);
                }
                for (var armor : com.devmod.foundry.tool.FoundryToolItems.getArmorItems()) {
                    output.accept(armor);
                }
                // Material ingots
                output.accept(Objects.requireNonNull(FoundryItems.STEEL_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.BRONZE_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.COBALT_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.MANYULLYN_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.TIN_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.LEAD_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.SILVER_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.NICKEL_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ELECTRUM_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.INVAR_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ARDITE_INGOT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.VOID_METAL_INGOT.get()));
                // Material blocks
                output.accept(Objects.requireNonNull(FoundryItems.STEEL_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.BRONZE_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.COBALT_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.MANYULLYN_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.TIN_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.LEAD_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.SILVER_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.NICKEL_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ELECTRUM_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.INVAR_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ARDITE_BLOCK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.VOID_METAL_BLOCK_ITEM.get()));
                // Nuggets
                output.accept(Objects.requireNonNull(FoundryItems.STEEL_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.BRONZE_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.COBALT_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.MANYULLYN_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.TIN_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.LEAD_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.SILVER_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.NICKEL_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ELECTRUM_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.INVAR_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ARDITE_NUGGET.get()));
                output.accept(Objects.requireNonNull(FoundryItems.VOID_METAL_NUGGET.get()));
                // Raw ores
                output.accept(Objects.requireNonNull(FoundryItems.RAW_TIN.get()));
                output.accept(Objects.requireNonNull(FoundryItems.RAW_LEAD.get()));
                output.accept(Objects.requireNonNull(FoundryItems.RAW_SILVER.get()));
                output.accept(Objects.requireNonNull(FoundryItems.RAW_NICKEL.get()));
                output.accept(Objects.requireNonNull(FoundryItems.RAW_COBALT.get()));
                output.accept(Objects.requireNonNull(FoundryItems.RAW_ARDITE.get()));
                // Ore blocks
                output.accept(Objects.requireNonNull(FoundryItems.TIN_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.DEEPSLATE_TIN_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.LEAD_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.DEEPSLATE_LEAD_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.SILVER_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.DEEPSLATE_SILVER_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.NICKEL_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.DEEPSLATE_NICKEL_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.COBALT_ORE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.ARDITE_ORE_ITEM.get()));
                // Fluid buckets
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_IRON.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_GOLD.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_COPPER.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_TIN.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_BRONZE.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_STEEL.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_COBALT.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_MANYULLYN.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_LEAD.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_SILVER.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_NICKEL.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_ELECTRUM.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_INVAR.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_ARDITE.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_NETHERITE.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_VOID_METAL.bucket().get()));
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        TABS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.debug("[Foundry] Foundry creative tab registered");
    }
}

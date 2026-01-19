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
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_TANK_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_DRAIN_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_FAUCET_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CASTING_TABLE_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_CASTING_BASIN_ITEM.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_INGOT_CAST.get()));
                output.accept(Objects.requireNonNull(FoundryItems.FOUNDRY_NUGGET_CAST.get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_IRON.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_GOLD.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_COPPER.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_TIN.bucket().get()));
                output.accept(Objects.requireNonNull(FoundryFluids.MOLTEN_BRONZE.bucket().get()));
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        TABS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.debug("[Foundry] Foundry creative tab registered");
    }
}

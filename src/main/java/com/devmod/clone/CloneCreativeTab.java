package com.devmod.clone;

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
 * Creative mode tab for Clone system items.
 * Displays all clone-related blocks and items.
 */
public final class CloneCreativeTab {
    private CloneCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Objects.requireNonNull(Registries.CREATIVE_MODE_TAB),
        DevMod.MODID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CLONE_TAB = TABS.register(
        "clone",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.devmod.clone"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> Objects.requireNonNull(CloneItems.BIOSCANNER.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                // Tools
                output.accept(Objects.requireNonNull(CloneItems.BIOSCANNER.get()));

                // Blocks in workflow order
                output.accept(Objects.requireNonNull(CloneItems.IMPRINTER.get()));
                output.accept(Objects.requireNonNull(CloneItems.NEUROCELL.get()));
                output.accept(Objects.requireNonNull(CloneItems.NEUROLINK.get()));
                output.accept(Objects.requireNonNull(CloneItems.REFORMER.get()));

                // Teleportation
                output.accept(Objects.requireNonNull(CloneItems.TELEPAD.get()));
            })
            .build()
    );

    /**
     * Register the creative tab on the mod event bus.
     */
    public static void register(IEventBus modEventBus) {
        TABS.register(Objects.requireNonNull(modEventBus, "modEventBus"));
        DevMod.LOGGER.debug("[Clone] Clone creative tab registered");
    }
}

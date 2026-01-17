package com.devmod.area;

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
 * Creative mode tab for Area Builder items.
 */
public final class AreaCreativeTab {
    private AreaCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Objects.requireNonNull(Registries.CREATIVE_MODE_TAB),
        DevMod.MODID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AREA_TAB = TABS.register(
        "area_builder",
        () -> CreativeModeTab.builder()
            .title(Objects.requireNonNull(Component.translatable("itemGroup.devmod.area_builder")))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> Objects.requireNonNull(AreaItems.AREA_EDITOR.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                // Area Editor - main item for creating areas
                output.accept(Objects.requireNonNull(AreaItems.AREA_EDITOR.get()));

                // Nexus Editor Central - not normally obtainable but available for admins
                output.accept(Objects.requireNonNull(AreaItems.NEXUS_EDITOR_CENTRAL.get()));
            })
            .build()
    );

    /**
     * Initialize and register the creative tab.
     *
     * @param modEventBus The mod event bus
     */
    public static void init(IEventBus modEventBus) {
        TABS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.debug("[Area] Area creative tab initialized");
    }
}

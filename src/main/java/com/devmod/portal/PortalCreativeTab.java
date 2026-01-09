package com.devmod.portal;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.portal.item.PortalIgniterItem;

/**
 * Creative mode tab for portal items.
 * Displays all portal igniters (16 colors), linker, and rune blocks (5 types).
 */
public final class PortalCreativeTab {
    private PortalCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Objects.requireNonNull(Registries.CREATIVE_MODE_TAB),
        DevMod.MODID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PORTAL_TAB = TABS.register(
        "portals",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.devmod.portals"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> Objects.requireNonNull(
                PortalItems.IGNITER_BLUE.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                // Add all igniters (sorted by color order)
                for (PortalColor color : PortalColor.values()) {
                    DeferredHolder<Item, PortalIgniterItem> igniter = PortalItems.getIgniter(color);
                    if (igniter != null) {
                        output.accept(Objects.requireNonNull(igniter.get()));
                    }
                }

                // Add linker
                output.accept(Objects.requireNonNull(PortalItems.PORTAL_LINKER.get()));

                // Add all rune blocks
                for (RuneType rune : RuneType.values()) {
                    DeferredHolder<Item, BlockItem> runeItem = PortalItems.getRuneItem(rune);
                    if (runeItem != null) {
                        output.accept(Objects.requireNonNull(runeItem.get()));
                    }
                }
            })
            .build()
    );

    /**
     * Called during mod initialization to ensure creative tab is registered.
     */
    public static void init(net.neoforged.bus.api.IEventBus modEventBus) {
        TABS.register(modEventBus);
        DevMod.LOGGER.debug("[Portal] Portal creative tab initialized");
    }
}

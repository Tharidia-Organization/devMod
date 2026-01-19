package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.menu.FoundryPartBuilderMenu;
import com.devmod.foundry.menu.FoundryToolAnvilMenu;
import com.devmod.foundry.menu.FoundryToolStationMenu;

/**
 * Foundry module menu registrations.
 */
public final class FoundryMenus {
    private FoundryMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Objects.requireNonNull(Registries.MENU), DevMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FoundryControllerMenu>> FOUNDRY_CONTROLLER =
        MENUS.register("foundry_controller", () -> new MenuType<>(
            (IContainerFactory<FoundryControllerMenu>) FoundryControllerMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<FoundryPartBuilderMenu>> FOUNDRY_PART_BUILDER =
        MENUS.register("foundry_part_builder", () -> new MenuType<>(
            (IContainerFactory<FoundryPartBuilderMenu>) FoundryPartBuilderMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<FoundryToolStationMenu>> FOUNDRY_TOOL_STATION =
        MENUS.register("foundry_tool_station", () -> new MenuType<>(
            (IContainerFactory<FoundryToolStationMenu>) FoundryToolStationMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<FoundryToolAnvilMenu>> FOUNDRY_TOOL_ANVIL =
        MENUS.register("foundry_tool_anvil", () -> new MenuType<>(
            (IContainerFactory<FoundryToolAnvilMenu>) FoundryToolAnvilMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus modEventBus) {
        MENUS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.info("[Foundry] Menu types registered");
    }
}

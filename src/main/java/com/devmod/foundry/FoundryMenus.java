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

    public static void register(IEventBus modEventBus) {
        MENUS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.info("[Foundry] Menu types registered");
    }
}

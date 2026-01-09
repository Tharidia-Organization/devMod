package com.devmod.clone;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.menu.NeurocellMenu;

/**
 * Clone module menu registrations.
 * Registers all clone-related container menus.
 */
public final class CloneMenus {
    private CloneMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, DevMod.MODID);

    /**
     * Neurocell menu for bioscanner storage.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<NeurocellMenu>> NEUROCELL =
        MENUS.register("neurocell", () ->
            IMenuTypeExtension.create(NeurocellMenu::new));

    /**
     * Register all menus with the mod event bus.
     */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
        DevMod.LOGGER.debug("[Clone] Clone menus registered");
    }
}

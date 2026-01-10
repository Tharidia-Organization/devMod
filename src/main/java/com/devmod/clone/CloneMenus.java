package com.devmod.clone;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.menu.CentrifugeMenu;
import com.devmod.clone.menu.NeurocellMenu;
import com.devmod.clone.menu.NeurocellLMenu;

/**
 * Clone module menu registrations.
 * Registers all clone-related container menus.
 */
public final class CloneMenus {
    private CloneMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Objects.requireNonNull(Registries.MENU), DevMod.MODID);

    /**
     * Neurocell menu for bioscanner storage.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<NeurocellMenu>> NEUROCELL =
        MENUS.register("neurocell", () ->
            IMenuTypeExtension.create(NeurocellMenu::new));

    /**
     * NeurocellL menu for bioscanner storage (2x2x2 large chamber).
     */
    public static final DeferredHolder<MenuType<?>, MenuType<NeurocellLMenu>> NEUROCELL_L =
        MENUS.register("neurocell_l", () ->
            IMenuTypeExtension.create(NeurocellLMenu::new));

    /**
     * Centrifuge menu for automatic crafting.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
        MENUS.register("centrifuge", () ->
            IMenuTypeExtension.create(CentrifugeMenu::new));

    /**
     * Register all menus with the mod event bus.
     */
    public static void register(IEventBus modEventBus) {
        MENUS.register(Objects.requireNonNull(modEventBus, "modEventBus"));
        DevMod.LOGGER.debug("[Clone] Clone menus registered");
    }
}

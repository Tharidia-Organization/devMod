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
import com.devmod.hologram.HologramItems;
import com.devmod.npc.item.NpcItems;

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
            .title(Objects.requireNonNull(Component.translatable("itemGroup.devmod.clone")))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> Objects.requireNonNull(CloneItems.BIOSCANNER.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                // Admin Tools
                output.accept(Objects.requireNonNull(CloneItems.ADMIN_TERMINAL.get()));

                // Tools
                output.accept(Objects.requireNonNull(CloneItems.BIOSCANNER.get()));
                output.accept(Objects.requireNonNull(NpcItems.NEUROCELL_NPC.get()));

                // Blocks in workflow order
                output.accept(Objects.requireNonNull(CloneItems.IMPRINTER.get()));
                output.accept(Objects.requireNonNull(CloneItems.NEUROCELL.get()));
                output.accept(Objects.requireNonNull(CloneItems.NEUROCELL_L.get()));
                output.accept(Objects.requireNonNull(CloneItems.NEUROLINK.get()));
                output.accept(Objects.requireNonNull(CloneItems.REFORMER.get()));

                // Teleportation
                output.accept(Objects.requireNonNull(CloneItems.TELEPAD.get()));

                // Processing
                output.accept(Objects.requireNonNull(CloneItems.CENTRIFUGE.get()));

                // Hologram
                output.accept(Objects.requireNonNull(HologramItems.HOLOGRAM_PROJECTOR.get()));

                // Clone Machines (Oritech-style)
                output.accept(Objects.requireNonNull(CloneItems.CLONE_PULVERIZER.get()));
                output.accept(Objects.requireNonNull(CloneItems.GRINDER.get())); // Pulverizer component
                output.accept(Objects.requireNonNull(CloneItems.CLONE_FOUNDRY.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_PUMP.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_STEAM_ENGINE.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_LAVA_GENERATOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_REFINERY.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_SHRINKER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_TREEFELLER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_TECH_DOOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_BIO_GENERATOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_ASSEMBLER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_CENTRIFUGE_L.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_FUEL_GENERATOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_ATOMIC_FORGE.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_LASER_ARM.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_DRILL.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_FERTILIZER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_CRUSHER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_SMELTER.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_STORAGE_UNIT.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_ENERGY_PIPE.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_TANK.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_BATTERY.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_SOLAR_PANEL.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_MOTOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_CONVEYOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_REACTOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_PROCESSOR.get()));
                output.accept(Objects.requireNonNull(CloneItems.CLONE_CHARGER.get()));
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

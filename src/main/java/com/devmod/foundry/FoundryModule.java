package com.devmod.foundry;

import javax.annotation.Nonnull;

import net.neoforged.bus.api.IEventBus;

import com.devmod.DevMod;
import com.devmod.foundry.progression.FoundryProgressAttachment;

/**
 * Foundry module initialization.
 * Provides multiblock smeltery/foundry mechanics inspired by Tinkers' Construct.
 */
public final class FoundryModule {
    private FoundryModule() {}

    /**
     * Initialize the foundry module.
     * Call from DevMod constructor after BLOCKS/ITEMS registries are set up.
     */
    public static void init(@Nonnull IEventBus modEventBus) {
        FoundryFluids.init(modEventBus);
        FoundryProgressAttachment.register(modEventBus);

        FoundryBlocks.init();
        FoundryItems.init();
        com.devmod.foundry.tool.FoundryToolItems.init();
        FoundryBlockEntities.register(modEventBus);
        FoundryMenus.register(modEventBus);
        FoundryRecipeTypes.register(modEventBus);
        FoundryCreativeTab.register(modEventBus);

        DevMod.LOGGER.info("[Foundry] Foundry module initialized");
    }
}

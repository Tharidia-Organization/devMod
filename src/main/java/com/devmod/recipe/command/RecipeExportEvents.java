package com.devmod.recipe.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handler for registering recipe export commands.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class RecipeExportEvents {

    private RecipeExportEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeExportCommand.register(event.getDispatcher());
    }
}

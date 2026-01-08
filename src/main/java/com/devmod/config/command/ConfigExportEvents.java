package com.devmod.config.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handler for registering config export commands.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class ConfigExportEvents {

    private ConfigExportEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ConfigExportCommand.register(event.getDispatcher());
    }
}

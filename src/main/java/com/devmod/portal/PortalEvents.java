package com.devmod.portal;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;
import com.devmod.portal.command.PortalCommand;

/**
 * Event handlers for the custom portals system.
 * Registers commands and handles server-side events.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class PortalEvents {

    private PortalEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PortalCommand.register(event.getDispatcher());
    }
}

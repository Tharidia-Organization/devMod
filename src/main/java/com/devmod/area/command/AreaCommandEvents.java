package com.devmod.area.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handler for Area command registration.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class AreaCommandEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(AreaCommandEvents.class);

    private AreaCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AreaCommands.register(event.getDispatcher());
        LOGGER.debug("[Area] Registered area commands");
    }
}

package com.devmod.foundry.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handler for Foundry command registration.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class FoundryCommandEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoundryCommandEvents.class);

    private FoundryCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FoundryCommands.register(event.getDispatcher());
        LOGGER.debug("[Foundry] Registered foundry commands");
    }
}

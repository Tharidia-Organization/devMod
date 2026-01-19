package com.devmod.nexus.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handler for Nexus command registration.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class NexusCommandEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(NexusCommandEvents.class);

    private NexusCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("[Nexus] NexusCommands disabled; commands are registered via NexusCommand");
    }
}

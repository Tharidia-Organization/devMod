package com.devmod.compat.mods.dummmmmmy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;

/**
 * Event handlers for Dummmmmmy mod integration.
 *
 * Registers commands conditionally when the mod is available.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class DummmmmmyEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(DummmmmmyEvents.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (DummmmmmyCompat.isAvailable()) {
            DummmmmmyCommands.register(event.getDispatcher());
            LOGGER.info("[Compat:dummmmmmy] Registered /dummy commands");
        }
    }
}

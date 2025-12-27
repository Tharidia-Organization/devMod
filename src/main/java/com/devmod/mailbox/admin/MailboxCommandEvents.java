package com.devmod.mailbox.admin;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.devmod.DevMod;
import com.devmod.mailbox.commands.ReportCommand;

/**
 * Event subscriber for registering mailbox admin commands.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class MailboxCommandEvents {

    private MailboxCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MailboxCommands.register(event.getDispatcher());
        ReportCommand.register(event.getDispatcher());
    }
}

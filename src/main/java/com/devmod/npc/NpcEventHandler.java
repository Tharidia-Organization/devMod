package com.devmod.npc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.devmod.DevMod;
import com.devmod.npc.dialog.NpcDialogManager;
import com.devmod.npc.dialog.group.GroupDialogManager;

/**
 * Event handler for NPC system events.
 * Handles player lifecycle events for NPC-related cleanup.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class NpcEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Player disconnect - cleanup NPC dialog sessions and rate limit data.
     * Prevents memory leaks by removing player-specific entries.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        NpcDialogManager.INSTANCE.cleanupPlayer(player.getUUID());
        GroupDialogManager.INSTANCE.cleanupPlayer(player.getUUID());
        LOGGER.debug("[NPC] Cleaned up dialog data for {}", player.getName().getString());
    }
}

package com.devmod.notification.network;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.notification.NotificationRouter;
import com.devmod.notification.persistence.NotificationPreferencesRepository;
/**
 * Server-side handlers for notification network payloads.
 */
public final class NotificationNetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationNetworkHandler.class);

    private NotificationNetworkHandler() {}

    public static void handlePreferencesUpdate(NotificationPreferencesUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        var future = context.enqueueWork(() -> {
            NotificationRouter.NotificationPreferences prefs = payload.toPreferences(player.getUUID());
            var saveFuture = NotificationPreferencesRepository.INSTANCE.savePreferences(prefs)
                .exceptionally(ex -> {
                    LOGGER.warn("[NotificationNetwork] Failed to save preferences for {}", player.getUUID(), ex);
                    return null;
                });
            if (saveFuture.isCancelled()) {
                LOGGER.debug("[NotificationNetwork] Preferences save cancelled for {}", player.getUUID());
            }

            PacketDistributor.sendToPlayer(
                    Objects.requireNonNull(player),
                    NotificationPreferencesSyncPayload.from(prefs));
        });

        if (future.isCancelled()) {
            LOGGER.debug("[NotificationNetwork] Preferences update enqueue cancelled");
        }
    }
}

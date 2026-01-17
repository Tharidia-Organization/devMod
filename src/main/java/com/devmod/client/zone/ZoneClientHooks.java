package com.devmod.client.zone;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import com.devmod.DevMod;
import com.devmod.zone.network.OpenZoneEditorPayload;
import com.devmod.zone.network.ZoneEnterPayload;
import com.devmod.zone.network.ZoneSyncPayload;

/**
 * Client-side hooks for Zone Marker system.
 * Called via reflection from ZoneNetworkHandler to avoid loading client classes on server.
 */
public final class ZoneClientHooks {
    private ZoneClientHooks() {}

    /**
     * Opens the zone editor screen.
     * Called when player interacts with a zone marker.
     */
    public static void openZoneEditor(OpenZoneEditorPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        DevMod.LOGGER.debug("[Zone] Opening zone editor - existing zone: {}, marker: {}, canDelete: {}",
            payload.getZoneId(), payload.markerPosition(), payload.canDelete());

        // Open the zone editor screen
        mc.setScreen(new ZoneEditorScreen(
            payload.existingZone(),
            payload.markerPosition(),
            payload.canDelete()
        ));
    }

    /**
     * Handles zone sync from server.
     * Updates the client-side zone cache for rendering and tracking.
     */
    public static void handleZoneSync(ZoneSyncPayload payload) {
        // Update client-side zone cache
        ZoneClientCache.INSTANCE.update(payload.zones(), payload.registryVersion());

        DevMod.LOGGER.debug("[Zone] Synced {} zones (version {})",
            payload.zones().size(), payload.registryVersion());
    }

    /**
     * Handles zone enter notification from server.
     * Plays the cue sound and shows the hint message.
     */
    public static void handleZoneEnter(ZoneEnterPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Play cue sound
        var cueSound = payload.getCueSound();
        if (cueSound != null) {
            mc.level.playLocalSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                cueSound, SoundSource.AMBIENT,
                0.5f, 1.0f, false
            );
        }

        // Show zone name in action bar
        mc.player.displayClientMessage(
            Component.literal(payload.displayName())
                .withStyle(style -> style.withColor(payload.color())),
            true
        );

        // Show hint in chat (if not empty)
        if (!payload.hintMessage().isEmpty()) {
            mc.player.displayClientMessage(
                Component.literal(payload.hintMessage())
                    .withStyle(style -> style.withColor(0xAAAAAA)),
                false
            );
        }

        DevMod.LOGGER.debug("[Zone] Entered zone: {} ({})",
            payload.displayName(), payload.zoneId());
    }
}

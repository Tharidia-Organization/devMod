package com.devmod.client.hologram;

import com.devmod.hologram.network.HologramSyncPayload;
import com.devmod.hologram.network.OpenHologramEditorPayload;
/**
 * Client-side handler for hologram network payloads.
 */
public final class HologramClientHandler {
    private HologramClientHandler() {}

    /**
     * Handles OpenHologramEditorPayload - opens the editor screen.
     */
    public static void handleOpenEditor(OpenHologramEditorPayload payload) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "hologram_editor",
            () -> new HologramEditorScreen(payload.definition()));
    }

    /**
     * Handles HologramSyncPayload - updates local hologram cache.
     * Currently a no-op since TextDisplay entities are server-authoritative.
     */
    public static void handleSync(HologramSyncPayload payload) {
        // TextDisplay entities are updated server-side and synced via entity updates.
        // This payload is reserved for future client-side caching optimizations.
    }
}

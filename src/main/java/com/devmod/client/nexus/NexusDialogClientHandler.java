package com.devmod.client.nexus;

import net.minecraft.client.Minecraft;

import com.devmod.bridge.ClientUiBridge;
import com.devmod.client.ui.screens.TelemetryLogViewerScreen;
import com.devmod.runtime.network.NexusDialogPayload;
import com.devmod.runtime.network.NexusLogSnapshotPayload;
import com.devmod.runtime.network.NexusUiPayload;

/**
 * Client-side handler for Nexus network packets.
 * This class is loaded via reflection to avoid class loading issues on dedicated servers.
 */
public final class NexusDialogClientHandler {

    private NexusDialogClientHandler() {}

    /**
     * Open a dialog screen with the given payload.
     */
    public static void openDialog(NexusDialogPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        com.devmod.client.ui.ScreenSafety.openSafe(
            "nexus_dialog",
            () -> new NexusDialogScreen(
                payload.speakerName(),
                payload.lines(),
                payload.options()
            ));
    }

    /**
     * Open a Nexus UI screen based on the payload action.
     */
    public static void openUi(NexusUiPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        switch (payload.action()) {
            case TESTING_HUB -> ClientUiBridge.get().openTestingHub();
            case TELEMETRY_DASHBOARD -> ClientUiBridge.get().openTelemetryDashboard();
            case LOG_VIEWER -> com.devmod.client.ui.ScreenSafety.openSafe(
                "telemetry_log_viewer",
                mc.screen,
                () -> new TelemetryLogViewerScreen(mc.screen));
        }
    }

    /**
     * Apply a log snapshot to the log viewer screen.
     */
    public static void applyLogSnapshot(NexusLogSnapshotPayload payload) {
        TelemetryLogViewerScreen.applySnapshot(payload);
    }
}

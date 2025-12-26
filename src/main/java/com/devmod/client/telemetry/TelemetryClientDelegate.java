package com.devmod.client.telemetry;

import net.minecraft.client.Minecraft;
public final class TelemetryClientDelegate {

    private TelemetryClientDelegate() {}

    /**
     * Opens the telemetry dashboard confirmation screen.
     */
    public static void openDashboardConfirmation(String url) {
        Minecraft.getInstance().tell(() -> {
            com.devmod.client.ui.OpenExternalConfirmScreen.openWithConfirmation(
                null,
                url,
                "Open Telemetry Dashboard?"
            );
        });
    }
}

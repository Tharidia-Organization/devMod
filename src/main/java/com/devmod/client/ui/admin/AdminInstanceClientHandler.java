package com.devmod.client.ui.admin;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.runtime.network.AdminInstanceSyncPayload;

/**
 * Client-side handler for admin instance sync packets.
 * Called via reflection from AdminInstanceNetworkHandler to avoid class loading on dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminInstanceClientHandler {

    private AdminInstanceClientHandler() {}

    /**
     * Apply sync data to the admin screen.
     * If the screen is not open, opens it first.
     * Called from server thread via enqueueWork().
     */
    public static void applySync(AdminInstanceSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();

        // If admin screen is open, update it
        if (mc.screen instanceof AdminInstanceScreen screen) {
            screen.applySync(payload);
            return;
        }

        // If no screen is open or a different screen is open, open the admin screen
        com.devmod.client.ui.ScreenSafety.openSafe(
            "admin_instance",
            () -> {
                AdminInstanceScreen newScreen = new AdminInstanceScreen();
                newScreen.applySync(payload);
                return newScreen;
            });
    }

    /**
     * Open the admin screen directly (for client-side commands).
     */
    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AdminInstanceScreen)) {
            com.devmod.client.ui.ScreenSafety.openSafe(
                "admin_instance",
                AdminInstanceScreen::new);
        }
    }
}

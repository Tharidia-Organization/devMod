package com.devmod.debug.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.debug.network.EntityScanDataPayload;

/**
 * Client-side handler for EntityScanner network payloads.
 */
@OnlyIn(Dist.CLIENT)
public final class EntityScannerClientHandler {
    private EntityScannerClientHandler() {}

    /**
     * Handle incoming scan data.
     */
    public static void handleScanData(EntityScanDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.screen;

            if (currentScreen instanceof EntityScannerScreen scannerScreen) {
                if (scannerScreen.getScannerPos().equals(payload.scannerPos())) {
                    scannerScreen.updateScanData(payload);
                }
            }
        });
    }

    /**
     * Handle request to open scanner screen.
     */
    public static void handleOpenScreen(EntityScanDataPayload.OpenScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.screen;

            // Only open if not already showing scanner screen for this position
            if (!(currentScreen instanceof EntityScannerScreen existingScreen)
                || !existingScreen.getScannerPos().equals(payload.scannerPos())) {
                mc.setScreen(new EntityScannerScreen(payload.scannerPos()));
            }
        });
    }

    /**
     * Get the currently open scanner screen, if any.
     */
    @Nullable
    public static EntityScannerScreen getCurrentScannerScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EntityScannerScreen screen) {
            return screen;
        }
        return null;
    }
}

package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Objects;

/**
 * Red flash effect for headshots.
 * Trigger: bodyPart == HEAD
 * Duration: 300ms
 * Color: Semi-transparent red fade-out
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class HeadshotFlashEffect {

    private static final ResourceLocation LAYER_ID =
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "headshot_flash"));

    private static long flashStartTime = 0;
    private static final long FLASH_DURATION_MS = 300;
    private static final int FLASH_COLOR_BASE = 0xFF0000; // Red

    /**
     * Activates the flash effect. Call when headshot is detected.
     */
    public static void trigger() {
        flashStartTime = System.currentTimeMillis();
    }

    /**
     * Checks if the flash is currently active.
     */
    public static boolean isActive() {
        return System.currentTimeMillis() - flashStartTime < FLASH_DURATION_MS;
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Register above crosshair to cover everything
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            (graphics, deltaTracker) -> render(graphics, graphics.guiWidth(), graphics.guiHeight())
        );
    }

    /**
     * Renders the flash effect. Called in HUD render loop.
     * @param graphics GuiGraphics context
     * @param screenWidth screen width
     * @param screenHeight screen height
     */
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!isActive()) return;

        long elapsed = System.currentTimeMillis() - flashStartTime;
        float progress = elapsed / (float) FLASH_DURATION_MS;

        // Alpha fade-out: starts at 128 (50%) and goes to 0
        int alpha = (int) ((1.0f - progress) * 128);
        int color = (alpha << 24) | FLASH_COLOR_BASE;

        // Overlay full-screen
        graphics.fill(0, 0, screenWidth, screenHeight, color);
    }
}

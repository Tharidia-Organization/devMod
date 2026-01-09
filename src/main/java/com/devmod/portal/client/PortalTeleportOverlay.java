package com.devmod.portal.client;

import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.portal.PortalColor;

/**
 * Client-side overlay showing teleportation progress when standing in a portal.
 * Displays a colored screen overlay that fades in as the teleport timer progresses.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PortalTeleportOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "portal_teleport_hud");

    // State
    private static boolean active = false;
    private static PortalColor portalColor = null;
    private static int ticksInPortal = 0;
    private static int requiredDelay = 80; // Default 4 seconds
    private static int ticksSinceLastServerUpdate = 0;

    // Visual constants
    private static final float MAX_ALPHA = 0.7f;
    /** Max ticks without server update before auto-resetting overlay (10 ticks = 500ms) */
    private static final int SERVER_UPDATE_TIMEOUT = 10;

    private PortalTeleportOverlay() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            PortalTeleportOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active || portalColor == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate progress (0.0 to 1.0)
        float progress = calculateProgress(ticksInPortal, requiredDelay);

        // Calculate alpha based on progress
        int alpha = (int) (progress * MAX_ALPHA * 255);
        if (alpha <= 0) return;

        // Create colored overlay with portal color
        int overlayColor = (alpha << 24)
            | (portalColor.getRed() << 16)
            | (portalColor.getGreen() << 8)
            | portalColor.getBlue();

        // Draw full-screen color overlay
        graphics.fill(0, 0, screenWidth, screenHeight, overlayColor);

        // Draw vignette effect (darker edges)
        if (progress > 0.3f) {
            drawVignette(graphics, screenWidth, screenHeight, progress);
        }
    }

    /**
     * Draws a vignette effect (darker edges) that intensifies with progress.
     */
    private static void drawVignette(GuiGraphics graphics, int width, int height, float progress) {
        int vignetteAlpha = (int) ((progress - 0.3f) * 0.5f * 255);
        if (vignetteAlpha <= 0) return;

        int darkColor = (vignetteAlpha << 24); // Pure black with alpha

        // Edge bands
        int edgeSize = Math.max(20, (int) (progress * 50));

        // Top edge
        graphics.fillGradient(0, 0, width, edgeSize, darkColor, 0);
        // Bottom edge
        graphics.fillGradient(0, height - edgeSize, width, height, 0, darkColor);
        // Left edge (approximate with fill)
        graphics.fill(0, 0, edgeSize / 2, height, darkColor);
        // Right edge
        graphics.fill(width - edgeSize / 2, 0, width, height, darkColor);
    }

    /**
     * Calculates teleport progress (0.0 to 1.0) with easing applied.
     */
    private static float calculateProgress(int currentTicks, int delay) {
        if (delay <= 0) {
            return 1.0f;
        }
        float linear = Math.max(0.0f, Math.min(1.0f, (float) currentTicks / delay));
        // Apply ease-in-out cubic for smoother visual transition
        return easeInOutCubic(linear);
    }

    /**
     * Ease-in-out cubic function for smooth animation.
     * Starts slow, speeds up in middle, slows down at end.
     */
    private static float easeInOutCubic(float t) {
        return t < 0.5f
            ? 4.0f * t * t * t
            : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0f;
    }

    // === Public API ===

    /**
     * Called when player enters a portal. Activates the overlay.
     */
    public static void enterPortal(PortalColor color, int delay) {
        active = true;
        portalColor = color;
        ticksInPortal = 0;
        requiredDelay = delay;
        ticksSinceLastServerUpdate = 0;
    }

    /**
     * Called each tick while in the portal. Increments the counter.
     * Also handles timeout detection - if no server update is received
     * for SERVER_UPDATE_TIMEOUT ticks, the overlay is automatically reset.
     */
    public static void tick() {
        if (active) {
            ticksInPortal++;
            ticksSinceLastServerUpdate++;

            // Auto-reset if no server update received (player exited portal)
            if (ticksSinceLastServerUpdate > SERVER_UPDATE_TIMEOUT) {
                exitPortal();
            }
        }
    }

    /**
     * Called when player exits the portal. Deactivates the overlay.
     */
    public static void exitPortal() {
        active = false;
        portalColor = null;
        ticksInPortal = 0;
    }

    /**
     * Updates the ticks in portal directly (for sync from server).
     * Also resets the server update timeout counter.
     */
    public static void updateTicks(int ticks) {
        ticksInPortal = ticks;
        ticksSinceLastServerUpdate = 0; // Reset timeout - server is still sending updates
    }

    /**
     * Returns true if the overlay is currently active.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Returns the current portal color, or null if not in a portal.
     */
    public static PortalColor getColor() {
        return portalColor;
    }

    /**
     * Returns the number of ticks spent in the portal.
     */
    public static int getTicksInPortal() {
        return ticksInPortal;
    }
}

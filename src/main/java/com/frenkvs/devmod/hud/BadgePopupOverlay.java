package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Overlay for badge unlock popup notifications.
 * Shows a popup for 5 seconds when a badge is unlocked, with fade in/out.
 * Supports queueing multiple badges.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class BadgePopupOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "badge_popup");

    private static final long POPUP_DURATION_MS = 5000;
    private static final long FADE_IN_MS = 300;
    private static final long FADE_OUT_MS = 500;

    private static final Queue<BadgePopup> popupQueue = new LinkedList<>();

    public record BadgePopup(String name, String rarity, long startTime) {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.TITLE,
            LAYER_ID,
            BadgePopupOverlay::render
        );
    }

    /**
     * Show a badge unlock popup. Called from network handler.
     */
    public static void showBadge(String badgeName, String rarity) {
        popupQueue.add(new BadgePopup(badgeName, rarity, System.currentTimeMillis()));
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        BadgePopup current = popupQueue.peek();
        if (current == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long elapsed = System.currentTimeMillis() - current.startTime();
        if (elapsed > POPUP_DURATION_MS) {
            popupQueue.poll();
            return;
        }

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();

        // Calculate alpha
        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / (float) FADE_IN_MS;
        } else if (elapsed > POPUP_DURATION_MS - FADE_OUT_MS) {
            alpha = (POPUP_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
        } else {
            alpha = 1.0f;
        }

        int y = 50; // Position near top
        int boxWidth = 220;
        int boxHeight = 55;
        int x = (screenWidth - boxWidth) / 2;

        // Background
        int bgAlpha = (int) (alpha * 220);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, (bgAlpha << 24) | 0x1A1A2E);

        // Border with rarity color
        int borderColor = getRarityColor(current.rarity(), alpha);
        drawBorder(graphics, x, y, boxWidth, boxHeight, borderColor);

        // Texts
        int textAlpha = (int) (alpha * 255);
        graphics.drawCenteredString(font, "BADGE UNLOCKED!",
            screenWidth / 2, y + 8, (textAlpha << 24) | 0xFFD700);
        graphics.drawCenteredString(font, current.name(),
            screenWidth / 2, y + 22, borderColor);
        graphics.drawCenteredString(font, "[" + current.rarity().toUpperCase() + "]",
            screenWidth / 2, y + 38, (textAlpha << 24) | 0xB0B0B0);
    }

    private static int getRarityColor(String rarity, float alpha) {
        int a = (int) (alpha * 255) << 24;
        return switch (rarity.toUpperCase()) {
            case "LEGENDARY" -> a | 0xFFD700; // Gold
            case "EPIC" -> a | 0xA335EE;      // Purple
            case "RARE" -> a | 0x0070DD;      // Blue
            case "UNCOMMON" -> a | 0x1EFF00;  // Green
            default -> a | 0xFFFFFF;          // White (Common)
        };
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 2, color);           // Top
        g.fill(x, y + h - 2, x + w, y + h, color);   // Bottom
        g.fill(x, y, x + 2, y + h, color);           // Left
        g.fill(x + w - 2, y, x + w, y + h, color);   // Right
    }
}

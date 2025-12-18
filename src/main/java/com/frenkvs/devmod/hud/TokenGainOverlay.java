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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Overlay for token gain animation.
 * Shows "+X Tokens" floating upward with fade-out effect.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class TokenGainOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "token_gain");

    private static final long ANIMATION_DURATION_MS = 2000;
    private static final List<TokenGain> activeGains = new ArrayList<>();

    public record TokenGain(int amount, long startTime) {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.HOTBAR,
            LAYER_ID,
            TokenGainOverlay::render
        );
    }

    /**
     * Show token gain animation. Called from network handler.
     */
    public static void show(int amount) {
        if (amount > 0) {
            activeGains.add(new TokenGain(amount, System.currentTimeMillis()));
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (activeGains.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        int screenHeight = graphics.guiHeight();

        Iterator<TokenGain> it = activeGains.iterator();
        int index = 0;

        int baseX = 25; // Left side
        int baseY = screenHeight - 90; // Above currency display

        while (it.hasNext()) {
            TokenGain gain = it.next();
            long elapsed = System.currentTimeMillis() - gain.startTime();

            if (elapsed > ANIMATION_DURATION_MS) {
                it.remove();
                continue;
            }

            float progress = elapsed / (float) ANIMATION_DURATION_MS;

            // Float up: 0 -> 40 pixels
            float yOffset = progress * 40;

            // Fade out with ease-out curve
            float alpha = 1.0f - (progress * progress);

            // Scale: start 1.3x, end 1.0x
            float scale = 1.3f - (progress * 0.3f);

            int x = baseX;
            int y = (int) (baseY - yOffset - (index * 18));

            // Color: gold with alpha
            int color = ((int) (alpha * 255) << 24) | 0xFFD700;

            String text = "+" + gain.amount() + " Tokens";

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(font, text, 0, 0, color, true);
            graphics.pose().popPose();

            index++;
        }
    }
}

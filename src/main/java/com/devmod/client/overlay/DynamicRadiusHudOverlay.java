package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.rendering.LightLevelOverlay;
import com.devmod.client.rendering.SpawnabilityOverlay;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.util.I18n;
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class DynamicRadiusHudOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "dynamic_radius_hint");

    // Colors (delegating to OverlayTheme)
    private static final int BG_COLOR = OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, OverlayTheme.Alpha.SUBTLE);
    private static final int TEXT_COLOR = OverlayTheme.Text.LIGHT;
    private static final int PADDING = 2;
    private static final int MARGIN = 6;
    private static final int LINE_GAP = 2;

    private DynamicRadiusHudOverlay() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        @Nonnull ResourceLocation overlay = Objects.requireNonNull(
            VanillaGuiLayers.DEBUG_OVERLAY,
            "debug overlay layer"
        );
        @Nonnull ResourceLocation layerId = Objects.requireNonNull(LAYER_ID, "layer id");
        event.registerAbove(overlay, layerId, DynamicRadiusHudOverlay::render);
    }

    private static void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        UIScaleManager.update();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        boolean lightEnabled = LightLevelOverlay.INSTANCE.isEnabled();
        boolean spawnEnabled = SpawnabilityOverlay.INSTANCE.isEnabled();
        if (!lightEnabled && !spawnEnabled) return;

        Font font = mc.font;
        if (font == null) return;

        int fps = mc.getFps();
        int sMargin = UIScaleManager.scale(MARGIN);
        int x = sMargin;
        int y = sMargin;

        if (lightEnabled) {
            Component name = I18n.translate("devmod.radial.item.light_levels");
            y = drawHint(graphics, font, x, y, name,
                LightLevelOverlay.INSTANCE.getRadius(),
                LightLevelOverlay.INSTANCE.getDynamicRadius(),
                fps);
        }

        if (spawnEnabled) {
            Component name = I18n.translate("devmod.radial.item.spawnability");
            y = drawHint(graphics, font, x, y, name,
                SpawnabilityOverlay.INSTANCE.getRadius(),
                SpawnabilityOverlay.INSTANCE.getDynamicRadius(),
                fps);
        }
    }

    private static int drawHint(GuiGraphics graphics, Font font, int x, int y, Component name,
                                int baseRadius, int dynamicRadius, int fps) {
        Component line = I18n.translate("devmod.render.overlay_radius_hint", name, baseRadius, dynamicRadius, fps);
        int sPadding = UIScaleManager.scale(PADDING);
        int sLineGap = UIScaleManager.scale(LINE_GAP);
        int maxWidth = Math.max(0, UIScaleManager.getSafeWidth() - sPadding * 2);
        String display = truncateToWidth(font, line.getString(), maxWidth);
        int width = UIScaleManager.getScaledStringWidth(font, display);
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int boxHeight = lineHeight + (sPadding * 2);
        graphics.fill(x - sPadding, y - sPadding, x + width + sPadding, y + lineHeight + sPadding, BG_COLOR);
        UIScaleManager.drawScaledString(graphics, font, display, x, y, TEXT_COLOR, false);
        return y + boxHeight + sLineGap;
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int minChars = Math.min(4, text.length());
        String trimmed = text;
        while (font.width(trimmed + ellipsis) > maxWidth && trimmed.length() > minChars) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }
}

package com.devmod.client.overlay;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.endurance.ClientCombatFlowCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.ComboSystem.StyleRank;

/**
 * DMC-style real-time Style Rank HUD overlay.
 *
 * Displays:
 * - Current rank letter (D through SSS) with color coding
 * - Combo hit count below the rank
 * - Fade-in/fade-out on rank changes
 * - Auto-hides after 3 seconds of inactivity
 *
 * Positioned in the top-right area of the screen.
 */
@OnlyIn(Dist.CLIENT)
public class StyleRankOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "style_rank_hud");

    public static final StyleRankOverlay INSTANCE = new StyleRankOverlay();

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            (graphics, deltaTracker) -> INSTANCE.render(graphics, deltaTracker)
        );
    }

    // Position (top-right corner)
    private static final int MARGIN = 10;
    private static final int PANEL_WIDTH = 80;
    private static final int PANEL_HEIGHT = 60;

    // Colors from OverlayTheme
    private static final int BG_COLOR = OverlayTheme.Panel.BG_STANDARD;
    private static final int BORDER_COLOR = OverlayTheme.Border.ACCENT;

    // Timing
    private static final long HIDE_DELAY_MS = 3000;
    private static final float FADE_SPEED = 0.1f; // alpha per tick (~10 ticks to full)

    // Animation state
    private float animationTick = 0;
    private float displayAlpha = 0f;
    private StyleRank lastDisplayedRank = StyleRank.D;
    private float rankChangeFlash = 0f;
    private long lastActiveTime = 0;

    private StyleRankOverlay() {}

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        UIScaleManager.update();
        ClientCombatFlowCache cache = ClientCombatFlowCache.INSTANCE;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        animationTick += partialTick;

        boolean active = cache.isActive() && cache.getCombo() > 0;
        long now = System.currentTimeMillis();

        if (active) {
            lastActiveTime = now;
        }

        // Determine target alpha: full if active or within hide delay, zero otherwise
        boolean withinHideDelay = (now - lastActiveTime) < HIDE_DELAY_MS;
        float targetAlpha = (active || withinHideDelay) ? 1.0f : 0.0f;

        // Lerp alpha
        if (displayAlpha < targetAlpha) {
            displayAlpha = Math.min(targetAlpha, displayAlpha + FADE_SPEED);
        } else if (displayAlpha > targetAlpha) {
            displayAlpha = Math.max(targetAlpha, displayAlpha - FADE_SPEED);
        }

        // Don't render if fully transparent
        if (displayAlpha <= 0.01f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = Objects.requireNonNull(mc.font, "Font cannot be null");
        int screenWidth = graphics.guiWidth();

        // Scale dimensions
        int sMargin = UIScaleManager.scale(MARGIN);
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);

        // Panel position (top-right)
        int x = screenWidth - sPanelWidth - sMargin;
        int y = sMargin;

        // Get current rank data
        StyleRank rank = cache.getStyleRank();
        int combo = cache.getCombo();

        // Detect rank change for flash effect
        if (rank != lastDisplayedRank) {
            rankChangeFlash = 1.0f;
            lastDisplayedRank = rank;
        }

        // Decay rank change flash
        if (rankChangeFlash > 0) {
            rankChangeFlash = Math.max(0, rankChangeFlash - 0.05f);
        }

        int alphaInt = (int) (displayAlpha * 255);

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // === BACKGROUND ===
        int bgAlpha = (int) (displayAlpha * ((BG_COLOR >> 24) & 0xFF));
        int bgColor = (bgAlpha << 24) | (BG_COLOR & DesignTokens.Mask.RGB);
        graphics.fill(x, y, x + sPanelWidth, y + sPanelHeight, bgColor);

        // Border
        int borderAlpha = (int) (displayAlpha * ((BORDER_COLOR >> 24) & 0xFF));
        int borderColor = (borderAlpha << 24) | (BORDER_COLOR & DesignTokens.Mask.RGB);
        graphics.fill(x, y, x + sPanelWidth, y + 1, borderColor);
        graphics.fill(x, y + sPanelHeight - 1, x + sPanelWidth, y + sPanelHeight, borderColor);
        graphics.fill(x, y, x + 1, y + sPanelHeight, borderColor);
        graphics.fill(x + sPanelWidth - 1, y, x + sPanelWidth, y + sPanelHeight, borderColor);

        // === RANK LETTER (large, centered) ===
        String rankText = rank.name();
        int rankColor = OverlayTheme.withAlpha(rank.getColor(), alphaInt);

        // Flash effect on rank change
        if (rankChangeFlash > 0) {
            rankColor = OverlayTheme.lerp(rankColor, OverlayTheme.withAlpha(OverlayTheme.Utility.WHITE, alphaInt), rankChangeFlash);
        }

        // Glow effect for SSS rank
        if (rank == StyleRank.SSS) {
            float glow = (float) Math.sin(animationTick * 0.3) * 0.3f + 0.7f;
            int glowAlpha = (int) (displayAlpha * glow * 64);
            int glowColor = OverlayTheme.withAlpha(rank.getColor(), glowAlpha);
            // Draw glow background behind rank text
            int glowPad = UIScaleManager.scale(4);
            graphics.fill(x + glowPad, y + UIScaleManager.scale(2),
                x + sPanelWidth - glowPad, y + UIScaleManager.scale(32), glowColor);
        }

        // Draw rank letter with scale effect
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int textWidth = UIScaleManager.getScaledStringWidth(font, rankText);
        float scale = 2.5f + rankChangeFlash * 0.5f;

        pose.pushPose();
        int centerX = x + sPanelWidth / 2;
        int centerY = y + UIScaleManager.scale(18);
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1.0f);
        pose.translate(-textWidth / 2f, -lineHeight / 2f, 0);
        UIScaleManager.drawScaledString(graphics, font, rankText, 0, 0, rankColor, true);
        pose.popPose();

        // === COMBO COUNT ===
        if (combo > 0) {
            String comboText = combo + " HITS";
            int comboWidth = UIScaleManager.getScaledStringWidth(font, comboText);
            int comboX = x + (sPanelWidth - comboWidth) / 2;
            int comboY = y + UIScaleManager.scale(40);
            int comboColor = OverlayTheme.withAlpha(OverlayTheme.Text.MUTED, alphaInt);
            UIScaleManager.drawScaledString(graphics, font, comboText, comboX, comboY, comboColor, false);
        }

        // === RANK NAME (subtitle) ===
        String rankName = rank.getDisplayName();
        int nameWidth = UIScaleManager.getScaledStringWidth(font, rankName);
        int nameX = x + (sPanelWidth - nameWidth) / 2;
        int nameY = y + UIScaleManager.scale(50);
        int nameColor = OverlayTheme.withAlpha(OverlayTheme.Neutral.N450, alphaInt);
        UIScaleManager.drawScaledString(graphics, font, rankName, nameX, nameY, nameColor, false);

        pose.popPose();
    }

}

package com.devmod.client.transport;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.transport.TransportState;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * HUD overlay that appears when the player is near a Transport Core block.
 *
 * <p>Shows:
 * <ul>
 *   <li>Charge level (progress bar, 0-100%)</li>
 *   <li>Current status (Idle, Charging, Ready, Cooldown)</li>
 *   <li>Connected waypoint/network name</li>
 * </ul>
 *
 * <p>Renders at bottom-center, above hotbar. Compact horizontal bar.
 * Fades in/out based on proximity (within 5 blocks of a Transport Core).
 */
public class TransportCoreOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_core_proximity");

    public static final TransportCoreOverlay INSTANCE = new TransportCoreOverlay();

    /** Maximum distance (squared) to detect a Transport Core. 5 blocks = 25 squared. */
    private static final double MAX_DISTANCE_SQ = 25.0;

    /** Scan radius in blocks for finding nearby cores. */
    private static final int SCAN_RADIUS = 5;

    /** How often to scan for nearby cores (every 10 ticks). */
    private static final int SCAN_INTERVAL = 10;

    // Rendering constants
    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 6;
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 32;

    // State
    private int scanCooldown = 0;
    private boolean visible = false;
    private float fadeAlpha = 0f;

    // Cached core data (updated on scan)
    private TransportState cachedState = TransportState.IDLE;
    private String cachedDisplayName = "";
    private String cachedNetworkName = "";
    private int cachedChargePercent = 0;
    private int cachedColorValue = 0x169C9C; // Cyan default

    private TransportCoreOverlay() {}

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            (graphics, deltaTracker) -> INSTANCE.render(graphics, deltaTracker)
        );
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        // Don't render if the charging overlay is already active
        TransportOverlay chargingOverlay = TransportClientPayloadHooks.getOverlay();
        if (chargingOverlay.isActive()) {
            fadeAlpha = 0f;
            visible = false;
            return;
        }

        // Periodic scan for nearby Transport Core
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            scanForNearbyCore(mc);
        }

        // Fade animation
        float fadeSpeed = 0.15f;
        if (visible) {
            fadeAlpha = Math.min(1.0f, fadeAlpha + fadeSpeed);
        } else {
            fadeAlpha = Math.max(0.0f, fadeAlpha - fadeSpeed);
        }

        if (fadeAlpha <= 0.01f) {
            return;
        }

        UIScaleManager.update();
        renderOverlay(graphics, mc);
    }

    /**
     * Scans nearby blocks for a TransportCoreBlockEntity.
     */
    private void scanForNearbyCore(@Nonnull Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            visible = false;
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        TransportCoreBlockEntity nearestCore = null;
        double nearestDistSq = MAX_DISTANCE_SQ + 1;

        // Scan in a box around the player
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    double distSq = playerPos.distSqr(checkPos);
                    if (distSq > MAX_DISTANCE_SQ || distSq >= nearestDistSq) {
                        continue;
                    }

                    BlockEntity be = mc.level.getBlockEntity(checkPos);
                    if (be instanceof TransportCoreBlockEntity core) {
                        nearestCore = core;
                        nearestDistSq = distSq;
                    }
                }
            }
        }

        if (nearestCore != null) {
            visible = true;
            cachedState = nearestCore.getCurrentState();
            cachedDisplayName = nearestCore.getDisplayName();
            cachedNetworkName = nearestCore.getNetworkName();
            cachedColorValue = nearestCore.getColor().getColorValue();

            // Estimate charge percent from executor state (client-side approximation)
            int chargeTime = nearestCore.getChargeTime();
            cachedChargePercent = chargeTime <= 0 ? 100 : 0;
        } else {
            visible = false;
        }
    }

    /**
     * Renders the compact overlay bar above the hotbar.
     */
    private void renderOverlay(@Nonnull GuiGraphics graphics, @Nonnull Minecraft mc) {
        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelHeight = UIScaleManager.scale(PANEL_HEIGHT);
        int sBarWidth = UIScaleManager.scale(BAR_WIDTH);
        int sBarHeight = UIScaleManager.scale(BAR_HEIGHT);

        // Position: bottom-center, above hotbar
        int panelX = (screenWidth - sPanelWidth) / 2;
        int panelY = screenHeight - sPanelHeight - UIScaleManager.scale(48);

        int alpha = (int) (fadeAlpha * 0xCC); // Max 80% opacity
        if (alpha <= 0) return;

        // Background
        int bgColor = (alpha << 24) | (OverlayTheme.Panel.BG_BASE & DesignTokens.Mask.RGB);
        graphics.fill(panelX, panelY, panelX + sPanelWidth, panelY + sPanelHeight, bgColor);

        // Border (top only for minimal look)
        int borderAlpha = (int) (fadeAlpha * 0xFF);
        int borderColor = (borderAlpha << 24) | (cachedColorValue & DesignTokens.Mask.RGB);
        graphics.fill(panelX, panelY, panelX + sPanelWidth, panelY + UIScaleManager.scale(1), borderColor);

        // State text (left side)
        String stateText = getStateLabel();
        int stateColor = withFadeAlpha(cachedState.getPrimaryColorWithAlpha());
        int sTextPadding = UIScaleManager.scale(6);
        int sTextY = panelY + UIScaleManager.scale(4);
        UIScaleManager.drawScaledString(graphics, font, stateText, panelX + sTextPadding, sTextY, stateColor);

        // Display name / network name (right side)
        String nameText = getNameLabel();
        if (!nameText.isEmpty()) {
            int nameWidth = UIScaleManager.getScaledStringWidth(font, nameText);
            int nameColor = withFadeAlpha(OverlayTheme.Text.MUTED);
            UIScaleManager.drawScaledString(graphics, font, nameText, panelX + sPanelWidth - nameWidth - sTextPadding, sTextY, nameColor);
        }

        // Progress bar (bottom area of panel)
        int barX = panelX + (sPanelWidth - sBarWidth) / 2;
        int barY = panelY + sPanelHeight - sBarHeight - UIScaleManager.scale(5);

        // Bar background
        int barBgColor = (int) (fadeAlpha * 0x66) << 24 | 0x1a1a1a;
        graphics.fill(barX, barY, barX + sBarWidth, barY + sBarHeight, barBgColor);

        // Bar fill
        if (cachedChargePercent > 0) {
            int fillWidth = (int) (sBarWidth * (cachedChargePercent / 100f));
            int fillColor = (borderAlpha << 24) | (cachedState.getSecondaryColor() & DesignTokens.Mask.RGB);
            if (fillWidth > 0) {
                graphics.fill(barX, barY, barX + fillWidth, barY + sBarHeight, fillColor);
            }
        }

        // Bar border (thin)
        int barBorderColor = (int) (fadeAlpha * 0x88) << 24 | (cachedColorValue & DesignTokens.Mask.RGB);
        int sThin = UIScaleManager.scale(1);
        graphics.fill(barX, barY, barX + sBarWidth, barY + sThin, barBorderColor);
        graphics.fill(barX, barY + sBarHeight - sThin, barX + sBarWidth, barY + sBarHeight, barBorderColor);
        graphics.fill(barX, barY, barX + sThin, barY + sBarHeight, barBorderColor);
        graphics.fill(barX + sBarWidth - sThin, barY, barX + sBarWidth, barY + sBarHeight, barBorderColor);
    }

    /**
     * Returns a label for the current transport state.
     */
    @Nonnull
    private String getStateLabel() {
        return switch (cachedState) {
            case IDLE -> "READY";
            case CHARGING -> "CHARGING";
            case ACTIVE -> "ACTIVE";
            case ERROR -> "ERROR";
            case COOLDOWN -> "COOLDOWN";
            case LOCKED -> "LOCKED";
        };
    }

    /**
     * Returns a label for the connected destination / network.
     */
    @Nonnull
    private String getNameLabel() {
        if (!cachedDisplayName.isEmpty()) {
            return cachedDisplayName;
        }
        if (!cachedNetworkName.isEmpty()) {
            return cachedNetworkName;
        }
        return "";
    }

    /**
     * Applies the current fade alpha to an ARGB color.
     */
    private int withFadeAlpha(int argbColor) {
        int originalAlpha = (argbColor >> 24) & 0xFF;
        int fadedAlpha = (int) (originalAlpha * fadeAlpha);
        return (fadedAlpha << 24) | (argbColor & DesignTokens.Mask.RGB);
    }
}

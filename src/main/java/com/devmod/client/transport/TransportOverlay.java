package com.devmod.client.transport;

import javax.annotation.Nonnull;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportState;
import com.devmod.transport.network.TransportChargeUpdatePayload;
import com.devmod.transport.network.TransportCountdownPayload;
import com.devmod.transport.network.TransportPartyStatusPayload;
import com.devmod.transport.network.TransportStatePayload;

/**
 * HUD overlay for transport charging and state display.
 *
 * <p>From Bibbia Estetica Regola 4:
 * <ul>
 *   <li>Progress bar ALWAYS horizontal, centered</li>
 *   <li>State ALWAYS bottom-left</li>
 *   <li>Destination ALWAYS bottom-right</li>
 *   <li>Action hint ALWAYS bottom center</li>
 *   <li>Border color = TransportState.primary</li>
 *   <li>Position: Screen center, slightly above</li>
 *   <li>Transparency: 85% opaque (0xD9)</li>
 * </ul>
 */
public class TransportOverlay implements LayeredDraw.Layer {

    private static final int BACKGROUND_ALPHA = 0xD9; // 85% opaque
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_BORDER = 2;

    // Scaled dimensions (updated each frame for responsiveness)
    private int scaledBarWidth;
    private int scaledBarHeight;
    private int scaledBarBorder;

    private boolean active = false;
    private TransportState state = TransportState.IDLE;
    private int currentCharge = 0;
    private int requiredCharge = 40;
    private String destinationName = "";
    private int distance = 0;
    private int ticksSinceLastUpdate = 0;

    private String cachedStateText = "READY";
    private String cachedPercentText = "0%";
    private String cachedDestinationText = "";
    private String cachedActionHint = "Stand still to charge";

    private static final int SERVER_UPDATE_TIMEOUT_TICKS = 20;

    /**
     * Updates the overlay state from a TransportStatePayload.
     */
    public void updateState(@Nonnull TransportStatePayload payload) {
        this.active = payload.inTransport();
        this.state = payload.getState();
        this.currentCharge = payload.currentCharge();
        this.requiredCharge = payload.requiredCharge();
        this.destinationName = payload.destinationName();
        this.distance = payload.distance();
        refreshTextCache();
        markUpdated();
    }

    /**
     * Updates charge progress from a TransportChargeUpdatePayload.
     */
    public void updateCharge(@Nonnull TransportChargeUpdatePayload payload) {
        this.active = true;
        this.currentCharge = payload.currentCharge();
        this.requiredCharge = payload.requiredCharge();
        if (payload.isComplete()) {
            this.state = TransportState.ACTIVE;
        }
        refreshTextCache();
        markUpdated();
    }

    /**
     * Resets the overlay to inactive state.
     */
    public void reset() {
        this.active = false;
        this.state = TransportState.IDLE;
        this.currentCharge = 0;
        this.requiredCharge = TransportData.DEFAULT_CHARGE_TIME;
        this.destinationName = "";
        this.distance = 0;
        this.ticksSinceLastUpdate = 0;
        refreshTextCache();
    }

    /**
     * Returns whether the overlay is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Ticks the overlay to handle timeouts when updates stop arriving.
     */
    public void tick() {
        if (!active) {
            return;
        }
        ticksSinceLastUpdate++;
        if (ticksSinceLastUpdate > SERVER_UPDATE_TIMEOUT_TICKS) {
            reset();
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        UIScaleManager.update();

        // Update scaled dimensions for responsiveness
        scaledBarWidth = UIScaleManager.scale(BAR_WIDTH);
        scaledBarHeight = UIScaleManager.scale(BAR_HEIGHT);
        scaledBarBorder = UIScaleManager.scale(BAR_BORDER);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        // Calculate center position (slightly above middle)
        int centerX = screenWidth / 2;
        int centerY = (int) (screenHeight * 0.4);

        // Get colors from state
        int primaryColor = state.getPrimaryColor();
        int secondaryColor = state.getSecondaryColor();
        int borderColor = (BACKGROUND_ALPHA << 24) | (primaryColor & 0xFFFFFF);
        int backgroundColor = (BACKGROUND_ALPHA << 24) | 0x000000;

        // Render background panel
        int panelWidth = scaledBarWidth + UIScaleManager.scale(40);
        int panelHeight = UIScaleManager.scale(60);
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, borderColor, backgroundColor);
        int panelCenterX = panelX + panelWidth / 2;

        // Render state text (top center of panel)
        String stateText = truncateToWidth(font, cachedStateText, panelWidth - UIScaleManager.scale(12));
        int stateTextWidth = UIScaleManager.getScaledStringWidth(font, stateText);
        UIScaleManager.drawScaledString(graphics, font, stateText, panelCenterX - stateTextWidth / 2, panelY + UIScaleManager.scale(6), primaryColor, true);

        // Render progress bar (center of panel)
        int barX = panelCenterX - scaledBarWidth / 2;
        int barY = panelY + UIScaleManager.scale(22);
        renderProgressBar(graphics, barX, barY, scaledBarWidth, scaledBarHeight, primaryColor, secondaryColor);

        // Render percentage text (below progress bar)
        String percentText = truncateToWidth(font, cachedPercentText, panelWidth - UIScaleManager.scale(12));
        int percentTextWidth = UIScaleManager.getScaledStringWidth(font, percentText);
        UIScaleManager.drawScaledString(graphics, font, percentText, panelCenterX - percentTextWidth / 2, barY + scaledBarHeight + UIScaleManager.scale(4), 0xFFFFFF, true);

        // Render destination text (bottom of panel)
        if (!cachedDestinationText.isEmpty()) {
            String destText = truncateToWidth(font, cachedDestinationText, panelWidth - UIScaleManager.scale(12));
            int destTextWidth = UIScaleManager.getScaledStringWidth(font, destText);
            UIScaleManager.drawScaledString(graphics, font, destText, panelCenterX - destTextWidth / 2,
                panelY + panelHeight - UIScaleManager.scale(14), secondaryColor, true);
        }

        // Render action hint at bottom center of screen
        renderActionHint(graphics, font, screenWidth, screenHeight, primaryColor);
    }

    /**
     * Renders a panel with border.
     */
    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, int borderColor, int bgColor) {
        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border (scaled)
        graphics.fill(x, y, x + width, y + scaledBarBorder, borderColor);  // Top
        graphics.fill(x, y + height - scaledBarBorder, x + width, y + height, borderColor);  // Bottom
        graphics.fill(x, y, x + scaledBarBorder, y + height, borderColor);  // Left
        graphics.fill(x + width - scaledBarBorder, y, x + width, y + height, borderColor);  // Right
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    /**
     * Renders the progress bar.
     */
    private void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, int primaryColor, int secondaryColor) {
        int sBorderThin = UIScaleManager.scale(1);
        int sInset = UIScaleManager.scale(2);

        // Background (dark)
        int bgColor = (BACKGROUND_ALPHA << 24) | 0x1a1a1a;
        graphics.fill(x, y, x + width, y + height, bgColor);

        // Border
        int borderColor = (BACKGROUND_ALPHA << 24) | (primaryColor & 0xFFFFFF);
        graphics.fill(x, y, x + width, y + sBorderThin, borderColor);  // Top
        graphics.fill(x, y + height - sBorderThin, x + width, y + height, borderColor);  // Bottom
        graphics.fill(x, y, x + sBorderThin, y + height, borderColor);  // Left
        graphics.fill(x + width - sBorderThin, y, x + width, y + height, borderColor);  // Right

        // Progress fill
        float progress = requiredCharge > 0 ? (float) currentCharge / requiredCharge : 0f;
        progress = Math.min(1f, Math.max(0f, progress));
        int fillWidth = (int) ((width - sInset * 2) * progress);
        if (fillWidth > 0) {
            int fillColor = (0xFF << 24) | (secondaryColor & 0xFFFFFF);
            graphics.fill(x + sInset, y + sInset, x + sInset + fillWidth, y + height - sInset, fillColor);
        }
    }

    /**
     * Renders the action hint at the bottom of the screen.
     */
    private void renderActionHint(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int color) {
        String hint = cachedActionHint;
        if (hint.isEmpty()) {
            return;
        }

        int sPadding = UIScaleManager.scale(4);
        int sVertPadding = UIScaleManager.scale(2);
        int sBottomOffset = UIScaleManager.scale(40);

        int maxWidth = UIScaleManager.getSafeWidth() - sPadding * 2;
        hint = truncateToWidth(font, hint, maxWidth);
        int hintWidth = UIScaleManager.getScaledStringWidth(font, hint);
        int hintX = screenWidth / 2 - hintWidth / 2;
        int hintY = screenHeight - sBottomOffset;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        int lineHeight = UIScaleManager.getScaledLineHeight();
        hintX = Math.max(safeLeft, Math.min(hintX, safeRight - hintWidth));
        hintY = Math.max(safeTop, Math.min(hintY, safeBottom - lineHeight - sVertPadding));

        // Background for hint
        int bgColor = (BACKGROUND_ALPHA << 24) | 0x000000;
        graphics.fill(hintX - sPadding, hintY - sVertPadding, hintX + hintWidth + sPadding, hintY + lineHeight + sVertPadding, bgColor);

        UIScaleManager.drawScaledString(graphics, font, hint, hintX, hintY, color, true);
    }

    /**
     * Gets the state display text.
     */
    private String getStateText() {
        return switch (state) {
            case IDLE -> "READY";
            case CHARGING -> "CHARGING";
            case ACTIVE -> "TELEPORTING";
            case ERROR -> "ERROR";
            case COOLDOWN -> "COOLDOWN";
            case LOCKED -> "LOCKED";
        };
    }

    /**
     * Gets the action hint text.
     */
    private String getActionHint() {
        return switch (state) {
            case IDLE -> "Stand still to charge";
            case CHARGING -> "Stay on pad...";
            case ACTIVE -> "";
            case ERROR -> "No destination found";
            case COOLDOWN -> "Please wait...";
            case LOCKED -> "Access denied";
        };
    }

    /**
     * Gets the translatable state component.
     */
    public Component getStateComponent() {
        return Component.translatable("transport.devmod.state." + state.getSerializedName());
    }

    /**
     * Updates countdown display from a TransportCountdownPayload.
     */
    public void updateCountdown(@Nonnull TransportCountdownPayload payload) {
        this.active = true;
        this.state = payload.isUrgent() ? TransportState.ERROR : TransportState.CHARGING;
        this.currentCharge = payload.totalSeconds() - payload.secondsRemaining();
        this.requiredCharge = payload.totalSeconds();
        this.destinationName = payload.secondsRemaining() + "s";
        refreshTextCache();
        markUpdated();
    }

    /**
     * Updates party status display from a TransportPartyStatusPayload.
     */
    public void updatePartyStatus(@Nonnull TransportPartyStatusPayload payload) {
        this.active = !payload.isComplete() && !payload.isCancelled();
        this.state = switch (payload.phase()) {
            case TransportPartyStatusPayload.PHASE_COUNTDOWN -> TransportState.CHARGING;
            case TransportPartyStatusPayload.PHASE_TELEPORTING -> TransportState.ACTIVE;
            case TransportPartyStatusPayload.PHASE_WAITING -> TransportState.CHARGING;
            case TransportPartyStatusPayload.PHASE_COMPLETE -> TransportState.ACTIVE;
            case TransportPartyStatusPayload.PHASE_CANCELLED -> TransportState.ERROR;
            default -> TransportState.IDLE;
        };
        this.currentCharge = payload.arrivedCount();
        this.requiredCharge = payload.expectedCount();
        this.destinationName = "Party: " + payload.arrivedCount() + "/" + payload.expectedCount();
        refreshTextCache();
        markUpdated();
    }

    private void markUpdated() {
        ticksSinceLastUpdate = 0;
    }

    private void refreshTextCache() {
        cachedStateText = getStateText();
        cachedActionHint = getActionHint();
        int percent = requiredCharge > 0 ? (currentCharge * 100) / requiredCharge : 0;
        cachedPercentText = percent + "%";
        if (destinationName.isEmpty()) {
            cachedDestinationText = "";
        } else if (distance > 0) {
            cachedDestinationText = destinationName + " (" + distance + "m)";
        } else {
            cachedDestinationText = destinationName;
        }
    }
}

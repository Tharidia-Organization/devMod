package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.contracts.ContractSyncPayload;

@OnlyIn(Dist.CLIENT)
public class ContractHudOverlay implements LayeredDraw.Layer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractHudOverlay.class);

    public static final ContractHudOverlay INSTANCE = new ContractHudOverlay();

    // Active contract data (synced from server)
    private final List<ContractSyncPayload.ContractData> activeContracts = new ArrayList<>();
    private float totalMultiplier = 1.0f;

    // Animation state
    private float pulsePhase = 0f;
    private long lastViolationTime = 0;
    private static final long VIOLATION_FLASH_DURATION_MS = 500;

    // Position constants
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int ICON_SIZE = 10;

    private ContractHudOverlay() {}

    /**
     * Update active contracts from server sync.
     */
    public void onContractSync(ContractSyncPayload payload) {
        activeContracts.clear();
        activeContracts.addAll(payload.contracts());
        totalMultiplier = payload.totalMultiplier();

        // Check for newly violated contracts
        for (var contract : payload.contracts()) {
            if (contract.violated()) {
                lastViolationTime = System.currentTimeMillis();
                break;
            }
        }

        LOGGER.debug("[ContractHUD] Synced {} contracts ({}x total)",
            activeContracts.size(), totalMultiplier);
    }

    /**
     * Clear all contracts (quest end).
     */
    public void clear() {
        activeContracts.clear();
        totalMultiplier = 1.0f;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, @Nonnull DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (activeContracts.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Don't render during screens
        if (mc.screen != null) return;

        // Get font with null check
        Font font = mc.font;
        if (font == null) return;

        int screenWidth = graphics.guiWidth();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);

        // Update pulse animation
        pulsePhase += partialTicks * 0.1f;
        if (pulsePhase > 2 * Math.PI) pulsePhase -= (float) (2 * Math.PI);

        int sPadding = UIScaleManager.scale(PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int sIconSize = UIScaleManager.scale(ICON_SIZE);

        // Calculate panel width based on longest contract name
        int maxWidth = 0;
        int maxMultiplierWidth = 0;
        for (var contract : activeContracts) {
            if (contract == null) continue;
            String nameKey = contract.nameKey();
            String translatedName = Component.translatable(nameKey).getString();
            int width = UIScaleManager.getScaledStringWidth(font, Objects.requireNonNull(translatedName, "Translated name cannot be null"));
            if (width > maxWidth) maxWidth = width;
            String multiplierText = String.format("%.1fx", contract.multiplier());
            maxMultiplierWidth = Math.max(maxMultiplierWidth, UIScaleManager.getScaledStringWidth(font, multiplierText));
        }
        int panelWidth = maxWidth + sIconSize + sPadding * 3 + maxMultiplierWidth + UIScaleManager.scale(6);
        panelWidth = Math.min(panelWidth, UIScaleManager.getSafeWidth());

        // Position in top-right
        int x = screenWidth - panelWidth - sPadding;
        int y = sPadding;
        int panelHeight = sPadding * 2 + activeContracts.size() * sLineHeight + sLineHeight;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        x = Math.max(safeLeft, Math.min(x, safeRight - panelWidth));
        y = Math.max(safeTop, Math.min(y, safeBottom - panelHeight));

        // Draw panel background
        graphics.fill(x - sPadding, y - sPadding,
            x + panelWidth, y + panelHeight,
            OverlayTheme.Panel.BG_STANDARD);

        // Draw header
        String headerText = "BLOOD CONTRACTS";
        UIScaleManager.drawScaledString(graphics, font, headerText, x, y, OverlayTheme.Contract.HEADER, true);
        y += sLineHeight + UIScaleManager.scale(2);

        // Violation flash effect
        boolean flashViolation = System.currentTimeMillis() - lastViolationTime < VIOLATION_FLASH_DURATION_MS;

        // Draw each contract
        for (var contract : activeContracts) {
            if (contract == null) continue;
            renderContract(graphics, font, contract, x, y, panelWidth, sPadding, sLineHeight, sIconSize, flashViolation);
            y += sLineHeight;
        }

        // Draw total multiplier
        y += UIScaleManager.scale(4);
        graphics.fill(x, y - UIScaleManager.scale(2), x + panelWidth - sPadding, y - UIScaleManager.scale(1),
            OverlayTheme.Contract.SEPARATOR);

        String totalText = String.format("TOTAL: %.1fx", totalMultiplier);
        int totalColor = totalMultiplier >= 2.0f ? OverlayTheme.Contract.MULTIPLIER_HIGH :
                        totalMultiplier >= 1.5f ? OverlayTheme.Contract.MULTIPLIER_MED : OverlayTheme.Contract.MULTIPLIER_LOW;

        // Pulse effect on high multipliers
        if (totalMultiplier >= 2.0f) {
            float pulse = (float) Math.sin(pulsePhase) * 0.3f + 0.7f;
            int alpha = (int) (pulse * 255);
            totalColor = (alpha << 24) | (totalColor & DesignTokens.Mask.RGB);
        }

        UIScaleManager.drawScaledString(graphics, font, totalText, x, y + UIScaleManager.scale(2), totalColor, true);
    }

    /**
     * Render a single contract line.
     */
    private void renderContract(@Nonnull GuiGraphics graphics, @Nonnull Font font,
                                @Nonnull ContractSyncPayload.ContractData contract,
                                int x, int y, int panelWidth, int padding,
                                int lineHeight, int iconSize,
                                boolean flashViolation) {

        int color = contract.color();
        int alpha = 255;

        // Violated contracts get strikethrough and dim color
        if (contract.violated()) {
            alpha = flashViolation ? 128 + (int)(Math.sin(System.currentTimeMillis() * 0.01) * 64) : 128;
            color = OverlayTheme.stripAlpha(OverlayTheme.Contract.VIOLATED);
        }

        int fullColor = (alpha << 24) | (color & DesignTokens.Mask.RGB);

        // Draw tier icon (colored square)
        graphics.fill(x, y + 1, x + iconSize, y + iconSize + 1, fullColor);

        // Draw contract name
        String nameKey = contract.nameKey();
        String name = Objects.requireNonNull(
            Component.translatable(nameKey).getString(),
            "Contract name cannot be null");
        String multiplierText = Objects.requireNonNull(
            String.format("%.1fx", contract.multiplier()),
            "Multiplier text cannot be null");
        int multiplierWidth = UIScaleManager.getScaledStringWidth(font, multiplierText);
        int nameX = x + iconSize + UIScaleManager.scale(4);
        int maxNameWidth = Math.max(0, panelWidth - padding * 2 - iconSize - multiplierWidth - UIScaleManager.scale(6));
        name = truncateToWidth(font, name, maxNameWidth);

        if (contract.violated()) {
            // Strikethrough for violated
            UIScaleManager.drawScaledString(graphics, font, name, nameX, y, OverlayTheme.Contract.VIOLATED, false);
            int nameWidth = UIScaleManager.getScaledStringWidth(font, name);
            graphics.fill(nameX, y + 4, nameX + nameWidth, y + 5, OverlayTheme.Contract.STRIKETHROUGH);
        } else {
            UIScaleManager.drawScaledString(graphics, font, name, nameX, y, fullColor, true);
        }

        // Draw multiplier on right
        int multiplierX = x + panelWidth - padding - multiplierWidth;

        int multiplierColor = contract.violated() ? OverlayTheme.Contract.VIOLATED_MUTED : OverlayTheme.Contract.MULTIPLIER_TEXT;
        UIScaleManager.drawScaledString(graphics, font, multiplierText, multiplierX, y, multiplierColor, false);
    }

    /**
     * Check if any contracts are active.
     */
    public boolean hasActiveContracts() {
        return !activeContracts.isEmpty();
    }

    /**
     * Get current total multiplier.
     */
    public float getTotalMultiplier() {
        return totalMultiplier;
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

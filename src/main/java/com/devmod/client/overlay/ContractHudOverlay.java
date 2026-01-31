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

        // Calculate panel width based on longest contract name
        int maxWidth = 0;
        for (var contract : activeContracts) {
            if (contract == null) continue;
            String nameKey = contract.nameKey();
            String translatedName = Component.translatable(nameKey).getString();
            int width = font.width(Objects.requireNonNull(translatedName, "Translated name cannot be null"));
            if (width > maxWidth) maxWidth = width;
        }
        int panelWidth = maxWidth + ICON_SIZE + PADDING * 3 + 40; // Extra for multiplier

        // Position in top-right
        int x = screenWidth - panelWidth - PADDING;
        int y = PADDING;

        // Draw panel background
        int panelHeight = PADDING * 2 + activeContracts.size() * LINE_HEIGHT + LINE_HEIGHT; // +1 for total
        graphics.fill(x - PADDING, y - PADDING,
            x + panelWidth, y + panelHeight,
            OverlayTheme.Panel.BG_STANDARD);

        // Draw header
        String headerText = "BLOOD CONTRACTS";
        graphics.drawString(font, headerText, x, y, OverlayTheme.Contract.HEADER, true);
        y += LINE_HEIGHT + 2;

        // Violation flash effect
        boolean flashViolation = System.currentTimeMillis() - lastViolationTime < VIOLATION_FLASH_DURATION_MS;

        // Draw each contract
        for (var contract : activeContracts) {
            if (contract == null) continue;
            renderContract(graphics, font, contract, x, y, flashViolation);
            y += LINE_HEIGHT;
        }

        // Draw total multiplier
        y += 4;
        graphics.fill(x, y - 2, x + panelWidth - PADDING, y - 1, OverlayTheme.Contract.SEPARATOR);

        String totalText = String.format("TOTAL: %.1fx", totalMultiplier);
        int totalColor = totalMultiplier >= 2.0f ? OverlayTheme.Contract.MULTIPLIER_HIGH :
                        totalMultiplier >= 1.5f ? OverlayTheme.Contract.MULTIPLIER_MED : OverlayTheme.Contract.MULTIPLIER_LOW;

        // Pulse effect on high multipliers
        if (totalMultiplier >= 2.0f) {
            float pulse = (float) Math.sin(pulsePhase) * 0.3f + 0.7f;
            int alpha = (int) (pulse * 255);
            totalColor = (alpha << 24) | (totalColor & DesignTokens.Mask.RGB);
        }

        graphics.drawString(font, totalText, x, y + 2, totalColor, true);
    }

    /**
     * Render a single contract line.
     */
    private void renderContract(@Nonnull GuiGraphics graphics, @Nonnull Font font,
                                @Nonnull ContractSyncPayload.ContractData contract,
                                int x, int y, boolean flashViolation) {

        int color = contract.color();
        int alpha = 255;

        // Violated contracts get strikethrough and dim color
        if (contract.violated()) {
            alpha = flashViolation ? 128 + (int)(Math.sin(System.currentTimeMillis() * 0.01) * 64) : 128;
            color = OverlayTheme.stripAlpha(OverlayTheme.Contract.VIOLATED);
        }

        int fullColor = (alpha << 24) | (color & DesignTokens.Mask.RGB);

        // Draw tier icon (colored square)
        graphics.fill(x, y + 1, x + ICON_SIZE, y + ICON_SIZE + 1, fullColor);

        // Draw contract name
        String nameKey = contract.nameKey();
        String name = Objects.requireNonNull(
            Component.translatable(nameKey).getString(),
            "Contract name cannot be null");
        int nameX = x + ICON_SIZE + 4;

        if (contract.violated()) {
            // Strikethrough for violated
            graphics.drawString(font, name, nameX, y, OverlayTheme.Contract.VIOLATED, false);
            int nameWidth = font.width(name);
            graphics.fill(nameX, y + 4, nameX + nameWidth, y + 5, OverlayTheme.Contract.STRIKETHROUGH);
        } else {
            graphics.drawString(font, name, nameX, y, fullColor, true);
        }

        // Draw multiplier on right
        String multiplierText = Objects.requireNonNull(
            String.format("%.1fx", contract.multiplier()),
            "Multiplier text cannot be null");
        int multiplierWidth = font.width(multiplierText);
        int multiplierX = graphics.guiWidth() - PADDING * 2 - multiplierWidth;

        int multiplierColor = contract.violated() ? OverlayTheme.Contract.VIOLATED_MUTED : OverlayTheme.Contract.MULTIPLIER_TEXT;
        graphics.drawString(font, multiplierText, multiplierX, y, multiplierColor, false);
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
}

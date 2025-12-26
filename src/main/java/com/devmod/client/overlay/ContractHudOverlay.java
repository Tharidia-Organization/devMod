package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.contracts.ContractSyncPayload;

/**
 * Client-side HUD overlay for active Blood Contracts.
 *
 * Displays signed contracts in the top-right corner with:
 * - Contract name and tier color
 * - Multiplier value
 * - Strikethrough effect when violated
 * - Total multiplier at bottom
 */
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
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (activeContracts.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Don't render during screens
        if (mc.screen != null) return;

        int screenWidth = graphics.guiWidth();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);

        // Update pulse animation
        pulsePhase += partialTicks * 0.1f;
        if (pulsePhase > 2 * Math.PI) pulsePhase -= (float) (2 * Math.PI);

        // Calculate panel width based on longest contract name
        int maxWidth = 0;
        for (var contract : activeContracts) {
            int width = mc.font.width(Component.translatable(contract.nameKey()));
            if (width > maxWidth) maxWidth = width;
        }
        int panelWidth = maxWidth + ICON_SIZE + PADDING * 3 + 40; // Extra for multiplier

        // Position in top-right
        int x = screenWidth - panelWidth - PADDING;
        int y = PADDING;

        // Draw panel background
        int panelHeight = PADDING * 2 + activeContracts.size() * LINE_HEIGHT + LINE_HEIGHT; // +1 for total
        int bgAlpha = 180;
        graphics.fill(x - PADDING, y - PADDING,
            x + panelWidth, y + panelHeight,
            (bgAlpha << 24) | 0x1A1A1A);

        // Draw header
        String headerText = "BLOOD CONTRACTS";
        int headerColor = 0xFF8800;
        graphics.drawString(mc.font, headerText, x, y, headerColor, true);
        y += LINE_HEIGHT + 2;

        // Violation flash effect
        boolean flashViolation = System.currentTimeMillis() - lastViolationTime < VIOLATION_FLASH_DURATION_MS;

        // Draw each contract
        for (var contract : activeContracts) {
            renderContract(graphics, mc, contract, x, y, flashViolation);
            y += LINE_HEIGHT;
        }

        // Draw total multiplier
        y += 4;
        graphics.fill(x, y - 2, x + panelWidth - PADDING, y - 1, 0x44FFFFFF); // Separator line

        String totalText = String.format("TOTAL: %.1fx", totalMultiplier);
        int totalColor = totalMultiplier >= 2.0f ? 0xFF4444 :
                        totalMultiplier >= 1.5f ? 0xFFAA00 : 0xAAFFAA;

        // Pulse effect on high multipliers
        if (totalMultiplier >= 2.0f) {
            float pulse = (float) Math.sin(pulsePhase) * 0.3f + 0.7f;
            int alpha = (int) (pulse * 255);
            totalColor = (alpha << 24) | (totalColor & 0xFFFFFF);
        }

        graphics.drawString(mc.font, totalText, x, y + 2, totalColor, true);
    }

    /**
     * Render a single contract line.
     */
    private void renderContract(GuiGraphics graphics, Minecraft mc,
                                ContractSyncPayload.ContractData contract,
                                int x, int y, boolean flashViolation) {

        int color = contract.color();
        int alpha = 255;

        // Violated contracts get strikethrough and dim color
        if (contract.violated()) {
            alpha = flashViolation ? 128 + (int)(Math.sin(System.currentTimeMillis() * 0.01) * 64) : 128;
            color = 0x888888;
        }

        int fullColor = (alpha << 24) | (color & 0xFFFFFF);

        // Draw tier icon (colored square)
        graphics.fill(x, y + 1, x + ICON_SIZE, y + ICON_SIZE + 1, fullColor);

        // Draw contract name
        String name = Component.translatable(contract.nameKey()).getString();
        int nameX = x + ICON_SIZE + 4;

        if (contract.violated()) {
            // Strikethrough for violated
            graphics.drawString(mc.font, name, nameX, y, 0xFF888888, false);
            int nameWidth = mc.font.width(name);
            graphics.fill(nameX, y + 4, nameX + nameWidth, y + 5, 0xFFFF4444);
        } else {
            graphics.drawString(mc.font, name, nameX, y, fullColor, true);
        }

        // Draw multiplier on right
        String multiplierText = String.format("%.1fx", contract.multiplier());
        int multiplierWidth = mc.font.width(multiplierText);
        int multiplierX = graphics.guiWidth() - PADDING * 2 - multiplierWidth;

        int multiplierColor = contract.violated() ? 0xFF666666 : 0xFFFFFF;
        graphics.drawString(mc.font, multiplierText, multiplierX, y, multiplierColor, false);
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

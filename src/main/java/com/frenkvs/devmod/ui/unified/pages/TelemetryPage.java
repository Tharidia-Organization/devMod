package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Telemetry settings page - analytics export and summaries.
 */
public class TelemetryPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    private String statusMessage = "";
    private long statusDisplayTime = 0;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.TELEMETRY;
    }

    @Override
    public String getTitle() {
        return "Telemetry & Analytics";
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int currentY = y;

        // === Quick Stats Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Session Summary");
        currentY += ROW_HEIGHT + 4;

        // Weapon summaries count
        List<String> weaponSummaries = TelemetryService.INSTANCE.getWeaponSummaries();
        graphics.drawString(font, "Weapons tracked: " + weaponSummaries.size(), x, currentY,
            UIConstants.Text.PRIMARY, false);
        currentY += ROW_HEIGHT;

        // Room summaries count
        List<String> roomSummaries = TelemetryService.INSTANCE.getRoomSummaries();
        graphics.drawString(font, "Rooms explored: " + roomSummaries.size(), x, currentY,
            UIConstants.Text.PRIMARY, false);
        currentY += ROW_HEIGHT;

        // Fight summaries count
        List<String> fightSummaries = TelemetryService.INSTANCE.getFightSummaries();
        graphics.drawString(font, "Fights recorded: " + fightSummaries.size(), x, currentY,
            UIConstants.Text.PRIMARY, false);
        currentY += ROW_HEIGHT + SECTION_SPACING;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === Export Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Export Heatmaps");
        currentY += ROW_HEIGHT + 4;

        // Export buttons - 2 columns
        int col2X = x + BUTTON_WIDTH + 10;

        // Row 1
        boolean deathHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Death Heatmap", deathHovered, false);

        boolean movementHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Movement Map", movementHovered, false);
        currentY += BUTTON_HEIGHT + 4;

        // Row 2
        boolean campingHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Camping Spots", campingHovered, false);

        boolean stuckHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Stuck Points", stuckHovered, false);
        currentY += BUTTON_HEIGHT + 4;

        // Row 3
        boolean aggroHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Aggro Drops", aggroHovered, false);

        boolean kitingHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, col2X, currentY, BUTTON_WIDTH, BUTTON_HEIGHT, "Kiting Paths", kitingHovered, false);
        currentY += BUTTON_HEIGHT + SECTION_SPACING;

        // Separator
        AxiomRenderer.drawSeparator(graphics, x, currentY, width);
        currentY += SECTION_SPACING;

        // === Dashboard Section ===
        AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Advanced Dashboard");
        currentY += ROW_HEIGHT + 4;

        // Open full dashboard button
        boolean dashboardHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, BUTTON_WIDTH + 40, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, BUTTON_WIDTH + 40, BUTTON_HEIGHT,
            "Open Full Dashboard [J]", dashboardHovered, false);
        currentY += BUTTON_HEIGHT + 8;

        // Status message
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusDisplayTime < 3000) {
            graphics.drawString(font, statusMessage, x, currentY, UIConstants.Status.SUCCESS, false);
            currentY += ROW_HEIGHT;
        }

        // Hint
        currentY += 4;
        AxiomRenderer.drawHint(graphics, font, x, currentY, "Exports save to run/telemetry/ folder");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0) return false;

        int y = contentY + ROW_HEIGHT + 4; // Skip section header
        y += ROW_HEIGHT * 3; // Skip stats rows
        y += SECTION_SPACING + SECTION_SPACING + ROW_HEIGHT + 4; // Skip to export section

        int col2X = contentX + BUTTON_WIDTH + 10;

        // Export buttons - Row 1
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportDeathHeatmap();
            showStatus("Death heatmap exported!");
            return true;
        }
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportMovementHeatmap();
            showStatus("Movement map exported!");
            return true;
        }
        y += BUTTON_HEIGHT + 4;

        // Row 2
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportCampingHeatmap();
            showStatus("Camping spots exported!");
            return true;
        }
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportStuckHeatmap();
            showStatus("Stuck points exported!");
            return true;
        }
        y += BUTTON_HEIGHT + 4;

        // Row 3
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportAggroDropHeatmap();
            showStatus("Aggro drops exported!");
            return true;
        }
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportKitingHeatmap();
            showStatus("Kiting paths exported!");
            return true;
        }
        y += BUTTON_HEIGHT + SECTION_SPACING + SECTION_SPACING + ROW_HEIGHT + 4;

        // Dashboard button
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, BUTTON_WIDTH + 40, BUTTON_HEIGHT)) {
            Minecraft.getInstance().setScreen(new com.frenkvs.devmod.TelemetryDashboardScreen(null));
            return true;
        }

        return false;
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusDisplayTime = System.currentTimeMillis();
    }

    @Override
    public int getContentHeight() {
        return 350;
    }
}

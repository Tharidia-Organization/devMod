package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.unified.SettingsCategory;
import com.frenkvs.devmod.ui.unified.SettingsPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Telemetry settings page - analytics export and summaries.
 * Uses responsive button layout that adapts to available width.
 */
public class TelemetryPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;

    private String statusMessage = "";
    private long statusDisplayTime = 0;

    // Layout state cached from render for mouseClicked
    private boolean lastUseOneColumn = false;
    private int lastEffectiveButtonWidth = BUTTON_WIDTH;
    private int lastCol2X = 0;

    @Override
    public SettingsCategory getCategory() {
        return SettingsCategory.TELEMETRY;
    }

    @Override
    public String getTitle() {
        return "Telemetry & Analytics";
    }

    @Override
    public void render(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
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

        // Calculate responsive button layout
        int buttonGap = 10;
        int minButtonWidth = 100;
        int effectiveButtonWidth;
        int col2X;
        boolean useOneColumn = width < (minButtonWidth * 2 + buttonGap);

        if (useOneColumn) {
            // Single column layout for narrow screens
            effectiveButtonWidth = Math.min(width, BUTTON_WIDTH);
            col2X = x; // Not used in single column
        } else {
            // Two column layout
            effectiveButtonWidth = Math.min(BUTTON_WIDTH, (width - buttonGap) / 2);
            col2X = x + effectiveButtonWidth + buttonGap;
        }

        // Cache layout state for mouseClicked
        this.lastUseOneColumn = useOneColumn;
        this.lastEffectiveButtonWidth = effectiveButtonWidth;
        this.lastCol2X = col2X;

        // Row 1
        boolean deathHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Death Heatmap", deathHovered, false);

        if (!useOneColumn) {
            boolean movementHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Movement Map", movementHovered, false);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            boolean movementHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Movement Map", movementHovered, false);
            currentY += BUTTON_HEIGHT + 4;
        }

        // Row 2
        boolean campingHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Camping Spots", campingHovered, false);

        if (!useOneColumn) {
            boolean stuckHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Stuck Points", stuckHovered, false);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            boolean stuckHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Stuck Points", stuckHovered, false);
            currentY += BUTTON_HEIGHT + 4;
        }

        // Row 3
        boolean aggroHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
        AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Aggro Drops", aggroHovered, false);

        if (!useOneColumn) {
            boolean kitingHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Kiting Paths", kitingHovered, false);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            boolean kitingHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT);
            AxiomRenderer.drawButton(graphics, font, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, "Kiting Paths", kitingHovered, false);
            currentY += BUTTON_HEIGHT + 4;
        }
        currentY += SECTION_SPACING - 4;

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

        // Use cached layout state from render
        int btnWidth = lastEffectiveButtonWidth;
        int col2X = lastCol2X;
        boolean oneCol = lastUseOneColumn;

        int y = contentY + ROW_HEIGHT + 4; // Skip section header
        y += ROW_HEIGHT * 3; // Skip stats rows
        y += SECTION_SPACING + SECTION_SPACING + ROW_HEIGHT + 4; // Skip to export section

        // Export buttons - use responsive layout
        // Death Heatmap (always row 1, col 1)
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportDeathHeatmap();
            showStatus("Death heatmap exported!");
            return true;
        }
        if (!oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportMovementHeatmap();
            showStatus("Movement map exported!");
            return true;
        }
        y += BUTTON_HEIGHT + 4;

        // Movement (if single column, it's on its own row)
        if (oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportMovementHeatmap();
            showStatus("Movement map exported!");
            return true;
        }
        if (oneCol) y += BUTTON_HEIGHT + 4;

        // Camping Spots
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportCampingHeatmap();
            showStatus("Camping spots exported!");
            return true;
        }
        if (!oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportStuckHeatmap();
            showStatus("Stuck points exported!");
            return true;
        }
        y += BUTTON_HEIGHT + 4;

        // Stuck Points (if single column)
        if (oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportStuckHeatmap();
            showStatus("Stuck points exported!");
            return true;
        }
        if (oneCol) y += BUTTON_HEIGHT + 4;

        // Aggro Drops
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportAggroDropHeatmap();
            showStatus("Aggro drops exported!");
            return true;
        }
        if (!oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, col2X, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportKitingHeatmap();
            showStatus("Kiting paths exported!");
            return true;
        }
        y += BUTTON_HEIGHT + 4;

        // Kiting Paths (if single column)
        if (oneCol && AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth, BUTTON_HEIGHT)) {
            TelemetryService.INSTANCE.exportKitingHeatmap();
            showStatus("Kiting paths exported!");
            return true;
        }
        if (oneCol) y += BUTTON_HEIGHT + 4;

        y += SECTION_SPACING - 4 + SECTION_SPACING + ROW_HEIGHT + 4;

        // Dashboard button
        if (AxiomRenderer.isMouseOver((int) mouseX, (int) mouseY, contentX, y, btnWidth + 40, BUTTON_HEIGHT)) {
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

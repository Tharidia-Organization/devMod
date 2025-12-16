package com.frenkvs.devmod.ui.unified.pages;

import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
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
    // Buttons
    private final EditorButton deathButton = new EditorButton("tele-death", "Death Heatmap");
    private final EditorButton movementButton = new EditorButton("tele-movement", "Movement Map");
    private final EditorButton campingButton = new EditorButton("tele-camping", "Camping Spots");
    private final EditorButton stuckButton = new EditorButton("tele-stuck", "Stuck Points");
    private final EditorButton aggroButton = new EditorButton("tele-aggro", "Aggro Drops");
    private final EditorButton kitingButton = new EditorButton("tele-kiting", "Kiting Paths");
    private final EditorButton dashboardButton = new EditorButton("tele-dashboard", "Open Full Dashboard [J]");

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

        // Row 1
        deathButton.onClick(() -> {
            TelemetryService.INSTANCE.exportDeathHeatmap();
            showStatus("Death heatmap exported!");
        });
        deathButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

        if (!useOneColumn) {
            movementButton.onClick(() -> {
                TelemetryService.INSTANCE.exportMovementHeatmap();
                showStatus("Movement map exported!");
            });
            movementButton.render(graphics, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            movementButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            currentY += BUTTON_HEIGHT + 4;
        }

        // Row 2
        campingButton.onClick(() -> {
            TelemetryService.INSTANCE.exportCampingHeatmap();
            showStatus("Camping spots exported!");
        });
        campingButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

        if (!useOneColumn) {
            stuckButton.onClick(() -> {
                TelemetryService.INSTANCE.exportStuckHeatmap();
                showStatus("Stuck points exported!");
            });
            stuckButton.render(graphics, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            stuckButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            currentY += BUTTON_HEIGHT + 4;
        }

        // Row 3
        aggroButton.onClick(() -> {
            TelemetryService.INSTANCE.exportAggroDropHeatmap();
            showStatus("Aggro drops exported!");
        });
        aggroButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

        if (!useOneColumn) {
            kitingButton.onClick(() -> {
                TelemetryService.INSTANCE.exportKitingHeatmap();
                showStatus("Kiting paths exported!");
            });
            kitingButton.render(graphics, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }
        currentY += BUTTON_HEIGHT + 4;

        if (useOneColumn) {
            kitingButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
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
        dashboardButton.onClick(() -> {
            Minecraft.getInstance().setScreen(new com.frenkvs.devmod.TelemetryDashboardScreen(null));
        });
        dashboardButton.render(graphics, x, currentY, BUTTON_WIDTH + 40, BUTTON_HEIGHT, mouseX, mouseY);
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
        boolean handled = false;
        handled |= deathButton.mouseClicked(mouseX, mouseY, button);
        handled |= movementButton.mouseClicked(mouseX, mouseY, button);
        handled |= campingButton.mouseClicked(mouseX, mouseY, button);
        handled |= stuckButton.mouseClicked(mouseX, mouseY, button);
        handled |= aggroButton.mouseClicked(mouseX, mouseY, button);
        handled |= kitingButton.mouseClicked(mouseX, mouseY, button);
        handled |= dashboardButton.mouseClicked(mouseX, mouseY, button);
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        handled |= deathButton.mouseReleased(mouseX, mouseY, button);
        handled |= movementButton.mouseReleased(mouseX, mouseY, button);
        handled |= campingButton.mouseReleased(mouseX, mouseY, button);
        handled |= stuckButton.mouseReleased(mouseX, mouseY, button);
        handled |= aggroButton.mouseReleased(mouseX, mouseY, button);
        handled |= kitingButton.mouseReleased(mouseX, mouseY, button);
        handled |= dashboardButton.mouseReleased(mouseX, mouseY, button);
        return handled;
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

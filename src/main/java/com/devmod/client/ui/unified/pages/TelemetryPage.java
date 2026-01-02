package com.devmod.client.ui.unified.pages;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.scroll.Scrollbar;
import com.devmod.client.ui.unified.SettingsCategory;
import com.devmod.client.ui.unified.SettingsPage;
import com.devmod.telemetry.TelemetryService;
import com.devmod.util.I18n;

public class TelemetryPage implements SettingsPage {

    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_SPACING = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int SCROLLBAR_WIDTH = 6;

    // Scrolling state
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private int visibleHeight = 0;
    private int totalContentHeight = 0;
    private boolean isDraggingScrollbar = false;
    private boolean showScrollbar = false;
    private int lastContentX, lastContentY, lastContentWidth, lastContentHeight;

    private String statusMessage = "";
    private long statusDisplayTime = 0;

    // Buttons
    private final EditorButton deathButton = new EditorButton("tele-death", "Death Heatmap");
    private final EditorButton movementButton = new EditorButton("tele-movement", "Movement Map");
    private final EditorButton campingButton = new EditorButton("tele-camping", "Camping Spots");
    private final EditorButton stuckButton = new EditorButton("tele-stuck", "Stuck Points");
    private final EditorButton aggroButton = new EditorButton("tele-aggro", "Aggro Drops");
    private final EditorButton kitingButton = new EditorButton("tele-kiting", "Kiting Paths");
    private final EditorButton dashboardButton = new EditorButton("tele-dashboard", "Open Full Dashboard");

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
        lastContentX = x;
        lastContentY = y;
        lastContentWidth = width;
        lastContentHeight = height;
        visibleHeight = height;

        boolean showStatus = shouldShowStatus();
        int buttonGap = 10;
        int minButtonWidth = 100;

        boolean useOneColumn = shouldUseOneColumn(width, buttonGap, minButtonWidth);
        totalContentHeight = calculateContentHeight(useOneColumn, showStatus);
        maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
        showScrollbar = totalContentHeight > visibleHeight;

        if (showScrollbar) {
            int effectiveWidth = Math.max(0, width - SCROLLBAR_WIDTH - 8);
            boolean useOneColumnWithScrollbar = shouldUseOneColumn(effectiveWidth, buttonGap, minButtonWidth);
            if (useOneColumnWithScrollbar != useOneColumn) {
                useOneColumn = useOneColumnWithScrollbar;
                totalContentHeight = calculateContentHeight(useOneColumn, showStatus);
                maxScrollOffset = Math.max(0, totalContentHeight - visibleHeight);
            }
        }

        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        int currentY = y - scrollOffset;

        graphics.enableScissor(x, y, x + width, y + height);
        try {

            // === Quick Stats Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Session Summary");
            currentY += ROW_HEIGHT + 4;

            // Weapon summaries count
            List<String> weaponSummaries = TelemetryService.INSTANCE.getWeaponSummaries();
            graphics.drawString(font, "Weapons tracked: " + weaponSummaries.size(), x, currentY,
                DesignTokens.Text.PRIMARY, false);
            currentY += ROW_HEIGHT;

            // Room summaries count
            List<String> roomSummaries = TelemetryService.INSTANCE.getRoomSummaries();
            graphics.drawString(font, "Rooms explored: " + roomSummaries.size(), x, currentY,
                DesignTokens.Text.PRIMARY, false);
            currentY += ROW_HEIGHT;

            // Fight summaries count
            List<String> fightSummaries = TelemetryService.INSTANCE.getFightSummaries();
            graphics.drawString(font, "Fights recorded: " + fightSummaries.size(), x, currentY,
                DesignTokens.Text.PRIMARY, false);
            currentY += ROW_HEIGHT + SECTION_SPACING;

            // Separator
            AxiomRenderer.drawSeparator(graphics, x, currentY, width);
            currentY += SECTION_SPACING;

            // === Export Section ===
            AxiomRenderer.drawSectionHeader(graphics, font, x, currentY, "Export Heatmaps");
            currentY += ROW_HEIGHT + 4;

            // Calculate responsive button layout
            int effectiveButtonWidth;
            int col2X;
            int effectiveWidth = showScrollbar ? Math.max(0, width - SCROLLBAR_WIDTH - 8) : width;

            if (useOneColumn) {
                // Single column layout for narrow screens
                effectiveButtonWidth = Math.min(effectiveWidth, BUTTON_WIDTH);
                col2X = x; // Not used in single column
            } else {
                // Two column layout
                effectiveButtonWidth = Math.min(BUTTON_WIDTH, (effectiveWidth - buttonGap) / 2);
                col2X = x + effectiveButtonWidth + buttonGap;
            }

            // Row 1
            deathButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH,
                "devmod.telemetry.exported_heatmap.death"));
            deathButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

            if (!useOneColumn) {
                movementButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT,
                    "devmod.telemetry.exported_heatmap.movement"));
                movementButton.render(graphics, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            }
            currentY += BUTTON_HEIGHT + 4;

            if (useOneColumn) {
                movementButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
                currentY += BUTTON_HEIGHT + 4;
            }

            // Row 2
            campingButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING,
                "devmod.telemetry.exported_heatmap.camping"));
            campingButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

            if (!useOneColumn) {
                stuckButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK,
                    "devmod.telemetry.exported_heatmap.stuck"));
                stuckButton.render(graphics, col2X, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
            }
            currentY += BUTTON_HEIGHT + 4;

            if (useOneColumn) {
                stuckButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);
                currentY += BUTTON_HEIGHT + 4;
            }

            // Row 3
            aggroButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP,
                "devmod.telemetry.exported_heatmap.aggro_drop"));
            aggroButton.render(graphics, x, currentY, effectiveButtonWidth, BUTTON_HEIGHT, mouseX, mouseY);

            if (!useOneColumn) {
                kitingButton.onClick(() -> invokeExport(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING,
                    "devmod.telemetry.exported_heatmap.kiting"));
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
                ActionRegistry.invoke(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.UI));
            });
            dashboardButton.render(graphics, x, currentY, BUTTON_WIDTH + 40, BUTTON_HEIGHT, mouseX, mouseY);
            currentY += BUTTON_HEIGHT + 8;

            // Status message
            if (showStatus) {
                graphics.drawString(font, statusMessage, x, currentY, DesignTokens.Semantic.SUCCESS, false);
                currentY += ROW_HEIGHT;
            }

            // Hint
            currentY += 4;
            AxiomRenderer.drawHint(graphics, font, x, currentY, "Exports save to run/telemetry/ folder");
        } finally {
            graphics.disableScissor();
        }

        if (showScrollbar) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH - 2, y, SCROLLBAR_WIDTH, height);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int contentX, int contentY, int contentWidth) {
        if (button != 0 || !isMouseOverContent(mouseX, mouseY)) return false;

        if (showScrollbar) {
            int scrollbarX = contentX + contentWidth - SCROLLBAR_WIDTH - 2;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 2) {
                isDraggingScrollbar = true;
                float visibleRatio = (float) visibleHeight / totalContentHeight;
                int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
                int trackHeight = lastContentHeight - thumbHeight;
                if (trackHeight > 0) {
                    float clickRatio = (float) (mouseY - contentY - thumbHeight / 2.0f) / trackHeight;
                    clickRatio = Math.max(0, Math.min(1, clickRatio));
                    scrollOffset = (int)(maxScrollOffset * clickRatio);
                }
                return true;
            }
        }
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
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
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

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScrollOffset > 0) {
            float visibleRatio = (float) visibleHeight / totalContentHeight;
            int thumbHeight = Math.max(20, (int) (lastContentHeight * visibleRatio));
            int trackHeight = lastContentHeight - thumbHeight;
            if (trackHeight > 0) {
                float dragRatio = (float) (mouseY - lastContentY - thumbHeight / 2.0f) / trackHeight;
                dragRatio = Math.max(0, Math.min(1, dragRatio));
                scrollOffset = (int)(maxScrollOffset * dragRatio);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!showScrollbar || maxScrollOffset <= 0 || !isMouseOverContent(mouseX, mouseY)) {
            return false;
        }
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        return true;
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusDisplayTime = System.currentTimeMillis();
    }

    private void invokeExport(String actionId, String statusKey) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
        showStatus(I18n.translate(statusKey).getString());
    }

    @Override
    public int getContentHeight() {
        return calculateContentHeight(true, true);
    }

    private boolean shouldShowStatus() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() - statusDisplayTime < 3000;
    }

    private boolean shouldUseOneColumn(int width, int buttonGap, int minButtonWidth) {
        return width < (minButtonWidth * 2 + buttonGap);
    }

    private int calculateContentHeight(boolean useOneColumn, boolean showStatus) {
        int height = 0;
        height += ROW_HEIGHT + 4; // Session Summary header
        height += ROW_HEIGHT * 3; // Summary rows
        height += SECTION_SPACING; // After summary
        height += SECTION_SPACING; // Separator + spacing

        height += ROW_HEIGHT + 4; // Export header
        int exportRows = useOneColumn ? 6 : 3;
        height += exportRows * (BUTTON_HEIGHT + 4);
        height += SECTION_SPACING - 4;
        height += SECTION_SPACING; // Separator + spacing

        height += ROW_HEIGHT + 4; // Dashboard header
        height += BUTTON_HEIGHT + 8; // Dashboard button
        if (showStatus) {
            height += ROW_HEIGHT;
        }
        height += 4; // Spacing before hint
        height += 20; // Hint
        return height;
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int barWidth, int height) {
        Scrollbar.render(graphics, x, y, barWidth, height,
            scrollOffset, totalContentHeight, visibleHeight,
            false, isDraggingScrollbar);
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return mouseX >= lastContentX && mouseX <= lastContentX + lastContentWidth
            && mouseY >= lastContentY && mouseY <= lastContentY + lastContentHeight;
    }
}

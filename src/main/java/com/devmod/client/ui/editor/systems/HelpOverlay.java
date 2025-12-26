package com.devmod.client.ui.editor.systems;

import java.util.List;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;

public final class HelpOverlay extends BaseOverlay {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final int TITLE_Y = UIConstants.Spacing.LG; // 12
    private static final int SEPARATOR_Y = TITLE_Y + UIConstants.Spacing.XL; // 28
    private static final int SEPARATOR_HEIGHT = 1;
    private static final int SECTION_START_Y = SEPARATOR_Y + UIConstants.Spacing.LG; // 40
    private static final int SECTION_BLOCK_HEIGHT = 120;
    private static final int COLUMN_PADDING = UIConstants.Spacing.XL; // 16
    private static final int COLUMN_GAP = UIConstants.Spacing.XL; // 16
    private static final int COLUMN_TOTAL_GAP = COLUMN_PADDING * 2 + COLUMN_GAP; // 48
    private static final int SECTION_TITLE_GAP = 14;
    private static final int ENTRY_LINE_HEIGHT = UIConstants.Spacing.LG; // 12
    private static final int ENTRY_DESC_OFFSET = 110;
    private static final int CLOSE_HINT_OFFSET = UIConstants.Spacing.XL + UIConstants.Spacing.SM; // 20

    // Help content sections
    private static final List<HelpSection> SECTIONS = List.of(
        new HelpSection("Navigation", List.of(
            new HelpEntry("Tab / Shift+Tab", "Switch between tabs"),
            new HelpEntry("1-9", "Quick select tab"),
            new HelpEntry("Scroll / Arrow Keys", "Navigate sliders"),
            new HelpEntry("Home / End", "Jump to min/max value"),
            new HelpEntry("Escape", "Close editor / overlays")
        )),
        new HelpSection("Editing & Apply", List.of(
            new HelpEntry("Ctrl+Z / Ctrl+Shift+Z", "Undo / Redo"),
            new HelpEntry("Ctrl+S", "Apply changes"),
            new HelpEntry("Ctrl+Enter", "Quick Apply (APPLY mode)"),
            new HelpEntry("F5", "Toggle Preview/Apply mode"),
            new HelpEntry("Backspace", "Reset slider to default"),
            new HelpEntry("M", "Toggle Multi-Edit panel")
        )),
        new HelpSection("Data & Presets", List.of(
            new HelpEntry("Ctrl+E / Ctrl+I", "Export / Import"),
            new HelpEntry("Ctrl+P", "Open Presets"),
            new HelpEntry("Ctrl+F", "Focus preset search"),
            new HelpEntry("Delete", "Delete hovered preset"),
            new HelpEntry("Click mode badge", "Scope + mode info")
        )),
        new HelpSection("Debug & Overlays", List.of(
            new HelpEntry("F9", "Toggle Debug Overlay"),
            new HelpEntry("F10", "Show grid in Debug Overlay"),
            new HelpEntry("F11", "Show bounds/performance in Debug Overlay"),
            new HelpEntry("F3 + D", "Toggle Dev Panel (debug tab)"),
            new HelpEntry("F1", "Show/hide this help")
        ))
    );

    /**
     * Toggle visibility of the help overlay.
     */
    public void toggle() {
        if (visible) {
            hide();
        } else {
            show();
        }
    }

    // =========================================================================
    // BaseOverlay IMPLEMENTATION
    // =========================================================================

    @Override
    protected int getPanelWidth() {
        return WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return HEIGHT;
    }

    @Override
    public OverlayType getType() {
        return OverlayType.HELP;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Use ACCENT border instead of DEFAULT for help overlay
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, width, height, UIConstants.Border.ACCENT());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        float textScale = Typography.withUiScale(Typography.BODY);

        // Title
        String title = "Help - Keyboard Shortcuts";
        int titleWidth = Math.round(font.width(Objects.requireNonNull(title)) * textScale);
        Typography.drawText(graphics, font, title, x + (width - titleWidth) / 2, y + ScaledCoord.scaleDim(TITLE_Y),
            UIConstants.Text.TITLE(), textScale);

        // Separator
        int separatorY = y + ScaledCoord.scaleDim(SEPARATOR_Y);
        int separatorBottom = y + ScaledCoord.scaleDim(SEPARATOR_Y + SEPARATOR_HEIGHT);
        graphics.fill(x + ScaledCoord.scaleDim(COLUMN_PADDING), separatorY,
            x + width - ScaledCoord.scaleDim(COLUMN_PADDING), separatorBottom, UIConstants.Border.DEFAULT());

        // Render sections
        int sectionY = y + ScaledCoord.scaleDim(SECTION_START_Y);
        int columnWidth = (width - ScaledCoord.scaleDim(COLUMN_TOTAL_GAP)) / 2;

        for (int i = 0; i < SECTIONS.size(); i++) {
            HelpSection section = SECTIONS.get(i);
            int sectionX = x + ScaledCoord.scaleDim(COLUMN_PADDING)
                + (i % 2) * (columnWidth + ScaledCoord.scaleDim(COLUMN_GAP));

            if (i >= 2) {
                // Third+ sections go below
                sectionY = y + ScaledCoord.scaleDim(SECTION_START_Y + SECTION_BLOCK_HEIGHT);
                sectionX = x + ScaledCoord.scaleDim(COLUMN_PADDING)
                    + ((i - 2) % 2) * (columnWidth + ScaledCoord.scaleDim(COLUMN_GAP));
            }

            // Section title
            Typography.drawText(graphics, font, Objects.requireNonNull(section.title()), sectionX, sectionY,
                UIConstants.Accent.CYAN(), textScale);

            int entryY = sectionY + ScaledCoord.scaleDim(SECTION_TITLE_GAP);
            for (HelpEntry entry : section.entries()) {
                // Key
                Typography.drawText(graphics, font, Objects.requireNonNull(entry.key()), sectionX, entryY,
                    UIConstants.Text.VALUE(), textScale);
                // Description
                Typography.drawText(graphics, font, Objects.requireNonNull(entry.description()),
                    sectionX + ScaledCoord.scaleDim(ENTRY_DESC_OFFSET), entryY,
                    UIConstants.Text.SECONDARY(), textScale);
                entryY += ScaledCoord.scaleDim(ENTRY_LINE_HEIGHT);
            }
        }

        // Close hint
        String closeHint = "Press F1 or Escape to close";
        int hintWidth = Math.round(font.width(Objects.requireNonNull(closeHint)) * textScale);
        Typography.drawText(graphics, font, closeHint, x + (width - hintWidth) / 2,
            y + height - ScaledCoord.scaleDim(CLOSE_HINT_OFFSET), UIConstants.Text.MUTED(), textScale);
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        // F1 also closes the help overlay
        if (keyCode == GLFW.GLFW_KEY_F1) {
            hide();
            return true;
        }
        return true; // Consume all keys when help is visible
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        return true; // Click anywhere to close
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                          int panelX, int panelY, int panelW, int panelH) {
        // Click inside panel also closes
        hide();
        return true;
    }

    // Helper records
    private record HelpSection(String title, List<HelpEntry> entries) {}
    private record HelpEntry(String key, String description) {}
}

package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.BaseOverlay;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

/**
 * Help overlay showing keyboard shortcuts and controls.
 * Extends BaseOverlay for consistent modal behavior.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.8
 */
public final class HelpOverlay extends BaseOverlay {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

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
        Typography.drawText(graphics, font, title, x + (width - titleWidth) / 2, y + ScaledCoord.scaleDim(12),
            UIConstants.Text.TITLE(), textScale);

        // Separator
        graphics.fill(x + ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(28),
               x + width - ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(29), UIConstants.Border.DEFAULT());

        // Render sections
        int sectionY = y + ScaledCoord.scaleDim(40);
        int columnWidth = (width - ScaledCoord.scaleDim(48)) / 2;

        for (int i = 0; i < SECTIONS.size(); i++) {
            HelpSection section = SECTIONS.get(i);
            int sectionX = x + ScaledCoord.scaleDim(16) + (i % 2) * (columnWidth + ScaledCoord.scaleDim(16));

            if (i >= 2) {
                // Third+ sections go below
                sectionY = y + ScaledCoord.scaleDim(40 + 120);
                sectionX = x + ScaledCoord.scaleDim(16) + ((i - 2) % 2) * (columnWidth + ScaledCoord.scaleDim(16));
            }

            // Section title
            Typography.drawText(graphics, font, Objects.requireNonNull(section.title()), sectionX, sectionY,
                UIConstants.Accent.CYAN(), textScale);

            int entryY = sectionY + ScaledCoord.scaleDim(14);
            for (HelpEntry entry : section.entries()) {
                // Key
                Typography.drawText(graphics, font, Objects.requireNonNull(entry.key()), sectionX, entryY,
                    UIConstants.Text.VALUE(), textScale);
                // Description
                Typography.drawText(graphics, font, Objects.requireNonNull(entry.description()),
                    sectionX + ScaledCoord.scaleDim(110), entryY,
                    UIConstants.Text.SECONDARY(), textScale);
                entryY += ScaledCoord.scaleDim(12);
            }
        }

        // Close hint
        String closeHint = "Press F1 or Escape to close";
        int hintWidth = Math.round(font.width(Objects.requireNonNull(closeHint)) * textScale);
        Typography.drawText(graphics, font, closeHint, x + (width - hintWidth) / 2,
            y + height - ScaledCoord.scaleDim(20), UIConstants.Text.MUTED(), textScale);
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

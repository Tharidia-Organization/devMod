package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.AxiomRenderer;
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
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.8
 */
public final class HelpOverlay {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private boolean visible = false;

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

    public void show() {
        visible = true;
    }

    public void hide() {
        visible = false;
    }

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        if (!visible) return;

        float textScale = Typography.withUiScale(Typography.BODY);

        // Dark overlay
        g.fill(0, 0, screenWidth, screenHeight, UIConstants.Background.OVERLAY);

        // Center panel
        int panelW = ScaledCoord.scaleDim(WIDTH);
        int panelH = ScaledCoord.scaleDim(HEIGHT);
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;

        // Panel background
        g.fill(x, y, x + panelW, y + panelH, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(g, x, y, panelW, panelH, UIConstants.Border.ACCENT);

        // Title
        String title = "Help - Keyboard Shortcuts";
        int titleWidth = Math.round(font.width(Objects.requireNonNull(title)) * textScale);
        Typography.drawText(g, font, title, x + (panelW - titleWidth) / 2, y + ScaledCoord.scaleDim(12),
            UIConstants.Text.TITLE, textScale);

        // Separator
        g.fill(x + ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(28),
               x + panelW - ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(29), UIConstants.Border.DEFAULT);

        // Render sections
        int sectionY = y + ScaledCoord.scaleDim(40);
        int columnWidth = (panelW - ScaledCoord.scaleDim(48)) / 2;

        for (int i = 0; i < SECTIONS.size(); i++) {
            HelpSection section = SECTIONS.get(i);
            int sectionX = x + ScaledCoord.scaleDim(16) + (i % 2) * (columnWidth + ScaledCoord.scaleDim(16));

            if (i >= 2) {
                // Third+ sections go below
                sectionY = y + ScaledCoord.scaleDim(40 + 120);
                sectionX = x + ScaledCoord.scaleDim(16) + ((i - 2) % 2) * (columnWidth + ScaledCoord.scaleDim(16));
            }

            // Section title
            Typography.drawText(g, font, Objects.requireNonNull(section.title()), sectionX, sectionY,
                UIConstants.Accent.CYAN, textScale);

            int entryY = sectionY + ScaledCoord.scaleDim(14);
            for (HelpEntry entry : section.entries()) {
                // Key
                Typography.drawText(g, font, Objects.requireNonNull(entry.key()), sectionX, entryY,
                    UIConstants.Text.VALUE, textScale);
                // Description
                Typography.drawText(g, font, Objects.requireNonNull(entry.description()),
                    sectionX + ScaledCoord.scaleDim(110), entryY,
                    UIConstants.Text.SECONDARY, textScale);
                entryY += ScaledCoord.scaleDim(12);
            }
        }

        // Close hint
        String closeHint = "Press F1 or Escape to close";
        int hintWidth = Math.round(font.width(Objects.requireNonNull(closeHint)) * textScale);
        Typography.drawText(g, font, closeHint, x + (panelW - hintWidth) / 2,
            y + panelH - ScaledCoord.scaleDim(20), UIConstants.Text.MUTED, textScale);
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_F1) {
            hide();
            return true;
        }

        return true; // Consume all keys when help is visible
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!visible) return false;

        // Click anywhere to close
        hide();
        return true;
    }

    // Helper records
    private record HelpSection(String title, List<HelpEntry> entries) {}
    private record HelpEntry(String key, String description) {}
}

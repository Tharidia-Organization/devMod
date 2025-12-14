package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.AxiomRenderer;
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
            new HelpEntry("Home / End", "Jump to min/max value")
        )),
        new HelpSection("Editing", List.of(
            new HelpEntry("Ctrl+Z", "Undo last change"),
            new HelpEntry("Ctrl+Shift+Z", "Redo last change"),
            new HelpEntry("Ctrl+S", "Apply changes"),
            new HelpEntry("Backspace", "Reset slider to default")
        )),
        new HelpSection("General", List.of(
            new HelpEntry("Escape", "Close editor"),
            new HelpEntry("F1", "Show/hide this help"),
            new HelpEntry("Alt+D", "Toggle dev panel"),
            new HelpEntry("Click mode badge", "Toggle Preview/Apply mode")
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

        // Dark overlay
        g.fill(0, 0, screenWidth, screenHeight, UIConstants.Background.OVERLAY);

        // Center panel
        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;

        // Panel background
        g.fill(x, y, x + WIDTH, y + HEIGHT, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(g, x, y, WIDTH, HEIGHT, UIConstants.Border.ACCENT);

        // Title
        String title = "Help - Keyboard Shortcuts";
        int titleWidth = font.width(Objects.requireNonNull(title));
        g.drawString(font, title, x + (WIDTH - titleWidth) / 2, y + 12, UIConstants.Text.TITLE, false);

        // Separator
        g.fill(x + 16, y + 28, x + WIDTH - 16, y + 29, UIConstants.Border.DEFAULT);

        // Render sections
        int sectionY = y + 40;
        int columnWidth = (WIDTH - 48) / 2;

        for (int i = 0; i < SECTIONS.size(); i++) {
            HelpSection section = SECTIONS.get(i);
            int sectionX = x + 16 + (i % 2) * (columnWidth + 16);

            if (i == 2) {
                // Third section goes below
                sectionY = y + 40 + 120;
                sectionX = x + 16;
            }

            // Section title
            g.drawString(font, Objects.requireNonNull(section.title()), sectionX, sectionY,
                        UIConstants.Accent.CYAN, false);

            int entryY = sectionY + 14;
            for (HelpEntry entry : section.entries()) {
                // Key
                g.drawString(font, Objects.requireNonNull(entry.key()), sectionX, entryY,
                            UIConstants.Text.VALUE, false);
                // Description
                g.drawString(font, Objects.requireNonNull(entry.description()), sectionX + 100, entryY,
                            UIConstants.Text.SECONDARY, false);
                entryY += 12;
            }
        }

        // Close hint
        String closeHint = "Press F1 or Escape to close";
        int hintWidth = font.width(Objects.requireNonNull(closeHint));
        g.drawString(font, closeHint, x + (WIDTH - hintWidth) / 2, y + HEIGHT - 20,
                    UIConstants.Text.MUTED, false);
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

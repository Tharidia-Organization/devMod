package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Pannello destro con:
 * - Toggle rapidi per overlay/tool
 * - Accesso rapido agli editor
 * - Info sessione corrente
 */
public class QuickToolsPanel implements HubPanel {

    private final int x, y, width, height;
    private final Font font;
    private final TestingHubState state;
    private final BiConsumer<ToolType, Boolean> onToolToggled;
    private final Consumer<EditorType> onEditorOpened;

    // Tool richiesti dal test corrente (evidenziati)
    private Set<ToolType> requiredTools = EnumSet.noneOf(ToolType.class);

    // Layout
    private static final int PADDING = 10;
    private static final int SECTION_HEADER_HEIGHT = 16;
    private static final int TOGGLE_ROW_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 22;
    private static final int SESSION_LINE_HEIGHT = 12;

    public QuickToolsPanel(int x, int y, int width, int height, Font font,
                           TestingHubState state,
                           BiConsumer<ToolType, Boolean> onToolToggled,
                           Consumer<EditorType> onEditorOpened) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = font;
        this.state = state;
        this.onToolToggled = onToolToggled;
        this.onEditorOpened = onEditorOpened;
    }

    public void highlightRequired(Set<ToolType> tools) {
        this.requiredTools = tools != null ? tools : EnumSet.noneOf(ToolType.class);
    }

    public void clearHighlights() {
        this.requiredTools = EnumSet.noneOf(ToolType.class);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL);

        // Bordo sinistro separatore
        graphics.fill(x, y, x + 1, y + height, UIConstants.Border.SEPARATOR);

        int contentY = y + PADDING;
        int contentX = x + PADDING;
        int contentWidth = width - PADDING * 2;

        // === SEZIONE OVERLAYS ===
        graphics.drawString(font, "OVERLAYS", contentX, contentY, UIConstants.Text.TITLE, false);
        contentY += SECTION_HEADER_HEIGHT;

        for (ToolType tool : ToolType.values()) {
            boolean enabled = tool.isEnabled();
            boolean required = requiredTools.contains(tool);
            boolean hovered = isInBounds(mouseX, mouseY, contentX, contentY, contentWidth, TOGGLE_ROW_HEIGHT - 2);

            renderToolToggle(graphics, contentX, contentY, contentWidth, tool, enabled, required, hovered);
            contentY += TOGGLE_ROW_HEIGHT;
        }

        contentY += 8;
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 12;

        // === SEZIONE EDITORS ===
        graphics.drawString(font, "EDITORS", contentX, contentY, UIConstants.Text.TITLE, false);
        contentY += SECTION_HEADER_HEIGHT;

        for (EditorType editor : EditorType.values()) {
            boolean hovered = isInBounds(mouseX, mouseY, contentX, contentY, contentWidth, BUTTON_HEIGHT);
            renderEditorButton(graphics, contentX, contentY, contentWidth, editor, hovered);
            contentY += BUTTON_HEIGHT + 4;
        }

        contentY += 8;
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 12;

        // === SEZIONE SESSION ===
        graphics.drawString(font, "SESSION", contentX, contentY, UIConstants.Text.TITLE, false);
        contentY += SECTION_HEADER_HEIGHT;

        renderSessionInfo(graphics, contentX, contentY, contentWidth);
    }

    private void renderToolToggle(GuiGraphics graphics, int rx, int ry, int rwidth,
                                   ToolType tool, boolean enabled, boolean required, boolean hovered) {
        // Background hover
        if (hovered) {
            graphics.fill(rx - 2, ry, rx + rwidth + 2, ry + TOGGLE_ROW_HEIGHT - 2, UIConstants.Background.HOVER);
        }

        // Indicatore "required" (bordo sinistro giallo)
        if (required && !enabled) {
            graphics.fill(rx - 2, ry, rx, ry + TOGGLE_ROW_HEIGHT - 2, UIConstants.Status.WARNING);
        }

        // Hotkey badge
        String hotkey = "[" + tool.getHotkey() + "]";
        graphics.drawString(font, hotkey, rx, ry + 5, UIConstants.Text.MUTED, false);

        // Label
        int labelX = rx + font.width(hotkey) + 4;
        int labelColor = required && !enabled ? UIConstants.Status.WARNING : UIConstants.Text.PRIMARY;

        String label = tool.getLabel();
        int maxLabelWidth = rwidth - font.width(hotkey) - 50;
        if (font.width(label) > maxLabelWidth) {
            while (font.width(label + "..") > maxLabelWidth && label.length() > 3) {
                label = label.substring(0, label.length() - 1);
            }
            label += "..";
        }
        graphics.drawString(font, label, labelX, ry + 5, labelColor, false);

        // Toggle switch (semplificato)
        int toggleWidth = 32;
        int toggleHeight = 14;
        int toggleX = rx + rwidth - toggleWidth;
        int toggleY = ry + 3;

        int toggleBg = enabled ? UIConstants.Status.SUCCESS : UIConstants.Background.INPUT;
        graphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, toggleBg);
        AxiomRenderer.drawBorder(graphics, toggleX, toggleY, toggleWidth, toggleHeight, UIConstants.Border.DEFAULT);

        // Toggle text
        String toggleText = enabled ? "ON" : "OFF";
        int textColor = enabled ? UIConstants.Text.WHITE : UIConstants.Text.MUTED;
        int textWidth = font.width(toggleText);
        graphics.drawString(font, toggleText, toggleX + (toggleWidth - textWidth) / 2, toggleY + 3, textColor, false);

        // Checkmark se required e enabled
        if (required && enabled) {
            graphics.drawString(font, "v", toggleX - 10, ry + 5, UIConstants.Status.SUCCESS, false);
        }
    }

    private void renderEditorButton(GuiGraphics graphics, int rx, int ry, int rwidth,
                                     EditorType editor, boolean hovered) {
        // Background
        int bgColor = hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
        graphics.fill(rx, ry, rx + rwidth, ry + BUTTON_HEIGHT, bgColor);

        // Border
        int borderColor = hovered ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, rx, ry, rwidth, BUTTON_HEIGHT, borderColor);

        // Hotkey hint
        String hotkey = "[" + editor.getHotkey() + "]";
        graphics.drawString(font, hotkey, rx + 6, ry + 7, UIConstants.Text.MUTED, false);

        // Label
        int labelX = rx + 6 + font.width(hotkey) + 4;
        graphics.drawString(font, editor.getLabel(), labelX, ry + 7, UIConstants.Text.PRIMARY, false);

        // Arrow
        int arrowColor = hovered ? UIConstants.Text.ACCENT : UIConstants.Text.MUTED;
        graphics.drawString(font, ">", rx + rwidth - 12, ry + 7, arrowColor, false);
    }

    private void renderSessionInfo(GuiGraphics graphics, int rx, int ry, int rwidth) {
        // Tester name
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        if (font.width(tester) > rwidth) {
            tester = tester.substring(0, Math.min(tester.length(), 20)) + "...";
        }
        graphics.drawString(font, tester, rx, ry, UIConstants.Text.SECONDARY, false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Session duration
        long durationMs = state.getSessionDuration();
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        long seconds = (durationMs % 60000) / 1000;
        String time = String.format("Time: %02d:%02d:%02d", hours, minutes, seconds);
        graphics.drawString(font, time, rx, ry, UIConstants.Text.SECONDARY, false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Progress
        int passed = TestingSession.INSTANCE.getPassedTests();
        int failed = TestingSession.INSTANCE.getFailedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        String progress = String.format("Tests: %d/%d", passed + failed, total);
        graphics.drawString(font, progress, rx, ry, UIConstants.Text.SECONDARY, false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Pass rate
        if (passed + failed > 0) {
            float passRate = (float) passed / (passed + failed) * 100;
            String rate = String.format("Pass Rate: %.0f%%", passRate);
            int rateColor = passRate >= 80 ? UIConstants.Status.SUCCESS :
                           passRate >= 50 ? UIConstants.Status.WARNING : UIConstants.Status.ERROR;
            graphics.drawString(font, rate, rx, ry, rateColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        int contentY = y + PADDING + SECTION_HEADER_HEIGHT;
        int contentX = x + PADDING;
        int contentWidth = width - PADDING * 2;

        // Check tool toggles
        for (ToolType tool : ToolType.values()) {
            if (isInBounds(mx, my, contentX, contentY, contentWidth, TOGGLE_ROW_HEIGHT - 2)) {
                boolean newState = !tool.isEnabled();
                tool.setEnabled(newState);
                onToolToggled.accept(tool, newState);
                return true;
            }
            contentY += TOGGLE_ROW_HEIGHT;
        }

        // Skip separator
        contentY += 8 + 12 + SECTION_HEADER_HEIGHT;

        // Check editor buttons
        for (EditorType editor : EditorType.values()) {
            if (isInBounds(mx, my, contentX, contentY, contentWidth, BUTTON_HEIGHT)) {
                onEditorOpened.accept(editor);
                return true;
            }
            contentY += BUTTON_HEIGHT + 4;
        }

        return false;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isInBounds(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public int getX() { return x; }
    @Override
    public int getY() { return y; }
    @Override
    public int getWidth() { return width; }
    @Override
    public int getHeight() { return height; }
}

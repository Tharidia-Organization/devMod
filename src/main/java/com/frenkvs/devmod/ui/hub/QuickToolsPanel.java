package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.testing.TestingSession;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Right panel with:
 * - Quick toggles for overlays/tools
 * - Quick access to editors
 * - Current session info
 */
@SuppressWarnings("null") // Minecraft APIs lack null annotations
public class QuickToolsPanel implements HubPanel {

    private final int x, y, width, height;
    private final Font font;
    private final TestingHubState state;
    private final BiConsumer<ToolType, Boolean> onToolToggled;
    private final Consumer<EditorType> onEditorOpened;

    // Tools required by current test (highlighted)
    private Set<ToolType> requiredTools = EnumSet.noneOf(ToolType.class);

    // Layout
    private static final int PADDING = 10;
    private static final int SECTION_HEADER_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = UIConstants.Size.BUTTON_HEIGHT;
    private static final int BUTTON_GAP = 4;
    private static final int SESSION_LINE_HEIGHT = 12;

    private final Map<ToolType, EditorButton> toolButtons;
    private final Map<EditorType, EditorButton> editorButtons;

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
        this.toolButtons = buildToolButtons();
        this.editorButtons = buildEditorButtons();
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
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.PANEL());

        // Left border separator
        graphics.fill(x, y, x + 1, y + height, UIConstants.Border.SEPARATOR());

        int contentY = y + PADDING;
        int contentX = x + PADDING;
        int contentWidth = width - PADDING * 2;

        // === OVERLAYS SECTION ===
        contentY = HubSectionHeader.draw(graphics, font, "OVERLAYS", contentX, contentY, SECTION_HEADER_HEIGHT);

        for (ToolType tool : ToolType.values()) {
            boolean enabled = tool.isEnabled();
            boolean required = requiredTools.contains(tool);
            renderToolToggle(graphics, contentX, contentY, contentWidth, tool, enabled, required, mouseX, mouseY);
            contentY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        contentY += 8;
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 12;

        // === EDITORS SECTION ===
        contentY = HubSectionHeader.draw(graphics, font, "EDITORS", contentX, contentY, SECTION_HEADER_HEIGHT);

        for (EditorType editor : EditorType.values()) {
            renderEditorButton(graphics, contentX, contentY, contentWidth, editor, mouseX, mouseY);
            contentY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        contentY += 8;
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 12;

        // === SESSION SECTION ===
        contentY = HubSectionHeader.draw(graphics, font, "SESSION", contentX, contentY, SECTION_HEADER_HEIGHT);

        renderSessionInfo(graphics, contentX, contentY, contentWidth);
    }

    private void renderToolToggle(GuiGraphics graphics, int rx, int ry, int rwidth,
                                   ToolType tool, boolean enabled, boolean required, int mouseX, int mouseY) {
        EditorButton button = toolButtons.get(tool);
        if (button == null) return;

        // Sync state every frame in case external toggles changed it.
        button.toggled(enabled);
        button.style(required && !enabled ? EditorButton.Style.DANGER : EditorButton.Style.NORMAL);
        button.accent(required && !enabled ? UIConstants.Status.WARNING() : UIConstants.Border.DEFAULT());
        button.render(graphics, rx, ry, rwidth, BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderEditorButton(GuiGraphics graphics, int rx, int ry, int rwidth,
                                     EditorType editor, int mouseX, int mouseY) {
        EditorButton button = editorButtons.get(editor);
        if (button == null) return;

        button.render(graphics, rx, ry, rwidth, BUTTON_HEIGHT, mouseX, mouseY);
    }

    private void renderSessionInfo(GuiGraphics graphics, int rx, int ry, int rwidth) {
        // Tester name
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        if (font.width(tester) > rwidth) {
            tester = tester.substring(0, Math.min(tester.length(), 20)) + "...";
        }
        graphics.drawString(font, tester, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Session duration
        long durationMs = state.getSessionDuration();
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        long seconds = (durationMs % 60000) / 1000;
        String time = String.format("Time: %02d:%02d:%02d", hours, minutes, seconds);
        graphics.drawString(font, time, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Progress
        int passed = TestingSession.INSTANCE.getPassedTests();
        int failed = TestingSession.INSTANCE.getFailedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        String progress = String.format("Tests: %d/%d", passed + failed, total);
        graphics.drawString(font, progress, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Pass rate
        if (passed + failed > 0) {
            float passRate = (float) passed / (passed + failed) * 100;
            String rate = String.format("Pass Rate: %.0f%%", passRate);
            int rateColor = passRate >= 80 ? UIConstants.Status.SUCCESS() :
                           passRate >= 50 ? UIConstants.Status.WARNING() : UIConstants.Status.ERROR();
            graphics.drawString(font, rate, rx, ry, rateColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        for (EditorButton btn : toolButtons.values()) {
            if (btn.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        for (EditorButton btn : editorButtons.values()) {
            if (btn.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        for (EditorButton btn : toolButtons.values()) {
            if (btn.mouseReleased(mx, my, button)) {
                return true;
            }
        }

        for (EditorButton btn : editorButtons.values()) {
            if (btn.mouseReleased(mx, my, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public int getX() { return x; }
    @Override
    public int getY() { return y; }
    @Override
    public int getWidth() { return width; }
    @Override
    public int getHeight() { return height; }

    private Map<ToolType, EditorButton> buildToolButtons() {
        Map<ToolType, EditorButton> buttons = new EnumMap<>(ToolType.class);
        for (ToolType tool : ToolType.values()) {
            EditorButton button = EditorButton.builder("tool-" + tool.name().toLowerCase(), tool.getLabel())
                .hotkeyHint("[" + tool.getHotkey() + "]")
                .toggleable(true)
                .toggled(tool.isEnabled())
                .style(EditorButton.Style.NORMAL)
                .onToggle(enabled -> {
                    tool.setEnabled(enabled);
                    onToolToggled.accept(tool, enabled);
                })
                .build();
            buttons.put(tool, button);
        }
        return buttons;
    }

    private Map<EditorType, EditorButton> buildEditorButtons() {
        Map<EditorType, EditorButton> buttons = new EnumMap<>(EditorType.class);
        for (EditorType editor : EditorType.values()) {
            EditorButton button = EditorButton.builder("editor-" + editor.name().toLowerCase(), editor.getLabel())
                .hotkeyHint("[" + editor.getHotkey() + "]")
                .style(EditorButton.Style.PRIMARY)
                .onClick(() -> onEditorOpened.accept(editor))
                .build();
            buttons.put(editor, button);
        }
        return buttons;
    }
}

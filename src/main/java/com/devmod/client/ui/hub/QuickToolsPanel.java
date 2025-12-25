package com.devmod.client.ui.hub;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.QuestActionPayload;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.editor.components.EditorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Right panel with:
 * - Quick toggles for overlays/tools
 * - Quick access to editors
 * - Current session info
 */

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
    private final Map<QuestActionPayload.Action, EditorButton> enduranceButtons;
    private final EditorButton dashboardButton;

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
        this.enduranceButtons = buildEnduranceButtons();
        this.dashboardButton = EditorButton.builder("dash-open", "Open Telemetry Dashboard")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .icon("\uD83D\uDD0E") // 🔎
            .onClick(this::openDashboard)
            .build();
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
        contentY += SESSION_LINE_HEIGHT * 4 + 12;

        // === ENDURANCE SHORTCUTS ===
        contentY = HubSectionHeader.draw(graphics, font, "ENDURANCE", contentX, contentY, SECTION_HEADER_HEIGHT);
        boolean hasQuest = ClientQuestCache.hasActiveQuest();
        EnduranceQuestState questState = hasQuest && ClientQuestCache.getData() != null
            ? ClientQuestCache.getData().getState()
            : EnduranceQuestState.AVAILABLE;

        for (QuestActionPayload.Action action : QuestActionPayload.Action.values()) {
            EditorButton btn = enduranceButtons.get(action);
            if (btn == null) continue;
            boolean enableBtn = hasQuest;
            // Only allow checkpoint actions when at checkpoint
            if ((action == QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE
                || action == QuestActionPayload.Action.EXIT_AT_CHECKPOINT)
                && questState != EnduranceQuestState.WAVE_COMPLETE) {
                enableBtn = false;
            }
            btn.setEnabled(enableBtn);
            btn.render(graphics, contentX, contentY, contentWidth, BUTTON_HEIGHT, mouseX, mouseY);
            contentY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        contentY += 8;
        AxiomRenderer.drawSeparator(graphics, contentX, contentY, contentWidth);
        contentY += 12;

        // === TELEMETRY SECTION ===
        contentY = HubSectionHeader.draw(graphics, font, "TELEMETRY", contentX, contentY, SECTION_HEADER_HEIGHT);
        dashboardButton.render(graphics, contentX, contentY, contentWidth, BUTTON_HEIGHT, mouseX, mouseY);
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
        Font safeFont = Objects.requireNonNull(font, "font");
        // Tester name
        String tester = "Tester: " + TestingSession.INSTANCE.getTesterName();
        if (safeFont.width(tester) > rwidth) {
            tester = tester.substring(0, Math.min(tester.length(), 20)) + "...";
        }
        graphics.drawString(safeFont, tester, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Session duration
        long durationMs = state.getSessionDuration();
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        long seconds = (durationMs % 60000) / 1000;
        String time = String.format("Time: %02d:%02d:%02d", hours, minutes, seconds);
        graphics.drawString(safeFont, time, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Progress
        int passed = TestingSession.INSTANCE.getPassedTests();
        int failed = TestingSession.INSTANCE.getFailedTests();
        int total = TestingSession.INSTANCE.getTotalTests();
        String progress = String.format("Tests: %d/%d", passed + failed, total);
        graphics.drawString(safeFont, progress, rx, ry, UIConstants.Text.SECONDARY(), false);
        ry += SESSION_LINE_HEIGHT + 2;

        // Pass rate
        if (passed + failed > 0) {
            float passRate = (float) passed / (passed + failed) * 100;
            String rate = String.format("Pass Rate: %.0f%%", passRate);
            int rateColor = passRate >= 80 ? UIConstants.Status.SUCCESS() :
                           passRate >= 50 ? UIConstants.Status.WARNING() : UIConstants.Status.ERROR();
            graphics.drawString(safeFont, rate, rx, ry, rateColor, false);
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

        for (EditorButton btn : enduranceButtons.values()) {
            if (btn.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        if (dashboardButton.mouseClicked(mx, my, button)) {
            return true;
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

        for (EditorButton btn : enduranceButtons.values()) {
            if (btn.mouseReleased(mx, my, button)) {
                return true;
            }
        }

        if (dashboardButton.mouseReleased(mx, my, button)) {
            return true;
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

    private Map<QuestActionPayload.Action, EditorButton> buildEnduranceButtons() {
        Map<QuestActionPayload.Action, EditorButton> buttons = new EnumMap<>(QuestActionPayload.Action.class);

        buttons.put(QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE,
            EditorButton.builder("endurance-next", "Continue to Next Wave")
                .style(EditorButton.Style.PRIMARY)
                .size(EditorButton.Size.MEDIUM)
                .icon("\u27A1") // →
                .onClick(() -> sendQuestAction(QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE))
                .build());

        buttons.put(QuestActionPayload.Action.EXIT_AT_CHECKPOINT,
            EditorButton.builder("endurance-exit", "Exit at Checkpoint")
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .icon("\u23CF") // ⏏
                .onClick(() -> sendQuestAction(QuestActionPayload.Action.EXIT_AT_CHECKPOINT))
                .build());

        buttons.put(QuestActionPayload.Action.ABANDON_QUEST,
            EditorButton.builder("endurance-abandon", "Abandon Quest")
                .style(EditorButton.Style.DANGER)
                .size(EditorButton.Size.MEDIUM)
                .icon("\u274C") // ❌
                .onClick(() -> sendQuestAction(QuestActionPayload.Action.ABANDON_QUEST))
                .build());

        return buttons;
    }

    private void sendQuestAction(QuestActionPayload.Action action) {
        if (!ClientQuestCache.hasActiveQuest()) {
            return;
        }
        ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI, action);
        if (action == QuestActionPayload.Action.CONTINUE_TO_NEXT_WAVE
            || action == QuestActionPayload.Action.CONTINUE_AFTER_DEATH) {
            ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_CONTINUE, context);
            return;
        }
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_EXIT, context.withConfirmed(true));
    }

    private void openDashboard() {
        ActionContext context = ClientActionContexts.forClient(
            ActionOrigin.UI, Minecraft.getInstance().screen);
        ActionRegistry.invoke(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, context);
    }
}

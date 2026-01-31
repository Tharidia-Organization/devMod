package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.condition.DialogCondition;

/**
 * Widget for editing dialog conditions.
 * Provides a dynamic form based on condition type selection.
 * Supports composition (And, Or, Not) for complex conditions.
 *
 * <p>Usage in a Screen:
 * <pre>
 * ConditionEditorWidget conditionEditor = new ConditionEditorWidget(x, y, width, condition -> {
 *     // Handle condition change
 * });
 * addRenderableWidget(conditionEditor);
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class ConditionEditorWidget extends AbstractWidget {

    private static final int COLOR_BG = DesignTokens.Panel.BG;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_LABEL = DesignTokens.Text.SECONDARY;
    private static final int FIELD_HEIGHT = 18;
    private static final int SPACING = 4;

    private final Consumer<DialogCondition> onChange;
    private final List<AbstractWidget> childWidgets = new ArrayList<>();

    private ConditionType selectedType = ConditionType.ALWAYS;
    private CycleButton<ConditionType> typeButton;

    // Parameter fields
    private EditBox param1Field;
    private EditBox param2Field;
    private CycleButton<DialogCondition.Comparison> comparisonButton;

    // Current values
    private String param1 = "";
    private String param2 = "";
    private DialogCondition.Comparison comparison = DialogCondition.Comparison.GREATER_OR_EQUAL;

    // For nested conditions (And, Or, Not)
    private final List<DialogCondition> nestedConditions = new ArrayList<>();
    private EditorButtonWidget addNestedButton;
    private EditorButtonWidget clearNestedButton;
    private EditorButtonWidget debugButton;

    // Debug overlay
    @Nullable
    private ConditionDebugWidget debugWidget;
    private boolean showingDebug = false;

    @SuppressWarnings("this-escape")
    public ConditionEditorWidget(int x, int y, int width, @Nonnull Consumer<DialogCondition> onChange) {
        super(x, y, width, calculateHeight(ConditionType.ALWAYS), Component.empty());
        this.onChange = onChange;
        initWidgets();
    }

    /**
     * Load an existing condition into the editor.
     */
    public void loadCondition(@Nullable DialogCondition condition) {
        nestedConditions.clear();
        if (condition == null) {
            selectedType = ConditionType.ALWAYS;
            param1 = "";
            param2 = "";
            comparison = DialogCondition.Comparison.GREATER_OR_EQUAL;
        } else {
            selectedType = ConditionType.fromCondition(condition);
            extractParams(condition);
        }
        updateWidgets();
    }

    /**
     * Build the current condition from widget values.
     */
    @Nonnull
    public DialogCondition buildCondition() {
        return switch (selectedType) {
            case ALWAYS -> new DialogCondition.Always();
            case FIRST_VISIT -> new DialogCondition.FirstVisit(parseUUIDOrPlaceholder(param1));
            case PERMISSION -> new DialogCondition.HasPermission(parseIntOrDefault(param1, 2));
            case HAS_SELECTED_OPTION -> new DialogCondition.HasSelectedOption(param1.isEmpty() ? "option_id" : param1);
            case QUEST_COMPLETED -> new DialogCondition.QuestCompleted(param1);
            case QUEST_COUNT -> new DialogCondition.QuestCount(parseIntOrDefault(param1, 1), comparison);
            case TIME_OF_DAY -> new DialogCondition.TimeOfDay(
                parseLongOrDefault(param1, 0),
                parseLongOrDefault(param2, 12000)
            );
            case AND -> new DialogCondition.And(List.copyOf(nestedConditions));
            case OR -> new DialogCondition.Or(List.copyOf(nestedConditions));
            case NOT -> nestedConditions.isEmpty()
                ? new DialogCondition.Always()
                : new DialogCondition.Not(nestedConditions.get(0));
        };
    }

    private void initWidgets() {
        int y = getY();
        int fieldWidth = width - 10;

        // Condition type selector
        typeButton = CycleButton.<ConditionType>builder(ConditionType::getDisplayName)
            .withValues(ConditionType.values())
            .withInitialValue(selectedType)
            .create(getX() + 5, y, fieldWidth, FIELD_HEIGHT,
                Component.translatable("gui.devmod.npc.condition.type"),
                (btn, value) -> {
                    selectedType = value;
                    updateWidgets();
                    notifyChange();
                });
        childWidgets.add(typeButton);
        y += FIELD_HEIGHT + SPACING;

        // Parameter fields
        param1Field = createEditBox(getX() + 5, y, fieldWidth, "");
        param1Field.setResponder(s -> { param1 = s; notifyChange(); });
        childWidgets.add(param1Field);
        y += FIELD_HEIGHT + SPACING;

        param2Field = createEditBox(getX() + 5, y, fieldWidth, "");
        param2Field.setResponder(s -> { param2 = s; notifyChange(); });
        childWidgets.add(param2Field);
        y += FIELD_HEIGHT + SPACING;

        // Comparison button for quest_count
        comparisonButton = CycleButton.<DialogCondition.Comparison>builder(c -> Component.literal(c.name()))
            .withValues(DialogCondition.Comparison.values())
            .withInitialValue(comparison)
            .create(getX() + 5, y, fieldWidth, FIELD_HEIGHT,
                Component.translatable("gui.devmod.npc.condition.comparison"),
                (btn, value) -> { comparison = value; notifyChange(); });
        childWidgets.add(comparisonButton);
        y += FIELD_HEIGHT + SPACING;

        // Nested condition buttons
        EditorButton addNested = EditorButton.builder("dialog-condition-add", "+")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::addNestedCondition)
            .build();
        addNestedButton = new EditorButtonWidget(addNested, getX() + 5, y, 30, FIELD_HEIGHT);
        childWidgets.add(addNestedButton);

        EditorButton clearNested = EditorButton.builder("dialog-condition-clear",
                Component.translatable("gui.devmod.npc.condition.clear").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::clearNestedConditions)
            .build();
        clearNestedButton = new EditorButtonWidget(clearNested, getX() + 40, y, 60, FIELD_HEIGHT);
        childWidgets.add(clearNestedButton);

        // Debug button - shows condition structure
        EditorButton debug = EditorButton.builder("dialog-condition-debug", "\uD83D\uDD0D")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.condition.debug.tooltip").getString())
            .onClick(this::toggleDebug)
            .build();
        debugButton = new EditorButtonWidget(debug, getX() + width - 25, getY(), 20, FIELD_HEIGHT);
        childWidgets.add(debugButton);

        updateWidgets();
    }

    private void toggleDebug() {
        showingDebug = !showingDebug;
        if (showingDebug) {
            // Create or update debug widget
            if (debugWidget == null) {
                debugWidget = new ConditionDebugWidget(
                    getX() + width + 10,
                    getY(),
                    200,
                    150
                );
            }
            ConditionDebugWidget widget = debugWidget;
            if (widget != null) {
                widget.setCondition(buildCondition());
            }
        }
    }

    /**
     * Gets the debug widget for rendering by parent screen.
     */
    @Nullable
    public ConditionDebugWidget getDebugWidget() {
        return showingDebug ? debugWidget : null;
    }

    /**
     * Checks if debug overlay is visible.
     */
    public boolean isShowingDebug() {
        return showingDebug;
    }

    private EditBox createEditBox(int x, int y, int width, String hint) {
        EditBox box = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            x, y, width, FIELD_HEIGHT, Component.empty()
        );
        box.setHint(Component.literal(hint));
        box.setBordered(false);
        box.setTextColor(DesignTokens.Text.PRIMARY);
        box.setTextColorUneditable(DesignTokens.Text.MUTED);
        return box;
    }

    private void updateWidgets() {
        // Hide all parameter fields first
        param1Field.visible = false;
        param2Field.visible = false;
        comparisonButton.visible = false;
        addNestedButton.visible = false;
        clearNestedButton.visible = false;

        // Update type button
        typeButton.setValue(selectedType);

        // Show relevant fields based on type
        switch (selectedType) {
            case FIRST_VISIT -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.first_visit.npc_id"));
                param1Field.setValue(param1);
            }
            case PERMISSION -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.permission.level"));
                param1Field.setValue(param1);
            }
            case HAS_SELECTED_OPTION -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.has_selected_option.option_id"));
                param1Field.setValue(param1);
            }
            case QUEST_COMPLETED -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.quest.id"));
                param1Field.setValue(param1);
            }
            case QUEST_COUNT -> {
                param1Field.visible = true;
                comparisonButton.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.quest_count.min"));
                param1Field.setValue(param1);
                comparisonButton.setValue(comparison);
            }
            case TIME_OF_DAY -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.condition.time.min"));
                param2Field.setHint(Component.translatable("gui.devmod.npc.condition.time.max"));
                param1Field.setValue(param1);
                param2Field.setValue(param2);
            }
            case AND, OR -> {
                addNestedButton.visible = true;
                clearNestedButton.visible = true;
            }
            case NOT -> {
                addNestedButton.visible = true;
                clearNestedButton.visible = true;
            }
            case ALWAYS -> {
                // No parameters
            }
        }

        // Update widget height
        this.height = calculateHeight(selectedType);
    }

    private void extractParams(DialogCondition condition) {
        param1 = "";
        param2 = "";
        comparison = DialogCondition.Comparison.GREATER_OR_EQUAL;
        nestedConditions.clear();

        switch (condition) {
            case DialogCondition.FirstVisit fv -> param1 = fv.npcId().toString();
            case DialogCondition.HasPermission hp -> param1 = String.valueOf(hp.opLevel());
            case DialogCondition.HasSelectedOption hso -> param1 = hso.optionId();
            case DialogCondition.QuestCompleted qc -> param1 = qc.questId();
            case DialogCondition.QuestCount qn -> {
                param1 = String.valueOf(qn.minQuests());
                comparison = qn.comparison();
            }
            case DialogCondition.TimeOfDay tod -> {
                param1 = String.valueOf(tod.minTime());
                param2 = String.valueOf(tod.maxTime());
            }
            case DialogCondition.And and -> nestedConditions.addAll(and.conditions());
            case DialogCondition.Or or -> nestedConditions.addAll(or.conditions());
            case DialogCondition.Not not -> nestedConditions.add(not.condition());
            default -> {}
        }
    }

    private void addNestedCondition() {
        // For NOT, only allow one condition
        if (selectedType == ConditionType.NOT && !nestedConditions.isEmpty()) {
            return;
        }
        nestedConditions.add(new DialogCondition.Always());
        notifyChange();
    }

    private void clearNestedConditions() {
        nestedConditions.clear();
        notifyChange();
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(buildCondition());
        }
    }

    private static int calculateHeight(ConditionType type) {
        int rows = switch (type) {
            case ALWAYS -> 1;
            case FIRST_VISIT, PERMISSION, HAS_SELECTED_OPTION, QUEST_COMPLETED -> 2;
            case QUEST_COUNT -> 3;
            case TIME_OF_DAY -> 3;
            case AND, OR, NOT -> 2;
        };
        return rows * (FIELD_HEIGHT + SPACING) + SPACING;
    }

    // Parsing helpers
    private UUID parseUUIDOrPlaceholder(String s) {
        if (s == null || s.isEmpty()) {
            return new UUID(0, 0); // Placeholder for "current NPC"
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    private int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private long parseLongOrDefault(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BG);
        graphics.renderOutline(getX(), getY(), width, height, COLOR_BORDER);

        renderInputBackgrounds(graphics);

        // Render child widgets
        for (AbstractWidget widget : childWidgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        // Render nested conditions count for And/Or/Not
        if (selectedType == ConditionType.AND || selectedType == ConditionType.OR || selectedType == ConditionType.NOT) {
            String countText = "(" + nestedConditions.size() + " conditions)";
            UIScaleManager.drawScaledString(
                graphics,
                net.minecraft.client.Minecraft.getInstance().font,
                countText,
                getX() + 110, getY() + FIELD_HEIGHT + SPACING + 5,
                COLOR_LABEL
            );
        }

        // Render debug widget if visible
        if (showingDebug && debugWidget != null) {
            debugWidget.render(graphics, mouseX, mouseY, partialTick);
        }

        renderButtonTooltips(graphics, mouseX, mouseY);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        for (AbstractWidget widget : childWidgets) {
            if (!widget.visible) {
                continue;
            }
            if (widget instanceof EditBox editBox) {
                AxiomRenderer.drawInputBackground(graphics, editBox.getX(), editBox.getY(), editBox.getWidth(),
                    editBox.getHeight(), editBox.isFocused());
            }
        }
    }

    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (debugButton != null) {
            String tooltip = debugButton.getButton().activeTooltip();
            if (tooltip != null) {
                graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font,
                    Component.literal(tooltip), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check debug widget first
        if (showingDebug && debugWidget != null && debugWidget.isMouseOver(mouseX, mouseY)) {
            return debugWidget.mouseClicked(mouseX, mouseY, button);
        }

        for (AbstractWidget widget : childWidgets) {
            if (widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Forward scroll to debug widget if visible and mouse over
        if (showingDebug && debugWidget != null && debugWidget.isMouseOver(mouseX, mouseY)) {
            return debugWidget.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AbstractWidget widget : childWidgets) {
            if (widget.visible && widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (AbstractWidget widget : childWidgets) {
            if (widget.visible && widget.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Condition types for the editor.
     */
    public enum ConditionType {
        ALWAYS("always", "gui.devmod.npc.condition.always"),
        FIRST_VISIT("first_visit", "gui.devmod.npc.condition.first_visit"),
        PERMISSION("permission", "gui.devmod.npc.condition.permission"),
        HAS_SELECTED_OPTION("has_selected_option", "gui.devmod.npc.condition.has_selected_option"),
        QUEST_COMPLETED("quest_completed", "gui.devmod.npc.condition.quest_completed"),
        QUEST_COUNT("quest_count", "gui.devmod.npc.condition.quest_count"),
        TIME_OF_DAY("time", "gui.devmod.npc.condition.time_of_day"),
        AND("and", "gui.devmod.npc.condition.and"),
        OR("or", "gui.devmod.npc.condition.or"),
        NOT("not", "gui.devmod.npc.condition.not");

        private final String typeId;
        private final String translationKey;

        ConditionType(String typeId, String translationKey) {
            this.typeId = typeId;
            this.translationKey = translationKey;
        }

        public String getTypeId() {
            return typeId;
        }

        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        public static ConditionType fromCondition(DialogCondition condition) {
            return switch (condition) {
                case DialogCondition.Always a -> ALWAYS;
                case DialogCondition.FirstVisit fv -> FIRST_VISIT;
                case DialogCondition.HasPermission hp -> PERMISSION;
                case DialogCondition.HasSelectedOption hso -> HAS_SELECTED_OPTION;
                case DialogCondition.NpcHasEmotion nhe -> ALWAYS; // No UI for emotion yet
                case DialogCondition.QuestCompleted qc -> QUEST_COMPLETED;
                case DialogCondition.QuestCount qn -> QUEST_COUNT;
                case DialogCondition.TimeOfDay tod -> TIME_OF_DAY;
                case DialogCondition.And and -> AND;
                case DialogCondition.Or or -> OR;
                case DialogCondition.Not not -> NOT;
                case DialogCondition.Custom cu -> ALWAYS;
            };
        }
    }
}

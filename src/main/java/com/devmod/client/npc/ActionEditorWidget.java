package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.action.DialogAction;

/**
 * Widget for editing dialog actions.
 * Provides a dynamic form based on action type selection.
 *
 * <p>Usage in a Screen:
 * <pre>
 * ActionEditorWidget actionEditor = new ActionEditorWidget(x, y, width, action -> {
 *     // Handle action change
 * });
 * addRenderableWidget(actionEditor);
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class ActionEditorWidget extends AbstractWidget {

    private static final int COLOR_BG = DesignTokens.Panel.BG;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int FIELD_HEIGHT = 18;
    private static final int SPACING = 4;

    private final Consumer<DialogAction> onChange;
    private final List<AbstractWidget> childWidgets = new ArrayList<>();

    private ActionType selectedType = ActionType.CLOSE;
    private CycleButton<ActionType> typeButton;

    // Parameter fields
    private EditBox param1Field;
    private EditBox param2Field;
    private EditBox param3Field;
    private CycleButton<DialogAction.CommandSource> sourceButton;

    // Current values
    private String param1 = "";
    private String param2 = "";
    private String param3 = "";
    private DialogAction.CommandSource commandSource = DialogAction.CommandSource.NPC;

    @SuppressWarnings("this-escape")
    public ActionEditorWidget(int x, int y, int width, @Nonnull Consumer<DialogAction> onChange) {
        super(x, y, width, calculateHeight(ActionType.CLOSE), Component.empty());
        this.onChange = onChange;
        initWidgets();
    }

    /**
     * Load an existing action into the editor.
     */
    public void loadAction(@Nullable DialogAction action) {
        if (action == null) {
            selectedType = ActionType.CLOSE;
            param1 = "";
            param2 = "";
            param3 = "";
            commandSource = DialogAction.CommandSource.NPC;
        } else {
            selectedType = ActionType.fromAction(action);
            extractParams(action);
        }
        updateWidgets();
    }

    /**
     * Build the current action from widget values.
     */
    @Nonnull
    public DialogAction buildAction() {
        return switch (selectedType) {
            case CLOSE -> new DialogAction.CloseDialog();
            case GOTO -> new DialogAction.GoToNode(param1.isEmpty() ? "start" : param1);
            case COMMAND -> new DialogAction.ExecuteCommand(param1, commandSource);
            case TELEPORT -> new DialogAction.Teleport(
                parseBlockPos(param1),
                null,
                param2.isEmpty() ? null : param2
            );
            case GIVE_ITEM -> new DialogAction.GiveItem(
                parseResourceLocation(param1),
                parseIntOrDefault(param2, 1),
                null
            );
            case PLAY_SOUND -> new DialogAction.PlaySound(
                parseResourceLocation(param1),
                parseFloatOrDefault(param2, 1.0f),
                parseFloatOrDefault(param3, 1.0f)
            );
            case OPEN_GUI -> new DialogAction.OpenGui(param1);
            case SET_VARIABLE -> new DialogAction.SetVariable(
                param1.isEmpty() ? "var" : param1,
                param2
            );
        };
    }

    private void initWidgets() {
        int y = getY();
        int fieldWidth = width - 10;

        // Action type selector
        typeButton = CycleButton.<ActionType>builder(ActionType::getDisplayName)
            .withValues(ActionType.values())
            .withInitialValue(selectedType)
            .create(getX() + 5, y, fieldWidth, FIELD_HEIGHT,
                Component.translatable("gui.devmod.npc.action.type"),
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

        param3Field = createEditBox(getX() + 5, y, fieldWidth, "");
        param3Field.setResponder(s -> { param3 = s; notifyChange(); });
        childWidgets.add(param3Field);
        y += FIELD_HEIGHT + SPACING;

        // Command source button
        sourceButton = CycleButton.<DialogAction.CommandSource>builder(s -> Component.literal(s.name()))
            .withValues(DialogAction.CommandSource.values())
            .withInitialValue(commandSource)
            .create(getX() + 5, y, fieldWidth, FIELD_HEIGHT,
                Component.translatable("gui.devmod.npc.action.command.source"),
                (btn, value) -> { commandSource = value; notifyChange(); });
        childWidgets.add(sourceButton);

        updateWidgets();
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
        param3Field.visible = false;
        sourceButton.visible = false;

        // Update type button
        typeButton.setValue(selectedType);

        // Show relevant fields based on type
        switch (selectedType) {
            case GOTO -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.goto.node_id"));
                param1Field.setValue(param1);
            }
            case COMMAND -> {
                param1Field.visible = true;
                sourceButton.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.command.cmd"));
                param1Field.setValue(param1);
                sourceButton.setValue(commandSource);
            }
            case TELEPORT -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.teleport.pos"));
                param2Field.setHint(Component.translatable("gui.devmod.npc.action.teleport.zone"));
                param1Field.setValue(param1);
                param2Field.setValue(param2);
            }
            case GIVE_ITEM -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.give_item.item"));
                param2Field.setHint(Component.translatable("gui.devmod.npc.action.give_item.count"));
                param1Field.setValue(param1);
                param2Field.setValue(param2);
            }
            case PLAY_SOUND -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param3Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.play_sound.sound"));
                param2Field.setHint(Component.translatable("gui.devmod.npc.action.play_sound.volume"));
                param3Field.setHint(Component.translatable("gui.devmod.npc.action.play_sound.pitch"));
                param1Field.setValue(param1);
                param2Field.setValue(param2);
                param3Field.setValue(param3);
            }
            case OPEN_GUI -> {
                param1Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.open_gui.id"));
                param1Field.setValue(param1);
            }
            case SET_VARIABLE -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.translatable("gui.devmod.npc.action.set_variable.key"));
                param2Field.setHint(Component.translatable("gui.devmod.npc.action.set_variable.value"));
                param1Field.setValue(param1);
                param2Field.setValue(param2);
            }
            case CLOSE -> {
                // No parameters
            }
        }

        // Update widget height
        this.height = calculateHeight(selectedType);
    }

    private void extractParams(DialogAction action) {
        param1 = "";
        param2 = "";
        param3 = "";
        commandSource = DialogAction.CommandSource.NPC;

        switch (action) {
            case DialogAction.GoToNode goTo -> param1 = goTo.nodeId();
            case DialogAction.ExecuteCommand cmd -> {
                param1 = cmd.command();
                commandSource = cmd.source();
            }
            case DialogAction.Teleport tp -> {
                if (tp.position() != null) {
                    param1 = tp.position().getX() + "," + tp.position().getY() + "," + tp.position().getZ();
                }
                param2 = tp.zoneId() != null ? tp.zoneId() : "";
            }
            case DialogAction.GiveItem give -> {
                param1 = give.itemId().toString();
                param2 = String.valueOf(give.count());
            }
            case DialogAction.PlaySound sound -> {
                param1 = sound.soundId().toString();
                param2 = String.valueOf(sound.volume());
                param3 = String.valueOf(sound.pitch());
            }
            case DialogAction.OpenGui gui -> param1 = gui.guiId();
            case DialogAction.SetVariable sv -> {
                param1 = sv.key();
                param2 = sv.value();
            }
            default -> {}
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(buildAction());
        }
    }

    private static int calculateHeight(ActionType type) {
        int rows = switch (type) {
            case CLOSE -> 1;
            case GOTO, OPEN_GUI -> 2;
            case COMMAND, TELEPORT, GIVE_ITEM, SET_VARIABLE -> 3;
            case PLAY_SOUND -> 4;
        };
        return rows * (FIELD_HEIGHT + SPACING) + SPACING;
    }

    // Parsing helpers
    @Nullable
    private net.minecraft.core.BlockPos parseBlockPos(String s) {
        if (s == null || s.isEmpty()) return null;
        List<String> parts = Splitter.on(',').trimResults().splitToList(s);
        if (parts.size() != 3) return null;
        try {
            return new net.minecraft.core.BlockPos(
                Integer.parseInt(parts.get(0)),
                Integer.parseInt(parts.get(1)),
                Integer.parseInt(parts.get(2))
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResourceLocation parseResourceLocation(String s) {
        if (s == null || s.isEmpty()) {
            return ResourceLocation.withDefaultNamespace("air");
        }
        ResourceLocation loc = ResourceLocation.tryParse(s);
        return loc != null ? loc : ResourceLocation.withDefaultNamespace("air");
    }

    private int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private float parseFloatOrDefault(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget widget : childWidgets) {
            if (widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
     * Action types for the editor.
     */
    public enum ActionType {
        CLOSE("close", "gui.devmod.npc.action.close"),
        GOTO("goto", "gui.devmod.npc.action.goto"),
        COMMAND("command", "gui.devmod.npc.action.command"),
        TELEPORT("teleport", "gui.devmod.npc.action.teleport"),
        GIVE_ITEM("give_item", "gui.devmod.npc.action.give_item"),
        PLAY_SOUND("sound", "gui.devmod.npc.action.play_sound"),
        OPEN_GUI("gui", "gui.devmod.npc.action.open_gui"),
        SET_VARIABLE("set_variable", "gui.devmod.npc.action.set_variable");

        private final String typeId;
        private final String translationKey;

        ActionType(String typeId, String translationKey) {
            this.typeId = typeId;
            this.translationKey = translationKey;
        }

        public String getTypeId() {
            return typeId;
        }

        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        public static ActionType fromAction(DialogAction action) {
            return switch (action) {
                case DialogAction.CloseDialog c -> CLOSE;
                case DialogAction.GoToNode g -> GOTO;
                case DialogAction.ExecuteCommand e -> COMMAND;
                case DialogAction.Teleport t -> TELEPORT;
                case DialogAction.GiveItem gi -> GIVE_ITEM;
                case DialogAction.PlaySound ps -> PLAY_SOUND;
                case DialogAction.OpenGui og -> OPEN_GUI;
                case DialogAction.SetVariable sv -> SET_VARIABLE;
                case DialogAction.RememberChoice rc -> SET_VARIABLE; // Similar key/value pattern
                case DialogAction.SetEmotion se -> CLOSE; // Simple action, no special UI
                case DialogAction.Custom cu -> CLOSE;
            };
        }
    }
}

package com.devmod.client.npc;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.DialogLimits;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.action.DialogAction;

/**
 * Popup screen for editing a single dialog option.
 * Allows editing label, icon, action type, and action parameters.
 */
@OnlyIn(Dist.CLIENT)
public class DialogOptionEditorScreen extends Screen {

    // === Colors ===
    private static final int COLOR_PANEL_BG = DesignTokens.Panel.BG;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_LABEL = DesignTokens.Text.SECONDARY;

    // === Layout ===
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 280;
    private static final int PADDING = 10;
    private static final int FIELD_HEIGHT = 20;
    private static final int LABEL_HEIGHT = 12;
    private static final int SPACING = 6;

    // === Action Types ===
    private static final List<ActionType> ACTION_TYPES = List.of(
        ActionType.CLOSE,
        ActionType.GOTO,
        ActionType.COMMAND,
        ActionType.TELEPORT,
        ActionType.GIVE_ITEM,
        ActionType.PLAY_SOUND,
        ActionType.OPEN_GUI
    );

    // === Data ===
    private final Screen parent;
    private final DialogOption originalOption;
    private final Consumer<DialogOption> onSave;

    // === Edited values ===
    private String editedId;
    private String editedLabel;
    private String editedIcon;
    private ActionType selectedActionType;
    private String actionParam1 = "";
    private String actionParam2 = "";
    private String actionParam3 = "";

    // === Widgets ===
    @Nullable private EditBox idField;
    @Nullable private EditBox labelField;
    @Nullable private EditBox iconField;
    @Nullable private CycleButton<ActionType> actionTypeButton;
    @Nullable private EditBox param1Field;
    @Nullable private EditBox param2Field;
    @Nullable private EditBox param3Field;

    public DialogOptionEditorScreen(
        @Nonnull Screen parent,
        @Nonnull DialogOption option,
        @Nonnull Consumer<DialogOption> onSave
    ) {
        super(Component.translatable("gui.devmod.npc.option_editor.title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.originalOption = Objects.requireNonNull(option, "option");
        this.onSave = Objects.requireNonNull(onSave, "onSave");

        // Initialize edited values from option
        this.editedId = option.id();
        this.editedLabel = option.label();
        this.editedIcon = option.icon();
        this.selectedActionType = ActionType.fromAction(option.action());
        initActionParams(option.action());
    }

    private void initActionParams(DialogAction action) {
        switch (action) {
            case DialogAction.GoToNode goTo -> actionParam1 = goTo.nodeId();
            case DialogAction.ExecuteCommand cmd -> {
                actionParam1 = cmd.command();
                actionParam2 = cmd.source().name();
            }
            case DialogAction.Teleport tp -> {
                if (tp.position() != null) {
                    actionParam1 = tp.position().getX() + "," + tp.position().getY() + "," + tp.position().getZ();
                }
                actionParam2 = tp.zoneId() != null ? tp.zoneId() : "";
            }
            case DialogAction.GiveItem give -> {
                actionParam1 = give.itemId().toString();
                actionParam2 = String.valueOf(give.count());
            }
            case DialogAction.PlaySound sound -> {
                actionParam1 = sound.soundId().toString();
                actionParam2 = String.valueOf(sound.volume());
                actionParam3 = String.valueOf(sound.pitch());
            }
            case DialogAction.OpenGui gui -> actionParam1 = gui.guiId();
            case DialogAction.CloseDialog close -> { /* no params */ }
            case DialogAction.SetVariable sv -> {
                actionParam1 = sv.key();
                actionParam2 = sv.value();
            }
            case DialogAction.RememberChoice rc -> {
                actionParam1 = rc.key();
                actionParam2 = rc.value();
            }
            case DialogAction.SetEmotion se -> actionParam1 = se.emotion().getSerializedName();
            case DialogAction.Custom custom -> actionParam1 = custom.handlerId();
        }
    }

    @Override
    protected void init() {
        super.init();

        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int contentX = popupX + PADDING;
        int contentWidth = POPUP_WIDTH - PADDING * 2;
        int y = popupY + PADDING + 15;

        // ID field
        idField = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.translatable("gui.devmod.npc.option_editor.id"));
        idField.setValue(editedId);
        idField.setMaxLength(DialogLimits.MAX_OPTION_ID_LENGTH);
        idField.setResponder(s -> editedId = s);
        idField.setBordered(false);
        idField.setTextColor(DesignTokens.Text.PRIMARY);
        idField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(idField);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        // Label field
        labelField = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.translatable("gui.devmod.npc.option_editor.label"));
        labelField.setValue(editedLabel);
        labelField.setMaxLength(DialogLimits.MAX_OPTION_LABEL_LENGTH);
        labelField.setResponder(s -> editedLabel = s);
        labelField.setBordered(false);
        labelField.setTextColor(DesignTokens.Text.PRIMARY);
        labelField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(labelField);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        // Icon field
        iconField = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.translatable("gui.devmod.npc.option_editor.icon"));
        iconField.setValue(editedIcon);
        iconField.setMaxLength(DialogLimits.MAX_OPTION_ICON_LENGTH);
        iconField.setResponder(s -> editedIcon = s);
        iconField.setBordered(false);
        iconField.setTextColor(DesignTokens.Text.PRIMARY);
        iconField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(iconField);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        // Action type cycle button
        actionTypeButton = CycleButton.<ActionType>builder(ActionType::getDisplayName)
            .withValues(ACTION_TYPES)
            .withInitialValue(selectedActionType)
            .create(contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
                Component.translatable("gui.devmod.npc.option_editor.action_type"),
                (btn, value) -> {
                    selectedActionType = value;
                    updateParamFields();
                });
        addRenderableWidget(actionTypeButton);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        // Parameter fields (shown/hidden based on action type)
        param1Field = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.literal("Param 1"));
        param1Field.setValue(actionParam1);
        param1Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);
        param1Field.setResponder(s -> actionParam1 = s);
        param1Field.setBordered(false);
        param1Field.setTextColor(DesignTokens.Text.PRIMARY);
        param1Field.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(param1Field);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        param2Field = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.literal("Param 2"));
        param2Field.setValue(actionParam2);
        param2Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);
        param2Field.setResponder(s -> actionParam2 = s);
        param2Field.setBordered(false);
        param2Field.setTextColor(DesignTokens.Text.PRIMARY);
        param2Field.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(param2Field);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        param3Field = new EditBox(font, contentX, y + LABEL_HEIGHT, contentWidth, FIELD_HEIGHT,
            Component.literal("Param 3"));
        param3Field.setValue(actionParam3);
        param3Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);
        param3Field.setResponder(s -> actionParam3 = s);
        param3Field.setBordered(false);
        param3Field.setTextColor(DesignTokens.Text.PRIMARY);
        param3Field.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(param3Field);

        updateParamFields();

        // Bottom buttons
        int buttonY = popupY + POPUP_HEIGHT - PADDING - FIELD_HEIGHT;
        int buttonWidth = 80;

        EditorButton cancelButton = EditorButton.builder("dialog-option-cancel",
                Component.translatable("gui.devmod.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::goBack)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton,
            popupX + POPUP_WIDTH / 2 - buttonWidth - 5, buttonY, buttonWidth, FIELD_HEIGHT));

        EditorButton saveButton = EditorButton.builder("dialog-option-save",
                Component.translatable("gui.devmod.npc.option_editor.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::saveAndClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(saveButton,
            popupX + POPUP_WIDTH / 2 + 5, buttonY, buttonWidth, FIELD_HEIGHT));
    }

    private void updateParamFields() {
        if (param1Field == null || param2Field == null || param3Field == null) {
            return;
        }
        // Hide all param fields first
        param1Field.visible = false;
        param2Field.visible = false;
        param3Field.visible = false;
        param1Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);
        param2Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);
        param3Field.setMaxLength(DialogLimits.MAX_ACTION_PARAM_LENGTH);

        // Show relevant fields based on action type
        switch (selectedActionType) {
            case GOTO -> {
                param1Field.visible = true;
                param1Field.setHint(Component.literal("Node ID"));
                param1Field.setMaxLength(DialogLimits.MAX_NODE_ID_LENGTH);
            }
            case COMMAND -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.literal("Command"));
                param2Field.setHint(Component.literal("Source: PLAYER/CONSOLE/NPC"));
                param1Field.setMaxLength(DialogLimits.MAX_COMMAND_LENGTH);
            }
            case TELEPORT -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.literal("Position: x,y,z"));
                param2Field.setHint(Component.literal("Zone ID (optional)"));
                param2Field.setMaxLength(DialogLimits.MAX_ZONE_ID_LENGTH);
            }
            case GIVE_ITEM -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param1Field.setHint(Component.literal("Item ID: minecraft:diamond"));
                param2Field.setHint(Component.literal("Count"));
            }
            case PLAY_SOUND -> {
                param1Field.visible = true;
                param2Field.visible = true;
                param3Field.visible = true;
                param1Field.setHint(Component.literal("Sound ID"));
                param2Field.setHint(Component.literal("Volume (0.0-1.0)"));
                param3Field.setHint(Component.literal("Pitch (0.5-2.0)"));
            }
            case OPEN_GUI -> {
                param1Field.visible = true;
                param1Field.setHint(Component.literal("GUI ID"));
                param1Field.setMaxLength(DialogLimits.MAX_GUI_ID_LENGTH);
            }
            case CLOSE -> {
                // No parameters needed
            }
        }
    }

    private void saveAndClose() {
        // Build action from parameters
        DialogAction action = buildAction();

        // Create updated option
        DialogOption updated = new DialogOption(
            editedId,
            editedLabel,
            editedIcon,
            originalOption.showCondition(),
            action
        );

        onSave.accept(updated);
        goBack();
    }

    private DialogAction buildAction() {
        return switch (selectedActionType) {
            case CLOSE -> new DialogAction.CloseDialog();
            case GOTO -> new DialogAction.GoToNode(actionParam1.isEmpty() ? "start" : actionParam1);
            case COMMAND -> new DialogAction.ExecuteCommand(
                actionParam1,
                parseCommandSource(actionParam2)
            );
            case TELEPORT -> new DialogAction.Teleport(
                parseBlockPos(actionParam1),
                null, // dimension not editable in simple editor
                actionParam2.isEmpty() ? null : actionParam2
            );
            case GIVE_ITEM -> new DialogAction.GiveItem(
                parseResourceLocation(actionParam1),
                parseIntOrDefault(actionParam2, 1),
                null
            );
            case PLAY_SOUND -> new DialogAction.PlaySound(
                parseResourceLocation(actionParam1),
                parseFloatOrDefault(actionParam2, 1.0f),
                parseFloatOrDefault(actionParam3, 1.0f)
            );
            case OPEN_GUI -> new DialogAction.OpenGui(actionParam1);
        };
    }

    private DialogAction.CommandSource parseCommandSource(String s) {
        try {
            return DialogAction.CommandSource.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DialogAction.CommandSource.NPC;
        }
    }

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

    private net.minecraft.resources.ResourceLocation parseResourceLocation(String s) {
        if (s == null || s.isEmpty()) {
            return net.minecraft.resources.ResourceLocation.withDefaultNamespace("air");
        }
        net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(s);
        return parsed != null ? parsed : net.minecraft.resources.ResourceLocation.withDefaultNamespace("air");
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

    private void goBack() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;

        // Popup background
        graphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, COLOR_PANEL_BG);

        // Border
        graphics.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, COLOR_BORDER);

        // Title
        graphics.drawCenteredString(font, title, width / 2, popupY + PADDING, COLOR_TEXT);

        // Field labels
        int contentX = popupX + PADDING;
        int y = popupY + PADDING + 15;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.option_editor.id"),
            contentX, y, COLOR_LABEL);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.option_editor.label"),
            contentX, y, COLOR_LABEL);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.option_editor.icon"),
            contentX, y, COLOR_LABEL);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.option_editor.action_type"),
            contentX, y, COLOR_LABEL);
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        // Parameter labels based on action type
        if (param1Field != null && param1Field.visible) {
            graphics.drawString(font, selectedActionType.getParam1Label(), contentX, y, COLOR_LABEL);
        }
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        if (param2Field != null && param2Field.visible) {
            graphics.drawString(font, selectedActionType.getParam2Label(), contentX, y, COLOR_LABEL);
        }
        y += LABEL_HEIGHT + FIELD_HEIGHT + SPACING;

        if (param3Field != null && param3Field.visible) {
            graphics.drawString(font, selectedActionType.getParam3Label(), contentX, y, COLOR_LABEL);
        }

        renderInputBackgrounds(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        if (idField != null) {
            AxiomRenderer.drawInputBackground(graphics, idField.getX(), idField.getY(), idField.getWidth(),
                idField.getHeight(), idField.isFocused());
        }
        if (labelField != null) {
            AxiomRenderer.drawInputBackground(graphics, labelField.getX(), labelField.getY(), labelField.getWidth(),
                labelField.getHeight(), labelField.isFocused());
        }
        if (iconField != null) {
            AxiomRenderer.drawInputBackground(graphics, iconField.getX(), iconField.getY(), iconField.getWidth(),
                iconField.getHeight(), iconField.isFocused());
        }
        if (param1Field != null && param1Field.visible) {
            AxiomRenderer.drawInputBackground(graphics, param1Field.getX(), param1Field.getY(), param1Field.getWidth(),
                param1Field.getHeight(), param1Field.isFocused());
        }
        if (param2Field != null && param2Field.visible) {
            AxiomRenderer.drawInputBackground(graphics, param2Field.getX(), param2Field.getY(), param2Field.getWidth(),
                param2Field.getHeight(), param2Field.isFocused());
        }
        if (param3Field != null && param3Field.visible) {
            AxiomRenderer.drawInputBackground(graphics, param3Field.getX(), param3Field.getY(), param3Field.getWidth(),
                param3Field.getHeight(), param3Field.isFocused());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Enum for action types with display names and parameter labels.
     */
    private enum ActionType {
        CLOSE("gui.devmod.npc.action.close"),
        GOTO("gui.devmod.npc.action.goto"),
        COMMAND("gui.devmod.npc.action.command"),
        TELEPORT("gui.devmod.npc.action.teleport"),
        GIVE_ITEM("gui.devmod.npc.action.give_item"),
        PLAY_SOUND("gui.devmod.npc.action.play_sound"),
        OPEN_GUI("gui.devmod.npc.action.open_gui");

        private final String translationKey;

        ActionType(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        public Component getParam1Label() {
            return switch (this) {
                case GOTO -> Component.translatable("gui.devmod.npc.action.goto.node_id");
                case COMMAND -> Component.translatable("gui.devmod.npc.action.command.cmd");
                case TELEPORT -> Component.translatable("gui.devmod.npc.action.teleport.pos");
                case GIVE_ITEM -> Component.translatable("gui.devmod.npc.action.give_item.item");
                case PLAY_SOUND -> Component.translatable("gui.devmod.npc.action.play_sound.sound");
                case OPEN_GUI -> Component.translatable("gui.devmod.npc.action.open_gui.id");
                default -> Component.empty();
            };
        }

        public Component getParam2Label() {
            return switch (this) {
                case COMMAND -> Component.translatable("gui.devmod.npc.action.command.source");
                case TELEPORT -> Component.translatable("gui.devmod.npc.action.teleport.zone");
                case GIVE_ITEM -> Component.translatable("gui.devmod.npc.action.give_item.count");
                case PLAY_SOUND -> Component.translatable("gui.devmod.npc.action.play_sound.volume");
                default -> Component.empty();
            };
        }

        public Component getParam3Label() {
            return switch (this) {
                case PLAY_SOUND -> Component.translatable("gui.devmod.npc.action.play_sound.pitch");
                default -> Component.empty();
            };
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
                case DialogAction.SetVariable sv -> COMMAND; // Treat as command-like for UI
                case DialogAction.RememberChoice rc -> COMMAND; // Treat as command-like for UI
                case DialogAction.SetEmotion se -> CLOSE; // Treat as simple action for UI
                case DialogAction.Custom cu -> CLOSE; // Default fallback
            };
        }
    }
}

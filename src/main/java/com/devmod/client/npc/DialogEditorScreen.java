package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.EditorApplyFeedbackRouter;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.testing.ToastMessage;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.npc.dialog.DialogLimits;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.network.NpcDialogPayload;
import com.devmod.npc.network.SaveDialogPayload;

/**
 * Client screen for editing NPC dialog sets.
 * Layout: 3 columns - Node List | Text Editor | Options List
 */
@OnlyIn(Dist.CLIENT)
public class DialogEditorScreen extends Screen {

    // === Colors ===
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_LABEL = DesignTokens.Text.SECONDARY;
    private static final int COLOR_WARNING = DesignTokens.Npc.DIALOG_ACCENT;

    // === Layout ===
    private static final int PADDING = 10;
    private static final int COL1_WIDTH = 140;
    private static final int COL3_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 18;
    private static final int MAX_VISIBLE_ITEMS = 12;

    // === Data ===
    private final String dialogSetId;
    @Nullable private DialogSet originalDialogSet;
    private Map<String, DialogNode> editedNodes;
    private String entryNodeId;
    private String dialogName;
    private int originalRevision;

    // === State ===
    @Nullable private String selectedNodeId;
    @Nullable private String selectedOptionId;
    private List<String> nodeIdList = new ArrayList<>();
    private int nodeScrollOffset = 0;
    private boolean suppressLineUpdate = false;

    // === Undo/Redo ===
    private final DialogEditorHistory history = new DialogEditorHistory();
    private DialogEditorHistory.EditorState savedState;
    @Nullable
    private EditorButtonWidget undoButtonWidget;
    @Nullable
    private EditorButtonWidget redoButtonWidget;
    @Nullable
    private EditorButtonWidget addNodeButtonWidget;
    @Nullable
    private EditorButtonWidget removeNodeButtonWidget;
    @Nullable
    private EditorButtonWidget addOptionButtonWidget;
    @Nullable
    private EditorButtonWidget removeOptionButtonWidget;

    // === Widgets ===
    @Nullable
    private EditBox nameField;
    @Nullable
    private EditBox entryNodeField;
    @Nullable
    private MultiLineEditBox linesEditor;
    private List<EditorButtonWidget> nodeButtons = new ArrayList<>();
    private List<EditorButtonWidget> optionButtons = new ArrayList<>();
    private final ToastMessage toast = new ToastMessage(1800, 250);
    private final EditorApplyFeedbackRouter.Listener feedbackListener = this::handleEditorConfirm;

    public DialogEditorScreen(
        @Nonnull String dialogSetId,
        @Nullable DialogSet dialogSet
    ) {
        super(Component.translatable("gui.devmod.npc.dialog_editor.title"));
        this.dialogSetId = Objects.requireNonNull(dialogSetId, "dialogSetId");
        this.originalDialogSet = dialogSet;

        if (dialogSet != null) {
            this.editedNodes = new HashMap<>(dialogSet.nodes());
            this.entryNodeId = dialogSet.entryNodeId();
            this.dialogName = dialogSet.name();
            this.originalRevision = dialogSet.revision();
        } else {
            this.editedNodes = new HashMap<>();
            this.entryNodeId = "start";
            this.dialogName = "New Dialog";
            this.originalRevision = 0;
        }

        rebuildNodeList();

        // Save initial state for dirty checking
        savedState = captureState();
    }

    // === Undo/Redo Helpers ===

    /**
     * Captures the current editor state for history.
     */
    private DialogEditorHistory.EditorState captureState() {
        return DialogEditorHistory.EditorState.of(
            dialogName,
            entryNodeId,
            editedNodes,
            selectedNodeId != null ? selectedNodeId : "",
            selectedOptionId != null ? selectedOptionId : ""
        );
    }

    /**
     * Restores the editor to a previous state.
     */
    private void restoreState(DialogEditorHistory.EditorState state) {
        dialogName = state.dialogName();
        entryNodeId = state.entryNodeId();
        editedNodes = new HashMap<>(state.nodes());
        selectedNodeId = state.selectedNodeId().isEmpty() ? null : state.selectedNodeId();
        selectedOptionId = state.selectedOptionId().isEmpty() ? null : state.selectedOptionId();

        // Update UI
        rebuildNodeList();
        if (nameField != null) nameField.setValue(dialogName);
        if (entryNodeField != null) entryNodeField.setValue(entryNodeId);
        updateLinesEditor();
        rebuildNodeButtons(PADDING, PADDING + 30 + 20);
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
        updateUndoRedoButtons();
    }

    /**
     * Pushes current state to history before making changes.
     */
    private void pushHistory() {
        history.push(captureState());
        updateUndoRedoButtons();
    }

    /**
     * Performs undo operation.
     */
    private void performUndo() {
        history.undo(captureState()).ifPresent(this::restoreState);
    }

    /**
     * Performs redo operation.
     */
    private void performRedo() {
        history.redo(captureState()).ifPresent(this::restoreState);
    }

    /**
     * Updates undo/redo button states.
     */
    private void updateUndoRedoButtons() {
        if (undoButtonWidget != null) undoButtonWidget.getButton().setEnabled(history.canUndo());
        if (redoButtonWidget != null) redoButtonWidget.getButton().setEnabled(history.canRedo());
    }

    /**
     * Checks if there are unsaved changes.
     */
    private boolean isDirty() {
        return history.isDirty(captureState(), savedState);
    }

    private void rebuildNodeList() {
        nodeIdList.clear();
        nodeIdList.addAll(editedNodes.keySet());
        nodeIdList.sort(String::compareTo);
    }

    @Override
    protected void init() {
        EditorApplyFeedbackRouter.register(feedbackListener);
        super.init();

        int col1X = PADDING;
        int col2X = col1X + COL1_WIDTH + PADDING;
        int col3X = width - COL3_WIDTH - PADDING;
        int col2Width = col3X - col2X - PADDING;
        int topY = PADDING + 30;

        // === Header Row ===
        int headerY = PADDING + 5;

        // Name field
        nameField = new EditBox(font, col1X + 50, headerY, COL1_WIDTH - 50, 16,
            Component.translatable("gui.devmod.npc.dialog_editor.name"));
        nameField.setValue(dialogName);
        nameField.setMaxLength(DialogLimits.MAX_DIALOG_NAME_LENGTH);
        nameField.setResponder(s -> {
            if (!s.equals(dialogName)) {
                pushHistory();
                dialogName = s;
            }
        });
        nameField.setBordered(false);
        nameField.setTextColor(DesignTokens.Text.PRIMARY);
        nameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(nameField);

        // Entry node field
        entryNodeField = new EditBox(font, col2X + 80, headerY, 100, 16,
            Component.translatable("gui.devmod.npc.dialog_editor.entry_node"));
        entryNodeField.setValue(entryNodeId);
        entryNodeField.setMaxLength(DialogLimits.MAX_NODE_ID_LENGTH);
        entryNodeField.setResponder(s -> {
            if (!s.equals(entryNodeId)) {
                pushHistory();
                entryNodeId = s;
            }
        });
        entryNodeField.setBordered(false);
        entryNodeField.setTextColor(DesignTokens.Text.PRIMARY);
        entryNodeField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(entryNodeField);

        // Undo/Redo buttons
        EditorButton undoButton = EditorButton.builder("dialog-undo", "↩")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.undo").getString())
            .enabled(false)
            .onClick(this::performUndo)
            .build();
        EditorButtonWidget undoWidget = new EditorButtonWidget(undoButton, col3X - 50, headerY, 20, 16);
        undoButtonWidget = undoWidget;
        addRenderableWidget(undoWidget);

        EditorButton redoButton = EditorButton.builder("dialog-redo", "↪")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.redo").getString())
            .enabled(false)
            .onClick(this::performRedo)
            .build();
        EditorButtonWidget redoWidget = new EditorButtonWidget(redoButton, col3X - 25, headerY, 20, 16);
        redoButtonWidget = redoWidget;
        addRenderableWidget(redoWidget);

        // === Column 1: Node List ===
        int listY = topY + 20;
        int listHeight = height - listY - 60;

        // Add node button
        EditorButton addNodeButton = EditorButton.builder("dialog-node-add", "+")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.add_node").getString())
            .onClick(this::addNewNode)
            .build();
        EditorButtonWidget addNodeWidget = new EditorButtonWidget(addNodeButton, col1X, topY, 30, BUTTON_HEIGHT);
        addNodeButtonWidget = addNodeWidget;
        addRenderableWidget(addNodeWidget);

        // Remove node button
        EditorButton removeNodeButton = EditorButton.builder("dialog-node-remove", "-")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.remove_node").getString())
            .onClick(this::removeSelectedNode)
            .build();
        EditorButtonWidget removeNodeWidget = new EditorButtonWidget(removeNodeButton, col1X + 35, topY, 30, BUTTON_HEIGHT);
        removeNodeButtonWidget = removeNodeWidget;
        addRenderableWidget(removeNodeWidget);

        // Node buttons
        rebuildNodeButtons(col1X, listY);

        // === Column 2: Lines Editor ===
        EditorButton addLineButton = EditorButton.builder("dialog-line-add",
                Component.translatable("gui.devmod.npc.dialog_editor.add_line").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::addLineToSelected)
            .build();
        addRenderableWidget(new EditorButtonWidget(addLineButton, col2X, topY, 80, BUTTON_HEIGHT));

        linesEditor = new MultiLineEditBox(font, col2X, listY, col2Width, listHeight - 40,
            Component.empty(), Component.empty());
        linesEditor.setValueListener(this::onLinesChanged);
        addRenderableWidget(linesEditor);

        updateLinesEditor();

        // === Column 3: Options List ===
        EditorButton addOptionButton = EditorButton.builder("dialog-option-add", "+")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.add_option").getString())
            .onClick(this::addNewOption)
            .build();
        EditorButtonWidget addOptionWidget = new EditorButtonWidget(addOptionButton, col3X, topY, 30, BUTTON_HEIGHT);
        addOptionButtonWidget = addOptionWidget;
        addRenderableWidget(addOptionWidget);

        EditorButton removeOptionButton = EditorButton.builder("dialog-option-remove", "-")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .tooltip(Component.translatable("gui.devmod.npc.dialog_editor.remove_option").getString())
            .onClick(this::removeSelectedOption)
            .build();
        EditorButtonWidget removeOptionWidget = new EditorButtonWidget(removeOptionButton, col3X + 35, topY, 30, BUTTON_HEIGHT);
        removeOptionButtonWidget = removeOptionWidget;
        addRenderableWidget(removeOptionWidget);

        EditorButton editOptionButton = EditorButton.builder("dialog-option-edit",
                Component.translatable("gui.devmod.npc.dialog_editor.edit").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::editSelectedOption)
            .build();
        addRenderableWidget(new EditorButtonWidget(editOptionButton, col3X + 70, topY, 50, BUTTON_HEIGHT));

        rebuildOptionButtons(col3X, listY);

        // === Bottom Buttons ===
        int bottomY = height - PADDING - BUTTON_HEIGHT;
        int btnWidth = 80;

        // Preview button
        EditorButton previewButton = EditorButton.builder("dialog-preview",
                Component.translatable("gui.devmod.npc.dialog_editor.preview").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::previewDialog)
            .build();
        addRenderableWidget(new EditorButtonWidget(previewButton, PADDING, bottomY, btnWidth, BUTTON_HEIGHT));

        // Cancel button
        EditorButton cancelButton = EditorButton.builder("dialog-cancel", Component.translatable("gui.devmod.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton, width / 2 - btnWidth - 5, bottomY, btnWidth, BUTTON_HEIGHT));

        // Save button
        EditorButton saveButton = EditorButton.builder("dialog-save",
                Component.translatable("gui.devmod.npc.dialog_editor.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::saveAndClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(saveButton, width / 2 + 5, bottomY, btnWidth, BUTTON_HEIGHT));
    }

    private void rebuildNodeButtons(int x, int startY) {
        // Remove old buttons
        for (EditorButtonWidget btn : nodeButtons) {
            removeWidget(btn);
        }
        nodeButtons.clear();

        int y = startY;
        int visibleCount = Math.min(nodeIdList.size() - nodeScrollOffset, MAX_VISIBLE_ITEMS);

        for (int i = 0; i < visibleCount; i++) {
            int idx = nodeScrollOffset + i;
            if (idx >= nodeIdList.size()) break;

            String nodeId = nodeIdList.get(idx);
            boolean selected = nodeId.equals(selectedNodeId);

            EditorButton nodeButton = EditorButton.builder("dialog-node-" + nodeId, truncate(nodeId, 16))
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .toggled(selected)
                .onClick(() -> selectNode(nodeId))
                .build();
            EditorButtonWidget btn = new EditorButtonWidget(nodeButton, x, y, COL1_WIDTH, ITEM_HEIGHT);

            nodeButtons.add(btn);
            addRenderableWidget(btn);
            y += ITEM_HEIGHT + 2;
        }
    }

    private void rebuildOptionButtons(int x, int startY) {
        // Remove old buttons
        for (EditorButtonWidget btn : optionButtons) {
            removeWidget(btn);
        }
        optionButtons.clear();

        if (selectedNodeId == null) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;

        int y = startY;
        for (DialogOption opt : node.options()) {
            boolean selected = opt.id().equals(selectedOptionId);
            String label = opt.icon() + " " + truncate(opt.label(), 14);

            EditorButton optionButton = EditorButton.builder("dialog-option-" + opt.id(), label)
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .toggled(selected)
                .onClick(() -> selectOption(opt.id()))
                .build();
            EditorButtonWidget btn = new EditorButtonWidget(optionButton, x, y, COL3_WIDTH, ITEM_HEIGHT);

            optionButtons.add(btn);
            addRenderableWidget(btn);
            y += ITEM_HEIGHT + 2;
        }
    }

    private void selectNode(String nodeId) {
        selectedNodeId = nodeId;
        selectedOptionId = null;
        updateLinesEditor();
        rebuildNodeButtons(PADDING, PADDING + 30 + 20);
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
    }

    private void selectOption(String optionId) {
        selectedOptionId = optionId;
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
    }

    private void updateLinesEditor() {
        if (linesEditor == null) {
            return;
        }
        if (selectedNodeId == null) {
            linesEditor.setValue("");
            return;
        }

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node != null) {
            linesEditor.setValue(node.getCombinedText());
        } else {
            linesEditor.setValue("");
        }
    }

    private void onLinesChanged(String newText) {
        if (selectedNodeId == null) return;
        if (linesEditor == null) return;
        if (suppressLineUpdate) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;

        List<String> lines = new ArrayList<>();
        for (String line : Splitter.on('\n').split(newText)) {
            if (lines.size() >= DialogLimits.MAX_LINES_PER_NODE) {
                break;
            }
            lines.add(DialogLimits.truncate(line, DialogLimits.MAX_LINE_LENGTH));
        }

        String normalized = String.join("\n", lines);
        if (!normalized.equals(newText)) {
            suppressLineUpdate = true;
            linesEditor.setValue(normalized);
            suppressLineUpdate = false;
        }

        editedNodes.put(selectedNodeId, node.withLines(lines));
    }

    private void addNewNode() {
        if (editedNodes.size() >= DialogLimits.MAX_NODES) {
            return;
        }
        pushHistory();

        String newId = "node_" + (editedNodes.size() + 1);
        int counter = 1;
        while (editedNodes.containsKey(newId)) {
            newId = "node_" + (editedNodes.size() + counter++);
        }

        DialogNode newNode = DialogNode.terminal(newId, "New dialog text...");
        editedNodes.put(newId, newNode);
        rebuildNodeList();
        selectNode(newId);
        rebuildNodeButtons(PADDING, PADDING + 30 + 20);
        updateUndoRedoButtons();
    }

    private void removeSelectedNode() {
        if (selectedNodeId == null) return;

        pushHistory();
        editedNodes.remove(selectedNodeId);
        rebuildNodeList();
        selectedNodeId = nodeIdList.isEmpty() ? null : nodeIdList.get(0);
        updateLinesEditor();
        rebuildNodeButtons(PADDING, PADDING + 30 + 20);
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
        updateUndoRedoButtons();
    }

    private void addLineToSelected() {
        if (selectedNodeId == null) return;
        if (linesEditor == null) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;
        if (node.lines().size() >= DialogLimits.MAX_LINES_PER_NODE) return;

        String currentText = linesEditor.getValue();
        linesEditor.setValue(currentText + (currentText.isEmpty() ? "" : "\n") + "New line...");
    }

    private void addNewOption() {
        if (selectedNodeId == null) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;
        if (node.options().size() >= DialogLimits.MAX_OPTIONS_PER_NODE) return;

        pushHistory();
        String optId = "opt_" + (node.options().size() + 1);
        DialogOption newOpt = new DialogOption(optId, "New Option", "➡️", null,
            new DialogAction.CloseDialog());

        editedNodes.put(selectedNodeId, node.withAddedOption(newOpt));
        selectedOptionId = optId;
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
        updateUndoRedoButtons();
    }

    private void removeSelectedOption() {
        if (selectedNodeId == null || selectedOptionId == null) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;

        pushHistory();
        List<DialogOption> newOptions = new ArrayList<>();
        for (DialogOption opt : node.options()) {
            if (!opt.id().equals(selectedOptionId)) {
                newOptions.add(opt);
            }
        }

        editedNodes.put(selectedNodeId, node.withOptions(newOptions));
        selectedOptionId = null;
        rebuildOptionButtons(width - COL3_WIDTH - PADDING, PADDING + 30 + 20);
        updateUndoRedoButtons();
    }

    private void editSelectedOption() {
        if (selectedNodeId == null || selectedOptionId == null) return;

        DialogNode node = editedNodes.get(selectedNodeId);
        if (node == null) return;

        DialogOption opt = node.getOption(selectedOptionId).orElse(null);
        if (opt == null) return;

        // Open option editor screen
        if (minecraft != null) {
            minecraft.setScreen(new DialogOptionEditorScreen(this, opt, updatedOpt -> {
                pushHistory();
                List<DialogOption> newOptions = new ArrayList<>();
                for (DialogOption o : node.options()) {
                    if (o.id().equals(selectedOptionId)) {
                        newOptions.add(updatedOpt);
                    } else {
                        newOptions.add(o);
                    }
                }
                editedNodes.put(selectedNodeId, node.withOptions(newOptions));
                updateUndoRedoButtons();
            }));
        }
    }

    private void previewDialog() {
        if (entryNodeId == null || entryNodeId.isEmpty()) return;

        DialogNode entryNode = editedNodes.get(entryNodeId);
        if (entryNode == null) return;

        // Show preview in NpcDialogScreen
        if (minecraft != null && minecraft.player != null) {
            List<NpcDialogPayload.NpcDialogOptionData> options = entryNode.options().stream()
                .map(o -> new NpcDialogPayload.NpcDialogOptionData(o.id(), o.label(), o.icon()))
                .toList();

            minecraft.setScreen(new NpcDialogScreen(
                minecraft.player.getUUID(),
                "Preview NPC",
                dialogSetId,
                entryNodeId,
                entryNode.lines(),
                options
            ));
        }
    }

    private void saveAndClose() {
        if (originalDialogSet != null && originalDialogSet.isPreset()) {
            // Cannot save presets
            return;
        }

        DialogSet newDialogSet = new DialogSet(
            dialogSetId,
            dialogName,
            Map.copyOf(editedNodes),
            entryNodeId,
            System.currentTimeMillis(),
            originalRevision + 1,
            false,
            minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null,
            originalDialogSet != null ? originalDialogSet.schedule() : null
        );

        PacketDistributor.sendToServer(new SaveDialogPayload(newDialogSet, originalRevision));

        // Update saved state so dirty indicator resets
        savedState = captureState();

        onClose();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Column headers
        int col1X = PADDING;
        int col2X = col1X + COL1_WIDTH + PADDING;
        int col3X = width - COL3_WIDTH - PADDING;
        int topY = PADDING + 30;

        // Labels
        graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.name"),
            col1X, PADDING + 8, COLOR_LABEL);
        graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.entry_node"),
            col2X, PADDING + 8, COLOR_LABEL);

        // Column titles
        graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.nodes"),
            col1X, topY + 3, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.text"),
            col2X + 90, topY + 3, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.options"),
            col3X + 125, topY + 3, COLOR_TEXT);

        // Title (with dirty indicator)
        Component displayTitle = isDirty()
            ? Component.literal("* ").append(title)
            : title;
        graphics.drawCenteredString(font, displayTitle, width / 2, 5, COLOR_TEXT);

        // Preset warning
        if (originalDialogSet != null && originalDialogSet.isPreset()) {
            graphics.drawString(font, Component.translatable("gui.devmod.npc.dialog_editor.preset_readonly")
                .withStyle(s -> s.withColor(COLOR_WARNING)), width / 2 - 80, height - PADDING - 40, COLOR_WARNING);
        }

        // Borders between columns
        int listY = topY + 20;
        int listHeight = height - listY - 60;

        graphics.fill(col2X - 5, listY - 5, col2X - 4, listY + listHeight, COLOR_BORDER);
        graphics.fill(col3X - 5, listY - 5, col3X - 4, listY + listHeight, COLOR_BORDER);

        renderInputBackgrounds(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderButtonTooltips(graphics, mouseX, mouseY);

        var safeFont = this.font;
        if (safeFont != null) {
            toast.renderInBounds(graphics, safeFont, 0, 0, width, 24, ToastMessage.Position.TOP_RIGHT);
        }
    }

    @Override
    public void onClose() {
        EditorApplyFeedbackRouter.unregister(feedbackListener);
        super.onClose();
    }

    private boolean handleEditorConfirm(@Nonnull EditorApplyConfirmPayload payload) {
        if (!"dialog".equalsIgnoreCase(payload.scope())) {
            return false;
        }
        String msg = payload.success()
            ? "Dialog saved"
            : "Dialog save failed: " + payload.message();
        toast.show(msg);
        return true;
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        if (nameField != null) {
            AxiomRenderer.drawInputBackground(graphics, nameField.getX(), nameField.getY(), nameField.getWidth(),
                nameField.getHeight(), nameField.isFocused());
        }
        if (entryNodeField != null) {
            AxiomRenderer.drawInputBackground(graphics, entryNodeField.getX(), entryNodeField.getY(),
                entryNodeField.getWidth(), entryNodeField.getHeight(), entryNodeField.isFocused());
        }
        if (linesEditor != null) {
            AxiomRenderer.drawInputBackground(graphics, linesEditor.getX(), linesEditor.getY(), linesEditor.getWidth(),
                linesEditor.getHeight(), linesEditor.isFocused());
        }
    }

    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderTooltipIfActive(graphics, undoButtonWidget, mouseX, mouseY)) return;
        if (renderTooltipIfActive(graphics, redoButtonWidget, mouseX, mouseY)) return;
        if (renderTooltipIfActive(graphics, addNodeButtonWidget, mouseX, mouseY)) return;
        if (renderTooltipIfActive(graphics, removeNodeButtonWidget, mouseX, mouseY)) return;
        if (renderTooltipIfActive(graphics, addOptionButtonWidget, mouseX, mouseY)) return;
        renderTooltipIfActive(graphics, removeOptionButtonWidget, mouseX, mouseY);
    }

    private boolean renderTooltipIfActive(GuiGraphics graphics, @Nullable EditorButtonWidget widget, int mouseX, int mouseY) {
        if (widget == null) {
            return false;
        }
        String tooltip = widget.getButton().activeTooltip();
        if (tooltip == null) {
            return false;
        }
        graphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < PADDING + COL1_WIDTH) {
            // Scroll node list
            nodeScrollOffset = Math.max(0, Math.min(nodeIdList.size() - MAX_VISIBLE_ITEMS,
                nodeScrollOffset - (int) scrollY));
            rebuildNodeButtons(PADDING, PADDING + 30 + 20);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle Ctrl+Z (Undo) and Ctrl+Y (Redo)
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            performUndo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            performRedo();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

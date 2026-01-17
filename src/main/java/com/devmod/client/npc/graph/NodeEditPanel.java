package com.devmod.client.npc.graph;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;

/**
 * Side panel for viewing and editing a selected dialog node.
 * Displays node ID, lines preview, and options list.
 */
public class NodeEditPanel extends AbstractWidget {

    /** Panel colors */
    private static final int COLOR_BACKGROUND = DesignTokens.Background.PANEL;
    private static final int COLOR_HEADER = DesignTokens.Background.HEADER;
    private static final int COLOR_BORDER = DesignTokens.Border.DEFAULT;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;

    private static final int HEADER_HEIGHT = 32;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 16;

    private final Minecraft minecraft;

    @Nullable
    private String nodeId;
    @Nullable
    private DialogNode dialogNode;

    // Child widgets
    private EditorButton editButton;
    private EditorButton setEntryButton;

    // Callbacks
    @Nullable
    private Consumer<String> onEditClicked;
    @Nullable
    private Consumer<String> onSetEntryClicked;

    // Scroll state
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public NodeEditPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Node Editor"));
        this.minecraft = Minecraft.getInstance();
        initWidgets();
    }

    private void initWidgets() {
        editButton = EditorButton.builder("edit_node", "Edit Node")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .enabled(false)
            .onClick(() -> {
                if (nodeId != null && onEditClicked != null) {
                    onEditClicked.accept(nodeId);
                }
            })
            .build();

        setEntryButton = EditorButton.builder("set_entry", "Set Entry")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .enabled(false)
            .onClick(() -> {
                if (nodeId != null && onSetEntryClicked != null) {
                    onSetEntryClicked.accept(nodeId);
                }
            })
            .build();
    }

    // ========================================================================
    // Data
    // ========================================================================

    /**
     * Loads a node for display.
     */
    public void loadNode(@Nullable String nodeId, @Nullable DialogNode node) {
        this.nodeId = nodeId;
        this.dialogNode = node;
        this.scrollOffset = 0;

        if (editButton != null) {
            editButton.setEnabled(nodeId != null);
        }
        if (setEntryButton != null) {
            setEntryButton.setEnabled(nodeId != null);
        }

        calculateMaxScroll();
    }

    /**
     * Clears the panel.
     */
    public void clear() {
        loadNode(null, null);
    }

    private void calculateMaxScroll() {
        if (dialogNode == null) {
            maxScroll = 0;
            return;
        }

        int contentHeight = HEADER_HEIGHT + PADDING * 2;

        // Node ID section
        contentHeight += LINE_HEIGHT + PADDING;

        // Lines section
        contentHeight += LINE_HEIGHT; // "Lines:" header
        contentHeight += dialogNode.lines().size() * LINE_HEIGHT;
        contentHeight += PADDING;

        // Options section
        contentHeight += LINE_HEIGHT; // "Options:" header
        contentHeight += dialogNode.options().size() * (LINE_HEIGHT * 2);
        contentHeight += PADDING;

        // Buttons
        contentHeight += 40;

        maxScroll = Math.max(0, contentHeight - height);
    }

    // ========================================================================
    // Callbacks
    // ========================================================================

    public void setOnEditClicked(Consumer<String> callback) {
        this.onEditClicked = callback;
    }

    public void setOnSetEntryClicked(Consumer<String> callback) {
        this.onSetEntryClicked = callback;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, COLOR_BACKGROUND);

        // Border
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, COLOR_BORDER);

        // Header
        graphics.fill(getX(), getY(), getX() + width, getY() + HEADER_HEIGHT, COLOR_HEADER);
        graphics.drawString(
            minecraft.font,
            nodeId != null ? "Node: " + truncate(nodeId, 15) : "No Selection",
            getX() + PADDING,
            getY() + (HEADER_HEIGHT - 8) / 2,
            COLOR_TEXT,
            false
        );

        if (dialogNode == null) {
            // Empty state
            graphics.drawCenteredString(
                minecraft.font,
                "Select a node to view details",
                getX() + width / 2,
                getY() + height / 2,
                COLOR_TEXT_DIM
            );
            return;
        }

        // Enable scissor for scrolling content
        graphics.enableScissor(
            getX(),
            getY() + HEADER_HEIGHT,
            getX() + width,
            getY() + height - 40
        );

        int y = getY() + HEADER_HEIGHT + PADDING - scrollOffset;

        // Node ID
        graphics.drawString(minecraft.font, "ID:", getX() + PADDING, y, COLOR_TEXT_DIM, false);
        y += LINE_HEIGHT;
        graphics.drawString(minecraft.font, nodeId, getX() + PADDING, y, COLOR_TEXT, false);
        y += LINE_HEIGHT + PADDING;

        // Lines section
        graphics.drawString(minecraft.font, "Lines (" + dialogNode.lines().size() + "):", getX() + PADDING, y, COLOR_ACCENT, false);
        y += LINE_HEIGHT;

        List<String> lines = dialogNode.lines();
        for (int i = 0; i < lines.size(); i++) {
            String line = truncate(lines.get(i), 25);
            graphics.drawString(minecraft.font, (i + 1) + ". " + line, getX() + PADDING, y, COLOR_TEXT_DIM, false);
            y += LINE_HEIGHT;
        }
        y += PADDING;

        // Options section
        graphics.drawString(minecraft.font, "Options (" + dialogNode.options().size() + "):", getX() + PADDING, y, COLOR_ACCENT, false);
        y += LINE_HEIGHT;

        List<DialogOption> options = dialogNode.options();
        for (int i = 0; i < options.size(); i++) {
            DialogOption opt = options.get(i);
            String label = truncate(opt.label(), 20);
            String actionType = getActionTypeName(opt);

            // Option label
            graphics.drawString(minecraft.font, opt.icon() + " " + label, getX() + PADDING, y, COLOR_TEXT, false);
            y += LINE_HEIGHT;

            // Action type
            graphics.drawString(minecraft.font, "  → " + actionType, getX() + PADDING, y, COLOR_TEXT_DIM, false);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        // Buttons
        int btnWidth = (width - PADDING * 3) / 2;
        int btnHeight = EditorButton.Size.MEDIUM.height();
        int btnY = getY() + height - 32;
        int btnX = getX() + PADDING;
        editButton.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        setEntryButton.render(graphics, btnX + btnWidth + PADDING, btnY, btnWidth, btnHeight, mouseX, mouseY);

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarHeight = Math.max(20, (height - HEADER_HEIGHT - 40) * (height - HEADER_HEIGHT - 40) / (maxScroll + height - HEADER_HEIGHT - 40));
            int scrollbarY = getY() + HEADER_HEIGHT + (int) ((height - HEADER_HEIGHT - 40 - scrollbarHeight) * ((float) scrollOffset / maxScroll));

            graphics.fill(getX() + width - 4, getY() + HEADER_HEIGHT, getX() + width, getY() + height - 40,
                DesignTokens.Background.DARKER);
            graphics.fill(getX() + width - 4, scrollbarY, getX() + width, scrollbarY + scrollbarHeight, COLOR_TEXT_DIM);
        }
    }

    private String getActionTypeName(DialogOption option) {
        var action = option.action();
        return switch (action) {
            case com.devmod.npc.dialog.action.DialogAction.GoToNode g -> "Go to: " + truncate(g.nodeId(), 12);
            case com.devmod.npc.dialog.action.DialogAction.CloseDialog c -> "Close";
            case com.devmod.npc.dialog.action.DialogAction.OpenGui o -> "Open: " + o.guiId();
            case com.devmod.npc.dialog.action.DialogAction.Teleport t -> "Teleport";
            case com.devmod.npc.dialog.action.DialogAction.GiveItem g -> "Give item";
            case com.devmod.npc.dialog.action.DialogAction.ExecuteCommand e -> "Command";
            case com.devmod.npc.dialog.action.DialogAction.PlaySound p -> "Sound";
            case com.devmod.npc.dialog.action.DialogAction.SetVariable s -> "Set var";
            case com.devmod.npc.dialog.action.DialogAction.RememberChoice r -> "Remember";
            case com.devmod.npc.dialog.action.DialogAction.SetEmotion s -> "Emotion: " + s.emotion().getSerializedName();
            case com.devmod.npc.dialog.action.DialogAction.Custom c -> "Custom: " + c.handlerId();
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }

    // ========================================================================
    // Input
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (setEntryButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editButton.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (setEntryButton.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        // No narration
    }
}

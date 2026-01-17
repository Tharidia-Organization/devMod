package com.devmod.client.hologram;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramState;
import com.devmod.hologram.network.DeleteHologramPayload;
import com.devmod.hologram.network.SaveHologramPayload;
import com.devmod.hologram.runtime.HologramNaming;
import com.devmod.hologram.runtime.HologramSounds;

/**
 * GUI screen for editing hologram content.
 * Allows editing lines, previewing changes, and saving/deleting holograms.
 */
@OnlyIn(Dist.CLIENT)
public class HologramEditorScreen extends Screen {
    private static final int EDITOR_WIDTH = 420;
    private static final int EDITOR_HEIGHT = 320;
    private static final int LINE_HEIGHT = 24;
    private static final int PADDING = DesignTokens.Spacing.LG;
    private static final int MAX_VISIBLE_LINES = 8;

    private final HologramDefinition originalDefinition;
    private final List<EditBox> lineEditors = new ArrayList<>();
    private final List<EditorButtonWidget> lineActionButtons = new ArrayList<>();
    private final List<String> currentLines;

    private int leftPos;
    private int topPos;
    private int scrollOffset = 0;

    @Nullable private EditorButton saveButton;
    @Nullable private EditorButton addLineButton;
    @Nullable private EditorButton deleteButton;
    @Nullable private EditorButton cancelButton;

    public HologramEditorScreen(@Nonnull HologramDefinition definition) {
        super(Component.translatable("screen.devmod.hologram_editor"));
        this.originalDefinition = definition;
        this.currentLines = new ArrayList<>(definition.lines());
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - EDITOR_WIDTH) / 2;
        this.topPos = (this.height - EDITOR_HEIGHT) / 2;

        // Create line editors
        rebuildLineEditors();

        int buttonHeight = EditorButton.Size.MEDIUM.height();
        int buttonRowY = topPos + EDITOR_HEIGHT - PADDING - buttonHeight;
        int actionButtonWidth = DesignTokens.Size.BUTTON_WIDTH;
        int actionButtonGap = DesignTokens.Spacing.SM;

        addLineButton = EditorButton.builder("add_line", "+ Add Line")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::addLine)
            .build();
        int addLineWidth = DesignTokens.Size.BUTTON_WIDTH_MEDIUM;
        int addLineY = buttonRowY - DesignTokens.Spacing.MD - buttonHeight;
        addRenderableWidget(new EditorButtonWidget(addLineButton, leftPos + PADDING, addLineY, addLineWidth, buttonHeight));

        int rightX = leftPos + EDITOR_WIDTH - PADDING;
        int cancelX = rightX - actionButtonWidth;
        int deleteX = cancelX - actionButtonGap - actionButtonWidth;
        int saveX = deleteX - actionButtonGap - actionButtonWidth;

        saveButton = EditorButton.builder("save", Component.translatable("gui.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::save)
            .build();
        addRenderableWidget(new EditorButtonWidget(saveButton, saveX, buttonRowY, actionButtonWidth, buttonHeight));

        deleteButton = EditorButton.builder("delete", Component.translatable("gui.delete").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::confirmDelete)
            .build();
        addRenderableWidget(new EditorButtonWidget(deleteButton, deleteX, buttonRowY, actionButtonWidth, buttonHeight));

        cancelButton = EditorButton.builder("cancel", Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton, cancelX, buttonRowY, actionButtonWidth, buttonHeight));

        updateButtonStates();
    }

    private void rebuildLineEditors() {
        // Remove old editors
        for (EditBox editor : lineEditors) {
            removeWidget(editor);
        }
        lineEditors.clear();
        for (EditorButtonWidget widget : lineActionButtons) {
            removeWidget(widget);
        }
        lineActionButtons.clear();

        // Create new editors
        int editorWidth = EDITOR_WIDTH - PADDING * 2 - 60; // Leave room for buttons
        int startY = topPos + 50;

        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(scrollOffset + MAX_VISIBLE_LINES, currentLines.size());

        for (int i = visibleStart; i < visibleEnd; i++) {
            int displayIndex = i - scrollOffset;
            int y = startY + displayIndex * LINE_HEIGHT;

            EditBox editor = new EditBox(
                this.font,
                leftPos + PADDING,
                y,
                editorWidth,
                20,
                Component.literal("Line " + (i + 1))
            );
            editor.setBordered(false);
            editor.setTextColor(DesignTokens.Text.PRIMARY);
            editor.setTextColorUneditable(DesignTokens.Text.MUTED);
            editor.setMaxLength(HologramNaming.MAX_LINE_LENGTH);
            editor.setValue(i < currentLines.size() ? currentLines.get(i) : "");

            final int lineIndex = i;
            editor.setResponder(text -> {
                if (lineIndex < currentLines.size()) {
                    currentLines.set(lineIndex, text);
                }
            });

            lineEditors.add(editor);
            addRenderableWidget(editor);

            int iconButtonSize = EditorButton.Size.SMALL.height();
            int iconY = y + (20 - iconButtonSize) / 2;
            int buttonX = leftPos + PADDING + editorWidth + 6;

            EditorButton deleteLineButton = EditorButton.builder("delete_line_" + lineIndex, "X")
                .style(EditorButton.Style.DANGER)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> removeLine(lineIndex))
                .build();
            EditorButtonWidget deleteWidget = new EditorButtonWidget(deleteLineButton, buttonX, iconY, iconButtonSize, iconButtonSize);
            addRenderableWidget(deleteWidget);
            lineActionButtons.add(deleteWidget);

            // Move up button
            if (lineIndex > 0) {
                int upX = buttonX + iconButtonSize + 6;
                EditorButton upButton = EditorButton.builder("move_up_" + lineIndex, "\u2191")
                    .style(EditorButton.Style.GHOST)
                    .size(EditorButton.Size.SMALL)
                    .onClick(() -> moveLine(lineIndex, -1))
                    .build();
                EditorButtonWidget upWidget = new EditorButtonWidget(upButton, upX, iconY, iconButtonSize, iconButtonSize);
                addRenderableWidget(upWidget);
                lineActionButtons.add(upWidget);
            }
        }

        updateButtonStates();
    }

    private void addLine() {
        if (currentLines.size() >= HologramNaming.MAX_LINES) {
            return;
        }
        currentLines.add("");
        playSound(HologramSounds.Phase.LINE_ADD);
        rebuildEditorWidgets();
    }

    private void removeLine(int index) {
        if (index >= 0 && index < currentLines.size() && currentLines.size() > 1) {
            currentLines.remove(index);
            playSound(HologramSounds.Phase.LINE_REMOVE);
            rebuildEditorWidgets();
        }
    }

    private void moveLine(int index, int direction) {
        int newIndex = index + direction;
        if (newIndex >= 0 && newIndex < currentLines.size()) {
            String line = currentLines.remove(index);
            currentLines.add(newIndex, line);
            rebuildEditorWidgets();
        }
    }

    private void rebuildEditorWidgets() {
        clearWidgets();
        init();
    }

    private void updateButtonStates() {
        if (addLineButton != null) {
            addLineButton.setEnabled(currentLines.size() < HologramNaming.MAX_LINES);
        }
    }

    private void save() {
        // Validate lines
        List<String> validatedLines = new ArrayList<>();
        for (String line : currentLines) {
            validatedLines.add(HologramNaming.truncateLine(line));
        }

        // Send save payload
        SaveHologramPayload payload = new SaveHologramPayload(
            originalDefinition.id(),
            validatedLines,
            originalDefinition.style(),
            originalDefinition.options(),
            originalDefinition.revision()
        );
        PacketDistributor.sendToServer(payload);

        playSound(HologramSounds.Phase.SAVE);
        onClose();
    }

    private void confirmDelete() {
        // In a full implementation, show a confirmation dialog
        DeleteHologramPayload payload = new DeleteHologramPayload(originalDefinition.id());
        PacketDistributor.sendToServer(payload);

        playSound(HologramSounds.Phase.REMOVE);
        onClose();
    }

    private void playSound(HologramSounds.Phase phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            HologramSounds.playPhaseLocal(mc.level, mc.player.position(), phase);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int borderColor = HologramState.EDITING.getPrimaryWithAlpha();
        AxiomRenderer.drawPanelWithHeader(
            graphics,
            font,
            leftPos,
            topPos,
            EDITOR_WIDTH,
            EDITOR_HEIGHT,
            this.title.getString(),
            DesignTokens.Background.PANEL,
            DesignTokens.Background.HEADER,
            borderColor,
            borderColor
        );

        // Hologram info
        int contentTop = topPos + DesignTokens.Spacing.HEADER_HEIGHT + DesignTokens.Spacing.SM;
        String info = String.format("Type: %s | Lines: %d/%d",
            originalDefinition.type().getSerializedName(),
            currentLines.size(),
            HologramNaming.MAX_LINES
        );
        graphics.drawString(this.font, info, leftPos + PADDING, contentTop, DesignTokens.Text.SECONDARY, false);

        // Line numbers
        int startY = contentTop + DesignTokens.Spacing.MD + DesignTokens.Spacing.SM;
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(scrollOffset + MAX_VISIBLE_LINES, currentLines.size());

        for (int i = visibleStart; i < visibleEnd; i++) {
            int displayIndex = i - scrollOffset;
            int y = startY + displayIndex * LINE_HEIGHT + 5;
            graphics.drawString(this.font, String.valueOf(i + 1) + ":", leftPos + 5, y, DesignTokens.Text.MUTED, false);
        }

        renderLineEditorBackgrounds(graphics);

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Preview section title
        int previewY = topPos + EDITOR_HEIGHT - 90;
        graphics.drawString(this.font, "Preview:", leftPos + PADDING, previewY, DesignTokens.Text.SECONDARY, false);

        // Simple preview of first few lines
        int previewLineY = previewY + 12;
        int previewLines = Math.min(3, currentLines.size());
        for (int i = 0; i < previewLines; i++) {
            String line = currentLines.get(i);
            if (line.length() > 50) {
                line = line.substring(0, 47) + "...";
            }
            graphics.drawString(this.font, line, leftPos + PADDING, previewLineY + i * 10, DesignTokens.Text.PRIMARY, false);
        }
    }

    private void renderLineEditorBackgrounds(GuiGraphics graphics) {
        for (EditBox editor : lineEditors) {
            AxiomRenderer.drawInputBackground(
                graphics,
                editor.getX(),
                editor.getY(),
                editor.getWidth(),
                editor.getHeight(),
                editor.isFocused()
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentLines.size() > MAX_VISIBLE_LINES) {
            int maxScroll = currentLines.size() - MAX_VISIBLE_LINES;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            rebuildEditorWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        playSound(HologramSounds.Phase.EDITOR_CLOSE);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

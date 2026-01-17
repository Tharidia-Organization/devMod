package com.devmod.client.npc.group;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.NpcEmotion;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.group.SpeakerLine;

/**
 * Client screen for displaying group dialog conversations.
 * Shows speaker name, portrait area, dialog text, and response options.
 */
@OnlyIn(Dist.CLIENT)
public class GroupDialogScreen extends Screen {

    /** UI Constants */
    private static final int DIALOG_BOX_HEIGHT = 120;
    private static final int PADDING = 16;
    private static final int SPEAKER_NAME_HEIGHT = 20;
    private static final int OPTION_HEIGHT = 24;
    private static final int OPTION_SPACING = 4;

    /** Colors */
    private static final int COLOR_DIALOG_BG = DesignTokens.Nexus.DIALOG_PANEL_BG;
    private static final int COLOR_DIALOG_BORDER = DesignTokens.Nexus.DIALOG_BORDER;
    private static final int COLOR_SPEAKER_BG = DesignTokens.Background.HEADER;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.MUTED;
    private static final int COLOR_SPEAKER_PLAYER = DesignTokens.Accent.CYAN;
    private static final int COLOR_SPEAKER_NPC = DesignTokens.Accent.GOLD;
    private static final int COLOR_NARRATOR = DesignTokens.Text.MUTED;

    // Current state
    private UUID currentSpeakerId;
    private String currentSpeakerName;
    private String currentText;
    @Nullable
    private NpcEmotion currentEmotion;
    private int lineIndex;
    private int totalLines;
    private List<DialogOption> options;
    private boolean showOptions;

    // Typewriter effect
    private int displayedChars = 0;
    private float charTimer = 0;
    private static final float CHARS_PER_TICK = 1.5f;

    // Speaker transition animation
    private float speakerTransition = 1.0f;

    // Option buttons
    private List<EditorButtonWidget> optionButtons = List.of();

    public GroupDialogScreen(
        @Nonnull UUID sessionId,
        @Nonnull String dialogId,
        @Nonnull String dialogName,
        @Nonnull Map<UUID, String> participantNames,
        @Nonnull UUID currentSpeakerId,
        @Nonnull String currentSpeakerName,
        @Nonnull String currentText,
        @Nullable NpcEmotion currentEmotion,
        int lineIndex,
        int totalLines,
        @Nonnull List<DialogOption> options
    ) {
        super(Component.literal(dialogName));
        this.currentSpeakerId = currentSpeakerId;
        this.currentSpeakerName = currentSpeakerName;
        this.currentText = currentText;
        this.currentEmotion = currentEmotion;
        this.lineIndex = lineIndex;
        this.totalLines = totalLines;
        this.options = List.copyOf(options);
        this.showOptions = lineIndex >= totalLines - 1 && !options.isEmpty();
    }

    @Override
    protected void init() {
        createOptionButtons();
    }

    /**
     * Updates the dialog state with new data from the server.
     */
    public void updateState(
        @Nonnull UUID speakerId,
        @Nonnull String speakerName,
        @Nonnull String text,
        @Nullable NpcEmotion emotion,
        int lineIndex,
        int totalLines,
        @Nonnull List<DialogOption> options
    ) {
        // Check for speaker change
        if (!speakerId.equals(this.currentSpeakerId)) {
            this.speakerTransition = 0;
        }

        this.currentSpeakerId = speakerId;
        this.currentSpeakerName = speakerName;
        this.currentText = text;
        this.currentEmotion = emotion;
        this.lineIndex = lineIndex;
        this.totalLines = totalLines;
        this.options = List.copyOf(options);
        this.showOptions = lineIndex >= totalLines - 1 && !options.isEmpty();

        // Reset typewriter
        this.displayedChars = 0;
        this.charTimer = 0;

        // Recreate option buttons
        createOptionButtons();
    }

    private void createOptionButtons() {
        // Clear existing buttons
        optionButtons.forEach(this::removeWidget);

        if (!showOptions || options.isEmpty()) {
            optionButtons = List.of();
            return;
        }

        int dialogBoxY = height - DIALOG_BOX_HEIGHT - PADDING;
        int optionAreaY = dialogBoxY - (options.size() * (OPTION_HEIGHT + OPTION_SPACING)) - PADDING;
        int optionWidth = width - PADDING * 4;

        optionButtons = new java.util.ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            DialogOption opt = options.get(i);
            int btnY = optionAreaY + i * (OPTION_HEIGHT + OPTION_SPACING);

            EditorButton button = EditorButton.builder("dialog_opt_" + i, opt.icon() + " " + opt.label())
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> onOptionSelected(opt.id()))
                .build();
            EditorButtonWidget widget = new EditorButtonWidget(button, PADDING * 2, btnY, optionWidth, OPTION_HEIGHT);
            optionButtons.add(widget);
            addRenderableWidget(widget);
        }
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update typewriter effect
        updateTypewriter(partialTick);

        // Update speaker transition
        if (speakerTransition < 1.0f) {
            speakerTransition = Math.min(1.0f, speakerTransition + partialTick * 0.1f);
        }

        // Dim background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Dialog box
        int dialogBoxY = height - DIALOG_BOX_HEIGHT - PADDING;
        renderDialogBox(graphics, dialogBoxY);

        // Speaker name
        renderSpeakerName(graphics, dialogBoxY);

        // Dialog text with typewriter
        renderDialogText(graphics, dialogBoxY);

        // Progress indicator
        renderProgress(graphics, dialogBoxY);

        // Options (rendered by superclass via addRenderableWidget)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Click to continue hint
        if (!showOptions && displayedChars >= currentText.length()) {
            String hint = "Click to continue...";
            graphics.drawCenteredString(
                font,
                hint,
                width / 2,
                dialogBoxY + DIALOG_BOX_HEIGHT - 16,
                COLOR_TEXT_DIM
            );
        }
    }

    private void renderDialogBox(@Nonnull GuiGraphics graphics, int y) {
        // Main box
        graphics.fill(PADDING, y, width - PADDING, y + DIALOG_BOX_HEIGHT, COLOR_DIALOG_BG);
        graphics.renderOutline(PADDING, y, width - PADDING * 2, DIALOG_BOX_HEIGHT, COLOR_DIALOG_BORDER);

        // Speaker name background
        int speakerBgWidth = font.width(currentSpeakerName) + 16;
        graphics.fill(PADDING, y - SPEAKER_NAME_HEIGHT, PADDING + speakerBgWidth, y, COLOR_SPEAKER_BG);
        graphics.renderOutline(PADDING, y - SPEAKER_NAME_HEIGHT, speakerBgWidth, SPEAKER_NAME_HEIGHT, COLOR_DIALOG_BORDER);
    }

    private void renderSpeakerName(@Nonnull GuiGraphics graphics, int dialogY) {
        // Determine speaker color
        int speakerColor;
        if (currentSpeakerId.equals(SpeakerLine.PLAYER_SPEAKER_ID)) {
            speakerColor = COLOR_SPEAKER_PLAYER;
        } else if (currentSpeakerId.equals(SpeakerLine.NARRATOR_SPEAKER_ID)) {
            speakerColor = COLOR_NARRATOR;
        } else {
            // Use emotion color if available
            if (currentEmotion != null) {
                speakerColor = currentEmotion.getPrimaryColor() | 0xFF000000;
            } else {
                speakerColor = COLOR_SPEAKER_NPC;
            }
        }

        // Apply transition alpha
        int alpha = (int) (speakerTransition * 255);
        speakerColor = (speakerColor & 0x00FFFFFF) | (alpha << 24);

        // Draw speaker name
        if (!currentSpeakerName.isEmpty()) {
            graphics.drawString(
                font,
                currentSpeakerName,
                PADDING + 8,
                dialogY - SPEAKER_NAME_HEIGHT + 6,
                speakerColor,
                false
            );
        }
    }

    private void renderDialogText(@Nonnull GuiGraphics graphics, int dialogY) {
        String displayText = currentText.substring(0, Math.min(displayedChars, currentText.length()));

        // Word wrap
        int textX = PADDING + 12;
        int textY = dialogY + 12;
        int maxWidth = width - PADDING * 2 - 24;

        List<String> lines = wrapText(displayText, maxWidth);
        for (int i = 0; i < lines.size() && i < 4; i++) {
            graphics.drawString(font, lines.get(i), textX, textY + i * 14, COLOR_TEXT, false);
        }
    }

    private void renderProgress(@Nonnull GuiGraphics graphics, int dialogY) {
        // Line progress indicator (dots)
        int dotX = width - PADDING - 40;
        int dotY = dialogY - 8;

        for (int i = 0; i < totalLines; i++) {
            int dotColor = i <= lineIndex ? DesignTokens.Text.PRIMARY : DesignTokens.Text.MUTED;
            graphics.fill(dotX + i * 8, dotY, dotX + i * 8 + 4, dotY + 4, dotColor);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : Splitter.on(' ').omitEmptyStrings().split(text)) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (font.width(testLine) <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    // ========================================================================
    // Typewriter Effect
    // ========================================================================

    private void updateTypewriter(float partialTick) {
        if (displayedChars < currentText.length()) {
            charTimer += partialTick * CHARS_PER_TICK;
            while (charTimer >= 1.0f && displayedChars < currentText.length()) {
                charTimer -= 1.0f;
                displayedChars++;
            }
        }
    }

    private void skipTypewriter() {
        displayedChars = currentText.length();
    }

    // ========================================================================
    // Input Handling
    // ========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // If typewriter is running, skip it
            if (displayedChars < currentText.length()) {
                skipTypewriter();
                return true;
            }

            // If not showing options and all text displayed, advance
            if (!showOptions && displayedChars >= currentText.length()) {
                requestAdvance();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Space/Enter to advance or skip
        if (keyCode == 32 || keyCode == 257) {
            if (displayedChars < currentText.length()) {
                skipTypewriter();
            } else if (!showOptions) {
                requestAdvance();
            }
            return true;
        }

        // Escape to close
        if (keyCode == 256) {
            requestClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ========================================================================
    // Actions
    // ========================================================================

    private void onOptionSelected(@Nonnull String optionId) {
        // Send option selection to server
        // This would use a network payload in a real implementation
        DevMod.LOGGER.debug("Selected option: {}", optionId);
    }

    private void requestAdvance() {
        // Request next line from server
        // This would use a network payload in a real implementation
        DevMod.LOGGER.debug("Request advance");
    }

    private void requestClose() {
        // Request dialog close from server
        // This would use a network payload in a real implementation
        onClose();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================================================================
    // Factory
    // ========================================================================

    /**
     * Opens the group dialog screen with initial state.
     */
    public static void open(
        @Nonnull UUID sessionId,
        @Nonnull String dialogId,
        @Nonnull String dialogName,
        @Nonnull Map<UUID, String> participantNames,
        @Nonnull UUID currentSpeakerId,
        @Nonnull String currentSpeakerName,
        @Nonnull String currentText,
        @Nullable NpcEmotion currentEmotion,
        int lineIndex,
        int totalLines,
        @Nonnull List<DialogOption> options
    ) {
        Minecraft.getInstance().setScreen(new GroupDialogScreen(
            sessionId,
            dialogId,
            dialogName,
            participantNames,
            currentSpeakerId,
            currentSpeakerName,
            currentText,
            currentEmotion,
            lineIndex,
            totalLines,
            options
        ));
    }
}

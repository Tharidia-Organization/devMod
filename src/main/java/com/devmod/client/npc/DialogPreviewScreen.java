package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.NpcSounds;
import com.devmod.npc.NpcState;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSet;

/**
 * Preview screen for testing dialogs during editing.
 * Simulates the player dialog experience without server interaction.
 *
 * <p>BIBBIA ESTETICA R1/R2: Uses NpcState colors and NpcSounds for authentic preview.
 */
@OnlyIn(Dist.CLIENT)
public class DialogPreviewScreen extends Screen {

    // === Colors (BIBBIA ESTETICA R1) ===
    private static final int COLOR_PANEL_BG = DesignTokens.Nexus.DIALOG_PANEL_BG;
    private static final int COLOR_BORDER_IDLE = NpcState.IDLE.getPrimaryARGB();
    private static final int COLOR_BORDER_TALKING = NpcState.TALKING.getPrimaryARGB();
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_SPEAKER = DesignTokens.Npc.DIALOG_TEXT_PRIMARY;
    private static final int COLOR_PREVIEW_BADGE = DesignTokens.Npc.DIALOG_ERROR_TEXT;

    // === Dimensions ===
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;
    private static final int PADDING = 16;

    // === Typewriter Animation ===
    private static final int CHARS_PER_TICK = 2;
    private static final int TICK_DELAY = 1;

    // === Data ===
    private final DialogSet dialogSet;
    private final String speakerName;
    @Nullable
    private final Screen parentScreen;

    private String currentNodeId;
    @Nullable
    private DialogNode currentNode;

    // === Animation State ===
    private int revealedChars = 0;
    private int tickCounter = 0;
    private boolean textFullyRevealed = false;
    private final List<EditorButtonWidget> optionButtons = new ArrayList<>();

    // === Navigation ===
    @Nullable
    private CycleButton<String> nodeSelector;
    private final List<String> nodeIds = new ArrayList<>();

    public DialogPreviewScreen(
        @Nonnull DialogSet dialogSet,
        @Nonnull String speakerName,
        @Nullable Screen parentScreen
    ) {
        super(Component.translatable("gui.devmod.npc.dialog.preview.title"));
        this.dialogSet = Objects.requireNonNull(dialogSet);
        this.speakerName = Objects.requireNonNull(speakerName);
        this.parentScreen = parentScreen;
        this.currentNodeId = dialogSet.entryNodeId();
        this.nodeIds.addAll(dialogSet.nodes().keySet());
        updateCurrentNode();
    }

    @Override
    protected void init() {
        super.init();
        optionButtons.clear();

        // Play dialog open sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.DIALOG_OPEN);
        }

        // Node selector at top
        int selectorWidth = 200;
        nodeSelector = CycleButton.<String>builder(id -> Component.literal(id))
            .withValues(nodeIds)
            .withInitialValue(currentNodeId)
            .create(
                (width - selectorWidth) / 2,
                10,
                selectorWidth,
                20,
                Component.translatable("gui.devmod.npc.dialog.preview.node"),
                (btn, value) -> goToNode(value)
            );
        addRenderableWidget(nodeSelector);

        // Reset button
        EditorButton resetButton = EditorButton.builder("dialog-preview-reset",
                Component.translatable("gui.devmod.npc.dialog.preview.reset").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> goToNode(dialogSet.entryNodeId()))
            .build();
        addRenderableWidget(new EditorButtonWidget(resetButton, width - 70, 10, 60, 20));

        // Back button
        EditorButton backButton = EditorButton.builder("dialog-preview-back",
                Component.translatable("gui.devmod.npc.dialog.preview.back").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(backButton, 10, 10, 60, 20));

        rebuildOptionButtons();
    }

    private void rebuildOptionButtons() {
        // Remove old option buttons
        for (EditorButtonWidget btn : optionButtons) {
            removeWidget(btn);
        }
        optionButtons.clear();

        if (currentNode == null) return;

        int dialogY = (height - DIALOG_HEIGHT) / 2;
        int buttonY = dialogY + DIALOG_HEIGHT + 10;
        int buttonX = (width - BUTTON_WIDTH) / 2;

        List<DialogOption> options = currentNode.options();
        for (int i = 0; i < options.size(); i++) {
            DialogOption opt = options.get(i);
            String label = opt.icon() + " " + opt.label();

            final int index = i;
            EditorButton optionButton = EditorButton.builder("dialog-preview-option-" + opt.id(), label)
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectOption(options.get(index)))
                .build();
            EditorButtonWidget button = new EditorButtonWidget(optionButton,
                buttonX, buttonY + i * (BUTTON_HEIGHT + BUTTON_SPACING), BUTTON_WIDTH, BUTTON_HEIGHT);

            button.visible = textFullyRevealed;
            optionButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void updateCurrentNode() {
        currentNode = dialogSet.nodes().get(currentNodeId);
        revealedChars = 0;
        tickCounter = 0;
        textFullyRevealed = false;
    }

    private void goToNode(String nodeId) {
        currentNodeId = nodeId;
        updateCurrentNode();
        if (nodeSelector != null) {
            nodeSelector.setValue(nodeId);
        }
        rebuildOptionButtons();
    }

    private void selectOption(DialogOption option) {
        // Play option select sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.OPTION_SELECT);
        }

        // Check action type for navigation
        var action = option.action();
        if (action instanceof com.devmod.npc.dialog.action.DialogAction.GoToNode goTo) {
            String targetNode = goTo.nodeId();
            if (dialogSet.nodes().containsKey(targetNode)) {
                goToNode(targetNode);
            } else {
                // Show error - node not found
                if (minecraft != null && minecraft.level != null) {
                    NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.ACTION_ERROR);
                }
            }
        } else if (action instanceof com.devmod.npc.dialog.action.DialogAction.CloseDialog) {
            onClose();
        } else {
            // Other actions - show info that they would execute
            if (minecraft != null && minecraft.level != null) {
                NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.ACTION_SUCCESS);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!textFullyRevealed && currentNode != null) {
            tickCounter++;
            if (tickCounter >= TICK_DELAY) {
                tickCounter = 0;
                revealedChars += CHARS_PER_TICK;

                int totalChars = getTotalCharCount();
                if (revealedChars >= totalChars) {
                    textFullyRevealed = true;
                    revealedChars = totalChars;

                    for (EditorButtonWidget btn : optionButtons) {
                        btn.visible = true;
                    }
                }
            }
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        renderDialogPanel(graphics, dialogX, dialogY);

        var safeFont = Objects.requireNonNull(font, "font");

        // Speaker name
        graphics.drawString(safeFont, speakerName, dialogX + PADDING, dialogY + PADDING - 4, COLOR_SPEAKER);

        // Preview badge
        String previewBadge = "[PREVIEW]";
        int badgeWidth = safeFont.width(previewBadge);
        graphics.drawString(safeFont, previewBadge, dialogX + DIALOG_WIDTH - PADDING - badgeWidth, dialogY + PADDING - 4, COLOR_PREVIEW_BADGE);

        // Separator line
        int borderColor = textFullyRevealed ? COLOR_BORDER_IDLE : COLOR_BORDER_TALKING;
        graphics.fill(dialogX + PADDING, dialogY + PADDING + 10,
            dialogX + DIALOG_WIDTH - PADDING, dialogY + PADDING + 11, borderColor);

        // Dialog text
        renderDialogText(graphics, dialogX + PADDING, dialogY + PADDING + 20);

        // Skip hint
        if (!textFullyRevealed) {
            String hint = "[Click to skip]";
            int hintWidth = safeFont.width(hint);
            graphics.drawString(safeFont, hint, dialogX + DIALOG_WIDTH - PADDING - hintWidth,
                dialogY + DIALOG_HEIGHT - PADDING, COLOR_TEXT_DIM);
        }

        // Node info at bottom
        if (currentNode != null) {
            String nodeInfo = "Node: " + currentNodeId + " | Options: " + currentNode.options().size();
            graphics.drawString(safeFont, nodeInfo, dialogX + PADDING, dialogY + DIALOG_HEIGHT - PADDING, COLOR_TEXT_DIM);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDialogPanel(GuiGraphics graphics, int x, int y) {
        int borderColor = textFullyRevealed ? COLOR_BORDER_IDLE : COLOR_BORDER_TALKING;
        int glowColor = NpcState.TALKING.getSecondaryWithAlpha(0x88);

        // Glow effect
        graphics.fill(x - 2, y - 2, x + DIALOG_WIDTH + 2, y + DIALOG_HEIGHT + 2, glowColor);
        // Background
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, COLOR_PANEL_BG);
        // Borders
        graphics.fill(x, y, x + DIALOG_WIDTH, y + 1, borderColor);
        graphics.fill(x, y + DIALOG_HEIGHT - 1, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, borderColor);
        graphics.fill(x, y, x + 1, y + DIALOG_HEIGHT, borderColor);
        graphics.fill(x + DIALOG_WIDTH - 1, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, borderColor);
    }

    private void renderDialogText(GuiGraphics graphics, int x, int y) {
        if (currentNode == null) {
            graphics.drawString(font, "[No node selected]", x, y, COLOR_TEXT_DIM);
            return;
        }

        var safeFont = Objects.requireNonNull(font, "font");
        int charsDrawn = 0;
        int currentY = y;
        int lineHeight = safeFont.lineHeight + 2;
        int maxWidth = DIALOG_WIDTH - PADDING * 2;

        for (String line : currentNode.lines()) {
            if (line.isEmpty()) {
                currentY += lineHeight / 2;
                continue;
            }

            List<String> wrappedLines = wrapLine(line, maxWidth);
            for (String wrappedLine : wrappedLines) {
                int lineLength = wrappedLine.length();
                int charsToShow = Math.min(lineLength, Math.max(0, revealedChars - charsDrawn));

                if (charsToShow > 0) {
                    String visibleText = wrappedLine.substring(0, charsToShow);
                    graphics.drawString(safeFont, visibleText, x, currentY, COLOR_TEXT);
                }

                charsDrawn += lineLength;
                currentY += lineHeight;

                if (charsDrawn >= revealedChars && !textFullyRevealed) {
                    return;
                }
            }
        }
    }

    private List<String> wrapLine(String line, int maxWidth) {
        var safeFont = Objects.requireNonNull(font, "font");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : line.split(" ", -1)) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (safeFont.width(test) <= maxWidth) {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    result.add(word);
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private int getTotalCharCount() {
        if (currentNode == null) return 0;

        int total = 0;
        int maxWidth = DIALOG_WIDTH - PADDING * 2;
        for (String line : currentNode.lines()) {
            if (line.isEmpty()) continue;
            List<String> wrapped = wrapLine(line, maxWidth);
            for (String w : wrapped) {
                total += w.length();
            }
        }
        return total;
    }

    @Override
    public void onClose() {
        // Play dialog close sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.DIALOG_CLOSE);
        }

        if (parentScreen != null && minecraft != null) {
            minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!textFullyRevealed && button == 0) {
            textFullyRevealed = true;
            revealedChars = getTotalCharCount();
            for (EditorButtonWidget btn : optionButtons) {
                btn.visible = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (!textFullyRevealed) {
                textFullyRevealed = true;
                revealedChars = getTotalCharCount();
                for (EditorButtonWidget btn : optionButtons) {
                    btn.visible = true;
                }
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == 32 && !textFullyRevealed) { // Space
            textFullyRevealed = true;
            revealedChars = getTotalCharCount();
            for (EditorButtonWidget btn : optionButtons) {
                btn.visible = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

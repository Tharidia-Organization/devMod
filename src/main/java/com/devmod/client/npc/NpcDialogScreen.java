package com.devmod.client.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.NpcSounds;
import com.devmod.npc.NpcState;
import com.devmod.npc.network.NpcDialogActionPayload;
import com.devmod.npc.network.NpcDialogPayload.NpcDialogOptionData;

/**
 * Client screen for NPC dialogs.
 * Displays dialog text with typewriter animation and selectable options.
 */
@OnlyIn(Dist.CLIENT)
public class NpcDialogScreen extends Screen {

    // === Colors (BIBBIA ESTETICA R1) ===
    private static final int COLOR_PANEL_BG = DesignTokens.Nexus.DIALOG_PANEL_BG;
    private static final int COLOR_BORDER_IDLE = NpcState.IDLE.getPrimaryARGB();
    private static final int COLOR_BORDER_TALKING = NpcState.TALKING.getPrimaryARGB();
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_SPEAKER = DesignTokens.Npc.DIALOG_TEXT_PRIMARY;

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
    private final UUID npcId;
    private final String speakerName;
    private final String dialogSetId;
    private final String currentNodeId;
    private final List<String> lines;
    private final List<NpcDialogOptionData> options;

    // === Animation State ===
    private int revealedChars = 0;
    private int tickCounter = 0;
    private boolean textFullyRevealed = false;
    private final List<EditorButtonWidget> optionButtons = new ArrayList<>();

    public NpcDialogScreen(
        UUID npcId,
        String speakerName,
        String dialogSetId,
        String currentNodeId,
        List<String> lines,
        List<NpcDialogOptionData> options
    ) {
        super(Component.literal("NPC Dialog"));
        this.npcId = Objects.requireNonNull(npcId);
        this.speakerName = Objects.requireNonNull(speakerName);
        this.dialogSetId = Objects.requireNonNull(dialogSetId);
        this.currentNodeId = Objects.requireNonNull(currentNodeId);
        this.lines = Objects.requireNonNull(lines);
        this.options = Objects.requireNonNull(options);
    }

    @Override
    protected void init() {
        super.init();
        optionButtons.clear();

        // Play dialog open sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.DIALOG_OPEN);
        }

        int dialogY = (height - DIALOG_HEIGHT) / 2;
        int buttonY = dialogY + DIALOG_HEIGHT + 10;
        int buttonX = (width - BUTTON_WIDTH) / 2;

        for (int i = 0; i < options.size(); i++) {
            NpcDialogOptionData opt = options.get(i);
            String label = opt.icon() + " " + opt.label();

            EditorButton optionButton = EditorButton.builder("npc-dialog-option-" + opt.id(), label)
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectOption(opt))
                .build();
            EditorButtonWidget button = new EditorButtonWidget(optionButton,
                buttonX, buttonY + i * (BUTTON_HEIGHT + BUTTON_SPACING), BUTTON_WIDTH, BUTTON_HEIGHT);

            button.visible = textFullyRevealed;
            optionButtons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!textFullyRevealed) {
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
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        renderDialogPanel(graphics, dialogX, dialogY);

        var safeFont = Objects.requireNonNull(font, "font");
        UIScaleManager.drawScaledString(graphics, safeFont, speakerName, dialogX + PADDING, dialogY + PADDING - 4, COLOR_SPEAKER);

        int borderColor = textFullyRevealed ? COLOR_BORDER_IDLE : COLOR_BORDER_TALKING;
        graphics.fill(dialogX + PADDING, dialogY + PADDING + 10,
            dialogX + DIALOG_WIDTH - PADDING, dialogY + PADDING + 11, borderColor);

        renderDialogText(graphics, dialogX + PADDING, dialogY + PADDING + 20);

        if (!textFullyRevealed) {
            String hint = "[Click to skip]";
            int hintWidth = UIScaleManager.getScaledStringWidth(safeFont, hint);
            UIScaleManager.drawScaledString(graphics, safeFont, hint, dialogX + DIALOG_WIDTH - PADDING - hintWidth,
                dialogY + DIALOG_HEIGHT - PADDING, COLOR_TEXT_DIM);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDialogPanel(GuiGraphics graphics, int x, int y) {
        // Border color changes based on dialog state (BIBBIA ESTETICA R1)
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
        var safeFont = Objects.requireNonNull(font, "font");
        int charsDrawn = 0;
        int currentY = y;
        int lineHeight = UIScaleManager.getScaledLineHeight();
        int maxWidth = DIALOG_WIDTH - PADDING * 2;

        for (String line : lines) {
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
                    UIScaleManager.drawScaledString(graphics, safeFont, visibleText, x, currentY, COLOR_TEXT);
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
            if (UIScaleManager.getScaledStringWidth(safeFont, test) <= maxWidth) {
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
        int total = 0;
        int maxWidth = DIALOG_WIDTH - PADDING * 2;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            List<String> wrapped = wrapLine(line, maxWidth);
            for (String w : wrapped) {
                total += w.length();
            }
        }
        return total;
    }

    private void selectOption(NpcDialogOptionData option) {
        // Play option select sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.OPTION_SELECT);
        }

        // Send action to server
        PacketDistributor.sendToServer(new NpcDialogActionPayload(
            npcId,
            dialogSetId,
            currentNodeId,
            option.id()
        ));

        // Close screen if it's a close/farewell option
        if ("close".equals(option.id()) || "farewell".equals(option.id())) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        // Play dialog close sound (BIBBIA ESTETICA R2)
        if (minecraft != null && minecraft.level != null) {
            NpcSounds.playPhaseClient(minecraft.level, NpcSounds.Phase.DIALOG_CLOSE);
        }
        super.onClose();
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

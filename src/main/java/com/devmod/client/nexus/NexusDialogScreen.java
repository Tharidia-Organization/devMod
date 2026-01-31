package com.devmod.client.nexus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
import com.devmod.runtime.network.NexusDialogActionPayload;
import com.devmod.runtime.network.NexusDialogPayload.DialogOptionData;

/**
 * Client screen for Nexus avatar dialog.
 * Displays dialog text with typewriter animation and selectable options.
 */
@OnlyIn(Dist.CLIENT)
public class NexusDialogScreen extends Screen {

    // === Colors ===
    private static final int COLOR_PANEL_BG = DesignTokens.Nexus.DIALOG_PANEL_BG;
    private static final int COLOR_BORDER = DesignTokens.Nexus.DIALOG_BORDER;
    private static final int COLOR_BORDER_GLOW = DesignTokens.Nexus.DIALOG_BORDER_GLOW;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_SPEAKER = DesignTokens.Nexus.DIALOG_SPEAKER;

    // === Dimensions ===
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;
    private static final int PADDING = 16;

    // === Typewriter Animation ===
    private static final int CHARS_PER_TICK = 2;  // Characters revealed per tick
    private static final int TICK_DELAY = 1;      // Ticks between character reveals

    // === Data ===
    private final String speakerName;
    private final List<String> lines;
    private final List<DialogOptionData> options;

    // === Animation State ===
    private int revealedChars = 0;
    private int tickCounter = 0;
    private boolean textFullyRevealed = false;
    private final List<EditorButtonWidget> optionButtons = new ArrayList<>();

    public NexusDialogScreen(String speakerName, List<String> lines, List<DialogOptionData> options) {
        super(Component.literal("Nexus Dialog"));
        this.speakerName = Objects.requireNonNull(speakerName);
        this.lines = Objects.requireNonNull(lines);
        this.options = Objects.requireNonNull(options);
    }

    @Override
    protected void init() {
        super.init();
        optionButtons.clear();

        // Calculate dialog position
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Create option buttons (initially hidden until text is revealed)
        int buttonY = dialogY + DIALOG_HEIGHT + 10;
        int buttonX = (width - BUTTON_WIDTH) / 2;

        for (int i = 0; i < options.size(); i++) {
            DialogOptionData opt = options.get(i);
            String label = opt.icon() + " " + opt.label();

            EditorButton button = EditorButton.builder("nexus_dialog_opt_" + i, Objects.requireNonNull(label, "label"))
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectOption(opt))
                .build();
            EditorButtonWidget widget = new EditorButtonWidget(button,
                buttonX, buttonY + i * (BUTTON_HEIGHT + BUTTON_SPACING), BUTTON_WIDTH, BUTTON_HEIGHT);
            widget.visible = textFullyRevealed;
            optionButtons.add(widget);
            addRenderableWidget(widget);
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Typewriter animation
        if (!textFullyRevealed) {
            tickCounter++;
            if (tickCounter >= TICK_DELAY) {
                tickCounter = 0;
                revealedChars += CHARS_PER_TICK;

                // Check if all text is revealed
                int totalChars = getTotalCharCount();
                if (revealedChars >= totalChars) {
                    textFullyRevealed = true;
                    revealedChars = totalChars;

                    // Show option buttons
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
        // Semi-transparent background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Draw dialog panel with glow
        renderDialogPanel(graphics, dialogX, dialogY);

        // Draw speaker name
        var safeFont = Objects.requireNonNull(font, "font");
        graphics.drawString(safeFont, speakerName, dialogX + PADDING, dialogY + PADDING - 4, COLOR_SPEAKER);

        // Draw divider line under speaker name
        graphics.fill(dialogX + PADDING, dialogY + PADDING + 10,
            dialogX + DIALOG_WIDTH - PADDING, dialogY + PADDING + 11, COLOR_BORDER);

        // Draw dialog text with typewriter effect
        renderDialogText(graphics, dialogX + PADDING, dialogY + PADDING + 20);

        // Draw hint if text not fully revealed
        if (!textFullyRevealed) {
            String hint = "[Click to skip]";
            int hintWidth = safeFont.width(hint);
            graphics.drawString(safeFont, hint, dialogX + DIALOG_WIDTH - PADDING - hintWidth,
                dialogY + DIALOG_HEIGHT - PADDING, COLOR_TEXT_DIM);
        }

        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDialogPanel(GuiGraphics graphics, int x, int y) {
        // Outer glow
        graphics.fill(x - 2, y - 2, x + DIALOG_WIDTH + 2, y + DIALOG_HEIGHT + 2, COLOR_BORDER_GLOW);

        // Panel background
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, COLOR_PANEL_BG);

        // Border
        // Top
        graphics.fill(x, y, x + DIALOG_WIDTH, y + 1, COLOR_BORDER);
        // Bottom
        graphics.fill(x, y + DIALOG_HEIGHT - 1, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, COLOR_BORDER);
        // Left
        graphics.fill(x, y, x + 1, y + DIALOG_HEIGHT, COLOR_BORDER);
        // Right
        graphics.fill(x + DIALOG_WIDTH - 1, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, COLOR_BORDER);
    }

    private void renderDialogText(GuiGraphics graphics, int x, int y) {
        var safeFont = Objects.requireNonNull(font, "font");
        int charsDrawn = 0;
        int currentY = y;
        int lineHeight = safeFont.lineHeight + 2;
        int maxWidth = DIALOG_WIDTH - PADDING * 2;

        for (String line : lines) {
            if (line.isEmpty()) {
                currentY += lineHeight / 2;  // Empty line = half spacing
                continue;
            }

            // Word wrap the line
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
                    return;  // Stop rendering if we've hit the typewriter limit
                }
            }
        }
    }

    private List<String> wrapLine(String line, int maxWidth) {
        var safeFont = Objects.requireNonNull(font, "font");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : line.split(" ", -1)) {
            String test = Objects.requireNonNull(current.length() == 0 ? word : current + " " + word, "test");
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

    private void selectOption(DialogOptionData option) {
        // Send action to server
        PacketDistributor.sendToServer(new NexusDialogActionPayload(
            option.id(),
            option.nextDialogType()
        ));

        // If farewell, close the screen
        if ("farewell".equals(option.id()) || "FAREWELL".equals(option.nextDialogType())) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Click to skip typewriter animation
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
        // ESC or space to skip/close
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
        return false;  // Don't pause the game
    }
}

package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.editor.core.EditorSpacing;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal layout component for arranging multiple EditorButtons in a row.
 * Supports configurable gap between buttons and alignment options.
 *
 * <p>Example usage:
 * <pre>
 * ButtonRow row = new ButtonRow()
 *     .add(cancelButton)
 *     .add(applyButton)
 *     .gap(EditorSpacing.S)
 *     .alignment(ButtonRow.Alignment.RIGHT);
 *
 * row.render(graphics, x, y, availableWidth, mouseX, mouseY);
 * </pre>
 *
 * @see EditorButton
 */
public class ButtonRow {

    /**
     * Alignment options for the button row within its container.
     */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final List<EditorButton> buttons = new ArrayList<>();
    private int gap = EditorSpacing.S;
    private Alignment alignment = Alignment.LEFT;
    private int buttonWidth = 0;  // 0 = auto (use each button's natural width)
    private int buttonHeight = EditorButton.Size.MEDIUM.height();

    /**
     * Add a button to the row.
     *
     * @param button The button to add
     * @return this for chaining
     */
    public ButtonRow add(EditorButton button) {
        if (button != null) {
            buttons.add(button);
        }
        return this;
    }

    /**
     * Set the gap between buttons.
     *
     * @param gap Gap in pixels (use EditorSpacing constants)
     * @return this for chaining
     */
    public ButtonRow gap(int gap) {
        this.gap = gap;
        return this;
    }

    /**
     * Set the alignment of buttons within the row.
     *
     * @param alignment The alignment
     * @return this for chaining
     */
    public ButtonRow alignment(Alignment alignment) {
        this.alignment = alignment;
        return this;
    }

    /**
     * Set a fixed width for all buttons. Use 0 for auto-width.
     *
     * @param width Button width in pixels, or 0 for auto
     * @return this for chaining
     */
    public ButtonRow buttonWidth(int width) {
        this.buttonWidth = width;
        return this;
    }

    /**
     * Set the height for all buttons.
     *
     * @param height Button height in pixels
     * @return this for chaining
     */
    public ButtonRow buttonHeight(int height) {
        this.buttonHeight = height;
        return this;
    }

    /**
     * Get the list of buttons in this row.
     */
    public List<EditorButton> getButtons() {
        return buttons;
    }

    /**
     * Calculate the total width needed for all buttons and gaps.
     *
     * @param defaultButtonWidth Default width to use when buttonWidth is 0
     * @return Total width in pixels
     */
    public int calculateTotalWidth(int defaultButtonWidth) {
        if (buttons.isEmpty()) return 0;

        int width = buttonWidth > 0 ? buttonWidth : defaultButtonWidth;
        return buttons.size() * width + (buttons.size() - 1) * gap;
    }

    /**
     * Render all buttons in a horizontal row.
     *
     * @param graphics       The graphics context
     * @param x              Left edge X position
     * @param y              Top edge Y position
     * @param availableWidth Available width for the row (used for alignment)
     * @param mouseX         Current mouse X position
     * @param mouseY         Current mouse Y position
     */
    public void render(GuiGraphics graphics, int x, int y, int availableWidth, int mouseX, int mouseY) {
        if (buttons.isEmpty()) return;

        int defaultWidth = 80;  // Default button width if not specified
        int width = buttonWidth > 0 ? buttonWidth : defaultWidth;
        int totalWidth = calculateTotalWidth(defaultWidth);

        // Calculate starting X based on alignment
        int startX = switch (alignment) {
            case LEFT -> x;
            case CENTER -> x + (availableWidth - totalWidth) / 2;
            case RIGHT -> x + availableWidth - totalWidth;
        };

        int currentX = startX;
        for (EditorButton button : buttons) {
            button.render(graphics, currentX, y, width, buttonHeight, mouseX, mouseY);
            currentX += width + gap;
        }
    }

    /**
     * Handle mouse click events for all buttons in the row.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0 = left)
     * @return true if any button consumed the click
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditorButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle mouse release events for all buttons in the row.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button
     * @return true if any button consumed the release
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = false;
        for (EditorButton btn : buttons) {
            if (btn.mouseReleased(mouseX, mouseY, button)) {
                consumed = true;
            }
        }
        return consumed;
    }

    /**
     * Clear all buttons from the row.
     */
    public void clear() {
        buttons.clear();
    }
}

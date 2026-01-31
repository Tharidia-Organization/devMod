package com.devmod.client.ui.editor.components;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ResponsiveLayout;

public class EditorTextField {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int CURSOR_BLINK_MS = 500;
    private static final int LABEL_LINE_HEIGHT = 12;
    private static final int TEXT_HEIGHT = 8;
    private static final int TEXT_PADDING = 4;
    private static final int INPUT_PADDING = 14;
    private static final int CURSOR_WIDTH = 1;
    private static final int CURSOR_INSET = 2;
    private static final int SELECTION_ALPHA = 0x60;
    private static final int CURSOR_SCROLL_MARGIN = 10;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private final String label;
    private String placeholder = "";
    private int maxLength = 256;
    private boolean numeric = false;
    private float numericMin = Float.MIN_VALUE;
    private float numericMax = Float.MAX_VALUE;

    // State
    private String value = "";
    private boolean focused = false;
    private boolean hovered = false;
    private boolean enabled = true;
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int scrollOffset = 0;
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect inputBounds = ResponsiveLayout.Rect.EMPTY;

    // Validation
    private Predicate<String> validator = value -> true;
    private boolean valid = true;

    // Callback
    private Consumer<String> onChange = value -> {};
    private Runnable onSubmit = () -> {};

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public EditorTextField(String id, String label) {
        this.id = id;
        this.label = label;
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public EditorTextField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public EditorTextField maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public EditorTextField numeric(boolean numeric) {
        this.numeric = numeric;
        return this;
    }

    public EditorTextField numericRange(float min, float max) {
        this.numeric = true;
        this.numericMin = min;
        this.numericMax = max;
        return this;
    }

    public EditorTextField validator(Predicate<String> validator) {
        this.validator = validator != null ? validator : value -> true;
        return this;
    }

    public EditorTextField enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public EditorTextField onChange(Consumer<String> callback) {
        this.onChange = callback != null ? callback : value -> {};
        return this;
    }

    public EditorTextField onSubmit(Runnable callback) {
        this.onSubmit = callback != null ? callback : () -> {};
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the text field at the given position.
     * @return The total height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int height = DesignTokens.Size.INPUT_HEIGHT + INPUT_PADDING; // Label + input
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Check hover
        this.hovered = enabled && bounds.contains(mouseX, mouseY);

        int currentY = y;

        // Label
        if (label != null && !label.isEmpty()) {
            int labelColor = enabled ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.MUTED();
            UIScaleManager.drawScaledString(graphics, font, label, x, currentY, labelColor, false);
            currentY += LABEL_LINE_HEIGHT;
        }

        // Input field
        int inputHeight = DesignTokens.Size.INPUT_HEIGHT;
        this.inputBounds = new ResponsiveLayout.Rect(x, currentY, width, inputHeight);

        // Background
        int bgColor = enabled ? DesignTokens.Background.INPUT() : DesignTokens.Background.DARKER();
        graphics.fill(x, currentY, x + width, currentY + inputHeight, bgColor);

        // Border
        int borderColor;
        if (!valid) {
            borderColor = DesignTokens.Border.ERROR();
        } else if (focused) {
            borderColor = DesignTokens.Border.ACCENT();
        } else if (hovered) {
            borderColor = DesignTokens.Border.HOVER();
        } else {
            borderColor = DesignTokens.Border.DEFAULT();
        }
        AxiomRenderer.drawBorder(graphics, x, currentY, width, inputHeight, borderColor);

        // Enable scissor for text clipping
        int textPadding = TEXT_PADDING;
        graphics.enableScissor(x + textPadding, currentY, x + width - textPadding, currentY + inputHeight);

        // Text content
        int textY = currentY + (inputHeight - TEXT_HEIGHT) / 2;
        int textX = x + textPadding - scrollOffset;

        if (value.isEmpty() && !focused) {
            // Placeholder
            UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(placeholder, "placeholder cannot be null"), textX, textY, DesignTokens.Text.MUTED(), false);
        } else {
            // Actual text
            int textColor = enabled ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.MUTED();
            UIScaleManager.drawScaledString(graphics, font, Objects.requireNonNull(value, "value cannot be null"), textX, textY, textColor, false);

            // Selection highlight
            if (focused && selectionStart >= 0 && selectionStart != cursorPosition) {
                int selStart = Math.min(selectionStart, cursorPosition);
                int selEnd = Math.max(selectionStart, cursorPosition);
                String prefixStart = Objects.requireNonNull(value.substring(0, selStart), "selection prefix cannot be null");
                String prefixEnd = Objects.requireNonNull(value.substring(0, selEnd), "selection end prefix cannot be null");
                int selX1 = textX + UIScaleManager.getScaledStringWidth(font, prefixStart);
                int selX2 = textX + UIScaleManager.getScaledStringWidth(font, prefixEnd);
                graphics.fill(selX1, currentY + CURSOR_INSET, selX2, currentY + inputHeight - CURSOR_INSET,
                             DesignTokens.withAlpha(DesignTokens.Accent.CYAN(), SELECTION_ALPHA));
            }

            // Cursor
            if (focused && cursorVisible) {
                String cursorPrefix = Objects.requireNonNull(value.substring(0, cursorPosition), "cursor prefix cannot be null");
                int cursorX = textX + UIScaleManager.getScaledStringWidth(font, cursorPrefix);
                graphics.fill(cursorX, currentY + CURSOR_INSET, cursorX + CURSOR_WIDTH, currentY + inputHeight - CURSOR_INSET,
                             DesignTokens.Text.PRIMARY());
            }
        }

        graphics.disableScissor();

        return height;
    }

    public int calculateHeight() {
        return DesignTokens.Size.INPUT_HEIGHT + INPUT_PADDING;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled) return false;

        if (inputBounds.contains(mouseX, mouseY)) {
            if (!focused) {
                focused = true;
                cursorPosition = value.length();
                selectionStart = -1;
            } else {
                // Calculate cursor position from click
                int relativeX = (int) mouseX - inputBounds.x() - TEXT_PADDING + scrollOffset;
                cursorPosition = getCharacterIndexAt(relativeX);
                selectionStart = -1;
            }
            return true;
        } else if (focused) {
            focused = false;
            validateAndCommit();
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !enabled) return false;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                validateAndCommit();
                onSubmit.run();
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) {
                    if (selectionStart < 0) selectionStart = cursorPosition;
                } else {
                    selectionStart = -1;
                }
                if (ctrl) {
                    cursorPosition = findWordBoundary(cursorPosition, -1);
                } else if (cursorPosition > 0) {
                    cursorPosition--;
                }
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) {
                    if (selectionStart < 0) selectionStart = cursorPosition;
                } else {
                    selectionStart = -1;
                }
                if (ctrl) {
                    cursorPosition = findWordBoundary(cursorPosition, 1);
                } else if (cursorPosition < value.length()) {
                    cursorPosition++;
                }
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shift && selectionStart < 0) selectionStart = cursorPosition;
                else if (!shift) selectionStart = -1;
                cursorPosition = 0;
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (shift && selectionStart < 0) selectionStart = cursorPosition;
                else if (!shift) selectionStart = -1;
                cursorPosition = value.length();
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    if (ctrl) {
                        int wordStart = findWordBoundary(cursorPosition, -1);
                        value = value.substring(0, wordStart) + value.substring(cursorPosition);
                        cursorPosition = wordStart;
                    } else {
                        value = value.substring(0, cursorPosition - 1) + value.substring(cursorPosition);
                        cursorPosition--;
                    }
                    notifyChange();
                }
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < value.length()) {
                    if (ctrl) {
                        int wordEnd = findWordBoundary(cursorPosition, 1);
                        value = value.substring(0, cursorPosition) + value.substring(wordEnd);
                    } else {
                        value = value.substring(0, cursorPosition) + value.substring(cursorPosition + 1);
                    }
                    notifyChange();
                }
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    selectionStart = 0;
                    cursorPosition = value.length();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && hasSelection()) {
                    String selected = Objects.requireNonNull(getSelectedText(), "selected text cannot be null");
                    Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && hasSelection()) {
                    String selected = Objects.requireNonNull(getSelectedText(), "selected text cannot be null");
                    Minecraft.getInstance().keyboardHandler.setClipboard(selected);
                    deleteSelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                    insertText(clipboard);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused || !enabled) return false;

        // Filter for numeric mode
        if (numeric) {
            if (!Character.isDigit(chr) && chr != '.' && chr != '-') {
                return false;
            }
            // Only allow one decimal point
            if (chr == '.' && value.contains(".")) {
                return false;
            }
            // Only allow minus at start
            if (chr == '-' && cursorPosition != 0) {
                return false;
            }
        }

        // Check max length
        if (value.length() >= maxLength && !hasSelection()) {
            return false;
        }

        // Insert character
        if (hasSelection()) {
            deleteSelection();
        }

        value = value.substring(0, cursorPosition) + chr + value.substring(cursorPosition);
        cursorPosition++;
        ensureCursorVisible();
        notifyChange();

        return true;
    }

    public void tick() {
        // Cursor blink
        long now = System.currentTimeMillis();
        if (now - lastCursorBlink > CURSOR_BLINK_MS) {
            cursorVisible = !cursorVisible;
            lastCursorBlink = now;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursorPosition;
    }

    private String getSelectedText() {
        if (!hasSelection()) return "";
        int start = Math.min(selectionStart, cursorPosition);
        int end = Math.max(selectionStart, cursorPosition);
        return value.substring(start, end);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = Math.min(selectionStart, cursorPosition);
        int end = Math.max(selectionStart, cursorPosition);
        value = value.substring(0, start) + value.substring(end);
        cursorPosition = start;
        selectionStart = -1;
        notifyChange();
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) return;

        if (hasSelection()) {
            deleteSelection();
        }

        // Filter for numeric mode
        if (numeric) {
            text = text.replaceAll("[^0-9.\\-]", "");
        }

        // Trim to max length
        int available = maxLength - value.length();
        if (text.length() > available) {
            text = text.substring(0, available);
        }

        value = value.substring(0, cursorPosition) + text + value.substring(cursorPosition);
        cursorPosition += text.length();
        ensureCursorVisible();
        notifyChange();
    }

    private int findWordBoundary(int pos, int direction) {
        if (direction < 0) {
            while (pos > 0 && Character.isWhitespace(value.charAt(pos - 1))) pos--;
            while (pos > 0 && !Character.isWhitespace(value.charAt(pos - 1))) pos--;
        } else {
            while (pos < value.length() && !Character.isWhitespace(value.charAt(pos))) pos++;
            while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) pos++;
        }
        return pos;
    }

    private int getCharacterIndexAt(int pixelX) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int bestIndex = 0;
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i <= value.length(); i++) {
            String prefix = Objects.requireNonNull(value.substring(0, i), "substring cannot be null");
            int charX = UIScaleManager.getScaledStringWidth(font, prefix);
            int dist = Math.abs(charX - pixelX);
            if (dist < minDist) {
                minDist = dist;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private void ensureCursorVisible() {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        String cursorPrefix = Objects.requireNonNull(value.substring(0, cursorPosition), "cursor prefix cannot be null");
        int cursorX = UIScaleManager.getScaledStringWidth(font, cursorPrefix);
        int visibleWidth = inputBounds.width() - TEXT_PADDING * 2;

        if (cursorX - scrollOffset < 0) {
            scrollOffset = Math.max(0, cursorX - CURSOR_SCROLL_MARGIN);
        } else if (cursorX - scrollOffset > visibleWidth) {
            scrollOffset = cursorX - visibleWidth + CURSOR_SCROLL_MARGIN;
        }
    }

    private void notifyChange() {
        validate();
        onChange.accept(value);
    }

    private void validate() {
        boolean baseValid = validator.test(value);
        if (numeric && !value.isEmpty()) {
            try {
                float num = Float.parseFloat(value);
                boolean numericValid = num >= numericMin && num <= numericMax;
                valid = baseValid && numericValid;
            } catch (NumberFormatException e) {
                valid = false;
            }
        } else {
            valid = baseValid;
        }
    }

    private void validateAndCommit() {
        validate();
        if (numeric && valid && !value.isEmpty()) {
            // Clamp numeric value
            float num = Float.parseFloat(value);
            num = Mth.clamp(num, numericMin, numericMax);
            value = String.valueOf(num);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
        this.cursorPosition = Mth.clamp(cursorPosition, 0, this.value.length());
        validate();
    }

    public float getNumericValue() {
        if (!numeric || value.isEmpty()) return 0;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setNumericValue(float num) {
        this.value = String.valueOf(num);
        this.cursorPosition = value.length();
        validate();
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            cursorPosition = value.length();
            lastCursorBlink = System.currentTimeMillis();
            cursorVisible = true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }
}

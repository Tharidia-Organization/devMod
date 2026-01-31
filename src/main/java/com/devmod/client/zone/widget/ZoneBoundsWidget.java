package com.devmod.client.zone.widget;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.zone.data.ZoneBounds;

/**
 * Widget for editing zone bounds coordinates.
 * Shows 6 coordinate fields (minX, maxX, minY, maxY, minZ, maxZ) in a grid layout.
 */
@OnlyIn(Dist.CLIENT)
public class ZoneBoundsWidget extends AbstractWidget {

    private static final int FIELD_WIDTH = 60;
    private static final int FIELD_HEIGHT = 18;
    private static final int LABEL_WIDTH = 40;
    private static final int ROW_HEIGHT = 26;
    private static final int COL_SPACING = 20;

    private static final int COLOR_LABEL = DesignTokens.Text.MUTED();
    private static final int COLOR_VALUE = DesignTokens.Text.PRIMARY();

    private final Consumer<ZoneBounds> onChange;
    private final Font font;

    // Coordinate values
    private int minX, maxX;
    private int minY, maxY;
    private int minZ, maxZ;

    // Edit boxes
    private EditBox minXField;
    private EditBox maxXField;
    private EditBox minYField;
    private EditBox maxYField;
    private EditBox minZField;
    private EditBox maxZField;

    @SuppressWarnings("this-escape")
    public ZoneBoundsWidget(int x, int y, int width, int height,
                           @Nonnull ZoneBounds initialBounds,
                           @Nonnull Consumer<ZoneBounds> onChange) {
        super(x, y, width, height, Component.translatable("zone.editor.bounds"));
        this.onChange = onChange;
        this.font = Minecraft.getInstance().font;

        // Initialize from bounds
        this.minX = initialBounds.minX();
        this.maxX = initialBounds.maxX();
        this.minY = initialBounds.minY();
        this.maxY = initialBounds.maxY();
        this.minZ = initialBounds.minZ();
        this.maxZ = initialBounds.maxZ();

        // Create edit boxes
        createFields();
    }

    private void createFields() {
        int fieldX = getX() + LABEL_WIDTH + 10;
        int fieldX2 = fieldX + FIELD_WIDTH + COL_SPACING + LABEL_WIDTH + 10;

        // Row 1: Min X, Max X
        minXField = createIntField(fieldX, getY(), minX, v -> { minX = v; notifyChange(); });
        maxXField = createIntField(fieldX2, getY(), maxX, v -> { maxX = v; notifyChange(); });

        // Row 2: Min Y, Max Y
        minYField = createIntField(fieldX, getY() + ROW_HEIGHT, minY, v -> { minY = v; notifyChange(); });
        maxYField = createIntField(fieldX2, getY() + ROW_HEIGHT, maxY, v -> { maxY = v; notifyChange(); });

        // Row 3: Min Z, Max Z
        minZField = createIntField(fieldX, getY() + ROW_HEIGHT * 2, minZ, v -> { minZ = v; notifyChange(); });
        maxZField = createIntField(fieldX2, getY() + ROW_HEIGHT * 2, maxZ, v -> { maxZ = v; notifyChange(); });
    }

    private EditBox createIntField(int x, int y, int initialValue, Consumer<Integer> onValue) {
        EditBox field = new EditBox(font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        field.setValue(String.valueOf(initialValue));
        field.setFilter(this::isValidIntInput);
        field.setBordered(false);
        field.setTextColor(DesignTokens.Text.PRIMARY());
        field.setTextColorUneditable(DesignTokens.Text.MUTED());
        field.setResponder(text -> {
            try {
                int value = Integer.parseInt(text);
                onValue.accept(value);
            } catch (NumberFormatException ignored) {
                // Keep current value if invalid
            }
        });
        return field;
    }

    private boolean isValidIntInput(String text) {
        if (text.isEmpty() || text.equals("-")) return true;
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void notifyChange() {
        ZoneBounds newBounds = new ZoneBounds(minX, maxX, minY, maxY, minZ, maxZ).normalized();
        onChange.accept(newBounds);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int labelX2 = x + LABEL_WIDTH + 10 + FIELD_WIDTH + COL_SPACING;

        // Draw labels
        UIScaleManager.drawScaledString(graphics, font, "Min X:", x, y + 5, COLOR_LABEL, false);
        UIScaleManager.drawScaledString(graphics, font, "Max X:", labelX2, y + 5, COLOR_LABEL, false);

        UIScaleManager.drawScaledString(graphics, font, "Min Y:", x, y + ROW_HEIGHT + 5, COLOR_LABEL, false);
        UIScaleManager.drawScaledString(graphics, font, "Max Y:", labelX2, y + ROW_HEIGHT + 5, COLOR_LABEL, false);

        UIScaleManager.drawScaledString(graphics, font, "Min Z:", x, y + ROW_HEIGHT * 2 + 5, COLOR_LABEL, false);
        UIScaleManager.drawScaledString(graphics, font, "Max Z:", labelX2, y + ROW_HEIGHT * 2 + 5, COLOR_LABEL, false);

        // Render edit boxes
        AxiomRenderer.drawInputBackground(graphics, minXField.getX(), minXField.getY(), minXField.getWidth(),
            minXField.getHeight(), minXField.isFocused());
        AxiomRenderer.drawInputBackground(graphics, maxXField.getX(), maxXField.getY(), maxXField.getWidth(),
            maxXField.getHeight(), maxXField.isFocused());
        AxiomRenderer.drawInputBackground(graphics, minYField.getX(), minYField.getY(), minYField.getWidth(),
            minYField.getHeight(), minYField.isFocused());
        AxiomRenderer.drawInputBackground(graphics, maxYField.getX(), maxYField.getY(), maxYField.getWidth(),
            maxYField.getHeight(), maxYField.isFocused());
        AxiomRenderer.drawInputBackground(graphics, minZField.getX(), minZField.getY(), minZField.getWidth(),
            minZField.getHeight(), minZField.isFocused());
        AxiomRenderer.drawInputBackground(graphics, maxZField.getX(), maxZField.getY(), maxZField.getWidth(),
            maxZField.getHeight(), maxZField.isFocused());
        minXField.render(graphics, mouseX, mouseY, partialTick);
        maxXField.render(graphics, mouseX, mouseY, partialTick);
        minYField.render(graphics, mouseX, mouseY, partialTick);
        maxYField.render(graphics, mouseX, mouseY, partialTick);
        minZField.render(graphics, mouseX, mouseY, partialTick);
        maxZField.render(graphics, mouseX, mouseY, partialTick);

        // Draw summary
        int summaryY = y + ROW_HEIGHT * 3 + 10;
        ZoneBounds current = new ZoneBounds(minX, maxX, minY, maxY, minZ, maxZ).normalized();
        String sizeText = String.format("Size: %d x %d x %d = %,d blocks",
            current.width(), current.height(), current.length(), current.volume());
        UIScaleManager.drawScaledString(graphics, font, sizeText, x, summaryY, COLOR_VALUE, false);

        String centerText = String.format("Center: (%d, %d, %d)",
            (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        UIScaleManager.drawScaledString(graphics, font, centerText, x, summaryY + 12, COLOR_LABEL, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Forward to edit boxes - track if any field was clicked
        boolean anyClicked = false;
        anyClicked |= minXField.mouseClicked(mouseX, mouseY, button);
        anyClicked |= maxXField.mouseClicked(mouseX, mouseY, button);
        anyClicked |= minYField.mouseClicked(mouseX, mouseY, button);
        anyClicked |= maxYField.mouseClicked(mouseX, mouseY, button);
        anyClicked |= minZField.mouseClicked(mouseX, mouseY, button);
        anyClicked |= maxZField.mouseClicked(mouseX, mouseY, button);

        // If clicked inside widget but not on any field, unfocus all
        if (!anyClicked && isMouseOver(mouseX, mouseY)) {
            unfocusAll();
        }

        return anyClicked || super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Removes focus from all edit boxes.
     */
    private void unfocusAll() {
        minXField.setFocused(false);
        maxXField.setFocused(false);
        minYField.setFocused(false);
        maxYField.setFocused(false);
        minZField.setFocused(false);
        maxZField.setFocused(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Forward to focused edit box
        if (minXField.isFocused() && minXField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (maxXField.isFocused() && maxXField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (minYField.isFocused() && minYField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (maxYField.isFocused() && maxYField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (minZField.isFocused() && minZField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (maxZField.isFocused() && maxZField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Forward to focused edit box
        if (minXField.isFocused() && minXField.charTyped(codePoint, modifiers)) return true;
        if (maxXField.isFocused() && maxXField.charTyped(codePoint, modifiers)) return true;
        if (minYField.isFocused() && minYField.charTyped(codePoint, modifiers)) return true;
        if (maxYField.isFocused() && maxYField.charTyped(codePoint, modifiers)) return true;
        if (minZField.isFocused() && minZField.charTyped(codePoint, modifiers)) return true;
        if (maxZField.isFocused() && maxZField.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Updates the bounds from external source (e.g., marker pair calculation).
     */
    public void setBounds(@Nonnull ZoneBounds bounds) {
        this.minX = bounds.minX();
        this.maxX = bounds.maxX();
        this.minY = bounds.minY();
        this.maxY = bounds.maxY();
        this.minZ = bounds.minZ();
        this.maxZ = bounds.maxZ();

        // Update field values
        minXField.setValue(String.valueOf(minX));
        maxXField.setValue(String.valueOf(maxX));
        minYField.setValue(String.valueOf(minY));
        maxYField.setValue(String.valueOf(maxY));
        minZField.setValue(String.valueOf(minZ));
        maxZField.setValue(String.valueOf(maxZ));
    }

    /**
     * Gets the current bounds.
     */
    @Nonnull
    public ZoneBounds getBounds() {
        return new ZoneBounds(minX, maxX, minY, maxY, minZ, maxZ).normalized();
    }
}

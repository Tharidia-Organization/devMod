package com.devmod.client.ui;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.ui.editor.core.DesignTokens;

@OnlyIn(Dist.CLIENT)
public abstract class ModScreen extends Screen {

    @Nullable
    protected final Screen parent;
    protected boolean hasChanges = false;

    // Standard button dimensions
    protected static final int BUTTON_WIDTH = 90;
    protected static final int BUTTON_HEIGHT = 20;
    protected static final int BUTTON_SPACING = 10;
    protected static final int BOTTOM_MARGIN = 28;

    protected ModScreen(Component title, @Nullable Screen parent) {
        super(java.util.Objects.requireNonNull(title, "title"));
        this.parent = parent;
    }

    protected ModScreen(Component title) {
        this(title, null);
    }

    @Override
    protected void init() {
        super.init();
        // Track screen open for telemetry
        UiTelemetry.screenOpened(getTelemetryCategory(), getTelemetryScreen());
    }

    /**
     * Get the telemetry category for this screen (e.g., "editor", "settings").
     * Override to customize.
     */
    @Nonnull
    protected String getTelemetryCategory() {
        return "mod";
    }

    /**
     * Get the telemetry screen identifier (e.g., "item_editor", "mob_config").
     * Default uses simple class name.
     */
    @Nonnull
    protected String getTelemetryScreen() {
        return Objects.requireNonNull(getClass().getSimpleName().toLowerCase(Locale.ROOT).replace("screen", ""));
    }

    /**
     * Called when Apply button is pressed.
     * Override to save changes.
     */
    protected abstract void onApply();

    /**
     * Called when Cancel button is pressed.
     * Default: close without saving.
     */
    protected void onCancel() {
        onClose();
    }

    /**
     * Called when Reset button is pressed.
     * Override to reset to defaults.
     */
    protected void onReset() {
        // Default: no-op, override in subclasses
    }

    @Override
    public void onClose() {
        // Track screen close for telemetry
        UiTelemetry.screenClosed(getTelemetryCategory(), getTelemetryScreen());

        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    /**
     * Adds standard Apply/Cancel/Reset buttons at the bottom of the screen.
     */
    protected void addStandardButtons() {
        int centerX = width / 2;
        int buttonY = height - BOTTOM_MARGIN;

        // Apply button
        var apply = new com.devmod.client.ui.editor.components.EditorButton("apply", Component.translatable("devmod.ui.apply").getString())
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.PRIMARY)
            .onClick(() -> {
                onApply();
                onClose();
            });
        this.addRenderableWidget(new com.devmod.client.ui.editor.components.EditorButtonWidget(apply,
            centerX - BUTTON_WIDTH - BUTTON_SPACING - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));

        // Cancel button
        var cancel = new com.devmod.client.ui.editor.components.EditorButton("cancel", Component.translatable("devmod.ui.cancel").getString())
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.NORMAL)
            .onClick(this::onCancel);
        this.addRenderableWidget(new com.devmod.client.ui.editor.components.EditorButtonWidget(cancel,
            centerX - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));

        // Reset button (dangerous)
        var reset = new com.devmod.client.ui.editor.components.EditorButton("reset", Component.translatable("devmod.ui.reset").getString())
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.DANGER)
            .onClick(this::onReset);
        this.addRenderableWidget(new com.devmod.client.ui.editor.components.EditorButtonWidget(reset,
            centerX + BUTTON_SPACING + BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    /**
     * Adds Apply/Cancel buttons (without Reset).
     */
    protected void addApplyCancelButtons() {
        int centerX = width / 2;
        int buttonY = height - BOTTOM_MARGIN;

        // Apply button
        var apply = new com.devmod.client.ui.editor.components.EditorButton("apply", Component.translatable("devmod.ui.apply").getString())
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.PRIMARY)
            .onClick(() -> {
                onApply();
                onClose();
            });
        this.addRenderableWidget(new com.devmod.client.ui.editor.components.EditorButtonWidget(apply,
            centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));

        // Cancel button
        var cancel = new com.devmod.client.ui.editor.components.EditorButton("cancel", Component.translatable("devmod.ui.cancel").getString())
            .style(com.devmod.client.ui.editor.components.EditorButton.Style.NORMAL)
            .onClick(this::onCancel);
        this.addRenderableWidget(new com.devmod.client.ui.editor.components.EditorButtonWidget(cancel,
            centerX + BUTTON_SPACING / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    /**
     * Creates a number input field with validation.
     *
     * @param x X position
     * @param y Y position
     * @param fieldWidth Width of the field
     * @param initialValue Initial value to display
     * @return Configured EditBox
     */
    protected EditBox createNumberField(int x, int y, int fieldWidth, String initialValue) {
        EditBox box = new EditBox(Objects.requireNonNull(font), x, y, fieldWidth, 20, Objects.requireNonNull(Component.empty()));
        box.setValue(Objects.requireNonNull(initialValue));
        box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
        return box;
    }

    /**
     * Creates an integer-only input field.
     */
    protected EditBox createIntegerField(int x, int y, int fieldWidth, int initialValue) {
        EditBox box = new EditBox(Objects.requireNonNull(font), x, y, fieldWidth, 20, Objects.requireNonNull(Component.empty()));
        box.setValue(Objects.requireNonNull(String.valueOf(initialValue)));
        box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        return box;
    }

    /**
     * Creates a text input field with max length.
     */
    protected EditBox createTextField(int x, int y, int fieldWidth, String initialValue, int maxLength) {
        EditBox box = new EditBox(Objects.requireNonNull(font), x, y, fieldWidth, 20, Objects.requireNonNull(Component.empty()));
        box.setValue(Objects.requireNonNull(initialValue));
        box.setMaxLength(maxLength);
        return box;
    }

    /**
     * Renders a themed panel background.
     */
    protected void renderPanelBackground(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight) {
        // Background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, DesignTokens.Background.PANEL());

        // Border
        graphics.fill(x, y, x + panelWidth, y + 1, DesignTokens.Border.DEFAULT());
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, DesignTokens.Border.DEFAULT());
        graphics.fill(x, y, x + 1, y + panelHeight, DesignTokens.Border.DEFAULT());
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, DesignTokens.Border.DEFAULT());
    }

    /**
     * Renders a section header with colored line.
     */
    protected void renderSectionHeader(GuiGraphics graphics, String text, int x, int y, int sectionWidth) {
        graphics.drawString(Objects.requireNonNull(font), text, x, y, DesignTokens.Text.ACCENT(), false);
        graphics.fill(x, y + 12, x + sectionWidth, y + 13, DesignTokens.Accent.BLUE());
    }

    /**
     * Renders a label-value pair.
     */
    protected void renderLabelValue(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(Objects.requireNonNull(font), label + ":", x, y, DesignTokens.Text.MUTED(), false);
        graphics.drawString(Objects.requireNonNull(font), value, x + font.width(label + ": "), y, DesignTokens.Text.PRIMARY(), false);
    }

    /**
     * Mark that changes have been made (for unsaved changes warning).
     */
    protected void markChanged() {
        hasChanges = true;
    }

    /**
     * Check if there are unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return hasChanges;
    }

    /**
     * Get the Minecraft instance safely.
     */
    protected Minecraft mc() {
        return Minecraft.getInstance();
    }
}

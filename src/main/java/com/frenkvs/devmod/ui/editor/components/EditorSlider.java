package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.EditorSpacing;
import com.frenkvs.devmod.ui.editor.core.FocusRing;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Slider component for numeric value editing.
 * Supports drag, click-to-set, and keyboard input.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#42-sliders
 */
public class EditorSlider {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from EditorDimensions per spec Section 1.6)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = EditorDimensions.SLIDER_HEIGHT;
    private static final int TRACK_HEIGHT = EditorDimensions.SLIDER_TRACK_HEIGHT;
    private static final int THUMB_SIZE = EditorDimensions.SLIDER_THUMB_SIZE;
    private static final int LABEL_WIDTH = EditorDimensions.SLIDER_LABEL_WIDTH;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private final String label;
    private float min;
    private float max;
    private float step;
    private float value;
    private float defaultValue;

    // Display
    private String format = "%.2f";
    private String suffix = "";
    private int trackColor = UIConstants.SliderColors.NEUTRAL;
    private boolean showLabel = true;
    private boolean showValue = true;

    // State
    private boolean dragging = false;
    private boolean hovered = false;
    private boolean focused = false;
    private boolean enabled = true;

    // Bounds (set during render)
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect trackBounds = ResponsiveLayout.Rect.EMPTY;

    // Callback
    private Consumer<Float> onChange;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public EditorSlider(String id, String label, float min, float max, float defaultValue) {
        this.id = id;
        this.label = label;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.step = (max - min) / 100f; // Default 1% steps
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public EditorSlider step(float step) {
        this.step = step;
        return this;
    }

    public EditorSlider format(String format) {
        this.format = format;
        return this;
    }

    public EditorSlider suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public EditorSlider trackColor(int color) {
        this.trackColor = color;
        return this;
    }

    public EditorSlider showLabel(boolean show) {
        this.showLabel = show;
        return this;
    }

    public EditorSlider showValue(boolean show) {
        this.showValue = show;
        return this;
    }

    public EditorSlider onChange(Consumer<Float> callback) {
        this.onChange = callback;
        return this;
    }

    public EditorSlider enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the slider at the given position.
     * @return The total height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        // Update bounds
        int totalHeight = calculateHeight();
        this.bounds = new ResponsiveLayout.Rect(x, y, width, totalHeight);

        // Calculate track position using EditorSpacing (per spec Section 4.2)
        int trackX = x + LABEL_WIDTH + EditorSpacing.S;
        int trackWidth = width - LABEL_WIDTH - 60 - EditorSpacing.S * 2;
        int trackY = y + (HEIGHT - TRACK_HEIGHT) / 2;

        // Update hover state based on track area
        this.hovered = enabled && mouseX >= trackX && mouseX < trackX + trackWidth
                    && mouseY >= y && mouseY < y + HEIGHT;

        this.trackBounds = new ResponsiveLayout.Rect(trackX, trackY, trackWidth, TRACK_HEIGHT);

        // Label on the left
        if (showLabel) {
            int labelColor = enabled ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED;
            graphics.drawString(font, label, x, y + (HEIGHT - 8) / 2, labelColor, false);
        }

        // Track background
        int trackBg = enabled ? UIConstants.Slider.TRACK : UIConstants.Slider.TRACK_DISABLED;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, trackBg);

        // Filled portion
        float ratio = (value - min) / (max - min);
        int filledWidth = (int) (trackWidth * ratio);
        int fillColor = enabled ? trackColor : UIConstants.withAlpha(trackColor, 0x80);
        graphics.fill(trackX, trackY, trackX + filledWidth, trackY + TRACK_HEIGHT, fillColor);

        // Track border
        int borderColor = focused ? UIConstants.Border.ACCENT :
                         (hovered ? UIConstants.Border.HOVER : UIConstants.Border.DEFAULT);
        AxiomRenderer.drawBorder(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, borderColor);

        // Thumb
        int thumbX = trackX + filledWidth - THUMB_SIZE / 2;
        int thumbY = y + (HEIGHT - THUMB_SIZE) / 2;

        // Clamp thumb position
        thumbX = Mth.clamp(thumbX, trackX, trackX + trackWidth - THUMB_SIZE);

        int thumbColor = dragging ? trackColor :
                        (hovered ? UIConstants.Background.ACTIVE : UIConstants.Background.HOVER);
        if (!enabled) thumbColor = UIConstants.Slider.THUMB_DISABLED;

        graphics.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, thumbColor);
        AxiomRenderer.drawBorder(graphics, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE,
                                hovered || dragging ? trackColor : UIConstants.Border.DEFAULT);

        // Value text on the right
        if (showValue) {
            String valueText = String.format(format, value) + suffix;
            int valueX = trackX + trackWidth + EditorSpacing.S;
            int valueColor = enabled ? UIConstants.Text.VALUE : UIConstants.Text.MUTED;
            graphics.drawString(font, valueText, valueX, y + (HEIGHT - 8) / 2, valueColor, false);
        }

        // Focus ring (per spec Section 4.2)
        if (focused) {
            FocusRing.render(graphics, trackX - 2, y, trackWidth + 4, HEIGHT);
        }

        // Default value marker (small tick)
        if (enabled && defaultValue != min && defaultValue != max) {
            float defaultRatio = (defaultValue - min) / (max - min);
            int markerX = trackX + (int) (trackWidth * defaultRatio);
            graphics.fill(markerX - 1, trackY + TRACK_HEIGHT - 2, markerX + 1, trackY + TRACK_HEIGHT,
                         UIConstants.Text.MUTED);
        }

        return totalHeight;
    }

    public int calculateHeight() {
        return HEIGHT + EditorSpacing.XS;  // Use EditorSpacing for consistency
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        if (trackBounds.contains(mouseX, mouseY)) {
            dragging = true;
            focused = true;
            updateValueFromMouse(mouseX);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!enabled) return false;

        if (bounds.contains(mouseX, mouseY) || focused) {
            float delta = (float) scrollY * step;
            setValue(value + delta);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !enabled) return false;

        // Left/Right arrows
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            setValue(value - step);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            setValue(value + step);
            return true;
        }

        // Home/End
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            setValue(min);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            setValue(max);
            return true;
        }

        // Backspace to reset to default
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            setValue(defaultValue);
            return true;
        }

        return false;
    }

    private void updateValueFromMouse(double mouseX) {
        float ratio = (float) (mouseX - trackBounds.x()) / trackBounds.width();
        ratio = Mth.clamp(ratio, 0f, 1f);
        float newValue = min + (max - min) * ratio;

        // Snap to step (per spec Section 4.2)
        if (step > 0) {
            newValue = Math.round(newValue / step) * step;
        }

        // Only update and play sound if value changed
        if (newValue != this.value) {
            this.value = Mth.clamp(newValue, min, max);
            EditorSounds.playSliderTick();
            if (onChange != null) {
                onChange.accept(this.value);
            }
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

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        float newValue = Mth.clamp(value, min, max);
        if (newValue != this.value) {
            this.value = newValue;
            if (onChange != null) {
                onChange.accept(newValue);
            }
        }
    }

    public float getMin() {
        return min;
    }

    public void setMin(float min) {
        this.min = min;
        setValue(value); // Re-clamp
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
        setValue(value); // Re-clamp
    }

    public float getStep() {
        return step;
    }

    public float getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(float defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
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

    /**
     * Check if value differs from default.
     */
    public boolean isModified() {
        return Math.abs(value - defaultValue) > 0.0001f;
    }

    /**
     * Reset to default value.
     */
    public void reset() {
        setValue(defaultValue);
    }
}

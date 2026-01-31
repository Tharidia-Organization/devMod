package com.devmod.client.ui.editor.components;

import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorDimensions;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.EditorSpacing;
import com.devmod.client.ui.editor.core.FocusRing;
import com.devmod.client.ui.editor.core.ResponsiveLayout;

public class EditorSlider {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from EditorDimensions per spec Section 1.6)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = EditorDimensions.SLIDER_HEIGHT;
    private static final int TRACK_HEIGHT = EditorDimensions.SLIDER_TRACK_HEIGHT;
    private static final int THUMB_SIZE = EditorDimensions.SLIDER_THUMB_SIZE;
    private static final int LABEL_WIDTH = EditorDimensions.SLIDER_LABEL_WIDTH;
    private static final int TEXT_HEIGHT = 8;
    private static final int TEXT_OFFSET_Y = (HEIGHT - TEXT_HEIGHT) / 2;
    private static final int INPUT_WIDTH = 64;
    private static final int VALUE_TEXT_RESERVED_WIDTH = 60;
    private static final int FOCUS_RING_INSET = 2;
    private static final int FOCUS_RING_EXTRA_WIDTH = 4;
    private static final int DEFAULT_MARKER_HALF_WIDTH = 1;
    private static final int DEFAULT_MARKER_HEIGHT = 2;
    private static final float MANUAL_INPUT_MIN = -1_000_000f;
    private static final float MANUAL_INPUT_MAX = 1_000_000f;
    private static final String LABEL_ELLIPSIS = "...";
    private static final boolean SHOW_SOURCE_BADGE = false;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private String label;
    private float min;
    private float max;
    private float step;
    private float value;
    private float defaultValue;

    // Display
    private String format = "%.2f";
    private String suffix = "";
    private int trackColor = DesignTokens.SliderColors.NEUTRAL;
    private boolean showLabel = true;
    private boolean showValue = true;
    private boolean showInput = false;

    // Source badge (optional, shows value origin)
    @Nullable
    private SourceBadge sourceBadge = null;

    // Info button (optional, shows description tooltip)
    @Nullable
    private InfoButton infoButton = null;

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
    @Nullable
    private EditorTextField inputField;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public EditorSlider(String id, String label, float min, float max, float defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = label == null ? "" : label;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.step = (max - min) / 100f; // Default 1% steps
        this.onChange = value -> {};
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════

    public EditorSlider step(float step) {
        this.step = step;
        return this;
    }

    public EditorSlider format(String format) {
        this.format = format != null ? format : "%.2f";
        return this;
    }

    public EditorSlider suffix(String suffix) {
        this.suffix = suffix != null ? suffix : "";
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

    public EditorSlider showInput(boolean show) {
        this.showInput = show;
        if (show && inputField == null) {
            EditorTextField createdField = new EditorTextField(id + "_input", "");
            inputField = createdField;
            createdField.numeric(true).numericRange(MANUAL_INPUT_MIN, MANUAL_INPUT_MAX);
            createdField.onChange(text -> {
                if (!createdField.isValid() || text.isEmpty()) {
                    return;
                }
                setValue(createdField.getNumericValue());
            });
        }
        if (inputField != null) {
            inputField.numericRange(MANUAL_INPUT_MIN, MANUAL_INPUT_MAX);
        }
        return this;
    }

    public EditorSlider onChange(Consumer<Float> callback) {
        this.onChange = callback != null ? callback : value -> {};
        return this;
    }

    public EditorSlider enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Set a source badge to display the value's origin (DEV/NBT/VANILLA).
     */
    public EditorSlider sourceBadge(SourceBadge badge) {
        if (!SHOW_SOURCE_BADGE) {
            this.sourceBadge = null;
            return this;
        }
        this.sourceBadge = badge;
        return this;
    }

    /**
     * Set an info description that will be shown via a "?" button tooltip.
     * Use this to explain what the stat does and how it's calculated.
     *
     * @param description The description text (supports multi-line via word wrapping)
     */
    public EditorSlider info(String description) {
        if (description != null && !description.isEmpty()) {
            this.infoButton = new InfoButton(id + "_info", description);
        }
        return this;
    }

    /**
     * Set an info description using an i18n key from SliderDescriptions.
     * The description will be looked up via Minecraft's translation system.
     *
     * @param descriptionKey Key from SliderDescriptions (e.g., SliderDescriptions.ATTACK_DAMAGE)
     * @return this slider for chaining
     * @see SliderDescriptions
     */
    public EditorSlider infoKey(String descriptionKey) {
        if (descriptionKey != null && !descriptionKey.isEmpty()) {
            this.infoButton = InfoButton.withI18n(id + "_info", descriptionKey);
        }
        return this;
    }

    /**
     * Set an info description using an i18n key with a fallback.
     * If no translation exists for the key, the fallback text will be shown.
     *
     * @param descriptionKey Key from SliderDescriptions
     * @param fallback       Fallback text if no translation exists
     * @return this slider for chaining
     */
    public EditorSlider infoKey(String descriptionKey, String fallback) {
        if (descriptionKey != null && !descriptionKey.isEmpty()) {
            this.infoButton = InfoButton.withI18n(id + "_info", descriptionKey, fallback);
        }
        return this;
    }

    /**
     * Get the info button (may be null if no info was set).
     */
    @Nullable
    public InfoButton getInfoButton() {
        return infoButton;
    }

    /**
     * Set the source badge source type directly.
     */
    public EditorSlider source(SourceBadge.Source source) {
        if (!SHOW_SOURCE_BADGE) {
            this.sourceBadge = null;
            return this;
        }
        if (this.sourceBadge == null) {
            this.sourceBadge = new SourceBadge();
        }
        this.sourceBadge.setSource(source);
        return this;
    }

    /**
     * Get the current source badge (may be null).
     */
    @Nullable
    public SourceBadge getSourceBadge() {
        return sourceBadge;
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
        String safeLabel = label == null ? "" : label;
        int gap = EditorSpacing.XS;
        SourceBadge badge = SHOW_SOURCE_BADGE ? sourceBadge : null;
        int badgeWidth = badge != null ? badge.getWidth() : 0;
        int infoWidth = infoButton != null ? InfoButton.getSize() : 0;
        int badgeInfoGap = (badgeWidth > 0 && infoWidth > 0) ? gap : 0;
        int badgeInfoWidth = badgeWidth + infoWidth + badgeInfoGap;
        // Use fixed LABEL_WIDTH for consistent column alignment across all sliders.
        // Truncate label text to keep badges/info within the label column.
        int labelWidth = showLabel ? Math.max(LABEL_WIDTH, badgeInfoWidth) : 0;
        int labelGap = (badgeInfoWidth > 0 && !safeLabel.isEmpty()) ? gap : 0;
        int availableLabelWidth = Math.max(0, labelWidth - badgeInfoWidth - labelGap);
        String displayLabel = safeLabel;
        int ellipsisWidth = UIScaleManager.getScaledStringWidth(font, LABEL_ELLIPSIS);
        if (availableLabelWidth <= 0) {
            displayLabel = "";
        } else if (UIScaleManager.getScaledStringWidth(font, displayLabel) > availableLabelWidth) {
            int sliceWidth = Math.max(0, availableLabelWidth - ellipsisWidth);
            displayLabel = font.plainSubstrByWidth(displayLabel, sliceWidth);
            if (!displayLabel.isEmpty() && availableLabelWidth >= ellipsisWidth) {
                displayLabel = displayLabel + LABEL_ELLIPSIS;
            }
        }

        int trackX = x + labelWidth + EditorSpacing.S;
        int reservedRight = showInput ? INPUT_WIDTH + EditorSpacing.S : VALUE_TEXT_RESERVED_WIDTH;
        int trackWidth = width - labelWidth - reservedRight - EditorSpacing.S * 2;
        int trackY = y + (HEIGHT - TRACK_HEIGHT) / 2;

        // Update hover state based on track area
        this.hovered = enabled && mouseX >= trackX && mouseX < trackX + trackWidth
                    && mouseY >= y && mouseY < y + HEIGHT;

        this.trackBounds = new ResponsiveLayout.Rect(trackX, trackY, trackWidth, TRACK_HEIGHT);

        // Label on the left
        if (showLabel) {
            int labelColor = enabled ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.MUTED();
            if (!displayLabel.isEmpty()) {
                UIScaleManager.drawScaledString(graphics, font, displayLabel, x, y + TEXT_OFFSET_Y, labelColor, false);
            }

            // Track position after label for badges/buttons
            int afterLabelX = x + UIScaleManager.getScaledStringWidth(font, displayLabel);
            if (badgeInfoWidth > 0 && !displayLabel.isEmpty()) {
                afterLabelX += gap;
            }

            // Source badge (inline after label)
            if (badge != null) {
                int badgeY = y + (HEIGHT - badge.getHeight()) / 2;
                badge.render(graphics, afterLabelX, badgeY, mouseX, mouseY);
                afterLabelX += badge.getWidth();
                if (infoButton != null) {
                    afterLabelX += gap;
                }
            }

            // Info button (after source badge or label)
            if (infoButton != null) {
                int infoY = y + (HEIGHT - InfoButton.getSize()) / 2;
                infoButton.render(graphics, afterLabelX, infoY, mouseX, mouseY);
            }
        }

        // Track background
        int trackBg = enabled ? DesignTokens.Slider.TRACK : DesignTokens.Slider.TRACK_DISABLED;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, trackBg);

        // Filled portion
        float ratio = (value - min) / (max - min);
        float clampedRatio = Mth.clamp(ratio, 0f, 1f);
        int filledWidth = (int) (trackWidth * clampedRatio);
        int fillColor = enabled ? trackColor : DesignTokens.withAlpha(trackColor, 0x80);
        graphics.fill(trackX, trackY, trackX + filledWidth, trackY + TRACK_HEIGHT, fillColor);

        // Track border
        int borderColor = focused ? DesignTokens.Border.ACCENT() :
                         (hovered ? DesignTokens.Border.HOVER() : DesignTokens.Border.DEFAULT());
        AxiomRenderer.drawBorder(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, borderColor);

        // Thumb
        int thumbX = trackX + filledWidth - THUMB_SIZE / 2;
        int thumbY = y + (HEIGHT - THUMB_SIZE) / 2;

        // Clamp thumb position
        thumbX = Mth.clamp(thumbX, trackX, trackX + trackWidth - THUMB_SIZE);

        int thumbColor = dragging ? trackColor :
                        (hovered ? DesignTokens.Background.ACTIVE() : DesignTokens.Background.HOVER());
        if (!enabled) thumbColor = DesignTokens.Slider.THUMB_DISABLED;

        graphics.fill(thumbX, thumbY, thumbX + THUMB_SIZE, thumbY + THUMB_SIZE, thumbColor);
        AxiomRenderer.drawBorder(graphics, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE,
                                hovered || dragging ? trackColor : DesignTokens.Border.DEFAULT());

        // Value text on the right
        if (showInput && inputField != null) {
            if (!inputField.isFocused()) {
                inputField.setNumericValue(value);
            }
            int inputX = trackX + trackWidth + EditorSpacing.S;
            int inputY = y + (HEIGHT - DesignTokens.Size.INPUT_HEIGHT) / 2;
            inputField.render(graphics, inputX, inputY, INPUT_WIDTH, mouseX, mouseY);
        } else if (showValue) {
            String safeFormat = format != null ? format : "%.2f";
            String safeSuffix = suffix != null ? suffix : "";
            String valueText = String.format(safeFormat, value) + safeSuffix;
            int valueX = trackX + trackWidth + EditorSpacing.S;
            int valueColor = enabled ? DesignTokens.Text.VALUE() : DesignTokens.Text.MUTED();
            UIScaleManager.drawScaledString(graphics, font, valueText, valueX, y + TEXT_OFFSET_Y, valueColor, false);
        }

        // Focus ring (per spec Section 4.2)
        if (focused) {
            FocusRing.render(graphics, trackX - FOCUS_RING_INSET, y, trackWidth + FOCUS_RING_EXTRA_WIDTH, HEIGHT);
        }

        // Default value marker (small tick)
        if (enabled && defaultValue != min && defaultValue != max) {
            float defaultRatio = (defaultValue - min) / (max - min);
            int markerX = trackX + (int) (trackWidth * defaultRatio);
            int markerY = trackY + TRACK_HEIGHT - DEFAULT_MARKER_HEIGHT;
            graphics.fill(markerX - DEFAULT_MARKER_HALF_WIDTH, markerY,
                markerX + DEFAULT_MARKER_HALF_WIDTH, trackY + TRACK_HEIGHT, DesignTokens.Text.MUTED());
        }

        return totalHeight;
    }

    public int calculateHeight() {
        return HEIGHT + EditorSpacing.XS;  // Use EditorSpacing for consistency
    }

    /**
     * Render the info button tooltip if hovered.
     * Note: Tooltips are now managed by TooltipManager and rendered automatically
     * when InfoButton.render() is called. This method is kept for API compatibility
     * but delegates to TooltipManager.
     *
     * @param graphics     The graphics context
     * @param screenWidth  Screen width for bounds checking
     * @param screenHeight Screen height for bounds checking
     * @deprecated Tooltips are now handled by TooltipManager. Call TooltipManager.INSTANCE.renderAll() instead.
     */
    @Deprecated
    public void renderInfoTooltip(GuiGraphics graphics, int screenWidth, int screenHeight) {
        // No-op: InfoButton now queues tooltips via TooltipManager during render()
        // The calling code should use TooltipManager.INSTANCE.renderAll(graphics)
    }

    /**
     * Check if the info button has a tooltip to render.
     */
    public boolean hasInfoTooltip() {
        return infoButton != null && infoButton.isHovered();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        // Check info button click first - must be checked before track bounds
        // because info button is in the label area, not the track
        if (infoButton != null) {
            if (infoButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (showInput && inputField != null && inputField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (trackBounds.contains(mouseX, mouseY)) {
            dragging = true;
            focused = true;
            updateValueFromMouse(mouseX);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showInput && inputField != null && inputField.isFocused()) {
            // allow clicks outside to commit
            inputField.mouseClicked(mouseX, mouseY, button);
        }
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
        if (showInput && inputField != null && inputField.isFocused()) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!enabled) return false;

        if (showInput && inputField != null && inputField.isFocused()) {
            return false;
        }

        if (bounds.contains(mouseX, mouseY) || focused) {
            float delta = (float) scrollY * step;
            setValueClamped(value + delta);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showInput && inputField != null && inputField.isFocused()) {
            return inputField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!focused || !enabled) return false;

        // Left/Right arrows
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            setValueClamped(value - step);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            setValueClamped(value + step);
            return true;
        }

        // Home/End
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            setValueClamped(min);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            setValueClamped(max);
            return true;
        }

        // Backspace to reset to default
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            setValueClamped(defaultValue);
            return true;
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (showInput && inputField != null) {
            if (inputField.charTyped(chr, modifiers)) {
                if (inputField.isValid()) {
                    setValue(inputField.getNumericValue());
                }
                return true;
            }
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
            setValueClamped(newValue);
            EditorSounds.playSliderTick();
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

    public void setLabel(String newLabel) {
        this.label = newLabel == null ? "" : newLabel;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        float newValue = clampGuardrail(value);
        if (newValue != this.value) {
            this.value = newValue;
            onChange.accept(newValue);
        }
    }

    public float getMin() {
        return min;
    }

    public void setMin(float min) {
        this.min = min;
        if (inputField != null) {
            inputField.numericRange(MANUAL_INPUT_MIN, MANUAL_INPUT_MAX);
        }
        setValue(value);
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
        if (inputField != null) {
            inputField.numericRange(MANUAL_INPUT_MIN, MANUAL_INPUT_MAX);
        }
        setValue(value);
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
        setValueClamped(defaultValue);
    }

    private void setValueClamped(float value) {
        setValue(Mth.clamp(value, min, max));
    }

    private float clampGuardrail(float value) {
        if (!Float.isFinite(value)) {
            return this.value;
        }
        return Mth.clamp(value, MANUAL_INPUT_MIN, MANUAL_INPUT_MAX);
    }
}

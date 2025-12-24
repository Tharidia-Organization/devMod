package com.devmod.ui.editor.components;

import com.devmod.ui.AxiomRenderer;
import com.devmod.ui.editor.core.EditorDimensions;
import com.devmod.ui.editor.core.EditorSounds;
import com.devmod.ui.editor.core.ResponsiveLayout;
import com.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Toggle/switch component for boolean values.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#43-toggles
 */
public class EditorToggle {

    // Dimensions (from EDITOR_DESIGN_SYSTEM.md Section 4.3)
    private static final int TOGGLE_WIDTH = EditorDimensions.TOGGLE_WIDTH;   // 36
    private static final int TOGGLE_HEIGHT = EditorDimensions.TOGGLE_HEIGHT; // 20
    private static final int TRACK_HEIGHT = 14;  // Per spec Section 4.3
    private static final int HANDLE_SIZE = 12;   // Per spec Section 4.3
    private static final int HANDLE_MARGIN = 2;
    private static final int TEXT_HEIGHT = 8;
    private static final int TEXT_OFFSET_Y = (TOGGLE_HEIGHT - TEXT_HEIGHT) / 2;
    private static final int BADGE_GAP = 4;
    private static final int STATE_TEXT_GAP = 8;

    // Configuration
    private final String id;
    private String label;
    private String tooltip = null;
    private boolean enabled = true;
    private boolean playSound = true;

    // State
    private boolean value;
    private boolean hovered = false;
    private boolean focused = false;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect toggleBounds = ResponsiveLayout.Rect.EMPTY;

    // Callback
    private Consumer<Boolean> onChange;

    // Source badge (optional, shows value origin)
    private SourceBadge sourceBadge = null;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public EditorToggle(String id, String label, boolean defaultValue) {
        this.id = id;
        this.label = label;
        this.value = defaultValue;
    }

    /**
     * Adapter to expose this toggle as a vanilla checkbox-style widget for legacy screens.
     */
    public net.minecraft.client.gui.components.AbstractWidget asVanilla(int x, int y, int width, int height) {
        @Nonnull String safeLabel = Objects.requireNonNull(
            Objects.requireNonNullElse(label, ""), "label");
        @Nonnull net.minecraft.client.gui.Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
        @Nonnull net.minecraft.network.chat.Component labelComponent = Objects.requireNonNull(
            net.minecraft.network.chat.Component.literal(safeLabel), "label");
        net.minecraft.client.gui.components.Checkbox checkbox = net.minecraft.client.gui.components.Checkbox.builder(
                labelComponent, font)
            .pos(x, y)
            .selected(value)
            .onValueChange((cb, selected) -> {
                if (!enabled) return;
                if (selected != value) {
                    setValue(selected);
                }
            })
            .build();
        checkbox.active = enabled;
        return checkbox;
    }

    /**
     * Convenience to update visibility on wrapped vanilla widget in legacy screens.
     */
    public void setVisible(boolean visible) {
        // Intentionally no-op; legacy callers store the wrapped widget for visibility.
    }

    // =========================================================================
    // BUILDER METHODS
    // =========================================================================

    public EditorToggle tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public EditorToggle enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public EditorToggle playSound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    public EditorToggle onChange(Consumer<Boolean> callback) {
        this.onChange = callback;
        return this;
    }

    /**
     * Set a source badge to display the value's origin (DEV/NBT/VANILLA).
     */
    public EditorToggle sourceBadge(SourceBadge badge) {
        this.sourceBadge = badge;
        return this;
    }

    /**
     * Set the source badge source type directly.
     */
    public EditorToggle source(SourceBadge.Source source) {
        if (this.sourceBadge == null) {
            this.sourceBadge = new SourceBadge();
        }
        this.sourceBadge.setSource(source);
        return this;
    }

    /**
     * Get the current source badge (may be null).
     */
    public SourceBadge getSourceBadge() {
        return sourceBadge;
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    /** Height of this control (without external spacing). */
    public int calculateHeight() {
        return TOGGLE_HEIGHT;
    }

    /**
     * Render the toggle at the given position.
     * @return The total height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        // Some callers (e.g., layout measurement) may pass a null graphics; just skip drawing.
        if (graphics == null) {
            this.bounds = new ResponsiveLayout.Rect(x, y, width, TOGGLE_HEIGHT);
            this.toggleBounds = ResponsiveLayout.Rect.EMPTY;
            return TOGGLE_HEIGHT;
        }

        @Nonnull GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        @Nonnull net.minecraft.client.gui.Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int height = TOGGLE_HEIGHT;
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Check hover state
        this.hovered = enabled && bounds.contains(mouseX, mouseY);

        // Label on the left
        int labelColor = enabled ? UIConstants.Text.PRIMARY() : UIConstants.Text.MUTED();
        String safeLabel = Objects.requireNonNullElse(label, "");
        safeGraphics.drawString(font, safeLabel, x, y + TEXT_OFFSET_Y, labelColor, false);

        // Source badge (inline after label)
        if (sourceBadge != null) {
            int badgeX = x + font.width(Objects.requireNonNull(safeLabel, "safeLabel")) + BADGE_GAP;
            int badgeY = y + (height - sourceBadge.getHeight()) / 2;
            sourceBadge.render(graphics, badgeX, badgeY, mouseX, mouseY);
        }

        // Toggle on the right - centered vertically within TOGGLE_HEIGHT
        int toggleX = x + width - TOGGLE_WIDTH;
        int trackY = y + (TOGGLE_HEIGHT - TRACK_HEIGHT) / 2;  // Center track vertically
        this.toggleBounds = new ResponsiveLayout.Rect(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT);

        // Track background (using TRACK_HEIGHT per spec Section 4.3)
        int trackColor;
        if (!enabled) {
            trackColor = UIConstants.Background.DARKER();
        } else if (value) {
            trackColor = UIConstants.Accent.GREEN();
        } else {
            trackColor = UIConstants.Background.INPUT();
        }

        safeGraphics.fill(toggleX, trackY, toggleX + TOGGLE_WIDTH, trackY + TRACK_HEIGHT, trackColor);

        // Border
        int borderColor = focused ? UIConstants.Border.ACCENT() :
                         (hovered ? UIConstants.Border.HOVER() : UIConstants.Border.DEFAULT());
        AxiomRenderer.drawBorder(graphics, toggleX, trackY, TOGGLE_WIDTH, TRACK_HEIGHT, borderColor);

        // Handle (using HANDLE_SIZE per spec Section 4.3)
        int handleX = value ?
            toggleX + TOGGLE_WIDTH - HANDLE_SIZE - HANDLE_MARGIN :
            toggleX + HANDLE_MARGIN;
        int handleY = trackY + (TRACK_HEIGHT - HANDLE_SIZE) / 2;

        int handleColor;
        if (!enabled) {
            handleColor = UIConstants.Slider.THUMB_DISABLED;
        } else if (hovered) {
            handleColor = UIConstants.Slider.THUMB_HOVER;
        } else {
            handleColor = UIConstants.Slider.THUMB;
        }

        safeGraphics.fill(handleX, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, handleColor);

        // State indicator text
        String stateText = value ? "ON" : "OFF";
        int stateColor = enabled ?
            (value ? UIConstants.Accent.GREEN() : UIConstants.Text.MUTED()) :
            UIConstants.Text.DISABLED();
        int stateX = toggleX - font.width(stateText) - STATE_TEXT_GAP;
        safeGraphics.drawString(font, stateText, stateX, y + TEXT_OFFSET_Y, stateColor, false);

        return height;
    }

    // =========================================================================
    // INPUT HANDLING
    // =========================================================================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        if (toggleBounds.contains(mouseX, mouseY) || bounds.contains(mouseX, mouseY)) {
            toggle();
            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !enabled) return false;

        // Space or Enter to toggle
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE ||
            keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            toggle();
            return true;
        }

        return false;
    }

    private void toggle() {
        value = !value;

        if (playSound) {
            EditorSounds.playToggle(value);
        }

        if (onChange != null) {
            onChange.accept(value);
        }
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String newLabel) {
        this.label = newLabel == null ? "" : newLabel;
    }

    public String getTooltip() {
        return tooltip;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        if (this.value != value) {
            this.value = value;
            if (onChange != null) {
                onChange.accept(value);
            }
        }
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
}

package com.devmod.client.ui.editor.components;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.SliderDescriptions;
import com.devmod.client.ui.editor.core.TooltipManager;
import com.devmod.client.ui.editor.core.UIConstants;
public final class InfoButton {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS
    // ═══════════════════════════════════════════════════════════════

    private static final int SIZE = 12;           // Button size (circle diameter)
    private static final int TEXT_HEIGHT = 8;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final String id;
    private final String descriptionKey;      // i18n key (if using translation system)
    private final String directDescription;   // Direct text (fallback or when not using i18n)

    private boolean hovered = false;
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Click debounce to prevent repeated clicks while mouse is held
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_MS = 250;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create an InfoButton with a direct description text.
     *
     * @param id          Unique identifier
     * @param description The description text to show
     */
    public InfoButton(String id, String description) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.descriptionKey = null;
        this.directDescription = description != null ? description : "";
    }

    /**
     * Create an InfoButton using an i18n key from SliderDescriptions.
     *
     * @param id              Unique identifier
     * @param descriptionKey  Key from SliderDescriptions (e.g., SliderDescriptions.ATTACK_DAMAGE)
     * @param fallback        Fallback text if translation is not found
     */
    private InfoButton(String id, String descriptionKey, String fallback) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.descriptionKey = descriptionKey;
        this.directDescription = fallback != null ? fallback : "";
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create an InfoButton using an i18n key with fallback.
     *
     * @param id              Unique identifier
     * @param descriptionKey  Key from SliderDescriptions constants
     * @param fallback        Fallback text if translation is not found
     * @return New InfoButton instance
     */
    public static InfoButton withI18n(String id, String descriptionKey, String fallback) {
        return new InfoButton(id, descriptionKey, fallback);
    }

    /**
     * Create an InfoButton using an i18n key only.
     * If no translation is found, the button won't show a tooltip.
     *
     * @param id              Unique identifier
     * @param descriptionKey  Key from SliderDescriptions constants
     * @return New InfoButton instance
     */
    public static InfoButton withI18n(String id, String descriptionKey) {
        return new InfoButton(id, descriptionKey, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the info button at the given position.
     *
     * @param graphics The graphics context
     * @param x        X position (left edge)
     * @param y        Y position (top edge)
     * @param mouseX   Current mouse X
     * @param mouseY   Current mouse Y
     * @return The width consumed (SIZE)
     */
    public int render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int scaledSize = ScaledCoord.scaleDim(SIZE);
        this.bounds = new ResponsiveLayout.Rect(x, y, scaledSize, scaledSize);

        // Check hover state
        this.hovered = mouseX >= x && mouseX < x + scaledSize
                    && mouseY >= y && mouseY < y + scaledSize;

        // Draw circular button background
        int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.DARKER();
        int borderColor = hovered ? UIConstants.Accent.CYAN() : UIConstants.Border.DEFAULT();

        // Draw as rounded rectangle (approximating circle)
        graphics.fill(x, y, x + scaledSize, y + scaledSize, bgColor);
        AxiomRenderer.drawBorder(graphics, x, y, scaledSize, scaledSize, borderColor);

        // Draw "?" text centered
        String questionMark = "?";
        int textColor = hovered ? UIConstants.Accent.CYAN() : UIConstants.Text.SECONDARY();
        int textX = x + (scaledSize - font.width(questionMark)) / 2;
        int textY = y + (scaledSize - TEXT_HEIGHT) / 2;
        graphics.drawString(font, questionMark, textX, textY, textColor, false);

        // Queue tooltip if hovered
        if (hovered) {
            String description = getResolvedDescription();
            if (!description.isEmpty()) {
                TooltipManager.INSTANCE.queueTooltip(
                    description,
                    x, y, scaledSize, scaledSize,
                    TooltipManager.TooltipPosition.ABOVE
                );
            }
        }

        return scaledSize;
    }

    /**
     * Get the resolved description (from i18n or direct text).
     */
    public String getResolvedDescription() {
        if (descriptionKey != null) {
            // Try to get translation
            String translated = SliderDescriptions.get(descriptionKey);
            if (!translated.isEmpty()) {
                return translated;
            }
        }
        // Fall back to direct description
        return directDescription;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public String getId() {
        return id;
    }

    public String getDescription() {
        return getResolvedDescription();
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public boolean isHovered() {
        return hovered;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    /**
     * Get the button size (for layout calculations).
     */
    public static int getSize() {
        return SIZE;
    }

    /**
     * Check if this button has a valid description to display.
     */
    public boolean hasDescription() {
        return !getResolvedDescription().isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse click on the info button.
     * When clicked, pins the tooltip so it stays visible until clicked again.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0 = left)
     * @return true if the click was consumed
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Use bounds set during render() - they contain the actual screen position
        if (bounds == null || bounds == ResponsiveLayout.Rect.EMPTY) {
            return false;
        }

        boolean inBounds = mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                        && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height();

        // Debounce repeated clicks (Minecraft sends repeated click events when mouse is held)
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
            return inBounds; // Still consume if in bounds, but don't toggle
        }

        if (inBounds && hasDescription()) {
            lastClickTime = now;
            // Pin tooltip on click - stays visible until clicked again
            TooltipManager.INSTANCE.pinTooltip(
                getResolvedDescription(),
                bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                TooltipManager.TooltipPosition.ABOVE
            );
            return true;
        }
        return false;
    }
}

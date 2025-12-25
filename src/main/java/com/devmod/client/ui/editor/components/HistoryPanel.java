package com.devmod.client.ui.editor.components;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * History panel component showing edit history for the current module.
 * Displays a scrollable list of history entries with a clear button.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 3.7
 */
public class HistoryPanel {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS
    // ═══════════════════════════════════════════════════════════════

    public static final int WIDTH = 260;
    public static final int HEIGHT = 200;
    public static final int MARGIN_RIGHT = 8;
    public static final int MARGIN_TOP = 8;

    private static final int TITLE_OFFSET_X = 8;
    private static final int TITLE_OFFSET_Y = 8;
    private static final int LIST_OFFSET_Y = 24;
    private static final int LIST_HEIGHT_PADDING = 50;
    private static final int LINE_HEIGHT = 14;
    private static final int FOOTER_OFFSET_Y = 18;
    private static final int TEXT_OFFSET_X = 8;
    private static final int CLEAR_WIDTH = 60;
    private static final int CLEAR_HEIGHT = 14;
    private static final int CLEAR_OFFSET_X = 8;
    private static final int CLEAR_OFFSET_Y = 2;
    private static final int CLEAR_TEXT_OFFSET_X = 12;
    private static final int CLEAR_TEXT_OFFSET_Y = 3;

    private static final String TITLE_TEXT = "Edit History";
    private static final String EMPTY_TEXT = "No history yet";
    private static final String SHOWING_EMPTY = "Showing 0/0";
    private static final String SHOWING_PREFIX = "Showing ";
    private static final String SHOWING_SEPARATOR = "/";
    private static final String CLEAR_TEXT = "Clear";

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private int scrollOffset = 0;
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Data source - provides history entries
    private Supplier<List<String>> entriesSupplier = List::of;
    private Runnable onClear;

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the supplier for history entries.
     *
     * @param supplier Function that returns current history entries
     * @return this for chaining
     */
    public HistoryPanel setEntriesSupplier(Supplier<List<String>> supplier) {
        this.entriesSupplier = supplier != null ? supplier : List::of;
        return this;
    }

    /**
     * Set the callback for clearing history.
     *
     * @param onClear Callback when clear is pressed
     * @return this for chaining
     */
    public HistoryPanel setOnClear(Runnable onClear) {
        this.onClear = onClear;
        return this;
    }

    /**
     * Update the panel bounds.
     *
     * @param bounds The new bounds
     */
    public void setBounds(ResponsiveLayout.Rect bounds) {
        this.bounds = bounds != null ? bounds : ResponsiveLayout.Rect.EMPTY;
    }

    /**
     * Get the current bounds.
     */
    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the history panel.
     *
     * @param graphics The graphics context
     * @param font     The font to use
     */
    public void render(@Nonnull GuiGraphics graphics, @Nonnull Font font) {
        if (bounds.isEmpty()) return;

        int panelWidth = bounds.width();
        int panelHeight = bounds.height();
        int x = bounds.x();
        int y = bounds.y();

        // Background and border
        graphics.fill(x, y, x + panelWidth, y + panelHeight, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, UIConstants.Border.DEFAULT());

        // Title
        graphics.drawString(font, TITLE_TEXT, x + TITLE_OFFSET_X, y + TITLE_OFFSET_Y,
            UIConstants.Text.TITLE(), false);

        // Get entries
        List<String> entries = entriesSupplier.get();
        int listY = y + LIST_OFFSET_Y;
        int listHeight = panelHeight - LIST_HEIGHT_PADDING;

        // Calculate scroll bounds
        int maxScroll = Math.max(0, entries.size() * LINE_HEIGHT - listHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int visibleLines = listHeight / LINE_HEIGHT;

        if (entries.isEmpty()) {
            graphics.drawString(font, EMPTY_TEXT, x + TEXT_OFFSET_X, listY,
                UIConstants.Text.MUTED(), false);
        } else {
            // Show most recent at bottom, scrollable
            int startFromEnd = scrollOffset / LINE_HEIGHT;
            int startIndex = Math.max(0, entries.size() - visibleLines - startFromEnd);
            int endIndex = Math.min(entries.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                String entry = entries.get(entries.size() - 1 - i);
                int entryY = listY + (i - startIndex) * LINE_HEIGHT;
                graphics.drawString(font, entry, x + TEXT_OFFSET_X, entryY,
                    UIConstants.Text.SECONDARY(), false);
            }
        }

        // Footer with count and clear button
        int footerY = y + panelHeight - FOOTER_OFFSET_Y;
        String countText = entries.isEmpty()
            ? SHOWING_EMPTY
            : SHOWING_PREFIX + Math.min(visibleLines, entries.size()) + SHOWING_SEPARATOR + entries.size();
        graphics.drawString(font, countText, x + TEXT_OFFSET_X, footerY,
            UIConstants.Text.MUTED(), false);

        // Clear button
        int clearX = x + panelWidth - CLEAR_WIDTH - CLEAR_OFFSET_X;
        int clearY = footerY - CLEAR_OFFSET_Y;
        int clearBg = entries.isEmpty() ? UIConstants.Button.DISABLED() : UIConstants.Background.INPUT();
        graphics.fill(clearX, clearY, clearX + CLEAR_WIDTH, clearY + CLEAR_HEIGHT, clearBg);
        AxiomRenderer.drawBorder(graphics, clearX, clearY, CLEAR_WIDTH, CLEAR_HEIGHT, UIConstants.Border.DEFAULT());
        int clearColor = entries.isEmpty() ? UIConstants.Text.DISABLED() : UIConstants.Text.PRIMARY();
        graphics.drawString(font, CLEAR_TEXT, clearX + CLEAR_TEXT_OFFSET_X,
            clearY + CLEAR_TEXT_OFFSET_Y, clearColor, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle mouse click inside the panel.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return true if click was handled
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (bounds.isEmpty()) return false;

        int x = bounds.x();
        int y = bounds.y();
        int panelWidth = bounds.width();
        int panelHeight = bounds.height();

        // Check if click is outside panel
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }

        // Check clear button
        int footerY = y + panelHeight - FOOTER_OFFSET_Y;
        int clearX = x + panelWidth - CLEAR_WIDTH - CLEAR_OFFSET_X;
        int clearY = footerY - CLEAR_OFFSET_Y;

        if (mouseX >= clearX && mouseX <= clearX + CLEAR_WIDTH &&
            mouseY >= clearY && mouseY <= clearY + CLEAR_HEIGHT) {
            if (!entriesSupplier.get().isEmpty() && onClear != null) {
                onClear.run();
                scrollOffset = 0;
            }
            return true;
        }

        return true; // Consume click inside panel
    }

    /**
     * Handle scroll inside the panel.
     *
     * @param delta Scroll amount (positive = up, negative = down)
     */
    public void scroll(int delta) {
        scrollOffset += delta * LINE_HEIGHT;
        scrollOffset = Math.max(0, scrollOffset);
    }

    /**
     * Reset scroll position to top.
     */
    public void resetScroll() {
        scrollOffset = 0;
    }

    /**
     * Check if a point is inside the panel.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return true if point is inside
     */
    public boolean contains(double mouseX, double mouseY) {
        return bounds.contains(mouseX, mouseY);
    }
}

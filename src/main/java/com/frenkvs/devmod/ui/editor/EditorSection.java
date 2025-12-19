package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sealed interface for editor content sections.
 * Each section type defines a specific kind of editable content.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#33-editor-section-types
 */
public sealed interface EditorSection permits
    EditorSection.SliderSection,
    EditorSection.ToggleSection,
    EditorSection.InputSection,
    EditorSection.ListSection,
    EditorSection.HeaderSection,
    EditorSection.SpacerSection,
    EditorSection.CustomSection {

    /** Get section identifier */
    String getId();

    /** Get section label */
    String getLabel();

    /** Calculate height needed for this section */
    int getHeight();

    /** Render this section */
    void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY);

    /** Handle mouse click */
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse release */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse drag */
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    /** Handle key press */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** Handle character typed */
    default boolean charTyped(char chr, int modifiers) { return false; }

    // ═══════════════════════════════════════════════════════════════
    // SECTION TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Slider section for numeric value editing.
     */
    non-sealed interface SliderSection extends EditorSection {
        float getValue();
        void setValue(float value);
        float getMin();
        float getMax();
        float getStep();
        String getFormat();
        int getColor();
        boolean isDragging();
        void setDragging(boolean dragging);
    }

    /**
     * Toggle section for boolean values.
     */
    non-sealed interface ToggleSection extends EditorSection {
        boolean getValue();
        void setValue(boolean value);
    }

    /**
     * Text input section.
     */
    non-sealed interface InputSection extends EditorSection {
        String getText();
        void setText(String text);
        String getPlaceholder();
        boolean isNumeric();
    }

    /**
     * List selection section.
     */
    non-sealed interface ListSection extends EditorSection {
        java.util.List<String> getOptions();
        int getSelectedIndex();
        void setSelectedIndex(int index);
    }

    /**
     * Header/title section for grouping.
     */
    non-sealed interface HeaderSection extends EditorSection {
        boolean isCollapsible();
        boolean isCollapsed();
        void setCollapsed(boolean collapsed);
    }

    /**
     * Spacer for layout purposes.
     */
    non-sealed interface SpacerSection extends EditorSection {
        @Override
        default String getLabel() { return ""; }

        @Override
        default void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
            // Spacers don't render anything
        }
    }

    /**
     * Custom section for complex content.
     */
    non-sealed interface CustomSection extends EditorSection {
        // Custom sections implement their own rendering logic
    }
}

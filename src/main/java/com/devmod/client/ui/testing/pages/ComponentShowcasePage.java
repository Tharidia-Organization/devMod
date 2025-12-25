package com.devmod.ui.testing.pages;

import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.testing.VoxelLabTab;
import com.devmod.ui.testing.panel.*;

/**
 * Component Showcase Page - Demonstrates all UI components.
 * Shows EditorButton variants, styles, sizes, and states.
 */
public class ComponentShowcasePage extends AbstractVoxelLabPage {

    public ComponentShowcasePage() {
        super(VoxelLabTab.SHOWCASE);
    }

    @Override
    protected void buildPanels() {
        // Header
        panelContainer.addPanel(new HeaderPanel("COMPONENT SHOWCASE"));

        // Button Styles Section
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-styles", "Button Styles")
                .columns(2)
                .addItem(new EditorButton("normal", "Normal"), "Default style")
                .addItem(new EditorButton("primary", "Primary").style(EditorButton.Style.PRIMARY), "Primary action")
                .addItem(new EditorButton("danger", "Danger").style(EditorButton.Style.DANGER), "Destructive action")
                .addItem(new EditorButton("success", "Success").style(EditorButton.Style.SUCCESS), "Positive action")
                .addItem(new EditorButton("ghost", "Ghost").style(EditorButton.Style.GHOST), "Subtle action")
                .build()
        );

        // Button States Section
        panelContainer.addPanel(new SpacerPanel("spacer-states", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-states", "Button States")
                .columns(2)
                .addItem(new EditorButton("disabled", "Disabled").enabled(false), "Disabled state")
                .addItem(new EditorButton("toggle-off", "Toggle Off").toggleable(true).style(EditorButton.Style.PRIMARY), "Toggleable")
                .addItem(new EditorButton("toggle-on", "Toggle On").toggleable(true).toggled(true).style(EditorButton.Style.SUCCESS), "Toggled on")
                .build()
        );

        // Button Sizes Section
        panelContainer.addPanel(new SpacerPanel("spacer-sizes", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-sizes", "Button Sizes")
                .columns(2)
                .addItem(new EditorButton("small", "Small").size(EditorButton.Size.SMALL), "Small size")
                .addItem(new EditorButton("medium", "Medium"), "Medium size (default)")
                .addItem(new EditorButton("large", "Large").size(EditorButton.Size.LARGE).style(EditorButton.Style.PRIMARY), "Large size")
                .build()
        );

        // Button Features Section
        panelContainer.addPanel(new SpacerPanel("spacer-features", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-features", "Button Features")
                .columns(2)
                .addItem(new EditorButton("icon", "With Icon").icon("\u25B6"), "Icon prefix")
                .addItem(new EditorButton("hotkey", "Hotkey Hint").hotkeyHint("[CTRL+H]"), "Hotkey hint")
                .addItem(new EditorButton("both", "Icon + Hotkey").icon("\u2605").hotkeyHint("[F]").style(EditorButton.Style.SUCCESS), "Combined")
                .build()
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-showcase")
                .addStatus("Styles", () -> true)
                .addStatus("States", () -> true)
                .addStatus("Sizes", () -> true)
                .addStatus("Features", () -> true)
                .build()
        );
    }
}

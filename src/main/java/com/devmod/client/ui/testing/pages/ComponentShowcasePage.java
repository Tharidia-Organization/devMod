package com.devmod.client.ui.testing.pages;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.ShowcasePanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.util.I18n;
public class ComponentShowcasePage extends AbstractVoxelLabPage {

    public ComponentShowcasePage() {
        super(VoxelLabTab.SHOWCASE);
    }

    @Override
    protected void buildPanels() {
        // Header
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.showcase.header").getString()));

        // Button Styles Section
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-styles",
                I18n.translate("devmod.testing.voxel_lab.showcase.section.styles").getString())
                .columns(2)
                .addItem(new EditorButton("normal",
                        I18n.translate("devmod.testing.voxel_lab.demo.normal").getString()),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.default_style").getString())
                .addItem(new EditorButton("primary",
                        I18n.translate("devmod.testing.voxel_lab.demo.primary").getString())
                        .style(EditorButton.Style.PRIMARY),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.primary_action").getString())
                .addItem(new EditorButton("danger",
                        I18n.translate("devmod.testing.voxel_lab.demo.danger").getString())
                        .style(EditorButton.Style.DANGER),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.destructive_action").getString())
                .addItem(new EditorButton("success",
                        I18n.translate("devmod.testing.voxel_lab.demo.success").getString())
                        .style(EditorButton.Style.SUCCESS),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.positive_action").getString())
                .addItem(new EditorButton("ghost",
                        I18n.translate("devmod.testing.voxel_lab.demo.ghost").getString())
                        .style(EditorButton.Style.GHOST),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.subtle_action").getString())
                .build()
        );

        // Button States Section
        panelContainer.addPanel(new SpacerPanel("spacer-states", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-states",
                I18n.translate("devmod.testing.voxel_lab.showcase.section.states").getString())
                .columns(2)
                .addItem(new EditorButton("disabled",
                        I18n.translate("devmod.testing.voxel_lab.demo.disabled").getString())
                        .enabled(false),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.disabled_state").getString())
                .addItem(new EditorButton("toggle-off",
                        I18n.translate("devmod.testing.voxel_lab.demo.toggle_off").getString())
                        .toggleable(true).style(EditorButton.Style.PRIMARY),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.toggleable").getString())
                .addItem(new EditorButton("toggle-on",
                        I18n.translate("devmod.testing.voxel_lab.demo.toggle_on").getString())
                        .toggleable(true).toggled(true).style(EditorButton.Style.SUCCESS),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.toggled_on").getString())
                .build()
        );

        // Button Sizes Section
        panelContainer.addPanel(new SpacerPanel("spacer-sizes", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-sizes",
                I18n.translate("devmod.testing.voxel_lab.showcase.section.sizes").getString())
                .columns(2)
                .addItem(new EditorButton("small",
                        I18n.translate("devmod.testing.voxel_lab.demo.small").getString())
                        .size(EditorButton.Size.SMALL),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.small_size").getString())
                .addItem(new EditorButton("medium",
                        I18n.translate("devmod.testing.voxel_lab.demo.medium").getString()),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.medium_size_default").getString())
                .addItem(new EditorButton("large",
                        I18n.translate("devmod.testing.voxel_lab.demo.large").getString())
                        .size(EditorButton.Size.LARGE).style(EditorButton.Style.PRIMARY),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.large_size").getString())
                .build()
        );

        // Button Features Section
        panelContainer.addPanel(new SpacerPanel("spacer-features", 8));
        panelContainer.addPanel(
            ShowcasePanel.builder("showcase-features",
                I18n.translate("devmod.testing.voxel_lab.showcase.section.features").getString())
                .columns(2)
                .addItem(new EditorButton("icon",
                        I18n.translate("devmod.testing.voxel_lab.demo.with_icon").getString())
                        .icon("\u25B6"),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.icon_prefix").getString())
                .addItem(new EditorButton("hotkey",
                        I18n.translate("devmod.testing.voxel_lab.showcase.label.hotkey_hint").getString())
                        .hotkeyHint("[CTRL+H]"),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.hotkey_hint").getString())
                .addItem(new EditorButton("both",
                        I18n.translate("devmod.testing.voxel_lab.showcase.label.icon_hotkey").getString())
                        .icon("\u2605").hotkeyHint("[F]").style(EditorButton.Style.SUCCESS),
                    I18n.translate("devmod.testing.voxel_lab.showcase.desc.combined").getString())
                .build()
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-showcase")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.showcase.status.styles").getString(),
                    () -> true)
                .addStatus(I18n.translate("devmod.testing.voxel_lab.showcase.status.states").getString(),
                    () -> true)
                .addStatus(I18n.translate("devmod.testing.voxel_lab.showcase.status.sizes").getString(),
                    () -> true)
                .addStatus(I18n.translate("devmod.testing.voxel_lab.showcase.status.features").getString(),
                    () -> true)
                .build()
        );
    }
}

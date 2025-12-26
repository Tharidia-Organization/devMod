package com.devmod.client.ui.testing.pages;

import javax.annotation.Nonnull;

import com.devmod.ModConfig;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.CompositePanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SliderPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.config.Config;
import com.devmod.util.I18n;

import static com.devmod.client.ui.testing.pages.PageUtils.nonNullDouble;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;

/**
 * Combat Page - Configuration for combat mechanics and damage systems.
 * Controls body part detection, damage multipliers, and armor penetration.
 */

public class CombatPage extends AbstractVoxelLabPage {

    // Body part system
    private EditorButton bodyPartToggle;
    private EditorButton showBodyPartBoxesToggle;

    public CombatPage() {
        super(VoxelLabTab.COMBAT);
    }

    @Override
    protected void buildPanels() {
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.combat.header").getString()));

        // Body Part System Section
        panelContainer.addPanel(
            SectionPanel.builder("section-bodypart",
                I18n.translate("devmod.testing.voxel_lab.combat.section.body_part").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.combat.section.body_part_desc").getString())
                .addButton(bodyPartToggle)
                .addButton(showBodyPartBoxesToggle)
                .build()
        );

        // Damage Multipliers Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-mult",
                I18n.translate("devmod.testing.voxel_lab.combat.section.damage_multipliers").getString(),
                CompositePanel.of("mult-content",
                    I18n.translate("devmod.testing.voxel_lab.combat.section.multipliers").getString(),
                    SliderPanel.of("slider-head",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.head_multiplier").getString(),
                        () -> nonNullDouble(Config.HEAD_DAMAGE_MULTIPLIER, 2.0),
                        (@Nonnull Double v) -> Config.HEAD_DAMAGE_MULTIPLIER.set(v),
                        0.5, 4.0, 0.1,
                        I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString()),
                    SliderPanel.of("slider-body",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.body_multiplier").getString(),
                        () -> nonNullDouble(Config.BODY_DAMAGE_MULTIPLIER, 1.0),
                        (@Nonnull Double v) -> Config.BODY_DAMAGE_MULTIPLIER.set(v),
                        0.5, 2.0, 0.1,
                        I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString()),
                    SliderPanel.of("slider-arms",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.arms_multiplier").getString(),
                        () -> nonNullDouble(Config.ARMS_DAMAGE_MULTIPLIER, 0.75),
                        (@Nonnull Double v) -> Config.ARMS_DAMAGE_MULTIPLIER.set(v),
                        0.25, 1.5, 0.05,
                        I18n.translate("devmod.testing.voxel_lab.format.multiplier_2").getString()),
                    SliderPanel.of("slider-legs",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.legs_multiplier").getString(),
                        () -> nonNullDouble(Config.LEGS_DAMAGE_MULTIPLIER, 0.75),
                        (@Nonnull Double v) -> Config.LEGS_DAMAGE_MULTIPLIER.set(v),
                        0.25, 1.5, 0.05,
                        I18n.translate("devmod.testing.voxel_lab.format.multiplier_2").getString())
                ),
                0xFFFF5500)
        );

        // Armor Penetration Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-armor",
                I18n.translate("devmod.testing.voxel_lab.combat.section.armor_penetration").getString(),
                CompositePanel.of("armor-content",
                    I18n.translate("devmod.testing.voxel_lab.combat.section.armor_settings").getString(),
                    SliderPanel.of("slider-armor-mult",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.penetration_multiplier").getString(),
                        () -> nonNullDouble(Config.ARMOR_PEN_MULTIPLIER, 1.0),
                        (@Nonnull Double v) -> Config.ARMOR_PEN_MULTIPLIER.set(v),
                        0.0, 2.0, 0.1,
                        I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString()),
                    SliderPanel.of("slider-armor-flat",
                        I18n.translate("devmod.testing.voxel_lab.combat.slider.flat_penetration_bonus").getString(),
                        () -> nonNullDouble(Config.ARMOR_PEN_FLAT_BONUS, 0.0),
                        (@Nonnull Double v) -> Config.ARMOR_PEN_FLAT_BONUS.set(v),
                        0.0, 10.0, 0.5,
                        I18n.translate("devmod.testing.voxel_lab.format.decimal_1").getString())
                ),
                0xFF00AAFF)
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-combat")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.combat.status.body_parts").getString(),
                    () -> safeGetBool(Config.BODY_PART_DETECTION_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.combat.status.boxes").getString(),
                    () -> safeGetBool(Config.SHOW_BODY_PART_BOXES))
                .build()
        );
    }

    private void createButtons() {
        bodyPartToggle = new EditorButton("toggle-bodypart",
            I18n.translate("devmod.testing.voxel_lab.combat.toggle.body_part_detection").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.BODY_PART_DETECTION_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u25CE")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.BODY_PART_DETECTION_ENABLED)));

        showBodyPartBoxesToggle = new EditorButton("toggle-boxes",
            I18n.translate("devmod.testing.voxel_lab.combat.toggle.show_hit_boxes").getString())
            .toggleable(true)
            .toggled(ModConfig.showBodyPartBoxes)
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .hotkeyHint("[Shift+G]")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                Boolean.TRUE.equals(v), ModConfig.showBodyPartBoxes));
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        bodyPartToggle.toggled(safeGetBool(Config.BODY_PART_DETECTION_ENABLED));
        showBodyPartBoxesToggle.toggled(ModConfig.showBodyPartBoxes);
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

}

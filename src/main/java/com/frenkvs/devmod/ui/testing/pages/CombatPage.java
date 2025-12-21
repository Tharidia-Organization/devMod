package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionOrigin;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.client.ClientActionContexts;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.testing.VoxelLabTab;
import com.frenkvs.devmod.ui.testing.panel.*;

import javax.annotation.Nonnull;

import static com.frenkvs.devmod.ui.testing.pages.PageUtils.safeGetBool;
import static com.frenkvs.devmod.ui.testing.pages.PageUtils.nonNullDouble;

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
        panelContainer.addPanel(new HeaderPanel("COMBAT MECHANICS"));

        // Body Part System Section
        panelContainer.addPanel(
            SectionPanel.builder("section-bodypart", "Body Part Detection")
                .description("Hit zones with different damage multipliers")
                .addButton(bodyPartToggle)
                .addButton(showBodyPartBoxesToggle)
                .build()
        );

        // Damage Multipliers Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-mult", "Damage Multipliers",
                CompositePanel.of("mult-content", "Multipliers",
                    SliderPanel.of("slider-head", "Head Multiplier",
                        () -> nonNullDouble(Config.HEAD_DAMAGE_MULTIPLIER, 2.0),
                        (@Nonnull Double v) -> Config.HEAD_DAMAGE_MULTIPLIER.set(v),
                        0.5, 4.0, 0.1, "%.1fx"),
                    SliderPanel.of("slider-body", "Body Multiplier",
                        () -> nonNullDouble(Config.BODY_DAMAGE_MULTIPLIER, 1.0),
                        (@Nonnull Double v) -> Config.BODY_DAMAGE_MULTIPLIER.set(v),
                        0.5, 2.0, 0.1, "%.1fx"),
                    SliderPanel.of("slider-arms", "Arms Multiplier",
                        () -> nonNullDouble(Config.ARMS_DAMAGE_MULTIPLIER, 0.75),
                        (@Nonnull Double v) -> Config.ARMS_DAMAGE_MULTIPLIER.set(v),
                        0.25, 1.5, 0.05, "%.2fx"),
                    SliderPanel.of("slider-legs", "Legs Multiplier",
                        () -> nonNullDouble(Config.LEGS_DAMAGE_MULTIPLIER, 0.75),
                        (@Nonnull Double v) -> Config.LEGS_DAMAGE_MULTIPLIER.set(v),
                        0.25, 1.5, 0.05, "%.2fx")
                ),
                0xFFFF5500)
        );

        // Armor Penetration Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-armor", "Armor Penetration",
                CompositePanel.of("armor-content", "Armor Settings",
                    SliderPanel.of("slider-armor-mult", "Penetration Multiplier",
                        () -> nonNullDouble(Config.ARMOR_PEN_MULTIPLIER, 1.0),
                        (@Nonnull Double v) -> Config.ARMOR_PEN_MULTIPLIER.set(v),
                        0.0, 2.0, 0.1, "%.1fx"),
                    SliderPanel.of("slider-armor-flat", "Flat Penetration Bonus",
                        () -> nonNullDouble(Config.ARMOR_PEN_FLAT_BONUS, 0.0),
                        (@Nonnull Double v) -> Config.ARMOR_PEN_FLAT_BONUS.set(v),
                        0.0, 10.0, 0.5, "%.1f")
                ),
                0xFF00AAFF)
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-combat")
                .addStatus("Body Parts", () -> safeGetBool(Config.BODY_PART_DETECTION_ENABLED))
                .addStatus("Boxes", () -> safeGetBool(Config.SHOW_BODY_PART_BOXES))
                .build()
        );
    }

    private void createButtons() {
        bodyPartToggle = new EditorButton("toggle-bodypart", "Body Part Detection")
            .toggleable(true)
            .toggled(safeGetBool(Config.BODY_PART_DETECTION_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u25CE")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.BODY_PART_DETECTION_ENABLED)));

        showBodyPartBoxesToggle = new EditorButton("toggle-boxes", "Show Hit Boxes")
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

package com.devmod.client.ui.testing.pages;

import javax.annotation.Nonnull;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.CollapsiblePanel;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SliderPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.config.Config;

import static com.devmod.client.ui.testing.pages.PageUtils.nonNullDouble;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetDouble;

/**
 * Effects Page - Configuration for VFX and visual effects.
 * Controls impact effects, screen shake, and particle systems.
 */

public class EffectsPage extends AbstractVoxelLabPage {

    // VFX Master toggle
    private EditorButton vfxMasterToggle;

    // VFX type toggles
    private EditorButton vfxVortexToggle;
    private EditorButton vfxSlashToggle;
    private EditorButton vfxLinesToggle;

    // Screen effects
    private EditorButton screenShakeToggle;
    private EditorButton projectileTrailsToggle;
    private EditorButton badgePopupToggle;

    // Intensity buttons
    private EditorButton intensityLow;
    private EditorButton intensityMed;
    private EditorButton intensityHigh;
    private EditorButton intensityMax;

    public EffectsPage() {
        super(VoxelLabTab.EFFECTS);
    }

    @Override
    protected void buildPanels() {
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel("VISUAL EFFECTS"));

        // Master VFX Section
        panelContainer.addPanel(
            SectionPanel.builder("section-vfx-master", "Impact VFX")
                .description("Visual effects on damage")
                .addButton(vfxMasterToggle)
                .addRow(vfxVortexToggle, vfxSlashToggle, vfxLinesToggle)
                .build()
        );

        // Intensity Section
        panelContainer.addPanel(
            SectionPanel.builder("section-intensity", "VFX Intensity")
                .addRow(intensityLow, intensityMed, intensityHigh, intensityMax)
                .build()
        );

        // Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-intensity", "Intensity Level",
                () -> nonNullDouble(Config.IMPACT_VFX_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.IMPACT_VFX_INTENSITY.set(v),
                0.1, 2.0, 0.1, "%.1fx")
        );

        // Screen Effects Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-screen", "Screen Effects",
                SectionPanel.builder("section-screen-content", "Feedback Effects")
                    .addRow(screenShakeToggle, projectileTrailsToggle)
                    .addButton(badgePopupToggle)
                    .build(),
                0xFFFF5500)
        );

        // Screen Shake Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-shake", "Shake Intensity",
                () -> nonNullDouble(Config.SCREEN_SHAKE_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.SCREEN_SHAKE_INTENSITY.set(v),
                0.0, 2.0, 0.1, "%.1fx")
        );

        // Projectile Trails Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-trails", "Trail Intensity",
                () -> nonNullDouble(Config.PROJECTILE_TRAILS_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.PROJECTILE_TRAILS_INTENSITY.set(v),
                0.0, 2.0, 0.1, "%.1fx")
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-effects")
                .addStatus("VFX", () -> safeGetBool(Config.IMPACT_VFX_ENABLED))
                .addStatus("Shake", () -> safeGetBool(Config.SCREEN_SHAKE_ENABLED))
                .addStatus("Trails", () -> safeGetBool(Config.PROJECTILE_TRAILS_ENABLED))
                .build()
        );
    }

    private void createButtons() {
        // VFX Master
        vfxMasterToggle = new EditorButton("toggle-vfx", "Enable VFX")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_ENABLED))
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2728")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_ENABLED)));

        // VFX types
        vfxVortexToggle = new EditorButton("toggle-vortex", "Vortex")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED)));

        vfxSlashToggle = new EditorButton("toggle-slash", "Slash")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED)));

        vfxLinesToggle = new EditorButton("toggle-lines", "Lines")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_LINES_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_LINES_ENABLED)));

        // Intensity presets
        intensityLow = new EditorButton("int-low", "Low")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW); });

        intensityMed = new EditorButton("int-med", "Normal")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED); });

        intensityHigh = new EditorButton("int-high", "High")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH); });

        intensityMax = new EditorButton("int-max", "Max")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX); });

        // Screen effects
        screenShakeToggle = new EditorButton("toggle-shake", "Screen Shake")
            .toggleable(true)
            .toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u21C4")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.SCREEN_SHAKE_ENABLED)));

        projectileTrailsToggle = new EditorButton("toggle-trails", "Projectile Trails")
            .toggleable(true)
            .toggled(safeGetBool(Config.PROJECTILE_TRAILS_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u27A1")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.PROJECTILE_TRAILS_ENABLED)));

        badgePopupToggle = new EditorButton("toggle-badge", "Badge Popups")
            .toggleable(true)
            .toggled(safeGetBool(Config.BADGE_POPUP_ENABLED))
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2605")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.BADGE_POPUP_ENABLED)));
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        vfxMasterToggle.toggled(safeGetBool(Config.IMPACT_VFX_ENABLED));
        vfxVortexToggle.toggled(safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED));
        vfxSlashToggle.toggled(safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED));
        vfxLinesToggle.toggled(safeGetBool(Config.IMPACT_VFX_LINES_ENABLED));
        screenShakeToggle.toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED));
        projectileTrailsToggle.toggled(safeGetBool(Config.PROJECTILE_TRAILS_ENABLED));
        badgePopupToggle.toggled(safeGetBool(Config.BADGE_POPUP_ENABLED));
        syncIntensityButtons();
    }

    private void syncIntensityButtons() {
        double intensity = safeGetDouble(Config.IMPACT_VFX_INTENSITY, 1.0);
        intensityLow.toggled(intensity < 0.7);
        intensityMed.toggled(intensity >= 0.7 && intensity < 1.3);
        intensityHigh.toggled(intensity >= 1.3 && intensity < 1.8);
        intensityMax.toggled(intensity >= 1.8);
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void invokeAction(String actionId) {
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
        syncIntensityButtons();
    }

}

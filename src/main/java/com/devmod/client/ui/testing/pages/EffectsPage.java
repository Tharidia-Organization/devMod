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
import com.devmod.util.I18n;

import static com.devmod.client.ui.testing.pages.PageUtils.nonNullDouble;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;
import static com.devmod.client.ui.testing.pages.PageUtils.safeGetDouble;
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
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.effects.header").getString()));

        // Master VFX Section
        panelContainer.addPanel(
            SectionPanel.builder("section-vfx-master",
                I18n.translate("devmod.testing.voxel_lab.effects.section.vfx").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.effects.section.vfx_desc").getString())
                .addButton(vfxMasterToggle)
                .addRow(vfxVortexToggle, vfxSlashToggle, vfxLinesToggle)
                .build()
        );

        // Intensity Section
        panelContainer.addPanel(
            SectionPanel.builder("section-intensity",
                I18n.translate("devmod.testing.voxel_lab.effects.section.intensity").getString())
                .addRow(intensityLow, intensityMed, intensityHigh, intensityMax)
                .build()
        );

        // Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-intensity",
                I18n.translate("devmod.testing.voxel_lab.effects.slider.intensity").getString(),
                () -> nonNullDouble(Config.IMPACT_VFX_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.IMPACT_VFX_INTENSITY.set(v),
                0.1, 2.0, 0.1,
                I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString())
        );

        // Screen Effects Section
        panelContainer.addPanel(
            new CollapsiblePanel("collapsible-screen",
                I18n.translate("devmod.testing.voxel_lab.effects.section.screen").getString(),
                SectionPanel.builder("section-screen-content",
                        I18n.translate("devmod.testing.voxel_lab.effects.section.screen_desc").getString())
                    .addRow(screenShakeToggle, projectileTrailsToggle)
                    .addButton(badgePopupToggle)
                    .build(),
                0xFFFF5500)
        );

        // Screen Shake Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-shake",
                I18n.translate("devmod.testing.voxel_lab.effects.slider.shake").getString(),
                () -> nonNullDouble(Config.SCREEN_SHAKE_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.SCREEN_SHAKE_INTENSITY.set(v),
                0.0, 2.0, 0.1,
                I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString())
        );

        // Projectile Trails Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-trails",
                I18n.translate("devmod.testing.voxel_lab.effects.slider.trails").getString(),
                () -> nonNullDouble(Config.PROJECTILE_TRAILS_INTENSITY, 1.0),
                (@Nonnull Double v) -> Config.PROJECTILE_TRAILS_INTENSITY.set(v),
                0.0, 2.0, 0.1,
                I18n.translate("devmod.testing.voxel_lab.format.multiplier_1").getString())
        );

        // Status Panel
        panelContainer.addPanel(new SpacerPanel("spacer-status", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-effects")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.effects.status.vfx").getString(),
                    () -> safeGetBool(Config.IMPACT_VFX_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.effects.status.shake").getString(),
                    () -> safeGetBool(Config.SCREEN_SHAKE_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.effects.status.trails").getString(),
                    () -> safeGetBool(Config.PROJECTILE_TRAILS_ENABLED))
                .build()
        );
    }

    private void createButtons() {
        // VFX Master
        vfxMasterToggle = new EditorButton("toggle-vfx",
            I18n.translate("devmod.testing.voxel_lab.effects.toggle.enable_vfx").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_ENABLED))
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2728")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_ENABLED)));

        // VFX types
        vfxVortexToggle = new EditorButton("toggle-vortex",
            I18n.translate("devmod.testing.impact_hud.vfx_vortex").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED)));

        vfxSlashToggle = new EditorButton("toggle-slash",
            I18n.translate("devmod.testing.impact_hud.vfx_slash").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED)));

        vfxLinesToggle = new EditorButton("toggle-lines",
            I18n.translate("devmod.testing.impact_hud.vfx_lines").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_LINES_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_LINES_ENABLED)));

        // Intensity presets
        intensityLow = new EditorButton("int-low",
            I18n.translate("devmod.testing.impact_hud.intensity_low").getString())
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW); });

        intensityMed = new EditorButton("int-med",
            I18n.translate("devmod.testing.impact_hud.intensity_normal").getString())
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED); });

        intensityHigh = new EditorButton("int-high",
            I18n.translate("devmod.testing.impact_hud.intensity_high").getString())
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH); });

        intensityMax = new EditorButton("int-max",
            I18n.translate("devmod.testing.impact_hud.intensity_max").getString())
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) invokeAction(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX); });

        // Screen effects
        screenShakeToggle = new EditorButton("toggle-shake",
            I18n.translate("devmod.testing.voxel_lab.effects.toggle.screen_shake").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u21C4")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.SCREEN_SHAKE_ENABLED)));

        projectileTrailsToggle = new EditorButton("toggle-trails",
            I18n.translate("devmod.testing.voxel_lab.effects.toggle.projectile_trails").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.PROJECTILE_TRAILS_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u27A1")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.PROJECTILE_TRAILS_ENABLED)));

        badgePopupToggle = new EditorButton("toggle-badge",
            I18n.translate("devmod.testing.voxel_lab.effects.toggle.badge_popups").getString())
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

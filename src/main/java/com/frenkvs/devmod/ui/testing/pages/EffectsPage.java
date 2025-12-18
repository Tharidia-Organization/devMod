package com.frenkvs.devmod.ui.testing.pages;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.testing.VoxelLabTab;
import com.frenkvs.devmod.ui.testing.panel.*;

import static com.frenkvs.devmod.ui.testing.pages.PageUtils.safeGetBool;
import static com.frenkvs.devmod.ui.testing.pages.PageUtils.safeGetDouble;
import static com.frenkvs.devmod.ui.testing.pages.PageUtils.nonNullDouble;

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
                v -> Config.IMPACT_VFX_INTENSITY.set(v),
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
                v -> Config.SCREEN_SHAKE_INTENSITY.set(v),
                0.0, 2.0, 0.1, "%.1fx")
        );

        // Projectile Trails Intensity Slider
        panelContainer.addPanel(
            SliderPanel.of("slider-trails", "Trail Intensity",
                () -> nonNullDouble(Config.PROJECTILE_TRAILS_INTENSITY, 1.0),
                v -> Config.PROJECTILE_TRAILS_INTENSITY.set(v),
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
            .onToggle(v -> Config.IMPACT_VFX_ENABLED.set(Boolean.TRUE.equals(v)));

        // VFX types
        vfxVortexToggle = new EditorButton("toggle-vortex", "Vortex")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> Config.IMPACT_VFX_VORTEX_ENABLED.set(Boolean.TRUE.equals(v)));

        vfxSlashToggle = new EditorButton("toggle-slash", "Slash")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_SLASH_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> Config.IMPACT_VFX_SLASH_ENABLED.set(Boolean.TRUE.equals(v)));

        vfxLinesToggle = new EditorButton("toggle-lines", "Lines")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_LINES_ENABLED))
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> Config.IMPACT_VFX_LINES_ENABLED.set(Boolean.TRUE.equals(v)));

        // Intensity presets
        intensityLow = new EditorButton("int-low", "Low")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(0.5); });

        intensityMed = new EditorButton("int-med", "Normal")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(1.0); });

        intensityHigh = new EditorButton("int-high", "High")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(1.5); });

        intensityMax = new EditorButton("int-max", "Max")
            .toggleable(true)
            .size(EditorButton.Size.SMALL)
            .onToggle(v -> { if (Boolean.TRUE.equals(v)) setIntensity(2.0); });

        // Screen effects
        screenShakeToggle = new EditorButton("toggle-shake", "Screen Shake")
            .toggleable(true)
            .toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u21C4")
            .onToggle(v -> Config.SCREEN_SHAKE_ENABLED.set(Boolean.TRUE.equals(v)));

        projectileTrailsToggle = new EditorButton("toggle-trails", "Projectile Trails")
            .toggleable(true)
            .toggled(safeGetBool(Config.PROJECTILE_TRAILS_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u27A1")
            .onToggle(v -> Config.PROJECTILE_TRAILS_ENABLED.set(Boolean.TRUE.equals(v)));

        badgePopupToggle = new EditorButton("toggle-badge", "Badge Popups")
            .toggleable(true)
            .toggled(safeGetBool(Config.BADGE_POPUP_ENABLED))
            .style(EditorButton.Style.SUCCESS)
            .icon("\u2605")
            .onToggle(v -> Config.BADGE_POPUP_ENABLED.set(Boolean.TRUE.equals(v)));
    }

    private void setIntensity(double value) {
        Config.IMPACT_VFX_INTENSITY.set(value);
        syncIntensityButtons();
    }

    @Override
    protected void onTick() {
        syncButtonStates();
    }

    private void syncButtonStates() {
        vfxMasterToggle.toggled(safeGetBool(Config.IMPACT_VFX_ENABLED));
        vfxVortexToggle.toggled(safeGetBool(Config.IMPACT_VFX_VORTEX_ENABLED));
        vfxSlashToggle.toggled(safeGetBool(Config.IMPACT_VFX_LINES_ENABLED));
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

}

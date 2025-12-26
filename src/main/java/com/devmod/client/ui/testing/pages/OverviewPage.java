package com.devmod.client.ui.testing.pages;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.testing.VoxelLabTab;
import com.devmod.client.ui.testing.panel.HeaderPanel;
import com.devmod.client.ui.testing.panel.SectionPanel;
import com.devmod.client.ui.testing.panel.SpacerPanel;
import com.devmod.client.ui.testing.panel.StatusPanel;
import com.devmod.config.Config;
import com.devmod.util.I18n;

import static com.devmod.client.ui.testing.pages.PageUtils.safeGetBool;

public class OverviewPage extends AbstractVoxelLabPage {

    // Quick toggle buttons
    private EditorButton debugToggle;
    private EditorButton impactHudToggle;
    private EditorButton impact3dToggle;
    private EditorButton vfxToggle;
    private EditorButton telemetryToggle;
    private EditorButton screenShakeToggle;

    public OverviewPage() {
        super(VoxelLabTab.OVERVIEW);
        Buttons buttons = createButtons();
        debugToggle = buttons.debugToggle;
        impactHudToggle = buttons.impactHudToggle;
        impact3dToggle = buttons.impact3dToggle;
        vfxToggle = buttons.vfxToggle;
        telemetryToggle = buttons.telemetryToggle;
        screenShakeToggle = buttons.screenShakeToggle;
    }

    @Override
    protected void buildPanels() {
        // Header
        panelContainer.addPanel(new HeaderPanel(
            I18n.translate("devmod.testing.voxel_lab.overview.header").getString()));

        // Debug Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-debug",
                I18n.translate("devmod.testing.voxel_lab.overview.section.debug").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.overview.section.debug_desc").getString())
                .addButton(debugToggle)
                .build()
        );

        // HUD Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-hud",
                I18n.translate("devmod.testing.voxel_lab.overview.section.hud").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.overview.section.hud_desc").getString())
                .addRow(impactHudToggle, impact3dToggle)
                .build()
        );

        // VFX Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-vfx",
                I18n.translate("devmod.testing.voxel_lab.overview.section.vfx").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.overview.section.vfx_desc").getString())
                .addRow(vfxToggle, screenShakeToggle)
                .build()
        );

        // Telemetry Section
        panelContainer.addPanel(
            SectionPanel.builder("section-telemetry",
                I18n.translate("devmod.testing.voxel_lab.overview.section.telemetry").getString())
                .description(I18n.translate("devmod.testing.voxel_lab.overview.section.telemetry_desc").getString())
                .addButton(telemetryToggle)
                .build()
        );

        // System Statistics
        panelContainer.addPanel(new SpacerPanel("spacer-stats", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-overview")
                .addStatus(I18n.translate("devmod.testing.voxel_lab.overview.status.debug").getString(),
                    () -> DebugRenderer.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.voxel_lab.overview.status.hud_2d").getString(),
                    ImpactHudOverlay::isEnabled)
                .addStatus(I18n.translate("devmod.testing.voxel_lab.overview.status.hud_3d").getString(),
                    () -> Impact3DPanelManager.INSTANCE.isEnabled())
                .addStatus(I18n.translate("devmod.testing.voxel_lab.overview.status.vfx").getString(),
                    () -> safeGetBool(Config.IMPACT_VFX_ENABLED))
                .addStatus(I18n.translate("devmod.testing.voxel_lab.overview.status.telemetry").getString(),
                    () -> safeGetBool(Config.TELEMETRY_ENABLED))
                .messageSupplier(this::getSystemStats)
                .build()
        );
    }

    private Buttons createButtons() {
        EditorButton debugToggle = new EditorButton("toggle-debug",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.debug_overlay").getString())
            .toggleable(true)
            .toggled(DebugRenderer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2699")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), DebugRenderer.INSTANCE.isEnabled()));

        EditorButton impactHudToggle = new EditorButton("toggle-impact-hud",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.hud_2d").getString())
            .toggleable(true)
            .toggled(ImpactHudOverlay.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_TOGGLE,
                Boolean.TRUE.equals(v), ImpactHudOverlay.isEnabled()));

        EditorButton impact3dToggle = new EditorButton("toggle-impact-3d",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.hud_3d").getString())
            .toggleable(true)
            .toggled(Impact3DPanelManager.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A0")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_3D_TOGGLE,
                Boolean.TRUE.equals(v), Impact3DPanelManager.INSTANCE.isEnabled()));

        EditorButton vfxToggle = new EditorButton("toggle-vfx",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.vfx").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2728")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_ENABLED)));

        EditorButton screenShakeToggle = new EditorButton("toggle-shake",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.screen_shake").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u21C4")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.SCREEN_SHAKE_ENABLED)));

        EditorButton telemetryToggle = new EditorButton("toggle-telemetry",
            I18n.translate("devmod.testing.voxel_lab.overview.toggle.recording").getString())
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_ENABLED))
            .style(EditorButton.Style.DANGER)
            .icon("\u25CF")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_ENABLED)));

        return new Buttons(
            debugToggle,
            impactHudToggle,
            impact3dToggle,
            vfxToggle,
            telemetryToggle,
            screenShakeToggle
        );
    }

    private static final class Buttons {
        private final EditorButton debugToggle;
        private final EditorButton impactHudToggle;
        private final EditorButton impact3dToggle;
        private final EditorButton vfxToggle;
        private final EditorButton telemetryToggle;
        private final EditorButton screenShakeToggle;

        private Buttons(
            EditorButton debugToggle,
            EditorButton impactHudToggle,
            EditorButton impact3dToggle,
            EditorButton vfxToggle,
            EditorButton telemetryToggle,
            EditorButton screenShakeToggle
        ) {
            this.debugToggle = debugToggle;
            this.impactHudToggle = impactHudToggle;
            this.impact3dToggle = impact3dToggle;
            this.vfxToggle = vfxToggle;
            this.telemetryToggle = telemetryToggle;
            this.screenShakeToggle = screenShakeToggle;
        }
    }

    @Override
    protected void onTick() {
        // Sync button states with actual config values
        syncButtonStates();
    }

    private void syncButtonStates() {
        debugToggle.toggled(DebugRenderer.INSTANCE.isEnabled());
        impactHudToggle.toggled(ImpactHudOverlay.isEnabled());
        impact3dToggle.toggled(Impact3DPanelManager.INSTANCE.isEnabled());
        vfxToggle.toggled(safeGetBool(Config.IMPACT_VFX_ENABLED));
        screenShakeToggle.toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED));
        telemetryToggle.toggled(safeGetBool(Config.TELEMETRY_ENABLED));
    }

    private void invokeToggleAction(String actionId, boolean desired, boolean current) {
        if (desired == current) {
            return;
        }
        ActionRegistry.invoke(actionId, ClientActionContexts.forClient(ActionOrigin.UI));
    }


    private String getSystemStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "";
        @Nonnull ClientLevel level = Objects.requireNonNull(mc.level, "level");

        int fps = mc.getFps();
        int entities = 0;
        for (Object entity : level.entitiesForRendering()) {
            if (entity != null) entities++;
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        return I18n.translate("devmod.testing.voxel_lab.overview.stats", fps, entities, usedMb, maxMb).getString();
    }
}

package com.devmod.ui.testing.pages;

import com.devmod.config.Config;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.overlay.Impact3DPanelManager;
import com.devmod.overlay.ImpactHudOverlay;
import com.devmod.rendering.DebugRenderer;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.ui.testing.VoxelLabTab;
import com.devmod.ui.testing.panel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Objects;
import javax.annotation.Nonnull;

import static com.devmod.ui.testing.pages.PageUtils.safeGetBool;

/**
 * Overview Page - Dashboard with system status and quick toggles.
 * Shows the current state of all major systems with one-click enable/disable.
 */
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
    }

    @Override
    protected void buildPanels() {
        createButtons();

        // Header
        panelContainer.addPanel(new HeaderPanel("SYSTEM DASHBOARD"));

        // Debug Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-debug", "Debug Systems")
                .description("Debug rendering and overlays")
                .addButton(debugToggle)
                .build()
        );

        // HUD Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-hud", "HUD Systems")
                .description("On-screen displays and overlays")
                .addRow(impactHudToggle, impact3dToggle)
                .build()
        );

        // VFX Systems Section
        panelContainer.addPanel(
            SectionPanel.builder("section-vfx", "Visual Effects")
                .description("Impact effects and screen effects")
                .addRow(vfxToggle, screenShakeToggle)
                .build()
        );

        // Telemetry Section
        panelContainer.addPanel(
            SectionPanel.builder("section-telemetry", "Telemetry")
                .description("Data collection and analytics")
                .addButton(telemetryToggle)
                .build()
        );

        // System Statistics
        panelContainer.addPanel(new SpacerPanel("spacer-stats", 8));
        panelContainer.addPanel(
            StatusPanel.builder("status-overview")
                .addStatus("Debug", () -> DebugRenderer.INSTANCE.isEnabled())
                .addStatus("HUD 2D", ImpactHudOverlay::isEnabled)
                .addStatus("HUD 3D", () -> Impact3DPanelManager.INSTANCE.isEnabled())
                .addStatus("VFX", () -> safeGetBool(Config.IMPACT_VFX_ENABLED))
                .addStatus("Telemetry", () -> safeGetBool(Config.TELEMETRY_ENABLED))
                .messageSupplier(this::getSystemStats)
                .build()
        );
    }

    private void createButtons() {
        debugToggle = new EditorButton("toggle-debug", "Debug Overlay")
            .toggleable(true)
            .toggled(DebugRenderer.INSTANCE.isEnabled())
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2699")
            .onToggle(v -> invokeToggleAction(ActionIds.DEBUG_OVERLAY_TOGGLE,
                Boolean.TRUE.equals(v), DebugRenderer.INSTANCE.isEnabled()));

        impactHudToggle = new EditorButton("toggle-impact-hud", "Impact HUD 2D")
            .toggleable(true)
            .toggled(ImpactHudOverlay.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A1")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_TOGGLE,
                Boolean.TRUE.equals(v), ImpactHudOverlay.isEnabled()));

        impact3dToggle = new EditorButton("toggle-impact-3d", "Impact HUD 3D")
            .toggleable(true)
            .toggled(Impact3DPanelManager.INSTANCE.isEnabled())
            .style(EditorButton.Style.SUCCESS)
            .icon("\u25A0")
            .onToggle(v -> invokeToggleAction(ActionIds.HUD_IMPACT_3D_TOGGLE,
                Boolean.TRUE.equals(v), Impact3DPanelManager.INSTANCE.isEnabled()));

        vfxToggle = new EditorButton("toggle-vfx", "Impact VFX")
            .toggleable(true)
            .toggled(safeGetBool(Config.IMPACT_VFX_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u2728")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_IMPACT_VFX_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.IMPACT_VFX_ENABLED)));

        screenShakeToggle = new EditorButton("toggle-shake", "Screen Shake")
            .toggleable(true)
            .toggled(safeGetBool(Config.SCREEN_SHAKE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .icon("\u21C4")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.SCREEN_SHAKE_ENABLED)));

        telemetryToggle = new EditorButton("toggle-telemetry", "Recording")
            .toggleable(true)
            .toggled(safeGetBool(Config.TELEMETRY_ENABLED))
            .style(EditorButton.Style.DANGER)
            .icon("\u25CF")
            .onToggle(v -> invokeToggleAction(ActionIds.CONFIG_TELEMETRY_TOGGLE,
                Boolean.TRUE.equals(v), safeGetBool(Config.TELEMETRY_ENABLED)));
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

        return String.format("FPS: %d | Entities: %d | Memory: %dMB/%dMB", fps, entities, usedMb, maxMb);
    }
}

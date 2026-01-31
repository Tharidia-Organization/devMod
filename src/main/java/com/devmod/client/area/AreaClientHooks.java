package com.devmod.client.area;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.network.AreaPreviewPayload;
import com.devmod.area.network.BuildStatusPayload;
import com.devmod.area.network.OpenAreaBuilderPayload;
import com.devmod.area.network.OpenEditorCentralPayload;
import com.devmod.area.network.SaveAreaResultPayload;
import com.devmod.area.network.SnapshotListPayload;
import com.devmod.area.network.TemplateDataPayload;
import com.devmod.area.network.TemplateListPayload;
import com.devmod.area.network.ZoneListPayload;

/**
 * Client-side hooks for Area Builder system network payloads.
 * Called via reflection from AreaNetworkHandler to avoid class loading on server.
 */
@OnlyIn(Dist.CLIENT)
public final class AreaClientHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaClientHooks.class);

    private AreaClientHooks() {}

    /**
     * Opens the Area Builder screen.
     * Called when OpenAreaBuilderPayload is received.
     */
    public static void openBuilderScreen(OpenAreaBuilderPayload payload) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "area_builder",
            () -> new AreaBuilderScreen(
                payload.existingArea(),
                payload.editorPosition(),
                payload.isMainHub()
            ));
    }

    /**
     * Opens the Editor Central screen.
     * Called when OpenEditorCentralPayload is received.
     */
    public static void openEditorCentralScreen(OpenEditorCentralPayload payload) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "nexus_editor_central",
            () -> new NexusEditorCentralScreen(
                payload.areas(),
                payload.mainHubId(),
                payload.canCreateAreas()
            ));
    }

    /**
     * Shows area preview visualization.
     * Called when AreaPreviewPayload is received.
     */
    public static void showAreaPreview(AreaPreviewPayload payload) {
        // Store preview data for rendering
        AreaPreviewRenderer.INSTANCE.setPreview(payload);
    }

    /**
     * Handles zone list payload for ZoneSelectorWidget.
     * Called when ZoneListPayload is received.
     */
    public static void handleZoneList(ZoneListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // If current screen is AreaBuilderScreen, update its zone selector
        if (currentScreen instanceof AreaBuilderScreen areaBuilder) {
            mc.execute(() -> areaBuilder.updateZoneList(Objects.requireNonNull(payload.zones())));
        } else {
            LOGGER.debug("[Area] ZoneList payload dropped - screen is {} (expected AreaBuilderScreen)",
                currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
        }
    }

    /**
     * Handles template list payload for template selection UI.
     * Called when TemplateListPayload is received.
     */
    public static void handleTemplateList(TemplateListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // If current screen is AreaBuilderScreen, update its template list
        if (currentScreen instanceof AreaBuilderScreen areaBuilder) {
            mc.execute(() -> areaBuilder.updateTemplateList(Objects.requireNonNull(payload.templates())));
        } else {
            LOGGER.debug("[Area] TemplateList payload dropped - screen is {} (expected AreaBuilderScreen)",
                currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
        }
    }

    /**
     * Handles template data payload for loading template into editor.
     * Called when TemplateDataPayload is received.
     */
    public static void handleTemplateData(TemplateDataPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // If current screen is AreaBuilderScreen, apply the template
        if (currentScreen instanceof AreaBuilderScreen areaBuilder) {
            mc.execute(() -> areaBuilder.applyTemplate(Objects.requireNonNull(payload)));
        } else {
            LOGGER.debug("[Area] TemplateData payload dropped - screen is {} (expected AreaBuilderScreen)",
                currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
        }
    }

    /**
     * Handles save result payload for area saves.
     * Called when SaveAreaResultPayload is received.
     */
    public static void handleSaveAreaResult(SaveAreaResultPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        if (currentScreen instanceof AreaBuilderScreen areaBuilder) {
            mc.execute(() -> areaBuilder.handleSaveResult(Objects.requireNonNull(payload)));
        } else {
            LOGGER.debug("[Area] SaveAreaResult payload dropped - screen is {} (expected AreaBuilderScreen)",
                currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
        }
    }

    /**
     * Handles snapshot list payload for snapshot management UI.
     * Called when SnapshotListPayload is received (CLIENT-01 fix).
     */
    public static void handleSnapshotList(SnapshotListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // If current screen is NexusEditorCentralScreen, show snapshot dialog
        if (currentScreen instanceof NexusEditorCentralScreen editorCentral) {
            mc.execute(() -> editorCentral.showSnapshotDialog(
                Objects.requireNonNull(payload.areaId()),
                Objects.requireNonNull(payload.snapshots())
            ));
        } else {
            LOGGER.debug("[Area] SnapshotList payload dropped - screen is {} (expected NexusEditorCentralScreen)",
                currentScreen != null ? currentScreen.getClass().getSimpleName() : "null");
        }
    }

    /**
     * Handles build status payload for build progress UI.
     * Called when BuildStatusPayload is received (CLIENT-01 fix).
     */
    public static void handleBuildStatus(BuildStatusPayload payload) {
        Minecraft mc = Minecraft.getInstance();

        // Update the build progress tracker (singleton)
        mc.execute(() -> BuildProgressTracker.INSTANCE.updateStatus(
            Objects.requireNonNull(payload.areaId()),
            Objects.requireNonNull(payload.status()),
            payload.progressPercent()
        ));

        // Also notify current screen if it's relevant
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof AreaBuilderScreen areaBuilder) {
            mc.execute(() -> areaBuilder.handleBuildStatus(Objects.requireNonNull(payload)));
        }
    }
}

package com.devmod.client.template;

import net.minecraft.client.Minecraft;

import com.devmod.template.network.OpenTemplateEditorPayload;

/**
 * Client-side handler for template network payloads.
 */
public final class TemplateClientHandler {
    private TemplateClientHandler() {}

    /**
     * Handle OpenTemplateEditorPayload - opens the editor screen.
     */
    public static void handleOpenEditor(OpenTemplateEditorPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            mc.setScreen(new TemplateEditorScreen(
                payload.zoneId(),
                payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(), payload.floorY(),
                payload.availableTemplates(),
                payload.currentTemplateId()
            ));
        });
    }
}

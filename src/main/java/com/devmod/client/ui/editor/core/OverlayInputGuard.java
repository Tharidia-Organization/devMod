package com.devmod.client.ui.editor.core;

/**
 * Small helper for overlay input handling; split out for unit tests without Minecraft dependencies.
 */
public final class OverlayInputGuard {
    private OverlayInputGuard() {}

    public static boolean shouldConsumePresetInput(boolean presetsOpen) {
        return presetsOpen;
    }
}

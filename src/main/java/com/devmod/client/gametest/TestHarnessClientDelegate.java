package com.devmod.client.gametest;

import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.ui.hub.TestingHub;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

/**
 * Client-side delegate for TestHarnessCommands.
 *
 * This class isolates all client-only singleton access (DebugRenderer.INSTANCE,
 * Impact3DPanelManager.INSTANCE, etc.) from the common TestHarnessCommands class.
 *
 * This prevents ClassNotFoundException/NoClassDefFoundError on dedicated servers
 * where client classes like GuiGraphics, Font, and Minecraft are not available.
 *
 * All methods in this class are safe to call ONLY from client-side code.
 * The caller (TestHarnessCommands) must verify FMLEnvironment.dist.isClient()
 * before invoking any method here.
 */
public final class TestHarnessClientDelegate {

    private TestHarnessClientDelegate() {} // Utility class

    // ==================== Panel Manager ====================

    public static void setPanelEnabled(boolean enabled) {
        Impact3DPanelManager.INSTANCE.setEnabled(enabled);
    }

    public static void togglePanel() {
        Impact3DPanelManager.INSTANCE.toggle();
    }

    public static boolean isPanelEnabled() {
        return Impact3DPanelManager.INSTANCE.isEnabled();
    }

    public static int getPanelCount() {
        return Impact3DPanelManager.INSTANCE.getPanelCount();
    }

    public static void clearPanels() {
        Impact3DPanelManager.INSTANCE.clear();
    }

    // ==================== Debug Renderer ====================

    public static void setDebugEnabled(boolean enabled) {
        if (enabled) {
            DebugRenderer.INSTANCE.enable();
        } else {
            DebugRenderer.INSTANCE.disable();
        }
    }

    public static void toggleDebug() {
        DebugRenderer.INSTANCE.toggle();
    }

    public static boolean isDebugEnabled() {
        return DebugRenderer.INSTANCE.isEnabled();
    }

    public static void clearDebugShapes() {
        DebugRenderer.INSTANCE.clear();
    }

    public static void addDebugBox(AABB box, int color, boolean wireframe, long durationMs) {
        DebugRenderer.INSTANCE.addBox(box, color, wireframe, durationMs);
    }

    // ==================== Impact HUD Overlay ====================

    public static void setHudEnabled(boolean enabled) {
        ImpactHudOverlay.setEnabled(enabled);
    }

    public static void toggleHud() {
        ImpactHudOverlay.toggle();
    }

    public static boolean isHudEnabled() {
        return ImpactHudOverlay.isEnabled();
    }

    // ==================== Screens ====================

    public static void openTestingHub() {
        Minecraft.getInstance().execute(() ->
            Minecraft.getInstance().setScreen(new TestingHub()));
    }

    // ==================== Info ====================

    /**
     * Returns formatted status info for all debug systems.
     */
    public static String[] getSystemStatus() {
        return new String[] {
            isHudEnabled() ? "ON" : "OFF",
            isPanelEnabled() ? "ON" : "OFF",
            String.valueOf(getPanelCount()),
            isDebugEnabled() ? "ON" : "OFF"
        };
    }
}

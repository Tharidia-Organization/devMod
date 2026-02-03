package com.devmod.client.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import com.devmod.client.overlay.Impact3DPanelManager;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.rendering.DebugRenderer;
import com.devmod.client.ui.hub.TestingHub;
import com.devmod.abilities.AbilityActionPayload;
import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.endurance.RequestMobPoolConfigPayload;
import com.devmod.endurance.config.ConfigScope;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.mailbox.network.payload.MailboxActionPayload;

import net.neoforged.neoforge.network.PacketDistributor;

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
        com.devmod.client.ui.ScreenSafety.openSafe(
            "testing_hub",
            () -> new TestingHub());
    }

    // ==================== Network Payload Smoke ====================

    /**
     * Sends a small set of client->server payloads to exercise codecs and sizing.
     */
    public static void runPayloadSmoke() {
        PacketDistributor.sendToServer(AbilityActionPayload.dash());
        PacketDistributor.sendToServer(AbilityActionPayload.dodge(DodgeAbilitySystem.DodgeDirection.BACK));
        PacketDistributor.sendToServer(new RequestMobPoolConfigPayload(ConfigScope.SESSION));
        PacketDistributor.sendToServer(MailboxActionPayload.refresh());
        var player = Minecraft.getInstance().player;
        if (player != null) {
            PacketDistributor.sendToServer(new TelepadConfigPayload(player.blockPosition(), "SmokeTelepad"));
        }
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

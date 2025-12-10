package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton that manages all active Impact 3D panels.
 *
 * Responsibilities:
 * - Maintains the list of active panels
 * - Spawns new panels when an impact occurs
 * - Updates panels every client tick
 * - Renders all panels
 * - Removes expired panels
 * - Manages the maximum panel limit
 */
@SuppressWarnings("null") // Minecraft API methods are not annotated but never return null in practice
public class Impact3DPanelManager {

    // Singleton instance
    public static final Impact3DPanelManager INSTANCE = new Impact3DPanelManager();

    // === Configuration ===
    private static final int MAX_PANELS = 12;  // Max simultaneous panels (performance)
    private static final double MAX_RENDER_DISTANCE = 64.0; // Don't render panels beyond this distance
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // === State === (thread-safe to avoid ConcurrentModificationException)
    private final List<Impact3DPanel> activePanels = new CopyOnWriteArrayList<>();
    private boolean enabled = true;

    private Impact3DPanelManager() {}

    /**
     * Spawns a new 3D panel from an impact.
     *
     * @param data Impact data (must contain hitPoint)
     */
    public void spawnPanelFromImpact(ImpactData data) {
        if (!enabled) return;
        if (data == null) return;

        // Verify there's a valid hit point
        Vec3 hitPoint = data.hitPoint;
        if (hitPoint == null) {
            // If no hit point, try using target position
            // NOTE: For teleporting entities (Enderman), target might
            // already be null. In this case use player position + direction
            var target = data.getTarget();
            if (target != null) {
                hitPoint = target.position().add(0, target.getBbHeight() * 0.5, 0);
            } else {
                // Fallback: use player position + 3 blocks in look direction
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    Vec3 look = mc.player.getLookAngle();
                    hitPoint = mc.player.getEyePosition().add(look.scale(3.0));
                } else {
                    // No valid point, cannot spawn
                    return;
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Remove excess panels (FIFO)
        while (activePanels.size() >= MAX_PANELS) {
            activePanels.remove(0);
        }

        // Create and add the new panel
        Impact3DPanel panel = new Impact3DPanel(hitPoint, data, cameraPos);
        activePanels.add(panel);
    }

    /**
     * Updates all active panels.
     * Called every client tick from ClientTickEvent.
     */
    public void clientTick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);

        // Update all panels (CopyOnWriteArrayList is safe for iteration)
        for (Impact3DPanel panel : activePanels) {
            panel.update(level, player, partialTick);
        }

        // Remove expired panels (removeIf works with CopyOnWriteArrayList)
        activePanels.removeIf(Impact3DPanel::isExpired);
    }

    /**
     * Renders all active panels.
     * Called during RenderLevelStageEvent (AFTER_ENTITIES).
     *
     * <p>PERFORMANCE OPTIMIZATION: Panels beyond MAX_RENDER_DISTANCE are
     * skipped to reduce rendering load.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Buffer for rendering
     * @param camera Active camera
     * @param partialTick Partial tick
     */
    public void renderAllPanels(PoseStack poseStack, MultiBufferSource bufferSource,
                                 Camera camera, float partialTick) {
        if (!enabled) return;
        if (activePanels.isEmpty()) return;

        Vec3 cameraPos = camera.getPosition();

        for (Impact3DPanel panel : activePanels) {
            // DISTANCE-GATED RENDERING: Skip panels too far away
            Vec3 panelPos = panel.getPanelPosition();
            if (panelPos != null) {
                double distSq = panelPos.distanceToSqr(cameraPos);
                if (distSq > MAX_RENDER_DISTANCE_SQ) {
                    continue; // Skip rendering, panel too far
                }
            }

            panel.render(poseStack, bufferSource, camera, partialTick);
        }

        // Flush buffer to ensure rendering
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    /**
     * Clears all active panels.
     */
    public void clear() {
        activePanels.clear();
    }

    /**
     * Checks if there are active panels.
     */
    public boolean hasActivePanels() {
        return !activePanels.isEmpty();
    }

    /**
     * Gets the number of active panels.
     */
    public int getPanelCount() {
        return activePanels.size();
    }

    /**
     * Enables/disables the 3D panel system.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    /**
     * Checks if the system is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Toggle enable/disable.
     */
    public void toggle() {
        setEnabled(!enabled);
    }
}

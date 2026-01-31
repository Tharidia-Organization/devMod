package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Impact3DPanelManager {

    // Singleton instance
    public static final Impact3DPanelManager INSTANCE = new Impact3DPanelManager();

    // === Configuration ===
    private static final int MAX_PANELS = 12;  // Max simultaneous panels (performance)
    private static final double MAX_RENDER_DISTANCE = 64.0; // Don't render panels beyond this distance
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    // === State ===
    private final List<Impact3DPanel> activePanels = new ArrayList<>();
    private boolean enabled = true;
    private int lastRenderedFull = 0;
    private int lastRenderedCompact = 0;
    private int lastRenderedMinimal = 0;
    private int lastCacheHits = 0;
    private int lastCacheMisses = 0;

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
        Vec3 hitPoint = data.getHitPoint();
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
                var player = mc.player;
                if (player != null) {
                    Vec3 look = player.getLookAngle();
                    hitPoint = player.getEyePosition().add(Objects.requireNonNull(look.scale(3.0)));
                } else {
                    // No valid point, cannot spawn
                    return;
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        if (hitPoint.distanceToSqr(Objects.requireNonNull(cameraPos)) > MAX_RENDER_DISTANCE_SQ) {
            return;
        }
        Vec3 panelPosition = resolvePanelPosition(mc, hitPoint, cameraPos);

        // Create and add the new panel
        Impact3DPanel panel = new Impact3DPanel(hitPoint, panelPosition, data);
        synchronized (activePanels) {
            // Remove excess panels (FIFO)
            while (activePanels.size() >= MAX_PANELS) {
                activePanels.remove(0);
            }
            activePanels.add(panel);
        }
    }

    private Vec3 resolvePanelPosition(Minecraft mc, Vec3 hitPoint, Vec3 cameraPos) {
        Vec3 rightCandidate = Impact3DRenderer.INSTANCE.calculatePanelPosition(hitPoint, cameraPos);
        Vec3 leftCandidate = Objects.requireNonNull(hitPoint.subtract(Objects.requireNonNull(rightCandidate.subtract(hitPoint))));

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return rightCandidate;
        }

        if (isVisible(level, player, cameraPos, rightCandidate)) {
            return rightCandidate;
        }
        if (isVisible(level, player, cameraPos, leftCandidate)) {
            return leftCandidate;
        }
        return rightCandidate;
    }

    private boolean isVisible(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(
            Objects.requireNonNull(from), Objects.requireNonNull(to),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Objects.requireNonNull(player));
        var hitResult = level.clip(context);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        double hitDistSq = Objects.requireNonNull(hitResult.getLocation()).distanceToSqr(from);
        double targetDistSq = to.distanceToSqr(from);
        return hitDistSq >= targetDistSq - 0.01;
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

        synchronized (activePanels) {
            for (Impact3DPanel panel : activePanels) {
                panel.update(level, player, partialTick);
            }
            for (int i = activePanels.size() - 1; i >= 0; i--) {
                if (activePanels.get(i).isExpired()) {
                    activePanels.remove(i);
                }
            }
        }
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
        if (!enabled) {
            resetRenderStats();
            return;
        }
        synchronized (activePanels) {
            if (activePanels.isEmpty()) {
                resetRenderStats();
                return;
            }
        }

        Vec3 cameraPos = camera.getPosition();
        int full = 0;
        int compact = 0;
        int minimal = 0;
        int cacheHits = 0;
        int cacheMisses = 0;

        synchronized (activePanels) {
            for (Impact3DPanel panel : activePanels) {
                // DISTANCE-GATED RENDERING: Skip panels too far away
                Vec3 panelPos = panel.getPanelPosition();
                if (panelPos != null) {
                    double distSq = panelPos.distanceToSqr(Objects.requireNonNull(cameraPos));
                    if (distSq > MAX_RENDER_DISTANCE_SQ) {
                        continue; // Skip rendering, panel too far
                    }
                }

                int outcome = panel.renderAndCollectStats(poseStack, bufferSource, camera, partialTick);
                if (outcome < 0) {
                    continue;
                }
                boolean cacheHit = (outcome & Impact3DPanel.CACHE_HIT_FLAG) != 0;
                int detailIndex = outcome & 0x0F;
                switch (detailIndex) {
                    case Impact3DPanel.DETAIL_FULL -> full++;
                    case Impact3DPanel.DETAIL_COMPACT -> compact++;
                    case Impact3DPanel.DETAIL_MINIMAL -> minimal++;
                    default -> {}
                }
                if (cacheHit) {
                    cacheHits++;
                } else {
                    cacheMisses++;
                }
            }
        }

        lastRenderedFull = full;
        lastRenderedCompact = compact;
        lastRenderedMinimal = minimal;
        lastCacheHits = cacheHits;
        lastCacheMisses = cacheMisses;
    }

    /**
     * Clears all active panels.
     */
    public void clear() {
        synchronized (activePanels) {
            activePanels.clear();
        }
        resetRenderStats();
    }

    /**
     * Checks if there are active panels.
     */
    public boolean hasActivePanels() {
        synchronized (activePanels) {
            return !activePanels.isEmpty();
        }
    }

    /**
     * Gets the number of active panels.
     */
    public int getPanelCount() {
        synchronized (activePanels) {
            return activePanels.size();
        }
    }

    public int getLastRenderedFull() {
        return lastRenderedFull;
    }

    public int getLastRenderedCompact() {
        return lastRenderedCompact;
    }

    public int getLastRenderedMinimal() {
        return lastRenderedMinimal;
    }

    public int getLastCacheHits() {
        return lastCacheHits;
    }

    public int getLastCacheMisses() {
        return lastCacheMisses;
    }

    private void resetRenderStats() {
        lastRenderedFull = 0;
        lastRenderedCompact = 0;
        lastRenderedMinimal = 0;
        lastCacheHits = 0;
        lastCacheMisses = 0;
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

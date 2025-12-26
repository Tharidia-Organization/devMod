package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Represents a single 3D panel instance in the world.
 * Manages position, lifecycle (fade in/out), and rendering.
 *
 * Each panel is associated with a specific impact and contains:
 * - Original impact point position
 * - Panel position (offset from hit point)
 * - Impact data (ImpactData)
 * - Timestamp for lifecycle management
 */

public class Impact3DPanel {

    // === Lifecycle Configuration ===
    private static final long LIFETIME_MS = 4000;        // Total duration: 4 seconds
    private static final long FADE_IN_MS = 500;          // Fade in: 500ms (smoother)
    private static final long FADE_OUT_MS = 1000;        // Fade out: 1000ms (gentler)
    private static final long FADE_OUT_START = LIFETIME_MS - FADE_OUT_MS;

    // === Panel Data ===
    private final Vec3 hitPoint;           // Original impact point
    private Vec3 panelPosition;            // Panel position in the world
    private final ImpactData data;         // Impact data
    private final long spawnTime;          // Creation timestamp

    // === State ===
    private boolean expired = false;

    /**
     * Creates a new 3D panel.
     *
     * @param hitPoint Impact point in the world
     * @param data Impact data
     * @param cameraPos Camera position (to calculate panel offset)
     */
    public Impact3DPanel(Vec3 hitPoint, ImpactData data, Vec3 cameraPos) {
        this(hitPoint, Impact3DRenderer.INSTANCE.calculatePanelPosition(hitPoint, cameraPos), data);
    }

    /**
     * Creates a new 3D panel with a precomputed position.
     *
     * @param hitPoint Impact point in the world
     * @param panelPosition Panel position in the world
     * @param data Impact data
     */
    public Impact3DPanel(Vec3 hitPoint, Vec3 panelPosition, ImpactData data) {
        this.hitPoint = hitPoint;
        this.data = data;
        this.spawnTime = System.currentTimeMillis();
        this.panelPosition = panelPosition;
    }

    /**
     * Updates the panel state every client tick.
     *
     * @param level Client level
     * @param player Local player
     * @param partialTick Partial tick for interpolation
     */
    public void update(ClientLevel level, LocalPlayer player, float partialTick) {
        long elapsed = System.currentTimeMillis() - spawnTime;

        // Check expiration
        if (elapsed > LIFETIME_MS) {
            expired = true;
            return;
        }

        // Optional: update panel position if it should follow something
        // For now the position is fixed at creation time
    }

    /**
     * Renders the panel in the world.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Rendering buffer
     * @param camera Active camera
     * @param partialTick Partial tick
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       Camera camera, float partialTick) {
        if (expired) return;

        float alpha = calculateAlpha();
        if (alpha <= 0.01f) return;

        Vec3 cameraPos = camera.getPosition();

        // Delega il rendering a Impact3DRenderer
        Impact3DRenderer.INSTANCE.renderPanel(
            poseStack,
            bufferSource,
            cameraPos,
            panelPosition,
            hitPoint,
            data,
            alpha
        );
    }

    /**
     * Calculates current alpha based on lifecycle.
     *
     * Alpha curve:
     * - 0-200ms: fade in (0 -> 1)
     * - 200-3200ms: full alpha (1)
     * - 3200-4000ms: fade out (1 -> 0)
     *
     * @return Alpha between 0.0 and 1.0
     */
    private float calculateAlpha() {
        long elapsed = System.currentTimeMillis() - spawnTime;

        if (elapsed < 0) return 0f;
        if (elapsed > LIFETIME_MS) return 0f;

        // Fade in
        if (elapsed < FADE_IN_MS) {
            return (float) elapsed / FADE_IN_MS;
        }

        // Full alpha
        if (elapsed < FADE_OUT_START) {
            return 1.0f;
        }

        // Fade out
        float fadeOutProgress = (float) (elapsed - FADE_OUT_START) / FADE_OUT_MS;
        return 1.0f - fadeOutProgress;
    }

    /**
     * Checks if the panel has expired and should be removed.
     */
    public boolean isExpired() {
        return expired || (System.currentTimeMillis() - spawnTime > LIFETIME_MS);
    }

    /**
     * Gets the original impact point.
     */
    public Vec3 getHitPoint() {
        return hitPoint;
    }

    /**
     * Gets the panel position.
     */
    public Vec3 getPanelPosition() {
        return panelPosition;
    }

    /**
     * Gets the impact data.
     */
    public ImpactData getData() {
        return data;
    }

    /**
     * Gets the spawn timestamp.
     */
    public long getSpawnTime() {
        return spawnTime;
    }

    /**
     * Gets the panel age in milliseconds.
     */
    public long getAge() {
        return System.currentTimeMillis() - spawnTime;
    }

    /**
     * Calculates distance from camera.
     */
    public double getDistanceFromCamera(Vec3 cameraPos) {
        return panelPosition.distanceTo(Objects.requireNonNull(cameraPos));
    }

    @Override
    public String toString() {
        return String.format("Impact3DPanel[hit=%s, pos=%s, age=%dms, expired=%s]",
            hitPoint, panelPosition, getAge(), expired);
    }
}

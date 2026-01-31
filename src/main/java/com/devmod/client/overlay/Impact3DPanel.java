package com.devmod.client.overlay;

import java.util.EnumMap;
import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
public class Impact3DPanel {

    // === Lifecycle Configuration ===
    private static final long LIFETIME_MS = 4000;        // Total duration: 4 seconds
    private static final long FADE_IN_MS = 500;          // Fade in: 500ms (smoother)
    private static final long FADE_OUT_MS = 1000;        // Fade out: 1000ms (gentler)
    private static final long FADE_OUT_START = LIFETIME_MS - FADE_OUT_MS;
    private static final ImpactHudContentBuilder.NumberFormat NUMBER_FORMAT =
        new ImpactHudContentBuilder.NumberFormat("%.1f", "%.2f");
    private static final double LOD_MID_SQ = 18.0 * 18.0;
    private static final double LOD_FAR_SQ = 34.0 * 34.0;
    private static final int FPS_SOFT_LIMIT = 45;
    private static final int FPS_HARD_LIMIT = 30;
    public static final int DETAIL_FULL = 0;
    public static final int DETAIL_COMPACT = 1;
    public static final int DETAIL_MINIMAL = 2;
    public static final int CACHE_HIT_FLAG = 0x10;

    // === Panel Data ===
    private final Vec3 hitPoint;           // Original impact point
    private Vec3 panelPosition;            // Panel position in the world
    private final ImpactData data;         // Impact data
    private final long spawnTime;          // Creation timestamp
    private int cachedRevision = -1;
    private final EnumMap<ImpactHudContentBuilder.DetailLevel, Impact3DRenderer.RenderLayout> cachedLayouts =
        new EnumMap<>(ImpactHudContentBuilder.DetailLevel.class);

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
    public int renderAndCollectStats(PoseStack poseStack, MultiBufferSource bufferSource,
                                     Camera camera, float partialTick) {
        if (expired) return -1;

        float alpha = calculateAlpha();
        if (alpha <= 0.01f) return -1;

        Vec3 cameraPos = camera.getPosition();
        ImpactHudContentBuilder.DetailLevel detail = selectDetailLevel(cameraPos);
        LayoutResult layoutResult = getCachedLayout(detail);
        Impact3DRenderer.RenderLayout layout = layoutResult.layout();
        boolean showConnectionLine = detail != ImpactHudContentBuilder.DetailLevel.MINIMAL;

        // Delega il rendering a Impact3DRenderer
        Impact3DRenderer.INSTANCE.renderPanel(
            poseStack,
            bufferSource,
            cameraPos,
            panelPosition,
            hitPoint,
            data,
            layout,
            detail,
            showConnectionLine,
            alpha
        );

        int detailIndex = toDetailIndex(detail);
        int result = detailIndex;
        if (layoutResult.cacheHit()) {
            result |= CACHE_HIT_FLAG;
        }
        return result;
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

    private record LayoutResult(Impact3DRenderer.RenderLayout layout, boolean cacheHit) {}

    private LayoutResult getCachedLayout(ImpactHudContentBuilder.DetailLevel detail) {
        int revision = data.getRevision();
        if (cachedRevision != revision) {
            cachedLayouts.clear();
            cachedRevision = revision;
        }
        Impact3DRenderer.RenderLayout layout = cachedLayouts.get(detail);
        if (layout == null) {
            var sections = ImpactHudContentBuilder.buildContent(data, NUMBER_FORMAT, detail);
            layout = Impact3DRenderer.INSTANCE.buildLayout(sections);
            cachedLayouts.put(detail, layout);
            return new LayoutResult(layout, false);
        }
        return new LayoutResult(layout, true);
    }

    private ImpactHudContentBuilder.DetailLevel selectDetailLevel(Vec3 cameraPos) {
        double distSq = panelPosition.distanceToSqr(Objects.requireNonNull(cameraPos));
        ImpactHudContentBuilder.DetailLevel level = distSq > LOD_FAR_SQ
            ? ImpactHudContentBuilder.DetailLevel.MINIMAL
            : distSq > LOD_MID_SQ
                ? ImpactHudContentBuilder.DetailLevel.COMPACT
                : ImpactHudContentBuilder.DetailLevel.FULL;

        int fps = Minecraft.getInstance().getFps();
        if (fps <= FPS_HARD_LIMIT) {
            return ImpactHudContentBuilder.DetailLevel.MINIMAL;
        }
        if (fps <= FPS_SOFT_LIMIT) {
            return degrade(level);
        }
        return level;
    }

    private ImpactHudContentBuilder.DetailLevel degrade(ImpactHudContentBuilder.DetailLevel level) {
        return switch (level) {
            case FULL -> ImpactHudContentBuilder.DetailLevel.COMPACT;
            case COMPACT, MINIMAL -> ImpactHudContentBuilder.DetailLevel.MINIMAL;
        };
    }

    private int toDetailIndex(ImpactHudContentBuilder.DetailLevel detail) {
        return switch (detail) {
            case FULL -> DETAIL_FULL;
            case COMPACT -> DETAIL_COMPACT;
            case MINIMAL -> DETAIL_MINIMAL;
        };
    }

    @Override
    public String toString() {
        return String.format("Impact3DPanel[hit=%s, pos=%s, age=%dms, expired=%s]",
            hitPoint, panelPosition, getAge(), expired);
    }
}

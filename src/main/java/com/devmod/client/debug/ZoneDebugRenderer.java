package com.devmod.client.debug;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.arena.zone.ZoneLayout;
import com.devmod.client.ui.overlay.OverlayTheme;

/**
 * Client-side debug renderer for visualizing arena zone boundaries.
 * Renders zone boundaries as colored lines in the world.
 *
 * Usage:
 * - Enable via /devmod zone debug command
 * - Zone layout is synced from server when entering an arena
 * - Different colors indicate different zone environments
 */
public final class ZoneDebugRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneDebugRenderer.class);

    private ZoneDebugRenderer() {} // Utility class

    // Debug state
    private static boolean enabled = false;
    private static @Nullable ZoneLayout currentLayout = null;
    private static int baseY = 64;

    // Rendering constants
    private static final float LINE_OPACITY = 0.8f;
    private static final int SEGMENTS_PER_CIRCLE = 32;
    private static final float LINE_HEIGHT = 3.0f; // Vertical lines height

    // ============== Public API ==============

    /**
     * Enables or disables zone debug rendering.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            currentLayout = null;
        }
        // Sync transition renderer state
        ZoneTransitionRenderer.setEnabled(value);
        LOGGER.info("Zone debug rendering: {}", value ? "ENABLED" : "DISABLED");
    }

    /**
     * Returns whether zone debug rendering is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the current zone layout to render.
     * Called when receiving zone sync from server.
     */
    public static void setZoneLayout(@Nullable ZoneLayout layout, int floorY) {
        currentLayout = layout;
        baseY = floorY;
        if (layout != null) {
            LOGGER.debug("Zone layout set: {} zones, baseY={}", layout.zoneCount(), floorY);
        }
    }

    /**
     * Clears the current zone layout.
     */
    public static void clearZoneLayout() {
        currentLayout = null;
        LOGGER.debug("Zone layout cleared");
    }

    /**
     * Main render method - called from level render event.
     *
     * @param poseStack The pose stack for transforms
     * @param bufferSource Buffer source for rendering
     * @param cameraPos Camera position for offset
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        ZoneLayout layout = currentLayout;
        if (!enabled || layout == null) {
            return;
        }

        try {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = Objects.requireNonNull(pose.pose(), "pose matrix");
            VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));

            for (ArenaZone zone : layout.zones()) {
                int color = getZoneColor(zone.environment());
                renderZoneBoundary(lineConsumer, matrix, zone, color);
            }

            poseStack.popPose();
        } catch (Exception e) {
            LOGGER.warn("Error rendering zone debug: {}", e.getMessage());
        }
    }

    // ============== Zone Rendering ==============

    private static void renderZoneBoundary(VertexConsumer consumer, @Nonnull Matrix4f matrix, ArenaZone zone, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = LINE_OPACITY;

        ArenaZone.ZoneBounds bounds = zone.bounds();
        float y1 = baseY + 0.1f;
        float y2 = baseY + LINE_HEIGHT;

        switch (zone.shape()) {
            case RECTANGULAR -> renderRectangularBoundary(consumer, matrix, bounds, r, g, b, a, y1, y2);
            case CIRCULAR -> renderCircularBoundary(consumer, matrix, bounds, r, g, b, a, y1, y2, false);
            case RING -> {
                // Render both outer and inner radius
                renderCircularBoundary(consumer, matrix, bounds, r, g, b, a, y1, y2, false);
                renderCircularBoundary(consumer, matrix, bounds, r * 0.7f, g * 0.7f, b * 0.7f, a, y1, y2, true);
            }
        }
    }

    private static void renderRectangularBoundary(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                                   ArenaZone.ZoneBounds bounds,
                                                   float r, float g, float b, float a,
                                                   float y1, float y2) {
        float x1 = bounds.x1();
        float z1 = bounds.z1();
        float x2 = bounds.x2();
        float z2 = bounds.z2();

        // Bottom rectangle
        addLine(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top rectangle
        addLine(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges at corners
        addLine(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        addLine(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        addLine(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void renderCircularBoundary(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                                ArenaZone.ZoneBounds bounds,
                                                float r, float g, float b, float a,
                                                float y1, float y2, boolean useInnerRadius) {
        BlockPos center = bounds.getCenter();
        float cx = center.getX() + 0.5f;
        float cz = center.getZ() + 0.5f;

        int radius;
        if (useInnerRadius) {
            radius = bounds.innerRadius().orElse(bounds.radius().orElse(0) / 2);
        } else {
            radius = bounds.radius().orElse(0);
        }
        if (radius <= 0) return;

        // Draw circles at bottom and top
        drawCircle(consumer, matrix, cx, y1, cz, radius, r, g, b, a);
        drawCircle(consumer, matrix, cx, y2, cz, radius, r, g, b, a);

        // Vertical lines at cardinal directions
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            float px = cx + (float)(Math.cos(angle) * radius);
            float pz = cz + (float)(Math.sin(angle) * radius);
            addLine(consumer, matrix, px, y1, pz, px, y2, pz, r, g, b, a);
        }
    }

    private static void drawCircle(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                   float cx, float y, float cz, float radius,
                                   float r, float g, float b, float a) {
        float prevX = cx + radius;
        float prevZ = cz;

        for (int i = 1; i <= SEGMENTS_PER_CIRCLE; i++) {
            double angle = (2 * Math.PI * i) / SEGMENTS_PER_CIRCLE;
            float nextX = cx + (float)(Math.cos(angle) * radius);
            float nextZ = cz + (float)(Math.sin(angle) * radius);

            addLine(consumer, matrix, prevX, y, prevZ, nextX, y, nextZ, r, g, b, a);

            prevX = nextX;
            prevZ = nextZ;
        }
    }

    private static void addLine(VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        // Calculate normal for line (simplified)
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.0001f) return;

        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    // ============== Zone Color Mapping ==============
    // Colors delegated to OverlayTheme.Debug (single source of truth)

    /**
     * Gets a color for a zone based on its environment.
     */
    private static int getZoneColor(ZoneEnvironment env) {
        if (env == null || env.equals(ZoneEnvironment.DEFAULT)) {
            return OverlayTheme.Debug.ZONE_ENV_DEFAULT;
        }

        Optional<net.minecraft.resources.ResourceLocation> biome = env.biome();
        if (biome.isPresent()) {
            String path = biome.get().getPath().toLowerCase(Locale.ROOT);

            // Nether biomes - red/orange
            if (path.contains("nether") || path.contains("basalt") || path.contains("soul")) {
                return OverlayTheme.Debug.ZONE_ENV_NETHER;
            }
            // End biomes - purple
            if (path.contains("end")) {
                return OverlayTheme.Debug.ZONE_ENV_END;
            }
            // Ice/snow biomes - cyan
            if (path.contains("snow") || path.contains("ice") || path.contains("frozen")) {
                return OverlayTheme.Debug.ZONE_ENV_ICE;
            }
            // Desert biomes - yellow
            if (path.contains("desert") || path.contains("badlands")) {
                return OverlayTheme.Debug.ZONE_ENV_DESERT;
            }
            // Ocean biomes - blue
            if (path.contains("ocean") || path.contains("river")) {
                return OverlayTheme.Debug.ZONE_ENV_OCEAN;
            }
            // Forest biomes - green
            if (path.contains("forest") || path.contains("jungle") || path.contains("taiga")) {
                return OverlayTheme.Debug.ZONE_ENV_FOREST;
            }
            // Cave biomes - brown
            if (path.contains("cave") || path.contains("deep_dark") || path.contains("dripstone")) {
                return OverlayTheme.Debug.ZONE_ENV_CAVE;
            }
        }

        // Check time config for day/night
        if (env.time() == ZoneEnvironment.TimeConfig.NIGHT) {
            return OverlayTheme.Debug.ZONE_ENV_NIGHT;
        }
        if (env.time() == ZoneEnvironment.TimeConfig.DAY) {
            return OverlayTheme.Debug.ZONE_ENV_DAY;
        }

        // Check lighting config
        if (env.lighting().blockLight() <= 4) {
            return OverlayTheme.Debug.ZONE_ENV_DARK;
        }
        if (env.lighting().blockLight() >= 12) {
            return OverlayTheme.Debug.ZONE_ENV_BRIGHT;
        }

        return OverlayTheme.Debug.ZONE_ENV_FALLBACK;
    }
}

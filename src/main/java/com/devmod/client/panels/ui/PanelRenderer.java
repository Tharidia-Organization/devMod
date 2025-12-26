package com.devmod.client.panels.ui;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.panels.core.FloatingPanel;
import com.devmod.client.ui.editor.core.UIConstants;

public class PanelRenderer {

    public static final PanelRenderer INSTANCE = new PanelRenderer();

    // === Scale Configuration ===
    private static final float BASE_SCALE = 0.015f;       // Base panel scale
    private static final float MIN_SCALE = 0.008f;        // Minimum scale (far)
    private static final float MAX_SCALE = 0.025f;        // Maximum scale (near)
    private static final float SCALE_DISTANCE_NEAR = 3.0f;
    private static final float SCALE_DISTANCE_FAR = 20.0f;

    // === Distance Fade (soft culling) ===
    private static final double FADE_START_DISTANCE = 24.0;  // Start fading at 24 blocks
    private static final double FADE_END_DISTANCE = 32.0;    // Fully faded at 32 blocks (MAX_RENDER_DISTANCE)

    // === Layout ===
    private static final float HEADER_HEIGHT = 16f;
    private static final float PADDING = 6f;

    // === Colors (Axiom style) ===
    private static final int BG_COLOR = UIConstants.Background.PANEL();
    private static final int HEADER_BG = UIConstants.Background.HEADER();
    private static final int BORDER_COLOR = UIConstants.Border.DEFAULT();
    private static final int BORDER_HOVER = UIConstants.Border.ACCENT();
    private static final int TITLE_COLOR = UIConstants.Text.PRIMARY();
    private static final int PIN_COLOR = UIConstants.Status.WARNING();

    // === Title truncation cache ===
    // Key: "title|maxWidth", Value: truncated title
    private static final Map<String, String> TRUNCATED_TITLE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100;

    private PanelRenderer() {}

    /**
     * Render a panel in the 3D world.
     *
     * @param poseStack Transform stack
     * @param bufferSource Buffer for rendering
     * @param cameraPos Camera position
     * @param panel Panel to render
     * @param partialTick Partial tick
     */
    public void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                            Vec3 cameraPos, FloatingPanel panel, float partialTick) {

        if (!panel.shouldRender()) return;

        Vec3 panelPos = nn(panel.getWorldPosition());
        double distance = panelPos.distanceTo(nn(cameraPos));

        // Calculate scale based on distance
        float distanceScale = calculateDistanceScale(distance);
        float finalScale = BASE_SCALE * distanceScale * panel.getCurrentScale();

        // Apply distance-based fade (soft culling)
        float distanceFade = calculateDistanceFade(distance);
        float alpha = panel.getCurrentAlpha() * distanceFade;
        if (alpha <= 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Setup transform
        poseStack.pushPose();

        // Translate to position relative to camera
        Vec3 relativePos = nn(panelPos.subtract(nn(cameraPos)));
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

        // Billboard rotation
        Vec3 toCamera = nn(cameraPos.subtract(panelPos).normalize());
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(nn(new Quaternionf().rotationY(yaw)));

        // Scale and flip Y
        poseStack.scale(finalScale, -finalScale, finalScale);

        // Panel dimensions
        int width = panel.getWidth();
        int height = panel.getHeight();

        // Center the panel
        poseStack.translate(-width / 2.0f, -height / 2.0f, 0);

        // Render components
        renderBackground(poseStack, bufferSource, width, height, panel, alpha);
        renderHeader(poseStack, bufferSource, font, width, panel, alpha);

        // Content only if not minimized
        if (!panel.isMinimized()) {
            renderContent(poseStack, bufferSource, font, width, height, panel, alpha);
        }

        poseStack.popPose();
    }

    /**
     * Calculates scale based on distance from camera.
     */
    private float calculateDistanceScale(double distance) {
        if (distance <= SCALE_DISTANCE_NEAR) {
            return MAX_SCALE / BASE_SCALE;
        }
        if (distance >= SCALE_DISTANCE_FAR) {
            return MIN_SCALE / BASE_SCALE;
        }

        // Linear interpolation
        float t = (float) ((distance - SCALE_DISTANCE_NEAR) / (SCALE_DISTANCE_FAR - SCALE_DISTANCE_NEAR));
        float scale = MAX_SCALE + (MIN_SCALE - MAX_SCALE) * t;
        return scale / BASE_SCALE;
    }

    /**
     * Calculates fade alpha based on distance (soft culling).
     * Panels start fading at FADE_START_DISTANCE and are fully transparent at FADE_END_DISTANCE.
     */
    private float calculateDistanceFade(double distance) {
        if (distance <= FADE_START_DISTANCE) {
            return 1.0f; // Full opacity
        }
        if (distance >= FADE_END_DISTANCE) {
            return 0.0f; // Fully faded
        }

        // Linear fade between start and end
        float t = (float) ((distance - FADE_START_DISTANCE) / (FADE_END_DISTANCE - FADE_START_DISTANCE));
        return 1.0f - t;
    }

    /**
     * Renders the panel background.
     */
    private void renderBackground(PoseStack poseStack, MultiBufferSource bufferSource,
                                   int width, int height, FloatingPanel panel, float alpha) {
        Matrix4f matrix = nn(poseStack.last().pose());
        VertexConsumer consumer = bufferSource.getBuffer(nn(RenderType.debugQuads()));

        // Main background
        int bgColor = applyAlpha(BG_COLOR, alpha * 0.95f);
        drawQuad(consumer, matrix, 0, 0, width, height, 0.001f, bgColor);

        // Border
        VertexConsumer lineConsumer = bufferSource.getBuffer(nn(RenderType.lines()));
        int borderColor = panel.isHovered() ? applyAlpha(BORDER_HOVER, alpha) : applyAlpha(BORDER_COLOR, alpha);

        // Accent color for pinned or hovered panels
        if (panel.isPinned()) {
            borderColor = applyAlpha(PIN_COLOR, alpha);
        } else if (panel.isHovered()) {
            borderColor = applyAlpha(panel.getType().getAccentColor(), alpha);
        }

        drawBorder(lineConsumer, matrix, poseStack, 0, 0, width, height, borderColor);
    }

    /**
     * Renders the panel header.
     */
    private void renderHeader(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               int width, FloatingPanel panel, float alpha) {
        Matrix4f matrix = nn(poseStack.last().pose());

        // Header background
        VertexConsumer consumer = bufferSource.getBuffer(nn(RenderType.debugQuads()));
        int headerBg = applyAlpha(HEADER_BG, alpha * 0.9f);
        drawQuad(consumer, matrix, 0, 0, width, HEADER_HEIGHT, 0.0005f, headerBg);

        // Header separator
        VertexConsumer lineConsumer = bufferSource.getBuffer(nn(RenderType.lines()));
        int sepColor = applyAlpha(BORDER_COLOR, alpha * 0.5f);
        drawHLine(lineConsumer, matrix, poseStack, 0, HEADER_HEIGHT, width, sepColor);

        // Title (with caching to avoid repeated font.width() calls)
        poseStack.pushPose();
        poseStack.translate(PADDING, 4, -0.5f);

        String title = panel.getTitle();
        int maxTitleWidth = (int) (width - PADDING * 2 - 30);
        String displayTitle = getTruncatedTitle(font, title, maxTitleWidth);

        renderText3D(poseStack, bufferSource, font, displayTitle, 0, 0, applyAlpha(TITLE_COLOR, alpha));

        poseStack.popPose();

        // Icone header (pin, minimize)
        renderHeaderIcons(poseStack, bufferSource, font, width, panel, alpha);
    }

    /**
     * Renders the header icons (pin, close).
     */
    private void renderHeaderIcons(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                    int width, FloatingPanel panel, float alpha) {
        poseStack.pushPose();
        float iconX = width - PADDING - 8;
        float iconY = 4;

        // Pin icon
        if (panel.getType().canPin()) {
            String pinIcon = panel.isPinned() ? "*" : "o";
            int pinColor = panel.isPinned() ? applyAlpha(PIN_COLOR, alpha) : applyAlpha(UIConstants.Text.MUTED(), alpha);
            poseStack.translate(iconX - 10, iconY, -0.5f);
            renderText3D(poseStack, bufferSource, font, pinIcon, 0, 0, pinColor);
            poseStack.translate(-(iconX - 10), -iconY, 0.5f);
        }

        // Minimize icon
        String minIcon = panel.isMinimized() ? "+" : "-";
        poseStack.translate(iconX, iconY, -0.5f);
        renderText3D(poseStack, bufferSource, font, minIcon, 0, 0, applyAlpha(UIConstants.Text.SECONDARY(), alpha));

        poseStack.popPose();
    }

    /**
     * Renders the panel content.
     * Delegates to the specific panel's renderContent3D method.
     */
    private void renderContent(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                int width, int height, FloatingPanel panel, float alpha) {
        poseStack.pushPose();

        // Content area (below header)
        float contentY = HEADER_HEIGHT + PADDING;
        int contentHeight = (int) (height - HEADER_HEIGHT - PADDING * 2);
        int contentWidth = (int) (width - PADDING * 2);

        poseStack.translate(PADDING, contentY, -0.3f);

        // Delegate rendering to the specific panel
        panel.renderContent3D(poseStack, nn(bufferSource), nn(font), contentWidth, contentHeight, alpha);

        poseStack.popPose();
    }

    // === Utility Methods ===

    /**
     * Renders 3D text.
     */
    private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               String text, float x, float y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);

        Matrix4f matrix = nn(poseStack.last().pose());
        font.drawInBatch(
            nn(text),
            0, 0,
            color,
            false,
            nn(matrix),
            nn(bufferSource),
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        );

        poseStack.popPose();
    }

    /**
     * Draws a filled quad.
     */
    private void drawQuad(VertexConsumer consumer, Matrix4f matrix,
                          float x, float y, float w, float h, float z, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(nn(matrix), x, y, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(nn(matrix), x, y + h, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(nn(matrix), x + w, y + h, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(nn(matrix), x + w, y, z).setColor(r, g, b, a).setNormal(0, 0, 1);
    }

    /**
     * Draws a rectangular border.
     */
    private void drawBorder(VertexConsumer consumer, Matrix4f matrix, PoseStack poseStack,
                            float x, float y, float w, float h, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        // Top
        consumer.addVertex(nn(matrix), x, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
        consumer.addVertex(nn(matrix), x + w, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
        // Right
        consumer.addVertex(nn(matrix), x + w, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 0, 1, 0);
        consumer.addVertex(nn(matrix), x + w, y + h, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 0, 1, 0);
        // Bottom
        consumer.addVertex(nn(matrix), x + w, y + h, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
        consumer.addVertex(nn(matrix), x, y + h, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
        // Left
        consumer.addVertex(nn(matrix), x, y + h, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 0, 1, 0);
        consumer.addVertex(nn(matrix), x, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 0, 1, 0);
    }

    /**
     * Draws a horizontal line.
     */
    private void drawHLine(VertexConsumer consumer, Matrix4f matrix, PoseStack poseStack,
                           float x, float y, float w, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(nn(matrix), x, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
        consumer.addVertex(nn(matrix), x + w, y, 0).setColor(r, g, b, a).setNormal(nn(poseStack.last()), 1, 0, 0);
    }

    /**
     * Applies alpha to an ARGB color.
     */
    private int applyAlpha(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        if (originalAlpha == 0) originalAlpha = 255;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Returns a truncated title with caching to avoid repeated font.width() calls.
     * Uses Font.plainSubstrByWidth for efficient single-pass truncation.
     *
     * OPTIMIZATION: Check cache FIRST before any font.width() calls.
     * This avoids per-frame string measurement when we already know the result.
     *
     * @param font The font to measure with
     * @param title The original title
     * @param maxWidth Maximum width in pixels
     * @return The truncated title (with "..." if needed), or original if it fits
     */
    private String getTruncatedTitle(Font font, String title, int maxWidth) {
        // Check cache FIRST - avoids font.width() call on cache hit
        String cacheKey = title + "|" + maxWidth;
        String cached = TRUNCATED_TITLE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Cache miss - measure and truncate
        String result;
        if (font.width(nn(title)) <= maxWidth) {
            // Title fits, cache the original
            result = title;
        } else {
            // Use plainSubstrByWidth for efficient single-pass truncation
            // Reserve space for "..." (approximately 10 pixels)
            int ellipsisWidth = font.width("...");
            int availableWidth = Math.max(0, maxWidth - ellipsisWidth);

            // Get the substring that fits in availableWidth
            String truncated = nn(font.plainSubstrByWidth(nn(title), availableWidth));

            // If we actually truncated, add ellipsis
            if (truncated.length() < title.length()) {
                result = truncated + "...";
            } else {
                result = title;
            }
        }

        // Cache the result (with size limit to prevent memory leak)
        if (TRUNCATED_TITLE_CACHE.size() >= MAX_CACHE_SIZE) {
            // Simple eviction: clear half the cache
            TRUNCATED_TITLE_CACHE.clear();
        }
        TRUNCATED_TITLE_CACHE.put(cacheKey, result);

        return result;
    }

    /**
     * Clears the title truncation cache.
     * Call this when font settings change or on world unload.
     */
    public static void clearTitleCache() {
        TRUNCATED_TITLE_CACHE.clear();
    }

    // === Null-safety helper ===
    @Nonnull
    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }
}

package com.frenkvs.devmod.panels.ui;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.PanelState;
import com.frenkvs.devmod.ui.UIConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderizza i FloatingPanel nel mondo 3D.
 *
 * Caratteristiche:
 * - Billboard orientation (sempre rivolto alla camera)
 * - Supporto per alpha e scale animati
 * - Rendering di sfondo, bordi, header, contenuto
 * - Stile coerente con Axiom UI
 */
public class PanelRenderer {

    public static final PanelRenderer INSTANCE = new PanelRenderer();

    // === Scale Configuration ===
    private static final float BASE_SCALE = 0.015f;       // Scala base pannello
    private static final float MIN_SCALE = 0.008f;        // Scala minima (lontano)
    private static final float MAX_SCALE = 0.025f;        // Scala massima (vicino)
    private static final float SCALE_DISTANCE_NEAR = 3.0f;
    private static final float SCALE_DISTANCE_FAR = 20.0f;

    // === Distance Fade (soft culling) ===
    private static final double FADE_START_DISTANCE = 24.0;  // Start fading at 24 blocks
    private static final double FADE_END_DISTANCE = 32.0;    // Fully faded at 32 blocks (MAX_RENDER_DISTANCE)

    // === Layout ===
    private static final float HEADER_HEIGHT = 16f;
    private static final float PADDING = 6f;
    private static final float BORDER_WIDTH = 1f;

    // === Colors (Axiom style) ===
    private static final int BG_COLOR = UIConstants.Background.PANEL;
    private static final int HEADER_BG = UIConstants.Background.HEADER;
    private static final int BORDER_COLOR = UIConstants.Border.DEFAULT;
    private static final int BORDER_HOVER = UIConstants.Border.ACCENT;
    private static final int TITLE_COLOR = UIConstants.Text.PRIMARY;
    private static final int PIN_COLOR = UIConstants.Status.WARNING;

    // === Title truncation cache ===
    // Key: "title|maxWidth", Value: truncated title
    private static final Map<String, String> TRUNCATED_TITLE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100;

    private PanelRenderer() {}

    /**
     * Renderizza un pannello nel mondo 3D.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per rendering
     * @param cameraPos Posizione camera
     * @param panel Pannello da renderizzare
     * @param partialTick Tick parziale
     */
    public void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                            Vec3 cameraPos, FloatingPanel panel, float partialTick) {

        if (!panel.shouldRender()) return;

        Vec3 panelPos = panel.getWorldPosition();
        double distance = panelPos.distanceTo(cameraPos);

        // Calcola scala basata sulla distanza
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

        // Trasla alla posizione relativa alla camera
        Vec3 relativePos = panelPos.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

        // Billboard rotation
        Vec3 toCamera = cameraPos.subtract(panelPos).normalize();
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));

        // Scala e flip Y
        poseStack.scale(finalScale, -finalScale, finalScale);

        // Dimensioni pannello
        int width = panel.getWidth();
        int height = panel.getHeight();

        // Centra il pannello
        poseStack.translate(-width / 2.0f, -height / 2.0f, 0);

        // Renderizza componenti
        renderBackground(poseStack, bufferSource, width, height, panel, alpha);
        renderHeader(poseStack, bufferSource, font, width, panel, alpha);

        // Contenuto solo se non minimizzato
        if (!panel.isMinimized()) {
            renderContent(poseStack, bufferSource, font, width, height, panel, alpha);
        }

        poseStack.popPose();
    }

    /**
     * Calcola la scala basata sulla distanza dalla camera.
     */
    private float calculateDistanceScale(double distance) {
        if (distance <= SCALE_DISTANCE_NEAR) {
            return MAX_SCALE / BASE_SCALE;
        }
        if (distance >= SCALE_DISTANCE_FAR) {
            return MIN_SCALE / BASE_SCALE;
        }

        // Interpolazione lineare
        float t = (float) ((distance - SCALE_DISTANCE_NEAR) / (SCALE_DISTANCE_FAR - SCALE_DISTANCE_NEAR));
        float scale = MAX_SCALE + (MIN_SCALE - MAX_SCALE) * t;
        return scale / BASE_SCALE;
    }

    /**
     * Calcola il fade alpha basato sulla distanza (soft culling).
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
     * Renderizza lo sfondo del pannello.
     */
    private void renderBackground(PoseStack poseStack, MultiBufferSource bufferSource,
                                   int width, int height, FloatingPanel panel, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());

        // Sfondo principale
        int bgColor = applyAlpha(BG_COLOR, alpha * 0.95f);
        drawQuad(consumer, matrix, 0, 0, width, height, 0.001f, bgColor);

        // Bordo
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        int borderColor = panel.isHovered() ? applyAlpha(BORDER_HOVER, alpha) : applyAlpha(BORDER_COLOR, alpha);

        // Accent color per pannelli pinnati o hovered
        if (panel.isPinned()) {
            borderColor = applyAlpha(PIN_COLOR, alpha);
        } else if (panel.isHovered()) {
            borderColor = applyAlpha(panel.getType().getAccentColor(), alpha);
        }

        drawBorder(lineConsumer, matrix, poseStack, 0, 0, width, height, borderColor);
    }

    /**
     * Renderizza l'header del pannello.
     */
    private void renderHeader(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               int width, FloatingPanel panel, float alpha) {
        Matrix4f matrix = poseStack.last().pose();

        // Header background
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        int headerBg = applyAlpha(HEADER_BG, alpha * 0.9f);
        drawQuad(consumer, matrix, 0, 0, width, HEADER_HEIGHT, 0.0005f, headerBg);

        // Separatore header
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        int sepColor = applyAlpha(BORDER_COLOR, alpha * 0.5f);
        drawHLine(lineConsumer, matrix, poseStack, 0, HEADER_HEIGHT, width, sepColor);

        // Titolo (with caching to avoid repeated font.width() calls)
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
     * Renderizza le icone dell'header (pin, close).
     */
    private void renderHeaderIcons(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                    int width, FloatingPanel panel, float alpha) {
        poseStack.pushPose();
        float iconX = width - PADDING - 8;
        float iconY = 4;

        // Pin icon
        if (panel.getType().canPin()) {
            String pinIcon = panel.isPinned() ? "*" : "o";
            int pinColor = panel.isPinned() ? applyAlpha(PIN_COLOR, alpha) : applyAlpha(UIConstants.Text.MUTED, alpha);
            poseStack.translate(iconX - 10, iconY, -0.5f);
            renderText3D(poseStack, bufferSource, font, pinIcon, 0, 0, pinColor);
            poseStack.translate(-(iconX - 10), -iconY, 0.5f);
        }

        // Minimize icon
        String minIcon = panel.isMinimized() ? "+" : "-";
        poseStack.translate(iconX, iconY, -0.5f);
        renderText3D(poseStack, bufferSource, font, minIcon, 0, 0, applyAlpha(UIConstants.Text.SECONDARY, alpha));

        poseStack.popPose();
    }

    /**
     * Renderizza il contenuto del pannello.
     * Delega al metodo renderContent3D del pannello specifico.
     */
    private void renderContent(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                int width, int height, FloatingPanel panel, float alpha) {
        poseStack.pushPose();

        // Area contenuto (sotto l'header)
        float contentY = HEADER_HEIGHT + PADDING;
        int contentHeight = (int) (height - HEADER_HEIGHT - PADDING * 2);
        int contentWidth = (int) (width - PADDING * 2);

        poseStack.translate(PADDING, contentY, -0.3f);

        // Delega il rendering al pannello specifico
        panel.renderContent3D(poseStack, bufferSource, font, contentWidth, contentHeight, alpha);

        poseStack.popPose();
    }

    // === Utility Methods ===

    /**
     * Renderizza testo 3D.
     */
    private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               String text, float x, float y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);

        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(
            text,
            0, 0,
            color,
            false,
            matrix,
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0,
            15728880
        );

        poseStack.popPose();
    }

    /**
     * Disegna un quad filled.
     */
    private void drawQuad(VertexConsumer consumer, Matrix4f matrix,
                          float x, float y, float w, float h, float z, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x, y + h, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x + w, y + h, z).setColor(r, g, b, a).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x + w, y, z).setColor(r, g, b, a).setNormal(0, 0, 1);
    }

    /**
     * Disegna un bordo rettangolare.
     */
    private void drawBorder(VertexConsumer consumer, Matrix4f matrix, PoseStack poseStack,
                            float x, float y, float w, float h, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        // Top
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        consumer.addVertex(matrix, x + w, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        // Right
        consumer.addVertex(matrix, x + w, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 0, 1, 0);
        consumer.addVertex(matrix, x + w, y + h, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 0, 1, 0);
        // Bottom
        consumer.addVertex(matrix, x + w, y + h, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        consumer.addVertex(matrix, x, y + h, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        // Left
        consumer.addVertex(matrix, x, y + h, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 0, 1, 0);
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 0, 1, 0);
    }

    /**
     * Disegna una linea orizzontale.
     */
    private void drawHLine(VertexConsumer consumer, Matrix4f matrix, PoseStack poseStack,
                           float x, float y, float w, int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        consumer.addVertex(matrix, x + w, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
    }

    /**
     * Applica alpha a un colore ARGB.
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
        if (font.width(title) <= maxWidth) {
            // Title fits, cache the original
            result = title;
        } else {
            // Use plainSubstrByWidth for efficient single-pass truncation
            // Reserve space for "..." (approximately 10 pixels)
            int ellipsisWidth = font.width("...");
            int availableWidth = Math.max(0, maxWidth - ellipsisWidth);

            // Get the substring that fits in availableWidth
            String truncated = font.plainSubstrByWidth(title, availableWidth);

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
}

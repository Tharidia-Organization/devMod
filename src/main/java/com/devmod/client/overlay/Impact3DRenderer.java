package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;

public class Impact3DRenderer {

    // Singleton instance
    public static final Impact3DRenderer INSTANCE = new Impact3DRenderer();

    // === Panel dimensions (in world units) ===
    private static final float PANEL_SCALE = 0.02f;  // Panel base scale (bigger = more readable)
    private static final float PANEL_OFFSET_SIDE = 4.5f;  // Lateral offset from impact point (to the right)
    private static final float PANEL_OFFSET_UP = 1.0f;    // Vertical offset (above the point)

    // === UI colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Panel.BG_HEAVY;
    private static final int PANEL_BORDER = OverlayTheme.Impact.CORE_PRIMARY;

    // === Internal dimensions (in font pixels, 1:1 scale with Minecraft font) ===
    private static final float PANEL_WIDTH_PX = 320f;   // Panel width in font pixels
    private static final float PANEL_HEIGHT_PX = 220f;  // Panel height in font pixels
    private static final float PADDING = 6f;
    private static final float LINE_HEIGHT = 11f;
    private static final float SECTION_SPACING = 5f;

    private static final ImpactHudContentBuilder.NumberFormat NUMBER_FORMAT =
        new ImpactHudContentBuilder.NumberFormat("%.1f", "%.2f");

    private Impact3DRenderer() {}

    /**
     * Renders a 3D panel in the world with impact data.
     * The panel billboards towards the camera.
     *
     * @param poseStack Transformation stack
     * @param bufferSource Buffer for rendering
     * @param cameraPos Camera position
     * @param panelWorldPos Panel position in world
     * @param hitPoint Original impact point (for connection line)
     * @param data Impact data to display
     * @param alpha Global alpha for fade in/out
     */
    public void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                            Vec3 cameraPos, Vec3 panelWorldPos, Vec3 hitPoint,
                            ImpactData data, float alpha) {
        if (alpha <= 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Render connection line from hit point to panel
        renderConnectionLine(poseStack, bufferSource, cameraPos, hitPoint, panelWorldPos, alpha);

        // 2. Translate to panel position (relative to camera)
        poseStack.pushPose();
        Vec3 relativePos = Objects.requireNonNull(panelWorldPos.subtract(Objects.requireNonNull(cameraPos)));
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

        // 3. Calculate billboard rotation (face camera)
        Vec3 toCamera = cameraPos.subtract(panelWorldPos).normalize();
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(Objects.requireNonNull(new Quaternionf().rotationY(yaw)));

        // 4. Uniform scale for panel - negative Y for vertical flip
        poseStack.scale(PANEL_SCALE, -PANEL_SCALE, PANEL_SCALE);

        // 5. Center panel relative to its width/height
        poseStack.translate(-PANEL_WIDTH_PX / 2, -PANEL_HEIGHT_PX / 2, 0);

        // 6. Render panel content
        renderPanelContent(poseStack, bufferSource, data, alpha, mc.font);

        poseStack.popPose();
    }

    /**
     * Renders cyan connection line from impact point to panel.
     * Uses RenderType.lines() to avoid flickering.
     */
    private void renderConnectionLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                       Vec3 cameraPos, Vec3 hitPoint, Vec3 panelPos, float alpha) {
        poseStack.pushPose();

        // Position relative to camera
        Vec3 hitRel = Objects.requireNonNull(hitPoint.subtract(Objects.requireNonNull(cameraPos)));
        Vec3 panelRel = Objects.requireNonNull(panelPos.subtract(Objects.requireNonNull(cameraPos)));

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        // Use standard RenderType.lines() instead of debugLineStrip for stability
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));

        // Cyan color with alpha
        float r = 0.0f;
        float g = 0.9f;
        float b = 1.0f;
        float a = alpha * 0.9f;

        // Straight stable line from hit point to panel
        // RenderType.lines() requires vertex pairs
        var lastPose = Objects.requireNonNull(poseStack.last());
        consumer.addVertex(matrix, (float)hitRel.x, (float)hitRel.y, (float)hitRel.z)
            .setColor(r, g, b, a)
            .setNormal(lastPose, 0, 1, 0);
        consumer.addVertex(matrix, (float)panelRel.x, (float)panelRel.y, (float)panelRel.z)
            .setColor(r, g, b, a)
            .setNormal(lastPose, 0, 1, 0);

        poseStack.popPose();
    }

    /**
     * Renders the panel's internal content (background, text, data).
     */
    private void renderPanelContent(PoseStack poseStack, MultiBufferSource bufferSource,
                                     ImpactData data, float alpha, Font font) {

        // === BACKGROUND ===
        renderPanelBackground(poseStack, bufferSource, alpha);

        // === TEXT CONTENT ===
        // Z offset to avoid z-fighting with background
        poseStack.pushPose();
        poseStack.translate(0, 0, -0.01f);

        float textX = PADDING;
        float textY = PADDING;

        List<ImpactHudContentBuilder.HudSection> sections =
            ImpactHudContentBuilder.buildContent(data, NUMBER_FORMAT);
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                textY += LINE_HEIGHT + SECTION_SPACING;
            }
            textY = renderSection(poseStack, bufferSource, font, sections.get(i), textX, textY, alpha);
        }

        // === DPS DISPLAY (in DETAILED/ANALYSIS mode) ===
        ImpactDisplayMode mode = ImpactHudController.INSTANCE.getDisplayMode();
        if (mode == ImpactDisplayMode.DETAILED || mode == ImpactDisplayMode.ANALYSIS) {
            float currentDps = ImpactDpsTracker.getCurrentDps(data.attackerUUID);
            if (currentDps > 0.1f) {
                textY += LINE_HEIGHT;
                String dpsText = String.format("DPS: %.1f/s", currentDps);
                int dpsColor = OverlayTheme.Impact3D.DPS;
                renderText3D(poseStack, bufferSource, font, dpsText, textX, textY,
                    applyAlpha(dpsColor, alpha), alpha);
            }
        }

        poseStack.popPose();
    }

    private float renderSection(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                ImpactHudContentBuilder.HudSection section,
                                float textX, float textY, float alpha) {
        renderText3D(poseStack, bufferSource, font,
            section.title().getString(),
            textX, textY, applyAlpha(ImpactHudContentBuilder.Colors.TITLE, alpha), alpha);
        textY += LINE_HEIGHT + spacingPixels(section.titleSpacing());

        if (section.drawSeparator()) {
            renderSeparatorLine(poseStack, bufferSource, 4, textY, PANEL_WIDTH_PX - 12, alpha);
            textY += SECTION_SPACING;
        }

        for (ImpactHudContentBuilder.HudLine line : section.lines()) {
            renderLine(poseStack, bufferSource, font, line, textX, textY, alpha);
            textY += LINE_HEIGHT + spacingPixels(line.spacingAfter());
        }

        return textY;
    }

    private void renderLine(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                            ImpactHudContentBuilder.HudLine line, float x, float y, float alpha) {
        renderText3D(poseStack, bufferSource, font,
            line.text().getString(),
            x, y, applyAlpha(line.color(), alpha), alpha);
    }

    private float spacingPixels(ImpactHudContentBuilder.Spacing spacing) {
        return switch (spacing) {
            case NONE -> 0f;
            case SMALL -> 2f;
            case SECTION -> SECTION_SPACING;
            case LARGE -> 4f;
        };
    }

    /**
     * Renders the panel background with border.
     * Uses RenderType.gui() for stable rendering without flickering.
     */
    private void renderPanelBackground(PoseStack poseStack, MultiBufferSource bufferSource, float alpha) {
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        float w = PANEL_WIDTH_PX;
        float h = PANEL_HEIGHT_PX;

        // Main background - use debug quads which is more stable for filled areas
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));

        // Semi-transparent dark background
        int bgColor = applyAlphaARGB(PANEL_BG, alpha);
        float br = ((bgColor >> 16) & 0xFF) / 255f;
        float bgc = ((bgColor >> 8) & 0xFF) / 255f;
        float bb = (bgColor & 0xFF) / 255f;
        float ba = ((bgColor >> 24) & 0xFF) / 255f;

        // Quad for background (vertex order for correct display)
        Matrix4f m = Objects.requireNonNull(matrix);
        consumer.addVertex(m, 0, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(m, 0, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(m, w, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(m, w, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);

        // Border with RenderType.lines() - more stable
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        int borderColor = applyAlphaARGB(PANEL_BORDER, alpha * 0.9f);
        float bor = ((borderColor >> 16) & 0xFF) / 255f;
        float bog = ((borderColor >> 8) & 0xFF) / 255f;
        float bob = (borderColor & 0xFF) / 255f;
        float boa = ((borderColor >> 24) & 0xFF) / 255f;

        var lastPose = Objects.requireNonNull(poseStack.last());
        // Top line
        lineConsumer.addVertex(m, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 1, 0, 0);
        lineConsumer.addVertex(m, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 1, 0, 0);
        // Right line
        lineConsumer.addVertex(m, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 0, 1, 0);
        lineConsumer.addVertex(m, w, h, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 0, 1, 0);
        // Bottom line
        lineConsumer.addVertex(m, w, h, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 1, 0, 0);
        lineConsumer.addVertex(m, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 1, 0, 0);
        // Left line
        lineConsumer.addVertex(m, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 0, 1, 0);
        lineConsumer.addVertex(m, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(lastPose, 0, 1, 0);
    }

    /**
     * Renders a horizontal separator line.
     */
    private void renderSeparatorLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                      float x, float y, float width, float alpha) {
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));

        int color = applyAlphaARGB(PANEL_BORDER, alpha * 0.5f);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        var lastPose = Objects.requireNonNull(poseStack.last());
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(lastPose, 1, 0, 0);
        consumer.addVertex(matrix, x + width, y, 0).setColor(r, g, b, a).setNormal(lastPose, 1, 0, 0);
    }

    /**
     * Renders text in 3D world using Minecraft font.
     * Uses SEE_THROUGH for correct visibility in 3D.
     */
    private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               String text, float x, float y, int color, float globalAlpha) {
        poseStack.pushPose();
        poseStack.translate(x, y, -0.5f); // Z offset to be in front of background

        Matrix4f matrix = poseStack.last().pose();

        // Apply alpha to color
        int alpha = (int) (((color >> 24) & 0xFF) * globalAlpha);
        if (alpha == 0) alpha = (int) (255 * globalAlpha);
        int finalColor = (alpha << 24) | (color & DesignTokens.Mask.RGB);

        // Use SEE_THROUGH for correct 3D rendering (not blocked by depth)
        font.drawInBatch(
            Objects.requireNonNull(text),
            0, 0,
            finalColor,
            false, // shadow
            Objects.requireNonNull(matrix),
            Objects.requireNonNull(bufferSource),
            Font.DisplayMode.SEE_THROUGH,
            0, // backgroundColor
            15728880 // packedLight (full bright)
        );

        poseStack.popPose();
    }

    /**
     * Applies alpha to an RGB color (without original alpha).
     */
    private int applyAlpha(int rgb, float alpha) {
        int a = (int) (255 * alpha);
        return (a << 24) | (rgb & DesignTokens.Mask.RGB);
    }

    /**
     * Applies alpha to an existing ARGB color.
     */
    private int applyAlphaARGB(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & DesignTokens.Mask.RGB);
    }

    /**
     * Calculates panel position given an impact point.
     * The panel is positioned to the side of the impact point.
     *
     * @param hitPoint Impact point
     * @param cameraPos Camera position
     * @return Panel position in world
     */
    public Vec3 calculatePanelPosition(Vec3 hitPoint, Vec3 cameraPos) {
        // Direction from camera to impact point
        Vec3 toHit = Objects.requireNonNull(hitPoint.subtract(Objects.requireNonNull(cameraPos))).normalize();

        // Perpendicular vector (to the right of view)
        Vec3 right = toHit.cross(new Vec3(0, 1, 0)).normalize();

        // If right vector is zero (looking straight up/down), use default
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }

        // Position panel to the right and above impact point
        // Lateral offset moves panel to the right of view
        // Vertical offset lifts it slightly
        return Objects.requireNonNull(hitPoint
            .add(Objects.requireNonNull(right.scale(PANEL_OFFSET_SIDE)))
            .add(0, PANEL_OFFSET_UP, 0));
    }
}

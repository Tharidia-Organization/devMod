package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Manages the rendering of the Impact Analysis panel in 3D in the world.
 *
 * Instead of using an off-screen framebuffer (complex and problematic),
 * it renders the HUD content directly as 3D geometry in the world.
 *
 * This approach is simpler, more performant, and integrates better
 * with the Minecraft 1.21.1 rendering system.
 */
@SuppressWarnings("null") // Minecraft API methods are not annotated but never return null in practice
public class Impact3DRenderer {

    // Singleton instance
    public static final Impact3DRenderer INSTANCE = new Impact3DRenderer();

    // === Panel dimensions (in world units) ===
    private static final float PANEL_SCALE = 0.02f;  // Panel base scale (bigger = more readable)
    private static final float PANEL_OFFSET_SIDE = 4.5f;  // Lateral offset from impact point (to the right)
    private static final float PANEL_OFFSET_UP = 1.0f;    // Vertical offset (above the point)

    // === UI colors (reference image style) ===
    private static final int PANEL_BG = 0xDD1A1A2E;           // Dark blue 87% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue

    private static final int TEXT_TITLE = 0x00FFFF;           // Cyan (no alpha for font)
    private static final int TEXT_NORMAL = 0xFFFFFF;          // White
    private static final int TEXT_VALUE = 0x00FF00;           // Green
    private static final int TEXT_FORMULA = 0xFFD700;         // Gold
    private static final int TEXT_MUTED = 0xAAAAAA;           // Gray

    // === Internal dimensions (in font pixels, 1:1 scale with Minecraft font) ===
    private static final float PANEL_WIDTH_PX = 320f;   // Panel width in font pixels
    private static final float PANEL_HEIGHT_PX = 220f;  // Panel height in font pixels
    private static final float PADDING = 6f;
    private static final float LINE_HEIGHT = 11f;
    private static final float SECTION_SPACING = 5f;

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
        Vec3 relativePos = panelWorldPos.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

        // 3. Calculate billboard rotation (face camera)
        Vec3 toCamera = cameraPos.subtract(panelWorldPos).normalize();
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));

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
        Vec3 hitRel = hitPoint.subtract(cameraPos);
        Vec3 panelRel = panelPos.subtract(cameraPos);

        Matrix4f matrix = poseStack.last().pose();
        // Use standard RenderType.lines() instead of debugLineStrip for stability
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        // Cyan color with alpha
        float r = 0.0f;
        float g = 0.9f;
        float b = 1.0f;
        float a = alpha * 0.9f;

        // Straight stable line from hit point to panel
        // RenderType.lines() requires vertex pairs
        consumer.addVertex(matrix, (float)hitRel.x, (float)hitRel.y, (float)hitRel.z)
            .setColor(r, g, b, a)
            .setNormal(poseStack.last(), 0, 1, 0);
        consumer.addVertex(matrix, (float)panelRel.x, (float)panelRel.y, (float)panelRel.z)
            .setColor(r, g, b, a)
            .setNormal(poseStack.last(), 0, 1, 0);

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

        // === TITLE ===
        renderText3D(poseStack, bufferSource, font,
            "Impact Analysis (Multi-Part & Mod Integrated)",
            textX, textY, applyAlpha(TEXT_TITLE, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // Separator line (rendered as thin quad)
        renderSeparatorLine(poseStack, bufferSource, 4, textY, PANEL_WIDTH_PX - 12, alpha);
        textY += SECTION_SPACING;

        // === PART HIT ===
        String partText = "Part Hit: " + data.bodyPart.name() + " (Modifier: x" +
                          String.format("%.2f", data.bodyPartMultiplier) + ")";
        renderText3D(poseStack, bufferSource, font, partText,
            textX, textY, applyAlpha(TEXT_NORMAL, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // === SOURCE ===
        renderText3D(poseStack, bufferSource, font,
            "Source: " + data.getFormattedAttackSource(),
            textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
        textY += LINE_HEIGHT + SECTION_SPACING;

        // === BREAKDOWN ===
        DamageBreakdown bd = data.breakdown;

        // Base Weapon Dmg
        renderText3D(poseStack, bufferSource, font,
            String.format("Base Weapon Dmg: %.1f", bd.baseWeaponDamage),
            textX, textY, applyAlpha(TEXT_NORMAL, alpha), alpha);
        textY += LINE_HEIGHT;

        // Enchants
        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            if (eb.bonus() > 0) {
                renderText3D(poseStack, bufferSource, font,
                    String.format("Enchant (%s): +%.1f", eb.name(), eb.bonus()),
                    textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
                textY += LINE_HEIGHT;
            }
        }

        // Pehkui Size Bonus
        if (bd.pehkuiSizeBonus > 0) {
            renderText3D(poseStack, bufferSource, font,
                String.format("Pehkui Size Bonus (+25%% of Base): +%.1f", bd.pehkuiSizeBonus),
                textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
            textY += LINE_HEIGHT;
        }

        textY += SECTION_SPACING;

        // === FORMULA ===
        renderText3D(poseStack, bufferSource, font,
            "Local Part Calc: " + bd.getFormulaString(),
            textX, textY, applyAlpha(TEXT_FORMULA, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // === ACTUAL OR CALCULATED DAMAGE ===
        if (data.hasActualDamage()) {
            float actualDmg = data.getActualDamageDealt();
            renderText3D(poseStack, bufferSource, font,
                String.format("ACTUAL DAMAGE: %.1f", actualDmg),
                textX, textY, applyAlpha(0xFF4444, alpha), alpha);
            textY += LINE_HEIGHT + 2;

            String healthText = String.format("HP: %.1f -> %.1f",
                data.getHealthBefore(), data.getHealthAfter());
            renderText3D(poseStack, bufferSource, font, healthText,
                textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
            textY += LINE_HEIGHT;

            float reduction = data.getDamageReduction();
            if (Math.abs(reduction) > 0.1f) {
                String reductionText;
                int reductionColor;
                if (reduction > 0) {
                    reductionText = String.format("Armor/Effects reduced: -%.1f", reduction);
                    reductionColor = 0xFF8888;
                } else {
                    reductionText = String.format("Damage amplified: +%.1f", -reduction);
                    reductionColor = 0x88FF88;
                }
                renderText3D(poseStack, bufferSource, font, reductionText,
                    textX, textY, applyAlpha(reductionColor, alpha), alpha);
            }
        } else {
            String finalText = String.format("*Calculated Dmg: %.1f*", bd.finalDamage);
            renderText3D(poseStack, bufferSource, font, finalText,
                textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
        }

        // === MOD SPECIFICS (if present) ===
        if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
            textY += LINE_HEIGHT + SECTION_SPACING;

            renderText3D(poseStack, bufferSource, font, "Mod Specifics",
                textX, textY, applyAlpha(TEXT_TITLE, alpha), alpha);
            textY += LINE_HEIGHT + 4;

            if (data.isBetterCombatAttack()) {
                renderText3D(poseStack, bufferSource, font,
                    "Better Combat: Arc Collision Detected",
                    textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
                textY += LINE_HEIGHT;
            }

            if (data.hasPehkuiModification()) {
                String scaleText = String.format("Pehkui: Entity Size Modified (Scale %.1f)",
                    data.pehkuiVisualScale != null ? data.pehkuiVisualScale : 1.0f);
                renderText3D(poseStack, bufferSource, font, scaleText,
                    textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
            }
        }

        poseStack.popPose();
    }

    /**
     * Renders the panel background with border.
     * Uses RenderType.gui() for stable rendering without flickering.
     */
    private void renderPanelBackground(PoseStack poseStack, MultiBufferSource bufferSource, float alpha) {
        Matrix4f matrix = poseStack.last().pose();

        float w = PANEL_WIDTH_PX;
        float h = PANEL_HEIGHT_PX;

        // Main background - use debug quads which is more stable for filled areas
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());

        // Semi-transparent dark background
        int bgColor = applyAlphaARGB(PANEL_BG, alpha);
        float br = ((bgColor >> 16) & 0xFF) / 255f;
        float bgc = ((bgColor >> 8) & 0xFF) / 255f;
        float bb = (bgColor & 0xFF) / 255f;
        float ba = ((bgColor >> 24) & 0xFF) / 255f;

        // Quad for background (vertex order for correct display)
        consumer.addVertex(matrix, 0, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, w, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, w, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);

        // Border with RenderType.lines() - more stable
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        int borderColor = applyAlphaARGB(PANEL_BORDER, alpha * 0.9f);
        float bor = ((borderColor >> 16) & 0xFF) / 255f;
        float bog = ((borderColor >> 8) & 0xFF) / 255f;
        float bob = (borderColor & 0xFF) / 255f;
        float boa = ((borderColor >> 24) & 0xFF) / 255f;

        // Top line
        lineConsumer.addVertex(matrix, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        lineConsumer.addVertex(matrix, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        // Right line
        lineConsumer.addVertex(matrix, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        lineConsumer.addVertex(matrix, w, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        // Bottom line
        lineConsumer.addVertex(matrix, w, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        lineConsumer.addVertex(matrix, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        // Left line
        lineConsumer.addVertex(matrix, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        lineConsumer.addVertex(matrix, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
    }

    /**
     * Renders a horizontal separator line.
     */
    private void renderSeparatorLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                      float x, float y, float width, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        int color = applyAlphaARGB(PANEL_BORDER, alpha * 0.5f);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        consumer.addVertex(matrix, x + width, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
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
        int finalColor = (alpha << 24) | (color & 0x00FFFFFF);

        // Use SEE_THROUGH for correct 3D rendering (not blocked by depth)
        font.drawInBatch(
            text,
            0, 0,
            finalColor,
            false, // shadow
            matrix,
            bufferSource,
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
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Applies alpha to an existing ARGB color.
     */
    private int applyAlphaARGB(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
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
        Vec3 toHit = hitPoint.subtract(cameraPos).normalize();

        // Perpendicular vector (to the right of view)
        Vec3 right = toHit.cross(new Vec3(0, 1, 0)).normalize();

        // If right vector is zero (looking straight up/down), use default
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }

        // Position panel to the right and above impact point
        // Lateral offset moves panel to the right of view
        // Vertical offset lifts it slightly
        return hitPoint
            .add(right.scale(PANEL_OFFSET_SIDE))
            .add(0, PANEL_OFFSET_UP, 0);
    }
}

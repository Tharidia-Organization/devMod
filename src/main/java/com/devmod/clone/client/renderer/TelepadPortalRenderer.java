package com.devmod.clone.client.renderer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.client.rendering.CustomRenderTypes;
@OnlyIn(Dist.CLIENT)
public class TelepadPortalRenderer implements BlockEntityRenderer<TelepadBlockEntity> {
    private static final int OVAL_SEGMENTS = 48;

    // Vortex settings
    private static final int VORTEX_ARMS = 3;
    private static final float VORTEX_ROTATION_SPEED = 1.5f;

    // Ring glow settings
    private static final float RING_THICKNESS = 0.12f;
    private static final float GLOW_LAYERS = 3;

    public TelepadPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TelepadBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockState state = be.getBlockState();
        boolean active = state.getValue(TelepadBlock.ACTIVE);
        float charge = be.getChargeProgress(partialTick);

        if (!active && charge <= 0.01f) {
            charge = 0.0f;
        }

        float idleBoost = active ? 1.0f : 0.75f;
        float intensity = Mth.clamp(idleBoost * (0.45f + 0.65f * charge), 0.0f, 1.5f);

        // Register this telepad for client-side occlusion checks
        if (intensity > 0.01f) {
            TelepadDepthRenderer.registerTelepad(be.getBlockPos());
        } else {
            TelepadDepthRenderer.unregisterTelepad(be.getBlockPos());
        }

        // Get game time for animation
        float gameTime = (level.getGameTime() + partialTick) / 20.0f;

        // Calculate vortex rotation for Effekseer sync
        float vortexRotation = gameTime * VORTEX_ROTATION_SPEED;

        // Update Effekseer spiral effects (front and back of portal)
        TelepadEffekseerController.update(be, intensity, vortexRotation);

        // First: Render depth-writing base layer using RenderType (for cloud occlusion)
        renderDepthBase(be, poseStack, bufferSource, intensity);

        // Second: Render the visual portal with RenderSystem (original vortex effect)
        renderPortal(be, poseStack, gameTime, intensity);
    }

    /**
     * Render a depth-only base layer using RenderType.translucent() to help with cloud occlusion.
     * Note: This won't occlude the player in third person due to render order.
     */
    private void renderDepthBase(TelepadBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource,
                                  float intensity) {
        poseStack.pushPose();

        // Position portal above telepad center
        poseStack.translate(0.5, TelepadPortalGeometry.PORTAL_Y_OFFSET, 0.5);

        // Rotate based on facing direction
        Direction facing = be.getBlockState().getValue(TelepadBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = TelepadPortalGeometry.getHalfWidth();
        float halfHeight = TelepadPortalGeometry.getHalfHeight();

        // Render depth base using textureless POSITION_COLOR to avoid GPU-specific atlas artifacts.
        VertexConsumer consumer = bufferSource.getBuffer(CustomRenderTypes.TRANSLUCENT_POSITION_COLOR);

        // Dark base color (nearly black, fully opaque for depth writing)
        float r = 0.02f * intensity;
        float g = 0.01f * intensity;
        float b = 0.05f * intensity;
        float a = 1.0f;

        float frontZ = -0.01f;  // Front face
        float backZ = 0.01f;    // Back face

        // Render filled oval using concentric ring quads
        int numRings = 8;
        for (int ring = 0; ring < numRings; ring++) {
            float innerRadius = (float) ring / numRings;
            float outerRadius = (float) (ring + 1) / numRings;

            for (int i = 0; i < OVAL_SEGMENTS; i++) {
                float angle1 = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
                float angle2 = (float) ((i + 1) * 2.0 * Math.PI / OVAL_SEGMENTS);

                // Inner ring vertices
                float ix1 = (float) Math.sin(angle1) * halfWidth * innerRadius;
                float iy1 = (float) Math.cos(angle1) * halfHeight * innerRadius + halfHeight;
                float ix2 = (float) Math.sin(angle2) * halfWidth * innerRadius;
                float iy2 = (float) Math.cos(angle2) * halfHeight * innerRadius + halfHeight;

                // Outer ring vertices
                float ox1 = (float) Math.sin(angle1) * halfWidth * outerRadius;
                float oy1 = (float) Math.cos(angle1) * halfHeight * outerRadius + halfHeight;
                float ox2 = (float) Math.sin(angle2) * halfWidth * outerRadius;
                float oy2 = (float) Math.cos(angle2) * halfHeight * outerRadius + halfHeight;

                // Front face quad
                consumer.addVertex(matrix, ix1, iy1, frontZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ox1, oy1, frontZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ox2, oy2, frontZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ix2, iy2, frontZ)
                        .setColor(r, g, b, a);

                // Back face quad
                consumer.addVertex(matrix, ix1, iy1, backZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ix2, iy2, backZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ox2, oy2, backZ)
                        .setColor(r, g, b, a);

                consumer.addVertex(matrix, ox1, oy1, backZ)
                        .setColor(r, g, b, a);
            }
        }

        poseStack.popPose();
    }

    /**
     * Render the visual vortex portal using RenderSystem (original effect).
     */
    private void renderPortal(TelepadBlockEntity be, PoseStack poseStack, float gameTime, float intensity) {
        poseStack.pushPose();

        // Position portal above telepad center
        poseStack.translate(0.5, TelepadPortalGeometry.PORTAL_Y_OFFSET, 0.5);

        // Rotate based on facing direction
        Direction facing = be.getBlockState().getValue(TelepadBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        Matrix4f matrix = poseStack.last().pose();
        float effectiveIntensity = Math.max(intensity, 0.5f);
        float halfWidth = TelepadPortalGeometry.getHalfWidth();
        float halfHeight = TelepadPortalGeometry.getHalfHeight();

        // Setup render state
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Layer 1: Vortex center (darker, swirling)
        RenderSystem.defaultBlendFunc();
        renderVortexCenter(matrix, halfWidth, halfHeight, gameTime, effectiveIntensity);

        // Layer 2: Energy vortex effect (follows spiral, extends beyond oval)
        RenderSystem.blendFunc(
            com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
            com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE
        );
        renderEnergyVortex(matrix, halfWidth, halfHeight, gameTime, effectiveIntensity);

        // Layer 3: Radial energy ring (bright cyan glow)
        renderEnergyRing(matrix, halfWidth, halfHeight, gameTime, effectiveIntensity);

        // Restore render state
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /**
     * Render the swirling vortex center with spiral pattern
     */
    private void renderVortexCenter(Matrix4f matrix, float halfWidth, float halfHeight,
                                     float gameTime, float intensity) {
        Tesselator tesselator = Tesselator.getInstance();

        // Base vortex fill (lighter blue/purple with transparency)
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // Center is lighter purple/blue (more visible)
        float centerR = 0.25f * intensity;
        float centerG = 0.2f * intensity;
        float centerB = 0.6f * intensity;
        float centerA = 0.8f;

        // Edge is lighter cyan/blue
        float edgeR = 0.35f * intensity;
        float edgeG = 0.7f * intensity;
        float edgeB = 1.0f * intensity;
        float edgeA = 0.6f;

        // Center point
        buffer.addVertex(matrix, 0, halfHeight, 0.001f).setColor(centerR, centerG, centerB, centerA);

        // Edge points with gradient
        for (int i = 0; i <= OVAL_SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
            float x = (float) Math.sin(angle) * halfWidth * 0.88f;
            float y = (float) Math.cos(angle) * halfHeight * 0.88f + halfHeight;
            buffer.addVertex(matrix, x, y, 0.001f).setColor(edgeR, edgeG, edgeB, edgeA);
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // Spiral arms
        float rotation = gameTime * VORTEX_ROTATION_SPEED;
        renderSpiralArms(matrix, halfWidth * 0.95f, halfHeight * 0.95f, rotation, intensity);
    }

    /**
     * Render spiral arms that create the vortex swirl effect
     */
    private void renderSpiralArms(Matrix4f matrix, float halfWidth, float halfHeight,
                                   float rotation, float intensity) {
        Tesselator tesselator = Tesselator.getInstance();

        for (int arm = 0; arm < VORTEX_ARMS; arm++) {
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            float armOffset = (float) (arm * 2.0 * Math.PI / VORTEX_ARMS);
            int spiralSegments = 28;
            float armWidth = 0.12f;

            for (int i = 0; i <= spiralSegments; i++) {
                float t = (float) i / spiralSegments;
                float spiralAngle = rotation + armOffset + t * (float) Math.PI * 2.5f;
                float radius = t;

                // Calculate position on spiral
                float x = (float) Math.sin(spiralAngle) * radius * halfWidth;
                float y = (float) Math.cos(spiralAngle) * radius * halfHeight + halfHeight;

                // Perpendicular direction for arm width
                float perpX = (float) Math.cos(spiralAngle) * armWidth * (1.0f - t * 0.4f);
                float perpY = (float) -Math.sin(spiralAngle) * armWidth * (1.0f - t * 0.4f);

                // Color - brighter, fades from bright cyan to light blue
                float alpha = (1.0f - t * 0.7f) * 0.8f * intensity;
                float r = 0.5f + t * 0.3f;
                float g = 0.85f + t * 0.1f;
                float b = 1.0f;

                buffer.addVertex(matrix, x - perpX, y - perpY * (halfHeight / halfWidth), 0.002f)
                      .setColor(r * intensity, g * intensity, b, alpha);
                buffer.addVertex(matrix, x + perpX, y + perpY * (halfHeight / halfWidth), 0.002f)
                      .setColor(r * intensity, g * intensity, b, alpha);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }
    }

    /**
     * Render energy vortex effect that follows the spiral but extends beyond the oval
     */
    private void renderEnergyVortex(Matrix4f matrix, float halfWidth, float halfHeight,
                                     float gameTime, float intensity) {
        Tesselator tesselator = Tesselator.getInstance();
        float rotation = gameTime * VORTEX_ROTATION_SPEED;

        int numTrails = 12;
        float extendScale = 1.25f;

        for (int trail = 0; trail < numTrails; trail++) {
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            float trailOffset = (float) (trail * 2.0 * Math.PI / numTrails);
            int segments = 20;
            float trailWidth = 0.06f;

            float irregularity = (float) Math.sin(trail * 1.7f + gameTime * 2.0f) * 0.15f;
            float speedVariation = 1.0f + (float) Math.sin(trail * 2.3f) * 0.3f;

            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float spiralAngle = rotation * speedVariation + trailOffset + t * (float) Math.PI * 2.0f;

                float baseRadius = t * extendScale;
                float radiusWave = (float) Math.sin(spiralAngle * 3.0f + gameTime * 4.0f) * 0.08f * t;
                float radius = baseRadius + radiusWave + irregularity * t;

                float x = (float) Math.sin(spiralAngle) * radius * halfWidth;
                float y = (float) Math.cos(spiralAngle) * radius * halfHeight + halfHeight;

                float widthFactor = (1.0f - t * 0.6f) * trailWidth;
                float perpX = (float) Math.cos(spiralAngle) * widthFactor;
                float perpY = (float) -Math.sin(spiralAngle) * widthFactor;

                float edgeFade = t > 0.8f ? (1.0f - (t - 0.8f) * 3.0f) : 1.0f;
                float alpha = (1.0f - t * 0.5f) * 0.6f * intensity * Math.max(edgeFade, 0.2f);

                float flicker = 0.7f + 0.3f * (float) Math.sin(gameTime * 8.0f + trail * 0.5f + t * 4.0f);

                float r = 0.3f * flicker;
                float g = 0.95f * flicker;
                float b = 1.0f;

                buffer.addVertex(matrix, x - perpX, y - perpY * (halfHeight / halfWidth), 0.003f)
                      .setColor(r * intensity, g * intensity, b, alpha);
                buffer.addVertex(matrix, x + perpX, y + perpY * (halfHeight / halfWidth), 0.003f)
                      .setColor(r * intensity, g * intensity, b, alpha);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }

        renderEnergySparks(matrix, halfWidth, halfHeight, rotation, intensity, extendScale);
    }

    /**
     * Render small energy sparks that follow the vortex pattern
     */
    private void renderEnergySparks(Matrix4f matrix, float halfWidth, float halfHeight,
                                     float rotation, float intensity, float extendScale) {
        Tesselator tesselator = Tesselator.getInstance();

        int numSparks = 24;
        float sparkSize = 0.04f;

        for (int spark = 0; spark < numSparks; spark++) {
            float sparkPhase = (float) (spark * 2.0 * Math.PI / numSparks);
            float sparkRadius = 0.3f + (spark % 8) * 0.12f;

            float sparkAngle = rotation * 1.2f + sparkPhase + (float) Math.sin(spark * 0.7f) * 0.5f;

            float jitterX = (float) Math.sin(rotation * 3.0f + spark * 1.3f) * 0.05f;
            float jitterY = (float) Math.cos(rotation * 2.5f + spark * 1.7f) * 0.05f;

            float x = (float) Math.sin(sparkAngle) * sparkRadius * halfWidth * extendScale + jitterX;
            float y = (float) Math.cos(sparkAngle) * sparkRadius * halfHeight * extendScale + halfHeight + jitterY;

            float pulse = 0.5f + 0.5f * (float) Math.sin(rotation * 5.0f + spark * 0.8f);
            float alpha = pulse * 0.8f * intensity;

            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

            float r = 0.5f * pulse;
            float g = 1.0f;
            float b = 1.0f;

            buffer.addVertex(matrix, x, y, 0.004f).setColor(r * intensity, g * intensity, b, alpha);

            int sparkSegments = 8;
            for (int i = 0; i <= sparkSegments; i++) {
                float angle = (float) (i * 2.0 * Math.PI / sparkSegments);
                float sx = x + (float) Math.cos(angle) * sparkSize;
                float sy = y + (float) Math.sin(angle) * sparkSize * (halfHeight / halfWidth);
                buffer.addVertex(matrix, sx, sy, 0.004f).setColor(r * intensity * 0.5f, g * intensity * 0.5f, b, alpha * 0.3f);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }
    }

    /**
     * Render the glowing energy ring around the portal edge
     */
    private void renderEnergyRing(Matrix4f matrix, float halfWidth, float halfHeight,
                                   float gameTime, float intensity) {
        Tesselator tesselator = Tesselator.getInstance();

        // Multiple glow layers for soft edge
        for (int layer = 0; layer < GLOW_LAYERS; layer++) {
            float layerScale = 1.0f + layer * 0.03f;
            float layerAlpha = (1.0f - layer / GLOW_LAYERS) * 0.5f * intensity;
            float innerScale = 1.0f - RING_THICKNESS - layer * 0.02f;

            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            float pulse = 0.8f + 0.2f * (float) Math.sin(gameTime * 3.0f);

            for (int i = 0; i <= OVAL_SEGMENTS; i++) {
                float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);

                float ox = (float) Math.sin(angle) * halfWidth * layerScale;
                float oy = (float) Math.cos(angle) * halfHeight * layerScale + halfHeight;

                float ix = (float) Math.sin(angle) * halfWidth * innerScale;
                float iy = (float) Math.cos(angle) * halfHeight * innerScale + halfHeight;

                float r = 0.2f * pulse;
                float g = 0.9f * pulse;
                float b = 1.0f;

                buffer.addVertex(matrix, ox, oy, 0).setColor(r, g, b, layerAlpha * 0.3f);
                buffer.addVertex(matrix, ix, iy, 0).setColor(r, g, b, layerAlpha);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                BufferUploader.drawWithShader(meshData);
            }
        }

        // Core bright ring
        BufferBuilder coreBuffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float coreInner = 1.0f - RING_THICKNESS * 0.5f;
        float coreOuter = 1.0f - RING_THICKNESS * 0.2f;

        for (int i = 0; i <= OVAL_SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);

            float ox = (float) Math.sin(angle) * halfWidth * coreOuter;
            float oy = (float) Math.cos(angle) * halfHeight * coreOuter + halfHeight;
            float ix = (float) Math.sin(angle) * halfWidth * coreInner;
            float iy = (float) Math.cos(angle) * halfHeight * coreInner + halfHeight;

            float r = 0.4f * intensity;
            float g = 1.0f * intensity;
            float b = 1.0f;
            float a = 0.9f * intensity;

            coreBuffer.addVertex(matrix, ox, oy, 0).setColor(r, g, b, a * 0.7f);
            coreBuffer.addVertex(matrix, ix, iy, 0).setColor(r, g, b, a);
        }

        MeshData coreMesh = coreBuffer.build();
        if (coreMesh != null) {
            BufferUploader.drawWithShader(coreMesh);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TelepadBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}

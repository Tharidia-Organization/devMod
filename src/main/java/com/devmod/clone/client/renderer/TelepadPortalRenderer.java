package com.devmod.clone.client.renderer;

import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
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
import com.mojang.math.Axis;

@OnlyIn(Dist.CLIENT)
public class TelepadPortalRenderer implements BlockEntityRenderer<TelepadBlockEntity> {
    private static final float CENTER_Y = 1.4f;
    private static final float LAYER_DEPTH = 0.01f;

    private static final float BASE_RADIUS = 0.86f;
    private static final float RING_RADIUS = 0.98f;
    private static final float SPARK_RADIUS = 1.05f;

    private static final float LAYER_BASE = 0.0f;
    private static final float LAYER_RING = 0.5f;
    private static final float LAYER_SPARK = 1.0f;

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

        if (!active && charge <= 0.01f && be.getTelepadName().isEmpty()) {
            return;
        }

        float idleBoost = active ? 1.0f : 0.35f;
        float intensity = Mth.clamp(idleBoost * (0.35f + 0.65f * charge), 0.0f, 1.2f);
        if (intensity <= 0.02f) {
            return;
        }

        ShaderInstance shader = TelepadPortalShaderRegistry.getShader();
        if (shader != null) {
            float time = (level.getGameTime() + partialTick) / 20.0f;
            float chargeUniform = active ? charge : 0.15f;

            shader.safeGetUniform("GameTime").set(time);
            shader.safeGetUniform("Charge").set(chargeUniform);
            shader.safeGetUniform("Intensity").set(intensity);
            shader.safeGetUniform("Aspect").set(0.85f, 1.25f);
            shader.safeGetUniform("PulseSpeed").set(1.0f);
            shader.safeGetUniform("ColorPrimary").set(0.12f, 0.85f, 1.0f);
            shader.safeGetUniform("ColorSecondary").set(0.02f, 0.20f, 0.55f);
            shader.safeGetUniform("ColorAccent").set(0.72f, 0.98f, 1.0f);
            shader.safeGetUniform("ColorSpark").set(1.0f, 0.72f, 0.18f);
        }

        float time = (level.getGameTime() + partialTick) / 20.0f;
        float bob = Mth.sin(time * 0.6f) * 0.02f;
        float pulse = 1.0f + (0.02f + charge * 0.05f) * Mth.sin(time * 2.2f);
        Direction facing = state.getValue(TelepadBlock.FACING);
        float facingRotation = getFacingRotation(facing);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.translate(0.0, CENTER_Y + bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation));

        RenderType baseType = TelepadPortalShaderRegistry.getBaseRenderType();
        RenderType glowType = TelepadPortalShaderRegistry.getGlowRenderType();

        if (baseType != null) {
            float baseAlpha = active ? (0.32f + 0.35f * charge) : 0.12f;
            renderLayer(poseStack, bufferSource, baseType, BASE_RADIUS * pulse,
                time * 0.2f, baseAlpha, LAYER_BASE, 0.0f);
        }

        if (glowType != null) {
            float ringAlpha = active ? (0.35f + 0.55f * charge) : 0.18f;
            renderLayer(poseStack, bufferSource, glowType, RING_RADIUS * pulse,
                -time * 0.3f, ringAlpha, LAYER_RING, LAYER_DEPTH);
        }

        if (glowType != null && active) {
            float sparkAlpha = 0.15f + 0.65f * charge;
            renderLayer(poseStack, bufferSource, glowType, SPARK_RADIUS * pulse,
                time * 0.45f, sparkAlpha, LAYER_SPARK, LAYER_DEPTH * 2.0f);
        }

        poseStack.popPose();
    }

    private static void renderLayer(PoseStack poseStack, MultiBufferSource bufferSource,
                                    RenderType type, float radius, float rotation,
                                    float alpha, float layerId, float depthOffset) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(rotation));
        poseStack.translate(0.0, 0.0, depthOffset);
        poseStack.scale(radius, radius, 1.0f);

        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(type));
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;

        consumer.addVertex(matrix, -1.0f, -1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);
        consumer.addVertex(matrix,  1.0f, -1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);
        consumer.addVertex(matrix,  1.0f,  1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);

        consumer.addVertex(matrix, -1.0f, -1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);
        consumer.addVertex(matrix,  1.0f,  1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);
        consumer.addVertex(matrix, -1.0f,  1.0f, 0.0f).setColor(r, g, b, alpha).setNormal(layerId, 0.0f, 0.0f);

        poseStack.popPose();
    }

    private static float getFacingRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}

package com.devmod.clone.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

import com.devmod.DevMod;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;

/**
 * Renders depth-only ovals for telepads at AFTER_CUTOUT_BLOCKS stage.
 * This renders AFTER terrain but BEFORE entities, allowing proper player occlusion in third person.
 * Uses colorMask to disable color writes and only write to depth buffer.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class TelepadDepthRenderHandler {
    // Portal dimensions (must match TelepadPortalRenderer)
    private static final float PORTAL_WIDTH = 2.457f;
    private static final float PORTAL_HEIGHT = 3.51f;
    private static final float PORTAL_Y_OFFSET = 1.0f;
    private static final int OVAL_SEGMENTS = 32;

    private TelepadDepthRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Render at AFTER_CUTOUT_BLOCKS - right before entities
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }

        // Check if there are any active telepads to render
        if (TelepadDepthRenderer.getActiveTelepadPositions().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        // Setup render state for depth-only rendering
        // Disable color writes - we ONLY want to write to depth buffer
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend(); // No blending needed for depth-only
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Iterate through active telepads
        for (BlockPos pos : TelepadDepthRenderer.getActiveTelepadPositions()) {
            if (!(level.getBlockEntity(pos) instanceof TelepadBlockEntity be)) {
                continue;
            }

            BlockState state = be.getBlockState();
            if (!state.hasProperty(TelepadBlock.ACTIVE)) {
                continue;
            }

            boolean active = state.getValue(TelepadBlock.ACTIVE);
            float charge = be.getChargeProgress(event.getPartialTick().getGameTimeDeltaPartialTick(false));

            if (!active && charge <= 0.01f) {
                continue;
            }

            // Get facing direction
            Direction facing = state.getValue(TelepadBlock.FACING);

            // Render depth oval for this telepad
            renderDepthOval(poseStack, pos, facing, cameraPos);
        }

        // Restore render state
        RenderSystem.colorMask(true, true, true, true); // Re-enable color writes
        RenderSystem.enableCull();
    }

    private static void renderDepthOval(PoseStack poseStack, BlockPos pos, Direction facing, Vec3 cameraPos) {
        poseStack.pushPose();

        // Translate to telepad position (relative to camera)
        double x = pos.getX() + 0.5 - cameraPos.x;
        double y = pos.getY() + PORTAL_Y_OFFSET - cameraPos.y;
        double z = pos.getZ() + 0.5 - cameraPos.z;
        poseStack.translate(x, y, z);

        // Rotate based on facing direction
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = PORTAL_WIDTH / 2.0f;
        float halfHeight = PORTAL_HEIGHT / 2.0f;

        // Full alpha to ensure depth write (color writes are disabled, so color doesn't matter)
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 1.0f; // Full alpha to ensure shader doesn't discard fragment

        Tesselator tesselator = Tesselator.getInstance();

        // Front face
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, 0, halfHeight, -0.005f).setColor(r, g, b, a);
        for (int i = 0; i <= OVAL_SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
            float px = (float) Math.sin(angle) * halfWidth;
            float py = (float) Math.cos(angle) * halfHeight + halfHeight;
            buffer.addVertex(matrix, px, py, -0.005f).setColor(r, g, b, a);
        }
        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // Back face
        buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, 0, halfHeight, 0.005f).setColor(r, g, b, a);
        for (int i = OVAL_SEGMENTS; i >= 0; i--) {
            float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
            float px = (float) Math.sin(angle) * halfWidth;
            float py = (float) Math.cos(angle) * halfHeight + halfHeight;
            buffer.addVertex(matrix, px, py, 0.005f).setColor(r, g, b, a);
        }
        meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        poseStack.popPose();
    }
}

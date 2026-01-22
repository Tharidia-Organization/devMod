package com.devmod.clone.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * Renders depth-only ovals for telepads at AFTER_SOLID_BLOCKS stage.
 * This renders AFTER solid terrain but BEFORE entities, allowing proper player occlusion in third person.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class TelepadDepthRenderHandler {
    // Portal dimensions (must match TelepadPortalRenderer)
    private static final float PORTAL_WIDTH = 2.457f;
    private static final float PORTAL_HEIGHT = 3.51f;
    private static final float PORTAL_Y_OFFSET = 1.0f;
    private static final int OVAL_SEGMENTS = 32;

    // DEBUG: Set to true to see the depth layer as visible red oval
    private static final boolean DEBUG_VISIBLE = true;

    private static boolean loggedOnce = false;
    private static int frameCount = 0;

    private TelepadDepthRenderHandler() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // TEST: Use AFTER_ENTITIES to verify rendering works (same timing as WorldRenderEvents)
        // Once we see red lines, we can move to earlier stage for actual occlusion
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        frameCount++;

        // Log every 60 frames to see if handler is being called
        if (frameCount % 60 == 1) {
            DevMod.LOGGER.info("[TelepadDepthRenderHandler] Frame {}, active telepads: {}",
                frameCount, TelepadDepthRenderer.getActiveTelepadPositions().size());
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

        if (!loggedOnce) {
            DevMod.LOGGER.info("[TelepadDepthRenderHandler] RENDERING {} telepads (DEBUG_VISIBLE={})",
                TelepadDepthRenderer.getActiveTelepadPositions().size(), DEBUG_VISIBLE);
            loggedOnce = true;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        // Get buffer source from Minecraft (same as WorldRenderEvents)
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Setup render state for depth writing
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        if (!DEBUG_VISIBLE) {
            RenderSystem.colorMask(false, false, false, false);
        }

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

            // Render depth oval for this telepad using lines (visible for debug)
            renderDepthOvalWithLines(poseStack, bufferSource, pos, facing, cameraPos);
        }

        // Flush the buffer to ensure rendering happens
        bufferSource.endBatch();

        // Restore render state
        if (!DEBUG_VISIBLE) {
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    /**
     * Render the oval outline using lines (like WorldRenderEvents does).
     * This uses the proven rendering approach that works.
     */
    private static void renderDepthOvalWithLines(PoseStack poseStack, MultiBufferSource bufferSource,
            BlockPos pos, Direction facing, Vec3 cameraPos) {

        // Get line buffer (this is proven to work in WorldRenderEvents)
        VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();

        // Translate relative to camera (same approach as WorldRenderEvents)
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Now translate to the telepad position (absolute world coordinates)
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + PORTAL_Y_OFFSET;
        double centerZ = pos.getZ() + 0.5;
        poseStack.translate(centerX, centerY, centerZ);

        // Rotate based on facing direction
        float yRotRad = (float) Math.toRadians(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        });

        Matrix4f matrix = poseStack.last().pose();
        float halfWidth = PORTAL_WIDTH / 2.0f;
        float halfHeight = PORTAL_HEIGHT / 2.0f;

        // Debug: red color to see the depth layer position
        float r = DEBUG_VISIBLE ? 1.0f : 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 1.0f;

        // Draw oval outline using line segments
        float cosY = (float) Math.cos(yRotRad);
        float sinY = (float) Math.sin(yRotRad);

        for (int i = 0; i < OVAL_SEGMENTS; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / OVAL_SEGMENTS);

            // Local oval coordinates
            float lx1 = (float) Math.sin(angle1) * halfWidth;
            float ly1 = (float) Math.cos(angle1) * halfHeight + halfHeight;
            float lx2 = (float) Math.sin(angle2) * halfWidth;
            float ly2 = (float) Math.cos(angle2) * halfHeight + halfHeight;

            // Rotate around Y axis based on facing
            float x1 = lx1 * cosY;
            float z1 = lx1 * sinY;
            float x2 = lx2 * cosY;
            float z2 = lx2 * sinY;

            // Add line segment
            builder.addVertex(matrix, x1, ly1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
            builder.addVertex(matrix, x2, ly2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
        }

        // Also draw some cross lines to make it more visible
        // Horizontal line through center
        float cx = 0, cy = halfHeight;
        builder.addVertex(matrix, -halfWidth * cosY, cy, -halfWidth * sinY).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, halfWidth * cosY, cy, halfWidth * sinY).setColor(r, g, b, a).setNormal(0, 1, 0);

        // Vertical line through center
        builder.addVertex(matrix, 0, 0, 0).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, 0, PORTAL_HEIGHT, 0).setColor(r, g, b, a).setNormal(0, 1, 0);

        poseStack.popPose();
    }
}

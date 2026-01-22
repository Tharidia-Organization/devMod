package com.devmod.clone.client.renderer;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Matrix4f;

import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;

/**
 * Renders depth-only ovals for telepads BEFORE entities are rendered.
 * This allows the portal to properly occlude the player in third person.
 */
@OnlyIn(Dist.CLIENT)
public final class TelepadDepthRenderer {
    // Portal dimensions (must match TelepadPortalRenderer)
    private static final float PORTAL_WIDTH = 2.457f;
    private static final float PORTAL_HEIGHT = 3.51f;
    private static final float PORTAL_Y_OFFSET = 1.0f;
    private static final int OVAL_SEGMENTS = 32;

    // Track active telepads that need depth rendering
    private static final Set<BlockPos> activeTelepadPositions = new HashSet<>();

    private TelepadDepthRenderer() {
    }

    /**
     * Register a telepad position for depth rendering.
     * Called from TelepadBlockEntity when it starts rendering.
     */
    public static void registerTelepad(BlockPos pos) {
        activeTelepadPositions.add(pos.immutable());
    }

    /**
     * Unregister a telepad position.
     * Called when telepad is removed or stops being active.
     */
    public static void unregisterTelepad(BlockPos pos) {
        activeTelepadPositions.remove(pos);
    }

    /**
     * Clear all registered telepads (called on world unload).
     */
    public static void clearAll() {
        activeTelepadPositions.clear();
    }

    /**
     * Get the set of active telepad positions.
     * Used by RenderLevelStageEvent handler.
     */
    public static Set<BlockPos> getActiveTelepadPositions() {
        return activeTelepadPositions;
    }

    /**
     * Render depth-only ovals for all active telepads.
     * Called from mixin BEFORE entity rendering.
     */
    public static void renderDepthLayers(PoseStack poseStack, Camera camera, Matrix4f projectionMatrix) {
        if (activeTelepadPositions.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();

        // Setup render state for depth-only rendering
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Iterate through active telepads
        for (BlockPos pos : activeTelepadPositions) {
            if (!(level.getBlockEntity(pos) instanceof TelepadBlockEntity be)) {
                continue;
            }

            BlockState state = be.getBlockState();
            if (!state.hasProperty(TelepadBlock.ACTIVE)) {
                continue;
            }

            boolean active = state.getValue(TelepadBlock.ACTIVE);
            float charge = be.getChargeProgress(mc.getTimer().getGameTimeDeltaPartialTick(false));

            if (!active && charge <= 0.01f) {
                continue;
            }

            // Get facing direction
            Direction facing = state.getValue(TelepadBlock.FACING);

            // Render depth oval for this telepad
            renderDepthOval(poseStack, pos, facing, cameraPos);
        }

        // Restore render state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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

        // Very dark color (nearly invisible but writes to depth buffer)
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 0.01f; // Very low alpha to be nearly invisible

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

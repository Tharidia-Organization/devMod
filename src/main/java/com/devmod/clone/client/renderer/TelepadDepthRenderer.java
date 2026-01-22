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

        Vec3 cameraPos = camera.getPosition();
        renderDepthLayersBeforeEntities(poseStack, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    /**
     * Render depth-only ovals using the view and projection matrices from LevelRenderer.
     * Uses direct OpenGL-style rendering for maximum compatibility.
     */
    public static void renderDepthLayersWithMatrix(Matrix4f viewMatrix, Matrix4f projectionMatrix, double camX, double camY, double camZ) {
        // DISABLED: Now using TelepadDepthRenderHandler with RenderLevelStageEvent instead
        // This method was called from mixin at RETURN (after everything) which doesn't help with occlusion
        if (true) return;

        if (activeTelepadPositions.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        // Only log once per session to avoid spam
        if (!loggedMatrixRender) {
            com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] renderDepthLayersWithMatrix called with {} telepads", activeTelepadPositions.size());
            loggedMatrixRender = true;
        }

        // DEBUG: Use visible red color
        boolean debugVisible = true;

        // TEST: Render a simple 2D overlay to verify rendering works at all
        renderDebugOverlay();

        // Backup current GL state
        boolean wasDepthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean wasCullFace = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean wasBlend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);

        // Setup render state
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();

        if (!debugVisible) {
            RenderSystem.colorMask(false, false, false, false);
        }

        // Create combined MVP matrix
        Matrix4f mvp = new Matrix4f(projectionMatrix);
        mvp.mul(viewMatrix);

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

            Direction facing = state.getValue(TelepadBlock.FACING);

            // Render using the combined MVP matrix applied per-vertex
            renderDepthOvalWithMVP(mvp, pos, facing, camX, camY, camZ, debugVisible);
        }

        // Restore render state
        if (!debugVisible) {
            RenderSystem.colorMask(true, true, true, true);
        }

        if (wasCullFace) RenderSystem.enableCull();
        else RenderSystem.disableCull();

        if (wasBlend) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();

        if (!wasDepthTest) RenderSystem.disableDepthTest();
    }

    private static boolean loggedMatrixRender = false;

    /**
     * Render a simple red rectangle in the corner of the screen to verify rendering works.
     */
    private static void renderDebugOverlay() {
        // Save current matrices
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();

        // Create orthographic projection for 2D overlay
        Minecraft mc = Minecraft.getInstance();
        Matrix4f ortho = new Matrix4f().ortho(0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0, -1000, 1000);
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Red square in top-left corner (10x10 at position 10,10)
        float x = 10, y = 10, size = 20;
        buffer.addVertex(x, y, 0).setColor(1f, 0f, 0f, 1f);
        buffer.addVertex(x, y + size, 0).setColor(1f, 0f, 0f, 1f);
        buffer.addVertex(x + size, y + size, 0).setColor(1f, 0f, 0f, 1f);
        buffer.addVertex(x + size, y, 0).setColor(1f, 0f, 0f, 1f);

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        // Restore matrices
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static boolean loggedProjection = false;

    /**
     * Render depth oval by projecting 3D coordinates to screen space.
     */
    private static void renderDepthOvalWithMVP(Matrix4f mvp, BlockPos pos, Direction facing, double camX, double camY, double camZ, boolean debugVisible) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Position relative to camera
        float baseX = (float)(pos.getX() + 0.5 - camX);
        float baseY = (float)(pos.getY() + PORTAL_Y_OFFSET - camY);
        float baseZ = (float)(pos.getZ() + 0.5 - camZ);

        // Log once to see what values we're getting
        if (!loggedProjection) {
            com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] Projecting oval at camera-rel pos: ({}, {}, {})",
                String.format("%.2f", baseX), String.format("%.2f", baseY), String.format("%.2f", baseZ));
        }

        float yRotRad = (float) Math.toRadians(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        });
        float cosY = (float) Math.cos(yRotRad);
        float sinY = (float) Math.sin(yRotRad);

        float halfWidth = PORTAL_WIDTH / 2.0f;
        float halfHeight = PORTAL_HEIGHT / 2.0f;

        float r = debugVisible ? 1.0f : 0.0f;
        float g = debugVisible ? 0.0f : 0.0f;
        float b = debugVisible ? 0.0f : 0.0f;
        float a = 1.0f;

        // Setup 2D rendering (like the debug overlay that works)
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();

        Matrix4f ortho = new Matrix4f().ortho(0, screenWidth, screenHeight, 0, -1000, 1000);
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest(); // Disable depth test for 2D overlay

        // Project center point first to check if it works
        float centerWorldX = baseX;
        float centerWorldY = baseY + halfHeight;
        float centerWorldZ = baseZ;
        org.joml.Vector4f centerClip = new org.joml.Vector4f(centerWorldX, centerWorldY, centerWorldZ, 1.0f);
        mvp.transform(centerClip);

        if (!loggedProjection) {
            com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] Center clip coords: ({}, {}, {}, {})",
                String.format("%.2f", centerClip.x), String.format("%.2f", centerClip.y),
                String.format("%.2f", centerClip.z), String.format("%.2f", centerClip.w));
        }

        // Skip if behind camera
        if (centerClip.w <= 0.01f) {
            if (!loggedProjection) {
                com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] Skipped - behind camera (w={})", centerClip.w);
                loggedProjection = true;
            }
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            return;
        }

        float centerScreenX = (centerClip.x / centerClip.w + 1.0f) * 0.5f * screenWidth;
        float centerScreenY = (1.0f - centerClip.y / centerClip.w) * 0.5f * screenHeight;

        if (!loggedProjection) {
            com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] Center screen coords: ({}, {}) - screen size: {}x{}",
                String.format("%.1f", centerScreenX), String.format("%.1f", centerScreenY), screenWidth, screenHeight);
            loggedProjection = true;
        }

        // Project all oval vertices
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // Add center vertex
        buffer.addVertex(centerScreenX, centerScreenY, 0).setColor(r, g, b, a);

        // Add edge vertices
        for (int i = 0; i <= OVAL_SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / OVAL_SEGMENTS);
            float localX = (float) Math.sin(angle) * halfWidth;
            float localY = (float) Math.cos(angle) * halfHeight + halfHeight;

            // World position (camera-relative)
            float worldX = baseX + localX * cosY;
            float worldY = baseY + localY;
            float worldZ = baseZ + localX * sinY;

            // Project to clip space
            org.joml.Vector4f clipPos = new org.joml.Vector4f(worldX, worldY, worldZ, 1.0f);
            mvp.transform(clipPos);

            // Skip vertices behind camera
            if (clipPos.w <= 0.01f) {
                continue;
            }

            // Convert to screen coordinates
            float screenX = (clipPos.x / clipPos.w + 1.0f) * 0.5f * screenWidth;
            float screenY = (1.0f - clipPos.y / clipPos.w) * 0.5f * screenHeight;

            buffer.addVertex(screenX, screenY, 0).setColor(r, g, b, a);
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    @SuppressWarnings("unused")
    private static boolean loggedRenderOnce = false;

    /**
     * Render depth-only ovals for all active telepads.
     * Called from TelepadEntityRenderMixin BEFORE the first entity is rendered.
     * This is the key method for player occlusion in third person.
     */
    public static void renderDepthLayersBeforeEntities(PoseStack poseStack, double camX, double camY, double camZ) {
        if (!loggedRenderOnce) {
            com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] renderDepthLayersBeforeEntities called! positions={}", activeTelepadPositions.size());
            loggedRenderOnce = true;
        }

        if (activeTelepadPositions.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPos = new Vec3(camX, camY, camZ);

        com.devmod.DevMod.LOGGER.info("[TelepadDepthRenderer] Rendering {} telepads at camera ({}, {}, {})",
            activeTelepadPositions.size(), camX, camY, camZ);

        // DEBUG: Use visible red color to see where the depth layer is being rendered
        // Once working, change back to colorMask approach
        boolean debugVisible = true;

        // Setup render state for depth rendering
        if (!debugVisible) {
            RenderSystem.colorMask(false, false, false, false);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
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
            renderDepthOval(poseStack, pos, facing, cameraPos, debugVisible);
        }

        // Restore render state
        if (!debugVisible) {
            RenderSystem.colorMask(true, true, true, true);
        }
        RenderSystem.enableCull();
    }

    private static void renderDepthOval(PoseStack poseStack, BlockPos pos, Direction facing, Vec3 cameraPos, boolean debugVisible) {
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

        // Debug: use red color to see where it's rendering
        // Normal: use black with full alpha
        float r = debugVisible ? 1.0f : 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 1.0f;

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

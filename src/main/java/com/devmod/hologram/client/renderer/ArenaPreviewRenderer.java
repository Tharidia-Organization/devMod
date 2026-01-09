package com.devmod.hologram.client.renderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Standalone renderer for arena preview meshes.
 * Can be used in GUI screens to display a rotating 3D preview of an arena template.
 *
 * <p>Usage:
 * <pre>
 * ArenaPreviewRenderer renderer = new ArenaPreviewRenderer();
 * renderer.setTemplate(template);
 * renderer.render(guiGraphics, x, y, width, height, mouseX, mouseY);
 * renderer.cleanup();
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class ArenaPreviewRenderer {

    @Nullable
    private ArenaPreviewMesh mesh;
    @Nullable
    private VertexBuffer vertexBuffer;
    @Nullable
    private String currentTemplateId;
    private int vertexCount;

    private float rotationAngle = 0.0f;
    private boolean autoRotate = true;
    private float scale = 1.0f;

    /**
     * Set the template to preview.
     * Rebuilds the mesh and VBO if template changed.
     */
    public void setTemplate(@Nullable ArenaTemplate template) {
        if (template == null) {
            clearMesh();
            currentTemplateId = null;
            return;
        }

        // Only rebuild if template changed
        if (template.id().equals(currentTemplateId)) {
            return;
        }

        currentTemplateId = template.id();
        rebuildMesh(template);
    }

    /**
     * Set the preview from simplified suggestion data.
     * Used on client-side where full ArenaTemplate is not available.
     *
     * @param templateId Unique ID for caching
     * @param arenaShape Shape name: "RECTANGULAR", "CIRCULAR", or "RING"
     * @param size Arena size
     * @param hasWalls Whether the arena has walls
     * @param hasHazards Whether the arena has hazards
     * @param spawnSlotCount Number of spawn slots
     */
    public void setFromSuggestion(
        String templateId,
        String arenaShape,
        int size,
        boolean hasWalls,
        boolean hasHazards,
        int spawnSlotCount
    ) {
        if (templateId == null) {
            clearMesh();
            currentTemplateId = null;
            return;
        }

        // Only rebuild if template changed
        if (templateId.equals(currentTemplateId)) {
            return;
        }

        currentTemplateId = templateId;
        rebuildMeshFromSuggestion(arenaShape, size, hasWalls, hasHazards, spawnSlotCount);
    }

    /**
     * Rebuild mesh from suggestion data.
     */
    private void rebuildMeshFromSuggestion(String arenaShape, int size, boolean hasWalls, boolean hasHazards, int spawnSlotCount) {
        clearMesh();

        this.mesh = new ArenaPreviewMesh(arenaShape, size, hasWalls, hasHazards, spawnSlotCount);

        if (mesh.isEmpty()) {
            return;
        }

        // Calculate scale to fit preview area
        int maxDim = Math.max(mesh.getWidth(), mesh.getDepth());
        this.scale = 1.0f / maxDim;

        // Build and upload to VBO
        uploadToVBO();
    }

    /**
     * Rebuild the mesh and upload to VBO.
     */
    private void rebuildMesh(@Nonnull ArenaTemplate template) {
        clearMesh();

        this.mesh = new ArenaPreviewMesh(template);

        if (mesh.isEmpty()) {
            return;
        }

        // Calculate scale to fit preview area
        int maxDim = Math.max(mesh.getWidth(), mesh.getDepth());
        this.scale = 1.0f / maxDim;

        // Build and upload to VBO
        uploadToVBO();
    }

    /**
     * Upload mesh to VBO for efficient rendering.
     */
    private void uploadToVBO() {
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack poseStack = new PoseStack();
        mesh.render(builder, poseStack);

        MeshData meshData = builder.buildOrThrow();
        this.vertexCount = meshData.drawState().vertexCount();

        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.vertexBuffer.bind();
        this.vertexBuffer.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Clear mesh and release GPU resources.
     */
    private void clearMesh() {
        mesh = null;
        if (vertexBuffer != null) {
            VertexBuffer bufferToClose = vertexBuffer;
            vertexBuffer = null;
            vertexCount = 0;
            RenderSystem.recordRenderCall(bufferToClose::close);
        }
    }

    /**
     * Render the preview within the specified bounds.
     *
     * @param poseStack The pose stack for transformations
     * @param x Left edge of preview area
     * @param y Top edge of preview area
     * @param width Width of preview area
     * @param height Height of preview area
     * @param partialTick Partial tick for smooth animation
     */
    public void render(@Nonnull PoseStack poseStack, int x, int y, int width, int height, float partialTick) {
        if (vertexBuffer == null || vertexCount == 0) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        // Update rotation
        if (autoRotate) {
            rotationAngle += partialTick * 0.5f;
            if (rotationAngle > 360) rotationAngle -= 360;
        }

        poseStack.pushPose();

        // Position at center of preview area
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;
        poseStack.translate(centerX, centerY, 100);

        // Scale to fit preview area with some padding
        float previewScale = Math.min(width, height) * 0.35f * scale;
        poseStack.scale(previewScale, -previewScale, previewScale); // Flip Y for GUI

        // Apply rotation
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(30)));
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(rotationAngle)));

        // Render
        renderVBO(poseStack);

        poseStack.popPose();
    }

    /**
     * Render the VBO with current transformations.
     */
    private void renderVBO(@Nonnull PoseStack poseStack) {
        if (vertexBuffer == null) {
            return;
        }

        // Set shader
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Setup render state
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 771); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA

        // Get shader
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        // Build matrices
        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();

        // Set shader uniforms
        shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        shader.PROJECTION_MATRIX.set(projectionMatrix);
        shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, 0.9f);

        // Draw
        vertexBuffer.bind();
        shader.apply();
        vertexBuffer.draw();
        VertexBuffer.unbind();
        shader.clear();
    }

    /**
     * Set whether the preview auto-rotates.
     */
    public void setAutoRotate(boolean autoRotate) {
        this.autoRotate = autoRotate;
    }

    /**
     * Set the rotation angle manually.
     */
    public void setRotationAngle(float angle) {
        this.rotationAngle = angle;
    }

    /**
     * Get the current rotation angle.
     */
    public float getRotationAngle() {
        return rotationAngle;
    }

    /**
     * Check if a template is currently loaded.
     */
    public boolean hasTemplate() {
        return currentTemplateId != null && vertexBuffer != null;
    }

    /**
     * Get the current template ID.
     */
    @Nullable
    public String getCurrentTemplateId() {
        return currentTemplateId;
    }

    /**
     * Cleanup all resources. Call when done using the renderer.
     */
    public void cleanup() {
        clearMesh();
        currentTemplateId = null;
    }
}

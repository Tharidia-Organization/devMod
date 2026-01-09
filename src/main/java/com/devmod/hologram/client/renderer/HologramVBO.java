package com.devmod.hologram.client.renderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * GPU vertex buffer wrapper for hologram rendering.
 * Uploads mesh data once and renders with a single draw call.
 *
 * <p>This provides significant performance improvement over immediate mode
 * rendering when the mesh doesn't change frequently.
 */
public class HologramVBO {
    @Nullable
    private VertexBuffer vertexBuffer;
    private VertexFormat.Mode drawMode;
    private int vertexCount;

    /**
     * Upload mesh data to the GPU.
     *
     * @param mesh The mesh to upload
     */
    public void upload(@Nonnull HologramMesh mesh) {
        // Close existing buffer if any
        close();

        if (mesh.isEmpty()) {
            return;
        }

        this.drawMode = VertexFormat.Mode.QUADS;
        VertexFormat format = DefaultVertexFormat.POSITION_COLOR;

        // Build vertices
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(drawMode, format);
        PoseStack poseStack = new PoseStack();
        mesh.render(builder, poseStack);

        // Upload to GPU
        MeshData meshData = builder.buildOrThrow();
        this.vertexCount = meshData.drawState().vertexCount();

        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.vertexBuffer.bind();
        this.vertexBuffer.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Render the VBO with the given matrices and alpha.
     *
     * @param modelViewMatrix The model-view matrix
     * @param projectionMatrix The projection matrix
     * @param alpha Alpha value for transparency (0.0-1.0)
     */
    public void render(@Nonnull Matrix4f modelViewMatrix, @Nonnull Matrix4f projectionMatrix, float alpha) {
        if (vertexBuffer == null || vertexCount == 0) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        // Setup render state
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 771); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA

        // Get current shader
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        // Set shader uniforms
        shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        shader.PROJECTION_MATRIX.set(projectionMatrix);
        shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, alpha);

        // Draw
        vertexBuffer.bind();
        shader.apply();
        vertexBuffer.draw();
        VertexBuffer.unbind();
        shader.clear();
    }

    /**
     * Close and release GPU resources.
     */
    public void close() {
        if (vertexBuffer != null) {
            VertexBuffer bufferToClose = vertexBuffer;
            vertexBuffer = null;
            vertexCount = 0;

            // Schedule close on render thread
            RenderSystem.recordRenderCall(bufferToClose::close);
        }
    }

    /**
     * Check if this VBO is valid and ready for rendering.
     */
    public boolean isValid() {
        return vertexBuffer != null && vertexCount > 0;
    }

    /**
     * Get the number of vertices in this VBO.
     */
    public int getVertexCount() {
        return vertexCount;
    }
}

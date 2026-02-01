package com.devmod.hologram.client.renderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramVBO.class);

    @Nullable
    private VertexBuffer vertexBuffer;
    private VertexFormat.Mode drawMode = VertexFormat.Mode.QUADS;
    private int vertexCount;
    private boolean texturedMode;
    private boolean hasUVCoords;  // Actual VBO format includes UV coordinates

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

        // Choose vertex format based on mesh mode and shader availability
        // CRITICAL: Track actual format used, not just what mesh wanted
        boolean useUV = HologramShaderRegistry.isTexturedModeSupported();
        this.hasUVCoords = useUV;
        // texturedMode is true only if BOTH mesh wants textures AND VBO has UV coords
        this.texturedMode = mesh.isTexturedMode() && useUV;

        LOGGER.info("[HologramVBO] upload: meshTexturedMode={}, shaderSupported={}, useUV={}, finalTexturedMode={}",
                    mesh.isTexturedMode(), useUV, useUV, this.texturedMode);

        VertexFormat format = useUV ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR;

        // Build vertices
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(drawMode, format);
        PoseStack poseStack = new PoseStack();
        mesh.render(builder, poseStack, useUV);

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
     * Check if this VBO was built in textured mode (mesh wanted textures AND VBO has UV coords).
     */
    public boolean isTexturedMode() {
        return texturedMode;
    }

    /**
     * Check if this VBO was built with UV coordinates.
     * This determines whether the custom hologram shader can be used safely.
     * If false, must use fallback shader to avoid vertex attribute mismatch.
     */
    public boolean hasUVCoords() {
        return hasUVCoords;
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

        // Get current shader
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        // Setup render state - state is managed by RenderType in Minecraft's pipeline,
        // so we use default blend function which is what most RenderTypes expect
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // Use Minecraft's default blend function

        try {
            // Set shader uniforms (with null checks for safety)
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
            }
            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(projectionMatrix);
            }
            if (shader.COLOR_MODULATOR != null) {
                shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, alpha);
            }

            // Draw
            vertexBuffer.bind();
            shader.apply();
            vertexBuffer.draw();
            VertexBuffer.unbind();
            shader.clear();
        } finally {
            // Restore default render state to avoid affecting subsequent rendering
            RenderSystem.disableBlend();
        }
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

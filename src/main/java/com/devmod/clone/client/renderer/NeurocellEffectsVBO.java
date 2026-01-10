package com.devmod.clone.client.renderer;

import java.util.Objects;

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
import com.mojang.math.Axis;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;

/**
 * Singleton VBO manager for Neurocell energy effects.
 *
 * <p>This class manages two VBOs:
 * <ul>
 *   <li>Ring VBO - scanning ring geometry</li>
 *   <li>Helix VBO - energy helix particles</li>
 * </ul>
 *
 * <p>The geometry is static and uploaded once. Animation is achieved through:
 * <ul>
 *   <li>PoseStack transformations (rotation, translation)</li>
 *   <li>COLOR_MODULATOR shader uniform (alpha pulsing)</li>
 * </ul>
 */
public final class NeurocellEffectsVBO {

    private static NeurocellEffectsVBO instance;

    @Nullable
    private VertexBuffer ringVBO;
    @Nullable
    private VertexBuffer helixVBO;

    private int ringVertexCount;
    private int helixVertexCount;
    private boolean initialized = false;

    private NeurocellEffectsVBO() {}

    /**
     * Get the singleton instance, initializing if necessary.
     */
    @Nonnull
    public static NeurocellEffectsVBO getInstance() {
        if (instance == null) {
            instance = new NeurocellEffectsVBO();
        }
        return instance;
    }

    /**
     * Initialize VBOs with pre-computed mesh geometry.
     * Call this during client setup or on first render.
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        NeurocellEffectsMesh mesh = NeurocellEffectsMesh.build();
        uploadRingVBO(mesh);
        uploadHelixVBO(mesh);

        initialized = true;
    }

    /**
     * Upload ring geometry to VBO.
     */
    private void uploadRingVBO(NeurocellEffectsMesh mesh) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR_NORMAL
        );

        PoseStack poseStack = new PoseStack();
        mesh.renderRing(builder, poseStack, 0, 1.0f);

        MeshData meshData = builder.buildOrThrow();
        ringVertexCount = meshData.drawState().vertexCount();

        ringVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        ringVBO.bind();
        ringVBO.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Upload helix geometry to VBO.
     */
    private void uploadHelixVBO(NeurocellEffectsMesh mesh) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR_NORMAL
        );

        PoseStack poseStack = new PoseStack();
        mesh.renderHelix(builder, poseStack, 1.0f);

        MeshData meshData = builder.buildOrThrow();
        helixVertexCount = meshData.drawState().vertexCount();

        helixVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        helixVBO.bind();
        helixVBO.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Render the complete energy effects (rings + helix).
     *
     * @param poseStack Current pose stack (positioned at block center)
     * @param animTime Animation time for transformations
     */
    public void renderEffects(@Nonnull PoseStack poseStack, float animTime) {
        renderEffects(poseStack, animTime, 1.0f);
    }

    /**
     * Render the complete energy effects with custom scale.
     *
     * @param poseStack Current pose stack (positioned at block center)
     * @param animTime Animation time for transformations
     * @param scale Scale factor for effect size (1.0 = normal, 1.3 = NeurocellL)
     */
    public void renderEffects(@Nonnull PoseStack poseStack, float animTime, float scale) {
        if (!initialized) {
            initialize();
        }

        if (ringVBO == null || helixVBO == null) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        // Setup render state for translucent lightning-like effect
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false); // Don't write to depth buffer
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 771); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA

        // Get projection matrix
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();

        // Render first ring (moving up) - scale affects radius
        float scanY1 = 0.3f + 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderRing(poseStack, projectionMatrix, scanY1, 0.42f * scale, animTime * 50.0f, 1.0f);

        // Render second ring (moving down)
        float scanY2 = 1.3f - 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderRing(poseStack, projectionMatrix, scanY2, 0.38f * scale, -animTime * 50.0f, 1.0f);

        // Render helix with pulsing alpha - scale affects helix size
        float cycleTime = animTime % 5.0f;
        float fadeMultiplier = 0.5f + 0.5f * Mth.sin((cycleTime / 5.0f) * 6.28318f - 1.5708f);
        renderHelix(poseStack, projectionMatrix, animTime * 2.0f, fadeMultiplier, scale);

        // Restore render state
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Render a scanning ring at the specified position.
     */
    private void renderRing(@Nonnull PoseStack poseStack, @Nonnull Matrix4f projectionMatrix,
                            float y, float radius, float rotation, float alpha) {
        if (ringVBO == null || ringVertexCount == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation)));
        poseStack.scale(radius, 1.0f, radius);

        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());

        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
            shader.PROJECTION_MATRIX.set(projectionMatrix);
            shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, alpha);

            ringVBO.bind();
            shader.apply();
            ringVBO.draw();
            VertexBuffer.unbind();
            shader.clear();
        }

        poseStack.popPose();
    }

    /**
     * Render the energy helix with rotation, alpha, and scale.
     */
    private void renderHelix(@Nonnull PoseStack poseStack, @Nonnull Matrix4f projectionMatrix,
                             float rotation, float alpha, float scale) {
        if (helixVBO == null || helixVertexCount == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation * 57.2958f)));
        poseStack.scale(scale, 1.0f, scale); // Scale helix in XZ plane

        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());

        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
            shader.PROJECTION_MATRIX.set(projectionMatrix);
            shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, alpha);

            helixVBO.bind();
            shader.apply();
            helixVBO.draw();
            VertexBuffer.unbind();
            shader.clear();
        }

        poseStack.popPose();
    }

    /**
     * Check if VBOs are initialized and ready.
     */
    public boolean isReady() {
        return initialized && ringVBO != null && helixVBO != null;
    }

    /**
     * Release GPU resources.
     */
    public void close() {
        if (ringVBO != null) {
            VertexBuffer vbo = ringVBO;
            ringVBO = null;
            RenderSystem.recordRenderCall(vbo::close);
        }
        if (helixVBO != null) {
            VertexBuffer vbo = helixVBO;
            helixVBO = null;
            RenderSystem.recordRenderCall(vbo::close);
        }
        ringVertexCount = 0;
        helixVertexCount = 0;
        initialized = false;
    }

    /**
     * Reset the singleton instance (for resource reload).
     */
    public static void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}

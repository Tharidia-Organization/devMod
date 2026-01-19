package com.devmod.clone.client.renderer;

import java.util.EnumMap;
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

import com.devmod.clone.client.renderer.NeurocellEffectsMesh.EffectColor;

/**
 * Singleton VBO manager for Neurocell energy effects.
 *
 * <p>This class manages VBOs for each color variant:
 * <ul>
 *   <li>CYAN - Default Neurocell</li>
 *   <li>RED - NeurocellL (large)</li>
 *   <li>GREEN - NeurocellMannequin</li>
 * </ul>
 *
 * <p>Each color has:
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

    @Nullable
    private static NeurocellEffectsVBO instance;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    /** VBO data for a single color variant */
    private static class ColorVBOData {
        @Nullable VertexBuffer ringVBO;
        @Nullable VertexBuffer helixVBO;
        int ringVertexCount;
        int helixVertexCount;
        boolean initialized = false;

        void close() {
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
    }

    /** VBO data per color */
    private final EnumMap<EffectColor, ColorVBOData> colorData = new EnumMap<>(EffectColor.class);

    private NeurocellEffectsVBO() {
        // Initialize color data map
        for (EffectColor color : EffectColor.values()) {
            colorData.put(color, new ColorVBOData());
        }
    }

    /**
     * Get the singleton instance, initializing if necessary.
     */
    @Nonnull
    public static NeurocellEffectsVBO getInstance() {
        if (instance == null) {
            instance = new NeurocellEffectsVBO();
        }
        return Objects.requireNonNull(instance);
    }

    /**
     * Initialize VBOs for the specified color variant.
     *
     * @param color The color to initialize
     */
    private void initializeColor(EffectColor color) {
        ColorVBOData data = colorData.get(color);
        if (data == null || data.initialized) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        NeurocellEffectsMesh mesh = NeurocellEffectsMesh.build(color);
        uploadRingVBO(mesh, data);
        uploadHelixVBO(mesh, data);

        data.initialized = true;
    }

    /**
     * Initialize VBOs with pre-computed mesh geometry (default CYAN).
     * Call this during client setup or on first render.
     */
    public void initialize() {
        initializeColor(EffectColor.CYAN);
    }

    /**
     * Upload ring geometry to VBO.
     * VBO is stored in ColorVBOData and closed via close() method.
     */
    @SuppressWarnings("resource") // VBO stored in data.ringVBO, closed via ColorVBOData.close()
    private void uploadRingVBO(NeurocellEffectsMesh mesh, ColorVBOData data) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            Objects.requireNonNull(DefaultVertexFormat.POSITION_COLOR_NORMAL)
        );

        PoseStack poseStack = new PoseStack();
        mesh.renderRing(Objects.requireNonNull(builder), poseStack, 0, 1.0f);

        MeshData meshData = builder.buildOrThrow();
        data.ringVertexCount = meshData.drawState().vertexCount();

        // VBO is stored in data.ringVBO and closed via ColorVBOData.close()
        VertexBuffer ringVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        data.ringVBO = ringVBO;
        ringVBO.bind();
        ringVBO.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Upload helix geometry to VBO.
     * VBO is stored in ColorVBOData and closed via close() method.
     */
    @SuppressWarnings("resource") // VBO stored in data.helixVBO, closed via ColorVBOData.close()
    private void uploadHelixVBO(NeurocellEffectsMesh mesh, ColorVBOData data) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            Objects.requireNonNull(DefaultVertexFormat.POSITION_COLOR_NORMAL)
        );

        PoseStack poseStack = new PoseStack();
        mesh.renderHelix(Objects.requireNonNull(builder), poseStack, 1.0f);

        MeshData meshData = builder.buildOrThrow();
        data.helixVertexCount = meshData.drawState().vertexCount();

        // VBO is stored in data.helixVBO and closed via ColorVBOData.close()
        VertexBuffer helixVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        data.helixVBO = helixVBO;
        helixVBO.bind();
        helixVBO.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Render the complete energy effects (rings + helix) with default CYAN color.
     *
     * @param poseStack Current pose stack (positioned at block center)
     * @param animTime Animation time for transformations
     */
    public void renderEffects(@Nonnull PoseStack poseStack, float animTime) {
        renderEffects(poseStack, animTime, 1.0f, EffectColor.CYAN);
    }

    /**
     * Render the complete energy effects with custom scale (default CYAN color).
     *
     * @param poseStack Current pose stack (positioned at block center)
     * @param animTime Animation time for transformations
     * @param scale Scale factor for effect size (1.0 = normal, 1.3 = NeurocellL)
     */
    public void renderEffects(@Nonnull PoseStack poseStack, float animTime, float scale) {
        renderEffects(poseStack, animTime, scale, EffectColor.CYAN);
    }

    /**
     * Render the complete energy effects with custom scale and color.
     *
     * @param poseStack Current pose stack (positioned at block center)
     * @param animTime Animation time for transformations
     * @param scale Scale factor for effect size (1.0 = normal, 1.3 = NeurocellL)
     * @param color The color variant to render
     */
    public void renderEffects(@Nonnull PoseStack poseStack, float animTime, float scale, EffectColor color) {
        ColorVBOData data = colorData.get(color);
        if (data == null) {
            return;
        }

        if (!data.initialized) {
            initializeColor(color);
        }

        if (data.ringVBO == null || data.helixVBO == null) {
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
        Matrix4f projectionMatrix = Objects.requireNonNull(RenderSystem.getProjectionMatrix());

        // Render first ring (moving up) - scale affects radius
        float scanY1 = 0.3f + 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderRing(poseStack, projectionMatrix, scanY1, 0.42f * scale, animTime * 50.0f, 1.0f, data);

        // Render second ring (moving down)
        float scanY2 = 1.3f - 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderRing(poseStack, projectionMatrix, scanY2, 0.38f * scale, -animTime * 50.0f, 1.0f, data);

        // Render helix with pulsing alpha - scale affects helix size
        float cycleTime = animTime % 5.0f;
        float fadeMultiplier = 0.5f + 0.5f * Mth.sin((cycleTime / 5.0f) * TWO_PI - HALF_PI);
        renderHelix(poseStack, projectionMatrix, animTime * 2.0f, fadeMultiplier, scale, data);

        // Restore render state
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Render a scanning ring at the specified position.
     */
    private void renderRing(@Nonnull PoseStack poseStack, @Nonnull Matrix4f projectionMatrix,
                            float y, float radius, float rotation, float alpha, ColorVBOData data) {
        if (data.ringVBO == null || data.ringVertexCount == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation)));
        poseStack.scale(radius, 1.0f, radius);

        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(Objects.requireNonNull(poseStack.last().pose()));

        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            var modelViewUniform = shader.MODEL_VIEW_MATRIX;
            var projectionUniform = shader.PROJECTION_MATRIX;
            var colorUniform = shader.COLOR_MODULATOR;
            if (modelViewUniform != null) {
                modelViewUniform.set(modelViewMatrix);
            }
            if (projectionUniform != null) {
                projectionUniform.set(projectionMatrix);
            }
            if (colorUniform != null) {
                colorUniform.set(1.0f, 1.0f, 1.0f, alpha);
            }

            var vbo = data.ringVBO;
            if (vbo != null) {
                vbo.bind();
                shader.apply();
                vbo.draw();
                VertexBuffer.unbind();
            }
            shader.clear();
        }

        poseStack.popPose();
    }

    /**
     * Render the energy helix with rotation, alpha, and scale.
     */
    private void renderHelix(@Nonnull PoseStack poseStack, @Nonnull Matrix4f projectionMatrix,
                             float rotation, float alpha, float scale, ColorVBOData data) {
        if (data.helixVBO == null || data.helixVertexCount == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation * RAD_TO_DEG)));
        poseStack.scale(scale, 1.0f, scale); // Scale helix in XZ plane

        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(Objects.requireNonNull(poseStack.last().pose()));

        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            var modelViewUniform = shader.MODEL_VIEW_MATRIX;
            var projectionUniform = shader.PROJECTION_MATRIX;
            var colorUniform = shader.COLOR_MODULATOR;
            if (modelViewUniform != null) {
                modelViewUniform.set(modelViewMatrix);
            }
            if (projectionUniform != null) {
                projectionUniform.set(projectionMatrix);
            }
            if (colorUniform != null) {
                colorUniform.set(1.0f, 1.0f, 1.0f, alpha);
            }

            var vbo = data.helixVBO;
            if (vbo != null) {
                vbo.bind();
                shader.apply();
                vbo.draw();
                VertexBuffer.unbind();
            }
            shader.clear();
        }

        poseStack.popPose();
    }

    /**
     * Check if VBOs are initialized and ready (for default CYAN color).
     */
    public boolean isReady() {
        ColorVBOData data = colorData.get(EffectColor.CYAN);
        return data != null && data.initialized && data.ringVBO != null && data.helixVBO != null;
    }

    /**
     * Release GPU resources for all colors.
     */
    public void close() {
        for (ColorVBOData data : colorData.values()) {
            data.close();
        }
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

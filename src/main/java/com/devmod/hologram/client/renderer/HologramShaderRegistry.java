package com.devmod.hologram.client.renderer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.devmod.DevMod;
import com.devmod.client.rendering.shader.ShaderPipeline;
import com.devmod.client.rendering.shader.ShaderPipelineDiagnostics;
import com.devmod.client.rendering.shader.ShaderRenderTypeConfig;

/**
 * Registry for the hologram shader with sci-fi effects.
 * Provides scan lines, glow, glitch, and pulse animations.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HologramShaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramShaderRegistry.class);

    private static final ShaderRenderTypeConfig HOLOGRAM_CONFIG = new ShaderRenderTypeConfig(
        "devmod_hologram",
        "devmod_hologram_fallback",
        DefaultVertexFormat.POSITION_TEX_COLOR,  // Support textures
        DefaultVertexFormat.POSITION_COLOR,       // Fallback without textures
        VertexFormat.Mode.QUADS,
        262144, // Large buffer for big holograms
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderPipeline PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram"),
        HOLOGRAM_CONFIG
    );

    @Nullable
    private static RenderType renderType;
    private static boolean usingFallback = true;

    private HologramShaderRegistry() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("╔══════════════════════════════════════════════════════════════╗");
        LOGGER.info("║ [Hologram] RegisterShadersEvent received - starting registration");
        LOGGER.info("╚══════════════════════════════════════════════════════════════╝");
        PIPELINE.register(event, LOGGER);
        refreshRenderType();
        ShaderPipelineDiagnostics.logStatus("Hologram", PIPELINE, LOGGER);
        LOGGER.info("[Hologram] Shader registration complete. Ready={}, UsingFallback={}",
                    PIPELINE.isReady(), usingFallback);
    }

    private static void refreshRenderType() {
        boolean useFallback = !PIPELINE.isReady();
        LOGGER.info("[Hologram] refreshRenderType: pipelineReady={}, useFallback={}", PIPELINE.isReady(), useFallback);

        String renderTypeName = useFallback ? HOLOGRAM_CONFIG.fallbackName() : HOLOGRAM_CONFIG.name();
        var format = useFallback ? HOLOGRAM_CONFIG.fallbackFormat() : HOLOGRAM_CONFIG.primaryFormat();

        RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
            .setShaderState(PIPELINE.shaderStateShard())
            .setTransparencyState(HOLOGRAM_CONFIG.transparencyState())
            .setDepthTestState(HOLOGRAM_CONFIG.depthTestState())
            .setWriteMaskState(HOLOGRAM_CONFIG.writeMaskState())
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false);

        renderType = RenderType.create(renderTypeName, format, HOLOGRAM_CONFIG.mode(),
            HOLOGRAM_CONFIG.bufferSize(), HOLOGRAM_CONFIG.affectsCrumbling(),
            HOLOGRAM_CONFIG.sortOnUpload(), compositeState);

        usingFallback = useFallback;
    }

    @Nullable
    public static RenderType getRenderType() {
        if (renderType == null || (usingFallback && PIPELINE.isReady())) {
            refreshRenderType();
        }
        return renderType != null ? renderType : PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getShader() {
        return PIPELINE.shader();
    }

    public static boolean isReady() {
        return PIPELINE.isReady();
    }

    /**
     * Get the vertex format to use for mesh building.
     * Returns POSITION_TEX_COLOR when shader is ready, POSITION_COLOR as fallback.
     */
    public static com.mojang.blaze3d.vertex.VertexFormat getVertexFormat() {
        return PIPELINE.isReady() ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR;
    }

    /**
     * Check if texture mode is supported (shader is ready and not using fallback).
     * Triggers lazy refresh if shader became ready after initial setup.
     */
    public static boolean isTexturedModeSupported() {
        boolean pipelineReady = PIPELINE.isReady();
        boolean pipelineInitialized = PIPELINE.isInitialized();

        // Lazy refresh: if shader is now ready but we're still using fallback, refresh
        if (usingFallback && pipelineReady) {
            LOGGER.info("[Hologram] Shader became ready (was fallback), refreshing render type for textured mode");
            refreshRenderType();
        }

        boolean result = pipelineReady && !usingFallback;
        LOGGER.debug("[Hologram] isTexturedModeSupported: pipelineReady={}, initialized={}, usingFallback={}, result={}",
                    pipelineReady, pipelineInitialized, usingFallback, result);
        return result;
    }

    /**
     * Set shader uniforms for hologram rendering.
     *
     * @param gameTime Current game time for animations
     * @param alpha Base transparency
     * @param glitchIntensity Glitch effect strength (0-1)
     * @param scanLineSpeed Scan line scroll speed
     * @param holoColor Hologram tint color (RGB)
     */
    public static void setUniforms(float gameTime, float alpha, float glitchIntensity,
                                    float scanLineSpeed, float[] holoColor) {
        ShaderInstance shader = getShader();
        if (shader == null) return;

        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(gameTime);
        }

        setUniformSafe(shader, "Alpha", alpha);
        setUniformSafe(shader, "GlitchIntensity", glitchIntensity);
        setUniformSafe(shader, "ScanLineSpeed", scanLineSpeed);
        setUniformSafe(shader, "ScanLineDensity", 30.0f);
        setUniformSafe(shader, "GlowIntensity", 0.4f);
        setUniformSafe(shader, "PulseSpeed", 1.0f);

        if (holoColor != null && holoColor.length >= 3) {
            setUniformSafe(shader, "HoloColor", holoColor[0], holoColor[1], holoColor[2]);
            setUniformSafe(shader, "GlowColor", holoColor[0] * 1.2f, holoColor[1] * 1.1f, holoColor[2]);
        }
    }

    /**
     * Set animation-specific uniforms for fade-in and wave effects.
     *
     * @param fadeInProgress Progress of fade-in animation (0-1, 1 = fully visible)
     * @param wavePhase Current phase of the wave animation
     * @param waveIntensity Intensity of the wave effect
     * @param maxY Maximum Y height of the hologram mesh
     */
    public static void setAnimationUniforms(float fadeInProgress, float wavePhase,
                                             float waveIntensity, float maxY) {
        ShaderInstance shader = getShader();
        if (shader == null) return;

        setUniformSafe(shader, "FadeInProgress", fadeInProgress);
        setUniformSafe(shader, "WavePhase", wavePhase);
        setUniformSafe(shader, "WaveIntensity", waveIntensity);
        setUniformSafe(shader, "MaxY", maxY);
    }

    /**
     * Set texture mode uniforms.
     *
     * @param texturedMode Whether to sample from block textures
     * @param textureBlend How much to blend texture with hologram tint (0 = full texture, 1 = full holo color)
     */
    public static void setTexturedModeUniforms(boolean texturedMode, float textureBlend) {
        ShaderInstance shader = getShader();
        if (shader == null) return;

        setUniformSafe(shader, "TexturedMode", texturedMode ? 1.0f : 0.0f);
        setUniformSafe(shader, "TextureBlend", textureBlend);
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float r, float g, float b) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(r, g, b);
        }
    }
}

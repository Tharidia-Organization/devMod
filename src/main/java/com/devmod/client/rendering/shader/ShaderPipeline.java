package com.devmod.client.rendering.shader;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
@OnlyIn(Dist.CLIENT)
public final class ShaderPipeline {
    private final String name;
    private final ResourceLocation shaderLocation;
    private final ShaderRenderTypeConfig renderTypeConfig;
    private final RenderStateShard.ShaderStateShard fallbackShaderState;

    private RenderType renderType;
    private RenderType fallbackRenderType;
    @Nullable
    private ShaderInstance shaderInstance;
    private boolean initialized;

    public ShaderPipeline(ResourceLocation shaderLocation, ShaderRenderTypeConfig renderTypeConfig) {
        this(shaderLocation, renderTypeConfig, RenderStateShard.POSITION_COLOR_SHADER);
    }

    public ShaderPipeline(ResourceLocation shaderLocation, ShaderRenderTypeConfig renderTypeConfig,
                          RenderStateShard.ShaderStateShard fallbackShaderState) {
        this.name = shaderLocation.toString();
        this.shaderLocation = Objects.requireNonNull(shaderLocation, "shaderLocation");
        this.renderTypeConfig = Objects.requireNonNull(renderTypeConfig, "renderTypeConfig");
        this.fallbackShaderState = Objects.requireNonNull(fallbackShaderState, "fallbackShaderState");
        // Always create a fallback RenderType so callers have something usable pre-init
        this.fallbackRenderType = renderTypeConfig.build(fallbackShaderState, true);
        this.renderType = this.fallbackRenderType;
    }

    /**
    * Registers the shader and builds its RenderTypes.
    *
    * <p>This is idempotent; subsequent calls after success are ignored.</p>
    */
    public void register(RegisterShadersEvent event, Logger logger) {
        if (initialized) return;

        try {
            event.registerShader(
                new ShaderInstance(
                    Objects.requireNonNull(event.getResourceProvider()),
                    Objects.requireNonNull(shaderLocation),
                    Objects.requireNonNull(renderTypeConfig.primaryFormat())
                ),
                shader -> {
                    shaderInstance = shader;
                    buildRenderTypes();
                    initialized = true;
                    logger.info("[Shaders] Pipeline '{}' registered", name);
                }
            );
        } catch (IOException e) {
            logger.error("[Shaders] Failed to register shader '{}'", name, e);
            buildRenderTypes(); // build fallback
            initialized = true;
        }
    }

    private void buildRenderTypes() {
        // Build fallback first (vanilla shader)
        fallbackRenderType = renderTypeConfig.build(fallbackShaderState, true);

        RenderStateShard.ShaderStateShard shaderState = shaderStateShard();

        // Use fallback format when shader is missing
        renderType = renderTypeConfig.build(shaderState, shaderInstance == null);
    }

    /**
     * Builds an additional RenderType variant using the active shader (or fallback if missing).
     *
     * @param config RenderType config for the variant
     * @param useFallbackFormatWhenShaderMissing use fallback format if shader failed to load
     */
    public RenderType buildRenderType(ShaderRenderTypeConfig config, boolean useFallbackFormatWhenShaderMissing) {
        boolean useFallbackFormat = useFallbackFormatWhenShaderMissing && shaderInstance == null;
        return config.build(shaderStateShard(), useFallbackFormat);
    }

    /**
     * Returns the shader state shard to use (custom if ready, otherwise fallback).
     */
    public RenderStateShard.ShaderStateShard shaderStateShard() {
        if (shaderInstance != null) {
            return new RenderStateShard.ShaderStateShard(() -> Objects.requireNonNull(shaderInstance));
        }
        return fallbackShaderState;
    }

    @Nullable
    public ShaderInstance shader() {
        return shaderInstance;
    }

    public RenderType renderType() {
        return renderType;
    }

    public RenderType fallbackRenderType() {
        return fallbackRenderType;
    }

    public boolean isReady() {
        return shaderInstance != null;
    }

    public boolean isInitialized() {
        return initialized;
    }
}

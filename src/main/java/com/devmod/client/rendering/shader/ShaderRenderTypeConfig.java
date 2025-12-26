package com.devmod.client.rendering.shader;

import java.util.Objects;

import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
@OnlyIn(Dist.CLIENT)
public record ShaderRenderTypeConfig(
    String name,
    String fallbackName,
    VertexFormat primaryFormat,
    VertexFormat fallbackFormat,
    VertexFormat.Mode mode,
    int bufferSize,
    RenderStateShard.TransparencyStateShard transparencyState,
    RenderStateShard.DepthTestStateShard depthTestState,
    RenderStateShard.WriteMaskStateShard writeMaskState,
    RenderStateShard.CullStateShard cullState,
    boolean affectsCrumbling,
    boolean sortOnUpload
) {
    public ShaderRenderTypeConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fallbackName, "fallbackName");
        Objects.requireNonNull(primaryFormat, "primaryFormat");
        Objects.requireNonNull(fallbackFormat, "fallbackFormat");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(transparencyState, "transparencyState");
        Objects.requireNonNull(depthTestState, "depthTestState");
        Objects.requireNonNull(writeMaskState, "writeMaskState");
        Objects.requireNonNull(cullState, "cullState");
    }

    public ShaderRenderTypeConfig(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                  RenderStateShard.TransparencyStateShard transparencyState,
                                  boolean affectsCrumbling, boolean sortOnUpload) {
        this(name, name + "_fallback", format, format, mode, bufferSize, transparencyState,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE, RenderStateShard.NO_CULL,
            affectsCrumbling, sortOnUpload);
    }

    public ShaderRenderTypeConfig(String name, String fallbackName, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                  RenderStateShard.TransparencyStateShard transparencyState,
                                  boolean affectsCrumbling, boolean sortOnUpload) {
        this(name, fallbackName, format, format, mode, bufferSize, transparencyState,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE, RenderStateShard.NO_CULL,
            affectsCrumbling, sortOnUpload);
    }

    public ShaderRenderTypeConfig(String name, VertexFormat primaryFormat, VertexFormat fallbackFormat,
                                  VertexFormat.Mode mode, int bufferSize,
                                  RenderStateShard.TransparencyStateShard transparencyState,
                                  boolean affectsCrumbling, boolean sortOnUpload) {
        this(name, name + "_fallback", primaryFormat, fallbackFormat, mode, bufferSize, transparencyState,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE, RenderStateShard.NO_CULL,
            affectsCrumbling, sortOnUpload);
    }

    public ShaderRenderTypeConfig(String name, String fallbackName, VertexFormat primaryFormat, VertexFormat fallbackFormat,
                                  VertexFormat.Mode mode, int bufferSize,
                                  RenderStateShard.TransparencyStateShard transparencyState,
                                  boolean affectsCrumbling, boolean sortOnUpload) {
        this(name, fallbackName, primaryFormat, fallbackFormat, mode, bufferSize, transparencyState,
            RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.COLOR_WRITE, RenderStateShard.NO_CULL,
            affectsCrumbling, sortOnUpload);
    }

    public RenderType build(RenderStateShard.ShaderStateShard shaderState, boolean useFallbackFormat) {
        String renderTypeName = useFallbackFormat ? fallbackName : name;
        VertexFormat format = useFallbackFormat ? fallbackFormat : primaryFormat;

        RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
            .setShaderState(Objects.requireNonNull(shaderState))
            .setTransparencyState(Objects.requireNonNull(transparencyState))
            .setDepthTestState(Objects.requireNonNull(depthTestState))
            .setWriteMaskState(Objects.requireNonNull(writeMaskState))
            .setCullState(Objects.requireNonNull(cullState))
            .createCompositeState(false);

        return RenderType.create(Objects.requireNonNull(renderTypeName), Objects.requireNonNull(format), Objects.requireNonNull(mode), bufferSize, affectsCrumbling, sortOnUpload, Objects.requireNonNull(compositeState));
    }
}

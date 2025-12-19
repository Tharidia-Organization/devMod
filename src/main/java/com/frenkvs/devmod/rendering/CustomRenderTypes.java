package com.frenkvs.devmod.rendering;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.Objects;

/**
 * Custom RenderTypes for PhantomShapes-style rendering.
 * Provides POSITION_COLOR format with transparency support.
 */
public class CustomRenderTypes extends RenderType {

    // Dummy constructor - never called
    public CustomRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                              int bufferSize, boolean affectsCrumbling,
                              boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * Custom RenderType for translucent quads with POSITION_COLOR format.
     * Matches PhantomShapes POSITION_COLOR + alpha blending.
     */
    public static final RenderType TRANSLUCENT_POSITION_COLOR = create(
            "translucent_position_color",
            Objects.requireNonNull(DefaultVertexFormat.POSITION_COLOR, "POSITION_COLOR"),
            VertexFormat.Mode.QUADS,
            1536, // buffer size
            false, // affectsCrumbling
            true, // sortOnUpload - important for transparency
            Objects.requireNonNull(RenderType.CompositeState.builder()
                    .setShaderState(Objects.requireNonNull(RenderStateShard.POSITION_COLOR_SHADER, "POSITION_COLOR_SHADER"))
                    .setTransparencyState(Objects.requireNonNull(RenderStateShard.TRANSLUCENT_TRANSPARENCY, "TRANSLUCENT_TRANSPARENCY")) // Enable alpha blending
                    .setWriteMaskState(Objects.requireNonNull(RenderStateShard.COLOR_DEPTH_WRITE, "COLOR_DEPTH_WRITE")) // Write both color AND depth
                    .setCullState(Objects.requireNonNull(RenderStateShard.NO_CULL, "NO_CULL")) // Don't cull back faces
                    .setLightmapState(Objects.requireNonNull(RenderStateShard.NO_LIGHTMAP, "NO_LIGHTMAP"))
                    .setOverlayState(Objects.requireNonNull(RenderStateShard.NO_OVERLAY, "NO_OVERLAY"))
                    .setDepthTestState(Objects.requireNonNull(RenderStateShard.LEQUAL_DEPTH_TEST, "LEQUAL_DEPTH_TEST")) // Enable depth testing
                    .createCompositeState(true), "compositeState") // Enable sorting for transparency
    );
}

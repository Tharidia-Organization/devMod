package com.frenkvs.devmod.rendering;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

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
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536, // buffer size
            false, // affectsCrumbling
            true, // sortOnUpload - important for transparency
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY) // Enable alpha blending
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE) // Write both color AND depth
                    .setCullState(RenderStateShard.NO_CULL) // Don't cull back faces
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST) // Enable depth testing
                    .createCompositeState(true) // Enable sorting for transparency
    );
}
package com.frenkvs.devmod.rendering.shader;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.Arrays;

/**
 * Utility for logging shader pipeline readiness and fallback state.
 */
@OnlyIn(Dist.CLIENT)
public final class ShaderPipelineDiagnostics {
    private ShaderPipelineDiagnostics() {}

    public static void logStatuses(String groupName, Logger logger, ShaderPipeline... pipelines) {
        Arrays.stream(pipelines).forEach(pipeline -> logStatus(groupName, pipeline, logger));
    }

    public static void logStatus(String groupName, ShaderPipeline pipeline, Logger logger) {
        boolean ready = pipeline.isReady();
        RenderType renderType = pipeline.renderType();
        RenderType fallbackType = pipeline.fallbackRenderType();
        logger.info("[Shaders][{}] {} -> {}", groupName, renderType, ready ? "CUSTOM" : "FALLBACK");
        if (!ready) {
            logger.info("[Shaders][{}] Fallback RenderType: {}", groupName, fallbackType);
        }
    }
}

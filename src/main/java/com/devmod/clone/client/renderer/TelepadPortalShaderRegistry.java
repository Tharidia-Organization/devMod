package com.devmod.clone.client.renderer;

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

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class TelepadPortalShaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelepadPortalShaderRegistry.class);

    private static final ShaderRenderTypeConfig BASE_CONFIG = new ShaderRenderTypeConfig(
        "devmod_telepad_portal_base",
        "devmod_telepad_portal_base_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderRenderTypeConfig GLOW_CONFIG = new ShaderRenderTypeConfig(
        "devmod_telepad_portal_glow",
        "devmod_telepad_portal_glow_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.ADDITIVE_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderPipeline PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_portal"),
        BASE_CONFIG
    );

    @Nullable
    private static RenderType baseRenderType;
    @Nullable
    private static RenderType glowRenderType;

    private TelepadPortalShaderRegistry() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("[Telepad] Registering telepad portal shader...");
        PIPELINE.register(event, LOGGER);
        refreshRenderTypes();
        ShaderPipelineDiagnostics.logStatus("TelepadPortal", PIPELINE, LOGGER);
    }

    private static void refreshRenderTypes() {
        baseRenderType = PIPELINE.buildRenderType(BASE_CONFIG, true);
        glowRenderType = PIPELINE.buildRenderType(GLOW_CONFIG, true);
    }

    @Nullable
    public static RenderType getBaseRenderType() {
        if (baseRenderType == null) {
            refreshRenderTypes();
        }
        return baseRenderType != null ? baseRenderType : PIPELINE.renderType();
    }

    @Nullable
    public static RenderType getGlowRenderType() {
        if (glowRenderType == null) {
            refreshRenderTypes();
        }
        return glowRenderType != null ? glowRenderType : PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getShader() {
        return PIPELINE.shader();
    }

    public static boolean isReady() {
        return PIPELINE.isReady();
    }
}

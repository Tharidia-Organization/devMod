package com.frenkvs.devmod.rendering.shield;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.rendering.shader.ShaderPipeline;
import com.frenkvs.devmod.rendering.shader.ShaderPipelineDiagnostics;
import com.frenkvs.devmod.rendering.shader.ShaderRenderTypeConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Registers and manages custom shaders for the energy shield rendering.
 *
 * <p>This class handles:</p>
 * <ul>
 *   <li>Registration of the energy_shield shader via RegisterShadersEvent</li>
 *   <li>Creation of custom RenderType using the shader</li>
 *   <li>Proper integration with Minecraft's rendering pipeline</li>
 * </ul>
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class ShieldShaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShieldShaderRegistry.class);

    private static final ShaderRenderTypeConfig RENDER_CONFIG = new ShaderRenderTypeConfig(
        "devmod_energy_shield",
        "devmod_energy_shield_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        1536,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderPipeline PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "energy_shield"),
        RENDER_CONFIG
    );

    /**
     * Registers the energy shield shader during the RegisterShadersEvent.
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("[Shield] Registering energy shield shader...");
        PIPELINE.register(event, LOGGER);
        ShaderPipelineDiagnostics.logStatus("Shield", PIPELINE, LOGGER);
    }

    /**
     * Gets the energy shield RenderType.
     *
     * @return The RenderType for shield rendering, or null if not initialized
     */
    @Nullable
    public static RenderType getShieldRenderType() {
        return PIPELINE.renderType();
    }

    /**
     * Gets the energy shield shader instance.
     *
     * @return The ShaderInstance, or null if not loaded
     */
    @Nullable
    public static ShaderInstance getShader() {
        return PIPELINE.shader();
    }

    /**
     * Checks if the shader system is properly initialized.
     */
    public static boolean isInitialized() {
        return PIPELINE.isInitialized();
    }

    /**
     * Checks if the custom shader (not fallback) is being used.
     */
    public static boolean isUsingCustomShader() {
        return PIPELINE.isReady();
    }
}

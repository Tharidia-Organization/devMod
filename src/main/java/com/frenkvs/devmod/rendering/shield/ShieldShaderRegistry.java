package com.frenkvs.devmod.rendering.shield;

import com.frenkvs.devmod.DevMod;
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
import java.io.IOException;

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
@SuppressWarnings("null") // Minecraft API lacks null annotations
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class ShieldShaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShieldShaderRegistry.class);

    // Shader instance - populated after registration
    @Nullable
    private static ShaderInstance energyShieldShader;

    // Custom RenderType for energy shield
    private static RenderType energyShieldRenderType;

    // Flag to track initialization
    private static boolean initialized = false;

    /**
     * Registers the energy shield shader during the RegisterShadersEvent.
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("[Shield] Registering energy shield shader...");

        try {
            // Register the shader with the event
            // The shader JSON and GLSL files are in: assets/devmod/shaders/core/
            // Note: ShaderInstance automatically looks in shaders/core/ so we just specify the name
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "energy_shield"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    energyShieldShader = shader;
                    LOGGER.info("[Shield] Energy shield shader registered successfully!");
                    initializeRenderType();
                }
            );

        } catch (IOException e) {
            LOGGER.error("[Shield] Failed to register energy shield shader!", e);
            // Fall back to position_color shader if custom shader fails
            initializeFallbackRenderType();
        }
    }

    /**
     * Creates the custom RenderType using our shader.
     */
    private static void initializeRenderType() {
        if (energyShieldShader == null) {
            LOGGER.warn("[Shield] Shader is null, using fallback");
            initializeFallbackRenderType();
            return;
        }

        // Create ShaderStateShard with our custom shader
        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
            () -> energyShieldShader
        );

        // Create the RenderType with proper transparency settings
        energyShieldRenderType = RenderType.create(
            "devmod_energy_shield",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES,
            1536, // Buffer size
            false, // affects crumbling
            true,  // sort on upload (for transparency)
            RenderType.CompositeState.builder()
                .setShaderState(shaderState)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        initialized = true;
        LOGGER.info("[Shield] Energy shield RenderType created successfully");
    }

    /**
     * Creates a fallback RenderType using vanilla shaders if custom shader fails.
     */
    private static void initializeFallbackRenderType() {
        // Use vanilla position_color shader as fallback
        energyShieldRenderType = RenderType.create(
            "devmod_energy_shield_fallback",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        initialized = true;
        LOGGER.info("[Shield] Energy shield fallback RenderType created");
    }

    /**
     * Gets the energy shield RenderType.
     *
     * @return The RenderType for shield rendering, or null if not initialized
     */
    @Nullable
    public static RenderType getShieldRenderType() {
        return energyShieldRenderType;
    }

    /**
     * Gets the energy shield shader instance.
     *
     * @return The ShaderInstance, or null if not loaded
     */
    @Nullable
    public static ShaderInstance getShader() {
        return energyShieldShader;
    }

    /**
     * Checks if the shader system is properly initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if the custom shader (not fallback) is being used.
     */
    public static boolean isUsingCustomShader() {
        return initialized && energyShieldShader != null;
    }
}

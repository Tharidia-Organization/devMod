package com.frenkvs.devmod.rendering.shader;

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

/**
 * Centralized registry for all VFX shaders (ImpactVFX, Heatmap, Pathfinding).
 *
 * <p>Pattern follows ShieldShaderRegistry with support for multiple shaders.</p>
 *
 * <p>Registered shaders:</p>
 * <ul>
 *   <li>impact_vfx - Combat impact effects (vortex, slash, lines)</li>
 *   <li>heatmap - Block heatmap visualization (future)</li>
 *   <li>pathfinding - Mob pathfinding debug (future)</li>
 * </ul>
 */
@SuppressWarnings({"null", "removal"}) // Minecraft API lacks null annotations, Bus.MOD is deprecated but functional
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class VFXShaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(VFXShaderRegistry.class);

    private static final ShaderRenderTypeConfig IMPACT_CONFIG = new ShaderRenderTypeConfig(
        "devmod_impact_vfx",
        "devmod_impact_vfx_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderRenderTypeConfig HEATMAP_CONFIG = new ShaderRenderTypeConfig(
        "devmod_heatmap",
        "devmod_heatmap_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        4096,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderRenderTypeConfig PATHFINDING_CONFIG = new ShaderRenderTypeConfig(
        "devmod_pathfinding",
        "devmod_pathfinding_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderRenderTypeConfig WEAPON_TRAIL_CONFIG = new ShaderRenderTypeConfig(
        "devmod_weapon_trail",
        "devmod_weapon_trail_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.ADDITIVE_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderRenderTypeConfig WEAPON_TRAIL_DARK_CONFIG = new ShaderRenderTypeConfig(
        "devmod_weapon_trail_dark",
        "devmod_weapon_trail_dark_fallback",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES,
        2048,
        RenderStateShard.TRANSLUCENT_TRANSPARENCY,
        false,
        true
    );

    private static final ShaderPipeline IMPACT_PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "impact_vfx"),
        IMPACT_CONFIG
    );

    private static final ShaderPipeline HEATMAP_PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "heatmap"),
        HEATMAP_CONFIG
    );

    private static final ShaderPipeline PATHFINDING_PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "pathfinding"),
        PATHFINDING_CONFIG
    );

    private static final ShaderPipeline WEAPON_TRAIL_PIPELINE = new ShaderPipeline(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "weapon_trail"),
        WEAPON_TRAIL_CONFIG
    );

    @Nullable
    private static RenderType weaponTrailDarkRenderType;

    /**
     * Registers all VFX shaders during RegisterShadersEvent.
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("[VFX] Registering VFX shaders...");
        registerPipeline(IMPACT_PIPELINE, event);
        registerPipeline(HEATMAP_PIPELINE, event);
        registerPipeline(PATHFINDING_PIPELINE, event);
        registerPipeline(WEAPON_TRAIL_PIPELINE, event);
        refreshWeaponTrailDarkRenderType();
        ShaderPipelineDiagnostics.logStatuses("VFX",
            LOGGER,
            IMPACT_PIPELINE,
            HEATMAP_PIPELINE,
            PATHFINDING_PIPELINE,
            WEAPON_TRAIL_PIPELINE
        );
    }

    // ==================== Public API ====================
    private static void registerPipeline(ShaderPipeline pipeline, RegisterShadersEvent event) {
        pipeline.register(event, LOGGER);
    }

    private static void refreshWeaponTrailDarkRenderType() {
        weaponTrailDarkRenderType = WEAPON_TRAIL_PIPELINE.buildRenderType(WEAPON_TRAIL_DARK_CONFIG, true);
    }

    // ==================== Impact VFX ====================
    @Nullable
    public static RenderType getImpactVfxRenderType() {
        return IMPACT_PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getImpactVfxShader() {
        return IMPACT_PIPELINE.shader();
    }

    public static boolean isImpactShaderReady() {
        return IMPACT_PIPELINE.isReady();
    }

    // ==================== Heatmap (Phase 3) ====================
    @Nullable
    public static RenderType getHeatmapRenderType() {
        return HEATMAP_PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getHeatmapShader() {
        return HEATMAP_PIPELINE.shader();
    }

    public static boolean isHeatmapShaderReady() {
        return HEATMAP_PIPELINE.isReady();
    }

    // ==================== Pathfinding (Phase 4) ====================
    @Nullable
    public static RenderType getPathfindingRenderType() {
        return PATHFINDING_PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getPathfindingShader() {
        return PATHFINDING_PIPELINE.shader();
    }

    public static boolean isPathfindingShaderReady() {
        return PATHFINDING_PIPELINE.isReady();
    }

    // ==================== Weapon Trail ====================
    @Nullable
    public static RenderType getWeaponTrailRenderType() {
        return WEAPON_TRAIL_PIPELINE.renderType();
    }

    /**
     * Gets the dark rendering mode RenderType (TRANSLUCENT blend instead of ADDITIVE).
     * Uses fallback format/shader if the custom shader is unavailable.
     */
    @Nullable
    public static RenderType getWeaponTrailDarkRenderType() {
        if (weaponTrailDarkRenderType == null) {
            refreshWeaponTrailDarkRenderType();
        }
        return weaponTrailDarkRenderType != null ? weaponTrailDarkRenderType : WEAPON_TRAIL_PIPELINE.renderType();
    }

    @Nullable
    public static ShaderInstance getWeaponTrailShader() {
        return WEAPON_TRAIL_PIPELINE.shader();
    }

    public static boolean isWeaponTrailShaderReady() {
        return WEAPON_TRAIL_PIPELINE.isReady();
    }
}

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
import java.io.IOException;

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

    // === Impact VFX Shader ===
    @Nullable
    private static ShaderInstance impactVfxShader;
    private static RenderType impactVfxRenderType;
    private static boolean impactVfxInitialized = false;

    // === Heatmap Shader (Phase 3) ===
    @Nullable
    private static ShaderInstance heatmapShader;
    private static RenderType heatmapRenderType;
    private static boolean heatmapInitialized = false;

    // === Pathfinding Shader (Phase 4) ===
    @Nullable
    private static ShaderInstance pathfindingShader;
    private static RenderType pathfindingRenderType;
    private static boolean pathfindingInitialized = false;

    // === Weapon Trail Shader ===
    @Nullable
    private static ShaderInstance weaponTrailShader;
    private static RenderType weaponTrailRenderType;
    private static RenderType weaponTrailDarkRenderType;  // Dark rendering mode (like EpicFightSwordLight)
    private static boolean weaponTrailInitialized = false;

    /**
     * Registers all VFX shaders during RegisterShadersEvent.
     */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        LOGGER.info("[VFX] Registering VFX shaders...");

        // Register Impact VFX shader
        registerImpactVfxShader(event);

        // Register Heatmap shader
        registerHeatmapShader(event);

        // Register Pathfinding shader
        registerPathfindingShader(event);

        // Register Weapon Trail shader
        registerWeaponTrailShader(event);
    }

    // ==================== Impact VFX ====================

    private static void registerImpactVfxShader(RegisterShadersEvent event) {
        try {
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "impact_vfx"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    impactVfxShader = shader;
                    LOGGER.info("[VFX] Impact VFX shader registered successfully!");
                    initializeImpactVfxRenderType();
                }
            );

        } catch (IOException e) {
            LOGGER.error("[VFX] Failed to register impact_vfx shader!", e);
            initializeImpactVfxFallback();
        }
    }

    private static void initializeImpactVfxRenderType() {
        if (impactVfxShader == null) {
            LOGGER.warn("[VFX] Impact shader is null, using fallback");
            initializeImpactVfxFallback();
            return;
        }

        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
            () -> impactVfxShader
        );

        // LINE_STRIP for spirals, rings, rays
        impactVfxRenderType = RenderType.create(
            "devmod_impact_vfx",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES, // Triangles for filled geometry
            2048, // Buffer size
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

        impactVfxInitialized = true;
        LOGGER.info("[VFX] Impact VFX RenderType created successfully");
    }

    private static void initializeImpactVfxFallback() {
        impactVfxRenderType = RenderType.create(
            "devmod_impact_vfx_fallback",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            2048,
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

        impactVfxInitialized = true;
        LOGGER.info("[VFX] Impact VFX fallback RenderType created");
    }

    // ==================== Heatmap ====================

    private static void registerHeatmapShader(RegisterShadersEvent event) {
        try {
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "heatmap"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    heatmapShader = shader;
                    LOGGER.info("[VFX] Heatmap shader registered successfully!");
                    initializeHeatmapRenderType();
                }
            );

        } catch (IOException e) {
            LOGGER.error("[VFX] Failed to register heatmap shader!", e);
            initializeHeatmapFallback();
        }
    }

    private static void initializeHeatmapRenderType() {
        if (heatmapShader == null) {
            LOGGER.warn("[VFX] Heatmap shader is null, using fallback");
            initializeHeatmapFallback();
            return;
        }

        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
            () -> heatmapShader
        );

        // QUADS for block surfaces
        heatmapRenderType = RenderType.create(
            "devmod_heatmap",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            4096, // Larger buffer for many blocks
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(shaderState)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        heatmapInitialized = true;
        LOGGER.info("[VFX] Heatmap RenderType created successfully");
    }

    private static void initializeHeatmapFallback() {
        heatmapRenderType = RenderType.create(
            "devmod_heatmap_fallback",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
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

        heatmapInitialized = true;
        LOGGER.info("[VFX] Heatmap fallback RenderType created");
    }

    // ==================== Pathfinding ====================

    private static void registerPathfindingShader(RegisterShadersEvent event) {
        try {
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "pathfinding"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    pathfindingShader = shader;
                    LOGGER.info("[VFX] Pathfinding shader registered successfully!");
                    initializePathfindingRenderType();
                }
            );

        } catch (IOException e) {
            LOGGER.error("[VFX] Failed to register pathfinding shader!", e);
            initializePathfindingFallback();
        }
    }

    private static void initializePathfindingRenderType() {
        if (pathfindingShader == null) {
            LOGGER.warn("[VFX] Pathfinding shader is null, using fallback");
            initializePathfindingFallback();
            return;
        }

        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
            () -> pathfindingShader
        );

        // LINES for path segments
        pathfindingRenderType = RenderType.create(
            "devmod_pathfinding",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(shaderState)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        pathfindingInitialized = true;
        LOGGER.info("[VFX] Pathfinding RenderType created successfully");
    }

    private static void initializePathfindingFallback() {
        pathfindingRenderType = RenderType.create(
            "devmod_pathfinding_fallback",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            2048,
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

        pathfindingInitialized = true;
        LOGGER.info("[VFX] Pathfinding fallback RenderType created");
    }

    // ==================== Public API ====================

    /**
     * Gets the Impact VFX RenderType.
     */
    @Nullable
    public static RenderType getImpactVfxRenderType() {
        return impactVfxRenderType;
    }

    /**
     * Gets the Impact VFX shader instance for uniform access.
     */
    @Nullable
    public static ShaderInstance getImpactVfxShader() {
        return impactVfxShader;
    }

    /**
     * Checks if the Impact VFX shader is ready (custom, not fallback).
     */
    public static boolean isImpactShaderReady() {
        return impactVfxInitialized && impactVfxShader != null;
    }

    // ==================== Heatmap (Phase 3) ====================

    @Nullable
    public static RenderType getHeatmapRenderType() {
        return heatmapRenderType;
    }

    @Nullable
    public static ShaderInstance getHeatmapShader() {
        return heatmapShader;
    }

    public static boolean isHeatmapShaderReady() {
        return heatmapInitialized && heatmapShader != null;
    }

    // ==================== Pathfinding (Phase 4) ====================

    @Nullable
    public static RenderType getPathfindingRenderType() {
        return pathfindingRenderType;
    }

    @Nullable
    public static ShaderInstance getPathfindingShader() {
        return pathfindingShader;
    }

    public static boolean isPathfindingShaderReady() {
        return pathfindingInitialized && pathfindingShader != null;
    }

    // ==================== Weapon Trail ====================

    private static void registerWeaponTrailShader(RegisterShadersEvent event) {
        try {
            ResourceLocation shaderLocation = ResourceLocation.fromNamespaceAndPath(
                DevMod.MODID, "weapon_trail"
            );

            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    shaderLocation,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> {
                    weaponTrailShader = shader;
                    LOGGER.info("[VFX] Weapon Trail shader registered successfully!");
                    initializeWeaponTrailRenderType();
                }
            );

        } catch (IOException e) {
            LOGGER.error("[VFX] Failed to register weapon_trail shader!", e);
            initializeWeaponTrailFallback();
        }
    }

    private static void initializeWeaponTrailRenderType() {
        if (weaponTrailShader == null) {
            LOGGER.warn("[VFX] Weapon Trail shader is null, using fallback");
            initializeWeaponTrailFallback();
            return;
        }

        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(
            () -> weaponTrailShader
        );

        // TRIANGLES for ribbon trail geometry
        // Additive blend for glow effect (srcAlpha + one)
        weaponTrailRenderType = RenderType.create(
            "devmod_weapon_trail",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(shaderState)
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        // Dark rendering mode - uses TRANSLUCENT instead of ADDITIVE for better visibility in dark scenes
        // This matches EpicFightSwordLight's DARK_RENDERING option
        weaponTrailDarkRenderType = RenderType.create(
            "devmod_weapon_trail_dark",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(shaderState)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        weaponTrailInitialized = true;
        LOGGER.info("[VFX] Weapon Trail RenderType (normal + dark) created successfully");
    }

    private static void initializeWeaponTrailFallback() {
        weaponTrailRenderType = RenderType.create(
            "devmod_weapon_trail_fallback",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(false)
        );

        weaponTrailInitialized = true;
        LOGGER.info("[VFX] Weapon Trail fallback RenderType created");
    }

    @Nullable
    public static RenderType getWeaponTrailRenderType() {
        return weaponTrailRenderType;
    }

    /**
     * Gets the dark rendering mode RenderType (TRANSLUCENT blend instead of ADDITIVE).
     * Use this for better visibility in dark scenes - matches EpicFightSwordLight's DARK_RENDERING.
     */
    @Nullable
    public static RenderType getWeaponTrailDarkRenderType() {
        return weaponTrailDarkRenderType != null ? weaponTrailDarkRenderType : weaponTrailRenderType;
    }

    @Nullable
    public static ShaderInstance getWeaponTrailShader() {
        return weaponTrailShader;
    }

    public static boolean isWeaponTrailShaderReady() {
        return weaponTrailInitialized && weaponTrailShader != null;
    }
}

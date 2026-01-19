package com.devmod.foundry.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import com.devmod.DevMod;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

/**
 * Loads material render info from JSON files in assets/<modid>/foundry/materials/.
 * Also provides fallback to material definitions if no render info JSON exists.
 */
public class FoundryMaterialRenderInfoLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FoundryMaterialRenderInfoLoader.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "foundry/materials";

    public static final FoundryMaterialRenderInfoLoader INSTANCE = new FoundryMaterialRenderInfoLoader();

    private final Map<ResourceLocation, FoundryMaterialRenderInfo> renderInfoMap = new ConcurrentHashMap<>();

    private FoundryMaterialRenderInfoLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        renderInfoMap.clear();
        LOGGER.info("[FoundryMaterialRenderInfoLoader] Loading material render info...");

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "material_render_info");
                FoundryMaterialRenderInfo info = parseRenderInfo(id, json);
                renderInfoMap.put(id, info);
                LOGGER.debug("[FoundryMaterialRenderInfoLoader] Loaded render info for: {}", id);
            } catch (Exception e) {
                LOGGER.error("[FoundryMaterialRenderInfoLoader] Failed to load render info for: {}", id, e);
            }
        }

        LOGGER.info("[FoundryMaterialRenderInfoLoader] Loaded {} material render info entries", renderInfoMap.size());
    }

    private FoundryMaterialRenderInfo parseRenderInfo(ResourceLocation id, JsonObject json) {
        // Parse texture override
        ResourceLocation texture = null;
        if (json.has("texture")) {
            texture = ResourceLocation.parse(GsonHelper.getAsString(json, "texture"));
        }

        // Parse fallbacks
        List<String> fallbacks = new ArrayList<>();
        if (json.has("fallbacks")) {
            JsonArray fallbacksArray = GsonHelper.getAsJsonArray(json, "fallbacks");
            for (JsonElement element : fallbacksArray) {
                fallbacks.add(element.getAsString());
            }
        }

        // Parse color (hex string or integer)
        int color = 0xFFFFFFFF;
        if (json.has("color")) {
            JsonElement colorElement = json.get("color");
            if (colorElement.isJsonPrimitive()) {
                if (colorElement.getAsJsonPrimitive().isString()) {
                    String colorStr = colorElement.getAsString();
                    if (colorStr.startsWith("#")) {
                        colorStr = colorStr.substring(1);
                    }
                    color = (int) Long.parseLong(colorStr, 16);
                    // Ensure alpha is set if not specified
                    if ((color & 0xFF000000) == 0) {
                        color |= 0xFF000000;
                    }
                } else {
                    color = colorElement.getAsInt();
                }
            }
        }

        // Parse luminosity (0-15)
        int luminosity = GsonHelper.getAsInt(json, "luminosity", 0);

        return new FoundryMaterialRenderInfo(id, texture, List.copyOf(fallbacks), color, luminosity);
    }

    /**
     * Get render info for a material, falling back to material definition if not found.
     */
    @Nullable
    public FoundryMaterialRenderInfo getRenderInfo(ResourceLocation materialId) {
        // First check loaded render info
        FoundryMaterialRenderInfo info = renderInfoMap.get(materialId);
        if (info != null) {
            return info;
        }

        // Fall back to material definition color
        FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
        if (material != null) {
            return createFromMaterialDefinition(material);
        }

        return null;
    }

    /**
     * Create render info from a material definition (fallback).
     */
    private FoundryMaterialRenderInfo createFromMaterialDefinition(FoundryMaterialDefinition material) {
        // Use material color with full alpha
        int color = 0xFF000000 | material.color();

        // Determine fallbacks based on material tier
        List<String> fallbacks = determineFallbacks(material);

        return new FoundryMaterialRenderInfo(
            material.id(),
            null,
            fallbacks,
            color,
            0
        );
    }

    /**
     * Determine texture fallbacks based on material tier.
     */
    private List<String> determineFallbacks(FoundryMaterialDefinition material) {
        int tier = material.tier();
        List<String> fallbacks = new ArrayList<>();

        // Add tier-based fallbacks
        if (tier <= 1) {
            fallbacks.add("wood");
            fallbacks.add("primitive");
        } else if (tier == 2) {
            fallbacks.add("stone");
            fallbacks.add("metal");
        } else if (tier == 3) {
            fallbacks.add("iron");
            fallbacks.add("metal");
        } else {
            fallbacks.add("diamond");
            fallbacks.add("gem");
            fallbacks.add("metal");
        }

        return List.copyOf(fallbacks);
    }

    /**
     * Get all loaded render info entries.
     */
    public Map<ResourceLocation, FoundryMaterialRenderInfo> getAllRenderInfo() {
        return Map.copyOf(renderInfoMap);
    }

    /**
     * Check if render info exists for a material.
     */
    public boolean hasRenderInfo(ResourceLocation materialId) {
        return renderInfoMap.containsKey(materialId) || FoundryMaterialRegistry.get(materialId) != null;
    }
}

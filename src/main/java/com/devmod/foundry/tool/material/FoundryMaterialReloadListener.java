package com.devmod.foundry.tool.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import net.minecraft.world.item.crafting.Ingredient;

import com.devmod.DevMod;

/**
 * Loads material definitions from datapack JSON.
 */
public class FoundryMaterialReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public FoundryMaterialReloadListener() {
        super(GSON, "foundry/materials");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        FoundryMaterialRegistry.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonObject root = entry.getValue().getAsJsonObject();

            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(root, "ingredient"));
            String colorRaw = GsonHelper.getAsString(root, "color", "FFFFFF");
            int color = parseColor(colorRaw);

            Map<String, FoundryMaterialStats> stats = new LinkedHashMap<>();
            JsonObject statsObj = GsonHelper.getAsJsonObject(root, "stats", new JsonObject());
            for (Map.Entry<String, JsonElement> statEntry : statsObj.entrySet()) {
                if (statEntry.getValue().isJsonObject()) {
                    stats.put(statEntry.getKey(), FoundryMaterialStats.fromJson(statEntry.getValue().getAsJsonObject()));
                }
            }

            List<ResourceLocation> traits = new ArrayList<>();
            JsonArray traitArray = GsonHelper.getAsJsonArray(root, "traits", new JsonArray());
            for (JsonElement traitElement : traitArray) {
                ResourceLocation traitId = ResourceLocation.tryParse(traitElement.getAsString());
                if (traitId != null) {
                    traits.add(traitId);
                }
            }

            FoundryMaterialDefinition definition = new FoundryMaterialDefinition(id, ingredient, color, stats, List.copyOf(traits));
            FoundryMaterialRegistry.register(definition);
        }
        DevMod.LOGGER.info("[Foundry] Loaded {} materials", FoundryMaterialRegistry.all().size());
    }

    private static int parseColor(String value) {
        String raw = value.startsWith("#") ? value.substring(1) : value;
        try {
            return (int) Long.parseLong(raw, 16);
        } catch (NumberFormatException ex) {
            return 0xFFFFFF;
        }
    }
}

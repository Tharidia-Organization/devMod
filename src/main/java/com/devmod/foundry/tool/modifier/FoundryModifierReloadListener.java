package com.devmod.foundry.tool.modifier;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Loads modifier definitions from datapack JSON.
 */
public class FoundryModifierReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public FoundryModifierReloadListener() {
        super(GSON, "foundry/modifiers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        FoundryModifierRegistry.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonObject root = entry.getValue().getAsJsonObject();

            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(root, "ingredient"));
            int maxLevel = GsonHelper.getAsInt(root, "max_level", 1);
            FoundryModifierStats bonuses = FoundryModifierStats.fromJson(GsonHelper.getAsJsonObject(root, "bonuses", new JsonObject()));

            FoundryModifierDefinition definition = new FoundryModifierDefinition(id, ingredient, maxLevel, bonuses);
            FoundryModifierRegistry.register(definition);
        }
        DevMod.LOGGER.info("[Foundry] Loaded {} modifiers", FoundryModifierRegistry.all().size());
    }
}

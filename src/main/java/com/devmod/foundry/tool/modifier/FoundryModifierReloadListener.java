package com.devmod.foundry.tool.modifier;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

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

            Ingredient ingredient = Ingredient.EMPTY;
            if (root.has("ingredient")) {
                JsonElement ingredientElement = root.get("ingredient");
                ingredient = Ingredient.CODEC.parse(JsonOps.INSTANCE, ingredientElement)
                    .resultOrPartial(message -> DevMod.LOGGER.warn("[Foundry] Invalid modifier ingredient {}: {}", id, message))
                    .orElse(Ingredient.EMPTY);
            }
            int maxLevel = GsonHelper.getAsInt(root, "max_level", 1);
            String slotRaw = GsonHelper.getAsString(root, "slot_type", "upgrade");
            FoundryModifierSlot slotType = FoundryModifierSlot.fromString(slotRaw);
            int slots = GsonHelper.getAsInt(root, "slots", 1);
            FoundryModifierStats bonuses = FoundryModifierStats.fromJson(GsonHelper.getAsJsonObject(root, "bonuses", new JsonObject()));

            ResourceLocation specialization = null;
            if (root.has("specialization")) {
                String specRaw = GsonHelper.getAsString(root, "specialization");
                specialization = ResourceLocation.tryParse(specRaw);
                if (specialization == null) {
                    DevMod.LOGGER.warn("[Foundry] Invalid modifier specialization {}: {}", id, specRaw);
                }
            }

            FoundryModifierDefinition definition = new FoundryModifierDefinition(id, ingredient, maxLevel, slotType, slots, bonuses, specialization);
            FoundryModifierRegistry.register(definition);
        }
        DevMod.LOGGER.info("[Foundry] Loaded {} modifiers", FoundryModifierRegistry.all().size());
    }
}

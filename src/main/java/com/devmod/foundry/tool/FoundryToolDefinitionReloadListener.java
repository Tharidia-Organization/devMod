package com.devmod.foundry.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

/**
 * Loads tool definitions from datapack JSON.
 */
public class FoundryToolDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public FoundryToolDefinitionReloadListener() {
        super(GSON, "foundry/tools");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        FoundryToolDefinitionRegistry.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonObject root = entry.getValue().getAsJsonObject();

            String kindRaw = GsonHelper.getAsString(root, "kind", "pickaxe").toUpperCase(java.util.Locale.ROOT);
            FoundryToolKind kind;
            try {
                kind = FoundryToolKind.valueOf(kindRaw);
            } catch (IllegalArgumentException ex) {
                DevMod.LOGGER.warn("[Foundry] Unknown tool kind {} for {}", kindRaw, id);
                continue;
            }

            String itemRaw = GsonHelper.getAsString(root, "item", id.toString());
            ResourceLocation itemId = ResourceLocation.tryParse(itemRaw);
            if (itemId == null) {
                DevMod.LOGGER.warn("[Foundry] Invalid tool item {} for {}", itemRaw, id);
                continue;
            }

            List<FoundryPartType> parts = new ArrayList<>();
            JsonArray partArray = GsonHelper.getAsJsonArray(root, "parts", new JsonArray());
            for (JsonElement element : partArray) {
                ResourceLocation partId = ResourceLocation.tryParse(element.getAsString());
                if (partId == null) {
                    continue;
                }
                FoundryPartType type = FoundryPartTypes.all().get(partId);
                if (type != null) {
                    parts.add(type);
                }
            }

            FoundryToolStats baseStats = FoundryToolStats.fromJson(GsonHelper.getAsJsonObject(root, "base_stats", new JsonObject()));
            int baseUpgrades = GsonHelper.getAsInt(root, "base_upgrades", 3);
            int baseAbilities = GsonHelper.getAsInt(root, "base_abilities", 1);
            int priority = GsonHelper.getAsInt(root, "priority", 0);

            if (parts.isEmpty()) {
                DevMod.LOGGER.warn("[Foundry] Tool {} has no parts defined", id);
                continue;
            }

            FoundryToolDefinition definition = new FoundryToolDefinition(
                id,
                Objects.requireNonNull(itemId),
                kind,
                List.copyOf(parts),
                baseStats,
                baseUpgrades,
                baseAbilities,
                priority
            );
            FoundryToolDefinitionRegistry.register(definition);
        }
        DevMod.LOGGER.info("[Foundry] Loaded {} tool definitions", FoundryToolDefinitionRegistry.all().size());
    }
}

package com.devmod.foundry.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Minimal JSON helpers used by Foundry model utilities.
 */
public final class FoundryJsonHelper {
    private FoundryJsonHelper() {}

    public static ResourceLocation getResourceLocation(JsonObject json, String key) {
        String text = GsonHelper.getAsString(json, key);
        ResourceLocation location = ResourceLocation.tryParse(text);
        if (location == null) {
            throw new JsonSyntaxException("Expected " + key + " to be a resource location, was '" + text + "'");
        }
        return location;
    }

    public static <T> List<T> parseList(JsonArray array, String name, BiFunction<JsonElement, String, T> mapper) {
        if (array.isEmpty()) {
            throw new JsonSyntaxException(name + " must have at least 1 element");
        }
        List<T> builder = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            builder.add(mapper.apply(array.get(i), name + "[" + i + "]"));
        }
        return List.copyOf(builder);
    }

    public static <T> List<T> parseList(JsonObject parent, String name, BiFunction<JsonElement, String, T> mapper) {
        return parseList(GsonHelper.getAsJsonArray(parent, name), name, mapper);
    }

    public static <T> List<T> parseList(JsonArray array, String name, Function<JsonObject, T> mapper) {
        return parseList(array, name, (element, key) -> mapper.apply(GsonHelper.convertToJsonObject(element, key)));
    }

    public static <T> List<T> parseList(JsonObject parent, String name, Function<JsonObject, T> mapper) {
        return parseList(GsonHelper.getAsJsonArray(parent, name), name, mapper);
    }
}

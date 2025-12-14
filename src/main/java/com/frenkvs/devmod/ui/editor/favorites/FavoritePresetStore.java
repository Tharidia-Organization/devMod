package com.frenkvs.devmod.ui.editor.favorites;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.util.ConfigPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight persistence for favorite presets per itemType.
 */
public final class FavoritePresetStore {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>(){}.getType();
    private final Map<String, List<String>> favorites = new LinkedHashMap<>();
    private boolean initialized = false;

    private Path file() {
        return ConfigPaths.getItemEditorDir().resolve("favorite_presets.json");
    }

    private void ensureInit() {
        if (initialized) return;
        initialized = true;
        load();
    }

    public List<String> getFavorites(String itemType) {
        ensureInit();
        return Collections.unmodifiableList(favorites.getOrDefault(itemType, List.of()));
    }

    public boolean isFavorite(String itemType, String presetName) {
        ensureInit();
        return favorites.getOrDefault(itemType, List.of()).contains(presetName);
    }

    public void toggleFavorite(String itemType, String presetName) {
        ensureInit();
        favorites.computeIfAbsent(Objects.requireNonNull(itemType, "itemType"), k -> new ArrayList<>());
        List<String> list = favorites.get(itemType);
        if (list.contains(presetName)) {
            list.remove(presetName);
        } else {
            list.add(0, presetName);
        }
        save();
    }

    private void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            String json = Files.readString(f, StandardCharsets.UTF_8);
            Map<String, List<String>> map = GSON.fromJson(json, MAP_TYPE);
            if (map != null) {
                favorites.clear();
                favorites.putAll(map);
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Editor] Failed to load favorite presets: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(favorites), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.warn("[Editor] Failed to save favorite presets: {}", e.getMessage());
        }
    }
}

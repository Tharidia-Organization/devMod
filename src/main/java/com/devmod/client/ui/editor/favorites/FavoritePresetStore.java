package com.devmod.ui.editor.favorites;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.devmod.DevMod;
import com.devmod.util.ConfigPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight persistence for favorite presets per itemType.
 */
public final class FavoritePresetStore {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>(){}.getType();
    private final Map<String, LinkedHashSet<String>> favorites = new LinkedHashMap<>();
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
        var set = favorites.get(itemType);
        if (set == null || set.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(set));
    }

    public boolean isFavorite(String itemType, String presetName) {
        ensureInit();
        var set = favorites.get(itemType);
        return set != null && set.contains(presetName);
    }

    public void toggleFavorite(String itemType, String presetName) {
        ensureInit();
        Objects.requireNonNull(presetName, "presetName");
        String safeType = Objects.requireNonNull(itemType, "itemType");
        LinkedHashSet<String> current = favorites.get(safeType);
        if (current == null) {
            current = new LinkedHashSet<>();
        }

        if (current.contains(presetName)) {
            current.remove(presetName);
        } else {
            LinkedHashSet<String> reordered = new LinkedHashSet<>();
            reordered.add(presetName); // keep newest favorite at the front
            reordered.addAll(current);
            current = reordered;
        }
        favorites.put(safeType, current);
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
                map.forEach((type, list) -> {
                    if (type == null || list == null) return;
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    for (String name : list) {
                        if (name != null && !name.isBlank()) {
                            set.add(name);
                        }
                    }
                    if (!set.isEmpty()) {
                        favorites.put(type, set);
                    }
                });
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Editor] Failed to load favorite presets: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file().getParent());
            Map<String, List<String>> serializable = new LinkedHashMap<>();
            favorites.forEach((type, set) -> {
                if (type != null && set != null) {
                    serializable.put(type, new ArrayList<>(set));
                }
            });
            Files.writeString(file(), GSON.toJson(serializable), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DevMod.LOGGER.warn("[Editor] Failed to save favorite presets: {}", e.getMessage());
        }
    }
}

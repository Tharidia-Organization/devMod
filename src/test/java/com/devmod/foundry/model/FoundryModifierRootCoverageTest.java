package com.devmod.foundry.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryModifierRootCoverageTest {

    private static final Path ASSETS_ROOT = Paths.get("src/main/resources/assets");

    @Test
    @DisplayName("Modifier roots resolve to texture directories")
    void modifierRootsResolveToTextures() throws IOException {
        if (!Files.exists(ASSETS_ROOT)) {
            System.out.println("Assets root not found, skipping test: " + ASSETS_ROOT);
            return;
        }

        Set<String> roots = new HashSet<>();
        Files.walk(ASSETS_ROOT)
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> collectModifierRoots(path, roots));

        List<String> missing = new ArrayList<>();
        List<String> empty = new ArrayList<>();

        for (String root : roots) {
            Path textureDir = resolveTextureDir(root);
            if (textureDir == null || !Files.exists(textureDir)) {
                missing.add(root);
                continue;
            }
            if (!containsPng(textureDir)) {
                empty.add(root);
            }
        }

        String message = buildMessage(missing, empty);
        assertTrue(missing.isEmpty() && empty.isEmpty(), message);
    }

    private static void collectModifierRoots(Path path, Set<String> roots) {
        JsonObject json;
        try {
            json = JsonParser.parseString(readFile(path)).getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        if (!json.has("modifier_roots")) {
            return;
        }

        JsonElement element = json.get("modifier_roots");
        if (element.isJsonArray()) {
            addRootsFromArray(element.getAsJsonArray(), roots);
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : obj.keySet()) {
                JsonElement value = obj.get(key);
                if (value.isJsonArray()) {
                    addRootsFromArray(value.getAsJsonArray(), roots);
                }
            }
        }
    }

    private static void addRootsFromArray(JsonArray array, Set<String> roots) {
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (entry.isJsonPrimitive()) {
                roots.add(entry.getAsString());
            }
        }
    }

    private static Path resolveTextureDir(String resource) {
        String[] parts = resource.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        String namespace = parts[0];
        String path = parts[1];
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return ASSETS_ROOT.resolve(namespace).resolve("textures").resolve(path);
    }

    private static boolean containsPng(Path dir) {
        try {
            return Files.walk(dir).anyMatch(path -> path.toString().endsWith(".png"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String buildMessage(List<String> missing, List<String> empty) {
        StringBuilder builder = new StringBuilder("Modifier roots missing texture directories or files.");
        builder.append("\nSee FOUNDRY_MODIFIER_ROOTS_REPORT.md for the full audit.");
        if (!missing.isEmpty()) {
            builder.append("\nMissing roots (no directory):");
            for (String root : missing) {
                builder.append("\n- ").append(root);
            }
        }
        if (!empty.isEmpty()) {
            builder.append("\nEmpty roots (no .png files):");
            for (String root : empty) {
                builder.append("\n- ").append(root);
            }
        }
        return builder.toString();
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}

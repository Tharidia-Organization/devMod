package com.devmod.foundry.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryToolModelSchemaTest {

    private static final Path ASSETS_ROOT = Paths.get("src/main/resources/assets");

    @Test
    @DisplayName("Foundry tool models declare textures for each part")
    void toolModelsHaveTexturesForParts() throws IOException {
        if (!Files.exists(ASSETS_ROOT)) {
            System.out.println("Assets root not found, skipping test: " + ASSETS_ROOT);
            return;
        }

        List<String> failures = new ArrayList<>();
        Files.walk(ASSETS_ROOT)
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> validateToolModel(path, failures));

        assertTrue(failures.isEmpty(), "Foundry tool model schema issues:\n" + String.join("\n", failures));
    }

    private static void validateToolModel(Path path, List<String> failures) {
        JsonObject json;
        try {
            json = JsonParser.parseString(readFile(path)).getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        String loader = json.has("loader") ? json.get("loader").getAsString() : "";
        if (!"devmod:foundry_tool".equals(loader) && !"tconstruct:tool".equals(loader)) {
            return;
        }

        if (!json.has("textures") || !json.get("textures").isJsonObject()) {
            failures.add(path + ": missing textures object");
            return;
        }

        JsonObject textures = json.getAsJsonObject("textures");
        List<String> partNames = parsePartNames(json, path, failures);

        for (String part : partNames) {
            if (!textures.has(part)) {
                failures.add(path + ": missing texture key '" + part + "'");
            }
        }

        boolean isLarge = json.has("large") && json.get("large").getAsBoolean();
        if (isLarge) {
            for (String part : partNames) {
                String largeKey = "large_" + part;
                if (!textures.has(largeKey)) {
                    failures.add(path + ": missing texture key '" + largeKey + "'");
                }
            }
        }
    }

    private static List<String> parsePartNames(JsonObject json, Path path, List<String> failures) {
        if (!json.has("parts") || !json.get("parts").isJsonArray()) {
            return List.of("tool");
        }

        JsonArray parts = json.getAsJsonArray("parts");
        if (parts.isEmpty()) {
            return List.of("tool");
        }

        List<String> names = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            JsonElement element = parts.get(i);
            if (!element.isJsonObject()) {
                failures.add(path + ": parts[" + i + "] is not an object");
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            if (!part.has("name")) {
                failures.add(path + ": parts[" + i + "] missing name");
                continue;
            }
            names.add(part.get("name").getAsString());
        }
        if (names.isEmpty()) {
            return List.of("tool");
        }
        return List.copyOf(names);
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}

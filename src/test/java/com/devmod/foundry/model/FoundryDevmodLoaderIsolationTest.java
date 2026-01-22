package com.devmod.foundry.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryDevmodLoaderIsolationTest {

    private static final Path DEV_MOD_MODELS = Paths.get("src/main/resources/assets/devmod/models");

    @Test
    @DisplayName("DevMod models use only DevMod model loaders")
    void devmodModelsUseOnlyDevmodLoaders() throws IOException {
        if (!Files.exists(DEV_MOD_MODELS)) {
            System.out.println("DevMod models not found, skipping test: " + DEV_MOD_MODELS);
            return;
        }

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(DEV_MOD_MODELS)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> collectLegacyLoader(path, offenders));
        }

        assertTrue(offenders.isEmpty(), "Legacy model loaders found in DevMod models:\n" + String.join("\n", offenders));
    }

    private static void collectLegacyLoader(Path path, List<String> offenders) {
        JsonObject json;
        try {
            json = JsonParser.parseString(readFile(path)).getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        if (!json.has("loader")) {
            return;
        }

        String loader = json.get("loader").getAsString();
        int colonIndex = loader.indexOf(':');
        String namespace = colonIndex > 0 ? loader.substring(0, colonIndex) : "";
        if (!"devmod".equals(namespace)) {
            offenders.add(path + ": " + loader);
        }
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}

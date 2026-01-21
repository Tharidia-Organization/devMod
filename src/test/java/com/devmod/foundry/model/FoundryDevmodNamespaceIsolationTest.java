package com.devmod.foundry.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryDevmodNamespaceIsolationTest {

    private static final Path DEV_MOD_ASSETS = Paths.get("src/main/resources/assets/devmod");
    private static final Path DEV_MOD_DATA = Paths.get("src/main/resources/data/devmod");

    @Test
    @DisplayName("DevMod JSON resources avoid Mantle/TCon namespaces")
    void devmodJsonAvoidsLegacyNamespaces() throws IOException {
        List<String> offenders = new ArrayList<>();
        scanForLegacyNamespaces(DEV_MOD_ASSETS, offenders);
        scanForLegacyNamespaces(DEV_MOD_DATA, offenders);

        assertTrue(offenders.isEmpty(), "Legacy namespaces found in DevMod resources:\n" + String.join("\n", offenders));
    }

    private static void scanForLegacyNamespaces(Path root, List<String> offenders) throws IOException {
        if (!Files.exists(root)) {
            System.out.println("DevMod resources not found, skipping scan: " + root);
            return;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                .filter(path -> !path.toString().contains("/lang/"))
                .forEach(path -> checkForLegacyNamespaces(path, offenders));
        }
    }

    private static void checkForLegacyNamespaces(Path path, List<String> offenders) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return;
        }
        if (content.contains("tconstruct:") || content.contains("mantle:")) {
            offenders.add(path.toString());
        }
    }
}

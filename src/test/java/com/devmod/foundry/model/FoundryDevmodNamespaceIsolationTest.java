package com.devmod.foundry.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryDevmodNamespaceIsolationTest {

    private static final Path DEV_MOD_MODELS = Paths.get("src/main/resources/assets/devmod/models");
    private static final Set<String> ALLOWED_NAMESPACES = Set.of("devmod", "minecraft", "neoforge");
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("\"([a-z0-9_\\-]+):[^\"]+\"");

    @Test
    @DisplayName("DevMod model JSON uses only allowed namespaces")
    void devmodModelJsonUsesAllowedNamespaces() throws IOException {
        List<String> offenders = new ArrayList<>();
        scanForUnexpectedNamespaces(DEV_MOD_MODELS, offenders);

        assertTrue(offenders.isEmpty(), "Unexpected namespaces found in DevMod models:\n" + String.join("\n", offenders));
    }

    private static void scanForUnexpectedNamespaces(Path root, List<String> offenders) throws IOException {
        if (!Files.exists(root)) {
            System.out.println("DevMod resources not found, skipping scan: " + root);
            return;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> checkForUnexpectedNamespaces(path, offenders));
        }
    }

    private static void checkForUnexpectedNamespaces(Path path, List<String> offenders) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return;
        }
        Matcher matcher = NAMESPACE_PATTERN.matcher(content);
        while (matcher.find()) {
            String namespace = matcher.group(1);
            if (!ALLOWED_NAMESPACES.contains(namespace)) {
                offenders.add(path + ": " + namespace);
                break;
            }
        }
    }
}

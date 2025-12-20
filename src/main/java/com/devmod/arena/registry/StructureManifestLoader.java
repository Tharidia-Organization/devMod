package com.devmod.arena.registry;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loader for structure manifest JSON (spec v2.23).
 *
 * Defaults:
 * - allowedNamespaces: ["devmod"]
 * - maxFileSizeBytes: 512_000
 * - maxBlockCount: 100_000
 * - maxEntityCount: 50
 */
public class StructureManifestLoader {

    private static final Gson GSON = new Gson();
    private static final Set<String> DEFAULT_NAMESPACES = Set.of("devmod");
    private static final long DEFAULT_MAX_FILE_SIZE = 512_000;
    private static final int DEFAULT_MAX_BLOCK_COUNT = 100_000;
    private static final int DEFAULT_MAX_ENTITY_COUNT = 50;

    public StructureManifest load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return parse(reader);
        }
    }

    public StructureManifest parse(Reader reader) {
        ManifestJson json = GSON.fromJson(reader, ManifestJson.class);
        if (json == null) {
            throw new IllegalArgumentException("Manifest JSON is empty or invalid");
        }

        Set<String> namespaces = json.allowedNamespaces != null && !json.allowedNamespaces.isEmpty()
            ? Set.copyOf(json.allowedNamespaces)
            : DEFAULT_NAMESPACES;

        long maxSize = json.maxFileSizeBytes != null ? json.maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE;
        int maxBlocks = json.maxBlockCount != null ? json.maxBlockCount : DEFAULT_MAX_BLOCK_COUNT;
        int maxEntities = json.maxEntityCount != null ? json.maxEntityCount : DEFAULT_MAX_ENTITY_COUNT;

        Map<String, StructureManifest.Entry> structures = json.structures != null ? Map.copyOf(json.structures) : Map.of();

        return new StructureManifest(structures, namespaces, maxSize, maxBlocks, maxEntities);
    }

    private static class ManifestJson {
        Map<String, StructureManifest.Entry> structures;
        List<String> allowedNamespaces;
        Long maxFileSizeBytes;
        Integer maxBlockCount;
        Integer maxEntityCount;
    }
}

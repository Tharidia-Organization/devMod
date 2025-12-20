package com.devmod.arena.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses a StructureManifest from JSON, applying sane defaults per spec v2.23.
 */
public final class StructureManifestParser {

    /**
     * Parse a manifest from the given path.
     * Expected schema:
     * {
     *   "structures": { "devmod:structures/foo": { "sha256": "...", "sizeBytes": 123, "blockCount": 10, "entityCount": 0 } },
     *   "allowedNamespaces": ["devmod"],
     *   "maxFileSizeBytes": 524288,
     *   "maxBlockCount": 100000,
     *   "maxEntityCount": 50
     * }
     */
    public StructureManifest parse(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();

            Map<String, StructureManifest.Entry> entries = new HashMap<>();
            JsonObject structures = obj.has("structures") ? obj.getAsJsonObject("structures") : new JsonObject();
            for (String key : structures.keySet()) {
                JsonObject e = structures.getAsJsonObject(key);
                String sha = e.get("sha256").getAsString();
                long sizeBytes = e.get("sizeBytes").getAsLong();
                int blockCount = e.has("blockCount") ? e.get("blockCount").getAsInt() : 0;
                int entityCount = e.has("entityCount") ? e.get("entityCount").getAsInt() : 0;
                entries.put(key, new StructureManifest.Entry(sha, sizeBytes, blockCount, entityCount));
            }

            Set<String> allowedNamespaces = new HashSet<>();
            if (obj.has("allowedNamespaces") && obj.get("allowedNamespaces").isJsonArray()) {
                obj.getAsJsonArray("allowedNamespaces").forEach(el -> allowedNamespaces.add(el.getAsString()));
            }
            if (allowedNamespaces.isEmpty()) {
                allowedNamespaces.add("devmod");
            }

            long maxFileSizeBytes = obj.has("maxFileSizeBytes") ? obj.get("maxFileSizeBytes").getAsLong() : 512 * 1024;
            int maxBlockCount = obj.has("maxBlockCount") ? obj.get("maxBlockCount").getAsInt() : 100_000;
            int maxEntityCount = obj.has("maxEntityCount") ? obj.get("maxEntityCount").getAsInt() : 50;

            return new StructureManifest(entries, allowedNamespaces, maxFileSizeBytes, maxBlockCount, maxEntityCount);
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid manifest JSON: " + e.getMessage(), e);
        }
    }
}

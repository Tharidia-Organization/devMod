package com.devmod.arena.registry;

import java.util.Map;
import java.util.Set;

public record StructureManifest(
    Map<String, Entry> structures,
    Set<String> allowedNamespaces,
    long maxFileSizeBytes,
    int maxBlockCount,
    int maxEntityCount
) {
    public record Entry(String sha256, long sizeBytes, int blockCount, int entityCount) {}
}

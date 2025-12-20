package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructureManifestLoaderTest {

    @Test
    void parsesManifestWithDefaults() {
        String json = """
            {
              "structures": {
                "devmod:structures/test": {
                  "sha256": "abcd",
                  "sizeBytes": 10,
                  "blockCount": 1,
                  "entityCount": 0
                }
              }
            }
            """;

        StructureManifestLoader loader = new StructureManifestLoader();
        StructureManifest manifest = loader.parse(new StringReader(json));

        assertEquals(Set.of("devmod"), manifest.allowedNamespaces());
        assertEquals(512_000, manifest.maxFileSizeBytes());
        assertEquals(100_000, manifest.maxBlockCount());
        assertEquals(50, manifest.maxEntityCount());
        assertTrue(manifest.structures().containsKey("devmod:structures/test"));
    }

    @Test
    void parsesManifestWithOverrides() {
        String json = """
            {
              "structures": {
                "custom:boss": {
                  "sha256": "deadbeef",
                  "sizeBytes": 2048,
                  "blockCount": 5000,
                  "entityCount": 3
                }
              },
              "allowedNamespaces": ["custom"],
              "maxFileSizeBytes": 1024,
              "maxBlockCount": 6000,
              "maxEntityCount": 5
            }
            """;

        StructureManifestLoader loader = new StructureManifestLoader();
        StructureManifest manifest = loader.parse(new StringReader(json));

        assertEquals(Set.of("custom"), manifest.allowedNamespaces());
        assertEquals(1024, manifest.maxFileSizeBytes());
        assertEquals(6000, manifest.maxBlockCount());
        assertEquals(5, manifest.maxEntityCount());
        StructureManifest.Entry entry = manifest.structures().get("custom:boss");
        assertNotNull(entry);
        assertEquals("deadbeef", entry.sha256());
        assertEquals(2048, entry.sizeBytes());
        assertEquals(5000, entry.blockCount());
        assertEquals(3, entry.entityCount());
    }

    @Test
    void emptyJsonThrows() {
        StructureManifestLoader loader = new StructureManifestLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.parse(new StringReader("")));
    }
}


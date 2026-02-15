package com.devmod.arena.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TemplateManifestTest {

    // ---- Entry ----

    @Test
    void entryMatchesFullChecksum() {
        var entry = new TemplateManifest.Entry(1, "abc12345", "abc12345def67890");
        assertTrue(entry.matches("abc12345def67890"));
        assertFalse(entry.matches("abc12345000000"));
    }

    @Test
    void entryMatchesShortChecksum() {
        var entry = new TemplateManifest.Entry(1, "abc12345", null);
        assertTrue(entry.matches("abc12345def67890"));
        assertFalse(entry.matches("xyz12345def67890"));
    }

    @Test
    void entryDoesNotMatchNull() {
        var entry = new TemplateManifest.Entry(1, "abc", null);
        assertFalse(entry.matches(null));
    }

    // ---- Mode ----

    @Test
    void modeValues() {
        assertEquals(3, TemplateManifest.Mode.values().length);
        assertNotNull(TemplateManifest.Mode.WARN);
        assertNotNull(TemplateManifest.Mode.STRICT);
        assertNotNull(TemplateManifest.Mode.SKIP);
    }

    // ---- Empty Manifest ----

    @Test
    void emptyManifest() {
        TemplateManifest manifest = TemplateManifest.empty();
        assertEquals(TemplateManifest.Mode.SKIP, manifest.getMode());
        assertFalse(manifest.hasEntries());
        assertTrue(manifest.getEntry("any").isEmpty());
    }

    // ---- Add Entry ----

    @Test
    void addAndGetEntry() {
        TemplateManifest manifest = TemplateManifest.empty();
        var entry = new TemplateManifest.Entry(1, "abc", "abcdef");
        manifest.addEntry("template1", entry);
        assertTrue(manifest.getEntry("template1").isPresent());
        assertEquals(entry, manifest.getEntry("template1").get());
    }

    // ---- ParseJson ----

    @Test
    void parseValidJson() {
        String json = """
            {
              "validationMode": "STRICT",
              "entries": {
                "arena_small": {
                  "version": 2,
                  "checksum": "abc12345",
                  "checksumFull": "abc12345def67890"
                }
              }
            }
            """;
        TemplateManifest manifest = TemplateManifest.parseJson(json);
        assertEquals(TemplateManifest.Mode.STRICT, manifest.getMode());
        assertTrue(manifest.hasEntries());
        assertTrue(manifest.getEntry("arena_small").isPresent());
        assertEquals(2, manifest.getEntry("arena_small").get().version());
    }

    @Test
    void parseJsonDefaultsMode() {
        String json = """
            {
              "entries": {}
            }
            """;
        TemplateManifest manifest = TemplateManifest.parseJson(json);
        assertEquals(TemplateManifest.Mode.WARN, manifest.getMode());
    }

    @Test
    void parseInvalidJsonReturnsEmpty() {
        TemplateManifest manifest = TemplateManifest.parseJson("not json");
        assertEquals(TemplateManifest.Mode.SKIP, manifest.getMode());
        assertFalse(manifest.hasEntries());
    }

    // ---- ToJson/RoundTrip ----

    @Test
    void toJsonRoundTrip() {
        String json = """
            {
              "validationMode": "WARN",
              "entries": {
                "t1": {
                  "version": 1,
                  "checksum": "abc12345",
                  "checksumFull": "abc12345def67890"
                }
              }
            }
            """;
        TemplateManifest original = TemplateManifest.parseJson(json);
        String serialized = original.toJson();
        TemplateManifest reparsed = TemplateManifest.parseJson(serialized);
        assertEquals(original.getMode(), reparsed.getMode());
        assertTrue(reparsed.getEntry("t1").isPresent());
    }

    // ---- ChecksumValidationResult ----

    @Test
    void validResult() {
        var result = TemplateManifest.ChecksumValidationResult.valid("t1", "abc");
        assertTrue(result.valid());
        assertFalse(result.skipped());
        assertFalse(result.isError());
        assertFalse(result.isWarning());
    }

    @Test
    void skippedResult() {
        var result = TemplateManifest.ChecksumValidationResult.skipped("t1");
        assertTrue(result.valid());
        assertTrue(result.skipped());
    }

    @Test
    void checksumMismatchStrict() {
        var result = TemplateManifest.ChecksumValidationResult.checksumMismatch("t1", "expected", "computed", true);
        assertFalse(result.valid());
        assertTrue(result.strict());
        assertTrue(result.isError());
        assertFalse(result.isWarning());
    }

    @Test
    void checksumMismatchWarn() {
        var result = TemplateManifest.ChecksumValidationResult.checksumMismatch("t1", "expected", "computed", false);
        assertFalse(result.valid());
        assertFalse(result.strict());
        assertFalse(result.isError());
        assertTrue(result.isWarning());
    }

    @Test
    void versionMismatchResult() {
        var result = TemplateManifest.ChecksumValidationResult.versionMismatch("t1", 1, 2, "abc");
        assertFalse(result.valid());
        assertTrue(result.message().contains("Version mismatch"));
    }

    @Test
    void noManifestEntryResult() {
        var result = TemplateManifest.ChecksumValidationResult.noManifestEntry("t1", "abc");
        assertTrue(result.valid());
        assertNotNull(result.computedChecksum());
    }
}

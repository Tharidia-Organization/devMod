package com.devmod.arena.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * P1: Template manifest for checksum validation.
 *
 * Maps template IDs to expected checksums, enabling detection of:
 * - Template tampering
 * - Template corruption
 * - Unauthorized modifications
 *
 * Manifest JSON format:
 * <pre>
 * {
 *   "entries": {
 *     "template_id": {
 *       "version": 1,
 *       "checksum": "abc12345",
 *       "checksumFull": "abc12345def67890..."
 *     }
 *   },
 *   "validationMode": "STRICT"
 * }
 * </pre>
 */
public final class TemplateManifest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateManifest.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Manifest entry for a single template.
     */
    public record Entry(
        int version,
        String checksum,           // Short checksum (8 chars)
        @Nullable String checksumFull  // Full checksum (optional, for strict validation)
    ) {
        public boolean matches(String computed) {
            if (computed == null) return false;
            if (checksumFull != null) {
                return computed.equalsIgnoreCase(checksumFull);
            }
            return computed.startsWith(checksum);
        }
    }

    /**
     * Validation mode for checksum enforcement.
     */
    public enum Mode {
        /** Checksum mismatch is a warning, template still loads */
        WARN,
        /** Checksum mismatch fails validation */
        STRICT,
        /** Skip checksum validation entirely */
        SKIP
    }

    private final Map<String, Entry> entries;
    private final Mode mode;

    private TemplateManifest(Map<String, Entry> entries, Mode mode) {
        this.entries = new ConcurrentHashMap<>(entries);
        this.mode = mode;
    }

    /**
     * Create an empty manifest (no checksums to validate).
     */
    public static TemplateManifest empty() {
        return new TemplateManifest(Map.of(), Mode.SKIP);
    }

    /**
     * Get the expected entry for a template ID.
     */
    public Optional<Entry> getEntry(String templateId) {
        return Optional.ofNullable(entries.get(templateId));
    }

    /**
     * Get the validation mode.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Check if manifest has any entries.
     */
    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    /**
     * Validate a template against its manifest entry.
     *
     * @param template The template to validate
     * @return Validation result
     */
    public ChecksumValidationResult validate(ArenaTemplate template) {
        if (mode == Mode.SKIP) {
            return ChecksumValidationResult.skipped(template.id());
        }

        Optional<Entry> entryOpt = getEntry(template.id());
        if (entryOpt.isEmpty()) {
            // No manifest entry - compute and record for future
            String computed = TemplateChecksum.computeShort(template);
            return ChecksumValidationResult.noManifestEntry(template.id(), computed);
        }

        Entry entry = entryOpt.get();
        String computedFull = TemplateChecksum.compute(template);
        String computedShort = computedFull.substring(0, 8);

        // Version check
        if (template.version() != entry.version()) {
            return ChecksumValidationResult.versionMismatch(
                template.id(), entry.version(), template.version(), computedShort);
        }

        // Checksum check
        if (!entry.matches(computedFull)) {
            return ChecksumValidationResult.checksumMismatch(
                template.id(), entry.checksum(), computedShort, mode == Mode.STRICT);
        }

        return ChecksumValidationResult.valid(template.id(), computedShort);
    }

    /**
     * Add or update a manifest entry.
     */
    public void addEntry(String templateId, Entry entry) {
        entries.put(templateId, entry);
    }

    /**
     * Generate a manifest entry from a template.
     */
    public static Entry generateEntry(ArenaTemplate template) {
        String full = TemplateChecksum.compute(template);
        return new Entry(template.version(), full.substring(0, 8), full);
    }

    /**
     * Parse manifest from JSON file.
     */
    public static TemplateManifest parse(Path path) throws IOException {
        if (!Files.exists(path)) {
            LOGGER.info("Template manifest not found at {}, using empty manifest", path);
            return empty();
        }

        String json = Files.readString(path);
        return parseJson(json);
    }

    /**
     * Parse manifest from JSON string.
     */
    public static TemplateManifest parseJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            Mode mode = Mode.WARN;
            if (root.has("validationMode")) {
                try {
                    mode = Mode.valueOf(root.get("validationMode").getAsString());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid validationMode in manifest, defaulting to WARN");
                }
            }

            Map<String, Entry> entries = new ConcurrentHashMap<>();
            if (root.has("entries") && root.get("entries").isJsonObject()) {
                JsonObject entriesObj = root.getAsJsonObject("entries");
                for (Map.Entry<String, JsonElement> e : entriesObj.entrySet()) {
                    String templateId = e.getKey();
                    JsonObject entryObj = e.getValue().getAsJsonObject();

                    int version = entryObj.has("version") ? entryObj.get("version").getAsInt() : 1;
                    String checksum = entryObj.has("checksum") ? entryObj.get("checksum").getAsString() : "";
                    String checksumFull = entryObj.has("checksumFull")
                        ? entryObj.get("checksumFull").getAsString()
                        : null;

                    entries.put(templateId, new Entry(version, checksum, checksumFull));
                }
            }

            LOGGER.info("Loaded template manifest with {} entries, mode={}", entries.size(), mode);
            return new TemplateManifest(entries, mode);

        } catch (Exception e) {
            LOGGER.error("Failed to parse template manifest: {}", e.getMessage());
            return empty();
        }
    }

    /**
     * Serialize manifest to JSON.
     */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("validationMode", mode.name());

        JsonObject entriesObj = new JsonObject();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("version", e.getValue().version());
            entryObj.addProperty("checksum", e.getValue().checksum());
            if (e.getValue().checksumFull() != null) {
                entryObj.addProperty("checksumFull", e.getValue().checksumFull());
            }
            entriesObj.add(e.getKey(), entryObj);
        }
        root.add("entries", entriesObj);

        return GSON.toJson(root);
    }

    /**
     * Save manifest to file.
     */
    public void save(Path path) throws IOException {
        Files.writeString(path, toJson());
        LOGGER.info("Saved template manifest to {} with {} entries", path, entries.size());
    }

    /**
     * Result of checksum validation.
     */
    public record ChecksumValidationResult(
        String templateId,
        boolean valid,
        boolean skipped,
        boolean strict,
        @Nullable String computedChecksum,
        @Nullable String expectedChecksum,
        @Nullable String message
    ) {
        public static ChecksumValidationResult valid(String templateId, String computed) {
            return new ChecksumValidationResult(templateId, true, false, false, computed, null, null);
        }

        public static ChecksumValidationResult skipped(String templateId) {
            return new ChecksumValidationResult(templateId, true, true, false, null, null, "Validation skipped");
        }

        public static ChecksumValidationResult noManifestEntry(String templateId, String computed) {
            return new ChecksumValidationResult(templateId, true, false, false, computed, null,
                "No manifest entry (new template?)");
        }

        public static ChecksumValidationResult versionMismatch(String templateId, int expected, int actual, String computed) {
            return new ChecksumValidationResult(templateId, false, false, false, computed, null,
                String.format("Version mismatch: expected %d, got %d", expected, actual));
        }

        public static ChecksumValidationResult checksumMismatch(String templateId, String expected, String computed, boolean strict) {
            return new ChecksumValidationResult(templateId, false, false, strict, computed, expected,
                String.format("Checksum mismatch: expected %s, computed %s", expected, computed));
        }

        public boolean isError() {
            return !valid && strict;
        }

        public boolean isWarning() {
            return !valid && !strict;
        }
    }
}

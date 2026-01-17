package com.devmod.npc.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import com.devmod.DevMod;
import com.devmod.npc.dialog.DialogSet;

/**
 * Utility class for importing and exporting DialogSet to/from JSON files.
 * Uses the DialogSet CODEC for serialization.
 *
 * <p>Usage:
 * <pre>
 * // Export
 * String json = DialogSetIO.exportToJson(dialogSet);
 * DialogSetIO.exportToFile(dialogSet, Path.of("dialogs/my_dialog.json"));
 *
 * // Import
 * DialogSet set = DialogSetIO.importFromJson(jsonString);
 * DialogSet set = DialogSetIO.importFromFile(Path.of("dialogs/my_dialog.json"));
 * </pre>
 */
public final class DialogSetIO {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private DialogSetIO() {}

    /**
     * Exports a DialogSet to a JSON string.
     *
     * @param dialogSet The dialog set to export
     * @return JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    @Nonnull
    public static String exportToJson(@Nonnull DialogSet dialogSet) {
        DataResult<JsonElement> result = DialogSet.CODEC.encodeStart(JsonOps.INSTANCE, dialogSet);

        return result.resultOrPartial(error -> {
            DevMod.LOGGER.error("Failed to encode DialogSet: {}", error);
        }).map(GSON::toJson).orElseThrow(() ->
            new IllegalStateException("Failed to serialize DialogSet to JSON")
        );
    }

    /**
     * Imports a DialogSet from a JSON string.
     *
     * @param json The JSON string to parse
     * @return The parsed DialogSet, or empty if parsing fails
     */
    @Nonnull
    public static Optional<DialogSet> importFromJson(@Nonnull String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            DataResult<DialogSet> result = DialogSet.CODEC.parse(JsonOps.INSTANCE, element);

            return result.resultOrPartial(error -> {
                DevMod.LOGGER.error("Failed to decode DialogSet: {}", error);
            });
        } catch (JsonSyntaxException e) {
            DevMod.LOGGER.error("Invalid JSON syntax: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Exports a DialogSet to a file.
     *
     * @param dialogSet The dialog set to export
     * @param path      The file path to write to
     * @throws IOException if writing fails
     */
    public static void exportToFile(@Nonnull DialogSet dialogSet, @Nonnull Path path) throws IOException {
        String json = exportToJson(dialogSet);

        // Create parent directories if needed
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, json, StandardCharsets.UTF_8);
        DevMod.LOGGER.info("Exported DialogSet '{}' to {}", dialogSet.id(), path);
    }

    /**
     * Imports a DialogSet from a file.
     *
     * @param path The file path to read from
     * @return The parsed DialogSet, or empty if reading/parsing fails
     */
    @Nonnull
    public static Optional<DialogSet> importFromFile(@Nonnull Path path) {
        if (!Files.exists(path)) {
            DevMod.LOGGER.warn("Dialog file does not exist: {}", path);
            return Optional.empty();
        }

        if (!Files.isRegularFile(path)) {
            DevMod.LOGGER.warn("Path is not a regular file: {}", path);
            return Optional.empty();
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Optional<DialogSet> result = importFromJson(json);

            if (result.isPresent()) {
                DevMod.LOGGER.info("Imported DialogSet '{}' from {}", result.get().id(), path);
            }

            return result;
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to read dialog file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Validates a JSON string as a valid DialogSet without fully parsing it.
     *
     * @param json The JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidJson(@Nonnull String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            DataResult<DialogSet> result = DialogSet.CODEC.parse(JsonOps.INSTANCE, element);
            return result.result().isPresent();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Gets the default export directory for dialog files.
     *
     * @return Path to config/devmod/dialogs/
     */
    @Nonnull
    public static Path getExportDirectory() {
        return Path.of("config", "devmod", "dialogs");
    }

    /**
     * Gets the default file path for a dialog set export.
     *
     * @param dialogSetId The dialog set ID
     * @return Path to config/devmod/dialogs/{id}.json
     */
    @Nonnull
    public static Path getDefaultExportPath(@Nonnull String dialogSetId) {
        // Sanitize the ID for use as filename
        String safeName = dialogSetId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return getExportDirectory().resolve(safeName + ".json");
    }

    /**
     * Lists all available dialog JSON files in the export directory.
     *
     * @return Array of file paths, or empty array if directory doesn't exist
     */
    @Nonnull
    public static Path[] listExportedDialogs() {
        Path dir = getExportDirectory();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return new Path[0];
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(p -> p.toString().endsWith(".json"))
                .filter(Files::isRegularFile)
                .toArray(Path[]::new);
        } catch (IOException e) {
            DevMod.LOGGER.error("Failed to list dialog files: {}", e.getMessage());
            return new Path[0];
        }
    }
}

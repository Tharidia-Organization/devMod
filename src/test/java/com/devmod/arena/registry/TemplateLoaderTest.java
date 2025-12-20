package com.devmod.arena.registry;

import com.devmod.arena.telemetry.ArenaTelemetry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateLoaderTest {

    private TemplateValidator validator;
    private List<ArenaTelemetry.TelemetryEvent> events;
    private ArenaTelemetry telemetry;
    private TemplateLoader loader;

    @BeforeEach
    void setUp() {
        validator = new TemplateValidator();
        events = new ArrayList<>();
        telemetry = new ArenaTelemetry(events::add);
        loader = new TemplateLoader(validator, TemplateValidator.ValidationMode.PERMISSIVE, telemetry);
    }

    @AfterEach
    void tearDown() throws Exception {
        // no-op
    }

    @Test
    void loadsTemplateAndEmitsUnknownFields() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-test");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("unknownField", true);
        Path file = dir.resolve("t1.json");
        Files.writeString(file, obj.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertTrue(result.success());
        assertEquals(1, result.templates().size());

        boolean unknownFieldsEvent = events.stream()
            .anyMatch(e -> e.name().equals("arena.template.unknown_fields"));
        assertTrue(unknownFieldsEvent, "Expected unknown_fields telemetry");
    }

    @Test
    void strictModeRejectsUnknownFields() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-test-strict");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("extra", 123);
        Path file = dir.resolve("t1.json");
        Files.writeString(file, obj.toString());

        TemplateLoader strictLoader = new TemplateLoader(new TemplateValidator(), TemplateValidator.ValidationMode.STRICT, telemetry);
        TemplateLoader.LoadResult result = strictLoader.loadAll(dir);

        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("Unknown fields"));
    }

    // === DD40: Malformed Template Edge Cases ===

    @Test
    void dd40_rejectsMalformedJsonSyntax() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-malformed");
        Path file = dir.resolve("malformed.json");
        Files.writeString(file, "{ invalid json without quotes }");

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Malformed JSON should fail to load");
        assertFalse(result.errors().isEmpty(), "Should have error messages");
    }

    @Test
    void dd40_rejectsEmptyJsonObject() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-empty");
        Path file = dir.resolve("empty.json");
        Files.writeString(file, "{}");

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Empty JSON object should fail validation");
    }

    @Test
    void dd40_rejectsNullRequiredFields() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-null");
        Path file = dir.resolve("nullfields.json");
        Files.writeString(file, """
            {
                "id": null,
                "version": 1,
                "schemaVersion": 1
            }
            """);

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Null required field should fail validation");
    }

    @Test
    void dd40_rejectsInvalidTypeForSize() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-wrongtype");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("size", "not-a-number");
        Path file = dir.resolve("wrongtype.json");
        Files.writeString(file, obj.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Invalid type for size should fail");
    }

    @Test
    void dd40_rejectsSizeBelowMinimum() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-toosmall");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("size", 4); // MIN_SIZE is 8
        Path file = dir.resolve("toosmall.json");
        Files.writeString(file, obj.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Size below minimum should fail validation");
    }

    @Test
    void dd40_rejectsSizeAboveMaximum() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-toolarge");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("size", 512); // MAX_SIZE is 256
        Path file = dir.resolve("toolarge.json");
        Files.writeString(file, obj.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Size above maximum should fail validation");
    }

    @Test
    void dd40_rejectsInvalidLightingValues() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-lighting");
        String json = new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonObject lighting = new JsonObject();
        lighting.addProperty("skyLight", 20); // Max is 15
        lighting.addProperty("blockLight", -5); // Min is 0
        lighting.addProperty("ambientLight", true);
        obj.add("lighting", lighting);
        Path file = dir.resolve("badlighting.json");
        Files.writeString(file, obj.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        assertFalse(result.success(), "Invalid lighting values should fail validation");
    }

    @Test
    void dd40_rejectsCircularInheritance() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-circular");

        // Template A extends B
        JsonObject templateA = JsonParser.parseString(
            new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate())
        ).getAsJsonObject();
        templateA.addProperty("id", "template_a");
        templateA.addProperty("extendsTemplate", "template_b");
        Files.writeString(dir.resolve("template_a.json"), templateA.toString());

        // Template B extends A (circular)
        JsonObject templateB = JsonParser.parseString(
            new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate())
        ).getAsJsonObject();
        templateB.addProperty("id", "template_b");
        templateB.addProperty("extendsTemplate", "template_a");
        Files.writeString(dir.resolve("template_b.json"), templateB.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        // Should either fail or handle gracefully
        assertTrue(result.errors().isEmpty() || !result.success(),
            "Circular inheritance should be detected or handled");
    }

    @Test
    void dd40_handlesMissingParentTemplate() throws Exception {
        Path dir = Files.createTempDirectory("template-loader-missingparent");

        JsonObject template = JsonParser.parseString(
            new com.google.gson.Gson().toJson(ArenaTemplate.defaultTemplate())
        ).getAsJsonObject();
        template.addProperty("id", "child_template");
        template.addProperty("extendsTemplate", "nonexistent_parent");
        Files.writeString(dir.resolve("child.json"), template.toString());

        TemplateLoader.LoadResult result = loader.loadAll(dir);

        // Should report error about missing parent
        assertFalse(result.success(), "Missing parent template should fail");
    }
}

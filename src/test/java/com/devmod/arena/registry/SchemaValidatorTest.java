package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {

    @Test
    void strictRejectsUnknownFields() {
        JsonObject obj = JsonParser.parseString("""
            { "id":"t1", "size":64, "bogus":true }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.STRICT);

        assertFalse(result.valid());
        assertTrue(result.errors().get(0).contains("Unknown fields"));
    }

    @Test
    void permissiveWarnsUnknownFields() {
        JsonObject obj = JsonParser.parseString("""
            { "id":"t1", "size":64, "extra_field":123 }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.PERMISSIVE);

        assertTrue(result.valid());
        assertTrue(result.warnings().get(0).contains("Unknown fields"));
        assertEquals("extra_field", result.unknownFields().get(0));
    }

    @Test
    void lenientIgnoresUnknownFields() {
        JsonObject obj = JsonParser.parseString("""
            { "id":"t1", "size":64, "extra_field":123 }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.LENIENT);

        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().stream().noneMatch(w -> w.contains("Unknown fields")));
    }

    @Test
    void ignoresSpecialKeys() {
        JsonObject obj = JsonParser.parseString("""
            {
              "id": "t1",
              "version": 1,
              "schemaVersion": 1,
              "origin": { "mode": "CENTER", "x": 0, "y": 64, "z": 0 },
              "size": 64,
              "floor": {
                "y": 64,
                "thickness": 1,
                "material": "minecraft:stone_bricks",
                "pattern": "solid",
                "borderMaterial": "minecraft:stone_bricks",
                "borderWidth": 0
              },
              "mobSpawnStrategy": "DISTRIBUTED",
              "$schema": "x",
              "_comment": "y",
              "//note": true
            }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.STRICT);

        assertTrue(result.valid());
        assertTrue(result.unknownFields().isEmpty());
    }

    @Test
    void strictRejectsUnknownNestedFields() {
        JsonObject obj = JsonParser.parseString("""
            {
              "id": "t1",
              "version": 1,
              "schemaVersion": 1,
              "origin": { "mode": "CENTER", "x": 0, "y": 64, "z": 0, "extra": 1 },
              "size": 64,
              "floor": {
                "y": 64,
                "thickness": 1,
                "material": "minecraft:stone_bricks",
                "pattern": "solid",
                "borderMaterial": "minecraft:stone_bricks",
                "borderWidth": 0
              },
              "mobSpawnStrategy": "DISTRIBUTED"
            }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.STRICT);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("origin")));
    }
}

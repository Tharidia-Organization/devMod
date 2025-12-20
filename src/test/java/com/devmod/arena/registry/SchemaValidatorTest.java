package com.devmod.arena.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

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
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void ignoresSpecialKeys() {
        JsonObject obj = JsonParser.parseString("""
            { "id":"t1", "size":64, "$schema":"x", "_comment":"y", "//note":true }
            """).getAsJsonObject();

        SchemaValidator.ValidationResult result = SchemaValidator.validate(
            obj, TemplateValidator.ValidationMode.STRICT);

        assertTrue(result.valid());
        assertTrue(result.unknownFields().isEmpty());
    }
}

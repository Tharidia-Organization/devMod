package com.devmod.arena.policy;

import com.devmod.arena.registry.TemplateValidator;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolicySchemaValidatorTest {

    @Test
    void strictModeRejectsUnknownAndMissingFields() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "p1");
        obj.addProperty("templateId", "default_flat_64");
        obj.addProperty("unknownField", true);

        PolicySchemaValidator.ValidationResult result =
            PolicySchemaValidator.validate(obj, TemplateValidator.ValidationMode.STRICT);

        assertFalse(result.valid(), "Strict mode should fail on missing/unknown fields");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unknown fields")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required fields")));
    }

    @Test
    void permissiveModeWarnsButPasses() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "p2");
        obj.addProperty("templateId", "default_flat_64");
        obj.addProperty("unknownField", true);

        PolicySchemaValidator.ValidationResult result =
            PolicySchemaValidator.validate(obj, TemplateValidator.ValidationMode.PERMISSIVE);

        assertTrue(result.valid(), "Permissive mode should not fail");
        assertFalse(result.warnings().isEmpty(), "Permissive mode should emit warnings");
    }
}

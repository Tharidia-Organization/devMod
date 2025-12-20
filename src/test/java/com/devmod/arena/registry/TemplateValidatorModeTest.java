package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateValidatorModeTest {

    @Test
    void parsesModesCaseInsensitive() {
        assertEquals(TemplateValidator.ValidationMode.STRICT,
            TemplateValidator.fromString("STRICT", TemplateValidator.ValidationMode.LENIENT));
        assertEquals(TemplateValidator.ValidationMode.PERMISSIVE,
            TemplateValidator.fromString("permissive", TemplateValidator.ValidationMode.STRICT));
        assertEquals(TemplateValidator.ValidationMode.LENIENT,
            TemplateValidator.fromString("Lenient", TemplateValidator.ValidationMode.STRICT));
    }

    @Test
    void fallsBackOnUnknown() {
        assertEquals(TemplateValidator.ValidationMode.PERMISSIVE,
            TemplateValidator.fromString("unknown", TemplateValidator.ValidationMode.PERMISSIVE));
    }
}

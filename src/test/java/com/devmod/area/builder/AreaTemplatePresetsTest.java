package com.devmod.area.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.area.template.TemplateCategory;

@DisplayName("AreaTemplatePresets")
class AreaTemplatePresetsTest {

    @Test
    @DisplayName("arena_medium is available as standard arena preset")
    void arenaMediumPreset_isConfigured() {
        assertTrue(AreaTemplatePresets.hasPreset("arena_medium"));

        AreaTemplatePresets.AreaTemplatePreset preset = AreaTemplatePresets.getPreset("arena_medium");
        assertEquals(TemplateCategory.ARENA, preset.category());
        assertEquals("default", preset.palettePreset());
        assertEquals("octagonal", preset.shape().getSerializedName());
    }
}

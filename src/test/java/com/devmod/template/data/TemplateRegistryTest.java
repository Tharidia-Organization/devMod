package com.devmod.template.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRegistryTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private TemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TemplateRegistry();
    }

    private RoomTemplate makeTemplate(String templateId, String zoneId, boolean isCustom) {
        return new RoomTemplate(UUID.randomUUID(), templateId, "Display: " + templateId,
                zoneId, List.of(), Map.of(), 16, 16, isCustom, 1000L, 1);
    }

    @Test
    void initializesBuiltinComponents() {
        Collection<ComponentDefinition> components = registry.getAllComponents();
        assertFalse(components.isEmpty(), "Should have built-in components");
        assertTrue(components.size() >= 20, "Should have at least 20 built-in components");
    }

    @Test
    void registerTemplateAndRetrieveByUUID() {
        RoomTemplate template = makeTemplate("test1", "spawn", false);
        registry.registerTemplate(template);
        Optional<RoomTemplate> found = registry.getTemplate(template.id());
        assertTrue(found.isPresent());
        assertEquals("test1", found.get().templateId());
    }

    @Test
    void registerTemplateAndRetrieveByStringId() {
        RoomTemplate template = makeTemplate("test1", "spawn", false);
        registry.registerTemplate(template);
        Optional<RoomTemplate> found = registry.getTemplate("test1");
        assertTrue(found.isPresent());
        assertEquals(template.id(), found.get().id());
    }

    @Test
    void getTemplateReturnsEmptyForUnknownUUID() {
        Optional<RoomTemplate> found = registry.getTemplate(UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void getTemplateReturnsEmptyForUnknownStringId() {
        Optional<RoomTemplate> found = registry.getTemplate("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void deleteTemplateRemovesIt() {
        RoomTemplate template = makeTemplate("test_del", "spawn", false);
        registry.registerTemplate(template);
        assertTrue(registry.deleteTemplate(template.id()));
        assertTrue(registry.getTemplate(template.id()).isEmpty());
        assertTrue(registry.getTemplate("test_del").isEmpty());
    }

    @Test
    void deleteTemplateReturnsFalseForUnknown() {
        assertFalse(registry.deleteTemplate(UUID.randomUUID()));
    }

    @Test
    void updateTemplateWithCorrectRevision() {
        RoomTemplate template = makeTemplate("test_upd", "spawn", false);
        registry.registerTemplate(template);

        RoomTemplate updated = new RoomTemplate(template.id(), "test_upd", "Updated Name",
                "spawn", List.of(), Map.of(), 20, 20, false, 1000L, 1);
        assertTrue(registry.updateTemplate(template.id(), updated, 1));

        Optional<RoomTemplate> found = registry.getTemplate(template.id());
        assertTrue(found.isPresent());
        assertEquals("Updated Name", found.get().displayName());
        assertEquals(2, found.get().revision()); // withNewRevision increments
    }

    @Test
    void updateTemplateFailsWithWrongRevision() {
        RoomTemplate template = makeTemplate("test_rev", "spawn", false);
        registry.registerTemplate(template);

        RoomTemplate updated = new RoomTemplate(template.id(), "test_rev", "Updated",
                "spawn", List.of(), Map.of(), 16, 16, false, 1000L, 1);
        assertFalse(registry.updateTemplate(template.id(), updated, 99));

        // Original should be unchanged
        assertEquals("Display: test_rev", registry.getTemplate(template.id()).get().displayName());
    }

    @Test
    void updateTemplateFailsForNonexistentId() {
        RoomTemplate template = makeTemplate("ghost", "spawn", false);
        assertFalse(registry.updateTemplate(UUID.randomUUID(), template, 1));
    }

    @Test
    void getTemplatesForZone() {
        registry.registerTemplate(makeTemplate("s1", "spawn", false));
        registry.registerTemplate(makeTemplate("s2", "spawn", false));
        registry.registerTemplate(makeTemplate("t1", "tutorial", false));

        List<RoomTemplate> spawnTemplates = registry.getTemplatesForZone("spawn");
        assertEquals(2, spawnTemplates.size());

        List<RoomTemplate> tutorialTemplates = registry.getTemplatesForZone("tutorial");
        assertEquals(1, tutorialTemplates.size());

        List<RoomTemplate> emptyTemplates = registry.getTemplatesForZone("nonexistent");
        assertTrue(emptyTemplates.isEmpty());
    }

    @Test
    void getPresetsFiltersCorrectly() {
        registry.registerTemplate(makeTemplate("preset1", "spawn", false));
        registry.registerTemplate(makeTemplate("custom1", "spawn", true));

        List<RoomTemplate> presets = registry.getPresets();
        assertEquals(1, presets.size());
        assertEquals("preset1", presets.get(0).templateId());
    }

    @Test
    void getCustomTemplatesFiltersCorrectly() {
        registry.registerTemplate(makeTemplate("preset1", "spawn", false));
        registry.registerTemplate(makeTemplate("custom1", "spawn", true));

        List<RoomTemplate> customs = registry.getCustomTemplates();
        assertEquals(1, customs.size());
        assertEquals("custom1", customs.get(0).templateId());
    }

    @Test
    void getTemplateCountReflectsRegistrations() {
        int initial = registry.getTemplateCount();
        registry.registerTemplate(makeTemplate("a", "spawn", false));
        assertEquals(initial + 1, registry.getTemplateCount());
        registry.registerTemplate(makeTemplate("b", "spawn", false));
        assertEquals(initial + 2, registry.getTemplateCount());
    }

    @Test
    void registerComponentAndRetrieve() {
        ComponentDefinition custom = new ComponentDefinition("custom_comp", "Custom Component",
                ComponentCategory.DECORATIVE, new net.minecraft.core.Vec3i(2, 2, 2),
                8, false, List.of("*"));
        registry.registerComponent(custom);
        Optional<ComponentDefinition> found = registry.getComponent("custom_comp");
        assertTrue(found.isPresent());
        assertEquals("Custom Component", found.get().displayName());
    }

    @Test
    void getComponentReturnsEmptyForUnknown() {
        assertTrue(registry.getComponent("does_not_exist").isEmpty());
    }

    @Test
    void getComponentsForZoneUsesCompatibility() {
        // Built-in components with "*" should be returned for any zone
        List<ComponentDefinition> components = registry.getComponentsForZone("spawn");
        assertFalse(components.isEmpty());
    }

    @Test
    void getComponentsByCategory() {
        List<ComponentDefinition> display = registry.getComponentsByCategory(ComponentCategory.DISPLAY);
        assertFalse(display.isEmpty());
        for (ComponentDefinition comp : display) {
            assertEquals(ComponentCategory.DISPLAY, comp.category());
        }
    }

    @Test
    void registerNullTemplateThrows() {
        assertThrows(NullPointerException.class, () -> registry.registerTemplate(null));
    }

    @Test
    void registerNullComponentThrows() {
        assertThrows(NullPointerException.class, () -> registry.registerComponent(null));
    }
}

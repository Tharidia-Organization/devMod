package com.devmod.template.data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class RoomTemplateTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private RoomTemplate makeTemplate(int minWidth, int minDepth, boolean isCustom) {
        UUID id = UUID.randomUUID();
        return new RoomTemplate(id, "test_template", "Test Template", "spawn",
                List.of(), Map.of(), minWidth, minDepth, isCustom, 1000L, 1);
    }

    @Test
    void constructorRejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(null, "tid", "name", "zone",
                        List.of(), Map.of(), 16, 16, false, 0L, 1));
    }

    @Test
    void constructorRejectsNullTemplateId() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(UUID.randomUUID(), null, "name", "zone",
                        List.of(), Map.of(), 16, 16, false, 0L, 1));
    }

    @Test
    void constructorRejectsNullDisplayName() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(UUID.randomUUID(), "tid", null, "zone",
                        List.of(), Map.of(), 16, 16, false, 0L, 1));
    }

    @Test
    void constructorRejectsNullZoneId() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(UUID.randomUUID(), "tid", "name", null,
                        List.of(), Map.of(), 16, 16, false, 0L, 1));
    }

    @Test
    void constructorRejectsNullComponents() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(UUID.randomUUID(), "tid", "name", "zone",
                        null, Map.of(), 16, 16, false, 0L, 1));
    }

    @Test
    void constructorRejectsNullMaterialOverrides() {
        assertThrows(NullPointerException.class, () ->
                new RoomTemplate(UUID.randomUUID(), "tid", "name", "zone",
                        List.of(), null, 16, 16, false, 0L, 1));
    }

    @Test
    void fitsRoomReturnsTrueWhenLargeEnough() {
        RoomTemplate template = makeTemplate(16, 16, false);
        assertTrue(template.fitsRoom(16, 16));
        assertTrue(template.fitsRoom(20, 20));
        assertTrue(template.fitsRoom(16, 30));
    }

    @Test
    void fitsRoomReturnsFalseWhenTooSmall() {
        RoomTemplate template = makeTemplate(16, 16, false);
        assertFalse(template.fitsRoom(15, 16));
        assertFalse(template.fitsRoom(16, 15));
        assertFalse(template.fitsRoom(10, 10));
    }

    @Test
    void fitsRoomChecksWidthAndDepthIndependently() {
        RoomTemplate template = makeTemplate(20, 10, false);
        assertTrue(template.fitsRoom(20, 10));
        assertFalse(template.fitsRoom(19, 10));
        assertTrue(template.fitsRoom(20, 10));
        assertFalse(template.fitsRoom(20, 9));
    }

    @Test
    void getMaterialOverrideReturnsValueWhenPresent() {
        UUID id = UUID.randomUUID();
        RoomTemplate template = new RoomTemplate(id, "tid", "name", "zone",
                List.of(), Map.of("floor", "stone", "wall", "brick"),
                16, 16, false, 0L, 1);
        assertEquals("stone", template.getMaterialOverride("floor"));
        assertEquals("brick", template.getMaterialOverride("wall"));
    }

    @Test
    void getMaterialOverrideReturnsNullWhenAbsent() {
        RoomTemplate template = makeTemplate(16, 16, false);
        assertNull(template.getMaterialOverride("nonexistent"));
    }

    @Test
    void withNewRevisionIncrementsRevision() {
        RoomTemplate original = makeTemplate(16, 16, false);
        assertEquals(1, original.revision());
        RoomTemplate revised = original.withNewRevision();
        assertEquals(2, revised.revision());
        assertEquals(original.id(), revised.id());
        assertEquals(original.templateId(), revised.templateId());
    }

    @Test
    void withComponentsReplacesComponentsAndIncrementsRevision() {
        RoomTemplate original = makeTemplate(16, 16, false);
        assertTrue(original.components().isEmpty());

        List<ComponentPlacement> newComps = List.of(
                ComponentPlacement.of("comp1", RelativePosition.center()),
                ComponentPlacement.of("comp2", RelativePosition.northWall(2))
        );
        RoomTemplate updated = original.withComponents(newComps);
        assertEquals(2, updated.components().size());
        assertEquals(2, updated.revision());
        assertEquals(original.id(), updated.id());
    }

    @Test
    void builderCreatesTemplateWithDefaults() {
        RoomTemplate template = RoomTemplate.builder("test_id", "spawn").build();
        assertNotNull(template.id());
        assertEquals("test_id", template.templateId());
        assertEquals("test_id", template.displayName()); // default displayName = templateId
        assertEquals("spawn", template.zoneId());
        assertTrue(template.components().isEmpty());
        assertTrue(template.materialOverrides().isEmpty());
        assertEquals(16, template.minWidth());
        assertEquals(16, template.minDepth());
        assertFalse(template.isCustom());
        assertEquals(1, template.revision());
    }

    @Test
    void builderSetsDisplayName() {
        RoomTemplate template = RoomTemplate.builder("id", "zone")
                .displayName("My Custom Name")
                .build();
        assertEquals("My Custom Name", template.displayName());
    }

    @Test
    void builderAddsComponents() {
        RoomTemplate template = RoomTemplate.builder("id", "zone")
                .component("comp1", RelativePosition.center())
                .component(ComponentPlacement.of("comp2", RelativePosition.northWall(3), Direction.SOUTH))
                .build();
        assertEquals(2, template.components().size());
        assertEquals("comp1", template.components().get(0).componentId());
        assertEquals("comp2", template.components().get(1).componentId());
    }

    @Test
    void builderSetsMaterialOverrides() {
        RoomTemplate template = RoomTemplate.builder("id", "zone")
                .material("floor", "stone")
                .material("wall", "brick")
                .build();
        assertEquals(2, template.materialOverrides().size());
        assertEquals("stone", template.materialOverrides().get("floor"));
    }

    @Test
    void builderSetsMinSize() {
        RoomTemplate template = RoomTemplate.builder("id", "zone")
                .minSize(24, 32)
                .build();
        assertEquals(24, template.minWidth());
        assertEquals(32, template.minDepth());
    }

    @Test
    void builderSetsCustomFlag() {
        RoomTemplate template = RoomTemplate.builder("id", "zone")
                .custom(true)
                .build();
        assertTrue(template.isCustom());
    }
}

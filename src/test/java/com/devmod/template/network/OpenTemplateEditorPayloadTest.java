package com.devmod.template.network;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.devmod.template.data.RoomTemplate;

import static org.junit.jupiter.api.Assertions.*;

class OpenTemplateEditorPayloadTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private RoomTemplate makeTemplate(String id) {
        return new RoomTemplate(UUID.randomUUID(), id, "Display " + id,
                "spawn", List.of(), Map.of(), 16, 16, false, 1000L, 1);
    }

    @Test
    void constructorRejectsNullZoneId() {
        assertThrows(NullPointerException.class, () ->
                new OpenTemplateEditorPayload(null, 0, 0, 20, 20, 64, List.of(), ""));
    }

    @Test
    void constructorRejectsNullTemplateList() {
        assertThrows(NullPointerException.class, () ->
                new OpenTemplateEditorPayload("spawn", 0, 0, 20, 20, 64, null, ""));
    }

    @Test
    void constructorRejectsNullCurrentTemplateId() {
        assertThrows(NullPointerException.class, () ->
                new OpenTemplateEditorPayload("spawn", 0, 0, 20, 20, 64, List.of(), null));
    }

    @Test
    void recordAccessorsWork() {
        List<RoomTemplate> templates = List.of(makeTemplate("t1"), makeTemplate("t2"));
        OpenTemplateEditorPayload payload = new OpenTemplateEditorPayload(
                "spawn", 10, 20, 50, 60, 64, templates, "t1");

        assertEquals("spawn", payload.zoneId());
        assertEquals(10, payload.minX());
        assertEquals(20, payload.minZ());
        assertEquals(50, payload.maxX());
        assertEquals(60, payload.maxZ());
        assertEquals(64, payload.floorY());
        assertEquals(2, payload.availableTemplates().size());
        assertEquals("t1", payload.currentTemplateId());
    }

    @Test
    void typeReturnsCorrectType() {
        OpenTemplateEditorPayload payload = new OpenTemplateEditorPayload(
                "spawn", 0, 0, 20, 20, 64, List.of(), "");
        assertEquals(OpenTemplateEditorPayload.TYPE, payload.type());
    }

    @Test
    void estimatedSizeIsPositive() {
        OpenTemplateEditorPayload payload = new OpenTemplateEditorPayload(
                "spawn", 0, 0, 20, 20, 64,
                List.of(makeTemplate("t1")), "t1");
        assertTrue(payload.estimatedSize() > 0);
    }

    @Test
    void estimatedSizeGrowsWithMoreTemplates() {
        OpenTemplateEditorPayload small = new OpenTemplateEditorPayload(
                "spawn", 0, 0, 20, 20, 64, List.of(), "");
        OpenTemplateEditorPayload large = new OpenTemplateEditorPayload(
                "spawn", 0, 0, 20, 20, 64,
                List.of(makeTemplate("t1"), makeTemplate("t2"), makeTemplate("t3")), "");
        assertTrue(large.estimatedSize() > small.estimatedSize());
    }
}

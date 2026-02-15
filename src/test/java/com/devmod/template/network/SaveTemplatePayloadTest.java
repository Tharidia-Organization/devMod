package com.devmod.template.network;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.devmod.template.data.RoomTemplate;

import static org.junit.jupiter.api.Assertions.*;

class SaveTemplatePayloadTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private RoomTemplate makeTemplate() {
        return new RoomTemplate(UUID.randomUUID(), "test_template", "Test",
                "spawn", List.of(), Map.of(), 16, 16, true, 1000L, 1);
    }

    @Test
    void constructorRejectsNullTemplate() {
        assertThrows(NullPointerException.class, () ->
                new SaveTemplatePayload(null, 1));
    }

    @Test
    void recordAccessorsWork() {
        RoomTemplate template = makeTemplate();
        SaveTemplatePayload payload = new SaveTemplatePayload(template, 3);
        assertSame(template, payload.template());
        assertEquals(3, payload.expectedRevision());
    }

    @Test
    void typeReturnsCorrectType() {
        SaveTemplatePayload payload = new SaveTemplatePayload(makeTemplate(), 1);
        assertEquals(SaveTemplatePayload.TYPE, payload.type());
    }

    @Test
    void estimatedSizeIsPositive() {
        SaveTemplatePayload payload = new SaveTemplatePayload(makeTemplate(), 1);
        assertTrue(payload.estimatedSize() > 0);
    }
}

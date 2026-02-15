package com.devmod.template.network;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class ApplyTemplatePayloadTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void constructorRejectsNullTemplateId() {
        assertThrows(NullPointerException.class, () ->
                new ApplyTemplatePayload(null, 0, 0, 20, 20, 64, false));
    }

    @Test
    void recordAccessorsWork() {
        ApplyTemplatePayload payload = new ApplyTemplatePayload("spawn_standard", 10, 20, 30, 40, 64, true);
        assertEquals("spawn_standard", payload.templateId());
        assertEquals(10, payload.minX());
        assertEquals(20, payload.minZ());
        assertEquals(30, payload.maxX());
        assertEquals(40, payload.maxZ());
        assertEquals(64, payload.floorY());
        assertTrue(payload.clearFirst());
    }

    @Test
    void typeReturnsCorrectType() {
        ApplyTemplatePayload payload = new ApplyTemplatePayload("test", 0, 0, 20, 20, 64, false);
        assertEquals(ApplyTemplatePayload.TYPE, payload.type());
    }

    @Test
    void estimatedSizeIsPositive() {
        ApplyTemplatePayload payload = new ApplyTemplatePayload("spawn_standard", 0, 0, 100, 100, 64, true);
        assertTrue(payload.estimatedSize() > 0);
    }
}

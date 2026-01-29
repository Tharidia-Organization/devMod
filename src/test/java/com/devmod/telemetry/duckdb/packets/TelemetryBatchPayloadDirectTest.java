package com.devmod.telemetry.duckdb.packets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.CompressedEvent;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.EventType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryBatchPayloadDirectTest {

    @Test
    @DisplayName("EventType lookup and compressed event helpers")
    void eventTypeLookupAndCompressedEvent() {
        EventType type = EventType.COMBAT_HIT;
        assertEquals(type, EventType.fromId(type.getId()));
        assertNull(EventType.fromId((byte) 120));

        CompressedEvent event = CompressedEvent.fromString(type, 42, "payload");
        assertEquals("payload", event.getDataAsString());
        assertEquals(1042L, event.getFullTimestamp(1000L));
    }

    @Test
    @DisplayName("Builder enforces max events per batch")
    void builderEnforcesMaxEvents() {
        TelemetryBatchPayload.Builder builder = new TelemetryBatchPayload.Builder();
        for (int i = 0; i < TelemetryBatchPayload.MAX_EVENTS_PER_BATCH + 5; i++) {
            builder.addEvent(EventType.COMBAT_HIT, "{}");
        }
        TelemetryBatchPayload payload = builder.build();

        assertNotNull(payload);
        assertEquals(TelemetryBatchPayload.MAX_EVENTS_PER_BATCH, payload.events().size());
        assertTrue(builder.isFull());
    }
}

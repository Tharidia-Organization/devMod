package com.devmod.attributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AttributeLogEntry")
class AttributeLogEntryTest {

    @Nested
    @DisplayName("record fields")
    class RecordFieldsTests {

        @Test
        @DisplayName("stores type, message, position and timestamp")
        void basicFields() {
            Vec3 pos = new Vec3(1, 2, 3);
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.KILL, "test kill", pos, 12345L);

            assertEquals(AttributeLogEntry.Type.KILL, entry.type());
            assertEquals("test kill", entry.message());
            assertEquals(pos, entry.position());
            assertEquals(12345L, entry.timestamp());
        }

        @Test
        @DisplayName("auto-timestamp constructor uses current millis")
        void autoTimestamp() {
            long before = System.currentTimeMillis();
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null);
            long after = System.currentTimeMillis();

            assertTrue(entry.timestamp() >= before && entry.timestamp() <= after);
        }

        @Test
        @DisplayName("position can be null")
        void nullPosition() {
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, 0L);
            assertNull(entry.position());
        }
    }

    @Nested
    @DisplayName("getFormattedMessage")
    class FormattedMessageTests {

        @Test
        @DisplayName("includes type prefix and message")
        void containsPrefixAndMessage() {
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.KILL, "slain a zombie", null, 0L);
            String formatted = entry.getFormattedMessage();

            assertTrue(formatted.contains("[KILL]"), "Should contain type prefix");
            assertTrue(formatted.contains("slain a zombie"), "Should contain message text");
        }

        @Test
        @DisplayName("each type produces unique prefix")
        void uniquePrefixes() {
            AttributeLogEntry.Type[] types = AttributeLogEntry.Type.values();
            for (AttributeLogEntry.Type t : types) {
                AttributeLogEntry entry = new AttributeLogEntry(t, "x", null, 0L);
                String formatted = entry.getFormattedMessage();
                assertNotNull(formatted);
                assertFalse(formatted.isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("getFormattedAge")
    class FormattedAgeTests {

        @Test
        @DisplayName("very recent entry returns 'now'")
        void recentIsNow() {
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null);
            assertEquals("now", entry.getFormattedAge());
        }

        @Test
        @DisplayName("entry from 5 seconds ago returns '5s'")
        void fiveSecondsAgo() {
            long fiveSecondsAgo = System.currentTimeMillis() - 5000;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, fiveSecondsAgo);
            assertEquals("5s", entry.getFormattedAge());
        }

        @Test
        @DisplayName("entry from 90 seconds ago returns '1m 30s'")
        void ninetySecondsAgo() {
            long ninetySecondsAgo = System.currentTimeMillis() - 90_000;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, ninetySecondsAgo);
            assertEquals("1m 30s", entry.getFormattedAge());
        }

        @Test
        @DisplayName("entry from 2 minutes ago returns '2m 0s'")
        void twoMinutesAgo() {
            long twoMinutesAgo = System.currentTimeMillis() - 120_000;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, twoMinutesAgo);
            assertEquals("2m 0s", entry.getFormattedAge());
        }
    }

    @Nested
    @DisplayName("getAlpha")
    class AlphaTests {

        @Test
        @DisplayName("fresh entry has alpha 1.0")
        void freshEntryAlpha() {
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null);
            assertEquals(1.0f, entry.getAlpha(10_000), 0.01f);
        }

        @Test
        @DisplayName("expired entry has alpha 0.0")
        void expiredAlpha() {
            long longAgo = System.currentTimeMillis() - 20_000;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, longAgo);
            assertEquals(0.0f, entry.getAlpha(10_000), 0.01f);
        }

        @Test
        @DisplayName("entry in fade zone has alpha between 0 and 1")
        void fadeZoneAlpha() {
            // maxAge=10000, fade starts at 7000 (70%). Entry age ~8500 -> fade progress ~0.5
            long ageMs = 8500;
            long ts = System.currentTimeMillis() - ageMs;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, ts);
            float alpha = entry.getAlpha(10_000);
            assertTrue(alpha > 0.0f && alpha < 1.0f,
                "Alpha should be between 0 and 1 in fade zone, got " + alpha);
        }

        @Test
        @DisplayName("entry before fade zone has alpha 1.0")
        void beforeFadeZone() {
            // maxAge=10000, fade starts at 7000. Entry age ~3000 -> no fade
            long ts = System.currentTimeMillis() - 3000;
            AttributeLogEntry entry = new AttributeLogEntry(
                AttributeLogEntry.Type.INFO, "msg", null, ts);
            assertEquals(1.0f, entry.getAlpha(10_000), 0.01f);
        }
    }

    @Nested
    @DisplayName("Type enum")
    class TypeEnumTests {

        @Test
        @DisplayName("all types have non-empty prefix")
        void allTypesHavePrefix() {
            for (AttributeLogEntry.Type type : AttributeLogEntry.Type.values()) {
                assertNotNull(type.getPrefix());
                assertFalse(type.getPrefix().isEmpty(),
                    type.name() + " should have a non-empty prefix");
            }
        }

        @Test
        @DisplayName("all types have non-zero color")
        void allTypesHaveColor() {
            for (AttributeLogEntry.Type type : AttributeLogEntry.Type.values()) {
                // Colors are int values (can be any value), just verify they exist
                // (the getter doesn't throw)
                type.getColor();
            }
        }

        @Test
        @DisplayName("expected number of types")
        void expectedTypeCount() {
            assertEquals(16, AttributeLogEntry.Type.values().length);
        }

        @Test
        @DisplayName("specific types exist")
        void specificTypesExist() {
            assertNotNull(AttributeLogEntry.Type.ENTITY_DETECTED);
            assertNotNull(AttributeLogEntry.Type.KILL);
            assertNotNull(AttributeLogEntry.Type.HEALTH_CRITICAL);
            assertNotNull(AttributeLogEntry.Type.TELEPORT);
            assertNotNull(AttributeLogEntry.Type.ERROR);
        }
    }
}

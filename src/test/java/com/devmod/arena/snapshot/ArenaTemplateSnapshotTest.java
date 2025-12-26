package com.devmod.arena.snapshot;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaTemplateSnapshotTest {

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Snapshot configuration map is immutable")
        void snapshotConfigurationIsImmutable() {
            Map<String, Object> mutableConfig = new HashMap<>();
            mutableConfig.put("key1", "value1");

            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(mutableConfig)
                .build();

            // Modify original map
            mutableConfig.put("key2", "value2");

            // Snapshot should not be affected
            assertFalse(snapshot.configuration().containsKey("key2"),
                "Snapshot configuration should not reflect changes to original map");
        }

        @Test
        @DisplayName("Snapshot configuration map cannot be modified after creation")
        void snapshotConfigurationCannotBeModified() {
            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(Map.of("key1", "value1"))
                .build();

            assertThrows(UnsupportedOperationException.class, () -> {
                snapshot.configuration().put("key2", "value2");
            }, "Snapshot configuration should be unmodifiable");
        }

        @Test
        @DisplayName("Snapshot metrics map is immutable")
        void snapshotMetricsIsImmutable() {
            Map<String, Object> mutableMetrics = new HashMap<>();
            mutableMetrics.put("metric1", 100);

            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .metrics(mutableMetrics)
                .build();

            // Modify original map
            mutableMetrics.put("metric2", 200);

            // Snapshot should not be affected
            assertFalse(snapshot.metrics().containsKey("metric2"),
                "Snapshot metrics should not reflect changes to original map");
        }

        @Test
        @DisplayName("withUpdatedMetrics creates new immutable snapshot")
        void withUpdatedMetricsCreatesNewSnapshot() {
            ArenaTemplateSnapshot original = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .metrics(Map.of("metric1", 100))
                .build();

            ArenaTemplateSnapshot updated = original.withUpdatedMetrics(Map.of("metric1", 200));

            // Original should be unchanged
            assertEquals(100, original.metrics().get("metric1"));

            // Updated should have new value
            assertEquals(200, updated.metrics().get("metric1"));

            // Should be different objects
            assertNotSame(original, updated);
        }
    }

    @Nested
    @DisplayName("Version Drift Detection Tests")
    class VersionDriftTests {

        @Test
        @DisplayName("Detects version drift when versions differ")
        void detectsVersionDrift() {
            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .build();

            assertTrue(snapshot.hasVersionDrift("1.1.0"),
                "Should detect drift when versions differ");
            assertTrue(snapshot.hasVersionDrift("2.0.0"),
                "Should detect drift for major version change");
        }

        @Test
        @DisplayName("No version drift when versions match")
        void noVersionDriftWhenVersionsMatch() {
            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .build();

            assertFalse(snapshot.hasVersionDrift("1.0.0"),
                "Should not detect drift when versions match");
        }

        @Test
        @DisplayName("Detects configuration drift via checksum")
        void detectsConfigurationDrift() {
            ArenaTemplateSnapshot snapshot1 = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(Map.of("setting", "value1"))
                .build();

            ArenaTemplateSnapshot snapshot2 = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(Map.of("setting", "value2"))
                .build();

            assertTrue(snapshot1.hasConfigurationDrift(snapshot2),
                "Should detect configuration drift when configs differ");
        }

        @Test
        @DisplayName("No configuration drift for identical configs")
        void noConfigurationDriftForIdenticalConfigs() {
            Map<String, Object> config = Map.of("setting", "value1");

            ArenaTemplateSnapshot snapshot1 = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(config)
                .build();

            ArenaTemplateSnapshot snapshot2 = ArenaTemplateSnapshot.builder()
                .sessionId(UUID.randomUUID())
                .templateId("test-template")
                .templateVersion("1.0.0")
                .configuration(config)
                .build();

            assertFalse(snapshot1.hasConfigurationDrift(snapshot2),
                "Should not detect drift for identical configurations");
        }
    }

    @Nested
    @DisplayName("Session Duration Tests")
    class SessionDurationTests {

        @Test
        @DisplayName("Calculates session duration correctly")
        void calculatesSessionDuration() {
            Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
            Instant snapshotAt = Instant.parse("2024-01-01T10:05:00Z");

            ArenaTemplateSnapshot snapshot = new ArenaTemplateSnapshot(
                UUID.randomUUID(),
                "test-template",
                "1.0.0",
                createdAt,
                snapshotAt,
                Map.of(),
                Map.of(),
                null
            );

            assertEquals(5 * 60 * 1000, snapshot.sessionDurationMs(),
                "Should calculate 5 minutes duration in milliseconds");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder generates UUID if not provided")
        void builderGeneratesUuid() {
            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .templateId("test-template")
                .templateVersion("1.0.0")
                .build();

            assertNotNull(snapshot.sessionId());
        }

        @Test
        @DisplayName("Builder generates timestamps if not provided")
        void builderGeneratesTimestamps() {
            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.builder()
                .templateId("test-template")
                .templateVersion("1.0.0")
                .build();

            assertNotNull(snapshot.createdAt());
            assertNotNull(snapshot.snapshotAt());
        }

        @Test
        @DisplayName("Static create method works correctly")
        void staticCreateMethodWorks() {
            UUID sessionId = UUID.randomUUID();
            Instant createdAt = Instant.now().minusSeconds(60);

            ArenaTemplateSnapshot snapshot = ArenaTemplateSnapshot.create(
                sessionId,
                "test-template",
                "1.0.0",
                createdAt,
                Map.of("key", "value"),
                Map.of("metric", 100)
            );

            assertEquals(sessionId, snapshot.sessionId());
            assertEquals("test-template", snapshot.templateId());
            assertEquals(createdAt, snapshot.createdAt());
            assertTrue(snapshot.snapshotAt().isAfter(createdAt));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Throws on null sessionId")
        void throwsOnNullSessionId() {
            assertThrows(NullPointerException.class, () -> {
                new ArenaTemplateSnapshot(
                    null,
                    "test-template",
                    "1.0.0",
                    Instant.now(),
                    Instant.now(),
                    Map.of(),
                    Map.of(),
                    null
                );
            });
        }

        @Test
        @DisplayName("Throws on null templateId")
        void throwsOnNullTemplateId() {
            assertThrows(NullPointerException.class, () -> {
                new ArenaTemplateSnapshot(
                    UUID.randomUUID(),
                    null,
                    "1.0.0",
                    Instant.now(),
                    Instant.now(),
                    Map.of(),
                    Map.of(),
                    null
                );
            });
        }

        @Test
        @DisplayName("Handles null configuration gracefully")
        void handlesNullConfiguration() {
            ArenaTemplateSnapshot snapshot = new ArenaTemplateSnapshot(
                UUID.randomUUID(),
                "test-template",
                "1.0.0",
                Instant.now(),
                Instant.now(),
                null,
                null,
                null
            );

            assertNotNull(snapshot.configuration());
            assertTrue(snapshot.configuration().isEmpty());
        }

        @Test
        @DisplayName("Generates checksum if not provided")
        void generatesChecksumIfNotProvided() {
            ArenaTemplateSnapshot snapshot = new ArenaTemplateSnapshot(
                UUID.randomUUID(),
                "test-template",
                "1.0.0",
                Instant.now(),
                Instant.now(),
                Map.of(),
                Map.of(),
                null
            );

            assertNotNull(snapshot.checksum());
            assertFalse(snapshot.checksum().isBlank());
        }
    }
}

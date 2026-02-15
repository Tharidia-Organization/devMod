package com.devmod.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryIntentPhaseTest {

    // === Phase Enum ===

    @Test
    @DisplayName("Phase has 5 values in expected order")
    void phaseHasFiveValues() {
        RecoveryIntent.Phase[] phases = RecoveryIntent.Phase.values();
        assertEquals(5, phases.length);
        assertEquals(RecoveryIntent.Phase.INITIATED, phases[0]);
        assertEquals(RecoveryIntent.Phase.TELEPORTED, phases[1]);
        assertEquals(RecoveryIntent.Phase.INVENTORY_RESTORED, phases[2]);
        assertEquals(RecoveryIntent.Phase.EFFECTS_RESTORED, phases[3]);
        assertEquals(RecoveryIntent.Phase.COMPLETED, phases[4]);
    }

    // === RecoveryIntent Construction ===

    @Test
    @DisplayName("RecoveryIntent initializes with INITIATED phase")
    void constructorInitializesPhase() {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        RecoveryIntent intent = new RecoveryIntent(playerId, instanceId, "test_reason");

        assertEquals(playerId, intent.getPlayerId());
        assertEquals(instanceId, intent.getInstanceId());
        assertEquals("test_reason", intent.getReason());
        assertEquals(RecoveryIntent.Phase.INITIATED, intent.getPhase());
        assertTrue(intent.getCreatedAt() > 0);
        assertTrue(intent.getUpdatedAt() > 0);
    }

    // === File Operations ===

    @Nested
    class FileOperations {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getIntentFile constructs correct path")
        void getIntentFileConstructsPath() {
            UUID playerId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            Path file = RecoveryIntent.getIntentFile(tempDir, playerId);
            assertEquals("12345678-1234-1234-1234-123456789abc.intent.json", file.getFileName().toString());
        }

        @Test
        @DisplayName("exists returns false for non-existent intent")
        void existsReturnsFalseForNonExistent() {
            assertFalse(RecoveryIntent.exists(tempDir, UUID.randomUUID()));
        }

        @Test
        @DisplayName("saveToFile and loadFromFile roundtrip correctly")
        void saveAndLoadRoundTrip() throws IOException {
            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();
            RecoveryIntent intent = new RecoveryIntent(playerId, instanceId, "crash_recovery");

            Path file = RecoveryIntent.getIntentFile(tempDir, playerId);
            intent.saveToFile(file);

            assertTrue(Files.exists(file));

            RecoveryIntent loaded = RecoveryIntent.loadFromFile(file);
            assertEquals(playerId, loaded.getPlayerId());
            assertEquals(instanceId, loaded.getInstanceId());
            assertEquals("crash_recovery", loaded.getReason());
            assertEquals(RecoveryIntent.Phase.INITIATED, loaded.getPhase());
        }

        @Test
        @DisplayName("delete removes intent file")
        void deleteRemovesFile() throws IOException {
            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();
            RecoveryIntent intent = new RecoveryIntent(playerId, instanceId, "test");

            Path file = RecoveryIntent.getIntentFile(tempDir, playerId);
            intent.saveToFile(file);
            assertTrue(Files.exists(file));

            boolean deleted = RecoveryIntent.delete(tempDir, playerId);
            assertTrue(deleted);
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("delete returns false for non-existent file")
        void deleteReturnsFalseForNonExistent() {
            assertFalse(RecoveryIntent.delete(tempDir, UUID.randomUUID()));
        }
    }

    // === toString ===

    @Test
    @DisplayName("toString includes key fields")
    void toStringIncludesKeyFields() {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        RecoveryIntent intent = new RecoveryIntent(playerId, instanceId, "test");

        String str = intent.toString();
        assertTrue(str.contains(playerId.toString()));
        assertTrue(str.contains(instanceId.toString()));
        assertTrue(str.contains("INITIATED"));
        assertTrue(str.contains("test"));
    }
}

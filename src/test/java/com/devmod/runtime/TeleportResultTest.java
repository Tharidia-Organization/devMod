package com.devmod.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportResultTest {

    @Test
    @DisplayName("TeleportResult has 7 values")
    void teleportResultHasSevenValues() {
        assertEquals(7, TeleportTransaction.TeleportResult.values().length);
    }

    @Test
    @DisplayName("TeleportResult contains expected values")
    void teleportResultContainsExpectedValues() {
        assertNotNull(TeleportTransaction.TeleportResult.SUCCESS);
        assertNotNull(TeleportTransaction.TeleportResult.LOCK_FAILED);
        assertNotNull(TeleportTransaction.TeleportResult.DIMENSION_NOT_FOUND);
        assertNotNull(TeleportTransaction.TeleportResult.INSTANCE_INVALID_STATE);
        assertNotNull(TeleportTransaction.TeleportResult.TELEPORT_FAILED);
        assertNotNull(TeleportTransaction.TeleportResult.PLAYER_DISCONNECTED);
        assertNotNull(TeleportTransaction.TeleportResult.RECOVERED_AFTER_FAILURE);
    }

    @Test
    @DisplayName("TeleportResult valueOf works for all values")
    void valueOfWorksForAll() {
        for (TeleportTransaction.TeleportResult result : TeleportTransaction.TeleportResult.values()) {
            assertEquals(result, TeleportTransaction.TeleportResult.valueOf(result.name()));
        }
    }
}

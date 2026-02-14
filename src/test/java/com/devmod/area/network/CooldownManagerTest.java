package com.devmod.area.network;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Area Cooldown Manager")
class CooldownManagerTest {

    @Test
    @DisplayName("player cooldown rollback restores availability")
    void playerCooldownRollbackRestoresAvailability() {
        UUID playerId = UUID.randomUUID();
        long now = 1_000_000L;

        assertEquals(0L, CooldownManager.checkAndUpdatePlayerBuildCooldown(playerId, now));
        assertTrue(CooldownManager.checkAndUpdatePlayerBuildCooldown(playerId, now + 1) > 0L);

        CooldownManager.rollbackPlayerBuildCooldown(playerId, now);

        assertEquals(0L, CooldownManager.checkAndUpdatePlayerBuildCooldown(playerId, now));
    }

    @Test
    @DisplayName("area cooldown clear restores availability")
    void areaCooldownClearRestoresAvailability() {
        UUID areaId = UUID.randomUUID();
        long now = 2_000_000L;

        assertEquals(0L, CooldownManager.checkAndUpdateAreaBuildCooldown(areaId, now));
        assertTrue(CooldownManager.checkAndUpdateAreaBuildCooldown(areaId, now + 1) > 0L);

        CooldownManager.clearAreaBuildCooldown(areaId);

        assertEquals(0L, CooldownManager.checkAndUpdateAreaBuildCooldown(areaId, now));
    }

    @Test
    @DisplayName("clear null area ID is safe")
    void clearNullAreaIdIsSafe() {
        // clearAreaBuildCooldown handles null gracefully
        CooldownManager.clearAreaBuildCooldown(null);
    }
}

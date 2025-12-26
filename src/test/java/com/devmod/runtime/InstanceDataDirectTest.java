package com.devmod.runtime;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceDataDirectTest {

    @Test
    @DisplayName("Players can only join when instance is READY or ACTIVE and capacity allows")
    void playersJoinOnlyWhenReadyOrActive() {
        InstanceData instance = InstanceData.createParty(UUID.randomUUID(), 2);
        UUID playerOne = UUID.randomUUID();

        assertFalse(instance.addPlayer(playerOne));

        instance.setState(InstanceState.READY);
        assertTrue(instance.addPlayer(playerOne));
        assertTrue(instance.addPlayer(UUID.randomUUID()));
        assertFalse(instance.addPlayer(UUID.randomUUID()));
        assertTrue(instance.isFull());
        assertEquals(2, instance.getPlayerCount());
    }

    @Test
    @DisplayName("Removing last player schedules destruction in READY/ACTIVE")
    void removeSchedulesDestructionWhenEmpty() {
        InstanceData instance = InstanceData.createSolo(UUID.randomUUID());
        instance.setState(InstanceState.READY);

        UUID playerId = UUID.randomUUID();
        assertTrue(instance.addPlayer(playerId));
        assertTrue(instance.removePlayer(playerId));
        assertTrue(instance.isMarkedForDestruction());

        InstanceData completingInstance = InstanceData.createSolo(UUID.randomUUID());
        completingInstance.setState(InstanceState.READY);
        UUID completingPlayer = UUID.randomUUID();
        assertTrue(completingInstance.addPlayer(completingPlayer));
        completingInstance.setState(InstanceState.COMPLETING);
        assertTrue(completingInstance.removePlayer(completingPlayer));
        assertFalse(completingInstance.isMarkedForDestruction());
    }

    @Test
    @DisplayName("Invalid state transitions return false but still update state")
    void invalidTransitionReturnsFalseButUpdatesState() {
        InstanceData instance = InstanceData.createSolo(UUID.randomUUID());
        boolean valid = instance.setState(InstanceState.ACTIVE);

        assertFalse(valid);
        assertEquals(InstanceState.ACTIVE, instance.getState());
    }

    @Test
    @DisplayName("Scheduled destruction becomes ready after delay")
    void scheduledDestructionBecomesReadyAfterDelay() throws Exception {
        InstanceData instance = InstanceData.createSolo(UUID.randomUUID());
        instance.scheduleDestruction();

        Field markedField = InstanceData.class.getDeclaredField("markedForDestructionAt");
        markedField.setAccessible(true);
        markedField.setLong(instance, System.currentTimeMillis() - InstanceData.DESTROY_DELAY_MS - 1);

        assertTrue(instance.shouldDestroy());
    }
}

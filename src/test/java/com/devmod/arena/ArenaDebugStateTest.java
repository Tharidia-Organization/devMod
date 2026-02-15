package com.devmod.arena;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArenaDebugStateTest {

    private ArenaDebugState state;

    @BeforeEach
    void setUp() {
        state = ArenaDebugState.getInstance();
        state.resetAllStates();
        state.setGlobalHudEnabled(true);
    }

    @Test
    void singletonInstance() {
        assertSame(ArenaDebugState.getInstance(), ArenaDebugState.getInstance());
    }

    @Test
    void defaultHudDisabledForPlayer() {
        UUID player = UUID.randomUUID();
        assertFalse(state.isHudEnabled(player));
    }

    @Test
    void enableHud() {
        UUID player = UUID.randomUUID();
        state.enableHud(player);
        assertTrue(state.isHudEnabled(player));
    }

    @Test
    void disableHud() {
        UUID player = UUID.randomUUID();
        state.enableHud(player);
        state.disableHud(player);
        assertFalse(state.isHudEnabled(player));
    }

    @Test
    void toggleHud() {
        UUID player = UUID.randomUUID();
        assertTrue(state.toggleHud(player));
        assertFalse(state.toggleHud(player));
    }

    @Test
    void globalDisableOverridesPlayer() {
        UUID player = UUID.randomUUID();
        state.enableHud(player);
        state.setGlobalHudEnabled(false);
        assertFalse(state.isHudEnabled(player));
    }

    @Test
    void clearPlayerState() {
        UUID player = UUID.randomUUID();
        state.enableHud(player);
        state.clearPlayerState(player);
        assertFalse(state.isHudEnabled(player));
    }

    @Test
    void enabledPlayerCount() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        state.enableHud(p1);
        state.enableHud(p2);
        state.disableHud(p3);
        assertEquals(2, state.getEnabledPlayerCount());
    }

    @Test
    void resetAllStates() {
        state.enableHud(UUID.randomUUID());
        state.enableHud(UUID.randomUUID());
        state.resetAllStates();
        assertEquals(0, state.getEnabledPlayerCount());
    }
}

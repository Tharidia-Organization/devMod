package com.devmod.client.ui.editor.controller;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OverlayControllerTest {

    private OverlayController controller;

    @BeforeEach
    void setUp() {
        controller = new OverlayController();
    }

    @Test
    void initialStateIsNone() {
        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
        assertFalse(controller.hasActiveOverlay());
    }

    @Test
    void toggleOpensOverlay() {
        controller.toggle(OverlayController.OverlayType.HISTORY);

        assertEquals(OverlayController.OverlayType.HISTORY, controller.getActive());
        assertTrue(controller.hasActiveOverlay());
        assertTrue(controller.isHistoryVisible());
    }

    @Test
    void toggleSameOverlayCloses() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        assertTrue(controller.isHistoryVisible());

        controller.toggle(OverlayController.OverlayType.HISTORY);
        assertFalse(controller.isHistoryVisible());
        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
    }

    @Test
    void toggleDifferentOverlaySwitches() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        assertTrue(controller.isHistoryVisible());

        controller.toggle(OverlayController.OverlayType.PRESETS);
        assertFalse(controller.isHistoryVisible());
        assertTrue(controller.isPresetsVisible());
    }

    @Test
    void closeReturnsToNone() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        controller.close();

        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
        assertFalse(controller.hasActiveOverlay());
    }

    @Test
    void closeReturnsToPreviousOverlay() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        controller.toggle(OverlayController.OverlayType.PRESETS);

        // Previous was HISTORY
        assertEquals(OverlayController.OverlayType.HISTORY, controller.getPrevious());

        controller.close();

        // Should return to HISTORY
        assertEquals(OverlayController.OverlayType.HISTORY, controller.getActive());
        assertTrue(controller.isHistoryVisible());
    }

    @Test
    void closeAllResetsEverything() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        controller.toggle(OverlayController.OverlayType.PRESETS);

        controller.closeAll();

        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
        assertEquals(OverlayController.OverlayType.NONE, controller.getPrevious());
        assertFalse(controller.hasActiveOverlay());
    }

    @Test
    void openSetsOverlayDirectly() {
        controller.open(OverlayController.OverlayType.TEMPLATES);

        assertEquals(OverlayController.OverlayType.TEMPLATES, controller.getActive());
        assertTrue(controller.isTemplatesVisible());
    }

    @Test
    void openWithNoneCloses() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        controller.open(OverlayController.OverlayType.NONE);

        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
    }

    @Test
    void isActiveChecksCorrectly() {
        controller.toggle(OverlayController.OverlayType.CRAFTING);

        assertTrue(controller.isActive(OverlayController.OverlayType.CRAFTING));
        assertFalse(controller.isActive(OverlayController.OverlayType.HISTORY));
        assertFalse(controller.isActive(OverlayController.OverlayType.NONE));
    }

    @Test
    void visibilityMethodsWorkCorrectly() {
        assertFalse(controller.isHistoryVisible());
        assertFalse(controller.isPresetsVisible());
        assertFalse(controller.isTemplatesVisible());
        assertFalse(controller.isCraftingVisible());

        controller.toggle(OverlayController.OverlayType.HISTORY);
        assertTrue(controller.isHistoryVisible());
        assertFalse(controller.isPresetsVisible());

        controller.toggle(OverlayController.OverlayType.PRESETS);
        assertFalse(controller.isHistoryVisible());
        assertTrue(controller.isPresetsVisible());

        controller.toggle(OverlayController.OverlayType.TEMPLATES);
        assertTrue(controller.isTemplatesVisible());

        controller.toggle(OverlayController.OverlayType.CRAFTING);
        assertTrue(controller.isCraftingVisible());
    }

    @Test
    void callbackIsInvokedOnChange() {
        AtomicReference<OverlayController.OverlayType> lastChange = new AtomicReference<>();
        controller.setOnOverlayChanged(lastChange::set);

        controller.toggle(OverlayController.OverlayType.HISTORY);
        assertEquals(OverlayController.OverlayType.HISTORY, lastChange.get());

        controller.close();
        assertEquals(OverlayController.OverlayType.NONE, lastChange.get());
    }

    @Test
    void nullCallbackDoesNotThrow() {
        controller.setOnOverlayChanged(null);
        assertDoesNotThrow(() -> controller.toggle(OverlayController.OverlayType.HISTORY));
    }

    @Test
    void toggleWithNullTreatedAsNone() {
        controller.toggle(OverlayController.OverlayType.HISTORY);
        controller.toggle(null);

        // Should close (toggle to NONE)
        assertEquals(OverlayController.OverlayType.NONE, controller.getActive());
    }
}

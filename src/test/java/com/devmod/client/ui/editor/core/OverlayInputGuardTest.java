package com.devmod.client.ui.editor.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayInputGuardTest {

    @Test
    void presetsOverlayConsumesInput() {
        assertTrue(OverlayInputGuard.shouldConsumePresetInput(true));
    }

    @Test
    void presetsOverlayAllowsInputWhenClosed() {
        assertFalse(OverlayInputGuard.shouldConsumePresetInput(false));
    }
}

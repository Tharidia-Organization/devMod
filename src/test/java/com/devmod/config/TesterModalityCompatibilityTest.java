package com.devmod.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TesterModality Compatibility")
class TesterModalityCompatibilityTest {

    @Test
    @DisplayName("client=false/server=false is compatible")
    void bothDisabledCompatible() {
        assertTrue(TesterModality.isCompatible(false, false));
    }

    @Test
    @DisplayName("client=true/server=true is compatible")
    void bothEnabledCompatible() {
        assertTrue(TesterModality.isCompatible(true, true));
    }

    @Test
    @DisplayName("client=true/server=false is incompatible")
    void clientEnabledServerDisabledIncompatible() {
        assertFalse(TesterModality.isCompatible(false, true));
    }

    @Test
    @DisplayName("client=false/server=true is incompatible")
    void clientDisabledServerEnabledIncompatible() {
        assertFalse(TesterModality.isCompatible(true, false));
    }

    @Test
    @DisplayName("mismatch reason is deterministic")
    void mismatchReasonDeterministic() {
        assertEquals(
            "TesterModality mismatch: server=true, client=false. Access denied.",
            TesterModality.mismatchReason(true, false));
    }
}

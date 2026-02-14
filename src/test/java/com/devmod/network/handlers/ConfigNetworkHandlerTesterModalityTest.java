package com.devmod.network.handlers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.config.TesterModality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TesterModality handshake decision logic.
 *
 * The disconnect reason logic was moved from ConfigNetworkHandler to
 * TesterModality.isCompatible() + TesterModality.mismatchReason().
 */
@DisplayName("TesterModality Handshake Decision")
class ConfigNetworkHandlerTesterModalityTest {

    @Test
    @DisplayName("client=false/server=false allows connection")
    void bothDisabledAllowsConnection() {
        assertTrue(TesterModality.isCompatible(false, false));
    }

    @Test
    @DisplayName("client=true/server=true allows connection")
    void bothEnabledAllowsConnection() {
        assertTrue(TesterModality.isCompatible(true, true));
    }

    @Test
    @DisplayName("client=true/server=false denies connection")
    void clientEnabledServerDisabledDeniesConnection() {
        assertFalse(TesterModality.isCompatible(false, true));
        String reason = TesterModality.mismatchReason(false, true);
        assertNotNull(reason);
        assertEquals(
            "TesterModality mismatch: server=false, client=true. Access denied.",
            reason);
    }

    @Test
    @DisplayName("client=false/server=true denies connection")
    void clientDisabledServerEnabledDeniesConnection() {
        assertFalse(TesterModality.isCompatible(true, false));
        String reason = TesterModality.mismatchReason(true, false);
        assertNotNull(reason);
        assertEquals(
            "TesterModality mismatch: server=true, client=false. Access denied.",
            reason);
    }
}

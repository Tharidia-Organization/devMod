package com.devmod.transport.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportState;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transport Network Payloads")
class TransportPayloadTest {

    // =========================================================================
    // TransportCountdownPayload
    // =========================================================================

    @Nested
    @DisplayName("TransportCountdownPayload")
    class CountdownPayloadTests {

        @Test
        @DisplayName("getProgress returns 1.0 at full time")
        void progressFull() {
            TransportCountdownPayload payload = new TransportCountdownPayload(10, 10, 0, 0);
            assertEquals(1.0f, payload.getProgress(), 0.001f);
        }

        @Test
        @DisplayName("getProgress returns 0.5 at halfway")
        void progressHalf() {
            TransportCountdownPayload payload = new TransportCountdownPayload(5, 10, 0, 0);
            assertEquals(0.5f, payload.getProgress(), 0.001f);
        }

        @Test
        @DisplayName("getProgress returns 0.0 when done")
        void progressDone() {
            TransportCountdownPayload payload = new TransportCountdownPayload(0, 10, 0, 0);
            assertEquals(0.0f, payload.getProgress(), 0.001f);
        }

        @Test
        @DisplayName("getProgress returns 0.0 for zero totalSeconds")
        void progressZeroTotal() {
            TransportCountdownPayload payload = new TransportCountdownPayload(5, 0, 0, 0);
            assertEquals(0.0f, payload.getProgress(), 0.001f);
        }

        @Test
        @DisplayName("isWarning returns true for phase 1")
        void isWarning() {
            TransportCountdownPayload payload = new TransportCountdownPayload(3, 10, 1, 0);
            assertTrue(payload.isWarning());
            assertFalse(payload.isUrgent());
        }

        @Test
        @DisplayName("isUrgent returns true for phase 2")
        void isUrgent() {
            TransportCountdownPayload payload = new TransportCountdownPayload(1, 10, 2, 0);
            assertTrue(payload.isUrgent());
            assertFalse(payload.isWarning());
        }

        @Test
        @DisplayName("isComplete returns true when secondsRemaining is 0")
        void isCompleteZero() {
            TransportCountdownPayload payload = new TransportCountdownPayload(0, 10, 0, 0);
            assertTrue(payload.isComplete());
        }

        @Test
        @DisplayName("isComplete returns true when secondsRemaining is negative")
        void isCompleteNegative() {
            TransportCountdownPayload payload = new TransportCountdownPayload(-1, 10, 0, 0);
            assertTrue(payload.isComplete());
        }

        @Test
        @DisplayName("isComplete returns false when secondsRemaining is positive")
        void isCompletePositive() {
            TransportCountdownPayload payload = new TransportCountdownPayload(5, 10, 0, 0);
            assertFalse(payload.isComplete());
        }

        @Test
        @DisplayName("estimatedSize returns positive value")
        void estimatedSizePositive() {
            TransportCountdownPayload payload = new TransportCountdownPayload(10, 10, 0, 0);
            assertTrue(payload.estimatedSize() > 0);
        }
    }

    // =========================================================================
    // TransportChargeUpdatePayload
    // =========================================================================

    @Nested
    @DisplayName("TransportChargeUpdatePayload")
    class ChargeUpdatePayloadTests {

        @Test
        @DisplayName("isComplete returns true at 100%")
        void isCompleteAt100() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(40, 40, 100);
            assertTrue(payload.isComplete());
        }

        @Test
        @DisplayName("isComplete returns true above 100%")
        void isCompleteAbove100() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(50, 40, 125);
            assertTrue(payload.isComplete());
        }

        @Test
        @DisplayName("isComplete returns false below 100%")
        void notComplete() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(20, 40, 50);
            assertFalse(payload.isComplete());
        }

        @Test
        @DisplayName("getProgressFloat converts percent to 0.0-1.0")
        void progressFloat() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(20, 40, 50);
            assertEquals(0.5f, payload.getProgressFloat(), 0.001f);
        }

        @Test
        @DisplayName("getProgressFloat returns 0.0 for 0%")
        void progressFloatZero() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(0, 40, 0);
            assertEquals(0.0f, payload.getProgressFloat(), 0.001f);
        }

        @Test
        @DisplayName("getProgressFloat returns 1.0 for 100%")
        void progressFloat100() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(40, 40, 100);
            assertEquals(1.0f, payload.getProgressFloat(), 0.001f);
        }

        @Test
        @DisplayName("estimatedSize returns positive value")
        void estimatedSizePositive() {
            TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(20, 40, 50);
            assertTrue(payload.estimatedSize() > 0);
        }
    }

    // =========================================================================
    // TransportStatePayload
    // =========================================================================

    @Nested
    @DisplayName("TransportStatePayload")
    class StatePayloadTests {

        @Test
        @DisplayName("enter factory creates inTransport=true payload")
        void enterFactory() {
            TransportStatePayload payload = TransportStatePayload.enter(
                TransportState.CHARGING, TransportColor.CYAN, 10, 40, "Test", 100
            );
            assertTrue(payload.inTransport());
            assertEquals(TransportState.CHARGING.ordinal(), payload.stateIndex());
            assertEquals(TransportColor.CYAN.getIndex(), payload.colorIndex());
            assertEquals(10, payload.currentCharge());
            assertEquals(40, payload.requiredCharge());
            assertEquals("Test", payload.destinationName());
            assertEquals(100, payload.distance());
        }

        @Test
        @DisplayName("exit factory creates inTransport=false payload")
        void exitFactory() {
            TransportStatePayload payload = TransportStatePayload.exit();
            assertFalse(payload.inTransport());
            assertEquals(0, payload.stateIndex());
            assertEquals("", payload.destinationName());
        }

        @Test
        @DisplayName("getState returns correct enum from index")
        void getState() {
            TransportStatePayload payload = TransportStatePayload.enter(
                TransportState.ERROR, TransportColor.RED, 0, 0, "", 0
            );
            assertEquals(TransportState.ERROR, payload.getState());
        }

        @Test
        @DisplayName("getState clamps invalid index")
        void getStateClampsInvalid() {
            // Create payload with out-of-range stateIndex
            TransportStatePayload payload = new TransportStatePayload(true, 999, 0, 0, 0, "", 0);
            TransportState state = payload.getState();
            assertNotNull(state);
            // Should clamp to last value
            assertEquals(TransportState.values()[TransportState.values().length - 1], state);
        }

        @Test
        @DisplayName("getState clamps negative index to first")
        void getStateClampsNegative() {
            TransportStatePayload payload = new TransportStatePayload(true, -5, 0, 0, 0, "", 0);
            assertEquals(TransportState.values()[0], payload.getState());
        }

        @Test
        @DisplayName("getColor returns correct color from index")
        void getColor() {
            TransportStatePayload payload = TransportStatePayload.enter(
                TransportState.IDLE, TransportColor.PURPLE, 0, 0, "", 0
            );
            assertEquals(TransportColor.PURPLE, payload.getColor());
        }

        @Test
        @DisplayName("estimatedSize returns positive value")
        void estimatedSizePositive() {
            TransportStatePayload payload = TransportStatePayload.enter(
                TransportState.CHARGING, TransportColor.CYAN, 10, 40, "Destination", 500
            );
            assertTrue(payload.estimatedSize() > 0);
        }

        @Test
        @DisplayName("estimatedSize accounts for destination name length")
        void estimatedSizeScalesWithName() {
            TransportStatePayload small = TransportStatePayload.enter(
                TransportState.IDLE, TransportColor.CYAN, 0, 0, "A", 0
            );
            TransportStatePayload large = TransportStatePayload.enter(
                TransportState.IDLE, TransportColor.CYAN, 0, 0, "A".repeat(48), 0
            );
            assertTrue(large.estimatedSize() >= small.estimatedSize());
        }
    }
}

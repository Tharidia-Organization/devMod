package com.devmod.nexus.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NexusBuildProgressPayload")
class NexusBuildProgressPayloadTest {

    // ========================================================================
    // Progress Percentage Tests
    // ========================================================================

    @Nested
    @DisplayName("Progress Percentage")
    class ProgressPercentTests {

        @Test
        @DisplayName("Progress at step 0 is 0%")
        void progressAtZeroIsZero() {
            var payload = NexusBuildProgressPayload.progress(0, 10, "floor");
            assertEquals(0.0f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Progress at step 5 of 10 is 50%")
        void progressAtHalfIs50() {
            var payload = NexusBuildProgressPayload.progress(5, 10, "walls");
            assertEquals(0.5f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Progress at step 10 of 10 is 100%")
        void progressAtFullIs100() {
            var payload = NexusBuildProgressPayload.progress(10, 10, "done");
            assertEquals(1.0f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Progress beyond total is clamped to 100%")
        void progressBeyondTotalClampedTo100() {
            var payload = NexusBuildProgressPayload.progress(15, 10, "overflow");
            assertEquals(1.0f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Progress with zero total returns 0%")
        void progressWithZeroTotalReturnsZero() {
            var payload = NexusBuildProgressPayload.progress(5, 0, "broken");
            assertEquals(0.0f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Progress with negative total returns 0%")
        void progressWithNegativeTotalReturnsZero() {
            var payload = NexusBuildProgressPayload.progress(5, -1, "neg");
            assertEquals(0.0f, payload.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("Completed payload always returns 100%")
        void completedPayloadAlwaysReturns100() {
            var payload = NexusBuildProgressPayload.completed(10);
            assertEquals(1.0f, payload.getProgressPercent(), 0.001f);
        }
    }

    // ========================================================================
    // Factory Method Tests
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("progress() creates in-progress payload")
        void progressFactoryCreatesInProgress() {
            var payload = NexusBuildProgressPayload.progress(3, 8, "corridors");
            assertEquals(3, payload.currentStep());
            assertEquals(8, payload.totalSteps());
            assertEquals("corridors", payload.stepName());
            assertFalse(payload.complete());
        }

        @Test
        @DisplayName("progress() with null step name")
        void progressFactoryWithNullStepName() {
            var payload = NexusBuildProgressPayload.progress(1, 5, null);
            assertNull(payload.stepName());
            assertFalse(payload.complete());
        }

        @Test
        @DisplayName("completed() creates completion payload")
        void completedFactoryCreatesComplete() {
            var payload = NexusBuildProgressPayload.completed(10);
            assertEquals(10, payload.currentStep());
            assertEquals(10, payload.totalSteps());
            assertNull(payload.stepName());
            assertTrue(payload.complete());
        }
    }

    // ========================================================================
    // Record Field Tests
    // ========================================================================

    @Nested
    @DisplayName("Record Fields")
    class RecordFieldTests {

        @Test
        @DisplayName("All fields are accessible")
        void allFieldsAccessible() {
            var payload = new NexusBuildProgressPayload(3, 8, "walls", false);
            assertEquals(3, payload.currentStep());
            assertEquals(8, payload.totalSteps());
            assertEquals("walls", payload.stepName());
            assertFalse(payload.complete());
        }

        @Test
        @DisplayName("Equals works for identical payloads")
        void equalsWorksForIdentical() {
            var a = new NexusBuildProgressPayload(2, 5, "floor", false);
            var b = new NexusBuildProgressPayload(2, 5, "floor", false);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Equals distinguishes different payloads")
        void equalsDistinguishesDifferent() {
            var a = new NexusBuildProgressPayload(2, 5, "floor", false);
            var b = new NexusBuildProgressPayload(3, 5, "floor", false);
            assertNotEquals(a, b);
        }
    }
}

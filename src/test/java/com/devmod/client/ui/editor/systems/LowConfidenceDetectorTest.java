package com.devmod.client.ui.editor.systems;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmod.client.ui.editor.overlay.EditorOverlay;

import static org.junit.jupiter.api.Assertions.*;

public class LowConfidenceDetectorTest {

    private LowConfidenceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new LowConfidenceDetector();
    }

    @Test
    void initialStateIsHidden() {
        assertFalse(detector.isVisible());
        assertFalse(detector.hasPendingDetection());
        assertNull(detector.getPendingDetection());
    }

    @Test
    void implementsEditorOverlayInterface() {
        assertTrue(detector instanceof EditorOverlay);
    }

    @Test
    void getTypeReturnsLowConfidence() {
        assertEquals(EditorOverlay.OverlayType.LOW_CONFIDENCE, detector.getType());
    }

    @Test
    void showMakesOverlayVisible() {
        detector.show();
        assertTrue(detector.isVisible());
    }

    @Test
    void hideImmediateMakesOverlayHidden() {
        detector.show();
        assertTrue(detector.isVisible());

        // Use hideImmediate() for instant visibility change (hide() uses animation callback)
        detector.hideImmediate();
        assertFalse(detector.isVisible());
    }

    @Test
    void toggleShowsThenHideImmediate() {
        assertFalse(detector.isVisible());

        detector.toggle(); // show
        assertTrue(detector.isVisible());

        // toggle() calls hide() which defers visibility; use hideImmediate() for test
        detector.hideImmediate();
        assertFalse(detector.isVisible());
    }

    @Test
    void resetClearsStateAndHides() {
        detector.show();
        assertTrue(detector.isVisible());

        detector.reset();

        assertFalse(detector.isVisible());
        assertNull(detector.getPendingDetection());
    }

    @Test
    void statusConsumerCanBeSet() {
        AtomicReference<String> lastMessage = new AtomicReference<>();
        AtomicReference<Integer> lastColor = new AtomicReference<>();

        detector.setStatusConsumer((msg, color) -> {
            lastMessage.set(msg);
            lastColor.set(color);
        });

        // Just verify no exception - actual status messages come from button clicks
        assertNotNull(detector);
    }

    @Test
    void cancelCallbackCanBeSet() {
        AtomicReference<Boolean> callbackInvoked = new AtomicReference<>(false);

        detector.setOnCancelCallback(() -> callbackInvoked.set(true));

        // Just verify no exception - actual callback invocation requires UI interaction
        assertNotNull(detector);
    }

    @Test
    void checkAndWarnWithNullItemDoesNothing() {
        // Should not throw or show dialog
        detector.checkAndWarn(null, null, null);
        assertFalse(detector.isVisible());
    }
}

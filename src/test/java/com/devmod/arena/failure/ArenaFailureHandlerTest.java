package com.devmod.arena.failure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArenaFailureHandler.
 * DD46: Default Fail Message - user-friendly, no tech details.
 */
@DisplayName("ArenaFailureHandler Tests")
class ArenaFailureHandlerTest {

    private ArenaFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ArenaFailureHandler();
    }

    @Test
    @DisplayName("DD46: Player message contains no tech details")
    void playerMessageNoTechDetails() {
        RuntimeException technicalException = new RuntimeException(
            "java.sql.SQLException: Connection refused to host: 192.168.1.1"
        );

        String playerMessage = handler.handleFailure(
            FailureType.INTERNAL_ERROR,
            technicalException,
            "Database connection failed"
        );

        // Player message should NOT contain tech details
        assertFalse(playerMessage.contains("SQLException"));
        assertFalse(playerMessage.contains("192.168.1.1"));
        assertFalse(playerMessage.contains("Connection refused"));
        assertFalse(playerMessage.contains("java.sql"));

        // Should be user friendly
        assertTrue(playerMessage.contains("unexpected") || playerMessage.contains("error") ||
                   playerMessage.contains("try again"));
    }

    @Test
    @DisplayName("DD46: Stack trace is logged (verified via event)")
    void stackTraceIsLogged() {
        AtomicReference<ArenaFailureHandler.FailureEvent> capturedEvent = new AtomicReference<>();
        handler.registerFailureListener(capturedEvent::set);

        RuntimeException exception = new RuntimeException("Test exception with stack trace");

        handler.handleFailure(FailureType.BUILD_FAILED, exception, "test context");

        assertNotNull(capturedEvent.get());
        assertTrue(capturedEvent.get().getException().isPresent());
        assertFalse(capturedEvent.get().getStackTraceString().isEmpty());
        assertTrue(capturedEvent.get().getStackTraceString().contains("RuntimeException"));
    }

    @Test
    @DisplayName("All failure types have player messages")
    void allFailureTypesHaveMessages() {
        for (FailureType type : FailureType.values()) {
            String message = handler.getPlayerMessage(type);
            assertNotNull(message, "Missing message for " + type);
            assertFalse(message.isEmpty(), "Empty message for " + type);
        }
    }

    @Test
    @DisplayName("Alert handlers are called for critical failures")
    void alertHandlersCalledForCritical() {
        AtomicBoolean alertCalled = new AtomicBoolean(false);
        handler.registerAlertHandler(event -> alertCalled.set(true));

        // INTERNAL_ERROR is critical
        handler.handleFailure(FailureType.INTERNAL_ERROR, null, "test");

        assertTrue(alertCalled.get(), "Alert handler should be called for critical failure");
    }

    @Test
    @DisplayName("Alert handlers NOT called for non-critical failures")
    void alertHandlersNotCalledForNonCritical() {
        AtomicBoolean alertCalled = new AtomicBoolean(false);
        handler.registerAlertHandler(event -> alertCalled.set(true));

        // SPAWN_FAILED is not critical
        handler.handleFailure(FailureType.SPAWN_FAILED, null, "test");

        assertFalse(alertCalled.get(), "Alert handler should not be called for non-critical failure");
    }

    @Test
    @DisplayName("Critical failures are correctly identified")
    void criticalFailuresIdentified() {
        assertTrue(handler.isCriticalFailure(FailureType.INTERNAL_ERROR));
        assertTrue(handler.isCriticalFailure(FailureType.RESOURCE_EXHAUSTED));
        assertTrue(handler.isCriticalFailure(FailureType.CONFIG_ERROR));
        assertTrue(handler.isCriticalFailure(FailureType.CIRCUIT_OPEN));

        assertFalse(handler.isCriticalFailure(FailureType.SPAWN_FAILED));
        assertFalse(handler.isCriticalFailure(FailureType.TEMPLATE_NOT_FOUND));
    }

    @Test
    @DisplayName("Failure listeners receive all events")
    void failureListenersReceiveAllEvents() {
        AtomicReference<ArenaFailureHandler.FailureEvent> capturedEvent = new AtomicReference<>();
        handler.registerFailureListener(capturedEvent::set);

        handler.handleFailure(FailureType.BUILD_FAILED, null, "test context", "player123");

        assertNotNull(capturedEvent.get());
        assertEquals(FailureType.BUILD_FAILED, capturedEvent.get().type());
        assertEquals("test context", capturedEvent.get().context());
        assertEquals("player123", capturedEvent.get().playerId());
    }

    @Test
    @DisplayName("Handlers can be removed")
    void handlersCanBeRemoved() {
        AtomicBoolean called = new AtomicBoolean(false);
        java.util.function.Consumer<ArenaFailureHandler.FailureEvent> listener = event -> called.set(true);

        handler.registerFailureListener(listener);
        handler.removeFailureListener(listener);

        handler.handleFailure(FailureType.BUILD_FAILED, null, "test");

        assertFalse(called.get());
    }

    @Test
    @DisplayName("HandleFailureWithResult returns correct result")
    void handleFailureWithResultReturnsCorrectResult() {
        ArenaFailureHandler.FailureResult result = handler.handleFailureWithResult(
            FailureType.SPAWN_FAILED,
            null,
            "spawn context"
        );

        assertEquals(FailureType.SPAWN_FAILED, result.type());
        assertNotNull(result.playerMessage());
        assertEquals("spawn context", result.context());
    }

    @Test
    @DisplayName("Clear all removes all handlers")
    void clearAllRemovesAllHandlers() {
        AtomicBoolean alertCalled = new AtomicBoolean(false);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);

        handler.registerAlertHandler(event -> alertCalled.set(true));
        handler.registerFailureListener(event -> listenerCalled.set(true));

        handler.clearAll();

        handler.handleFailure(FailureType.INTERNAL_ERROR, null, "test");

        assertFalse(alertCalled.get());
        assertFalse(listenerCalled.get());
    }

    @Test
    @DisplayName("Unknown failure type returns default message")
    void unknownFailureTypeReturnsDefaultMessage() {
        String message = handler.getPlayerMessage(FailureType.UNKNOWN);
        assertNotNull(message);
        assertFalse(message.isEmpty());
    }
}

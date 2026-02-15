package com.devmod.config.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigHandlerRegistry")
class ConfigHandlerRegistryTest {

    @BeforeEach
    void setUp() {
        ConfigHandlerRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        ConfigHandlerRegistry.reset();
    }

    @Test
    @DisplayName("starts empty after reset")
    void startsEmptyAfterReset() {
        assertEquals(0, ConfigHandlerRegistry.size());
        assertTrue(ConfigHandlerRegistry.getAll().isEmpty());
    }

    @Test
    @DisplayName("registerAll registers built-in handlers")
    void registerAllRegistersBuiltInHandlers() {
        ConfigHandlerRegistry.registerAll();
        assertTrue(ConfigHandlerRegistry.size() > 0);
    }

    @Test
    @DisplayName("registerAll is idempotent")
    void registerAllIsIdempotent() {
        ConfigHandlerRegistry.registerAll();
        int firstSize = ConfigHandlerRegistry.size();

        ConfigHandlerRegistry.registerAll();
        assertEquals(firstSize, ConfigHandlerRegistry.size());
    }

    @Test
    @DisplayName("getForItem returns null for null stack")
    void getForItemReturnsNullForNullStack() {
        // No handlers registered, no items to match
        assertNull(ConfigHandlerRegistry.getForItem(null));
    }

    @Test
    @DisplayName("isRegistered returns false for unregistered type")
    void isRegisteredReturnsFalseForUnregistered() {
        assertFalse(ConfigHandlerRegistry.isRegistered(DummyStats.class));
    }

    @Test
    @DisplayName("get returns empty for unregistered type")
    void getReturnsEmptyForUnregistered() {
        assertTrue(ConfigHandlerRegistry.get(DummyStats.class).isEmpty());
    }

    @Test
    @DisplayName("loadAll does not throw when empty")
    void loadAllDoesNotThrowWhenEmpty() {
        assertDoesNotThrow(ConfigHandlerRegistry::loadAll);
    }

    @Test
    @DisplayName("saveAll does not throw when empty")
    void saveAllDoesNotThrowWhenEmpty() {
        assertDoesNotThrow(ConfigHandlerRegistry::saveAll);
    }

    @Test
    @DisplayName("reset clears all handlers")
    void resetClearsAllHandlers() {
        ConfigHandlerRegistry.registerAll();
        assertTrue(ConfigHandlerRegistry.size() > 0);

        ConfigHandlerRegistry.reset();
        assertEquals(0, ConfigHandlerRegistry.size());
    }

    /**
     * Dummy stats interface for type-safe get/isRegistered tests.
     */
    private interface DummyStats extends com.devmod.config.stats.IItemStats {}
}

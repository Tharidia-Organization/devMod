package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.clone.client.renderer.MannequinBillboardRegistry;

/**
 * Tests for MannequinBillboardRegistry key detection, singleton, and lookup.
 * Avoids ItemStack usage (requires MC bootstrap) and focuses on pure logic.
 */
class MannequinBillboardRegistryTest {

    @BeforeEach
    void setUp() {
        MannequinBillboardRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        MannequinBillboardRegistry.reset();
    }

    @Nested
    @DisplayName("Singleton")
    class SingletonTests {

        @Test
        @DisplayName("getInstance returns non-null")
        void notNull() {
            assertNotNull(MannequinBillboardRegistry.getInstance());
        }

        @Test
        @DisplayName("getInstance returns same instance")
        void sameInstance() {
            assertSame(
                MannequinBillboardRegistry.getInstance(),
                MannequinBillboardRegistry.getInstance()
            );
        }

        @Test
        @DisplayName("reset creates new instance")
        void resetCreatesNew() {
            var first = MannequinBillboardRegistry.getInstance();
            MannequinBillboardRegistry.reset();
            var second = MannequinBillboardRegistry.getInstance();
            assertNotSame(first, second);
        }
    }

    @Nested
    @DisplayName("KEY_PREFIX")
    class KeyPrefixTests {

        @Test
        @DisplayName("KEY_PREFIX starts with devmod:")
        void startsWithDevmod() {
            assertTrue(MannequinBillboardRegistry.KEY_PREFIX.startsWith("devmod:"));
        }

        @Test
        @DisplayName("KEY_PREFIX contains mannequin")
        void containsMannequin() {
            assertTrue(MannequinBillboardRegistry.KEY_PREFIX.contains("mannequin"));
        }

        @Test
        @DisplayName("KEY_PREFIX is not empty")
        void notEmpty() {
            assertFalse(MannequinBillboardRegistry.KEY_PREFIX.isEmpty());
        }

        @Test
        @DisplayName("KEY_PREFIX value is 'devmod:mannequin:'")
        void exactValue() {
            assertEquals("devmod:mannequin:", MannequinBillboardRegistry.KEY_PREFIX);
        }
    }

    @Nested
    @DisplayName("isMannequinKey")
    class IsMannequinKeyTests {

        @Test
        @DisplayName("Returns true for keys starting with KEY_PREFIX")
        void trueForValidKey() {
            assertTrue(MannequinBillboardRegistry.isMannequinKey(MannequinBillboardRegistry.KEY_PREFIX + "abc"));
        }

        @Test
        @DisplayName("Returns false for random string")
        void falseForRandom() {
            assertFalse(MannequinBillboardRegistry.isMannequinKey("minecraft:zombie"));
        }

        @Test
        @DisplayName("Returns false for empty string")
        void falseForEmpty() {
            assertFalse(MannequinBillboardRegistry.isMannequinKey(""));
        }

        @Test
        @DisplayName("Returns true for exact prefix")
        void trueForExactPrefix() {
            assertTrue(MannequinBillboardRegistry.isMannequinKey(MannequinBillboardRegistry.KEY_PREFIX));
        }

        @Test
        @DisplayName("Returns false for partial prefix")
        void falseForPartialPrefix() {
            assertFalse(MannequinBillboardRegistry.isMannequinKey("devmod:mann"));
        }

        @Test
        @DisplayName("Returns true for prefix plus hex string")
        void trueForHexKey() {
            assertTrue(MannequinBillboardRegistry.isMannequinKey("devmod:mannequin:1a2b3c4d"));
        }
    }

    @Nested
    @DisplayName("getEquipment without registration")
    class UnregisteredLookupTests {

        @Test
        @DisplayName("Unregistered key returns null")
        void unregisteredReturnsNull() {
            var registry = MannequinBillboardRegistry.getInstance();
            assertNull(registry.getEquipment("devmod:mannequin:nonexistent"));
        }

        @Test
        @DisplayName("Empty string key returns null")
        void emptyKeyReturnsNull() {
            var registry = MannequinBillboardRegistry.getInstance();
            assertNull(registry.getEquipment(""));
        }

        @Test
        @DisplayName("Random key returns null")
        void randomKeyReturnsNull() {
            var registry = MannequinBillboardRegistry.getInstance();
            assertNull(registry.getEquipment("someRandomKey"));
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("clear on empty registry is safe")
        void clearOnEmpty() {
            var registry = MannequinBillboardRegistry.getInstance();
            assertDoesNotThrow(() -> registry.clear());
        }

        @Test
        @DisplayName("Multiple clears are safe")
        void multipleClears() {
            var registry = MannequinBillboardRegistry.getInstance();
            registry.clear();
            registry.clear();
            registry.clear();
            // Should not throw
        }
    }

    @Nested
    @DisplayName("reset")
    class ResetTests {

        @Test
        @DisplayName("Double reset is safe")
        void doubleReset() {
            MannequinBillboardRegistry.reset();
            MannequinBillboardRegistry.reset();
            // Should not throw
            assertNotNull(MannequinBillboardRegistry.getInstance());
        }
    }
}

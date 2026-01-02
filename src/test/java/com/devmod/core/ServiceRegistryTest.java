package com.devmod.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ServiceRegistry functionality.
 */
class ServiceRegistryTest {

    @BeforeEach
    void setUp() {
        ServiceRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    @Test
    void testRegisterAndGet() {
        // Given
        TestService expected = new TestServiceImpl("test");
        ServiceRegistry.register(TestService.class, () -> expected);

        // When
        TestService actual = ServiceRegistry.get(TestService.class);

        // Then
        assertSame(expected, actual);
    }

    @Test
    void testRegisterInstance() {
        // Given
        TestService expected = new TestServiceImpl("instance");
        ServiceRegistry.registerInstance(TestService.class, expected);

        // When
        TestService actual = ServiceRegistry.get(TestService.class);

        // Then
        assertSame(expected, actual);
    }

    @Test
    void testGetUnregisteredThrows() {
        assertThrows(IllegalStateException.class, () ->
            ServiceRegistry.get(TestService.class)
        );
    }

    @Test
    void testGetOrNullReturnsNull() {
        assertNull(ServiceRegistry.getOrNull(TestService.class));
    }

    @Test
    void testIsRegistered() {
        assertFalse(ServiceRegistry.isRegistered(TestService.class));

        ServiceRegistry.register(TestService.class, TestServiceImpl::new);

        assertTrue(ServiceRegistry.isRegistered(TestService.class));
    }

    @Test
    void testOverride() {
        // Given
        TestService original = new TestServiceImpl("original");
        TestService override = new TestServiceImpl("override");
        ServiceRegistry.register(TestService.class, () -> original);

        // When
        ServiceRegistry.override(TestService.class, override);
        TestService result = ServiceRegistry.get(TestService.class);

        // Then
        assertSame(override, result);
        assertEquals("override", result.getName());
    }

    @Test
    void testClearOverride() {
        // Given
        TestService original = new TestServiceImpl("original");
        TestService override = new TestServiceImpl("override");
        ServiceRegistry.register(TestService.class, () -> original);
        ServiceRegistry.override(TestService.class, override);

        // When
        ServiceRegistry.clearOverride(TestService.class);
        TestService result = ServiceRegistry.get(TestService.class);

        // Then
        assertSame(original, result);
    }

    @Test
    void testClearAllOverrides() {
        // Given
        TestService original1 = new TestServiceImpl("original1");
        AnotherService original2 = new AnotherServiceImpl("original2");
        ServiceRegistry.register(TestService.class, () -> original1);
        ServiceRegistry.register(AnotherService.class, () -> original2);
        ServiceRegistry.override(TestService.class, new TestServiceImpl("override1"));
        ServiceRegistry.override(AnotherService.class, new AnotherServiceImpl("override2"));

        // When
        ServiceRegistry.clearAllOverrides();

        // Then
        assertSame(original1, ServiceRegistry.get(TestService.class));
        assertSame(original2, ServiceRegistry.get(AnotherService.class));
    }

    @Test
    void testLazyInitialization() {
        // Given
        int[] callCount = {0};
        ServiceRegistry.register(TestService.class, () -> {
            callCount[0]++;
            return new TestServiceImpl("lazy");
        });

        // Factory not called yet
        assertEquals(0, callCount[0]);

        // When
        ServiceRegistry.get(TestService.class);
        ServiceRegistry.get(TestService.class);

        // Then - factory called only once
        assertEquals(1, callCount[0]);
    }

    @Test
    void testDuplicateRegistrationIgnored() {
        // Given
        TestService first = new TestServiceImpl("first");
        TestService second = new TestServiceImpl("second");
        ServiceRegistry.register(TestService.class, () -> first);

        // When - duplicate registration
        ServiceRegistry.register(TestService.class, () -> second);

        // Then - first registration wins
        assertSame(first, ServiceRegistry.get(TestService.class));
    }

    @Test
    void testOverrideUnregisteredService() {
        // Given
        TestService override = new TestServiceImpl("override");

        // When - override without prior registration
        ServiceRegistry.override(TestService.class, override);

        // Then - override works (registers with override as factory)
        assertSame(override, ServiceRegistry.get(TestService.class));
    }

    // Test interfaces and implementations

    interface TestService {
        String getName();
    }

    interface AnotherService {
        String getValue();
    }

    static class TestServiceImpl implements TestService {
        private final String name;

        TestServiceImpl() {
            this.name = "default";
        }

        TestServiceImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class AnotherServiceImpl implements AnotherService {
        private final String value;

        AnotherServiceImpl(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}

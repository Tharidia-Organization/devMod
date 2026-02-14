package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for BaseCompatModule lifecycle, state machine, caching, and
 * graceful degradation when the target mod is not present.
 */
class BaseCompatModuleTest {

    /**
     * Concrete test module that records whether init methods were called.
     */
    private static class TestModule extends BaseCompatModule {
        boolean commonCalled = false;
        boolean clientCalled = false;
        boolean shutdownCalled = false;
        boolean throwOnInit = false;

        TestModule(String modId) {
            super(modId);
        }

        TestModule(String modId, String displayName, int priority) {
            super(modId, displayName, priority);
        }

        @Override
        protected void doInitCommon() throws Exception {
            if (throwOnInit) {
                throw new RuntimeException("Simulated init failure");
            }
            commonCalled = true;
        }

        @Override
        protected void doInitClient() {
            clientCalled = true;
        }

        @Override
        protected void doShutdown() {
            shutdownCalled = true;
        }
    }

    @BeforeEach
    void setUp() {
        Compat.clearCaches();
    }

    @AfterEach
    void tearDown() {
        Compat.clearCaches();
    }

    // ================================================================
    // Constructor / identity
    // ================================================================

    @Test
    void constructor_singleArg_setsModIdAsDisplayName() {
        var module = new TestModule("testmod");
        assertEquals("testmod", module.modId());
        assertEquals("testmod", module.displayName());
        assertEquals(100, module.priority());
    }

    @Test
    void constructor_threeArgs_setsAllFields() {
        var module = new TestModule("mymod", "My Mod", 42);
        assertEquals("mymod", module.modId());
        assertEquals("My Mod", module.displayName());
        assertEquals(42, module.priority());
    }

    // ================================================================
    // InitState enum
    // ================================================================

    @Test
    void initState_operationalStates() {
        assertTrue(BaseCompatModule.InitState.COMMON_COMPLETE.isOperational());
        assertTrue(BaseCompatModule.InitState.CLIENT_COMPLETE.isOperational());
    }

    @Test
    void initState_nonOperationalStates() {
        assertFalse(BaseCompatModule.InitState.NOT_STARTED.isOperational());
        assertFalse(BaseCompatModule.InitState.COMMON_STARTED.isOperational());
        assertFalse(BaseCompatModule.InitState.SKIPPED.isOperational());
        assertFalse(BaseCompatModule.InitState.VERSION_MISMATCH.isOperational());
        assertFalse(BaseCompatModule.InitState.FAILED.isOperational());
        assertFalse(BaseCompatModule.InitState.SHUTDOWN.isOperational());
    }

    // ================================================================
    // initCommon — mod not loaded → SKIPPED
    // ================================================================

    @Test
    void initCommon_modNotLoaded_skips() {
        var module = new TestModule("nonexistent_mod_xyz");
        module.initCommon();

        assertFalse(module.commonCalled, "doInitCommon should NOT be called");
        assertEquals(BaseCompatModule.InitState.SKIPPED, module.getInitState());
        assertFalse(module.isActive());
        assertFalse(module.isAvailable());
    }

    // ================================================================
    // initCommon — idempotency
    // ================================================================

    @Test
    void initCommon_calledTwice_skipsSecond() {
        var module = new TestModule("nonexistent_mod");
        module.initCommon();
        BaseCompatModule.InitState stateAfterFirst = module.getInitState();

        module.initCommon();
        assertEquals(stateAfterFirst, module.getInitState());
    }

    // ================================================================
    // initClient — not available → skips
    // ================================================================

    @Test
    void initClient_notAvailable_skips() {
        var module = new TestModule("nonexistent_mod");
        module.initCommon();  // skipped because mod not loaded
        module.initClient();

        assertFalse(module.clientCalled);
    }

    // ================================================================
    // shutdown — always runs doShutdown and resets state
    // ================================================================

    @Test
    void shutdown_clearsAvailableAndSetsState() {
        var module = new TestModule("nonexistent_mod");
        module.initCommon(); // skipped but that's fine

        module.shutdown();

        assertTrue(module.shutdownCalled);
        assertFalse(module.isAvailable());
        assertEquals(BaseCompatModule.InitState.SHUTDOWN, module.getInitState());
    }

    // ================================================================
    // registerActions — not available → no-op
    // ================================================================

    @Test
    void registerActions_notAvailable_doesNotThrow() {
        var module = new TestModule("nonexistent_mod");
        module.initCommon();
        assertDoesNotThrow(() -> module.registerActions(null));
    }

    // ================================================================
    // isActive — delegates to available + initState.isOperational
    // ================================================================

    @Test
    void isActive_notStarted_returnsFalse() {
        var module = new TestModule("nonexistent_mod");
        assertFalse(module.isActive());
    }

    @Test
    void isActive_shutdown_returnsFalse() {
        var module = new TestModule("nonexistent_mod");
        module.shutdown();
        assertFalse(module.isActive());
    }

    // ================================================================
    // Class / method caching
    // ================================================================

    @Test
    void loadOptionalClass_validClass_caches() {
        var module = new TestModule("test");
        Class<?> first = module.loadOptionalClass("java.lang.String");
        Class<?> second = module.loadOptionalClass("java.lang.String");

        assertNotNull(first);
        assertSame(first, second, "Should return cached instance");
    }

    @Test
    void loadOptionalClass_nonexistent_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.loadOptionalClass("com.nonexistent.Fake"));
    }

    @Test
    void loadClassFromAny_findsFirst() {
        var module = new TestModule("test");
        Class<?> result = module.loadClassFromAny(
            "com.nonexistent.Fake",
            "java.lang.Integer"
        );
        assertEquals(Integer.class, result);
    }

    @Test
    void loadClassFromAny_noneExist_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.loadClassFromAny("com.fake.A", "com.fake.B"));
    }

    @Test
    void getMethod_cachesResult() {
        var module = new TestModule("test");
        var m1 = module.getMethod(String.class, "length");
        var m2 = module.getMethod(String.class, "length");
        assertNotNull(m1);
        assertSame(m1, m2);
    }

    @Test
    void getMethod_nullClass_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.getMethod(null, "anything"));
    }

    @Test
    void getDeclaredMethod_nullClass_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.getDeclaredMethod(null, "anything"));
    }

    // ================================================================
    // Object cache
    // ================================================================

    @Test
    void cache_and_getCached_roundTrips() {
        var module = new TestModule("test");
        module.cache("key", "value");
        assertEquals("value", module.getCached("key", String.class));
    }

    @Test
    void getCached_wrongType_returnsNull() {
        var module = new TestModule("test");
        module.cache("key", 42);
        assertNull(module.getCached("key", String.class));
    }

    @Test
    void getCached_missingKey_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.getCached("nonexistent", String.class));
    }

    // ================================================================
    // clearCaches
    // ================================================================

    @Test
    void clearCaches_emptiesCaches() {
        var module = new TestModule("test");
        module.loadOptionalClass("java.lang.String");
        module.getMethod(String.class, "length");
        module.cache("key", "value");

        module.clearCaches();

        // After clearing, getCached should return null
        assertNull(module.getCached("key", String.class));
    }

    // ================================================================
    // invoke / invokeAs
    // ================================================================

    @Test
    void invoke_nullMethod_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.invoke(null, null));
    }

    @Test
    void invoke_validMethod_returnsResult() throws Exception {
        var module = new TestModule("test");
        var method = String.class.getMethod("length");
        Object result = module.invoke(method, "hello");
        assertEquals(5, result);
    }

    @Test
    void invokeAs_correctType_returnsTyped() throws Exception {
        var module = new TestModule("test");
        var method = String.class.getMethod("length");
        Integer result = module.invokeAs(method, Integer.class, "hello");
        assertNotNull(result);
        assertEquals(5, result);
    }

    @Test
    void invokeAs_wrongType_returnsNull() throws Exception {
        var module = new TestModule("test");
        var method = String.class.getMethod("length");
        // length() returns int/Integer, not String
        String result = module.invokeAs(method, String.class, "hello");
        assertNull(result);
    }

    @Test
    void invokeAs_nullMethod_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.invokeAs(null, String.class, null));
    }

    // ================================================================
    // getInitError
    // ================================================================

    @Test
    void getInitError_beforeInit_returnsNull() {
        var module = new TestModule("test");
        assertNull(module.getInitError());
    }

    // ================================================================
    // Logging helpers — just verify they don't throw
    // ================================================================

    @Test
    void loggingHelpers_doNotThrow() {
        var module = new TestModule("test");
        assertDoesNotThrow(() -> module.debug("msg {}", "arg"));
        assertDoesNotThrow(() -> module.info("msg {}", "arg"));
        assertDoesNotThrow(() -> module.warn("msg {}", "arg"));
        assertDoesNotThrow(() -> module.error("msg {}", "arg"));
    }
}

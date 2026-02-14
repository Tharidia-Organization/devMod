package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for CompatRegistry — module registration, lifecycle management,
 * status reporting, and graceful handling of missing mods.
 *
 * Note: Since no mods are actually loaded in a unit-test environment,
 * initCommon/initClient will skip all modules (mod not present).
 * We test the registration mechanics and the graceful degradation.
 */
class CompatRegistryTest {

    /**
     * Fully reset CompatRegistry static state via reflection, since
     * shutdown() intentionally preserves MODULES for re-init scenarios.
     */
    @SuppressWarnings("unchecked")
    private static void resetRegistry() throws Exception {
        CompatRegistry.shutdown();

        Field modulesField = CompatRegistry.class.getDeclaredField("MODULES");
        modulesField.setAccessible(true);
        ((Map<?, ?>) modulesField.get(null)).clear();
    }

    @BeforeEach
    void setUp() throws Exception {
        resetRegistry();
        Compat.clearCaches();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetRegistry();
        Compat.clearCaches();
    }

    /**
     * Simple test module for registration tests.
     */
    private static class StubCompatModule implements CompatModule {
        final String modId;
        final String display;
        final int prio;
        boolean commonCalled = false;
        boolean clientCalled = false;
        boolean shutdownCalled = false;

        StubCompatModule(String modId) {
            this(modId, modId, 100);
        }

        StubCompatModule(String modId, String display, int prio) {
            this.modId = modId;
            this.display = display;
            this.prio = prio;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public String displayName() {
            return display;
        }

        @Override
        public int priority() {
            return prio;
        }

        @Override
        public void initCommon() {
            commonCalled = true;
        }

        @Override
        public void initClient() {
            clientCalled = true;
        }

        @Override
        public void shutdown() {
            shutdownCalled = true;
        }
    }

    // ================================================================
    // Registration
    // ================================================================

    @Test
    void register_addsModule() {
        var module = new StubCompatModule("testmod_reg");
        CompatRegistry.register(module);

        assertNotNull(CompatRegistry.getModule("testmod_reg"));
        assertTrue(CompatRegistry.getRegisteredModIds().contains("testmod_reg"));
    }

    @Test
    void register_nullModule_throwsNPE() {
        assertThrows(NullPointerException.class, () -> CompatRegistry.register(null));
    }

    @Test
    void register_duplicate_replaces() {
        var module1 = new StubCompatModule("testmod_dup", "First", 10);
        var module2 = new StubCompatModule("testmod_dup", "Second", 20);

        CompatRegistry.register(module1);
        CompatRegistry.register(module2);

        assertEquals("Second", CompatRegistry.getModule("testmod_dup").displayName());
    }

    @Test
    void registerAll_addsMultiple() {
        int before = CompatRegistry.getRegisteredCount();
        CompatRegistry.registerAll(
            new StubCompatModule("multi_a"),
            new StubCompatModule("multi_b"),
            new StubCompatModule("multi_c")
        );

        assertEquals(before + 3, CompatRegistry.getRegisteredCount());
        assertTrue(CompatRegistry.getRegisteredModIds().contains("multi_a"));
        assertTrue(CompatRegistry.getRegisteredModIds().contains("multi_b"));
        assertTrue(CompatRegistry.getRegisteredModIds().contains("multi_c"));
    }

    // ================================================================
    // getModule — typed retrieval
    // ================================================================

    @Test
    void getModule_byType_returnsTypedModule() {
        var module = new StubCompatModule("testmod_typed");
        CompatRegistry.register(module);

        StubCompatModule typed = CompatRegistry.getModule("testmod_typed", StubCompatModule.class);
        assertNotNull(typed);
        assertSame(module, typed);
    }

    @Test
    void getModule_byType_wrongType_returnsNull() {
        CompatRegistry.register(new StubCompatModule("testmod_wrong"));

        // Try to retrieve as BaseCompatModule — StubCompatModule doesn't extend it
        BaseCompatModule result = CompatRegistry.getModule("testmod_wrong", BaseCompatModule.class);
        assertNull(result);
    }

    @Test
    void getModule_nonexistent_returnsNull() {
        assertNull(CompatRegistry.getModule("nonexistent_compat_xyz"));
    }

    // ================================================================
    // initCommon — mods not loaded → all skipped
    // ================================================================

    @Test
    void initCommon_modsNotLoaded_skipsAll() {
        CompatRegistry.register(new StubCompatModule("fake_mod_1"));
        CompatRegistry.register(new StubCompatModule("fake_mod_2"));

        CompatRegistry.initCommon();

        assertEquals(0, CompatRegistry.getActiveCount());
        assertTrue(CompatRegistry.getActiveModIds().isEmpty());
    }

    @Test
    void initCommon_calledTwice_warnsButDoesNotCrash() {
        CompatRegistry.initCommon();
        // Second call should log a warning but not throw
        assertDoesNotThrow(CompatRegistry::initCommon);
    }

    // ================================================================
    // initClient — before initCommon → warns but doesn't crash
    // ================================================================

    @Test
    void initClient_noActiveModules_doesNotCrash() {
        CompatRegistry.initCommon();
        assertDoesNotThrow(CompatRegistry::initClient);
    }

    @Test
    void initClient_calledTwice_doesNotCrash() {
        CompatRegistry.initCommon();
        CompatRegistry.initClient();
        assertDoesNotThrow(CompatRegistry::initClient);
    }

    // ================================================================
    // registerActions — null registry → no-op
    // ================================================================

    @Test
    void registerActions_nullRegistry_doesNotThrow() {
        CompatRegistry.initCommon();
        assertDoesNotThrow(() -> CompatRegistry.registerActions(null));
    }

    // ================================================================
    // shutdown — resets active state
    // ================================================================

    @Test
    void shutdown_clearsActiveState() {
        CompatRegistry.register(new StubCompatModule("mod_shut_a"));
        CompatRegistry.register(new StubCompatModule("mod_shut_b"));
        CompatRegistry.initCommon();

        CompatRegistry.shutdown();

        assertEquals(0, CompatRegistry.getActiveCount());
        assertTrue(CompatRegistry.getActiveModIds().isEmpty());
    }

    // ================================================================
    // isModuleActive
    // ================================================================

    @Test
    void isModuleActive_notInitialized_returnsFalse() {
        CompatRegistry.register(new StubCompatModule("testmod_active"));
        assertFalse(CompatRegistry.isModuleActive("testmod_active"));
    }

    @Test
    void isModuleActive_modNotLoaded_returnsFalse() {
        CompatRegistry.register(new StubCompatModule("fake_mod_active"));
        CompatRegistry.initCommon();
        assertFalse(CompatRegistry.isModuleActive("fake_mod_active"));
    }

    // ================================================================
    // getRegisteredModIds
    // ================================================================

    @Test
    void getRegisteredModIds_returnsUnmodifiableSet() {
        CompatRegistry.register(new StubCompatModule("mod_unmod"));
        Set<String> ids = CompatRegistry.getRegisteredModIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.add("illegal"));
    }

    @Test
    void getRegisteredModIds_containsRegisteredMod() {
        CompatRegistry.register(new StubCompatModule("mod_contains"));
        assertTrue(CompatRegistry.getRegisteredModIds().contains("mod_contains"));
    }

    // ================================================================
    // getActiveModIds
    // ================================================================

    @Test
    void getActiveModIds_returnsUnmodifiableSet() {
        CompatRegistry.initCommon();
        Set<String> ids = CompatRegistry.getActiveModIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.add("illegal"));
    }

    // ================================================================
    // getStatusReport
    // ================================================================

    @Test
    void getStatusReport_containsHeader() {
        CompatRegistry.register(new StubCompatModule("mod_report"));
        String report = CompatRegistry.getStatusReport();
        assertTrue(report.contains("DevMod Compatibility Status"));
    }

    @Test
    void getStatusReport_listsModules() {
        CompatRegistry.register(new StubCompatModule("mod_listed", "Mod Listed", 10));
        String report = CompatRegistry.getStatusReport();
        assertTrue(report.contains("Mod Listed"));
    }

    @Test
    void getStatusReport_showsNotPresent_forUnloadedMods() {
        CompatRegistry.register(new StubCompatModule("fake_mod_report"));
        CompatRegistry.initCommon();
        String report = CompatRegistry.getStatusReport();
        assertTrue(report.contains("NOT PRESENT"));
    }

    // ================================================================
    // Counts
    // ================================================================

    @Test
    void counts_emptyRegistry() {
        assertEquals(0, CompatRegistry.getRegisteredCount());
        assertEquals(0, CompatRegistry.getActiveCount());
    }

    @Test
    void registeredCount_matchesRegistrations() {
        int before = CompatRegistry.getRegisteredCount();
        CompatRegistry.register(new StubCompatModule("cnt_a"));
        CompatRegistry.register(new StubCompatModule("cnt_b"));
        assertEquals(before + 2, CompatRegistry.getRegisteredCount());
    }
}

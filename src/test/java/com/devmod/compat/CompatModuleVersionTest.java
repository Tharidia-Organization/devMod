package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for CompatModule interface default methods, focusing on
 * the version comparison logic which is pure and testable.
 */
class CompatModuleVersionTest {

    /**
     * Concrete stub implementing CompatModule for testing defaults.
     */
    private static class StubModule implements CompatModule {
        private final String modId;
        private final String minVersion;

        StubModule(String modId, String minVersion) {
            this.modId = modId;
            this.minVersion = minVersion;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public void initCommon() {
            // no-op for testing
        }

        @Override
        public String getMinVersion() {
            return minVersion;
        }
    }

    // ================================================================
    // Default method: displayName
    // ================================================================

    @Test
    void displayName_defaultsToModId() {
        var module = new StubModule("mymod", null);
        assertEquals("mymod", module.displayName());
    }

    // ================================================================
    // Default method: priority
    // ================================================================

    @Test
    void priority_defaultsTo100() {
        var module = new StubModule("mymod", null);
        assertEquals(100, module.priority());
    }

    // ================================================================
    // Default method: getFeatureDescription
    // ================================================================

    @Test
    void featureDescription_containsDisplayName() {
        var module = new StubModule("mymod", null);
        assertTrue(module.getFeatureDescription().contains("mymod"));
    }

    // ================================================================
    // Default method: initClient — should not throw
    // ================================================================

    @Test
    void initClient_defaultDoesNotThrow() {
        var module = new StubModule("mymod", null);
        assertDoesNotThrow(module::initClient);
    }

    // ================================================================
    // Default method: shutdown — should not throw
    // ================================================================

    @Test
    void shutdown_defaultDoesNotThrow() {
        var module = new StubModule("mymod", null);
        assertDoesNotThrow(module::shutdown);
    }

    // ================================================================
    // Default method: registerActions — null registry should not throw
    // ================================================================

    @Test
    void registerActions_nullRegistry_doesNotThrow() {
        var module = new StubModule("mymod", null);
        assertDoesNotThrow(() -> module.registerActions(null));
    }

    // ================================================================
    // isVersionCompatible — uses the private compareVersions logic
    // ================================================================

    @Test
    void isVersionCompatible_nullMinVersion_alwaysTrue() {
        var module = new StubModule("mymod", null);
        assertTrue(module.isVersionCompatible());
    }

    @Test
    void isVersionCompatible_modNotLoaded_returnsFalse() {
        // When min version is set but mod is not loaded, getVersion returns null
        var module = new StubModule("nonexistent_mod", "1.0.0");
        assertFalse(module.isVersionCompatible());
    }

    // ================================================================
    // Version comparison tests (via reflection on the private static
    // compareVersions method, or by testing isVersionCompatible behavior)
    //
    // We use a helper to invoke the private method directly.
    // ================================================================

    /**
     * Access the private static compareVersions method for testing.
     */
    private int compareVersions(String v1, String v2) throws Exception {
        var method = CompatModule.class.getDeclaredMethod("compareVersions", String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, v1, v2);
    }

    @ParameterizedTest
    @CsvSource({
        "1.0.0,  1.0.0,  0",
        "2.0.0,  1.0.0,  1",
        "1.0.0,  2.0.0, -1",
        "1.2.0,  1.1.0,  1",
        "1.0.1,  1.0.0,  1",
        "1.0.0,  1.0.1, -1",
        "1.0,    1.0.0,  0",
        "1,      1.0.0,  0",
        "10.0.0, 9.0.0,  1",
    })
    void compareVersions_semanticVersioning(String v1, String v2, int expectedSign) throws Exception {
        int result = compareVersions(v1, v2);
        assertEquals(Integer.signum(expectedSign), Integer.signum(result),
            String.format("compareVersions(%s, %s) = %d, expected sign %d", v1, v2, result, expectedSign));
    }

    // ================================================================
    // Version suffix stripping
    // ================================================================

    @Test
    void compareVersions_stripsPlus() throws Exception {
        assertEquals(0, compareVersions("1.2.3+build123", "1.2.3"));
    }

    @Test
    void compareVersions_stripsDash() throws Exception {
        assertEquals(0, compareVersions("1.2.3-beta1", "1.2.3"));
    }

    @Test
    void compareVersions_stripsBothSuffixes() throws Exception {
        assertEquals(0, compareVersions("1.2.3-beta+build", "1.2.3"));
    }

    @Test
    void compareVersions_twoVersionsWithSuffixes() throws Exception {
        // 2.0.0-alpha > 1.9.9-rc1  (suffixes stripped, compare numeric)
        assertTrue(compareVersions("2.0.0-alpha", "1.9.9-rc1") > 0);
    }

    // ================================================================
    // parseVersionPart edge cases
    // ================================================================

    @Test
    void compareVersions_alphaInPart_treatsAsZero() throws Exception {
        // "abc" should parse to 0
        assertEquals(0, compareVersions("abc", "0"));
    }

    @Test
    void compareVersions_mixedPart_extractsLeadingDigits() throws Exception {
        // "3rc1" should parse to 3
        int result = compareVersions("3rc1", "3");
        assertEquals(0, result);
    }

    // ================================================================
    // isActive — default delegates to Compat.isLoaded
    // ================================================================

    @Test
    void isActive_modNotLoaded_returnsFalse() {
        var module = new StubModule("nonexistent_mod_abc", null);
        assertFalse(module.isActive());
    }
}

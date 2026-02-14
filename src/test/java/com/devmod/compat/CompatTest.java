package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Compat utility class.
 *
 * Since ModList is not available in a unit-test environment, most
 * isLoaded() calls will return false. We test the graceful degradation
 * paths, null/empty input handling, class loading helpers, and
 * reflection wrappers that can be exercised without Minecraft running.
 */
class CompatTest {

    @BeforeEach
    void setUp() {
        Compat.clearCaches();
    }

    @AfterEach
    void tearDown() {
        Compat.clearCaches();
    }

    // ================================================================
    // isLoaded — graceful fallback when ModList is unavailable
    // ================================================================

    @Test
    void isLoaded_nullModId_returnsFalse() {
        assertFalse(Compat.isLoaded(null));
    }

    @Test
    void isLoaded_emptyModId_returnsFalse() {
        assertFalse(Compat.isLoaded(""));
    }

    @Test
    void isLoaded_nonexistentMod_returnsFalseGracefully() {
        // ModList.get() will either throw or return null in a test env;
        // either way isLoaded should return false, not throw.
        assertFalse(Compat.isLoaded("nonexistent_mod_xyz"));
    }

    // ================================================================
    // areAllLoaded / isAnyLoaded
    // ================================================================

    @Test
    void areAllLoaded_emptyArray_returnsTrue() {
        // Vacuous truth — no mods to check means all are "loaded"
        assertTrue(Compat.areAllLoaded());
    }

    @Test
    void areAllLoaded_withNonexistentMods_returnsFalse() {
        assertFalse(Compat.areAllLoaded("mod_a", "mod_b"));
    }

    @Test
    void isAnyLoaded_emptyArray_returnsFalse() {
        assertFalse(Compat.isAnyLoaded());
    }

    @Test
    void isAnyLoaded_withNonexistentMods_returnsFalse() {
        assertFalse(Compat.isAnyLoaded("mod_x", "mod_y"));
    }

    // ================================================================
    // getVersion / getVersionOpt
    // ================================================================

    @Test
    void getVersion_unloadedMod_returnsNull() {
        assertNull(Compat.getVersion("unloaded_mod"));
    }

    @Test
    void getVersionOpt_unloadedMod_returnsEmpty() {
        assertTrue(Compat.getVersionOpt("unloaded_mod").isEmpty());
    }

    // ================================================================
    // loadClass / classExists
    // ================================================================

    @Test
    void loadClass_validJdkClass_returnsClass() {
        Class<?> result = Compat.loadClass("java.lang.String");
        assertNotNull(result);
        assertEquals(String.class, result);
    }

    @Test
    void loadClass_nonexistentClass_returnsNull() {
        assertNull(Compat.loadClass("com.nonexistent.FakeClass123"));
    }

    @Test
    void classExists_validClass_returnsTrue() {
        assertTrue(Compat.classExists("java.util.List"));
    }

    @Test
    void classExists_nonexistentClass_returnsFalse() {
        assertFalse(Compat.classExists("com.nonexistent.FakeClass123"));
    }

    // ================================================================
    // loadClassFromAny
    // ================================================================

    @Test
    void loadClassFromAny_findsFirstMatch() {
        Class<?> result = Compat.loadClassFromAny(
            "com.nonexistent.Fake",
            "java.lang.Integer",
            "java.lang.String"
        );
        assertNotNull(result);
        assertEquals(Integer.class, result);
    }

    @Test
    void loadClassFromAny_noneExist_returnsNull() {
        assertNull(Compat.loadClassFromAny(
            "com.nonexistent.A",
            "com.nonexistent.B"
        ));
    }

    // ================================================================
    // runIfLoaded — mod not loaded → no-op
    // ================================================================

    @Test
    void runIfLoaded_modNotLoaded_doesNotExecute() {
        boolean[] executed = {false};
        Compat.runIfLoaded("nonexistent_mod", () -> executed[0] = true);
        assertFalse(executed[0]);
    }

    // ================================================================
    // Reflection helpers — getMethod / getField / getDeclaredMethod / getDeclaredField
    // ================================================================

    @Test
    void getMethod_validPublicMethod_returnsMethod() {
        var method = Compat.getMethod(String.class, "length");
        assertNotNull(method);
        assertEquals("length", method.getName());
    }

    @Test
    void getMethod_nullClass_returnsNull() {
        assertNull(Compat.getMethod(null, "anything"));
    }

    @Test
    void getMethod_nonexistentMethod_returnsNull() {
        assertNull(Compat.getMethod(String.class, "nonExistentMethod12345"));
    }

    @Test
    void getDeclaredMethod_privateMethod_returnsAccessibleMethod() {
        // String has private methods; test that we can access them
        // hashCode is public but let's test the declared path
        var method = Compat.getDeclaredMethod(String.class, "hashCode");
        assertNotNull(method);
        assertTrue(method.canAccess("test"));
    }

    @Test
    void getDeclaredMethod_nullClass_returnsNull() {
        assertNull(Compat.getDeclaredMethod(null, "anything"));
    }

    @Test
    void getField_validPublicField_returnsField() {
        // Integer.MAX_VALUE is a public static field
        var field = Compat.getField(Integer.class, "MAX_VALUE");
        assertNotNull(field);
    }

    @Test
    void getField_nullClass_returnsNull() {
        assertNull(Compat.getField(null, "anything"));
    }

    @Test
    void getField_nonexistentField_returnsNull() {
        assertNull(Compat.getField(String.class, "nonExistentField999"));
    }

    @Test
    void getDeclaredField_nullClass_returnsNull() {
        assertNull(Compat.getDeclaredField(null, "anything"));
    }

    // ================================================================
    // invoke / getStaticFieldValue / getFieldValue
    // ================================================================

    @Test
    void invoke_nullMethod_returnsNull() {
        assertNull(Compat.invoke(null, null));
    }

    @Test
    void invoke_validMethod_returnsResult() throws Exception {
        var method = String.class.getMethod("length");
        Object result = Compat.invoke(method, "hello");
        assertEquals(5, result);
    }

    @Test
    void getStaticFieldValue_nullField_returnsNull() {
        assertNull(Compat.getStaticFieldValue(null));
    }

    @Test
    void getStaticFieldValue_validField_returnsValue() {
        var field = Compat.getField(Integer.class, "MAX_VALUE");
        assertNotNull(field);
        Object value = Compat.getStaticFieldValue(field);
        assertEquals(Integer.MAX_VALUE, value);
    }

    @Test
    void getFieldValue_nullField_returnsNull() {
        assertNull(Compat.getFieldValue(null, "instance"));
    }

    @Test
    void getFieldValue_nullInstance_returnsNull() {
        var field = Compat.getField(Integer.class, "MAX_VALUE");
        assertNull(Compat.getFieldValue(field, null));
    }

    // ================================================================
    // Mods constants — verify they exist and are non-empty
    // ================================================================

    @Test
    void modConstants_areNotEmpty() {
        assertFalse(Compat.Mods.EPIC_FIGHT.isEmpty());
        assertFalse(Compat.Mods.CURIOS.isEmpty());
        assertFalse(Compat.Mods.SPARK.isEmpty());
        assertFalse(Compat.Mods.GECKOLIB.isEmpty());
        assertFalse(Compat.Mods.SODIUM.isEmpty());
    }

    // ================================================================
    // clearCaches
    // ================================================================

    @Test
    void clearCaches_doesNotThrow() {
        // Exercise the cache clear path twice
        Compat.clearCaches();
        Compat.clearCaches();
    }

    // ================================================================
    // logStatus — just verifying it does not throw
    // ================================================================

    @Test
    void logStatus_enabled_doesNotThrow() {
        Compat.logStatus("testmod", "TestFeature", true);
    }

    @Test
    void logStatus_disabled_doesNotThrow() {
        Compat.logStatus("testmod", "TestFeature", false);
    }
}

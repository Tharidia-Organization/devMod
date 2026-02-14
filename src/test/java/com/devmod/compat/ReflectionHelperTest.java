package com.devmod.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ReflectionHelper — MethodHandle-based invocation,
 * cached lookups, type checking, proxy creation, and field access.
 *
 * Uses standard JDK classes so no external dependencies are needed.
 */
class ReflectionHelperTest {

    @BeforeEach
    void setUp() {
        ReflectionHelper.clearCaches();
    }

    @AfterEach
    void tearDown() {
        ReflectionHelper.clearCaches();
    }

    // ================================================================
    // MethodHandle lookup
    // ================================================================

    @Test
    void findMethodHandle_publicMethod_returnsHandle() {
        MethodHandle handle = ReflectionHelper.findMethodHandle(String.class, "length");
        assertNotNull(handle);
    }

    @Test
    void findMethodHandle_nonexistentMethod_returnsNull() {
        assertNull(ReflectionHelper.findMethodHandle(String.class, "nonExistentMethod"));
    }

    @Test
    void findMethodHandle_withParameters_returnsHandle() {
        MethodHandle handle = ReflectionHelper.findMethodHandle(
            String.class, "substring", int.class, int.class);
        assertNotNull(handle);
    }

    @Test
    void findStaticMethodHandle_validMethod_returnsHandle() {
        MethodHandle handle = ReflectionHelper.findStaticMethodHandle(
            String.class, "valueOf", String.class, int.class);
        assertNotNull(handle);
    }

    @Test
    void findStaticMethodHandle_nonexistent_returnsNull() {
        assertNull(ReflectionHelper.findStaticMethodHandle(
            String.class, "nonexistent", Void.class));
    }

    // ================================================================
    // MethodHandle invocation
    // ================================================================

    @Test
    void invokeHandle_validHandle_returnsResult() {
        MethodHandle handle = ReflectionHelper.findMethodHandle(String.class, "length");
        Object result = ReflectionHelper.invokeHandle(handle, "hello");
        assertEquals(5, result);
    }

    @Test
    void invokeHandle_nullHandle_returnsNull() {
        assertNull(ReflectionHelper.invokeHandle(null));
    }

    @Test
    void invokeHandleAs_correctType_returnsTyped() {
        MethodHandle handle = ReflectionHelper.findMethodHandle(String.class, "length");
        Integer result = ReflectionHelper.invokeHandleAs(handle, Integer.class, "test");
        assertNotNull(result);
        assertEquals(4, result);
    }

    @Test
    void invokeHandleAs_wrongType_returnsNull() {
        MethodHandle handle = ReflectionHelper.findMethodHandle(String.class, "length");
        // length() returns int, not String
        String result = ReflectionHelper.invokeHandleAs(handle, String.class, "test");
        assertNull(result);
    }

    @Test
    void invokeHandleAs_nullHandle_returnsNull() {
        assertNull(ReflectionHelper.invokeHandleAs(null, String.class));
    }

    // ================================================================
    // Method lookup — hierarchy search
    // ================================================================

    @Test
    void findMethodInHierarchy_directMethod_found() {
        Method method = ReflectionHelper.findMethodInHierarchy(
            String.class, "hashCode");
        assertNotNull(method);
    }

    @Test
    void findMethodInHierarchy_declaredInSuperclass_found() {
        // ArrayList declares its own "size" method directly
        Method method = ReflectionHelper.findMethodInHierarchy(
            java.util.ArrayList.class, "size");
        assertNotNull(method);
    }

    @Test
    void findMethodInHierarchy_nonexistent_returnsNull() {
        assertNull(ReflectionHelper.findMethodInHierarchy(
            String.class, "completelyFakeMethod"));
    }

    @Test
    void findMethodByName_findsDespiteParameterTypes() {
        Method method = ReflectionHelper.findMethodByName(String.class, "substring");
        assertNotNull(method);
        assertEquals("substring", method.getName());
    }

    @Test
    void findMethodByName_nonexistent_returnsNull() {
        assertNull(ReflectionHelper.findMethodByName(String.class, "fakeMethod"));
    }

    @Test
    void findAllMethodsByName_returnsAllOverloads() {
        List<Method> methods = ReflectionHelper.findAllMethodsByName(
            String.class, "substring");
        assertTrue(methods.size() >= 2, "String.substring has at least 2 overloads");
    }

    @Test
    void findAllMethodsByName_nonexistent_returnsEmpty() {
        List<Method> methods = ReflectionHelper.findAllMethodsByName(
            String.class, "nonExistentMethod");
        assertTrue(methods.isEmpty());
    }

    // ================================================================
    // Field access
    // ================================================================

    @Test
    void findFieldInHierarchy_publicStaticField_found() {
        Field field = ReflectionHelper.findFieldInHierarchy(
            Integer.class, "MAX_VALUE");
        assertNotNull(field);
    }

    @Test
    void findFieldInHierarchy_nonexistent_returnsNull() {
        assertNull(ReflectionHelper.findFieldInHierarchy(
            String.class, "completelyFakeField"));
    }

    /**
     * Helper class with an accessible instance field for testing.
     */
    static class FieldHolder {
        String name = "test";
        int count = 42;
    }

    @Test
    void getFieldValue_instanceField_returnsValue() {
        FieldHolder holder = new FieldHolder();
        Optional<String> value = ReflectionHelper.getFieldValue(holder, "name", String.class);
        assertTrue(value.isPresent());
        assertEquals("test", value.get());
    }

    @Test
    void getFieldValue_instanceField_wrongType_returnsEmpty() {
        FieldHolder holder = new FieldHolder();
        Optional<String> value = ReflectionHelper.getFieldValue(holder, "count", String.class);
        assertTrue(value.isEmpty());
    }

    @Test
    void getFieldValue_nullInstance_returnsEmpty() {
        Optional<String> result = ReflectionHelper.getFieldValue(null, "value", String.class);
        assertTrue(result.isEmpty());
    }

    @Test
    void getStaticFieldValue_validField_returnsValue() {
        Optional<Integer> value = ReflectionHelper.getStaticFieldValue(
            Integer.class, "MAX_VALUE", Integer.class);
        assertTrue(value.isPresent());
        assertEquals(Integer.MAX_VALUE, value.get());
    }

    @Test
    void getStaticFieldValue_nonexistentField_returnsEmpty() {
        Optional<Integer> value = ReflectionHelper.getStaticFieldValue(
            Integer.class, "FAKE_FIELD", Integer.class);
        assertTrue(value.isEmpty());
    }

    @Test
    void setFieldValue_nullInstance_returnsFalse() {
        assertFalse(ReflectionHelper.setFieldValue(null, "field", "value"));
    }

    // ================================================================
    // Constructor access
    // ================================================================

    @Test
    void findConstructor_noArg_found() {
        Constructor<StringBuilder> ctor = ReflectionHelper.findConstructor(
            StringBuilder.class);
        assertNotNull(ctor);
    }

    @Test
    void findConstructor_withArgs_found() {
        Constructor<StringBuilder> ctor = ReflectionHelper.findConstructor(
            StringBuilder.class, String.class);
        assertNotNull(ctor);
    }

    @Test
    void findConstructor_wrongParams_returnsNull() {
        Constructor<StringBuilder> ctor = ReflectionHelper.findConstructor(
            StringBuilder.class, java.io.File.class);
        assertNull(ctor);
    }

    @Test
    void newInstance_noArg_createsInstance() {
        StringBuilder sb = ReflectionHelper.newInstance(StringBuilder.class);
        assertNotNull(sb);
        assertEquals(0, sb.length());
    }

    @Test
    void newInstance_withArgs_createsInstance() {
        StringBuilder sb = ReflectionHelper.newInstance(StringBuilder.class, "hello");
        assertNotNull(sb);
        assertEquals("hello", sb.toString());
    }

    // ================================================================
    // Type checking
    // ================================================================

    @Test
    void implementsInterface_directInterface_returnsTrue() {
        assertTrue(ReflectionHelper.implementsInterface(
            java.util.ArrayList.class, "java.util.List"));
    }

    @Test
    void implementsInterface_parentInterface_returnsTrue() {
        // ArrayList implements List which extends Collection
        assertTrue(ReflectionHelper.implementsInterface(
            java.util.ArrayList.class, "java.util.Collection"));
    }

    @Test
    void implementsInterface_unrelatedInterface_returnsFalse() {
        assertFalse(ReflectionHelper.implementsInterface(
            String.class, "java.util.List"));
    }

    @Test
    void implementsInterface_nullClass_returnsFalse() {
        assertFalse(ReflectionHelper.implementsInterface(null, "java.util.List"));
    }

    @Test
    void extendsClass_directParent_returnsTrue() {
        assertTrue(ReflectionHelper.extendsClass(
            java.util.ArrayList.class, "java.util.AbstractList"));
    }

    @Test
    void extendsClass_transitiveParent_returnsTrue() {
        assertTrue(ReflectionHelper.extendsClass(
            java.util.ArrayList.class, "java.util.AbstractCollection"));
    }

    @Test
    void extendsClass_unrelatedClass_returnsFalse() {
        assertFalse(ReflectionHelper.extendsClass(
            String.class, "java.util.List"));
    }

    @Test
    void extendsClass_nullClass_returnsFalse() {
        assertFalse(ReflectionHelper.extendsClass(null, "java.util.List"));
    }

    @Test
    void getAllInterfaces_returnsCompleteSet() {
        Set<Class<?>> interfaces = ReflectionHelper.getAllInterfaces(
            java.util.ArrayList.class);
        assertFalse(interfaces.isEmpty());
        assertTrue(interfaces.contains(java.util.List.class));
        assertTrue(interfaces.contains(java.util.Collection.class));
        assertTrue(interfaces.contains(java.lang.Iterable.class));
        assertTrue(interfaces.contains(java.io.Serializable.class));
    }

    // ================================================================
    // Proxy creation
    // ================================================================

    @Test
    void createProxy_implementsInterface() {
        Runnable proxy = ReflectionHelper.createProxy(Runnable.class, call -> null);
        assertNotNull(proxy);
        // Should not throw
        proxy.run();
    }

    @Test
    void createProxy_routesMethodCalls() {
        Comparable<String> proxy = ReflectionHelper.createProxy(
            Comparable.class,
            call -> {
                if ("compareTo".equals(call.methodName())) {
                    return 42;
                }
                return 0;
            }
        );

        assertEquals(42, proxy.compareTo("anything"));
    }

    // ================================================================
    // ProxyCall record
    // ================================================================

    @Test
    void proxyCall_argCount_returnsCorrectCount() {
        var call = new ReflectionHelper.ProxyCall("test", new Object[]{"a", "b"});
        assertEquals(2, call.argCount());
    }

    @Test
    void proxyCall_arg_returnsCorrectValue() {
        var call = new ReflectionHelper.ProxyCall("test", new Object[]{"hello", 42});
        assertEquals("hello", call.arg(0));
        assertEquals(42, call.arg(1));
    }

    @Test
    void proxyCall_typedArg_correctType_returnsValue() {
        var call = new ReflectionHelper.ProxyCall("test", new Object[]{"hello"});
        assertEquals("hello", call.arg(0, String.class));
    }

    @Test
    void proxyCall_typedArg_wrongType_returnsNull() {
        var call = new ReflectionHelper.ProxyCall("test", new Object[]{"hello"});
        assertNull(call.arg(0, Integer.class));
    }

    // ================================================================
    // Cache stats / clearing
    // ================================================================

    @Test
    void getCacheStats_containsLabels() {
        String stats = ReflectionHelper.getCacheStats();
        assertTrue(stats.contains("handles="));
        assertTrue(stats.contains("methods="));
        assertTrue(stats.contains("fields="));
        assertTrue(stats.contains("constructors="));
    }

    @Test
    void clearCaches_resetsCounters() {
        // Populate caches
        ReflectionHelper.findMethodHandle(String.class, "length");
        ReflectionHelper.findMethodInHierarchy(String.class, "hashCode");
        ReflectionHelper.findFieldInHierarchy(Integer.class, "MAX_VALUE");
        ReflectionHelper.findConstructor(StringBuilder.class);

        ReflectionHelper.clearCaches();

        // Verify stats show zeroes
        String stats = ReflectionHelper.getCacheStats();
        assertTrue(stats.contains("handles=0"));
        assertTrue(stats.contains("methods=0"));
        assertTrue(stats.contains("fields=0"));
        assertTrue(stats.contains("constructors=0"));
    }
}

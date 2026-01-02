package com.devmod.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced reflection helper for compat modules.
 *
 * <p>Provides:
 * <ul>
 *   <li>MethodHandle-based invocation (faster than Method.invoke)</li>
 *   <li>Cached lookups with hierarchical search</li>
 *   <li>Type-safe invocation helpers</li>
 *   <li>Constructor access</li>
 *   <li>Field access (get/set)</li>
 *   <li>Interface implementation checking</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Fast method invocation via MethodHandle
 * MethodHandle mh = ReflectionHelper.findMethodHandle(MyClass.class, "myMethod", String.class);
 * String result = (String) mh.invoke(instance, "arg");
 *
 * // Cached class hierarchy search
 * Method m = ReflectionHelper.findMethodInHierarchy(clazz, "methodName", int.class);
 *
 * // Type-safe getter
 * Optional<String> value = ReflectionHelper.getFieldValue(instance, "fieldName", String.class);
 * }</pre>
 */
public final class ReflectionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionHelper.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // ============================================================================
    // CACHES
    // ============================================================================

    private static final Map<String, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private ReflectionHelper() {}

    // ============================================================================
    // METHOD HANDLES (Fast invocation)
    // ============================================================================

    /**
     * Find a MethodHandle for a method (faster than Method.invoke).
     *
     * @param clazz The class containing the method
     * @param methodName The method name
     * @param parameterTypes Parameter types
     * @return The MethodHandle, or null if not found
     */
    @Nullable
    public static MethodHandle findMethodHandle(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = makeMethodKey(clazz, methodName, parameterTypes);

        return METHOD_HANDLE_CACHE.computeIfAbsent(key, k -> {
            try {
                Method method = clazz.getMethod(methodName, parameterTypes);
                return LOOKUP.unreflect(method);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                LOGGER.debug("[ReflectionHelper] No MethodHandle for {}.{}: {}",
                    clazz.getSimpleName(), methodName, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Find a MethodHandle for a declared method (including private).
     */
    @Nullable
    public static MethodHandle findDeclaredMethodHandle(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = makeMethodKey(clazz, methodName, parameterTypes) + "#declared";

        return METHOD_HANDLE_CACHE.computeIfAbsent(key, k -> {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return LOOKUP.unreflect(method);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                LOGGER.debug("[ReflectionHelper] No declared MethodHandle for {}.{}: {}",
                    clazz.getSimpleName(), methodName, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Find a static method handle.
     */
    @Nullable
    public static MethodHandle findStaticMethodHandle(Class<?> clazz, String methodName,
                                                       Class<?> returnType, Class<?>... parameterTypes) {
        String key = makeMethodKey(clazz, methodName, parameterTypes) + "#static";

        return METHOD_HANDLE_CACHE.computeIfAbsent(key, k -> {
            try {
                MethodType type = MethodType.methodType(returnType, parameterTypes);
                return LOOKUP.findStatic(clazz, methodName, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                LOGGER.debug("[ReflectionHelper] No static MethodHandle for {}.{}: {}",
                    clazz.getSimpleName(), methodName, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Invoke a MethodHandle safely.
     */
    @Nullable
    public static Object invokeHandle(@Nullable MethodHandle handle, Object... args) {
        if (handle == null) return null;

        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable e) {
            LOGGER.debug("[ReflectionHelper] MethodHandle invocation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Invoke a MethodHandle and cast the result.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T invokeHandleAs(@Nullable MethodHandle handle, Class<T> resultType, Object... args) {
        Object result = invokeHandle(handle, args);
        if (result != null && resultType.isInstance(result)) {
            return (T) result;
        }
        return null;
    }

    // ============================================================================
    // METHOD LOOKUP
    // ============================================================================

    /**
     * Find a method in a class hierarchy (searches superclasses and interfaces).
     *
     * @param clazz Starting class
     * @param methodName Method name
     * @param parameterTypes Parameter types
     * @return The method, or null if not found
     */
    @Nullable
    public static Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = makeMethodKey(clazz, methodName, parameterTypes) + "#hierarchy";

        return METHOD_CACHE.computeIfAbsent(key, k -> {
            Class<?> current = clazz;

            while (current != null && current != Object.class) {
                // Check current class
                try {
                    return current.getDeclaredMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                    // Continue searching
                }

                // Check interfaces
                for (Class<?> iface : current.getInterfaces()) {
                    Method m = findMethodInHierarchy(iface, methodName, parameterTypes);
                    if (m != null) return m;
                }

                current = current.getSuperclass();
            }

            return null;
        });
    }

    /**
     * Find a method by name only (ignores parameter types).
     * Returns first match found.
     */
    @Nullable
    public static Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }

        return null;
    }

    /**
     * Find all methods with a specific name.
     */
    public static java.util.List<Method> findAllMethodsByName(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getMethods())
            .filter(m -> m.getName().equals(methodName))
            .toList();
    }

    // ============================================================================
    // FIELD ACCESS
    // ============================================================================

    /**
     * Find a field in a class hierarchy.
     */
    @Nullable
    public static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName + "#hierarchy";

        return FIELD_CACHE.computeIfAbsent(key, k -> {
            Class<?> current = clazz;

            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Continue searching
                }

                current = current.getSuperclass();
            }

            return null;
        });
    }

    /**
     * Get a field value with type safety.
     *
     * @param instance The object instance
     * @param fieldName The field name
     * @param expectedType The expected value type
     * @return Optional containing the value, or empty if not found/wrong type
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getFieldValue(Object instance, String fieldName, Class<T> expectedType) {
        if (instance == null) return Optional.empty();

        Field field = findFieldInHierarchy(instance.getClass(), fieldName);
        if (field == null) return Optional.empty();

        try {
            Object value = field.get(instance);
            if (value != null && expectedType.isInstance(value)) {
                return Optional.of((T) value);
            }
        } catch (IllegalAccessException e) {
            LOGGER.debug("[ReflectionHelper] Cannot access field {}: {}", fieldName, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get a static field value.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getStaticFieldValue(Class<?> clazz, String fieldName, Class<T> expectedType) {
        Field field = findFieldInHierarchy(clazz, fieldName);
        if (field == null) return Optional.empty();

        try {
            Object value = field.get(null);
            if (value != null && expectedType.isInstance(value)) {
                return Optional.of((T) value);
            }
        } catch (IllegalAccessException e) {
            LOGGER.debug("[ReflectionHelper] Cannot access static field {}: {}", fieldName, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Set a field value.
     *
     * @param instance The object instance
     * @param fieldName The field name
     * @param value The value to set
     * @return true if successful
     */
    public static boolean setFieldValue(Object instance, String fieldName, Object value) {
        if (instance == null) return false;

        Field field = findFieldInHierarchy(instance.getClass(), fieldName);
        if (field == null) return false;

        try {
            // Handle final fields
            if (Modifier.isFinal(field.getModifiers())) {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            }

            field.set(instance, value);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[ReflectionHelper] Cannot set field {}: {}", fieldName, e.getMessage());
            return false;
        }
    }

    // ============================================================================
    // CONSTRUCTOR ACCESS
    // ============================================================================

    /**
     * Find a constructor by parameter types.
     */
    @Nullable
    public static <T> Constructor<T> findConstructor(Class<T> clazz, Class<?>... parameterTypes) {
        String key = clazz.getName() + "#<init>#" + Arrays.toString(parameterTypes);

        @SuppressWarnings("unchecked")
        Constructor<T> cached = (Constructor<T>) CONSTRUCTOR_CACHE.get(key);
        if (cached != null) return cached;

        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor(parameterTypes);
            ctor.setAccessible(true);
            CONSTRUCTOR_CACHE.put(key, ctor);
            return ctor;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[ReflectionHelper] No constructor for {} with params {}",
                clazz.getSimpleName(), Arrays.toString(parameterTypes));
            return null;
        }
    }

    /**
     * Create a new instance using reflection.
     */
    @Nullable
    public static <T> T newInstance(Class<T> clazz, Object... args) {
        Class<?>[] parameterTypes = Arrays.stream(args)
            .map(Object::getClass)
            .toArray(Class<?>[]::new);

        Constructor<T> ctor = findConstructor(clazz, parameterTypes);
        if (ctor == null) return null;

        try {
            return ctor.newInstance(args);
        } catch (Exception e) {
            LOGGER.debug("[ReflectionHelper] Cannot instantiate {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Create a new instance using no-arg constructor.
     */
    @Nullable
    public static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            LOGGER.debug("[ReflectionHelper] Cannot instantiate {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ============================================================================
    // TYPE CHECKING
    // ============================================================================

    /**
     * Check if a class implements an interface (by name, to avoid class loading).
     */
    public static boolean implementsInterface(Class<?> clazz, String interfaceName) {
        if (clazz == null) return false;

        // Check direct interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.getName().equals(interfaceName)) {
                return true;
            }
            // Check parent interfaces
            if (implementsInterface(iface, interfaceName)) {
                return true;
            }
        }

        // Check superclass
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return implementsInterface(superclass, interfaceName);
        }

        return false;
    }

    /**
     * Check if a class extends another (by name).
     */
    public static boolean extendsClass(Class<?> clazz, String className) {
        if (clazz == null) return false;

        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            if (current.getName().equals(className)) {
                return true;
            }
            current = current.getSuperclass();
        }

        return false;
    }

    /**
     * Get all interfaces implemented by a class (including inherited).
     */
    public static java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        java.util.Set<Class<?>> interfaces = new java.util.LinkedHashSet<>();
        collectInterfaces(clazz, interfaces);
        return interfaces;
    }

    private static void collectInterfaces(Class<?> clazz, java.util.Set<Class<?>> result) {
        if (clazz == null) return;

        for (Class<?> iface : clazz.getInterfaces()) {
            result.add(iface);
            collectInterfaces(iface, result);
        }

        collectInterfaces(clazz.getSuperclass(), result);
    }

    // ============================================================================
    // PROXY CREATION
    // ============================================================================

    /**
     * Create a proxy that delegates to a handler function.
     * Useful for implementing interfaces dynamically.
     *
     * @param interfaceType The interface to implement
     * @param handler Function that handles method calls: (methodName, args) -> result
     * @return A proxy instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceType, Function<ProxyCall, Object> handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},
            (proxy, method, args) -> handler.apply(new ProxyCall(method.getName(), args != null ? args : new Object[0]))
        );
    }

    /**
     * Proxy method call information.
     */
    public record ProxyCall(String methodName, Object[] args) {
        public int argCount() {
            return args.length;
        }

        public Object arg(int index) {
            return args[index];
        }

        @Nullable
        public <T> T arg(int index, Class<T> type) {
            Object value = args[index];
            if (value != null && type.isInstance(value)) {
                return type.cast(value);
            }
            return null;
        }
    }

    // ============================================================================
    // UTILITIES
    // ============================================================================

    private static String makeMethodKey(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        return clazz.getName() + "#" + methodName + "#" + Arrays.toString(parameterTypes);
    }

    /**
     * Clear all caches.
     */
    public static void clearCaches() {
        METHOD_HANDLE_CACHE.clear();
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        CONSTRUCTOR_CACHE.clear();
        LOGGER.debug("[ReflectionHelper] Caches cleared");
    }

    /**
     * Get cache statistics.
     */
    public static String getCacheStats() {
        return String.format(
            "ReflectionHelper caches: handles=%d, methods=%d, fields=%d, constructors=%d",
            METHOD_HANDLE_CACHE.size(),
            METHOD_CACHE.size(),
            FIELD_CACHE.size(),
            CONSTRUCTOR_CACHE.size()
        );
    }
}

package com.devmod.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionRegistry;

/**
 * Abstract base class for compatibility modules.
 *
 * <p>Provides common functionality:
 * <ul>
 *   <li>Lazy initialization with proper state tracking</li>
 *   <li>Reflection method/class caching</li>
 *   <li>Standard error handling and logging</li>
 *   <li>Version checking</li>
 *   <li>Active state management</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * public class MyModCompat extends BaseCompatModule {
 *     public MyModCompat() {
 *         super("mymod", "My Mod");
 *     }
 *
 *     @Override
 *     protected void doInitCommon() {
 *         // Load classes and methods via reflection
 *         loadRequiredClass("com.mymod.api.MyApi");
 *         cacheMethod("doSomething", () -> getMethod(apiClass, "doSomething", String.class));
 *     }
 *
 *     @Override
 *     protected void doInitClient() {
 *         // Client-specific initialization
 *     }
 * }
 * }</pre>
 */
public abstract class BaseCompatModule implements CompatModule {

    protected final Logger logger;
    protected final String modId;
    protected final String displayName;
    protected final int priority;

    // ============================================================================
    // STATE
    // ============================================================================

    /** Initialization state */
    protected volatile InitState initState = InitState.NOT_STARTED;

    /** Whether the module is functionally available after init */
    protected volatile boolean available = false;

    /** Error message if initialization failed */
    @Nullable
    protected String initError = null;

    // ============================================================================
    // CACHES
    // ============================================================================

    /** Cached class references */
    protected final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    /** Cached method references (Optional to cache negative lookups — ConcurrentHashMap ignores null) */
    protected final Map<String, Optional<Method>> methodCache = new ConcurrentHashMap<>();

    /** Generic object cache for misc data */
    protected final Map<String, Object> objectCache = new ConcurrentHashMap<>();

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    /**
     * Create a new compat module.
     *
     * @param modId The target mod ID
     */
    protected BaseCompatModule(String modId) {
        this(modId, modId, 100);
    }

    /**
     * Create a new compat module with display name.
     *
     * @param modId The target mod ID
     * @param displayName Human-readable name
     */
    protected BaseCompatModule(String modId, String displayName) {
        this(modId, displayName, 100);
    }

    /**
     * Create a new compat module with full configuration.
     *
     * @param modId The target mod ID
     * @param displayName Human-readable name
     * @param priority Initialization priority (lower = earlier)
     */
    protected BaseCompatModule(String modId, String displayName, int priority) {
        this.modId = modId;
        this.displayName = displayName;
        this.priority = priority;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    // ============================================================================
    // COMPAT MODULE INTERFACE
    // ============================================================================

    @Override
    public final String modId() {
        return modId;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final int priority() {
        return priority;
    }

    @Override
    public final void initCommon() {
        if (initState != InitState.NOT_STARTED) {
            logger.debug("[Compat:{}] Skipping initCommon - already {}", modId, initState);
            return;
        }

        initState = InitState.COMMON_STARTED;

        if (!Compat.isLoaded(modId)) {
            logger.debug("[Compat:{}] Mod not loaded, skipping initialization", modId);
            initState = InitState.SKIPPED;
            return;
        }

        if (!isVersionCompatible()) {
            String minVer = getMinVersion();
            String loadedVer = Compat.getVersion(modId);
            logger.warn("[Compat:{}] Version {} incompatible (requires {}), skipping",
                modId, loadedVer, minVer);
            initState = InitState.VERSION_MISMATCH;
            return;
        }

        try {
            doInitCommon();
            available = true;
            initState = InitState.COMMON_COMPLETE;
            logger.info("[Compat:{}] {} v{} initialized successfully",
                modId, displayName, Compat.getVersion(modId));
        } catch (Exception e) {
            available = false;
            initError = e.getMessage();
            initState = InitState.FAILED;
            logger.error("[Compat:{}] Initialization failed: {}", modId, e.getMessage(), e);
        }
    }

    @Override
    public final void initClient() {
        if (!available) {
            logger.debug("[Compat:{}] Skipping initClient - not available", modId);
            return;
        }

        if (initState == InitState.CLIENT_COMPLETE) {
            logger.debug("[Compat:{}] Skipping initClient - already done", modId);
            return;
        }

        try {
            doInitClient();
            initState = InitState.CLIENT_COMPLETE;
            logger.debug("[Compat:{}] Client initialization complete", modId);
        } catch (Exception e) {
            logger.error("[Compat:{}] Client initialization failed: {}", modId, e.getMessage(), e);
        }
    }

    @Override
    public void registerActions(@Nullable ActionRegistry registry) {
        if (!available || registry == null) return;

        try {
            doRegisterActions(registry);
        } catch (Exception e) {
            logger.error("[Compat:{}] Failed to register actions: {}", modId, e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        try {
            doShutdown();
        } catch (Exception e) {
            logger.warn("[Compat:{}] Error during shutdown: {}", modId, e.getMessage());
        } finally {
            clearCaches();
            available = false;
            initState = InitState.SHUTDOWN;
        }
    }

    @Override
    public final boolean isActive() {
        return available && initState.isOperational();
    }

    // ============================================================================
    // ABSTRACT METHODS (Override these)
    // ============================================================================

    /**
     * Initialize common functionality. Called when mod is loaded and version is compatible.
     * Override to load classes, cache methods, register events.
     *
     * @throws Exception if initialization fails critically
     */
    protected abstract void doInitCommon() throws Exception;

    /**
     * Initialize client functionality. Called after doInitCommon() on physical client.
     * Override for client-specific setup (renderers, HUD, keybinds).
     */
    protected void doInitClient() {
        // Default: no client initialization
    }

    /**
     * Register actions with the action system.
     * Override to add mod-specific actions to radial menu, etc.
     *
     * @param registry The action registry
     */
    protected void doRegisterActions(ActionRegistry registry) {
        // Default: no actions
    }

    /**
     * Clean up resources during shutdown.
     * Override to release resources, close connections, etc.
     */
    protected void doShutdown() {
        // Default: no cleanup
    }

    // ============================================================================
    // REFLECTION HELPERS
    // ============================================================================

    /**
     * Load a required class. If not found, throws exception to fail initialization.
     *
     * @param className Fully qualified class name
     * @return The loaded class
     * @throws ClassNotFoundException if class not found
     */
    protected Class<?> loadRequiredClass(String className) throws ClassNotFoundException {
        Class<?> clazz = classCache.get(className);
        if (clazz != null) {
            return clazz;
        }

        clazz = Class.forName(className);
        classCache.put(className, clazz);
        logger.debug("[Compat:{}] Loaded class: {}", modId, className);
        return clazz;
    }

    /**
     * Load an optional class. Returns null if not found.
     *
     * @param className Fully qualified class name
     * @return The loaded class, or null
     */
    @Nullable
    protected Class<?> loadOptionalClass(String className) {
        Class<?> clazz = classCache.get(className);
        if (clazz != null) {
            return clazz;
        }

        clazz = Compat.loadClass(className);
        if (clazz != null) {
            classCache.put(className, clazz);
            logger.debug("[Compat:{}] Loaded optional class: {}", modId, className);
        }
        return clazz;
    }

    /**
     * Load a class from multiple possible names. Returns first found.
     *
     * @param classNames Possible class names to try
     * @return The loaded class, or null if none found
     */
    @Nullable
    protected Class<?> loadClassFromAny(String... classNames) {
        for (String className : classNames) {
            Class<?> clazz = loadOptionalClass(className);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    /**
     * Get a method from a class, caching the result.
     *
     * @param clazz The class
     * @param methodName Method name
     * @param parameterTypes Parameter types
     * @return The method, or null if not found
     */
    @Nullable
    protected Method getMethod(@Nullable Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;

        String key = clazz.getName() + "#" + methodName;
        // Wrap in Optional so null results (method not found) are cached properly —
        // ConcurrentHashMap.computeIfAbsent silently ignores null returns (JDK-8161372).
        return methodCache.computeIfAbsent(key,
            k -> Optional.ofNullable(Compat.getMethod(clazz, methodName, parameterTypes))).orElse(null);
    }

    /**
     * Get a declared method (including private) from a class.
     *
     * @param clazz The class
     * @param methodName Method name
     * @param parameterTypes Parameter types
     * @return The method (made accessible), or null if not found
     */
    @Nullable
    protected Method getDeclaredMethod(@Nullable Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;

        String key = clazz.getName() + "#" + methodName + "#declared";
        return methodCache.computeIfAbsent(key,
            k -> Optional.ofNullable(Compat.getDeclaredMethod(clazz, methodName, parameterTypes))).orElse(null);
    }

    /**
     * Cache a method using a supplier (for complex lookups).
     *
     * @param key Cache key
     * @param supplier Method supplier
     * @return The cached or computed method
     */
    @Nullable
    protected Method cacheMethod(String key, Supplier<Method> supplier) {
        return methodCache.computeIfAbsent(key, k -> Optional.ofNullable(supplier.get())).orElse(null);
    }

    /**
     * Safely invoke a method.
     *
     * @param method The method to invoke
     * @param instance Instance (null for static)
     * @param args Arguments
     * @return Result or null if failed
     */
    @Nullable
    protected Object invoke(@Nullable Method method, @Nullable Object instance, Object... args) {
        return Compat.invoke(method, instance, args);
    }

    /**
     * Invoke a method and cast the result.
     *
     * @param method The method
     * @param resultType Expected result type
     * @param instance Instance (null for static)
     * @param args Arguments
     * @return Cast result or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T invokeAs(@Nullable Method method, Class<T> resultType,
                             @Nullable Object instance, Object... args) {
        Object result = invoke(method, instance, args);
        if (result != null && resultType.isInstance(result)) {
            return (T) result;
        }
        return null;
    }

    /**
     * Get a cached object.
     */
    @Nullable
    protected <T> T getCached(String key, Class<T> type) {
        Object value = objectCache.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Cache an object.
     */
    protected void cache(String key, Object value) {
        objectCache.put(key, value);
    }

    /**
     * Clear all caches.
     */
    protected void clearCaches() {
        classCache.clear();
        methodCache.clear();
        objectCache.clear();
    }

    // ============================================================================
    // LOGGING HELPERS
    // ============================================================================

    /**
     * Log a debug message with mod prefix.
     */
    protected void debug(String message, Object... args) {
        logger.debug("[Compat:{}] " + message, prependModId(args));
    }

    /**
     * Log an info message with mod prefix.
     */
    protected void info(String message, Object... args) {
        logger.info("[Compat:{}] " + message, prependModId(args));
    }

    /**
     * Log a warning with mod prefix.
     */
    protected void warn(String message, Object... args) {
        logger.warn("[Compat:{}] " + message, prependModId(args));
    }

    /**
     * Log an error with mod prefix.
     */
    protected void error(String message, Object... args) {
        logger.error("[Compat:{}] " + message, prependModId(args));
    }

    private Object[] prependModId(Object[] args) {
        Object[] result = new Object[args.length + 1];
        result[0] = modId;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }

    // ============================================================================
    // STATE
    // ============================================================================

    /**
     * Get the initialization state.
     */
    public InitState getInitState() {
        return initState;
    }

    /**
     * Get the initialization error message, if any.
     */
    @Nullable
    public String getInitError() {
        return initError;
    }

    /**
     * Check if module is available and functional.
     */
    public boolean isAvailable() {
        return available;
    }

    // ============================================================================
    // INIT STATE ENUM
    // ============================================================================

    /**
     * Initialization state enum.
     */
    public enum InitState {
        NOT_STARTED(false),
        COMMON_STARTED(false),
        COMMON_COMPLETE(true),
        CLIENT_COMPLETE(true),
        SKIPPED(false),
        VERSION_MISMATCH(false),
        FAILED(false),
        SHUTDOWN(false);

        private final boolean operational;

        InitState(boolean operational) {
            this.operational = operational;
        }

        public boolean isOperational() {
            return operational;
        }
    }
}

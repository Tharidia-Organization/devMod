package com.devmod.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P2 Architecture: Lightweight service registry for dependency injection.
 * Replaces direct singleton access with a testable registry pattern.
 *
 * <p>Usage:
 * <pre>
 * // Registration (in mod initialization):
 * ServiceRegistry.register(IWaveManager.class, WaveManager::getInstance);
 *
 * // Retrieval (in consumer code):
 * IWaveManager waveManager = ServiceRegistry.get(IWaveManager.class);
 *
 * // Testing (replace with mock):
 * ServiceRegistry.override(IWaveManager.class, mockWaveManager);
 * </pre>
 *
 * <p>Benefits:
 * <ul>
 *   <li>Decouples consumers from concrete implementations</li>
 *   <li>Enables easy mocking in tests</li>
 *   <li>Supports lazy initialization</li>
 *   <li>Thread-safe</li>
 * </ul>
 */
public final class ServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistry.class);

    /**
     * Service entry supporting lazy initialization and overrides.
     */
    private static final class ServiceEntry<T> {
        private final Supplier<T> factory;
        @Nullable private volatile T instance;
        @Nullable private volatile T override;

        ServiceEntry(Supplier<T> factory) {
            this.factory = factory;
        }

        T get() {
            if (override != null) {
                return override;
            }
            T result = instance;
            if (result == null) {
                synchronized (this) {
                    result = instance;
                    if (result == null) {
                        result = factory.get();
                        instance = result;
                    }
                }
            }
            return result;
        }

        void setOverride(@Nullable T override) {
            this.override = override;
        }

        void clearOverride() {
            this.override = null;
        }
    }

    private static final Map<Class<?>, ServiceEntry<?>> SERVICES = new ConcurrentHashMap<>();

    private ServiceRegistry() {} // Utility class

    /**
     * Register a service with a factory for lazy initialization.
     *
     * @param <T> Service interface type
     * @param serviceClass The service interface class
     * @param factory Factory to create the service instance
     */
    public static <T> void register(Class<T> serviceClass, Supplier<T> factory) {
        if (SERVICES.containsKey(serviceClass)) {
            LOGGER.warn("Service already registered: {}. Skipping.", serviceClass.getName());
            return;
        }
        SERVICES.put(serviceClass, new ServiceEntry<>(factory));
        LOGGER.debug("Registered service: {}", serviceClass.getName());
    }

    /**
     * Register a service with an existing instance (eager initialization).
     *
     * @param <T> Service interface type
     * @param serviceClass The service interface class
     * @param instance The service instance
     */
    public static <T> void registerInstance(Class<T> serviceClass, T instance) {
        register(serviceClass, () -> instance);
    }

    /**
     * Get a service by its interface.
     *
     * @param <T> Service interface type
     * @param serviceClass The service interface class
     * @return The service instance
     * @throws IllegalStateException if service is not registered
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> serviceClass) {
        ServiceEntry<T> entry = (ServiceEntry<T>) SERVICES.get(serviceClass);
        if (entry == null) {
            throw new IllegalStateException("Service not registered: " + serviceClass.getName());
        }
        return entry.get();
    }

    /**
     * Get a service, returning null if not registered.
     *
     * @param <T> Service interface type
     * @param serviceClass The service interface class
     * @return The service instance, or null if not registered
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getOrNull(Class<T> serviceClass) {
        ServiceEntry<T> entry = (ServiceEntry<T>) SERVICES.get(serviceClass);
        return entry != null ? entry.get() : null;
    }

    /**
     * Check if a service is registered.
     *
     * @param serviceClass The service interface class
     * @return true if registered
     */
    public static boolean isRegistered(Class<?> serviceClass) {
        return SERVICES.containsKey(serviceClass);
    }

    /**
     * Override a service for testing purposes.
     * Call {@link #clearOverride(Class)} to restore the original.
     *
     * @param <T> Service interface type
     * @param serviceClass The service interface class
     * @param override The override instance
     */
    @SuppressWarnings("unchecked")
    public static <T> void override(Class<T> serviceClass, T override) {
        ServiceEntry<T> entry = (ServiceEntry<T>) SERVICES.get(serviceClass);
        if (entry == null) {
            // Register with override as factory for unregistered services
            register(serviceClass, () -> override);
            return;
        }
        entry.setOverride(override);
        LOGGER.debug("Overrode service: {}", serviceClass.getName());
    }

    /**
     * Clear an override, restoring the original service.
     *
     * @param serviceClass The service interface class
     */
    @SuppressWarnings("unchecked")
    public static <T> void clearOverride(Class<T> serviceClass) {
        ServiceEntry<T> entry = (ServiceEntry<T>) SERVICES.get(serviceClass);
        if (entry != null) {
            entry.clearOverride();
            LOGGER.debug("Cleared override for service: {}", serviceClass.getName());
        }
    }

    /**
     * Clear all overrides. Useful in test teardown.
     */
    public static void clearAllOverrides() {
        SERVICES.values().forEach(ServiceEntry::clearOverride);
        LOGGER.debug("Cleared all service overrides");
    }

    /**
     * Unregister all services. Only for testing.
     */
    public static void reset() {
        SERVICES.clear();
        LOGGER.debug("Service registry reset");
    }

    /**
     * Get count of registered services.
     */
    public static int size() {
        return SERVICES.size();
    }
}

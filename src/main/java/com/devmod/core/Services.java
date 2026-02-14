package com.devmod.core;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.config.TesterModality;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.WaveManager;
import com.devmod.mailbox.MailboxManager;
import com.devmod.notification.NotificationService;
import com.devmod.party.PartyManager;
import com.devmod.runtime.InstanceManager;
import com.devmod.telemetry.TelemetryService;

/**
 * P2 Architecture: Central service access point.
 * Provides type-safe access to core services via {@link ServiceRegistry}.
 *
 * <p>All accessors delegate to the registry. If a service was not registered
 * (e.g. {@link #endurance()} when {@link TesterModality} is disabled), the
 * accessor returns {@code null} rather than silently falling back to a
 * singleton. This makes the registry meaningful for testing and ensures
 * callers handle missing services explicitly.</p>
 *
 * <p>Benefits over direct INSTANCE access:
 * <ul>
 *   <li>Testable - services can be mocked via ServiceRegistry.override()</li>
 *   <li>Discoverable - all core services in one place</li>
 *   <li>Future-proof - easy to migrate to full DI later</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Instead of: PartyManager.INSTANCE.createParty(...)
 * Services.party().createParty(...)
 *
 * // For testing:
 * ServiceRegistry.override(PartyManager.class, mockPartyManager);
 * Services.party().createParty(...) // uses mock
 * </pre>
 */
public final class Services {

    private static final Logger LOGGER = LoggerFactory.getLogger(Services.class);
    private static volatile boolean initialized = false;

    private Services() {} // Utility class

    /**
     * Initialize all core services in the registry.
     * Should be called once during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Services already initialized, skipping");
            return;
        }

        // Register core services with lazy factories pointing to existing singletons
        ServiceRegistry.register(PartyManager.class, () -> PartyManager.INSTANCE);
        ServiceRegistry.register(WaveManager.class, () -> WaveManager.INSTANCE);
        ServiceRegistry.register(TelemetryService.class, () -> TelemetryService.INSTANCE);
        ServiceRegistry.register(NotificationService.class, () -> NotificationService.INSTANCE);
        ServiceRegistry.register(MailboxManager.class, () -> MailboxManager.INSTANCE);
        ServiceRegistry.register(InstanceManager.class, () -> InstanceManager.INSTANCE);
        if (TesterModality.isEnabled()) {
            ServiceRegistry.register(EnduranceQuestManager.class, () -> EnduranceQuestManager.INSTANCE);
        }

        initialized = true;
        LOGGER.info("Initialized {} core services in registry", ServiceRegistry.size());
    }

    /**
     * Check if services have been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // === Type-safe Service Accessors ===

    /**
     * Get the party management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static PartyManager party() {
        return getRequired(PartyManager.class);
    }

    /**
     * Get the wave management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static WaveManager waves() {
        return getRequired(WaveManager.class);
    }

    /**
     * Get the telemetry service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static TelemetryService telemetry() {
        return getRequired(TelemetryService.class);
    }

    /**
     * Get the notification service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static NotificationService notifications() {
        return getRequired(NotificationService.class);
    }

    /**
     * Get the mailbox management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static MailboxManager mailbox() {
        return getRequired(MailboxManager.class);
    }

    /**
     * Get the instance management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static InstanceManager instances() {
        return getRequired(InstanceManager.class);
    }

    /**
     * Get the endurance quest management service.
     * Returns {@code null} when {@link TesterModality} is disabled,
     * since the endurance module is not registered in that case.
     *
     * @return the endurance service, or null if not registered
     */
    @Nullable
    public static EnduranceQuestManager endurance() {
        if (!initialized) {
            LOGGER.warn("Services.endurance() called before initialization");
            return null;
        }
        return ServiceRegistry.getOrNull(EnduranceQuestManager.class);
    }

    // === Helper Methods ===

    /**
     * Get a required service from the registry.
     * Throws if services are not initialized or the service is not registered.
     *
     * @throws IllegalStateException if not initialized or service not found
     */
    private static <T> T getRequired(Class<T> serviceClass) {
        if (!initialized) {
            throw new IllegalStateException(
                "Services not initialized. Call Services.initialize() during mod setup. "
                + "Requested: " + serviceClass.getName());
        }
        return ServiceRegistry.get(serviceClass);
    }

    /**
     * Reset services (for testing only).
     */
    public static void reset() {
        ServiceRegistry.reset();
        initialized = false;
        LOGGER.debug("Services reset");
    }
}

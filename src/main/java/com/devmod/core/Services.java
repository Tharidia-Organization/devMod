package com.devmod.core;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.WaveManager;
import com.devmod.mailbox.MailboxManager;
import com.devmod.notification.NotificationService;
import com.devmod.party.PartyManager;
import com.devmod.runtime.InstanceManager;
import com.devmod.telemetry.TelemetryService;

/**
 * P2 Architecture: Central service access point.
 * Provides type-safe access to core services while maintaining
 * backward compatibility with existing singleton patterns.
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
        ServiceRegistry.register(EnduranceQuestManager.class, () -> EnduranceQuestManager.INSTANCE);

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
     */
    public static PartyManager party() {
        return getOrFallback(PartyManager.class, PartyManager.INSTANCE);
    }

    /**
     * Get the wave management service.
     */
    public static WaveManager waves() {
        return getOrFallback(WaveManager.class, WaveManager.INSTANCE);
    }

    /**
     * Get the telemetry service.
     */
    public static TelemetryService telemetry() {
        return getOrFallback(TelemetryService.class, TelemetryService.INSTANCE);
    }

    /**
     * Get the notification service.
     */
    public static NotificationService notifications() {
        return getOrFallback(NotificationService.class, NotificationService.INSTANCE);
    }

    /**
     * Get the mailbox management service.
     */
    public static MailboxManager mailbox() {
        return getOrFallback(MailboxManager.class, MailboxManager.INSTANCE);
    }

    /**
     * Get the instance management service.
     */
    public static InstanceManager instances() {
        return getOrFallback(InstanceManager.class, InstanceManager.INSTANCE);
    }

    /**
     * Get the endurance quest management service.
     */
    public static EnduranceQuestManager endurance() {
        return getOrFallback(EnduranceQuestManager.class, EnduranceQuestManager.INSTANCE);
    }

    // === Helper Methods ===

    /**
     * Get service from registry, falling back to singleton if not registered.
     * This allows graceful operation even if initialize() wasn't called.
     */
    private static <T> T getOrFallback(Class<T> serviceClass, T fallback) {
        if (!initialized) {
            return fallback;
        }
        @Nullable T service = ServiceRegistry.getOrNull(serviceClass);
        return service != null ? service : fallback;
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

package com.devmod.arena.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * DD13: Event dispatcher for template lifecycle events.
 *
 * <p>Provides centralized event emission and listener registration for all
 * {@link TemplateEvent} types. Supports:
 * <ul>
 *   <li>Type-safe event subscription by event class</li>
 *   <li>Global listeners for all events</li>
 *   <li>Synchronous and asynchronous event emission</li>
 *   <li>Event filtering by template ID or arena ID</li>
 * </ul>
 *
 * <p>Thread-safe for concurrent listener registration and event emission.
 */
public class TemplateEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateEventDispatcher.class);

    private static final TemplateEventDispatcher INSTANCE = new TemplateEventDispatcher();

    private final Map<Class<? extends TemplateEvent>, List<Consumer<? extends TemplateEvent>>> listeners = new ConcurrentHashMap<>();
    private final List<Consumer<TemplateEvent>> globalListeners = new CopyOnWriteArrayList<>();
    private final Executor asyncExecutor;

    private volatile boolean enabled = true;

    public TemplateEventDispatcher() {
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public TemplateEventDispatcher(Executor executor) {
        this.asyncExecutor = executor;
    }

    /**
     * Returns the global singleton instance.
     */
    public static TemplateEventDispatcher getInstance() {
        return INSTANCE;
    }

    // ========== Listener Registration ==========

    /**
     * Registers a listener for a specific event type.
     *
     * @param eventType the event class to listen for
     * @param listener  the callback to invoke when event occurs
     * @param <T>       the event type
     * @return a registration handle for unregistering
     */
    public <T extends TemplateEvent> ListenerRegistration register(
            Class<T> eventType, Consumer<T> listener) {

        List<Consumer<? extends TemplateEvent>> typeListeners =
            listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

        typeListeners.add(listener);

        LOGGER.debug("Registered listener for {} (total: {})",
            eventType.getSimpleName(), typeListeners.size());

        return () -> {
            typeListeners.remove(listener);
            LOGGER.debug("Unregistered listener for {}", eventType.getSimpleName());
        };
    }

    /**
     * Registers a global listener for all event types.
     *
     * @param listener the callback to invoke for any event
     * @return a registration handle for unregistering
     */
    public ListenerRegistration registerGlobal(Consumer<TemplateEvent> listener) {
        globalListeners.add(listener);
        LOGGER.debug("Registered global listener (total: {})", globalListeners.size());

        return () -> {
            globalListeners.remove(listener);
            LOGGER.debug("Unregistered global listener");
        };
    }

    /**
     * Registers a listener filtered by template ID.
     *
     * @param eventType  the event class to listen for
     * @param templateId the template ID to filter by
     * @param listener   the callback to invoke
     * @param <T>        the event type
     * @return a registration handle
     */
    public <T extends TemplateEvent> ListenerRegistration registerForTemplate(
            Class<T> eventType, String templateId, Consumer<T> listener) {

        return register(eventType, event -> {
            if (templateId.equals(event.templateId())) {
                listener.accept(event);
            }
        });
    }

    // ========== Event Emission ==========

    /**
     * Emits an event synchronously to all registered listeners.
     *
     * @param event the event to emit
     */
    @SuppressWarnings("unchecked")
    public void emit(TemplateEvent event) {
        if (!enabled) {
            LOGGER.trace("Event dispatcher disabled, skipping: {}", event.eventType());
            return;
        }

        LOGGER.debug("Emitting event: {} for template {}",
            event.eventType(), event.templateId());

        // Notify type-specific listeners
        List<Consumer<? extends TemplateEvent>> typeListeners =
            listeners.get(event.getClass());

        if (typeListeners != null) {
            for (Consumer<? extends TemplateEvent> listener : typeListeners) {
                try {
                    ((Consumer<TemplateEvent>) listener).accept(event);
                } catch (Exception e) {
                    LOGGER.error("Listener threw exception for event {}: {}",
                        event.eventType(), e.getMessage(), e);
                }
            }
        }

        // Notify global listeners
        for (Consumer<TemplateEvent> listener : globalListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.error("Global listener threw exception for event {}: {}",
                    event.eventType(), e.getMessage(), e);
            }
        }
    }

    /**
     * Emits an event asynchronously to all registered listeners.
     *
     * @param event the event to emit
     */
    public void emitAsync(TemplateEvent event) {
        asyncExecutor.execute(() -> emit(event));
    }

    // ========== Convenience Methods ==========

    /**
     * Emits a TemplateRegistered event.
     */
    public void emitTemplateRegistered(String templateId, int version, String source) {
        emit(TemplateEvent.TemplateRegistered.now(templateId, version, source));
    }

    /**
     * Emits a TemplateRegistered event with template type discriminator (DD1-DD2).
     */
    public void emitTemplateRegistered(String templateId, int version, String source,
            com.devmod.arena.registry.TemplateType templateType) {
        emit(TemplateEvent.TemplateRegistered.now(templateId, version, source, templateType));
    }

    /**
     * Emits a TemplateUnregistered event.
     */
    public void emitTemplateUnregistered(String templateId, String reason) {
        emit(TemplateEvent.TemplateUnregistered.now(templateId, reason));
    }

    /**
     * Emits a BuildStarted event.
     */
    public void emitBuildStarted(String templateId, java.util.UUID arenaId,
            java.util.UUID requestedBy, int estimatedBlocks) {
        emit(TemplateEvent.BuildStarted.now(templateId, arenaId, requestedBy, estimatedBlocks));
    }

    /**
     * Emits a BuildStarted event with template type discriminator (DD1-DD2).
     */
    public void emitBuildStarted(String templateId, java.util.UUID arenaId,
            java.util.UUID requestedBy, int estimatedBlocks,
            com.devmod.arena.registry.TemplateType templateType) {
        emit(TemplateEvent.BuildStarted.now(templateId, arenaId, requestedBy, estimatedBlocks, templateType));
    }

    /**
     * Emits a BuildCompleted event.
     */
    public void emitBuildCompleted(String templateId, java.util.UUID arenaId,
            int blocksPlaced, int entitiesSpawned, long durationMs) {
        emit(TemplateEvent.BuildCompleted.now(templateId, arenaId, blocksPlaced, entitiesSpawned, durationMs));
    }

    /**
     * Emits a BuildFailed event.
     */
    public void emitBuildFailed(String templateId, java.util.UUID arenaId,
            String reason, Throwable exception, long durationMs, boolean rollbackSucceeded) {
        emit(TemplateEvent.BuildFailed.now(templateId, arenaId, reason, exception, durationMs, rollbackSucceeded));
    }

    /**
     * Emits a BuildCancelled event.
     */
    public void emitBuildCancelled(String templateId, java.util.UUID arenaId,
            java.util.UUID cancelledBy, String reason, int blocksPlaced, long durationMs) {
        emit(TemplateEvent.BuildCancelled.now(templateId, arenaId, cancelledBy, reason, blocksPlaced, durationMs));
    }

    // ========== Management ==========

    /**
     * Enables or disables the event dispatcher.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Event dispatcher {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether the dispatcher is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns statistics about registered listeners.
     */
    public DispatcherStats getStats() {
        int typeListenerCount = listeners.values().stream()
            .mapToInt(List::size)
            .sum();
        return new DispatcherStats(
            listeners.size(),
            typeListenerCount,
            globalListeners.size(),
            enabled
        );
    }

    /**
     * Clears all registered listeners.
     */
    public void clearAllListeners() {
        listeners.clear();
        globalListeners.clear();
        LOGGER.info("Cleared all event listeners");
    }

    // ========== Supporting Types ==========

    /**
     * Handle for unregistering a listener.
     */
    @FunctionalInterface
    public interface ListenerRegistration {
        void unregister();
    }

    /**
     * Statistics about the dispatcher state.
     */
    public record DispatcherStats(
        int registeredEventTypes,
        int typeListenerCount,
        int globalListenerCount,
        boolean enabled
    ) {
        public int totalListeners() {
            return typeListenerCount + globalListenerCount;
        }
    }
}

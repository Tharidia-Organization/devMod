package com.devmod.endurance.lifecycle;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.lifecycle.QuestLifecycleEvent.CheckpointReached;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.PlayerJoinedQuest;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.PlayerLeftQuest;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveCompleted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveStarted;

/**
 * Central event bus for quest lifecycle events.
 *
 * <p>This bus decouples the EnduranceEventHandler from the 35+ subsystems
 * that need to respond to quest lifecycle changes. Instead of direct
 * INSTANCE calls, systems register as listeners and receive events.</p>
 *
 * <h2>Benefits:</h2>
 * <ul>
 *   <li><b>Decoupling</b>: Systems don't need to know about each other</li>
 *   <li><b>Testability</b>: Systems can be tested in isolation</li>
 *   <li><b>Extensibility</b>: New systems can be added without modifying handler</li>
 *   <li><b>Ordering</b>: Priority system controls execution order</li>
 *   <li><b>Traceability</b>: Centralized logging of event dispatch</li>
 * </ul>
 *
 * <h2>Thread Safety:</h2>
 * <p>The listener list uses CopyOnWriteArrayList for thread-safe iteration
 * during event dispatch. Registration is rare (server startup), while
 * dispatch is frequent (every quest event).</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Register a listener
 * QuestEventBus.INSTANCE.register(ComboSystem.INSTANCE);
 *
 * // Publish an event
 * QuestEventBus.INSTANCE.publish(QuestStarted.of(context));
 * }</pre>
 */
public final class QuestEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestEventBus.class);

    public static final QuestEventBus INSTANCE = new QuestEventBus();

    /**
     * Thread-safe list of registered listeners, sorted by priority (descending).
     * CopyOnWriteArrayList provides thread-safe iteration during event dispatch.
     */
    private final CopyOnWriteArrayList<QuestLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Whether to log detailed event dispatch information.
     * Useful for debugging listener execution order.
     */
    private volatile boolean debugMode = false;

    private QuestEventBus() {}

    // ═══════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Registers a listener to receive quest lifecycle events.
     * Listeners are automatically sorted by priority (highest first).
     *
     * @param listener The listener to register
     * @throws NullPointerException if listener is null
     */
    public void register(QuestLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");

        if (listeners.contains(listener)) {
            LOGGER.debug("[QuestEventBus] Listener {} already registered, skipping",
                listener.getListenerName());
            return;
        }

        listeners.add(listener);
        sortListeners();

        LOGGER.info("[QuestEventBus] Registered listener: {} (priority={})",
            listener.getListenerName(), listener.getPriority());
    }

    /**
     * Unregisters a listener from receiving events.
     *
     * @param listener The listener to unregister
     */
    public void unregister(QuestLifecycleListener listener) {
        if (listener == null) return;

        if (listeners.remove(listener)) {
            LOGGER.info("[QuestEventBus] Unregistered listener: {}", listener.getListenerName());
        }
    }

    /**
     * Sorts listeners by priority (descending order).
     * Called after registration to maintain sorted order.
     */
    private void sortListeners() {
        // Create sorted copy and replace atomically
        List<QuestLifecycleListener> sorted = listeners.stream()
            .sorted(Comparator.comparingInt(QuestLifecycleListener::getPriority).reversed())
            .toList();

        listeners.clear();
        listeners.addAll(sorted);
    }

    /**
     * Returns the number of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Returns an unmodifiable view of registered listener names (for debugging).
     */
    public List<String> getListenerNames() {
        return listeners.stream()
            .map(l -> l.getListenerName() + " (p=" + l.getPriority() + ")")
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // EVENT PUBLISHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Publishes an event to all registered listeners.
     * Listeners are called in priority order (highest first).
     *
     * @param event The event to publish
     */
    public void publish(QuestLifecycleEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        if (debugMode) {
            LOGGER.debug("[QuestEventBus] Publishing {} to {} listeners",
                event.getClass().getSimpleName(), listeners.size());
        }

        long startTime = System.nanoTime();
        int dispatchedCount = 0;

        // Dispatch using pattern matching
        for (QuestLifecycleListener listener : listeners) {
            try {
                boolean dispatched = dispatchToListener(event, listener);
                if (dispatched) dispatchedCount++;
            } catch (Exception e) {
                LOGGER.error("[QuestEventBus] Error dispatching {} to {}: {}",
                    event.getClass().getSimpleName(),
                    listener.getListenerName(),
                    e.getMessage(), e);
            }
        }

        if (debugMode) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            LOGGER.debug("[QuestEventBus] Dispatched {} to {} listeners in {}ms",
                event.getClass().getSimpleName(), dispatchedCount, durationMs);
        }
    }

    /**
     * Dispatches an event to a single listener using pattern matching.
     *
     * @param event The event to dispatch
     * @param listener The listener to receive the event
     * @return true if the event was dispatched
     */
    private boolean dispatchToListener(QuestLifecycleEvent event, QuestLifecycleListener listener) {
        // Check practice mode filter
        if (event instanceof QuestStarted qs && qs.context().practiceMode()
                && !listener.receivePracticeModeEvents()) {
            return false;
        }
        if (event instanceof QuestEnded qe && qe.context().practiceMode()
                && !listener.receivePracticeModeEvents()) {
            return false;
        }
        if (event instanceof WaveStarted ws && ws.context().practiceMode()
                && !listener.receivePracticeModeEvents()) {
            return false;
        }
        if (event instanceof WaveCompleted wc && wc.context().practiceMode()
                && !listener.receivePracticeModeEvents()) {
            return false;
        }

        // Dispatch using pattern matching
        switch (event) {
            case QuestStarted qs -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onQuestStarted()", listener.getListenerName());
                }
                listener.onQuestStarted(qs);
            }
            case QuestEnded qe -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onQuestEnded(completed={})",
                        listener.getListenerName(), qe.completed());
                }
                listener.onQuestEnded(qe);
            }
            case WaveStarted ws -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onWaveStarted(wave={})",
                        listener.getListenerName(), ws.context().waveNumber());
                }
                listener.onWaveStarted(ws);
            }
            case WaveCompleted wc -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onWaveCompleted(wave={})",
                        listener.getListenerName(), wc.context().waveNumber());
                }
                listener.onWaveCompleted(wc);
            }
            case PlayerJoinedQuest pj -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onPlayerJoined()", listener.getListenerName());
                }
                listener.onPlayerJoined(pj);
            }
            case PlayerLeftQuest pl -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onPlayerLeft()", listener.getListenerName());
                }
                listener.onPlayerLeft(pl);
            }
            case CheckpointReached cr -> {
                if (debugMode) {
                    LOGGER.trace("[QuestEventBus]   -> {}.onCheckpointReached(wave={})",
                        listener.getListenerName(), cr.waveNumber());
                }
                listener.onCheckpointReached(cr);
            }
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enables or disables debug logging for event dispatch.
     *
     * @param enabled true to enable debug logging
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        LOGGER.info("[QuestEventBus] Debug mode {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns true if debug mode is enabled.
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Clears all registered listeners.
     * Typically called on server shutdown.
     */
    public void clear() {
        int count = listeners.size();
        listeners.clear();
        LOGGER.info("[QuestEventBus] Cleared {} listeners", count);
    }
}

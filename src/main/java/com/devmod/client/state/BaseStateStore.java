package com.devmod.client.state;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Base class for client-side state stores.
 *
 * <p>Provides thread-safe state management with:
 * <ul>
 *   <li>Immutable state snapshots</li>
 *   <li>Subscription-based change notifications</li>
 *   <li>Priority-ordered subscriber notification</li>
 *   <li>Automatic error handling for subscribers</li>
 *   <li>State history (optional)</li>
 * </ul>
 *
 * <p>Thread Safety:
 * <ul>
 *   <li>State updates are atomic</li>
 *   <li>Subscribers are stored in a CopyOnWriteArrayList</li>
 *   <li>State is volatile for visibility</li>
 * </ul>
 *
 * @param <T> The state type (should be immutable)
 */
@OnlyIn(Dist.CLIENT)
public abstract class BaseStateStore<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseStateStore.class);

    private volatile @Nullable T currentState = null;
    private volatile long lastUpdateTime = 0;
    private final List<SubscriptionEntry<T>> subscriptions = new CopyOnWriteArrayList<>();
    private final Object stateLock = new Object();
    private final String storeName;

    protected BaseStateStore(String storeName) {
        this.storeName = storeName;
    }

    /**
     * Get the current state.
     */
    @Nullable
    public T getState() {
        return currentState;
    }

    /**
     * Get the current state or a default if null.
     */
    public T getStateOrDefault(T defaultValue) {
        T state = currentState;
        return state != null ? state : defaultValue;
    }

    /**
     * Check if state is present.
     */
    public boolean hasState() {
        return currentState != null;
    }

    /**
     * Get the last update timestamp.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Get time since last update in milliseconds.
     */
    public long getTimeSinceUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * Update the state and notify subscribers.
     *
     * @param newState The new state
     */
    public void setState(T newState) {
        T oldState;
        synchronized (stateLock) {
            oldState = currentState;
            currentState = newState;
            lastUpdateTime = System.currentTimeMillis();
        }

        notifySubscribers(oldState, newState);
    }

    /**
     * Update state using a transform function.
     * The current state may be null if not yet initialized.
     *
     * @param transformer Function that takes current state (possibly null) and returns new state
     */
    public void updateState(Function<T, T> transformer) {
        T oldState;
        T newState;
        synchronized (stateLock) {
            oldState = currentState;
            newState = transformer.apply(oldState);
            currentState = newState;
            lastUpdateTime = System.currentTimeMillis();
        }

        notifySubscribers(oldState, newState);
    }

    /**
     * Clear the state.
     */
    public void clear() {
        T oldState;
        synchronized (stateLock) {
            oldState = currentState;
            currentState = null;
            lastUpdateTime = System.currentTimeMillis();
        }

        if (oldState != null) {
            notifySubscribers(oldState, null);
        }

        onCleared();
    }

    /**
     * Called after state is cleared. Override to perform cleanup.
     */
    protected void onCleared() {
        // Default: no-op
    }

    /**
     * Subscribe to state changes.
     *
     * @param subscription The subscription callback
     * @return A handle to unsubscribe
     */
    public SubscriptionHandle subscribe(StateSubscription<T> subscription) {
        SubscriptionEntry<T> entry = new SubscriptionEntry<>(subscription);
        subscriptions.add(entry);

        // Sort by priority
        subscriptions.sort(Comparator.comparingInt(e -> e.subscription.priority()));

        LOGGER.debug("[{}] Added subscription (total={})",
            storeName, subscriptions.size());

        return () -> {
            subscriptions.remove(entry);
            LOGGER.debug("[{}] Removed subscription (total={})",
                storeName, subscriptions.size());
        };
    }

    /**
     * Subscribe with a simple consumer (convenience method).
     */
    public SubscriptionHandle subscribe(Consumer<T> consumer) {
        return subscribe((old, newState) -> {
            if (newState != null) {
                consumer.accept(newState);
            }
        });
    }

    /**
     * Subscribe with a predicate filter (only notified when predicate is true).
     */
    public SubscriptionHandle subscribeWhen(Predicate<T> filter, Consumer<T> consumer) {
        return subscribe((old, newState) -> {
            if (newState != null && filter.test(newState)) {
                consumer.accept(newState);
            }
        });
    }

    /**
     * Notify all subscribers of a state change.
     */
    private void notifySubscribers(@Nullable T oldState, @Nullable T newState) {
        java.util.List<SubscriptionEntry<T>> toRemove = null;
        for (SubscriptionEntry<T> entry : subscriptions) {
            try {
                entry.subscription.onStateChanged(oldState, newState);
            } catch (Exception e) {
                LOGGER.error("[{}] Subscriber error (id={})",
                    storeName, entry.subscription.getId(), e);

                if (!entry.subscription.keepOnError()) {
                    if (toRemove == null) {
                        toRemove = new java.util.ArrayList<>();
                    }
                    toRemove.add(entry);
                    LOGGER.warn("[{}] Removed failing subscription (id={})",
                        storeName, entry.subscription.getId());
                }
            }
        }
        if (toRemove != null) {
            subscriptions.removeAll(toRemove);
        }
    }

    /**
     * Get the number of active subscriptions.
     */
    public int getSubscriptionCount() {
        return subscriptions.size();
    }

    /**
     * Get the store name (for logging).
     */
    public String getStoreName() {
        return storeName;
    }

    /**
     * Handle for unsubscribing.
     */
    @FunctionalInterface
    public interface SubscriptionHandle {
        void unsubscribe();
    }

    /**
     * Internal subscription entry.
     */
    private record SubscriptionEntry<T>(StateSubscription<T> subscription) {}
}

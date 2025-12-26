package com.devmod.arena.registry;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class HotReloadManager<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotReloadManager.class);

    /**
     * Represents an immutable snapshot of the registry.
     * DD16: Snapshot is immutable after creation.
     */
    public record Snapshot<T>(
        UUID snapshotId,
        long version,
        Map<String, T> data,
        Instant createdAt,
        String source
    ) {
        /**
         * Creates an immutable snapshot.
         */
        public Snapshot {
            data = Collections.unmodifiableMap(Map.copyOf(data));
        }

        /**
         * Gets an item by ID.
         */
        public Optional<T> get(String id) {
            return Optional.ofNullable(data.get(id));
        }

        /**
         * Checks if an item exists.
         */
        public boolean contains(String id) {
            return data.containsKey(id);
        }

        /**
         * Gets the number of items.
         */
        public int size() {
            return data.size();
        }

        /**
         * Checks if this snapshot is newer than another.
         */
        public boolean isNewerThan(Snapshot<T> other) {
            return this.version > other.version;
        }
    }

    /**
     * Result of a reload operation.
     */
    public record ReloadResult(
        boolean success,
        long previousVersion,
        long newVersion,
        int itemCount,
        long reloadTimeMs,
        String message
    ) {
        public static ReloadResult success(long prevVersion, long newVersion, int itemCount, long timeMs) {
            return new ReloadResult(true, prevVersion, newVersion, itemCount, timeMs,
                "Reload successful: " + itemCount + " items loaded");
        }

        public static ReloadResult failure(long prevVersion, String error) {
            return new ReloadResult(false, prevVersion, prevVersion, 0, 0, error);
        }
    }

    /**
     * Listener for reload events.
     */
    public interface ReloadListener<T> {
        void onBeforeReload(Snapshot<T> currentSnapshot);
        void onAfterReload(Snapshot<T> newSnapshot, ReloadResult result);
        void onReloadFailed(Snapshot<T> currentSnapshot, Exception error);
    }

    /**
     * Data loader interface.
     */
    public interface DataLoader<T> {
        Map<String, T> load() throws Exception;
        String getSource();
    }

    private final AtomicReference<Snapshot<T>> currentSnapshot;
    private final ReadWriteLock reloadLock = new ReentrantReadWriteLock();
    private final DataLoader<T> loader;
    private final Map<String, ReloadListener<T>> listeners = new ConcurrentHashMap<>();

    private volatile long currentVersion = 0;

    public HotReloadManager(DataLoader<T> loader) {
        this.loader = loader;
        this.currentSnapshot = new AtomicReference<>(createEmptySnapshot());
    }

    /**
     * Gets the current snapshot.
     * DD16: Non-blocking read, returns immutable snapshot.
     *
     * @return current immutable snapshot
     */
    public Snapshot<T> getSnapshot() {
        return currentSnapshot.get();
    }

    /**
     * Gets an item from current snapshot.
     * DD16: O(1) lookup, no locking.
     *
     * @param id the item ID
     * @return Optional containing the item if found
     */
    public Optional<T> get(String id) {
        return getSnapshot().get(id);
    }

    /**
     * Checks if an item exists.
     *
     * @param id the item ID
     * @return true if item exists
     */
    public boolean contains(String id) {
        return getSnapshot().contains(id);
    }

    /**
     * Triggers a hot reload.
     * DD16: Atomic swap of snapshot.
     *
     * @return reload result
     */
    public ReloadResult reload() {
        reloadLock.writeLock().lock();
        try {
            long startTime = System.currentTimeMillis();
            Snapshot<T> oldSnapshot = currentSnapshot.get();
            long previousVersion = currentVersion;

            // Notify listeners before reload
            notifyBeforeReload(oldSnapshot);

            try {
                // Load new data
                Map<String, T> newData = loader.load();

                // Increment version
                long newVersion = ++currentVersion;

                // Create new immutable snapshot
                Snapshot<T> newSnapshot = new Snapshot<>(
                    UUID.randomUUID(),
                    newVersion,
                    newData,
                    Instant.now(),
                    loader.getSource()
                );

                // Atomic swap (DD16)
                currentSnapshot.set(newSnapshot);

                long reloadTime = System.currentTimeMillis() - startTime;
                ReloadResult result = ReloadResult.success(
                    previousVersion, newVersion, newData.size(), reloadTime
                );

                LOGGER.info("Hot reload completed: version {} -> {}, {} items in {}ms",
                    previousVersion, newVersion, newData.size(), reloadTime);

                // Notify listeners after reload
                notifyAfterReload(newSnapshot, result);

                return result;

            } catch (Exception e) {
                LOGGER.error("Hot reload failed", e);
                notifyReloadFailed(oldSnapshot, e);
                return ReloadResult.failure(previousVersion, e.getMessage());
            }

        } finally {
            reloadLock.writeLock().unlock();
        }
    }

    /**
     * Registers a reload listener.
     *
     * @param id listener ID
     * @param listener the listener
     */
    public void addListener(String id, ReloadListener<T> listener) {
        listeners.put(id, listener);
    }

    /**
     * Removes a reload listener.
     *
     * @param id listener ID
     */
    public void removeListener(String id) {
        listeners.remove(id);
    }

    /**
     * Gets current version number.
     *
     * @return current version
     */
    public long getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Gets the number of items in current snapshot.
     *
     * @return item count
     */
    public int size() {
        return getSnapshot().size();
    }

    /**
     * Checks if a reload is in progress.
     *
     * @return true if reload is in progress
     */
    public boolean isReloading() {
        return ((ReentrantReadWriteLock) reloadLock).isWriteLocked();
    }

    private Snapshot<T> createEmptySnapshot() {
        return new Snapshot<>(
            UUID.randomUUID(),
            0,
            Map.of(),
            Instant.now(),
            "empty"
        );
    }

    private void notifyBeforeReload(Snapshot<T> snapshot) {
        for (ReloadListener<T> listener : listeners.values()) {
            try {
                listener.onBeforeReload(snapshot);
            } catch (Exception e) {
                LOGGER.warn("Listener onBeforeReload failed", e);
            }
        }
    }

    private void notifyAfterReload(Snapshot<T> snapshot, ReloadResult result) {
        for (ReloadListener<T> listener : listeners.values()) {
            try {
                listener.onAfterReload(snapshot, result);
            } catch (Exception e) {
                LOGGER.warn("Listener onAfterReload failed", e);
            }
        }
    }

    private void notifyReloadFailed(Snapshot<T> snapshot, Exception error) {
        for (ReloadListener<T> listener : listeners.values()) {
            try {
                listener.onReloadFailed(snapshot, error);
            } catch (Exception e) {
                LOGGER.warn("Listener onReloadFailed failed", e);
            }
        }
    }
}

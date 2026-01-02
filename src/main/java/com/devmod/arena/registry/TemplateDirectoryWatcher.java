package com.devmod.arena.registry;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TemplateDirectoryWatcher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateDirectoryWatcher.class);
    private static final long DEFAULT_DEBOUNCE_MS = 250L;

    private final Path directory;
    private final Runnable onChange;
    private final long debounceMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService debounceExecutor;

    @Nullable
    private WatchService watchService;
    @Nullable
    private Thread watcherThread;
    @Nullable
    private ScheduledFuture<?> pendingReload;

    public TemplateDirectoryWatcher(Path directory, Runnable onChange) {
        this(directory, onChange, DEFAULT_DEBOUNCE_MS);
    }

    public TemplateDirectoryWatcher(Path directory, Runnable onChange, long debounceMs) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.debounceMs = debounceMs > 0 ? debounceMs : DEFAULT_DEBOUNCE_MS;
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArenaTemplateWatcher-Debounce");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        if (!Files.isDirectory(directory)) {
            LOGGER.warn("Template watcher not started; directory missing: {}", directory);
            return;
        }
        try {
            watchService = directory.getFileSystem().newWatchService();
            directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );
        } catch (IOException e) {
            LOGGER.warn("Template watcher failed to register: {}", e.getMessage());
            return;
        }

        running.set(true);
        final Thread thread = new Thread(this::runLoop, "ArenaTemplateWatcher");
        thread.setDaemon(true);
        watcherThread = thread;
        thread.start();
        LOGGER.info("Template watcher started for {}", directory);
    }

    public Path directory() {
        return directory;
    }

    private void runLoop() {
        final WatchService service = watchService;
        if (service == null) {
            return;
        }
        while (running.get()) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                changed = true;
            }

            boolean valid = key.reset();
            if (!valid) {
                LOGGER.warn("Template watcher key invalid, stopping");
                break;
            }

            if (changed) {
                scheduleReload();
            }
        }
    }

    private void scheduleReload() {
        if (!running.get()) {
            return;
        }
        if (pendingReload != null) {
            pendingReload.cancel(false);
        }
        pendingReload = debounceExecutor.schedule(() -> {
            try {
                onChange.run();
            } catch (Exception e) {
                LOGGER.warn("Template watcher reload failed: {}", e.getMessage());
            }
        }, debounceMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        running.set(false);
        if (pendingReload != null) {
            pendingReload.cancel(false);
            pendingReload = null;
        }
        // Interrupt the watcher thread to unblock from take()
        final Thread thread = watcherThread;
        if (thread != null) {
            thread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to close template watcher", e);
        }
        debounceExecutor.shutdownNow();
        watcherThread = null;
        watchService = null;
        LOGGER.info("Template watcher stopped for {}", directory);
    }
}

package com.devmod.client.vfx.effekseer.api;

import java.io.Closeable;
import java.lang.ref.Cleaner;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

/**
 * Keeps native Effekseer objects alive until the render thread closes them.
 */
public abstract class SafeFinalized<T> implements Closeable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Set<Object> KEEPER = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Cleaner.Cleanable cleanable;
    private final State<T> state;

    protected SafeFinalized(T kept, Consumer<T> closer) {
        KEEPER.add(kept);
        this.state = new State<>(kept, closer);
        this.cleanable = CLEANER.register(state, state);
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private static <T> void dispatchClose(AtomicReference<T> kept, Consumer<T> closer) {
        T removed = kept.getAndSet(null);
        if (removed == null) {
            return;
        }
        Runnable action = () -> {
            try {
                closer.accept(removed);
            } finally {
                KEEPER.remove(removed);
            }
        };
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.tell(action);
            } else {
                action.run();
            }
        } catch (Throwable ignored) {
            action.run();
        }
    }

    private static final class State<T> implements Runnable {
        private final AtomicReference<T> kept;
        private final Consumer<T> closer;

        private State(T kept, Consumer<T> closer) {
            this.kept = new AtomicReference<>(kept);
            this.closer = closer;
        }

        @Override
        public void run() {
            dispatchClose(kept, closer);
        }
    }
}

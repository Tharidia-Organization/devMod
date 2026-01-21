package com.devmod.client.vfx.effekseer.api;

import java.io.Closeable;
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
    private static final Set<Object> KEEPER = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicReference<T> kept;
    private final Consumer<T> closer;

    protected SafeFinalized(T kept, Consumer<T> closer) {
        this.kept = new AtomicReference<>(kept);
        this.closer = closer;
        KEEPER.add(kept);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() throws Throwable {
        try {
            T value = kept.get();
            if (value != null) {
                Minecraft.getInstance().tell(this::close);
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        T removed = kept.getAndSet(null);
        if (removed == null) {
            return;
        }
        try {
            closer.accept(removed);
        } finally {
            KEEPER.remove(removed);
        }
    }
}

package net.neoforged.neoforge.registries;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Minimal stub for DeferredHolder to support tests without NeoForge.
 */
public class DeferredHolder<R, T> implements Supplier<T> {
    private final T value;

    public DeferredHolder(T value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public T get() {
        return value;
    }
}

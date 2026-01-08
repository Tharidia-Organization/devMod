package com.devmod.config.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of IDecomposedConfig.
 * Stores component values in a type-safe map.
 */
public class DecomposedConfig implements IDecomposedConfig {

    private final Map<IConfigComponent<?>, Object> values = new HashMap<>();
    private final Set<IConfigComponent<?>> supported;

    /**
     * Create a decomposed config with the given supported components.
     *
     * @param supportedComponents the components this config supports
     */
    public DecomposedConfig(Set<IConfigComponent<?>> supportedComponents) {
        this.supported = Set.copyOf(supportedComponents);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(IConfigComponent<T> component) {
        Object value = values.get(component);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    @Override
    public <T> void set(IConfigComponent<T> component, T value) {
        if (value == null) {
            values.remove(component);
        } else {
            values.put(component, value);
        }
    }

    @Override
    public boolean has(IConfigComponent<?> component) {
        return values.containsKey(component);
    }

    @Override
    public void remove(IConfigComponent<?> component) {
        values.remove(component);
    }

    @Override
    public Set<IConfigComponent<?>> components() {
        return Collections.unmodifiableSet(values.keySet());
    }

    @Override
    public Set<IConfigComponent<?>> supportedComponents() {
        return supported;
    }

    /**
     * Get a value or the component's default.
     *
     * @param component the component
     * @param <T> the value type
     * @return the value or default
     */
    public <T> T getOrDefault(IConfigComponent<T> component) {
        return get(component).orElse(component.defaultValue());
    }
}

package com.devmod.config.handler;

import java.util.Optional;
import java.util.Set;

/**
 * A decomposed view of item stats as typed components.
 * Inspired by CraftTweaker's IDecomposedRecipe pattern.
 *
 * <p>Enables generic manipulation of stats without knowing the concrete type:
 * <pre>
 * IDecomposedConfig config = handler.decompose(stats);
 * config.set(WeaponComponents.ATTACK_DAMAGE, 10.0f);
 * WeaponStats modified = handler.recompose(config).orElseThrow();
 * </pre>
 */
public interface IDecomposedConfig {

    /**
     * Get the value of a component.
     *
     * @param component the component to get
     * @param <T> the component's value type
     * @return the value, or empty if not set
     */
    <T> Optional<T> get(IConfigComponent<T> component);

    /**
     * Set the value of a component.
     *
     * @param component the component to set
     * @param value the value to set
     * @param <T> the component's value type
     */
    <T> void set(IConfigComponent<T> component, T value);

    /**
     * Check if a component has a value set.
     *
     * @param component the component to check
     * @return true if the component has a value
     */
    boolean has(IConfigComponent<?> component);

    /**
     * Remove a component's value.
     *
     * @param component the component to remove
     */
    void remove(IConfigComponent<?> component);

    /**
     * Get all components that have values set.
     *
     * @return set of components with values
     */
    Set<IConfigComponent<?>> components();

    /**
     * Get all components that this decomposed config supports.
     *
     * @return set of all supported components
     */
    Set<IConfigComponent<?>> supportedComponents();
}

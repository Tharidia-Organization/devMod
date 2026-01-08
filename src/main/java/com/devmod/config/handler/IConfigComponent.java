package com.devmod.config.handler;

import javax.annotation.Nullable;

/**
 * A typed component representing a single editable property in item stats.
 * Inspired by CraftTweaker's IRecipeComponent pattern.
 *
 * <p>Components provide:
 * <ul>
 *   <li>Type-safe value access</li>
 *   <li>Display metadata for UI generation</li>
 *   <li>Validation with error messages</li>
 *   <li>Default values</li>
 * </ul>
 *
 * @param <T> The value type of this component
 */
public interface IConfigComponent<T> {

    /**
     * Get the unique identifier for this component.
     * Format: "category.name" (e.g., "weapon.attackDamage")
     */
    String id();

    /**
     * Get the display name for UI rendering.
     */
    String displayName();

    /**
     * Get the category for grouping in UI.
     */
    String category();

    /**
     * Get the default value for this component.
     */
    T defaultValue();

    /**
     * Get the Java class of this component's value type.
     */
    Class<T> valueType();

    /**
     * Validate a value.
     *
     * @param value the value to validate
     * @return null if valid, error message if invalid
     */
    @Nullable
    String validate(T value);

    /**
     * Clamp/sanitize a value to valid bounds.
     *
     * @param value the value to clamp
     * @return the clamped value
     */
    T clamp(T value);

    /**
     * Serialize a value to command string format for export.
     * Default implementation uses CommandStringUtil.formatValue().
     *
     * @param value the value to serialize
     * @return command string representation (e.g., "12.5f", "true", "\"text\"")
     */
    default String toCommandString(T value) {
        return com.devmod.config.handler.export.CommandStringUtil.formatValue(value);
    }
}

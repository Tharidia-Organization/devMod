package com.devmod.arena.policy;

import java.util.Objects;

/**
 * Represents a mutator binding configuration.
 * DD50: Mutator Binding - SUGGESTED soft, EXCLUDED/REQUIRED hard.
 *
 * @param mutatorId The identifier of the mutator
 * @param bindingType The type of binding (how the mutator is applied)
 * @param priority UI display priority (lower = higher priority)
 * @param description Optional description for UI
 */
public record MutatorBinding(
    String mutatorId,
    BindingType bindingType,
    int priority,
    String description
) {
    /**
     * Create a binding with default priority.
     */
    public MutatorBinding(String mutatorId, BindingType bindingType) {
        this(mutatorId, bindingType, 0, null);
    }

    /**
     * Create a binding with description.
     */
    public MutatorBinding(String mutatorId, BindingType bindingType, String description) {
        this(mutatorId, bindingType, 0, description);
    }

    /**
     * Validate binding.
     */
    public MutatorBinding {
        Objects.requireNonNull(mutatorId, "mutatorId cannot be null");
        Objects.requireNonNull(bindingType, "bindingType cannot be null");

        if (mutatorId.isBlank()) {
            throw new IllegalArgumentException("mutatorId cannot be blank");
        }
    }

    /**
     * Create a REQUIRED binding.
     * DD50: REQUIRED - hard binding, always active.
     */
    public static MutatorBinding required(String mutatorId) {
        return new MutatorBinding(mutatorId, BindingType.REQUIRED, -100, "Required");
    }

    /**
     * Create a REQUIRED binding with description.
     */
    public static MutatorBinding required(String mutatorId, String description) {
        return new MutatorBinding(mutatorId, BindingType.REQUIRED, -100, description);
    }

    /**
     * Create an EXCLUDED binding.
     * DD50: EXCLUDED - hard binding, always blocked.
     */
    public static MutatorBinding excluded(String mutatorId) {
        return new MutatorBinding(mutatorId, BindingType.EXCLUDED, 100, "Excluded");
    }

    /**
     * Create an EXCLUDED binding with reason.
     */
    public static MutatorBinding excluded(String mutatorId, String reason) {
        return new MutatorBinding(mutatorId, BindingType.EXCLUDED, 100, reason);
    }

    /**
     * Create a SUGGESTED binding.
     * DD50: SUGGESTED - soft binding, selectable in UI.
     */
    public static MutatorBinding suggested(String mutatorId) {
        return new MutatorBinding(mutatorId, BindingType.SUGGESTED, 0, null);
    }

    /**
     * Create a SUGGESTED binding with priority.
     */
    public static MutatorBinding suggested(String mutatorId, int priority) {
        return new MutatorBinding(mutatorId, BindingType.SUGGESTED, priority, null);
    }

    /**
     * Create a SUGGESTED binding with priority and description.
     */
    public static MutatorBinding suggested(String mutatorId, int priority, String description) {
        return new MutatorBinding(mutatorId, BindingType.SUGGESTED, priority, description);
    }

    /**
     * Check if this is a hard binding (REQUIRED or EXCLUDED).
     */
    public boolean isHardBinding() {
        return bindingType == BindingType.REQUIRED || bindingType == BindingType.EXCLUDED;
    }

    /**
     * Check if this is a soft binding (SUGGESTED).
     */
    public boolean isSoftBinding() {
        return bindingType == BindingType.SUGGESTED;
    }

    /**
     * Check if the mutator should be active by default.
     */
    public boolean isActiveByDefault() {
        return bindingType == BindingType.REQUIRED;
    }

    /**
     * Check if the mutator can be toggled by user.
     */
    public boolean isUserSelectable() {
        return bindingType == BindingType.SUGGESTED;
    }

    /**
     * Create a copy with different priority.
     */
    public MutatorBinding withPriority(int newPriority) {
        return new MutatorBinding(mutatorId, bindingType, newPriority, description);
    }

    /**
     * Create a copy with different description.
     */
    public MutatorBinding withDescription(String newDescription) {
        return new MutatorBinding(mutatorId, bindingType, priority, newDescription);
    }

    /**
     * Binding type enum.
     * DD50: Defines how mutators are bound to arena policies.
     */
    public enum BindingType {
        /**
         * REQUIRED: Hard binding - mutator is always active.
         * Cannot be disabled by users.
         */
        REQUIRED,

        /**
         * EXCLUDED: Hard binding - mutator is always blocked.
         * Cannot be enabled by users.
         */
        EXCLUDED,

        /**
         * SUGGESTED: Soft binding - mutator is suggested but optional.
         * Users can toggle on/off in UI.
         */
        SUGGESTED;

        /**
         * Check if this is a hard binding type.
         */
        public boolean isHard() {
            return this == REQUIRED || this == EXCLUDED;
        }

        /**
         * Check if this is a soft binding type.
         */
        public boolean isSoft() {
            return this == SUGGESTED;
        }
    }
}

package com.devmod.arena.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class PolicyMutatorResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyMutatorResolver.class);

    private final Map<String, MutatorBinding> bindings = new HashMap<>();
    private final Set<String> userSelected = new HashSet<>();

    /**
     * Create an empty resolver.
     */
    public PolicyMutatorResolver() {
    }

    /**
     * Create a resolver with initial bindings.
     */
    public PolicyMutatorResolver(Collection<MutatorBinding> initialBindings) {
        for (MutatorBinding binding : initialBindings) {
            addBindingInternal(binding);
        }
    }

    /**
     * Add a mutator binding.
     */
    public void addBinding(MutatorBinding binding) {
        addBindingInternal(binding);
    }

    private void addBindingInternal(MutatorBinding binding) {
        if (binding == null) {
            return;
        }

        MutatorBinding existing = bindings.get(binding.mutatorId());
        if (existing != null) {
            LOGGER.debug("Overwriting existing binding for mutator: {}", binding.mutatorId());
        }

        bindings.put(binding.mutatorId(), binding);
    }

    /**
     * Remove a mutator binding.
     */
    public void removeBinding(String mutatorId) {
        bindings.remove(mutatorId);
        userSelected.remove(mutatorId);
    }

    /**
     * Get binding for a mutator.
     */
    public Optional<MutatorBinding> getBinding(String mutatorId) {
        return Optional.ofNullable(bindings.get(mutatorId));
    }

    /**
     * Check if a mutator is REQUIRED.
     * DD50: REQUIRED mutators are always on.
     */
    public boolean isRequired(String mutatorId) {
        MutatorBinding binding = bindings.get(mutatorId);
        return binding != null && binding.bindingType() == MutatorBinding.BindingType.REQUIRED;
    }

    /**
     * Check if a mutator is EXCLUDED.
     * DD50: EXCLUDED mutators are always blocked.
     */
    public boolean isExcluded(String mutatorId) {
        MutatorBinding binding = bindings.get(mutatorId);
        return binding != null && binding.bindingType() == MutatorBinding.BindingType.EXCLUDED;
    }

    /**
     * Check if a mutator is SUGGESTED.
     * DD50: SUGGESTED mutators are optional.
     */
    public boolean isSuggested(String mutatorId) {
        MutatorBinding binding = bindings.get(mutatorId);
        return binding != null && binding.bindingType() == MutatorBinding.BindingType.SUGGESTED;
    }

    /**
     * Check if a mutator can be selected by the user.
     */
    public boolean isUserSelectable(String mutatorId) {
        return isSuggested(mutatorId);
    }

    /**
     * Set user selection for a SUGGESTED mutator.
     *
     * @param mutatorId The mutator ID
     * @param selected Whether the user wants this mutator active
     * @return true if selection was changed, false if mutator is not selectable
     */
    public boolean setUserSelection(String mutatorId, boolean selected) {
        if (!isSuggested(mutatorId)) {
            LOGGER.debug("Cannot set user selection for non-SUGGESTED mutator: {}", mutatorId);
            return false;
        }

        if (selected) {
            userSelected.add(mutatorId);
        } else {
            userSelected.remove(mutatorId);
        }
        return true;
    }

    /**
     * Toggle user selection for a SUGGESTED mutator.
     */
    public boolean toggleUserSelection(String mutatorId) {
        if (!isSuggested(mutatorId)) {
            return false;
        }

        if (userSelected.contains(mutatorId)) {
            userSelected.remove(mutatorId);
        } else {
            userSelected.add(mutatorId);
        }
        return true;
    }

    /**
     * Check if a mutator is currently selected by the user.
     */
    public boolean isUserSelected(String mutatorId) {
        return userSelected.contains(mutatorId);
    }

    /**
     * Resolve the final list of active mutators.
     * DD50: REQUIRED auto-add, EXCLUDED block, SUGGESTED based on user selection.
     *
     * @return Set of mutator IDs that should be active
     */
    public Set<String> resolveActiveMutators() {
        Set<String> active = new HashSet<>();

        for (MutatorBinding binding : bindings.values()) {
            switch (binding.bindingType()) {
                case REQUIRED:
                    // DD50: REQUIRED - auto-add
                    active.add(binding.mutatorId());
                    break;

                case EXCLUDED:
                    // DD50: EXCLUDED - never add
                    break;

                case SUGGESTED:
                    // DD50: SUGGESTED - add if user selected
                    if (userSelected.contains(binding.mutatorId())) {
                        active.add(binding.mutatorId());
                    }
                    break;
            }
        }

        return Collections.unmodifiableSet(active);
    }

    /**
     * Resolve active mutators, including a list of additional mutators to consider.
     * External mutators are subject to EXCLUDED check.
     *
     * @param additionalMutators Additional mutator IDs to consider
     * @return Set of mutator IDs that should be active
     */
    public Set<String> resolveActiveMutators(Collection<String> additionalMutators) {
        Set<String> active = new HashSet<>(resolveActiveMutators());

        // Add additional mutators if not excluded
        for (String mutatorId : additionalMutators) {
            if (!isExcluded(mutatorId)) {
                active.add(mutatorId);
            } else {
                LOGGER.debug("Blocked excluded mutator: {}", mutatorId);
            }
        }

        return Collections.unmodifiableSet(active);
    }

    /**
     * Get all REQUIRED mutators.
     * DD50: These are auto-added.
     */
    public List<MutatorBinding> getRequiredMutators() {
        return bindings.values().stream()
            .filter(b -> b.bindingType() == MutatorBinding.BindingType.REQUIRED)
            .sorted(Comparator.comparingInt(MutatorBinding::priority))
            .collect(Collectors.toList());
    }

    /**
     * Get all EXCLUDED mutators.
     * DD50: These are always blocked.
     */
    public List<MutatorBinding> getExcludedMutators() {
        return bindings.values().stream()
            .filter(b -> b.bindingType() == MutatorBinding.BindingType.EXCLUDED)
            .sorted(Comparator.comparingInt(MutatorBinding::priority))
            .collect(Collectors.toList());
    }

    /**
     * Get all SUGGESTED mutators, sorted by priority for UI display.
     * DD50: UI sorting for SUGGESTED.
     */
    public List<MutatorBinding> getSuggestedMutators() {
        return bindings.values().stream()
            .filter(b -> b.bindingType() == MutatorBinding.BindingType.SUGGESTED)
            .sorted(Comparator.comparingInt(MutatorBinding::priority))
            .collect(Collectors.toList());
    }

    /**
     * Get all bindings.
     */
    public List<MutatorBinding> getAllBindings() {
        return new ArrayList<>(bindings.values());
    }

    /**
     * Get all bindings sorted for UI display.
     * Order: REQUIRED first, then SUGGESTED by priority, then EXCLUDED.
     */
    public List<MutatorBinding> getBindingsForUI() {
        List<MutatorBinding> result = new ArrayList<>();

        // REQUIRED first (always on, user should know)
        result.addAll(getRequiredMutators());

        // SUGGESTED (user can toggle)
        result.addAll(getSuggestedMutators());

        // EXCLUDED last (informational)
        result.addAll(getExcludedMutators());

        return result;
    }

    /**
     * Validate a proposed mutator selection.
     *
     * @param proposedMutators Set of mutator IDs the user wants active
     * @return Validation result with any violations
     */
    public ValidationResult validateSelection(Set<String> proposedMutators) {
        List<String> violations = new ArrayList<>();

        for (String mutatorId : proposedMutators) {
            if (isExcluded(mutatorId)) {
                violations.add("Mutator '" + mutatorId + "' is excluded and cannot be selected");
            }
        }

        // Check that all REQUIRED mutators are included
        for (MutatorBinding binding : getRequiredMutators()) {
            if (!proposedMutators.contains(binding.mutatorId())) {
                violations.add("Required mutator '" + binding.mutatorId() + "' must be included");
            }
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    /**
     * Clear all user selections.
     */
    public void clearUserSelections() {
        userSelected.clear();
    }

    /**
     * Clear all bindings and selections.
     */
    public void clear() {
        bindings.clear();
        userSelected.clear();
    }

    /**
     * Get count of each binding type.
     */
    public BindingCounts getBindingCounts() {
        int required = 0, excluded = 0, suggested = 0;

        for (MutatorBinding binding : bindings.values()) {
            switch (binding.bindingType()) {
                case REQUIRED -> required++;
                case EXCLUDED -> excluded++;
                case SUGGESTED -> suggested++;
            }
        }

        return new BindingCounts(required, excluded, suggested);
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(boolean valid, List<String> violations) {
        public boolean isValid() {
            return valid;
        }
    }

    /**
     * Binding counts record.
     */
    public record BindingCounts(int required, int excluded, int suggested) {
        public int total() {
            return required + excluded + suggested;
        }
    }
}

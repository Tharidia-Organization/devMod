package com.devmod.arena.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PolicyMutatorResolver.
 * DD50: Mutator Binding - SUGGESTED soft, EXCLUDED/REQUIRED hard.
 */
@DisplayName("PolicyMutatorResolver Tests")
class PolicyMutatorResolverTest {

    private PolicyMutatorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PolicyMutatorResolver();
    }

    @Test
    @DisplayName("DD50: REQUIRED mutators are auto-added")
    void requiredMutatorsAutoAdded() {
        resolver.addBinding(MutatorBinding.required("required-1"));
        resolver.addBinding(MutatorBinding.required("required-2"));
        resolver.addBinding(MutatorBinding.suggested("optional-1"));

        Set<String> active = resolver.resolveActiveMutators();

        assertTrue(active.contains("required-1"));
        assertTrue(active.contains("required-2"));
        assertFalse(active.contains("optional-1")); // Not selected
    }

    @Test
    @DisplayName("DD50: EXCLUDED mutators are blocked")
    void excludedMutatorsBlocked() {
        resolver.addBinding(MutatorBinding.excluded("blocked-mutator"));

        // Try to add blocked mutator via additional list
        Set<String> active = resolver.resolveActiveMutators(
            Set.of("blocked-mutator", "other-mutator")
        );

        assertFalse(active.contains("blocked-mutator"));
        assertTrue(active.contains("other-mutator"));
    }

    @Test
    @DisplayName("DD50: SUGGESTED mutators depend on user selection")
    void suggestedMutatorsDependOnSelection() {
        resolver.addBinding(MutatorBinding.suggested("opt-1"));
        resolver.addBinding(MutatorBinding.suggested("opt-2"));

        // Initially not selected
        Set<String> active1 = resolver.resolveActiveMutators();
        assertFalse(active1.contains("opt-1"));
        assertFalse(active1.contains("opt-2"));

        // Select opt-1
        resolver.setUserSelection("opt-1", true);
        Set<String> active2 = resolver.resolveActiveMutators();
        assertTrue(active2.contains("opt-1"));
        assertFalse(active2.contains("opt-2"));
    }

    @Test
    @DisplayName("Cannot select non-SUGGESTED mutators")
    void cannotSelectNonSuggested() {
        resolver.addBinding(MutatorBinding.required("required"));
        resolver.addBinding(MutatorBinding.excluded("excluded"));

        assertFalse(resolver.setUserSelection("required", true));
        assertFalse(resolver.setUserSelection("excluded", true));
    }

    @Test
    @DisplayName("Toggle user selection works")
    void toggleUserSelectionWorks() {
        resolver.addBinding(MutatorBinding.suggested("toggle-me"));

        assertFalse(resolver.isUserSelected("toggle-me"));

        resolver.toggleUserSelection("toggle-me");
        assertTrue(resolver.isUserSelected("toggle-me"));

        resolver.toggleUserSelection("toggle-me");
        assertFalse(resolver.isUserSelected("toggle-me"));
    }

    @Test
    @DisplayName("Get bindings by type works")
    void getBindingsByTypeWorks() {
        resolver.addBinding(MutatorBinding.required("req-1"));
        resolver.addBinding(MutatorBinding.required("req-2"));
        resolver.addBinding(MutatorBinding.excluded("exc-1"));
        resolver.addBinding(MutatorBinding.suggested("sug-1"));
        resolver.addBinding(MutatorBinding.suggested("sug-2"));
        resolver.addBinding(MutatorBinding.suggested("sug-3"));

        assertEquals(2, resolver.getRequiredMutators().size());
        assertEquals(1, resolver.getExcludedMutators().size());
        assertEquals(3, resolver.getSuggestedMutators().size());
    }

    @Test
    @DisplayName("DD50: UI sorting for SUGGESTED")
    void uiSortingForSuggested() {
        resolver.addBinding(MutatorBinding.suggested("low-priority", 100));
        resolver.addBinding(MutatorBinding.suggested("high-priority", 1));
        resolver.addBinding(MutatorBinding.suggested("medium-priority", 50));

        List<MutatorBinding> sorted = resolver.getSuggestedMutators();

        assertEquals("high-priority", sorted.get(0).mutatorId());
        assertEquals("medium-priority", sorted.get(1).mutatorId());
        assertEquals("low-priority", sorted.get(2).mutatorId());
    }

    @Test
    @DisplayName("Bindings for UI are ordered correctly")
    void bindingsForUiOrderedCorrectly() {
        resolver.addBinding(MutatorBinding.excluded("exc"));
        resolver.addBinding(MutatorBinding.suggested("sug"));
        resolver.addBinding(MutatorBinding.required("req"));

        List<MutatorBinding> uiBindings = resolver.getBindingsForUI();

        // Order: REQUIRED, SUGGESTED, EXCLUDED
        assertEquals(MutatorBinding.BindingType.REQUIRED, uiBindings.get(0).bindingType());
        assertEquals(MutatorBinding.BindingType.SUGGESTED, uiBindings.get(1).bindingType());
        assertEquals(MutatorBinding.BindingType.EXCLUDED, uiBindings.get(2).bindingType());
    }

    @Test
    @DisplayName("Validate selection catches excluded mutators")
    void validateSelectionCatchesExcluded() {
        resolver.addBinding(MutatorBinding.excluded("blocked"));

        Set<String> proposed = new HashSet<>(Set.of("blocked", "other"));
        PolicyMutatorResolver.ValidationResult result = resolver.validateSelection(proposed);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("blocked")));
    }

    @Test
    @DisplayName("Validate selection catches missing required")
    void validateSelectionCatchesMissingRequired() {
        resolver.addBinding(MutatorBinding.required("must-have"));

        Set<String> proposed = new HashSet<>(Set.of("other"));
        PolicyMutatorResolver.ValidationResult result = resolver.validateSelection(proposed);

        assertFalse(result.isValid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("must-have")));
    }

    @Test
    @DisplayName("Valid selection passes validation")
    void validSelectionPassesValidation() {
        resolver.addBinding(MutatorBinding.required("required"));
        resolver.addBinding(MutatorBinding.suggested("optional"));

        Set<String> proposed = new HashSet<>(Set.of("required", "optional"));
        PolicyMutatorResolver.ValidationResult result = resolver.validateSelection(proposed);

        assertTrue(result.isValid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    @DisplayName("Binding counts are correct")
    void bindingCountsAreCorrect() {
        resolver.addBinding(MutatorBinding.required("r1"));
        resolver.addBinding(MutatorBinding.required("r2"));
        resolver.addBinding(MutatorBinding.excluded("e1"));
        resolver.addBinding(MutatorBinding.suggested("s1"));
        resolver.addBinding(MutatorBinding.suggested("s2"));
        resolver.addBinding(MutatorBinding.suggested("s3"));

        PolicyMutatorResolver.BindingCounts counts = resolver.getBindingCounts();

        assertEquals(2, counts.required());
        assertEquals(1, counts.excluded());
        assertEquals(3, counts.suggested());
        assertEquals(6, counts.total());
    }

    @Test
    @DisplayName("Clear user selections works")
    void clearUserSelectionsWorks() {
        resolver.addBinding(MutatorBinding.suggested("s1"));
        resolver.addBinding(MutatorBinding.suggested("s2"));

        resolver.setUserSelection("s1", true);
        resolver.setUserSelection("s2", true);

        assertEquals(2, resolver.resolveActiveMutators().size());

        resolver.clearUserSelections();

        assertTrue(resolver.resolveActiveMutators().isEmpty());
    }

    @Test
    @DisplayName("Clear removes all bindings")
    void clearRemovesAllBindings() {
        resolver.addBinding(MutatorBinding.required("r1"));
        resolver.addBinding(MutatorBinding.suggested("s1"));

        resolver.clear();

        assertTrue(resolver.getAllBindings().isEmpty());
        assertEquals(0, resolver.getBindingCounts().total());
    }

    @Test
    @DisplayName("Remove binding works")
    void removeBindingWorks() {
        resolver.addBinding(MutatorBinding.suggested("test"));
        resolver.setUserSelection("test", true);

        assertTrue(resolver.getBinding("test").isPresent());

        resolver.removeBinding("test");

        assertFalse(resolver.getBinding("test").isPresent());
        assertFalse(resolver.isUserSelected("test"));
    }

    @Test
    @DisplayName("Constructor with initial bindings works")
    void constructorWithInitialBindingsWorks() {
        List<MutatorBinding> initial = Arrays.asList(
            MutatorBinding.required("r1"),
            MutatorBinding.suggested("s1")
        );

        PolicyMutatorResolver resolverWithBindings = new PolicyMutatorResolver(initial);

        assertEquals(2, resolverWithBindings.getAllBindings().size());
        assertTrue(resolverWithBindings.isRequired("r1"));
        assertTrue(resolverWithBindings.isSuggested("s1"));
    }
}

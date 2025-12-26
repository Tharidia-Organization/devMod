package com.devmod.arena.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MutatorBinding Tests")
class MutatorBindingTest {

    @Test
    @DisplayName("DD50: SUGGESTED is soft binding (selectable)")
    void suggestedIsSoftBinding() {
        MutatorBinding binding = MutatorBinding.suggested("test-mutator");

        assertEquals(MutatorBinding.BindingType.SUGGESTED, binding.bindingType());
        assertTrue(binding.isSoftBinding());
        assertFalse(binding.isHardBinding());
        assertTrue(binding.isUserSelectable());
        assertFalse(binding.isActiveByDefault());
    }

    @Test
    @DisplayName("DD50: EXCLUDED is hard binding (block)")
    void excludedIsHardBinding() {
        MutatorBinding binding = MutatorBinding.excluded("blocked-mutator");

        assertEquals(MutatorBinding.BindingType.EXCLUDED, binding.bindingType());
        assertTrue(binding.isHardBinding());
        assertFalse(binding.isSoftBinding());
        assertFalse(binding.isUserSelectable());
        assertFalse(binding.isActiveByDefault());
    }

    @Test
    @DisplayName("DD50: REQUIRED is hard binding (always on)")
    void requiredIsHardBinding() {
        MutatorBinding binding = MutatorBinding.required("required-mutator");

        assertEquals(MutatorBinding.BindingType.REQUIRED, binding.bindingType());
        assertTrue(binding.isHardBinding());
        assertFalse(binding.isSoftBinding());
        assertFalse(binding.isUserSelectable());
        assertTrue(binding.isActiveByDefault());
    }

    @Test
    @DisplayName("Factory methods create correct bindings")
    void factoryMethodsCreateCorrectBindings() {
        MutatorBinding required = MutatorBinding.required("req", "Required mutator");
        MutatorBinding excluded = MutatorBinding.excluded("exc", "Not allowed");
        MutatorBinding suggested = MutatorBinding.suggested("sug", 5, "Try this one");

        assertEquals(MutatorBinding.BindingType.REQUIRED, required.bindingType());
        assertEquals("Required mutator", required.description());

        assertEquals(MutatorBinding.BindingType.EXCLUDED, excluded.bindingType());
        assertEquals("Not allowed", excluded.description());

        assertEquals(MutatorBinding.BindingType.SUGGESTED, suggested.bindingType());
        assertEquals(5, suggested.priority());
        assertEquals("Try this one", suggested.description());
    }

    @Test
    @DisplayName("REQUIRED has negative priority for UI sorting")
    void requiredHasNegativePriority() {
        MutatorBinding required = MutatorBinding.required("test");

        assertTrue(required.priority() < 0, "Required should have negative priority to sort first");
    }

    @Test
    @DisplayName("EXCLUDED has positive priority for UI sorting")
    void excludedHasPositivePriority() {
        MutatorBinding excluded = MutatorBinding.excluded("test");

        assertTrue(excluded.priority() > 0, "Excluded should have positive priority to sort last");
    }

    @Test
    @DisplayName("With methods create new instances")
    void withMethodsCreateNewInstances() {
        MutatorBinding original = MutatorBinding.suggested("test", 0, null);

        MutatorBinding withPriority = original.withPriority(10);
        MutatorBinding withDescription = original.withDescription("New description");

        assertNotSame(original, withPriority);
        assertNotSame(original, withDescription);

        assertEquals(10, withPriority.priority());
        assertEquals("New description", withDescription.description());
        assertEquals(0, original.priority()); // Original unchanged
    }

    @Test
    @DisplayName("Null mutatorId throws exception")
    void nullMutatorIdThrows() {
        assertThrows(NullPointerException.class, () ->
            new MutatorBinding(null, MutatorBinding.BindingType.SUGGESTED));
    }

    @Test
    @DisplayName("Blank mutatorId throws exception")
    void blankMutatorIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new MutatorBinding("   ", MutatorBinding.BindingType.SUGGESTED));
    }

    @Test
    @DisplayName("Null bindingType throws exception")
    void nullBindingTypeThrows() {
        assertThrows(NullPointerException.class, () ->
            new MutatorBinding("test", null));
    }

    @Test
    @DisplayName("BindingType enum methods work")
    void bindingTypeEnumMethodsWork() {
        assertTrue(MutatorBinding.BindingType.REQUIRED.isHard());
        assertTrue(MutatorBinding.BindingType.EXCLUDED.isHard());
        assertFalse(MutatorBinding.BindingType.SUGGESTED.isHard());

        assertFalse(MutatorBinding.BindingType.REQUIRED.isSoft());
        assertFalse(MutatorBinding.BindingType.EXCLUDED.isSoft());
        assertTrue(MutatorBinding.BindingType.SUGGESTED.isSoft());
    }

    @Test
    @DisplayName("Simple constructor works")
    void simpleConstructorWorks() {
        MutatorBinding binding = new MutatorBinding("simple", MutatorBinding.BindingType.SUGGESTED);

        assertEquals("simple", binding.mutatorId());
        assertEquals(MutatorBinding.BindingType.SUGGESTED, binding.bindingType());
        assertEquals(0, binding.priority());
        assertNull(binding.description());
    }

    @Test
    @DisplayName("Constructor with description works")
    void constructorWithDescriptionWorks() {
        MutatorBinding binding = new MutatorBinding("test", MutatorBinding.BindingType.REQUIRED, "A description");

        assertEquals("test", binding.mutatorId());
        assertEquals("A description", binding.description());
    }
}

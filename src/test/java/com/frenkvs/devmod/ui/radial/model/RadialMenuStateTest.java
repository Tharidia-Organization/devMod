package com.frenkvs.devmod.ui.radial.model;

import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RadialMenuState} immutable state transitions.
 * These tests verify the correctness of state management without requiring Minecraft runtime.
 */
@DisplayName("RadialMenuState")
class RadialMenuStateTest {

    // ================================================================
    // INITIALIZATION TESTS
    // ================================================================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("initial() creates state with ANALYZE macro")
        void initialCreatesDefaultState() {
            RadialMenuState state = RadialMenuState.initial();

            assertEquals(MacroCategory.ANALYZE, state.selectedMacro());
            assertNull(state.hoveredMacro());
            assertNull(state.transitionFromMacro());
            assertEquals(RadialMenuConstants.NO_SELECTION, state.selectedCategoryIndex());
            assertEquals(RadialMenuConstants.NO_SELECTION, state.selectedItemIndex());
            assertEquals(RadialMenuConstants.NO_SELECTION, state.selectedFavoriteIndex());
            assertEquals(1f, state.macroTransitionProgress());
            assertEquals(0f, state.openAnimation());
            assertFalse(state.searchMode());
            assertEquals("", state.searchQuery());
            assertFalse(state.editMode());
            assertFalse(state.closing());
        }

        @Test
        @DisplayName("initial(MacroCategory) creates state with specified macro")
        void initialWithMacro() {
            RadialMenuState state = RadialMenuState.initial(MacroCategory.COMBAT);

            assertEquals(MacroCategory.COMBAT, state.selectedMacro());
        }

        @Test
        @DisplayName("initial() throws on null macro")
        void initialThrowsOnNullMacro() {
            assertThrows(NullPointerException.class, () -> RadialMenuState.initial(null));
        }
    }

    // ================================================================
    // MACRO SELECTION TESTS
    // ================================================================

    @Nested
    @DisplayName("Macro Selection")
    class MacroSelectionTests {

        @Test
        @DisplayName("withSelectedMacro transitions to new macro")
        void withSelectedMacroTransitions() {
            RadialMenuState initial = RadialMenuState.initial(MacroCategory.ANALYZE);
            RadialMenuState updated = initial.withSelectedMacro(MacroCategory.COMBAT);

            assertEquals(MacroCategory.COMBAT, updated.selectedMacro());
            assertEquals(MacroCategory.ANALYZE, updated.transitionFromMacro());
            assertEquals(0f, updated.macroTransitionProgress());
        }

        @Test
        @DisplayName("withSelectedMacro clears category selection")
        void withSelectedMacroClearsSelection() {
            RadialMenuState initial = RadialMenuState.initial()
                .withSelectedCategory(3);
            RadialMenuState updated = initial.withSelectedMacro(MacroCategory.TOOLS);

            assertEquals(RadialMenuConstants.NO_SELECTION, updated.selectedCategoryIndex());
            assertEquals(RadialMenuConstants.NO_SELECTION, updated.selectedItemIndex());
        }

        @Test
        @DisplayName("withSelectedMacro returns same instance if macro unchanged")
        void withSelectedMacroNoOpIfSame() {
            RadialMenuState state = RadialMenuState.initial(MacroCategory.ANALYZE);
            RadialMenuState updated = state.withSelectedMacro(MacroCategory.ANALYZE);

            assertSame(state, updated);
        }

        @Test
        @DisplayName("withHoveredMacro updates hover state")
        void withHoveredMacroUpdates() {
            RadialMenuState state = RadialMenuState.initial();
            RadialMenuState updated = state.withHoveredMacro(MacroCategory.PLAY);

            assertEquals(MacroCategory.PLAY, updated.hoveredMacro());
        }

        @Test
        @DisplayName("withHoveredMacro can be set to null")
        void withHoveredMacroNull() {
            RadialMenuState state = RadialMenuState.initial()
                .withHoveredMacro(MacroCategory.COMBAT);
            RadialMenuState updated = state.withHoveredMacro(null);

            assertNull(updated.hoveredMacro());
        }
    }

    // ================================================================
    // SELECTION TESTS
    // ================================================================

    @Nested
    @DisplayName("Selection")
    class SelectionTests {

        @Test
        @DisplayName("withSelectedCategory updates index and tracks previous")
        void withSelectedCategoryTracksPrevious() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(2);
            RadialMenuState updated = state.withSelectedCategory(4);

            assertEquals(4, updated.selectedCategoryIndex());
            assertEquals(2, updated.prevSelectedCategoryIndex());
        }

        @Test
        @DisplayName("withSelectedCategory clears item selection")
        void withSelectedCategoryClearsItem() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(1)
                .withSelectedItem(3);
            RadialMenuState updated = state.withSelectedCategory(2);

            assertEquals(RadialMenuConstants.NO_SELECTION, updated.selectedItemIndex());
        }

        @Test
        @DisplayName("withSelectedCategory resets morph progress")
        void withSelectedCategoryResetsMorph() {
            RadialMenuState state = RadialMenuState.initial()
                .withMorphProgress(0.8f);
            RadialMenuState updated = state.withSelectedCategory(1);

            assertEquals(0f, updated.morphProgress());
        }

        @Test
        @DisplayName("withSelectedItem updates index")
        void withSelectedItemUpdates() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(1)
                .withSelectedItem(2);

            assertEquals(2, state.selectedItemIndex());
        }

        @Test
        @DisplayName("withSelectedFavorite updates index")
        void withSelectedFavoriteUpdates() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedFavorite(5);

            assertEquals(5, state.selectedFavoriteIndex());
        }

        @Test
        @DisplayName("clearSelections resets all selection indices")
        void clearSelectionsResetsAll() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(2)
                .withSelectedItem(3)
                .withSelectedFavorite(1);
            RadialMenuState cleared = state.clearSelections();

            assertEquals(RadialMenuConstants.NO_SELECTION, cleared.selectedCategoryIndex());
            assertEquals(RadialMenuConstants.NO_SELECTION, cleared.selectedItemIndex());
            assertEquals(RadialMenuConstants.NO_SELECTION, cleared.selectedFavoriteIndex());
        }
    }

    // ================================================================
    // ANIMATION TESTS
    // ================================================================

    @Nested
    @DisplayName("Animation Progress")
    class AnimationTests {

        @Test
        @DisplayName("withMacroTransitionProgress clamps to [0,1]")
        void macroTransitionProgressClamped() {
            RadialMenuState state = RadialMenuState.initial();

            assertEquals(0f, state.withMacroTransitionProgress(-0.5f).macroTransitionProgress());
            assertEquals(1f, state.withMacroTransitionProgress(1.5f).macroTransitionProgress());
            assertEquals(0.5f, state.withMacroTransitionProgress(0.5f).macroTransitionProgress());
        }

        @Test
        @DisplayName("transition completes when progress reaches 1")
        void transitionCompletesAt1() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedMacro(MacroCategory.COMBAT);

            assertEquals(MacroCategory.ANALYZE, state.transitionFromMacro());

            RadialMenuState completed = state.withMacroTransitionProgress(1f);
            assertNull(completed.transitionFromMacro());
        }

        @Test
        @DisplayName("openAnimation clamps to [0,1]")
        void openAnimationClamped() {
            RadialMenuState state = RadialMenuState.initial();

            assertEquals(0f, state.withOpenAnimation(-1f).openAnimation());
            assertEquals(1f, state.withOpenAnimation(2f).openAnimation());
        }

        @Test
        @DisplayName("morphProgress clamps to [0,1]")
        void morphProgressClamped() {
            RadialMenuState state = RadialMenuState.initial();

            assertEquals(0f, state.withMorphProgress(-0.1f).morphProgress());
            assertEquals(1f, state.withMorphProgress(1.1f).morphProgress());
        }

        @Test
        @DisplayName("searchBoxAnimation clamps to [0,1]")
        void searchBoxAnimationClamped() {
            RadialMenuState state = RadialMenuState.initial();

            assertEquals(0f, state.withSearchBoxAnimation(-0.5f).searchBoxAnimation());
            assertEquals(1f, state.withSearchBoxAnimation(1.5f).searchBoxAnimation());
        }
    }

    // ================================================================
    // MODE TESTS
    // ================================================================

    @Nested
    @DisplayName("Mode Transitions")
    class ModeTests {

        @Test
        @DisplayName("withSearchMode enables search")
        void withSearchModeEnables() {
            RadialMenuState state = RadialMenuState.initial()
                .withSearchMode(true);

            assertTrue(state.searchMode());
        }

        @Test
        @DisplayName("withSearchMode(false) clears query")
        void withSearchModeDisabledClearsQuery() {
            RadialMenuState state = RadialMenuState.initial()
                .withSearchMode(true)
                .withSearchQuery("test");
            RadialMenuState disabled = state.withSearchMode(false);

            assertFalse(disabled.searchMode());
            assertEquals("", disabled.searchQuery());
        }

        @Test
        @DisplayName("withSearchQuery updates query")
        void withSearchQueryUpdates() {
            RadialMenuState state = RadialMenuState.initial()
                .withSearchMode(true)
                .withSearchQuery("debug");

            assertEquals("debug", state.searchQuery());
        }

        @Test
        @DisplayName("withSearchQuery throws on null")
        void withSearchQueryThrowsOnNull() {
            RadialMenuState state = RadialMenuState.initial();
            assertThrows(NullPointerException.class, () -> state.withSearchQuery(null));
        }

        @Test
        @DisplayName("withEditMode toggles edit mode")
        void withEditModeToggles() {
            RadialMenuState state = RadialMenuState.initial()
                .withEditMode(true);

            assertTrue(state.editMode());

            RadialMenuState disabled = state.withEditMode(false);
            assertFalse(disabled.editMode());
        }

        @Test
        @DisplayName("withClosing marks menu as closing")
        void withClosingMarksClosing() {
            RadialMenuState state = RadialMenuState.initial()
                .withClosing();

            assertTrue(state.closing());
        }

        @Test
        @DisplayName("withCenterHovered updates hover state")
        void withCenterHoveredUpdates() {
            RadialMenuState state = RadialMenuState.initial()
                .withCenterHovered(true);

            assertTrue(state.centerHovered());
        }
    }

    // ================================================================
    // QUERY METHOD TESTS
    // ================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("isTransitioning returns true during transition")
        void isTransitioningDuringTransition() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedMacro(MacroCategory.COMBAT)
                .withMacroTransitionProgress(0.5f);

            assertTrue(state.isTransitioning());
        }

        @Test
        @DisplayName("isTransitioning returns false when complete")
        void isTransitioningFalseWhenComplete() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedMacro(MacroCategory.COMBAT)
                .withMacroTransitionProgress(1f);

            assertFalse(state.isTransitioning());
        }

        @Test
        @DisplayName("hasCategorySelected returns true when selected")
        void hasCategorySelectedTrue() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(2);

            assertTrue(state.hasCategorySelected());
        }

        @Test
        @DisplayName("hasCategorySelected returns false when not selected")
        void hasCategorySelectedFalse() {
            RadialMenuState state = RadialMenuState.initial();

            assertFalse(state.hasCategorySelected());
        }

        @Test
        @DisplayName("hasItemSelected returns true when selected")
        void hasItemSelectedTrue() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedItem(1);

            assertTrue(state.hasItemSelected());
        }

        @Test
        @DisplayName("hasFavoriteSelected returns true when selected")
        void hasFavoriteSelectedTrue() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedFavorite(3);

            assertTrue(state.hasFavoriteSelected());
        }

        @Test
        @DisplayName("categorySelectionChanged detects changes")
        void categorySelectionChangedDetects() {
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(1)
                .withSelectedCategory(2);

            assertTrue(state.categorySelectionChanged());
        }

        @Test
        @DisplayName("categorySelectionChanged false when unchanged")
        void categorySelectionChangedFalseWhenSame() {
            // After selecting category 1, then selecting category 2, then selecting category 1 again:
            // prevSelectedCategoryIndex becomes 2, selectedCategoryIndex becomes 1
            // Then selecting 1 again returns same instance (no-op)
            RadialMenuState state = RadialMenuState.initial()
                .withSelectedCategory(1)
                .withSelectedCategory(2)
                .withSelectedCategory(1);

            // Now select 1 again - should return same instance
            RadialMenuState same = state.withSelectedCategory(1);
            assertSame(state, same); // Verify no-op optimization
            // But categorySelectionChanged still true because prev=2, selected=1
            // The only way to get false is if prevSelectedCategoryIndex == selectedCategoryIndex
            // which happens when we call initial() where both are NO_SELECTION
            RadialMenuState initial = RadialMenuState.initial();
            assertFalse(initial.categorySelectionChanged());
        }
    }

    // ================================================================
    // IMMUTABILITY TESTS
    // ================================================================

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("state transitions return new instances")
        void transitionsReturnNewInstances() {
            RadialMenuState original = RadialMenuState.initial();

            RadialMenuState s1 = original.withSelectedMacro(MacroCategory.TOOLS);
            RadialMenuState s2 = original.withSelectedCategory(2);
            RadialMenuState s3 = original.withSearchMode(true);

            assertNotSame(original, s1);
            assertNotSame(original, s2);
            assertNotSame(original, s3);

            // Original unchanged
            assertEquals(MacroCategory.ANALYZE, original.selectedMacro());
            assertEquals(RadialMenuConstants.NO_SELECTION, original.selectedCategoryIndex());
            assertFalse(original.searchMode());
        }

        @Test
        @DisplayName("no-op transitions return same instance")
        void noOpReturnsSameInstance() {
            RadialMenuState state = RadialMenuState.initial();

            assertSame(state, state.withSelectedMacro(MacroCategory.ANALYZE));
            assertSame(state, state.withHoveredMacro(null));
            assertSame(state, state.withSelectedCategory(RadialMenuConstants.NO_SELECTION));
            assertSame(state, state.withSearchMode(false));
            assertSame(state, state.withSearchQuery(""));
        }
    }
}

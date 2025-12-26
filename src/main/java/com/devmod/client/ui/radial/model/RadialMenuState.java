package com.devmod.client.ui.radial.model;

import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.client.ui.radial.config.RadialMenuConstants;

public record RadialMenuState(
    // === Macro Selection ===
    MacroCategory selectedMacro,
    @Nullable MacroCategory hoveredMacro,
    @Nullable MacroCategory transitionFromMacro,

    // === Category/Item Selection ===
    int selectedCategoryIndex,
    int selectedItemIndex,
    int selectedFavoriteIndex,
    int prevSelectedCategoryIndex,

    // === Animation Progress ===
    float macroTransitionProgress,
    float openAnimation,
    float morphProgress,
    float searchBoxAnimation,

    // === Mode Flags ===
    boolean searchMode,
    String searchQuery,
    boolean editMode,
    boolean closing,

    // === Center Hub State ===
    boolean centerHovered
) {
    /**
     * Compact constructor with validation.
     */
    public RadialMenuState {
        Objects.requireNonNull(selectedMacro, "selectedMacro cannot be null");
        Objects.requireNonNull(searchQuery, "searchQuery cannot be null");

        // Clamp animation values
        macroTransitionProgress = clamp01(macroTransitionProgress);
        openAnimation = clamp01(openAnimation);
        morphProgress = clamp01(morphProgress);
        searchBoxAnimation = clamp01(searchBoxAnimation);
    }

    /**
     * Creates the initial state for a newly opened radial menu.
     * @param persistedMacro the macro-category to start with (persisted across opens)
     * @return initial state
     */
    public static RadialMenuState initial(MacroCategory persistedMacro) {
        return new RadialMenuState(
            persistedMacro,
            null,                               // hoveredMacro
            null,                               // transitionFromMacro
            RadialMenuConstants.NO_SELECTION,   // selectedCategoryIndex
            RadialMenuConstants.NO_SELECTION,   // selectedItemIndex
            RadialMenuConstants.NO_SELECTION,   // selectedFavoriteIndex
            RadialMenuConstants.NO_SELECTION,   // prevSelectedCategoryIndex
            1f,                                 // macroTransitionProgress (completed)
            0f,                                 // openAnimation (starting)
            1f,                                 // morphProgress (completed)
            0f,                                 // searchBoxAnimation
            false,                              // searchMode
            "",                                 // searchQuery
            false,                              // editMode
            false,                              // closing
            false                               // centerHovered
        );
    }

    /**
     * Creates initial state with default macro (ANALYZE).
     * @return initial state
     */
    public static RadialMenuState initial() {
        return initial(MacroCategory.ANALYZE);
    }

    // ================================================================
    // MACRO SELECTION TRANSITIONS
    // ================================================================

    /**
     * Transitions to a new macro-category with cross-fade animation.
     * @param newMacro the new macro to select
     * @return new state with transition in progress
     */
    public RadialMenuState withSelectedMacro(MacroCategory newMacro) {
        if (newMacro == selectedMacro) {
            return this;
        }
        return new RadialMenuState(
            newMacro,
            hoveredMacro,
            selectedMacro,  // Store current as transitionFrom
            RadialMenuConstants.NO_SELECTION,
            RadialMenuConstants.NO_SELECTION,
            selectedFavoriteIndex,
            prevSelectedCategoryIndex,
            0f,             // Reset transition progress
            openAnimation,
            morphProgress,
            searchBoxAnimation,
            searchMode,
            searchQuery,
            editMode,
            closing,
            centerHovered
        );
    }

    /**
     * Updates the hovered macro-category.
     * @param macro the macro being hovered, or null if none
     * @return new state
     */
    public RadialMenuState withHoveredMacro(@Nullable MacroCategory macro) {
        if (macro == hoveredMacro) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, macro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    // ================================================================
    // SELECTION TRANSITIONS
    // ================================================================

    /**
     * Updates the selected category index.
     * @param index the new category index, or NO_SELECTION
     * @return new state
     */
    public RadialMenuState withSelectedCategory(int index) {
        if (index == selectedCategoryIndex) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            index, RadialMenuConstants.NO_SELECTION, selectedFavoriteIndex, selectedCategoryIndex,
            macroTransitionProgress, openAnimation, 0f, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Updates the selected item index.
     * @param index the new item index, or NO_SELECTION
     * @return new state
     */
    public RadialMenuState withSelectedItem(int index) {
        if (index == selectedItemIndex) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, index, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Updates the selected favorite index.
     * @param index the new favorite index, or NO_SELECTION
     * @return new state
     */
    public RadialMenuState withSelectedFavorite(int index) {
        if (index == selectedFavoriteIndex) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, index, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Clears all selection indices.
     * @return new state with no selections
     */
    public RadialMenuState clearSelections() {
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            RadialMenuConstants.NO_SELECTION,
            RadialMenuConstants.NO_SELECTION,
            RadialMenuConstants.NO_SELECTION,
            selectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    // ================================================================
    // ANIMATION TRANSITIONS
    // ================================================================

    /**
     * Updates the macro transition progress.
     * @param progress new progress value (0-1)
     * @return new state
     */
    public RadialMenuState withMacroTransitionProgress(float progress) {
        float clamped = clamp01(progress);
        if (clamped == macroTransitionProgress) {
            return this;
        }
        // Clear transitionFromMacro when transition completes
        MacroCategory from = clamped >= 1f ? null : transitionFromMacro;
        return new RadialMenuState(
            selectedMacro, hoveredMacro, from,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            clamped, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Updates the open animation progress.
     * @param progress new progress value (0-1)
     * @return new state
     */
    public RadialMenuState withOpenAnimation(float progress) {
        float clamped = clamp01(progress);
        if (clamped == openAnimation) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, clamped, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Updates the morph progress.
     * @param progress new progress value (0-1)
     * @return new state
     */
    public RadialMenuState withMorphProgress(float progress) {
        float clamped = clamp01(progress);
        if (clamped == morphProgress) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, clamped, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    /**
     * Updates the search box animation progress.
     * @param progress new progress value (0-1)
     * @return new state
     */
    public RadialMenuState withSearchBoxAnimation(float progress) {
        float clamped = clamp01(progress);
        if (clamped == searchBoxAnimation) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, clamped,
            searchMode, searchQuery, editMode, closing, centerHovered
        );
    }

    // ================================================================
    // MODE TRANSITIONS
    // ================================================================

    /**
     * Toggles or sets search mode.
     * @param enabled whether search mode is enabled
     * @return new state
     */
    public RadialMenuState withSearchMode(boolean enabled) {
        if (enabled == searchMode) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            enabled, enabled ? searchQuery : "", editMode, closing, centerHovered
        );
    }

    /**
     * Updates the search query.
     * @param query the new search query
     * @return new state
     */
    public RadialMenuState withSearchQuery(String query) {
        Objects.requireNonNull(query, "searchQuery cannot be null");
        if (query.equals(searchQuery)) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, query, editMode, closing, centerHovered
        );
    }

    /**
     * Toggles or sets edit mode.
     * @param enabled whether edit mode is enabled
     * @return new state
     */
    public RadialMenuState withEditMode(boolean enabled) {
        if (enabled == editMode) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, enabled, closing, centerHovered
        );
    }

    /**
     * Marks the menu as closing.
     * @return new state with closing=true
     */
    public RadialMenuState withClosing() {
        if (closing) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, true, centerHovered
        );
    }

    /**
     * Updates the center hover state.
     * @param hovered whether the center close button is hovered
     * @return new state
     */
    public RadialMenuState withCenterHovered(boolean hovered) {
        if (hovered == centerHovered) {
            return this;
        }
        return new RadialMenuState(
            selectedMacro, hoveredMacro, transitionFromMacro,
            selectedCategoryIndex, selectedItemIndex, selectedFavoriteIndex, prevSelectedCategoryIndex,
            macroTransitionProgress, openAnimation, morphProgress, searchBoxAnimation,
            searchMode, searchQuery, editMode, closing, hovered
        );
    }

    // ================================================================
    // QUERY METHODS
    // ================================================================

    /**
     * Checks if a macro transition is in progress.
     * @return true if transitioning between macros
     */
    public boolean isTransitioning() {
        return macroTransitionProgress < 1f && transitionFromMacro != null;
    }

    /**
     * Checks if any category is selected.
     * @return true if a category is selected
     */
    public boolean hasCategorySelected() {
        return selectedCategoryIndex != RadialMenuConstants.NO_SELECTION;
    }

    /**
     * Checks if any item is selected.
     * @return true if an item is selected
     */
    public boolean hasItemSelected() {
        return selectedItemIndex != RadialMenuConstants.NO_SELECTION;
    }

    /**
     * Checks if any favorite is selected.
     * @return true if a favorite is selected
     */
    public boolean hasFavoriteSelected() {
        return selectedFavoriteIndex != RadialMenuConstants.NO_SELECTION;
    }

    /**
     * Checks if the category selection changed since last update.
     * @return true if category selection changed
     */
    public boolean categorySelectionChanged() {
        return selectedCategoryIndex != prevSelectedCategoryIndex;
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

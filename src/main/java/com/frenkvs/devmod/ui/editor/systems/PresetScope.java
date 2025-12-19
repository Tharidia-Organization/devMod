package com.frenkvs.devmod.ui.editor.systems;

/**
 * Sealed interface representing the scope/priority level of a preset.
 *
 * Presets are resolved with priority order: MODPACK (3) > CATEGORY (2) > GLOBAL (1)
 * This allows modpack-specific presets to override category defaults, which override global defaults.
 */
public sealed interface PresetScope permits
    PresetScope.Global,
    PresetScope.Category,
    PresetScope.Modpack {

    /**
     * Global presets available everywhere, lowest priority.
     */
    record Global() implements PresetScope {}

    /**
     * Category-specific presets (e.g., sword, bow, helmet).
     */
    record Category(String category) implements PresetScope {}

    /**
     * Modpack-specific presets, highest priority.
     */
    record Modpack(String modpackId, String category) implements PresetScope {}

    /**
     * Get the priority of this scope for resolution ordering.
     * Higher values take precedence.
     *
     * @return priority value (1=Global, 2=Category, 3=Modpack)
     */
    default int priority() {
        return switch (this) {
            case Modpack ignored -> 3;   // Highest priority
            case Category ignored -> 2;  // Medium priority
            case Global ignored -> 1;    // Lowest priority
        };
    }

    /**
     * Get a human-readable label for this scope.
     */
    default String label() {
        return switch (this) {
            case Modpack(var id, var cat) -> "Modpack: " + id;
            case Category(var cat) -> "Category: " + cat;
            case Global() -> "Global";
        };
    }
}

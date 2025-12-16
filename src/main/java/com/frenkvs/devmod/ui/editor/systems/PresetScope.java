package com.frenkvs.devmod.ui.editor.systems;

/**
 * Sealed hierarchy for preset scopes with 3-level priority.
 * Resolution order: MODPACK → CATEGORY → GLOBAL (first match wins)
 */
public sealed interface PresetScope {
    record Global() implements PresetScope {}
    record Category(String category) implements PresetScope {}
    record Modpack(String modpackId, String category) implements PresetScope {}
    
    default int priority() {
        return switch (this) {
            case Modpack(var id, var cat) -> 3; // Highest
            case Category(var cat) -> 2;
            case Global() -> 1; // Lowest
        };
    }
}
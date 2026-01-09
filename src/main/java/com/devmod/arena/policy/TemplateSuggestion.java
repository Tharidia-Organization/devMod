package com.devmod.arena.policy;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * DTO representing a suggested arena template for the UI.
 * Contains scoring information to help users understand why a template was chosen.
 */
public record TemplateSuggestion(
    /** Template ID for selection */
    String templateId,

    /** Human-readable template name */
    String templateName,

    /** Arena size in blocks */
    int size,

    /** Biome ID (e.g., "minecraft:plains") */
    String biome,

    /** Arena shape for 3D preview (RECTANGULAR, CIRCULAR, RING) */
    String arenaShape,

    /** Whether walls are enabled */
    boolean hasWalls,

    /** Whether hazards are present */
    boolean hasHazards,

    /** Number of spawn slots */
    int spawnSlotCount,

    /** Overall compatibility score (higher = better fit) */
    double compatibilityScore,

    /** Breakdown of individual score components */
    Map<String, Double> scoreBreakdown,

    /** Whether this is the currently selected/recommended template */
    boolean isSelected,

    /** If incompatible, explains why (null if compatible) */
    @Nullable String incompatibilityReason
) {
    /**
     * Creates a compatible suggestion with score.
     */
    public static TemplateSuggestion compatible(
            String templateId,
            String templateName,
            int size,
            String biome,
            String arenaShape,
            boolean hasWalls,
            boolean hasHazards,
            int spawnSlotCount,
            double score,
            Map<String, Double> breakdown,
            boolean selected) {
        return new TemplateSuggestion(templateId, templateName, size, biome, arenaShape,
            hasWalls, hasHazards, spawnSlotCount, score, breakdown, selected, null);
    }

    /**
     * Creates an incompatible suggestion with reason.
     */
    public static TemplateSuggestion incompatible(
            String templateId,
            String templateName,
            int size,
            String biome,
            String arenaShape,
            boolean hasWalls,
            boolean hasHazards,
            int spawnSlotCount,
            String reason) {
        return new TemplateSuggestion(templateId, templateName, size, biome, arenaShape,
            hasWalls, hasHazards, spawnSlotCount, 0.0, Map.of(), false, reason);
    }

    /**
     * Returns true if this template is compatible with the mob requirements.
     */
    public boolean isCompatible() {
        return incompatibilityReason == null;
    }

    /**
     * Returns a short display string for the UI.
     */
    public String displayString() {
        if (incompatibilityReason != null) {
            return String.format("%s (%d) - %s", templateName, size, incompatibilityReason);
        }
        return String.format("%s (%d) - Score: %.1f", templateName, size, compatibilityScore);
    }
}

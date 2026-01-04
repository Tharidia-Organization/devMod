package com.devmod.client.endurance.wis;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Pre-wave briefing data shown during the 10-second countdown.
 * Contains wave composition, threats, and modifiers for the upcoming wave.
 */
public record WaveBriefingData(
    // Wave info
    int waveNumber,
    int totalWaves,

    // Mob composition
    List<MobComposition> mobComposition,
    int totalMobCount,
    int eliteCount,

    // Modifiers
    Set<String> activeModifiers,

    // Objective
    @Nullable ObjectiveInfo objective,

    // Threat assessment
    ThreatLevel threatLevel,
    List<String> warnings,

    // Player status (can be null if not available)
    @Nullable PlayerStatus playerStatus,

    // Comparison to previous wave (can be null for first wave)
    @Nullable WaveComparison comparison
) {

    /**
     * Mob type and count in the wave.
     */
    public record MobComposition(
        ResourceLocation mobType,
        String displayName,
        int count,
        int eliteCount,
        float estimatedHealth,
        float estimatedDamage,
        boolean isBoss
    ) {}

    /**
     * Wave objective information.
     */
    public record ObjectiveInfo(
        String type,           // "kill_all", "elite_hunt", "survive", etc.
        String description,
        int targetCount,       // e.g., kills required
        int timeLimit          // -1 for no limit
    ) {}

    /**
     * Overall threat level for the wave.
     */
    public enum ThreatLevel {
        LOW("Low", DesignTokens.Semantic.SUCCESS),
        MEDIUM("Medium", DesignTokens.Semantic.WARNING),
        HIGH("High", DesignTokens.Accent.SECONDARY),
        EXTREME("Extreme", DesignTokens.Semantic.ERROR);

        private final String displayName;
        private final int color;

        ThreatLevel(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
    }

    /**
     * Current player status snapshot.
     */
    public record PlayerStatus(
        float health,
        float maxHealth,
        float armor,
        int activePerks,
        int comboCount,
        String styleRank
    ) {}

    /**
     * Comparison with previous wave for trend indicators.
     */
    public record WaveComparison(
        float healthChange,        // +10% harder, etc.
        float damageChange,
        int mobCountChange,
        boolean newModifiers,
        List<String> addedModifiers
    ) {}

    /**
     * Builder for creating WaveBriefingData.
     */
    public static class Builder {
        private int waveNumber;
        private int totalWaves;
        private List<MobComposition> mobComposition = List.of();
        private int totalMobCount;
        private int eliteCount;
        private Set<String> activeModifiers = Set.of();
        @javax.annotation.Nullable
        private ObjectiveInfo objective;
        private ThreatLevel threatLevel = ThreatLevel.MEDIUM;
        private List<String> warnings = List.of();
        @javax.annotation.Nullable
        private PlayerStatus playerStatus;
        @javax.annotation.Nullable
        private WaveComparison comparison;

        public Builder waveNumber(int waveNumber) {
            this.waveNumber = waveNumber;
            return this;
        }

        public Builder totalWaves(int totalWaves) {
            this.totalWaves = totalWaves;
            return this;
        }

        public Builder mobComposition(List<MobComposition> mobComposition) {
            this.mobComposition = mobComposition;
            return this;
        }

        public Builder totalMobCount(int totalMobCount) {
            this.totalMobCount = totalMobCount;
            return this;
        }

        public Builder eliteCount(int eliteCount) {
            this.eliteCount = eliteCount;
            return this;
        }

        public Builder activeModifiers(Set<String> activeModifiers) {
            this.activeModifiers = activeModifiers;
            return this;
        }

        public Builder objective(ObjectiveInfo objective) {
            this.objective = objective;
            return this;
        }

        public Builder threatLevel(ThreatLevel threatLevel) {
            this.threatLevel = threatLevel;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder playerStatus(PlayerStatus playerStatus) {
            this.playerStatus = playerStatus;
            return this;
        }

        public Builder comparison(WaveComparison comparison) {
            this.comparison = comparison;
            return this;
        }

        public WaveBriefingData build() {
            return new WaveBriefingData(
                waveNumber, totalWaves, mobComposition, totalMobCount, eliteCount,
                activeModifiers, objective, threatLevel, warnings, playerStatus, comparison
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the primary (most common) mob type in this wave.
     */
    @Nullable
    public MobComposition getPrimaryMobType() {
        if (mobComposition.isEmpty()) return null;
        return mobComposition.stream()
            .max((a, b) -> Integer.compare(a.count(), b.count()))
            .orElse(null);
    }

    /**
     * Check if this wave has a boss.
     */
    public boolean hasBoss() {
        return mobComposition.stream().anyMatch(MobComposition::isBoss);
    }

    /**
     * Get the total estimated wave HP.
     */
    public float getTotalEstimatedHP() {
        return (float) mobComposition.stream()
            .mapToDouble(m -> m.estimatedHealth() * m.count())
            .sum();
    }
}

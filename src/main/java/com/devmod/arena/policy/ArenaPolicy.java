package com.devmod.arena.policy;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Arena Policy definition (L2 Gameplay layer).
 *
 * <p>Policies define gameplay rules that get applied on top of templates:
 * which template to use for which mob/quest/difficulty combination.
 */
public record ArenaPolicy(
    /** Unique policy identifier */
    String id,

    /** Policy version (higher wins on tie-break) */
    int version,

    /** Template ID this policy uses */
    String templateId,

    /** Minimum template version supported (inclusive) */
    @Nullable Integer minTemplateVersion,

    /** Maximum template version supported (inclusive) */
    @Nullable Integer maxTemplateVersion,

    /** Mob type(s) this policy applies to */
    @Nullable Set<String> mobTypes,

    /** Quest type filter (e.g., "boss", "normal", "event") */
    @Nullable String questType,

    /** Difficulty filter */
    @Nullable String difficulty,

    /** Player count range - min */
    @Nullable Integer minPlayers,

    /** Player count range - max */
    @Nullable Integer maxPlayers,

    /** Tags for matching */
    @Nullable Set<String> tags,

    /** Priority for explicit ordering */
    int priority,

    /** Routing weight (0.1 - 10.0, default 1.0) */
    double weight,

    /** Perk bindings (suggested/excluded/required) */
    @Nullable PerkBindings perkBindings,

    /** Mutator bindings (suggested/excluded/required) */
    @Nullable MutatorBindings mutatorBindings,

    /** Reward modifiers */
    @Nullable RewardModifiers rewardModifiers,

    /** Balance overrides */
    @Nullable BalanceOverrides balanceOverrides,

    /** Whether this policy is enabled */
    boolean enabled,

    /** Human-readable description */
    @Nullable String description
) {
    /**
     * Default policy using default_flat_64 template.
     */
    public static final ArenaPolicy DEFAULT = new ArenaPolicy(
        "default",
        1,
        "default_flat_64",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Set.of(),
        0,
        1.0,
        null,
        null,
        null,
        null,
        true,
        "Default fallback policy"
    );

    /**
     * Creates a builder for this policy.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns a copy of this policy with the provided weight.
     */
    public ArenaPolicy withWeight(double newWeight) {
        return new ArenaPolicy(
            id, version, templateId, minTemplateVersion, maxTemplateVersion, mobTypes, questType, difficulty,
            minPlayers, maxPlayers, tags, priority, newWeight, perkBindings, mutatorBindings, rewardModifiers, balanceOverrides, enabled, description
        );
    }

    /**
     * Builder for ArenaPolicy.
     */
    public static class Builder {
        private final String id;
        private int version = 1;
        private String templateId;
        private Integer minTemplateVersion;
        private Integer maxTemplateVersion;
        private Set<String> mobTypes;
        private String questType;
        private String difficulty;
        private Integer minPlayers;
        private Integer maxPlayers;
        private Set<String> tags;
        private int priority = 0;
        private double weight = 1.0;
        private PerkBindings perkBindings;
        private MutatorBindings mutatorBindings;
        private RewardModifiers rewardModifiers;
        private BalanceOverrides balanceOverrides;
        private boolean enabled = true;
        private String description;

        public Builder(String id) {
            this.id = id;
        }

        public Builder version(int version) { this.version = version; return this; }
        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder minTemplateVersion(@Nullable Integer minTemplateVersion) { this.minTemplateVersion = minTemplateVersion; return this; }
        public Builder maxTemplateVersion(@Nullable Integer maxTemplateVersion) { this.maxTemplateVersion = maxTemplateVersion; return this; }
        public Builder mobTypes(Set<String> mobTypes) { this.mobTypes = mobTypes; return this; }
        public Builder questType(String questType) { this.questType = questType; return this; }
        public Builder difficulty(String difficulty) { this.difficulty = difficulty; return this; }
        public Builder minPlayers(Integer minPlayers) { this.minPlayers = minPlayers; return this; }
        public Builder maxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder weight(double weight) { this.weight = weight; return this; }
        public Builder perkBindings(@Nullable PerkBindings perkBindings) { this.perkBindings = perkBindings; return this; }
        public Builder mutatorBindings(@Nullable MutatorBindings mutatorBindings) { this.mutatorBindings = mutatorBindings; return this; }
        public Builder rewardModifiers(@Nullable RewardModifiers rewardModifiers) { this.rewardModifiers = rewardModifiers; return this; }
        public Builder balanceOverrides(@Nullable BalanceOverrides balanceOverrides) { this.balanceOverrides = balanceOverrides; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public ArenaPolicy build() {
            return new ArenaPolicy(
                id, version, templateId, minTemplateVersion, maxTemplateVersion, mobTypes, questType, difficulty,
                minPlayers, maxPlayers, tags, priority, weight,
                perkBindings, mutatorBindings, rewardModifiers, balanceOverrides,
                enabled, description
            );
        }
    }

    public record PerkBindings(
        @Nullable Set<String> suggested,
        @Nullable Set<String> excluded,
        @Nullable Set<String> required
    ) {}

    public record MutatorBindings(
        @Nullable Set<String> suggested,
        @Nullable Set<String> excluded,
        @Nullable Set<String> required
    ) {}

    public record RewardModifiers(
        double baseMultiplier,
        double firstCompletionBonus,
        double hazardBonus,
        double streakMultiplier
    ) {}

    public record BalanceOverrides(
        @Nullable Double spawnRateMultiplier,
        @Nullable Double damageMultiplier,
        @Nullable Double waveScaling
    ) {}
}

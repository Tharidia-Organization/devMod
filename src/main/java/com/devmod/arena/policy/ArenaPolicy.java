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
        Set.of(),
        0,
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
     * Builder for ArenaPolicy.
     */
    public static class Builder {
        private final String id;
        private int version = 1;
        private String templateId;
        private Set<String> mobTypes;
        private String questType;
        private String difficulty;
        private Integer minPlayers;
        private Integer maxPlayers;
        private Set<String> tags;
        private int priority = 0;
        private boolean enabled = true;
        private String description;

        public Builder(String id) {
            this.id = id;
        }

        public Builder version(int version) { this.version = version; return this; }
        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder mobTypes(Set<String> mobTypes) { this.mobTypes = mobTypes; return this; }
        public Builder questType(String questType) { this.questType = questType; return this; }
        public Builder difficulty(String difficulty) { this.difficulty = difficulty; return this; }
        public Builder minPlayers(Integer minPlayers) { this.minPlayers = minPlayers; return this; }
        public Builder maxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public ArenaPolicy build() {
            return new ArenaPolicy(
                id, version, templateId, mobTypes, questType, difficulty,
                minPlayers, maxPlayers, tags, priority, enabled, description
            );
        }
    }
}

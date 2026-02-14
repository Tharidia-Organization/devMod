package com.devmod.quest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Immutable quest definition. Player progress is tracked separately in {@link QuestProgress}.
 */
public class Quest {

    private final String id;
    private final Component name;
    private final Component description;
    private final List<QuestObjective> objectives;
    private final QuestReward reward;
    private final List<String> requiredQuestIds;
    private final boolean repeatable;
    private final long cooldownTicks;
    @Nullable
    private final String category;

    private Quest(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.objectives = List.copyOf(builder.objectives);
        this.reward = builder.reward;
        this.requiredQuestIds = List.copyOf(builder.requiredQuestIds);
        this.repeatable = builder.repeatable;
        this.cooldownTicks = builder.cooldownTicks;
        this.category = builder.category;
    }

    public String getId() { return id; }
    public Component getName() { return name; }
    public Component getDescription() { return description; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public QuestReward getReward() { return reward; }
    public List<String> getRequiredQuestIds() { return requiredQuestIds; }
    public boolean isRepeatable() { return repeatable; }
    public long getCooldownTicks() { return cooldownTicks; }
    @Nullable
    public String getCategory() { return category; }

    public boolean hasPrerequisites() {
        return !requiredQuestIds.isEmpty();
    }

    public int getObjectiveCount() {
        return objectives.size();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private Component name = Component.empty();
        private Component description = Component.empty();
        private final List<QuestObjective> objectives = new ArrayList<>();
        private QuestReward reward = QuestReward.EMPTY;
        private final List<String> requiredQuestIds = new ArrayList<>();
        private boolean repeatable = false;
        private long cooldownTicks = 0;
        @Nullable
        private String category;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(Component name) { this.name = name; return this; }
        public Builder name(String name) { this.name = Component.literal(name); return this; }
        public Builder description(Component desc) { this.description = desc; return this; }
        public Builder description(String desc) { this.description = Component.literal(desc); return this; }
        public Builder addObjective(QuestObjective obj) { this.objectives.add(obj); return this; }
        public Builder objectives(List<QuestObjective> objs) { this.objectives.addAll(objs); return this; }
        public Builder reward(QuestReward reward) { this.reward = reward; return this; }
        public Builder requiresQuest(String questId) { this.requiredQuestIds.add(questId); return this; }
        public Builder repeatable(boolean repeatable) { this.repeatable = repeatable; return this; }
        public Builder cooldownTicks(long ticks) { this.cooldownTicks = ticks; return this; }
        public Builder category(String category) { this.category = category; return this; }

        public Quest build() {
            return new Quest(this);
        }
    }
}

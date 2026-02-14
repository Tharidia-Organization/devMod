package com.devmod.quest;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Rewards granted upon quest completion.
 */
public record QuestReward(
    int experiencePoints,
    List<ItemStack> items,
    int currency,
    List<String> unlockedQuestIds
) {
    public static final QuestReward EMPTY = new QuestReward(0, List.of(), 0, List.of());

    public boolean hasExperience() {
        return experiencePoints > 0;
    }

    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    public boolean hasCurrency() {
        return currency > 0;
    }

    public boolean hasUnlocks() {
        return unlockedQuestIds != null && !unlockedQuestIds.isEmpty();
    }

    public boolean isEmpty() {
        return !hasExperience() && !hasItems() && !hasCurrency() && !hasUnlocks();
    }

    public Component getSummary() {
        StringBuilder sb = new StringBuilder();
        if (hasExperience()) {
            sb.append(experiencePoints).append(" XP");
        }
        if (hasCurrency()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(currency).append(" coins");
        }
        if (hasItems()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(items.size()).append(" item(s)");
        }
        if (sb.isEmpty()) {
            return Component.translatable("devmod.quest.reward.none");
        }
        return Component.literal(sb.toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int xp;
        private List<ItemStack> items = List.of();
        private int currency;
        private List<String> unlocks = List.of();

        public Builder xp(int xp) { this.xp = xp; return this; }
        public Builder items(List<ItemStack> items) { this.items = items; return this; }
        public Builder currency(int currency) { this.currency = currency; return this; }
        public Builder unlocks(List<String> unlocks) { this.unlocks = unlocks; return this; }

        public QuestReward build() {
            return new QuestReward(xp, items, currency, unlocks);
        }
    }
}

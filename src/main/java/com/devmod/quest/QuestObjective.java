package com.devmod.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Sealed interface representing typed quest objectives with progress tracking.
 */
public sealed interface QuestObjective permits
    QuestObjective.Kill, QuestObjective.Collect,
    QuestObjective.Visit, QuestObjective.VisitDimension,
    QuestObjective.Interact,
    QuestObjective.CraftItem, QuestObjective.ReachLevel {

    String id();
    Component description();
    boolean isComplete();
    float progress();

    record Kill(String id, EntityType<?> target, int required, int current) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.kill",
                target.getDescription(), current, required);
        }

        @Override
        public boolean isComplete() {
            return current >= required;
        }

        @Override
        public float progress() {
            return required <= 0 ? 1.0f : Math.min(1.0f, (float) current / required);
        }

        public Kill withCurrent(int newCurrent) {
            return new Kill(id, target, required, newCurrent);
        }

        public Kill increment() {
            return new Kill(id, target, required, current + 1);
        }
    }

    record Collect(String id, Item item, int required, int current) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.collect",
                item.getDescription(), current, required);
        }

        @Override
        public boolean isComplete() {
            return current >= required;
        }

        @Override
        public float progress() {
            return required <= 0 ? 1.0f : Math.min(1.0f, (float) current / required);
        }

        public Collect withCurrent(int newCurrent) {
            return new Collect(id, item, required, newCurrent);
        }

        public Collect increment(int amount) {
            return new Collect(id, item, required, current + amount);
        }
    }

    record Visit(String id, BlockPos target, int radius, boolean visited) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.visit",
                target.getX(), target.getY(), target.getZ());
        }

        @Override
        public boolean isComplete() {
            return visited;
        }

        @Override
        public float progress() {
            return visited ? 1.0f : 0.0f;
        }

        public Visit markVisited() {
            return new Visit(id, target, radius, true);
        }
    }

    record Interact(String id, String npcId, String npcName, boolean interacted) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.interact", npcName);
        }

        @Override
        public boolean isComplete() {
            return interacted;
        }

        @Override
        public float progress() {
            return interacted ? 1.0f : 0.0f;
        }

        public Interact markInteracted() {
            return new Interact(id, npcId, npcName, true);
        }
    }

    record CraftItem(String id, Item item, int required, int crafted) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.craft",
                item.getDescription(), crafted, required);
        }

        @Override
        public boolean isComplete() {
            return crafted >= required;
        }

        @Override
        public float progress() {
            return required <= 0 ? 1.0f : Math.min(1.0f, (float) crafted / required);
        }

        public CraftItem withCrafted(int newCrafted) {
            return new CraftItem(id, item, required, newCrafted);
        }

        public CraftItem increment(int amount) {
            return new CraftItem(id, item, required, crafted + amount);
        }
    }

    record ReachLevel(String id, int targetLevel, int currentLevel) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.reach_level",
                currentLevel, targetLevel);
        }

        @Override
        public boolean isComplete() {
            return currentLevel >= targetLevel;
        }

        @Override
        public float progress() {
            return targetLevel <= 0 ? 1.0f : Math.min(1.0f, (float) currentLevel / targetLevel);
        }

        public ReachLevel withLevel(int newLevel) {
            return new ReachLevel(id, targetLevel, newLevel);
        }
    }

    record VisitDimension(String id, ResourceKey<Level> dimension, String dimensionName, boolean visited) implements QuestObjective {
        @Override
        public Component description() {
            return Component.translatable("devmod.quest.objective.visit_dimension", dimensionName);
        }

        @Override
        public boolean isComplete() {
            return visited;
        }

        @Override
        public float progress() {
            return visited ? 1.0f : 0.0f;
        }

        public VisitDimension markVisited() {
            return new VisitDimension(id, dimension, dimensionName, true);
        }
    }
}

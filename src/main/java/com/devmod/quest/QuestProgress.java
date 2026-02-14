package com.devmod.quest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Tracks a player's progress on a specific quest.
 * Stores mutable state separate from the immutable Quest definition.
 */
public class QuestProgress {

    private final String questId;
    private QuestStatus status;
    private final List<QuestObjective> objectives;
    private long startedAtTick;
    private long completedAtTick;
    private boolean tracked;

    public QuestProgress(String questId, List<QuestObjective> objectives) {
        this.questId = questId;
        this.status = QuestStatus.NOT_STARTED;
        this.objectives = new ArrayList<>(objectives);
        this.startedAtTick = 0;
        this.completedAtTick = 0;
        this.tracked = false;
    }

    public String getQuestId() { return questId; }
    public QuestStatus getStatus() { return status; }
    public List<QuestObjective> getObjectives() { return objectives; }
    public long getStartedAtTick() { return startedAtTick; }
    public long getCompletedAtTick() { return completedAtTick; }
    public boolean isTracked() { return tracked; }

    public void setStatus(QuestStatus status) { this.status = status; }
    public void setStartedAtTick(long tick) { this.startedAtTick = tick; }
    public void setCompletedAtTick(long tick) { this.completedAtTick = tick; }
    public void setTracked(boolean tracked) { this.tracked = tracked; }

    /**
     * Updates a specific objective by ID, replacing it in the list.
     */
    public void updateObjective(String objectiveId, QuestObjective newObjective) {
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).id().equals(objectiveId)) {
                objectives.set(i, newObjective);
                return;
            }
        }
    }

    /**
     * Returns the objective with the given ID, or null if not found.
     */
    @Nullable
    public QuestObjective getObjective(String objectiveId) {
        for (QuestObjective obj : objectives) {
            if (obj.id().equals(objectiveId)) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Returns the overall progress (0.0 to 1.0) across all objectives.
     */
    public float getOverallProgress() {
        if (objectives.isEmpty()) return 0f;
        float total = 0f;
        for (QuestObjective obj : objectives) {
            total += obj.progress();
        }
        return total / objectives.size();
    }

    /**
     * Checks if all objectives are complete.
     */
    public boolean areAllObjectivesComplete() {
        for (QuestObjective obj : objectives) {
            if (!obj.isComplete()) return false;
        }
        return !objectives.isEmpty();
    }

    /**
     * Returns the count of completed objectives.
     */
    public int getCompletedObjectiveCount() {
        int count = 0;
        for (QuestObjective obj : objectives) {
            if (obj.isComplete()) count++;
        }
        return count;
    }

    /**
     * Returns a progress summary string (e.g., "2/5 objectives").
     */
    public String getProgressSummary() {
        return getCompletedObjectiveCount() + "/" + objectives.size() + " objectives";
    }
}

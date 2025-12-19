package com.frenkvs.devmod.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a quest with its tasks.
 */
public class QuestData {

    /**
     * Type of quest.
     */
    public enum QuestType {
        MANUAL,     // User-created quest with manual tasks
        ENDURANCE   // Auto-generated endurance quest (wave-based combat)
    }

    private final String id;
    private final String name;
    private final List<QuestTask> tasks;
    private int currentTaskIndex;
    private String questNote;

    // Endurance quest specific fields
    private QuestType questType = QuestType.MANUAL;
    private String targetMobId;        // For endurance quests: the mob to fight
    private int totalWaves;            // Total waves configured
    private int currentWave;           // Current wave (0-based)
    private boolean endlessMode;       // If true, waves continue indefinitely
    private int totalKills;            // Total kills in this endurance run
    private int totalPoints;           // Points accumulated
    private long startTimeMillis;      // When the quest started
    private boolean questActive;       // Is the endurance quest currently running

    public QuestData(String id, String name) {
        this.id = id;
        this.name = name;
        this.tasks = new ArrayList<>();
        this.currentTaskIndex = 0;
        this.questNote = "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<QuestTask> getTasks() {
        return tasks;
    }

    public void addTask(QuestTask task) {
        tasks.add(task);
    }

    public int getCurrentTaskIndex() {
        return currentTaskIndex;
    }

    public void setCurrentTaskIndex(int index) {
        if (index >= 0 && index < tasks.size()) {
            this.currentTaskIndex = index;
        }
    }

    /**
     * Returns the current task (next to complete).
     */
    public QuestTask getCurrentTask() {
        if (tasks.isEmpty() || currentTaskIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(currentTaskIndex);
    }

    /**
     * Advances to the next task.
     * @return true if there is a next task, false if the quest is complete
     */
    public boolean advanceToNextTask() {
        if (currentTaskIndex < tasks.size() - 1) {
            QuestTask current = getCurrentTask();
            if (current != null) {
                current.setCompleted(true);
            }
            currentTaskIndex++;
            return true;
        } else if (currentTaskIndex == tasks.size() - 1) {
            QuestTask current = getCurrentTask();
            if (current != null) {
                current.setCompleted(true);
            }
        }
        return false;
    }

    /**
     * Calculates completion percentage.
     */
    public float getCompletionPercentage() {
        if (tasks.isEmpty()) return 0f;
        int completed = 0;
        for (QuestTask task : tasks) {
            if (task.isCompleted()) completed++;
        }
        return (float) completed / tasks.size() * 100f;
    }

    /**
     * Checks if the quest is complete.
     */
    public boolean isComplete() {
        for (QuestTask task : tasks) {
            if (!task.isCompleted()) return false;
        }
        return !tasks.isEmpty();
    }

    public String getQuestNote() {
        return questNote;
    }

    public void setQuestNote(String note) {
        this.questNote = note != null ? note : "";
    }

    public boolean hasQuestNote() {
        return questNote != null && !questNote.isEmpty();
    }

    /**
     * Returns a progress summary (e.g., "2/5 tasks").
     */
    public String getProgressSummary() {
        if (questType == QuestType.ENDURANCE) {
            if (endlessMode) {
                return String.format("Wave %d | %d kills", currentWave + 1, totalKills);
            }
            return String.format("Wave %d/%d | %d kills", currentWave + 1, totalWaves, totalKills);
        }
        int completed = 0;
        for (QuestTask task : tasks) {
            if (task.isCompleted()) completed++;
        }
        return String.format("%d/%d tasks", completed, tasks.size());
    }

    // === Endurance Quest Methods ===

    /**
     * Creates an Endurance Quest.
     */
    public static QuestData createEnduranceQuest(String mobId, String mobDisplayName, int waves, boolean endless) {
        String id = "endurance_" + mobId.replace(":", "_") + "_" + System.currentTimeMillis();
        String name = "Endurance: " + mobDisplayName;
        QuestData quest = new QuestData(id, name);
        quest.questType = QuestType.ENDURANCE;
        quest.targetMobId = mobId;
        quest.totalWaves = waves;
        quest.endlessMode = endless;
        quest.currentWave = 0;
        quest.totalKills = 0;
        quest.totalPoints = 0;
        quest.questActive = false;

        // Create initial tasks for tracking with clear instructions
        QuestTask prepareTask = new QuestTask("prepare", "Equip armor & weapons, then press F11 to start!");
        prepareTask.setNote("Tip: Have food and healing items ready. The quest begins when you press F11.");
        quest.addTask(prepareTask);
        quest.addTask(new QuestTask("wave_1", "Survive Wave 1"));
        if (!endless) {
            for (int i = 2; i <= Math.min(waves, 5); i++) {
                quest.addTask(new QuestTask("wave_" + i, "Survive Wave " + i));
            }
            if (waves > 5) {
                quest.addTask(new QuestTask("final", "Complete all " + waves + " waves"));
            }
        } else {
            quest.addTask(new QuestTask("endless", "Survive as long as possible"));
        }

        return quest;
    }

    public QuestType getQuestType() {
        return questType;
    }

    public boolean isEnduranceQuest() {
        return questType == QuestType.ENDURANCE;
    }

    public String getTargetMobId() {
        return targetMobId;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void setCurrentWave(int wave) {
        this.currentWave = wave;
    }

    public void incrementWave() {
        this.currentWave++;
    }

    public boolean isEndlessMode() {
        return endlessMode;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void addKill() {
        this.totalKills++;
    }

    public void addKills(int count) {
        this.totalKills += count;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void addPoints(int points) {
        this.totalPoints += points;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void startQuest() {
        this.startTimeMillis = System.currentTimeMillis();
        this.questActive = true;
    }

    public void stopQuest() {
        this.questActive = false;
    }

    public boolean isQuestActive() {
        return questActive;
    }

    /**
     * Returns elapsed time in seconds since quest started.
     */
    public long getElapsedSeconds() {
        if (startTimeMillis == 0) return 0;
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    /**
     * Formats elapsed time as MM:SS.
     */
    public String getElapsedTimeFormatted() {
        long seconds = getElapsedSeconds();
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}

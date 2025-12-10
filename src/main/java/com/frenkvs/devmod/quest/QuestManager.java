package com.frenkvs.devmod.quest;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for quests.
 * Manages quest state and persistence.
 */
public class QuestManager {
    public static final QuestManager INSTANCE = new QuestManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<QuestData> quests = new ArrayList<>();
    private QuestData activeQuest;
    private boolean dirty = false;

    // Listeners to notify changes (for UI sync)
    private final List<Runnable> changeListeners = new ArrayList<>();

    private QuestManager() {}

    /**
     * Adds a quest.
     */
    public void addQuest(QuestData quest) {
        quests.add(quest);
        if (activeQuest == null) {
            activeQuest = quest;
        }
        markDirty();
        notifyListeners();
    }

    /**
     * Removes a quest.
     */
    public void removeQuest(String questId) {
        quests.removeIf(q -> q.getId().equals(questId));
        if (activeQuest != null && activeQuest.getId().equals(questId)) {
            activeQuest = quests.isEmpty() ? null : quests.get(0);
        }
        markDirty();
        notifyListeners();
    }

    /**
     * Returns the active quest.
     */
    public QuestData getActiveQuest() {
        return activeQuest;
    }

    /**
     * Sets the active quest.
     */
    public void setActiveQuest(QuestData quest) {
        if (quests.contains(quest)) {
            this.activeQuest = quest;
            notifyListeners();
        }
    }

    /**
     * Sets the active quest by ID.
     */
    public void setActiveQuestById(String questId) {
        for (QuestData q : quests) {
            if (q.getId().equals(questId)) {
                this.activeQuest = q;
                notifyListeners();
                return;
            }
        }
    }

    /**
     * Returns all quests.
     */
    public List<QuestData> getAllQuests() {
        return new ArrayList<>(quests);
    }

    /**
     * Returns the current task of the active quest.
     */
    public QuestTask getCurrentTask() {
        return activeQuest != null ? activeQuest.getCurrentTask() : null;
    }

    /**
     * Completes the current task and advances to the next one.
     */
    public void completeCurrentTask() {
        if (activeQuest != null) {
            activeQuest.advanceToNextTask();
            markDirty();
            notifyListeners();
        }
    }

    /**
     * Sets a note on the current task.
     */
    public void setCurrentTaskNote(String note) {
        QuestTask task = getCurrentTask();
        if (task != null) {
            task.setNote(note);
            markDirty();
            notifyListeners();
        }
    }

    /**
     * Sets a note on the active quest.
     */
    public void setActiveQuestNote(String note) {
        if (activeQuest != null) {
            activeQuest.setQuestNote(note);
            markDirty();
            notifyListeners();
        }
    }

    /**
     * Returns the current task's note.
     */
    public String getCurrentTaskNote() {
        QuestTask task = getCurrentTask();
        return task != null ? task.getNote() : "";
    }

    // === Persistence ===

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Saves quests to disk.
     */
    public void save() {
        if (!dirty) return;

        Path file = ConfigPaths.getQuestDataFile();
        try {
            Files.createDirectories(file.getParent());

            QuestSaveData saveData = new QuestSaveData();
            saveData.quests = quests;
            saveData.activeQuestId = activeQuest != null ? activeQuest.getId() : null;

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(saveData, writer);
            }

            dirty = false;
            LOGGER.info("[DevMod] Quest data saved to {}", file);
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to save quest data", e);
        }
    }

    /**
     * Loads quests from disk.
     */
    public void load() {
        Path file = ConfigPaths.getQuestDataFile();

        if (!Files.exists(file)) {
            LOGGER.info("[DevMod] No quest data file found, starting fresh");
            createDemoQuest();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            QuestSaveData loaded = GSON.fromJson(reader, QuestSaveData.class);

            if (loaded != null && loaded.quests != null) {
                quests.clear();
                quests.addAll(loaded.quests);

                if (loaded.activeQuestId != null) {
                    setActiveQuestById(loaded.activeQuestId);
                } else if (!quests.isEmpty()) {
                    activeQuest = quests.get(0);
                }

                LOGGER.info("[DevMod] Loaded {} quests", quests.size());
            }
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to load quest data", e);
            createDemoQuest();
        }

        dirty = false;
    }

    /**
     * Creates a demo quest for testing.
     */
    private void createDemoQuest() {
        QuestData demo = new QuestData("demo_quest", "Quest Tutorial");
        demo.addTask(new QuestTask("task_1", "Open the DevMod menu (press M)"));
        demo.addTask(new QuestTask("task_2", "Explore debug settings"));
        demo.addTask(new QuestTask("task_3", "Test a weapon on a mob"));
        addQuest(demo);
        LOGGER.info("[DevMod] Created demo quest");
    }

    /**
     * Clears all quests - used by global reset.
     */
    public void clearAllQuests() {
        quests.clear();
        activeQuest = null;
        markDirty();
        notifyListeners();
        LOGGER.info("[DevMod] All quests cleared");
    }

    // === Listeners ===

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.error("[DevMod] Error notifying quest listener", e);
            }
        }
    }

    // === Internal class for serialization ===

    private static class QuestSaveData {
        List<QuestData> quests;
        String activeQuestId;
    }
}

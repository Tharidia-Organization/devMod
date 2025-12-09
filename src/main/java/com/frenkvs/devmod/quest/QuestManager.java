package com.frenkvs.devmod.quest;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager per le quest.
 * Gestisce lo stato delle quest e la persistenza.
 */
public class QuestManager {
    public static final QuestManager INSTANCE = new QuestManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<QuestData> quests = new ArrayList<>();
    private QuestData activeQuest;
    private boolean dirty = false;

    // Listeners per notificare cambiamenti (per UI sync)
    private final List<Runnable> changeListeners = new ArrayList<>();

    private QuestManager() {}

    /**
     * Aggiunge una quest.
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
     * Rimuove una quest.
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
     * Restituisce la quest attiva.
     */
    public QuestData getActiveQuest() {
        return activeQuest;
    }

    /**
     * Imposta la quest attiva.
     */
    public void setActiveQuest(QuestData quest) {
        if (quests.contains(quest)) {
            this.activeQuest = quest;
            notifyListeners();
        }
    }

    /**
     * Imposta la quest attiva per ID.
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
     * Restituisce tutte le quest.
     */
    public List<QuestData> getAllQuests() {
        return new ArrayList<>(quests);
    }

    /**
     * Restituisce la task corrente della quest attiva.
     */
    public QuestTask getCurrentTask() {
        return activeQuest != null ? activeQuest.getCurrentTask() : null;
    }

    /**
     * Completa la task corrente e avanza alla prossima.
     */
    public void completeCurrentTask() {
        if (activeQuest != null) {
            activeQuest.advanceToNextTask();
            markDirty();
            notifyListeners();
        }
    }

    /**
     * Imposta una nota sulla task corrente.
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
     * Imposta una nota sulla quest attiva.
     */
    public void setActiveQuestNote(String note) {
        if (activeQuest != null) {
            activeQuest.setQuestNote(note);
            markDirty();
            notifyListeners();
        }
    }

    /**
     * Restituisce la nota della task corrente.
     */
    public String getCurrentTaskNote() {
        QuestTask task = getCurrentTask();
        return task != null ? task.getNote() : "";
    }

    // === Persistenza ===

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Salva le quest su disco.
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
     * Carica le quest da disco.
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
     * Crea una quest demo per test.
     */
    private void createDemoQuest() {
        QuestData demo = new QuestData("demo_quest", "Quest Tutorial");
        demo.addTask(new QuestTask("task_1", "Apri il menu DevMod (premi M)"));
        demo.addTask(new QuestTask("task_2", "Esplora le impostazioni debug"));
        demo.addTask(new QuestTask("task_3", "Testa un'arma su un mob"));
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

    // === Classe interna per serializzazione ===

    private static class QuestSaveData {
        List<QuestData> quests;
        String activeQuestId;
    }
}

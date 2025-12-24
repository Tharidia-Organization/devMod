package com.devmod.quest;

/**
 * Rappresenta una singola task di una quest.
 */
public class QuestTask {
    private final String id;
    private final String description;
    private boolean completed;
    private String note;

    public QuestTask(String id, String description) {
        this.id = id;
        this.description = description;
        this.completed = false;
        this.note = "";
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note != null ? note : "";
    }

    public boolean hasNote() {
        return note != null && !note.isEmpty();
    }
}

package com.devmod.quest;

/**
 * Lifecycle status of a quest.
 */
public enum QuestStatus {
    NOT_STARTED,
    ACTIVE,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}

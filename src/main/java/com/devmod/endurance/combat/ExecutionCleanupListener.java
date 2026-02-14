package com.devmod.endurance.combat;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.combat.ExecutionSystem;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;

/**
 * Listener that cleans up {@link ExecutionSystem} state when a quest ends.
 * <p>
 * This replaces the previous direct {@code QuestLifecycleListener} implementation
 * on ExecutionSystem, keeping the combat module free of endurance-lifecycle imports.
 */
public final class ExecutionCleanupListener implements QuestLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionCleanupListener.class);

    public static final ExecutionCleanupListener INSTANCE = new ExecutionCleanupListener();

    private ExecutionCleanupListener() {}

    @Override
    public void onQuestEnded(QuestEnded event) {
        UUID playerId = event.context().playerId();
        ExecutionSystem.INSTANCE.onPlayerLeave(playerId);
        LOGGER.debug("[ExecutionCleanupListener] Cleaned up ExecutionSystem state for player {} via event bus", playerId);
    }

    @Override
    public int getPriority() {
        return 50; // Low priority - cleanup after combat systems (same as before)
    }

    @Override
    public String getListenerName() {
        return "ExecutionCleanupListener";
    }
}

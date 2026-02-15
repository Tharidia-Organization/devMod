package com.devmod.endurance.combat;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.combat.bridge.CombatEnduranceBridge;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;

/**
 * Listener that cleans up execution state when a quest ends.
 * <p>
 * Delegates to {@link CombatEnduranceBridge} so that the endurance module
 * has no direct compile-time dependency on {@code ExecutionSystem}.
 */
public final class ExecutionCleanupListener implements QuestLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionCleanupListener.class);

    public static final ExecutionCleanupListener INSTANCE = new ExecutionCleanupListener();

    private ExecutionCleanupListener() {}

    @Override
    public void onQuestEnded(QuestEnded event) {
        UUID playerId = event.context().playerId();
        CombatEnduranceBridge.get().onPlayerLeave(playerId);
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

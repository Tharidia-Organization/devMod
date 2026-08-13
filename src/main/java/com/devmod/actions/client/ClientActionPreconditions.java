package com.devmod.actions.client;

import java.util.Map;

import com.devmod.actions.ActionPrecondition;

/**
 * The named preconditions that only exist on the client, keyed by the
 * {@code preconditionRef} strings the V2 domain registrars declare.
 *
 * <p>Separate from the common registry because every value here closes over
 * client-only state (the quest cache, the screen stack, the combat detector).
 * Loading this class on a dedicated server would drag those in, so the caller
 * must gate it on the client distribution.
 *
 * <p>Each entry delegates to the same helper the V1 registration uses, so a
 * shadow-mode comparison of gating decisions is meaningful.
 */
public final class ClientActionPreconditions {

    private ClientActionPreconditions() {}

    /**
     * Returns the ref-to-precondition mappings available on the client.
     */
    public static Map<String, ActionPrecondition> all() {
        return Map.ofEntries(
            Map.entry("screenPrecondition", ClientUIActions.screenPrecondition()),
            Map.entry("uiScreenPrecondition", ClientUIActions.uiScreenPrecondition()),
            Map.entry("qaSessionActivePrecondition",
                ClientUIActions.qaSessionActivePrecondition()),
            Map.entry("qaSessionExistsPrecondition",
                ClientUIActions.qaSessionExistsPrecondition()),
            Map.entry("qaActiveTestPrecondition", ClientUIActions.qaActiveTestPrecondition()),
            Map.entry("qaAutoTestPrecondition", ClientUIActions.qaAutoTestPrecondition()),
            Map.entry("developerModePrecondition", ClientUIActions.developerModePrecondition()),
            Map.entry("testerPrecondition", ClientUIActions.testerPrecondition()),
            Map.entry("partyInvitePrecondition", ClientUIActions.partyInvitePrecondition()),
            Map.entry("perkSelectionPrecondition", ClientUIActions.perkSelectionPrecondition()),
            Map.entry("questCompletionPrecondition",
                ClientUIActions.questCompletionPrecondition()),
            Map.entry("questDeathPrecondition", ClientUIActions.questDeathPrecondition()),
            Map.entry("onboardingActivePrecondition",
                ClientUIActions.onboardingActivePrecondition()),
            Map.entry("activeQuestPrecondition",
                ClientGameplayActions.activeQuestPrecondition()),
            Map.entry("clientOnly_activeTask", ClientGameplayActions.activeTaskPrecondition()),
            Map.entry("clientOnly_activeQuest", ClientGameplayActions.activeQuestPrecondition()),
            Map.entry("clientOnly_respawnOrCheckpoint",
                ClientGameplayActions.respawnOrCheckpointPrecondition())
        );
    }
}

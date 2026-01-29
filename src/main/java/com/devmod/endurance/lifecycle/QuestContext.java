package com.devmod.endurance.lifecycle;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.endurance.ArenaContext;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.EnduranceQuestManager.ActiveQuestSession;
import com.devmod.endurance.QuestType;

/**
 * Immutable context for quest lifecycle events.
 * Contains all information needed by listeners to handle quest start/end.
 *
 * <p>This record encapsulates the quest state at a point in time,
 * providing a clean contract for event listeners without exposing
 * mutable internal state.</p>
 */
public record QuestContext(
    UUID questId,
    UUID playerId,
    ServerPlayer player,
    EnduranceQuest quest,
    ActiveQuestSession session,
    @Nullable ArenaContext arena,
    @Nullable ArenaPolicy policy,
    QuestType questType,
    boolean practiceMode,
    @Nullable UUID partyId,
    @Nullable UUID instanceId,
    String templateId,
    @Nullable Integer templateVersion,
    @Nullable String policyId,
    @Nullable Integer policyVersion
) {
    /**
     * Creates a QuestContext from an active session.
     *
     * @param player The player in the quest
     * @param session The active quest session
     * @param policy The arena policy (may be null)
     * @return A new QuestContext
     */
    public static QuestContext from(ServerPlayer player, ActiveQuestSession session, @Nullable ArenaPolicy policy) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(session, "session cannot be null");

        EnduranceQuest quest = session.getQuest();

        return new QuestContext(
            quest.getQuestId(),
            player.getUUID(),
            player,
            quest,
            session,
            session.getArena(),
            policy,
            session.getQuestType(),
            session.isPracticeMode(),
            session.getPartyId(),
            session.getInstanceId(),
            session.getTemplateId(),
            session.getTemplateVersion(),
            session.getPolicyId(),
            session.getPolicyVersion()
        );
    }

    /**
     * Returns the player count in this quest session.
     */
    public int getPlayerCount() {
        return session.getPlayerCount();
    }

    /**
     * Returns true if this is a party quest.
     */
    public boolean isPartyQuest() {
        return partyId != null;
    }

    /**
     * Returns true if this quest uses an instance dimension.
     */
    public boolean usesInstanceDimension() {
        return instanceId != null;
    }

    /**
     * Returns the display name of the quest.
     */
    public String getDisplayName() {
        return quest.getDisplayName();
    }

    /**
     * Returns the total number of waves.
     */
    public int getTotalWaves() {
        return quest.getTotalWaves();
    }

    /**
     * Returns true if this is an endless mode quest.
     */
    public boolean isEndlessMode() {
        return quest.isEndlessMode();
    }
}

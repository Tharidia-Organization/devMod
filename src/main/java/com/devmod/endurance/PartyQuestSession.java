package com.devmod.endurance;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.devmod.endurance.QuestType;

/**
 * Shared party-run session for Endurance quests.
 */
public class PartyQuestSession {
    public enum Status {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final UUID partyId;
    private final UUID questId;
    private final EnduranceQuest quest;
    private final UUID arenaId;
    private final UUID instanceId;
    private final QuestType questType;
    private final Set<UUID> members;
    private final Set<UUID> activeMembers;
    private final Set<UUID> spectators;
    private final long startTime;
    private volatile Status status;
    private volatile long endTime;

    public PartyQuestSession(UUID partyId,
                             EnduranceQuest quest,
                             UUID arenaId,
                             UUID instanceId,
                             QuestType questType,
                             Collection<UUID> members) {
        this.partyId = partyId;
        this.quest = quest;
        this.questId = quest.getQuestId();
        this.arenaId = arenaId;
        this.instanceId = instanceId;
        this.questType = questType;
        this.members = ConcurrentHashMap.newKeySet();
        this.activeMembers = ConcurrentHashMap.newKeySet();
        this.spectators = ConcurrentHashMap.newKeySet();
        if (members != null) {
            this.members.addAll(members);
            this.activeMembers.addAll(members);
        }
        this.startTime = System.currentTimeMillis();
        this.status = Status.ACTIVE;
        this.endTime = 0L;
    }

    public UUID getPartyId() { return partyId; }
    public UUID getQuestId() { return questId; }
    public EnduranceQuest getQuest() { return quest; }
    public UUID getArenaId() { return arenaId; }
    public UUID getInstanceId() { return instanceId; }
    public QuestType getQuestType() { return questType; }
    public Optional<WaveManager.WaveState> getWaveState() {
        if (arenaId == null) {
            return Optional.empty();
        }
        return WaveManager.INSTANCE.getWaveState(arenaId);
    }
    public Optional<MutatorSystem.MutatorSession> getMutatorSession() {
        return MutatorSystem.INSTANCE.getSession(questId);
    }
    public TensionSystem.TensionInfo getTensionInfo() {
        return TensionSystem.INSTANCE.getTensionInfo(questId);
    }
    public Set<UUID> getMembers() { return Set.copyOf(members); }
    public Set<UUID> getActiveMembers() { return Set.copyOf(activeMembers); }
    public Set<UUID> getSpectators() { return Set.copyOf(spectators); }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public Status getStatus() { return status; }

    public boolean isActive() { return status == Status.ACTIVE; }

    public void markSpectator(UUID memberId) {
        if (memberId == null) return;
        activeMembers.remove(memberId);
        spectators.add(memberId);
    }

    public void markActive(UUID memberId) {
        if (memberId == null) return;
        spectators.remove(memberId);
        activeMembers.add(memberId);
        members.add(memberId);
    }

    public boolean isWiped() {
        return activeMembers.isEmpty();
    }

    public void end(Status newStatus) {
        if (status != Status.ACTIVE) {
            return;
        }
        status = newStatus != null ? newStatus : Status.CANCELLED;
        endTime = System.currentTimeMillis();
    }
}

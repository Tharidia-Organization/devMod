package com.devmod.endurance;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.devmod.arena.api.ArenaHandle;

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
    private final ArenaContext arena;
    private final ArenaHandle arenaHandle;
    private final UUID instanceId;
    private final QuestType questType;
    private final Set<UUID> members;
    private final Set<UUID> activeMembers;
    private final Set<UUID> spectators;
    private final Set<UUID> waveReadyMembers;
    private final AtomicInteger lastAdvancedWave;
    private final AtomicInteger lastCountdownWave;
    private final long startTime;
    private final AtomicReference<Status> status;
    private volatile long endTime;

    public PartyQuestSession(UUID partyId,
                             EnduranceQuest quest,
                             UUID arenaId,
                             ArenaContext arena,
                             ArenaHandle arenaHandle,
                             UUID instanceId,
                             QuestType questType,
                             Collection<UUID> members) {
        this.partyId = partyId;
        this.quest = quest;
        this.questId = quest.getQuestId();
        this.arenaId = arenaId;
        this.arena = arena;
        this.arenaHandle = arenaHandle;
        this.instanceId = instanceId;
        this.questType = questType;
        this.members = ConcurrentHashMap.newKeySet();
        this.activeMembers = ConcurrentHashMap.newKeySet();
        this.spectators = ConcurrentHashMap.newKeySet();
        this.waveReadyMembers = ConcurrentHashMap.newKeySet();
        this.lastAdvancedWave = new AtomicInteger(0);
        this.lastCountdownWave = new AtomicInteger(0);
        if (members != null) {
            this.members.addAll(members);
            this.activeMembers.addAll(members);
        }
        this.startTime = System.currentTimeMillis();
        this.status = new AtomicReference<>(Status.ACTIVE);
        this.endTime = 0L;
    }

    public UUID getPartyId() { return partyId; }
    public UUID getQuestId() { return questId; }
    public EnduranceQuest getQuest() { return quest; }
    public UUID getArenaId() { return arenaId; }
    public ArenaContext getArena() { return arena; }
    public ArenaHandle getArenaHandle() { return arenaHandle; }
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
    public Set<UUID> getWaveReadyMembers() { return Set.copyOf(waveReadyMembers); }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public Status getStatus() { return status.get(); }

    public boolean isActive() { return status.get() == Status.ACTIVE; }
    public boolean isSpectator(UUID memberId) {
        return memberId != null && spectators.contains(memberId);
    }
    public boolean isActiveMember(UUID memberId) {
        return memberId != null && activeMembers.contains(memberId);
    }
    public int getActiveMemberCount() { return activeMembers.size(); }

    public void markSpectator(UUID memberId) {
        if (memberId == null) return;
        activeMembers.remove(memberId);
        spectators.add(memberId);
        waveReadyMembers.remove(memberId);
    }

    public void markActive(UUID memberId) {
        if (memberId == null) return;
        spectators.remove(memberId);
        activeMembers.add(memberId);
        members.add(memberId);
        waveReadyMembers.remove(memberId);
    }

    public void markWaveReady(UUID memberId) {
        if (memberId == null) return;
        if (activeMembers.contains(memberId)) {
            waveReadyMembers.add(memberId);
        }
    }

    public boolean isWaveReady(UUID memberId) {
        return memberId != null && waveReadyMembers.contains(memberId);
    }

    public boolean isReadyForNextWave() {
        if (activeMembers.isEmpty()) {
            return false;
        }
        return waveReadyMembers.containsAll(activeMembers);
    }

    public void clearWaveReady() {
        waveReadyMembers.clear();
    }

    public boolean markWaveAdvance(int waveNumber) {
        if (waveNumber <= 0) {
            return false;
        }
        int prev = lastAdvancedWave.get();
        while (prev < waveNumber) {
            if (lastAdvancedWave.compareAndSet(prev, waveNumber)) {
                return true;
            }
            prev = lastAdvancedWave.get();
        }
        return false;
    }

    public boolean markCountdownWave(int waveNumber) {
        if (waveNumber <= 0) {
            return false;
        }
        int prev = lastCountdownWave.get();
        while (prev < waveNumber) {
            if (lastCountdownWave.compareAndSet(prev, waveNumber)) {
                return true;
            }
            prev = lastCountdownWave.get();
        }
        return false;
    }

    public boolean isWiped() {
        return activeMembers.isEmpty();
    }

    /**
     * Atomically end the session if it's currently active.
     * Uses compare-and-set to prevent race conditions where multiple threads
     * try to end the session simultaneously with different statuses.
     *
     * @param newStatus The status to set (defaults to CANCELLED if null)
     * @return true if this call successfully ended the session, false if already ended
     */
    public boolean end(Status newStatus) {
        Status targetStatus = newStatus != null ? newStatus : Status.CANCELLED;
        // Atomic compare-and-set: only succeeds if currently ACTIVE
        if (status.compareAndSet(Status.ACTIVE, targetStatus)) {
            endTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }
}

package com.devmod.endurance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.TemplateSpawnValidator;
import com.devmod.debug.DiagnosticLogger;
import com.devmod.endurance.EnduranceLogger.Phase;
import com.devmod.shared.SharedColorTokens;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public class WaveManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveManager.class);

    public static final WaveManager INSTANCE = new WaveManager();

    private final Random random = new Random();

    private static final int MODIFIER_START_WAVE = 3;
    private static final int MAX_MODIFIERS_EARLY = 1;
    private static final int MAX_MODIFIERS_MID = 2;
    private static final int MAX_MODIFIERS_LATE = 3;
    private static final float MODIFIER_CHANCE_TIER_1 = 0.10f;
    private static final float MODIFIER_CHANCE_TIER_2 = 0.20f;
    private static final float MODIFIER_CHANCE_TIER_3 = 0.35f;

    // Active wave states per arena (ConcurrentHashMap for thread-safety in multiplayer)
    private final Map<UUID, WaveState> activeWaves = new java.util.concurrent.ConcurrentHashMap<>();

    private final WaveMobSpawner mobSpawner = WaveMobSpawner.INSTANCE;
    private final WaveSpawnPositionResolver positionResolver = WaveSpawnPositionResolver.INSTANCE;

    private WaveManager() {}

    /**
     * State of an active wave.
     */
    public static class WaveState {
        // Note: arenaId parameter kept in constructor for API stability but field removed
        // The activeWaves map key serves as the authoritative arenaId
        private final EnduranceQuest quest;
        private final int waveNumber;
        private final List<UUID> spawnedMobs = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Map<UUID, SpawnAffix> spawnAffixes = new java.util.concurrent.ConcurrentHashMap<>();
        private final Set<UUID> objectiveTargets = java.util.Collections.synchronizedSet(new HashSet<>());
        private final WaveObjectiveState objective;
        private final List<WaveDirector.SpawnBatch> spawnPlan;
        private final @javax.annotation.Nullable SpawnContext spawnContext;
        private final float rewardMultiplier;
        private final @javax.annotation.Nullable String directiveId;
        private volatile int totalToSpawn;
        private volatile int spawned = 0;
        private volatile int killed = 0;
        private volatile int practiceDummyCounter = 0;
        private volatile long waveStartTime;
        private volatile int waveTicks = 0;
        private volatile int nextSpawnIndex = 0;
        private volatile boolean complete = false;
        private volatile boolean completionNotified = false;
        private volatile int externalRespawnCount = 0;
        private final int externalRespawnLimit;

        // Wave modifiers (roguelike elements) - thread-safe for concurrent access
        private final Set<WaveModifier> modifiers = java.util.concurrent.ConcurrentHashMap.newKeySet();

        // Multiplayer scaling parameters
        private final int playerCount;
        private final QuestType questType;

        // Practice mode (uses training dummies instead of real mobs)
        private final boolean practiceMode;

        // Session reference for config overrides
        private final @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session;

        public WaveState(UUID arenaId,
                         EnduranceQuest quest,
                         int waveNumber,
                         int playerCount,
                         QuestType questType,
                         int totalToSpawn,
                         WaveObjectiveState objective,
                         List<WaveDirector.SpawnBatch> spawnPlan,
                         @javax.annotation.Nullable SpawnContext spawnContext,
                         float rewardMultiplier,
                         @javax.annotation.Nullable String directiveId,
                         boolean practiceMode,
                         @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
            // arenaId parameter intentionally unused - the map key is the authoritative source
            this.quest = quest;
            this.waveNumber = waveNumber;
            this.playerCount = Math.max(1, playerCount);
            this.questType = questType;
            this.totalToSpawn = Math.max(1, totalToSpawn);
            this.objective = objective != null ? objective : WaveObjectiveState.killAll(this.totalToSpawn);
            this.spawnPlan = spawnPlan != null ? List.copyOf(spawnPlan) : List.of();
            this.spawnContext = spawnContext;
            this.rewardMultiplier = Math.max(0.1f, rewardMultiplier);
            this.directiveId = directiveId;
            this.practiceMode = practiceMode;
            this.session = session;
            this.waveStartTime = System.currentTimeMillis();
            this.externalRespawnLimit = Math.max(5, this.totalToSpawn);
        }

        /**
         * Legacy constructor without session (for backward compatibility).
         */
        public WaveState(UUID arenaId,
                         EnduranceQuest quest,
                         int waveNumber,
                         int playerCount,
                         QuestType questType,
                         int totalToSpawn,
                         WaveObjectiveState objective,
                         List<WaveDirector.SpawnBatch> spawnPlan,
                         @javax.annotation.Nullable SpawnContext spawnContext,
                         float rewardMultiplier,
                         @javax.annotation.Nullable String directiveId,
                         boolean practiceMode) {
            this(arenaId, quest, waveNumber, playerCount, questType, totalToSpawn, objective,
                 spawnPlan, spawnContext, rewardMultiplier, directiveId, practiceMode, null);
        }

        public WaveState(UUID arenaId, EnduranceQuest quest, int waveNumber) {
            this(
                arenaId,
                quest,
                waveNumber,
                1,
                QuestType.PVE_COOP,
                Math.max(1, quest.getCurrentWaveMobCount()),
                WaveObjectiveState.killAll(Math.max(1, quest.getCurrentWaveMobCount())),
                List.of(),
                null,
                1.0f,
                null,
                false
            );
        }

        public EnduranceQuest getQuest() { return quest; }
        public int getWaveNumber() { return waveNumber; }
        public List<UUID> getSpawnedMobs() { return spawnedMobs; }
        public WaveObjectiveState getObjective() { return objective; }
        public float getRewardMultiplier() { return rewardMultiplier; }
        public @javax.annotation.Nullable String getDirectiveId() { return directiveId; }
        public boolean isPracticeMode() { return practiceMode; }
        public int getTotalToSpawn() { return totalToSpawn; }
        public int getSpawned() { return spawned; }
        public int getKilled() { return killed; }
        public long getWaveStartTime() { return waveStartTime; }
        public int getWaveTicks() { return waveTicks; }
        public boolean isComplete() { return complete; }
        public boolean isCompletionNotified() { return completionNotified; }
        public Set<WaveModifier> getModifiers() { return modifiers; }
        public int getPlayerCount() { return playerCount; }
        public QuestType getQuestType() { return questType; }
        public int getExternalRespawnCount() { return externalRespawnCount; }
        public int getExternalRespawnLimit() { return externalRespawnLimit; }
        public List<WaveDirector.SpawnBatch> getSpawnPlan() { return spawnPlan; }
        public int getNextSpawnIndex() { return nextSpawnIndex; }
        public @javax.annotation.Nullable SpawnContext getSpawnContext() { return spawnContext; }
        public @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession getSession() { return session; }

        /**
         * Records a kill for this wave.
         * Thread-safe: Synchronized to prevent race conditions during kill counting.
         */
        public synchronized void recordKill(UUID mobId) {
            killed++;
            objective.recordKill();

            LOGGER.info("[WaveManager] Kill recorded: mob={}, killed={}/{}, spawned={}/{}, pending={}, objComplete={}",
                mobId, killed, totalToSpawn, spawned, totalToSpawn, hasPendingSpawns(), objective.isComplete());

            if (objective.getType() == WaveObjectiveState.Type.ELITE_HUNT) {
                objective.recordObjectiveKill(mobId);
            }
            if (objective.isComplete() && objective.getType() != WaveObjectiveState.Type.KILL_ALL) {
                complete = true;
                LOGGER.info("[WaveManager] Wave {} marked complete (non-KILL_ALL objective)", waveNumber);
            }
            if (objective.getType() == WaveObjectiveState.Type.KILL_ALL
                && objective.isComplete()
                && spawned >= totalToSpawn
                && !hasPendingSpawns()) {
                complete = true;
                LOGGER.info("[WaveManager] Wave {} marked complete (KILL_ALL: killed={}, totalToSpawn={})",
                    waveNumber, killed, totalToSpawn);
            }
        }

        /**
         * Adds a spawned mob to this wave's tracking.
         * Thread-safe: Synchronized to prevent race conditions during spawn counting.
         */
        public synchronized void addSpawnedMob(UUID mobId, SpawnAffix affix, boolean objectiveTarget) {
            spawnedMobs.add(mobId);
            if (affix != null) {
                spawnAffixes.put(mobId, affix);
            }
            if (objectiveTarget && mobId != null) {
                objectiveTargets.add(mobId);
                objective.registerObjectiveTarget(mobId);
            }
            spawned++;

            LOGGER.info("[WaveManager] Mob added to tracking: mobId={}, wave={}, spawned={}/{}, affix={}",
                mobId, waveNumber, spawned, totalToSpawn, affix);
        }

        /**
         * Thread-safe: Synchronized for atomic counter increment.
         */
        public synchronized int nextPracticeDummyIndex() {
            return practiceDummyCounter++;
        }

        /**
         * Thread-safe: Synchronized for atomic counter increment.
         */
        public synchronized void incrementWaveTicks() {
            waveTicks++;
        }

        /**
         * Thread-safe: Synchronized to prevent concurrent index updates.
         */
        public synchronized void advanceSpawnIndex(int index) {
            nextSpawnIndex = index;
        }

        public boolean hasPendingSpawns() {
            return nextSpawnIndex < spawnPlan.size();
        }

        /**
         * Thread-safe: Synchronized for flag consistency.
         */
        public synchronized void markComplete() {
            complete = true;
        }

        /**
         * Thread-safe: Synchronized for flag consistency.
         */
        public synchronized void markCompletionNotified() {
            completionNotified = true;
        }

        public SpawnAffix getAffixForMob(UUID mobId) {
            return spawnAffixes.getOrDefault(mobId, SpawnAffix.BASE);
        }

        public boolean isObjectiveTarget(UUID mobId) {
            return mobId != null && objectiveTargets.contains(mobId);
        }

        /**
         * Thread-safe: Synchronized for atomic remove+add operation on objective targets.
         */
        public synchronized boolean replaceObjectiveTarget(UUID oldId, UUID newId) {
            if (oldId == null || newId == null) {
                return false;
            }
            if (objectiveTargets.remove(oldId)) {
                objectiveTargets.add(newId);
                objective.registerObjectiveTarget(newId);
                return true;
            }
            return false;
        }

        /**
         * Thread-safe: Synchronized to prevent concurrent target adjustment.
         */
        public synchronized void adjustKillTarget(int newTarget) {
            if (objective.getType() == WaveObjectiveState.Type.KILL_ALL) {
                objective.adjustKillTarget(newTarget);
            }
        }

        /**
         * Thread-safe: Synchronized for atomic read-modify-write of respawn counter.
         */
        public synchronized int registerExternalRespawn(int count) {
            int remaining = Math.max(0, externalRespawnLimit - externalRespawnCount);
            int allowed = Math.min(remaining, Math.max(0, count));
            externalRespawnCount += allowed;
            return allowed;
        }

        /**
         * Gets the zone layout for this wave, if available.
         */
        public @javax.annotation.Nullable com.devmod.arena.zone.ZoneLayout getZoneLayout() {
            final SpawnContext ctx = spawnContext;
            return ctx != null ? ctx.zoneLayout() : null;
        }

        /**
         * Checks if this wave has multi-zone layout.
         */
        public boolean hasZones() {
            final SpawnContext ctx = spawnContext;
            return ctx != null && ctx.hasZones();
        }

        // Package-private accessors for WaveMobSpawner

        synchronized void adjustTotalToSpawn(int newTotal) {
            this.totalToSpawn = newTotal;
        }

        void removeSpawnAffix(UUID mobId) {
            spawnAffixes.remove(mobId);
        }

        void putSpawnAffix(UUID mobId, SpawnAffix affix) {
            spawnAffixes.put(mobId, affix);
        }
    }

    /**
     * Roguelike wave modifiers that add variety.
     */
    public enum WaveModifier {
        SPEED_BOOST("Swift", "Mobs move 25% faster"),
        DAMAGE_BOOST("Empowered", "Mobs deal 25% more damage"),
        HEALTH_BOOST("Fortified", "Mobs have 50% more health"),
        ARMOR_BOOST("Armored", "Mobs have increased armor"),
        FIRE_ASPECT("Blazing", "Mobs inflict fire on hit"),
        INVISIBILITY("Phantom", "Mobs are partially invisible"),
        REGEN("Regenerating", "Mobs slowly regenerate health"),
        DOUBLE_SPAWN("Horde", "Double the number of mobs");

        private final String displayName;
        private final String description;

        WaveModifier(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // ========== Wave Lifecycle ==========

    /**
     * Start a new wave for a quest.
     */
    public WaveState startWave(EnduranceQuestManager.ActiveQuestSession session) {
        EnduranceQuest quest = session.getQuest();
        ArenaContext arena = session.getArena();
        ArenaHandle handle = session.getArenaHandle();
        int waveNumber = quest.getCurrentWave();

        int playerCount = session.getPlayerCount();
        QuestType questType = session.getQuestType();

        DiagnosticLogger.quest("startWave: wave=%d, players=%d, questType=%s, arenaId=%s",
            waveNumber, playerCount, questType, arena != null ? arena.getId() : "null");

        EnduranceLogger.wave(Phase.WAVE_START, null, quest.getQuestId(), waveNumber, quest.getTotalWaves(),
            "Starting wave with players=%d, questType=%s", playerCount, questType);

        if (arena == null || handle == null || handle.mobSpawnPositions() == null || handle.mobSpawnPositions().isEmpty()) {
            handleWaveStartFailure(session, "missing_handle_or_spawns");
            return null;
        }

        LOGGER.debug("[EnduranceQuest] Starting wave {} with playerCount={}, questType={}",
            waveNumber, playerCount, questType);

        // Check if this is a boss wave (using dynamic tension system)
        UUID questId = quest.getQuestId();
        boolean practice = session.isPracticeMode();
        boolean bossWaveCandidate = BossWaveSystem.INSTANCE.isBossWave(waveNumber, questId);
        boolean shouldBeBossWave = !practice && bossWaveCandidate;
        LOGGER.info("[BossDebug] WaveManager.startWave: wave={}, shouldBeBossWave={}", waveNumber, shouldBeBossWave);
        if (shouldBeBossWave) {
            WaveState bossState = startBossWave(session, arena, waveNumber, playerCount, questType, practice);
            if (bossState != null) {
                return bossState;
            }
            LOGGER.warn("[EnduranceQuest] Boss spawn failed for wave {}, falling back to normal wave", waveNumber);
        }

        Set<WaveModifier> modifiers = rollWaveModifiers(waveNumber);
        int baseCount = quest.getCurrentWaveMobCount(playerCount, questType, session);
        if (modifiers.contains(WaveModifier.DOUBLE_SPAWN)) {
            baseCount = Math.max(1, baseCount * 2);
        }
        baseCount = Math.max(1, baseCount);

        WaveDirective directive = session.consumeDirectiveForWave(waveNumber);
        float rewardMultiplier = directive != null ? directive.rewardMultiplier() : 1.0f;
        String directiveId = directive != null ? directive.id() : null;

        WaveDirector.WavePlan plan = WaveDirector.INSTANCE.planWave(session, baseCount, directive);
        SpawnContext spawnContext = positionResolver.buildSpawnContext(arena, handle);
        if (spawnContext == null) {
            handleWaveStartFailure(session, "missing_spawn_slots");
            return null;
        }
        WaveState waveState = new WaveState(
            arena.getId(), quest, waveNumber, playerCount, questType,
            plan.totalToSpawn(), plan.objective(), plan.batches(), spawnContext,
            rewardMultiplier, directiveId, session.isPracticeMode(), session
        );
        waveState.getModifiers().addAll(modifiers);

        activeWaves.put(arena.getId(), waveState);

        spawnDueBatches(waveState, arena, handle);

        LOGGER.info("[EnduranceQuest] Started wave {} with {} mobs (modifiers: {})",
            waveState.waveNumber, waveState.totalToSpawn, waveState.modifiers);

        if (!practice) {
            EnduranceTelemetryService.INSTANCE.recordWaveStart(
                quest.getQuestId(), waveNumber, waveState.totalToSpawn,
                playerCount, questType, waveState.modifiers
            );
        }

        return waveState;
    }

    private @javax.annotation.Nullable WaveState startBossWave(
            EnduranceQuestManager.ActiveQuestSession session,
            ArenaContext arena, int waveNumber, int playerCount,
            QuestType questType, boolean practice) {
        LOGGER.info("[BossDebug] Starting boss wave {} via BossWaveSystem", waveNumber);
        BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.startBossWave(session, waveNumber);
        LOGGER.info("[BossDebug] startBossWave result: bossFight={}, bossEntity={}",
            bossFight != null, bossFight != null ? bossFight.getBossEntity() : null);
        if (bossFight == null || bossFight.getBossEntity() == null) {
            return null;
        }

        EnduranceQuest quest = session.getQuest();
        WaveState waveState = new WaveState(
            arena.getId(), quest, waveNumber, playerCount, questType,
            1, WaveObjectiveState.killAll(1), List.of(), null,
            1.0f, null, session.isPracticeMode(), session
        );
        waveState.addSpawnedMob(bossFight.getBossEntity().getUUID(), SpawnAffix.ELITE, false);
        activeWaves.put(arena.getId(), waveState);

        LOGGER.info("[EnduranceQuest] Started BOSS wave {} with {} archetype",
            waveNumber, bossFight.getArchetype().getDisplayName());

        EnduranceTelemetryService.INSTANCE.recordWaveStart(
            quest.getQuestId(), waveNumber, waveState.totalToSpawn,
            playerCount, questType, waveState.modifiers
        );

        return waveState;
    }

    public void tickWave(EnduranceQuestManager.ActiveQuestSession session,
                         @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        if (session == null || session.getQuest().getState() != EnduranceQuestState.IN_PROGRESS) {
            return;
        }
        ArenaContext arena = session.getArena();
        if (arena == null) {
            return;
        }
        WaveState waveState = activeWaves.get(arena.getId());
        if (waveState == null) {
            return;
        }
        if (waveState.isComplete()) {
            notifyWaveComplete(session, player, waveState);
            return;
        }

        waveState.incrementWaveTicks();
        spawnDueBatches(waveState, arena, session.getArenaHandle());

        waveState.getObjective().tick(player);
        if (waveState.getObjective().isComplete()) {
            waveState.markComplete();
        }
        if (waveState.getObjective().getType() == WaveObjectiveState.Type.KILL_ALL
            && waveState.getKilled() >= waveState.getTotalToSpawn()
            && !waveState.hasPendingSpawns()) {
            waveState.markComplete();
        }

        if (waveState.isComplete()) {
            notifyWaveComplete(session, player, waveState);
        }
    }

    public void handleMobDeath(UUID mobId,
                               UUID arenaId,
                               @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session,
                               @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        WaveState waveState = activeWaves.get(arenaId);

        if (waveState == null) {
            LOGGER.warn("[WaveManager] handleMobDeath: No wave state for arena {} (mob={})", arenaId, mobId);
            return;
        }
        boolean tracked = waveState.spawnedMobs.contains(mobId);
        LOGGER.info("[WaveManager] handleMobDeath: mob={}, tracked={}, arenaId={}, spawnedMobsCount={}",
            mobId, tracked, arenaId, waveState.spawnedMobs.size());

        if (tracked) {
            waveState.recordKill(mobId);
            EnduranceLogger.mob(Phase.MOB_DEATH, waveState.quest.getQuestId(), waveState.waveNumber,
                mobId, "unknown", "Killed: %d/%d, remaining=%d",
                waveState.killed, waveState.totalToSpawn, waveState.spawnedMobs.size() - waveState.killed);
            DiagnosticLogger.quest("mobKilled: wave=%d, killed=%d/%d, remaining=%d",
                waveState.waveNumber, waveState.killed, waveState.totalToSpawn,
                waveState.spawnedMobs.size() - waveState.killed);

            UUID questId = waveState.quest.getQuestId();
            if (!waveState.isPracticeMode() && BossWaveSystem.INSTANCE.isBossWave(waveState.waveNumber, questId)) {
                BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.endBossFight(arenaId, true);
                if (bossFight != null) {
                    waveState.quest.incrementBossWavesCompleted();
                    TensionSystem.INSTANCE.onBossDefeated(questId);
                    LOGGER.info("[EnduranceQuest] BOSS wave {} complete! Bonus points: {}",
                        waveState.waveNumber, bossFight.getBonusPoints());
                }
            }

            if (waveState.isComplete() && session != null) {
                notifyWaveComplete(session, player, waveState);
            }
        } else {
            LOGGER.warn("[WaveManager] handleMobDeath: Mob {} NOT in spawnedMobs list (size={}), wave {}, arena {}",
                mobId, waveState.spawnedMobs.size(), waveState.waveNumber, arenaId);
        }
    }

    public int respawnMissingMobs(EnduranceQuestManager.ActiveQuestSession session,
                                  WaveState waveState,
                                  int missingCount,
                                  List<UUID> deadMobIds) {
        if (session == null || waveState == null || missingCount <= 0) {
            return 0;
        }
        if (!waveState.getObjective().shouldRespawnExternalDeaths()) {
            return 0;
        }
        int allowed = waveState.registerExternalRespawn(missingCount);
        if (allowed <= 0) {
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.quest.getQuestId());
            return 0;
        }
        if (waveState.isPracticeMode()) {
            return mobSpawner.respawnPracticeDummies(session, waveState, allowed, deadMobIds);
        }
        return mobSpawner.respawnMissingMobs(session, waveState, allowed, deadMobIds);
    }

    public Optional<WaveState> getWaveState(UUID arenaId) {
        return Optional.ofNullable(activeWaves.get(arenaId));
    }

    public void cleanupWave(UUID arenaId, ServerLevel level) {
        WaveState state = activeWaves.remove(arenaId);
        if (state != null) {
            despawnRemainingMobs(state, level);
        }
    }

    /**
     * Clear wave state for an arena if the wave is complete.
     * Thread-safe: Uses computeIfPresent for atomic check-and-remove to prevent TOCTOU race.
     */
    public boolean clearCompletedWaveState(UUID arenaId) {
        final boolean[] removed = {false};
        activeWaves.computeIfPresent(arenaId, (key, state) -> {
            if (state.isComplete()) {
                removed[0] = true;
                LOGGER.debug("[WaveManager] Cleared completed wave state for arena {}", arenaId);
                return null;
            }
            return state;
        });
        return removed[0];
    }

    // ========== Internal Helpers ==========

    private Set<WaveModifier> rollWaveModifiers(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return Set.of();
        }

        float modifierChance = getModifierChance(waveNumber);
        int maxModifiers = waveNumber < 5 ? MAX_MODIFIERS_EARLY : (waveNumber < 8 ? MAX_MODIFIERS_MID : MAX_MODIFIERS_LATE);
        Set<WaveModifier> modifiers = new HashSet<>();

        for (WaveModifier modifier : WaveModifier.values()) {
            if (random.nextFloat() < modifierChance) {
                modifiers.add(modifier);

                if (modifiers.size() >= maxModifiers) break;
            }
        }
        return modifiers;
    }

    private float getModifierChance(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }
        if (waveNumber < 5) {
            return MODIFIER_CHANCE_TIER_1;
        }
        if (waveNumber < 8) {
            return MODIFIER_CHANCE_TIER_2;
        }
        return MODIFIER_CHANCE_TIER_3;
    }

    private void handleWaveStartFailure(EnduranceQuestManager.ActiveQuestSession session, String reason) {
        if (session == null || session.getQuest() == null) {
            return;
        }
        EnduranceTelemetryService.INSTANCE.recordWaveBlocked(session.getQuest().getQuestId());
        LOGGER.error("[EnduranceQuest] Wave start aborted: {}", reason);

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(Objects.requireNonNull(session.getPlayerId()));
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Objects.requireNonNull(net.minecraft.network.chat.Component.literal(
            "[DevMod] Arena configuration missing - quest aborted.")
            .withStyle(SharedColorTokens.Chat.RED)));
        EnduranceQuestManager.INSTANCE.abandonQuest(player);
    }

    private void spawnDueBatches(WaveState waveState, ArenaContext arena, ArenaHandle handle) {
        if (waveState == null || waveState.getSpawnPlan().isEmpty()) {
            return;
        }
        int index = waveState.getNextSpawnIndex();
        int waveTicks = waveState.getWaveTicks();
        List<WaveDirector.SpawnBatch> plan = waveState.getSpawnPlan();
        while (index < plan.size() && waveTicks >= plan.get(index).scheduledTick()) {
            mobSpawner.spawnWaveBatch(waveState, arena, handle, plan.get(index));
            index++;
        }
        waveState.advanceSpawnIndex(index);
    }

    private void notifyWaveComplete(EnduranceQuestManager.ActiveQuestSession session,
                                    @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player,
                                    WaveState waveState) {
        if (waveState == null) {
            return;
        }

        synchronized (waveState) {
            if (waveState.isCompletionNotified()) {
                return;
            }
            waveState.markCompletionNotified();
        }

        EnduranceLogger.wave(Phase.WAVE_COMPLETE, player, waveState.quest.getQuestId(),
            waveState.waveNumber, waveState.quest.getTotalWaves(),
            "Wave complete: killed=%d/%d, duration=%dms", waveState.killed, waveState.totalToSpawn,
            System.currentTimeMillis() - waveState.getWaveStartTime());
        PartyQuestSession partySession = session.getPartyId() != null
            ? EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId()).orElse(null)
            : null;
        if (partySession != null && partySession.isActive()) {
            handlePartyWaveComplete(session, partySession, waveState, player);
            return;
        }
        waveState.advanceSpawnIndex(waveState.getSpawnPlan().size());

        ArenaContext arena = session.getArena();
        if (arena != null) {
            despawnRemainingMobs(waveState, arena.getLevel());
        }

        long durationMs = System.currentTimeMillis() - waveState.getWaveStartTime();
        float killsPerSecond = durationMs > 0 ? (waveState.killed * 1000f / durationMs) : 0f;
        if (!waveState.isPracticeMode()) {
            EnduranceTelemetryService.INSTANCE.recordWaveComplete(
                waveState.quest.getQuestId(), waveState.waveNumber, waveState.killed,
                durationMs, false, killsPerSecond
            );
        }

        if (player != null && session.getQuest().getState() == EnduranceQuestState.IN_PROGRESS) {
            EnduranceEventHandler.onWaveComplete(player, session, session.getQuest().getCurrentWave());
            EnduranceQuestManager.INSTANCE.completeWave(player);
        }
    }

    private void handlePartyWaveComplete(EnduranceQuestManager.ActiveQuestSession session,
                                         PartyQuestSession partySession,
                                         WaveState waveState,
                                         @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        waveState.advanceSpawnIndex(waveState.getSpawnPlan().size());

        ArenaContext arena = session.getArena();
        if (arena != null) {
            despawnRemainingMobs(waveState, arena.getLevel());
        }

        long durationMs = System.currentTimeMillis() - waveState.getWaveStartTime();
        float killsPerSecond = durationMs > 0 ? (waveState.killed * 1000f / durationMs) : 0f;
        if (!waveState.isPracticeMode()) {
            EnduranceTelemetryService.INSTANCE.recordWaveComplete(
                waveState.quest.getQuestId(), waveState.waveNumber, waveState.killed,
                durationMs, false, killsPerSecond
            );
        }

        net.minecraft.server.MinecraftServer server = null;
        if (player != null) {
            server = player.getServer();
        } else if (arena != null && arena.getLevel() != null) {
            server = arena.getLevel().getServer();
        }

        if (server != null && session.getQuest().getState() == EnduranceQuestState.IN_PROGRESS) {
            boolean applyShared = true;
            EnduranceQuestManager.ActiveQuestSession sharedSession = null;
            for (UUID memberId : partySession.getMembers()) {
                net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayer(
                    java.util.Objects.requireNonNull(memberId, "memberId cannot be null"));
                if (member == null) {
                    continue;
                }
                EnduranceQuestManager.ActiveQuestSession memberSession =
                    EnduranceQuestManager.INSTANCE.getActiveSession(memberId).orElse(null);
                if (memberSession != null) {
                    EnduranceEventHandler.onWaveComplete(member, memberSession,
                        memberSession.getQuest().getCurrentWave(), applyShared);
                    if (applyShared) {
                        sharedSession = memberSession;
                    }
                    applyShared = false;
                }
            }
            if (sharedSession != null) {
                List<WaveDirective> directives = sharedSession.getPendingDirectives();
                int directiveWave = sharedSession.getDirectiveWaveNumber();
                boolean chainActive = DirectiveChainManager.INSTANCE.hasActiveChain(sharedSession.getQuest().getQuestId());
                for (UUID memberId : partySession.getMembers()) {
                    EnduranceQuestManager.ActiveQuestSession memberSession =
                        EnduranceQuestManager.INSTANCE.getActiveSession(memberId).orElse(null);
                    if (memberSession == null) {
                        continue;
                    }
                    if (memberSession != sharedSession && !directives.isEmpty()) {
                        memberSession.setPendingDirectives(directives, directiveWave);
                    }
                    if (!chainActive && !directives.isEmpty()) {
                        net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayer(
                            java.util.Objects.requireNonNull(memberId, "memberId cannot be null"));
                        if (member != null) {
                            com.devmod.network.NetworkHandler.sendWaveDirectiveChoices(
                                member, directiveWave, directives);
                        }
                    }
                }
            }
            EnduranceQuestManager.INSTANCE.completePartyWave(partySession);
        }
    }

    private void despawnRemainingMobs(WaveState waveState, ServerLevel level) {
        if (waveState == null || level == null) {
            return;
        }
        List<UUID> mobSnapshot = List.copyOf(waveState.spawnedMobs);
        for (UUID mobId : mobSnapshot) {
            Entity entity = level.getEntity(Objects.requireNonNull(mobId));
            if (entity != null) {
                entity.discard();
            }
        }
    }

    // ========== Public Records ==========

    public record SpawnContext(
        List<BlockPos> positions,
        Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
        @javax.annotation.Nullable ArenaTemplate template,
        @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator,
        SpawnPools pools,
        @javax.annotation.Nullable com.devmod.arena.zone.ZoneLayout zoneLayout,
        @javax.annotation.Nullable com.devmod.arena.zone.ZoneSpawnSlotAllocator zoneAllocator,
        int maxEntities
    ) {
        /** Convenience constructor without zone parameters. */
        public SpawnContext(List<BlockPos> positions,
                     Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
                     @javax.annotation.Nullable ArenaTemplate template,
                     @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator,
                     SpawnPools pools) {
            this(positions, slotMap, template, runtimeValidator, pools, null, null, 0);
        }

        public boolean hasZones() {
            return zoneLayout != null && zoneLayout.isMultiZone();
        }

        public boolean canSpawnMore(int currentCount) {
            return maxEntities <= 0 || currentCount < maxEntities;
        }
    }

    public record SpawnPools(
        List<BlockPos> all,
        List<BlockPos> melee,
        List<BlockPos> ranged,
        List<BlockPos> corner
    ) {}
}

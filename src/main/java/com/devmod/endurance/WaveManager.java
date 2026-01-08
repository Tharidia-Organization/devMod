package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.DevMod;
import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateSpawnValidator;
import com.devmod.arena.spawn.SpawnOccupancyTracker;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.endurance.config.EffectiveConfig;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;
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
        private int totalToSpawn;
        private int spawned = 0;
        private int killed = 0;
        private int practiceDummyCounter = 0;
        private long waveStartTime;
        private int waveTicks = 0;
        private int nextSpawnIndex = 0;
        private boolean complete = false;
        private boolean completionNotified = false;
        private int externalRespawnCount = 0;
        private final int externalRespawnLimit;

        // Wave modifiers (roguelike elements)
        private final Set<WaveModifier> modifiers = new HashSet<>();

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

        public void recordKill(UUID mobId) {
            killed++;
            objective.recordKill();
            if (objective.getType() == WaveObjectiveState.Type.ELITE_HUNT) {
                objective.recordObjectiveKill(mobId);
            }
            if (objective.isComplete() && objective.getType() != WaveObjectiveState.Type.KILL_ALL) {
                complete = true;
            }
            if (objective.getType() == WaveObjectiveState.Type.KILL_ALL
                && objective.isComplete()
                && spawned >= totalToSpawn
                && !hasPendingSpawns()) {
                complete = true;
            }
        }

        public void addSpawnedMob(UUID mobId, SpawnAffix affix, boolean objectiveTarget) {
            spawnedMobs.add(mobId);
            if (affix != null) {
                spawnAffixes.put(mobId, affix);
            }
            if (objectiveTarget && mobId != null) {
                objectiveTargets.add(mobId);
                objective.registerObjectiveTarget(mobId);
            }
            spawned++;
        }

        public int nextPracticeDummyIndex() {
            return practiceDummyCounter++;
        }

        public void incrementWaveTicks() {
            waveTicks++;
        }

        public void advanceSpawnIndex(int index) {
            nextSpawnIndex = index;
        }

        public boolean hasPendingSpawns() {
            return nextSpawnIndex < spawnPlan.size();
        }

        public void markComplete() {
            complete = true;
        }

        public void markCompletionNotified() {
            completionNotified = true;
        }

        public SpawnAffix getAffixForMob(UUID mobId) {
            return spawnAffixes.getOrDefault(mobId, SpawnAffix.BASE);
        }

        public boolean isObjectiveTarget(UUID mobId) {
            return mobId != null && objectiveTargets.contains(mobId);
        }

        public boolean replaceObjectiveTarget(UUID oldId, UUID newId) {
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

        public void adjustKillTarget(int newTarget) {
            if (objective.getType() == WaveObjectiveState.Type.KILL_ALL) {
                objective.adjustKillTarget(newTarget);
            }
        }

        public int registerExternalRespawn(int count) {
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
            return ctx != null ? ctx.zoneLayout : null;
        }

        /**
         * Checks if this wave has multi-zone layout.
         */
        public boolean hasZones() {
            final SpawnContext ctx = spawnContext;
            return ctx != null && ctx.hasZones();
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

        public final String displayName;
        public final String description;

        WaveModifier(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // ========== Wave Management ==========

    /**
     * Start a new wave for a quest.
     */
    public WaveState startWave(EnduranceQuestManager.ActiveQuestSession session) {
        EnduranceQuest quest = session.getQuest();
        ArenaContext arena = session.getArena();
        ArenaHandle handle = session.getArenaHandle();
        int waveNumber = quest.getCurrentWave();

        // Get multiplayer scaling parameters from session
        int playerCount = session.getPlayerCount();
        QuestType questType = session.getQuestType();

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
            // Start boss wave instead of normal wave
            LOGGER.info("[BossDebug] Starting boss wave {} via BossWaveSystem", waveNumber);
            BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.startBossWave(session, waveNumber);
            LOGGER.info("[BossDebug] startBossWave result: bossFight={}, bossEntity={}",
                bossFight != null, bossFight != null ? bossFight.getBossEntity() : null);
            if (bossFight != null && bossFight.getBossEntity() != null) {
                WaveState waveState = new WaveState(
                    arena.getId(),
                    quest,
                    waveNumber,
                    playerCount,
                    questType,
                    1,
                    WaveObjectiveState.killAll(1),
                    List.of(),
                    null,
                    1.0f,
                    null,
                    session.isPracticeMode(),
                    session
                );
                waveState.addSpawnedMob(bossFight.getBossEntity().getUUID(), SpawnAffix.ELITE, false);

                activeWaves.put(arena.getId(), waveState);

                LOGGER.info("[EnduranceQuest] Started BOSS wave {} with {} archetype",
                    waveNumber, bossFight.getArchetype().displayName);

                EnduranceTelemetryService.INSTANCE.recordWaveStart(
                    quest.getQuestId(),
                    waveNumber,
                    waveState.totalToSpawn,
                    playerCount,
                    questType,
                    waveState.modifiers
                );

                return waveState;
            }
            // Fallback to normal wave if boss spawn fails
            LOGGER.warn("[EnduranceQuest] Boss spawn failed for wave {}, falling back to normal wave", waveNumber);
        }

        Set<WaveModifier> modifiers = rollWaveModifiers(waveNumber);
        // Use session-aware mob count that respects config overrides
        int baseCount = quest.getCurrentWaveMobCount(playerCount, questType, session);
        if (modifiers.contains(WaveModifier.DOUBLE_SPAWN)) {
            baseCount = Math.max(1, baseCount * 2);
        }
        baseCount = Math.max(1, baseCount);

        WaveDirective directive = session.consumeDirectiveForWave(waveNumber);
        float rewardMultiplier = directive != null ? directive.rewardMultiplier() : 1.0f;
        String directiveId = directive != null ? directive.id() : null;

        WaveDirector.WavePlan plan = WaveDirector.INSTANCE.planWave(session, baseCount, directive);
        SpawnContext spawnContext = buildSpawnContext(arena, handle);
        if (spawnContext == null) {
            handleWaveStartFailure(session, "missing_spawn_slots");
            return null;
        }
        WaveState waveState = new WaveState(
            arena.getId(),
            quest,
            waveNumber,
            playerCount,
            questType,
            plan.totalToSpawn(),
            plan.objective(),
            plan.batches(),
            spawnContext,
            rewardMultiplier,
            directiveId,
            session.isPracticeMode(),
            session
        );
        waveState.getModifiers().addAll(modifiers);

        activeWaves.put(arena.getId(), waveState);

        // Spawn initial batch
        spawnDueBatches(waveState, arena, handle);

        LOGGER.info("[EnduranceQuest] Started wave {} with {} mobs (modifiers: {})",
            waveState.waveNumber, waveState.totalToSpawn, waveState.modifiers);

        // Telemetry: record wave start
        if (!practice) {
            EnduranceTelemetryService.INSTANCE.recordWaveStart(
                quest.getQuestId(),
                waveNumber,
                waveState.totalToSpawn,
                playerCount,
                questType,
                waveState.modifiers
            );
        }

        return waveState;
    }

    /**
     * Apply random modifiers to a wave based on wave number.
     */
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
            spawnWaveBatch(waveState, arena, handle, plan.get(index));
            index++;
        }
        waveState.advanceSpawnIndex(index);
    }

    private void spawnWaveBatch(WaveState waveState,
                                ArenaContext arena,
                                ArenaHandle handle,
                                WaveDirector.SpawnBatch batch) {
        if (batch == null) {
            return;
        }
        spawnWaveMobs(waveState, arena, handle, batch.count(), batch.affix(), batch.role(), batch.objectiveTarget());
    }

    /**
     * Spawn mobs for a scheduled wave batch.
     * Verifies each spawn and logs failures for debugging.
     */
    private void spawnWaveMobs(WaveState waveState,
                               ArenaContext arena,
                               @javax.annotation.Nullable ArenaHandle handle,
                               int count,
                               SpawnAffix affix,
                               WaveDirector.SpawnRole role,
                               boolean objectiveTarget) {
        if (count <= 0 || arena == null) {
            return;
        }

        // Practice mode: spawn training dummies instead of real mobs
        if (waveState.isPracticeMode() && DummmmmmyCompat.isAvailable()) {
            spawnPracticeDummies(waveState, arena, count);
            return;
        }

        SpawnAffix safeAffix = affix != null ? affix : SpawnAffix.BASE;
        SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions.isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for wave {}", waveState.waveNumber);
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.quest.getQuestId());
            return;
        }

        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.quest.getMobConfig();
        EntityType<?> entityType = mobConfig.entityType;

        // Check if this mob type is enabled in session's mob pool config
        EnduranceQuestManager.ActiveQuestSession session = waveState.getSession();
        if (!com.devmod.endurance.config.EffectiveConfig.isMobEnabled(session, mobConfig.mobId)) {
            LOGGER.info("[EnduranceQuest] Mob {} is disabled by session config, skipping spawn", mobConfig.mobId);
            return;
        }

        // Get mob requirements for spawn validation
        MobRequirements mobReqs = MobRequirementsRegistry.INSTANCE.get(entityType);

        // Log if time requirements are not met (non-blocking - arena should control time)
        if (!mobReqs.time().isValidAt(level.getDayTime())) {
            LOGGER.debug("[EnduranceQuest] Time requirement ({}) not optimal for {} at dayTime={}, spawning anyway",
                mobReqs.time(), entityType.getDescriptionId(), level.getDayTime() % 24000);
        }

        List<BlockPos> spawnPositions = spawnContext.positions;
        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        boolean allowReuse = spawnPositions.size() < count;
        SpawnPools pools = spawnContext.pools;

        int successfulSpawns = 0;
        int failedSpawns = 0;

        if (spawnPositions.size() < count) {
            LOGGER.warn("[EnduranceQuest] Only {} valid spawn positions found for {} mobs",
                spawnPositions.size(), count);
        }

        for (int i = 0; i < count; i++) {
            // Safety check: prevent entity overload
            if (!spawnContext.canSpawnMore(successfulSpawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping spawn at {}/{}",
                    successfulSpawns, count);
                break;
            }

            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (entity instanceof Mob mob) {
                List<BlockPos> candidatePool = chooseSpawnPool(role, pools, mob);
                BlockPos spawnPos = pickValidatedSpawnPosition(
                    candidatePool,
                    i,
                    occupied,
                    spawnContext.runtimeValidator,
                    spawnContext.slotMap,
                    spawnContext.template,
                    level,
                    allowReuse
                );
                if (spawnPos == null && candidatePool != pools.all) {
                    EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                        waveState.quest.getQuestId(),
                        waveState.waveNumber,
                        resolvePoolTag(candidatePool, pools),
                        handle != null ? handle.templateId() : null,
                        "pool_exhausted"
                    );
                    spawnPos = pickValidatedSpawnPosition(
                        pools.all,
                        i,
                        occupied,
                        spawnContext.runtimeValidator,
                        spawnContext.slotMap,
                        spawnContext.template,
                        level,
                        allowReuse
                    );
                }
                if (spawnPos == null) {
                    failedSpawns++;
                    EnduranceTelemetryService.INSTANCE.recordSpawnFailure(
                        waveState.quest.getQuestId(),
                        waveState.waveNumber,
                        "no_valid_spawn",
                        handle != null ? handle.templateId() : null
                    );
                    continue;
                }

                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                applyMobModifiers(mob, waveState);
                applyMultiplayerHPScaling(mob, waveState);
                applySpawnAffix(mob, safeAffix);

                SpawnAffix appliedAffix = safeAffix;
                // FIX #9B: Check ELITE_HUNTER curse - forces all mobs to be elite
                boolean forcedElite = com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE
                    .shouldSpawnElite(waveState.quest.getQuestId());
                if (!safeAffix.elite && (forcedElite || random.nextFloat() < getEliteChance(mobConfig.eliteChance, waveState.waveNumber, waveState.getSession()))) {
                    applyEliteBuffs(mob, waveState.waveNumber);
                    appliedAffix = SpawnAffix.ELITE;
                } else if (safeAffix.elite) {
                    applyEliteBuffs(mob, waveState.waveNumber);
                }

                if (shouldLogBossHp(mobConfig)) {
                    LOGGER.info("[EnduranceQuest] Boss HP final for {}: maxHp={}, currentHp={}, affix={}, wave={}, questId={}",
                        mobConfig.mobId, mob.getMaxHealth(), mob.getHealth(), appliedAffix.name(),
                        waveState.waveNumber, waveState.quest.getQuestId());
                }

                finalizeMobSpawn(mob, level, spawnPos);

                CompoundTag tag = mob.getPersistentData();
                tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.quest.getQuestId()));
                tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId()));
                tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.quest.getMobId().toString()));
                tag.putString(EnduranceTags.AFFIX,
                    Objects.requireNonNull(appliedAffix.name(), "appliedAffixName"));
                if (handle != null) {
                    if (handle.templateId() != null) {
                        tag.putString("endurance_template_id",
                            Objects.requireNonNull(handle.templateId(), "templateId"));
                    }
                    tag.putInt("endurance_template_version", handle.templateVersion());
                    if (handle.policyId() != null) {
                        tag.putString("endurance_policy_id",
                            Objects.requireNonNull(handle.policyId(), "policyId"));
                    }
                    tag.putInt("endurance_policy_version", handle.policyVersion());
                }
                if (objectiveTarget) {
                    tag.putBoolean("endurance_objective_target", true);
                }

                boolean added = level.addFreshEntity(mob);
                if (added && mob.isAlive()) {
                    waveState.addSpawnedMob(mob.getUUID(), appliedAffix, objectiveTarget);
                    successfulSpawns++;
                    awakeMobAI(mob, level);
                    if (handle != null) {
                        EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                            waveState.quest.getQuestId(), handle, spawnPos);
                    }
                } else {
                    failedSpawns++;
                    LOGGER.warn("[EnduranceQuest] Failed to spawn mob at {} (added={}, alive={})",
                        spawnPos, added, mob.isAlive());
                }
            } else {
                failedSpawns++;
                LOGGER.error("[EnduranceQuest] Failed to create entity of type {}", entityType);
            }
        }

        if (failedSpawns > 0) {
            LOGGER.warn("[EnduranceQuest] Wave {} spawn summary: {} successful, {} failed. Adjusting wave target.",
                waveState.waveNumber, successfulSpawns, failedSpawns);
            waveState.totalToSpawn = Math.max(1, waveState.spawned);
            waveState.adjustKillTarget(waveState.totalToSpawn);
        }

        LOGGER.info("[EnduranceQuest] Wave {} spawned {}/{} mobs successfully",
            waveState.waveNumber, successfulSpawns, count);
    }

    /**
     * Spawn training dummies for practice mode.
     * Uses DummmmmmyCompat to create practice targets instead of real mobs.
     */
    private void spawnPracticeDummies(WaveState waveState, ArenaContext arena, int count) {
        if (!DummmmmmyCompat.isAvailable()) {
            LOGGER.warn("[EnduranceQuest] Practice mode requested but Dummmmmmy mod not available");
            return;
        }

        SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions.isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for practice dummies");
            return;
        }

        ServerLevel level = arena.getLevel();
        List<BlockPos> positions = spawnContext.positions;

        // Limit dummies to avoid overwhelming the player
        int dummyCount = Math.min(count, Math.min(5, positions.size()));
        boolean objectiveTarget = waveState.getObjective().getType() == WaveObjectiveState.Type.ELITE_HUNT;

        int successfulSpawns = 0;
        for (int i = 0; i < dummyCount; i++) {
            BlockPos pos = positions.get(i % positions.size());
            int dummyIndex = waveState.nextPracticeDummyIndex();
            String dummyId = String.format("practice_w%d_%d", waveState.getWaveNumber(), dummyIndex);

            UUID uuid = DummmmmmyCompat.spawnDummy(level, pos, dummyId,
                dummy -> tagPracticeDummy(dummy, waveState, arena));
            if (uuid != null) {
                waveState.addSpawnedMob(uuid, SpawnAffix.BASE, objectiveTarget);
                successfulSpawns++;
            }
        }

        // Adjust wave target to match spawned dummies
        if (successfulSpawns > 0) {
            waveState.totalToSpawn = successfulSpawns;
            waveState.adjustKillTarget(successfulSpawns);
            waveState.getObjective().adjustEliteTargetCount(successfulSpawns);
        }

        LOGGER.info("[EnduranceQuest] Practice mode: spawned {} dummies for wave {}",
            successfulSpawns, waveState.getWaveNumber());
    }

    private void tagPracticeDummy(Entity dummy, WaveState waveState, ArenaContext arena) {
        if (dummy == null || waveState == null || arena == null) {
            return;
        }
        CompoundTag tag = dummy.getPersistentData();
        tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.quest.getQuestId(), "questId"));
        tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId(), "arenaId"));
        tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.quest.getMobId().toString()));
        tag.putString(EnduranceTags.MOB_ID_OVERRIDE, Objects.requireNonNull(waveState.quest.getMobId().toString()));
        tag.putString(EnduranceTags.AFFIX, Objects.requireNonNull(SpawnAffix.BASE.name(), "affix"));
        tag.putBoolean(EnduranceTags.PRACTICE_DUMMY, true);
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
            return respawnPracticeDummies(session, waveState, allowed, deadMobIds);
        }

        ArenaContext arena = session.getArena();
        ArenaHandle handle = session.getArenaHandle();
        if (arena == null || handle == null) {
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.quest.getQuestId());
            return 0;
        }

        SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions.isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.quest.getQuestId());
            return 0;
        }

        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.quest.getMobConfig();
        EntityType<?> entityType = mobConfig.entityType;

        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        boolean allowReuse = spawnContext.positions.size() < allowed;
        SpawnPools pools = spawnContext.pools;

        int successfulRespawns = 0;
        int failedRespawns = 0;

        for (int i = 0; i < allowed; i++) {
            // Safety check: prevent entity overload
            if (!spawnContext.canSpawnMore(successfulRespawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping respawn at {}/{}",
                    successfulRespawns, allowed);
                break;
            }

            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (!(entity instanceof Mob mob)) {
                failedRespawns++;
                LOGGER.error("[EnduranceQuest] Failed to create entity of type {}", entityType);
                continue;
            }

            UUID deadId = i < deadMobIds.size() ? deadMobIds.get(i) : null;
            SpawnAffix affix = deadId != null ? waveState.getAffixForMob(deadId) : SpawnAffix.BASE;
            boolean objectiveTarget = deadId != null && waveState.isObjectiveTarget(deadId);

            List<BlockPos> candidatePool = chooseSpawnPool(resolveSpawnRole(affix), pools, mob);
            BlockPos spawnPos = pickValidatedSpawnPosition(
                candidatePool,
                i,
                occupied,
                spawnContext.runtimeValidator,
                spawnContext.slotMap,
                spawnContext.template,
                level,
                allowReuse
            );
            if (spawnPos == null && candidatePool != pools.all) {
                EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                    waveState.quest.getQuestId(),
                    waveState.waveNumber,
                    resolvePoolTag(candidatePool, pools),
                    handle.templateId(),
                    "pool_exhausted"
                );
                spawnPos = pickValidatedSpawnPosition(
                    pools.all,
                    i,
                    occupied,
                    spawnContext.runtimeValidator,
                    spawnContext.slotMap,
                    spawnContext.template,
                    level,
                    allowReuse
                );
            }
            if (spawnPos == null) {
                failedRespawns++;
                EnduranceTelemetryService.INSTANCE.recordSpawnFailure(
                    waveState.quest.getQuestId(),
                    waveState.waveNumber,
                    "no_valid_spawn",
                    handle.templateId()
                );
                continue;
            }

            mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            applyMobModifiers(mob, waveState);
            applyMultiplayerHPScaling(mob, waveState);
            applySpawnAffix(mob, affix);
            if (affix.elite) {
                applyEliteBuffs(mob, waveState.waveNumber);
            }

            finalizeMobSpawn(mob, level, spawnPos);

            CompoundTag tag = mob.getPersistentData();
            tag.putUUID(EnduranceTags.QUEST_ID, Objects.requireNonNull(waveState.quest.getQuestId()));
            tag.putUUID(EnduranceTags.ARENA_ID, Objects.requireNonNull(arena.getId()));
            tag.putString(EnduranceTags.MOB_ID, Objects.requireNonNull(waveState.quest.getMobId().toString()));
            tag.putBoolean("endurance_respawned", true);
            tag.putString(EnduranceTags.AFFIX, Objects.requireNonNull(affix.name(), "affixName"));
            if (handle.templateId() != null) {
                tag.putString("endurance_template_id",
                    Objects.requireNonNull(handle.templateId(), "templateId"));
            }
            tag.putInt("endurance_template_version", handle.templateVersion());
            if (handle.policyId() != null) {
                tag.putString("endurance_policy_id",
                    Objects.requireNonNull(handle.policyId(), "policyId"));
            }
            tag.putInt("endurance_policy_version", handle.policyVersion());
            if (objectiveTarget) {
                tag.putBoolean("endurance_objective_target", true);
            }

            boolean added = level.addFreshEntity(mob);
            if (added && mob.isAlive()) {
                if (deadId != null) {
                    waveState.getSpawnedMobs().remove(deadId);
                    waveState.spawnAffixes.remove(deadId);
                }
                if (objectiveTarget && deadId != null) {
                    waveState.replaceObjectiveTarget(deadId, mob.getUUID());
                }
                waveState.getSpawnedMobs().add(Objects.requireNonNull(mob.getUUID()));
                waveState.spawnAffixes.put(mob.getUUID(), affix);
                successfulRespawns++;
                awakeMobAI(mob, level);
                EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                    waveState.quest.getQuestId(), handle, spawnPos);
            } else {
                failedRespawns++;
                LOGGER.warn("[EnduranceQuest] Failed to respawn mob at {}", spawnPos);
            }
        }

        if (failedRespawns > 0) {
            LOGGER.warn("[EnduranceQuest] External respawn summary: {} successful, {} failed (wave {})",
                successfulRespawns, failedRespawns, waveState.waveNumber);
        }

        return successfulRespawns;
    }

    private int respawnPracticeDummies(EnduranceQuestManager.ActiveQuestSession session,
                                       WaveState waveState,
                                       int missingCount,
                                       List<UUID> deadMobIds) {
        if (session == null || waveState == null || missingCount <= 0) {
            return 0;
        }
        if (!DummmmmmyCompat.isAvailable()) {
            LOGGER.warn("[EnduranceQuest] Practice mode respawn requested but Dummmmmmy mod not available");
            return 0;
        }

        ArenaContext arena = session.getArena();
        if (arena == null) {
            return 0;
        }

        SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions.isEmpty()) {
            LOGGER.error("[EnduranceQuest] No spawn positions available for practice dummy respawn");
            return 0;
        }

        ServerLevel level = arena.getLevel();
        List<BlockPos> positions = spawnContext.positions;
        List<UUID> safeDeadMobIds = deadMobIds != null ? deadMobIds : List.of();
        boolean objectiveTarget = waveState.getObjective().getType() == WaveObjectiveState.Type.ELITE_HUNT;

        int spawnLimit = Math.min(missingCount, positions.size());
        int successfulRespawns = 0;
        int deadIndex = 0;

        for (int i = 0; i < spawnLimit; i++) {
            if (!spawnContext.canSpawnMore(waveState.getSpawned() + successfulRespawns)) {
                LOGGER.warn("[EnduranceQuest] Entity limit reached, stopping practice respawn at {}/{}",
                    successfulRespawns, spawnLimit);
                break;
            }
            BlockPos pos = positions.get(i % positions.size());
            int dummyIndex = waveState.nextPracticeDummyIndex();
            String dummyId = String.format("practice_w%d_%d", waveState.getWaveNumber(), dummyIndex);
            UUID uuid = DummmmmmyCompat.spawnDummy(level, pos, dummyId,
                dummy -> tagPracticeDummy(dummy, waveState, arena));
            if (uuid != null) {
                UUID deadId = deadIndex < safeDeadMobIds.size() ? safeDeadMobIds.get(deadIndex) : null;
                deadIndex++;
                if (deadId != null) {
                    waveState.getSpawnedMobs().remove(deadId);
                    waveState.spawnAffixes.remove(deadId);
                    if (objectiveTarget) {
                        waveState.replaceObjectiveTarget(deadId, uuid);
                    }
                } else if (objectiveTarget) {
                    waveState.getObjective().registerObjectiveTarget(uuid);
                }
                waveState.getSpawnedMobs().add(uuid);
                waveState.spawnAffixes.put(uuid, SpawnAffix.BASE);
                successfulRespawns++;
            }
        }

        if (successfulRespawns > 0) {
            LOGGER.info("[EnduranceQuest] Practice mode: respawned {} dummies for wave {}",
                successfulRespawns, waveState.getWaveNumber());
        }

        return successfulRespawns;
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

    private void notifyWaveComplete(EnduranceQuestManager.ActiveQuestSession session,
                                    @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player,
                                    WaveState waveState) {
        if (waveState == null || waveState.isCompletionNotified()) {
            return;
        }
        PartyQuestSession partySession = session.getPartyId() != null
            ? EnduranceQuestManager.INSTANCE.getPartySession(session.getPartyId()).orElse(null)
            : null;
        if (partySession != null && partySession.isActive()) {
            handlePartyWaveComplete(session, partySession, waveState, player);
            return;
        }
        waveState.markCompletionNotified();
        waveState.advanceSpawnIndex(waveState.getSpawnPlan().size());

        ArenaContext arena = session.getArena();
        if (arena != null) {
            despawnRemainingMobs(waveState, arena.getLevel());
        }

        long durationMs = System.currentTimeMillis() - waveState.getWaveStartTime();
        float killsPerSecond = durationMs > 0 ? (waveState.killed * 1000f / durationMs) : 0f;
        if (!waveState.isPracticeMode()) {
            EnduranceTelemetryService.INSTANCE.recordWaveComplete(
                waveState.quest.getQuestId(),
                waveState.waveNumber,
                waveState.killed,
                durationMs,
                false,
                killsPerSecond
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
        waveState.markCompletionNotified();
        waveState.advanceSpawnIndex(waveState.getSpawnPlan().size());

        ArenaContext arena = session.getArena();
        if (arena != null) {
            despawnRemainingMobs(waveState, arena.getLevel());
        }

        long durationMs = System.currentTimeMillis() - waveState.getWaveStartTime();
        float killsPerSecond = durationMs > 0 ? (waveState.killed * 1000f / durationMs) : 0f;
        if (!waveState.isPracticeMode()) {
            EnduranceTelemetryService.INSTANCE.recordWaveComplete(
                waveState.quest.getQuestId(),
                waveState.waveNumber,
                waveState.killed,
                durationMs,
                false,
                killsPerSecond
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
        for (UUID mobId : waveState.spawnedMobs) {
            Entity entity = level.getEntity(Objects.requireNonNull(mobId));
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private BlockPos pickValidatedSpawnPosition(
            List<BlockPos> positions,
            int startIndex,
            SpawnOccupancyTracker occupied,
            @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator,
            Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
            @javax.annotation.Nullable ArenaTemplate template,
            ServerLevel level,
            boolean allowReuse) {
        int size = positions.size();
        for (int offset = 0; offset < size; offset++) {
            BlockPos pos = positions.get((startIndex + offset) % size);
            if (!allowReuse && occupied.isOccupied(pos)) {
                continue;
            }
            if (runtimeValidator != null && template != null && !slotMap.isEmpty()) {
                ArenaTemplate.SpawnSlot slot = slotMap.get(pos);
                if (slot == null) {
                    continue;
                }
                if (!runtimeValidator.validateAtRuntime(template.id(), slot, level, pos)) {
                    continue;
                }
            }
            if (!allowReuse) {
                occupied.markOccupied(pos);
            }
            return pos;
        }
        return null;
    }

    public static class SpawnContext {
        public final List<BlockPos> positions;
        public final Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap;
        public final @javax.annotation.Nullable ArenaTemplate template;
        public final @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator;
        public final SpawnPools pools;
        public final @javax.annotation.Nullable com.devmod.arena.zone.ZoneLayout zoneLayout;
        public final @javax.annotation.Nullable com.devmod.arena.zone.ZoneSpawnSlotAllocator zoneAllocator;
        public final int maxEntities;

        public SpawnContext(List<BlockPos> positions,
                     Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
                     @javax.annotation.Nullable ArenaTemplate template,
                     @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator,
                     SpawnPools pools) {
            this(positions, slotMap, template, runtimeValidator, pools, null, null, 0);
        }

        public SpawnContext(List<BlockPos> positions,
                     Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
                     @javax.annotation.Nullable ArenaTemplate template,
                     @javax.annotation.Nullable TemplateSpawnValidator runtimeValidator,
                     SpawnPools pools,
                     @javax.annotation.Nullable com.devmod.arena.zone.ZoneLayout zoneLayout,
                     @javax.annotation.Nullable com.devmod.arena.zone.ZoneSpawnSlotAllocator zoneAllocator,
                     int maxEntities) {
            this.positions = positions;
            this.slotMap = slotMap;
            this.template = template;
            this.runtimeValidator = runtimeValidator;
            this.pools = pools;
            this.zoneLayout = zoneLayout;
            this.zoneAllocator = zoneAllocator;
            this.maxEntities = maxEntities;
        }

        public boolean hasZones() {
            return zoneLayout != null && zoneLayout.isMultiZone();
        }

        public boolean canSpawnMore(int currentCount) {
            return maxEntities <= 0 || currentCount < maxEntities;
        }
    }

    public static class SpawnPools {
        public final List<BlockPos> all;
        public final List<BlockPos> melee;
        public final List<BlockPos> ranged;
        public final List<BlockPos> corner;

        public SpawnPools(List<BlockPos> all, List<BlockPos> melee, List<BlockPos> ranged, List<BlockPos> corner) {
            this.all = all;
            this.melee = melee;
            this.ranged = ranged;
            this.corner = corner;
        }
    }

    private SpawnPools buildSpawnPools(List<BlockPos> positions, Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap) {
        List<BlockPos> melee = new ArrayList<>();
        List<BlockPos> ranged = new ArrayList<>();
        List<BlockPos> corner = new ArrayList<>();
        for (BlockPos pos : positions) {
            ArenaTemplate.SpawnSlot slot = slotMap.get(pos);
            if (slot != null && slot.tags() != null) {
                if (slot.tags().contains("melee")) {
                    melee.add(pos);
                }
                if (slot.tags().contains("ranged")) {
                    ranged.add(pos);
                }
                if (slot.tags().contains("corner")) {
                    corner.add(pos);
                }
            }
        }
        return new SpawnPools(positions, melee, ranged, corner);
    }

    private @javax.annotation.Nullable SpawnContext buildSpawnContext(ArenaContext arena, ArenaHandle handle) {
        if (arena == null || handle == null || handle.mobSpawnPositions() == null || handle.mobSpawnPositions().isEmpty()) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>(handle.mobSpawnPositions().size());
        for (ArenaHandle.BlockPos pos : handle.mobSpawnPositions()) {
            positions.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }

        ArenaTemplate template = null;
        TemplateSpawnValidator runtimeValidator = null;
        Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap = Collections.emptyMap();
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry != null) {
            template = registry.get(handle.templateId()).orElse(null);
            if (template != null) {
                slotMap = buildMobSpawnSlotMap(template, handle);
                runtimeValidator = new TemplateSpawnValidator();
            }
        }

        SpawnPools pools = buildSpawnPools(positions, slotMap);
        return new SpawnContext(positions, slotMap, template, runtimeValidator, pools);
    }

    private List<BlockPos> chooseSpawnPool(WaveDirector.SpawnRole role, SpawnPools pools, Mob mob) {
        if (role == WaveDirector.SpawnRole.RANGED && !pools.ranged.isEmpty()) {
            return pools.ranged;
        }
        if (role == WaveDirector.SpawnRole.MELEE && !pools.melee.isEmpty()) {
            return pools.melee;
        }
        if (role == WaveDirector.SpawnRole.CORNER && !pools.corner.isEmpty()) {
            return pools.corner;
        }
        boolean isRanged = mob instanceof net.minecraft.world.entity.monster.RangedAttackMob;
        if (isRanged && !pools.ranged.isEmpty()) {
            return pools.ranged;
        }
        if (!pools.melee.isEmpty()) {
            return pools.melee;
        }
        if (!pools.corner.isEmpty()) {
            return pools.corner;
        }
        return pools.all;
    }

    private String resolvePoolTag(List<BlockPos> candidatePool, SpawnPools pools) {
        if (candidatePool == pools.ranged) {
            return "ranged";
        }
        if (candidatePool == pools.melee) {
            return "melee";
        }
        if (candidatePool == pools.corner) {
            return "corner";
        }
        return "all";
    }

    private WaveDirector.SpawnRole resolveSpawnRole(@javax.annotation.Nullable SpawnAffix affix) {
        if (affix == null) {
            return WaveDirector.SpawnRole.ANY;
        }
        return switch (affix) {
            case SNIPER -> WaveDirector.SpawnRole.RANGED;
            case RUSH, BRUTE -> WaveDirector.SpawnRole.MELEE;
            case ELITE, OBJECTIVE_ELITE -> WaveDirector.SpawnRole.CORNER;
            default -> WaveDirector.SpawnRole.ANY;
        };
    }

    private Map<BlockPos, ArenaTemplate.SpawnSlot> buildMobSpawnSlotMap(
            ArenaTemplate template, ArenaHandle handle) {
        Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap = new HashMap<>();
        if (template.spawnSlots() == null) {
            return slotMap;
        }
        for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
            if (slot.tags() == null || !(slot.tags().contains("mob") || slot.tags().contains("boss"))) {
                continue;
            }
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) continue;
            int x = handle.originX() + pos[0];
            int y = resolveSpawnY(slot, template, handle.originY());
            int z = handle.originZ() + pos[2];
            slotMap.put(new BlockPos(x, y, z), slot);
        }
        return slotMap;
    }

    private int resolveSpawnY(ArenaTemplate.SpawnSlot slot, ArenaTemplate template, int originY) {
        int baseY = slot.pos() != null && slot.pos().length == 3 ? slot.pos()[1] : 0;
        int floorY = template.floor() != null ? template.floor().y() : originY;
        if (slot.yMode() == ArenaTemplate.SpawnSlot.YMode.RELATIVE_TO_FLOOR) {
            return floorY + baseY;
        }
        return baseY;
    }

    /**
     * Apply wave modifiers to a mob.
     */
    private void applyMobModifiers(Mob mob, WaveState waveState) {
        for (WaveModifier modifier : waveState.modifiers) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ARMOR));
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    // Handled in damage events
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.INVISIBILITY), Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Handled in wave setup
                }
            }
        }
    }

    private void applySpawnAffix(Mob mob, SpawnAffix affix) {
        if (mob == null || affix == null) {
            return;
        }
        if (affix == SpawnAffix.BASE) {
            return;
        }
        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * affix.hpMultiplier);
            mob.setHealth(mob.getMaxHealth());
        }
        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * affix.damageMultiplier);
        }
        var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * affix.speedMultiplier);
        }
    }

    /**
     * Apply HP and damage scaling based on player count, quest type, AND mob difficulty preset.
     * This ensures the spawned mobs match what the UI preview shows.
     */
    private void applyMultiplayerHPScaling(Mob mob, WaveState waveState) {
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.quest.getMobConfig();
        EnduranceQuestManager.ActiveQuestSession session = waveState.getSession();
        int playerCount = waveState.getPlayerCount();
        QuestType questType = waveState.getQuestType();
        float waveScale = DifficultyScaler.INSTANCE.getWaveMultiplier(waveState.waveNumber, waveState.quest.getTotalWaves());

        // Get global multipliers from session's mob pool config
        float globalHealthMult = com.devmod.endurance.config.EffectiveConfig.getGlobalHealthMult(session);
        float globalDamageMult = com.devmod.endurance.config.EffectiveConfig.getGlobalDamageMult(session);
        float globalSpeedMult = com.devmod.endurance.config.EffectiveConfig.getGlobalSpeedMult(session);

        // Apply HP scaling using MobQuestConfig (includes difficultyPreset.hpMultiplier)
        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            float baseHP = (float) healthAttr.getBaseValue();
            // Use mobConfig.getScaledHealth() which applies preset multiplier
            float scaledHP = mobConfig.getScaledHealth(playerCount, questType);
            // If mob has different base HP than estimated, scale proportionally
            float ratio = 1.0f;
            if (Math.abs(baseHP - mobConfig.baseHealth) > 0.1f && mobConfig.baseHealth > 0) {
                ratio = baseHP / mobConfig.baseHealth;
                scaledHP = scaledHP * ratio;
            }

            scaledHP *= waveScale * globalHealthMult;
            healthAttr.setBaseValue(scaledHP);
            mob.setHealth(mob.getMaxHealth());

            LOGGER.debug("[EnduranceQuest] Mob HP scaled: {} -> {} (players={}, preset={}, type={}, waveScale={}, globalMult={})",
                baseHP, scaledHP, playerCount, mobConfig.difficultyPreset.displayName, questType, waveScale, globalHealthMult);
            if (shouldLogBossHp(mobConfig)) {
                LOGGER.info("[EnduranceQuest] Boss HP scaling (pre-affix) for {}: baseAttr={}, baseEstimated={}, ratio={}, scaled={}, maxHp={}, wave={}, players={}, type={}, waveScale={}, globalMult={}, questId={}",
                    mobConfig.mobId, baseHP, mobConfig.baseHealth, ratio, scaledHP, mob.getMaxHealth(),
                    waveState.waveNumber, playerCount, questType, waveScale, globalHealthMult,
                    waveState.quest.getQuestId());
            }
        }

        // Apply damage scaling using MobQuestConfig (includes difficultyPreset.damageMultiplier)
        // Note: Apply preset multiplier even in single player for consistency with UI preview
        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            float baseDamage = (float) attackAttr.getBaseValue();
            float scaledDamage = mobConfig.getScaledDamage(playerCount);
            // Proportional scaling for different base damages
            if (Math.abs(baseDamage - mobConfig.baseDamage) > 0.1f && mobConfig.baseDamage > 0) {
                float ratio = baseDamage / mobConfig.baseDamage;
                scaledDamage = scaledDamage * ratio;
            }

            scaledDamage *= waveScale * globalDamageMult;
            attackAttr.setBaseValue(scaledDamage);

            LOGGER.debug("[EnduranceQuest] Mob DMG scaled: {} -> {} (players={}, preset={}, waveScale={}, globalMult={})",
                baseDamage, scaledDamage, playerCount, mobConfig.difficultyPreset.displayName, waveScale, globalDamageMult);
        }

        // Apply speed multiplier if session has global speed override
        if (globalSpeedMult != 1.0f) {
            var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
            if (speedAttr != null) {
                float baseSpeed = (float) speedAttr.getBaseValue();
                float scaledSpeed = baseSpeed * globalSpeedMult;
                speedAttr.setBaseValue(scaledSpeed);
                LOGGER.debug("[EnduranceQuest] Mob SPEED scaled: {} -> {} (globalMult={})",
                    baseSpeed, scaledSpeed, globalSpeedMult);
            }
        }
    }

    private static boolean shouldLogBossHp(EnduranceQuestRegistry.MobQuestConfig mobConfig) {
        if (mobConfig == null || mobConfig.mobId == null) {
            return false;
        }
        return "ender_dragon".equals(mobConfig.mobId.getPath());
    }

    private float getEliteChance(float baseChance, int waveNumber,
                                  @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (baseChance <= 0f || waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }

        // Get config values for elite chance (with session override support)
        float configBaseChance = (float) EffectiveConfig.getEliteChanceBase(session);
        float configScaling = (float) EffectiveConfig.getEliteChanceScaling(session);

        // Calculate ramp chance based on wave number with config scaling
        float rampChance;
        if (waveNumber < 5) {
            rampChance = configBaseChance;
        } else if (waveNumber < 8) {
            rampChance = configBaseChance + configScaling * (waveNumber - 4);
        } else {
            rampChance = configBaseChance + configScaling * 4 + configScaling * 0.5f * (waveNumber - 8);
        }

        return Math.min(baseChance, rampChance);
    }

    /**
     * Apply elite buffs to a mob (special stronger variant).
     */
    private void applyEliteBuffs(Mob mob, int waveNumber) {
        // Mark as elite
        mob.getPersistentData().putBoolean("endurance_elite", true);

        // Scale stats based on wave
        float scaleFactor = 1.0f + (waveNumber * 0.1f);

        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * scaleFactor * 1.5);
            mob.setHealth(mob.getMaxHealth());
        }

        var attackAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * scaleFactor);
        }

        // Give elites equipment
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Objects.requireNonNull(Items.IRON_HELMET)));
        }
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Objects.requireNonNull(Items.IRON_CHESTPLATE)));
        }

        // Visual indicator - glowing effect
        mob.setGlowingTag(true);

        // Effects
        mob.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Handle mob death in a wave.
     */
    public void handleMobDeath(UUID mobId,
                               UUID arenaId,
                               @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session,
                               @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        WaveState waveState = activeWaves.get(arenaId);
        if (waveState != null && waveState.spawnedMobs.contains(mobId)) {
            waveState.recordKill(mobId);

            // Check if this was a boss wave (using dynamic tension system)
            UUID questId = waveState.quest.getQuestId();
            if (!waveState.isPracticeMode() && BossWaveSystem.INSTANCE.isBossWave(waveState.waveNumber, questId)) {
                // End the boss fight and award bonus points
                BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.endBossFight(arenaId, true);
                if (bossFight != null) {
                    // Increment boss waves completed in quest
                    waveState.quest.incrementBossWavesCompleted();

                    // Reset tension system after boss defeat
                    TensionSystem.INSTANCE.onBossDefeated(questId);

                    LOGGER.info("[EnduranceQuest] BOSS wave {} complete! Bonus points: {}",
                        waveState.waveNumber, bossFight.getBonusPoints());
                }
            }

            if (waveState.isComplete() && session != null) {
                notifyWaveComplete(session, player, waveState);
            }
        }
    }

    /**
     * Get current wave state for an arena.
     */
    public Optional<WaveState> getWaveState(UUID arenaId) {
        return Optional.ofNullable(activeWaves.get(arenaId));
    }

    /**
     * Clean up wave state for an arena.
     */
    public void cleanupWave(UUID arenaId, ServerLevel level) {
        WaveState state = activeWaves.remove(arenaId);
        if (state != null) {
            // Remove any remaining mobs
            for (UUID mobId : state.spawnedMobs) {
                Entity entity = level.getEntity(Objects.requireNonNull(mobId));
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Clear wave state only if the wave is already complete.
     * This is used when transitioning between waves to allow the next wave to start.
     * Does not despawn mobs (they should already be gone when wave completes).
     *
     * @param arenaId The arena ID to clear wave state for
     * @return true if a completed wave state was removed, false otherwise
     */
    public boolean clearCompletedWaveState(UUID arenaId) {
        WaveState state = activeWaves.get(arenaId);
        if (state != null && state.isComplete()) {
            activeWaves.remove(arenaId);
            LOGGER.debug("[WaveManager] Cleared completed wave state for arena {}", arenaId);
            return true;
        }
        return false;
    }

    // =========================================================================
    // DEPRECATED API ISOLATION
    // =========================================================================

    /**
     * Isolates the deprecated finalizeSpawn call.
     * The Mob.finalizeSpawn API is marked deprecated but is still the correct way
     * to initialize mob attributes and equipment for programmatic spawns.
     */
    @SuppressWarnings("deprecation")
    private static void finalizeMobSpawn(Mob mob, ServerLevel level, BlockPos spawnPos) {
        mob.finalizeSpawn(level, Objects.requireNonNull(level.getCurrentDifficultyAt(Objects.requireNonNull(spawnPos))),
            MobSpawnType.MOB_SUMMONED, null);
    }

    /**
     * Awakens AI for a spawned mob, registering custom targeting and attack goals.
     * This ensures mobs actively target and attack players in Endurance Quests.
     */
    @SuppressWarnings("unchecked")
    private void awakeMobAI(Mob mob, ServerLevel level) {
        try {
            // Register custom targeting goal
            mob.targetSelector.addGoal(1, new com.devmod.endurance.ai.EnduranceTargetPlayerGoal(mob));

            // Register custom attack goal
            mob.goalSelector.addGoal(2, new com.devmod.endurance.ai.EnduranceMeleeAttackGoal(mob, 1.0, true));

            // Tick sensing and selectors
            mob.getSensing().tick();
            mob.targetSelector.tick();
            mob.goalSelector.tick();

            // Tick brain for Brain API mobs
            net.minecraft.world.entity.ai.Brain<Mob> brain =
                (net.minecraft.world.entity.ai.Brain<Mob>) mob.getBrain();
            brain.tick(Objects.requireNonNull(level, "level"), mob);

            mob.setAggressive(true);

            LOGGER.debug("[EnduranceQuest] Registered EnduranceAI for {} (target goals={}, behavior goals={})",
                mob.getType().toString(),
                mob.targetSelector.getAvailableGoals().size(),
                mob.goalSelector.getAvailableGoals().size());

        } catch (Exception e) {
            LOGGER.warn("[EnduranceQuest] Failed to awaken AI for {}: {}",
                mob.getType().toString(), e.getMessage());
        }
    }
}

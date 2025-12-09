package com.frenkvs.devmod.endurance;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages wave spawning, mob buffs, and wave progression for Endurance Quests.
 */
public class WaveManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveManager.class);

    public static final WaveManager INSTANCE = new WaveManager();

    private final Random random = new Random();

    // Active wave states per arena (ConcurrentHashMap for thread-safety in multiplayer)
    private final Map<UUID, WaveState> activeWaves = new java.util.concurrent.ConcurrentHashMap<>();

    private WaveManager() {}

    /**
     * State of an active wave.
     */
    public static class WaveState {
        private final UUID arenaId;
        private final EnduranceQuest quest;
        private final int waveNumber;
        private final List<UUID> spawnedMobs = java.util.Collections.synchronizedList(new ArrayList<>());
        private int totalToSpawn;
        private int spawned = 0;
        private int killed = 0;
        private long waveStartTime;
        private boolean complete = false;

        // Wave modifiers (roguelike elements)
        private final Set<WaveModifier> modifiers = new HashSet<>();

        public WaveState(UUID arenaId, EnduranceQuest quest, int waveNumber) {
            this.arenaId = arenaId;
            this.quest = quest;
            this.waveNumber = waveNumber;
            this.totalToSpawn = quest.getCurrentWaveMobCount();
            this.waveStartTime = System.currentTimeMillis();
        }

        public UUID getArenaId() { return arenaId; }
        public EnduranceQuest getQuest() { return quest; }
        public int getWaveNumber() { return waveNumber; }
        public List<UUID> getSpawnedMobs() { return spawnedMobs; }
        public int getTotalToSpawn() { return totalToSpawn; }
        public int getSpawned() { return spawned; }
        public int getKilled() { return killed; }
        public long getWaveStartTime() { return waveStartTime; }
        public boolean isComplete() { return complete; }
        public Set<WaveModifier> getModifiers() { return modifiers; }

        public int getRemainingMobs() {
            return spawnedMobs.size() - killed;
        }

        public void recordKill() {
            killed++;
            if (killed >= totalToSpawn && spawned >= totalToSpawn) {
                complete = true;
            }
        }

        public void addSpawnedMob(UUID mobId) {
            spawnedMobs.add(mobId);
            spawned++;
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
        ArenaManager.Arena arena = session.getArena();
        int waveNumber = quest.getCurrentWave();

        WaveState waveState = new WaveState(arena.getId(), quest, waveNumber);

        // Check if this is a boss wave (every 5 waves)
        if (BossWaveSystem.INSTANCE.isBossWave(waveNumber)) {
            // Start boss wave instead of normal wave
            BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.startBossWave(session, waveNumber);
            if (bossFight != null && bossFight.getBossEntity() != null) {
                // Track the boss as the only mob in this wave
                waveState.totalToSpawn = 1;
                waveState.addSpawnedMob(bossFight.getBossEntity().getUUID());

                // Mark as boss wave
                waveState.modifiers.add(WaveModifier.HEALTH_BOOST); // Visual indicator

                activeWaves.put(arena.getId(), waveState);

                LOGGER.info("[EnduranceQuest] Started BOSS wave {} with {} archetype",
                    waveNumber, bossFight.getArchetype().displayName);

                return waveState;
            }
            // Fallback to normal wave if boss spawn fails
            LOGGER.warn("[EnduranceQuest] Boss spawn failed for wave {}, falling back to normal wave", waveNumber);
        }

        // Apply roguelike modifiers (chance increases with wave number)
        applyWaveModifiers(waveState);

        // Adjust spawn count for modifiers
        if (waveState.modifiers.contains(WaveModifier.DOUBLE_SPAWN)) {
            waveState.totalToSpawn *= 2;
        }

        activeWaves.put(arena.getId(), waveState);

        // Spawn initial batch
        spawnWaveMobs(waveState, arena);

        LOGGER.info("[EnduranceQuest] Started wave {} with {} mobs (modifiers: {})",
            waveState.waveNumber, waveState.totalToSpawn, waveState.modifiers);

        return waveState;
    }

    /**
     * Apply random modifiers to a wave based on wave number.
     */
    private void applyWaveModifiers(WaveState waveState) {
        int waveNum = waveState.waveNumber;

        // Chance for modifiers increases with wave number
        float modifierChance = Math.min(0.1f + (waveNum * 0.05f), 0.6f);

        for (WaveModifier modifier : WaveModifier.values()) {
            if (random.nextFloat() < modifierChance) {
                waveState.modifiers.add(modifier);

                // Limit to max 3 modifiers
                if (waveState.modifiers.size() >= 3) break;
            }
        }
    }

    /**
     * Spawn mobs for the current wave.
     */
    private void spawnWaveMobs(WaveState waveState, ArenaManager.Arena arena) {
        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.quest.getMobConfig();
        EntityType<?> entityType = mobConfig.entityType;

        int toSpawn = waveState.totalToSpawn - waveState.spawned;
        List<BlockPos> spawnPositions = arena.getDistributedSpawnPositions(toSpawn);

        for (int i = 0; i < toSpawn && i < spawnPositions.size(); i++) {
            BlockPos spawnPos = spawnPositions.get(i);

            Entity entity = entityType.create(level);
            if (entity instanceof Mob mob) {
                // Position the mob
                mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // Apply wave modifiers
                applyMobModifiers(mob, waveState);

                // Apply elite chance buffs
                if (random.nextFloat() < mobConfig.eliteChance) {
                    applyEliteBuffs(mob, waveState.waveNumber);
                }

                // Finalize spawn
                @SuppressWarnings("deprecation")
                var ignored = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.MOB_SUMMONED, null);

                // Add to world
                level.addFreshEntity(mob);

                // Track the mob
                waveState.addSpawnedMob(mob.getUUID());

                // Tag mob as quest mob for tracking
                CompoundTag tag = mob.getPersistentData();
                tag.putUUID("endurance_quest_id", waveState.quest.getQuestId());
                tag.putUUID("endurance_arena_id", arena.getId());
            }
        }
    }

    /**
     * Apply wave modifiers to a mob.
     */
    private void applyMobModifiers(Mob mob, WaveState waveState) {
        for (WaveModifier modifier : waveState.modifiers) {
            switch (modifier) {
                case SPEED_BOOST -> {
                    var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.25);
                    }
                }
                case DAMAGE_BOOST -> {
                    var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(attackAttr.getBaseValue() * 1.25);
                    }
                }
                case HEALTH_BOOST -> {
                    var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                    if (healthAttr != null) {
                        healthAttr.setBaseValue(healthAttr.getBaseValue() * 1.5);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }
                case ARMOR_BOOST -> {
                    var armorAttr = mob.getAttribute(Attributes.ARMOR);
                    if (armorAttr != null) {
                        armorAttr.setBaseValue(armorAttr.getBaseValue() + 8);
                    }
                }
                case FIRE_ASPECT -> {
                    // Handled in damage events
                    mob.getPersistentData().putBoolean("endurance_fire_aspect", true);
                }
                case INVISIBILITY -> {
                    mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                }
                case REGEN -> {
                    mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 0, false, false));
                }
                case DOUBLE_SPAWN -> {
                    // Handled in wave setup
                }
            }
        }
    }

    /**
     * Apply elite buffs to a mob (special stronger variant).
     */
    private void applyEliteBuffs(Mob mob, int waveNumber) {
        // Mark as elite
        mob.getPersistentData().putBoolean("endurance_elite", true);

        // Scale stats based on wave
        float scaleFactor = 1.0f + (waveNumber * 0.1f);

        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * scaleFactor * 1.5);
            mob.setHealth(mob.getMaxHealth());
        }

        var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * scaleFactor);
        }

        // Give elites equipment
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        }
        if (random.nextBoolean()) {
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        }

        // Visual indicator - glowing effect
        mob.setGlowingTag(true);

        // Effects
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Handle mob death in a wave.
     */
    public void handleMobDeath(UUID mobId, UUID arenaId) {
        WaveState waveState = activeWaves.get(arenaId);
        if (waveState != null && waveState.spawnedMobs.contains(mobId)) {
            waveState.recordKill();

            // Check if this was a boss wave
            if (BossWaveSystem.INSTANCE.isBossWave(waveState.waveNumber)) {
                // End the boss fight and award bonus points
                BossWaveSystem.BossFight bossFight = BossWaveSystem.INSTANCE.endBossFight(arenaId, true);
                if (bossFight != null) {
                    // Increment boss waves completed in quest
                    waveState.quest.incrementBossWavesCompleted();

                    LOGGER.info("[EnduranceQuest] BOSS wave {} complete! Bonus points: {}",
                        waveState.waveNumber, bossFight.getBonusPoints());
                }
            }

            if (waveState.isComplete()) {
                LOGGER.info("[EnduranceQuest] Wave {} complete!", waveState.waveNumber);
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
     * Check if wave is complete.
     */
    public boolean isWaveComplete(UUID arenaId) {
        WaveState state = activeWaves.get(arenaId);
        return state != null && state.isComplete();
    }

    /**
     * Clean up wave state for an arena.
     */
    public void cleanupWave(UUID arenaId, ServerLevel level) {
        WaveState state = activeWaves.remove(arenaId);
        if (state != null) {
            // Remove any remaining mobs
            for (UUID mobId : state.spawnedMobs) {
                Entity entity = level.getEntity(mobId);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    /**
     * Get wave progress percentage.
     */
    public float getWaveProgress(UUID arenaId) {
        WaveState state = activeWaves.get(arenaId);
        if (state == null || state.totalToSpawn == 0) return 0;
        return (float) state.killed / state.totalToSpawn;
    }

    /**
     * Get remaining mob count in wave.
     */
    public int getRemainingMobs(UUID arenaId) {
        WaveState state = activeWaves.get(arenaId);
        return state != null ? state.getRemainingMobs() : 0;
    }
}

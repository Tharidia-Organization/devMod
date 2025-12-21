package com.frenkvs.devmod.endurance;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.SpawnSlotValidator;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.endurance.EnduranceTelemetryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss Wave System - Special waves with unique mechanics.
 *
 * Every 5 waves (or configurable), a boss wave occurs with:
 * - A powerful "Champion" version of the mob
 * - Special attack patterns
 * - Phase transitions
 * - Unique rewards
 *
 * Boss Types:
 * - BERSERKER: High damage, charges, ground slam
 * - SUMMONER: Spawns minions, defensive shields
 * - JUGGERNAUT: Tanky, reflects damage, slow but devastating
 * - ASSASSIN: Fast, teleports, backstab attacks
 * - ELEMENTAL: Elemental attacks, area denial
 */
public class BossWaveSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BossWaveSystem.class);

    public static final BossWaveSystem INSTANCE = new BossWaveSystem();

    // Active boss fights
    private final Map<UUID, BossFight> activeBosses = new ConcurrentHashMap<>();

    private final Random random = new Random();

    /**
     * Boss archetype determines attack patterns and abilities.
     */
    public enum BossArchetype {
        BERSERKER("Berserker", 0xFF4444, 1.5f, 2.0f, 0.8f,
            "Aggressive melee boss with charge attacks and ground slams"),
        SUMMONER("Summoner", 0x9944FF, 0.8f, 1.0f, 1.5f,
            "Spawns minions and creates protective barriers"),
        JUGGERNAUT("Juggernaut", 0x44FF44, 0.5f, 0.7f, 3.0f,
            "Extremely tanky, reflects damage, devastating hits"),
        ASSASSIN("Assassin", 0x4444FF, 2.0f, 1.5f, 0.6f,
            "Fast and elusive, teleports behind players"),
        ELEMENTAL("Elemental", 0xFFAA00, 1.2f, 1.8f, 1.0f,
            "Harnesses elemental powers for area attacks");

        public final String displayName;
        public final int color;
        public final float speedMultiplier;
        public final float damageMultiplier;
        public final float healthMultiplier;
        public final String description;

        BossArchetype(String displayName, int color, float speed, float damage, float health, String desc) {
            this.displayName = displayName;
            this.color = color;
            this.speedMultiplier = speed;
            this.damageMultiplier = damage;
            this.healthMultiplier = health;
            this.description = desc;
        }
    }

    /**
     * Boss attack abilities.
     */
    public enum BossAbility {
        // Berserker abilities
        CHARGE("Charge", 100, "Charges towards player at high speed"),
        GROUND_SLAM("Ground Slam", 150, "Slams ground, damaging nearby players"),
        ENRAGE("Enrage", 200, "Enters enraged state with increased damage"),

        // Summoner abilities
        SUMMON_MINIONS("Summon Minions", 120, "Summons 3-5 weaker copies"),
        BARRIER("Barrier", 180, "Creates damage-absorbing shield"),
        LIFE_LINK("Life Link", 250, "Links health with minions"),

        // Juggernaut abilities
        REFLECT("Reflect", 100, "Reflects 50% of damage taken"),
        UNSTOPPABLE("Unstoppable", 200, "Becomes immune to knockback and stuns"),
        EARTHQUAKE("Earthquake", 180, "Massive area damage + slow"),

        // Assassin abilities
        SHADOW_STEP("Shadow Step", 80, "Teleports behind player"),
        MARKED_FOR_DEATH("Mark", 150, "Marks player for bonus damage"),
        SMOKE_BOMB("Smoke Bomb", 120, "Becomes invisible briefly"),

        // Elemental abilities
        FIREBALL_BARRAGE("Fireball Barrage", 100, "Launches multiple fireballs"),
        FROST_NOVA("Frost Nova", 150, "Freezes nearby players"),
        LIGHTNING_STORM("Lightning Storm", 200, "Calls down lightning strikes"),
        ELEMENTAL_SHIFT("Elemental Shift", 250, "Changes element and gains new powers");

        public final String displayName;
        public final int cooldownTicks;
        public final String description;

        BossAbility(String displayName, int cooldownTicks, String description) {
            this.displayName = displayName;
            this.cooldownTicks = cooldownTicks;
            this.description = description;
        }
    }

    /**
     * Boss fight state.
     */
    public static class BossFight {
        private final UUID bossId;
        private final UUID arenaId;
        private final BossArchetype archetype;
        private final int waveNumber;
        private final ResourceLocation baseMobType;

        // Boss entity
        private UUID bossEntityId;
        private Mob bossEntity;

        // Fight state
        private BossPhase currentPhase = BossPhase.PHASE_1;
        private float maxHealth;
        private float currentHealth;
        private boolean isEnraged = false;
        private boolean hasShield = false;
        private float shieldHealth = 0;

        // Ability cooldowns
        private final Map<BossAbility, Integer> abilityCooldowns = new EnumMap<>(BossAbility.class);

        // Minions (for Summoner)
        private final List<UUID> activeMinions = new ArrayList<>();
        public static final int MAX_MINIONS = 8;

        // Combat tracking
        private int ticksAlive = 0;
        private float totalDamageDealt = 0; // Damage dealt to players by this boss
        private float totalDamageTaken = 0; // Damage taken by this boss
        private int abilitiesUsed = 0;

        // Rewards
        private int bonusPoints = 0;
        private boolean perfectFight = true; // No damage taken


        public BossFight(UUID bossId, UUID arenaId, BossArchetype archetype, int waveNumber, ResourceLocation baseMobType) {
            this.bossId = bossId;
            this.arenaId = arenaId;
            this.archetype = archetype;
            this.waveNumber = waveNumber;
            this.baseMobType = baseMobType;

            // Initialize cooldowns using static method to avoid this-escape
            for (BossAbility ability : getAbilitiesForArchetype(archetype)) {
                abilityCooldowns.put(ability, 0);
            }
        }

        /** Returns abilities for this boss's archetype. */
        public List<BossAbility> getArchetypeAbilities() {
            return getAbilitiesForArchetype(archetype);
        }

        /** Static helper to get abilities for an archetype (avoids this-escape in constructor). */
        public static List<BossAbility> getAbilitiesForArchetype(BossArchetype archetype) {
            return switch (archetype) {
                case BERSERKER -> List.of(BossAbility.CHARGE, BossAbility.GROUND_SLAM, BossAbility.ENRAGE);
                case SUMMONER -> List.of(BossAbility.SUMMON_MINIONS, BossAbility.BARRIER, BossAbility.LIFE_LINK);
                case JUGGERNAUT -> List.of(BossAbility.REFLECT, BossAbility.UNSTOPPABLE, BossAbility.EARTHQUAKE);
                case ASSASSIN -> List.of(BossAbility.SHADOW_STEP, BossAbility.MARKED_FOR_DEATH, BossAbility.SMOKE_BOMB);
                case ELEMENTAL -> List.of(BossAbility.FIREBALL_BARRAGE, BossAbility.FROST_NOVA, BossAbility.LIGHTNING_STORM);
            };
        }

        // Getters
        public UUID getBossId() { return bossId; }
        public UUID getArenaId() { return arenaId; }
        public BossArchetype getArchetype() { return archetype; }
        public int getWaveNumber() { return waveNumber; }
        public ResourceLocation getBaseMobType() { return baseMobType; }
        public UUID getBossEntityId() { return bossEntityId; }
        public BossPhase getCurrentPhase() { return currentPhase; }
        public float getHealthPercent() { return maxHealth > 0 ? currentHealth / maxHealth : 0; }
        public boolean isEnraged() { return isEnraged; }
        public boolean hasShield() { return hasShield; }
        public float getShieldHealth() { return shieldHealth; }
        public Mob getBossEntity() { return bossEntity; }
        public List<UUID> getActiveMinions() { return activeMinions; }
        public boolean isPerfectFight() { return perfectFight; }
        public int getBonusPoints() { return bonusPoints; }

        public void setBossEntity(Mob entity) {
            this.bossEntity = entity;
            this.bossEntityId = entity.getUUID();
        }

        public void setMaxHealth(float health) {
            this.maxHealth = health;
            this.currentHealth = health;
        }

        public void takeDamage(float damage) {
            if (hasShield) {
                shieldHealth -= damage;
                if (shieldHealth <= 0) {
                    hasShield = false;
                    damage = -shieldHealth;
                } else {
                    return;
                }
            }
            currentHealth -= damage;
            totalDamageTaken += damage;
            checkPhaseTransition();
        }

        public void activateShield(float health) {
            hasShield = true;
            shieldHealth = health;
        }

        public void setEnraged(boolean enraged) {
            this.isEnraged = enraged;
        }

        public void onPlayerDamaged(float damage) {
            perfectFight = false;
            totalDamageDealt += damage;
        }

        private void checkPhaseTransition() {
            float healthPercent = getHealthPercent();
            BossPhase newPhase = currentPhase;

            if (healthPercent <= 0.25f && currentPhase.ordinal() < BossPhase.PHASE_3.ordinal()) {
                newPhase = BossPhase.PHASE_3;
            } else if (healthPercent <= 0.50f && currentPhase.ordinal() < BossPhase.PHASE_2.ordinal()) {
                newPhase = BossPhase.PHASE_2;
            }

            if (newPhase != currentPhase) {
                currentPhase = newPhase;
                onPhaseTransition(newPhase);
            }
        }

        private void onPhaseTransition(BossPhase newPhase) {
            bonusPoints += 500 * newPhase.ordinal();
            LOGGER.info("[BossWave] Boss {} entered {}", bossId, newPhase);
        }

        public boolean canUseAbility(BossAbility ability) {
            return abilityCooldowns.getOrDefault(ability, 0) <= 0;
        }

        public void useAbility(BossAbility ability) {
            abilityCooldowns.put(ability, ability.cooldownTicks);
            abilitiesUsed++;
        }

        public void tick() {
            ticksAlive++;
            // Reduce cooldowns
            abilityCooldowns.replaceAll((ability, cooldown) -> Math.max(0, cooldown - 1));
        }
    }

    /**
     * Boss fight phases.
     */
    public enum BossPhase {
        PHASE_1("Phase 1", 1.0f),   // 100%-51% health
        PHASE_2("Phase 2", 1.25f),  // 50%-26% health - more aggressive
        PHASE_3("Phase 3", 1.5f);   // 25%-0% health - desperate, strongest

        public final String displayName;
        public final float damageMultiplier;

        BossPhase(String displayName, float damageMultiplier) {
            this.displayName = displayName;
            this.damageMultiplier = damageMultiplier;
        }
    }

    // ========== Boss Wave Management ==========

    // === Boss Alert Constants ===
    private static final long BOSS_ALERT_DURATION_MS = 3000;

    /**
     * Check if a wave should be a boss wave.
     */
    public boolean isBossWave(int waveNumber) {
        return waveNumber > 0 && waveNumber % 5 == 0;
    }

    /**
     * Send boss alert to player 3 seconds before boss spawn.
     * Should be called when transitioning to a boss wave.
     */
    public void triggerBossAlert(net.minecraft.server.level.ServerPlayer player, String bossType) {
        com.frenkvs.devmod.NetworkHandler.sendBossAlert(player, BOSS_ALERT_DURATION_MS, bossType);
    }

    /**
     * Start a boss wave.
     */
    public BossFight startBossWave(EnduranceQuestManager.ActiveQuestSession session, int waveNumber) {
        EnduranceQuest quest = session.getQuest();
        ArenaContext arena = session.getArena();
        ServerLevel level = Objects.requireNonNull(arena.getLevel());
        BlockPos center = Objects.requireNonNull(arena.getCenter());
        ArenaHandle handle = session.getArenaHandle();

        // Get multiplayer scaling parameters
        int playerCount = session.getPlayerCount();
        QuestType questType = session.getQuestType();

        // Select random archetype
        BossArchetype archetype = selectArchetype(waveNumber);

        // Create boss fight
        UUID bossId = UUID.randomUUID();
        BossFight fight = new BossFight(bossId, arena.getId(), archetype, waveNumber, quest.getMobId());

        // Spawn boss with multiplayer scaling
        BlockPos spawnPos = resolveBossSpawnPosition(arena, handle, quest.getQuestId(), waveNumber);
        Mob boss = spawnBoss(arena, spawnPos, quest.getMobConfig(), archetype, waveNumber, quest.getQuestId(), playerCount, questType, handle);
        if (boss != null) {
            fight.setBossEntity(boss);
            fight.setMaxHealth(boss.getMaxHealth());
            activeBosses.put(arena.getId(), fight);
            if (handle != null && spawnPos != null) {
                EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                    quest.getQuestId(), handle, spawnPos);
            }

            // Announce boss
            announceBoss(level, spawnPos != null ? spawnPos : center, archetype, waveNumber);

            // Telemetry: record boss wave start
            EnduranceTelemetryService.INSTANCE.recordBossWaveStart(
                quest.getQuestId(), waveNumber, archetype.name(), boss.getMaxHealth(), playerCount
            );

            LOGGER.info("[BossWave] Started boss wave {} with {} archetype (players={}, type={})",
                waveNumber, archetype, playerCount, questType);
        }

        return fight;
    }

    /**
     * Select archetype based on wave number and randomness.
     */
    private BossArchetype selectArchetype(int waveNumber) {
        BossArchetype[] archetypes = BossArchetype.values();

        // Higher waves can have any archetype, lower waves are simpler
        int maxIndex = Math.min(archetypes.length, 1 + waveNumber / 5);
        return archetypes[random.nextInt(maxIndex)];
    }

    /**
     * Spawn the boss mob with enhanced stats and multiplayer scaling.
     */
    private Mob spawnBoss(ArenaContext arena,
                          @javax.annotation.Nullable BlockPos spawnPos,
                          EnduranceQuestRegistry.MobQuestConfig mobConfig,
                          BossArchetype archetype, int waveNumber, UUID questId,
                          int playerCount, QuestType questType,
                          @javax.annotation.Nullable ArenaHandle handle) {
        ServerLevel level = Objects.requireNonNull(arena.getLevel());
        BlockPos center = Objects.requireNonNull(arena.getCenter());
        BossArchetype safeArchetype = Objects.requireNonNull(archetype);
        UUID arenaId = Objects.requireNonNull(arena.getId());
        UUID safeQuestId = Objects.requireNonNull(questId);

        Entity entity = mobConfig.entityType.create(level);
        if (!(entity instanceof Mob mob)) return null;

        // Position at center
        BlockPos resolvedPos = spawnPos != null ? spawnPos : center;
        mob.setPos(resolvedPos.getX() + 0.5, resolvedPos.getY(), resolvedPos.getZ() + 0.5);

        // Apply boss stats with multiplayer scaling
        float waveScaling = 1.0f + (waveNumber * 0.1f);

        var healthAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
        if (healthAttr != null) {
            double baseHealth = healthAttr.getBaseValue();
            double bossHealth = baseHealth * archetype.healthMultiplier * waveScaling * 5; // 5x base for boss

            // Apply multiplayer HP scaling using DifficultyScaler
            float scaledHealth = DifficultyScaler.INSTANCE.scaleBossHealth((float) bossHealth, playerCount, questType);
            healthAttr.setBaseValue(scaledHealth);
            mob.setHealth(scaledHealth);

            LOGGER.debug("[BossWave] Boss HP: {} -> {} (players={}, type={})",
                bossHealth, scaledHealth, playerCount, questType);
        }

        var damageAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (damageAttr != null) {
            double baseDamage = damageAttr.getBaseValue() * archetype.damageMultiplier * waveScaling;

            // Apply multiplayer damage scaling using DifficultyScaler
            float scaledDamage = DifficultyScaler.INSTANCE.scaleBossDamage((float) baseDamage, playerCount, questType);
            damageAttr.setBaseValue(scaledDamage);

            LOGGER.debug("[BossWave] Boss DMG: {} -> {} (players={}, type={})",
                baseDamage, scaledDamage, playerCount, questType);
        }

        var speedAttr = mob.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * archetype.speedMultiplier);
        }

        // Visual indicators
        mob.setGlowingTag(true);
        mob.setCustomName(Component.literal("§c§l" + Objects.requireNonNull(safeArchetype.displayName) + " " + Objects.requireNonNull(mobConfig.displayName)));
        mob.setCustomNameVisible(true);

        // Tag as boss (include endurance_quest_id so isQuestMob() detects it)
        CompoundTag tag = mob.getPersistentData();
        tag.putBoolean("endurance_boss", true);
        tag.putString("endurance_archetype", Objects.requireNonNull(safeArchetype.name()));
        tag.putUUID("endurance_arena_id", arenaId);
        tag.putUUID("endurance_quest_id", safeQuestId);
        if (handle != null) {
            String templateId = handle.templateId();
            if (templateId != null) {
                tag.putString("endurance_template_id", templateId);
            }
            tag.putInt("endurance_template_version", handle.templateVersion());
            String policyId = handle.policyId();
            if (policyId != null) {
                tag.putString("endurance_policy_id", policyId);
            }
            tag.putInt("endurance_policy_version", handle.policyVersion());
        }

        // Add spawn effects
        level.addFreshEntity(mob);

        // Spawn particles
        for (int i = 0; i < 50; i++) {
            double x = resolvedPos.getX() + random.nextGaussian() * 2;
            double y = resolvedPos.getY() + random.nextDouble() * 3;
            double z = resolvedPos.getZ() + random.nextGaussian() * 2;
            level.sendParticles(Objects.requireNonNull(ParticleTypes.FLAME), x, y, z, 1, 0, 0.1, 0, 0.05);
        }

        return mob;
    }

    private BlockPos resolveBossSpawnPosition(ArenaContext arena,
                                              @javax.annotation.Nullable ArenaHandle handle,
                                              UUID questId,
                                              int waveNumber) {
        BlockPos center = Objects.requireNonNull(arena.getCenter());
        if (handle == null) {
            EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                questId,
                waveNumber,
                "boss_fallback_center",
                null,
                "missing_handle"
            );
            return center;
        }
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry == null) {
            EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                questId,
                waveNumber,
                "boss_fallback_center",
                handle.templateId(),
                "registry_unavailable"
            );
            return center;
        }
        ArenaTemplate template = registry.get(handle.templateId()).orElse(null);
        if (template == null || template.spawnSlots() == null || template.spawnSlots().isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                questId,
                waveNumber,
                "boss_fallback_center",
                handle.templateId(),
                "missing_slots"
            );
            return center;
        }

        List<ArenaTemplate.SpawnSlot> bossSlots = new ArrayList<>();
        List<ArenaTemplate.SpawnSlot> mobSlots = new ArrayList<>();
        for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
            if (slot.tags() == null) {
                continue;
            }
            if (slot.tags().contains("boss")) {
                bossSlots.add(slot);
            } else if (slot.tags().contains("mob")) {
                mobSlots.add(slot);
            }
        }

        List<ArenaTemplate.SpawnSlot> candidates = !bossSlots.isEmpty() ? bossSlots : mobSlots;
        if (candidates.isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
                questId,
                waveNumber,
                "boss_fallback_center",
                handle.templateId(),
                "no_tagged_slots"
            );
            return center;
        }

        Collections.shuffle(candidates, random);
        SpawnSlotValidator validator = new SpawnSlotValidator();
        for (ArenaTemplate.SpawnSlot slot : candidates) {
            int[] pos = slot.pos();
            if (pos == null || pos.length != 3) {
                continue;
            }
            int x = handle.originX() + pos[0];
            int y = resolveSpawnY(slot, template, handle.originY());
            int z = handle.originZ() + pos[2];
            BlockPos candidate = new BlockPos(x, y, z);
            if (validator.validateAtRuntime(template.id(), slot, arena.getLevel(), candidate)) {
                return candidate;
            }
        }

        EnduranceTelemetryService.INSTANCE.recordSpawnFallback(
            questId,
            waveNumber,
            "boss_fallback_center",
            handle.templateId(),
            "runtime_validation_failed"
        );
        return center;
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
     * Announce boss spawn to players.
     */
    private void announceBoss(ServerLevel level, BlockPos pos, BossArchetype archetype, int waveNumber) {
        // Sound effect
        BlockPos safePos = Objects.requireNonNull(pos);
        level.playSound(null, safePos, Objects.requireNonNull(SoundEvents.ENDER_DRAGON_GROWL), SoundSource.HOSTILE, 1.0f, 0.5f);

        // Title announcement (would need client-side packet)
        LOGGER.info("[BossWave] BOSS WAVE {} - {} has appeared!", waveNumber, Objects.requireNonNull(archetype.displayName));
    }

    /**
     * Get active boss fight for an arena.
     */
    public Optional<BossFight> getBossFight(UUID arenaId) {
        return Optional.ofNullable(activeBosses.get(arenaId));
    }

    /**
     * End a boss fight.
     */
    public BossFight endBossFight(UUID arenaId, boolean victory) {
        BossFight fight = activeBosses.remove(arenaId);
        if (fight != null) {
            // Clean up minions
            for (UUID minionId : fight.activeMinions) {
                if (fight.bossEntity != null && fight.bossEntity.level() instanceof ServerLevel level) {
                    Entity minion = level.getEntity(Objects.requireNonNull(minionId));
                    if (minion != null) minion.discard();
                }
            }

            if (victory) {
                // Calculate bonus rewards
                int baseBonus = 1000 * fight.waveNumber;
                int phaseBonus = fight.bonusPoints;
                int perfectBonus = fight.perfectFight ? 2000 : 0;
                int speedBonus = fight.ticksAlive < 1200 ? 1500 : 0; // Under 1 minute

                fight.bonusPoints = baseBonus + phaseBonus + perfectBonus + speedBonus;

                // Telemetry: record boss defeated
                long fightDurationMs = (long) fight.ticksAlive * 50; // 20 ticks/second = 50ms/tick
                EnduranceTelemetryService.INSTANCE.recordBossDefeated(
                    fight.arenaId, fight.waveNumber, fight.archetype.name(),
                    fightDurationMs, fight.bonusPoints, fight.totalDamageTaken
                );

                LOGGER.info("[BossWave] Boss defeated! Bonus: {} (perfect: {}, speed: {}, abilities: {}, dmgDealt: {})",
                    fight.bonusPoints, fight.perfectFight, fight.ticksAlive < 1200, fight.abilitiesUsed, fight.totalDamageDealt);
            }
        }
        return fight;
    }

    // ========== Boss AI / Abilities ==========

    /**
     * Process boss AI tick.
     */
    public void tickBoss(BossFight fight, ServerPlayer target) {
        if (fight.bossEntity == null || !fight.bossEntity.isAlive()) return;

        fight.tick();
        Mob boss = fight.bossEntity;
        ServerLevel level = Objects.requireNonNull((ServerLevel) boss.level());
        ServerPlayer safeTarget = Objects.requireNonNull(target);

        // Check for ability use based on archetype
        for (BossAbility ability : fight.getArchetypeAbilities()) {
            if (fight.canUseAbility(ability) && shouldUseAbility(fight, ability, safeTarget)) {
                executeAbility(fight, ability, safeTarget, level);
                fight.useAbility(ability);
                break; // One ability per tick
            }
        }

        // Phase-specific behavior
        switch (fight.getCurrentPhase()) {
            case PHASE_1 -> {
                // Initial phase - standard behavior, no special actions
            }
            case PHASE_2 -> {
                // More aggressive in phase 2
                if (!fight.isEnraged() && fight.archetype == BossArchetype.BERSERKER) {
                    executeAbility(fight, BossAbility.ENRAGE, target, level);
                }
            }
            case PHASE_3 -> {
                // Desperate attacks in phase 3
                if (fight.archetype == BossArchetype.SUMMONER && fight.activeMinions.size() < 3) {
                    executeAbility(fight, BossAbility.SUMMON_MINIONS, target, level);
                }
            }
        }
    }

    /**
     * Determine if boss should use an ability.
     */
    private boolean shouldUseAbility(BossFight fight, BossAbility ability, ServerPlayer target) {
        Mob boss = fight.bossEntity;
        double distanceToTarget = boss.distanceTo(Objects.requireNonNull(target));

        return switch (ability) {
            case CHARGE -> distanceToTarget > 8 && distanceToTarget < 20;
            case GROUND_SLAM -> distanceToTarget < 5;
            case ENRAGE -> fight.getHealthPercent() < 0.5f && !fight.isEnraged();
            case SUMMON_MINIONS -> fight.activeMinions.size() < BossFight.MAX_MINIONS / 2;
            case BARRIER -> fight.getHealthPercent() < 0.7f && !fight.hasShield();
            case LIFE_LINK -> !fight.activeMinions.isEmpty();
            case REFLECT -> random.nextFloat() < 0.3f;
            case UNSTOPPABLE -> fight.getCurrentPhase() == BossPhase.PHASE_3;
            case EARTHQUAKE -> distanceToTarget < 10;
            case SHADOW_STEP -> distanceToTarget > 10 || random.nextFloat() < 0.2f;
            case MARKED_FOR_DEATH -> random.nextFloat() < 0.15f;
            case SMOKE_BOMB -> fight.getHealthPercent() < 0.3f;
            case FIREBALL_BARRAGE -> distanceToTarget > 5;
            case FROST_NOVA -> distanceToTarget < 8;
            case LIGHTNING_STORM -> random.nextFloat() < 0.1f;
            case ELEMENTAL_SHIFT -> fight.getCurrentPhase() != BossPhase.PHASE_1;
        };
    }

    /**
     * Execute a boss ability.
     */
    private void executeAbility(BossFight fight, BossAbility ability, ServerPlayer target, ServerLevel level) {
        Mob boss = fight.bossEntity;

        LOGGER.debug("[BossWave] Boss using ability: {}", ability);

        // Telemetry: record boss ability (playersHit and damage will be updated by specific abilities)
        EnduranceTelemetryService.INSTANCE.recordBossAbility(
            fight.arenaId, fight.archetype.name(), ability.displayName, 0, 0
        );

        switch (ability) {
            case CHARGE -> executeCharge(boss, target, level);
            case GROUND_SLAM -> executeGroundSlam(fight, boss, target, level);
            case ENRAGE -> executeEnrage(fight, boss, level);
            case SUMMON_MINIONS -> executeSummonMinions(fight, boss, level);
            case BARRIER -> executeBarrier(fight, boss, level);
            case LIFE_LINK -> executeLifeLink(fight, boss);
            case REFLECT -> { /* Handled in damage processing */ }
            case UNSTOPPABLE -> executeUnstoppable(boss, level);
            case EARTHQUAKE -> executeEarthquake(boss, target, level);
            case SHADOW_STEP -> executeShadowStep(fight, boss, target, level);
            case MARKED_FOR_DEATH -> executeMarkForDeath(target, level);
            case SMOKE_BOMB -> executeSmokeBomb(boss, level);
            case FIREBALL_BARRAGE -> executeFireballBarrage(boss, target, level);
            case FROST_NOVA -> executeFrostNova(boss, level);
            case LIGHTNING_STORM -> executeLightningStorm(boss, target, level);
            case ELEMENTAL_SHIFT -> executeElementalShift(fight, boss, level);
        }
    }

    // ========== Ability Implementations ==========

    private void executeCharge(Mob boss, ServerPlayer target, ServerLevel level) {
        Vec3 direction = Objects.requireNonNull(
            Objects.requireNonNull(target.position()).subtract(Objects.requireNonNull(boss.position()))
        ).normalize().scale(2.0);
        boss.setDeltaMovement(Objects.requireNonNull(direction));
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.MOVEMENT_SPEED), 40, 3));

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.RAVAGER_ATTACK), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void executeGroundSlam(BossFight fight, Mob boss, ServerPlayer target, ServerLevel level) {
        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        float damage = 8.0f;

        // Damage nearby players
        AABB area = Objects.requireNonNull(new AABB(pos).inflate(5));
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            player.hurt(Objects.requireNonNull(level.damageSources().mobAttack(boss)), damage);
            player.knockback(1.5f, boss.getX() - player.getX(), boss.getZ() - player.getZ());
            fight.onPlayerDamaged(damage);
        }

        // Particles
        for (int i = 0; i < 30; i++) {
            double x = pos.getX() + random.nextGaussian() * 3;
            double z = pos.getZ() + random.nextGaussian() * 3;
            level.sendParticles(Objects.requireNonNull(ParticleTypes.EXPLOSION), x, pos.getY(), z, 1, 0, 0, 0, 0);
        }

        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.GENERIC_EXPLODE.value()), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private void executeEnrage(BossFight fight, Mob boss, ServerLevel level) {
        fight.setEnraged(true);

        var damageAttr = boss.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * 1.5);
        }

        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_BOOST), 6000, 1));
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.MOVEMENT_SPEED), 6000, 1));

        // Visual
        for (int i = 0; i < 20; i++) {
            level.sendParticles(Objects.requireNonNull(ParticleTypes.ANGRY_VILLAGER),
                boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0.5, 0.5, 0.5, 0);
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.ENDER_DRAGON_GROWL), SoundSource.HOSTILE, 1.0f, 1.5f);
    }

    private void executeSummonMinions(BossFight fight, Mob boss, ServerLevel level) {
        int toSpawn = Math.min(3, BossFight.MAX_MINIONS - fight.activeMinions.size());

        for (int i = 0; i < toSpawn; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 3 + random.nextDouble() * 3;
            double x = boss.getX() + Math.cos(angle) * distance;
            double z = boss.getZ() + Math.sin(angle) * distance;

            Entity minion = boss.getType().create(Objects.requireNonNull(level));
            if (minion instanceof Mob minionMob) {
                minionMob.setPos(x, boss.getY(), z);

                // Minions are weaker
                var healthAttr = minionMob.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                if (healthAttr != null) {
                    healthAttr.setBaseValue(healthAttr.getBaseValue() * 0.3);
                    minionMob.setHealth((float) (healthAttr.getBaseValue()));
                }

                // Tag as minion
                UUID fightArenaId = Objects.requireNonNull(fight.arenaId);
                CompoundTag tag = minionMob.getPersistentData();
                tag.putBoolean("endurance_minion", true);
                tag.putUUID("endurance_arena_id", fightArenaId);
                CompoundTag bossTag = boss.getPersistentData();
                if (bossTag.contains("endurance_template_id")) {
                    String templateId = bossTag.getString("endurance_template_id");
                    if (templateId != null) {
                        tag.putString("endurance_template_id", templateId);
                    }
                }
                if (bossTag.contains("endurance_template_version")) {
                    tag.putInt("endurance_template_version", bossTag.getInt("endurance_template_version"));
                }
                if (bossTag.contains("endurance_policy_id")) {
                    String policyId = bossTag.getString("endurance_policy_id");
                    if (policyId != null) {
                        tag.putString("endurance_policy_id", policyId);
                    }
                }
                if (bossTag.contains("endurance_policy_version")) {
                    tag.putInt("endurance_policy_version", bossTag.getInt("endurance_policy_version"));
                }

                level.addFreshEntity(minionMob);
                fight.activeMinions.add(minionMob.getUUID());

                // Spawn particles
                level.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), x, boss.getY(), z, 20, 0.5, 1, 0.5, 0);
            }
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.EVOKER_PREPARE_SUMMON), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void executeBarrier(BossFight fight, Mob boss, ServerLevel level) {
        float shieldHealth = boss.getMaxHealth() * 0.3f;
        fight.activateShield(shieldHealth);

        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), 100, 2));

        // Visual
        for (int i = 0; i < 30; i++) {
            double angle = (i / 30.0) * Math.PI * 2;
            double x = boss.getX() + Math.cos(angle) * 2;
            double z = boss.getZ() + Math.sin(angle) * 2;
            level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD), x, boss.getY() + 1, z, 1, 0, 0, 0, 0);
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.SHIELD_BLOCK), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private void executeLifeLink(BossFight fight, Mob boss) {
        // Heal boss based on minion count
        float healAmount = fight.activeMinions.size() * 5.0f;
        boss.heal(healAmount);
    }

    private void executeUnstoppable(Mob boss, ServerLevel level) {
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), 200, 3));
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.SLOW_FALLING), 200, 0));

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.IRON_GOLEM_REPAIR), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private void executeEarthquake(Mob boss, ServerPlayer target, ServerLevel level) {
        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        AABB area = Objects.requireNonNull(new AABB(pos).inflate(12));

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            float distance = player.distanceTo(boss);
            float damage = Math.max(2, 15 - distance);
            player.hurt(Objects.requireNonNull(level.damageSources().mobAttack(boss)), damage);
            player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.MOVEMENT_SLOWDOWN), 60, 2));
        }

        // Screen shake effect would need client packet
        for (int i = 0; i < 50; i++) {
            double x = pos.getX() + random.nextGaussian() * 8;
            double z = pos.getZ() + random.nextGaussian() * 8;
            level.sendParticles(Objects.requireNonNull(ParticleTypes.CAMPFIRE_COSY_SMOKE), x, pos.getY(), z, 1, 0, 0.5, 0, 0);
        }

        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.GENERIC_EXPLODE.value()), SoundSource.HOSTILE, 2.0f, 0.3f);
    }

    private void executeShadowStep(BossFight fight, Mob boss, ServerPlayer target, ServerLevel level) {
        // Particles at old position
        level.sendParticles(Objects.requireNonNull(ParticleTypes.LARGE_SMOKE), boss.getX(), boss.getY(), boss.getZ(), 20, 0.5, 1, 0.5, 0);

        // Calculate teleport destination behind player
        Vec3 playerPos = Objects.requireNonNull(target.position());
        Vec3 lookDir = Objects.requireNonNull(target.getLookAngle());
        Vec3 backOffset = Objects.requireNonNull(lookDir.scale(2));
        Vec3 behindPlayer = Objects.requireNonNull(playerPos.subtract(backOffset));

        // Bounds check - ensure teleport stays within arena
        Optional<EnduranceQuestManager.ActiveQuestSession> sessionOpt =
            EnduranceQuestManager.INSTANCE.getActiveSession(target);
        if (sessionOpt.isPresent()) {
            ArenaContext arena = sessionOpt.get().getArena();
            int halfSize = arena.getSize() / 2;
            // Clamp position to arena bounds
            double minX = arena.getCenter().getX() - halfSize;
            double maxX = arena.getCenter().getX() + halfSize;
            double minZ = arena.getCenter().getZ() - halfSize;
            double maxZ = arena.getCenter().getZ() + halfSize;

            behindPlayer = Objects.requireNonNull(new Vec3(
                Math.max(minX, Math.min(maxX, behindPlayer.x)),
                behindPlayer.y,
                Math.max(minZ, Math.min(maxZ, behindPlayer.z))
            ));
        }

        boss.teleportTo(behindPlayer.x, behindPlayer.y, behindPlayer.z);

        // Particles at new position
        level.sendParticles(Objects.requireNonNull(ParticleTypes.REVERSE_PORTAL), boss.getX(), boss.getY(), boss.getZ(), 20, 0.5, 1, 0.5, 0);

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.ENDERMAN_TELEPORT), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void executeMarkForDeath(ServerPlayer target, ServerLevel level) {
        target.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.GLOWING), 200, 0));
        target.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.UNLUCK), 200, 1));

        // Tag for bonus damage
        CompoundTag tag = target.getPersistentData();
        tag.putLong("marked_for_death", System.currentTimeMillis() + 10000);

        BlockPos pos = Objects.requireNonNull(target.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.WITHER_SPAWN), SoundSource.HOSTILE, 0.5f, 2.0f);
    }

    private void executeSmokeBomb(Mob boss, ServerLevel level) {
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.INVISIBILITY), 100, 0));
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.MOVEMENT_SPEED), 100, 2));

        // Smoke particles
        for (int i = 0; i < 50; i++) {
            level.sendParticles(Objects.requireNonNull(ParticleTypes.LARGE_SMOKE),
                boss.getX() + random.nextGaussian() * 2,
                boss.getY() + random.nextDouble() * 2,
                boss.getZ() + random.nextGaussian() * 2,
                1, 0, 0, 0, 0);
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.FIRE_EXTINGUISH), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void executeFireballBarrage(Mob boss, ServerPlayer target, ServerLevel level) {
        Vec3 direction = Objects.requireNonNull(
            Objects.requireNonNull(target.position()).subtract(Objects.requireNonNull(boss.position()))
        ).normalize();

        for (int i = 0; i < 5; i++) {
            Vec3 velocity = new Vec3(
                direction.x + random.nextGaussian() * 0.2,
                direction.y + random.nextGaussian() * 0.1,
                direction.z + random.nextGaussian() * 0.2
            );
            SmallFireball fireball = new SmallFireball(Objects.requireNonNull(level), boss, velocity);
            fireball.setPos(boss.getX(), boss.getY() + 1.5, boss.getZ());
            level.addFreshEntity(fireball);
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.BLAZE_SHOOT), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void executeFrostNova(Mob boss, ServerLevel level) {
        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        AABB area = Objects.requireNonNull(new AABB(pos).inflate(8));

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.MOVEMENT_SLOWDOWN), 100, 3));
            player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DIG_SLOWDOWN), 100, 2));
            player.hurt(Objects.requireNonNull(level.damageSources().freeze()), 4.0f);
        }

        // Ice particles
        for (int i = 0; i < 100; i++) {
            double angle = (i / 100.0) * Math.PI * 2;
            double radius = random.nextDouble() * 8;
            double x = pos.getX() + Math.cos(angle) * radius;
            double z = pos.getZ() + Math.sin(angle) * radius;
            level.sendParticles(Objects.requireNonNull(ParticleTypes.SNOWFLAKE), x, pos.getY() + 0.5, z, 1, 0, 0.2, 0, 0);
        }

        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.GLASS_BREAK), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private void executeLightningStorm(Mob boss, ServerPlayer target, ServerLevel level) {
        // Strike at player and random nearby positions
        BlockPos baseStrike = Objects.requireNonNull(target.blockPosition());
        for (int i = 0; i < 3; i++) {
            BlockPos strikePos = (i == 0)
                ? baseStrike
                : baseStrike.offset(
                    random.nextInt(10) - 5,
                    0,
                    random.nextInt(10) - 5
                );

            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(Objects.requireNonNull(level));
            if (lightning != null) {
                lightning.moveTo(strikePos.getX(), strikePos.getY(), strikePos.getZ());
                lightning.setVisualOnly(false);
                level.addFreshEntity(lightning);
            }
        }
    }

    private void executeElementalShift(BossFight fight, Mob boss, ServerLevel level) {
        // Cycle through elements, gaining different resistances
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.FIRE_RESISTANCE), 400, 0));
        boss.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE), 200, 1));

        // Reset ability cooldowns for new element abilities
        fight.abilityCooldowns.replaceAll((ability, cooldown) -> cooldown / 2);

        // Visual transformation
        for (int i = 0; i < 40; i++) {
            level.sendParticles(Objects.requireNonNull(ParticleTypes.DRAGON_BREATH),
                boss.getX() + random.nextGaussian(),
                boss.getY() + random.nextDouble() * 2,
                boss.getZ() + random.nextGaussian(),
                1, 0, 0, 0, 0.1);
        }

        BlockPos pos = Objects.requireNonNull(boss.blockPosition());
        level.playSound(null, pos, Objects.requireNonNull(SoundEvents.BEACON_POWER_SELECT), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private BossWaveSystem() {}
}

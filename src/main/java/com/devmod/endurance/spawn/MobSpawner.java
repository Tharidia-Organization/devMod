package com.devmod.endurance.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import com.devmod.arena.zone.ZoneSpawnSlotAllocator;
import com.devmod.endurance.ArenaContext;
import com.devmod.endurance.DifficultyScaler;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.QuestType;
import com.devmod.endurance.SpawnAffix;
import com.devmod.endurance.WaveDirector;
import com.devmod.endurance.WaveManager;
import com.devmod.endurance.WaveManager.WaveModifier;
import com.devmod.endurance.WaveManager.WaveState;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

/**
 * P1-001: Extracted mob spawning logic from WaveManager.
 *
 * Handles all aspects of mob spawning in endurance quests:
 * - Position selection (pool-based, zone-based)
 * - Stat application (modifiers, affixes, scaling)
 * - Entity creation and finalization
 * - AI activation
 */
public class MobSpawner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobSpawner.class);

    public static final MobSpawner INSTANCE = new MobSpawner();

    private final Random random = new Random();

    // Elite spawn chance tiers (matches WaveManager constants)
    private static final float MODIFIER_CHANCE_TIER_1 = 0.10f;
    private static final float MODIFIER_CHANCE_TIER_2 = 0.20f;
    private static final float MODIFIER_CHANCE_TIER_3 = 0.35f;
    private static final int MODIFIER_START_WAVE = 3;

    private MobSpawner() {}

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Spawn mobs for a wave batch.
     *
     * @param waveState Current wave state
     * @param arena Arena context
     * @param handle Arena handle with spawn positions
     * @param count Number of mobs to spawn
     * @param affix Spawn affix to apply
     * @param role Spawn role for position selection
     * @param objectiveTarget Whether spawned mobs are objective targets
     * @return SpawnResult with success/failure counts
     */
    public SpawnResult spawnMobs(WaveState waveState,
                                  ArenaContext arena,
                                  @Nullable ArenaHandle handle,
                                  int count,
                                  SpawnAffix affix,
                                  WaveDirector.SpawnRole role,
                                  boolean objectiveTarget) {
        if (count <= 0 || arena == null || waveState == null) {
            return SpawnResult.empty();
        }

        SpawnAffix safeAffix = affix != null ? affix : SpawnAffix.BASE;
        WaveManager.SpawnContext spawnContext = waveState.getSpawnContext();
        if (spawnContext == null || spawnContext.positions.isEmpty()) {
            LOGGER.error("[MobSpawner] No spawn positions available for wave {}", waveState.getWaveNumber());
            EnduranceTelemetryService.INSTANCE.recordWaveBlocked(waveState.getQuest().getQuestId());
            return SpawnResult.empty();
        }

        ServerLevel level = arena.getLevel();
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        EntityType<?> entityType = Objects.requireNonNull(mobConfig.entityType, "mobConfig.entityType");
        ResourceLocation mobId = Objects.requireNonNull(
            BuiltInRegistries.ENTITY_TYPE.getKey(entityType), "entityType key");

        List<BlockPos> spawnPositions = spawnContext.positions;
        SpawnOccupancyTracker occupied = new SpawnOccupancyTracker();
        boolean allowReuse = spawnPositions.size() < count;
        WaveManager.SpawnPools pools = spawnContext.pools;

        int successfulSpawns = 0;
        int failedSpawns = 0;
        int zoneAllocSuccess = 0;
        int zoneAllocFallback = 0;

        if (spawnPositions.size() < count) {
            LOGGER.warn("[MobSpawner] Only {} valid spawn positions found for {} mobs",
                spawnPositions.size(), count);
        }

        for (int i = 0; i < count; i++) {
            Entity entity = entityType.create(Objects.requireNonNull(level));
            if (!(entity instanceof Mob mob)) {
                failedSpawns++;
                LOGGER.error("[MobSpawner] Failed to create entity of type {} (registry: {})",
                    entityType, mobId);
                continue;
            }

            // Allocate spawn position
            BlockPos spawnPos = allocateSpawnPosition(
                spawnContext, occupied, pools, role, mob, mobId,
                i, allowReuse, level
            );

            if (spawnPos == null) {
                failedSpawns++;
                EnduranceTelemetryService.INSTANCE.recordSpawnFailure(
                    waveState.getQuest().getQuestId(),
                    waveState.getWaveNumber(),
                    "no_valid_spawn",
                    handle != null ? handle.templateId() : null
                );
                continue;
            }

            // Zone allocation metrics are tracked inside allocateSpawnPosition

            // Position and configure mob
            mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

            applyMobModifiers(mob, waveState);
            applyMultiplayerHPScaling(mob, waveState);
            applySpawnAffix(mob, safeAffix);

            // Check for elite promotion
            SpawnAffix appliedAffix = safeAffix;
            if (!safeAffix.elite && random.nextFloat() < getEliteChance(mobConfig.eliteChance, waveState.getWaveNumber())) {
                applyEliteBuffs(mob, waveState.getWaveNumber());
                appliedAffix = SpawnAffix.ELITE;
            } else if (safeAffix.elite) {
                applyEliteBuffs(mob, waveState.getWaveNumber());
            }

            // Finalize spawn
            finalizeMobSpawn(mob, level, spawnPos);

            // Tag mob with quest metadata
            tagMobForQuest(mob, waveState, arena, handle, appliedAffix, objectiveTarget);

            // Add to world
            boolean added = level.addFreshEntity(mob);
            if (added && mob.isAlive()) {
                UUID mobUuid = mob.getUUID();
                waveState.addSpawnedMob(mobUuid, appliedAffix, objectiveTarget);
                successfulSpawns++;

                // Awaken AI after entity is in the world
                awakeMobAI(mob, level);

                // Record heatmap telemetry
                if (handle != null) {
                    EnduranceTelemetryService.INSTANCE.recordSpawnHeatmap(
                        waveState.getQuest().getQuestId(), handle, spawnPos);
                }

                LOGGER.debug("[MobSpawner] Spawned {} at {} (affix={})",
                    entityType, spawnPos, appliedAffix);
            } else {
                failedSpawns++;
                LOGGER.warn("[MobSpawner] Failed to spawn mob at {} (added={}, alive={})",
                    spawnPos, added, mob.isAlive());
            }
        }

        // Adjust wave target if spawns failed
        if (failedSpawns > 0) {
            LOGGER.warn("[MobSpawner] Wave {} spawn summary: {} successful, {} failed. Adjusting wave target.",
                waveState.getWaveNumber(), successfulSpawns, failedSpawns);
            int newTotalToSpawn = Math.max(1, waveState.getSpawned());
            waveState.setTotalToSpawn(newTotalToSpawn);
            waveState.adjustKillTarget(newTotalToSpawn);
        }

        // Emit zone allocation metrics telemetry
        if (spawnContext.hasZones() && (zoneAllocSuccess > 0 || zoneAllocFallback > 0)) {
            EnduranceTelemetryService.INSTANCE.recordZoneAllocationMetrics(
                waveState.getQuest().getQuestId(),
                waveState.getWaveNumber(),
                zoneAllocSuccess,
                zoneAllocFallback,
                zoneAllocSuccess + zoneAllocFallback,
                handle != null ? handle.templateId() : null
            );
        }

        LOGGER.info("[MobSpawner] Wave {} spawned {}/{} mobs successfully",
            waveState.getWaveNumber(), successfulSpawns, count);

        return new SpawnResult(successfulSpawns, failedSpawns, zoneAllocSuccess, zoneAllocFallback);
    }

    // =========================================================================
    // POSITION ALLOCATION
    // =========================================================================

    @Nullable
    private BlockPos allocateSpawnPosition(
            WaveManager.SpawnContext spawnContext,
            SpawnOccupancyTracker occupied,
            WaveManager.SpawnPools pools,
            WaveDirector.SpawnRole role,
            Mob mob,
            ResourceLocation mobId,
            int index,
            boolean allowReuse,
            ServerLevel level) {

        BlockPos spawnPos = null;

        // Try zone-aware spawn allocation first if zones are configured
        if (spawnContext.hasZones() && spawnContext.zoneAllocator != null) {
            spawnPos = spawnContext.zoneAllocator.allocateSpawnPosition(mobId);
            if (spawnPos != null && occupied.isOccupied(spawnPos) && !allowReuse) {
                spawnPos = null;
            }
            if (spawnPos != null) {
                occupied.markOccupied(spawnPos);
                return spawnPos;
            }
        }

        // Fall back to pool-based selection
        List<BlockPos> candidatePool = chooseSpawnPool(role, pools, mob);
        spawnPos = pickValidatedSpawnPosition(
            candidatePool,
            index,
            occupied,
            spawnContext.runtimeValidator,
            spawnContext.slotMap,
            spawnContext.template,
            level,
            allowReuse
        );

        // Try all pool as fallback
        if (spawnPos == null && candidatePool != pools.all) {
            spawnPos = pickValidatedSpawnPosition(
                pools.all,
                index,
                occupied,
                spawnContext.runtimeValidator,
                spawnContext.slotMap,
                spawnContext.template,
                level,
                allowReuse
            );
        }

        return spawnPos;
    }

    @Nullable
    private BlockPos pickValidatedSpawnPosition(
            List<BlockPos> positions,
            int startIndex,
            SpawnOccupancyTracker occupied,
            @Nullable TemplateSpawnValidator runtimeValidator,
            Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap,
            @Nullable ArenaTemplate template,
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

    private List<BlockPos> chooseSpawnPool(WaveDirector.SpawnRole role, WaveManager.SpawnPools pools, Mob mob) {
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

    // =========================================================================
    // STAT APPLICATION
    // =========================================================================

    /**
     * Apply wave modifiers (roguelike elements) to a mob.
     */
    public void applyMobModifiers(Mob mob, WaveState waveState) {
        for (WaveModifier modifier : waveState.getModifiers()) {
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
     * Apply spawn affix stat modifiers.
     */
    public void applySpawnAffix(Mob mob, SpawnAffix affix) {
        if (mob == null || affix == null || affix == SpawnAffix.BASE) {
            return;
        }

        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * affix.hpMultiplier);
            mob.setHealth(mob.getMaxHealth());
        }

        var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(attackAttr.getBaseValue() * affix.damageMultiplier);
        }

        var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * affix.speedMultiplier);
        }
    }

    /**
     * Apply HP and damage scaling based on player count, quest type, AND mob difficulty preset.
     */
    public void applyMultiplayerHPScaling(Mob mob, WaveState waveState) {
        EnduranceQuestRegistry.MobQuestConfig mobConfig = waveState.getQuest().getMobConfig();
        int playerCount = waveState.getPlayerCount();
        QuestType questType = waveState.getQuestType();
        float waveScale = DifficultyScaler.INSTANCE.getWaveMultiplier(
            waveState.getWaveNumber(), waveState.getQuest().getTotalWaves());

        // Apply HP scaling
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            float baseHP = (float) healthAttr.getBaseValue();
            float scaledHP = mobConfig.getScaledHealth(playerCount, questType);

            if (Math.abs(baseHP - mobConfig.baseHealth) > 0.1f && mobConfig.baseHealth > 0) {
                float ratio = baseHP / mobConfig.baseHealth;
                scaledHP = scaledHP * ratio;
            }

            scaledHP *= waveScale;
            healthAttr.setBaseValue(scaledHP);
            mob.setHealth(mob.getMaxHealth());

            LOGGER.debug("[MobSpawner] Mob HP scaled: {} -> {} (players={}, preset={}, type={}, waveScale={})",
                baseHP, scaledHP, playerCount, mobConfig.difficultyPreset.displayName, questType, waveScale);
        }

        // Apply damage scaling
        var attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            float baseDamage = (float) attackAttr.getBaseValue();
            float scaledDamage = mobConfig.getScaledDamage(playerCount);

            if (Math.abs(baseDamage - mobConfig.baseDamage) > 0.1f && mobConfig.baseDamage > 0) {
                float ratio = baseDamage / mobConfig.baseDamage;
                scaledDamage = scaledDamage * ratio;
            }

            scaledDamage *= waveScale;
            attackAttr.setBaseValue(scaledDamage);

            LOGGER.debug("[MobSpawner] Mob DMG scaled: {} -> {} (players={}, preset={}, waveScale={})",
                baseDamage, scaledDamage, playerCount, mobConfig.difficultyPreset.displayName, waveScale);
        }
    }

    /**
     * Apply elite buffs to a mob (special stronger variant).
     */
    public void applyEliteBuffs(Mob mob, int waveNumber) {
        mob.getPersistentData().putBoolean("endurance_elite", true);

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

    private float getEliteChance(float baseChance, int waveNumber) {
        if (baseChance <= 0f || waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }
        float rampChance;
        if (waveNumber < 5) {
            rampChance = MODIFIER_CHANCE_TIER_1;
        } else if (waveNumber < 8) {
            rampChance = MODIFIER_CHANCE_TIER_2;
        } else {
            rampChance = MODIFIER_CHANCE_TIER_3;
        }
        return Math.min(baseChance, rampChance);
    }

    // =========================================================================
    // MOB FINALIZATION
    // =========================================================================

    @SuppressWarnings("deprecation")
    private void finalizeMobSpawn(Mob mob, ServerLevel level, BlockPos spawnPos) {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
            MobSpawnType.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
    }

    private void tagMobForQuest(Mob mob, WaveState waveState, ArenaContext arena,
                                 @Nullable ArenaHandle handle, SpawnAffix affix, boolean objectiveTarget) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID("endurance_quest_id", waveState.getQuest().getQuestId());
        tag.putUUID("endurance_arena_id", arena.getId());
        tag.putString("endurance_affix", affix.name());

        if (handle != null) {
            if (handle.templateId() != null) {
                tag.putString("endurance_template_id", handle.templateId());
            }
            tag.putInt("endurance_template_version", handle.templateVersion());
            if (handle.policyId() != null) {
                tag.putString("endurance_policy_id", handle.policyId());
            }
            tag.putInt("endurance_policy_version", handle.policyVersion());
        }

        if (objectiveTarget) {
            tag.putBoolean("endurance_objective_target", true);
        }
    }

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
            brain.tick(level, mob);

            mob.setAggressive(true);

            LOGGER.debug("[MobSpawner] Registered EnduranceAI for {} (target goals={}, behavior goals={})",
                mob.getType().toString(),
                mob.targetSelector.getAvailableGoals().size(),
                mob.goalSelector.getAvailableGoals().size());

        } catch (Exception e) {
            LOGGER.warn("[MobSpawner] Failed to awaken AI for {}: {}",
                mob.getType().toString(), e.getMessage());
        }
    }

    // =========================================================================
    // SPAWN CONTEXT BUILDING
    // =========================================================================

    /**
     * Build spawn context from arena handle and template.
     */
    @Nullable
    public WaveManager.SpawnContext buildSpawnContext(ArenaContext arena, ArenaHandle handle,
                                          @Nullable EnduranceQuestRegistry.MobQuestConfig mobConfig) {
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

        WaveManager.SpawnPools pools = buildSpawnPools(positions, slotMap);

        // Build zone layout if template has zone settings
        com.devmod.arena.zone.ZoneLayout zoneLayout = null;
        ZoneSpawnSlotAllocator zoneAllocator = null;

        if (template != null) {
            ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
            if (zoneSettings != null && zoneSettings.enabled()) {
                int arenaRadius = template.size() / 2;
                int baseY = template.floor() != null ? template.floor().y() : 64;

                if (zoneSettings.autoGenerate() && mobConfig != null && mobConfig.entityType != null) {
                    ResourceLocation mobId =
                        BuiltInRegistries.ENTITY_TYPE.getKey(mobConfig.entityType);
                    com.devmod.arena.zone.ZoneLayoutPlanner planner = new com.devmod.arena.zone.ZoneLayoutPlanner();
                    zoneLayout = planner.planLayout(List.of(mobId), arenaRadius);
                    LOGGER.debug("[MobSpawner] Auto-generated zone layout for mob {}: {} zones, strategy {}",
                        mobId, zoneLayout.zoneCount(), zoneLayout.strategy());
                } else if (!zoneSettings.zones().isEmpty()) {
                    zoneLayout = buildZoneLayoutFromTemplate(template, arenaRadius);
                }

                if (zoneLayout != null) {
                    zoneAllocator = new ZoneSpawnSlotAllocator(zoneLayout, baseY);
                    LOGGER.info("[MobSpawner] Created zone allocator with {} zones", zoneLayout.zoneCount());
                }
            }
        }

        // P1: Extract entity budget from template limits
        int maxEntities = 0; // 0 means unlimited
        if (template != null && template.limits() != null && template.limits().maxEntities() > 0) {
            maxEntities = template.limits().maxEntities();
            LOGGER.debug("[MobSpawner] Entity budget set to {} from template '{}'", maxEntities, template.id());
        }

        return new WaveManager.SpawnContext(positions, slotMap, template, runtimeValidator, pools, zoneLayout, zoneAllocator, maxEntities);
    }

    private WaveManager.SpawnPools buildSpawnPools(List<BlockPos> positions, Map<BlockPos, ArenaTemplate.SpawnSlot> slotMap) {
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

        return new WaveManager.SpawnPools(positions, melee, ranged, corner);
    }

    private Map<BlockPos, ArenaTemplate.SpawnSlot> buildMobSpawnSlotMap(ArenaTemplate template, ArenaHandle handle) {
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

    @Nullable
    private com.devmod.arena.zone.ZoneLayout buildZoneLayoutFromTemplate(ArenaTemplate template, int arenaRadius) {
        ArenaTemplate.ZoneSettings zoneSettings = template.zoneSettings();
        if (zoneSettings == null || zoneSettings.zones().isEmpty()) {
            return null;
        }

        var zoneDefs = zoneSettings.zones();
        int zoneCount = zoneDefs.size();

        List<com.devmod.arena.zone.ZoneEnvironment> environments = new ArrayList<>();
        for (var zoneDef : zoneDefs) {
            environments.add(convertZoneDefToEnvironment(zoneDef));
        }

        com.devmod.arena.zone.ZoneLayout.Builder builder = com.devmod.arena.zone.ZoneLayout.builder(arenaRadius);

        if (zoneCount == 1) {
            com.devmod.arena.zone.ArenaZone zone = com.devmod.arena.zone.ArenaZone.circular(zoneDefs.get(0).name(), arenaRadius)
                .withEnvironment(environments.get(0));
            builder.addZone(zone);
        } else if (zoneCount == 2) {
            builder.stripedVertical(environments.get(0), environments.get(1));
        } else if (zoneCount <= 4) {
            com.devmod.arena.zone.ZoneEnvironment ne = environments.size() > 0 ? environments.get(0) : com.devmod.arena.zone.ZoneEnvironment.DEFAULT;
            com.devmod.arena.zone.ZoneEnvironment nw = environments.size() > 1 ? environments.get(1) : com.devmod.arena.zone.ZoneEnvironment.DEFAULT;
            com.devmod.arena.zone.ZoneEnvironment sw = environments.size() > 2 ? environments.get(2) : com.devmod.arena.zone.ZoneEnvironment.DEFAULT;
            com.devmod.arena.zone.ZoneEnvironment se = environments.size() > 3 ? environments.get(3) : com.devmod.arena.zone.ZoneEnvironment.DEFAULT;
            builder.quadrant(ne, nw, sw, se);
        } else {
            builder.radial(environments);
        }

        return builder.build();
    }

    private com.devmod.arena.zone.ZoneEnvironment convertZoneDefToEnvironment(ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef) {
        Integer blockLightValue = zoneDef.blockLight();
        Integer skyLightValue = zoneDef.skyLight();
        int skyLight = skyLightValue != null ? skyLightValue : 15;

        com.devmod.arena.zone.ZoneEnvironment.LightingConfig lighting = blockLightValue != null
            ? new com.devmod.arena.zone.ZoneEnvironment.LightingConfig(skyLight, blockLightValue, false)
            : com.devmod.arena.zone.ZoneEnvironment.LightingConfig.DEFAULT;

        String biomeValue = zoneDef.biome();
        String floorMaterialValue = zoneDef.floorMaterial();

        com.devmod.arena.zone.ZoneEnvironment.TimeConfig time = com.devmod.arena.zone.ZoneEnvironment.TimeConfig.ANY;
        String timeStr = zoneDef.time();
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                time = com.devmod.arena.zone.ZoneEnvironment.TimeConfig.valueOf(timeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown time config '{}' for zone '{}', using ANY", timeStr, zoneDef.name());
            }
        }

        return new com.devmod.arena.zone.ZoneEnvironment(
            biomeValue != null ? java.util.Optional.of(ResourceLocation.parse(biomeValue)) : java.util.Optional.empty(),
            floorMaterialValue != null ? java.util.Optional.of(ResourceLocation.parse(floorMaterialValue)) : java.util.Optional.empty(),
            lighting,
            time
        );
    }

    // =========================================================================
    // RESULT CLASSES
    // =========================================================================

    /**
     * Result of a spawn operation.
     */
    public record SpawnResult(
        int successful,
        int failed,
        int zoneAllocSuccess,
        int zoneAllocFallback
    ) {
        public static SpawnResult empty() {
            return new SpawnResult(0, 0, 0, 0);
        }

        public int total() {
            return successful + failed;
        }

        public boolean hasFailures() {
            return failed > 0;
        }
    }

}

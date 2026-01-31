package com.devmod.endurance.hazard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.endurance.ArenaContext;
import com.devmod.endurance.EnduranceColors;
import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveCompleted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;
public class ArenaHazardSystem implements QuestLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaHazardSystem.class);

    public static final ArenaHazardSystem INSTANCE = new ArenaHazardSystem();

    // Active hazard sessions per quest
    private final Map<UUID, HazardSession> sessions = new ConcurrentHashMap<>();

    // ========== Hazard Types ==========

    /**
     * Types of arena hazards with their properties.
     */
    public enum HazardType {
        FLOOR_CRUMBLE(
            "Floor Crumble",
            "Parts of the floor become unstable!",
            3,  // Trigger wave
            EnduranceColors.Hazard.FIRE,
            120, // Duration ticks (6 seconds warning)
            "The ground trembles beneath you..."
        ),

        BLOOD_MOON(
            "Blood Moon",
            "The sky turns red, enemies grow stronger!",
            5,
            EnduranceColors.Hazard.BLEED,
            600, // Duration: 30 seconds
            "A crimson moon rises..."
        ),

        ARENA_SHRINK(
            "Arena Shrink",
            "The arena walls close in!",
            7,
            EnduranceColors.Hazard.VOID,
            400, // Duration: 20 seconds shrinking
            "The shadows creep closer..."
        ),

        LIGHTNING_STORM(
            "Lightning Storm",
            "Lightning strikes randomly in the arena!",
            9,
            EnduranceColors.Hazard.ARC,
            300, // Duration: 15 seconds
            "Storm clouds gather overhead..."
        ),

        VOID_RIFTS(
            "Void Rifts",
            "Dark portals spawn additional enemies!",
            11,
            EnduranceColors.Hazard.PSI,
            200, // Duration: 10 seconds per rift
            "Reality tears at the seams..."
        );

        private final String displayName;
        private final String description;
        private final int triggerWave;
        private final int color;
        private final int durationTicks;
        private final String warningText;

        HazardType(String displayName, String description, int triggerWave,
                   int color, int durationTicks, String warningText) {
            this.displayName = displayName;
            this.description = description;
            this.triggerWave = triggerWave;
            this.color = color;
            this.durationTicks = durationTicks;
            this.warningText = warningText;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getTriggerWave() { return triggerWave; }
        public int getColor() { return color; }
        public int getDurationTicks() { return durationTicks; }
        public String getWarningText() { return warningText; }
    }

    /**
     * State of an active hazard.
     */
    public enum HazardState {
        WARNING,    // Warning phase (players see indicator)
        ACTIVE,     // Hazard is in effect
        ENDING,     // Hazard is winding down
        INACTIVE    // Hazard has ended
    }

    /**
     * An active hazard instance.
     */
    public static class ActiveHazard {
        private final HazardType type;
        public HazardState state;
        public int ticksRemaining;
        public int warningTicks;
        private final List<BlockPos> affectedBlocks = new ArrayList<>();
        public AABB shrinkBounds;

        public ActiveHazard(HazardType type) {
            this.type = type;
            this.state = HazardState.WARNING;
            this.warningTicks = 60; // 3 second warning
            this.ticksRemaining = type.getDurationTicks();
        }

        public HazardType getType() { return type; }
        public List<BlockPos> getAffectedBlocks() { return affectedBlocks; }

        public float getProgress() {
            return 1.0f - ((float) ticksRemaining / type.getDurationTicks());
        }
    }

    /**
     * Hazard session for a quest.
     */
    public static class HazardSession {
        private final UUID questId;
        private final List<ActiveHazard> activeHazards = new ArrayList<>();
        private final Set<HazardType> triggeredHazards = EnumSet.noneOf(HazardType.class);
        private BlockPos arenaCenter;
        private int arenaRadius = 30;
        private int currentShrinkLevel = 0;

        public HazardSession(UUID questId) {
            this.questId = questId;
        }

        public UUID getQuestId() {
            return questId;
        }

        public void setArenaCenter(BlockPos center) {
            this.arenaCenter = center;
        }

        public void setArenaRadius(int radius) {
            this.arenaRadius = radius;
        }

        public List<ActiveHazard> getActiveHazards() {
            return Collections.unmodifiableList(activeHazards);
        }

        public boolean hasActiveHazard(HazardType type) {
            return activeHazards.stream()
                .anyMatch(h -> h.getType() == type && h.state != HazardState.INACTIVE);
        }

        public Optional<ActiveHazard> getHazard(HazardType type) {
            return activeHazards.stream()
                .filter(h -> h.getType() == type)
                .findFirst();
        }
    }

    // ========== Session Management ==========

    /*
     * Start a hazard session for a quest.
     */
    public HazardSession startSession(UUID questId, BlockPos arenaCenter, int arenaRadius) {
        HazardSession session = new HazardSession(questId);
        session.setArenaCenter(arenaCenter);
        session.setArenaRadius(arenaRadius);
        sessions.put(questId, session);
        LOGGER.info("[ArenaHazards] Started hazard session for quest {}", questId);
        return session;
    }

    /*
     * Get active session for a quest.
     */
    public Optional<HazardSession> getSession(UUID questId) {
        return Optional.ofNullable(sessions.get(questId));
    }

    /*
     * End hazard session.
     */
    public HazardSession endSession(UUID questId) {
        HazardSession session = sessions.remove(questId);
        if (session != null) {
            LOGGER.info("[ArenaHazards] Ended hazard session for quest {} ({} hazards triggered)",
                session.getQuestId(), session.triggeredHazards.size());
        }
        return session;
    }

    // ========== Wave Triggers ==========

    /*
     * Check and trigger hazards for a wave.
     * Called when a wave starts.
     */
    public List<HazardType> checkWaveHazards(UUID questId, int waveNumber) {
        HazardSession session = sessions.get(questId);
        if (session == null) return List.of();

        List<HazardType> triggered = new ArrayList<>();

        for (HazardType type : HazardType.values()) {
            // Check if this wave triggers the hazard
            if (waveNumber >= type.getTriggerWave() && !session.triggeredHazards.contains(type)) {
                // First time reaching this wave - trigger hazard
                triggerHazard(questId, type);
                triggered.add(type);
            } else if (waveNumber > type.getTriggerWave() && (waveNumber - type.getTriggerWave()) % 4 == 0) {
                // Repeat hazard every 4 waves after first trigger
                if (!session.hasActiveHazard(type)) {
                    triggerHazard(questId, type);
                    triggered.add(type);
                }
            }
        }

        return triggered;
    }

    /*
     * Trigger a specific hazard.
     */
    public void triggerHazard(UUID questId, HazardType type) {
        HazardSession session = sessions.get(questId);
        if (session == null) return;

        ActiveHazard hazard = new ActiveHazard(type);
        session.activeHazards.add(hazard);
        session.triggeredHazards.add(type);

        LOGGER.info("[ArenaHazards] Triggered {} for quest {}", type.getDisplayName(), questId);
    }

    // ========== Tick Processing ==========

    /*
     * Tick all hazards for a quest.
     * Called from EnduranceEventTick.
     */
    public void tick(ServerLevel level, UUID questId, ServerPlayer player) {
        HazardSession session = sessions.get(questId);
        if (session == null) return;

        Iterator<ActiveHazard> iterator = session.activeHazards.iterator();
        while (iterator.hasNext()) {
            ActiveHazard hazard = iterator.next();

            switch (hazard.state) {
                case WARNING -> tickWarning(level, player, session, hazard);
                case ACTIVE -> tickActive(level, player, session, hazard);
                case ENDING -> tickEnding(player, hazard);
                case INACTIVE -> iterator.remove();
            }
        }
    }

    private void tickWarning(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        // Warning effects
        if (hazard.warningTicks == 60) {
            // Initial warning
            displayClientMessage(player, "\u00A7c\u00A7l⚠ " + hazard.getType().getWarningText(), true);
            playSound(level, resolveBlockPos(player), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.0f, 0.8f);
        }

        hazard.warningTicks--;

        if (hazard.warningTicks <= 0) {
            hazard.state = HazardState.ACTIVE;
            displayClientMessage(player, "\u00A74\u00A7l" + hazard.getType().getDisplayName().toUpperCase(Locale.ROOT) + "!", true);
            playSound(level, resolveBlockPos(player), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 0.8f, 1.2f);

            // Initialize hazard-specific state
            initializeHazard(level, session, hazard);
        }
    }

    private void tickActive(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        hazard.ticksRemaining--;

        switch (hazard.getType()) {
            case FLOOR_CRUMBLE -> tickFloorCrumble(level, player, hazard);
            case BLOOD_MOON -> tickBloodMoon(level, player, hazard);
            case ARENA_SHRINK -> tickArenaShrink(level, player, session, hazard);
            case LIGHTNING_STORM -> tickLightningStorm(level, player, session, hazard);
            case VOID_RIFTS -> tickVoidRifts(level, player, session, hazard);
        }

        if (hazard.ticksRemaining <= 0) {
            hazard.state = HazardState.ENDING;
            hazard.ticksRemaining = 40; // 2 second wind-down
        }
    }

    private void tickEnding(ServerPlayer player, ActiveHazard hazard) {
        hazard.ticksRemaining--;

        // Cleanup effects
        if (hazard.ticksRemaining <= 0) {
            cleanupHazard(hazard);
            hazard.state = HazardState.INACTIVE;
            displayClientMessage(player, "\u00A7a" + hazard.getType().getDisplayName() + " subsides...", true);
        }
    }

    // ========== Hazard Implementations ==========

    private void initializeHazard(ServerLevel level, HazardSession session, ActiveHazard hazard) {
        switch (hazard.getType()) {
            case FLOOR_CRUMBLE -> initFloorCrumble(level, session, hazard);
            case ARENA_SHRINK -> initArenaShrink(session, hazard);
            default -> {}
        }
    }

    private void cleanupHazard(ActiveHazard hazard) {
        switch (hazard.getType()) {
            case FLOOR_CRUMBLE -> cleanupFloorCrumble(hazard);
            case BLOOD_MOON -> cleanupBloodMoon();
            case ARENA_SHRINK -> {}  // Shrink doesn't revert
            default -> {}
        }
    }

    // --- FLOOR CRUMBLE ---

    private void initFloorCrumble(ServerLevel level, HazardSession session, ActiveHazard hazard) {
        if (session.arenaCenter == null) return;

        RandomSource random = level.random;
        int crumbleCount = 5 + random.nextInt(5);
        BlockPos arenaCenter = Objects.requireNonNull(session.arenaCenter, "session.arenaCenter");

        for (int i = 0; i < crumbleCount; i++) {
            int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            BlockPos pos = Objects.requireNonNull(arenaCenter.offset(dx, -1, dz));

            // Find ground level
            BlockState state = level.getBlockState(pos);
            while (state.isAir() && pos.getY() > arenaCenter.getY() - 10) {
                pos = Objects.requireNonNull(pos.below());
                state = level.getBlockState(pos);
            }

            if (!state.isAir()) {
                hazard.getAffectedBlocks().add(pos);
            }
        }
    }

    private void tickFloorCrumble(ServerLevel level, ServerPlayer player, ActiveHazard hazard) {
        RandomSource random = level.random;
        SimpleParticleType smokeParticles = ParticleTypes.SMOKE;

        // Crack particles on affected blocks
        for (BlockPos pos : hazard.getAffectedBlocks()) {
            if (random.nextFloat() < 0.3f) {
                sendParticles(level, smokeParticles,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    2, 0.3, 0.1, 0.3, 0.02);
            }
        }

        // Periodically collapse blocks
        if (hazard.ticksRemaining % 20 == 0 && !hazard.getAffectedBlocks().isEmpty()) {
            BlockPos collapsing = hazard.getAffectedBlocks().remove(random.nextInt(hazard.getAffectedBlocks().size()));
            BlockPos playerPos = resolveBlockPos(player);

            // Check if player is on this block
            if (playerPos.below().equals(collapsing) || playerPos.equals(collapsing)) {
                // Damage player
                DamageSource fallDamage = player.damageSources().fall();
                if (fallDamage != null) {
                    player.hurt(fallDamage, 4.0f);
                }
            }

            // Visual effect
            SimpleParticleType explosionParticles = ParticleTypes.EXPLOSION;
            sendParticles(level, explosionParticles,
                collapsing.getX() + 0.5, collapsing.getY() + 0.5, collapsing.getZ() + 0.5,
                5, 0.5, 0.5, 0.5, 0.1);
            SoundEvent explodeSound = SoundEvents.GENERIC_EXPLODE.value();
            playSound(level, collapsing, explodeSound, SoundSource.BLOCKS, 0.5f, 1.0f);

            // Temporarily replace with air (will restore later)
            BlockState airState = Blocks.AIR.defaultBlockState();
            if (airState != null) {
                level.setBlock(collapsing, airState, 3);
            }
        }
    }

    private void cleanupFloorCrumble(ActiveHazard hazard) {
        // Restore any remaining affected blocks would be handled by arena reset
        hazard.getAffectedBlocks().clear();
    }

    // --- BLOOD MOON ---

    private void tickBloodMoon(ServerLevel level, ServerPlayer player, ActiveHazard hazard) {
        // Apply strength to nearby mobs
        if (hazard.ticksRemaining % 40 == 0) {
            AABB playerBounds = player.getBoundingBox();
            AABB area = Objects.requireNonNull(playerBounds.inflate(30), "area");
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area);
            Holder<MobEffect> damageBoost = MobEffects.DAMAGE_BOOST;
            Holder<MobEffect> speedBoost = MobEffects.MOVEMENT_SPEED;

            for (Mob mob : mobs) {
                if (damageBoost != null && !mob.hasEffect(damageBoost)) {
                    mob.addEffect(new MobEffectInstance(damageBoost, 100, 0, false, true));
                }
                if (speedBoost != null && !mob.hasEffect(speedBoost)) {
                    mob.addEffect(new MobEffectInstance(speedBoost, 100, 0, false, true));
                }
            }
        }

        // Red particle ambiance
        if (hazard.ticksRemaining % 10 == 0) {
            RandomSource random = level.random;
            SimpleParticleType crimsonSpores = ParticleTypes.CRIMSON_SPORE;
            for (int i = 0; i < 5; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * 20;
                double y = player.getY() + random.nextDouble() * 10;
                double z = player.getZ() + (random.nextDouble() - 0.5) * 20;
                sendParticles(level, crimsonSpores, x, y, z, 1, 0, -0.1, 0, 0);
            }
        }
    }

    private void cleanupBloodMoon() {
        // Effects naturally expire
    }

    // --- ARENA SHRINK ---

    private void initArenaShrink(HazardSession session, ActiveHazard hazard) {
        if (session.arenaCenter == null) return;

        int radius = session.arenaRadius - (session.currentShrinkLevel * 5);
        BlockPos center = session.arenaCenter;

        // Shrink by 5 blocks
        session.currentShrinkLevel++;
        int newRadius = Math.max(10, radius - 5);
        hazard.shrinkBounds = new AABB(
            center.getX() - newRadius, center.getY() - 5, center.getZ() - newRadius,
            center.getX() + newRadius, center.getY() + 20, center.getZ() + newRadius
        );
    }

    private void tickArenaShrink(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        if (hazard.shrinkBounds == null) return;
        AABB shrinkBounds = hazard.shrinkBounds;

        // Check if player is outside bounds
        Vec3 playerPos = Objects.requireNonNull(player.position(), "playerPos");
        if (!shrinkBounds.contains(playerPos)) {
            // Push player toward center
            BlockPos arenaCenter = session.arenaCenter;
            if (arenaCenter != null) {
                double dx = arenaCenter.getX() - player.getX();
                double dz = arenaCenter.getZ() - player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    player.push(dx / dist * 0.3, 0, dz / dist * 0.3);
                }
            }

            // Damage for being outside - use generic() to respect armor instead of magic()
            if (hazard.ticksRemaining % 20 == 0) {
                DamageSource hazardDamage = player.damageSources().generic();
                if (hazardDamage != null) {
                    player.hurt(hazardDamage, 2.0f);
                }
                displayClientMessage(player, "\u00A7c\u00A7lThe void consumes you!", true);
            }
        }

        // Visual boundary
        if (hazard.ticksRemaining % 5 == 0) {
            RandomSource random = level.random;
            SimpleParticleType portalParticles = ParticleTypes.PORTAL;
            for (int i = 0; i < 10; i++) {
                double x = shrinkBounds.minX + random.nextDouble() * (shrinkBounds.maxX - shrinkBounds.minX);
                double z = shrinkBounds.minZ + random.nextDouble() * (shrinkBounds.maxZ - shrinkBounds.minZ);

                // Only show on edges
                if (random.nextBoolean()) {
                    x = random.nextBoolean() ? shrinkBounds.minX : shrinkBounds.maxX;
                } else {
                    z = random.nextBoolean() ? shrinkBounds.minZ : shrinkBounds.maxZ;
                }

                sendParticles(level, portalParticles,
                    x, player.getY() + random.nextDouble() * 3, z,
                    1, 0, 0, 0, 0.1);
            }
        }
    }

    // --- LIGHTNING STORM ---

    private void tickLightningStorm(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        RandomSource random = level.random;

        // Random lightning strikes
        if (hazard.ticksRemaining % 40 == 0) {
            int strikeCount = 1 + random.nextInt(3);
            SimpleParticleType sparkParticles = ParticleTypes.ELECTRIC_SPARK;
            SoundEvent thunderSound = SoundEvents.LIGHTNING_BOLT_THUNDER;

            for (int i = 0; i < strikeCount; i++) {
                int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
                int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;

                BlockPos arenaCenter = session.arenaCenter;
                if (arenaCenter != null) {
                    BlockPos strikePos = arenaCenter.offset(dx, 0, dz);

                    // Warning flash
                    sendParticles(level, sparkParticles,
                        strikePos.getX() + 0.5, strikePos.getY() + 5, strikePos.getZ() + 0.5,
                        20, 0.5, 3, 0.5, 0.5);

                    // Delayed strike effect
                    playSound(level, strikePos, thunderSound, SoundSource.WEATHER, 0.5f, 1.0f);

                    // Damage entities in area
                    AABB strikeArea = new AABB(strikePos).inflate(2);
                    if (strikeArea != null) {
                        level.getEntitiesOfClass(ServerPlayer.class, strikeArea).forEach(p -> {
                            if (random.nextFloat() < 0.3f) { // 30% chance to hit
                                DamageSource lightningDamage = p.damageSources().lightningBolt();
                                if (lightningDamage != null) {
                                    p.hurt(lightningDamage, 3.0f);
                                }
                            }
                        });
                    }
                }
            }
        }

        // Ambient rain particles
        if (hazard.ticksRemaining % 2 == 0) {
            SimpleParticleType rainParticles = ParticleTypes.RAIN;
            for (int i = 0; i < 20; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * 30;
                double y = player.getY() + 10 + random.nextDouble() * 5;
                double z = player.getZ() + (random.nextDouble() - 0.5) * 30;
                sendParticles(level, rainParticles, x, y, z, 1, 0, -1, 0, 0.5);
            }
        }
    }

    // --- VOID RIFTS ---

    private void tickVoidRifts(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        RandomSource random = level.random;

        // Spawn rift effect periodically
        BlockPos arenaCenter = session.arenaCenter;
        if (hazard.ticksRemaining % 100 == 0 && arenaCenter != null) {
            int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            BlockPos riftPos = arenaCenter.offset(dx, 0, dz);
            SimpleParticleType reversePortalParticles = ParticleTypes.REVERSE_PORTAL;

            // Rift visual
            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = random.nextDouble() * 2;
                double rx = Math.cos(angle) * radius;
                double rz = Math.sin(angle) * radius;
                sendParticles(level, reversePortalParticles,
                    riftPos.getX() + 0.5 + rx, riftPos.getY() + 1, riftPos.getZ() + 0.5 + rz,
                    1, 0, 0.5, 0, 0.1);
            }

            SoundEvent teleportSound = SoundEvents.ENDERMAN_TELEPORT;
            playSound(level, riftPos, teleportSound, SoundSource.HOSTILE, 1.0f, 0.5f);

            // Signal to spawn system that extra mobs should spawn
            // This would integrate with WaveSpawnSystem
            displayClientMessage(player, "\u00A75A void rift tears open!", true);
        }

        // Ambient void particles
        SimpleParticleType portalParticles = ParticleTypes.PORTAL;
        for (int i = 0; i < 5; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 20;
            double y = player.getY() + random.nextDouble() * 5;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 20;
            sendParticles(level, portalParticles, x, y, z, 1, 0, 0, 0, 0.5);
        }
    }

    // ========== Public API ==========

    /*
     * Get active hazards for HUD display.
     */
    public List<HazardInfo> getActiveHazardInfo(UUID questId) {
        HazardSession session = sessions.get(questId);
        if (session == null) return List.of();

        return session.activeHazards.stream()
            .filter(h -> h.state != HazardState.INACTIVE)
            .map(h -> new HazardInfo(
                h.getType().getDisplayName(),
                h.getType().getDescription(),
                h.getType().getColor(),
                h.state,
                h.getProgress()
            ))
            .toList();
    }

    public record HazardInfo(
        String name,
        String description,
        int color,
        HazardState state,
        float progress
    ) {}

    /*
     * Check if a position is in the safe zone (considering shrink).
     */
    public boolean isInSafeZone(UUID questId, BlockPos pos) {
        HazardSession session = sessions.get(questId);
        if (session == null) return true;

        Optional<ActiveHazard> shrink = session.getHazard(HazardType.ARENA_SHRINK);
        if (shrink.isEmpty() || shrink.get().shrinkBounds == null) return true;

        return shrink.get().shrinkBounds.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /*
     * Get current shrink level (for mob spawn adjustment).
     */
    public int getShrinkLevel(UUID questId) {
        HazardSession session = sessions.get(questId);
        return session != null ? session.currentShrinkLevel : 0;
    }

    /*
     * Check if blood moon is active (for mob damage bonus).
     */
    public boolean isBloodMoonActive(UUID questId) {
        HazardSession session = sessions.get(questId);
        return session != null && session.hasActiveHazard(HazardType.BLOOD_MOON);
    }

    private static void displayClientMessage(ServerPlayer player, String message, boolean actionBar) {
        if (player == null) {
            return;
        }
        Component component = Component.literal(message == null ? "" : message);
        if (component != null) {
            player.displayClientMessage(component, actionBar);
        }
    }

    private static BlockPos resolveBlockPos(ServerPlayer player) {
        return player.blockPosition();
    }

    private static void playSound(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
            float volume, float pitch) {
        if (level == null || pos == null || sound == null || source == null) {
            return;
        }
        level.playSound(null, pos, sound, source, volume, pitch);
    }

    private static void sendParticles(ServerLevel level, SimpleParticleType particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level == null || particle == null) {
            return;
        }
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }

    private ArenaHazardSystem() {}

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        QuestContext ctx = event.context();
        UUID questId = ctx.questId();

        // Only create session if not already exists (quest-scoped)
        if (getSession(questId).isEmpty()) {
            // Get arena info from context
            BlockPos arenaCenter = ctx.player().blockPosition();
            int arenaRadius = 30; // Default
            ArenaContext arena = ctx.arena();
            if (arena != null) {
                arenaCenter = arena.getCenter();
                arenaRadius = arena.getSize() / 2;
            }

            startSession(questId, arenaCenter, arenaRadius);
            LOGGER.debug("[ArenaHazardSystem] Created session for quest {} via event bus (center: {}, radius: {})",
                questId, arenaCenter, arenaRadius);
        }
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        // Only cleanup shared resources (quest-scoped sessions) when cleanupShared is true
        if (!event.cleanupShared()) return;

        UUID questId = event.questId();
        HazardSession session = endSession(questId);
        if (session != null) {
            LOGGER.debug("[ArenaHazardSystem] Ended session for quest {} via event bus ({} hazards triggered)",
                questId, session.triggeredHazards.size());
        }
    }

    @Override
    public void onWaveCompleted(WaveCompleted event) {
        // Only trigger hazards once per wave (shared)
        if (!event.applyShared()) return;

        UUID questId = event.questId();
        int upcomingWave = event.context().waveNumber() + 1;

        List<HazardType> triggered = checkWaveHazards(questId, upcomingWave);
        if (!triggered.isEmpty()) {
            LOGGER.debug("[ArenaHazardSystem] Triggered {} hazards for wave {} via event bus: {}",
                triggered.size(), upcomingWave, triggered);
        }
    }

    @Override
    public int getPriority() {
        return 200; // After bargain system
    }

    @Override
    public String getListenerName() {
        return "ArenaHazardSystem";
    }
}

package com.devmod.endurance.hazard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class ArenaHazardSystem {
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
            0xFF6600,
            120, // Duration ticks (6 seconds warning)
            "The ground trembles beneath you..."
        ),

        BLOOD_MOON(
            "Blood Moon",
            "The sky turns red, enemies grow stronger!",
            5,
            0xCC0000,
            600, // Duration: 30 seconds
            "A crimson moon rises..."
        ),

        ARENA_SHRINK(
            "Arena Shrink",
            "The arena walls close in!",
            7,
            0x9900FF,
            400, // Duration: 20 seconds shrinking
            "The shadows creep closer..."
        ),

        LIGHTNING_STORM(
            "Lightning Storm",
            "Lightning strikes randomly in the arena!",
            9,
            0x00CCFF,
            300, // Duration: 15 seconds
            "Storm clouds gather overhead..."
        ),

        VOID_RIFTS(
            "Void Rifts",
            "Dark portals spawn additional enemies!",
            11,
            0x660099,
            200, // Duration: 10 seconds per rift
            "Reality tears at the seams..."
        );

        public final String displayName;
        public final String description;
        public final int triggerWave;
        public final int color;
        public final int durationTicks;
        public final String warningText;

        HazardType(String displayName, String description, int triggerWave,
                   int color, int durationTicks, String warningText) {
            this.displayName = displayName;
            this.description = description;
            this.triggerWave = triggerWave;
            this.color = color;
            this.durationTicks = durationTicks;
            this.warningText = warningText;
        }
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
        public final HazardType type;
        public HazardState state;
        public int ticksRemaining;
        public int warningTicks;
        public final List<BlockPos> affectedBlocks = new ArrayList<>();
        public AABB shrinkBounds;
        public AABB originalBounds;

        public ActiveHazard(HazardType type) {
            this.type = type;
            this.state = HazardState.WARNING;
            this.warningTicks = 60; // 3 second warning
            this.ticksRemaining = type.durationTicks;
        }

        public float getProgress() {
            return 1.0f - ((float) ticksRemaining / type.durationTicks);
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
                .anyMatch(h -> h.type == type && h.state != HazardState.INACTIVE);
        }

        public Optional<ActiveHazard> getHazard(HazardType type) {
            return activeHazards.stream()
                .filter(h -> h.type == type)
                .findFirst();
        }
    }

    // ========== Session Management ==========

    /**
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

    /**
     * Get active session for a quest.
     */
    public Optional<HazardSession> getSession(UUID questId) {
        return Optional.ofNullable(sessions.get(questId));
    }

    /**
     * End hazard session.
     */
    public HazardSession endSession(UUID questId) {
        HazardSession session = sessions.remove(questId);
        if (session != null) {
            LOGGER.info("[ArenaHazards] Ended hazard session for quest {} ({} hazards triggered)",
                questId, session.triggeredHazards.size());
        }
        return session;
    }

    // ========== Wave Triggers ==========

    /**
     * Check and trigger hazards for a wave.
     * Called when a wave starts.
     */
    public List<HazardType> checkWaveHazards(UUID questId, int waveNumber) {
        HazardSession session = sessions.get(questId);
        if (session == null) return List.of();

        List<HazardType> triggered = new ArrayList<>();

        for (HazardType type : HazardType.values()) {
            // Check if this wave triggers the hazard
            if (waveNumber >= type.triggerWave && !session.triggeredHazards.contains(type)) {
                // First time reaching this wave - trigger hazard
                triggerHazard(questId, type);
                triggered.add(type);
            } else if (waveNumber > type.triggerWave && (waveNumber - type.triggerWave) % 4 == 0) {
                // Repeat hazard every 4 waves after first trigger
                if (!session.hasActiveHazard(type)) {
                    triggerHazard(questId, type);
                    triggered.add(type);
                }
            }
        }

        return triggered;
    }

    /**
     * Trigger a specific hazard.
     */
    public void triggerHazard(UUID questId, HazardType type) {
        HazardSession session = sessions.get(questId);
        if (session == null) return;

        ActiveHazard hazard = new ActiveHazard(type);
        session.activeHazards.add(hazard);
        session.triggeredHazards.add(type);

        LOGGER.info("[ArenaHazards] Triggered {} for quest {}", type.displayName, questId);
    }

    // ========== Tick Processing ==========

    /**
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
                case ENDING -> tickEnding(level, player, session, hazard);
                case INACTIVE -> iterator.remove();
            }
        }
    }

    private void tickWarning(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        hazard.warningTicks--;

        // Warning effects
        if (hazard.warningTicks == 60) {
            // Initial warning
            player.displayClientMessage(
                Component.literal("§c§l⚠ " + hazard.type.warningText),
                true
            );
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.AMBIENT, 1.0f, 0.8f);
        }

        if (hazard.warningTicks <= 0) {
            hazard.state = HazardState.ACTIVE;
            player.displayClientMessage(
                Component.literal("§4§l" + hazard.type.displayName.toUpperCase() + "!"),
                true
            );
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.AMBIENT, 0.8f, 1.2f);

            // Initialize hazard-specific state
            initializeHazard(level, session, hazard);
        }
    }

    private void tickActive(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        hazard.ticksRemaining--;

        switch (hazard.type) {
            case FLOOR_CRUMBLE -> tickFloorCrumble(level, player, session, hazard);
            case BLOOD_MOON -> tickBloodMoon(level, player, session, hazard);
            case ARENA_SHRINK -> tickArenaShrink(level, player, session, hazard);
            case LIGHTNING_STORM -> tickLightningStorm(level, player, session, hazard);
            case VOID_RIFTS -> tickVoidRifts(level, player, session, hazard);
        }

        if (hazard.ticksRemaining <= 0) {
            hazard.state = HazardState.ENDING;
            hazard.ticksRemaining = 40; // 2 second wind-down
        }
    }

    private void tickEnding(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        hazard.ticksRemaining--;

        // Cleanup effects
        if (hazard.ticksRemaining <= 0) {
            cleanupHazard(level, session, hazard);
            hazard.state = HazardState.INACTIVE;
            player.displayClientMessage(
                Component.literal("§a" + hazard.type.displayName + " subsides..."),
                true
            );
        }
    }

    // ========== Hazard Implementations ==========

    private void initializeHazard(ServerLevel level, HazardSession session, ActiveHazard hazard) {
        switch (hazard.type) {
            case FLOOR_CRUMBLE -> initFloorCrumble(level, session, hazard);
            case ARENA_SHRINK -> initArenaShrink(session, hazard);
            default -> {}
        }
    }

    private void cleanupHazard(ServerLevel level, HazardSession session, ActiveHazard hazard) {
        switch (hazard.type) {
            case FLOOR_CRUMBLE -> cleanupFloorCrumble(level, hazard);
            case BLOOD_MOON -> cleanupBloodMoon(level, session);
            case ARENA_SHRINK -> {}  // Shrink doesn't revert
            default -> {}
        }
    }

    // --- FLOOR CRUMBLE ---

    private void initFloorCrumble(ServerLevel level, HazardSession session, ActiveHazard hazard) {
        if (session.arenaCenter == null) return;

        RandomSource random = RandomSource.create();
        int crumbleCount = 5 + random.nextInt(5);

        for (int i = 0; i < crumbleCount; i++) {
            int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            BlockPos pos = session.arenaCenter.offset(dx, -1, dz);

            // Find ground level
            while (level.getBlockState(pos).isAir() && pos.getY() > session.arenaCenter.getY() - 10) {
                pos = pos.below();
            }

            if (!level.getBlockState(pos).isAir()) {
                hazard.affectedBlocks.add(pos);
            }
        }
    }

    private void tickFloorCrumble(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        RandomSource random = level.random;

        // Crack particles on affected blocks
        for (BlockPos pos : hazard.affectedBlocks) {
            if (random.nextFloat() < 0.3f) {
                level.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    2, 0.3, 0.1, 0.3, 0.02);
            }
        }

        // Periodically collapse blocks
        if (hazard.ticksRemaining % 20 == 0 && !hazard.affectedBlocks.isEmpty()) {
            BlockPos collapsing = hazard.affectedBlocks.remove(random.nextInt(hazard.affectedBlocks.size()));

            // Check if player is on this block
            if (player.blockPosition().below().equals(collapsing) ||
                player.blockPosition().equals(collapsing)) {
                // Damage player
                player.hurt(player.damageSources().fall(), 4.0f);
            }

            // Visual effect
            level.sendParticles(ParticleTypes.EXPLOSION,
                collapsing.getX() + 0.5, collapsing.getY() + 0.5, collapsing.getZ() + 0.5,
                5, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, collapsing, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 0.5f, 1.0f);

            // Temporarily replace with air (will restore later)
            level.setBlock(collapsing, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void cleanupFloorCrumble(ServerLevel level, ActiveHazard hazard) {
        // Restore any remaining affected blocks would be handled by arena reset
        hazard.affectedBlocks.clear();
    }

    // --- BLOOD MOON ---

    private void tickBloodMoon(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        // Apply strength to nearby mobs
        if (hazard.ticksRemaining % 40 == 0) {
            AABB area = player.getBoundingBox().inflate(30);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area);

            for (Mob mob : mobs) {
                if (!mob.hasEffect(MobEffects.DAMAGE_BOOST)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
                }
                if (!mob.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, true));
                }
            }
        }

        // Red particle ambiance
        if (hazard.ticksRemaining % 10 == 0) {
            RandomSource random = level.random;
            for (int i = 0; i < 5; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * 20;
                double y = player.getY() + random.nextDouble() * 10;
                double z = player.getZ() + (random.nextDouble() - 0.5) * 20;
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 1, 0, -0.1, 0, 0);
            }
        }
    }

    private void cleanupBloodMoon(ServerLevel level, HazardSession session) {
        // Effects naturally expire
    }

    // --- ARENA SHRINK ---

    private void initArenaShrink(HazardSession session, ActiveHazard hazard) {
        if (session.arenaCenter == null) return;

        int radius = session.arenaRadius - (session.currentShrinkLevel * 5);
        BlockPos center = session.arenaCenter;
        hazard.originalBounds = new AABB(
            center.getX() - radius, center.getY() - 5, center.getZ() - radius,
            center.getX() + radius, center.getY() + 20, center.getZ() + radius
        );

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

        // Check if player is outside bounds
        if (!hazard.shrinkBounds.contains(player.position())) {
            // Push player toward center
            if (session.arenaCenter != null) {
                double dx = session.arenaCenter.getX() - player.getX();
                double dz = session.arenaCenter.getZ() - player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    player.push(dx / dist * 0.3, 0, dz / dist * 0.3);
                }
            }

            // Damage for being outside
            if (hazard.ticksRemaining % 20 == 0) {
                player.hurt(player.damageSources().magic(), 2.0f);
                player.displayClientMessage(
                    Component.literal("§c§lThe void consumes you!"),
                    true
                );
            }
        }

        // Visual boundary
        if (hazard.ticksRemaining % 5 == 0 && hazard.shrinkBounds != null) {
            RandomSource random = level.random;
            for (int i = 0; i < 10; i++) {
                double x = hazard.shrinkBounds.minX + random.nextDouble() * (hazard.shrinkBounds.maxX - hazard.shrinkBounds.minX);
                double z = hazard.shrinkBounds.minZ + random.nextDouble() * (hazard.shrinkBounds.maxZ - hazard.shrinkBounds.minZ);

                // Only show on edges
                if (random.nextBoolean()) {
                    x = random.nextBoolean() ? hazard.shrinkBounds.minX : hazard.shrinkBounds.maxX;
                } else {
                    z = random.nextBoolean() ? hazard.shrinkBounds.minZ : hazard.shrinkBounds.maxZ;
                }

                level.sendParticles(ParticleTypes.PORTAL,
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

            for (int i = 0; i < strikeCount; i++) {
                int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
                int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;

                if (session.arenaCenter != null) {
                    BlockPos strikePos = session.arenaCenter.offset(dx, 0, dz);

                    // Warning flash
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        strikePos.getX() + 0.5, strikePos.getY() + 5, strikePos.getZ() + 0.5,
                        20, 0.5, 3, 0.5, 0.5);

                    // Delayed strike effect
                    level.playSound(null, strikePos, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 0.5f, 1.0f);

                    // Damage entities in area
                    AABB strikeArea = new AABB(strikePos).inflate(2);
                    level.getEntitiesOfClass(ServerPlayer.class, strikeArea).forEach(p -> {
                        if (random.nextFloat() < 0.3f) { // 30% chance to hit
                            p.hurt(p.damageSources().lightningBolt(), 3.0f);
                        }
                    });
                }
            }
        }

        // Ambient rain particles
        if (hazard.ticksRemaining % 2 == 0) {
            for (int i = 0; i < 20; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * 30;
                double y = player.getY() + 10 + random.nextDouble() * 5;
                double z = player.getZ() + (random.nextDouble() - 0.5) * 30;
                level.sendParticles(ParticleTypes.RAIN, x, y, z, 1, 0, -1, 0, 0.5);
            }
        }
    }

    // --- VOID RIFTS ---

    private void tickVoidRifts(ServerLevel level, ServerPlayer player, HazardSession session, ActiveHazard hazard) {
        RandomSource random = level.random;

        // Spawn rift effect periodically
        if (hazard.ticksRemaining % 100 == 0 && session.arenaCenter != null) {
            int dx = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            int dz = random.nextInt(session.arenaRadius * 2) - session.arenaRadius;
            BlockPos riftPos = session.arenaCenter.offset(dx, 0, dz);

            // Rift visual
            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = random.nextDouble() * 2;
                double rx = Math.cos(angle) * radius;
                double rz = Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    riftPos.getX() + 0.5 + rx, riftPos.getY() + 1, riftPos.getZ() + 0.5 + rz,
                    1, 0, 0.5, 0, 0.1);
            }

            level.playSound(null, riftPos, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0f, 0.5f);

            // Signal to spawn system that extra mobs should spawn
            // This would integrate with WaveSpawnSystem
            player.displayClientMessage(
                Component.literal("§5A void rift tears open!"),
                true
            );
        }

        // Ambient void particles
        for (int i = 0; i < 5; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 20;
            double y = player.getY() + random.nextDouble() * 5;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 20;
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0.5);
        }
    }

    // ========== Public API ==========

    /**
     * Get active hazards for HUD display.
     */
    public List<HazardInfo> getActiveHazardInfo(UUID questId) {
        HazardSession session = sessions.get(questId);
        if (session == null) return List.of();

        return session.activeHazards.stream()
            .filter(h -> h.state != HazardState.INACTIVE)
            .map(h -> new HazardInfo(
                h.type.displayName,
                h.type.description,
                h.type.color,
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

    /**
     * Check if a position is in the safe zone (considering shrink).
     */
    public boolean isInSafeZone(UUID questId, BlockPos pos) {
        HazardSession session = sessions.get(questId);
        if (session == null) return true;

        Optional<ActiveHazard> shrink = session.getHazard(HazardType.ARENA_SHRINK);
        if (shrink.isEmpty() || shrink.get().shrinkBounds == null) return true;

        return shrink.get().shrinkBounds.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Get current shrink level (for mob spawn adjustment).
     */
    public int getShrinkLevel(UUID questId) {
        HazardSession session = sessions.get(questId);
        return session != null ? session.currentShrinkLevel : 0;
    }

    /**
     * Check if blood moon is active (for mob damage bonus).
     */
    public boolean isBloodMoonActive(UUID questId) {
        HazardSession session = sessions.get(questId);
        return session != null && session.hasActiveHazard(HazardType.BLOOD_MOON);
    }

    private ArenaHazardSystem() {}
}

package com.devmod.transport.executor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportState;

/**
 * Manages visual and audio effects for the transport system.
 * Follows Bibbia Estetica Rules 2 (Sounds) and 5 (Particles).
 *
 * <p>Sound sequences (Rule 2):
 * <ul>
 *   <li>Charging: BEACON_ACTIVATE → BEACON_AMBIENT (every 10 ticks) → BEACON_POWER_SELECT</li>
 *   <li>Teleport: ENDERMAN_TELEPORT (departure and arrival)</li>
 *   <li>Error: NOTE_BLOCK_BASS</li>
 *   <li>Countdown: NOTE_BLOCK_BELL (per second)</li>
 * </ul>
 *
 * <p>Particle patterns (Rule 5):
 * <ul>
 *   <li>IDLE: END_ROD, count=1, spread=0.3</li>
 *   <li>CHARGING: PORTAL, count=3, spread=0.5</li>
 *   <li>ACTIVE: PORTAL + END_ROD, count=10+5, spread=0.8</li>
 *   <li>TELEPORT: REVERSE_PORTAL, count=50, spread=1.0</li>
 *   <li>ARRIVAL: PORTAL + ENCHANT, count=30+20, spread=1.2</li>
 *   <li>ERROR: SMOKE, count=5, spread=0.3</li>
 * </ul>
 */
public class TransportEffectManager {

    /**
     * Transport phases for sound selection.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum TransportPhase {
        CHARGE_START(SoundEvents.BEACON_ACTIVATE, 1.5f, 0.5f),
        CHARGE_TICK(SoundEvents.BEACON_AMBIENT, 1.8f, 0.3f),
        CHARGE_COMPLETE(SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.6f),
        TELEPORT_DEPART(SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.8f),
        TELEPORT_ARRIVE(SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.8f),
        ERROR(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.6f),
        COOLDOWN_END(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.4f),
        COUNTDOWN_TICK(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.6f),
        RIFT_SPAWN(SoundEvents.END_PORTAL_SPAWN, 1.2f, 0.8f),
        RIFT_COLLAPSE(SoundEvents.GLASS_BREAK, 0.8f, 0.7f);

        private final SoundEvent sound;
        private final float pitch;
        private final float volume;

        TransportPhase(SoundEvent sound, float pitch, float volume) {
            this.sound = sound;
            this.pitch = pitch;
            this.volume = volume;
        }

        public SoundEvent getSound() { return sound; }
        public float getPitch() { return pitch; }
        public float getVolume() { return volume; }
    }

    // BossBar tracking for temporal nodes
    private final Map<UUID, ServerBossEvent> bossBars = new ConcurrentHashMap<>();

    // ============================================================================
    // SOUNDS
    // ============================================================================

    /**
     * Plays a phase-specific sound at the given position.
     */
    public void playPhaseSound(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull TransportPhase phase) {
        level.playSound(null, pos, Objects.requireNonNull(phase.getSound()), SoundSource.BLOCKS, phase.getVolume(), phase.getPitch());
    }

    /**
     * Plays a phase-specific sound at the given position with custom volume/pitch.
     */
    public void playPhaseSound(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull TransportPhase phase,
                               float volumeMultiplier, float pitchMultiplier) {
        level.playSound(null, pos, Objects.requireNonNull(phase.getSound()), SoundSource.BLOCKS,
            phase.getVolume() * volumeMultiplier, phase.getPitch() * pitchMultiplier);
    }

    // ============================================================================
    // PARTICLES
    // ============================================================================

    /**
     * Spawns particles for the given transport state at a position.
     */
    public void spawnStateParticles(
        @Nonnull ServerLevel level,
        @Nonnull Vec3 pos,
        @Nonnull TransportState state,
        @Nonnull TransportColor color,
        int moduleCount
    ) {
        switch (state) {
            case IDLE -> spawnIdleParticles(level, pos);
            case CHARGING -> spawnChargingParticles(level, pos, color, moduleCount);
            case ACTIVE -> spawnActiveParticles(level, pos);
            case ERROR -> spawnErrorParticles(level, pos);
            case COOLDOWN -> spawnCooldownParticles(level, pos);
            case LOCKED -> {} // No particles for locked state
        }
    }

    private void spawnIdleParticles(ServerLevel level, Vec3 pos) {
        // END_ROD, count=1, spread=0.3
        level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD), pos.x, pos.y + 0.5, pos.z, 1, 0.3, 0.1, 0.3, 0.01);
    }

    private void spawnChargingParticles(ServerLevel level, Vec3 pos, TransportColor color, int moduleCount) {
        // PORTAL, count=3 + moduleCount, spread=0.5
        int count = 3 + moduleCount;
        level.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), pos.x, pos.y, pos.z, count, 0.5, 0.3, 0.5, 0.05);

        // Add colored dust particles based on color
        DustParticleOptions dust = new DustParticleOptions(
            new org.joml.Vector3f(color.getRedF(), color.getGreenF(), color.getBlueF()),
            1.0f
        );
        level.sendParticles(dust, pos.x, pos.y + 0.3, pos.z, 2, 0.3, 0.2, 0.3, 0.02);
    }

    private void spawnActiveParticles(ServerLevel level, Vec3 pos) {
        // PORTAL + END_ROD, count=10+5, spread=0.8
        level.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), pos.x, pos.y, pos.z, 10, 0.8, 0.5, 0.8, 0.1);
        level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD), pos.x, pos.y + 0.5, pos.z, 5, 0.5, 0.3, 0.5, 0.05);
    }

    private void spawnErrorParticles(ServerLevel level, Vec3 pos) {
        // SMOKE, count=5, spread=0.3
        level.sendParticles(Objects.requireNonNull(ParticleTypes.SMOKE), pos.x, pos.y + 0.5, pos.z, 5, 0.3, 0.2, 0.3, 0.02);
    }

    private void spawnCooldownParticles(ServerLevel level, Vec3 pos) {
        // Minimal particles during cooldown
        level.sendParticles(Objects.requireNonNull(ParticleTypes.ENCHANT), pos.x, pos.y, pos.z, 1, 0.2, 0.1, 0.2, 0.01);
    }

    /**
     * Spawns teleport departure particles.
     */
    public void spawnDepartureParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos) {
        // REVERSE_PORTAL + END_ROD burst
        level.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), pos.x, pos.y + 0.5, pos.z, 50, 0.3, 0.5, 0.3, 0.5);
        level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD), pos.x, pos.y + 0.5, pos.z, 20, 0.2, 0.3, 0.2, 0.1);
    }

    /**
     * Spawns teleport arrival particles.
     */
    public void spawnArrivalParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos) {
        // PORTAL + ENCHANT burst
        level.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), pos.x, pos.y + 0.5, pos.z, 30, 0.4, 0.6, 0.4, 0.3);
        level.sendParticles(Objects.requireNonNull(ParticleTypes.ENCHANT), pos.x, pos.y + 0.3, pos.z, 20, 0.5, 0.4, 0.5, 0.1);
    }

    /**
     * Spawns rift spawn particles (for RiftGate).
     */
    public void spawnRiftSpawnParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos) {
        level.sendParticles(Objects.requireNonNull(ParticleTypes.REVERSE_PORTAL), pos.x, pos.y, pos.z, 100, 1.5, 1.5, 1.5, 0.5);
        level.sendParticles(Objects.requireNonNull(ParticleTypes.END_ROD), pos.x, pos.y + 1, pos.z, 30, 1.0, 1.0, 1.0, 0.2);
    }

    /**
     * Spawns rift collapse particles (for RiftGate expiration).
     */
    public void spawnRiftCollapseParticles(@Nonnull ServerLevel level, @Nonnull Vec3 pos) {
        level.sendParticles(Objects.requireNonNull(ParticleTypes.EXPLOSION), pos.x, pos.y + 1, pos.z, 5, 1.0, 1.0, 1.0, 0.1);
        level.sendParticles(Objects.requireNonNull(ParticleTypes.SMOKE), pos.x, pos.y, pos.z, 50, 2.0, 2.0, 2.0, 0.1);
    }

    // ============================================================================
    // BOSSBAR MANAGEMENT
    // ============================================================================

    /**
     * Creates a BossBar for a temporal node countdown.
     */
    public void createBossBar(@Nonnull UUID nodeId, @Nonnull Component title, int durationTicks) {
        ServerBossEvent bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setDarkenScreen(false);
        bossBar.setCreateWorldFog(false);
        bossBar.setPlayBossMusic(false);
        bossBar.setProgress(1.0f);
        bossBars.put(nodeId, bossBar);
    }

    /**
     * Updates a BossBar progress.
     */
    public void updateBossBar(@Nonnull UUID nodeId, float progress, @Nullable BossEvent.BossBarColor color) {
        ServerBossEvent bossBar = bossBars.get(nodeId);
        if (bossBar != null) {
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            if (color != null) {
                bossBar.setColor(color);
            }
        }
    }

    /**
     * Removes a BossBar.
     */
    public void removeBossBar(@Nonnull UUID nodeId) {
        ServerBossEvent bossBar = bossBars.remove(nodeId);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }

    /**
     * Adds a player to a BossBar.
     */
    public void addPlayerToBossBar(@Nonnull UUID nodeId, @Nonnull ServerPlayer player) {
        ServerBossEvent bossBar = bossBars.get(nodeId);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    /**
     * Removes a player from a BossBar.
     */
    public void removePlayerFromBossBar(@Nonnull UUID nodeId, @Nonnull ServerPlayer player) {
        ServerBossEvent bossBar = bossBars.get(nodeId);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    /**
     * Updates BossBar visibility based on player distance.
     */
    public void updateBossBarVisibility(@Nonnull UUID nodeId, @Nonnull BlockPos nodePos, @Nonnull ServerLevel level, double range) {
        ServerBossEvent bossBar = bossBars.get(nodeId);
        if (bossBar == null) {
            return;
        }

        double rangeSquared = range * range;
        for (ServerPlayer player : level.players()) {
            double distSq = player.blockPosition().distSqr(nodePos);
            if (distSq <= rangeSquared) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    /**
     * Clears all BossBars (used on server shutdown).
     */
    public void clearAllBossBars() {
        for (ServerBossEvent bossBar : bossBars.values()) {
            bossBar.removeAllPlayers();
        }
        bossBars.clear();
    }

    // ============================================================================
    // CHARGING EFFECTS (from TelepadBlockEntity pattern)
    // ============================================================================

    /**
     * Handles per-tick charging effects (particles and periodic sounds).
     *
     * @param level the server level
     * @param pos the charging position
     * @param color the transport color
     * @param currentTick current charge tick (0 = start)
     * @param totalTicks total required ticks
     */
    public void tickChargingEffects(
        @Nonnull ServerLevel level,
        @Nonnull Vec3 pos,
        @Nonnull TransportColor color,
        int currentTick,
        int totalTicks
    ) {
        // Play start sound on tick 0
        if (currentTick == 0) {
            playPhaseSound(level, Objects.requireNonNull(BlockPos.containing(pos)), TransportPhase.CHARGE_START);
        }
        // Play ambient sound every 10 ticks during charging
        else if (currentTick % 10 == 0 && currentTick < totalTicks) {
            playPhaseSound(level, Objects.requireNonNull(BlockPos.containing(pos)), TransportPhase.CHARGE_TICK);
        }

        // Spawn particles every 2 ticks
        if (currentTick % 2 == 0) {
            spawnChargingParticles(level, pos, color, 0);
        }
    }
}

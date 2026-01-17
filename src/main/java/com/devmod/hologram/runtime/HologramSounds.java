package com.devmod.hologram.runtime;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Standardized sounds for hologram interactions (Regola 2 - Bibbia Estetica).
 *
 * <p>All hologram sounds follow a consistent pattern with defined events,
 * pitches, and volumes.
 */
public final class HologramSounds {
    private HologramSounds() {}

    /**
     * Sound phases for hologram interactions.
     */
    @SuppressWarnings("ImmutableEnumChecker")
    public enum Phase {
        /** Opening the hologram editor. */
        EDITOR_OPEN(SoundEvents.BEACON_ACTIVATE, 1.2f, 0.6f),

        /** Closing the hologram editor. */
        EDITOR_CLOSE(SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.4f),

        /** Adding a new line. */
        LINE_ADD(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.3f),

        /** Removing a line. */
        LINE_REMOVE(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 0.3f),

        /** Saving changes. */
        SAVE(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f),

        /** Toggling preview mode. */
        PREVIEW(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 0.4f),

        /** Error occurred. */
        ERROR(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.6f),

        /** Spawning a new hologram. */
        SPAWN(SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f),

        /** Removing a hologram. */
        REMOVE(SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.6f);

        private final SoundEvent sound;
        private final float basePitch;
        private final float baseVolume;

        Phase(SoundEvent sound, float basePitch, float baseVolume) {
            this.sound = sound;
            this.basePitch = basePitch;
            this.baseVolume = baseVolume;
        }

        public SoundEvent getSound() {
            return sound;
        }

        public float getBasePitch() {
            return basePitch;
        }

        public float getBaseVolume() {
            return baseVolume;
        }
    }

    /**
     * Plays a hologram sound at the specified position.
     *
     * @param level The level to play the sound in
     * @param pos   The position to play the sound at
     * @param phase The sound phase to play
     */
    public static void playPhase(Level level, Vec3 pos, Phase phase) {
        if (level == null || pos == null || phase == null) {
            return;
        }

        // Add slight pitch variation for natural feel
        float pitch = phase.getBasePitch() + (level.random.nextFloat() - 0.5f) * 0.1f;
        float volume = phase.getBaseVolume();

        level.playSound(
            null, // No specific player = all nearby players hear it
            pos.x, pos.y, pos.z,
            phase.getSound(),
            SoundSource.BLOCKS,
            volume,
            pitch
        );
    }

    /**
     * Plays a hologram sound for a specific player only (client-side).
     *
     * @param level The level to play the sound in
     * @param pos   The position to play the sound at
     * @param phase The sound phase to play
     */
    public static void playPhaseLocal(Level level, Vec3 pos, Phase phase) {
        if (level == null || pos == null || phase == null) {
            return;
        }

        float pitch = phase.getBasePitch() + (level.random.nextFloat() - 0.5f) * 0.1f;
        float volume = phase.getBaseVolume();

        level.playLocalSound(
            pos.x, pos.y, pos.z,
            phase.getSound(),
            SoundSource.BLOCKS,
            volume,
            pitch,
            false
        );
    }
}

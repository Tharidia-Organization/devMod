package com.devmod.npc;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Unified NPC sound system.
 * All NPC interactions follow this standard audio sequence.
 *
 * <p>BIBBIA ESTETICA R2: Suoni Dialog - INVIOLABILE
 * <ul>
 *   <li>MAI suoni custom per singoli NPC</li>
 *   <li>Pitch può variare ±0.1 per varietà</li>
 *   <li>Suoni typewriter opzionali (config client)</li>
 * </ul>
 */
public final class NpcSounds {
    private NpcSounds() {}

    /**
     * Sound phases for NPC dialog interactions.
     * Uses runtime lookup to avoid storing mutable references in enum constants.
     */
    public enum Phase {
        /** Apertura dialog */
        DIALOG_OPEN(1.2f, 0.6f),

        /** Ogni linea di testo */
        LINE_APPEAR(1.5f, 0.2f),

        /** Mouse over opzione */
        OPTION_HOVER(1.8f, 0.3f),

        /** Click su opzione */
        OPTION_SELECT(1.4f, 0.5f),

        /** Chiusura dialog */
        DIALOG_CLOSE(0.8f, 0.4f),

        /** Azione completata */
        ACTION_SUCCESS(1.2f, 0.6f),

        /** Azione fallita */
        ACTION_ERROR(0.5f, 0.6f),

        /** Prima visita NPC */
        FIRST_VISIT(1.0f, 0.4f);

        private final float basePitch;
        private final float baseVolume;

        Phase(float basePitch, float baseVolume) {
            this.basePitch = basePitch;
            this.baseVolume = baseVolume;
        }

        /**
         * Gets the sound event via runtime lookup to avoid storing mutable references.
         */
        public SoundEvent getSound() {
            return Objects.requireNonNull(switch (this) {
                case DIALOG_OPEN -> SoundEvents.NOTE_BLOCK_CHIME.value();
                case LINE_APPEAR -> SoundEvents.NOTE_BLOCK_HAT.value();
                case OPTION_HOVER -> SoundEvents.UI_BUTTON_CLICK.value();
                case OPTION_SELECT -> SoundEvents.NOTE_BLOCK_BELL.value();
                case DIALOG_CLOSE -> SoundEvents.NOTE_BLOCK_CHIME.value();
                case ACTION_SUCCESS -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case ACTION_ERROR -> SoundEvents.NOTE_BLOCK_BASS.value();
                case FIRST_VISIT -> SoundEvents.PLAYER_LEVELUP;
            });
        }

        public float getBasePitch() {
            return basePitch;
        }

        public float getBaseVolume() {
            return baseVolume;
        }
    }

    /**
     * Play a sound phase at the given position.
     * Pitch varies slightly (±0.1) for natural variety.
     */
    public static void playPhase(Level level, Vec3 pos, Phase phase) {
        if (level == null || pos == null || phase == null) return;

        SoundEvent sound = phase.getSound();
        float pitch = phase.getBasePitch() + (level.random.nextFloat() - 0.5f) * 0.1f;
        float volume = phase.getBaseVolume();

        level.playLocalSound(pos.x, pos.y, pos.z, Objects.requireNonNull(sound), SoundSource.NEUTRAL, volume, pitch, false);
    }

    /**
     * Play a sound phase at the given block position.
     */
    public static void playPhase(Level level, BlockPos pos, Phase phase) {
        if (pos == null) return;
        playPhase(level, Vec3.atCenterOf(pos), phase);
    }

    /**
     * Play a sound phase for client-side only.
     * Use this from GUI screens.
     */
    public static void playPhaseClient(Level level, Phase phase) {
        if (level == null || !level.isClientSide || phase == null) return;

        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        playPhase(level, player.position(), phase);
    }
}

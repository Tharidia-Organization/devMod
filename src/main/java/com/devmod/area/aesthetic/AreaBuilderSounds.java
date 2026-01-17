package com.devmod.area.aesthetic;

import java.util.Objects;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * BIBBIA ESTETICA - REGOLA 2: SUONI FASI COSTRUZIONE
 *
 * Sequenza sonora obbligatoria per ogni fase.
 * Questi suoni definiscono il feedback audio del sistema Area Builder.
 */
public final class AreaBuilderSounds {

    private AreaBuilderSounds() {}

    // ============================================================================
    // INTERAZIONE
    // ============================================================================

    /** Sound when editor is activated */
    public static final SoundEvent EDITOR_ACTIVATE = SoundEvents.BEACON_ACTIVATE;

    /** Sound when editor is deactivated */
    public static final SoundEvent EDITOR_DEACTIVATE = SoundEvents.BEACON_DEACTIVATE;

    /** Sound when GUI is opened */
    public static final SoundEvent OPEN_GUI = SoundEvents.UI_LOOM_SELECT_PATTERN;

    // ============================================================================
    // PREVIEW
    // ============================================================================

    /** Sound when preview starts */
    public static final SoundEvent PREVIEW_START = SoundEvents.ENCHANTMENT_TABLE_USE;

    /** Sound when preview changes (dimensions, shape, etc.) */
    public static final SoundEvent PREVIEW_CHANGE = SoundEvents.NOTE_BLOCK_CHIME.value();

    /** Sound when preview is confirmed */
    public static final SoundEvent PREVIEW_CONFIRM = SoundEvents.PLAYER_LEVELUP;

    // ============================================================================
    // BUILDING
    // ============================================================================

    /** Sound when building starts */
    public static final SoundEvent BUILD_START = SoundEvents.BEACON_POWER_SELECT;

    /** Sound for each building step */
    public static final SoundEvent BUILD_STEP = SoundEvents.STONE_PLACE;

    /** Sound for progress milestones (25%, 50%, 75%) */
    public static final SoundEvent BUILD_PROGRESS = SoundEvents.EXPERIENCE_ORB_PICKUP;

    /** Sound when building completes */
    public static final SoundEvent BUILD_COMPLETE = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;

    // ============================================================================
    // ERRORI
    // ============================================================================

    /** Sound for general errors */
    public static final SoundEvent ERROR = SoundEvents.VILLAGER_NO;

    /** Sound for conflict/overlap errors */
    public static final SoundEvent CONFLICT = SoundEvents.ANVIL_LAND;

    // ============================================================================
    // VOLUME/PITCH CONSTANTS
    // ============================================================================

    public static final float VOLUME_FULL = 1.0f;
    public static final float VOLUME_HIGH = 0.8f;
    public static final float VOLUME_MEDIUM = 0.6f;
    public static final float VOLUME_LOW = 0.5f;
    public static final float VOLUME_QUIET = 0.3f;

    public static final float PITCH_NORMAL = 1.0f;
    public static final float PITCH_HIGH = 1.2f;
    public static final float PITCH_LOW = 0.8f;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Plays the editor activation sound to a player.
     */
    public static void playEditorActivate(Level level, Player player) {
        player.playSound(Objects.requireNonNull(EDITOR_ACTIVATE, "EDITOR_ACTIVATE"), VOLUME_FULL, PITCH_HIGH);
    }

    /**
     * Plays the GUI open sound to a player.
     */
    public static void playOpenGui(Level level, Player player) {
        player.playSound(Objects.requireNonNull(OPEN_GUI, "OPEN_GUI"), VOLUME_HIGH, PITCH_NORMAL);
    }

    /**
     * Plays the preview start sound to a player.
     */
    public static void playPreviewStart(Level level, Player player) {
        player.playSound(Objects.requireNonNull(PREVIEW_START, "PREVIEW_START"), VOLUME_MEDIUM, PITCH_NORMAL);
    }

    /**
     * Plays the preview change sound with variable pitch.
     */
    public static void playPreviewChange(Level level, Player player, float pitchVariation) {
        player.playSound(Objects.requireNonNull(PREVIEW_CHANGE, "PREVIEW_CHANGE"), VOLUME_LOW, PITCH_NORMAL + pitchVariation);
    }

    /**
     * Plays the build start sound to a player.
     */
    public static void playBuildStart(Level level, Player player) {
        player.playSound(Objects.requireNonNull(BUILD_START, "BUILD_START"), VOLUME_FULL, PITCH_NORMAL);
    }

    /**
     * Plays the build step sound at a position.
     */
    public static void playBuildStep(Level level, double x, double y, double z) {
        float randomPitch = PITCH_NORMAL + (level.random.nextFloat() - 0.5f) * 0.2f;
        level.playSound(null, x, y, z, Objects.requireNonNull(BUILD_STEP, "BUILD_STEP"), SoundSource.BLOCKS, VOLUME_QUIET, randomPitch);
    }

    /**
     * Plays the build progress sound (for milestones).
     */
    public static void playBuildProgress(Level level, Player player, int milestone) {
        float pitch = PITCH_NORMAL + (milestone * 0.2f / 100f);
        player.playSound(Objects.requireNonNull(BUILD_PROGRESS, "BUILD_PROGRESS"), VOLUME_HIGH - 0.1f, pitch);
    }

    /**
     * Plays the build complete sound to a player.
     */
    public static void playBuildComplete(Level level, Player player) {
        player.playSound(Objects.requireNonNull(BUILD_COMPLETE, "BUILD_COMPLETE"), VOLUME_FULL, PITCH_NORMAL);
    }

    /**
     * Plays the error sound to a player.
     */
    public static void playError(Level level, Player player) {
        player.playSound(Objects.requireNonNull(ERROR, "ERROR"), VOLUME_FULL, PITCH_LOW);
    }

    /**
     * Plays the conflict sound to a player.
     */
    public static void playConflict(Level level, Player player) {
        player.playSound(Objects.requireNonNull(CONFLICT, "CONFLICT"), VOLUME_FULL, PITCH_NORMAL);
    }
}

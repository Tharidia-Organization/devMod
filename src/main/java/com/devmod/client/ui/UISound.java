package com.devmod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Centralized UI sound feedback following the UI Bible specification.
 * All UI sound playback MUST go through this class.
 *
 * <p>Sound design principles:
 * <ul>
 *   <li>Consistent pitch/volume for same action types</li>
 *   <li>Subtle hover sounds (low volume)</li>
 *   <li>Distinct feedback for success/error/warning</li>
 *   <li>All sounds derived from Minecraft vanilla for consistency</li>
 * </ul>
 */
public final class UISound {

    private UISound() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // SOUND SPECIFICATIONS (from UI Bible)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final float VOLUME_QUIET = 0.15f;
    private static final float VOLUME_SOFT = 0.4f;
    private static final float VOLUME_NORMAL = 0.6f;
    private static final float VOLUME_LOUD = 0.8f;

    private static final float PITCH_LOW = 0.8f;
    private static final float PITCH_NORMAL = 1.0f;
    private static final float PITCH_HIGH = 1.2f;
    private static final float PITCH_HIGHER = 1.5f;

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERACTION SOUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play hover sound - very subtle feedback when entering interactive element.
     * Use sparingly to avoid fatigue.
     */
    public static void hover() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), PITCH_HIGH, VOLUME_QUIET);
    }

    /**
     * Play click sound - standard button press feedback.
     */
    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), PITCH_NORMAL, VOLUME_LOUD);
    }

    /**
     * Play confirm sound - successful action completion.
     */
    public static void confirm() {
        play(SoundEvents.PLAYER_LEVELUP, PITCH_HIGHER, VOLUME_NORMAL);
    }

    /**
     * Play cancel sound - action cancelled or back navigation.
     */
    public static void cancel() {
        play(SoundEvents.VILLAGER_NO, PITCH_NORMAL, VOLUME_SOFT);
    }

    /**
     * Play error sound - action failed or validation error.
     */
    public static void error() {
        play(SoundEvents.ANVIL_LAND, PITCH_LOW, VOLUME_SOFT);
    }

    /**
     * Play warning sound - cautionary feedback.
     */
    public static void warning() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), PITCH_LOW, VOLUME_SOFT);
    }

    /**
     * Play notification sound - new notification arrived.
     */
    public static void playNotification() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, PITCH_HIGH, VOLUME_NORMAL);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOGGLE SOUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play toggle on sound.
     */
    public static void toggleOn() {
        play(SoundEvents.LEVER_CLICK, PITCH_HIGH, VOLUME_SOFT);
    }

    /**
     * Play toggle off sound.
     */
    public static void toggleOff() {
        play(SoundEvents.LEVER_CLICK, PITCH_LOW, VOLUME_SOFT);
    }

    /**
     * Play toggle sound based on new state.
     */
    public static void toggle(boolean newState) {
        if (newState) {
            toggleOn();
        } else {
            toggleOff();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PANEL SOUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play panel open sound.
     */
    public static void panelOpen() {
        play(SoundEvents.CHEST_OPEN, PITCH_HIGH + 0.1f, VOLUME_SOFT - 0.1f);
    }

    /**
     * Play panel close sound.
     */
    public static void panelClose() {
        play(SoundEvents.CHEST_CLOSE, PITCH_HIGH + 0.1f, VOLUME_SOFT - 0.1f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA SOUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play save confirmation sound.
     */
    public static void save() {
        play(SoundEvents.VILLAGER_YES, VOLUME_LOUD, PITCH_HIGH);
    }

    /**
     * Play delete/reset sound.
     */
    public static void delete() {
        play(SoundEvents.ITEM_PICKUP, VOLUME_NORMAL, PITCH_LOW);
    }

    /**
     * Play typing sound (for text input).
     */
    public static void type() {
        play(SoundEvents.WOOD_HIT, PITCH_HIGH + 0.2f, VOLUME_QUIET - 0.05f);
    }

    /**
     * Play scroll sound.
     */
    public static void scroll() {
        play(SoundEvents.WOOL_HIT, PITCH_HIGHER, VOLUME_QUIET - 0.05f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RADIAL MENU SOUNDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play radial menu open sound.
     */
    public static void radialOpen() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, VOLUME_NORMAL);
    }

    /**
     * Play radial menu close sound.
     */
    public static void radialClose() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f, VOLUME_SOFT);
    }

    /**
     * Play radial category switch sound.
     */
    public static void radialSwitch() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, VOLUME_SOFT);
    }

    /**
     * Play favorite add sound.
     */
    public static void favoriteAdd() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), PITCH_HIGH + 0.1f, VOLUME_NORMAL);
    }

    /**
     * Play favorite remove sound.
     */
    public static void favoriteRemove() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), PITCH_LOW - 0.1f, VOLUME_SOFT);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE PLAYBACK
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Play a sound with specified parameters.
     *
     * @param sound  The sound event to play
     * @param pitch  Pitch multiplier (0.5 = octave down, 2.0 = octave up)
     * @param volume Volume (0.0 - 1.0)
     */
    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && sound != null) {
            try {
                SimpleSoundInstance instance = SimpleSoundInstance.forUI(sound, pitch, volume);
                if (instance != null) {
                    mc.getSoundManager().play(instance);
                }
            } catch (Exception e) {
                // Silently ignore sound playback errors
            }
        }
    }

    /**
     * Play a sound with normal pitch.
     */
    public static void play(SoundEvent sound, float volume) {
        play(sound, PITCH_NORMAL, volume);
    }

    /**
     * Check if sounds are enabled (respects game settings).
     */
    public static boolean isSoundEnabled() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options != null && mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) > 0;
    }
}

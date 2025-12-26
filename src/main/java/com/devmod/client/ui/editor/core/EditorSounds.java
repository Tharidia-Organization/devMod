package com.devmod.client.ui.editor.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.devmod.config.EditorClientConfig;

/**
 * Sound effects for editor interactions.
 * All sounds are optional and can be disabled in config.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#17-sound-design
 */
public final class EditorSounds {
    private EditorSounds() {}

    // ═══════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════════════════════════

    /** Tab switch sound */
    public static void playTabSwitch() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
        }
    }

    /** Slot selection sound */
    public static void playSlotSelect() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.4f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERACTION
    // ═══════════════════════════════════════════════════════════════

    /** Button click sound */
    public static void playButtonClick() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.0f);
        }
    }

    /** Slider drag tick sound */
    public static void playSliderTick() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.1f, 1.5f);
        }
    }

    /** Toggle switch sound */
    public static void playToggle(boolean newState) {
        if (isSoundEnabled()) {
            float pitch = newState ? 1.3f : 0.9f;
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, pitch);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FEEDBACK
    // ═══════════════════════════════════════════════════════════════

    /** Successful action sound (save, apply) */
    public static void playSuccess() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.PLAYER_LEVELUP, 0.3f, 2.0f);
        }
    }

    /** Error/invalid action sound */
    public static void playError() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.ANVIL_LAND, 0.2f, 0.5f);
        }
    }

    /** Warning sound */
    public static void playWarning() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.3f, 0.8f);
        }
    }

    /** Undo action sound */
    public static void playUndo() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.7f);
        }
    }

    /** Redo action sound */
    public static void playRedo() {
        if (isSoundEnabled()) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.1f);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════

    private static boolean isSoundEnabled() {
        try {
            return EditorClientConfig.EDITOR_SOUNDS_ENABLED.get();
        } catch (Exception e) {
            // Config not yet loaded, default to enabled
            return true;
        }
    }

    private static void playSound(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && sound != null) {
            SimpleSoundInstance instance = SimpleSoundInstance.forUI(sound, pitch, volume);
            if (instance != null) {
                mc.getSoundManager().play(instance);
            }
        }
    }
}

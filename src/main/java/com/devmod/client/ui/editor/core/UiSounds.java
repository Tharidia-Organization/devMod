package com.devmod.client.ui.editor.core;

/**
 * UI sound feedback helpers (client-only).
 */
public final class UiSounds {
    private UiSounds() {}

    /* Play a click sound for button presses */
    public static void click() {
        playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
    }

    /* Play a success sound for completed actions (save, confirm) */
    public static void success() {
        playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    /* Play an error sound for failed actions */
    public static void error() {
        playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
    }

    /* Play a warning sound */
    public static void warning() {
        playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.8f);
    }

    /* Play a toggle on sound */
    public static void toggleOn() {
        playSound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.5f, 1.2f);
    }

    /* Play a toggle off sound */
    public static void toggleOff() {
        playSound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.5f, 0.8f);
    }

    /* Play a notification sound */
    public static void notification() {
        playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
    }

    /* Play a save confirmation sound */
    public static void save() {
        playSound(net.minecraft.sounds.SoundEvents.VILLAGER_YES, 0.8f, 1.2f);
    }

    /* Play a delete/reset sound */
    public static void delete() {
        playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.6f, 0.6f);
    }

    private static void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            net.minecraft.client.resources.sounds.SimpleSoundInstance instance =
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, volume);
            if (instance != null) {
                mc.getSoundManager().play(instance);
            }
        }
    }
}

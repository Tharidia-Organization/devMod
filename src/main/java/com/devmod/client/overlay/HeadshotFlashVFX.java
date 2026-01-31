package com.devmod.client.overlay;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import com.google.errorprone.annotations.InlineMe;

import com.devmod.DevMod;

/**
 * Headshot feedback system.
 *
 * Provides satisfying audio feedback when the player lands a headshot.
 * Previously used a red screen flash, but that was conceptually wrong
 * (red = damage taken, not damage dealt). Now uses audio-only feedback
 * for a clean, non-intrusive experience.
 *
 * The "dink" sound (high-pitched crossbow hit) is universally recognized
 * as a headshot confirmation sound from games like CS:GO.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class HeadshotFlashVFX {

    // Cooldown to prevent sound spam on rapid headshots
    private static long lastTriggerTime = 0;
    private static final long COOLDOWN_MS = 50;

    // Sound settings - "dink" style headshot confirmation
    private static final float SOUND_PITCH = 1.8f;  // High pitch for sharp "dink"
    private static final float SOUND_VOLUME = 0.7f; // Audible but not overwhelming

    /**
     * Triggers headshot feedback. Call when a headshot is detected.
     * Plays a satisfying "dink" sound as confirmation.
     */
    public static void trigger() {
        long now = System.currentTimeMillis();

        // Prevent sound spam
        if (now - lastTriggerTime < COOLDOWN_MS) {
            return;
        }
        lastTriggerTime = now;

        // Play headshot confirmation sound (client-side)
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(
                    Objects.requireNonNull(SoundEvents.CROSSBOW_HIT),
                    SOUND_PITCH,
                    SOUND_VOLUME
                )
            ));
        }
    }

    /**
     * Returns false.
     *
     * @deprecated Flash is no longer used. Kept for API compatibility.
     * @return always false
     */
    @Deprecated
    @InlineMe(replacement = "false")
    public static boolean isActive() {
        return false;
    }
}

package com.devmod.client.ui.components;

import com.devmod.client.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Simple countdown helper for timed UI selections.
 * Provides urgency state, color mapping, and low-time audio cues.
 */
@OnlyIn(Dist.CLIENT)
public final class CountdownTimer {

    public enum Urgency {
        NORMAL,
        WARN,
        URGENT
    }

    private final long expiresAtMs;
    private final int warnSeconds;
    private final int urgentSeconds;
    private final int audioCueSeconds;
    private int lastCueSecond = Integer.MIN_VALUE;

    public CountdownTimer(long expiresAtMs, int warnSeconds, int urgentSeconds, int audioCueSeconds) {
        this.expiresAtMs = expiresAtMs;
        this.warnSeconds = Math.max(0, warnSeconds);
        this.urgentSeconds = Math.max(0, urgentSeconds);
        this.audioCueSeconds = Math.max(0, audioCueSeconds);
    }

    public boolean isActive() {
        return expiresAtMs > 0;
    }

    public long getRemainingMs() {
        if (!isActive()) {
            return 0;
        }
        return Math.max(0L, expiresAtMs - System.currentTimeMillis());
    }

    public int getRemainingSeconds() {
        if (!isActive()) {
            return -1;
        }
        long remainingMs = getRemainingMs();
        if (remainingMs <= 0) {
            return 0;
        }
        return (int) Math.ceil(remainingMs / 1000.0);
    }

    public Urgency getUrgency() {
        int remaining = getRemainingSeconds();
        if (remaining <= urgentSeconds) {
            return Urgency.URGENT;
        }
        if (remaining <= warnSeconds) {
            return Urgency.WARN;
        }
        return Urgency.NORMAL;
    }

    public int getColor() {
        return switch (getUrgency()) {
            case URGENT -> UIConstants.Accent.RED();
            case WARN -> UIConstants.Accent.GOLD();
            case NORMAL -> UIConstants.Accent.GREEN();
        };
    }

    public float getPulse() {
        if (getUrgency() != Urgency.URGENT) {
            return 1.0f;
        }
        return 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 80.0);
    }

    public void tick() {
        if (!isActive()) {
            return;
        }
        int remaining = getRemainingSeconds();
        if (remaining <= 0 || remaining > audioCueSeconds) {
            return;
        }
        if (remaining != lastCueSecond) {
            playCue(remaining);
            lastCueSecond = remaining;
        }
    }

    private void playCue(int remainingSeconds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) {
            return;
        }
        float pitch = Math.min(1.6f, 1.0f + (audioCueSeconds - remainingSeconds) * 0.06f);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvents.UI_BUTTON_CLICK.value(),
            pitch,
            0.6f
        ));
    }
}
